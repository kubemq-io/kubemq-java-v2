package io.kubemq.example.management;

import io.kubemq.sdk.cq.CQChannel;
import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.pubsub.PubSubChannel;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.QueuesChannel;
import io.kubemq.sdk.queues.QueuesClient;
import java.util.List;

public class ListChannelsExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-management-list-channels-client";

    public static void main(String[] args) {
        System.out.println("=== List Channels ===\n");

        // List PubSub channels (events and events-store)
        try (PubSubClient pubsub = PubSubClient.builder().address(ADDRESS).clientId(CLIENT_ID + "-pubsub").build()) {
            List<PubSubChannel> events = pubsub.listEventsChannels("java-");
            System.out.println("Events channels (" + events.size() + "):");
            events.forEach(ch -> System.out.println("  " + ch.getName()));

            List<PubSubChannel> stores = pubsub.listEventsStoreChannels("java-");
            System.out.println("EventsStore channels (" + stores.size() + "):");
            stores.forEach(ch -> System.out.println("  " + ch.getName()));
        }

        // List Queues channels
        try (QueuesClient queues = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID + "-queues").build()) {
            List<QueuesChannel> qChs = queues.listQueuesChannels("java-");
            System.out.println("Queues channels (" + qChs.size() + "):");
            qChs.forEach(ch -> System.out.println("  " + ch.getName()));
        }

        // List Commands and Queries channels
        try (CQClient cq = CQClient.builder().address(ADDRESS).clientId(CLIENT_ID + "-cq").build()) {
            List<CQChannel> cmds = cq.listCommandsChannels("java-");
            System.out.println("Commands channels (" + cmds.size() + "):");
            cmds.forEach(ch -> System.out.println("  " + ch.getName()));

            List<CQChannel> queries = cq.listQueriesChannels("java-");
            System.out.println("Queries channels (" + queries.size() + "):");
            queries.forEach(ch -> System.out.println("  " + ch.getName()));
        }
    }
}
