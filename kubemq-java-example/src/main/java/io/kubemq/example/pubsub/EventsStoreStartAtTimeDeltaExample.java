package io.kubemq.example.pubsub;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * EventsStore StartAtTimeDelta Example
 *
 * This example demonstrates subscribing to a KubeMQ Events Store channel
 * with EventsStoreType.StartAtTimeDelta configuration. This subscription type
 * starts receiving messages from a relative time offset (e.g., last 30 minutes).
 *
 * StartAtTimeDelta Behavior:
 * - Calculates start time as: now - delta_seconds
 * - Receives all messages stored after that calculated time
 * - Continues with new messages after historical replay
 * - Dynamic - relative to subscription time, not a fixed timestamp
 *
 * Use Cases:
 * - "Show me the last 5 minutes of events"
 * - Rolling window data processing
 * - Recent event analysis
 * - Catch-up after short outage
 * - Time-based data sampling
 *
 * Comparison with StartAtTime:
 * - StartAtTime: Fixed absolute timestamp
 * - StartAtTimeDelta: Relative, calculated at subscription time
 *
 * @see io.kubemq.sdk.pubsub.EventsStoreType#StartAtTimeDelta
 * @see io.kubemq.sdk.pubsub.EventsStoreSubscription
 */
public class EventsStoreStartAtTimeDeltaExample {

