package io.kubemq.example.pubsub;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * EventsStore StartFromLast Example
 *
 * This example demonstrates subscribing to a KubeMQ Events Store channel
 * with EventsStoreType.StartFromLast configuration. This subscription type
 * starts from the most recent message and then continues with new messages.
 *
 * StartFromLast Behavior:
 * - First receives the last (most recent) stored message
 * - Then receives all new messages as they arrive
 * - Skips all historical messages except the last one
 * - Provides quick startup with current context
 *
 * Use Cases:
 * - Resume processing with latest state context
 * - Quick subscriber initialization
 * - Get current state before receiving updates
 * - Monitoring systems needing latest status
 * - Cache warming with most recent data
 *
 * Comparison with other types:
 * - StartNewOnly: Only new messages, no history
 * - StartFromLast: Last message + new messages
 * - StartFromFirst: Complete history + new messages
 *
 * @see io.kubemq.sdk.pubsub.EventsStoreType#StartFromLast
 * @see io.kubemq.sdk.pubsub.EventsStoreSubscription
 */
public class EventsStoreStartFromLastExample {

    private final PubSubClient pubSubClient;
    private final String channelName = "events-store-start-last";
    private final String address = "localhost:50000";
    private final String clientId = "start-from-last-client";

    /**
     * Initializes the PubSubClient.
     */
    public EventsStoreStartFromLastExample() {
        pubSubClient = PubSubClient.builder()
                .address(address)
                .clientId(clientId)
                .logLevel(KubeMQClient.Level.INFO)
                .build();

        ServerInfo pingResult = pubSubClient.ping();
        System.out.println("Connected to: " + pingResult.getHost() + " v" + pingResult.getVersion());

        // Create the channel
        pubSubClient.createEventsStoreChannel(channelName);
    }

    /**
     * Sends numbered messages to build up history.
     */
    public void sendHistoricalMessages(int count) {
        System.out.println("\n=== Sending " + count + " Historical Messages ===\n");

        for (int i = 1; i <= count; i++) {
            EventStoreMessage message = EventStoreMessage.builder()
                    .id("historical-" + i)
                    .channel(channelName)
                    .body(("Historical message #" + i + " (will be skipped)").getBytes())
                    .metadata("Sequence: " + i)
                    .build();

            pubSubClient.sendEventsStoreMessage(message);
            System.out.println("  Sent message #" + i);
        }

        // Send the "last" message that will be received
        EventStoreMessage lastMessage = EventStoreMessage.builder()
                .id("last-message")
                .channel(channelName)
                .body("LAST MESSAGE - This is the most recent".getBytes())
                .metadata("This will be the first message received")
                .build();

        pubSubClient.sendEventsStoreMessage(lastMessage);
        System.out.println("  Sent LAST message (this will be received first)\n");
    }

