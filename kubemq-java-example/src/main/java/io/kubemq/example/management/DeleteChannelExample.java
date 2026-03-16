package io.kubemq.example.management;

import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.QueuesClient;

public class DeleteChannelExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-management-delete-channel-client";

    public static void main(String[] args) {
        System.out.println("=== Delete Channels ===\n");

        try (PubSubClient pubsub = PubSubClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            pubsub.createEventsChannel("java-management.delete-events-test");
            System.out.println("Created events channel.");
            pubsub.deleteEventsChannel("java-management.delete-events-test");
            System.out.println("Deleted events channel.");
        }

        try (QueuesClient queues = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            queues.createQueuesChannel("java-management.delete-queues-test");
            System.out.println("Created queues channel.");
            queues.deleteQueuesChannel("java-management.delete-queues-test");
            System.out.println("Deleted queues channel.");
        }

        System.out.println("\nDelete channel examples completed.");
    }
}
