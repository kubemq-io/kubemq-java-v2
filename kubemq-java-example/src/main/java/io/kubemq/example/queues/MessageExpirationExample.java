package io.kubemq.example.queues;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueueMessage;
import io.kubemq.sdk.queues.QueueSendResult;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.queues.QueuesPollRequest;
import io.kubemq.sdk.queues.QueuesPollResponse;
import io.kubemq.sdk.queues.QueueMessagesWaiting;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Message Expiration Example
 *
 * This example demonstrates how to use message expiration (TTL - Time To Live)
 * in KubeMQ queues. Expired messages are automatically removed from the queue
 * and will not be delivered to consumers.
 *
 * Use Cases:
 * - Time-sensitive notifications
 * - Session-based data
 * - Cache invalidation triggers
 * - Flash sales or limited-time offers
 * - Preventing stale data processing
 * - Rate limiting with sliding windows
 *
 * How it works:
 * 1. Message is sent with expirationInSeconds parameter
 * 2. KubeMQ tracks when the message was received
 * 3. If the message is not consumed before expiration, it's discarded
 * 4. Expired messages are not delivered and don't appear in polls
 *
 * Important Notes:
 * - Expiration is calculated from server receive time
 * - Expired messages are removed silently (no notification)
 * - Consider using DLQ for tracking expired messages
 * - Expiration and delay can be combined
 *
 * @see io.kubemq.sdk.queues.QueueMessage#getExpirationInSeconds()
 */
public class MessageExpirationExample {

    private final QueuesClient queuesClient;
    private final String channelName = "expiring-messages-channel";
    private final String address = "localhost:50000";
    private final String clientId = "expiration-example-client";
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Initializes the QueuesClient and verifies connection.
     */
    public MessageExpirationExample() {
        queuesClient = QueuesClient.builder()
                .address(address)
                .clientId(clientId)
                .build();

        ServerInfo pingResult = queuesClient.ping();
        System.out.println("Connected to: " + pingResult.getHost() + " v" + pingResult.getVersion());

        // Create the channel
        queuesClient.createQueuesChannel(channelName);
    }

    /**
     * Helper method to get current time formatted.
     */
    private String now() {
        return LocalDateTime.now().format(timeFormatter);
    }

    /**
     * Demonstrates basic message expiration.
     * A message with short TTL expires before it can be consumed.
     */
    public void basicExpirationExample() {
        System.out.println("\n=== Basic Expiration Example ===\n");

        int expirationSeconds = 3;

        // Send message with short expiration
        System.out.println("[" + now() + "] Sending message with " + expirationSeconds + "-second expiration...");

        QueueMessage message = QueueMessage.builder()
                .id(UUID.randomUUID().toString())
                .channel(channelName)
                .body("This message will expire quickly".getBytes())
                .metadata("Expires in " + expirationSeconds + " seconds")
                .expirationInSeconds(expirationSeconds)
                .build();

        QueueSendResult result = queuesClient.sendQueuesMessage(message);
        System.out.println("[" + now() + "] Message sent: " + result.getId());
        System.out.println("[" + now() + "] Will expire at: " +
                LocalDateTime.now().plusSeconds(expirationSeconds).format(timeFormatter));

        // Wait longer than expiration
        System.out.println("[" + now() + "] Waiting " + (expirationSeconds + 2) + " seconds...");
        try {
            Thread.sleep((expirationSeconds + 2) * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        // Try to receive - message should be expired
        System.out.println("[" + now() + "] Attempting to receive message...");

        QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                .channel(channelName)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(1)
                .autoAckMessages(true)
                .build();

        QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);
        if (response.getMessages().isEmpty()) {
            System.out.println("[" + now() + "] No message received - it expired as expected!");
        } else {
            System.out.println("[" + now() + "] Unexpectedly received: " +
                    new String(response.getMessages().get(0).getBody()));
        }
    }

    /**
     * Demonstrates successful consumption before expiration.
     */
    public void consumeBeforeExpirationExample() {
        System.out.println("\n=== Consume Before Expiration Example ===\n");

        int expirationSeconds = 10;

        // Send message
        System.out.println("[" + now() + "] Sending message with " + expirationSeconds + "-second expiration...");

        QueueMessage message = QueueMessage.builder()
                .id(UUID.randomUUID().toString())
                .channel(channelName)
                .body("Consume me quickly!".getBytes())
                .expirationInSeconds(expirationSeconds)
                .build();

        queuesClient.sendQueuesMessage(message);

        // Consume immediately (before expiration)
        System.out.println("[" + now() + "] Receiving message immediately...");

        QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                .channel(channelName)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(5)
                .autoAckMessages(true)
                .build();

        QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);
        if (!response.getMessages().isEmpty()) {
            System.out.println("[" + now() + "] Success! Message received before expiration: " +
                    new String(response.getMessages().get(0).getBody()));
        }
    }

