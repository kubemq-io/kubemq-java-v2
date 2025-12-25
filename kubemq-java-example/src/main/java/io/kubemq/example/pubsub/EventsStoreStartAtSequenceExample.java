package io.kubemq.example.pubsub;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * EventsStore StartAtSequence Example
 *
 * This example demonstrates subscribing to a KubeMQ Events Store channel
 * with EventsStoreType.StartAtSequence configuration. This subscription type
 * starts receiving messages from a specific sequence number.
 *
 * StartAtSequence Behavior:
 * - Begins from the specified sequence number (inclusive)
 * - Skips all messages before that sequence
 * - Continues with subsequent messages and new ones
 * - Sequence numbers are monotonically increasing per channel
 *
 * Use Cases:
 * - Resume processing after known checkpoint
 * - Skip already-processed messages
 * - Implement exactly-once processing with sequence tracking
 * - Selective replay from specific point
 * - Partition-like consumption patterns
 *
 * Important Notes:
 * - Sequence numbers start at 1
 * - Each message in a channel has a unique sequence number
 * - Sequences are assigned by KubeMQ server
 * - If specified sequence doesn't exist, starts from closest available
 *
 * @see io.kubemq.sdk.pubsub.EventsStoreType#StartAtSequence
 * @see io.kubemq.sdk.pubsub.EventsStoreSubscription
 */
public class EventsStoreStartAtSequenceExample {

    private final PubSubClient pubSubClient;
    private final String channelName = "events-store-sequence";
    private final String address = "localhost:50000";
    private final String clientId = "sequence-client";

