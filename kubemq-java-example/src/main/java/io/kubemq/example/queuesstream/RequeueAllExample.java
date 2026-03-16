package io.kubemq.example.queuesstream;

import io.kubemq.sdk.queues.*;
import java.util.UUID;

/**
 * RequeueAllExample for Queues Stream
 */
public class RequeueAllExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queuesstream-LRequeue-LAll-client";
    private static final String CHANNEL = "java-queuesstream.LRequeue-LAll";

    private static final String REQUEUE_CHANNEL = "java-queuesstream.requeue-all-dest";

    public static void main(String[] args) {
        // Create a client connected to the KubeMQ server
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            // Create source and destination queue channels
            client.createQueuesChannel(CHANNEL);
            client.createQueuesChannel(REQUEUE_CHANNEL);

            // Send messages to the source queue
            for (int i = 1; i <= 3; i++) {
                client.sendQueuesMessage(QueueMessage.builder()
                        .channel(CHANNEL).body(("Requeue msg " + i).getBytes()).build());
            }

            // Poll for messages from the source queue
            QueuesPollResponse response = client.receiveQueuesMessages(QueuesPollRequest.builder()
                    .channel(CHANNEL).pollMaxMessages(10).pollWaitTimeoutInSeconds(5).build());

            System.out.println("Received " + response.getMessages().size() + " messages.");
            // Requeue all messages to a different channel
            response.reQueueAll(REQUEUE_CHANNEL);
            System.out.println("All messages requeued to: " + REQUEUE_CHANNEL);

            // Verify messages arrived in the destination queue
            QueuesPollResponse dest = client.receiveQueuesMessages(QueuesPollRequest.builder()
                    .channel(REQUEUE_CHANNEL).pollMaxMessages(10).pollWaitTimeoutInSeconds(2).autoAckMessages(true).build());
            System.out.println("Destination queue received: " + dest.getMessages().size() + " messages.");

            // Clean up resources
            client.deleteQueuesChannel(CHANNEL);
            client.deleteQueuesChannel(REQUEUE_CHANNEL);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
