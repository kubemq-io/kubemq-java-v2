package io.kubemq.example.queues;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueuesClient;

/**
 * Example class demonstrating the use of QueuesClient to delete a queue channel using KubeMQ.
 * This class covers operations such as initializing the KubeMQClient,
 * and deleting a specified queue channel.
 */
public class DeleteQueuesChannelExample {

    private final KubeMQClient kubeMQClient;
    private final QueuesClient queuesClient;
    private final String queueChannelName = "mytest-channel";
    private final String address = "localhost:50000";
    private final String clientId = "kubeMQClientId";

    /**
     * Constructs a DeleteQueuesChannelExample instance, initializing the {@link KubeMQClient} and {@link QueuesClient}.
     */
    public DeleteQueuesChannelExample() {
        // Setup KubeMQ client
        kubeMQClient = KubeMQClient.builder()
                .address(address)
                .clientId(clientId)
                .build();

        // Ping to test connection is successful
        ServerInfo pingResult = kubeMQClient.ping();
        System.out.println("Ping Response: " + pingResult.toString());

        // Create QueuesClient using the builder pattern
        queuesClient = QueuesClient.builder()
                .kubeMQClient(kubeMQClient)
                .build();
    }

    /**
     * Deletes a queue channel using the {@link QueuesClient}.
     * This method attempts to delete the specified queue channel and logs the result.
     */
    public void deleteQueueChannel() {
        try {
            boolean isChannelDeleted = queuesClient.deleteQueuesChannel(queueChannelName);
            System.out.println("QueueChannel deleted: " + isChannelDeleted);
        } catch (RuntimeException e) {
            System.err.println("Failed to delete queue channel: " + e.getMessage());
        }
    }

    /**
     * Main method to demonstrate the usage of {@link DeleteQueuesChannelExample}.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        DeleteQueuesChannelExample example = new DeleteQueuesChannelExample();
        example.deleteQueueChannel();
    }
}
