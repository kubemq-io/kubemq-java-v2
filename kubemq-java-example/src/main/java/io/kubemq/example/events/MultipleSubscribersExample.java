package io.kubemq.example.events;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multiple Subscribers Example
 *
 * Demonstrates multiple independent subscribers (no group) each receiving
 * all events -- broadcast mode.
 */
public class MultipleSubscribersExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-events-multiple-subscribers-client";
    private static final String CHANNEL = "java-events.multiple-subscribers";

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

        int numMessages = 3;
        AtomicInteger sub1Count = new AtomicInteger(0);
        AtomicInteger sub2Count = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(numMessages * 2);

        EventsSubscription subscription1 = EventsSubscription.builder()
                .channel(CHANNEL)
                .onReceiveEventCallback(event -> {
                    sub1Count.incrementAndGet();
                    System.out.println("  Subscriber A: " + new String(event.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        EventsSubscription subscription2 = EventsSubscription.builder()
                .channel(CHANNEL)
                .onReceiveEventCallback(event -> {
                    sub2Count.incrementAndGet();
                    System.out.println("  Subscriber B: " + new String(event.getBody()));
                    latch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        // Subscribe two independent subscribers (no group = broadcast to all)
        client.subscribeToEvents(subscription1);
        client.subscribeToEvents(subscription2);
        System.out.println("Two independent subscribers created (broadcast mode).\n");

        // Wait for subscribers to be ready
        Thread.sleep(500);

        // Send event messages (each subscriber receives all)
        for (int i = 1; i <= numMessages; i++) {
            client.sendEventsMessage(EventMessage.builder()
                    .id("msg-" + i)
                    .channel(CHANNEL)
                    .body(("Broadcast message #" + i).getBytes())
                    .build());
            Thread.sleep(100);
        }

        // Wait for both subscribers to receive all messages
        latch.await(5, TimeUnit.SECONDS);

        // Handle response: print counts per subscriber
        System.out.println("\nResults:");
        System.out.println("  Subscriber A received: " + sub1Count.get());
        System.out.println("  Subscriber B received: " + sub2Count.get());
        System.out.println("  Both received all " + numMessages + " messages (broadcast).");

        // Clean up resources
        subscription1.cancel();
        subscription2.cancel();
        client.deleteEventsChannel(CHANNEL);
        client.close();
        System.out.println("\nMultiple subscribers example completed.");
    }
}
