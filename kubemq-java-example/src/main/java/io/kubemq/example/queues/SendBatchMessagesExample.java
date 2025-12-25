package io.kubemq.example.queues;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueueMessage;
import io.kubemq.sdk.queues.QueueSendResult;
import io.kubemq.sdk.queues.QueuesClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Batch Message Sending Example
 *
 * This example demonstrates efficient patterns for sending multiple messages
 * to KubeMQ queues. While the SDK sends messages individually, this example
 * shows how to implement batch-like behavior using:
 *
 * 1. Sequential batch sending - Send multiple messages in a loop
 * 2. Parallel batch sending - Send messages concurrently for higher throughput
 * 3. Batch with error handling - Track success/failure of each message
 *
 * Use Cases:
 * - Bulk data ingestion
 * - Event sourcing batch commits
 * - Migration of data between systems
 * - High-throughput message production
 *
 * Performance Considerations:
 * - Parallel sending increases throughput but uses more connections
 * - Consider message ordering requirements when using parallel sending
 * - Monitor server resources when sending large batches
 *
 * @see io.kubemq.sdk.queues.QueuesClient
 * @see io.kubemq.sdk.queues.QueueMessage
 */
public class SendBatchMessagesExample {

    private final QueuesClient queuesClient;
    private final String channelName = "batch-messages-channel";
    private final String address = "localhost:50000";
    private final String clientId = "batch-sender";

    /**
     * Initializes the QueuesClient and verifies connection.
     */
    public SendBatchMessagesExample() {
        queuesClient = QueuesClient.builder()
                .address(address)
                .clientId(clientId)
                .build();

        ServerInfo pingResult = queuesClient.ping();
        System.out.println("Connected to: " + pingResult.getHost() + " v" + pingResult.getVersion());
    }

    /**
     * Creates a batch of test messages.
     *
     * @param count Number of messages to create
     * @return List of QueueMessage objects
     */
    private List<QueueMessage> createMessageBatch(int count) {
        List<QueueMessage> messages = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Map<String, String> tags = new HashMap<>();
            tags.put("batch_id", UUID.randomUUID().toString());
            tags.put("sequence", String.valueOf(i));
            tags.put("timestamp", String.valueOf(System.currentTimeMillis()));

            QueueMessage message = QueueMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .channel(channelName)
                    .body(("Batch message #" + (i + 1)).getBytes())
                    .metadata("Batch processing - message " + (i + 1) + " of " + count)
                    .tags(tags)
                    .build();

            messages.add(message);
        }

