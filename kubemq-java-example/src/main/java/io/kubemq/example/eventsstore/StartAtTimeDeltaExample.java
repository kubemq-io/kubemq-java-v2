package io.kubemq.example.eventsstore;

import io.kubemq.sdk.pubsub.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Start At Time Delta Example
 *
 * Demonstrates StartAtTimeDelta subscription that replays messages from a relative time.
 */
public class StartAtTimeDeltaExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-eventsstore-start-at-time-delta-client";
    private static final String CHANNEL = "java-eventsstore.start-at-time-delta";

    public static void main(String[] args) throws InterruptedException {
        // Create a client connected to the KubeMQ server
        PubSubClient client = PubSubClient.builder().address(ADDRESS).clientId(CLIENT_ID).build();
        client.ping();
        // Create the events store channel
        client.createEventsStoreChannel(CHANNEL);

        // Send messages to create history
        for (int i = 1; i <= 5; i++) {
            client.sendEventsStoreMessage(EventStoreMessage.builder()
                    .id("msg-" + i).channel(CHANNEL)
                    .body(("Event #" + i).getBytes()).build());
            Thread.sleep(100);
        }
        System.out.println("Sent 5 messages.\n");

        long deltaSeconds = 60L;
        AtomicInteger received = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(5);

        EventsStoreSubscription sub = EventsStoreSubscription.builder()
                .channel(CHANNEL)
                .eventsStoreType(EventsStoreType.StartAtTimeDelta)
                .eventsStoreSequenceValue(deltaSeconds)
                .onReceiveEventCallback(e -> {
                    received.incrementAndGet();
                    System.out.println("  Received: " + new String(e.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        // Subscribe with StartAtTimeDelta (replay from relative time)
        client.subscribeToEventsStore(sub);
        System.out.println("Subscribed with TimeDelta = " + deltaSeconds + "s.");

        latch.await(10, TimeUnit.SECONDS);
        System.out.println("\nReceived " + received.get() + " messages from the last " + deltaSeconds + "s.");

        // Clean up resources
        sub.cancel();
        client.deleteEventsStoreChannel(CHANNEL);
        client.close();
    }
}
