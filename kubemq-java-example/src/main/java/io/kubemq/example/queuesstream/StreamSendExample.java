package io.kubemq.example.queuesstream;

import io.kubemq.sdk.queues.*;
import java.util.UUID;

/**
 * StreamSendExample for Queues Stream
 */
public class StreamSendExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queuesstream-LStream-LSend-client";
    private static final String CHANNEL = "java-queuesstream.LStream-LSend";

    public static void main(String[] args) {
        // Create a client connected to the KubeMQ server
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            // Create the queue channel
            client.createQueuesChannel(CHANNEL);

            System.out.println("Sending messages via stream...\n");
            long start = System.currentTimeMillis();

            // Send messages in a loop via stream
            for (int i = 1; i <= 10; i++) {
                QueueSendResult result = client.sendQueuesMessage(QueueMessage.builder()
                        .id(UUID.randomUUID().toString()).channel(CHANNEL)
                        .body(("Stream message #" + i).getBytes()).build());
                System.out.println("  Sent #" + i + " -> " + result.getId());
            }

            long elapsed = System.currentTimeMillis() - start;
            System.out.println("\n10 messages sent in " + elapsed + "ms");

            // Clean up resources
            client.deleteQueuesChannel(CHANNEL);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
