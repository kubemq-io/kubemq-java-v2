package io.kubemq.example.errorhandling;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;
import io.kubemq.sdk.queues.*;
import io.kubemq.sdk.cq.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Graceful Shutdown Example
 *
 * This example demonstrates proper shutdown procedures for KubeMQ clients.
 * Graceful shutdown ensures all in-flight operations complete and resources
 * are properly released before application exit.
 *
 * Why Graceful Shutdown Matters:
 * - Prevents message loss during processing
 * - Releases server-side resources properly
 * - Avoids orphaned connections
 * - Ensures data consistency
 * - Prevents resource leaks
 *
 * Shutdown Steps:
 * 1. Stop accepting new work
 * 2. Wait for in-flight operations to complete
 * 3. Cancel all subscriptions
 * 4. Close client connections
 * 5. Release local resources
 *
 * Components to Shutdown:
 * - PubSubClient (subscriptions + connection)
 * - QueuesClient (polling + connection)
 * - CQClient (subscriptions + connection)
 * - Background threads
 * - Message processors
 *
 * @see io.kubemq.sdk.pubsub.EventsSubscription#cancel()
 * @see io.kubemq.sdk.client.KubeMQClient#close()
 */
public class GracefulShutdownExample {

    private final String address = "localhost:50000";
    private final String clientId = "graceful-shutdown-client";

