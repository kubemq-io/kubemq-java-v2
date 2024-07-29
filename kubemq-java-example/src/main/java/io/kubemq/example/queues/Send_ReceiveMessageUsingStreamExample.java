package io.kubemq.example.queues;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueueMessageWrapper;
import io.kubemq.sdk.queues.QueueSendResult;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.queues.QueuesPollRequest;
import io.kubemq.sdk.queues.QueuesPollResponse;
import java.util.UUID;
import kubemq.Kubemq.QueuesDownstreamRequest;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Example class demonstrating how to poll the queue messages using a
 * bi-directional stream with KubeMQ. This class initializes the KubeMQClient
 * and QueuesClient, and handles the message polling, ack, requeue, reject.
 */
public class Send_ReceiveMessageUsingStreamExample {

    private final KubeMQClient kubeMQClient;
    private final QueuesClient queuesClient;
    private final String channelName = "mytest-channel";
    private final String address = "localhost:50000";
    private final String clientId = "kubeMQClientId";

    private StreamObserver<QueuesDownstreamRequest> responseHandler;

    /**
     * Constructs a QueuesDownstreamMessageExample instance, initializing the
     * KubeMQClient and QueuesClient. It also tests the connection by pinging
     * the KubeMQ server.
     */
    public Send_ReceiveMessageUsingStreamExample() {
        // Setup KubeMQ client
        kubeMQClient = KubeMQClient.builder()
                .address(address)
                .clientId(clientId)
                .keepAlive(true)
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
     * Initiates the queue messages stream to send messages and receive messages send result from server.
     */
    public void sendQueueMessage() {
         System.out.println("\n============================== sendMessage Started =============================\n");
            // Send message in Stream 
            QueueMessageWrapper message = QueueMessageWrapper.builder()
                    .body(("Sending data in queue message stream").getBytes())
                    .channel(channelName)
                    .metadata("metadata")
                    .id(UUID.randomUUID().toString())
                    .build();
            QueueSendResult sendResult = queuesClient.sendQueuesMessageUpStream(message);

            System.out.println("Message sent Response: " + sendResult);

    }
    


    public void receiveQueuesMessages() {
        System.out.println("\n============================== receiveQueuesMessages =============================\n");

        // Define the onReceiveMessageCallback handler to receive the message from queue
        Consumer<QueuesPollResponse> onReceiveMessageCallback = (response) -> {
            System.out.println("Received Message: {}" + response);

            System.out.println("RefRequestId: " + response.getRefRequestId());
            System.out.println("ReceiverClientId: " + response.getReceiverClientId());
            System.out.println("TransactionId: " + response.getTransactionId());
            response.getMessages().forEach(msg -> {
                System.out.println("Message  Id: " + msg.getId());
                System.out.println("Message Body: "+ByteString.copyFrom(msg.getBody()).toStringUtf8());
               // Acknowledge message
               msg.ack();
               
               // *** Reject message
              // msg.reject();
              
               // *** ReQueue message
              // msg.reQueue(channelName);
            });
        };

        // Define the onErrorCallback
        Consumer<String> onErrorCallback = (errorMsg) -> {
            System.err.println("Error: " + errorMsg);
        };

        QueuesPollRequest queuesPollRequest = QueuesPollRequest.builder()
                .channel(channelName)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(10)
                .onReceiveMessageCallback(onReceiveMessageCallback)
                .onErrorCallback(onErrorCallback)
                .build();

        queuesClient.receiveQueuesMessagesDownStream(queuesPollRequest);

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
        example.receiveQueuesMessages();

        // Keep the main thread running to handle responses
        CountDownLatch latch = new CountDownLatch(1);
        latch.await();  // This will keep the main thread alive
    }
}
