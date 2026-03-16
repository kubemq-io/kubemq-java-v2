package io.kubemq.example.queues;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.*;
import java.util.*;

public class BatchSendExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queues-batch-send-client";
    private static final String CHANNEL = "java-queues.batch-send";

    public static void main(String[] args) {
        // Create a queues client connected to the KubeMQ server
        QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build();
        // Verify connection to the server
        ServerInfo info = client.ping();
        System.out.println("Connected to: " + info.getHost());
        // Create the queue channel
        client.createQueuesChannel(CHANNEL);

        // Send a batch of messages
        int batchSize = 10;
        int success = 0;
        long start = System.currentTimeMillis();

        for (int i = 1; i <= batchSize; i++) {
            Map<String, String> tags = new HashMap<>();
            tags.put("batch_id", UUID.randomUUID().toString());
            tags.put("sequence", String.valueOf(i));

            QueueMessage msg = QueueMessage.builder()
                    .id(UUID.randomUUID().toString()).channel(CHANNEL)
                    .body(("Batch message #" + i).getBytes()).tags(tags).build();

            QueueSendResult result = client.sendQueuesMessage(msg);
            if (!result.isError()) { success++; }
        }

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Batch complete: " + success + "/" + batchSize + " in " + elapsed + "ms");
        System.out.println("Throughput: " + (batchSize * 1000 / Math.max(elapsed, 1)) + " msg/sec");

        // Clean up resources
        client.deleteQueuesChannel(CHANNEL);
        client.close();
    }
}
