package io.kubemq.example.queuesstream;

import io.kubemq.sdk.queues.*;
import java.util.UUID;

/**
 * PollModeExample for Queues Stream
 */
public class PollModeExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queuesstream-LPoll-LMode-client";
    private static final String CHANNEL = "java-queuesstream.LPoll-LMode";

    public static void main(String[] args) {
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            client.createQueuesChannel(CHANNEL);

            for (int i = 1; i <= 5; i++) {
                client.sendQueuesMessage(QueueMessage.builder()
                        .channel(CHANNEL).body(("Poll msg " + i).getBytes()).build());
            }

            System.out.println("=== Waiting Pull Mode ===\n");
            QueuesPollResponse response = client.receiveQueuesMessages(QueuesPollRequest.builder()
                    .channel(CHANNEL).pollMaxMessages(3).pollWaitTimeoutInSeconds(10).build());

            System.out.println("Received " + response.getMessages().size() + " messages:");
            response.getMessages().forEach(msg -> {
                System.out.println("  " + new String(msg.getBody()));
                msg.ack();
            });

            client.deleteQueuesChannel(CHANNEL);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
