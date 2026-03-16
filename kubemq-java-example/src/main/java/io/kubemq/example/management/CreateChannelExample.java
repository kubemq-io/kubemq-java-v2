package io.kubemq.example.management;

import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.QueuesClient;

public class CreateChannelExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-management-create-channel-client";

    public static void main(String[] args) {
        System.out.println("=== Create Channels ===\n");

        try (PubSubClient pubsub = PubSubClient.builder().address(ADDRESS).clientId(CLIENT_ID + "-pubsub").build()) {
            pubsub.createEventsChannel("java-management.events-test");
            System.out.println("Events channel created.");
            pubsub.createEventsStoreChannel("java-management.store-test");
            System.out.println("EventsStore channel created.");
            pubsub.deleteEventsChannel("java-management.events-test");
            pubsub.deleteEventsStoreChannel("java-management.store-test");
        }

        try (QueuesClient queues = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID + "-queues").build()) {
            queues.createQueuesChannel("java-management.queues-test");
            System.out.println("Queues channel created.");
            queues.deleteQueuesChannel("java-management.queues-test");
        }

        try (CQClient cq = CQClient.builder().address(ADDRESS).clientId(CLIENT_ID + "-cq").build()) {
            cq.createCommandsChannel("java-management.commands-test");
            System.out.println("Commands channel created.");
            cq.createQueriesChannel("java-management.queries-test");
            System.out.println("Queries channel created.");
            cq.deleteCommandsChannel("java-management.commands-test");
            cq.deleteQueriesChannel("java-management.queries-test");
        }

        System.out.println("\nAll channels created and cleaned up.");
    }
}
