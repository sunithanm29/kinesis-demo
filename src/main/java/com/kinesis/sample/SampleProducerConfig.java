package com.kinesis.sample;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.kinesis.producer.KinesisProducerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import java.util.Set;

public class SampleProducerConfig {
    
    private static final Logger log = LoggerFactory.getLogger(SampleProducerConfig.class);
    /**
     * Change these to try larger or smaller records.
     */
    private static final int DATA_SIZE_DEFAULT = 128;

    /**
     * Put records for this number of seconds before exiting.
     */
    private static final int SECONDS_TO_RUN_DEFAULT = 5;


    private static final int RECORDS_PER_SECOND_DEFAULT = 2000;

    /**
     * Change this to your stream name.
     */
    public static final String STREAM_NAME_DEFAULT = "TestStream";

    /**
     * Change this to the region you are using.
     */
    public static final String REGION_DEFAULT = "us-east-2";

    @NotBlank(message = "KPL Sample region should not be null or blank" )
    private final String region;
    @Positive(message = "KPL Sample connections should not be less than 1")
    private final int connections;
    @Positive(message = "KPL Sample requestTimeout should not be less than 1")
    private final int requestTimeout;
    @Positive(message = "KPL Sample bufferTime should not be less than 1")
    private final int bufferTime;
    @NotBlank(message = "KPL Sample threadingModel should be one of PER_REQUEST or POOLED")
    private final String threadingModel;
    @NotBlank(message = "KPL Sample streamName should not be null or blank" )
    private final String streamName;
    @Positive(message = "KPL Sample secondsToRun should not be less than 1")
    private final int secondsToRun;
    @Positive(message = "KPL Sample recordsPerSecond should not be less than 1")
    private final int recordsPerSecond;
    @Positive(message = "KPL Sample dataSize should not be less than 1")
    private final int dataSize;
    @PositiveOrZero(message = "KPL Sample threadPoolSize should not be less than 0")
    private final int threadPoolSize;
    private boolean aggregationEnabled;
    @Min(value = 1, message = "KPL Sample aggregationMaxCount should not be less than 1")
    private long aggregationMaxCount;
    @Min(value = 2, message = "KPL Sample aggregationMaxSize should not be less than 2")
    private long aggregationMaxSize;
    @Min(value = 0, message = "KPL Sample requestTimeoutInMillis should not be less than 0")
    private long requestTimeoutInMillis;

    public static String getArgIfPresent(final String[] args, final int index, final String defaultValue) {
        return args.length > index ? args[index] : defaultValue;
    }

    public static int getIntArgIfPresent(final String[] args, final int index, final String defaultValue) {
        return Integer.parseInt(getArgIfPresent(args, index, defaultValue));
    }

    public static long getLongArgIfPresent(final String[] args, final int index, final String defaultValue) {
        return Long.parseLong(getArgIfPresent(args, index, defaultValue));
    }

    public static boolean getBooleanArgIfPresent(final String[] args, final int index, final String defaultValue) {
        return Boolean.parseBoolean(getArgIfPresent(args, index, defaultValue));
    }

