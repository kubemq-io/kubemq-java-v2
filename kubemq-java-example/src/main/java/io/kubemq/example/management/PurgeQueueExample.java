package io.kubemq.example.management;

import io.kubemq.sdk.queues.*;

public class PurgeQueueExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-management-purge-queue-client";
    private static final String CHANNEL = "java-management.purge-queue";

    public static void main(String[] args) {
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            client.createQueuesChannel(CHANNEL);

            for (int i = 1; i <= 5; i++) {
                client.sendQueuesMessage(QueueMessage.builder()
                        .channel(CHANNEL).body(("Message " + i).getBytes()).build());
            }

            QueueMessagesWaiting before = client.waiting(CHANNEL, 10, 2);
            System.out.println("Messages before purge: " + before.getMessages().size());

            client.purgeQueue(CHANNEL);
            System.out.println("Queue purged.");

            QueueMessagesWaiting after = client.waiting(CHANNEL, 10, 2);
            System.out.println("Messages after purge: " + after.getMessages().size());

            client.deleteQueuesChannel(CHANNEL);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
