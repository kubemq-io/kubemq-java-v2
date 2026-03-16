package io.kubemq.example.queuesstream;

import io.kubemq.sdk.queues.*;
import java.util.UUID;

/**
 * ExpirationPolicyExample for Queues Stream
 */
public class ExpirationPolicyExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queuesstream-LExpiration-LPolicy-client";
    private static final String CHANNEL = "java-queuesstream.LExpiration-LPolicy";

    public static void main(String[] args) throws InterruptedException {
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            client.createQueuesChannel(CHANNEL);

            int expirationSeconds = 3;
            client.sendQueuesMessage(QueueMessage.builder()
                    .id(UUID.randomUUID().toString()).channel(CHANNEL)
                    .body("Expiring message".getBytes())
                    .expirationInSeconds(expirationSeconds).build());
            System.out.println("Sent message with " + expirationSeconds + "s expiration.");

            Thread.sleep((expirationSeconds + 2) * 1000);

            QueuesPollResponse response = client.receiveQueuesMessages(QueuesPollRequest.builder()
                    .channel(CHANNEL).pollMaxMessages(1).pollWaitTimeoutInSeconds(1).autoAckMessages(true).build());
            System.out.println("Messages after expiration: " + response.getMessages().size() + " (expected 0)");

            client.deleteQueuesChannel(CHANNEL);
        }
    }
}
