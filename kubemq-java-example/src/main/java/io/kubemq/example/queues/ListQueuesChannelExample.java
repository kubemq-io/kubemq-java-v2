package io.kubemq.example.queues;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueuesChannel;
import io.kubemq.sdk.queues.QueuesClient;
import java.util.List;

/**
 * Example class demonstrating the use of QueuesClient to list queue channels using KubeMQ.
 * This class covers operations such as initializing the KubeMQClient, creating the QueuesClient,
 * and listing all available queue channels.
 */
public class ListQueuesChannelExample {

    private final QueuesClient queuesClient;
    private final String address = "localhost:50000";
    private final String clientId = "kubeMQClientId";

    /**
     * Constructs a ListQueuesChannelExample instance, initializing the {@link KubeMQClient} and {@link QueuesClient}.
     */
    public ListQueuesChannelExample() {
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
     * Lists all queue channels using the {@link QueuesClient} and prints their attributes.
     * This method retrieves all available queue channels and logs their details such as name, type,
     * last activity, active status, and incoming and outgoing statistics.
     */
    public void listQueueChannels() {
        try {
            List<QueuesChannel> channels = queuesClient.listQueuesChannels("");
            for (QueuesChannel channel : channels) {
                System.out.println("Channel Name: " + channel.getName());
                System.out.println("Type: " + channel.getType());
                System.out.println("Last Activity: " + channel.getLastActivity());
                System.out.println("Is Active: " + channel.getIsActive());
                System.out.println("Incoming Stats: " + channel.getIncoming());
                System.out.println("Outgoing Stats: " + channel.getOutgoing());
                System.out.println();
            }
        } catch (RuntimeException e) {
            System.err.println("Failed to list queue channels: " + e.getMessage());
        }
    }

    /**
     * Main method to demonstrate the usage of {@link ListQueuesChannelExample}.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        ListQueuesChannelExample example = new ListQueuesChannelExample();
        example.listQueueChannels();
    }
}