    /**
     * Parses the commandline input to produce a Config object
     **  @param args  The command line args for the Sample Producer. It takes 13 optional position parameters:
     * 1. The stream name to use, test is default.
     * 2. The region name to use, us-west-1 in default.
     * 3. The duration of the test in seconds, 5 is the default.
     * 4. The number of records per second to send, 2000 is the default.
     * 5. The payload size of each record being sent in bytes, 128 is the default.
     * 6. The max number of connections to configure the KPL with, 1 is the default.
     * 7. The requestTimeout in milliseconds to configure the KPL with, 60000 is the default.
     * 8. The bufferTime in milliseconds to configure the KPL with, 2000 is the default.
     * 9. The threading model to configure the KPL with, PER_REQUEST is the default.
     * 10. The threadPoolSize to configure the KPL with, 0 is the default.
     * 11. The aggregationEnabled to configure the KPL with, true is the default.
     * 12. The aggregationMaxCount to configure the KPL with, 4294967295 is the default.
     * 13. The aggregationMaxSize to configure the KPL with, 51200 is the default.
     */
    public SampleProducerConfig(String[] args) {
        int argIndex = 0;
        streamName = getArgIfPresent(args, argIndex++, STREAM_NAME_DEFAULT);
        region = getArgIfPresent(args, argIndex++, REGION_DEFAULT);
        secondsToRun = getIntArgIfPresent(args, argIndex++, String.valueOf(SECONDS_TO_RUN_DEFAULT));
        recordsPerSecond = getIntArgIfPresent(args, argIndex++, String.valueOf(RECORDS_PER_SECOND_DEFAULT));
        dataSize = getIntArgIfPresent(args, argIndex++, String.valueOf(DATA_SIZE_DEFAULT));
        connections = getIntArgIfPresent(args, argIndex++, String.valueOf(1));
        requestTimeout = getIntArgIfPresent(args, argIndex++, String.valueOf(60000));
        bufferTime = getIntArgIfPresent(args, argIndex++, String.valueOf(2000));
        threadingModel = getArgIfPresent(args, argIndex++, KinesisProducerConfiguration.ThreadingModel.PER_REQUEST.name());
        threadPoolSize = getIntArgIfPresent(args, argIndex++, String.valueOf(0));
        aggregationEnabled = getBooleanArgIfPresent(args, argIndex++, "true");
        aggregationMaxCount = getLongArgIfPresent(args, argIndex++, "4294967295");
        aggregationMaxSize = getLongArgIfPresent(args, argIndex++, "51200");
        // Value of 0 for requestTimeoutInMillis means its disabled and this is disabled by default
        requestTimeoutInMillis = getLongArgIfPresent(args, argIndex++, "0");

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();

        Set<ConstraintViolation<SampleProducerConfig>> violations = validator.validate(this);

        for (ConstraintViolation<SampleProducerConfig> violation : violations) {
            log.error(violation.getMessage());
        }

        if(!threadingModel.equals(KinesisProducerConfiguration.ThreadingModel.PER_REQUEST.name()) &&
                !threadingModel.equals(KinesisProducerConfiguration.ThreadingModel.POOLED.name())){
            log.error("KPL Sample threadingModel needs to be one of [PER_REQUEST | POOLED]");
            System.exit(1);
        }

        if(!violations.isEmpty()){
            System.exit(1);
        }
    }

    public String getRegion() {
        return region;
    }

    public int getConnections() {
        return connections;
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }

    public int getBufferTime() {
        return bufferTime;
    }

    public String getThreadingModel() {
        return threadingModel;
    }

    public String getStreamName() {
        return streamName;
    }

    public int getSecondsToRun() {
        return secondsToRun;
    }

    public int getRecordsPerSecond() {
        return recordsPerSecond;
    }

    public int getDataSize() {
        return dataSize;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public boolean isAggregationEnabled() {
        return aggregationEnabled;
    }

    public long getAggregationMaxCount() {
        return aggregationMaxCount;
    }

    public long getAggregationMaxSize() {
        return aggregationMaxSize;
    }

    public long getRequestTimeoutInMillis() {
        return requestTimeoutInMillis;
    }

    public KinesisProducerConfiguration transformToKinesisProducerConfiguration(){
        // There are many configurable parameters in the KPL. See the javadocs
        // on each each set method for details.
        KinesisProducerConfiguration config = new KinesisProducerConfiguration();

        config.setRegion(this.getRegion());
        config.setCredentialsProvider(new DefaultAWSCredentialsProviderChain());
        config.setMaxConnections(this.getConnections());
        config.setRequestTimeout(this.getRequestTimeout());
        config.setRecordMaxBufferedTime(this.getBufferTime());
        config.setThreadingModel(this.getThreadingModel());
        config.setThreadPoolSize(this.getThreadPoolSize());
        config.setAggregationEnabled(this.isAggregationEnabled());
        config.setAggregationMaxCount(this.getAggregationMaxCount());
        config.setAggregationMaxSize(this.getAggregationMaxSize());
        config.setUserRecordTimeoutInMillis(this.getRequestTimeoutInMillis());

        return config;
    }
}
