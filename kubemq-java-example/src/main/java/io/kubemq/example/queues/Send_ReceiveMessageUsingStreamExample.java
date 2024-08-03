package io.kubemq.example.queues;

import com.google.protobuf.ByteString;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueueMessage;
import io.kubemq.sdk.queues.QueueSendResult;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.queues.QueuesPollRequest;
import io.kubemq.sdk.queues.QueuesPollResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * Example class demonstrating how to poll the queue messages using from KubeMQ
 * This class initializes the KubeMQClient
 * and QueuesClient, and handles the message polling, ack, requeue, reject.
 */
public class Send_ReceiveMessageUsingStreamExample {

    private final QueuesClient queuesClient;
    private final String channelName = "mytest-channel";
    private final String address = "localhost:50000";
    private final String clientId = "kubeMQClientId";

    /**
     * Constructs a QueuesDownstreamMessageExample instance, initializing the
     * KubeMQClient and QueuesClient. It also tests the connection by pinging
     * the KubeMQ server.
     */
    public Send_ReceiveMessageUsingStreamExample() {
        // Create QueuesClient using the builder pattern
        queuesClient = QueuesClient.builder()
                  .address(address)
                .clientId(clientId)
                .build();

        // Ping to test connection is successful
        ServerInfo pingResult = queuesClient.ping();
        System.out.println("Ping Response: " + pingResult.toString());
    }
    
    
    /**
     * Initiates the queue messages stream to send messages and receive messages send result from server.
     */
    public void sendQueueMessage() {
        System.out.println("\n============================== Send Queue Message Started =============================\n");
        try {
            Map<String, String> tags = new HashMap<>();
            tags.put("tag1", "kubemq");
            tags.put("tag2", "kubemq2");
            QueueMessage message = QueueMessage.builder()
                    .body("Sending data in queue message stream".getBytes())
                    .channel(channelName)
                    .metadata("Sample metadata")
                    .id(UUID.randomUUID().toString())
                    // Optional parameters
                    .tags(tags)
                    .delayInSeconds(1)
                    .expirationInSeconds(3600)
                    .attemptsBeforeDeadLetterQueue(3)
                    .deadLetterQueue("dlq-" + channelName)
                    .build();

            QueueSendResult sendResult = queuesClient.sendQueuesMessage(message);

            System.out.println("Message sent Response:");
            System.out.println("ID: " + sendResult.getId());
            System.out.println("Sent At: " + sendResult.getSentAt());
            System.out.println("Expired At: " + sendResult.getExpiredAt());
            System.out.println("Delayed To: " + sendResult.getDelayedTo());
            System.out.println("Is Error: " + sendResult.isError());
            if (sendResult.isError()) {
                System.out.println("Error: " + sendResult.getError());
            }
        } catch (RuntimeException e) {
            System.err.println("Failed to send queue message: " + e.getMessage());
        }

    }



    public void receiveQueuesMessages() {
        try {
            QueuesPollRequest queuesPollRequest = QueuesPollRequest.builder()
                    .channel(channelName)
                    .pollMaxMessages(1)
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
                    System.out.println("Message ID: " + msg.getId());
                    System.out.println("Message Body: " + new String(msg.getBody()));

                    // Message handling options:

                    // 1. Acknowledge message (mark as processed)
                    msg.ack();

                    // 2. Reject message (won't be requeued)
                    // msg.reject();

                    // 3. Requeue message (send back to queue)
                    // msg.reQueue(channelName);
                });
            }

        } catch (RuntimeException e) {
            System.err.println("Failed to receive queue messages: " + e.getMessage());
        }
    }



    public void receiveAndBulkHandleQueueMessages(String channelName) {
        System.out.println("\n============================== Receive and Bulk Handle Queue Messages =============================\n");
        try {
            QueuesPollRequest queuesPollRequest = QueuesPollRequest.builder()
                    .channel(channelName)
                    .pollMaxMessages(10)  // Increased to receive multiple messages
                    .pollWaitTimeoutInSeconds(15)
                    .build();

            QueuesPollResponse pollResponse = queuesClient.receiveQueuesMessages(queuesPollRequest);

            System.out.println("Received Message Response:");
            System.out.println("RefRequestId: " + pollResponse.getRefRequestId());
            System.out.println("ReceiverClientId: " + pollResponse.getReceiverClientId());
            System.out.println("TransactionId: " + pollResponse.getTransactionId());

            if (pollResponse.isError()) {
                System.out.println("Error: " + pollResponse.getError());
            } else {
                int messageCount = pollResponse.getMessages().size();
                System.out.println("Received " + messageCount + " messages.");

                // Print details of received messages
                pollResponse.getMessages().forEach(msg -> {
                    System.out.println("Message ID: " + msg.getId());
                    System.out.println("Message Body: " + new String(msg.getBody()));
                });

                // Acknowledge all messages
                pollResponse.ackAll();
                System.out.println("Acknowledged all messages.");

                // Reject all messages
                // pollResponse.rejectAll();
                // System.out.println("Rejected all messages.");

                // Requeue all messages
                // pollResponse.reQueueAll(channelName);
                // System.out.println("Requeued all messages.");
            }

        } catch (RuntimeException e) {
            System.err.println("Failed to receive or handle queue messages: " + e.getMessage());
        }
    }

    /**
     * Main method to execute the example. This method starts the message stream
     * and keeps the main thread running to handle responses.
     *
     * @param args command line arguments
     * @throws InterruptedException if the main thread is interrupted
     */
    public static void main(String[] args) throws InterruptedException {
        Send_ReceiveMessageUsingStreamExample example = new Send_ReceiveMessageUsingStreamExample();
        System.out.println("Starting to send messages & Receive message from queue stream: " + new java.util.Date());
        example.sendQueueMessage();
        Thread.sleep(1000);
        example.receiveQueuesMessages();

        // Keep the main thread running to handle responses
        CountDownLatch latch = new CountDownLatch(1);
        latch.await();  // This will keep the main thread alive
    }
}
