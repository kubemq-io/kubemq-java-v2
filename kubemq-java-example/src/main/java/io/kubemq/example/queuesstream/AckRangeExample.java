package io.kubemq.example.queuesstream;

import io.kubemq.sdk.queues.*;
import java.util.UUID;

/**
 * AckRangeExample for Queues Stream
 */
public class AckRangeExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queuesstream-LAck-LRange-client";
    private static final String CHANNEL = "java-queuesstream.LAck-LRange";

    /**
     * Demonstrates acknowledging a range of messages by receiving a batch
     * and using ackAll() on the poll response.
     */
    public static void main(String[] args) {
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            client.createQueuesChannel(CHANNEL);

            for (int i = 1; i <= 5; i++) {
                client.sendQueuesMessage(QueueMessage.builder()
                        .channel(CHANNEL).body(("Range msg " + i).getBytes()).build());
            }

            QueuesPollResponse response = client.receiveQueuesMessages(QueuesPollRequest.builder()
                    .channel(CHANNEL).pollMaxMessages(5).pollWaitTimeoutInSeconds(5).build());

            System.out.println("Received " + response.getMessages().size() + " messages.");
            response.getMessages().forEach(msg ->
                System.out.println("  " + new String(msg.getBody())));

            response.ackAll();
            System.out.println("\nAll " + response.getMessages().size() + " messages acked via ackAll().");

            client.deleteQueuesChannel(CHANNEL);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
