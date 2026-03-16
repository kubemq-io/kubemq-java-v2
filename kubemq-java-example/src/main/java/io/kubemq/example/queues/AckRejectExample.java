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
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            client.createQueuesChannel(CHANNEL);

            for (int i = 1; i <= 3; i++) {
                client.sendQueuesMessage(QueueMessage.builder()
                        .id(UUID.randomUUID().toString()).channel(CHANNEL)
                        .body(("Message " + i).getBytes()).build());
            }
            System.out.println("Sent 3 messages.\n");

            QueuesPollResponse response = client.receiveQueuesMessages(QueuesPollRequest.builder()
                    .channel(CHANNEL).pollMaxMessages(10).pollWaitTimeoutInSeconds(5).build());

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

            // Clean up rejected message
            QueuesPollResponse cleanup = client.receiveQueuesMessages(QueuesPollRequest.builder()
                    .channel(CHANNEL).pollMaxMessages(10).pollWaitTimeoutInSeconds(1).autoAckMessages(true).build());
            System.out.println("\nRemaining messages cleaned up: " + cleanup.getMessages().size());

            client.deleteQueuesChannel(CHANNEL);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
