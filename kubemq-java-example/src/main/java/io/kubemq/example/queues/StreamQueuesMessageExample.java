package io.kubemq.example.queues;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueueMessageWrapper;
import io.kubemq.sdk.queues.QueuesClient;
import kubemq.Kubemq;
import kubemq.Kubemq.StreamQueueMessagesRequest;
import kubemq.Kubemq.StreamQueueMessagesResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * Example class demonstrating how to send and receive queue messages using a bi-directional stream with KubeMQ.
 * This class initializes the KubeMQ client and QueuesClient, and provides methods to stream messages to and from
 * a specified queue channel. The streaming is managed using bi-directional communication where messages are sent
 * and received concurrently.
 */
public class StreamQueuesMessageExample {

    private final KubeMQClient kubeMQClient;
    private final QueuesClient queuesClient;
    private final String channelName = "mytest-channel";
    private final String address = "localhost:50000";
    private final String clientId = "kubeMQClientId";

    /**
     * Constructs a StreamQueuesMessageExample instance, initializing the KubeMQClient and QueuesClient.
     * It also performs a ping operation to test the connection to the KubeMQ server.
     */
    public StreamQueuesMessageExample() {
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
     * Initiates a bi-directional stream to send and receive messages to/from the server.
     * This method sets up a `StreamObserver` to handle incoming messages and any errors,
     * and then starts a separate thread to send messages to the server.
     */
    public void streamQueuesMessages() {
        // Receive messages from the server
        StreamObserver<StreamQueueMessagesResponse> streamQueueMessageResponse = new StreamObserver<StreamQueueMessagesResponse>() {
            @Override
            public void onNext(StreamQueueMessagesResponse result) {
                // Handle the response received from the server
                System.out.println("Received response: " + result);
            }

            @Override
            public void onError(Throwable t) {
                // Handle any errors that occur during communication
                System.err.println("Error during stream: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                // Handle the completion of the stream
                System.out.println("Stream completed.");
            }
        };

        // Create request observer for sending messages
        StreamObserver<StreamQueueMessagesRequest> requestObserver = queuesClient.streamQueuesMessages(streamQueueMessageResponse);
        System.out.println("streamQueuesMessages: " + requestObserver);

        // Send messages using a separate thread to simulate concurrent message sending
        new Thread(() -> ackQueueMessages(requestObserver)).start();
    }

    /**
     * Sends a series of queue messages to the server.
     * This method constructs and sends messages in a loop, using a `StreamObserver` to send each message.
     * Each message includes a unique ID, channel name, message body, metadata, and tags.
     *
     * @param requestObserver the `StreamObserver` used to send queue messages
     */
    private void ackQueueMessages(StreamObserver<StreamQueueMessagesRequest> requestObserver) {
        for (int i = 0; i < 10; i++) {
            String body = "Sending data in queue messages stream";
            Map<String, String> tags = new HashMap<>();
            tags.put("tag1", "kubemq");
            tags.put("tag2", "kubemq2");

            QueueMessageWrapper messageWrapper = QueueMessageWrapper.builder()
                    .id(UUID.randomUUID().toString())
                    .body(body.getBytes())
                    .channel(channelName)
                    .metadata("metadata")
                    .tags(tags)
                    .expirationInSeconds(60 * 10) // 10 minutes
                    .build();

            StreamQueueMessagesRequest message = StreamQueueMessagesRequest.newBuilder()
                    .setRequestID(UUID.randomUUID().toString())
                    .setChannel(channelName)
                    .setClientID(clientId)
                    .setRefSequence(i)
                    .setModifiedMessage(messageWrapper.encodeMessage(clientId))
                    .setStreamRequestTypeData(Kubemq.StreamRequestType.ResendMessage)
                    .setStreamRequestTypeDataValue(Kubemq.StreamRequestType.ResendMessage_VALUE)
                    .build();

            requestObserver.onNext(message);
            System.out.println("Message sent to queue message stream: " + i);

            // Optional sleep to simulate delay between messages
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        System.out.println("All messages sent to queue messages stream: " + new java.util.Date());
    }

    /**
     * Main method to execute the example.
     * This method creates an instance of `StreamQueuesMessageExample`, starts streaming messages,
     * and keeps the main thread running to handle incoming responses.
     *
     * @param args command line arguments
     * @throws InterruptedException if the main thread is interrupted
     */
    public static void main(String[] args) throws InterruptedException {
        StreamQueuesMessageExample example = new StreamQueuesMessageExample();
        System.out.println("Starting to send messages in queue stream: " + new java.util.Date());
        example.streamQueuesMessages();

        // Keep the main thread running to handle responses
        CountDownLatch latch = new CountDownLatch(1);
        latch.await();  // This will keep the main thread alive
    }
}
