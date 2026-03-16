package io.kubemq.example.queuesstream;

import io.kubemq.sdk.queues.*;
import java.util.UUID;

/**
 * DelayPolicyExample for Queues Stream
 */
public class DelayPolicyExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queuesstream-LDelay-LPolicy-client";
    private static final String CHANNEL = "java-queuesstream.LDelay-LPolicy";

    /**
     * Demonstrates sending multiple messages with different delay values
     * to implement scheduled delivery policies.
     */
    public static void main(String[] args) throws InterruptedException {
        // Create a client connected to the KubeMQ server
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            // Create the queue channel
            client.createQueuesChannel(CHANNEL);

            // Send messages with different delay values (scheduled delivery)
            int[] delays = {1, 3, 5};
            for (int delay : delays) {
                client.sendQueuesMessage(QueueMessage.builder()
                        .id(UUID.randomUUID().toString()).channel(CHANNEL)
                        .body(("Delay " + delay + "s").getBytes())
                        .delayInSeconds(delay).build());
                System.out.println("Sent message with " + delay + "s delay.");
            }

            // Poll as messages become available after their delay
            System.out.println("\nPolling as messages become available...");
            for (int i = 0; i < 3; i++) {
                QueuesPollResponse response = client.receiveQueuesMessages(QueuesPollRequest.builder()
                        .channel(CHANNEL).pollMaxMessages(1).pollWaitTimeoutInSeconds(10).autoAckMessages(true).build());
                if (!response.getMessages().isEmpty()) {
                    System.out.println("  Received: " + new String(response.getMessages().get(0).getBody()));
                }
            }

            // Clean up resources
            client.deleteQueuesChannel(CHANNEL);
        }
    }
}
