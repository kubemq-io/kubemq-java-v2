package io.kubemq.example.pubsub;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.EventMessageReceived;
import io.kubemq.sdk.pubsub.EventStoreMessageReceived;
import io.kubemq.sdk.pubsub.EventsStoreSubscription;
import io.kubemq.sdk.pubsub.EventsStoreType;
import io.kubemq.sdk.pubsub.EventsSubscription;
import io.kubemq.sdk.pubsub.PubSubClient;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;


public class SubscribeToEventExample {
 
    private final KubeMQClient kubeMQClient;
    private final PubSubClient pubSubClient;
    private final String eventChannelName = "mytest-channel";
    private final String eventStoreChannelName = "mytest-channel-eventstore";
    private final String address = "localhost:50000";
    private final String clientId = "kueMQClientId";

    /**
     * Constructs a PubSubClientExample instance, initializing the KubeMQClient and PubSubClient.
     */
    public SubscribeToEventExample() {
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
                    .channel(eventStoreChannelName)
                    //.group("All IT Team")
                    .eventsStoreType(EventsStoreType.StartAtTime)
                    .eventsStoreStartTime(Instant.now().minus(1, ChronoUnit.HOURS))
                    .onReceiveEventCallback(onReceiveEventCallback)
                    .onErrorCallback(onErrorCallback)
                    .build();

            pubSubClient.subscribeToEventsStore(subscription);
            System.out.println("EventsStore Subscribed");
        } catch (RuntimeException e) {
            System.err.println("Failed to subscribe to events store: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        
        SubscribeToEventExample example = new SubscribeToEventExample();
        example.subscribeToEvents();
        example.subscribeToEventsStore();
        
        // Keep listening for events from channel
        CountDownLatch latch = new CountDownLatch(1);
        latch.await();  // This will keep the main thread alive
        
    }

    
}
