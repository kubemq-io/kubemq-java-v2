package io.kubemq.example.patterns;

import io.kubemq.sdk.queues.*;
import java.util.UUID;

/**
 * Work Queue Pattern Example
 *
 * Demonstrates the competing-consumers (work queue) pattern using KubeMQ queues.
 */
public class WorkQueueExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-patterns-work-queue-client";
    private static final String CHANNEL = "java-patterns.work-queue";

    public static void main(String[] args) throws InterruptedException {
        QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build();
        client.ping();
        client.createQueuesChannel(CHANNEL);

        System.out.println("Sending 10 tasks to work queue...\n");
        for (int i = 1; i <= 10; i++) {
            client.sendQueuesMessage(QueueMessage.builder()
                    .id(UUID.randomUUID().toString()).channel(CHANNEL)
                    .body(("Task #" + i).getBytes()).build());
        }

        System.out.println("Worker 1 pulling batch...");
        QueuesPollResponse resp1 = client.receiveQueuesMessages(QueuesPollRequest.builder()
                .channel(CHANNEL).pollMaxMessages(5).pollWaitTimeoutInSeconds(3).build());
        System.out.println("  Received: " + resp1.getMessages().size());
        resp1.getMessages().forEach(m -> { System.out.println("    " + new String(m.getBody())); m.ack(); });

        System.out.println("\nWorker 2 pulling batch...");
        QueuesPollResponse resp2 = client.receiveQueuesMessages(QueuesPollRequest.builder()
                .channel(CHANNEL).pollMaxMessages(5).pollWaitTimeoutInSeconds(3).build());
        System.out.println("  Received: " + resp2.getMessages().size());
        resp2.getMessages().forEach(m -> { System.out.println("    " + new String(m.getBody())); m.ack(); });

        System.out.println("\nAll tasks distributed and processed.");

        client.deleteQueuesChannel(CHANNEL);
        client.close();
    }
}
