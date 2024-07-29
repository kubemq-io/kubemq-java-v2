
package io.kubemq.example.pubsub;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.EventMessage;
import io.kubemq.sdk.pubsub.PubSubClient;
import kubemq.Kubemq.Event;
import kubemq.Kubemq.Result;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * Example class demonstrating how to send events using a bi-directional stream with KubeMQ.
 */
public class SendEventsStreamExample {

    private final KubeMQClient kubeMQClient;
    private final PubSubClient pubSubClient;
    private final String eventChannelName = "mytest-channel";
    private final String address = "localhost:50000";
    private final String clientId = "kueMQClientId";

    /**
     * Constructs a SendEventsStreamExample instance, initializing the KubeMQClient and PubSubClient.
     */
    public SendEventsStreamExample() {
        // Setup KubeMQ client
        kubeMQClient = KubeMQClient.builder()
                .address(address)
                .clientId(clientId)
                .build();

        // Ping to test Connection is successful
        ServerInfo pingResult = kubeMQClient.ping();
        System.out.println("Ping Response: " + pingResult.toString());

        // Create PubSubClient using the builder pattern
        pubSubClient = PubSubClient.builder()
                .kubeMQClient(kubeMQClient)
                .build();
    }

    /**
     * Initiates the event stream to send messages to the server.
     */
    public void sendEventsStream() {
        StreamObserver<Result> observer = new StreamObserver<Result>() {
            @Override
            public void onNext(Result result) {
                // Handle the response received from the server
                System.out.println("Received response: " + result);
            }

            @Override
            public void onError(Throwable t) {
                // Handle any errors that occur during communication
                System.err.println("Error during stream: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                // Handle the completion of the stream
                System.out.println("Stream completed.");
            }
        };

        StreamObserver<Event> requestObserver = pubSubClient.sendEventsStream(observer);

        // Sending events using a separate thread to simulate concurrent message sending
        new Thread(new Runnable() {
            @Override
            public void run() {
                sendEventMessages(requestObserver);
            }
        }).start();
        
        // Complete the stream, so we marked it done and closing stream
        // requestObserver.onCompleted();
    }

    /**
     * Sends a series of event messages to the server.
     *
     * @param requestObserver the StreamObserver used to send events
     */
    private void sendEventMessages(StreamObserver<Event> requestObserver) {
        for (int i = 0; i < 10; i++) {
            String data = "Sending data in events stream";
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
            requestObserver.onNext(eventMessage.encode(clientId));
            System.out.println("Message sent to events stream: " + i);
            // Optional sleep to simulate delay between messages
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        System.out.println("All messages sent to events stream: " + new java.util.Date());
    }

    /**
     * Main method to execute the example.
     *
     * @param args command line arguments
     * @throws InterruptedException if the main thread is interrupted
     */
    public static void main(String[] args) throws InterruptedException {
        SendEventsStreamExample example = new SendEventsStreamExample();
        System.out.println("Starting to send messages in events stream: " + new java.util.Date());
        example.sendEventsStream();

        // Keep the main thread running to handle responses
        CountDownLatch latch = new CountDownLatch(1);
        latch.await();  // This will keep the main thread alive
    }
}

