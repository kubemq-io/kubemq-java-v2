package io.kubemq.example.queues;

import io.kubemq.sdk.queues.*;
import java.util.UUID;

/**
 * Ack/Reject Example
 *
 * Demonstrates acknowledging and rejecting individual messages based on processing outcome.
 */
public class AckRejectExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queues-ack-reject-client";
    private static final String CHANNEL = "java-queues.ack-reject";

    public static void main(String[] args) {
        // Create a queues client connected to the KubeMQ server
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            // Create the queue channel
            client.createQueuesChannel(CHANNEL);

            // Send messages to the queue
            for (int i = 1; i <= 3; i++) {
                client.sendQueuesMessage(QueueMessage.builder()
                        .id(UUID.randomUUID().toString()).channel(CHANNEL)
                        .body(("Message " + i).getBytes()).build());
            }
            System.out.println("Sent 3 messages.\n");

            // Receive messages from the queue
            QueuesPollResponse response = client.receiveQueuesMessages(QueuesPollRequest.builder()
                    .channel(CHANNEL).pollMaxMessages(10).pollWaitTimeoutInSeconds(5).build());

            // Handle each message: ack on success, reject on failure
            for (QueueMessageReceived msg : response.getMessages()) {
                String body = new String(msg.getBody());
                if (body.contains("2")) {
                    msg.reject();
                    System.out.println("  REJECTED: " + body);
                } else {
                    msg.ack();
                    System.out.println("  ACKNOWLEDGED: " + body);
                }
            }

            // Clean up rejected message (receive and auto-ack)
            QueuesPollResponse cleanup = client.receiveQueuesMessages(QueuesPollRequest.builder()
                    .channel(CHANNEL).pollMaxMessages(10).pollWaitTimeoutInSeconds(1).autoAckMessages(true).build());
            System.out.println("\nRemaining messages cleaned up: " + cleanup.getMessages().size());

            // Clean up resources
            client.deleteQueuesChannel(CHANNEL);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
