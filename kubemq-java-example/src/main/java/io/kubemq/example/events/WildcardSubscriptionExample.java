package io.kubemq.example.events;

import io.kubemq.sdk.pubsub.EventMessage;
import io.kubemq.sdk.pubsub.EventsSubscription;
import io.kubemq.sdk.pubsub.PubSubClient;

/**
 * Wildcard Subscription Example
 *
 * Demonstrates subscribing to events using wildcard channel patterns (* and >).
 */
public class WildcardSubscriptionExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-events-wildcard-subscription-client";

    public static void main(String[] args) throws InterruptedException {
        // Create a client connected to the KubeMQ server
        PubSubClient client = PubSubClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID)
                .build();

        EventsSubscription singleLevel = EventsSubscription.builder()
                .channel("java-events.orders.*")
                .group("")
                .onReceiveEventCallback(event ->
                    System.out.println("Single-level wildcard received: " + event.getChannel()))
                .onErrorCallback(err ->
                    System.err.println("Error: " + err.getMessage()))
                .build();

        // Subscribe with single-level wildcard (* matches one token)
        client.subscribeToEvents(singleLevel);
        System.out.println("Subscribed with single-level wildcard: java-events.orders.*");

        EventsSubscription multiLevel = EventsSubscription.builder()
                .channel("java-events.>")
                .group("")
                .onReceiveEventCallback(event ->
                    System.out.println("Multi-level wildcard received: " + event.getChannel()))
                .onErrorCallback(err ->
                    System.err.println("Error: " + err.getMessage()))
                .build();

        // Subscribe with multi-level wildcard (> matches one or more tokens)
        client.subscribeToEvents(multiLevel);
        System.out.println("Subscribed with multi-level wildcard: java-events.>");

        // Send events to different channels to demonstrate wildcard matching
        String[] channels = {"java-events.orders.us", "java-events.orders.eu", "java-events.inventory.update"};
        for (String ch : channels) {
            EventMessage msg = EventMessage.builder()
                    .channel(ch)
                    .body(("hello from " + ch).getBytes())
                    .metadata("wildcard-test")
                    .build();
            client.sendEventsMessage(msg);
            System.out.println("Published to: " + ch);
        }

        // Wait for events to be delivered
        Thread.sleep(3000);
        // Clean up resources
        client.close();
        System.out.println("Wildcard subscription example completed.");
    }
}
