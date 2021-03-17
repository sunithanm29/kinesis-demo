/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates.
 * Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kinesis.sample;

import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.amazonaws.services.kinesis.producer.UnexpectedMessageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.kinesis.producer.Attempt;
import com.amazonaws.services.kinesis.producer.KinesisProducerConfiguration;
import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.amazonaws.services.kinesis.producer.UserRecordFailedException;
import com.amazonaws.services.kinesis.producer.UserRecordResult;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;


 public class SampleProducer {
    private static final Logger log = LoggerFactory.getLogger(SampleProducer.class);
    
    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(1);
    
    
    private static final String TIMESTAMP = Long.toString(System.currentTimeMillis());
    
    
    public static void main(String[] args) throws Exception {
        final SampleProducerConfig config = new SampleProducerConfig(args);

        log.info(String.format("Stream name: %s Region: %s secondsToRun %d",config.getStreamName(), config.getRegion(),
                config.getSecondsToRun()));
        log.info(String.format("Will attempt to run the KPL at %f MB/s...",(config.getDataSize() * config
                .getRecordsPerSecond())/(1000000.0)));

        final KinesisProducer producer = new KinesisProducer(config.transformToKinesisProducerConfiguration());
        
        
        final AtomicLong recordCount = new AtomicLong(0);
        final AtomicLong completed = new AtomicLong(0);
      
        final FutureCallback<UserRecordResult> callback = new FutureCallback<UserRecordResult>() {
            @Override
            public void onFailure(Throwable t) {
                if (t instanceof UserRecordFailedException) {
                    int attempts = ((UserRecordFailedException) t).getResult().getAttempts().size()-1;
                    Attempt last = ((UserRecordFailedException) t).getResult().getAttempts().get(attempts);
                    if(attempts > 1) {
                        Attempt previous = ((UserRecordFailedException) t).getResult().getAttempts().get(attempts - 1);
                        log.error(String.format(
                                "Record failed to put - %s : %s. Previous failure - %s : %s",
                                last.getErrorCode(), last.getErrorMessage(), previous.getErrorCode(), previous.getErrorMessage()));
                    }else{
                        log.error(String.format(
                                "Record failed to put - %s : %s.",
                                last.getErrorCode(), last.getErrorMessage()));
                    }

                } else if (t instanceof Exception) {
                    log.error("Record failed to put due to unexpected message received from native layer",
                            t);
                }
                log.error("Exception during put", t);
            }

            @Override
            public void onSuccess(UserRecordResult result) {
                completed.getAndIncrement();
            }
        };
        
        final ExecutorService callbackThreadPool = Executors.newCachedThreadPool();

        // The lines within run() are the essence of the KPL API.
        final Runnable putOneRecord = new Runnable() {
            @Override
            public void run() {
                ByteBuffer data = Utils.generateData(recordCount.get(), config.getDataSize());
                // TIMESTAMP is our partition key
                ListenableFuture<UserRecordResult> f =
                        producer.addUserRecord(config.getStreamName(), TIMESTAMP, Utils.randomExplicitHashKey(), data);
                Futures.addCallback(f, callback, callbackThreadPool);
            }
        };
        
        // This gives us progress updates
        EXECUTOR.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                long put = recordCount.get();
                long total = config.getRecordsPerSecond() * config.getSecondsToRun();
                double putPercent = 100.0 * put / total;
                long done = completed.get();
                double donePercent = 100.0 * done / total;
                log.info(String.format(
                        "Put %d of %d so far (%.2f %%), %d have completed (%.2f %%)",
                        put, total, putPercent, done, donePercent));
            }
        }, 1, 1, TimeUnit.SECONDS);
        
        // Kick off the puts
        log.info(String.format(
                "Starting puts... will run for %d seconds at %d records per second", config.getSecondsToRun(),
                config.getRecordsPerSecond()));
        executeAtTargetRate(EXECUTOR, putOneRecord, recordCount, config.getSecondsToRun(),
                config.getRecordsPerSecond());
        
        
        EXECUTOR.awaitTermination(config.getSecondsToRun() + 1, TimeUnit.SECONDS);
        
       
        log.info("Waiting for remaining puts to finish...");
        producer.flushSync();
        log.info("All records complete.");
        
        producer.destroy();
        log.info("Finished.");
    }

    private static void executeAtTargetRate(
            final ScheduledExecutorService exec,
            final Runnable task,
            final AtomicLong counter,
            final int durationSeconds,
            final int ratePerSecond) {
        exec.scheduleWithFixedDelay(new Runnable() {
            final long startTime = System.nanoTime();

            @Override
            public void run() {
                double secondsRun = (System.nanoTime() - startTime) / 1e9;
                double targetCount = Math.min(durationSeconds, secondsRun) * ratePerSecond;
                
                while (counter.get() < targetCount) {
                    counter.getAndIncrement();
                    try {
                        task.run();
                    } catch (Exception e) {
                        log.error("Error running task", e);
                        System.exit(1);
                    }
                }
                
                if (secondsRun >= durationSeconds) {
                    exec.shutdown();
                }
            }
        }, 0, 1, TimeUnit.MILLISECONDS);
    }
    

}
