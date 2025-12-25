package io.kubemq.example.errorhandling;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;
import io.kubemq.sdk.queues.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Connection Error Handling Example
 *
 * This example demonstrates how to properly handle connection errors
 * when working with KubeMQ. Connection issues are inevitable in
 * distributed systems, and proper error handling is essential.
 *
 * Common Connection Errors:
 * - Server unavailable (wrong address, server down)
 * - Network timeout (firewall, latency)
 * - Authentication failure (invalid token)
 * - TLS/SSL errors (certificate issues)
 * - Connection refused (port blocked)
 *
 * Error Handling Strategies:
 * 1. Try-Catch: Wrap operations in exception handlers
 * 2. Ping Check: Test connection before operations
 * 3. Callback Errors: Handle async subscription errors
 * 4. Graceful Degradation: Fall back when KubeMQ unavailable
 *
 * Best Practices:
 * - Always wrap client creation in try-catch
 * - Use ping() to verify connection before operations
 * - Implement error callbacks for subscriptions
 * - Log errors for debugging and monitoring
 * - Have a circuit breaker for repeated failures
 *
 * @see io.kubemq.sdk.exception.GRPCException
 */
public class ConnectionErrorHandlingExample {

    private final String address = "localhost:50000";
    private final String clientId = "error-handling-client";

    /**
     * Demonstrates handling connection failure on client creation.
     */
    public void clientCreationErrorExample() {
        System.out.println("\n=== Client Creation Error Handling ===\n");

        // Scenario 1: Wrong address
        System.out.println("1. Attempting connection to wrong address...");

        try {
            PubSubClient badClient = PubSubClient.builder()
                    .address("localhost:99999")  // Wrong port
                    .clientId(clientId)
                    .build();

            // Try to ping - this will fail
            ServerInfo info = badClient.ping();
            System.out.println("Connected: " + info.getHost());
            badClient.close();
        } catch (Exception e) {
            System.out.println("   Connection failed (expected): " + e.getClass().getSimpleName());
            System.out.println("   Message: " + truncate(e.getMessage(), 60));
        }

        // Scenario 2: Valid address
        System.out.println("\n2. Attempting connection to valid address...");

        try {
            PubSubClient goodClient = PubSubClient.builder()
                    .address(address)
                    .clientId(clientId)
                    .build();

            ServerInfo info = goodClient.ping();
            System.out.println("   Connected successfully!");
            System.out.println("   Server: " + info.getHost() + " v" + info.getVersion());
            goodClient.close();
        } catch (Exception e) {
            System.out.println("   Connection failed: " + e.getMessage());
        }

        System.out.println();
    }

    private String truncate(String message, int maxLength) {
        if (message == null) return "null";
        return message.length() > maxLength ?
                message.substring(0, maxLength) + "..." : message;
    }

    /**
     * Demonstrates ping-based connection verification.
     */
    public void pingVerificationExample() {
        System.out.println("=== Ping-Based Connection Verification ===\n");

        System.out.println("Pattern: Verify connection with ping() before operations\n");

        try {
            PubSubClient client = PubSubClient.builder()
                    .address(address)
                    .clientId(clientId)
                    .build();

            // Always ping first to verify connection
            System.out.println("Step 1: Pinging server...");
            ServerInfo info = client.ping();

            if (info != null) {
                System.out.println("   Ping successful!");
                System.out.println("   Host: " + info.getHost());
                System.out.println("   Version: " + info.getVersion());

                System.out.println("\nStep 2: Safe to proceed with operations");
                // Now safe to do operations
                client.createEventsChannel("test-channel");
                System.out.println("   Created channel successfully");

                client.deleteEventsChannel("test-channel");
                System.out.println("   Deleted channel successfully");
            }

            client.close();
        } catch (Exception e) {
            System.out.println("   Error: " + e.getMessage());
        }

        System.out.println();
    }

