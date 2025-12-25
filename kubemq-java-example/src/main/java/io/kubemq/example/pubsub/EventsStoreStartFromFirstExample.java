package io.kubemq.example.pubsub;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * EventsStore StartFromFirst Example
 *
 * This example demonstrates subscribing to a KubeMQ Events Store channel
 * with EventsStoreType.StartFromFirst configuration. This subscription type
 * replays ALL messages ever stored in the channel from the very beginning.
 *
 * StartFromFirst Behavior:
 * - Receives all messages from the first message ever stored
 * - Provides complete message history
 * - New messages are delivered after historical replay
 * - Useful for initializing new consumers with full state
 *
 * Use Cases:
 * - Event sourcing - rebuild aggregate state from all events
 * - New service deployment - catch up on all historical data
 * - Data migration - replay entire event history
 * - Audit and compliance - process all historical records
 * - Testing and debugging - verify entire event sequence
 *
 * Considerations:
 * - May take time if channel has many historical messages
 * - Consider message volume before using in production
 * - Combine with message filtering for selective replay
 * - Monitor consumer lag during replay
 *
 * @see io.kubemq.sdk.pubsub.EventsStoreType#StartFromFirst
 * @see io.kubemq.sdk.pubsub.EventsStoreSubscription
 */
public class EventsStoreStartFromFirstExample {

    private final PubSubClient pubSubClient;
    private final String channelName = "events-store-start-first";
    private final String address = "localhost:50000";
    private final String clientId = "start-from-first-client";

