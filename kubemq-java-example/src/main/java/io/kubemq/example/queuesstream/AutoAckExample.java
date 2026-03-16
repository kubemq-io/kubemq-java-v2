package io.kubemq.example.queuesstream;

import io.kubemq.sdk.queues.*;
import java.util.UUID;

/**
 * AutoAckExample for Queues Stream
 */
public class AutoAckExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queuesstream-LAuto-LAck-client";
    private static final String CHANNEL = "java-queuesstream.LAuto-LAck";

    public static void main(String[] args) {
        // Create a client connected to the KubeMQ server
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            // Create the queue channel
            client.createQueuesChannel(CHANNEL);

            // Send messages to the queue
            for (int i = 1; i <= 3; i++) {
                client.sendQueuesMessage(QueueMessage.builder()
                        .channel(CHANNEL).body(("Auto-ack msg " + i).getBytes()).build());
            }

            // Poll with auto-ack enabled (messages acknowledged automatically)
            System.out.println("Polling with autoAckMessages=true...\n");
            QueuesPollResponse response = client.receiveQueuesMessages(QueuesPollRequest.builder()
                    .channel(CHANNEL).pollMaxMessages(10).pollWaitTimeoutInSeconds(5)
                    .autoAckMessages(true).build());

            response.getMessages().forEach(msg ->
                System.out.println("  Received (auto-acked): " + new String(msg.getBody())));
            System.out.println("\n" + response.getMessages().size() + " messages auto-acknowledged.");

            // Clean up resources
            client.deleteQueuesChannel(CHANNEL);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
