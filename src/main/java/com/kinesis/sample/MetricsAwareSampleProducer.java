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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.kinesis.producer.Attempt;
import com.amazonaws.services.kinesis.producer.KinesisProducerConfiguration;
import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.amazonaws.services.kinesis.producer.Metric;
import com.amazonaws.services.kinesis.producer.UserRecordFailedException;
import com.amazonaws.services.kinesis.producer.UserRecordResult;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

public class MetricsAwareSampleProducer {
    private static final Logger log = LoggerFactory.getLogger(SampleProducer.class);
    
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        final long totalRecordsToPut = 50000;
        final int dataSize = 64;
        final long outstandingLimit = 5000;
        
        final AtomicLong sequenceNumber = new AtomicLong(0);
        final AtomicLong completed = new AtomicLong(0);
        final String timetstamp = Long.toString(System.currentTimeMillis());
        
        KinesisProducerConfiguration config = new KinesisProducerConfiguration()
                .setRecordMaxBufferedTime(3000)
                .setMaxConnections(1)
                .setRequestTimeout(60000)
                .setRegion(SampleProducerConfig.REGION_DEFAULT);
        
        final KinesisProducer kinesisProducer = new KinesisProducer(config);
        
        // Result handler
        final FutureCallback<UserRecordResult> callback = new FutureCallback<UserRecordResult>() {
            @Override
            public void onFailure(Throwable t) {
                if (t instanceof UserRecordFailedException) {
                    Attempt last = Iterables.getLast(
                            ((UserRecordFailedException) t).getResult().getAttempts());
                    log.error(String.format(
                            "Record failed to put - %s : %s",
                            last.getErrorCode(), last.getErrorMessage()));
                }
                log.error("Exception during put", t);
                System.exit(1);
            }

            @Override
            public void onSuccess(UserRecordResult result) {
                completed.getAndIncrement();
            }
        };
        
        // Progress updates
        Thread progress = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    long put = sequenceNumber.get();
                    double putPercent = 100.0 * put / totalRecordsToPut;
                    long done = completed.get();
                    double donePercent = 100.0 * done / totalRecordsToPut;
                    log.info(String.format(
                            "Put %d of %d so far (%.2f %%), %d have completed (%.2f %%)",
                            put, totalRecordsToPut, putPercent, done, donePercent));
                    
                    if (done == totalRecordsToPut) {
                        break;
                    }

             
                    try {
                        for (Metric m : kinesisProducer.getMetrics("UserRecordsPut", 5)) {
                         
                            if (m.getDimensions().size() == 1 && m.getSampleCount() > 0) {
                                log.info(String.format(
                                        "(Sliding 5 seconds) Avg put rate: %.2f per sec, success rate: %.2f, failure rate: %.2f, total attemped: %d",
                                        m.getSum() / 5,
                                        m.getSum() / m.getSampleCount() * 100,
                                        (m.getSampleCount() - m.getSum()) / m.getSampleCount() * 100,
                                        (long) m.getSampleCount()));
                            }
                        }
                    } catch (Exception e) {
                        log.error("Unexpected error getting metrics", e);
                        System.exit(1);
                    }
                    
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {}
                }
            }
        });
        progress.start();
        
        // Put records
        while (true) {
           
            if (sequenceNumber.get() < totalRecordsToPut) {
                if (kinesisProducer.getOutstandingRecordsCount() < outstandingLimit) {
                    ByteBuffer data = Utils.generateData(sequenceNumber.incrementAndGet(), dataSize);
                    ListenableFuture<UserRecordResult> f = kinesisProducer.addUserRecord(SampleProducerConfig.STREAM_NAME_DEFAULT,
                            timetstamp, data);
                    Futures.addCallback(f, callback, Executors.newSingleThreadExecutor());
                } else {
                    Thread.sleep(1);
                }
            } else {
                break;
            }
        }
        
       
        while (kinesisProducer.getOutstandingRecordsCount() > 0) {
            kinesisProducer.flush();
            Thread.sleep(100);
        }
        
        progress.join();
        
        for (Metric m : kinesisProducer.getMetrics("UserRecordsPerKinesisRecord")) {
            if (m.getDimensions().containsKey("ShardId")) {
                log.info(String.format(
                        "%.2f user records were aggregated into each Kinesis record on average for shard %s, for a total of %d Kinesis records.",
                        m.getMean(),
                        m.getDimensions().get("ShardId"),
                        (long) m.getSampleCount()));
            }
        }
        
        kinesisProducer.destroy();
        log.info("Finished.");
    }
}
