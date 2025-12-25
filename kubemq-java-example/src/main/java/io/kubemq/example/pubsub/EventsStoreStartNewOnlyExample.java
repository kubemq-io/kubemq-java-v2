package io.kubemq.example.pubsub;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * EventsStore StartNewOnly Example
 *
 * This example demonstrates subscribing to a KubeMQ Events Store channel
 * with EventsStoreType.StartNewOnly configuration. This subscription type
 * ignores all historical messages and only receives new messages.
 *
 * StartNewOnly Behavior:
 * - Ignores all messages stored before subscription
 * - Only receives messages published after subscribing
 * - Fastest startup time (no historical replay)
 * - Similar to regular Events subscription but with persistence
 *
 * Use Cases:
 * - Real-time monitoring (only care about current events)
 * - Live dashboards
 * - Alert systems
 * - When historical data is irrelevant
 * - High-throughput scenarios where replay would be too slow
 *
 * Comparison:
 * - Events (regular): Fire-and-forget, no persistence
 * - EventsStore + StartNewOnly: Real-time + persistence for other consumers
 * - EventsStore + StartFromFirst: Full history replay
 *
 * @see io.kubemq.sdk.pubsub.EventsStoreType#StartNewOnly
 * @see io.kubemq.sdk.pubsub.EventsStoreSubscription
 */
public class EventsStoreStartNewOnlyExample {

    private final PubSubClient pubSubClient;
    private final String channelName = "events-store-new-only";
    private final String address = "localhost:50000";
    private final String clientId = "new-only-client";

    /**
     * Initializes the PubSubClient.
     */
    public EventsStoreStartNewOnlyExample() {
        pubSubClient = PubSubClient.builder()
                .address(address)
                .clientId(clientId)
                .logLevel(KubeMQClient.Level.INFO)
                .build();

        ServerInfo pingResult = pubSubClient.ping();
        System.out.println("Connected to: " + pingResult.getHost() + " v" + pingResult.getVersion());

        pubSubClient.createEventsStoreChannel(channelName);
    }

    /**
     * Sends historical messages before subscription.
     */
    public void sendHistoricalMessages() {
        System.out.println("\n=== Sending Historical Messages (Before Subscription) ===\n");

        for (int i = 1; i <= 5; i++) {
            EventStoreMessage message = EventStoreMessage.builder()
                    .id("historical-" + i)
                    .channel(channelName)
                    .body(("Historical message #" + i + " (should NOT be received)").getBytes())
                    .metadata("Pre-subscription message")
                    .build();

            pubSubClient.sendEventsStoreMessage(message);
            System.out.println("  Sent historical message #" + i);
        }

        System.out.println("\n5 historical messages sent. These will be ignored by StartNewOnly.\n");
    }

    /**
     * Demonstrates StartNewOnly subscription.
     */
    public void subscribeNewOnlyExample() {
        System.out.println("=== StartNewOnly Subscription ===\n");

        AtomicInteger receivedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3); // Expect 3 new messages

        Consumer<EventStoreMessageReceived> onReceive = event -> {
            int count = receivedCount.incrementAndGet();
            System.out.println("  [" + count + "] Received: " + new String(event.getBody()));
            latch.countDown();
        };

        EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                .channel(channelName)
                .eventsStoreType(EventsStoreType.StartNewOnly)  // Key setting!
                .onReceiveEventCallback(onReceive)
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        System.out.println("Subscribing with StartNewOnly...");
        System.out.println("(Historical messages will be ignored)\n");

        pubSubClient.subscribeToEventsStore(subscription);

        // Wait a moment for subscription to be established
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Now send new messages AFTER subscription
        System.out.println("Sending new messages (after subscription):\n");

