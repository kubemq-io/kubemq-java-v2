package io.kubemq.sdk.pubsub;

import kubemq.Kubemq;
import lombok.*;

import java.util.function.Consumer;

/**
 * Represents a subscription to events in KubeMQ.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    public Kubemq.Subscribe encode(String clientId) {
        return Kubemq.Subscribe.newBuilder()
                .setChannel(channel)
                .setGroup(group != null ?group :"")
                .setClientID(clientId)
                .setSubscribeTypeData(Kubemq.Subscribe.SubscribeType.Events)
                .build();
    }

    @Override
    public String toString() {
        return String.format("EventsSubscription: channel=%s, group=%s", channel, group);
    }
}

