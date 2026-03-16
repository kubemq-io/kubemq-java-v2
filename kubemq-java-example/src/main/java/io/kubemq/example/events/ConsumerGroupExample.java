package io.kubemq.example.events;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Consumer Group Example for Events
 *
 * Demonstrates load-balanced event consumption using subscription groups.
 */
public class ConsumerGroupExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-events-consumer-group-client";
    private static final String CHANNEL = "java-events.consumer-group";

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

        String groupName = "workers-group";
        int numWorkers = 3;
        int numMessages = 9;

        AtomicInteger[] workerCounts = new AtomicInteger[numWorkers];
        CountDownLatch latch = new CountDownLatch(numMessages);
        EventsSubscription[] subscriptions = new EventsSubscription[numWorkers];

        for (int i = 0; i < numWorkers; i++) {
            final int workerId = i + 1;
            workerCounts[i] = new AtomicInteger(0);
            final AtomicInteger counter = workerCounts[i];

            subscriptions[i] = EventsSubscription.builder()
                    .channel(CHANNEL)
                    .group(groupName)
                    .onReceiveEventCallback(event -> {
                        counter.incrementAndGet();
                        System.out.println("  Worker " + workerId + " received: " + new String(event.getBody()));
                        latch.countDown();
                    })
                    .onErrorCallback(err -> System.err.println("Error: " + err))
                    .build();

            // Subscribe each worker to the channel with a shared group (load-balanced)
            client.subscribeToEvents(subscriptions[i]);
        }

        System.out.println("Created " + numWorkers + " workers in group: " + groupName);
        // Wait for subscribers to be ready
        Thread.sleep(500);

        // Send messages (distributed across workers in the group)
        System.out.println("Sending " + numMessages + " messages...\n");
        for (int i = 1; i <= numMessages; i++) {
            client.sendEventsMessage(EventMessage.builder()
                    .id("msg-" + i)
                    .channel(CHANNEL)
                    .body(("Task #" + i).getBytes())
                    .build());
            Thread.sleep(100);
        }

        // Wait for all messages to be received
        latch.await(10, TimeUnit.SECONDS);

        // Handle response: print distribution across workers
        System.out.println("\nMessage Distribution:");
        int total = 0;
        for (int i = 0; i < numWorkers; i++) {
            int count = workerCounts[i].get();
            total += count;
            System.out.println("  Worker " + (i + 1) + ": " + count + " messages");
        }
        System.out.println("  Total: " + total);

        // Clean up resources
        for (EventsSubscription sub : subscriptions) { sub.cancel(); }
        client.deleteEventsChannel(CHANNEL);
        client.close();
        System.out.println("\nConsumer group example completed.");
    }
}
