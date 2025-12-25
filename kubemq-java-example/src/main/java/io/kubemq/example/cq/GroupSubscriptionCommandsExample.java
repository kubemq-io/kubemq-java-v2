package io.kubemq.example.cq;

import io.kubemq.sdk.cq.*;
import io.kubemq.sdk.common.ServerInfo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Group Subscription Commands Example
 *
 * This example demonstrates using subscription groups for load-balanced
 * command processing in KubeMQ CQ (Commands/Queries) pattern.
 *
 * Group Subscription Behavior:
 * - Multiple subscribers in the same group share the workload
 * - Each command is delivered to exactly ONE subscriber in the group
 * - Provides horizontal scaling for command handlers
 * - Built-in load balancing across group members
 *
 * Key Concepts:
 * - Group: A named collection of subscribers sharing commands
 * - Load Balancing: Commands distributed across group members
 * - Failover: If one member is slow/unavailable, others handle commands
 *
 * Use Cases:
 * - Scalable command processing
 * - High availability command handlers
 * - Work distribution across service instances
 * - Microservice command routing
 *
 * Important Notes:
 * - Only ONE subscriber in the group receives each command
 * - That subscriber MUST respond within the timeout
 * - Different groups receive the same command (fan-out to groups)
 * - Subscribers without a group receive ALL commands (broadcast)
 *
 * @see io.kubemq.sdk.cq.CommandsSubscription#getGroup()
 */
public class GroupSubscriptionCommandsExample {

    private final CQClient cqClient;
    private final String channelName = "group-commands-channel";
    private final String address = "localhost:50000";
    private final String clientId = "group-cmd-client";

    /**
     * Initializes the CQClient.
     */
    public GroupSubscriptionCommandsExample() {
        cqClient = CQClient.builder()
                .address(address)
                .clientId(clientId)
                .build();

        ServerInfo pingResult = cqClient.ping();
        System.out.println("Connected to: " + pingResult.getHost() + " v" + pingResult.getVersion());

        cqClient.createCommandsChannel(channelName);
    }

