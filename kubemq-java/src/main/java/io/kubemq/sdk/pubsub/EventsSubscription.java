package io.kubemq.sdk.pubsub;

import io.grpc.stub.StreamObserver;
import kubemq.Kubemq;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * Represents a subscription to events in KubeMQ.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class EventsSubscription {

    /**
     * The channel to subscribe to.
     */
    private String channel;

    /**
     * The group to subscribe with.
     */
    private String group;

    /**
     * Callback function to be called when an event message is received.
     */
    private Consumer<EventMessageReceived> onReceiveEventCallback;

    /**
     * Callback function to be called when an error occurs.
     */
    private Consumer<String> onErrorCallback;

    /**
     * Observer for the subscription.
     * This field is excluded from the builder and setter.
     */
    @Setter(onMethod_ = @__(@java.lang.SuppressWarnings("unused")))
    private transient StreamObserver<Kubemq.EventReceive> observer;

    /**
     * Raises the onReceiveEventCallback with the given event message.
     *
     * @param receivedEvent The received event message.
     */
    public void raiseOnReceiveMessage(EventMessageReceived receivedEvent) {
        if (onReceiveEventCallback != null) {
            onReceiveEventCallback.accept(receivedEvent);
        }
    }

    /**
     * Raises the onErrorCallback with the given error message.
     *
     * @param msg The error message.
     */
    public void raiseOnError(String msg) {
        if (onErrorCallback != null) {
            onErrorCallback.accept(msg);
        }
    }

    /**
     * Cancel the subscription
     */
    public void cancel() {
        if (observer != null) {
            observer.onCompleted();
            log.debug("Subscription Cancelled");
        }
    }


    /**
     * Validates the subscription, ensuring that the required fields are set.
     *
     * @throws IllegalArgumentException if the channel or onReceiveEventCallback is not set.
     */
    public void validate() {
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("Event subscription must have a channel.");
        }
        if (onReceiveEventCallback == null) {
            throw new IllegalArgumentException("Event subscription must have a onReceiveEventCallback function.");
        }
    }

    /**
     * Encodes the subscription into a KubeMQ Subscribe object.
     *
     * @param clientId The client ID to use for the subscription.
     * @return The encoded KubeMQ Subscribe object.
     */
    public Kubemq.Subscribe encode(String clientId, PubSubClient pubSubClient) {
        Kubemq.Subscribe subscribe = Kubemq.Subscribe.newBuilder()
                .setChannel(channel)
                .setGroup(group != null ?group :"")
                .setClientID(clientId)
                .setSubscribeTypeData(Kubemq.Subscribe.SubscribeType.Events)
                .setSubscribeTypeDataValue(1)
                .build();

        observer = new StreamObserver<Kubemq.EventReceive>() {
            @Override
            public void onNext(Kubemq.EventReceive messageReceive) {
                log.debug("Event Received Event: EventID:'{}', Channel:'{}', Metadata: '{}'", messageReceive.getEventID(), messageReceive.getChannel(), messageReceive.getMetadata());
                // Send the received message to the consumer
               raiseOnReceiveMessage(EventMessageReceived.decode(messageReceive));
            }

            @Override
            public void onError(Throwable t) {
                log.error("Error:-- > "+t.getMessage());
                raiseOnError(t.getMessage());
                // IF gRPC exception attempt to retry
                if(t instanceof io.grpc.StatusRuntimeException){
                    io.grpc.StatusRuntimeException se =(io.grpc.StatusRuntimeException)t;
                    reconnect(pubSubClient);
                }
            }
            @Override
            public void onCompleted() {
                log.debug("StreamObserver completed.");
            }
        };
        return subscribe;
    }

    private void reconnect(PubSubClient pubSubClient) {
            try {
                Thread.sleep(pubSubClient.getReconnectIntervalSeconds());
                log.debug("Attempting to re-subscribe... ");
                // Your method to subscribe again
                pubSubClient.getAsyncClient().subscribeToEvents(this.encode(pubSubClient.getClientId(),pubSubClient), this.getObserver());
                log.debug("Re-subscribed successfully");
            } catch (Exception e) {
                log.error("Re-subscribe attempt failed", e);
                this.reconnect(pubSubClient);
            }

    }

    @Override
    public String toString() {
        return String.format("EventsSubscription: channel=%s, group=%s", channel, group);
    }
}

