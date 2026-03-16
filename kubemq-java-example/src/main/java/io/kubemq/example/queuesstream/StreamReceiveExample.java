package io.kubemq.example.queuesstream;

import io.kubemq.sdk.queues.*;
import java.util.UUID;

/**
 * StreamReceiveExample for Queues Stream
 */
public class StreamReceiveExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queuesstream-LStream-LReceive-client";
    private static final String CHANNEL = "java-queuesstream.LStream-LReceive";

    public static void main(String[] args) {
        // Create a client connected to the KubeMQ server
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            // Create the queue channel
            client.createQueuesChannel(CHANNEL);

            // Send messages to the queue
            for (int i = 1; i <= 5; i++) {
                client.sendQueuesMessage(QueueMessage.builder()
                        .channel(CHANNEL).body(("Message " + i).getBytes()).build());
            }

            // Poll for messages via stream
            System.out.println("Receiving messages via stream poll...\n");
            QueuesPollResponse response = client.receiveQueuesMessages(QueuesPollRequest.builder()
                    .channel(CHANNEL).pollMaxMessages(10).pollWaitTimeoutInSeconds(5).build());

            // Process each message and acknowledge it
            response.getMessages().forEach(msg -> {
                System.out.println("  Received: " + new String(msg.getBody()));
                msg.ack();
            });
            System.out.println("\nReceived and acked " + response.getMessages().size() + " messages.");

            // Clean up resources
            client.deleteQueuesChannel(CHANNEL);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
