package io.kubemq.example.management;

import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.QueuesClient;
import java.util.List;

/**
 * Demonstrates the generic createChannel / deleteChannel / listChannels methods available on all
 * client classes. These accept a channel type string ("events", "events_store", "commands",
 * "queries", "queues") so callers don't need separate typed methods.
 */
public class GenericChannelManagementExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-generic-channel-mgmt";

    public static void main(String[] args) {
        System.out.println("=== Generic Channel Management ===\n");

        // --- PubSubClient ---
        try (PubSubClient pubsub = PubSubClient.builder()
                .address(ADDRESS).clientId(CLIENT_ID + "-pubsub").build()) {

            pubsub.createChannel("generic-test.events", "events");
            System.out.println("PubSubClient: created events channel via generic API");

            pubsub.createChannel("generic-test.store", "events_store");
            System.out.println("PubSubClient: created events_store channel via generic API");

            List<?> channels = pubsub.listChannels("events", "generic-test");
            System.out.println("PubSubClient: listed events channels (" + channels.size() + ")");

            pubsub.deleteChannel("generic-test.events", "events");
            pubsub.deleteChannel("generic-test.store", "events_store");
            System.out.println("PubSubClient: deleted channels via generic API");
        }

        // --- CQClient ---
        try (CQClient cq = CQClient.builder()
                .address(ADDRESS).clientId(CLIENT_ID + "-cq").build()) {

            cq.createChannel("generic-test.commands", "commands");
            System.out.println("CQClient: created commands channel via generic API");

            cq.createChannel("generic-test.queries", "queries");
            System.out.println("CQClient: created queries channel via generic API");

            List<?> cmds = cq.listChannels("commands", "generic-test");
            System.out.println("CQClient: listed commands channels (" + cmds.size() + ")");

            cq.deleteChannel("generic-test.commands", "commands");
            cq.deleteChannel("generic-test.queries", "queries");
            System.out.println("CQClient: deleted channels via generic API");
        }

        // --- QueuesClient ---
        try (QueuesClient queues = QueuesClient.builder()
                .address(ADDRESS).clientId(CLIENT_ID + "-queues").build()) {

            queues.createChannel("generic-test.queues", "queues");
            System.out.println("QueuesClient: created queues channel via generic API");

            List<?> qChs = queues.listChannels("queues", "generic-test");
            System.out.println("QueuesClient: listed queues channels (" + qChs.size() + ")");

            queues.deleteChannel("generic-test.queues", "queues");
            System.out.println("QueuesClient: deleted queues channel via generic API");
        }

        System.out.println("\nGeneric channel management examples completed.");
    }
}
