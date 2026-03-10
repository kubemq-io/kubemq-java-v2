package io.kubemq.sdk.pubsub;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.common.SubscribeType;
import io.kubemq.sdk.common.SubscriptionReconnectHandler;
import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.GrpcErrorMapper;
import io.kubemq.sdk.exception.HandlerException;
import io.kubemq.sdk.exception.KubeMQException;
import io.kubemq.sdk.exception.ValidationException;
import io.kubemq.sdk.observability.KubeMQLogger;
import io.kubemq.sdk.observability.KubeMQLoggerFactory;
import kubemq.Kubemq;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import javax.annotation.concurrent.ThreadSafe;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Represents a subscription to a KubeMQ events store.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. The {@link #cancel()} method
 * can be called from any thread to stop the subscription.</p>
 */
@ThreadSafe
@Getter
@Setter
@Builder
@AllArgsConstructor
public class EventsStoreSubscription {

    private static final KubeMQLogger log = KubeMQLoggerFactory.getLogger(EventsStoreSubscription.class);

    private static final ScheduledExecutorService reconnectExecutor =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kubemq-eventsstore-reconnect");
            t.setDaemon(true);
            return t;
        });

    @Builder.Default
    private transient SubscriptionReconnectHandler reconnectHandler = null;

    public static ScheduledExecutorService getReconnectExecutor() {
        return reconnectExecutor;
    }

    private String channel;

    private String group;

    @Builder.Default
    private transient Executor callbackExecutor = null;

    @Builder.Default
    private int maxConcurrentCallbacks = 1;

    private transient Semaphore callbackSemaphore;

    private EventsStoreType eventsStoreType;

    private int eventsStoreSequenceValue;

    private Instant eventsStoreStartTime;

    private Consumer<EventStoreMessageReceived> onReceiveEventCallback;

    /**
     * Callback function to be invoked when an error occurs.
     * Receives a typed KubeMQException for rich error classification.
     */
    private Consumer<KubeMQException> onErrorCallback;

    @Setter
    private transient StreamObserver<Kubemq.EventReceive> observer;

    @Setter
    private transient Kubemq.Subscribe subscribe;

    public EventsStoreSubscription() {
        this.eventsStoreType = EventsStoreType.Undefined;
        this.eventsStoreSequenceValue = 0;
    }

    public void raiseOnReceiveMessage(EventStoreMessageReceived receivedEvent) {
        if (onReceiveEventCallback != null) {
            onReceiveEventCallback.accept(receivedEvent);
        }
    }

    /**
     * Raises the onErrorCallback with the given typed exception.
     * If no callback is registered, logs at ERROR level.
     */
    public void raiseOnError(KubeMQException error) {
        if (onErrorCallback != null) {
            onErrorCallback.accept(error);
        } else {
            log.error("Unhandled async error in subscription", error,
                      "channel", channel);
        }
    }

    public void cancel() {
        if (observer != null) {
            observer.onCompleted();
            log.debug("Subscription Cancelled");
        }
    }


    /**
     * Validates the subscription, ensuring that the required fields are set.
     *
     * @throws ValidationException if any required field is not set.
     */
    public void validate() {
        if (channel == null || channel.isEmpty()) {
            throw ValidationException.builder()
                .code(ErrorCode.INVALID_ARGUMENT)
                .message("Event Store subscription must have a channel.")
                .operation("EventsStoreSubscription.validate")
                .build();
        }
        if (onReceiveEventCallback == null) {
            throw ValidationException.builder()
                .code(ErrorCode.INVALID_ARGUMENT)
                .message("Event Store subscription must have an onReceiveEventCallback function.")
                .operation("EventsStoreSubscription.validate")
                .channel(channel)
                .build();
        }
        if (eventsStoreType == null || eventsStoreType == EventsStoreType.Undefined) {
            throw ValidationException.builder()
                .code(ErrorCode.INVALID_ARGUMENT)
                .message("Event Store subscription must have an events store type.")
                .operation("EventsStoreSubscription.validate")
                .channel(channel)
                .build();
        }
        if (eventsStoreType == EventsStoreType.StartAtSequence && eventsStoreSequenceValue == 0) {
            throw ValidationException.builder()
                .code(ErrorCode.INVALID_ARGUMENT)
                .message("Event Store subscription with StartAtSequence events store type must have a sequence value.")
                .operation("EventsStoreSubscription.validate")
                .channel(channel)
                .build();
        }
        if (eventsStoreType == EventsStoreType.StartAtTime && eventsStoreStartTime == null) {
            throw ValidationException.builder()
                .code(ErrorCode.INVALID_ARGUMENT)
                .message("Event Store subscription with StartAtTime events store type must have a start time.")
                .operation("EventsStoreSubscription.validate")
                .channel(channel)
                .build();
        }
    }

    public Kubemq.Subscribe encode(String clientId, final PubSubClient pubSubClient) {
         this.callbackSemaphore = new Semaphore(Math.max(1, maxConcurrentCallbacks));
         Executor resolvedExecutor = callbackExecutor;
         if (resolvedExecutor == null) {
             resolvedExecutor = pubSubClient.getCallbackExecutor();
         }
         if (resolvedExecutor == null) {
             resolvedExecutor = Runnable::run;
         }
         final Executor executor = resolvedExecutor;
         final AtomicInteger inFlight = pubSubClient.getInFlightOperations();

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
                log.debug("Event store message received",
                          "eventId", messageReceive.getEventID(),
                          "channel", messageReceive.getChannel());
                executor.execute(() -> {
                    if (inFlight != null) {
                        inFlight.incrementAndGet();
                    }
                    try {
                        callbackSemaphore.acquire();
                        try {
                            raiseOnReceiveMessage(EventStoreMessageReceived.decode(messageReceive));
                        } catch (Exception userException) {
                            HandlerException handlerError = HandlerException.builder()
                                .message("User handler threw exception: " + userException.getMessage())
                                .operation("onReceiveEventStore")
                                .channel(channel)
                                .cause(userException)
                                .build();
                            raiseOnError(handlerError);
                        } finally {
                            callbackSemaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Callback dispatch interrupted", "channel", channel);
                    } finally {
                        if (inFlight != null) {
                            inFlight.decrementAndGet();
                        }
                    }
                });
            }

            @Override
            public void onError(Throwable t) {
                log.error("Subscription stream error", "error", t.getMessage());
                if (t instanceof io.grpc.StatusRuntimeException) {
                    KubeMQException mapped = GrpcErrorMapper.map(
                        (io.grpc.StatusRuntimeException) t, "subscribeToEventsStore", channel, null, false);
                    raiseOnError(mapped);
                    if (mapped.isRetryable()) {
                        reconnect(pubSubClient);
                    }
                } else {
                    KubeMQException transportError = io.kubemq.sdk.exception.TransportException.builder()
                        .message("Stream error: " + t.getMessage())
                        .operation("subscribeToEventsStore")
                        .channel(channel)
                        .cause(t)
                        .build();
                    raiseOnError(transportError);
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
        if (reconnectHandler == null) {
            reconnectHandler = new SubscriptionReconnectHandler(
                reconnectExecutor, pubSubClient.getReconnectIntervalInMillis(),
                channel, "subscribeToEventsStore");
        }
        reconnectHandler.scheduleReconnect(() -> {
            pubSubClient.getAsyncClient().subscribeToEvents(this.subscribe, this.getObserver());
        }, this::raiseOnError);
    }

    public void resetReconnectAttempts() {
        if (reconnectHandler != null) {
            reconnectHandler.resetAttempts();
        }
    }

    @Override
    public String toString() {
        return String.format("EventsStoreSubscription: channel=%s, group=%s, eventsStoreType=%s, eventsStoreSequenceValue=%d, eventsStoreStartTime=%s",
                channel, group, eventsStoreType.name(), eventsStoreSequenceValue, eventsStoreStartTime);
    }
}
