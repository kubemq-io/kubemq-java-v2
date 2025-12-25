package io.kubemq.example.pubsub;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Subscription Cancel Example
 *
 * This example demonstrates how to properly cancel and manage subscriptions
 * in KubeMQ PubSub. Proper subscription lifecycle management is essential
 * for resource cleanup and graceful shutdown.
 *
 * Subscription Lifecycle:
 * 1. Create subscription with callbacks
 * 2. Subscribe to channel (starts receiving)
 * 3. Process messages...
 * 4. Cancel subscription (stops receiving)
 * 5. Optionally re-subscribe
 *
 * Why Cancel Subscriptions:
 * - Release server resources
 * - Stop message delivery gracefully
 * - Implement pause/resume patterns
 * - Handle application shutdown
 * - Switch between channels
 *
 * @see io.kubemq.sdk.pubsub.EventsSubscription#cancel()
 * @see io.kubemq.sdk.pubsub.EventsStoreSubscription#cancel()
 */
public class SubscriptionCancelExample {

    private final PubSubClient pubSubClient;
    private final String eventsChannel = "cancel-events-channel";
    private final String eventsStoreChannel = "cancel-store-channel";
    private final String address = "localhost:50000";
    private final String clientId = "cancel-example-client";

    /**
     * Initializes the PubSubClient.
     */
    public SubscriptionCancelExample() {
        pubSubClient = PubSubClient.builder()
                .address(address)
                .clientId(clientId)
                .logLevel(KubeMQClient.Level.INFO)
                .build();

        ServerInfo pingResult = pubSubClient.ping();
        System.out.println("Connected to: " + pingResult.getHost() + " v" + pingResult.getVersion());

        pubSubClient.createEventsChannel(eventsChannel);
        pubSubClient.createEventsStoreChannel(eventsStoreChannel);
    }

