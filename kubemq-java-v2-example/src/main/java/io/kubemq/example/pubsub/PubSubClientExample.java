package io.kubemq.example.pubsub;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Example class demonstrating the use of PubSubClient to send and receive messages using KubeMQ.
 * This class covers operations such as sending and receiving messages, creating and deleting channels,
 * and subscribing to different types of event stores.
 */
public class PubSubClientExample {

    private final KubeMQClient kubeMQClient;
    private final PubSubClient pubSubClient;
    private final String eventChannelName = "mytest-channel";
    private final String eventStoreChannelName = "mytest-channel-eventstore";
    private final String address = "localhost:50000";
    private final String clientId = "kueMQClientId";

    /**
     * Constructs a PubSubClientExample instance, initializing the KubeMQClient and PubSubClient.
     */
    public PubSubClientExample() {
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
     * Subscribes to events from the specified channel and processes received events.
     */
    public void subscribeToEvents() {
        try {
            // Consumer for handling received events
            Consumer<EventMessageReceived> onReceiveEventCallback = event -> {
                System.out.println("Received event:");
                System.out.println("ID: " + event.getId());
                System.out.println("Channel: " + event.getChannel());
                System.out.println("Metadata: " + event.getMetadata());
                System.out.println("Body: " + new String(event.getBody()));
                System.out.println("Tags: " + event.getTags());
            };

            // Consumer for handling errors
            Consumer<String> onErrorCallback = error -> {
                System.err.println("Error Received: " + error);
            };

            EventsSubscription subscription = EventsSubscription.builder()
                    .channel(eventChannelName)
                    .group("All IT Team")
                    .onReceiveEventCallback(onReceiveEventCallback)
                    .onErrorCallback(onErrorCallback)
                    .build();

            pubSubClient.subscribeToEvents(subscription);
            System.out.println("Events Subscribed");
        } catch (RuntimeException e) {
            System.err.println("Failed to subscribe to events: " + e.getMessage());
        }
    }

    /**
     * Subscribes to events store messages from the specified channel with a specific configuration.
     */
    public void subscribeToEventsStore() {
        try {
            // Consumer for handling received event store messages
            Consumer<EventStoreMessageReceived> onReceiveEventCallback = event -> {
                System.out.println("Received event store:");
                System.out.println("ID: " + event.getId());
                System.out.println("Channel: " + event.getChannel());
                System.out.println("Metadata: " + event.getMetadata());
                System.out.println("Body: " + new String(event.getBody()));
                System.out.println("Tags: " + event.getTags());
            };

            // Consumer for handling errors
            Consumer<String> onErrorCallback = error -> {
                System.err.println("Error Received: " + error);
            };

            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel(eventChannelName)
                    .group("All IT Team")
                    .eventsStoreType(EventsStoreType.StartNewOnly)
                    .onReceiveEventCallback(onReceiveEventCallback)
                    .onErrorCallback(onErrorCallback)
                    .build();

            pubSubClient.subscribeToEventsStore(subscription);
            System.out.println("EventsStore Subscribed");
        } catch (RuntimeException e) {
            System.err.println("Failed to subscribe to events store: " + e.getMessage());
        }
    }


    /**
     * Sends an event message to the configured events channel.
     */
    public void sendEventMessage() {
        try {
            String data = "Any data can be passed in byte, JSON or anything";
            Map<String, String> tags = new HashMap<>();
            tags.put("tag1", "kubemq");
            tags.put("tag2", "kubemq2");

            EventMessage eventMessage = EventMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .channel(eventChannelName)
                    .metadata("something you want to describe")
                    .body(data.getBytes())
                    .tags(tags)
                    .build();
            
            EventSendResult result = pubSubClient.sendEventsMessage(eventMessage);
            System.out.println("Send event result: " + result);
        } catch (RuntimeException e) {
            System.err.println("Failed to send event message: " + e.getMessage());
        }
    }

    /**
     * Sends an event store message to the configured events store channel.
     */
    public void sendEventStoreMessage() {
        try {
            String data = "Any data can be passed in byte, JSON or anything";
            Map<String, String> tags = new HashMap<>();
            tags.put("tag1", "kubemq");
            tags.put("tag2", "kubemq2");

            EventStoreMessage eventStoreMessage = EventStoreMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .channel(eventStoreChannelName)
                    .metadata("something you want to describe")
                    .body(data.getBytes())
                    .tags(tags)
                    .build();
            
            EventSendResult result = pubSubClient.sendEventsStoreMessage(eventStoreMessage);
            System.out.println("Send event result: " + result);
        } catch (RuntimeException e) {
            System.err.println("Failed to send event store message: " + e.getMessage());
        }
    }

    /**
     * Deletes an events channel with the specified name.
     */
    public void deleteEventsChannel() {
        try {
            boolean isChannelDeleted = pubSubClient.deleteEventsChannel(eventChannelName);
            System.out.println("Channel deleted: " + isChannelDeleted);
        } catch (RuntimeException e) {
            System.err.println("Failed to delete events channel: " + e.getMessage());
        }
    }

    /**
     * Deletes an events store channel with the specified name.
     */
    public void deleteEventsStoreChannel() {
        try {
            boolean isChannelDeleted = pubSubClient.deleteEventsChannel(eventStoreChannelName);
            System.out.println("Channel deleted: " + isChannelDeleted);
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
        PubSubClientExample example = new PubSubClientExample();
        example.createEventsChannel();
        example.createEventsStoreChannel();
        example.subscribeToEvents();
        example.subscribeToEventsStore();
        example.sendEventMessage();
        example.sendEventStoreMessage();
        example.deleteEventsChannel();
        example.deleteEventsStoreChannel();
        example.shutdown();
    }
}
