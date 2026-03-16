package io.kubemq.sdk.pubsub;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.common.SubscriptionReconnectHandler;
import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.GrpcErrorMapper;
import io.kubemq.sdk.exception.HandlerException;
import io.kubemq.sdk.exception.KubeMQException;
import io.kubemq.sdk.exception.ValidationException;
import io.kubemq.sdk.observability.KubeMQLogger;
import io.kubemq.sdk.observability.KubeMQLoggerFactory;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.annotation.concurrent.ThreadSafe;
import kubemq.Kubemq;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a subscription to events in KubeMQ.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. The {@link #cancel()} method can be called
 * from any thread to stop the subscription.
 *
 * <h3>Callback Behavior</h3>
 *
 * <p><b>Sequential by default:</b> Callbacks for a single subscription are invoked sequentially
 * (one at a time) in the order messages are received from the server.
 *
 * <p><b>Concurrent callbacks (opt-in):</b> Set {@code maxConcurrentCallbacks} &gt; 1 and provide a
 * multi-threaded {@code callbackExecutor} to enable parallel callback processing. When enabled,
 * messages may be processed out of order.
 *
 * <p><b>Blocking:</b> Do not perform long-running or blocking operations inside callbacks. If heavy
 * processing is needed, submit work to a separate worker pool.
 */
@ThreadSafe
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventsSubscription {

  private static final KubeMQLogger LOG = KubeMQLoggerFactory.getLogger(EventsSubscription.class);

  private static final ScheduledExecutorService RECONNECT_EXECUTOR =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "kubemq-events-reconnect");
            t.setDaemon(true);
            return t;
          });

  @Builder.Default private transient SubscriptionReconnectHandler reconnectHandler = null;

  public static ScheduledExecutorService getReconnectExecutor() {
    return RECONNECT_EXECUTOR;
  }

  private String channel;

  private String group;

  /**
   * Executor for dispatching subscription callbacks. Default: SDK's shared callback executor
   * (single-thread, sequential). Set to a custom executor to control thread pool size for callback
   * processing.
   */
  @Builder.Default private transient Executor callbackExecutor = null;

  /**
   * Maximum number of callbacks that may execute concurrently for this subscription. Default: 1
   * (sequential processing). When &gt; 1, callbacks may fire out of order.
   */
  @Builder.Default private int maxConcurrentCallbacks = 1;

  private transient Semaphore callbackSemaphore;

  private Consumer<EventMessageReceived> onReceiveEventCallback;

  /**
   * Callback function to be called when an error occurs. Receives a typed KubeMQException for rich
   * error classification.
   */
  private Consumer<KubeMQException> onErrorCallback;

  @Setter private transient StreamObserver<Kubemq.EventReceive> observer;

  /**
   * Raises the onReceiveEventCallback with the given event message.
   *
   * @param receivedEvent the received event
   */
  public void raiseOnReceiveMessage(EventMessageReceived receivedEvent) {
    if (onReceiveEventCallback != null) {
      onReceiveEventCallback.accept(receivedEvent);
    }
  }

  /**
   * Raises the onErrorCallback with the given typed exception. If no callback is registered, logs
   * at ERROR level.
   *
   * @param error the error
   */
  public void raiseOnError(KubeMQException error) {
    if (onErrorCallback != null) {
      onErrorCallback.accept(error);
    } else {
      LOG.error(
          "Unhandled async error in subscription", error,
          "channel", channel);
    }
  }

  /** Cancel the subscription */
  public void cancel() {
    if (observer != null) {
      observer.onCompleted();
      LOG.debug("Subscription Cancelled");
    }
  }

  /**
   * Validates the subscription, ensuring that the required fields are set.
   *
   * @throws ValidationException if the channel or onReceiveEventCallback is not set.
   */
  public void validate() {
    if (channel == null || channel.isEmpty()) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message("Event subscription must have a channel.")
          .operation("EventsSubscription.validate")
          .build();
    }
    if (onReceiveEventCallback == null) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message("Event subscription must have a onReceiveEventCallback function.")
          .operation("EventsSubscription.validate")
          .channel(channel)
          .build();
    }
  }

  /**
   * Encodes the subscription into a KubeMQ Subscribe object.
   *
   * @param clientId the client id
   * @param pubSubClient the pub sub client
   * @return the result
   */
  public Kubemq.Subscribe encode(String clientId, PubSubClient pubSubClient) {
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

    Kubemq.Subscribe subscribe =
        Kubemq.Subscribe.newBuilder()
            .setChannel(channel)
            .setGroup(group != null ? group : "")
            .setClientID(clientId)
            .setSubscribeTypeData(Kubemq.Subscribe.SubscribeType.Events)
            .setSubscribeTypeDataValue(1)
            .build();

    observer =
        new StreamObserver<Kubemq.EventReceive>() {
          @Override
          public void onNext(Kubemq.EventReceive messageReceive) {
            LOG.debug(
                "Event received",
                "eventId",
                messageReceive.getEventID(),
                "channel",
                messageReceive.getChannel());
            executor.execute(
                () -> {
                  if (inFlight != null) {
                    inFlight.incrementAndGet();
                  }
                  try {
                    callbackSemaphore.acquire();
                    try {
                      raiseOnReceiveMessage(EventMessageReceived.decode(messageReceive));
                    } catch (Exception userException) {
                      HandlerException handlerError =
                          HandlerException.builder()
                              .message(
                                  "User handler threw exception: " + userException.getMessage())
                              .operation("onReceiveEvent")
                              .channel(channel)
                              .cause(userException)
                              .build();
                      raiseOnError(handlerError);
                    } finally {
                      callbackSemaphore.release();
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.warn("Callback dispatch interrupted", "channel", channel);
                  } finally {
                    if (inFlight != null) {
                      inFlight.decrementAndGet();
                    }
                  }
                });
          }

          @Override
          public void onError(Throwable t) {
            LOG.error("Subscription stream error", "error", t.getMessage());
            if (t instanceof io.grpc.StatusRuntimeException) {
              KubeMQException mapped =
                  GrpcErrorMapper.map(
                      (io.grpc.StatusRuntimeException) t,
                      "subscribeToEvents",
                      channel,
                      null,
                      false);
              raiseOnError(mapped);
              if (mapped.isRetryable()) {
                reconnect(pubSubClient);
              }
            } else {
              KubeMQException transportError =
                  io.kubemq.sdk.exception.TransportException.builder()
                      .message("Stream error: " + t.getMessage())
                      .operation("subscribeToEvents")
                      .channel(channel)
                      .cause(t)
                      .build();
              raiseOnError(transportError);
            }
          }

          @Override
          public void onCompleted() {
            LOG.debug("StreamObserver completed.");
          }
        };
    return subscribe;
  }

  private void reconnect(PubSubClient pubSubClient) {
    if (reconnectHandler == null) {
      reconnectHandler =
          new SubscriptionReconnectHandler(
              RECONNECT_EXECUTOR,
              pubSubClient.getReconnectIntervalInMillis(),
              channel,
              "subscribeToEvents");
    }
    reconnectHandler.scheduleReconnect(
        () -> {
          pubSubClient
              .getAsyncClient()
              .subscribeToEvents(
                  this.encode(pubSubClient.getClientId(), pubSubClient), this.getObserver());
        },
        this::raiseOnError);
  }

  /** Resets the reconnect attempts. */
  public void resetReconnectAttempts() {
    if (reconnectHandler != null) {
      reconnectHandler.resetAttempts();
    }
  }

  /**
   * Returns a string representation.
   *
   * @return the result
   */
  @Override
  public String toString() {
    return String.format("EventsSubscription: channel=%s, group=%s", channel, group);
  }
}
