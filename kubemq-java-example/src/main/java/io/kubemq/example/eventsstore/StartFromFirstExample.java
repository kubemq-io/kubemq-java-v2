package io.kubemq.example.eventsstore;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * StartFromFirst Example
 *
 * Demonstrates subscribing with StartFromFirst to replay all messages from the beginning.
 */
public class StartFromFirstExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-eventsstore-start-from-first-client";
    private static final String CHANNEL = "java-eventsstore.start-from-first";

    public static void main(String[] args) throws InterruptedException {
        // Create a client connected to the KubeMQ server
        PubSubClient client = PubSubClient.builder().address(ADDRESS).clientId(CLIENT_ID).build();
        client.ping();
        // Create the events store channel
        client.createEventsStoreChannel(CHANNEL);

        // Send messages before subscribing (to create history)
        for (int i = 1; i <= 5; i++) {
            client.sendEventsStoreMessage(EventStoreMessage.builder()
                    .id("msg-" + i).channel(CHANNEL)
                    .body(("Message #" + i).getBytes()).build());
        }
        System.out.println("Sent 5 messages.\n");

        AtomicInteger received = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(5);

        EventsStoreSubscription sub = EventsStoreSubscription.builder()
                .channel(CHANNEL)
                .eventsStoreType(EventsStoreType.StartFromFirst)
                .onReceiveEventCallback(e -> {
                    int n = received.incrementAndGet();
                    System.out.println("  [" + n + "] Seq " + e.getSequence() + ": " + new String(e.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        // Subscribe with StartFromFirst to replay all messages from the beginning
        client.subscribeToEventsStore(sub);
        System.out.println("Subscribed with StartFromFirst.");

        // Wait for all historical messages to be received
        latch.await(10, TimeUnit.SECONDS);
        System.out.println("\nReceived " + received.get() + " messages (complete history).");

        // Clean up resources
        sub.cancel();
        client.deleteEventsStoreChannel(CHANNEL);
        client.close();
    }
}