    /**
     * Demonstrates basic shutdown procedure.
     */
    public void basicShutdownExample() {
        System.out.println("\n=== Basic Graceful Shutdown ===\n");

        PubSubClient client = null;
        EventsSubscription subscription = null;

        try {
            // Initialize
            client = PubSubClient.builder()
                    .address(address)
                    .clientId(clientId)
                    .build();

            client.ping();
            client.createEventsChannel("shutdown-test");

            System.out.println("1. Client connected");

            // Create subscription
            subscription = EventsSubscription.builder()
                    .channel("shutdown-test")
                    .onReceiveEventCallback(event ->
                            System.out.println("   Received: " + new String(event.getBody())))
                    .onErrorCallback(err -> {})
                    .build();

            client.subscribeToEvents(subscription);
            System.out.println("2. Subscription active");

            // Send some messages
            for (int i = 1; i <= 3; i++) {
                client.sendEventsMessage(EventMessage.builder()
                        .channel("shutdown-test")
                        .body(("Message " + i).getBytes())
                        .build());
                Thread.sleep(100);
            }

            System.out.println("3. Messages sent");

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        } finally {
            // GRACEFUL SHUTDOWN
            System.out.println("\n--- Graceful Shutdown ---");

            // Step 1: Cancel subscriptions
            if (subscription != null) {
                subscription.cancel();
                System.out.println("4. Subscription cancelled");
            }

            // Step 2: Give time for pending operations
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Step 3: Cleanup channels (optional)
            if (client != null) {
                try {
                    client.deleteEventsChannel("shutdown-test");
                    System.out.println("5. Channel deleted");
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }

            // Step 4: Close client
            if (client != null) {
                client.close();
                System.out.println("6. Client closed");
            }

            System.out.println("\nShutdown complete.\n");
        }
    }

    /**
     * Demonstrates shutdown hook registration.
     */
    public void shutdownHookExample() {
        System.out.println("=== Shutdown Hook Pattern ===\n");

        System.out.println("Registering JVM shutdown hook for graceful shutdown...\n");

        // Create resources that need cleanup
        final PubSubClient client = PubSubClient.builder()
                .address(address)
                .clientId(clientId + "-hook")
                .build();

        client.ping();
        client.createEventsChannel("hook-test");

        final List<EventsSubscription> subscriptions = new ArrayList<>();

        EventsSubscription sub = EventsSubscription.builder()
                .channel("hook-test")
                .onReceiveEventCallback(event -> {})
                .onErrorCallback(err -> {})
                .build();

        client.subscribeToEvents(sub);
        subscriptions.add(sub);

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Shutdown Hook] Starting graceful shutdown...");

            // Cancel all subscriptions
            for (EventsSubscription subscription : subscriptions) {
                try {
                    subscription.cancel();
                    System.out.println("[Shutdown Hook] Subscription cancelled");
                } catch (Exception e) {
                    // Log but continue
                }
            }

            // Close client
            try {
                client.close();
                System.out.println("[Shutdown Hook] Client closed");
            } catch (Exception e) {
                // Log but continue
            }

            System.out.println("[Shutdown Hook] Shutdown complete");
        }, "kubemq-shutdown-hook"));

        System.out.println("Shutdown hook registered.");
        System.out.println("In production, JVM shutdown triggers the hook.\n");

        // For this example, manually cleanup
        sub.cancel();
        try {
            client.deleteEventsChannel("hook-test");
        } catch (Exception e) {
            // Ignore
        }
        client.close();

        System.out.println("Code pattern:");
        System.out.println("```java");
        System.out.println("Runtime.getRuntime().addShutdownHook(new Thread(() -> {");
        System.out.println("    // Cancel all subscriptions");
        System.out.println("    subscriptions.forEach(EventsSubscription::cancel);");
        System.out.println("    // Close all clients");
        System.out.println("    clients.forEach(KubeMQClient::close);");
        System.out.println("}, \"kubemq-shutdown\"));");
        System.out.println("```\n");
    }

    /**
     * Demonstrates waiting for in-flight messages.
     */
    public void waitForInFlightExample() {
        System.out.println("=== Wait for In-Flight Messages ===\n");

        AtomicInteger inFlightCount = new AtomicInteger(0);
        AtomicBoolean shuttingDown = new AtomicBoolean(false);
        CountDownLatch allComplete = new CountDownLatch(1);

        try {
            PubSubClient client = PubSubClient.builder()
                    .address(address)
                    .clientId(clientId)
                    .build();

            client.ping();
            client.createEventsChannel("inflight-test");

            // Subscription that tracks in-flight processing
            EventsSubscription subscription = EventsSubscription.builder()
                    .channel("inflight-test")
                    .onReceiveEventCallback(event -> {
                        int count = inFlightCount.incrementAndGet();
                        String msg = new String(event.getBody());
                        System.out.println("  Processing: " + msg + " (in-flight: " + count + ")");

                        // Simulate processing time
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }

                        int remaining = inFlightCount.decrementAndGet();
                        System.out.println("  Completed: " + msg + " (in-flight: " + remaining + ")");

                        if (shuttingDown.get() && remaining == 0) {
                            allComplete.countDown();
                        }
                    })
                    .onErrorCallback(err -> {})
                    .build();

            client.subscribeToEvents(subscription);
            System.out.println("Subscription active, sending messages...\n");

            // Send messages
            for (int i = 1; i <= 5; i++) {
                client.sendEventsMessage(EventMessage.builder()
                        .channel("inflight-test")
                        .body(("Message " + i).getBytes())
                        .build());
                Thread.sleep(100);
            }

            // Start shutdown after messages are sent
            System.out.println("\nInitiating shutdown...");
            shuttingDown.set(true);

            // Cancel subscription to stop new messages
            subscription.cancel();
            System.out.println("Subscription cancelled (no new messages)");

            // Wait for in-flight messages (with timeout)
            System.out.println("Waiting for in-flight messages to complete...");
            boolean completed = allComplete.await(5, TimeUnit.SECONDS);

            if (completed) {
                System.out.println("All in-flight messages completed!");
            } else {
                System.out.println("Timeout waiting for in-flight messages");
            }

            client.deleteEventsChannel("inflight-test");
            client.close();

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        System.out.println();
    }

    /**
     * Demonstrates multi-client shutdown.
     */
    public void multiClientShutdownExample() {
        System.out.println("=== Multi-Client Shutdown ===\n");

        List<AutoCloseable> clients = new ArrayList<>();
        List<Object> subscriptions = new ArrayList<>();

        try {
            // Create multiple clients
            PubSubClient pubSubClient = PubSubClient.builder()
                    .address(address)
                    .clientId(clientId + "-pubsub")
                    .build();
            clients.add(pubSubClient);

            QueuesClient queuesClient = QueuesClient.builder()
                    .address(address)
                    .clientId(clientId + "-queues")
                    .build();
            clients.add(queuesClient);

            CQClient cqClient = CQClient.builder()
                    .address(address)
                    .clientId(clientId + "-cq")
                    .build();
            clients.add(cqClient);

            System.out.println("Created " + clients.size() + " clients");

            // Create subscriptions
            pubSubClient.createEventsChannel("multi-test");
            EventsSubscription eventsSub = EventsSubscription.builder()
                    .channel("multi-test")
                    .onReceiveEventCallback(e -> {})
                    .onErrorCallback(err -> {})
                    .build();
            pubSubClient.subscribeToEvents(eventsSub);
            subscriptions.add(eventsSub);

            cqClient.createCommandsChannel("multi-cmd");
            CommandsSubscription cmdSub = CommandsSubscription.builder()
                    .channel("multi-cmd")
                    .onReceiveCommandCallback(c -> {})
                    .onErrorCallback(err -> {})
                    .build();
            cqClient.subscribeToCommands(cmdSub);
            subscriptions.add(cmdSub);

            System.out.println("Created " + subscriptions.size() + " subscriptions\n");

            // SHUTDOWN SEQUENCE
            System.out.println("--- Initiating Multi-Client Shutdown ---\n");

            // Step 1: Cancel all subscriptions
            System.out.println("Step 1: Cancelling subscriptions...");
            for (Object sub : subscriptions) {
                if (sub instanceof EventsSubscription) {
                    ((EventsSubscription) sub).cancel();
                } else if (sub instanceof CommandsSubscription) {
                    ((CommandsSubscription) sub).cancel();
                }
            }
            System.out.println("   " + subscriptions.size() + " subscriptions cancelled");

            // Step 2: Wait for pending operations
            System.out.println("Step 2: Waiting for pending operations...");
            Thread.sleep(500);
            System.out.println("   Wait complete");

            // Step 3: Clean up channels
            System.out.println("Step 3: Cleaning up channels...");
            try {
                pubSubClient.deleteEventsChannel("multi-test");
                cqClient.deleteCommandsChannel("multi-cmd");
            } catch (Exception e) {
                // Ignore cleanup errors
            }
            System.out.println("   Channels cleaned");

            // Step 4: Close all clients (in reverse order of creation)
            System.out.println("Step 4: Closing clients...");
            for (int i = clients.size() - 1; i >= 0; i--) {
                try {
                    clients.get(i).close();
                } catch (Exception e) {
                    // Log but continue
                }
            }
            System.out.println("   " + clients.size() + " clients closed");

            System.out.println("\nMulti-client shutdown complete.\n");

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /**
     * Demonstrates queue-specific shutdown.
     */
    public void queueShutdownExample() {
        System.out.println("=== Queue-Specific Shutdown ===\n");

        try {
            QueuesClient client = QueuesClient.builder()
                    .address(address)
                    .clientId(clientId)
                    .build();

            client.ping();
            client.createQueuesChannel("queue-shutdown-test");

            // Send some messages
            System.out.println("Sending messages to queue...");
            for (int i = 1; i <= 5; i++) {
                client.sendQueuesMessage(QueueMessage.builder()
                        .channel("queue-shutdown-test")
                        .body(("Queue message " + i).getBytes())
                        .build());
            }
            System.out.println("5 messages sent\n");

            // Start processing
            System.out.println("Processing messages...");
            AtomicBoolean running = new AtomicBoolean(true);
            AtomicInteger processed = new AtomicInteger(0);

            Thread processor = new Thread(() -> {
                while (running.get()) {
                    try {
                        QueuesPollResponse response = client.receiveQueuesMessages(
                                QueuesPollRequest.builder()
                                        .channel("queue-shutdown-test")
                                        .pollMaxMessages(2)
                                        .pollWaitTimeoutInSeconds(1)
                                        .build()
                        );

                        if (!response.isError() && response.getMessages() != null) {
                            for (QueueMessageReceived msg : response.getMessages()) {
                                System.out.println("  Processed: " + new String(msg.getBody()));
                                msg.ack();
                                processed.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        if (running.get()) {
                            System.out.println("  Error: " + e.getMessage());
                        }
                    }
                }
            });

            processor.start();
            Thread.sleep(3000);

            // SHUTDOWN
            System.out.println("\n--- Queue Processor Shutdown ---");

            // Step 1: Stop accepting new work
            System.out.println("1. Stopping processor...");
            running.set(false);

            // Step 2: Wait for processor to finish current batch
            System.out.println("2. Waiting for current batch...");
            processor.join(5000);

            if (processor.isAlive()) {
                System.out.println("   Processor didn't finish, interrupting...");
                processor.interrupt();
                processor.join(1000);
            }

            System.out.println("3. Processor stopped. Processed: " + processed.get() + " messages");

            // Step 3: Cleanup
            client.deleteQueuesChannel("queue-shutdown-test");
            client.close();
            System.out.println("4. Client closed\n");

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /**
     * Summary of graceful shutdown best practices.
     */
    public void bestPracticesSummary() {
        System.out.println("=== Graceful Shutdown Best Practices ===\n");

        System.out.println("SHUTDOWN ORDER:");
        System.out.println("  1. Stop accepting new work (set running flag)");
        System.out.println("  2. Cancel all subscriptions");
        System.out.println("  3. Wait for in-flight operations (with timeout)");
        System.out.println("  4. Acknowledge/reject remaining messages");
        System.out.println("  5. Clean up channels (if appropriate)");
        System.out.println("  6. Close client connections");
        System.out.println("  7. Release local resources\n");

        System.out.println("TIMEOUTS:");
        System.out.println("  - Set reasonable wait times for in-flight ops");
        System.out.println("  - Use Thread.join(timeout) for worker threads");
        System.out.println("  - Have a hard limit on shutdown time");
        System.out.println("  - Log operations that exceed timeout\n");

        System.out.println("ERROR HANDLING:");
        System.out.println("  - Continue shutdown even if errors occur");
        System.out.println("  - Log errors but don't throw");
        System.out.println("  - Use try-finally for cleanup");
        System.out.println("  - Handle InterruptedException properly\n");

        System.out.println("KUBERNETES/CONTAINERS:");
        System.out.println("  - Handle SIGTERM gracefully");
        System.out.println("  - Finish within terminationGracePeriodSeconds");
        System.out.println("  - Remove from load balancer first (readiness)");
        System.out.println("  - Don't start new subscriptions in shutdown\n");
    }

    /**
     * Main method demonstrating graceful shutdown.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║          Graceful Shutdown Examples                          ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        GracefulShutdownExample example = new GracefulShutdownExample();

        // Basic shutdown
        example.basicShutdownExample();

        // Shutdown hook pattern
        example.shutdownHookExample();

        // Wait for in-flight messages
        example.waitForInFlightExample();

        // Multi-client shutdown
        example.multiClientShutdownExample();

        // Queue-specific shutdown
        example.queueShutdownExample();

        // Best practices
        example.bestPracticesSummary();

        System.out.println("Graceful shutdown examples completed.");
    }
}
