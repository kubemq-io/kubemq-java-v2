package io.kubemq.example.pubsub;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.EventMessageReceived;
import io.kubemq.sdk.pubsub.EventsSubscription;
import io.kubemq.sdk.pubsub.PubSubClient;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;


public class SubscribeToEventExample {
 
    private final PubSubClient pubSubClient;
    private final String eventChannelName = "mytest-channel";
    private final String address = "localhost:50000";
    private final String clientId = "kueMQClientId";

    /**
     * Constructs a PubSubClientExample instance, initializing the KubeMQClient and PubSubClient.
     */
    public SubscribeToEventExample() {
 // Create PubSubClient using the builder pattern
        pubSubClient = PubSubClient.builder()
                .address(address)
                .clientId(clientId)
                .logLevel(KubeMQClient.Level.INFO)
                .build();
        // Ping to test Connection is succesffull
        ServerInfo pingResult = pubSubClient.ping();
        System.out.println("Ping Response: "+pingResult.toString());
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
                    .onReceiveEventCallback(onReceiveEventCallback)
                    .onErrorCallback(onErrorCallback)
                    .build();

            pubSubClient.subscribeToEvents(subscription);
            System.out.println("Events Subscribed");
            
            // Wait for 10 seconds and call the cancel subscription
//            try{
//                Thread.sleep(10 * 1000);
//                subscription.cancel();
//            } catch(Exception ex){
//
//            }
            
        } catch (RuntimeException e) {
            System.err.println("Failed to subscribe to events: " + e.getMessage());
        }
    }

    
    public static void main(String[] args) throws InterruptedException {
        
        SubscribeToEventExample example = new SubscribeToEventExample();
        example.subscribeToEvents();
        
        // Keep the main thread running to handle responses test reconnection
        CountDownLatch latch = new CountDownLatch(1);
        latch.await();  // This will keep the main thread alive
    }

}
