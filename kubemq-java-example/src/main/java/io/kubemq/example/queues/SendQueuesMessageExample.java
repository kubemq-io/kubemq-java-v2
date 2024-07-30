package io.kubemq.example.queues;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueueMessage;
import io.kubemq.sdk.queues.QueueMessagesBatchSendResult;
import io.kubemq.sdk.queues.QueueSendResult;
import io.kubemq.sdk.queues.QueuesClient;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Example class demonstrating how to send messages to a KubeMQ queue.
 * This class initializes the KubeMQ client and the QueuesClient, and provides methods to send
 * both single and batch messages to a specified queue channel.
 */
public class SendQueuesMessageExample {

    private final KubeMQClient kubeMQClient;
    private final QueuesClient queuesClient;
    private final String queueChannelName = "mytest-channel";
    private final String address = "localhost:50000";
    private final String clientId = "kubeMQClientId";

    /**
     * Constructs a SendQueuesMessageExample instance, initializing the KubeMQClient and QueuesClient.
     * It also tests the connection to the KubeMQ server by pinging it.
     */
    public SendQueuesMessageExample() {
        // Setup KubeMQ client
        kubeMQClient = KubeMQClient.builder()
                .address(address)
                .clientId(clientId)
                .build();

        // Ping to test connection is successful
        ServerInfo pingResult = kubeMQClient.ping();
        System.out.println("Ping Response: " + pingResult);

        // Create QueuesClient using the builder pattern
        queuesClient = QueuesClient.builder()
                .kubeMQClient(kubeMQClient)
                .build();
    }

    /**
     * Sends a single message to the specified queue channel.
     * This method constructs a `QueueMessageWrapper` with a unique ID, message body, metadata, tags,
     * and an expiration time. It then sends the message using the QueuesClient and prints the result.
     * 
     * @throws RuntimeException if an error occurs during message sending
     */
    public void sendSingleMessage() {
        try {
            Map<String, String> tags = new HashMap<>();
            tags.put("tag1", "kubemq");
            tags.put("tag2", "kubemq2");

            QueueMessage message = QueueMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .body("Hello KubeMQ!".getBytes())
                    .channel(queueChannelName)
                    .metadata("metadata")
                    .tags(tags)
                    .expirationInSeconds(60 * 10) // 10 minutes
                    .build();

            QueueSendResult sendResult = queuesClient.sendQueuesMessage(message);
            System.out.println("Message sent result: " + sendResult);
        } catch (RuntimeException e) {
            System.err.println("Failed to send message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sends a batch of messages to the specified queue channel.
     * This method constructs multiple `QueueMessageWrapper` instances, groups them into a list,
     * and sends them in a batch using the QueuesClient. It prints the result of the batch send operation.
     * 
     * @throws RuntimeException if an error occurs during batch message sending
     */
    public void sendBatchMessages() {
        try {
            Map<String, String> tags = new HashMap<>();
            tags.put("tag1", "kubemq");
            tags.put("tag2", "kubemq2");

            QueueMessage message1 = QueueMessage.builder()
                    .body("Message 1".getBytes())
                    .channel(queueChannelName)
                    .id(UUID.randomUUID().toString())
                    .tags(tags)
                    .build();

            QueueMessage message2 = QueueMessage.builder()
                    .body("Message 2".getBytes())
                    .channel(queueChannelName)
                    .id(UUID.randomUUID().toString())
                    .build();

            List<QueueMessage> messages = Arrays.asList(message1, message2);
            String batchId = UUID.randomUUID().toString();
            QueueMessagesBatchSendResult batchSendResult = queuesClient.sendQueuesMessageInBatch(messages, batchId);
            System.out.println("Batch messages sent result: " + batchSendResult);
        } catch (RuntimeException e) {
            System.err.println("Failed to send batch messages: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Main method to demonstrate the usage of sending single and batch messages to a KubeMQ queue.
     * It creates an instance of SendQueuesMessageExample and calls methods to send both single and batch messages.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        SendQueuesMessageExample example = new SendQueuesMessageExample();
        example.sendSingleMessage();
        example.sendBatchMessages();
    }
}
