package io.kubemq.example.queues;

import io.kubemq.sdk.queues.*;
import java.util.UUID;

/**
 * AckAll Example
 *
 * Demonstrates acknowledging all messages in a single poll response using ackAll().
 */
public class AckAllExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queues-ack-all-client";
    private static final String CHANNEL = "java-queues.ack-all";

    public static void main(String[] args) {
        // Create a queues client connected to the KubeMQ server
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            // Create the queue channel
            client.createQueuesChannel(CHANNEL);

            // Send messages to the queue
            for (int i = 1; i <= 5; i++) {
                client.sendQueuesMessage(QueueMessage.builder()
                        .id(UUID.randomUUID().toString()).channel(CHANNEL)
                        .body(("Message " + i).getBytes()).build());
            }
            System.out.println("Sent 5 messages.\n");

            // Receive messages from the queue
            QueuesPollResponse response = client.receiveQueuesMessages(QueuesPollRequest.builder()
                    .channel(CHANNEL).pollMaxMessages(10).pollWaitTimeoutInSeconds(5).build());

            if (!response.isError()) {
                System.out.println("Received " + response.getMessages().size() + " messages.");
                response.getMessages().forEach(msg ->
                    System.out.println("  " + new String(msg.getBody())));

                // Acknowledge all messages in the response at once
                response.ackAll();
                System.out.println("\nAll messages acknowledged with ackAll().");
            }

            // Clean up resources
            client.deleteQueuesChannel(CHANNEL);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
