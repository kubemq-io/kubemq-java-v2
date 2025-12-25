package io.kubemq.example.queues;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueueMessage;
import io.kubemq.sdk.queues.QueueSendResult;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.queues.QueuesPollRequest;
import io.kubemq.sdk.queues.QueuesPollResponse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Message Delay Example
 *
 * This example demonstrates how to send delayed messages to KubeMQ queues.
 * Delayed messages are not immediately available for consumption - they become
 * visible only after the specified delay period has elapsed.
 *
 * Use Cases:
 * - Scheduled task execution
 * - Retry mechanisms with backoff
 * - Time-based workflow triggers
 * - Reminder systems
 * - Rate limiting
 * - Deferred processing
 *
 * How it works:
 * 1. Message is sent with delayInSeconds parameter
 * 2. KubeMQ stores the message but marks it as not yet available
 * 3. After the delay period, the message becomes visible to consumers
 * 4. Consumers can then poll and process the message normally
 *
 * Important Notes:
 * - Delay is calculated from the time the server receives the message
 * - Network latency affects actual delay timing
 * - Delayed messages still count against queue depth
 * - Delay and expiration can be combined
 *
 * @see io.kubemq.sdk.queues.QueueMessage#getDelayInSeconds()
 */
public class MessageDelayExample {

