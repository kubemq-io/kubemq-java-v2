package io.kubemq.example.queues;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueuesClient;

/**
 * Example class demonstrating how to create a queue channel using KubeMQ.
 * This class covers the initialization of KubeMQClient and QueuesClient,
 * and the creation of a queue channel.
 */
public class CreateQueuesChannelExample {

    private final KubeMQClient kubeMQClient;
    private final QueuesClient queuesClient;
    private final String queueChannelName = "mytest-channel";
    private final String address = "localhost:50000";
    private final String clientId = "kubeMQClientId";

    /**
     * Constructs a CreateQueuesChannelExample instance, initializing the {@link KubeMQClient} and {@link QueuesClient}.
     */
    public CreateQueuesChannelExample() {
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
     * Creates a queue channel using the {@link QueuesClient}.
     * This method checks if the channel creation is successful and logs the result.
     */
    public void createQueueChannel() {
        try {
            boolean isChannelCreated = queuesClient.createQueuesChannel(queueChannelName);
            System.out.println("QueueChannel created: " + isChannelCreated);
        } catch (RuntimeException e) {
            System.err.println("Failed to create queue channel: " + e.getMessage());
        }
    }

    /**
     * Main method to demonstrate the usage of {@link CreateQueuesChannelExample}.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        CreateQueuesChannelExample example = new CreateQueuesChannelExample();
        example.createQueueChannel();
    }
}
