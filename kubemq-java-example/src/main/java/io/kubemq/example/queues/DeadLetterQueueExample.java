package io.kubemq.example.queues;

import io.kubemq.sdk.queues.*;
import java.util.UUID;

/**
 * Dead Letter Queue Example
 *
 * Demonstrates DLQ routing when messages exceed maximum receive attempts.
 */
public class DeadLetterQueueExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queues-dead-letter-queue-client";
    private static final String CHANNEL = "java-queues.dead-letter-queue";
    private static final String DLQ_CHANNEL = "java-queues.dead-letter-queue-dlq";

    public static void main(String[] args) throws InterruptedException {
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            client.createQueuesChannel(CHANNEL);
            client.createQueuesChannel(DLQ_CHANNEL);

            client.sendQueuesMessage(QueueMessage.builder()
                    .id(UUID.randomUUID().toString()).channel(CHANNEL)
                    .body("Message with DLQ".getBytes())
                    .attemptsBeforeDeadLetterQueue(2).deadLetterQueue(DLQ_CHANNEL).build());
            System.out.println("Sent message with DLQ config (max 2 attempts).\n");

            for (int attempt = 1; attempt <= 3; attempt++) {
                QueuesPollResponse resp = client.receiveQueuesMessages(QueuesPollRequest.builder()
                        .channel(CHANNEL).pollMaxMessages(1).pollWaitTimeoutInSeconds(2).build());
                if (!resp.getMessages().isEmpty()) {
                    System.out.println("Attempt " + attempt + ": Rejecting (receiveCount=" + resp.getMessages().get(0).getReceiveCount() + ")");
                    resp.getMessages().get(0).reject();
                } else {
                    System.out.println("Attempt " + attempt + ": No message in main queue.");
                    break;
                }
                Thread.sleep(500);
            }

            System.out.println("\nChecking DLQ...");
            QueuesPollResponse dlqResp = client.receiveQueuesMessages(QueuesPollRequest.builder()
                    .channel(DLQ_CHANNEL).pollMaxMessages(1).pollWaitTimeoutInSeconds(2).autoAckMessages(true).build());
            if (!dlqResp.getMessages().isEmpty()) {
                System.out.println("Found in DLQ: " + new String(dlqResp.getMessages().get(0).getBody()));
            }

            client.deleteQueuesChannel(CHANNEL);
            client.deleteQueuesChannel(DLQ_CHANNEL);
        }
    }
}
