package io.kubemq.example.eventsstore;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * StartNewOnly Example
 *
 * Demonstrates subscribing with StartNewOnly which ignores all historical messages.
 */
public class StartNewOnlyExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-eventsstore-start-new-only-client";
    private static final String CHANNEL = "java-eventsstore.start-new-only";

    public static void main(String[] args) throws InterruptedException {
        // Create a client connected to the KubeMQ server
        PubSubClient client = PubSubClient.builder().address(ADDRESS).clientId(CLIENT_ID).build();
        client.ping();
        // Create the events store channel
        client.createEventsStoreChannel(CHANNEL);

        // Send historical messages before subscribing (will be ignored)
        for (int i = 1; i <= 3; i++) {
            client.sendEventsStoreMessage(EventStoreMessage.builder()
                    .id("hist-" + i).channel(CHANNEL)
                    .body(("Historical #" + i).getBytes()).build());
        }
        System.out.println("Sent 3 historical messages (will be ignored).\n");

        AtomicInteger received = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2);

        EventsStoreSubscription sub = EventsStoreSubscription.builder()
                .channel(CHANNEL)
                .eventsStoreType(EventsStoreType.StartNewOnly)
                .onReceiveEventCallback(e -> {
                    received.incrementAndGet();
                    System.out.println("  Received: " + new String(e.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        // Subscribe with StartNewOnly (ignore all historical messages)
        client.subscribeToEventsStore(sub);
        System.out.println("Subscribed with StartNewOnly.");
        Thread.sleep(500);

        // Send new messages after subscribing
        for (int i = 1; i <= 2; i++) {
            client.sendEventsStoreMessage(EventStoreMessage.builder()
                    .id("new-" + i).channel(CHANNEL)
                    .body(("New message #" + i).getBytes()).build());
        }

        latch.await(5, TimeUnit.SECONDS);
        System.out.println("\nReceived " + received.get() + " new messages (historical ignored).");

        // Clean up resources
        sub.cancel();
        client.deleteEventsStoreChannel(CHANNEL);
        client.close();
    }
}
