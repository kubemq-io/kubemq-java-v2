package io.kubemq.example.queues;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueueMessage;
import io.kubemq.sdk.queues.QueueMessageReceived;
import io.kubemq.sdk.queues.QueueSendResult;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.queues.QueuesPollRequest;
import io.kubemq.sdk.queues.QueuesPollResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ReQueue Message Example
 *
 * This example demonstrates how to re-route (requeue) messages to different
 * queue channels in KubeMQ. This is useful for implementing routing logic,
 * error handling workflows, and message prioritization.
 *
 * Use Cases:
 * - Content-based routing (route to different queues based on content)
 * - Priority escalation (move messages to high-priority queue)
 * - Error handling (route failed messages to error queue)
 * - Load distribution (spread messages across multiple queues)
 * - Workflow orchestration (move messages between processing stages)
 *
 * How it works:
 * 1. Consumer receives a message from original queue
 * 2. Based on some logic, decides to requeue to different channel
 * 3. Calls reQueue(targetChannel) on the message
 * 4. Message appears in the target queue
 * 5. Original message is removed from source queue
 *
 * Important Notes:
 * - ReQueue is atomic - message moves completely or not at all
 * - Original message metadata/tags are preserved
 * - Message gets new sequence number in target queue
 * - Cannot requeue auto-acknowledged messages
 *
 * @see io.kubemq.sdk.queues.QueueMessageReceived#reQueue(String)
 */
public class ReQueueMessageExample {

    private final QueuesClient queuesClient;
    private final String sourceChannel = "source-queue";
    private final String priorityChannel = "priority-queue";
    private final String errorChannel = "error-queue";
    private final String processingChannel = "processing-queue";
    private final String address = "localhost:50000";
    private final String clientId = "requeue-example-client";

    /**
     * Initializes the QueuesClient and creates all required channels.
     */
    public ReQueueMessageExample() {
        queuesClient = QueuesClient.builder()
                .address(address)
                .clientId(clientId)
                .build();

        ServerInfo pingResult = queuesClient.ping();
        System.out.println("Connected to: " + pingResult.getHost() + " v" + pingResult.getVersion());

        // Create all channels
        queuesClient.createQueuesChannel(sourceChannel);
        queuesClient.createQueuesChannel(priorityChannel);
        queuesClient.createQueuesChannel(errorChannel);
        queuesClient.createQueuesChannel(processingChannel);
        System.out.println("Created all required channels\n");
    }

    /**
     * Demonstrates basic requeue operation.
     * Receives a message and moves it to a different queue.
     */
    public void basicReQueueExample() {
        System.out.println("=== Basic ReQueue Example ===\n");

        // Send a message to source queue
        QueueMessage message = QueueMessage.builder()
                .id(UUID.randomUUID().toString())
                .channel(sourceChannel)
                .body("Message to be re-routed".getBytes())
                .metadata("Original message")
                .build();

        QueueSendResult sendResult = queuesClient.sendQueuesMessage(message);
        System.out.println("1. Sent message to source queue: " + sendResult.getId());

        // Receive from source queue (without auto-ack to allow requeue)
        QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                .channel(sourceChannel)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(5)
                .autoAckMessages(false)  // Must be false to allow requeue
                .build();

        QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);

        if (!response.getMessages().isEmpty()) {
            QueueMessageReceived received = response.getMessages().get(0);
            System.out.println("2. Received message from source queue: " + received.getId());

            // Requeue to priority queue
            received.reQueue(priorityChannel);
            System.out.println("3. ReQueued message to: " + priorityChannel);
        }

        // Verify message is now in priority queue
        QueuesPollRequest priorityPoll = QueuesPollRequest.builder()
                .channel(priorityChannel)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(2)
                .autoAckMessages(true)
                .build();

