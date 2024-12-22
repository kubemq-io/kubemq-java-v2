package io.kubemq.example.queues;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.queues.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class ReceiveMessageDLQ {

    private final QueuesClient queuesClient;
    private final String channelName = "process_queue";
    private final String address = "localhost:50000";
    private final String clientId = "kubeMQClientId";


    public ReceiveMessageDLQ() {
        queuesClient = QueuesClient.builder()
                .address(address)
                .clientId(clientId)
                .logLevel(KubeMQClient.Level.INFO)
                .build();

    }

    /**
     * Sends 20 messages to the queue.
     */
    public void sendQueueMessage() {
        System.out.println("\n============================== Send Queue Messages Started =============================\n");
        try {
            QueueMessage message = QueueMessage.builder()
                    .body(("Message with DLQ").getBytes())  // Unique message body
                    .channel(channelName)
                    .metadata("Sample metadata ")
                    .id(UUID.randomUUID().toString())
                    .attemptsBeforeDeadLetterQueue(10)
                    .deadLetterQueue("dlq-" + channelName)
                    .build();

            QueueSendResult sendResult = queuesClient.sendQueuesMessage(message);
            System.out.println("Message sent Response: " + sendResult);
        } catch (RuntimeException e) {
            System.err.println("Failed to send queue messages: " + e.getMessage());
        }
    }

    /**
     * Receives messages from the queue and processes them in multiple threads.
     */
    public void receiveQueuesMessages() {
        System.out.println("\n============================== Waiting for a message =============================\n");
        QueuesPollRequest queuesPollRequest = QueuesPollRequest.builder()
                .channel(channelName)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(3600)
                .build();
        QueuesPollResponse pollResponse = queuesClient.receiveQueuesMessages(queuesPollRequest);
        if (pollResponse.isError()) {
            System.out.println("Error: " + pollResponse.getError());
        } else {
            if (pollResponse.getMessages().isEmpty()) {
                System.out.println("No messages in the queue");
            }
            pollResponse.getMessages().forEach(msg -> {
                System.out.println("Message ID: " + msg.getId() + "Receive Count: "+msg.getReceiveCount()+ " Rejecting");
                msg.reject();
            });
        }
        System.out.println("Received Message Response ");
//        try {
//            for (int i = 0; i < 10; i++) {
//                System.out.println("Polling count: " + i);
//                QueuesPollRequest queuesPollRequest = QueuesPollRequest.builder()
//                        .channel(channelName)
//                        .pollMaxMessages(1)
//                        .pollWaitTimeoutInSeconds(2)
//                        .build();
//                QueuesPollResponse pollResponse = queuesClient.receiveQueuesMessages(queuesPollRequest);
//                System.out.println("Received Message Response for Polling count: " + i);
//                if (pollResponse.isError()) {
//                    System.out.println("Error: " + pollResponse.getError());
//                    break;
//                } else {
//                    if (pollResponse.getMessages().isEmpty()) {
//                        System.out.println("No messages in the queue");
//
//                    }
////                    pollResponse.getMessages().forEach(msg -> {
////
////                        System.out.println("Message ID: " + msg.getId() + "Receive Count: "+msg.getReceiveCount()+ " Rejecting");
////                        msg.reject();
////                    });
//                }
//            }
//        } catch (RuntimeException e) {
//            System.err.println("Failed to receive queue messages: " + e.getMessage());
//        }
        System.out.println("All Polled message processed successfully");
    }

    public void longPolling() {
        System.out.println("\n============================== Long Polling =============================\n");

        for (int i = 0; i < 100; i++) {
            QueuesPollRequest queuesPollRequest = QueuesPollRequest.builder()
                    .channel(channelName)
                    .pollMaxMessages(1)
                    .pollWaitTimeoutInSeconds(3600)
                    .build();
            QueuesPollResponse pollResponse = queuesClient.receiveQueuesMessages(queuesPollRequest);
            if (pollResponse.isError()) {
                System.out.println("Error: " + pollResponse.getError());
            } else {
                if (pollResponse.getMessages().isEmpty()) {
                    System.out.println("No messages in the queue");

                }
                pollResponse.getMessages().forEach(msg -> {
                    System.out.println("Message ID: " + msg.getId() + "Receive Count: "+msg.getReceiveCount());
                    msg.ack();
                });
            }
        }
        System.out.println("All Polled message processed successfully");
    }


    public static void main(String[] args) throws InterruptedException {
        ReceiveMessageDLQ example = new ReceiveMessageDLQ();  // 10 worker threads
        System.out.println("Starting to send and receive messages: " + new java.util.Date());


//        example.sendQueueMessage();

        // Wait for a moment to ensure messages are sent
//        Thread.sleep(2000);

        // Receive and process messages
        example.longPolling();

        System.exit(0);
    }
}
