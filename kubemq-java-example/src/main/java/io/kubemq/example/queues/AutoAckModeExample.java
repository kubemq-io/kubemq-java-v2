package io.kubemq.example.queues;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueueMessage;
import io.kubemq.sdk.queues.QueueMessageReceived;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.queues.QueuesPollRequest;
import io.kubemq.sdk.queues.QueuesPollResponse;

import java.util.UUID;

/**
 * Auto-Acknowledge Mode Example
 *
 * This example demonstrates the auto-acknowledge feature in KubeMQ queues.
 * When auto-ack is enabled, messages are automatically acknowledged upon receipt,
 * simplifying consumer code but reducing control over message handling.
 *
 * Comparison of Modes:
 *
 * AUTO-ACK MODE (autoAckMessages = true):
 * - Messages acknowledged immediately upon delivery
 * - Simpler code - no need to call ack() or reject()
 * - Message removed from queue instantly
 * - Cannot requeue or reject after receipt
 * - No visibility timeout needed
 * - Best for: Simple, stateless processors; logging; analytics
 *
 * MANUAL ACK MODE (autoAckMessages = false):
 * - Consumer must explicitly ack() or reject()
 * - Full control over message lifecycle
 * - Can use visibility timeout for processing time
 * - Can requeue to different channels
 * - Messages returned to queue if consumer crashes
 * - Best for: Critical processing; workflows; error handling
 *
 * When to use Auto-Ack:
 * - Fire-and-forget scenarios
 * - Logging and metrics collection
 * - Non-critical notifications
 * - Simple, fast processing that rarely fails
 * - When at-most-once delivery is acceptable
 *
 * When NOT to use Auto-Ack:
 * - Critical business transactions
 * - When processing might fail
 * - When you need to route messages based on content
 * - When exactly-once or at-least-once delivery is required
 *
 * @see io.kubemq.sdk.queues.QueuesPollRequest#isAutoAckMessages()
 */
public class AutoAckModeExample {

    private final QueuesClient queuesClient;
    private final String channelName = "auto-ack-example-channel";
    private final String address = "localhost:50000";
    private final String clientId = "auto-ack-example-client";

    /**
     * Initializes the QueuesClient and verifies connection.
     */
    public AutoAckModeExample() {
        queuesClient = QueuesClient.builder()
                .address(address)
                .clientId(clientId)
                .build();

        ServerInfo pingResult = queuesClient.ping();
        System.out.println("Connected to: " + pingResult.getHost() + " v" + pingResult.getVersion());

        queuesClient.createQueuesChannel(channelName);
    }

    /**
     * Sends multiple test messages for the examples.
     */
    private void sendTestMessages(int count) {
        for (int i = 1; i <= count; i++) {
            QueueMessage message = QueueMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .channel(channelName)
                    .body(("Test message " + i).getBytes())
                    .metadata("Message number " + i)
                    .build();

            queuesClient.sendQueuesMessage(message);
        }
        System.out.println("Sent " + count + " test messages\n");
    }

    /**
     * Demonstrates auto-ack mode - simple receive without explicit acknowledgment.
     */
    public void autoAckModeExample() {
        System.out.println("=== Auto-Ack Mode Example ===\n");

        // Send some messages
        sendTestMessages(3);

        // Create poll request with auto-ack enabled
        QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                .channel(channelName)
                .pollMaxMessages(10)
                .pollWaitTimeoutInSeconds(5)
                .autoAckMessages(true)  // Enable auto-ack
                .build();

        System.out.println("Polling with autoAckMessages=true...\n");

        QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);

        if (!response.getMessages().isEmpty()) {
            System.out.println("Received " + response.getMessages().size() + " messages:");

            for (QueueMessageReceived msg : response.getMessages()) {
                System.out.println("  - " + new String(msg.getBody()));

                // Note: Message is already acknowledged!
                // No need to call msg.ack()
                // Calling ack() would throw an exception
            }

            System.out.println("\nMessages were automatically acknowledged upon receipt.");
            System.out.println("They are already removed from the queue.");
        }

