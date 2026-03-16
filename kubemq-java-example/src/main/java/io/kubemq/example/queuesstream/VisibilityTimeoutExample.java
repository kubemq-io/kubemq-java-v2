package io.kubemq.example.queuesstream;

import io.kubemq.sdk.queues.*;
import java.util.UUID;

/**
 * VisibilityTimeoutExample for Queues Stream
 */
public class VisibilityTimeoutExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queuesstream-LVisibility-LTimeout-client";
    private static final String CHANNEL = "java-queuesstream.LVisibility-LTimeout";

    public static void main(String[] args) throws InterruptedException {
        // Create a client connected to the KubeMQ server
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            // Create the queue channel
            client.createQueuesChannel(CHANNEL);

            // Send a test message
            client.sendQueuesMessage(QueueMessage.builder()
                    .channel(CHANNEL).body("Visibility test".getBytes()).build());

            // Poll with visibility timeout (message hidden from other consumers for 3s)
            QueuesPollResponse response = client.receiveQueuesMessages(QueuesPollRequest.builder()
                    .channel(CHANNEL).pollMaxMessages(1).pollWaitTimeoutInSeconds(5)
                    .visibilitySeconds(3).autoAckMessages(false).build());

            if (!response.getMessages().isEmpty()) {
                QueueMessageReceived msg = response.getMessages().get(0);
                System.out.println("Received with 3s visibility: " + new String(msg.getBody()));

                // Extend visibility window before it expires
                Thread.sleep(1000);
                msg.extendVisibilityTimer(3);
                System.out.println("Extended visibility by 3s.");

                Thread.sleep(2000);
                // Acknowledge the message after processing
                msg.ack();
                System.out.println("Acknowledged within extended window.");
            }

            // Clean up resources
            client.deleteQueuesChannel(CHANNEL);
        }
    }
}