        for (int i = 1; i <= 3; i++) {
            EventStoreMessage newMessage = EventStoreMessage.builder()
                    .id("new-" + i)
                    .channel(channelName)
                    .body(("New message #" + i + " (should be received)").getBytes())
                    .metadata("Post-subscription message")
                    .build();

            pubSubClient.sendEventsStoreMessage(newMessage);
            System.out.println("  Sent new message #" + i);

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Wait for messages
        try {
            latch.await(5, TimeUnit.SECONDS);
            System.out.println("\n─────────────────────────────────────");
            System.out.println("Received " + receivedCount.get() + " new messages");
            System.out.println("Historical messages (5) were ignored");
            System.out.println("─────────────────────────────────────\n");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        subscription.cancel();
    }

    /**
     * Demonstrates live monitoring use case.
     */
    public void liveMonitoringExample() {
        System.out.println("=== Live Monitoring Example ===\n");

        String alertsChannel = "system-alerts";
        pubSubClient.createEventsStoreChannel(alertsChannel);

        // Simulate old alerts (before monitoring starts)
        System.out.println("Simulating old alerts (before monitoring starts)...");
        for (int i = 1; i <= 3; i++) {
            EventStoreMessage oldAlert = EventStoreMessage.builder()
                    .id("old-alert-" + i)
                    .channel(alertsChannel)
                    .body(("Old alert #" + i + " - already handled").getBytes())
                    .build();
            pubSubClient.sendEventsStoreMessage(oldAlert);
        }
        System.out.println("3 old alerts in the system.\n");

        // Start live monitoring
        System.out.println("Starting live monitoring dashboard...");
        System.out.println("Using StartNewOnly to see only current/future alerts.\n");

        AtomicInteger alertCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2);

        Consumer<EventStoreMessageReceived> alertHandler = event -> {
            int count = alertCount.incrementAndGet();
            System.out.println("  [LIVE ALERT " + count + "] " + new String(event.getBody()));
            latch.countDown();
        };

        EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                .channel(alertsChannel)
                .eventsStoreType(EventsStoreType.StartNewOnly)
                .onReceiveEventCallback(alertHandler)
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        pubSubClient.subscribeToEventsStore(subscription);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // New alerts come in
        System.out.println("New alerts arriving...\n");

        EventStoreMessage criticalAlert = EventStoreMessage.builder()
                .id("new-critical")
                .channel(alertsChannel)
                .body("CRITICAL: Database connection lost!".getBytes())
                .build();
        pubSubClient.sendEventsStoreMessage(criticalAlert);

        EventStoreMessage warningAlert = EventStoreMessage.builder()
                .id("new-warning")
                .channel(alertsChannel)
                .body("WARNING: High memory usage detected".getBytes())
                .build();
        pubSubClient.sendEventsStoreMessage(warningAlert);

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\nDashboard received " + alertCount.get() + " live alerts.");
        System.out.println("Old alerts (3) were not shown - already handled.\n");

        subscription.cancel();
        pubSubClient.deleteEventsStoreChannel(alertsChannel);
    }

    /**
     * Demonstrates the difference between Events and EventsStore + StartNewOnly.
     */
    public void comparisonWithEventsExample() {
        System.out.println("=== Events vs EventsStore + StartNewOnly ===\n");

        System.out.println("Both provide real-time messaging, but with key differences:\n");

        System.out.println("┌────────────────────────┬────────────────┬─────────────────────────┐");
        System.out.println("│ Feature                │ Events         │ EventsStore+StartNewOnly│");
        System.out.println("├────────────────────────┼────────────────┼─────────────────────────┤");
        System.out.println("│ Real-time delivery     │ Yes            │ Yes                     │");
        System.out.println("│ Message persistence    │ No             │ Yes                     │");
        System.out.println("│ Historical replay      │ N/A            │ Available (other modes) │");
        System.out.println("│ Delivery guarantee     │ At-most-once   │ Stored for replay       │");
        System.out.println("│ Use case               │ Fire & forget  │ Real-time + audit trail │");
        System.out.println("└────────────────────────┴────────────────┴─────────────────────────┘\n");

        System.out.println("Choose EventsStore + StartNewOnly when you need:");
        System.out.println("  - Real-time processing like Events");
        System.out.println("  - But also need messages persisted for:");
        System.out.println("    * Other consumers with different start points");
        System.out.println("    * Audit/compliance requirements");
        System.out.println("    * Debugging/replay capabilities\n");
    }