    /**
     * Demonstrates StartFromLast subscription - receives only the last stored message.
     */
    public void subscribeFromLastExample() {
        System.out.println("=== StartFromLast Subscription ===\n");

        AtomicInteger receivedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        Consumer<EventStoreMessageReceived> onReceive = event -> {
            int count = receivedCount.incrementAndGet();
            System.out.println("  [" + count + "] Received: " + new String(event.getBody()));
            System.out.println("      Sequence: " + event.getSequence());
            System.out.println("      Timestamp: " + event.getTimestamp());

            if (count >= 1) {
                latch.countDown();
            }
        };

        Consumer<String> onError = error -> {
            System.err.println("  Error: " + error);
        };

        EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                .channel(channelName)
                .eventsStoreType(EventsStoreType.StartFromLast)  // Key setting!
                .onReceiveEventCallback(onReceive)
                .onErrorCallback(onError)
                .build();

        System.out.println("Subscribing with StartFromLast...");
        System.out.println("(Will receive only the last message, then new messages)\n");

        pubSubClient.subscribeToEventsStore(subscription);

        try {
            latch.await(5, TimeUnit.SECONDS);
            System.out.println("\nReceived " + receivedCount.get() + " message(s)");
            System.out.println("Note: Historical messages before 'last' were skipped.\n");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        subscription.cancel();
    }

    /**
     * Demonstrates receiving the last message plus new ones.
     */
    public void lastPlusNewMessagesExample() {
        System.out.println("=== Last Message + New Messages ===\n");

        AtomicInteger receivedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(4); // 1 last + 3 new

        Consumer<EventStoreMessageReceived> onReceive = event -> {
            int count = receivedCount.incrementAndGet();
            String body = new String(event.getBody());

            if (count == 1) {
                System.out.println("  [LAST] " + body);
            } else {
                System.out.println("  [NEW " + (count - 1) + "] " + body);
            }

            latch.countDown();
        };

        EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                .channel(channelName)
                .eventsStoreType(EventsStoreType.StartFromLast)
                .onReceiveEventCallback(onReceive)
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        pubSubClient.subscribeToEventsStore(subscription);

        // Wait a moment for subscription to be ready
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Send new messages
        System.out.println("Sending 3 new messages after subscribing...\n");

        for (int i = 1; i <= 3; i++) {
            EventStoreMessage newMsg = EventStoreMessage.builder()
                    .id("new-" + i)
                    .channel(channelName)
                    .body(("New message #" + i + " (after subscription)").getBytes())
                    .build();

            pubSubClient.sendEventsStoreMessage(newMsg);

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            latch.await(5, TimeUnit.SECONDS);
            System.out.println("\nTotal received: " + receivedCount.get() + " (1 last + 3 new)\n");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        subscription.cancel();
    }

    /**
     * Demonstrates a monitoring scenario using StartFromLast.
     */
    public void monitoringScenarioExample() {
        System.out.println("=== Monitoring Scenario ===\n");

        String statusChannel = "system-status";
        pubSubClient.createEventsStoreChannel(statusChannel);

        // Simulate system sending status updates over time
        System.out.println("Simulating system status updates...\n");

        String[] statuses = {
                "{\"cpu\": 45, \"memory\": 60, \"status\": \"healthy\"}",
                "{\"cpu\": 55, \"memory\": 65, \"status\": \"healthy\"}",
                "{\"cpu\": 75, \"memory\": 80, \"status\": \"warning\"}",
                "{\"cpu\": 50, \"memory\": 70, \"status\": \"healthy\"}"
        };

        for (String status : statuses) {
            EventStoreMessage msg = EventStoreMessage.builder()
                    .id("status-" + System.currentTimeMillis())
                    .channel(statusChannel)
                    .body(status.getBytes())
                    .build();
            pubSubClient.sendEventsStoreMessage(msg);
            System.out.println("  Status update sent: " + status);

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // New monitoring dashboard connects
        System.out.println("\nNew monitoring dashboard connects...");
        System.out.println("Using StartFromLast to get current status + future updates.\n");

        AtomicInteger updateCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2); // 1 current + 1 new

        Consumer<EventStoreMessageReceived> dashboardHandler = event -> {
            int count = updateCount.incrementAndGet();
            String body = new String(event.getBody());

            if (count == 1) {
                System.out.println("  [CURRENT STATE] " + body);
                System.out.println("  Dashboard initialized with latest status.\n");
            } else {
                System.out.println("  [LIVE UPDATE] " + body);
            }

            latch.countDown();
        };

        EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                .channel(statusChannel)
                .eventsStoreType(EventsStoreType.StartFromLast)
                .onReceiveEventCallback(dashboardHandler)
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        pubSubClient.subscribeToEventsStore(subscription);

        // Wait for initial state
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Send a new update
        EventStoreMessage newStatus = EventStoreMessage.builder()
                .id("status-new")
                .channel(statusChannel)
                .body("{\"cpu\": 30, \"memory\": 55, \"status\": \"healthy\"}".getBytes())
                .build();
        pubSubClient.sendEventsStoreMessage(newStatus);

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\nMonitoring dashboard received " + updateCount.get() + " update(s).");

        subscription.cancel();
        pubSubClient.deleteEventsStoreChannel(statusChannel);
    }

    /**
     * Compares different subscription types.
     */
    public void comparisonExample() {
        System.out.println("\n=== Subscription Type Comparison ===\n");

        System.out.println("Given a channel with messages [1, 2, 3, 4, 5]:");
        System.out.println();
        System.out.println("┌─────────────────────┬──────────────────────────────────┐");
        System.out.println("│ Subscription Type   │ Messages Received                │");
        System.out.println("├─────────────────────┼──────────────────────────────────┤");
        System.out.println("│ StartFromFirst      │ [1, 2, 3, 4, 5] + new            │");
        System.out.println("│ StartFromLast       │ [5] + new                        │");
        System.out.println("│ StartNewOnly        │ [only new messages]              │");
        System.out.println("│ StartAtSequence(3)  │ [3, 4, 5] + new                  │");
        System.out.println("│ StartAtTime(T)      │ [messages after T] + new         │");
        System.out.println("│ StartAtTimeDelta(D) │ [messages in last D seconds] + new│");
        System.out.println("└─────────────────────┴──────────────────────────────────┘");
        System.out.println();
        System.out.println("StartFromLast is ideal when you need:");
        System.out.println("  - Quick startup (no history replay)");
        System.out.println("  - Current context before receiving updates");
        System.out.println("  - To avoid processing old, potentially stale data");
        System.out.println();
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
     * Main method demonstrating StartFromLast subscription.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║         EventsStore StartFromLast Examples                   ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        EventsStoreStartFromLastExample example = new EventsStoreStartFromLastExample();

        try {
            // Show comparison first
            example.comparisonExample();

            // Send historical messages
            example.sendHistoricalMessages(5);

            // Subscribe from last
            example.subscribeFromLastExample();

            // Last plus new messages
            example.lastPlusNewMessagesExample();

            // Monitoring scenario
            example.monitoringScenarioExample();

        } finally {
            example.cleanup();
        }

        System.out.println("\nStartFromLast examples completed.");
    }
}
