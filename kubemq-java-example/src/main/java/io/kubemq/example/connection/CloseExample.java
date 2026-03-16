package io.kubemq.example.connection;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.cq.CQClient;

/**
 * Close Example
 *
 * Demonstrates proper client lifecycle management: creating, using,
 * and closing KubeMQ clients. Shows try-with-resources and explicit close patterns.
 */
public class CloseExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-connection-close-client";

    public void tryWithResourcesExample() {
        System.out.println("=== Try-With-Resources Pattern ===\n");

        // Client auto-closes when leaving try block
        try (QueuesClient client = QueuesClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID + "-twr")
                .build()) {

            ServerInfo info = client.ping();
            System.out.println("Connected: " + info.getHost());
            System.out.println("Client will be auto-closed when leaving this block.\n");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }

        System.out.println("Client has been automatically closed.\n");
    }

    public void explicitCloseExample() {
        System.out.println("=== Explicit Close Pattern ===\n");

        // Create client and close explicitly in finally block
        PubSubClient client = null;
        try {
            client = PubSubClient.builder()
                    .address(ADDRESS)
                    .clientId(CLIENT_ID + "-explicit")
                    .build();

            ServerInfo info = client.ping();
            System.out.println("Connected: " + info.getHost());

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        } finally {
            if (client != null) {
                client.close();
                System.out.println("Client explicitly closed.\n");
            }
        }
    }

    public void multiClientCloseExample() {
        System.out.println("=== Multi-Client Close ===\n");

        // Create multiple clients and close in reverse order
        PubSubClient pubSubClient = null;
        QueuesClient queuesClient = null;
        CQClient cqClient = null;

        try {
            pubSubClient = PubSubClient.builder()
                    .address(ADDRESS).clientId(CLIENT_ID + "-pubsub").build();
            queuesClient = QueuesClient.builder()
                    .address(ADDRESS).clientId(CLIENT_ID + "-queues").build();
            cqClient = CQClient.builder()
                    .address(ADDRESS).clientId(CLIENT_ID + "-cq").build();

            System.out.println("Three clients created.");
            pubSubClient.ping();
            queuesClient.ping();
            cqClient.ping();
            System.out.println("All clients connected.\n");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        } finally {
            if (cqClient != null) { cqClient.close(); System.out.println("CQClient closed."); }
            if (queuesClient != null) { queuesClient.close(); System.out.println("QueuesClient closed."); }
            if (pubSubClient != null) { pubSubClient.close(); System.out.println("PubSubClient closed."); }
        }
    }

    public static void main(String[] args) {
        CloseExample example = new CloseExample();
        example.tryWithResourcesExample();
        example.explicitCloseExample();
        example.multiClientCloseExample();
        System.out.println("\nClose examples completed.");
    }
}
