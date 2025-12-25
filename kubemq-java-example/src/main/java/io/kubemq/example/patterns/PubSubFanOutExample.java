package io.kubemq.example.patterns;

import io.kubemq.sdk.pubsub.*;
import io.kubemq.sdk.common.ServerInfo;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PubSub Fan-Out Pattern Example
 *
 * This example demonstrates the Fan-Out pattern using KubeMQ PubSub.
 * Fan-Out distributes a single message to multiple subscribers simultaneously.
 *
 * Fan-Out Characteristics:
 * - One publisher, many subscribers
 * - All subscribers receive every message
 * - Parallel processing of the same data
 * - Decoupling between producer and consumers
 *
 * Implementation Options:
 * 1. Events: Real-time, fire-and-forget
 * 2. EventsStore: Persistent, supports replay
 * 3. Mixed: Different subscribers use different modes
 *
 * Use Cases:
 * - Notification systems (email, SMS, push)
 * - Event broadcasting
 * - Data replication across services
 * - Logging and monitoring
 * - Cache invalidation
 * - Real-time analytics
 *
 * Pattern Variations:
 * - Simple Fan-Out: All receive same message
 * - Filtered Fan-Out: Subscribers filter by content
 * - Hybrid Fan-Out: Mix of broadcast and load-balanced groups
 *
 * @see io.kubemq.sdk.pubsub.EventMessage
 * @see io.kubemq.sdk.pubsub.EventsSubscription
 */
public class PubSubFanOutExample {

    private final PubSubClient pubSubClient;
    private final String address = "localhost:50000";
    private final String clientId = "fanout-client";

    /**
     * Initializes the PubSubClient.
     */
    public PubSubFanOutExample() {
        pubSubClient = PubSubClient.builder()
                .address(address)
                .clientId(clientId)
                .build();

        ServerInfo pingResult = pubSubClient.ping();
        System.out.println("Connected to: " + pingResult.getHost() + " v" + pingResult.getVersion());
    }

