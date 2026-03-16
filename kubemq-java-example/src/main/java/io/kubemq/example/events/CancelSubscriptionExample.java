package io.kubemq.example.events;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cancel Subscription Example for Events
 *
 * Demonstrates how to cancel and manage event subscriptions.
 */
public class CancelSubscriptionExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-events-cancel-subscription-client";
    private static final String CHANNEL = "java-events.cancel-subscription";

    public static void main(String[] args) throws InterruptedException {
        PubSubClient client = PubSubClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID)
                .build();

        ServerInfo info = client.ping();
        System.out.println("Connected to: " + info.getHost());
        client.createEventsChannel(CHANNEL);

        AtomicInteger receivedCount = new AtomicInteger(0);
        AtomicBoolean active = new AtomicBoolean(true);

        EventsSubscription subscription = EventsSubscription.builder()
                .channel(CHANNEL)
                .onReceiveEventCallback(event -> {
                    if (active.get()) {
                        int count = receivedCount.incrementAndGet();
                        System.out.println("  [" + count + "] Received: " + new String(event.getBody()));
                    }
                })
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        System.out.println("1. Starting subscription...");
        client.subscribeToEvents(subscription);
        Thread.sleep(300);

        System.out.println("2. Sending messages while subscribed...");
        for (int i = 1; i <= 3; i++) {
            client.sendEventsMessage(EventMessage.builder()
                    .id("msg-" + i).channel(CHANNEL)
                    .body(("Message " + i).getBytes()).build());
            Thread.sleep(200);
        }

        System.out.println("\n3. Cancelling subscription...");
        active.set(false);
        subscription.cancel();
        System.out.println("   Subscription cancelled.");

        System.out.println("4. Sending more messages after cancel...");
        for (int i = 4; i <= 6; i++) {
            client.sendEventsMessage(EventMessage.builder()
                    .id("msg-" + i).channel(CHANNEL)
                    .body(("Message " + i).getBytes()).build());
        }

        Thread.sleep(500);
        System.out.println("5. Received " + receivedCount.get() + " messages (before cancel).");

        client.deleteEventsChannel(CHANNEL);
        client.close();
        System.out.println("\nCancel subscription example completed.");
    }
}
