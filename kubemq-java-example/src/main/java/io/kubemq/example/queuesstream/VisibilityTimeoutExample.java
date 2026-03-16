package io.kubemq.example.queuesstream;

import io.kubemq.sdk.queues.*;
import java.util.UUID;

/**
 * VisibilityTimeoutExample for Queues Stream
 */
public class VisibilityTimeoutExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queuesstream-LVisibility-LTimeout-client";
    private static final String CHANNEL = "java-queuesstream.LVisibility-LTimeout";

    public static void main(String[] args) throws InterruptedException {
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            client.createQueuesChannel(CHANNEL);

            client.sendQueuesMessage(QueueMessage.builder()
                    .channel(CHANNEL).body("Visibility test".getBytes()).build());

            QueuesPollResponse response = client.receiveQueuesMessages(QueuesPollRequest.builder()
                    .channel(CHANNEL).pollMaxMessages(1).pollWaitTimeoutInSeconds(5)
                    .visibilitySeconds(3).autoAckMessages(false).build());

            if (!response.getMessages().isEmpty()) {
                QueueMessageReceived msg = response.getMessages().get(0);
                System.out.println("Received with 3s visibility: " + new String(msg.getBody()));

                Thread.sleep(1000);
                msg.extendVisibilityTimer(3);
                System.out.println("Extended visibility by 3s.");

                Thread.sleep(2000);
                msg.ack();
                System.out.println("Acknowledged within extended window.");
            }

            client.deleteQueuesChannel(CHANNEL);
        }
    }
}
