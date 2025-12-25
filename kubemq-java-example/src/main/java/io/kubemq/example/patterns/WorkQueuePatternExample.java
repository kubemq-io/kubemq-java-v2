package io.kubemq.example.patterns;

import io.kubemq.sdk.queues.*;
import io.kubemq.sdk.common.ServerInfo;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Work Queue Pattern Example
 *
 * This example demonstrates the Work Queue (Task Queue) pattern using
 * KubeMQ Queues. This pattern distributes work across multiple workers
 * for parallel processing.
 *
 * Work Queue Characteristics:
 * - One producer, multiple consumers (workers)
 * - Each task is processed by exactly one worker
 * - Tasks are load-balanced across workers
 * - Failed tasks can be retried or sent to DLQ
 *
 * Key Features in KubeMQ Queues:
 * - Visibility timeout for processing safety
 * - Acknowledgment/rejection for reliability
 * - Dead letter queue for failed tasks
 * - Delayed tasks for scheduling
 * - Message expiration for time-sensitive work
 *
 * Use Cases:
 * - Background job processing
 * - Image/video processing pipelines
 * - Email/notification sending
 * - Data import/export jobs
 * - Order processing systems
 *
 * Best Practices:
 * - Set appropriate visibility timeout for task duration
 * - Implement idempotent task handlers
 * - Use DLQ for failed tasks
 * - Monitor queue depth and processing times
 * - Scale workers based on queue depth
 *
 * @see io.kubemq.sdk.queues.QueueMessage
 * @see io.kubemq.sdk.queues.QueuesPollRequest
 */
public class WorkQueuePatternExample {

    private final QueuesClient queuesClient;
    private final String address = "localhost:50000";
    private final String clientId = "work-queue-client";

    /**
     * Initializes the QueuesClient.
     */
    public WorkQueuePatternExample() {
        queuesClient = QueuesClient.builder()
                .address(address)
                .clientId(clientId)
                .build();

        ServerInfo pingResult = queuesClient.ping();
        System.out.println("Connected to: " + pingResult.getHost() + " v" + pingResult.getVersion());
    }

