package io.kubemq.example.errorhandling;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

/**
 * Reconnection Example
 *
 * Demonstrates built-in reconnection handling for KubeMQ subscriptions.
 */
public class ReconnectionExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-errorhandling-reconnection-client";

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Reconnection Handling ===\n");

        PubSubClient client = PubSubClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID)
                .reconnectIntervalSeconds(1)
                .build();

        client.ping();
        client.createEventsChannel("java-errorhandling.reconnect-test");

        EventsSubscription subscription = EventsSubscription.builder()
                .channel("java-errorhandling.reconnect-test")
                .onReceiveEventCallback(event ->
                        System.out.println("  Received: " + new String(event.getBody())))
                .onErrorCallback(error -> {
                    System.out.println("  [ERROR] " + error);
                    System.out.println("  (SDK will attempt reconnection automatically)");
                })
                .build();

        client.subscribeToEvents(subscription);
        System.out.println("Subscription active with automatic reconnection.\n");

        client.sendEventsMessage(EventMessage.builder()
                .channel("java-errorhandling.reconnect-test")
                .body("Test message".getBytes()).build());

        Thread.sleep(500);

        subscription.cancel();
        client.deleteEventsChannel("java-errorhandling.reconnect-test");
        client.close();
        System.out.println("Reconnection example completed.");
    }
}
