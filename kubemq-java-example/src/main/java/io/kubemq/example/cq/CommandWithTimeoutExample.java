package io.kubemq.example.cq;

import io.kubemq.sdk.cq.*;
import io.kubemq.sdk.common.ServerInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Command With Timeout Example
 *
 * This example demonstrates timeout handling in KubeMQ Commands (CQ pattern).
 * Commands are synchronous request-response operations where the sender waits
 * for an execution confirmation from the receiver.
 *
 * Timeout Behavior:
 * - Commands have a configurable timeout (in seconds)
 * - If no response is received within the timeout, an error is returned
 * - The timeout applies to the round-trip (send + process + response)
 * - Timeouts should account for network latency and processing time
 *
 * Use Cases:
 * - Execute action and wait for confirmation
 * - Synchronous command execution
 * - Operations that must complete within a deadline
 * - Circuit breaker patterns with timeout fallback
 *
 * Best Practices:
 * - Set realistic timeouts based on expected processing time
 * - Handle timeout exceptions gracefully
 * - Implement retry logic for transient failures
 * - Log timeouts for monitoring and alerting
 *
 * @see io.kubemq.sdk.cq.CommandMessage
 * @see io.kubemq.sdk.cq.CommandResponseMessage
 */
public class CommandWithTimeoutExample {

    private final CQClient cqClient;
    private final String channelName = "command-timeout-channel";
    private final String address = "localhost:50000";
    private final String clientId = "timeout-example-client";

    /**
     * Initializes the CQClient.
     */
    public CommandWithTimeoutExample() {
        cqClient = CQClient.builder()
                .address(address)
                .clientId(clientId)
                .build();

        ServerInfo pingResult = cqClient.ping();
        System.out.println("Connected to: " + pingResult.getHost() + " v" + pingResult.getVersion());

        cqClient.createCommandsChannel(channelName);
    }

    /**
     * Demonstrates basic command with timeout.
     */
    public void basicTimeoutExample() {
        System.out.println("\n=== Basic Command Timeout ===\n");

        CountDownLatch latch = new CountDownLatch(1);

        // Create a fast-responding subscriber
        Consumer<CommandMessageReceived> handler = cmd -> {
            System.out.println("  Subscriber received command: " + new String(cmd.getBody()));

            // Respond immediately
            CommandResponseMessage response = CommandResponseMessage.builder()
                    .commandReceived(cmd)
                    .isExecuted(true)
                    .build();

            cqClient.sendResponseMessage(response);
            System.out.println("  Subscriber sent response");
            latch.countDown();
        };

        CommandsSubscription subscription = CommandsSubscription.builder()
                .channel(channelName)
                .onReceiveCommandCallback(handler)
                .onErrorCallback(err -> System.err.println("Subscription error: " + err))
                .build();

        cqClient.subscribeToCommands(subscription);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Send command with 10-second timeout
        System.out.println("Sending command with 10-second timeout...");

        CommandMessage command = CommandMessage.builder()
                .channel(channelName)
                .body("Execute fast action".getBytes())
                .metadata("Quick task")
                .timeoutInSeconds(10)  // 10 second timeout
                .build();

        try {
            CommandResponseMessage response = cqClient.sendCommandRequest(command);
            System.out.println("\nCommand Response:");
            System.out.println("  Executed: " + response.isExecuted());
            System.out.println("  Timestamp: " + response.getTimestamp());
        } catch (Exception e) {
            System.err.println("Command failed: " + e.getMessage());
        }

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        subscription.cancel();
        System.out.println();
    }

