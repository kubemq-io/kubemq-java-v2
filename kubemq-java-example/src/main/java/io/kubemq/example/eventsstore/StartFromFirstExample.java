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
        PubSubClient client = PubSubClient.builder().address(ADDRESS).clientId(CLIENT_ID).build();
        client.ping();
        client.createEventsStoreChannel(CHANNEL);

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

        client.subscribeToEventsStore(sub);
        System.out.println("Subscribed with StartFromFirst.");

        latch.await(10, TimeUnit.SECONDS);
        System.out.println("\nReceived " + received.get() + " messages (complete history).");

        sub.cancel();
        client.deleteEventsStoreChannel(CHANNEL);
        client.close();
    }
}