    /**
     * Demonstrates basic subscription cancellation.
     */
    public void basicCancelExample() {
        System.out.println("\n=== Basic Subscription Cancel ===\n");

        AtomicInteger receivedCount = new AtomicInteger(0);
        AtomicBoolean subscriptionActive = new AtomicBoolean(true);

        Consumer<EventMessageReceived> handler = event -> {
            if (subscriptionActive.get()) {
                int count = receivedCount.incrementAndGet();
                System.out.println("  [" + count + "] Received: " + new String(event.getBody()));
            }
        };

        EventsSubscription subscription = EventsSubscription.builder()
                .channel(eventsChannel)
                .onReceiveEventCallback(handler)
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        // Subscribe
        System.out.println("1. Starting subscription...");
        pubSubClient.subscribeToEvents(subscription);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Send some messages
        System.out.println("2. Sending messages while subscribed...\n");
        for (int i = 1; i <= 3; i++) {
            pubSubClient.sendEventsMessage(EventMessage.builder()
                    .id("msg-" + i)
                    .channel(eventsChannel)
                    .body(("Message " + i).getBytes())
                    .build());
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("\n3. Cancelling subscription...");
        subscriptionActive.set(false);
        subscription.cancel();
        System.out.println("   Subscription cancelled.");

        // Send more messages (should not be received)
        System.out.println("\n4. Sending more messages after cancel...");
        for (int i = 4; i <= 6; i++) {
            pubSubClient.sendEventsMessage(EventMessage.builder()
                    .id("msg-" + i)
                    .channel(eventsChannel)
                    .body(("Message " + i + " (should not be received)").getBytes())
                    .build());
        }

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("   Messages sent but subscription is cancelled.");
        System.out.println("\n5. Result: Received " + receivedCount.get() + " messages (before cancel).\n");
    }

    /**
     * Demonstrates cancel and re-subscribe pattern.
     */
    public void cancelAndResubscribeExample() {
        System.out.println("=== Cancel and Re-subscribe Pattern ===\n");

        AtomicInteger phase1Count = new AtomicInteger(0);
        AtomicInteger phase2Count = new AtomicInteger(0);

        // Phase 1 subscription
        EventsSubscription phase1Sub = EventsSubscription.builder()
                .channel(eventsChannel)
                .onReceiveEventCallback(e -> {
                    phase1Count.incrementAndGet();
                    System.out.println("  [Phase 1] " + new String(e.getBody()));
                })
                .onErrorCallback(err -> {})
                .build();

        System.out.println("Phase 1: Initial subscription");
        pubSubClient.subscribeToEvents(phase1Sub);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Send messages in phase 1
        for (int i = 1; i <= 2; i++) {
            pubSubClient.sendEventsMessage(EventMessage.builder()
                    .id("p1-" + i)
                    .channel(eventsChannel)
                    .body(("Phase 1 message " + i).getBytes())
                    .build());
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Cancel phase 1
        System.out.println("\nCancelling Phase 1 subscription...\n");
        phase1Sub.cancel();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Phase 2 subscription (new instance)
        EventsSubscription phase2Sub = EventsSubscription.builder()
                .channel(eventsChannel)
                .onReceiveEventCallback(e -> {
                    phase2Count.incrementAndGet();
                    System.out.println("  [Phase 2] " + new String(e.getBody()));
                })
                .onErrorCallback(err -> {})
                .build();

        System.out.println("Phase 2: New subscription");
        pubSubClient.subscribeToEvents(phase2Sub);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Send messages in phase 2
        for (int i = 1; i <= 2; i++) {
            pubSubClient.sendEventsMessage(EventMessage.builder()
                    .id("p2-" + i)
                    .channel(eventsChannel)
                    .body(("Phase 2 message " + i).getBytes())
                    .build());
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        phase2Sub.cancel();

        System.out.println("\nResults:");
        System.out.println("  Phase 1 received: " + phase1Count.get() + " messages");
        System.out.println("  Phase 2 received: " + phase2Count.get() + " messages\n");
    }

    /**
     * Demonstrates timed subscription with automatic cancel.
     */
    public void timedSubscriptionExample() {
        System.out.println("=== Timed Subscription (Auto-Cancel) ===\n");

        int durationSeconds = 3;
        AtomicInteger receivedCount = new AtomicInteger(0);

        EventsSubscription subscription = EventsSubscription.builder()
                .channel(eventsChannel)
                .onReceiveEventCallback(e -> {
                    receivedCount.incrementAndGet();
                    System.out.println("  Received: " + new String(e.getBody()));
                })
                .onErrorCallback(err -> {})
                .build();

        System.out.println("Starting " + durationSeconds + "-second subscription window...\n");
        pubSubClient.subscribeToEvents(subscription);

        // Send messages periodically
        Thread sender = new Thread(() -> {
            int msgNum = 0;
            while (!Thread.currentThread().isInterrupted()) {
                msgNum++;
                pubSubClient.sendEventsMessage(EventMessage.builder()
                        .id("timed-" + msgNum)
                        .channel(eventsChannel)
                        .body(("Timed message " + msgNum).getBytes())
                        .build());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        sender.start();

        // Auto-cancel after duration
        try {
            Thread.sleep(durationSeconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        sender.interrupt();
        subscription.cancel();

        System.out.println("\nSubscription auto-cancelled after " + durationSeconds + " seconds.");
        System.out.println("Received " + receivedCount.get() + " messages during window.\n");
    }

    /**
     * Demonstrates EventsStore subscription cancel.
     */
    public void eventsStoreCancelExample() {
        System.out.println("=== EventsStore Subscription Cancel ===\n");

        // Send some historical messages
        for (int i = 1; i <= 5; i++) {
            pubSubClient.sendEventsStoreMessage(EventStoreMessage.builder()
                    .id("store-" + i)
                    .channel(eventsStoreChannel)
                    .body(("Stored message " + i).getBytes())
                    .build());
        }
        System.out.println("Sent 5 messages to store.\n");

        AtomicInteger receivedCount = new AtomicInteger(0);
        CountDownLatch cancelPoint = new CountDownLatch(1);

        EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                .channel(eventsStoreChannel)
                .eventsStoreType(EventsStoreType.StartFromFirst)
                .onReceiveEventCallback(e -> {
                    int count = receivedCount.incrementAndGet();
                    System.out.println("  [" + count + "] " + new String(e.getBody()));

                    // Cancel after 3 messages
                    if (count >= 3) {
                        cancelPoint.countDown();
                    }
                })
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        System.out.println("Subscribing (will cancel after 3 messages)...\n");
        pubSubClient.subscribeToEventsStore(subscription);

        try {
            cancelPoint.await(5, TimeUnit.SECONDS);
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        subscription.cancel();
        System.out.println("\nSubscription cancelled.");
        System.out.println("Received " + receivedCount.get() + " of 5 messages before cancel.\n");
    }

    /**
     * Demonstrates graceful shutdown pattern.
     */
    public void gracefulShutdownExample() {
        System.out.println("=== Graceful Shutdown Pattern ===\n");

        System.out.println("Code pattern for graceful shutdown:\n");

        System.out.println("```java");
        System.out.println("// Store subscription references");
        System.out.println("List<EventsSubscription> activeSubscriptions = new ArrayList<>();");
        System.out.println();
        System.out.println("// Create and track subscription");
        System.out.println("EventsSubscription sub = EventsSubscription.builder()");
        System.out.println("    .channel(channel)");
        System.out.println("    .onReceiveEventCallback(handler)");
        System.out.println("    .build();");
        System.out.println("pubSubClient.subscribeToEvents(sub);");
        System.out.println("activeSubscriptions.add(sub);");
        System.out.println();
        System.out.println("// On shutdown:");
        System.out.println("Runtime.getRuntime().addShutdownHook(new Thread(() -> {");
        System.out.println("    for (EventsSubscription s : activeSubscriptions) {");
        System.out.println("        s.cancel();");
        System.out.println("    }");
        System.out.println("    pubSubClient.close();");
        System.out.println("}));");
        System.out.println("```\n");

        System.out.println("Key points:");
        System.out.println("  1. Track all active subscriptions");
        System.out.println("  2. Cancel subscriptions before closing client");
        System.out.println("  3. Use try-with-resources for client when possible");
        System.out.println("  4. Handle InterruptedException properly\n");
    }

    /**
     * Demonstrates switching between channels.
     */
    public void switchChannelExample() {
        System.out.println("=== Switching Between Channels ===\n");

        String channel1 = eventsChannel;
        String channel2 = eventsChannel + "-alt";
        pubSubClient.createEventsChannel(channel2);

        AtomicInteger channel1Count = new AtomicInteger(0);
        AtomicInteger channel2Count = new AtomicInteger(0);

        // Subscribe to channel 1
        EventsSubscription sub1 = EventsSubscription.builder()
                .channel(channel1)
                .onReceiveEventCallback(e -> {
                    channel1Count.incrementAndGet();
                    System.out.println("  [Channel 1] " + new String(e.getBody()));
                })
                .onErrorCallback(err -> {})
                .build();

        System.out.println("Subscribed to Channel 1");
        pubSubClient.subscribeToEvents(sub1);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Send to channel 1
        pubSubClient.sendEventsMessage(EventMessage.builder()
                .id("ch1-msg")
                .channel(channel1)
                .body("Message on Channel 1".getBytes())
                .build());

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Switch to channel 2
        System.out.println("\nSwitching to Channel 2...\n");
        sub1.cancel();

        EventsSubscription sub2 = EventsSubscription.builder()
                .channel(channel2)
                .onReceiveEventCallback(e -> {
                    channel2Count.incrementAndGet();
                    System.out.println("  [Channel 2] " + new String(e.getBody()));
                })
                .onErrorCallback(err -> {})
                .build();

        pubSubClient.subscribeToEvents(sub2);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Send to channel 2
        pubSubClient.sendEventsMessage(EventMessage.builder()
                .id("ch2-msg")
                .channel(channel2)
                .body("Message on Channel 2".getBytes())
                .build());

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        sub2.cancel();

        System.out.println("\nResults:");
        System.out.println("  Channel 1: " + channel1Count.get() + " messages");
        System.out.println("  Channel 2: " + channel2Count.get() + " messages\n");

        pubSubClient.deleteEventsChannel(channel2);
    }

    /**
     * Cleans up resources.
     */
    public void cleanup() {
        try {
            pubSubClient.deleteEventsChannel(eventsChannel);
            pubSubClient.deleteEventsStoreChannel(eventsStoreChannel);
            System.out.println("Cleaned up channels");
        } catch (Exception e) {
            // Ignore
        }
        pubSubClient.close();
    }

    /**
     * Main method demonstrating subscription cancellation patterns.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║         Subscription Cancel Examples                         ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        SubscriptionCancelExample example = new SubscriptionCancelExample();

        try {
            // Basic cancel
            example.basicCancelExample();

            // Cancel and re-subscribe
            example.cancelAndResubscribeExample();

            // Timed subscription
            example.timedSubscriptionExample();

            // EventsStore cancel
            example.eventsStoreCancelExample();

            // Switch channels
            example.switchChannelExample();

            // Graceful shutdown pattern
            example.gracefulShutdownExample();

        } finally {
            example.cleanup();
        }

        System.out.println("Subscription cancel examples completed.");
    }
}
