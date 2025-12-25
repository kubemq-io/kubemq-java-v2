package io.kubemq.example.pubsub;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Group Subscription Example
 *
 * This example demonstrates using subscription groups in KubeMQ PubSub.
 * Group subscriptions enable load balancing of messages across multiple consumers.
 *
 * How Groups Work:
 * - Subscribers with the same group name form a consumer group
 * - Each message is delivered to only ONE subscriber in the group
 * - Messages are distributed (load balanced) across group members
 * - Subscribers without a group receive ALL messages (broadcast)
 *
 * Behavior:
 * - Within a group: Competing consumers (one gets each message)
 * - Without a group: All subscribers receive all messages
 * - Multiple groups: Each group gets all messages (one member per group)
 *
 * Use Cases:
 * - Horizontal scaling of message processors
 * - Work distribution across multiple instances
 * - High availability (if one consumer fails, others continue)
 * - Parallel processing of events
 *
 * @see io.kubemq.sdk.pubsub.EventsSubscription#getGroup()
 * @see io.kubemq.sdk.pubsub.EventsStoreSubscription#getGroup()
 */
public class GroupSubscriptionExample {

    private final PubSubClient pubSubClient;
    private final String eventsChannel = "group-events-channel";
    private final String eventsStoreChannel = "group-store-channel";
    private final String address = "localhost:50000";
    private final String clientId = "group-example-client";

