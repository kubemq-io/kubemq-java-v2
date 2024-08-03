package io.kubemq.example.queues;

import com.google.protobuf.ByteString;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.*;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * Example class demonstrating how to poll the queue messages using from KubeMQ
 * This class initializes the KubeMQClient
 * and QueuesClient, and handles the message polling, ack, requeue, reject.
 */
public class WaitingPullExample {

    private final QueuesClient queuesClient;
    private final String channelName = "mytest-channel";
    private final String address = "localhost:50000";
    private final String clientId = "kubeMQClientId";

    /**
     * Constructs a QueuesDownstreamMessageExample instance, initializing the
     * KubeMQClient and QueuesClient. It also tests the connection by pinging
     * the KubeMQ server.
     */
    public WaitingPullExample() {
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
         System.out.println("\n============================== sendMessage Started =============================\n");
            // Send message in Stream 
            QueueMessage message = QueueMessage.builder()
                    .body(("Sending data in queue message").getBytes())
                    .channel(channelName)
                    .metadata("metadata")
                    .id(UUID.randomUUID().toString())
                    .build();
            QueueSendResult sendResult = queuesClient.sendQueuesMessage(message);

            System.out.println("Message sent Response: " + sendResult);

    }
    


    public void getWaitingMessages() {
        System.out.println("\n============================== getWaitingMessages Started =============================\n");
        try {
            String channelName = "mytest-channel";
            int maxNumberOfMessages = 1;
            int waitTimeSeconds = 10;

            QueueMessagesWaiting rcvMessages = queuesClient.waiting(channelName, maxNumberOfMessages, waitTimeSeconds);

            if (rcvMessages.isError()) {
                System.out.println("Error occurred: " + rcvMessages.getError());
                return;
            }

            System.out.println("Waiting Messages Count: " + rcvMessages.getMessages().size());

            for (QueueMessageWaitingPulled msg : rcvMessages.getMessages()) {
                System.out.println("Message ID: " + msg.getId());
                System.out.println("Channel: " + msg.getChannel());
                System.out.println("Metadata: " + msg.getMetadata());
                System.out.println("Body: " + new String(msg.getBody()));
                System.out.println("From Client ID: " + msg.getFromClientId());
                System.out.println("Tags: " + msg.getTags());
                System.out.println("Timestamp: " + msg.getTimestamp());
                System.out.println("Sequence: " + msg.getSequence());
                System.out.println("Receive Count: " + msg.getReceiveCount());
                System.out.println("Is Re-routed: " + msg.isReRouted());
                System.out.println("Re-route From Queue: " + msg.getReRouteFromQueue());
                System.out.println("Expired At: " + msg.getExpiredAt());
                System.out.println("Delayed To: " + msg.getDelayedTo());
                System.out.println("Receiver Client ID: " + msg.getReceiverClientId());
                System.out.println("--------------------");
            }
        } catch (RuntimeException e) {
            System.err.println("Failed to get waiting messages: " + e.getMessage());
        }

    }


    public void getPullMessages() {
        System.out.println("\n============================== getPullMessages Started =============================\n");
        QueueMessagesPulled rcvMessages = queuesClient.pull(channelName, 1, 10);
        System.out.println("Pulled Messages Count: "+rcvMessages.getMessages().size());
        for (QueueMessageWaitingPulled msg : rcvMessages.getMessages()) {
            System.out.println("Message  Id: " + msg.getId());
            System.out.println("Message Body: "+ByteString.copyFrom(msg.getBody()).toStringUtf8());
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
        WaitingPullExample example = new WaitingPullExample();
        example.sendQueueMessage();
        example.getWaitingMessages();
        example.getPullMessages();
        // Keep the main thread running to handle responses
        CountDownLatch latch = new CountDownLatch(1);
        latch.await();  // This will keep the main thread alive
    }
}