    /**
     * Demonstrates basic work queue with multiple workers.
     */
    public void basicWorkQueueExample() {
        System.out.println("\n=== Basic Work Queue Pattern ===\n");

        String queueName = "task-queue";
        queuesClient.createQueuesChannel(queueName);

        int numWorkers = 3;
        int numTasks = 12;

        AtomicInteger[] workerCounts = new AtomicInteger[numWorkers];
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch allDone = new CountDownLatch(numTasks);

        // Start workers
        Thread[] workers = new Thread[numWorkers];
        for (int i = 0; i < numWorkers; i++) {
            final int workerId = i + 1;
            workerCounts[i] = new AtomicInteger(0);
            final AtomicInteger counter = workerCounts[i];

            workers[i] = new Thread(() -> {
                while (running.get()) {
                    try {
                        QueuesPollResponse response = queuesClient.receiveQueuesMessages(
                                QueuesPollRequest.builder()
                                        .channel(queueName)
                                        .pollMaxMessages(1)
                                        .pollWaitTimeoutInSeconds(2)
                                        .visibilitySeconds(30)
                                        .build()
                        );

                        if (!response.isError() && response.getMessages() != null) {
                            for (QueueMessageReceived msg : response.getMessages()) {
                                counter.incrementAndGet();
                                String task = new String(msg.getBody());

                                // Simulate processing
                                Thread.sleep(100 + (workerId * 50));

                                System.out.println("  Worker " + workerId + " completed: " + task);
                                msg.ack();
                                allDone.countDown();
                            }
                        }
                    } catch (Exception e) {
                        if (running.get()) {
                            System.err.println("Worker " + workerId + " error: " + e.getMessage());
                        }
                    }
                }
            }, "Worker-" + workerId);

            workers[i].start();
        }

        System.out.println("Started " + numWorkers + " workers\n");

        // Wait for workers to be ready
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Add tasks to queue
        System.out.println("Adding " + numTasks + " tasks to queue...\n");
        for (int i = 1; i <= numTasks; i++) {
            queuesClient.sendQueuesMessage(
                    QueueMessage.builder()
                            .channel(queueName)
                            .body(("Task #" + i).getBytes())
                            .build()
            );
        }

        // Wait for all tasks to complete
        try {
            allDone.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        running.set(false);

        // Wait for workers to stop
        for (Thread worker : workers) {
            try {
                worker.interrupt();
                worker.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Show distribution
        System.out.println("\n─────────────────────────────────────");
        System.out.println("Task Distribution:");
        int total = 0;
        for (int i = 0; i < numWorkers; i++) {
            int count = workerCounts[i].get();
            total += count;
            System.out.println("  Worker " + (i + 1) + ": " + count + " tasks");
        }
        System.out.println("  Total: " + total + "/" + numTasks);
        System.out.println("─────────────────────────────────────\n");

        queuesClient.deleteQueuesChannel(queueName);
    }

    /**
     * Demonstrates task with retry on failure.
     */
    public void taskRetryExample() {
        System.out.println("=== Task Retry Pattern ===\n");

        String queueName = "retry-queue";
        queuesClient.createQueuesChannel(queueName);

        AtomicInteger attempts = new AtomicInteger(0);
        int maxRetries = 3;

        // Add task that will fail initially
        System.out.println("Adding task that requires retries...\n");

        Map<String, String> tags = new HashMap<>();
        tags.put("retryCount", "0");
        tags.put("maxRetries", String.valueOf(maxRetries));

        queuesClient.sendQueuesMessage(
                QueueMessage.builder()
                        .channel(queueName)
                        .body("Flaky task".getBytes())
                        .tags(tags)
                        .build()
        );

        // Worker with retry logic
        AtomicBoolean taskCompleted = new AtomicBoolean(false);

        while (!taskCompleted.get() && attempts.get() <= maxRetries) {
            try {
                QueuesPollResponse response = queuesClient.receiveQueuesMessages(
                        QueuesPollRequest.builder()
                                .channel(queueName)
                                .pollMaxMessages(1)
                                .pollWaitTimeoutInSeconds(2)
                                .visibilitySeconds(10)
                                .build()
                );

                if (!response.isError() && response.getMessages() != null && !response.getMessages().isEmpty()) {
                    QueueMessageReceived msg = response.getMessages().get(0);
                    int attempt = attempts.incrementAndGet();
                    int retryCount = Integer.parseInt(msg.getTags().getOrDefault("retryCount", "0"));

                    System.out.println("  Attempt " + attempt + " (retry " + retryCount + "):");

                    // Simulate failure on first 2 attempts
                    boolean success = attempt > 2;

                    if (success) {
                        System.out.println("    SUCCESS - Task completed!");
                        msg.ack();
                        taskCompleted.set(true);
                    } else {
                        System.out.println("    FAILED - Will retry");

                        // Reject and re-queue with updated retry count
                        msg.reject();

                        if (retryCount < maxRetries) {
                            Map<String, String> newTags = new HashMap<>(msg.getTags());
                            newTags.put("retryCount", String.valueOf(retryCount + 1));

                            queuesClient.sendQueuesMessage(
                                    QueueMessage.builder()
                                            .channel(queueName)
                                            .body(msg.getBody())
                                            .tags(newTags)
                                            .delayInSeconds(1)  // Delay before retry
                                            .build()
                            );
                        }
                    }
                }

                Thread.sleep(500);

            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }

        System.out.println("\nTask " + (taskCompleted.get() ? "completed" : "failed") +
                " after " + attempts.get() + " attempts.\n");

        queuesClient.deleteQueuesChannel(queueName);
    }

    /**
     * Demonstrates priority queue pattern.
     */
    public void priorityQueueExample() {
        System.out.println("=== Priority Queue Pattern ===\n");

        // Use separate queues for different priorities
        String highQueue = "priority-high";
        String medQueue = "priority-medium";
        String lowQueue = "priority-low";

        queuesClient.createQueuesChannel(highQueue);
        queuesClient.createQueuesChannel(medQueue);
        queuesClient.createQueuesChannel(lowQueue);

        // Add tasks with different priorities
        System.out.println("Adding tasks with different priorities...\n");

        for (int i = 1; i <= 3; i++) {
            queuesClient.sendQueuesMessage(QueueMessage.builder()
                    .channel(lowQueue)
                    .body(("Low priority task " + i).getBytes())
                    .build());
        }

        for (int i = 1; i <= 2; i++) {
            queuesClient.sendQueuesMessage(QueueMessage.builder()
                    .channel(medQueue)
                    .body(("Medium priority task " + i).getBytes())
                    .build());
        }

        queuesClient.sendQueuesMessage(QueueMessage.builder()
                .channel(highQueue)
                .body("High priority task 1".getBytes())
                .build());

        System.out.println("Tasks added: 1 high, 2 medium, 3 low\n");

        // Priority worker - checks high queue first, then medium, then low
        System.out.println("Processing tasks by priority:\n");

        String[] priorityOrder = {highQueue, medQueue, lowQueue};
        AtomicInteger processed = new AtomicInteger(0);

        while (processed.get() < 6) {
            boolean foundTask = false;

            for (String queue : priorityOrder) {
                try {
                    QueuesPollResponse response = queuesClient.receiveQueuesMessages(
                            QueuesPollRequest.builder()
                                    .channel(queue)
                                    .pollMaxMessages(1)
                                    .pollWaitTimeoutInSeconds(1)
                                    .build()
                    );

                    if (!response.isError() && response.getMessages() != null && !response.getMessages().isEmpty()) {
                        QueueMessageReceived msg = response.getMessages().get(0);
                        processed.incrementAndGet();

                        String priority = queue.replace("priority-", "").toUpperCase();
                        System.out.println("  [" + priority + "] " + new String(msg.getBody()));

                        msg.ack();
                        foundTask = true;
                        break;  // Always check high priority first after processing
                    }
                } catch (Exception e) {
                    // Continue to next priority level
                }
            }

            if (!foundTask) {
                break;  // No more tasks
            }
        }

        System.out.println("\nProcessed " + processed.get() + " tasks.\n");

        queuesClient.deleteQueuesChannel(highQueue);
        queuesClient.deleteQueuesChannel(medQueue);
        queuesClient.deleteQueuesChannel(lowQueue);
    }

    /**
     * Demonstrates delayed task execution.
     */
    public void delayedTaskExample() {
        System.out.println("=== Delayed Task Execution ===\n");

        String queueName = "delayed-tasks";
        queuesClient.createQueuesChannel(queueName);

        // Schedule tasks with different delays
        System.out.println("Scheduling tasks with delays...\n");

        long now = System.currentTimeMillis();

        // Immediate task
        queuesClient.sendQueuesMessage(QueueMessage.builder()
                .channel(queueName)
                .body("Immediate task".getBytes())
                .build());
        System.out.println("  Scheduled: Immediate task (now)");

        // 2-second delay
        queuesClient.sendQueuesMessage(QueueMessage.builder()
                .channel(queueName)
                .body("2-second delayed task".getBytes())
                .delayInSeconds(2)
                .build());
        System.out.println("  Scheduled: 2-second delayed task");

        // 4-second delay
        queuesClient.sendQueuesMessage(QueueMessage.builder()
                .channel(queueName)
                .body("4-second delayed task".getBytes())
                .delayInSeconds(4)
                .build());
        System.out.println("  Scheduled: 4-second delayed task");

        System.out.println("\nPolling for tasks...\n");

        // Poll for tasks over time
        AtomicInteger received = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        while (received.get() < 3 && (System.currentTimeMillis() - startTime) < 10000) {
            try {
                QueuesPollResponse response = queuesClient.receiveQueuesMessages(
                        QueuesPollRequest.builder()
                                .channel(queueName)
                                .pollMaxMessages(1)
                                .pollWaitTimeoutInSeconds(1)
                                .build()
                );

                if (!response.isError() && response.getMessages() != null && !response.getMessages().isEmpty()) {
                    for (QueueMessageReceived msg : response.getMessages()) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        System.out.println("  [+" + (elapsed / 1000) + "s] Received: " + new String(msg.getBody()));
                        msg.ack();
                        received.incrementAndGet();
                    }
                }
            } catch (Exception e) {
                // Continue polling
            }
        }

        System.out.println("\nReceived " + received.get() + " tasks over time.\n");

        queuesClient.deleteQueuesChannel(queueName);
    }

    /**
     * Demonstrates batch processing pattern.
     */
    public void batchProcessingExample() {
        System.out.println("=== Batch Processing Pattern ===\n");

        String queueName = "batch-queue";
        queuesClient.createQueuesChannel(queueName);

        // Add many tasks
        int totalTasks = 20;
        int batchSize = 5;

        System.out.println("Adding " + totalTasks + " tasks to queue...\n");

        for (int i = 1; i <= totalTasks; i++) {
            queuesClient.sendQueuesMessage(QueueMessage.builder()
                    .channel(queueName)
                    .body(("Task item " + i).getBytes())
                    .build());
        }

        // Process in batches
        System.out.println("Processing in batches of " + batchSize + ":\n");

        int batchNumber = 0;
        AtomicInteger totalProcessed = new AtomicInteger(0);

        while (totalProcessed.get() < totalTasks) {
            try {
                QueuesPollResponse response = queuesClient.receiveQueuesMessages(
                        QueuesPollRequest.builder()
                                .channel(queueName)
                                .pollMaxMessages(batchSize)
                                .pollWaitTimeoutInSeconds(2)
                                .build()
                );

                if (response.isError() || response.getMessages() == null || response.getMessages().isEmpty()) {
                    break;
                }

                batchNumber++;
                List<QueueMessageReceived> batch = response.getMessages();

                System.out.println("  Batch " + batchNumber + " (" + batch.size() + " items):");

                // Process batch
                StringBuilder batchItems = new StringBuilder();
                for (QueueMessageReceived msg : batch) {
                    batchItems.append(new String(msg.getBody())).append(", ");
                    totalProcessed.incrementAndGet();
                }
                System.out.println("    Items: " + batchItems.toString().replaceAll(", $", ""));

                // Simulate batch processing
                Thread.sleep(200);

                // Acknowledge all in batch
                for (QueueMessageReceived msg : batch) {
                    msg.ack();
                }
                System.out.println("    Batch acknowledged");

            } catch (Exception e) {
                System.err.println("Batch error: " + e.getMessage());
            }
        }

        System.out.println("\nProcessed " + totalProcessed.get() + " items in " + batchNumber + " batches.\n");

        queuesClient.deleteQueuesChannel(queueName);
    }

    /**
     * Demonstrates work stealing pattern concept.
     */
    public void workStealingConceptExample() {
        System.out.println("=== Work Stealing Concept ===\n");

        System.out.println("Work stealing pattern for load balancing:\n");

        System.out.println("1. MULTIPLE QUEUE APPROACH:");
        System.out.println("   - Each worker has its own queue");
        System.out.println("   - Idle workers 'steal' from busy workers' queues");
        System.out.println("   - Balances load dynamically\n");

        System.out.println("2. IMPLEMENTATION STRATEGY:");
        System.out.println("   - Primary queue for each worker");
        System.out.println("   - Short timeout when polling own queue");
        System.out.println("   - Poll other queues if own is empty");
        System.out.println("   - Use visibility timeout to prevent duplicates\n");

        System.out.println("3. KUBEMQ FEATURES THAT HELP:");
        System.out.println("   - Short wait timeouts (waitTimeoutInSeconds)");
        System.out.println("   - Visibility timeout (visibilityTimeoutInSeconds)");
        System.out.println("   - Ack/Nack for reliable processing");
        System.out.println("   - Multiple channel polling\n");

        System.out.println("4. PSEUDOCODE:");
        System.out.println("   ```");
        System.out.println("   while (running) {");
        System.out.println("       msg = poll(myQueue, timeout=1s)");
        System.out.println("       if (msg == null) {");
        System.out.println("           // Steal from other worker");
        System.out.println("           msg = poll(otherQueue, timeout=1s)");
        System.out.println("       }");
        System.out.println("       if (msg != null) process(msg)");
        System.out.println("   }");
        System.out.println("   ```\n");
    }

    /**
     * Cleans up resources.
     */
    public void cleanup() {
        queuesClient.close();
        System.out.println("Cleaned up resources.\n");
    }

    /**
     * Main method demonstrating work queue patterns.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║          Work Queue Pattern Examples                         ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        WorkQueuePatternExample example = new WorkQueuePatternExample();

        try {
            // Basic work queue
            example.basicWorkQueueExample();

            // Task retry
            example.taskRetryExample();

            // Priority queue
            example.priorityQueueExample();

            // Delayed tasks
            example.delayedTaskExample();

            // Batch processing
            example.batchProcessingExample();

            // Work stealing concept
            example.workStealingConceptExample();

        } finally {
            example.cleanup();
        }

        System.out.println("Work queue pattern examples completed.");
    }
}
