package io.kubemq.example.eventsstore;

import io.kubemq.sdk.pubsub.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replay From Time Example
 *
 * Demonstrates StartAtTime subscription that replays from a specific timestamp.
 */
public class ReplayFromTimeExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-eventsstore-replay-from-time-client";
    private static final String CHANNEL = "java-eventsstore.replay-from-time";

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

        Instant startTime = Instant.now().minus(1, ChronoUnit.HOURS);
        AtomicInteger received = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(5);

        EventsStoreSubscription sub = EventsStoreSubscription.builder()
                .channel(CHANNEL)
                .eventsStoreType(EventsStoreType.StartAtTime)
                .eventsStoreStartTime(startTime)
                .onReceiveEventCallback(e -> {
                    received.incrementAndGet();
                    System.out.println("  Received: " + new String(e.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        // Subscribe with StartAtTime (replay from specific timestamp)
        client.subscribeToEventsStore(sub);
        System.out.println("Subscribed with StartAtTime (1 hour ago).");

        latch.await(10, TimeUnit.SECONDS);
        System.out.println("\nReceived " + received.get() + " messages.");

        // Clean up resources
        sub.cancel();
        client.deleteEventsStoreChannel(CHANNEL);
        client.close();
    }
}
