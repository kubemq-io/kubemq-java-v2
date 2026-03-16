package io.kubemq.example.queuesstream;

import io.kubemq.sdk.queues.*;
import java.util.UUID;

/**
 * DeadLetterPolicyExample for Queues Stream
 */
public class DeadLetterPolicyExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queuesstream-LDead-LLetter-LPolicy-client";
    private static final String CHANNEL = "java-queuesstream.LDead-LLetter-LPolicy";

    private static final String DLQ = "java-queuesstream.dead-letter-policy-dlq";

    /**
     * Demonstrates configuring dead letter policy with max attempts
     * and automatic routing to a dead letter queue.
     */
    public static void main(String[] args) throws InterruptedException {
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            client.createQueuesChannel(CHANNEL);
            client.createQueuesChannel(DLQ);

            client.sendQueuesMessage(QueueMessage.builder()
                    .id(UUID.randomUUID().toString()).channel(CHANNEL)
                    .body("Poison message".getBytes())
                    .attemptsBeforeDeadLetterQueue(2).deadLetterQueue(DLQ).build());
            System.out.println("Sent message with DLQ policy (max 2 attempts).\n");

            for (int attempt = 1; attempt <= 3; attempt++) {
                QueuesPollResponse resp = client.receiveQueuesMessages(QueuesPollRequest.builder()
                        .channel(CHANNEL).pollMaxMessages(1).pollWaitTimeoutInSeconds(2).build());
                if (!resp.getMessages().isEmpty()) {
                    System.out.println("Attempt " + attempt + ": Rejecting...");
                    resp.getMessages().get(0).reject();
                } else {
                    System.out.println("Attempt " + attempt + ": No message (moved to DLQ).");
                    break;
                }
                Thread.sleep(500);
            }

            QueuesPollResponse dlqResp = client.receiveQueuesMessages(QueuesPollRequest.builder()
                    .channel(DLQ).pollMaxMessages(1).pollWaitTimeoutInSeconds(2).autoAckMessages(true).build());
            if (!dlqResp.getMessages().isEmpty()) {
                System.out.println("\nDLQ message: " + new String(dlqResp.getMessages().get(0).getBody()));
            }

            client.deleteQueuesChannel(CHANNEL);
            client.deleteQueuesChannel(DLQ);
        }
    }
}
