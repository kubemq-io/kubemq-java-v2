package io.kubemq.example.eventsstore;

import io.kubemq.sdk.pubsub.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replay From Sequence Example
 *
 * Demonstrates StartAtSequence subscription that replays from a specific sequence number.
 */
public class ReplayFromSequenceExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-eventsstore-replay-from-sequence-client";
    private static final String CHANNEL = "java-eventsstore.replay-from-sequence";

    public static void main(String[] args) throws InterruptedException {
        // Create a client connected to the KubeMQ server
        PubSubClient client = PubSubClient.builder().address(ADDRESS).clientId(CLIENT_ID).build();
        client.ping();
        // Create the events store channel
        client.createEventsStoreChannel(CHANNEL);

        // Send messages to create history
        for (int i = 1; i <= 10; i++) {
            client.sendEventsStoreMessage(EventStoreMessage.builder()
                    .id("msg-" + i).channel(CHANNEL)
                    .body(("Message #" + i).getBytes()).build());
        }
        System.out.println("Sent 10 messages.\n");

        long startSequence = 5L;
        AtomicInteger received = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(6);

        EventsStoreSubscription sub = EventsStoreSubscription.builder()
                .channel(CHANNEL)
                .eventsStoreType(EventsStoreType.StartAtSequence)
                .eventsStoreSequenceValue(startSequence)
                .onReceiveEventCallback(e -> {
                    int n = received.incrementAndGet();
                    System.out.println("  [" + n + "] Seq " + e.getSequence() + ": " + new String(e.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        // Subscribe with StartAtSequence (replay from specific sequence number)
        client.subscribeToEventsStore(sub);
        System.out.println("Subscribed at sequence " + startSequence + ".");

        latch.await(10, TimeUnit.SECONDS);
        System.out.println("\nReceived " + received.get() + " messages (starting from seq " + startSequence + ").");

        // Clean up resources
        sub.cancel();
        client.deleteEventsStoreChannel(CHANNEL);
        client.close();
    }
}