    /**
     * Demonstrates basic group subscription for commands.
     */
    public void basicGroupExample() {
        System.out.println("\n=== Basic Command Group Subscription ===\n");

        String groupName = "command-handlers";
        int numWorkers = 3;
        int numCommands = 9;

        AtomicInteger[] workerCounts = new AtomicInteger[numWorkers];
        CountDownLatch latch = new CountDownLatch(numCommands);

        // Create multiple workers in the same group
        CommandsSubscription[] subscriptions = new CommandsSubscription[numWorkers];

        for (int i = 0; i < numWorkers; i++) {
            final int workerId = i + 1;
            workerCounts[i] = new AtomicInteger(0);
            final AtomicInteger counter = workerCounts[i];

            Consumer<CommandMessageReceived> handler = cmd -> {
                counter.incrementAndGet();
                System.out.println("  Worker " + workerId + " executing: " + new String(cmd.getBody()));

                // Simulate processing time
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // Send response
                CommandResponseMessage response = CommandResponseMessage.builder()
                        .commandReceived(cmd)
                        .isExecuted(true)
                        .build();
                cqClient.sendResponseMessage(response);

                latch.countDown();
            };

            subscriptions[i] = CommandsSubscription.builder()
                    .channel(channelName)
                    .group(groupName)  // Same group for all workers
                    .onReceiveCommandCallback(handler)
                    .onErrorCallback(err -> System.err.println("Worker " + workerId + " error: " + err))
                    .build();

            cqClient.subscribeToCommands(subscriptions[i]);
        }

        System.out.println("Created " + numWorkers + " workers in group: " + groupName + "\n");

        // Wait for subscriptions to be ready
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Send commands
        System.out.println("Sending " + numCommands + " commands (load balanced across workers)...\n");

        for (int i = 1; i <= numCommands; i++) {
            CommandMessage command = CommandMessage.builder()
                    .channel(channelName)
                    .body(("Command #" + i).getBytes())
                    .timeoutInSeconds(10)
                    .build();

            try {
                CommandResponseMessage response = cqClient.sendCommandRequest(command);
                System.out.println("  Command #" + i + " executed: " + response.isExecuted());
            } catch (Exception e) {
                System.err.println("  Command #" + i + " failed: " + e.getMessage());
            }
        }

        // Wait for processing
        try {
            latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Show distribution
        System.out.println("\n─────────────────────────────────────");
        System.out.println("Command Distribution:");
        int total = 0;
        for (int i = 0; i < numWorkers; i++) {
            int count = workerCounts[i].get();
            total += count;
            System.out.println("  Worker " + (i + 1) + ": " + count + " commands");
        }
        System.out.println("  Total: " + total + " (each command handled by exactly one worker)");
        System.out.println("─────────────────────────────────────\n");

        // Cancel subscriptions
        for (CommandsSubscription sub : subscriptions) {
            sub.cancel();
        }
    }

    /**
     * Demonstrates multiple groups receiving the same commands.
     */
    public void multipleGroupsExample() {
        System.out.println("=== Multiple Command Groups ===\n");

        String channel = channelName + "-multi";
        cqClient.createCommandsChannel(channel);

        // Group 1: Executors
        AtomicInteger executor1 = new AtomicInteger(0);
        AtomicInteger executor2 = new AtomicInteger(0);

        // Group 2: Auditors
        AtomicInteger auditor1 = new AtomicInteger(0);
        AtomicInteger auditor2 = new AtomicInteger(0);

        CountDownLatch latch = new CountDownLatch(6); // 3 commands x 2 groups

        // Create executors group
        CommandsSubscription execSub1 = createGroupSubscription(channel, "executors", "Executor-1", executor1, latch);
        CommandsSubscription execSub2 = createGroupSubscription(channel, "executors", "Executor-2", executor2, latch);

        // Create auditors group
        CommandsSubscription auditSub1 = createGroupSubscription(channel, "auditors", "Auditor-1", auditor1, latch);
        CommandsSubscription auditSub2 = createGroupSubscription(channel, "auditors", "Auditor-2", auditor2, latch);

        cqClient.subscribeToCommands(execSub1);
        cqClient.subscribeToCommands(execSub2);
        cqClient.subscribeToCommands(auditSub1);
        cqClient.subscribeToCommands(auditSub2);

        System.out.println("Two groups created:");
        System.out.println("  - 'executors' (2 members) - Execute commands");
        System.out.println("  - 'auditors' (2 members) - Audit commands\n");

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Send commands
        System.out.println("Sending 3 commands...\n");

        for (int i = 1; i <= 3; i++) {
            CommandMessage command = CommandMessage.builder()
                    .channel(channel)
                    .body(("Action #" + i).getBytes())
                    .timeoutInSeconds(10)
                    .build();

            try {
                cqClient.sendCommandRequest(command);
            } catch (Exception e) {
                // Expected - multiple groups, only one responds
            }

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\nResults:");
        System.out.println("  Executors Group: " + (executor1.get() + executor2.get()) + " total");
        System.out.println("    - Executor-1: " + executor1.get());
        System.out.println("    - Executor-2: " + executor2.get());
        System.out.println("  Auditors Group: " + (auditor1.get() + auditor2.get()) + " total");
        System.out.println("    - Auditor-1: " + auditor1.get());
        System.out.println("    - Auditor-2: " + auditor2.get());
        System.out.println("\nNote: Each group receives all commands (load balanced within group).\n");

        execSub1.cancel();
        execSub2.cancel();
        auditSub1.cancel();
        auditSub2.cancel();
        cqClient.deleteCommandsChannel(channel);
    }

    private CommandsSubscription createGroupSubscription(String channel, String group, String name,
                                                          AtomicInteger counter, CountDownLatch latch) {
        return CommandsSubscription.builder()
                .channel(channel)
                .group(group)
                .onReceiveCommandCallback(cmd -> {
                    counter.incrementAndGet();
                    System.out.println("    " + name + ": " + new String(cmd.getBody()));

                    CommandResponseMessage response = CommandResponseMessage.builder()
                            .commandReceived(cmd)
                            .isExecuted(true)
                            .build();
                    cqClient.sendResponseMessage(response);

                    latch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();
    }

    /**
     * Demonstrates group vs broadcast behavior.
     */
    public void groupVsBroadcastExample() {
        System.out.println("=== Group vs Broadcast Comparison ===\n");

        // GROUP MODE - each command to one subscriber
        System.out.println("1. GROUP MODE (load balanced):\n");

        String groupChannel = channelName + "-grp";
        cqClient.createCommandsChannel(groupChannel);

        AtomicInteger groupSub1 = new AtomicInteger(0);
        AtomicInteger groupSub2 = new AtomicInteger(0);
        CountDownLatch groupLatch = new CountDownLatch(3);

        CommandsSubscription gSub1 = CommandsSubscription.builder()
                .channel(groupChannel)
                .group("shared-group")
                .onReceiveCommandCallback(cmd -> {
                    groupSub1.incrementAndGet();
                    System.out.println("    Subscriber A: " + new String(cmd.getBody()));
                    cqClient.sendResponseMessage(CommandResponseMessage.builder()
                            .commandReceived(cmd).isExecuted(true).build());
                    groupLatch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        CommandsSubscription gSub2 = CommandsSubscription.builder()
                .channel(groupChannel)
                .group("shared-group")
                .onReceiveCommandCallback(cmd -> {
                    groupSub2.incrementAndGet();
                    System.out.println("    Subscriber B: " + new String(cmd.getBody()));
                    cqClient.sendResponseMessage(CommandResponseMessage.builder()
                            .commandReceived(cmd).isExecuted(true).build());
                    groupLatch.countDown();
                })
                .onErrorCallback(err -> {})
                .build();

        cqClient.subscribeToCommands(gSub1);
        cqClient.subscribeToCommands(gSub2);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (int i = 1; i <= 3; i++) {
            try {
                cqClient.sendCommandRequest(CommandMessage.builder()
                        .channel(groupChannel)
                        .body(("Grouped " + i).getBytes())
                        .timeoutInSeconds(5)
                        .build());
            } catch (Exception e) {
                // Handle
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            groupLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\n  Result: A=" + groupSub1.get() + ", B=" + groupSub2.get() +
                " (total=3, distributed)\n");

        gSub1.cancel();
        gSub2.cancel();

        // BROADCAST MODE - no group, each subscriber gets all
        System.out.println("2. BROADCAST MODE (no group - but only one can respond):\n");

        String broadcastChannel = channelName + "-bcast";
        cqClient.createCommandsChannel(broadcastChannel);

        AtomicInteger bcastSub1 = new AtomicInteger(0);
        AtomicInteger bcastSub2 = new AtomicInteger(0);

        // Note: Without groups, both receive but only first response is used
        CommandsSubscription bSub1 = CommandsSubscription.builder()
                .channel(broadcastChannel)
                // No group - broadcast mode
                .onReceiveCommandCallback(cmd -> {
                    bcastSub1.incrementAndGet();
                    System.out.println("    Subscriber X: " + new String(cmd.getBody()));
                    cqClient.sendResponseMessage(CommandResponseMessage.builder()
                            .commandReceived(cmd).isExecuted(true).build());
                })
                .onErrorCallback(err -> {})
                .build();

        CommandsSubscription bSub2 = CommandsSubscription.builder()
                .channel(broadcastChannel)
                // No group - broadcast mode
                .onReceiveCommandCallback(cmd -> {
                    bcastSub2.incrementAndGet();
                    System.out.println("    Subscriber Y: " + new String(cmd.getBody()));
                    // Also responds, but first response wins
                    cqClient.sendResponseMessage(CommandResponseMessage.builder()
                            .commandReceived(cmd).isExecuted(true).build());
                })
                .onErrorCallback(err -> {})
                .build();

        cqClient.subscribeToCommands(bSub1);
        cqClient.subscribeToCommands(bSub2);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (int i = 1; i <= 3; i++) {
            try {
                cqClient.sendCommandRequest(CommandMessage.builder()
                        .channel(broadcastChannel)
                        .body(("Broadcast " + i).getBytes())
                        .timeoutInSeconds(5)
                        .build());
            } catch (Exception e) {
                // Handle
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\n  Result: X=" + bcastSub1.get() + ", Y=" + bcastSub2.get() +
                " (both received all, but first response wins)\n");

        bSub1.cancel();
        bSub2.cancel();
        cqClient.deleteCommandsChannel(groupChannel);
        cqClient.deleteCommandsChannel(broadcastChannel);
    }

    /**
     * Demonstrates worker pool pattern.
     */
    public void workerPoolExample() {
        System.out.println("=== Worker Pool Pattern ===\n");

        String poolChannel = channelName + "-pool";
        cqClient.createCommandsChannel(poolChannel);

        int poolSize = 4;
        AtomicInteger totalProcessed = new AtomicInteger(0);
        AtomicInteger[] workerLoads = new AtomicInteger[poolSize];
        CommandsSubscription[] pool = new CommandsSubscription[poolSize];

        // Create worker pool
        for (int i = 0; i < poolSize; i++) {
            final int workerId = i + 1;
            workerLoads[i] = new AtomicInteger(0);
            final AtomicInteger load = workerLoads[i];

            pool[i] = CommandsSubscription.builder()
                    .channel(poolChannel)
                    .group("worker-pool")
                    .onReceiveCommandCallback(cmd -> {
                        load.incrementAndGet();
                        totalProcessed.incrementAndGet();

                        // Simulate variable processing time
                        try {
                            Thread.sleep(50 + (workerId * 10));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }

                        cqClient.sendResponseMessage(CommandResponseMessage.builder()
                                .commandReceived(cmd)
                                .isExecuted(true)
                                .build());
                    })
                    .onErrorCallback(err -> {})
                    .build();

            cqClient.subscribeToCommands(pool[i]);
        }

        System.out.println("Worker pool created with " + poolSize + " workers\n");

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Send burst of commands
        int numCommands = 20;
        System.out.println("Sending " + numCommands + " commands in parallel...\n");

        Thread[] senders = new Thread[numCommands];
        for (int i = 0; i < numCommands; i++) {
            final int cmdId = i + 1;
            senders[i] = new Thread(() -> {
                try {
                    cqClient.sendCommandRequest(CommandMessage.builder()
                            .channel(poolChannel)
                            .body(("Task " + cmdId).getBytes())
                            .timeoutInSeconds(30)
                            .build());
                } catch (Exception e) {
                    // Handle
                }
            });
            senders[i].start();
        }

        // Wait for all commands to complete
        for (Thread sender : senders) {
            try {
                sender.join(30000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("Work Distribution:");
        for (int i = 0; i < poolSize; i++) {
            int processed = workerLoads[i].get();
            int barLength = processed;
            String bar = "█".repeat(barLength);
            System.out.printf("  Worker %d: %2d %s%n", i + 1, processed, bar);
        }
        System.out.println("\nTotal processed: " + totalProcessed.get() + "\n");

        for (CommandsSubscription sub : pool) {
            sub.cancel();
        }
        cqClient.deleteCommandsChannel(poolChannel);
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
     * Main method demonstrating group subscription for commands.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║        Group Subscription Commands Examples                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        GroupSubscriptionCommandsExample example = new GroupSubscriptionCommandsExample();

        try {
            // Basic group subscription
            example.basicGroupExample();

            // Multiple groups
            example.multipleGroupsExample();

            // Group vs broadcast
            example.groupVsBroadcastExample();

            // Worker pool pattern
            example.workerPoolExample();

        } finally {
            example.cleanup();
        }

        System.out.println("Group subscription commands examples completed.");
    }
}
