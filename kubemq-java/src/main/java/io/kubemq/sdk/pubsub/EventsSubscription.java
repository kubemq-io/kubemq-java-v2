package io.kubemq.sdk.pubsub;

import io.grpc.stub.StreamObserver;
import kubemq.Kubemq;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final ScheduledExecutorService reconnectExecutor =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kubemq-events-reconnect");
            t.setDaemon(true);
            return t;
        });

    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    // Expose executor for shutdown hook
    public static ScheduledExecutorService getReconnectExecutor() {
        return reconnectExecutor;
    }

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
    @Setter
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
     * @param pubSubClient The client use to reconnect
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
        int attempt = reconnectAttempts.incrementAndGet();

        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            log.error("Max reconnection attempts ({}) reached for channel: {}",
                      MAX_RECONNECT_ATTEMPTS, channel);
            raiseOnError("Max reconnection attempts reached after " + attempt + " tries");
            return;
        }

        // Exponential backoff: base * 2^(attempt-1), capped at 60 seconds
        long delay = Math.min(
            pubSubClient.getReconnectIntervalInMillis() * (1L << (attempt - 1)),
            60000L
        );

        log.info("Scheduling reconnection attempt {} for channel {} in {}ms",
                 attempt, channel, delay);

        reconnectExecutor.schedule(() -> {
            try {
                pubSubClient.getAsyncClient().subscribeToEvents(
                    this.encode(pubSubClient.getClientId(), pubSubClient),
                    this.getObserver()
                );
                reconnectAttempts.set(0); // Reset on success
                log.info("Successfully reconnected to channel {} after {} attempts",
                         channel, attempt);
            } catch (Exception e) {
                log.error("Reconnection attempt {} failed for channel {}", attempt, channel, e);
                reconnect(pubSubClient); // Schedule next attempt (not recursive stack)
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Resets the reconnection attempt counter. Call this on successful message receive.
     */
    public void resetReconnectAttempts() {
        reconnectAttempts.set(0);
    }

    @Override
    public String toString() {
        return String.format("EventsSubscription: channel=%s, group=%s", channel, group);
    }
}