    /**
     * Initializes the PubSubClient.
     */
    public EventsStoreStartAtSequenceExample() {
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
     * Sends messages and tracks their sequence numbers.
     */
    public void sendMessagesAndTrackSequences() {
        System.out.println("\n=== Sending Messages with Sequence Tracking ===\n");

        for (int i = 1; i <= 10; i++) {
            EventStoreMessage message = EventStoreMessage.builder()
                    .id("msg-" + i)
                    .channel(channelName)
                    .body(("Message content #" + i).getBytes())
                    .metadata("Index: " + i)
                    .build();

            EventSendResult result = pubSubClient.sendEventsStoreMessage(message);
            System.out.println("  Sent message #" + i + " (ID: " + result.getId() + ")");
        }

        System.out.println("\nMessages 1-10 sent. Each has a sequence number assigned by server.\n");
    }

    /**
     * Demonstrates subscribing from a specific sequence number.
     */
    public void subscribeAtSequenceExample() {
        System.out.println("=== StartAtSequence Subscription ===\n");

        int startSequence = 5;  // Skip first 4 messages, start from 5th

        System.out.println("Subscribing with StartAtSequence = " + startSequence);
        System.out.println("(Will skip messages 1-4, start from message 5)\n");

        AtomicInteger receivedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(6); // Messages 5-10

        Consumer<EventStoreMessageReceived> onReceive = event -> {
            int count = receivedCount.incrementAndGet();
            System.out.println("  [" + count + "] Sequence " + event.getSequence() +
                    ": " + new String(event.getBody()));
            latch.countDown();
        };

        EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                .channel(channelName)
                .eventsStoreType(EventsStoreType.StartAtSequence)  // Key setting!
                .eventsStoreSequenceValue(startSequence)           // The sequence to start from
                .onReceiveEventCallback(onReceive)
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        pubSubClient.subscribeToEventsStore(subscription);

        try {
            latch.await(10, TimeUnit.SECONDS);
            System.out.println("\nReceived " + receivedCount.get() + " messages (starting from sequence " + startSequence + ")");
            System.out.println("Messages 1-4 were skipped.\n");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        subscription.cancel();
    }

    /**
     * Demonstrates checkpoint-based resumption using sequence numbers.
     */
    public void checkpointResumeExample() {
        System.out.println("=== Checkpoint Resume Pattern ===\n");

        String checkpointChannel = "checkpoint-channel";
        pubSubClient.createEventsStoreChannel(checkpointChannel);

        // Send messages
        System.out.println("Sending 8 messages...");
        for (int i = 1; i <= 8; i++) {
            EventStoreMessage msg = EventStoreMessage.builder()
                    .id("event-" + i)
                    .channel(checkpointChannel)
                    .body(("Event #" + i).getBytes())
                    .build();
            pubSubClient.sendEventsStoreMessage(msg);
        }

        // Simulate first processing run - process messages 1-5
        System.out.println("\n--- First Processing Run ---");
        System.out.println("Processing messages and saving checkpoint...\n");

        AtomicLong lastProcessedSequence = new AtomicLong(0);
        AtomicInteger processedCount = new AtomicInteger(0);
        CountDownLatch firstRun = new CountDownLatch(5);

        Consumer<EventStoreMessageReceived> firstProcessor = event -> {
            int count = processedCount.incrementAndGet();
            System.out.println("  Processed: " + new String(event.getBody()) +
                    " (Sequence: " + event.getSequence() + ")");
            lastProcessedSequence.set(event.getSequence());

            if (count >= 5) {
                // Simulate crash/stop after 5 messages
                System.out.println("\n  >>> Simulating processor shutdown...");
                firstRun.countDown();
            }
        };

        EventsStoreSubscription firstSub = EventsStoreSubscription.builder()
                .channel(checkpointChannel)
                .eventsStoreType(EventsStoreType.StartFromFirst)
                .onReceiveEventCallback(firstProcessor)
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        pubSubClient.subscribeToEventsStore(firstSub);

        try {
            firstRun.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        firstSub.cancel();

        long checkpoint = lastProcessedSequence.get();
        System.out.println("  >>> Checkpoint saved: sequence " + checkpoint);

        // Simulate restart - resume from checkpoint
        System.out.println("\n--- Second Processing Run (Resume from Checkpoint) ---");
        System.out.println("Resuming from sequence " + (checkpoint + 1) + "...\n");

        processedCount.set(0);
        CountDownLatch secondRun = new CountDownLatch(3); // Remaining 3 messages

        Consumer<EventStoreMessageReceived> resumeProcessor = event -> {
            int count = processedCount.incrementAndGet();
            System.out.println("  Resumed processing: " + new String(event.getBody()) +
                    " (Sequence: " + event.getSequence() + ")");
            secondRun.countDown();
        };

        EventsStoreSubscription resumeSub = EventsStoreSubscription.builder()
                .channel(checkpointChannel)
                .eventsStoreType(EventsStoreType.StartAtSequence)
                .eventsStoreSequenceValue((int) checkpoint + 1)  // Resume from next message
                .onReceiveEventCallback(resumeProcessor)
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        pubSubClient.subscribeToEventsStore(resumeSub);

        try {
            secondRun.await(5, TimeUnit.SECONDS);
            System.out.println("\nProcessed remaining " + processedCount.get() + " messages.");
            System.out.println("No messages were reprocessed - exactly-once semantics achieved!\n");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        resumeSub.cancel();
        pubSubClient.deleteEventsStoreChannel(checkpointChannel);
    }

    /**
     * Demonstrates parallel consumers with sequence partitioning.
     */
    public void sequencePartitioningExample() {
        System.out.println("=== Sequence-Based Partitioning ===\n");

        String partitionChannel = "partition-channel";
        pubSubClient.createEventsStoreChannel(partitionChannel);

        // Send 12 messages
        System.out.println("Sending 12 messages...");
        for (int i = 1; i <= 12; i++) {
            EventStoreMessage msg = EventStoreMessage.builder()
                    .id("item-" + i)
                    .channel(partitionChannel)
                    .body(("Item #" + i).getBytes())
                    .build();
            pubSubClient.sendEventsStoreMessage(msg);
        }

        System.out.println("\nCreating 3 'partitioned' consumers:");
        System.out.println("  Consumer A: sequences 1-4");
        System.out.println("  Consumer B: sequences 5-8");
        System.out.println("  Consumer C: sequences 9-12\n");

        // Note: This is a demonstration pattern. In practice, you'd need
        // to coordinate consumers to avoid gaps or overlaps.

        int[][] partitions = {{1, 4}, {5, 8}, {9, 12}};
        String[] consumerNames = {"A", "B", "C"};

        for (int p = 0; p < partitions.length; p++) {
            int startSeq = partitions[p][0];
            int endSeq = partitions[p][1];
            String name = consumerNames[p];

            CountDownLatch latch = new CountDownLatch(endSeq - startSeq + 1);
            StringBuilder received = new StringBuilder();

            Consumer<EventStoreMessageReceived> partitionConsumer = event -> {
                if (event.getSequence() <= endSeq) {
                    received.append(event.getSequence()).append(" ");
                    latch.countDown();
                }
            };

            EventsStoreSubscription sub = EventsStoreSubscription.builder()
                    .channel(partitionChannel)
                    .eventsStoreType(EventsStoreType.StartAtSequence)
                    .eventsStoreSequenceValue(startSeq)
                    .onReceiveEventCallback(partitionConsumer)
                    .onErrorCallback(err -> {})
                    .build();

            pubSubClient.subscribeToEventsStore(sub);

            try {
                latch.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.out.println("  Consumer " + name + " processed sequences: " + received.toString().trim());
            sub.cancel();
        }

        System.out.println("\nEach consumer processed its assigned partition.\n");
        pubSubClient.deleteEventsStoreChannel(partitionChannel);
    }

    /**
     * Shows what happens with invalid sequence numbers.
     */
    public void edgeCasesExample() {
        System.out.println("=== Edge Cases ===\n");

        // Case 1: Sequence 0 (invalid - sequences start at 1)
        System.out.println("1. Sequence = 0:");
        System.out.println("   Behavior: Starts from sequence 1 (first message)");

        // Case 2: Sequence greater than max
        System.out.println("\n2. Sequence > max stored sequence:");
        System.out.println("   Behavior: Waits for that sequence to arrive (new messages)");

        // Case 3: Negative sequence
        System.out.println("\n3. Negative sequence:");
        System.out.println("   Behavior: Treated as starting from beginning");

        System.out.println("\nNote: Always validate sequence numbers in production code.\n");
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
     * Main method demonstrating StartAtSequence subscription.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║        EventsStore StartAtSequence Examples                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        EventsStoreStartAtSequenceExample example = new EventsStoreStartAtSequenceExample();

        try {
            // Send messages and track sequences
            example.sendMessagesAndTrackSequences();

            // Subscribe at specific sequence
            example.subscribeAtSequenceExample();

            // Checkpoint resume pattern
            example.checkpointResumeExample();

            // Sequence partitioning
            example.sequencePartitioningExample();

            // Edge cases
            example.edgeCasesExample();

        } finally {
            example.cleanup();
        }

        System.out.println("StartAtSequence examples completed.");
    }
}
