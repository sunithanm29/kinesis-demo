

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.KinesisException;
import software.amazon.awssdk.services.kinesis.model.ListShardsRequest;
import software.amazon.awssdk.services.kinesis.model.ListShardsResponse;

public class ListShards {
    public static void main(String[] args) {

        String streamname =  "StockTradeStream";

        Region region = Region.US_EAST_2;
        KinesisClient kinesisClient = KinesisClient.builder()
                    .region(region)
                    .build();

        listKinShards(kinesisClient, streamname);
        kinesisClient.close();
        }

      // snippet-start:[kinesis.java2.ListShards.main]
        public static void listKinShards(KinesisClient kinesisClient, String name) {

        try {
       ListShardsRequest request = ListShardsRequest.builder()
                .streamName(name)
                .build();

            ListShardsResponse response = kinesisClient.listShards(request);
            System.out.println(request.streamName() + " has " + response.shards());

        } catch (KinesisException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        System.out.println("Done");
    }}