    /**
     * Demonstrates subscription error callback handling.
     */
    public void subscriptionErrorCallbackExample() {
        System.out.println("=== Subscription Error Callback ===\n");

        AtomicBoolean errorOccurred = new AtomicBoolean(false);
        AtomicInteger errorCount = new AtomicInteger(0);

        try {
            PubSubClient client = PubSubClient.builder()
                    .address(address)
                    .clientId(clientId)
                    .build();

            client.ping();
            client.createEventsChannel("error-test-channel");

            System.out.println("Creating subscription with error callback...\n");

            EventsSubscription subscription = EventsSubscription.builder()
                    .channel("error-test-channel")
                    .onReceiveEventCallback(event -> {
                        System.out.println("Received: " + new String(event.getBody()));
                    })
                    .onErrorCallback(errorMessage -> {
                        // This is called when subscription encounters an error
                        errorOccurred.set(true);
                        int count = errorCount.incrementAndGet();
                        System.out.println("[ERROR CALLBACK #" + count + "] " + errorMessage);

                        // Implement your error handling logic here:
                        // - Log the error
                        // - Notify monitoring system
                        // - Attempt reconnection
                        // - Trigger circuit breaker
                    })
                    .build();

            client.subscribeToEvents(subscription);
            System.out.println("Subscription established with error handling.\n");

            // Wait briefly
            Thread.sleep(500);

            // Send a test message
            client.sendEventsMessage(EventMessage.builder()
                    .channel("error-test-channel")
                    .body("Test message".getBytes())
                    .build());

            Thread.sleep(500);

            System.out.println("Error callback triggered: " + errorOccurred.get());
            System.out.println("Total errors: " + errorCount.get());

            subscription.cancel();
            client.deleteEventsChannel("error-test-channel");
            client.close();

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        System.out.println();
    }

    /**
     * Demonstrates queue operation error handling.
     */
    public void queueOperationErrorExample() {
        System.out.println("=== Queue Operation Error Handling ===\n");

        try {
            QueuesClient client = QueuesClient.builder()
                    .address(address)
                    .clientId(clientId)
                    .build();

            client.ping();

            // Scenario 1: Send to valid channel
            System.out.println("1. Sending to valid queue...");

            String testQueue = "error-handling-queue";
            client.createQueuesChannel(testQueue);

            try {
                QueueSendResult result = client.sendQueuesMessage(
                        QueueMessage.builder()
                                .channel(testQueue)
                                .body("Test message".getBytes())
                                .build()
                );

                if (result.isError()) {
                    System.out.println("   Send error: " + result.getError());
                } else {
                    System.out.println("   Sent successfully!");
                }
            } catch (Exception e) {
                System.out.println("   Exception: " + e.getMessage());
            }

            // Scenario 2: Receive with error handling
            System.out.println("\n2. Receiving from queue...");

            try {
                QueuesPollResponse response = client.receiveQueuesMessages(
                        QueuesPollRequest.builder()
                                .channel(testQueue)
                                .pollMaxMessages(10)
                                .pollWaitTimeoutInSeconds(2)
                                .build()
                );

                if (response.isError()) {
                    System.out.println("   Receive error: " + response.getError());
                } else {
                    System.out.println("   Received " + response.getMessages().size() + " messages");
                    for (QueueMessageReceived msg : response.getMessages()) {
                        msg.ack();
                    }
                }
            } catch (Exception e) {
                System.out.println("   Exception: " + e.getMessage());
            }

            client.deleteQueuesChannel(testQueue);
            client.close();

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        System.out.println();
    }

    /**
     * Demonstrates connection health check pattern.
     */
    public void healthCheckPatternExample() {
        System.out.println("=== Health Check Pattern ===\n");

        System.out.println("Recommended health check implementation:\n");

        try {
            PubSubClient client = PubSubClient.builder()
                    .address(address)
                    .clientId(clientId)
                    .build();

            // Health check method
            boolean healthy = performHealthCheck(client);
            System.out.println("Health check result: " + (healthy ? "HEALTHY" : "UNHEALTHY"));

            client.close();

        } catch (Exception e) {
            System.out.println("Health check failed: " + e.getMessage());
        }

        System.out.println("\nCode pattern:");
        System.out.println("```java");
        System.out.println("public boolean isHealthy(PubSubClient client) {");
        System.out.println("    try {");
        System.out.println("        ServerInfo info = client.ping();");
        System.out.println("        return info != null;");
        System.out.println("    } catch (Exception e) {");
        System.out.println("        logger.warn(\"Health check failed: {}\", e.getMessage());");
        System.out.println("        return false;");
        System.out.println("    }");
        System.out.println("}");
        System.out.println("```\n");
    }

    private boolean performHealthCheck(PubSubClient client) {
        try {
            ServerInfo info = client.ping();
            return info != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Demonstrates circuit breaker concept.
     */
    public void circuitBreakerConceptExample() {
        System.out.println("=== Circuit Breaker Concept ===\n");

        System.out.println("Circuit breaker pattern for KubeMQ connections:\n");

        System.out.println("States:");
        System.out.println("  CLOSED  -> Normal operation, requests pass through");
        System.out.println("  OPEN    -> Too many failures, fail fast without trying");
        System.out.println("  HALF    -> Testing if service recovered\n");

        System.out.println("Transitions:");
        System.out.println("  CLOSED -> OPEN:  After N consecutive failures");
        System.out.println("  OPEN -> HALF:    After timeout period");
        System.out.println("  HALF -> CLOSED:  On successful request");
        System.out.println("  HALF -> OPEN:    On failed request\n");

        // Simple circuit breaker demonstration
        SimpleCircuitBreaker breaker = new SimpleCircuitBreaker(3, 5000);

        System.out.println("Simulating circuit breaker behavior:\n");

        for (int i = 1; i <= 6; i++) {
            boolean allowed = breaker.allowRequest();
            System.out.println("  Request " + i + ": " +
                    (allowed ? "ALLOWED" : "BLOCKED") +
                    " (State: " + breaker.getState() + ")");

            if (allowed) {
                // Simulate failure
                boolean success = (i == 6); // Last request succeeds
                breaker.recordResult(success);
                System.out.println("    Result: " + (success ? "SUCCESS" : "FAILURE"));
            }
        }

        System.out.println();
    }

    /**
     * Simple circuit breaker implementation for demonstration.
     */
    private static class SimpleCircuitBreaker {
        private enum State { CLOSED, OPEN, HALF_OPEN }

        private State state = State.CLOSED;
        private int failureCount = 0;
        private final int threshold;
        private final long resetTimeoutMs;
        private long lastFailureTime;

        public SimpleCircuitBreaker(int threshold, long resetTimeoutMs) {
            this.threshold = threshold;
            this.resetTimeoutMs = resetTimeoutMs;
        }

        public boolean allowRequest() {
            switch (state) {
                case CLOSED:
                    return true;
                case OPEN:
                    if (System.currentTimeMillis() - lastFailureTime > resetTimeoutMs) {
                        state = State.HALF_OPEN;
                        return true;
                    }
                    return false;
                case HALF_OPEN:
                    return true;
                default:
                    return false;
            }
        }

        public void recordResult(boolean success) {
            if (success) {
                failureCount = 0;
                state = State.CLOSED;
            } else {
                failureCount++;
                lastFailureTime = System.currentTimeMillis();
                if (failureCount >= threshold) {
                    state = State.OPEN;
                }
            }
        }

        public String getState() {
            return state.name();
        }
    }

    /**
     * Summary of error handling best practices.
     */
    public void bestPracticesSummary() {
        System.out.println("=== Error Handling Best Practices ===\n");

        System.out.println("1. CLIENT CREATION:");
        System.out.println("   - Always wrap in try-catch");
        System.out.println("   - Validate configuration before connecting");
        System.out.println("   - Use ping() to verify connection\n");

        System.out.println("2. SUBSCRIPTIONS:");
        System.out.println("   - Always provide onErrorCallback");
        System.out.println("   - Log errors with context");
        System.out.println("   - Consider auto-reconnection logic\n");

        System.out.println("3. SEND OPERATIONS:");
        System.out.println("   - Check result.getIsError() for queues");
        System.out.println("   - Implement retry for transient failures");
        System.out.println("   - Have dead letter handling\n");

        System.out.println("4. RECEIVE OPERATIONS:");
        System.out.println("   - Check response.isError()");
        System.out.println("   - Handle empty responses gracefully");
        System.out.println("   - Ack/Nack messages properly\n");

        System.out.println("5. GENERAL:");
        System.out.println("   - Implement health checks");
        System.out.println("   - Use circuit breaker for stability");
        System.out.println("   - Monitor error rates");
        System.out.println("   - Have fallback strategies\n");
    }

    /**
     * Main method demonstrating connection error handling.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║        Connection Error Handling Examples                    ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        ConnectionErrorHandlingExample example = new ConnectionErrorHandlingExample();

        // Client creation errors
        example.clientCreationErrorExample();

        // Ping verification
        example.pingVerificationExample();

        // Subscription error callbacks
        example.subscriptionErrorCallbackExample();

        // Queue operation errors
        example.queueOperationErrorExample();

        // Health check pattern
        example.healthCheckPatternExample();

        // Circuit breaker concept
        example.circuitBreakerConceptExample();

        // Best practices
        example.bestPracticesSummary();

        System.out.println("Connection error handling examples completed.");
    }
}
