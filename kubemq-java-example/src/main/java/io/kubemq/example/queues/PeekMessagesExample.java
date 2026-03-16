package io.kubemq.example.queues;

import io.kubemq.sdk.queues.*;

public class PeekMessagesExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queues-peek-messages-client";
    private static final String CHANNEL = "java-queues.peek-messages";

    public static void main(String[] args) {
        // Create a queues client connected to the KubeMQ server
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            // Send messages to the queue
            for (int i = 1; i <= 3; i++) {
                client.sendQueuesMessage(QueueMessage.builder()
                        .channel(CHANNEL).body(("Peek message " + i).getBytes()).build());
            }

            // Peek at waiting messages without consuming them (non-destructive)
            System.out.println("=== Peeking at waiting messages (non-destructive) ===");
            QueueMessagesWaiting waiting = client.waiting(CHANNEL, 10, 5);
            if (waiting.isError()) {
                System.err.println("Peek error: " + waiting.getError());
            } else {
                System.out.println("Messages waiting: " + waiting.getMessages().size());
                waiting.getMessages().forEach(msg ->
                    System.out.println("  ID: " + msg.getId() + ", Body: " + new String(msg.getBody())));
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
