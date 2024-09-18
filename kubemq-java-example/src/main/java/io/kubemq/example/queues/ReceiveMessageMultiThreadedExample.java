package io.kubemq.example.queues;

import io.kubemq.sdk.queues.QueueMessage;
import io.kubemq.sdk.queues.QueueMessageReceived;
import io.kubemq.sdk.queues.QueueSendResult;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.queues.QueuesPollRequest;
import io.kubemq.sdk.queues.QueuesPollResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

public class ReceiveMessageMultiThreadedExample {

    private final QueuesClient queuesClient;
    private final String channelName = "mytest-channel";
    private final String address = "localhost:50000";
    private final String clientId = "kubeMQClientId";
    private final ExecutorService workerPool;

    public ReceiveMessageMultiThreadedExample(int numWorkers) {
        queuesClient = QueuesClient.builder()
                .address(address)
                .clientId(clientId)
                .build();

        // Create a thread pool with a fixed number of workers
        this.workerPool = Executors.newFixedThreadPool(numWorkers);
    }

    /**
     * Sends 20 messages to the queue.
     */
    public void sendQueueMessage() {
        System.out.println("\n============================== Send Queue Messages Started =============================\n");
        try {
            for (int i = 0; i < 20; i++) {
                Map<String, String> tags = new HashMap<>();
                tags.put("tag1", "kubemq");
                tags.put("tag2", "kubemq2");

                QueueMessage message = QueueMessage.builder()
                        .body(("Message " + (i + 1)).getBytes())  // Unique message body
                        .channel(channelName)
                        .metadata("Sample metadata " + (i + 1))
                        .id(UUID.randomUUID().toString())
                        .tags(tags)
                        .delayInSeconds(1)
                        .expirationInSeconds(3600)
                        .attemptsBeforeDeadLetterQueue(3)
                        .deadLetterQueue("dlq-" + channelName)
                        .build();

                QueueSendResult sendResult = queuesClient.sendQueuesMessage(message);
                System.out.println("Message " + (i + 1) + " sent with ID: " + sendResult.getId());
            }
        } catch (RuntimeException e) {
            System.err.println("Failed to send queue messages: " + e.getMessage());
        }
    }

    /**
     * Receives messages from the queue and processes them in multiple threads.
     */
    public void receiveQueuesMessages() {
        try {
            QueuesPollRequest queuesPollRequest = QueuesPollRequest.builder()
                    .channel(channelName)
                    .pollMaxMessages(10)  // Pull 10 messages at a time
                    .pollWaitTimeoutInSeconds(10)
                    .build();

            QueuesPollResponse pollResponse = queuesClient.receiveQueuesMessages(queuesPollRequest);

            System.out.println("Received Message Response:");
            System.out.println("RefRequestId: " + pollResponse.getRefRequestId());
            System.out.println("ReceiverClientId: " + pollResponse.getReceiverClientId());
            System.out.println("TransactionId: " + pollResponse.getTransactionId());

            if (pollResponse.isError()) {
                System.out.println("Error: " + pollResponse.getError());
            } else {
                pollResponse.getMessages().forEach(msg -> {
                    // Submit each message to a separate worker thread for processing
                    workerPool.submit(() -> processMessage(msg));
                });
            }

        } catch (RuntimeException e) {
            System.err.println("Failed to receive queue messages: " + e.getMessage());
        }
    }

    /**
     * Processes a single message in a separate thread.
     */
    private void processMessage(QueueMessageReceived msg) {
        try {
            System.out.println("Processing Message ID: " + msg.getId());
            System.out.println("Message Body: " + new String(msg.getBody()));

            // Simulate job processing, do some real world work to mark it success or fail
            boolean jobSuccess = ThreadLocalRandom.current().nextBoolean(); // Here i am sending true/false randomly

            if (jobSuccess) {
                msg.ack();
                System.out.println("Message ID: " + msg.getId() + " acknowledged.");
            } else {
                msg.reject();
                System.out.println("Message ID: " + msg.getId() + " rejected.");
            }

        } catch (Exception e) {
            System.err.println("Error processing message ID: " + msg.getId() + ". Error: " + e.getMessage());
            msg.reQueue(channelName);
            System.out.println("Message ID: " + msg.getId() + " requeued.");
        }
    }


    /**
     * Shutdown the thread pool gracefully.
     */
    public void shutdownWorkerPool() throws InterruptedException {
        workerPool.shutdown();
        if (!workerPool.awaitTermination(60, TimeUnit.SECONDS)) {
            workerPool.shutdownNow();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ReceiveMessageMultiThreadedExample example = new ReceiveMessageMultiThreadedExample(10);  // 10 worker threads
        System.out.println("Starting to send and receive messages: " + new java.util.Date());

        // Send 20 messages
        example.sendQueueMessage();

        // Wait for a moment to ensure messages are sent
        Thread.sleep(2000);

        // Receive and process messages
        example.receiveQueuesMessages();

        // Shutdown the worker pool after processing is done
        Thread.sleep(8000);  // Wait for workers to finish processing (for demo purposes)
        example.shutdownWorkerPool();

        // Keep the main thread running (for demo purposes)
//        CountDownLatch latch = new CountDownLatch(1);
//        latch.await();
    }
}
