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

        // Subscribe to handle incoming events
        System.out.println("1. Starting subscription...");
        client.subscribeToEvents(subscription);
        Thread.sleep(300);

        // Send messages while subscribed
        System.out.println("2. Sending messages while subscribed...");
        for (int i = 1; i <= 3; i++) {
            client.sendEventsMessage(EventMessage.builder()
                    .id("msg-" + i).channel(CHANNEL)
                    .body(("Message " + i).getBytes()).build());
            Thread.sleep(200);
        }

        // Cancel the subscription (stop receiving new events)
        System.out.println("\n3. Cancelling subscription...");
        active.set(false);
        subscription.cancel();
        System.out.println("   Subscription cancelled.");

        // Send more messages after cancel (subscriber will not receive them)
        System.out.println("4. Sending more messages after cancel...");
        for (int i = 4; i <= 6; i++) {
            client.sendEventsMessage(EventMessage.builder()
                    .id("msg-" + i).channel(CHANNEL)
                    .body(("Message " + i).getBytes()).build());
        }

        Thread.sleep(500);
        System.out.println("5. Received " + receivedCount.get() + " messages (before cancel).");

        // Clean up resources
        client.deleteEventsChannel(CHANNEL);
        client.close();
        System.out.println("\nCancel subscription example completed.");
    }
}