        QueuesPollResponse priorityResponse = queuesClient.receiveQueuesMessages(priorityPoll);
        if (!priorityResponse.getMessages().isEmpty()) {
            System.out.println("4. Message found in priority queue: " +
                    new String(priorityResponse.getMessages().get(0).getBody()));
        }
        System.out.println();
    }

    /**
     * Demonstrates content-based routing using requeue.
     * Routes messages to different queues based on their content or tags.
     */
    public void contentBasedRoutingExample() {
        System.out.println("=== Content-Based Routing Example ===\n");

        // Send messages with different priority levels
        String[] priorities = {"high", "medium", "low"};

        for (String priority : priorities) {
            Map<String, String> tags = new HashMap<>();
            tags.put("priority", priority);

            QueueMessage message = QueueMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .channel(sourceChannel)
                    .body(("Task with " + priority + " priority").getBytes())
                    .tags(tags)
                    .build();

            queuesClient.sendQueuesMessage(message);
            System.out.println("Sent " + priority + " priority message");
        }

        System.out.println("\nRouting messages based on priority tag...\n");

        // Process and route each message
        for (int i = 0; i < priorities.length; i++) {
            QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                    .channel(sourceChannel)
                    .pollMaxMessages(1)
                    .pollWaitTimeoutInSeconds(2)
                    .autoAckMessages(false)
                    .build();

            QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);

            if (!response.getMessages().isEmpty()) {
                QueueMessageReceived msg = response.getMessages().get(0);
                String priority = msg.getTags().get("priority");

                String targetQueue;
                switch (priority) {
                    case "high":
                        targetQueue = priorityChannel;
                        break;
                    case "low":
                        targetQueue = processingChannel;
                        break;
                    default:
                        msg.ack();  // Medium priority - process immediately
                        System.out.println("  Processed medium priority message directly");
                        continue;
                }

                msg.reQueue(targetQueue);
                System.out.println("  Routed " + priority + " priority message to: " + targetQueue);
            }
        }

        // Show resulting queue states
        System.out.println("\nFinal queue states:");
        showQueueCount(priorityChannel);
        showQueueCount(processingChannel);

        // Cleanup
        cleanupQueue(priorityChannel);
        cleanupQueue(processingChannel);
    }

    /**
     * Demonstrates error handling with requeue to error queue.
     */
    public void errorHandlingReQueueExample() {
        System.out.println("\n=== Error Handling ReQueue Example ===\n");

        // Send a message that will "fail" processing
        Map<String, String> tags = new HashMap<>();
        tags.put("type", "risky-operation");

        QueueMessage message = QueueMessage.builder()
                .id(UUID.randomUUID().toString())
                .channel(sourceChannel)
                .body("This operation might fail".getBytes())
                .tags(tags)
                .build();

        queuesClient.sendQueuesMessage(message);
        System.out.println("1. Sent potentially failing message");

        // Receive and attempt to process
        QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                .channel(sourceChannel)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(2)
                .autoAckMessages(false)
                .build();

        QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);

        if (!response.getMessages().isEmpty()) {
            QueueMessageReceived msg = response.getMessages().get(0);
            System.out.println("2. Received message, attempting to process...");

            try {
                // Simulate processing failure
                if (msg.getTags().get("type").equals("risky-operation")) {
                    throw new RuntimeException("Simulated processing error");
                }
                msg.ack();
            } catch (Exception e) {
                System.out.println("3. Processing failed: " + e.getMessage());
                System.out.println("4. Requeueing to error queue for investigation...");

                // Add error information and requeue to error queue
                msg.reQueue(errorChannel);
                System.out.println("5. Message moved to error queue");
            }
        }

        // Check error queue
        QueuesPollRequest errorPoll = QueuesPollRequest.builder()
                .channel(errorChannel)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(2)
                .autoAckMessages(true)
                .build();

        QueuesPollResponse errorResponse = queuesClient.receiveQueuesMessages(errorPoll);
        if (!errorResponse.getMessages().isEmpty()) {
            System.out.println("6. Message found in error queue: " +
                    new String(errorResponse.getMessages().get(0).getBody()));
        }
    }

    /**
     * Demonstrates workflow stages using requeue.
     * Moves messages through different processing stages.
     */
    public void workflowStagesExample() {
        System.out.println("\n=== Workflow Stages Example ===\n");

        // Workflow: Source -> Processing -> Priority (completed)

        // Send initial message
        QueueMessage message = QueueMessage.builder()
                .id("workflow-" + UUID.randomUUID())
                .channel(sourceChannel)
                .body("Order #12345".getBytes())
                .metadata("stage:received")
                .build();

        queuesClient.sendQueuesMessage(message);
        System.out.println("Stage 1: Order received in source queue");

        // Stage 1: Receive and validate
        QueuesPollRequest poll1 = QueuesPollRequest.builder()
                .channel(sourceChannel)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(2)
                .autoAckMessages(false)
                .build();

        QueuesPollResponse response1 = queuesClient.receiveQueuesMessages(poll1);
        if (!response1.getMessages().isEmpty()) {
            QueueMessageReceived msg = response1.getMessages().get(0);
            System.out.println("         Validating order...");

            // Move to processing stage
            msg.reQueue(processingChannel);
            System.out.println("Stage 2: Moved to processing queue");
        }

        // Stage 2: Process and complete
        QueuesPollRequest poll2 = QueuesPollRequest.builder()
                .channel(processingChannel)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(2)
                .autoAckMessages(false)
                .build();

        QueuesPollResponse response2 = queuesClient.receiveQueuesMessages(poll2);
        if (!response2.getMessages().isEmpty()) {
            QueueMessageReceived msg = response2.getMessages().get(0);
            System.out.println("         Processing order...");

            // Move to completed stage
            msg.reQueue(priorityChannel);  // Using priority as "completed" queue
            System.out.println("Stage 3: Moved to completed queue");
        }

        // Final stage: Mark as done
        QueuesPollRequest poll3 = QueuesPollRequest.builder()
                .channel(priorityChannel)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(2)
                .autoAckMessages(true)
                .build();

        QueuesPollResponse response3 = queuesClient.receiveQueuesMessages(poll3);
        if (!response3.getMessages().isEmpty()) {
            System.out.println("Stage 4: Order completed - " +
                    new String(response3.getMessages().get(0).getBody()));
        }

        System.out.println("\nWorkflow completed: received -> processing -> completed");
    }

    /**
     * Demonstrates that auto-ack prevents requeue (error case).
     */
    public void reQueueWithAutoAckErrorExample() {
        System.out.println("\n=== ReQueue with Auto-Ack Error Example ===\n");

        // Send a message
        QueueMessage message = QueueMessage.builder()
                .id(UUID.randomUUID().toString())
                .channel(sourceChannel)
                .body("Test message".getBytes())
                .build();

        queuesClient.sendQueuesMessage(message);
        System.out.println("Sent message to source queue");

        // Receive WITH auto-ack (this will prevent requeue)
        QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                .channel(sourceChannel)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(2)
                .autoAckMessages(true)  // Auto-ack enabled!
                .build();

        QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);

        if (!response.getMessages().isEmpty()) {
            QueueMessageReceived msg = response.getMessages().get(0);
            System.out.println("Received message with auto-ack");

            try {
                msg.reQueue(priorityChannel);
                System.out.println("ReQueue succeeded (unexpected)");
            } catch (IllegalStateException e) {
                System.out.println("ReQueue failed as expected: " + e.getMessage());
                System.out.println("\nNote: To use reQueue(), set autoAckMessages(false)");
            }
        }
    }

    /**
     * Helper method to show queue message count.
     */
    private void showQueueCount(String channel) {
        try {
            var waiting = queuesClient.waiting(channel, 100, 1);
            System.out.println("  " + channel + ": " + waiting.getMessages().size() + " messages");
        } catch (Exception e) {
            System.out.println("  " + channel + ": 0 messages");
        }
    }

    /**
     * Helper method to cleanup a queue.
     */
    private void cleanupQueue(String channel) {
        QueuesPollRequest cleanup = QueuesPollRequest.builder()
                .channel(channel)
                .pollMaxMessages(100)
                .pollWaitTimeoutInSeconds(1)
                .autoAckMessages(true)
                .build();
        queuesClient.receiveQueuesMessages(cleanup);
    }

    /**
     * Cleans up all resources.
     */
    public void cleanup() {
        try {
            queuesClient.deleteQueuesChannel(sourceChannel);
            queuesClient.deleteQueuesChannel(priorityChannel);
            queuesClient.deleteQueuesChannel(errorChannel);
            queuesClient.deleteQueuesChannel(processingChannel);
            System.out.println("\nCleaned up all channels");
        } catch (Exception e) {
            // Ignore cleanup errors
        }
        queuesClient.close();
    }

    /**
     * Main method demonstrating all requeue patterns.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           KubeMQ ReQueue Message Examples                    ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        ReQueueMessageExample example = new ReQueueMessageExample();

        try {
            // Basic requeue
            example.basicReQueueExample();

            // Content-based routing
            example.contentBasedRoutingExample();

            // Error handling
            example.errorHandlingReQueueExample();

            // Workflow stages
            example.workflowStagesExample();

            // Error case with auto-ack
            example.reQueueWithAutoAckErrorExample();

        } finally {
            example.cleanup();
        }

        System.out.println("\nReQueue examples completed.");
    }
}
