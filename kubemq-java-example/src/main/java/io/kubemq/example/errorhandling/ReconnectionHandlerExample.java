package io.kubemq.example.errorhandling;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;
import io.kubemq.sdk.queues.*;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reconnection Handler Example
 *
 * This example demonstrates how to implement reconnection logic for KubeMQ
 * clients. Network issues are inevitable, and proper reconnection handling
 * ensures application resilience.
 *
 * Built-in Reconnection:
 * - The KubeMQ SDK has built-in reconnection for subscriptions
 * - Uses exponential backoff (configurable interval)
 * - Maximum reconnection attempts (default: 10)
 * - Automatic re-subscription after connection restore
 *
 * When Custom Reconnection is Needed:
 * - Client-level connection loss (not just subscription)
 * - Custom retry policies
 * - Multi-client coordination
 * - Application-specific recovery logic
 * - Metrics and monitoring during reconnection
 *
 * Reconnection Strategies:
 * 1. Exponential Backoff: Increase delay between attempts
 * 2. Fixed Interval: Same delay between attempts
 * 3. Circuit Breaker: Stop trying after threshold
 * 4. Jitter: Add randomness to prevent thundering herd
 *
 * @see io.kubemq.sdk.client.KubeMQClient#getReconnectIntervalInMillis()
 */
public class ReconnectionHandlerExample {

    private final String address = "localhost:50000";
    private final String clientId = "reconnection-client";

