package io.kubemq.example.pubsub;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Example class demonstrating the use of PubSubClient to send messages using KubeMQ.
 * This class covers operations such as sending
 */
public class SendEventMessageExample {

    private final PubSubClient pubSubClient;
    private final String eventChannelName = "mytest-channel";
    private final String eventStoreChannelName = "mytest-channel-eventstore";
    private final String address = "localhost:50000";
    private final String clientId = "kueMQClientId";

    /**
     * Constructs a PubSubClientExample instance, initializing the KubeMQClient and PubSubClient.
     */
    public SendEventMessageExample() {
        // Create PubSubClient using the builder pattern
        pubSubClient = PubSubClient.builder()
                .address(address)
                .clientId(clientId)
                .build();
        // Ping to test Connection is succesffull
        ServerInfo pingResult = pubSubClient.ping();
        System.out.println("Ping Response: "+pingResult.toString());
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
            
            pubSubClient.sendEventsMessage(eventMessage);
            System.out.println("Event message sent ");
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
     * Main method to demonstrate the usage of SendEventMessageExample.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        SendEventMessageExample example = new SendEventMessageExample();
        example.sendEventMessage();
        example.sendEventStoreMessage();
    }
}