    /**
     * Initializes the PubSubClient.
     */
    public EventsStoreStartFromFirstExample() {
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
     * Sends numbered messages to the events store for testing.
     */
    public void sendHistoricalMessages(int count) {
        System.out.println("\n=== Sending " + count + " Historical Messages ===\n");

        for (int i = 1; i <= count; i++) {
            EventStoreMessage message = EventStoreMessage.builder()
                    .id("msg-" + i)
                    .channel(channelName)
                    .body(("Historical message #" + i).getBytes())
                    .metadata("Sequence: " + i)
                    .build();

            EventSendResult result = pubSubClient.sendEventsStoreMessage(message);
            if (result.isSent()) {
                System.out.println("  Sent message #" + i + " (ID: " + result.getId() + ")");
            }
        }

        System.out.println("\nAll historical messages sent.\n");
    }

    /**
     * Demonstrates subscribing with StartFromFirst to receive all messages.
     */
    public void subscribeFromFirstExample() {
        System.out.println("=== StartFromFirst Subscription ===\n");

        AtomicInteger receivedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        // Track timing
        long startTime = System.currentTimeMillis();

        // Callback for received messages
        Consumer<EventStoreMessageReceived> onReceive = event -> {
            int count = receivedCount.incrementAndGet();
            System.out.println("  [" + count + "] Received: " +
                    new String(event.getBody()) +
                    " (Sequence: " + event.getSequence() + ")");

            // Signal when we've received expected messages
            if (count >= 5) {
                latch.countDown();
            }
        };

        // Error callback
        Consumer<String> onError = error -> {
            System.err.println("  Error: " + error);
        };

        // Create subscription with StartFromFirst
        EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                .channel(channelName)
                .eventsStoreType(EventsStoreType.StartFromFirst)  // Key setting!
                .onReceiveEventCallback(onReceive)
                .onErrorCallback(onError)
                .build();

        System.out.println("Subscribing with StartFromFirst...");
        System.out.println("(Will receive all historical messages from the beginning)\n");

        pubSubClient.subscribeToEventsStore(subscription);

        // Wait for messages
        try {
            boolean received = latch.await(10, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - startTime;

            System.out.println("\n─────────────────────────────────────");
            System.out.println("Replay completed in " + duration + "ms");
            System.out.println("Total messages received: " + receivedCount.get());
            System.out.println("─────────────────────────────────────\n");

            if (!received) {
                System.out.println("Note: Timeout reached, some messages may not have arrived.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Cancel subscription
        subscription.cancel();
    }

    /**
     * Demonstrates that new subscribers receive complete history.
     */
    public void newSubscriberExample() {
        System.out.println("=== New Subscriber Catches Up ===\n");

        System.out.println("Scenario: A new service instance starts and needs full state.\n");

        // First, send some more messages
        System.out.println("Step 1: Adding more messages to the store...");
        for (int i = 6; i <= 8; i++) {
            EventStoreMessage message = EventStoreMessage.builder()
                    .id("msg-" + i)
                    .channel(channelName)
                    .body(("Additional message #" + i).getBytes())
                    .metadata("Sequence: " + i)
                    .build();
            pubSubClient.sendEventsStoreMessage(message);
            System.out.println("  Sent message #" + i);
        }

        System.out.println("\nStep 2: New subscriber joins with StartFromFirst...\n");

        AtomicInteger count = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        Consumer<EventStoreMessageReceived> onReceive = event -> {
            int n = count.incrementAndGet();
            System.out.println("  New subscriber received #" + n + ": " + new String(event.getBody()));
            if (n >= 8) {
                latch.countDown();
            }
        };

        EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                .channel(channelName)
                .eventsStoreType(EventsStoreType.StartFromFirst)
                .onReceiveEventCallback(onReceive)
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        pubSubClient.subscribeToEventsStore(subscription);

        try {
            latch.await(10, TimeUnit.SECONDS);
            System.out.println("\nNew subscriber received " + count.get() + " messages (complete history).");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        subscription.cancel();
    }

    /**
     * Demonstrates event sourcing pattern with StartFromFirst.
     */
    public void eventSourcingExample() {
        System.out.println("\n=== Event Sourcing Pattern ===\n");

        String accountChannel = "account-events";
        pubSubClient.createEventsStoreChannel(accountChannel);

        // Send account events
        System.out.println("Sending account events (deposits and withdrawals)...\n");

        String[] events = {
                "{\"type\":\"CREATED\",\"accountId\":\"ACC-001\"}",
                "{\"type\":\"DEPOSIT\",\"amount\":1000}",
                "{\"type\":\"WITHDRAWAL\",\"amount\":200}",
                "{\"type\":\"DEPOSIT\",\"amount\":500}",
                "{\"type\":\"WITHDRAWAL\",\"amount\":100}"
        };

        for (int i = 0; i < events.length; i++) {
            EventStoreMessage msg = EventStoreMessage.builder()
                    .id("event-" + (i + 1))
                    .channel(accountChannel)
                    .body(events[i].getBytes())
                    .build();
            pubSubClient.sendEventsStoreMessage(msg);
            System.out.println("  Event " + (i + 1) + ": " + events[i]);
        }

        // Rebuild state by replaying from first
        System.out.println("\nRebuilding account state by replaying all events...\n");

        AtomicInteger balance = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger eventCount = new AtomicInteger(0);

        Consumer<EventStoreMessageReceived> rebuilder = event -> {
            String body = new String(event.getBody());
            int n = eventCount.incrementAndGet();

            // Simple event processing
            if (body.contains("DEPOSIT")) {
                int amount = extractAmount(body);
                balance.addAndGet(amount);
                System.out.println("  Event " + n + ": DEPOSIT +" + amount + " -> Balance: " + balance.get());
            } else if (body.contains("WITHDRAWAL")) {
                int amount = extractAmount(body);
                balance.addAndGet(-amount);
                System.out.println("  Event " + n + ": WITHDRAWAL -" + amount + " -> Balance: " + balance.get());
            } else if (body.contains("CREATED")) {
                System.out.println("  Event " + n + ": ACCOUNT CREATED -> Balance: " + balance.get());
            }

            if (n >= events.length) {
                latch.countDown();
            }
        };

        EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                .channel(accountChannel)
                .eventsStoreType(EventsStoreType.StartFromFirst)
                .onReceiveEventCallback(rebuilder)
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        pubSubClient.subscribeToEventsStore(subscription);

        try {
            latch.await(10, TimeUnit.SECONDS);
            System.out.println("\n═══════════════════════════════════════");
            System.out.println("State rebuilt. Final balance: $" + balance.get());
            System.out.println("═══════════════════════════════════════\n");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        subscription.cancel();
        pubSubClient.deleteEventsStoreChannel(accountChannel);
    }

    /**
     * Helper method to extract amount from event JSON.
     */
    private int extractAmount(String json) {
        // Simple extraction - in production use proper JSON parsing
        int start = json.indexOf("\"amount\":") + 9;
        int end = json.indexOf("}", start);
        return Integer.parseInt(json.substring(start, end));
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
     * Main method demonstrating StartFromFirst subscription.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║        EventsStore StartFromFirst Examples                   ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        EventsStoreStartFromFirstExample example = new EventsStoreStartFromFirstExample();

        try {
            // Send historical messages first
            example.sendHistoricalMessages(5);

            // Subscribe and receive all from beginning
            example.subscribeFromFirstExample();

            // New subscriber catches up
            example.newSubscriberExample();

            // Event sourcing pattern
            example.eventSourcingExample();

        } finally {
            example.cleanup();
        }

        System.out.println("StartFromFirst examples completed.");
    }
}
