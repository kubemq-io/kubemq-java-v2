package io.kubemq.example.pubsub;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.PubSubChannel;
import io.kubemq.sdk.pubsub.PubSubClient;

import java.util.List;


public class ListEventsChanneExample {
 

    private final PubSubClient pubSubClient;
    private final String searchQuery = "";
    private final String address = "localhost:50000";
    private final String clientId = "kueMQClientId";

    /**
     * Constructs a PubSubClientExample instance, initializing the KubeMQClient and PubSubClient.
     */
    public ListEventsChanneExample() {
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
     * List events channel.
     */
    public void listEventsChannel() {
        try {
           System.out.println("Events Channel listing");
           List<PubSubChannel> eventChannel = pubSubClient.listEventsChannels(searchQuery);
           eventChannel.forEach(  evt -> {
               System.out.println("Name: "+evt.getName()+" ChannelTYpe: "+evt.getType()+" isActive: "+evt.getIsActive());
           });
           
        } catch (RuntimeException e) {
            System.err.println("Failed to list event channel: " + e.getMessage());
        }
    }

    /**
     * List events store channel.
     */
    public void listEventsStoreChannel() {
        try {
              System.out.println("Events Channel listing");
           List<PubSubChannel> eventChannel = pubSubClient.listEventsStoreChannels(searchQuery);
           eventChannel.forEach(  evt -> {
               System.out.println("Name: "+evt.getName()+" ChannelTYpe: "+evt.getType()+" isActive: "+evt.getIsActive());
           });
        } catch (RuntimeException e) {
            System.err.println("Failed to list events store channel: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        
        ListEventsChanneExample example = new ListEventsChanneExample();
        example.listEventsChannel();
        example.listEventsStoreChannel();  
    }

    
}