    private final QueuesClient queuesClient;
    private final String channelName = "delayed-messages-channel";
    private final String address = "localhost:50000";
    private final String clientId = "delay-example-client";
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Initializes the QueuesClient and verifies connection.
     */
    public MessageDelayExample() {
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
     * Sends a message with a specified delay.
     *
     * @param delaySeconds Number of seconds to delay the message
     * @param messageContent Content of the message
     * @return The send result
     */
    public QueueSendResult sendDelayedMessage(int delaySeconds, String messageContent) {
        QueueMessage message = QueueMessage.builder()
                .id(UUID.randomUUID().toString())
                .channel(channelName)
                .body(messageContent.getBytes())
                .metadata("Delayed message - will be available in " + delaySeconds + " seconds")
                .delayInSeconds(delaySeconds)  // Key parameter for delayed delivery
                .build();

        QueueSendResult result = queuesClient.sendQueuesMessage(message);

        System.out.println("Message sent at " + LocalDateTime.now().format(timeFormatter));
        System.out.println("  ID: " + result.getId());
        System.out.println("  Delay: " + delaySeconds + " seconds");
        System.out.println("  Will be available at: " +
                LocalDateTime.now().plusSeconds(delaySeconds).format(timeFormatter));

        return result;
    }

    /**
     * Demonstrates basic delayed message sending and receiving.
     * Sends a message with a 5-second delay and shows it's not immediately available.
     */
    public void basicDelayExample() {
        System.out.println("\n=== Basic Delay Example ===\n");

        int delaySeconds = 5;

        // Send delayed message
        System.out.println("Sending message with " + delaySeconds + "-second delay...");
        sendDelayedMessage(delaySeconds, "This message was delayed by " + delaySeconds + " seconds");

        // Try to receive immediately - should get no messages
        System.out.println("\nAttempting to receive immediately (before delay expires)...");
        QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                .channel(channelName)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(1)  // Short timeout
                .autoAckMessages(true)
                .build();

        QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);
        if (response.getMessages().isEmpty()) {
            System.out.println("  No messages available (as expected - still within delay period)");
        }

        // Wait for delay to expire
        System.out.println("\nWaiting for delay to expire...");
        try {
            Thread.sleep((delaySeconds + 1) * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        // Now receive - should get the message
        System.out.println("Attempting to receive after delay expired...");
        response = queuesClient.receiveQueuesMessages(pollRequest);
        if (!response.getMessages().isEmpty()) {
            System.out.println("  Message received: " + new String(response.getMessages().get(0).getBody()));
            System.out.println("  Received at: " + LocalDateTime.now().format(timeFormatter));
        }
    }

    /**
     * Demonstrates sending multiple messages with different delays.
     * Shows how messages become available in delay order, not send order.
     */
    public void multipleDelaysExample() {
        System.out.println("\n=== Multiple Delays Example ===\n");

        // Send messages with different delays (in reverse order)
        System.out.println("Sending 3 messages with different delays...\n");

        sendDelayedMessage(6, "Message A - 6 second delay");
        sendDelayedMessage(3, "Message B - 3 second delay");
        sendDelayedMessage(1, "Message C - 1 second delay");

        System.out.println("\nMessages will become available in order: C, B, A (by delay, not send order)\n");

        // Poll for messages as they become available
        System.out.println("Polling for messages as they become available...\n");

        for (int i = 0; i < 3; i++) {
            QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                    .channel(channelName)
                    .pollMaxMessages(1)
                    .pollWaitTimeoutInSeconds(10)
                    .autoAckMessages(true)
                    .build();

            QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);
            if (!response.getMessages().isEmpty()) {
                String body = new String(response.getMessages().get(0).getBody());
                System.out.println("Received at " + LocalDateTime.now().format(timeFormatter) + ": " + body);
            }
        }
    }

    /**
     * Demonstrates combining delay with expiration.
     * The message must be consumed within the window between delay end and expiration.
     */
    public void delayWithExpirationExample() {
        System.out.println("\n=== Delay with Expiration Example ===\n");

        int delaySeconds = 3;
        int expirationSeconds = 8;  // Message expires 8 seconds after being sent

        System.out.println("Sending message with:");
        System.out.println("  Delay: " + delaySeconds + " seconds (available after)");
        System.out.println("  Expiration: " + expirationSeconds + " seconds (expires after)");
        System.out.println("  Available window: seconds " + delaySeconds + " to " + expirationSeconds + "\n");

        QueueMessage message = QueueMessage.builder()
                .id(UUID.randomUUID().toString())
                .channel(channelName)
                .body("Message with delay and expiration".getBytes())
                .metadata("Available window: " + delaySeconds + "s to " + expirationSeconds + "s")
                .delayInSeconds(delaySeconds)
                .expirationInSeconds(expirationSeconds)
                .build();

        QueueSendResult result = queuesClient.sendQueuesMessage(message);
        System.out.println("Message sent: " + result.getId());

        // Wait for delay to expire, then receive
        System.out.println("\nWaiting for delay period...");
        try {
            Thread.sleep((delaySeconds + 1) * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                .channel(channelName)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(2)
                .autoAckMessages(true)
                .build();

        QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);
        if (!response.getMessages().isEmpty()) {
            System.out.println("Message received within the available window!");
        }
    }

    /**
     * Demonstrates using delay for implementing retry with backoff.
     * When a message fails processing, re-queue it with increasing delay.
     */
    public void retryWithBackoffExample() {
        System.out.println("\n=== Retry with Backoff Example ===\n");

        System.out.println("This pattern uses delay for retry backoff:");
        System.out.println("  Attempt 1: Immediate");
        System.out.println("  Attempt 2: 2-second delay");
        System.out.println("  Attempt 3: 4-second delay");
        System.out.println("  Attempt 4: 8-second delay\n");

        // Simulate a message that needs retry
        String originalMessageId = UUID.randomUUID().toString();
        int attempt = 1;
        int maxAttempts = 4;

        QueueMessage initialMessage = QueueMessage.builder()
                .id(originalMessageId)
                .channel(channelName)
                .body("Task that might fail".getBytes())
                .metadata("Attempt " + attempt + " of " + maxAttempts)
                .build();

        queuesClient.sendQueuesMessage(initialMessage);
        System.out.println("Initial message sent (attempt 1)");

        // Simulate processing and retry with backoff
        for (attempt = 2; attempt <= maxAttempts; attempt++) {
            // Calculate exponential backoff delay
            int backoffDelay = (int) Math.pow(2, attempt - 1);

            QueueMessage retryMessage = QueueMessage.builder()
                    .id(originalMessageId + "-retry-" + attempt)
                    .channel(channelName)
                    .body(("Task retry - attempt " + attempt).getBytes())
                    .metadata("Retry attempt " + attempt + " of " + maxAttempts)
                    .delayInSeconds(backoffDelay)
                    .build();

            queuesClient.sendQueuesMessage(retryMessage);
            System.out.println("Retry message scheduled (attempt " + attempt + ", delay: " + backoffDelay + "s)");
        }

        // Clean up by receiving all messages
        System.out.println("\nNote: In real implementation, you would:");
        System.out.println("  1. Poll and process the message");
        System.out.println("  2. If processing fails, send retry with delay");
        System.out.println("  3. Track attempt count in message tags");
        System.out.println("  4. Move to DLQ after max attempts");
    }

    /**
     * Demonstrates scheduled task pattern using delayed messages.
     */
    public void scheduledTaskExample() {
        System.out.println("\n=== Scheduled Task Example ===\n");

        // Schedule tasks for specific future times
        int[] scheduledDelays = {5, 10, 15}; // seconds from now

        System.out.println("Scheduling 3 tasks...\n");

        for (int i = 0; i < scheduledDelays.length; i++) {
            int delay = scheduledDelays[i];

            QueueMessage task = QueueMessage.builder()
                    .id("task-" + (i + 1))
                    .channel(channelName)
                    .body(("Execute scheduled task " + (i + 1)).getBytes())
                    .metadata("Scheduled task")
                    .delayInSeconds(delay)
                    .build();

            queuesClient.sendQueuesMessage(task);

            LocalDateTime scheduledTime = LocalDateTime.now().plusSeconds(delay);
            System.out.println("Task " + (i + 1) + " scheduled for: " + scheduledTime.format(timeFormatter));
        }

        System.out.println("\nTasks will execute at their scheduled times.");
        System.out.println("A worker process would poll continuously to process them.\n");
    }

    /**
     * Cleans up resources.
     */
    public void cleanup() {
        try {
            queuesClient.deleteQueuesChannel(channelName);
            System.out.println("Cleaned up channel: " + channelName);
        } catch (Exception e) {
            // Channel might not exist
        }
        queuesClient.close();
    }

    /**
     * Main method demonstrating all delay patterns.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           KubeMQ Message Delay Examples                      ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        MessageDelayExample example = new MessageDelayExample();

        try {
            // Basic delay demonstration
            example.basicDelayExample();

            // Multiple delays
            example.multipleDelaysExample();

            // Delay with expiration
            example.delayWithExpirationExample();

            // Retry with backoff pattern
            example.retryWithBackoffExample();

            // Scheduled tasks pattern
            example.scheduledTaskExample();

        } finally {
            example.cleanup();
        }

        System.out.println("\nMessage delay examples completed.");
    }
}
