package io.kubemq.example.queues;

import io.kubemq.sdk.queues.*;
import java.util.UUID;

/**
 * Delayed Messages Example
 *
 * Demonstrates sending messages with a delay before they become available for consumption.
 */
public class DelayedMessagesExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queues-delayed-messages-client";
    private static final String CHANNEL = "java-queues.delayed-messages";

    public static void main(String[] args) throws InterruptedException {
        // Create a queues client connected to the KubeMQ server
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            // Create the queue channel
            client.createQueuesChannel(CHANNEL);

            // Send a message with delay (not available for consumption until delay expires)
            int delaySeconds = 3;
            client.sendQueuesMessage(QueueMessage.builder()
                    .id(UUID.randomUUID().toString()).channel(CHANNEL)
                    .body(("Delayed by " + delaySeconds + "s").getBytes())
                    .delayInSeconds(delaySeconds).build());
            System.out.println("Sent message with " + delaySeconds + "s delay.");

            // Try to receive immediately (message not yet available)
            System.out.println("Trying to receive immediately...");
            QueuesPollResponse resp1 = client.receiveQueuesMessages(QueuesPollRequest.builder()
                    .channel(CHANNEL).pollMaxMessages(1).pollWaitTimeoutInSeconds(1).autoAckMessages(true).build());
            System.out.println("Messages available: " + resp1.getMessages().size() + " (expected 0)");

            // Wait for the delay to expire
            System.out.println("Waiting for delay to expire...");
            Thread.sleep((delaySeconds + 1) * 1000);

            // Receive the message after delay has expired
            QueuesPollResponse resp2 = client.receiveQueuesMessages(QueuesPollRequest.builder()
                    .channel(CHANNEL).pollMaxMessages(1).pollWaitTimeoutInSeconds(2).autoAckMessages(true).build());
            if (!resp2.getMessages().isEmpty()) {
                System.out.println("Received after delay: " + new String(resp2.getMessages().get(0).getBody()));
            }

            // Clean up resources
            client.deleteQueuesChannel(CHANNEL);
        }
    }
}
