package io.kubemq.example.queues;

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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Example class demonstrating how to poll the queue messages using from KubeMQ
 * This class initializes the KubeMQClient
 * and QueuesClient, and handles the message polling with visibility.
 */
public class ReceiveMessageWithVisibilityExample {

    private final QueuesClient queuesClient;
    private final String channelName = "visibility_channel";
    private final String address = "localhost:50000";
    private final String clientId = "kubeMQClientId";

    /**
     * Constructs a ReceiveMessageWithVisibilityExample instance, initializing the
     * KubeMQClient and QueuesClient. It also tests the connection by pinging
     * the KubeMQ server.
     */
    public ReceiveMessageWithVisibilityExample() {
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
     * Sends 3 messages to the queue.
     */
    public void sendQueueMessage() {
        System.out.println("\n============================== Send Queue Messages Started =============================\n");
        try {
            for (int i = 0; i < 5; i++) {
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



    public void receiveExampleWithVisibility() {
        System.out.println("\n============================== receiveExampleWithVisibility =============================\n");
       try {
            QueuesPollRequest queuesPollRequest = QueuesPollRequest.builder()
                    .channel(channelName)
                    .pollMaxMessages(1)  // Pull 10 messages at a time
                    .pollWaitTimeoutInSeconds(10)
                    .visibilitySeconds(5)
                    .autoAckMessages(false)
                    .build();

            QueuesPollResponse pollResponse = queuesClient.receiveQueuesMessages(queuesPollRequest);

            System.out.println("Received Message Response:"+" TransactionId: " + pollResponse.getTransactionId());
            System.out.println("RefRequestId: " + pollResponse.getRefRequestId()+" ReceiverClientId: " + pollResponse.getReceiverClientId());

            if (pollResponse.isError()) {
                System.out.println("Error: " + pollResponse.getError());
            } else {
                pollResponse.getMessages().forEach(msg -> {
                    try {
                        System.out.println("Message ID: " + msg.getId()+" Message Body: " + new String(msg.getBody()));
                        Thread.sleep(1000);
                        msg.ack();
                        System.out.println("Acknowledged to message");
                    } catch (InterruptedException ex) {
                        Logger.getLogger(ReceiveMessageWithVisibilityExample.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            }

        } catch (RuntimeException e) {
            System.err.println("Failed to receive queue messages: " + e.getMessage());
        }
    }

    public void receiveExampleWithVisibilityExpired() {
        System.out.println("\n============================== receiveExampleWithVisibilityExpired =============================\n");
        try {
            QueuesPollRequest queuesPollRequest = QueuesPollRequest.builder()
                    .channel(channelName)
                    .pollMaxMessages(1)  // Pull 10 messages at a time
                    .pollWaitTimeoutInSeconds(10)
                    .visibilitySeconds(2)
                    .autoAckMessages(false)
                    .build();

            QueuesPollResponse pollResponse = queuesClient.receiveQueuesMessages(queuesPollRequest);

            System.out.println("Received Message Response:"+" TransactionId: " + pollResponse.getTransactionId());
            System.out.println("RefRequestId: " + pollResponse.getRefRequestId()+" ReceiverClientId: " + pollResponse.getReceiverClientId());

            if (pollResponse.isError()) {
                System.out.println("Error: " + pollResponse.getError());
            } else {
                pollResponse.getMessages().forEach(msg -> {
                    try {
                        System.out.println("Message ID: " + msg.getId()+" Message Body: " + new String(msg.getBody()));
                        Thread.sleep(3000);
                        msg.ack();
                        System.out.println("Acknowledged to message");
                    } catch (InterruptedException ex) {
                        Logger.getLogger(ReceiveMessageWithVisibilityExample.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            }

        } catch (RuntimeException e) {
            System.err.println("Failed to receive queue messages: " + e.getMessage());
        }
    }

    
    public void receiveExampleWithVisibilityExtension() {
        System.out.println("\n============================== receiveExampleWithVisibilityExtension =============================\n");
        try {
            QueuesPollRequest queuesPollRequest = QueuesPollRequest.builder()
                    .channel(channelName)
                    .pollMaxMessages(1)  // Pull 10 messages at a time
                    .pollWaitTimeoutInSeconds(10)
                    .visibilitySeconds(3)
                    .autoAckMessages(false)
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
                    try {
                        System.out.println("Message ID: " + msg.getId()+" Message Body: " + new String(msg.getBody()));
                        Thread.sleep(1000);
                        msg.extendVisibilityTimer(3);
                        Thread.sleep(2000);
                        msg.ack();
                        System.out.println("Acknowledged to message");
                    } catch (InterruptedException ex) {
                        Logger.getLogger(ReceiveMessageWithVisibilityExample.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            }

        } catch (RuntimeException e) {
            System.err.println("Failed to receive queue messages: " + e.getMessage());
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
        ReceiveMessageWithVisibilityExample example = new ReceiveMessageWithVisibilityExample();
        System.out.println("Starting to send messages & Receive message from queue with visibility: " + new java.util.Date());
        example.sendQueueMessage();
        Thread.sleep(1000);
        
        //example.receiveExampleWithVisibility();
        
        // example.receiveExampleWithVisibilityExpired();
        
        example.receiveExampleWithVisibilityExtension();
        
        // Keep the main thread running to handle responses
        CountDownLatch latch = new CountDownLatch(1);
        latch.await();  // This will keep the main thread alive

    }
}
