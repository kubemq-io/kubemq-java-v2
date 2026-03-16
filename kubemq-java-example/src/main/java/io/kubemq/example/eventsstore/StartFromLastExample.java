package io.kubemq.example.eventsstore;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * StartFromLast Example
 *
 * Demonstrates subscribing with StartFromLast which receives the most recent
 * stored message plus all new ones.
 */
public class StartFromLastExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-eventsstore-start-from-last-client";
    private static final String CHANNEL = "java-eventsstore.start-from-last";

    public static void main(String[] args) throws InterruptedException {
        // Create a client connected to the KubeMQ server
        PubSubClient client = PubSubClient.builder().address(ADDRESS).clientId(CLIENT_ID).build();
        client.ping();
        // Create the events store channel
        client.createEventsStoreChannel(CHANNEL);

        // Send historical messages before subscribing
        for (int i = 1; i <= 5; i++) {
            client.sendEventsStoreMessage(EventStoreMessage.builder()
                    .id("hist-" + i).channel(CHANNEL)
                    .body(("Historical #" + i).getBytes()).build());
        }
        System.out.println("Sent 5 historical messages.\n");

        AtomicInteger received = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);

        EventsStoreSubscription sub = EventsStoreSubscription.builder()
                .channel(CHANNEL)
                .eventsStoreType(EventsStoreType.StartFromLast)
                .onReceiveEventCallback(e -> {
                    int n = received.incrementAndGet();
                    String label = (n == 1) ? "LAST" : "NEW " + (n - 1);
                    System.out.println("  [" + label + "] " + new String(e.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        // Subscribe with StartFromLast (most recent + new messages only)
        client.subscribeToEventsStore(sub);
        Thread.sleep(500);

        // Send new messages after subscribing
        for (int i = 1; i <= 2; i++) {
            client.sendEventsStoreMessage(EventStoreMessage.builder()
                    .id("new-" + i).channel(CHANNEL)
                    .body(("New message #" + i).getBytes()).build());
            Thread.sleep(200);
        }

        latch.await(5, TimeUnit.SECONDS);
        System.out.println("\nReceived " + received.get() + " (1 last + 2 new).");

        // Clean up resources
        sub.cancel();
        client.deleteEventsStoreChannel(CHANNEL);
        client.close();
    }
}
