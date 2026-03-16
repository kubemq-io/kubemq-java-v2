package io.kubemq.example.eventsstore;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Persistent Pub/Sub Example
 *
 * Demonstrates persistent pub/sub with EventsStore -- send and subscribe to stored events.
 */
public class PersistentPubSubExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-eventsstore-persistent-pubsub-client";
    private static final String CHANNEL = "java-eventsstore.persistent-pubsub";

    public static void main(String[] args) throws InterruptedException {
        // Create a client connected to the KubeMQ server
        PubSubClient client = PubSubClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID)
                .build();

        // Verify connection to the server
        ServerInfo info = client.ping();
        System.out.println("Connected to: " + info.getHost());
        // Create the persistent events store channel
        client.createEventsStoreChannel(CHANNEL);

        CountDownLatch latch = new CountDownLatch(3);

        Consumer<EventStoreMessageReceived> onReceive = event -> {
            System.out.println("Received stored event:");
            System.out.println("  ID: " + event.getId());
            System.out.println("  Sequence: " + event.getSequence());
            System.out.println("  Body: " + new String(event.getBody()));
            latch.countDown();
        };

        EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                .channel(CHANNEL)
                .eventsStoreType(EventsStoreType.StartNewOnly)
                .onReceiveEventCallback(onReceive)
                .onErrorCallback(err -> System.err.println("Error: " + err.getMessage()))
                .build();

        // Subscribe to handle incoming stored events
        client.subscribeToEventsStore(subscription);
        System.out.println("Subscribed to events store: " + CHANNEL);
        // Wait for the subscriber to be ready
        Thread.sleep(500);

        // Send persistent event messages
        for (int i = 1; i <= 3; i++) {
            Map<String, String> tags = new HashMap<>();
            tags.put("sequence", String.valueOf(i));

            EventStoreMessage message = EventStoreMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .channel(CHANNEL)
                    .metadata("Persistent event")
                    .body(("Stored event #" + i).getBytes())
                    .tags(tags)
                    .build();

            EventSendResult result = client.sendEventsStoreMessage(message);
            System.out.println("Sent event #" + i + " (sent=" + result.isSent() + ")");
        }

        // Wait for the subscriber to receive all messages
        latch.await(5, TimeUnit.SECONDS);

        // Clean up resources
        subscription.cancel();
        client.deleteEventsStoreChannel(CHANNEL);
        client.close();
        System.out.println("\nPersistent pub/sub example completed.");
    }
}

// Expected output:
// Connected to: <host>
// Subscribed to events store: java-eventsstore.persistent-pubsub
// Sent event #1 (sent=true)
// Sent event #2 (sent=true)
// Sent event #3 (sent=true)
// Received stored event:
//   ID: <message-id>
//   Sequence: <sequence>
//   Body: Stored event #1
// Received stored event:
//   ID: <message-id>
//   Sequence: <sequence>
//   Body: Stored event #2
// Received stored event:
//   ID: <message-id>
//   Sequence: <sequence>
//   Body: Stored event #3
//
// Persistent pub/sub example completed.
