package com.kinesis.producer;


import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import com.amazonaws.services.kinesis.model.CreateStreamRequest;
import com.amazonaws.services.kinesis.model.DescribeStreamRequest;
import com.amazonaws.services.kinesis.model.DescribeStreamResult;
import com.amazonaws.services.kinesis.model.ListStreamsRequest;
import com.amazonaws.services.kinesis.model.ListStreamsResult;
import com.amazonaws.services.kinesis.model.PutRecordRequest;
import com.amazonaws.services.kinesis.model.PutRecordResult;
import com.amazonaws.services.kinesis.model.ResourceNotFoundException;
import com.amazonaws.services.kinesis.model.StreamDescription;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.kinesis.common.KinesisClientUtil;

public class RecordProducerSample {
	
	
	private static AmazonKinesis kinesis;
	private static KinesisAsyncClient kinesisClient;
	private static Region region = Region.US_EAST_2;
	    private static String streamName = "TestStream";
	  //  private String streamName = "StockTradeStream";
	    
	    
	//Initializing the Properties
	 private static void init() throws Exception {
	       
	        ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
	        try {
	            credentialsProvider.getCredentials();
	        } catch (Exception e) {
	            throw new AmazonClientException(
	                    "Cannot load the credentials from the credential profiles file. ",e);
	        }

	        kinesis = AmazonKinesisClientBuilder.standard()
	            .withCredentials(credentialsProvider)
	            .withRegion("us-east-2")
	            .build();
	        
	        kinesisClient = KinesisClientUtil.createKinesisAsyncClient(KinesisAsyncClient.builder().region(region));
	    }
	  
	 public static void main(String[] args) throws Exception {
	        init();

	        final Integer myStreamSize = 1;

	        // Describe the stream and check if it exists.
	        DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest().withStreamName(streamName);
	        try {
	            StreamDescription streamDescription = kinesis.describeStream(describeStreamRequest).getStreamDescription();
	            System.out.printf("Stream %s has a status of %s.\n", streamName, streamDescription.getStreamStatus());

	            if ("DELETING".equals(streamDescription.getStreamStatus())) {
	                System.out.println("Stream is being deleted. This sample will now exit.");
	                System.exit(0);
	            }

	            // Wait for the stream to become active if it is not yet ACTIVE.
	            if (!"ACTIVE".equals(streamDescription.getStreamStatus())) {
	                waitForStreamToBecomeAvailable(streamName);
	            }
	        } catch (ResourceNotFoundException ex) {
	            System.out.printf("Stream %s does not exist. Creating it now.\n", streamName);

	            // Create a stream. The number of shards determines the provisioned throughput.
	            CreateStreamRequest createStreamRequest = new CreateStreamRequest();
	            createStreamRequest.setStreamName(streamName);
	            createStreamRequest.setShardCount(myStreamSize);
	            kinesis.createStream(createStreamRequest);
	            // The stream is now being created. Wait for it to become active.
	            waitForStreamToBecomeAvailable(streamName);
	        }

	        // List all of my streams.
	        ListStreamsRequest listStreamsRequest = new ListStreamsRequest();
	        listStreamsRequest.setLimit(10);
	        ListStreamsResult listStreamsResult = kinesis.listStreams(listStreamsRequest);
	        List<String> streamNames = listStreamsResult.getStreamNames();
	        while (listStreamsResult.isHasMoreStreams()) {
	            if (streamNames.size() > 0) {
	                listStreamsRequest.setExclusiveStartStreamName(streamNames.get(streamNames.size() - 1));
	            }

	            listStreamsResult = kinesis.listStreams(listStreamsRequest);
	            streamNames.addAll(listStreamsResult.getStreamNames());
	        }
	        // Print all of my streams.
	        System.out.println("List of my streams: ");
	        for (int i = 0; i < streamNames.size(); i++) {
	            System.out.println("\t- " + streamNames.get(i));
	        }

	        System.out.printf("Putting records in stream : %s until this application is stopped...\n", streamName);
	        // Write records to the stream until this program is aborted.
	        while (true) {
	            long createTime = System.currentTimeMillis();
	            PutRecordRequest putRecordRequest = new PutRecordRequest();
	            putRecordRequest.setStreamName(streamName);
	            putRecordRequest.setData(ByteBuffer.wrap(String.format("testData-%d", createTime).getBytes()));
	            putRecordRequest.setPartitionKey(String.format("partitionKey-%d", createTime));
	            PutRecordResult putRecordResult = kinesis.putRecord(putRecordRequest);
	            System.out.printf("Successfully put record, partition key : %s, ShardID : %s, SequenceNumber : %s.\n",
	                    putRecordRequest.getPartitionKey(),
	                    putRecordResult.getShardId(),
	                    putRecordResult.getSequenceNumber());
	        }
	    }

	    private static void waitForStreamToBecomeAvailable(String streamName) throws InterruptedException {
	        System.out.printf("Waiting for %s to become ACTIVE...\n", streamName);

	        long startTime = System.currentTimeMillis();
	        long endTime = startTime + TimeUnit.MINUTES.toMillis(10);
	        while (System.currentTimeMillis() < endTime) {
	            Thread.sleep(TimeUnit.SECONDS.toMillis(20));

	            try {
	                DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest();
	                describeStreamRequest.setStreamName(streamName);
	                // ask for no more than 10 shards at a time -- this is an optional parameter
	                describeStreamRequest.setLimit(10);
	                DescribeStreamResult describeStreamResponse = kinesis.describeStream(describeStreamRequest);

	                String streamStatus = describeStreamResponse.getStreamDescription().getStreamStatus();
	                System.out.printf("\t- current state: %s\n", streamStatus);
	                if ("ACTIVE".equals(streamStatus)) {
	                    return;
	                }
	            } catch (ResourceNotFoundException ex) {
	                // ResourceNotFound means the stream doesn't exist yet,
	                // so ignore this error and just keep polling.
	            } catch (AmazonServiceException ase) {
	                throw ase;
	            }
	        }

	        throw new RuntimeException(String.format("Stream %s never became active", streamName));
	    }
}