    /**
     * Demonstrates multiple subscribers with different start modes.
     */
    public void mixedSubscribersExample() {
        System.out.println("=== Mixed Subscribers Example ===\n");

        String sharedChannel = "shared-channel";
        pubSubClient.createEventsStoreChannel(sharedChannel);

        // Send some historical data
        System.out.println("Sending 5 historical messages...");
        for (int i = 1; i <= 5; i++) {
            EventStoreMessage msg = EventStoreMessage.builder()
                    .id("history-" + i)
                    .channel(sharedChannel)
                    .body(("Historical " + i).getBytes())
                    .build();
            pubSubClient.sendEventsStoreMessage(msg);
        }

        AtomicInteger newOnlyCount = new AtomicInteger(0);
        AtomicInteger fromFirstCount = new AtomicInteger(0);

        // Subscriber 1: StartNewOnly (live dashboard)
        EventsStoreSubscription dashboardSub = EventsStoreSubscription.builder()
                .channel(sharedChannel)
                .eventsStoreType(EventsStoreType.StartNewOnly)
                .onReceiveEventCallback(e -> {
                    newOnlyCount.incrementAndGet();
                    System.out.println("  [Dashboard] " + new String(e.getBody()));
                })
                .onErrorCallback(err -> {})
                .build();

        // Subscriber 2: StartFromFirst (analytics - needs all data)
        EventsStoreSubscription analyticsSub = EventsStoreSubscription.builder()
                .channel(sharedChannel)
                .eventsStoreType(EventsStoreType.StartFromFirst)
                .onReceiveEventCallback(e -> {
                    fromFirstCount.incrementAndGet();
                })
                .onErrorCallback(err -> {})
                .build();

        System.out.println("\nStarting two subscribers:");
        System.out.println("  1. Dashboard (StartNewOnly) - only new events");
        System.out.println("  2. Analytics (StartFromFirst) - all events\n");

        pubSubClient.subscribeToEventsStore(dashboardSub);
        pubSubClient.subscribeToEventsStore(analyticsSub);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Send new message
        System.out.println("Sending new message...\n");
        EventStoreMessage newMsg = EventStoreMessage.builder()
                .id("new-shared")
                .channel(sharedChannel)
                .body("New event for everyone".getBytes())
                .build();
        pubSubClient.sendEventsStoreMessage(newMsg);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Results:");
        System.out.println("  Dashboard (StartNewOnly): " + newOnlyCount.get() + " messages");
        System.out.println("  Analytics (StartFromFirst): " + fromFirstCount.get() + " messages\n");

        dashboardSub.cancel();
        analyticsSub.cancel();
        pubSubClient.deleteEventsStoreChannel(sharedChannel);
    }

    /**
     * Cleans up resources.
     */
    public void cleanup() {
        try {
            pubSubClient.deleteEventsStoreChannel(channelName);
            System.out.println("Cleaned up channel: " + channelName);
        } catch (Exception e) {
            // Ignore
        }
        pubSubClient.close();
    }

    /**
     * Main method demonstrating StartNewOnly subscription.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║        EventsStore StartNewOnly Examples                     ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        EventsStoreStartNewOnlyExample example = new EventsStoreStartNewOnlyExample();

        try {
            // Comparison info
            example.comparisonWithEventsExample();

            // Send historical messages
            example.sendHistoricalMessages();

            // Subscribe with StartNewOnly
            example.subscribeNewOnlyExample();

            // Live monitoring example
            example.liveMonitoringExample();

            // Mixed subscribers
            example.mixedSubscribersExample();

        } finally {
            example.cleanup();
        }

        System.out.println("StartNewOnly examples completed.");
    }
}
