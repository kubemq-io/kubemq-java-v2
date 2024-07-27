package io.kubemq.example.queues;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueueMessageAcknowledgment;
import io.kubemq.sdk.queues.QueueMessagesReceived;
import io.kubemq.sdk.queues.QueuesClient;
import java.util.UUID;

/**
 * Example class demonstrating how to receive and acknowledge queue messages using KubeMQ.
 * This class initializes the KubeMQClient and QueuesClient and provides methods to receive
 * messages from a specified queue channel and acknowledge all messages in that channel.
 */
public class ReceiveQueuesMessageExample {

    private final KubeMQClient kubeMQClient;
    private final QueuesClient queuesClient;
    private final String queueChannelName = "mytest-channel";
    private final String address = "localhost:50000";
    private final String clientId = "kubeMQClientId";

    /**
     * Constructs a ReceiveQueuesMessageExample instance, initializing the KubeMQClient and QueuesClient.
     * It also tests the connection to the KubeMQ server by pinging it.
     */
    public ReceiveQueuesMessageExample() {
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
     * Receives messages from the specified queue channel.
     * This method fetches up to a specified number of messages from the queue channel within a given timeout.
     * 
     * @throws RuntimeException if an error occurs during message retrieval
     */
    public void receiveMessages() {
        try {
            int maxMessages = 10; // Maximum number of messages to retrieve
            int waitTimeoutInSeconds = 5; // Timeout for waiting for messages
            boolean peak = false; // Flag to indicate whether to peek at the messages or not

            QueueMessagesReceived receivedMessages = queuesClient.receiveQueuesMessages(
                    UUID.randomUUID().toString(), queueChannelName, maxMessages, waitTimeoutInSeconds, peak);
            System.out.println("Received messages: " + receivedMessages);
        } catch (RuntimeException e) {
            System.err.println("Failed to receive messages: " + e.getMessage());
        }
    }

    /**
     * Acknowledges all messages in the specified queue channel.
     * This method confirms the processing of messages, allowing the server to remove them from the queue.
     * 
     * @throws RuntimeException if an error occurs during the acknowledgment process
     */
    public void acknowledgeAllMessages() {
        try {
            String requestId = UUID.randomUUID().toString(); // Unique request ID for acknowledgment
            int waitTimeInSeconds = 5; // Timeout for waiting for acknowledgment response

            QueueMessageAcknowledgment acknowledgment = queuesClient.ackAllQueueMessage(requestId, queueChannelName, waitTimeInSeconds);
            System.out.println("Acknowledgment result: " + acknowledgment);
        } catch (RuntimeException e) {
            System.err.println("Failed to acknowledge all messages: " + e.getMessage());
        }
    }

    /**
     * Main method to demonstrate the usage of receiving and acknowledging queue messages.
     * It creates an instance of ReceiveQueuesMessageExample and calls methods to receive and acknowledge messages.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        ReceiveQueuesMessageExample example = new ReceiveQueuesMessageExample();
        example.receiveMessages();
        example.acknowledgeAllMessages();
    }
}
