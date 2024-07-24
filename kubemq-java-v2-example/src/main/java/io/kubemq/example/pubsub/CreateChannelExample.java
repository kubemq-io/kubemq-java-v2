package io.kubemq.example.pubsub;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

/**
 * Example class demonstrating the use of PubSubClient to send and receive messages using KubeMQ.
 * This class covers operations such as sending and receiving messages, creating and deleting channels,
 * and subscribing to different types of event stores.
 */
public class CreateChannelExample {

    private final KubeMQClient kubeMQClient;
    private final PubSubClient pubSubClient;
    private final String eventChannelName = "mytest-channel";
    private final String eventStoreChannelName = "mytest-channel-eventstore";
    private final String address = "localhost:50000";
    private final String clientId = "kueMQClientId";

    /**
     * Constructs a PubSubClientExample instance, initializing the KubeMQClient and PubSubClient.
     */
    public CreateChannelExample() {
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
     * Creates an events channel using the PubSubClient.
     */
    public void createEventsChannel() {
        try {
            boolean isChannelCreated = pubSubClient.createEventsChannel(eventChannelName);
            System.out.println("EventsChannel created: " + isChannelCreated);
        } catch (RuntimeException e) {
            System.err.println("Failed to create events channel: " + e.getMessage());
        }
    }

    /**
     * Creates an events store channel using the PubSubClient.
     */
    public void createEventsStoreChannel() {
        try {
            boolean isChannelCreated = pubSubClient.createEventsStoreChannel(eventStoreChannelName);
            System.out.println("EventsStoreChannel created: " + isChannelCreated);
        } catch (RuntimeException e) {
            System.err.println("Failed to create events store channel: " + e.getMessage());
        }
    }

   

    /**
     * Main method to demonstrate the usage of PubSubClientExample.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        CreateChannelExample example = new CreateChannelExample();
        example.createEventsChannel();
        example.createEventsStoreChannel();

    }
}
