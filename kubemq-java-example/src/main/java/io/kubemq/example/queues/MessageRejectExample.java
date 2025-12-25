package io.kubemq.example.queues;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueueMessage;
import io.kubemq.sdk.queues.QueueMessageReceived;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.queues.QueuesPollRequest;
import io.kubemq.sdk.queues.QueuesPollResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Message Reject Example
 *
 * This example demonstrates how to reject messages in KubeMQ queues.
 * Rejecting a message indicates that it couldn't be processed and should
 * be made available for redelivery or moved to a Dead Letter Queue (DLQ).
 *
 * Reject vs Acknowledge:
 * - ack(): Message processed successfully, remove from queue
 * - reject(): Message processing failed, make available for retry
 *
 * What happens when you reject:
 * 1. Message becomes available for another consumer
 * 2. Message's receiveCount is incremented
 * 3. If receiveCount exceeds attemptsBeforeDeadLetterQueue, moves to DLQ
 * 4. Original visibility is released immediately
 *
 * Use Cases:
 * - Processing errors (database down, service unavailable)
 * - Validation failures that might succeed later
 * - Temporary resource constraints
 * - Poison message detection
 * - Implementing retry patterns
 *
 * @see io.kubemq.sdk.queues.QueueMessageReceived#reject()
 * @see io.kubemq.sdk.queues.QueueMessageReceived#ack()
 */
public class MessageRejectExample {

    private final QueuesClient queuesClient;
    private final String channelName = "reject-example-channel";
    private final String dlqChannel = "reject-example-dlq";
    private final String address = "localhost:50000";
    private final String clientId = "reject-example-client";

    /**
     * Initializes the QueuesClient and verifies connection.
     */
    public MessageRejectExample() {
        queuesClient = QueuesClient.builder()
                .address(address)
                .clientId(clientId)
                .build();

        ServerInfo pingResult = queuesClient.ping();
        System.out.println("Connected to: " + pingResult.getHost() + " v" + pingResult.getVersion());

        queuesClient.createQueuesChannel(channelName);
        queuesClient.createQueuesChannel(dlqChannel);
    }

    /**
     * Demonstrates basic message rejection.
     */
    public void basicRejectExample() {
        System.out.println("\n=== Basic Reject Example ===\n");

        // Send a message
        QueueMessage message = QueueMessage.builder()
                .id(UUID.randomUUID().toString())
                .channel(channelName)
                .body("Message that will be rejected".getBytes())
                .build();

        queuesClient.sendQueuesMessage(message);
        System.out.println("1. Sent message to queue");

        // Receive message
        QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                .channel(channelName)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(5)
                .autoAckMessages(false)
                .build();

        QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);

        if (!response.getMessages().isEmpty()) {
            QueueMessageReceived msg = response.getMessages().get(0);
            System.out.println("2. Received message: " + msg.getId());
            System.out.println("   Receive count: " + msg.getReceiveCount());

            // Reject the message
            msg.reject();
            System.out.println("3. Message rejected");
        }