    /**
     * Demonstrates built-in SDK reconnection.
     */
    public void builtInReconnectionExample() {
        System.out.println("\n=== Built-in SDK Reconnection ===\n");

        System.out.println("The KubeMQ SDK has built-in reconnection for subscriptions:");
        System.out.println("  - Configurable via reconnectIntervalInMillis");
        System.out.println("  - Exponential backoff with cap at 60 seconds");
        System.out.println("  - Maximum 10 reconnection attempts");
        System.out.println("  - Automatic re-subscription on success\n");

        try {
            // Create client with custom reconnection interval
            PubSubClient client = PubSubClient.builder()
                    .address(address)
                    .clientId(clientId)
                    .reconnectIntervalSeconds(1)  // Start with 1 second
                    .build();

            client.ping();
            client.createEventsChannel("reconnect-test");

            System.out.println("Client configured with 1000ms base reconnection interval");

            // Error callback sees reconnection attempts
            EventsSubscription subscription = EventsSubscription.builder()
                    .channel("reconnect-test")
                    .onReceiveEventCallback(event ->
                            System.out.println("  Received: " + new String(event.getBody())))
                    .onErrorCallback(error -> {
                        // This is called during disconnection/reconnection
                        System.out.println("  [ERROR] " + error);
                        System.out.println("  (SDK will attempt reconnection automatically)");
                    })
                    .build();

            client.subscribeToEvents(subscription);
            System.out.println("Subscription active (SDK handles reconnection)\n");

            // Send test message
            client.sendEventsMessage(EventMessage.builder()
                    .channel("reconnect-test")
                    .body("Test message".getBytes())
                    .build());

            Thread.sleep(500);

            subscription.cancel();
            client.deleteEventsChannel("reconnect-test");
            client.close();

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        System.out.println();
    }

    /**
     * Demonstrates custom reconnection with exponential backoff.
     */
    public void exponentialBackoffExample() {
        System.out.println("=== Custom Exponential Backoff ===\n");

        ExponentialBackoff backoff = new ExponentialBackoff(1000, 30000, 2.0);

        System.out.println("Exponential Backoff Configuration:");
        System.out.println("  Initial delay: 1000ms");
        System.out.println("  Max delay: 30000ms");
        System.out.println("  Multiplier: 2.0\n");

        System.out.println("Simulated reconnection delays:");
        for (int attempt = 1; attempt <= 8; attempt++) {
            long delay = backoff.getNextDelay();
            System.out.println("  Attempt " + attempt + ": wait " + delay + "ms");
            backoff.incrementAttempt();
        }

        backoff.reset();
        System.out.println("\n  After reset: " + backoff.getNextDelay() + "ms\n");
    }

    /**
     * Simple exponential backoff implementation.
     */
    private static class ExponentialBackoff {
        private final long initialDelayMs;
        private final long maxDelayMs;
        private final double multiplier;
        private int attempt = 0;

        public ExponentialBackoff(long initialDelayMs, long maxDelayMs, double multiplier) {
            this.initialDelayMs = initialDelayMs;
            this.maxDelayMs = maxDelayMs;
            this.multiplier = multiplier;
        }

        public long getNextDelay() {
            long delay = (long) (initialDelayMs * Math.pow(multiplier, attempt));
            return Math.min(delay, maxDelayMs);
        }

        public void incrementAttempt() {
            attempt++;
        }

        public void reset() {
            attempt = 0;
        }
    }

    /**
     * Demonstrates reconnection wrapper pattern.
     */
    public void reconnectionWrapperExample() {
        System.out.println("=== Reconnection Wrapper Pattern ===\n");

        ReconnectingClient wrapper = new ReconnectingClient(address, clientId);

        try {
            // Initial connection
            System.out.println("1. Establishing initial connection...");
            wrapper.connect();
            System.out.println("   Connected!\n");

            // Use the client
            System.out.println("2. Using client for operations...");
            wrapper.sendMessage("test-channel", "Hello KubeMQ");
            System.out.println("   Message sent\n");

            // Simulate disconnect detection
            System.out.println("3. Simulating health check failure...");
            wrapper.simulateDisconnect();
            System.out.println("   Disconnected!\n");

            // Automatic reconnection on next operation
            System.out.println("4. Next operation triggers reconnection...");
            wrapper.sendMessage("test-channel", "After reconnect");
            System.out.println("   Message sent (after reconnection)\n");

            wrapper.close();

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /**
     * Simple reconnecting client wrapper.
     */
    private class ReconnectingClient {
        private final String address;
        private final String clientId;
        private PubSubClient client;
        private boolean connected = false;
        private final ExponentialBackoff backoff = new ExponentialBackoff(1000, 30000, 2.0);

        public ReconnectingClient(String address, String clientId) {
            this.address = address;
            this.clientId = clientId;
        }

        public void connect() throws Exception {
            if (connected) return;

            int maxAttempts = 5;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    client = PubSubClient.builder()
                            .address(address)
                            .clientId(clientId)
                            .build();

                    client.ping();
                    connected = true;
                    backoff.reset();
                    return;

                } catch (Exception e) {
                    if (attempt == maxAttempts) {
                        throw new RuntimeException("Failed to connect after " + maxAttempts + " attempts", e);
                    }

                    long delay = backoff.getNextDelay();
                    System.out.println("   Connection attempt " + attempt + " failed, retrying in " + delay + "ms");
                    Thread.sleep(delay);
                    backoff.incrementAttempt();
                }
            }
        }

        public void sendMessage(String channel, String body) throws Exception {
            ensureConnected();
            try {
                client.createEventsChannel(channel);
            } catch (Exception e) {
                // Channel might already exist
            }
            client.sendEventsMessage(EventMessage.builder()
                    .channel(channel)
                    .body(body.getBytes())
                    .build());
        }

        private void ensureConnected() throws Exception {
            if (!connected || !isHealthy()) {
                connected = false;
                connect();
            }
        }

        private boolean isHealthy() {
            try {
                return client != null && client.ping() != null;
            } catch (Exception e) {
                return false;
            }
        }

        public void simulateDisconnect() {
            connected = false;
        }

        public void close() {
            if (client != null) {
                try {
                    client.deleteEventsChannel("test-channel");
                } catch (Exception e) {
                    // Ignore
                }
                client.close();
            }
            connected = false;
        }
    }

    /**
     * Demonstrates periodic health check with reconnection.
     */
    public void periodicHealthCheckExample() {
        System.out.println("=== Periodic Health Check Pattern ===\n");

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger healthCheckCount = new AtomicInteger(0);
        AtomicBoolean isHealthy = new AtomicBoolean(true);

        try {
            PubSubClient client = PubSubClient.builder()
                    .address(address)
                    .clientId(clientId)
                    .build();

            client.ping();
            System.out.println("Client connected. Starting health checks...\n");

            // Health check scheduler
            ScheduledExecutorService healthChecker = Executors.newSingleThreadScheduledExecutor();

            healthChecker.scheduleAtFixedRate(() -> {
                int count = healthCheckCount.incrementAndGet();
                try {
                    ServerInfo info = client.ping();
                    if (info != null) {
                        if (!isHealthy.get()) {
                            System.out.println("  [Check " + count + "] Connection restored!");
                            isHealthy.set(true);
                        } else {
                            System.out.println("  [Check " + count + "] Healthy");
                        }
                    } else {
                        isHealthy.set(false);
                        System.out.println("  [Check " + count + "] Unhealthy - null response");
                    }
                } catch (Exception e) {
                    isHealthy.set(false);
                    System.out.println("  [Check " + count + "] Unhealthy - " + e.getMessage());
                }
            }, 0, 1, TimeUnit.SECONDS);

            // Run for a few seconds
            Thread.sleep(3000);

            // Shutdown health checker
            running.set(false);
            healthChecker.shutdown();
            healthChecker.awaitTermination(2, TimeUnit.SECONDS);

            System.out.println("\nHealth check summary:");
            System.out.println("  Total checks: " + healthCheckCount.get());
            System.out.println("  Final status: " + (isHealthy.get() ? "HEALTHY" : "UNHEALTHY"));

            client.close();

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        System.out.println();
    }

    /**
     * Demonstrates reconnection with jitter.
     */
    public void jitterExample() {
        System.out.println("=== Reconnection with Jitter ===\n");

        System.out.println("Jitter adds randomness to prevent 'thundering herd' when");
        System.out.println("many clients reconnect simultaneously after an outage.\n");

        System.out.println("Full jitter formula: random(0, min(cap, base * 2^attempt))\n");

        long baseDelay = 1000;
        long cap = 30000;

        System.out.println("Simulated delays with jitter (multiple runs differ):");
        for (int attempt = 0; attempt < 5; attempt++) {
            long calculatedDelay = Math.min(cap, baseDelay * (1L << attempt));
            long jitteredDelay = (long) (Math.random() * calculatedDelay);

            System.out.println("  Attempt " + (attempt + 1) +
                    ": base=" + calculatedDelay + "ms, jittered=" + jitteredDelay + "ms");
        }

        System.out.println();
    }

    /**
     * Demonstrates queue client reconnection for polling.
     */
    public void queuePollingReconnectionExample() {
        System.out.println("=== Queue Polling Reconnection ===\n");

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger pollCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        try {
            QueuesClient client = QueuesClient.builder()
                    .address(address)
                    .clientId(clientId)
                    .reconnectIntervalSeconds(1)
                    .build();

            client.ping();
            client.createQueuesChannel("poll-reconnect-test");

            System.out.println("Starting polling loop with reconnection handling...\n");

            // Polling loop with reconnection
            Thread pollingThread = new Thread(() -> {
                while (running.get()) {
                    int count = pollCount.incrementAndGet();
                    try {
                        QueuesPollResponse response = client.receiveQueuesMessages(
                                QueuesPollRequest.builder()
                                        .channel("poll-reconnect-test")
                                        .pollMaxMessages(10)
                                        .pollWaitTimeoutInSeconds(1)
                                        .build()
                        );

                        if (response.isError()) {
                            errorCount.incrementAndGet();
                            System.out.println("  [Poll " + count + "] Error: " + response.getError());
                        } else {
                            int msgCount = response.getMessages() != null ?
                                    response.getMessages().size() : 0;
                            System.out.println("  [Poll " + count + "] Received " + msgCount + " messages");
                        }

                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        System.out.println("  [Poll " + count + "] Exception: " + e.getMessage());
                        System.out.println("    (Will retry on next poll)");

                        // Brief pause before retry
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            });

            pollingThread.start();

            // Run for a few seconds
            Thread.sleep(3000);

            // Stop polling
            running.set(false);
            pollingThread.interrupt();
            pollingThread.join(2000);

            System.out.println("\nPolling summary:");
            System.out.println("  Total polls: " + pollCount.get());
            System.out.println("  Errors: " + errorCount.get());

            client.deleteQueuesChannel("poll-reconnect-test");
            client.close();

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        System.out.println();
    }

    /**
     * Summary of reconnection best practices.
     */
    public void bestPracticesSummary() {
        System.out.println("=== Reconnection Best Practices ===\n");

        System.out.println("1. EXPONENTIAL BACKOFF:");
        System.out.println("   - Start with small delay (1-5 seconds)");
        System.out.println("   - Multiply by 2 each attempt");
        System.out.println("   - Cap at reasonable maximum (30-60 seconds)");
        System.out.println("   - Add jitter to prevent thundering herd\n");

        System.out.println("2. RETRY LIMITS:");
        System.out.println("   - Set maximum retry attempts");
        System.out.println("   - Eventually give up and alert");
        System.out.println("   - Consider circuit breaker pattern\n");

        System.out.println("3. STATE MANAGEMENT:");
        System.out.println("   - Track connection state (connected/disconnected)");
        System.out.println("   - Re-establish subscriptions after reconnect");
        System.out.println("   - Resume from last known position (for stores)\n");

        System.out.println("4. MONITORING:");
        System.out.println("   - Log reconnection attempts");
        System.out.println("   - Track reconnection metrics");
        System.out.println("   - Alert on repeated failures");
        System.out.println("   - Include timing information\n");

        System.out.println("5. KUBERNETES/CLOUD:");
        System.out.println("   - Use readiness probe based on connection");
        System.out.println("   - Consider pod restart on persistent failure");
        System.out.println("   - Handle DNS changes gracefully");
        System.out.println("   - Use service discovery if available\n");
    }

    /**
     * Main method demonstrating reconnection handling.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║          Reconnection Handler Examples                       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        ReconnectionHandlerExample example = new ReconnectionHandlerExample();

        // Built-in reconnection
        example.builtInReconnectionExample();

        // Exponential backoff
        example.exponentialBackoffExample();

        // Reconnection wrapper
        example.reconnectionWrapperExample();

        // Periodic health check
        example.periodicHealthCheckExample();

        // Jitter
        example.jitterExample();

        // Queue polling reconnection
        example.queuePollingReconnectionExample();

        // Best practices
        example.bestPracticesSummary();

        System.out.println("Reconnection handler examples completed.");
    }
}
