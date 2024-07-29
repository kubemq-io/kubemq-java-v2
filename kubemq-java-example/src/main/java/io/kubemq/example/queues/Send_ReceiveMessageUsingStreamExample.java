package io.kubemq.example.queues;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueueMessageWrapper;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.queues.QueuesPollRequest;
import io.kubemq.sdk.queues.QueuesPollResponse;
import io.kubemq.sdk.queues.UpstreamResponse;
import io.kubemq.sdk.queues.UpstreamSender;
import java.util.UUID;
import kubemq.Kubemq.QueuesDownstreamRequest;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import kubemq.Kubemq;

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
    public void sendMessage() {
         System.out.println("\n============================== sendMessage Started =============================\n");
       // Define the onReceiveMessageSendCallback handler to receive the message result
        Consumer<UpstreamResponse> onReceiveMessageSendCallback = (response) -> {
            System.out.println("Received Message send result");
            
            if(response.isError()){
            System.out.println("Error: "+response.getError());
            }else{
            
            System.out.println("RefRequestId: "+response.getRefRequestId());
            response.getResults().forEach(  qsr -> {
              System.out.println("Message Send Result Id: "+ qsr.getId());
                System.out.println("Sent At: "+qsr.getSentAt());
            });
            }
        };

        // Define the onErrorCallback
        Consumer<String> onErrorCallback = (errorMsg) -> {
            System.err.println("Error: " + errorMsg);
        };

        // Create an UpstreamSender to setup stream to send message
        UpstreamSender upstreamSender = UpstreamSender.builder()
                .onReceiveMessageSendCallback(onReceiveMessageSendCallback)
                .onErrorCallback(onErrorCallback)
                .build();

        StreamObserver<Kubemq.QueuesUpstreamRequest> requestObserver = queuesClient.sendMessageQueuesUpStream(upstreamSender);

        // Send message in Stream 
        QueueMessageWrapper message1 = QueueMessageWrapper.builder()
                    .body("Sending data in queue messages 1 stream".getBytes())
                    .channel(channelName)
                    .metadata("metadata")
                    .id(UUID.randomUUID().toString())
                    .build();
        
          QueueMessageWrapper message2 = QueueMessageWrapper.builder()
                    .body("Sending data in queue messages 12 stream".getBytes())
                    .channel(channelName)
                    .metadata("metadata")
                    .id(UUID.randomUUID().toString())
                    .build();
          
          requestObserver.onNext(message1.encode(clientId));
          requestObserver.onNext(message2.encode(clientId));
         System.out.println("Two Message sent to queue Up stream ");
        
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
            System.out.println("\n\n");
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
        example.sendMessage();
        example.receiveQueuesMessages();

        // Keep the main thread running to handle responses
        CountDownLatch latch = new CountDownLatch(1);
        latch.await();  // This will keep the main thread alive
    }
}