        // Receive again - same message should be available
        response = queuesClient.receiveQueuesMessages(pollRequest);
        if (!response.getMessages().isEmpty()) {
            QueueMessageReceived msg = response.getMessages().get(0);
            System.out.println("4. Message redelivered: " + msg.getId());
            System.out.println("   Receive count now: " + msg.getReceiveCount());

            // Acknowledge this time
            msg.ack();
            System.out.println("5. Message acknowledged on second attempt");
        }
    }

    /**
     * Demonstrates rejection with retry limit and DLQ routing.
     */
    public void rejectWithDLQExample() {
        System.out.println("\n=== Reject with DLQ Example ===\n");

        int maxAttempts = 3;

        // Send message with DLQ configuration
        Map<String, String> tags = new HashMap<>();
        tags.put("type", "important");

        QueueMessage message = QueueMessage.builder()
                .id("dlq-test-" + UUID.randomUUID())
                .channel(channelName)
                .body("Message that will fail multiple times".getBytes())
                .metadata("Max attempts: " + maxAttempts)
                .tags(tags)
                .attemptsBeforeDeadLetterQueue(maxAttempts)
                .deadLetterQueue(dlqChannel)
                .build();

        queuesClient.sendQueuesMessage(message);
        System.out.println("Sent message with DLQ config:");
        System.out.println("  Max attempts: " + maxAttempts);
        System.out.println("  DLQ: " + dlqChannel + "\n");

        // Simulate multiple processing failures
        for (int attempt = 1; attempt <= maxAttempts + 1; attempt++) {
            QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                    .channel(channelName)
                    .pollMaxMessages(1)
                    .pollWaitTimeoutInSeconds(2)
                    .autoAckMessages(false)
                    .build();

            QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);

            if (!response.getMessages().isEmpty()) {
                QueueMessageReceived msg = response.getMessages().get(0);
                System.out.println("Attempt " + attempt + ":");
                System.out.println("  Receive count: " + msg.getReceiveCount());

                // Simulate processing failure
                System.out.println("  Processing failed, rejecting...");
                msg.reject();
            } else {
                System.out.println("Attempt " + attempt + ": No message in main queue");
                break;
            }

            // Small delay between attempts
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Check DLQ for the message
        System.out.println("\nChecking DLQ...");
        QueuesPollRequest dlqPoll = QueuesPollRequest.builder()
                .channel(dlqChannel)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(2)
                .autoAckMessages(true)
                .build();

        QueuesPollResponse dlqResponse = queuesClient.receiveQueuesMessages(dlqPoll);
        if (!dlqResponse.getMessages().isEmpty()) {
            QueueMessageReceived dlqMsg = dlqResponse.getMessages().get(0);
            System.out.println("Message found in DLQ:");
            System.out.println("  ID: " + dlqMsg.getId());
            System.out.println("  Body: " + new String(dlqMsg.getBody()));
            System.out.println("  Final receive count: " + dlqMsg.getReceiveCount());
            System.out.println("  Was re-routed: " + dlqMsg.isReRouted());
        }
    }

    /**
     * Demonstrates conditional reject based on processing outcome.
     */
    public void conditionalRejectExample() {
        System.out.println("\n=== Conditional Reject Example ===\n");

        // Send messages with different "validity"
        String[] messageTypes = {"valid", "invalid", "valid", "error", "valid"};

        for (int i = 0; i < messageTypes.length; i++) {
            Map<String, String> tags = new HashMap<>();
            tags.put("validity", messageTypes[i]);

            QueueMessage message = QueueMessage.builder()
                    .id("msg-" + (i + 1))
                    .channel(channelName)
                    .body(("Message " + (i + 1) + " (" + messageTypes[i] + ")").getBytes())
                    .tags(tags)
                    .build();

            queuesClient.sendQueuesMessage(message);
        }
        System.out.println("Sent 5 messages with varying validity\n");

        // Process messages with conditional rejection
        System.out.println("Processing messages:");
        int processed = 0;
        int rejected = 0;

        while (true) {
            QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                    .channel(channelName)
                    .pollMaxMessages(1)
                    .pollWaitTimeoutInSeconds(1)
                    .autoAckMessages(false)
                    .build();

            QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);
            if (response.getMessages().isEmpty()) {
                break;
            }

            QueueMessageReceived msg = response.getMessages().get(0);
            String validity = msg.getTags().get("validity");
            String body = new String(msg.getBody());

            System.out.print("  " + body + " -> ");

            switch (validity) {
                case "valid":
                    msg.ack();
                    System.out.println("ACKNOWLEDGED");
                    processed++;
                    break;

                case "invalid":
                    // Invalid messages are rejected for potential retry
                    msg.reject();
                    System.out.println("REJECTED (will retry)");
                    rejected++;
                    break;

                case "error":
                    // Error messages are also rejected
                    msg.reject();
                    System.out.println("REJECTED (error condition)");
                    rejected++;
                    break;

                default:
                    msg.ack();
                    System.out.println("ACKNOWLEDGED (unknown type)");
                    processed++;
            }
        }

        System.out.println("\nResults:");
        System.out.println("  Processed: " + processed);
        System.out.println("  Rejected: " + rejected);
        System.out.println("  (Rejected messages would be redelivered to other consumers)");

        // Clean up rejected messages
        QueuesPollRequest cleanup = QueuesPollRequest.builder()
                .channel(channelName)
                .pollMaxMessages(10)
                .pollWaitTimeoutInSeconds(1)
                .autoAckMessages(true)
                .build();
        queuesClient.receiveQueuesMessages(cleanup);
    }

    /**
     * Demonstrates reject in processing pipeline with error handling.
     */
    public void pipelineProcessingExample() {
        System.out.println("\n=== Pipeline Processing with Reject ===\n");

        // Send a message to process
        QueueMessage message = QueueMessage.builder()
                .id("pipeline-" + UUID.randomUUID())
                .channel(channelName)
                .body("{\"action\":\"processOrder\",\"orderId\":\"ORD-123\"}".getBytes())
                .metadata("Order processing request")
                .attemptsBeforeDeadLetterQueue(3)
                .deadLetterQueue(dlqChannel)
                .build();

        queuesClient.sendQueuesMessage(message);
        System.out.println("Order submitted for processing\n");

        // Process with pipeline stages
        QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                .channel(channelName)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(5)
                .autoAckMessages(false)
                .visibilitySeconds(30)
                .build();

        QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);

        if (!response.getMessages().isEmpty()) {
            QueueMessageReceived msg = response.getMessages().get(0);
            String body = new String(msg.getBody());

            System.out.println("Processing order: " + body);
            System.out.println("Pipeline stages:");

            try {
                // Stage 1: Validate
                System.out.print("  1. Validation... ");
                validateOrder(body);
                System.out.println("PASSED");

                // Stage 2: Check inventory
                System.out.print("  2. Inventory check... ");
                checkInventory(body);
                System.out.println("PASSED");

                // Stage 3: Process payment (simulated failure)
                System.out.print("  3. Payment processing... ");
                boolean simulateFailure = msg.getReceiveCount() < 2;
                if (simulateFailure) {
                    throw new RuntimeException("Payment gateway timeout");
                }
                System.out.println("PASSED");

                // Stage 4: Fulfill
                System.out.print("  4. Fulfillment... ");
                fulfillOrder(body);
                System.out.println("PASSED");

                // All stages passed
                msg.ack();
                System.out.println("\nOrder processed successfully!");

            } catch (Exception e) {
                System.out.println("FAILED - " + e.getMessage());
                System.out.println("\nRejecting message for retry (attempt " +
                        msg.getReceiveCount() + ")");
                msg.reject();
            }
        }

        // Process retry
        System.out.println("\n--- Retry Processing ---\n");
        response = queuesClient.receiveQueuesMessages(pollRequest);

        if (!response.getMessages().isEmpty()) {
            QueueMessageReceived msg = response.getMessages().get(0);
            System.out.println("Retry attempt " + msg.getReceiveCount());
            System.out.println("Processing... (simulating success on retry)");
            msg.ack();
            System.out.println("Order completed on retry!");
        }
    }

    // Simulated pipeline methods
    private void validateOrder(String order) throws Exception {
        Thread.sleep(50);
    }

    private void checkInventory(String order) throws Exception {
        Thread.sleep(50);
    }

    private void fulfillOrder(String order) throws Exception {
        Thread.sleep(50);
    }

    /**
     * Demonstrates reject behavior with visibility timeout.
     */
    public void rejectWithVisibilityExample() {
        System.out.println("\n=== Reject with Visibility Example ===\n");

        // Send a message
        QueueMessage message = QueueMessage.builder()
                .id(UUID.randomUUID().toString())
                .channel(channelName)
                .body("Message with visibility".getBytes())
                .build();

        queuesClient.sendQueuesMessage(message);
        System.out.println("Sent message");

        // Receive with visibility
        QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                .channel(channelName)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(5)
                .autoAckMessages(false)
                .visibilitySeconds(30)  // 30-second visibility
                .build();

        QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);

        if (!response.getMessages().isEmpty()) {
            QueueMessageReceived msg = response.getMessages().get(0);
            System.out.println("Received message with 30s visibility timeout");
            System.out.println("Message ID: " + msg.getId());

            // Normally, message wouldn't be available for 30 seconds
            // But reject() releases it immediately
            System.out.println("\nRejecting message (releases visibility immediately)...");
            msg.reject();

            // Try to receive again immediately
            response = queuesClient.receiveQueuesMessages(pollRequest);
            if (!response.getMessages().isEmpty()) {
                System.out.println("Message available again immediately after reject!");
                response.getMessages().get(0).ack();
            }
        }
    }

    /**
     * Cleans up resources.
     */
    public void cleanup() {
        try {
            queuesClient.deleteQueuesChannel(channelName);
            queuesClient.deleteQueuesChannel(dlqChannel);
            System.out.println("\nCleaned up channels");
        } catch (Exception e) {
            // Ignore
        }
        queuesClient.close();
    }

    /**
     * Main method demonstrating all reject patterns.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           KubeMQ Message Reject Examples                     ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        MessageRejectExample example = new MessageRejectExample();

        try {
            // Basic rejection
            example.basicRejectExample();

            // Reject with DLQ
            example.rejectWithDLQExample();

            // Conditional rejection
            example.conditionalRejectExample();

            // Pipeline processing
            example.pipelineProcessingExample();

            // Visibility behavior
            example.rejectWithVisibilityExample();

        } finally {
            example.cleanup();
        }

        System.out.println("\nMessage reject examples completed.");
    }
}
