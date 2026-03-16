package io.kubemq.example.events;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Basic Pub/Sub Example
 *
 * Demonstrates publishing and subscribing to non-persistent events.
 */
public class BasicPubSubExample {

    // TODO: Replace with your KubeMQ server address
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-events-basic-pubsub-client";
    private static final String CHANNEL = "java-events.basic-pubsub";

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

        CountDownLatch latch = new CountDownLatch(3);

        Consumer<EventMessageReceived> onReceive = event -> {
            System.out.println("Received event:");
            System.out.println("  ID: " + event.getId());
            System.out.println("  Channel: " + event.getChannel());
            System.out.println("  Body: " + new String(event.getBody()));
            System.out.println("  Tags: " + event.getTags());
            latch.countDown();
        };

        Consumer<io.kubemq.sdk.exception.KubeMQException> onError = error -> {
            System.err.println("Error: " + error.getMessage());
        };

        EventsSubscription subscription = EventsSubscription.builder()
                .channel(CHANNEL)
                .onReceiveEventCallback(onReceive)
                .onErrorCallback(onError)
                .build();

        // Subscribe to handle incoming events on this channel
        client.subscribeToEvents(subscription);
        System.out.println("Subscribed to events on: " + CHANNEL);

        // Wait for the subscriber to be ready
        Thread.sleep(500);

        // Send event messages
        for (int i = 1; i <= 3; i++) {
            Map<String, String> tags = new HashMap<>();
            tags.put("sequence", String.valueOf(i));

            EventMessage message = EventMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .channel(CHANNEL)
                    .metadata("Event metadata")
                    .body(("Hello KubeMQ event #" + i).getBytes())
                    .tags(tags)
                    .build();

            client.sendEventsMessage(message);
            System.out.println("Sent event #" + i);
        }

        // Wait for the subscriber to receive all messages
        latch.await(5, TimeUnit.SECONDS);

        // Clean up resources
        subscription.cancel();
        client.deleteEventsChannel(CHANNEL);
        client.close();
        System.out.println("\nBasic pub/sub example completed.");
    }
}

// Expected output:
// Connected to: <host>
// Subscribed to events on: java-events.basic-pubsub
// Sent event #1
// Sent event #2
// Sent event #3
// Received event:
//   ID: <message-id>
//   Channel: java-events.basic-pubsub
//   Body: Hello KubeMQ event #1
//   Tags: {sequence=1}
// Received event:
//   ID: <message-id>
//   Channel: java-events.basic-pubsub
//   Body: Hello KubeMQ event #2
//   Tags: {sequence=2}
// Received event:
//   ID: <message-id>
//   Channel: java-events.basic-pubsub
//   Body: Hello KubeMQ event #3
//   Tags: {sequence=3}
//
// Basic pub/sub example completed.