    /**
     * Demonstrates timeout when subscriber is slow.
     */
    public void slowSubscriberTimeoutExample() {
        System.out.println("=== Slow Subscriber Timeout ===\n");

        String slowChannel = channelName + "-slow";
        cqClient.createCommandsChannel(slowChannel);

        // Create a slow-responding subscriber
        Consumer<CommandMessageReceived> slowHandler = cmd -> {
            System.out.println("  Slow subscriber received command");
            System.out.println("  Processing for 5 seconds...");

            try {
                Thread.sleep(5000);  // Simulate slow processing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            CommandResponseMessage response = CommandResponseMessage.builder()
                    .commandReceived(cmd)
                    .isExecuted(true)
                    .build();

            cqClient.sendResponseMessage(response);
            System.out.println("  Slow subscriber sent response (may be too late)");
        };

        CommandsSubscription subscription = CommandsSubscription.builder()
                .channel(slowChannel)
                .onReceiveCommandCallback(slowHandler)
                .onErrorCallback(err -> {})
                .build();

        cqClient.subscribeToCommands(subscription);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Send command with short timeout (2 seconds)
        System.out.println("Sending command with 2-second timeout (subscriber takes 5s)...\n");

        CommandMessage command = CommandMessage.builder()
                .channel(slowChannel)
                .body("Execute slow action".getBytes())
                .timeoutInSeconds(2)  // Will timeout before response
                .build();

        try {
            CommandResponseMessage response = cqClient.sendCommandRequest(command);
            System.out.println("Response received: " + response.isExecuted());
        } catch (Exception e) {
            System.out.println("Command timed out (expected): " + e.getMessage());
        }

        System.out.println("\nNote: Timeout occurred because processing time > timeout value.\n");

        // Give subscriber time to finish
        try {
            Thread.sleep(3500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        subscription.cancel();
        cqClient.deleteCommandsChannel(slowChannel);
    }

    /**
     * Demonstrates timeout with no subscribers.
     */
    public void noSubscriberTimeoutExample() {
        System.out.println("=== No Subscriber Timeout ===\n");

        String emptyChannel = channelName + "-empty";
        cqClient.createCommandsChannel(emptyChannel);

        System.out.println("Sending command to channel with no subscribers...");
        System.out.println("(Will timeout waiting for response)\n");

        CommandMessage command = CommandMessage.builder()
                .channel(emptyChannel)
                .body("Waiting for subscriber".getBytes())
                .timeoutInSeconds(3)
                .build();

        long startTime = System.currentTimeMillis();

        try {
            CommandResponseMessage response = cqClient.sendCommandRequest(command);
            System.out.println("Unexpected response: " + response);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("Timeout after " + elapsed + "ms: " + e.getMessage());
        }

        System.out.println("\nThis demonstrates that commands require an active subscriber.\n");

        cqClient.deleteCommandsChannel(emptyChannel);
    }

    /**
     * Demonstrates retry pattern with timeout.
     */
    public void retryWithTimeoutExample() {
        System.out.println("=== Retry Pattern with Timeout ===\n");

        String retryChannel = channelName + "-retry";
        cqClient.createCommandsChannel(retryChannel);

        AtomicInteger attempts = new AtomicInteger(0);

        // Subscriber that fails on first attempt, succeeds on retry
        Consumer<CommandMessageReceived> retryHandler = cmd -> {
            int attempt = attempts.incrementAndGet();
            String body = new String(cmd.getBody());
            System.out.println("  Attempt " + attempt + ": Received '" + body + "'");

            boolean success = attempt >= 2;  // Succeed on 2nd attempt

            CommandResponseMessage response = CommandResponseMessage.builder()
                    .commandReceived(cmd)
                    .isExecuted(success)
                    .error(success ? "" : "Temporary failure")
                    .build();

            cqClient.sendResponseMessage(response);
            System.out.println("  Attempt " + attempt + ": " + (success ? "SUCCESS" : "FAILED"));
        };

        CommandsSubscription subscription = CommandsSubscription.builder()
                .channel(retryChannel)
                .onReceiveCommandCallback(retryHandler)
                .onErrorCallback(err -> {})
                .build();

        cqClient.subscribeToCommands(subscription);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Retry logic
        int maxRetries = 3;
        int retryDelay = 1; // seconds

        System.out.println("Sending command with retry logic (max " + maxRetries + " attempts)...\n");

        for (int i = 1; i <= maxRetries; i++) {
            System.out.println("Attempt " + i + ":");

            CommandMessage command = CommandMessage.builder()
                    .channel(retryChannel)
                    .body(("Retry attempt " + i).getBytes())
                    .timeoutInSeconds(5)
                    .build();

            try {
                CommandResponseMessage response = cqClient.sendCommandRequest(command);

                if (response.isExecuted()) {
                    System.out.println("\nCommand succeeded on attempt " + i + "!\n");
                    break;
                } else {
                    System.out.println("  Command not executed: " + response.getError());
                    if (i < maxRetries) {
                        System.out.println("  Retrying in " + retryDelay + " second(s)...\n");
                        Thread.sleep(retryDelay * 1000);
                    }
                }
            } catch (Exception e) {
                System.out.println("  Request failed: " + e.getMessage());
                if (i < maxRetries) {
                    try {
                        System.out.println("  Retrying in " + retryDelay + " second(s)...\n");
                        Thread.sleep(retryDelay * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        subscription.cancel();
        cqClient.deleteCommandsChannel(retryChannel);
    }

    /**
     * Demonstrates different timeout values.
     */
    public void variableTimeoutsExample() {
        System.out.println("=== Variable Timeout Values ===\n");

        String varChannel = channelName + "-variable";
        cqClient.createCommandsChannel(varChannel);

        // Subscriber with variable response times
        Consumer<CommandMessageReceived> varHandler = cmd -> {
            Map<String, String> tags = cmd.getTags();
            int delayMs = Integer.parseInt(tags.getOrDefault("delay", "100"));

            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            CommandResponseMessage response = CommandResponseMessage.builder()
                    .commandReceived(cmd)
                    .isExecuted(true)
                    .build();

            cqClient.sendResponseMessage(response);
        };

        CommandsSubscription subscription = CommandsSubscription.builder()
                .channel(varChannel)
                .onReceiveCommandCallback(varHandler)
                .onErrorCallback(err -> {})
                .build();

        cqClient.subscribeToCommands(subscription);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Test different timeout scenarios
        int[][] scenarios = {
            {1, 500},    // 1s timeout, 500ms processing (success)
            {1, 1500},   // 1s timeout, 1500ms processing (fail)
            {3, 2000},   // 3s timeout, 2000ms processing (success)
        };

        System.out.println("Testing different timeout scenarios:\n");

        for (int[] scenario : scenarios) {
            int timeoutSec = scenario[0];
            int processingMs = scenario[1];

            Map<String, String> tags = new HashMap<>();
            tags.put("delay", String.valueOf(processingMs));

            CommandMessage command = CommandMessage.builder()
                    .channel(varChannel)
                    .body("Variable timeout test".getBytes())
                    .tags(tags)
                    .timeoutInSeconds(timeoutSec)
                    .build();

            System.out.println("  Timeout: " + timeoutSec + "s, Processing: " + processingMs + "ms");

            try {
                CommandResponseMessage response = cqClient.sendCommandRequest(command);
                System.out.println("  Result: SUCCESS");
            } catch (Exception e) {
                System.out.println("  Result: TIMEOUT");
            }
            System.out.println();
        }

        subscription.cancel();
        cqClient.deleteCommandsChannel(varChannel);
    }

    /**
     * Shows best practices summary.
     */
    public void bestPracticesSummary() {
        System.out.println("=== Timeout Best Practices ===\n");

        System.out.println("1. CALCULATING TIMEOUT VALUES:");
        System.out.println("   timeout = network_latency + processing_time + buffer");
        System.out.println("   Example: 50ms + 500ms + 450ms = 1s timeout\n");

        System.out.println("2. TIMEOUT STRATEGIES:");
        System.out.println("   - Short (1-5s): Interactive UI operations");
        System.out.println("   - Medium (5-30s): Background processing");
        System.out.println("   - Long (30s+): Batch operations, file processing\n");

        System.out.println("3. HANDLING TIMEOUTS:");
        System.out.println("   - Log timeout events for monitoring");
        System.out.println("   - Implement retry with exponential backoff");
        System.out.println("   - Have a fallback response for degraded mode");
        System.out.println("   - Consider circuit breaker pattern\n");

        System.out.println("4. CODE PATTERN:");
        System.out.println("   ```java");
        System.out.println("   try {");
        System.out.println("       CommandResponseMessage response = ");
        System.out.println("           cqClient.sendCommandRequest(command);");
        System.out.println("       if (response.isExecuted()) {");
        System.out.println("           // Success");
        System.out.println("       } else {");
        System.out.println("           // Command rejected: response.getError()");
        System.out.println("       }");
        System.out.println("   } catch (Exception e) {");
        System.out.println("       // Timeout or network error");
        System.out.println("       handleTimeout(e);");
        System.out.println("   }");
        System.out.println("   ```\n");
    }

    /**
     * Cleans up resources.
     */
    public void cleanup() {
        try {
            cqClient.deleteCommandsChannel(channelName);
            System.out.println("Cleaned up channel: " + channelName);
        } catch (Exception e) {
            // Ignore
        }
        cqClient.close();
    }

    /**
     * Main method demonstrating command timeout patterns.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║          Command Timeout Examples                            ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        CommandWithTimeoutExample example = new CommandWithTimeoutExample();

        try {
            // Basic timeout
            example.basicTimeoutExample();

            // Slow subscriber (timeout)
            example.slowSubscriberTimeoutExample();

            // No subscriber (timeout)
            example.noSubscriberTimeoutExample();

            // Retry pattern
            example.retryWithTimeoutExample();

            // Variable timeouts
            example.variableTimeoutsExample();

            // Best practices
            example.bestPracticesSummary();

        } finally {
            example.cleanup();
        }

        System.out.println("Command timeout examples completed.");
    }
}
