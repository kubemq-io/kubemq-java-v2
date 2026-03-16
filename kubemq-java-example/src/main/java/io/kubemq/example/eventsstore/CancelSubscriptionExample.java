package io.kubemq.example.eventsstore;

import io.kubemq.sdk.pubsub.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cancel Subscription Example (EventsStore)
 *
 * Demonstrates cancelling EventsStore subscriptions.
 */
public class CancelSubscriptionExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-eventsstore-cancel-subscription-client";
    private static final String CHANNEL = "java-eventsstore.cancel-subscription";

    public static void main(String[] args) throws InterruptedException {
        // Create a client connected to the KubeMQ server
        PubSubClient client = PubSubClient.builder().address(ADDRESS).clientId(CLIENT_ID).build();
        client.ping();
        // Create the events store channel
        client.createEventsStoreChannel(CHANNEL);

        // Send messages to the store before subscribing
        for (int i = 1; i <= 5; i++) {
            client.sendEventsStoreMessage(EventStoreMessage.builder()
                    .id("store-" + i).channel(CHANNEL)
                    .body(("Stored message " + i).getBytes()).build());
        }
        System.out.println("Sent 5 messages to store.\n");

        AtomicInteger received = new AtomicInteger(0);
        CountDownLatch cancelPoint = new CountDownLatch(1);

        EventsStoreSubscription sub = EventsStoreSubscription.builder()
                .channel(CHANNEL)
                .eventsStoreType(EventsStoreType.StartFromFirst)
                .onReceiveEventCallback(e -> {
                    int n = received.incrementAndGet();
                    System.out.println("  [" + n + "] " + new String(e.getBody()));
                    if (n >= 3) cancelPoint.countDown();
                })
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        // Subscribe to handle incoming stored events
        System.out.println("Subscribing (will cancel after 3 messages)...\n");
        client.subscribeToEventsStore(sub);

        // Wait until we receive 3 messages, then cancel
        cancelPoint.await(5, TimeUnit.SECONDS);
        Thread.sleep(100);

        // Cancel the subscription (stop receiving)
        sub.cancel();
        System.out.println("\nSubscription cancelled. Received " + received.get() + " of 5 messages.");

        // Clean up resources
        client.deleteEventsStoreChannel(CHANNEL);
        client.close();
    }
}
