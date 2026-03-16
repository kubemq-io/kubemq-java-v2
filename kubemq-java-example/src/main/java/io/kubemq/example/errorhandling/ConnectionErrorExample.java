package io.kubemq.example.errorhandling;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.*;

/**
 * Connection Error Example
 *
 * Demonstrates handling connection errors when working with KubeMQ.
 */
public class ConnectionErrorExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-errorhandling-connection-error-client";

    public static void main(String[] args) {
        System.out.println("=== Connection Error Handling ===\n");

        // Test 1: Connection to invalid address (expect failure)
        System.out.println("1. Attempting connection to wrong address...");
        try {
            PubSubClient badClient = PubSubClient.builder()
                    .address("localhost:99999").clientId(CLIENT_ID).build();
            badClient.ping();
            badClient.close();
        } catch (Exception e) {
            System.out.println("   Failed (expected): " + e.getClass().getSimpleName());
        }

        // Test 2: Connection to valid address (expect success)
        System.out.println("\n2. Attempting connection to valid address...");
        try {
            PubSubClient goodClient = PubSubClient.builder()
                    .address(ADDRESS).clientId(CLIENT_ID).build();
            ServerInfo info = goodClient.ping();
            System.out.println("   Connected: " + info.getHost() + " v" + info.getVersion());
            goodClient.close();
        } catch (Exception e) {
            System.out.println("   Failed: " + e.getMessage());
        }

        // Test 3: Queue operations with error checking
        System.out.println("\n3. Queue operation error handling...");
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            client.ping();
            String testQueue = "java-errorhandling.error-test";
            client.createQueuesChannel(testQueue);

            QueueSendResult result = client.sendQueuesMessage(QueueMessage.builder()
                    .channel(testQueue).body("Test".getBytes()).build());
            System.out.println("   Send error: " + result.isError());

            QueuesPollResponse response = client.receiveQueuesMessages(QueuesPollRequest.builder()
                    .channel(testQueue).pollMaxMessages(10).pollWaitTimeoutInSeconds(2).build());
            System.out.println("   Receive error: " + response.isError());
            response.getMessages().forEach(QueueMessageReceived::ack);

            client.deleteQueuesChannel(testQueue);
        } catch (Exception e) {
            System.out.println("   Error: " + e.getMessage());
        }

        System.out.println("\nConnection error examples completed.");
    }
}
