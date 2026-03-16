package io.kubemq.example.eventsstore;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.util.UUID;

/**
 * Stream Send Example (EventsStore)
 *
 * Demonstrates high-throughput streaming of events store messages.
 */
public class StreamSendExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-eventsstore-stream-send-client";
    private static final String CHANNEL = "java-eventsstore.stream-send";

    public static void main(String[] args) throws InterruptedException {
        // Create a client connected to the KubeMQ server
        PubSubClient client = PubSubClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID)
                .build();

        // Verify connection to the server
        ServerInfo info = client.ping();
        System.out.println("Connected to: " + info.getHost());
        // Create the events store channel
        client.createEventsStoreChannel(CHANNEL);

        int messageCount = 10;
        // Send multiple events store messages (high-throughput)
        System.out.println("Sending " + messageCount + " events store messages via stream...\n");

        long start = System.currentTimeMillis();
        int successCount = 0;

        for (int i = 1; i <= messageCount; i++) {
            EventStoreMessage message = EventStoreMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .channel(CHANNEL)
                    .body(("Stream store event #" + i).getBytes())
                    .metadata("stream-batch")
                    .build();

            EventSendResult result = client.sendEventsStoreMessage(message);
            if (result.isSent()) {
                successCount++;
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Sent " + successCount + "/" + messageCount + " events in " + elapsed + "ms");
        System.out.println("Throughput: " + (messageCount * 1000 / Math.max(elapsed, 1)) + " msg/sec");

        // Clean up resources
        client.deleteEventsStoreChannel(CHANNEL);
        client.close();
        System.out.println("\nStream send example completed.");
    }
}