    /**
     * Demonstrates basic fan-out to multiple subscribers.
     */
    public void basicFanOutExample() {
        System.out.println("\n=== Basic Fan-Out Pattern ===\n");

        String channel = "broadcast-channel";
        pubSubClient.createEventsChannel(channel);

        int numSubscribers = 4;
        int numMessages = 3;

        AtomicInteger[] receiveCounts = new AtomicInteger[numSubscribers];
        CountDownLatch latch = new CountDownLatch(numSubscribers * numMessages);

        // Create multiple subscribers
        EventsSubscription[] subscriptions = new EventsSubscription[numSubscribers];

        for (int i = 0; i < numSubscribers; i++) {
            final int subscriberId = i + 1;
            receiveCounts[i] = new AtomicInteger(0);
            final AtomicInteger counter = receiveCounts[i];

            subscriptions[i] = EventsSubscription.builder()
                    .channel(channel)
                    .onReceiveEventCallback(event -> {
                        counter.incrementAndGet();
                        System.out.println("  Subscriber " + subscriberId + ": " + new String(event.getBody()));
                        latch.countDown();
                    })
                    .onErrorCallback(err -> System.err.println("Subscriber " + subscriberId + " error: " + err))
                    .build();

            pubSubClient.subscribeToEvents(subscriptions[i]);
        }

        System.out.println("Created " + numSubscribers + " subscribers\n");

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Publish messages (fan-out to all)
        System.out.println("Publishing " + numMessages + " messages (fan-out to all subscribers)...\n");

        for (int i = 1; i <= numMessages; i++) {
            pubSubClient.sendEventsMessage(EventMessage.builder()
                    .id("msg-" + i)
                    .channel(channel)
                    .body(("Broadcast message #" + i).getBytes())
                    .build());

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Wait for all messages to be received
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Show results
        System.out.println("\n─────────────────────────────────────");
        System.out.println("Fan-Out Results:");
        int totalReceived = 0;
        for (int i = 0; i < numSubscribers; i++) {
            int count = receiveCounts[i].get();
            totalReceived += count;
            System.out.println("  Subscriber " + (i + 1) + ": " + count + " messages");
        }
        System.out.println("  Total: " + totalReceived + " (expected: " + (numSubscribers * numMessages) + ")");
        System.out.println("─────────────────────────────────────\n");

        // Cleanup
        for (EventsSubscription sub : subscriptions) {
            sub.cancel();
        }
        pubSubClient.deleteEventsChannel(channel);
    }

    /**
     * Demonstrates notification fan-out system.
     */
    public void notificationFanOutExample() {
        System.out.println("=== Notification Fan-Out System ===\n");

        String channel = "user-events";
        pubSubClient.createEventsChannel(channel);

        AtomicInteger emailCount = new AtomicInteger(0);
        AtomicInteger smsCount = new AtomicInteger(0);
        AtomicInteger pushCount = new AtomicInteger(0);
        AtomicInteger analyticsCount = new AtomicInteger(0);

        CountDownLatch latch = new CountDownLatch(8); // 2 events x 4 services

        // Email service subscriber
        EventsSubscription emailSub = EventsSubscription.builder()
                .channel(channel)
                .onReceiveEventCallback(event -> {
                    emailCount.incrementAndGet();
                    System.out.println("  [Email Service] Sending email for: " + new String(event.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        // SMS service subscriber
        EventsSubscription smsSub = EventsSubscription.builder()
                .channel(channel)
                .onReceiveEventCallback(event -> {
                    smsCount.incrementAndGet();
                    System.out.println("  [SMS Service] Sending SMS for: " + new String(event.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        // Push notification subscriber
        EventsSubscription pushSub = EventsSubscription.builder()
                .channel(channel)
                .onReceiveEventCallback(event -> {
                    pushCount.incrementAndGet();
                    System.out.println("  [Push Service] Sending push for: " + new String(event.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        // Analytics subscriber
        EventsSubscription analyticsSub = EventsSubscription.builder()
                .channel(channel)
                .onReceiveEventCallback(event -> {
                    analyticsCount.incrementAndGet();
                    System.out.println("  [Analytics] Recording event: " + new String(event.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        pubSubClient.subscribeToEvents(emailSub);
        pubSubClient.subscribeToEvents(smsSub);
        pubSubClient.subscribeToEvents(pushSub);
        pubSubClient.subscribeToEvents(analyticsSub);

        System.out.println("Notification services ready:");
        System.out.println("  - Email Service");
        System.out.println("  - SMS Service");
        System.out.println("  - Push Notification Service");
        System.out.println("  - Analytics Service\n");

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Publish user events
        System.out.println("Publishing user events...\n");

        pubSubClient.sendEventsMessage(EventMessage.builder()
                .channel(channel)
                .body("User signup: john@example.com".getBytes())
                .metadata("type:signup")
                .build());

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        pubSubClient.sendEventsMessage(EventMessage.builder()
                .channel(channel)
                .body("Order placed: #12345".getBytes())
                .metadata("type:order")
                .build());

        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\nNotification counts:");
        System.out.println("  Emails sent: " + emailCount.get());
        System.out.println("  SMS sent: " + smsCount.get());
        System.out.println("  Push sent: " + pushCount.get());
        System.out.println("  Analytics recorded: " + analyticsCount.get() + "\n");

        emailSub.cancel();
        smsSub.cancel();
        pushSub.cancel();
        analyticsSub.cancel();
        pubSubClient.deleteEventsChannel(channel);
    }

    /**
     * Demonstrates fan-out with content filtering.
     */
    public void filteredFanOutExample() {
        System.out.println("=== Filtered Fan-Out Pattern ===\n");

        String channel = "all-orders";
        pubSubClient.createEventsChannel(channel);

        AtomicInteger highValueCount = new AtomicInteger(0);
        AtomicInteger internationalCount = new AtomicInteger(0);
        AtomicInteger allOrdersCount = new AtomicInteger(0);

        CountDownLatch latch = new CountDownLatch(8); // Varies based on filters

        // High-value order processor (filters by amount)
        EventsSubscription highValueSub = EventsSubscription.builder()
                .channel(channel)
                .onReceiveEventCallback(event -> {
                    Map<String, String> tags = event.getTags();
                    double amount = Double.parseDouble(tags.getOrDefault("amount", "0"));

                    // Only process orders > $1000
                    if (amount > 1000) {
                        highValueCount.incrementAndGet();
                        System.out.println("  [High-Value] Processing: " + new String(event.getBody()) +
                                " ($" + amount + ")");
                    }
                    latch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        // International order processor (filters by region)
        EventsSubscription internationalSub = EventsSubscription.builder()
                .channel(channel)
                .onReceiveEventCallback(event -> {
                    Map<String, String> tags = event.getTags();
                    String region = tags.getOrDefault("region", "domestic");

                    // Only process international orders
                    if (!"domestic".equals(region)) {
                        internationalCount.incrementAndGet();
                        System.out.println("  [International] Processing: " + new String(event.getBody()) +
                                " (Region: " + region + ")");
                    }
                    latch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        // All orders logger
        EventsSubscription allOrdersSub = EventsSubscription.builder()
                .channel(channel)
                .onReceiveEventCallback(event -> {
                    allOrdersCount.incrementAndGet();
                    System.out.println("  [Logger] Received: " + new String(event.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        pubSubClient.subscribeToEvents(highValueSub);
        pubSubClient.subscribeToEvents(internationalSub);
        pubSubClient.subscribeToEvents(allOrdersSub);

        System.out.println("Filtered subscribers ready:");
        System.out.println("  - High-Value Processor (amount > $1000)");
        System.out.println("  - International Processor (non-domestic)");
        System.out.println("  - All Orders Logger\n");

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Publish various orders
        System.out.println("Publishing orders...\n");

        Object[][] orders = {
            {"Order #1", "500", "domestic"},
            {"Order #2", "1500", "domestic"},      // High-value
            {"Order #3", "300", "europe"},          // International
            {"Order #4", "2000", "asia"}            // Both high-value and international
        };

        for (Object[] order : orders) {
            Map<String, String> tags = new HashMap<>();
            tags.put("amount", (String) order[1]);
            tags.put("region", (String) order[2]);

            pubSubClient.sendEventsMessage(EventMessage.builder()
                    .channel(channel)
                    .body(((String) order[0]).getBytes())
                    .tags(tags)
                    .build());

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\nFiltered counts:");
        System.out.println("  All orders logged: " + allOrdersCount.get());
        System.out.println("  High-value processed: " + highValueCount.get());
        System.out.println("  International processed: " + internationalCount.get() + "\n");

        highValueSub.cancel();
        internationalSub.cancel();
        allOrdersSub.cancel();
        pubSubClient.deleteEventsChannel(channel);
    }

    /**
     * Demonstrates fan-out with groups (hybrid pattern).
     */
    public void hybridFanOutExample() {
        System.out.println("=== Hybrid Fan-Out Pattern ===\n");

        String channel = "hybrid-channel";
        pubSubClient.createEventsChannel(channel);

        AtomicInteger group1Total = new AtomicInteger(0);
        AtomicInteger group2Total = new AtomicInteger(0);
        AtomicInteger broadcastTotal = new AtomicInteger(0);

        CountDownLatch latch = new CountDownLatch(9); // 3 messages x 3 destinations

        // Group 1: Load-balanced handlers (2 members)
        EventsSubscription g1Sub1 = EventsSubscription.builder()
                .channel(channel)
                .group("handlers")
                .onReceiveEventCallback(event -> {
                    group1Total.incrementAndGet();
                    System.out.println("  [Handler-A] " + new String(event.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        EventsSubscription g1Sub2 = EventsSubscription.builder()
                .channel(channel)
                .group("handlers")
                .onReceiveEventCallback(event -> {
                    group1Total.incrementAndGet();
                    System.out.println("  [Handler-B] " + new String(event.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        // Group 2: Load-balanced loggers (2 members)
        EventsSubscription g2Sub1 = EventsSubscription.builder()
                .channel(channel)
                .group("loggers")
                .onReceiveEventCallback(event -> {
                    group2Total.incrementAndGet();
                    System.out.println("  [Logger-X] " + new String(event.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        EventsSubscription g2Sub2 = EventsSubscription.builder()
                .channel(channel)
                .group("loggers")
                .onReceiveEventCallback(event -> {
                    group2Total.incrementAndGet();
                    System.out.println("  [Logger-Y] " + new String(event.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        // Broadcast subscriber (no group - receives all)
        EventsSubscription broadcastSub = EventsSubscription.builder()
                .channel(channel)
                // No group = broadcast mode
                .onReceiveEventCallback(event -> {
                    broadcastTotal.incrementAndGet();
                    System.out.println("  [Monitor] " + new String(event.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        pubSubClient.subscribeToEvents(g1Sub1);
        pubSubClient.subscribeToEvents(g1Sub2);
        pubSubClient.subscribeToEvents(g2Sub1);
        pubSubClient.subscribeToEvents(g2Sub2);
        pubSubClient.subscribeToEvents(broadcastSub);

        System.out.println("Hybrid setup:");
        System.out.println("  - Group 'handlers': 2 members (load balanced)");
        System.out.println("  - Group 'loggers': 2 members (load balanced)");
        System.out.println("  - Monitor: broadcast (receives all)\n");

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Publish messages
        System.out.println("Publishing 3 messages...\n");

        for (int i = 1; i <= 3; i++) {
            pubSubClient.sendEventsMessage(EventMessage.builder()
                    .channel(channel)
                    .body(("Event " + i).getBytes())
                    .build());

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\nResults:");
        System.out.println("  Handlers group: " + group1Total.get() + " (load balanced within group)");
        System.out.println("  Loggers group: " + group2Total.get() + " (load balanced within group)");
        System.out.println("  Monitor: " + broadcastTotal.get() + " (received all)\n");

        g1Sub1.cancel();
        g1Sub2.cancel();
        g2Sub1.cancel();
        g2Sub2.cancel();
        broadcastSub.cancel();
        pubSubClient.deleteEventsChannel(channel);
    }

    /**
     * Demonstrates event sourcing with fan-out.
     */
    public void eventSourcingFanOutExample() {
        System.out.println("=== Event Sourcing Fan-Out ===\n");

        String channel = "order-events";
        pubSubClient.createEventsStoreChannel(channel);

        // Publish order lifecycle events
        System.out.println("Publishing order lifecycle events...\n");

        String orderId = "ORD-" + System.currentTimeMillis();

        String[] events = {
            "OrderCreated|" + orderId + "|amount:100",
            "PaymentReceived|" + orderId + "|method:card",
            "OrderShipped|" + orderId + "|tracking:TRK123",
            "OrderDelivered|" + orderId + "|signature:JohnDoe"
        };

        for (String event : events) {
            pubSubClient.sendEventsStoreMessage(EventStoreMessage.builder()
                    .channel(channel)
                    .body(event.getBytes(StandardCharsets.UTF_8))
                    .build());
            System.out.println("  Published: " + event.split("\\|")[0]);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("\nMultiple subscribers with different start points:\n");

        CountDownLatch latch = new CountDownLatch(8); // 4 events x 2 subscribers

        AtomicInteger fullHistoryCount = new AtomicInteger(0);
        AtomicInteger newOnlyCount = new AtomicInteger(0);

        // Full history subscriber (event sourcing - replay all)
        EventsStoreSubscription historySub = EventsStoreSubscription.builder()
                .channel(channel)
                .eventsStoreType(EventsStoreType.StartFromFirst)
                .onReceiveEventCallback(event -> {
                    fullHistoryCount.incrementAndGet();
                    String data = new String(event.getBody());
                    System.out.println("  [Full History] Seq " + event.getSequence() + ": " + data.split("\\|")[0]);
                    latch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        // New events only subscriber
        EventsStoreSubscription newSub = EventsStoreSubscription.builder()
                .channel(channel)
                .eventsStoreType(EventsStoreType.StartNewOnly)
                .onReceiveEventCallback(event -> {
                    newOnlyCount.incrementAndGet();
                    String data = new String(event.getBody());
                    System.out.println("  [New Only] " + data.split("\\|")[0]);
                    latch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        pubSubClient.subscribeToEventsStore(historySub);
        pubSubClient.subscribeToEventsStore(newSub);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Publish new event
        System.out.println("\n  Publishing new event (OrderRefunded)...\n");
        pubSubClient.sendEventsStoreMessage(EventStoreMessage.builder()
                .channel(channel)
                .body(("OrderRefunded|" + orderId + "|reason:damaged").getBytes())
                .build());

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\nResults:");
        System.out.println("  Full History subscriber: " + fullHistoryCount.get() + " events (all history)");
        System.out.println("  New Only subscriber: " + newOnlyCount.get() + " events (just new)\n");

        historySub.cancel();
        newSub.cancel();
        pubSubClient.deleteEventsStoreChannel(channel);
    }

    /**
     * Cleans up resources.
     */
    public void cleanup() {
        pubSubClient.close();
        System.out.println("Cleaned up resources.\n");
    }

    /**
     * Main method demonstrating fan-out patterns.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║          PubSub Fan-Out Pattern Examples                     ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        PubSubFanOutExample example = new PubSubFanOutExample();

        try {
            // Basic fan-out
            example.basicFanOutExample();

            // Notification system
            example.notificationFanOutExample();

            // Filtered fan-out
            example.filteredFanOutExample();

            // Hybrid fan-out
            example.hybridFanOutExample();

            // Event sourcing fan-out
            example.eventSourcingFanOutExample();

        } finally {
            example.cleanup();
        }

        System.out.println("PubSub fan-out pattern examples completed.");
    }
}
