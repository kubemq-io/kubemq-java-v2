package io.kubemq.example.pubsub;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

/**
 * Example class demonstrating the use of PubSubClient to send and receive messages using KubeMQ.
 * This class covers operations deleting channels
 */
public class DeleteChannelExample {

    private final KubeMQClient kubeMQClient;
    private final PubSubClient pubSubClient;
    private final String eventChannelName = "mytest-channel";
    private final String eventStoreChannelName = "mytest-channel-eventstore";
    private final String address = "localhost:50000";
    private final String clientId = "kueMQClientId";

    /**
     * Constructs a PubSubClientExample instance, initializing the KubeMQClient and PubSubClient.
     */
    public DeleteChannelExample() {
        // Setup KubeMQ client
        kubeMQClient = KubeMQClient.builder()
                .address(address)
                .clientId(clientId)
                .build();
        // Ping to test Connection is succesffull
        ServerInfo pingResult = kubeMQClient.ping();
        System.out.println("Ping Response: "+pingResult.toString());

        // Create PubSubClient using the builder pattern
        pubSubClient = PubSubClient.builder()
                .kubeMQClient(kubeMQClient)
                .build();
    }

    /**
     * Deletes an events channel with the specified name.
     */
    public void deleteEventsChannel() {
        try {
            boolean isChannelDeleted = pubSubClient.deleteEventsChannel(eventChannelName);
            System.out.println("Events Channel deleted: " + isChannelDeleted);
        } catch (RuntimeException e) {
            System.err.println("Failed to delete events channel: " + e.getMessage());
        }
    }

    /**
     * Deletes an events store channel with the specified name.
     */
    public void deleteEventsStoreChannel() {
        try {
            boolean isChannelDeleted = pubSubClient.deleteEventsStoreChannel(eventStoreChannelName);
            System.out.println("Events store Channel deleted: " + isChannelDeleted);
        } catch (RuntimeException e) {
            System.err.println("Failed to delete events store channel: " + e.getMessage());
        }
    }

    /**
     * Closes the KubeMQ client connection.
     */
    public void shutdown() {
        try {
            kubeMQClient.close();
        } catch (RuntimeException e) {
            System.err.println("Failed to close KubeMQ client: " + e.getMessage());
        }
    }

    /**
     * Main method to demonstrate the usage of PubSubClientExample.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        DeleteChannelExample example = new DeleteChannelExample();
        example.deleteEventsChannel();
        example.deleteEventsStoreChannel();
        example.shutdown();
    }
}
