package io.kubemq.example.queues;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.queues.QueuesDetailInfo;

/**
 * Example class demonstrating how to use the {@link QueuesClient} to get detailed information
 * about queues from the KubeMQ server. This class initializes the necessary clients and performs
 * the operation to fetch queue details.
 */
public class GetQueuesInfoExample {

    private final QueuesClient queuesClient;
    private final String channelName = "mytest-channel";
    private final String address = "localhost:50000";
    private final String clientId = "kubeMQClientId";

    /**
     * Constructs a GetQueuesInfoExample instance, initializing the {@link KubeMQClient} and {@link QueuesClient}.
     */
    public GetQueuesInfoExample() {
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
     * Fetches and prints detailed information of the specified queue from the KubeMQ server.
     */
    public void getQueueDetails() {
        try {
            // Get the queue information
            QueuesDetailInfo queuesDetailInfo = queuesClient.getQueuesInfo(channelName);

            // Print the queue information
            System.out.println("Queue Information:");
            System.out.println("RefRequestID: " + queuesDetailInfo.getRefRequestID());
            System.out.println("TotalQueue: " + queuesDetailInfo.getTotalQueue());
            System.out.println("Sent: " + queuesDetailInfo.getSent());
            System.out.println("Delivered: " + queuesDetailInfo.getDelivered());
            System.out.println("Waiting: " + queuesDetailInfo.getWaiting());

            queuesDetailInfo.getQueues().forEach(queueInfo -> {
                System.out.println("Queue Name: " + queueInfo.getName());
                System.out.println("Messages: " + queueInfo.getMessages());
                System.out.println("Bytes: " + queueInfo.getBytes());
                System.out.println("FirstSequence: " + queueInfo.getFirstSequence());
                System.out.println("LastSequence: " + queueInfo.getLastSequence());
                System.out.println("Sent: " + queueInfo.getSent());
                System.out.println("Delivered: " + queueInfo.getDelivered());
                System.out.println("Waiting: " + queueInfo.getWaiting());
                System.out.println("Subscribers: " + queueInfo.getSubscribers());
            });
        } catch (Exception e) {
            System.out.println("Error while getting queue information");
            e.printStackTrace();
        }
    }

    /**
     * Main method to demonstrate the usage of {@link GetQueuesInfoExample}.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        GetQueuesInfoExample example = new GetQueuesInfoExample();
        example.getQueueDetails();
    }
}
