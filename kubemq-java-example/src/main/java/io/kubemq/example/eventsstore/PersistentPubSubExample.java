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
        PubSubClient client = PubSubClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID)
                .build();

        ServerInfo info = client.ping();
        System.out.println("Connected to: " + info.getHost());
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

        client.subscribeToEventsStore(subscription);
        System.out.println("Subscribed to events store: " + CHANNEL);
        Thread.sleep(500);

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

        latch.await(5, TimeUnit.SECONDS);

        subscription.cancel();
        client.deleteEventsStoreChannel(CHANNEL);
        client.close();
        System.out.println("\nPersistent pub/sub example completed.");
    }
}