        return messages;
    }

    /**
     * Sends messages sequentially (one at a time).
     * This approach preserves message order and is simpler to implement.
     *
     * Pros:
     * - Guaranteed message ordering
     * - Simple error handling
     * - Lower resource usage
     *
     * Cons:
     * - Lower throughput than parallel sending
     */
    public void sendBatchSequentially() {
        System.out.println("\n=== Sequential Batch Sending ===\n");

        int batchSize = 10;
        List<QueueMessage> messages = createMessageBatch(batchSize);

        int successCount = 0;
        int failureCount = 0;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < messages.size(); i++) {
            try {
                QueueSendResult result = queuesClient.sendQueuesMessage(messages.get(i));

                if (!result.isError()) {
                    successCount++;
                    System.out.println("  [" + (i + 1) + "/" + batchSize + "] Sent: " + result.getId());
                } else {
                    failureCount++;
                    System.err.println("  [" + (i + 1) + "/" + batchSize + "] Failed: " + result.getError());
                }
            } catch (Exception e) {
                failureCount++;
                System.err.println("  [" + (i + 1) + "/" + batchSize + "] Error: " + e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("\nSequential batch complete:");
        System.out.println("  Total: " + batchSize + ", Success: " + successCount + ", Failed: " + failureCount);
        System.out.println("  Duration: " + duration + "ms");
        System.out.println("  Throughput: " + (batchSize * 1000 / Math.max(duration, 1)) + " msg/sec\n");
    }

    /**
     * Sends messages in parallel for higher throughput.
     * Uses CompletableFuture to send multiple messages concurrently.
     *
     * Pros:
     * - Higher throughput
     * - Better utilization of network resources
     *
     * Cons:
     * - No guaranteed message ordering
     * - Higher resource usage
     * - More complex error handling
     */
    public void sendBatchInParallel() {
        System.out.println("\n=== Parallel Batch Sending ===\n");

        int batchSize = 20;
        int parallelism = 5; // Number of concurrent senders
        List<QueueMessage> messages = createMessageBatch(batchSize);

        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        List<CompletableFuture<BatchResult>> futures = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        // Submit all messages for parallel processing
        for (int i = 0; i < messages.size(); i++) {
            final int index = i;
            final QueueMessage message = messages.get(i);

            CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    QueueSendResult result = queuesClient.sendQueuesMessage(message);
                    return new BatchResult(index, result.getId(), !result.isError(), result.getError());
                } catch (Exception e) {
                    return new BatchResult(index, null, false, e.getMessage());
                }
            }, executor);

            futures.add(future);
        }

        // Wait for all messages to be sent
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Collect results
        int successCount = 0;
        int failureCount = 0;

        for (CompletableFuture<BatchResult> future : futures) {
            try {
                BatchResult result = future.get();
                if (result.success) {
                    successCount++;
                } else {
                    failureCount++;
                    System.err.println("  Message " + result.index + " failed: " + result.error);
                }
            } catch (Exception e) {
                failureCount++;
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Parallel batch complete:");
        System.out.println("  Total: " + batchSize + ", Success: " + successCount + ", Failed: " + failureCount);
        System.out.println("  Parallelism: " + parallelism + " threads");
        System.out.println("  Duration: " + duration + "ms");
        System.out.println("  Throughput: " + (batchSize * 1000 / Math.max(duration, 1)) + " msg/sec\n");
    }

    /**
     * Sends messages in configurable chunks for controlled batch processing.
     * Useful when you need to process large batches without overwhelming the server.
     *
     * @param totalMessages Total number of messages to send
     * @param chunkSize     Number of messages per chunk
     * @param delayBetweenChunksMs Delay between chunks in milliseconds
     */
    public void sendBatchInChunks(int totalMessages, int chunkSize, long delayBetweenChunksMs) {
        System.out.println("\n=== Chunked Batch Sending ===\n");
        System.out.println("Configuration:");
        System.out.println("  Total Messages: " + totalMessages);
        System.out.println("  Chunk Size: " + chunkSize);
        System.out.println("  Delay Between Chunks: " + delayBetweenChunksMs + "ms\n");

        List<QueueMessage> allMessages = createMessageBatch(totalMessages);
        int totalChunks = (int) Math.ceil((double) totalMessages / chunkSize);
        int overallSuccess = 0;
        int overallFailure = 0;

        long startTime = System.currentTimeMillis();

        for (int chunk = 0; chunk < totalChunks; chunk++) {
            int start = chunk * chunkSize;
            int end = Math.min(start + chunkSize, totalMessages);
            List<QueueMessage> chunkMessages = allMessages.subList(start, end);

            System.out.println("Processing chunk " + (chunk + 1) + "/" + totalChunks +
                    " (messages " + (start + 1) + "-" + end + ")");

            int chunkSuccess = 0;
            for (QueueMessage message : chunkMessages) {
                try {
                    QueueSendResult result = queuesClient.sendQueuesMessage(message);
                    if (!result.isError()) {
                        chunkSuccess++;
                        overallSuccess++;
                    } else {
                        overallFailure++;
                    }
                } catch (Exception e) {
                    overallFailure++;
                }
            }

            System.out.println("  Chunk complete: " + chunkSuccess + "/" + chunkMessages.size() + " succeeded");

            // Delay between chunks (except after the last chunk)
            if (chunk < totalChunks - 1 && delayBetweenChunksMs > 0) {
                try {
                    Thread.sleep(delayBetweenChunksMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("\nChunked batch complete:");
        System.out.println("  Total: " + totalMessages + ", Success: " + overallSuccess + ", Failed: " + overallFailure);
        System.out.println("  Duration: " + duration + "ms");
        System.out.println("  Effective Throughput: " + (totalMessages * 1000 / Math.max(duration, 1)) + " msg/sec\n");
    }

    /**
     * Demonstrates batch sending with comprehensive result tracking.
     * Returns detailed results for each message in the batch.
     */
    public List<BatchResult> sendBatchWithTracking() {
        System.out.println("\n=== Batch Sending with Result Tracking ===\n");

        int batchSize = 5;
        List<QueueMessage> messages = createMessageBatch(batchSize);
        List<BatchResult> results = new ArrayList<>();

        for (int i = 0; i < messages.size(); i++) {
            QueueMessage message = messages.get(i);
            long sendStart = System.currentTimeMillis();

            try {
                QueueSendResult result = queuesClient.sendQueuesMessage(message);
                long sendDuration = System.currentTimeMillis() - sendStart;

                BatchResult batchResult = new BatchResult(
                        i,
                        result.getId(),
                        !result.isError(),
                        result.getError()
                );
                batchResult.durationMs = sendDuration;
                results.add(batchResult);

                System.out.println("  Message " + (i + 1) + ": " +
                        (batchResult.success ? "SUCCESS" : "FAILED") +
                        " (" + sendDuration + "ms)");

            } catch (Exception e) {
                long sendDuration = System.currentTimeMillis() - sendStart;
                BatchResult batchResult = new BatchResult(i, null, false, e.getMessage());
                batchResult.durationMs = sendDuration;
                results.add(batchResult);

                System.out.println("  Message " + (i + 1) + ": EXCEPTION - " + e.getMessage());
            }
        }

        // Summary statistics
        long successCount = results.stream().filter(r -> r.success).count();
        double avgDuration = results.stream().mapToLong(r -> r.durationMs).average().orElse(0);

        System.out.println("\nBatch tracking summary:");
        System.out.println("  Success Rate: " + (successCount * 100 / batchSize) + "%");
        System.out.println("  Average Send Time: " + String.format("%.2f", avgDuration) + "ms\n");

        return results;
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
     * Helper class to track batch send results.
     */
    public static class BatchResult {
        public final int index;
        public final String messageId;
        public final boolean success;
        public final String error;
        public long durationMs;

        public BatchResult(int index, String messageId, boolean success, String error) {
            this.index = index;
            this.messageId = messageId;
            this.success = success;
            this.error = error;
        }
    }

    /**
     * Main method demonstrating all batch sending patterns.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           KubeMQ Batch Message Sending Examples              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        SendBatchMessagesExample example = new SendBatchMessagesExample();

        try {
            // Create channel
            example.queuesClient.createQueuesChannel(example.channelName);

            // Sequential batch
            example.sendBatchSequentially();

            // Parallel batch
            example.sendBatchInParallel();

            // Chunked batch
            example.sendBatchInChunks(15, 5, 100);

            // Tracked batch
            example.sendBatchWithTracking();

        } finally {
            example.cleanup();
        }

        System.out.println("Batch sending examples completed.");
    }
}