    /**
     * Demonstrates different expiration times for message prioritization.
     * Messages with longer TTL are lower priority (can wait longer).
     */
    public void priorityByExpirationExample() {
        System.out.println("\n=== Priority by Expiration Example ===\n");

        System.out.println("Sending messages with different expirations (priority levels):\n");

        // High priority - expires quickly
        QueueMessage highPriority = QueueMessage.builder()
                .id("high-priority-" + UUID.randomUUID())
                .channel(channelName)
                .body("HIGH PRIORITY - Process immediately!".getBytes())
                .metadata("Priority: HIGH")
                .expirationInSeconds(5)
                .build();

        // Medium priority
        QueueMessage mediumPriority = QueueMessage.builder()
                .id("medium-priority-" + UUID.randomUUID())
                .channel(channelName)
                .body("MEDIUM PRIORITY - Process when possible".getBytes())
                .metadata("Priority: MEDIUM")
                .expirationInSeconds(30)
                .build();

        // Low priority - can wait
        QueueMessage lowPriority = QueueMessage.builder()
                .id("low-priority-" + UUID.randomUUID())
                .channel(channelName)
                .body("LOW PRIORITY - Process eventually".getBytes())
                .metadata("Priority: LOW")
                .expirationInSeconds(120)
                .build();

        queuesClient.sendQueuesMessage(highPriority);
        System.out.println("  HIGH priority sent (5s expiration)");

        queuesClient.sendQueuesMessage(mediumPriority);
        System.out.println("  MEDIUM priority sent (30s expiration)");

        queuesClient.sendQueuesMessage(lowPriority);
        System.out.println("  LOW priority sent (120s expiration)");

        System.out.println("\nConsumer should process in order received, but:");
        System.out.println("  - HIGH priority: Must process within 5 seconds");
        System.out.println("  - MEDIUM priority: Has 30 seconds");
        System.out.println("  - LOW priority: Has 2 minutes");

        // Receive all messages
        QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                .channel(channelName)
                .pollMaxMessages(10)
                .pollWaitTimeoutInSeconds(2)
                .autoAckMessages(true)
                .build();

        QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);
        System.out.println("\nReceived " + response.getMessages().size() + " messages before any expired.");
    }

    /**
     * Demonstrates combining expiration with Dead Letter Queue.
     * Instead of silently discarding, expired messages can be routed to DLQ.
     */
    public void expirationWithDLQExample() {
        System.out.println("\n=== Expiration with DLQ Example ===\n");

        String dlqChannel = channelName + "-dlq";
        queuesClient.createQueuesChannel(dlqChannel);

        System.out.println("Sending message with expiration and DLQ configuration...");
        System.out.println("  Expiration: 3 seconds");
        System.out.println("  DLQ: " + dlqChannel);
        System.out.println("  Max attempts: 1\n");

        // Note: Expiration doesn't automatically send to DLQ
        // DLQ is for processing failures, not expiration
        // This example shows both concepts together

        QueueMessage message = QueueMessage.builder()
                .id(UUID.randomUUID().toString())
                .channel(channelName)
                .body("Message with DLQ fallback".getBytes())
                .expirationInSeconds(3)
                .attemptsBeforeDeadLetterQueue(1)
                .deadLetterQueue(dlqChannel)
                .build();

        queuesClient.sendQueuesMessage(message);
        System.out.println("[" + now() + "] Message sent");

        // Receive but don't ack (simulating processing failure)
        QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                .channel(channelName)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(2)
                .visibilitySeconds(2)
                .autoAckMessages(false)
                .build();

        QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);
        if (!response.getMessages().isEmpty()) {
            System.out.println("[" + now() + "] Received message, NOT acknowledging (simulating failure)");
            response.getMessages().get(0).reject();
        }

        // Wait and check DLQ
        System.out.println("[" + now() + "] Waiting for message to move to DLQ...");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        QueuesPollRequest dlqPoll = QueuesPollRequest.builder()
                .channel(dlqChannel)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(2)
                .autoAckMessages(true)
                .build();

        QueuesPollResponse dlqResponse = queuesClient.receiveQueuesMessages(dlqPoll);
        if (!dlqResponse.getMessages().isEmpty()) {
            System.out.println("[" + now() + "] Message found in DLQ: " +
                    new String(dlqResponse.getMessages().get(0).getBody()));
        }

        // Cleanup
        queuesClient.deleteQueuesChannel(dlqChannel);
    }

    /**
     * Demonstrates checking message waiting status including expiration.
     */
    public void checkWaitingMessagesExample() {
        System.out.println("\n=== Check Waiting Messages Example ===\n");

        // Send multiple messages with different expirations
        System.out.println("Sending 3 messages with varied expirations...");

        for (int i = 1; i <= 3; i++) {
            int expiration = i * 5; // 5, 10, 15 seconds

            QueueMessage msg = QueueMessage.builder()
                    .id("waiting-" + i)
                    .channel(channelName)
                    .body(("Message " + i).getBytes())
                    .expirationInSeconds(expiration)
                    .build();

            queuesClient.sendQueuesMessage(msg);
            System.out.println("  Message " + i + ": expires in " + expiration + "s");
        }

        // Check waiting messages
        System.out.println("\n[" + now() + "] Checking waiting messages...");

        QueueMessagesWaiting waiting = queuesClient.waiting(channelName, 10, 2);
        System.out.println("Waiting messages: " + waiting.getMessages().size());

        waiting.getMessages().forEach(msg -> {
            System.out.println("  - ID: " + msg.getId() +
                    ", Expires at: " + msg.getExpiredAt());
        });

        // Wait for first message to expire
        System.out.println("\n[" + now() + "] Waiting 6 seconds for first message to expire...");
        try {
            Thread.sleep(6000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        // Check again
        System.out.println("[" + now() + "] Checking waiting messages again...");
        waiting = queuesClient.waiting(channelName, 10, 2);
        System.out.println("Waiting messages now: " + waiting.getMessages().size());

        // Cleanup remaining
        QueuesPollRequest cleanup = QueuesPollRequest.builder()
                .channel(channelName)
                .pollMaxMessages(10)
                .pollWaitTimeoutInSeconds(1)
                .autoAckMessages(true)
                .build();
        queuesClient.receiveQueuesMessages(cleanup);
    }

    /**
     * Cleans up resources.
     */
    public void cleanup() {
        try {
            queuesClient.deleteQueuesChannel(channelName);
            System.out.println("\nCleaned up channel: " + channelName);
        } catch (Exception e) {
            // Channel might not exist
        }
        queuesClient.close();
    }

    /**
     * Main method demonstrating all expiration patterns.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           KubeMQ Message Expiration Examples                 ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        MessageExpirationExample example = new MessageExpirationExample();

        try {
            // Basic expiration
            example.basicExpirationExample();

            // Successful consumption
            example.consumeBeforeExpirationExample();

            // Priority by expiration
            example.priorityByExpirationExample();

            // Check waiting messages
            example.checkWaitingMessagesExample();

            // Expiration with DLQ
            example.expirationWithDLQExample();

        } finally {
            example.cleanup();
        }

        System.out.println("\nMessage expiration examples completed.");
    }
}
