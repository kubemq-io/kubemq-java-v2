package io.kubemq.example.patterns;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fan-Out Pattern Example
 *
 * Demonstrates the fan-out pattern where one publisher sends to multiple subscribers.
 */
public class FanOutExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-patterns-fan-out-client";
    private static final String CHANNEL = "java-patterns.fan-out";

    public static void main(String[] args) throws InterruptedException {
        PubSubClient client = PubSubClient.builder().address(ADDRESS).clientId(CLIENT_ID).build();
        ServerInfo info = client.ping();
        System.out.println("Connected to: " + info.getHost());
        client.createEventsChannel(CHANNEL);

        int numSubscribers = 3;
        int numMessages = 3;
        AtomicInteger[] counts = new AtomicInteger[numSubscribers];
        CountDownLatch latch = new CountDownLatch(numSubscribers * numMessages);
        EventsSubscription[] subs = new EventsSubscription[numSubscribers];

        for (int i = 0; i < numSubscribers; i++) {
            final int id = i + 1;
            counts[i] = new AtomicInteger(0);
            final AtomicInteger counter = counts[i];

            subs[i] = EventsSubscription.builder()
                    .channel(CHANNEL)
                    .onReceiveEventCallback(event -> {
                        counter.incrementAndGet();
                        System.out.println("  Subscriber " + id + ": " + new String(event.getBody()));
                        latch.countDown();
                    })
                    .onErrorCallback(err -> {}).build();
            client.subscribeToEvents(subs[i]);
        }
        Thread.sleep(500);

        System.out.println("Publishing " + numMessages + " messages to " + numSubscribers + " subscribers...\n");
        for (int i = 1; i <= numMessages; i++) {
            client.sendEventsMessage(EventMessage.builder()
                    .id("fan-" + i).channel(CHANNEL)
                    .body(("Broadcast #" + i).getBytes()).build());
            Thread.sleep(100);
        }

        latch.await(5, TimeUnit.SECONDS);
        System.out.println("\nResults:");
        for (int i = 0; i < numSubscribers; i++) {
            System.out.println("  Subscriber " + (i + 1) + ": " + counts[i].get() + " messages");
        }

        for (EventsSubscription s : subs) { s.cancel(); }
        client.deleteEventsChannel(CHANNEL);
        client.close();
    }
}
