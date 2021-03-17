
package com.kinesis.producer;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.model.AddTagsToStreamRequest;
import software.amazon.awssdk.services.kinesis.model.AddTagsToStreamRequest.Builder;
import software.amazon.awssdk.services.kinesis.model.CreateStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse;
import software.amazon.kinesis.common.KinesisClientUtil;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.kinesis.producer.Attempt;
import com.amazonaws.services.kinesis.producer.UserRecordFailedException;
import com.amazonaws.services.kinesis.producer.UserRecordResult;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.kinesis.model.StockTrade;
import com.kinesis.sample.SampleProducer;
import com.kinesis.sample.Utils;

public class StockTradeWriter {
	
	 private static final Logger log = LoggerFactory.getLogger(StockTradeWriter.class);
	 
    private KinesisAsyncClient kinesisClient;
    private Region region = Region.US_EAST_2;
  //  private String streamName = "TestStream";
    private String streamName = "StockTradeStream";
    public StockTradeWriter(){
    	
    	//UpdateStreamProperties(kinesisClient);
        setUpProducer();
        validateStream();
    }
    private void setUpProducer(){
         kinesisClient = KinesisClientUtil
                .createKinesisAsyncClient(KinesisAsyncClient.builder().region(region));
    }

    private void validateStream(){
        try {  	
            DescribeStreamRequest describeStreamRequest =  DescribeStreamRequest.builder().streamName(streamName).build();
            DescribeStreamResponse describeStreamResponse = kinesisClient.describeStream(describeStreamRequest).get();
            
            
            
            if(!describeStreamResponse.streamDescription().streamStatus().toString().equals("ACTIVE")) {
                System.err.println("Stream " + streamName + " is not active. Please wait a few moments and try again.");
              
                System.exit(1);
            }else{
                System.out.println("Stream " + streamName + " is active");
            }
        }catch (Exception e) {
            System.err.println("Error found while describing the stream " + streamName);
            System.err.println(e);
            System.exit(1);
        }
    }
	
	public void sendStockTrade() {
		StockTrade stockTrade = new StockTradeGenerator().getRandomTrade();
		byte[] bytes = stockTrade.toJsonAsBytes();
		PutRecordRequest request = PutRecordRequest.builder().data(SdkBytes.fromByteArray(bytes))
				.partitionKey(stockTrade.getTickerSymbol()).streamName(streamName).build();

		try {
			System.out.println("Sending stock - " + stockTrade);
			PutRecordResponse response = kinesisClient.putRecord(request).get();
			System.out.println("Stock sent successfully with response -- " + response);
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
	}
        
    
    public static void main(String[] args) {
        System.out.println("Starting Producer ...");
        StockTradeWriter tradesWriter = new StockTradeWriter();
        tradesWriter.sendStockTrade();
    }

}