    private final PubSubClient pubSubClient;
    private final String channelName = "events-store-time-delta";
    private final String address = "localhost:50000";
    private final String clientId = "time-delta-client";
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Initializes the PubSubClient.
     */
    public EventsStoreStartAtTimeDeltaExample() {
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
     * Helper to get formatted current time.
     */
    private String now() {
        return LocalDateTime.now().format(formatter);
    }

    /**
     * Sends messages at different times to demonstrate time-based filtering.
     */
    public void sendTimedMessages() throws InterruptedException {
        System.out.println("\n=== Sending Messages Over Time ===\n");

        // Send messages with delays to create time spread
        System.out.println("[" + now() + "] Sending first batch (3 messages)...");
        for (int i = 1; i <= 3; i++) {
            sendMessage("Old message #" + i);
        }

        System.out.println("[" + now() + "] Waiting 3 seconds...\n");
        Thread.sleep(3000);

        System.out.println("[" + now() + "] Sending second batch (3 messages)...");
        for (int i = 4; i <= 6; i++) {
            sendMessage("Recent message #" + i);
        }

        System.out.println("[" + now() + "] Waiting 2 seconds...\n");
        Thread.sleep(2000);

        System.out.println("[" + now() + "] Sending third batch (3 messages)...");
        for (int i = 7; i <= 9; i++) {
            sendMessage("Very recent message #" + i);
        }

        System.out.println("\nMessage timeline:");
        System.out.println("  T-5s: Messages 1-3 (old)");
        System.out.println("  T-2s: Messages 4-6 (recent)");
        System.out.println("  T-0s: Messages 7-9 (very recent)\n");
    }

    private void sendMessage(String content) {
        EventStoreMessage message = EventStoreMessage.builder()
                .id("msg-" + System.currentTimeMillis())
                .channel(channelName)
                .body(content.getBytes())
                .metadata("Sent at: " + now())
                .build();
        pubSubClient.sendEventsStoreMessage(message);
        System.out.println("  [" + now() + "] Sent: " + content);
    }

    /**
     * Demonstrates subscribing with time delta (last N seconds).
     */
    public void subscribeWithTimeDeltaExample() {
        System.out.println("=== StartAtTimeDelta Subscription ===\n");

        int deltaSeconds = 3; // Get messages from last 3 seconds

        System.out.println("Subscribing with delta = " + deltaSeconds + " seconds");
        System.out.println("(Will receive messages from the last " + deltaSeconds + " seconds)\n");

        AtomicInteger receivedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        Consumer<EventStoreMessageReceived> onReceive = event -> {
            int count = receivedCount.incrementAndGet();
            long timestampSeconds = event.getTimestamp();
            Instant timestamp = Instant.ofEpochSecond(timestampSeconds);
            LocalDateTime eventTime = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault());

            System.out.println("  [" + count + "] " + new String(event.getBody()));
            System.out.println("      Event time: " + eventTime.format(formatter));

            // Wait a bit to collect all messages
            if (count >= 1) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        // Use eventsStoreSequenceValue to pass delta seconds
        // The SDK interprets this as seconds when using StartAtTimeDelta
        EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                .channel(channelName)
                .eventsStoreType(EventsStoreType.StartAtTimeDelta)
                .eventsStoreSequenceValue(deltaSeconds)  // Delta in seconds
                .onReceiveEventCallback(onReceive)
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        pubSubClient.subscribeToEventsStore(subscription);

        // Wait for messages
        try {
            Thread.sleep(3000);
            System.out.println("\nReceived " + receivedCount.get() + " messages from last " + deltaSeconds + " seconds.");
            System.out.println("Older messages were filtered out.\n");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        subscription.cancel();
    }

    /**
     * Demonstrates different delta values and their effects.
     */
    public void differentDeltasExample() throws InterruptedException {
        System.out.println("=== Different Delta Values ===\n");

        String deltaChannel = "delta-test-channel";
        pubSubClient.createEventsStoreChannel(deltaChannel);

        // Send messages spaced out over time
        System.out.println("Sending messages over 6 seconds...\n");

        for (int i = 1; i <= 6; i++) {
            EventStoreMessage msg = EventStoreMessage.builder()
                    .id("timed-" + i)
                    .channel(deltaChannel)
                    .body(("Message at second " + i).getBytes())
                    .build();
            pubSubClient.sendEventsStoreMessage(msg);
            System.out.println("[" + now() + "] Sent message " + i);

            if (i < 6) {
                Thread.sleep(1000);
            }
        }

        System.out.println("\nTesting different delta values:\n");

        // Test different deltas
        int[] deltas = {2, 4, 10};

        for (int delta : deltas) {
            AtomicInteger count = new AtomicInteger(0);

            Consumer<EventStoreMessageReceived> counter = event -> {
                count.incrementAndGet();
            };

            EventsStoreSubscription sub = EventsStoreSubscription.builder()
                    .channel(deltaChannel)
                    .eventsStoreType(EventsStoreType.StartAtTimeDelta)
                    .eventsStoreSequenceValue(delta)
                    .onReceiveEventCallback(counter)
                    .onErrorCallback(err -> {})
                    .build();

            pubSubClient.subscribeToEventsStore(sub);
            Thread.sleep(1000);
            sub.cancel();

            System.out.println("  Delta = " + delta + " seconds: Received " + count.get() + " messages");
        }

        System.out.println("\nNote: Larger delta = more historical messages.\n");
        pubSubClient.deleteEventsStoreChannel(deltaChannel);
    }

    /**
     * Demonstrates rolling window monitoring pattern.
     */
    public void rollingWindowExample() {
        System.out.println("=== Rolling Window Monitoring ===\n");

        String metricsChannel = "metrics-stream";
        pubSubClient.createEventsStoreChannel(metricsChannel);

        System.out.println("Scenario: Monitor system metrics with 5-second rolling window\n");

        // Simulate continuous metrics
        System.out.println("Sending simulated metrics...");
        for (int i = 1; i <= 10; i++) {
            String metric = String.format("{\"cpu\": %d, \"memory\": %d, \"timestamp\": %d}",
                    30 + (i * 5), 50 + (i * 3), System.currentTimeMillis());

            EventStoreMessage msg = EventStoreMessage.builder()
                    .id("metric-" + i)
                    .channel(metricsChannel)
                    .body(metric.getBytes())
                    .build();
            pubSubClient.sendEventsStoreMessage(msg);

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("Metrics sent over 5 seconds.\n");

        // Subscribe with rolling window
        int windowSeconds = 3;
        System.out.println("Subscribing with " + windowSeconds + "-second rolling window...\n");

        AtomicInteger windowCount = new AtomicInteger(0);

        Consumer<EventStoreMessageReceived> windowProcessor = event -> {
            int count = windowCount.incrementAndGet();
            System.out.println("  [Window] Metric " + count + ": " + new String(event.getBody()));
        };

        EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                .channel(metricsChannel)
                .eventsStoreType(EventsStoreType.StartAtTimeDelta)
                .eventsStoreSequenceValue(windowSeconds)
                .onReceiveEventCallback(windowProcessor)
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        pubSubClient.subscribeToEventsStore(subscription);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\nRolling window captured " + windowCount.get() + " recent metrics.");
        System.out.println("Older metrics were outside the window.\n");

        subscription.cancel();
        pubSubClient.deleteEventsStoreChannel(metricsChannel);
    }

    /**
     * Compares StartAtTime vs StartAtTimeDelta.
     */
    public void comparisonExample() {
        System.out.println("=== StartAtTime vs StartAtTimeDelta ===\n");

        System.out.println("┌────────────────────┬────────────────────────────────────────┐");
        System.out.println("│ Feature            │ StartAtTime    │ StartAtTimeDelta     │");
        System.out.println("├────────────────────┼────────────────┼──────────────────────┤");
        System.out.println("│ Time Reference     │ Absolute       │ Relative             │");
        System.out.println("│ Input              │ Instant        │ Seconds (delta)      │");
        System.out.println("│ Use Case           │ \"Since 9am\"    │ \"Last 5 minutes\"     │");
        System.out.println("│ Consistency        │ Same result    │ Varies by sub time   │");
        System.out.println("│ Code Simplicity    │ Need Instant   │ Just integer         │");
        System.out.println("└────────────────────┴────────────────┴──────────────────────┘\n");

        System.out.println("StartAtTimeDelta is ideal when you need:");
        System.out.println("  - Rolling/sliding windows");
        System.out.println("  - Relative time queries (last N minutes/hours)");
        System.out.println("  - Simpler code without timestamp calculations\n");

        System.out.println("StartAtTime is better when you need:");
        System.out.println("  - Consistent results across subscribers");
        System.out.println("  - Replay from specific point in time");
        System.out.println("  - Reproducible message sets\n");
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
     * Main method demonstrating StartAtTimeDelta subscription.
     */
    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║       EventsStore StartAtTimeDelta Examples                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        EventsStoreStartAtTimeDeltaExample example = new EventsStoreStartAtTimeDeltaExample();

        try {
            // Comparison info
            example.comparisonExample();

            // Send timed messages
            example.sendTimedMessages();

            // Subscribe with time delta
            example.subscribeWithTimeDeltaExample();

            // Different delta values
            example.differentDeltasExample();

            // Rolling window pattern
            example.rollingWindowExample();

        } finally {
            example.cleanup();
        }

        System.out.println("StartAtTimeDelta examples completed.");
    }
}
