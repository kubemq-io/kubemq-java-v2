package io.kubemq.sdk.pubsub;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.common.SubscribeType;
import kubemq.Kubemq;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Represents a subscription to a KubeMQ events store.
 * This class is used to configure and manage an events store subscription.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@Slf4j
public class EventsStoreSubscription {

    /**
     * The channel to which the subscription is made.
     */
    private String channel;

    /**
     * The group to which the subscription belongs.
     */
    private String group;

    /**
     * The type of events store (e.g., start from sequence, start from time).
     */
    private EventsStoreType eventsStoreType;

    /**
     * The sequence value for the events store type.
     */
    private int eventsStoreSequenceValue;

    /**
     * The start time for the events store type.
     */
    private Instant eventsStoreStartTime;

    /**
     * Callback function to be invoked when an event is received.
     */
    private Consumer<EventStoreMessageReceived> onReceiveEventCallback;

    /**
     * Callback function to be invoked when an error occurs.
     */
    private Consumer<String> onErrorCallback;

    /**
     * Observer for the subscription.
     * This field is excluded from the builder and setter.
     */
    @Setter(onMethod_ = @__(@java.lang.SuppressWarnings("unused")))
    private transient StreamObserver<Kubemq.EventReceive> observer;

    @Setter(onMethod_ = @__(@java.lang.SuppressWarnings("unused")))
    private transient Kubemq.Subscribe subscribe;

    /**
     * Default constructor initializing default values.
     */
    public EventsStoreSubscription() {
        this.eventsStoreType = EventsStoreType.Undefined;
        this.eventsStoreSequenceValue = 0;
    }

    /**
     * Invokes the onReceiveEventCallback with the given event.
     *
     * @param receivedEvent The received event.
     */
    public void raiseOnReceiveMessage(EventStoreMessageReceived receivedEvent) {
        if (onReceiveEventCallback != null) {
            onReceiveEventCallback.accept(receivedEvent);
        }
    }

    /**
     * Invokes the onErrorCallback with the given error message.
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
     * Validates the subscription configuration.
     *
     * @throws IllegalArgumentException if the configuration is invalid.
     */
    public void validate() {
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("Event Store subscription must have a channel.");
        }
        if (onReceiveEventCallback == null) {
            throw new IllegalArgumentException("Event Store subscription must have an onReceiveEventCallback function.");
        }
        if (eventsStoreType == null || eventsStoreType == EventsStoreType.Undefined) {
            throw new IllegalArgumentException("Event Store subscription must have an events store type.");
        }
        if (eventsStoreType == EventsStoreType.StartAtSequence && eventsStoreSequenceValue == 0) {
            throw new IllegalArgumentException("Event Store subscription with StartAtSequence events store type must have a sequence value.");
        }
        if (eventsStoreType == EventsStoreType.StartAtTime && eventsStoreStartTime == null) {
            throw new IllegalArgumentException("Event Store subscription with StartAtTime events store type must have a start time.");
        }
    }

    /**
     * Encodes the subscription into a KubeMQ Subscribe object.
     *
     * @param clientId The client ID for the subscription.
     * @return The encoded KubeMQ Subscribe object.
     */
    public Kubemq.Subscribe encode(String clientId, final PubSubClient pubSubClient) {
         subscribe = Kubemq.Subscribe.newBuilder()
                .setSubscribeTypeData(Kubemq.Subscribe.SubscribeType.forNumber(SubscribeType.EventsStore.getValue()))
                .setClientID(clientId)
                .setChannel(channel)
                .setGroup(Optional.ofNullable(group).orElse(""))
                .setEventsStoreTypeData(Kubemq.Subscribe.EventsStoreType.forNumber(eventsStoreType == null ? 0 : eventsStoreType.getValue()))
                .setEventsStoreTypeValue(eventsStoreStartTime != null?(int) eventsStoreStartTime.getEpochSecond() : eventsStoreSequenceValue)
                .build();

         observer = new StreamObserver<Kubemq.EventReceive>() {
            @Override
            public void onNext(Kubemq.EventReceive messageReceive) {
                log.debug("Event Received Event: EventID:'{}', Channel:'{}', Metadata: '{}'", messageReceive.getEventID(), messageReceive.getChannel(), messageReceive.getMetadata());
                // Send the received message to the consumer
                raiseOnReceiveMessage(EventStoreMessageReceived.decode(messageReceive));
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
            log.debug("Attempting to re-subscribe...");
            pubSubClient.getAsyncClient().subscribeToEvents(this.subscribe, this.getObserver());
            log.debug("Re-subscribed successfully");
        } catch (Exception e) {
            log.error("Re-subscribe attempt failed", e);
            this.reconnect(pubSubClient);
        }
    }

    /**
     * Returns a string representation of the events store subscription.
     *
     * @return A string containing the subscription details.
     */
    @Override
    public String toString() {
        return String.format("EventsStoreSubscription: channel=%s, group=%s, eventsStoreType=%s, eventsStoreSequenceValue=%d, eventsStoreStartTime=%s",
                channel, group, eventsStoreType.name(), eventsStoreSequenceValue, eventsStoreStartTime);
    }
}
