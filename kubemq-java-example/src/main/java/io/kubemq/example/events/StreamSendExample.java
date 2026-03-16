package io.kubemq.example.events;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Stream Send Example
 *
 * Demonstrates sending multiple events over a persistent gRPC stream
 * for high-throughput event publishing.
 */
public class StreamSendExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-events-stream-send-client";
    private static final String CHANNEL = "java-events.stream-send";

    public static void main(String[] args) throws InterruptedException {
        // Create a client connected to the KubeMQ server
        PubSubClient client = PubSubClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID)
                .build();

        // Verify connection to the server
        ServerInfo info = client.ping();
        System.out.println("Connected to: " + info.getHost());

        // Create the events channel
        client.createEventsChannel(CHANNEL);

        int messageCount = 10;
        CountDownLatch latch = new CountDownLatch(messageCount);

        EventsSubscription subscription = EventsSubscription.builder()
                .channel(CHANNEL)
                .onReceiveEventCallback(event -> {
                    System.out.println("  Stream received: " + new String(event.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> System.err.println("Error: " + err.getMessage()))
                .build();

        // Subscribe to handle incoming events on this channel
        client.subscribeToEvents(subscription);
        // Wait for the subscriber to be ready
        Thread.sleep(500);

        // Send multiple events via the internal gRPC stream
        System.out.println("Sending " + messageCount + " events via stream...\n");
        long start = System.currentTimeMillis();

        for (int i = 1; i <= messageCount; i++) {
            EventMessage message = EventMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .channel(CHANNEL)
                    .body(("Stream event #" + i).getBytes())
                    .metadata("stream-batch")
                    .build();

            // sendEventsMessage uses the internal gRPC stream
            client.sendEventsMessage(message);
        }

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Sent " + messageCount + " events in " + elapsed + "ms");
        System.out.println("Throughput: " + (messageCount * 1000 / Math.max(elapsed, 1)) + " msg/sec\n");

        // Wait for the subscriber to receive all messages
        latch.await(5, TimeUnit.SECONDS);

        // Clean up resources
        subscription.cancel();
        client.deleteEventsChannel(CHANNEL);
        client.close();
        System.out.println("Stream send example completed.");
    }
}