        // Verify queue is empty
        var waiting = queuesClient.waiting(channelName, 10, 1);
        System.out.println("Messages remaining in queue: " + waiting.getMessages().size() + "\n");
    }

    /**
     * Demonstrates that ack/reject/requeue operations fail in auto-ack mode.
     */
    public void autoAckOperationsErrorExample() {
        System.out.println("=== Auto-Ack Operations Error Example ===\n");

        // Send a message
        sendTestMessages(1);

        // Receive with auto-ack
        QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                .channel(channelName)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(5)
                .autoAckMessages(true)
                .build();

        QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);

        if (!response.getMessages().isEmpty()) {
            QueueMessageReceived msg = response.getMessages().get(0);
            System.out.println("Received: " + new String(msg.getBody()));
            System.out.println("isAutoAcked: " + msg.isAutoAcked());

            // Try to ack - will fail
            System.out.println("\nAttempting to call ack()...");
            try {
                msg.ack();
            } catch (IllegalStateException e) {
                System.out.println("  Error: " + e.getMessage());
            }

            // Try to reject - will fail
            System.out.println("\nAttempting to call reject()...");
            try {
                msg.reject();
            } catch (IllegalStateException e) {
                System.out.println("  Error: " + e.getMessage());
            }

            // Try to requeue - will fail
            System.out.println("\nAttempting to call reQueue()...");
            try {
                msg.reQueue("other-channel");
            } catch (IllegalStateException e) {
                System.out.println("  Error: " + e.getMessage());
            }
        }

        System.out.println("\nConclusion: Auto-acked messages don't support manual operations.\n");
    }

    /**
     * Demonstrates manual ack mode for comparison.
     */
    public void manualAckModeExample() {
        System.out.println("=== Manual Ack Mode (for comparison) ===\n");

        // Send some messages
        sendTestMessages(3);

        // Create poll request with manual ack
        QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                .channel(channelName)
                .pollMaxMessages(10)
                .pollWaitTimeoutInSeconds(5)
                .autoAckMessages(false)  // Manual ack mode
                .build();

        System.out.println("Polling with autoAckMessages=false...\n");

        QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);

        if (!response.getMessages().isEmpty()) {
            System.out.println("Received " + response.getMessages().size() + " messages:");

            for (QueueMessageReceived msg : response.getMessages()) {
                String body = new String(msg.getBody());
                System.out.println("  Processing: " + body);

                // Simulate processing
                boolean success = !body.contains("2");  // Fail on message 2

                if (success) {
                    msg.ack();
                    System.out.println("    -> Acknowledged");
                } else {
                    msg.reject();
                    System.out.println("    -> Rejected (will retry)");
                }
            }
        }

        System.out.println("\nWith manual ack, you have full control over each message.\n");
    }

    /**
     * Demonstrates the simplicity benefit of auto-ack for logging scenarios.
     */
    public void loggingScenarioExample() {
        System.out.println("=== Logging Scenario (Auto-Ack Ideal Use Case) ===\n");

        System.out.println("Scenario: Collecting log events for analytics");
        System.out.println("Why auto-ack works well here:");
        System.out.println("  - Log processing is stateless");
        System.out.println("  - Losing an occasional log is acceptable");
        System.out.println("  - Processing is fast and rarely fails");
        System.out.println("  - Simpler code, lower latency\n");

        // Send log events
        String[] logEvents = {
                "{\"level\":\"INFO\",\"msg\":\"User logged in\"}",
                "{\"level\":\"WARN\",\"msg\":\"High memory usage\"}",
                "{\"level\":\"INFO\",\"msg\":\"Request processed\"}"
        };

        for (String event : logEvents) {
            QueueMessage message = QueueMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .channel(channelName)
                    .body(event.getBytes())
                    .build();
            queuesClient.sendQueuesMessage(message);
        }
        System.out.println("Sent " + logEvents.length + " log events\n");

        // Simple log processor with auto-ack
        QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                .channel(channelName)
                .pollMaxMessages(100)
                .pollWaitTimeoutInSeconds(2)
                .autoAckMessages(true)  // Perfect for logging
                .build();

        QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);

        System.out.println("Processing log events:");
        for (QueueMessageReceived msg : response.getMessages()) {
            // Simple processing - just output
            System.out.println("  LOG: " + new String(msg.getBody()));
            // No ack needed - auto-handled!
        }

        System.out.println("\nAll logs processed with minimal code.\n");
    }

    /**
     * Demonstrates when NOT to use auto-ack (order processing scenario).
     */
    public void orderProcessingScenarioExample() {
        System.out.println("=== Order Processing (Manual Ack Required) ===\n");

        System.out.println("Scenario: Processing e-commerce orders");
        System.out.println("Why auto-ack is BAD here:");
        System.out.println("  - Orders are critical business data");
        System.out.println("  - Processing might fail (payment, inventory, etc.)");
        System.out.println("  - Need retry capability for failures");
        System.out.println("  - Might need to route to different queues\n");

        // Send an order
        QueueMessage order = QueueMessage.builder()
                .id("ORDER-" + UUID.randomUUID())
                .channel(channelName)
                .body("{\"orderId\":\"12345\",\"amount\":99.99}".getBytes())
                .metadata("E-commerce order")
                .build();

        queuesClient.sendQueuesMessage(order);
        System.out.println("Order submitted to queue\n");

        // Process with manual ack (the right way)
        QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                .channel(channelName)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(5)
                .autoAckMessages(false)  // Critical: Manual ack for orders!
                .visibilitySeconds(30)   // Give time to process
                .build();

        QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);

        if (!response.getMessages().isEmpty()) {
            QueueMessageReceived msg = response.getMessages().get(0);
            System.out.println("Processing order: " + new String(msg.getBody()));

            try {
                // Simulate order processing
                processOrder(msg);
                msg.ack();
                System.out.println("Order successfully processed and acknowledged");
            } catch (Exception e) {
                System.out.println("Order processing failed: " + e.getMessage());
                msg.reject();  // Message returns to queue for retry
                System.out.println("Order rejected - will be retried");
            }
        }
    }

    /**
     * Simulates order processing.
     */
    private void processOrder(QueueMessageReceived order) throws Exception {
        // Simulate processing steps
        Thread.sleep(100);  // Validate order
        Thread.sleep(100);  // Process payment
        Thread.sleep(100);  // Update inventory
        // If any step fails, exception would be thrown
    }

    /**
     * Shows configuration comparison between modes.
     */
    public void configurationComparisonExample() {
        System.out.println("=== Configuration Comparison ===\n");

        System.out.println("AUTO-ACK MODE CONFIGURATION:");
        System.out.println("┌────────────────────────────────────────────────┐");
        System.out.println("│ QueuesPollRequest.builder()                    │");
        System.out.println("│     .channel(\"my-channel\")                     │");
        System.out.println("│     .pollMaxMessages(10)                       │");
        System.out.println("│     .pollWaitTimeoutInSeconds(30)              │");
        System.out.println("│     .autoAckMessages(true)    // <-- KEY       │");
        System.out.println("│     .build();                                  │");
        System.out.println("│                                                │");
        System.out.println("│ Note: visibilitySeconds not needed             │");
        System.out.println("└────────────────────────────────────────────────┘\n");

        System.out.println("MANUAL ACK MODE CONFIGURATION:");
        System.out.println("┌────────────────────────────────────────────────┐");
        System.out.println("│ QueuesPollRequest.builder()                    │");
        System.out.println("│     .channel(\"my-channel\")                     │");
        System.out.println("│     .pollMaxMessages(10)                       │");
        System.out.println("│     .pollWaitTimeoutInSeconds(30)              │");
        System.out.println("│     .autoAckMessages(false)   // <-- KEY       │");
        System.out.println("│     .visibilitySeconds(60)    // <-- IMPORTANT │");
        System.out.println("│     .build();                                  │");
        System.out.println("│                                                │");
        System.out.println("│ Remember to call: msg.ack() or msg.reject()    │");
        System.out.println("└────────────────────────────────────────────────┘\n");
    }

    /**
     * Cleans up resources.
     */
    public void cleanup() {
        try {
            queuesClient.deleteQueuesChannel(channelName);
            System.out.println("Cleaned up channel: " + channelName);
        } catch (Exception e) {
            // Ignore
        }
        queuesClient.close();
    }

    /**
     * Main method demonstrating auto-ack modes.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           KubeMQ Auto-Acknowledge Mode Examples              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        AutoAckModeExample example = new AutoAckModeExample();

        try {
            // Configuration comparison
            example.configurationComparisonExample();

            // Auto-ack basics
            example.autoAckModeExample();

            // Error cases in auto-ack
            example.autoAckOperationsErrorExample();

            // Manual ack for comparison
            example.manualAckModeExample();

            // Use case: Logging (good for auto-ack)
            example.loggingScenarioExample();

            // Use case: Orders (bad for auto-ack)
            example.orderProcessingScenarioExample();

        } finally {
            example.cleanup();
        }

        System.out.println("\nAuto-ack mode examples completed.");
    }
}
