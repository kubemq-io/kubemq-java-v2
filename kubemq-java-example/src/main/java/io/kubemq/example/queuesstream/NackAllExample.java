package io.kubemq.example.queuesstream;

import io.kubemq.sdk.queues.*;
import java.util.UUID;

/**
 * NackAllExample for Queues Stream
 */
public class NackAllExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queuesstream-LNack-LAll-client";
    private static final String CHANNEL = "java-queuesstream.LNack-LAll";

    /**
     * Demonstrates rejecting all messages in a poll response using rejectAll().
     */
    public static void main(String[] args) {
        // Create a client connected to the KubeMQ server
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            // Create the queue channel
            client.createQueuesChannel(CHANNEL);

            // Send messages to the queue
            for (int i = 1; i <= 3; i++) {
                client.sendQueuesMessage(QueueMessage.builder()
                        .channel(CHANNEL).body(("Nack msg " + i).getBytes()).build());
            }

            // Poll for messages
            QueuesPollResponse response = client.receiveQueuesMessages(QueuesPollRequest.builder()
                    .channel(CHANNEL).pollMaxMessages(10).pollWaitTimeoutInSeconds(5).build());

            System.out.println("Received " + response.getMessages().size() + " messages.");
            // Reject all messages (return them to queue for redelivery)
            response.rejectAll();
            System.out.println("All messages rejected via rejectAll().");
            System.out.println("Messages returned to queue for redelivery.");

            // Clean up: consume rejected messages so we can delete the channel
            QueuesPollResponse cleanup = client.receiveQueuesMessages(QueuesPollRequest.builder()
                    .channel(CHANNEL).pollMaxMessages(10).pollWaitTimeoutInSeconds(1).autoAckMessages(true).build());
            System.out.println("Cleanup: " + cleanup.getMessages().size() + " messages consumed.");

            // Clean up resources
            client.deleteQueuesChannel(CHANNEL);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
