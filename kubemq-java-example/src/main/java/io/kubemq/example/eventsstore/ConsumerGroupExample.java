package io.kubemq.example.eventsstore;

import io.kubemq.sdk.pubsub.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Consumer Group Example (EventsStore)
 *
 * Demonstrates load-balanced EventsStore consumption using subscription groups.
 */
public class ConsumerGroupExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-eventsstore-consumer-group-client";
    private static final String CHANNEL = "java-eventsstore.consumer-group";

    public static void main(String[] args) throws InterruptedException {
        PubSubClient client = PubSubClient.builder().address(ADDRESS).clientId(CLIENT_ID).build();
        client.ping();
        client.createEventsStoreChannel(CHANNEL);

        String groupName = "store-processors";
        AtomicInteger proc1 = new AtomicInteger(0);
        AtomicInteger proc2 = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(6);

        EventsStoreSubscription sub1 = EventsStoreSubscription.builder()
                .channel(CHANNEL).group(groupName)
                .eventsStoreType(EventsStoreType.StartNewOnly)
                .onReceiveEventCallback(e -> { proc1.incrementAndGet(); System.out.println("  Processor 1: " + new String(e.getBody())); latch.countDown(); })
                .onErrorCallback(err -> {}).build();

        EventsStoreSubscription sub2 = EventsStoreSubscription.builder()
                .channel(CHANNEL).group(groupName)
                .eventsStoreType(EventsStoreType.StartNewOnly)
                .onReceiveEventCallback(e -> { proc2.incrementAndGet(); System.out.println("  Processor 2: " + new String(e.getBody())); latch.countDown(); })
                .onErrorCallback(err -> {}).build();

        client.subscribeToEventsStore(sub1);
        client.subscribeToEventsStore(sub2);
        System.out.println("Two processors in group: " + groupName);
        Thread.sleep(500);

        for (int i = 1; i <= 6; i++) {
            client.sendEventsStoreMessage(EventStoreMessage.builder()
                    .id("event-" + i).channel(CHANNEL)
                    .body(("Event #" + i).getBytes()).build());
            Thread.sleep(100);
        }

        latch.await(5, TimeUnit.SECONDS);
        System.out.println("\nProcessor 1: " + proc1.get() + ", Processor 2: " + proc2.get());

        sub1.cancel(); sub2.cancel();
        client.deleteEventsStoreChannel(CHANNEL);
        client.close();
    }
}