    /**
     * Initializes the PubSubClient.
     */
    public GroupSubscriptionExample() {
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
     * Demonstrates group subscription with Events (basic pub/sub).
     */
    public void eventsGroupSubscriptionExample() {
        System.out.println("\n=== Events Group Subscription ===\n");

        String groupName = "workers-group";
        int numWorkers = 3;
        int numMessages = 9;

        AtomicInteger[] workerCounts = new AtomicInteger[numWorkers];
        CountDownLatch latch = new CountDownLatch(numMessages);

        // Create multiple workers in the same group
        EventsSubscription[] subscriptions = new EventsSubscription[numWorkers];

        for (int i = 0; i < numWorkers; i++) {
            final int workerId = i + 1;
            workerCounts[i] = new AtomicInteger(0);
            final AtomicInteger counter = workerCounts[i];

            Consumer<EventMessageReceived> handler = event -> {
                counter.incrementAndGet();
                System.out.println("  Worker " + workerId + " received: " + new String(event.getBody()));
                latch.countDown();
            };

            subscriptions[i] = EventsSubscription.builder()
                    .channel(eventsChannel)
                    .group(groupName)  // Same group for all workers
                    .onReceiveEventCallback(handler)
                    .onErrorCallback(err -> System.err.println("Error: " + err))
                    .build();

            pubSubClient.subscribeToEvents(subscriptions[i]);
        }

        System.out.println("Created " + numWorkers + " workers in group: " + groupName + "\n");

        // Wait for subscriptions to be ready
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Send messages
        System.out.println("Sending " + numMessages + " messages...\n");

        for (int i = 1; i <= numMessages; i++) {
            EventMessage message = EventMessage.builder()
                    .id("msg-" + i)
                    .channel(eventsChannel)
                    .body(("Task #" + i).getBytes())
                    .build();

            pubSubClient.sendEventsMessage(message);

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Wait for all messages to be processed
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Show distribution
        System.out.println("\n─────────────────────────────────────");
        System.out.println("Message Distribution:");
        int total = 0;
        for (int i = 0; i < numWorkers; i++) {
            int count = workerCounts[i].get();
            total += count;
            System.out.println("  Worker " + (i + 1) + ": " + count + " messages");
        }
        System.out.println("  Total: " + total + " (each message delivered to exactly one worker)");
        System.out.println("─────────────────────────────────────\n");

        // Cancel subscriptions
        for (EventsSubscription sub : subscriptions) {
            sub.cancel();
        }
    }

    /**
     * Demonstrates group subscription with EventsStore.
     */
    public void eventsStoreGroupSubscriptionExample() {
        System.out.println("=== EventsStore Group Subscription ===\n");

        String groupName = "processors-group";
        AtomicInteger processor1Count = new AtomicInteger(0);
        AtomicInteger processor2Count = new AtomicInteger(0);

        CountDownLatch latch = new CountDownLatch(6);

        // Processor 1
        EventsStoreSubscription sub1 = EventsStoreSubscription.builder()
                .channel(eventsStoreChannel)
                .group(groupName)
                .eventsStoreType(EventsStoreType.StartNewOnly)
                .onReceiveEventCallback(e -> {
                    processor1Count.incrementAndGet();
                    System.out.println("  Processor 1: " + new String(e.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        // Processor 2
        EventsStoreSubscription sub2 = EventsStoreSubscription.builder()
                .channel(eventsStoreChannel)
                .group(groupName)
                .eventsStoreType(EventsStoreType.StartNewOnly)
                .onReceiveEventCallback(e -> {
                    processor2Count.incrementAndGet();
                    System.out.println("  Processor 2: " + new String(e.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        pubSubClient.subscribeToEventsStore(sub1);
        pubSubClient.subscribeToEventsStore(sub2);

        System.out.println("Two processors subscribed in group: " + groupName + "\n");

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Send messages
        for (int i = 1; i <= 6; i++) {
            EventStoreMessage msg = EventStoreMessage.builder()
                    .id("event-" + i)
                    .channel(eventsStoreChannel)
                    .body(("Event #" + i).getBytes())
                    .build();
            pubSubClient.sendEventsStoreMessage(msg);

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\nDistribution:");
        System.out.println("  Processor 1: " + processor1Count.get() + " events");
        System.out.println("  Processor 2: " + processor2Count.get() + " events\n");

        sub1.cancel();
        sub2.cancel();
    }

    /**
     * Demonstrates broadcast (no group) vs group behavior.
     */
    public void broadcastVsGroupExample() {
        System.out.println("=== Broadcast vs Group Comparison ===\n");

        // First: Broadcast (no group)
        System.out.println("1. BROADCAST MODE (no group - all receive all):\n");

        AtomicInteger broadcastSub1 = new AtomicInteger(0);
        AtomicInteger broadcastSub2 = new AtomicInteger(0);
        CountDownLatch broadcastLatch = new CountDownLatch(6); // 3 messages x 2 subscribers

        EventsSubscription bSub1 = EventsSubscription.builder()
                .channel(eventsChannel)
                // No group specified - broadcast mode
                .onReceiveEventCallback(e -> {
                    broadcastSub1.incrementAndGet();
                    System.out.println("    Subscriber A: " + new String(e.getBody()));
                    broadcastLatch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        EventsSubscription bSub2 = EventsSubscription.builder()
                .channel(eventsChannel)
                // No group specified - broadcast mode
                .onReceiveEventCallback(e -> {
                    broadcastSub2.incrementAndGet();
                    System.out.println("    Subscriber B: " + new String(e.getBody()));
                    broadcastLatch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        pubSubClient.subscribeToEvents(bSub1);
        pubSubClient.subscribeToEvents(bSub2);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (int i = 1; i <= 3; i++) {
            pubSubClient.sendEventsMessage(EventMessage.builder()
                    .id("broadcast-" + i)
                    .channel(eventsChannel)
                    .body(("Broadcast " + i).getBytes())
                    .build());
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            broadcastLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\n  Result: A=" + broadcastSub1.get() + ", B=" + broadcastSub2.get() +
                " (both received all 3 messages)\n");

        bSub1.cancel();
        bSub2.cancel();

        // Second: Group mode
        System.out.println("2. GROUP MODE (load balanced - each receives some):\n");

        AtomicInteger groupSub1 = new AtomicInteger(0);
        AtomicInteger groupSub2 = new AtomicInteger(0);
        CountDownLatch groupLatch = new CountDownLatch(3); // 3 messages total (distributed)

        EventsSubscription gSub1 = EventsSubscription.builder()
                .channel(eventsChannel)
                .group("my-group")  // Same group
                .onReceiveEventCallback(e -> {
                    groupSub1.incrementAndGet();
                    System.out.println("    Member X: " + new String(e.getBody()));
                    groupLatch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        EventsSubscription gSub2 = EventsSubscription.builder()
                .channel(eventsChannel)
                .group("my-group")  // Same group
                .onReceiveEventCallback(e -> {
                    groupSub2.incrementAndGet();
                    System.out.println("    Member Y: " + new String(e.getBody()));
                    groupLatch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        pubSubClient.subscribeToEvents(gSub1);
        pubSubClient.subscribeToEvents(gSub2);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (int i = 1; i <= 3; i++) {
            pubSubClient.sendEventsMessage(EventMessage.builder()
                    .id("grouped-" + i)
                    .channel(eventsChannel)
                    .body(("Grouped " + i).getBytes())
                    .build());
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            groupLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\n  Result: X=" + groupSub1.get() + ", Y=" + groupSub2.get() +
                " (total=3, distributed across group)\n");

        gSub1.cancel();
        gSub2.cancel();
    }

    /**
     * Demonstrates multiple groups on the same channel.
     */
    public void multipleGroupsExample() {
        System.out.println("=== Multiple Groups Example ===\n");

        System.out.println("Two groups subscribed to the same channel:");
        System.out.println("  - 'team-alpha' (2 members)");
        System.out.println("  - 'team-beta' (2 members)\n");

        AtomicInteger alpha1 = new AtomicInteger(0);
        AtomicInteger alpha2 = new AtomicInteger(0);
        AtomicInteger beta1 = new AtomicInteger(0);
        AtomicInteger beta2 = new AtomicInteger(0);

        CountDownLatch latch = new CountDownLatch(6); // 3 messages x 2 groups

        // Team Alpha
        EventsSubscription alphaSub1 = createGroupSubscription("team-alpha", "Alpha-1", alpha1, latch);
        EventsSubscription alphaSub2 = createGroupSubscription("team-alpha", "Alpha-2", alpha2, latch);

        // Team Beta
        EventsSubscription betaSub1 = createGroupSubscription("team-beta", "Beta-1", beta1, latch);
        EventsSubscription betaSub2 = createGroupSubscription("team-beta", "Beta-2", beta2, latch);

        pubSubClient.subscribeToEvents(alphaSub1);
        pubSubClient.subscribeToEvents(alphaSub2);
        pubSubClient.subscribeToEvents(betaSub1);
        pubSubClient.subscribeToEvents(betaSub2);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Send messages
        System.out.println("Sending 3 messages...\n");
        for (int i = 1; i <= 3; i++) {
            pubSubClient.sendEventsMessage(EventMessage.builder()
                    .id("multi-" + i)
                    .channel(eventsChannel)
                    .body(("Message " + i).getBytes())
                    .build());
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\nResults:");
        System.out.println("  Team Alpha: " + (alpha1.get() + alpha2.get()) + " total");
        System.out.println("    - Alpha-1: " + alpha1.get());
        System.out.println("    - Alpha-2: " + alpha2.get());
        System.out.println("  Team Beta: " + (beta1.get() + beta2.get()) + " total");
        System.out.println("    - Beta-1: " + beta1.get());
        System.out.println("    - Beta-2: " + beta2.get());
        System.out.println("\nEach group received all 3 messages (load balanced within group).\n");

        alphaSub1.cancel();
        alphaSub2.cancel();
        betaSub1.cancel();
        betaSub2.cancel();
    }

    private EventsSubscription createGroupSubscription(String group, String name,
                                                        AtomicInteger counter, CountDownLatch latch) {
        return EventsSubscription.builder()
                .channel(eventsChannel)
                .group(group)
                .onReceiveEventCallback(e -> {
                    counter.incrementAndGet();
                    System.out.println("    " + name + ": " + new String(e.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();
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
     * Main method demonstrating group subscriptions.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           Group Subscription Examples                        ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        GroupSubscriptionExample example = new GroupSubscriptionExample();

        try {
            // Events group subscription
            example.eventsGroupSubscriptionExample();

            // EventsStore group subscription
            example.eventsStoreGroupSubscriptionExample();

            // Broadcast vs group comparison
            example.broadcastVsGroupExample();

            // Multiple groups
            example.multipleGroupsExample();

        } finally {
            example.cleanup();
        }

        System.out.println("Group subscription examples completed.");
    }
}
