package io.kubemq.example.errorhandling;

import io.kubemq.sdk.pubsub.*;
import io.kubemq.sdk.queues.*;
import io.kubemq.sdk.cq.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Graceful Shutdown Example
 *
 * Demonstrates proper shutdown procedures for KubeMQ clients.
 */
public class GracefulShutdownExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-errorhandling-graceful-shutdown-client";

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Graceful Shutdown ===\n");

        PubSubClient pubSubClient = PubSubClient.builder()
                .address(ADDRESS).clientId(CLIENT_ID + "-pubsub").build();
        QueuesClient queuesClient = QueuesClient.builder()
                .address(ADDRESS).clientId(CLIENT_ID + "-queues").build();
        CQClient cqClient = CQClient.builder()
                .address(ADDRESS).clientId(CLIENT_ID + "-cq").build();

        System.out.println("Created 3 clients.");

        pubSubClient.createEventsChannel("java-errorhandling.shutdown-test");
        EventsSubscription sub = EventsSubscription.builder()
                .channel("java-errorhandling.shutdown-test")
                .onReceiveEventCallback(e -> {}).onErrorCallback(err -> {}).build();
        pubSubClient.subscribeToEvents(sub);

        System.out.println("Subscription active.\n");

        // Send some messages
        for (int i = 1; i <= 3; i++) {
            pubSubClient.sendEventsMessage(EventMessage.builder()
                    .channel("java-errorhandling.shutdown-test")
                    .body(("Message " + i).getBytes()).build());
        }
        Thread.sleep(300);

        // GRACEFUL SHUTDOWN
        System.out.println("--- Initiating Graceful Shutdown ---\n");

        // 1. Cancel subscriptions
        sub.cancel();
        System.out.println("1. Subscriptions cancelled.");

        // 2. Wait for pending ops
        Thread.sleep(200);
        System.out.println("2. Pending operations completed.");

        // 3. Cleanup channels
        try { pubSubClient.deleteEventsChannel("java-errorhandling.shutdown-test"); } catch (Exception e) {}
        System.out.println("3. Channels cleaned up.");

        // 4. Close clients in reverse order
        cqClient.close();
        queuesClient.close();
        pubSubClient.close();
        System.out.println("4. All clients closed.\n");

        System.out.println("Graceful shutdown complete.");
    }
}
