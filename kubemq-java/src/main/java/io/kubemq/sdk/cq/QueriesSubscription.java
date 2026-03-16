package io.kubemq.sdk.cq;

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
import kubemq.Kubemq.Subscribe;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a subscription to queries in KubeMQ.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. The {@link #cancel()} method can be called
 * from any thread to stop the subscription.
 */
@ThreadSafe
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueriesSubscription {

  private static final KubeMQLogger LOG = KubeMQLoggerFactory.getLogger(QueriesSubscription.class);

  private static final ScheduledExecutorService RECONNECT_EXECUTOR =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "kubemq-queries-reconnect");
            t.setDaemon(true);
            return t;
          });

  @Builder.Default private transient SubscriptionReconnectHandler reconnectHandler = null;

  public static ScheduledExecutorService getReconnectExecutor() {
    return RECONNECT_EXECUTOR;
  }

  private String channel;
  private String group;

  @Builder.Default private transient Executor callbackExecutor = null;

  @Builder.Default private int maxConcurrentCallbacks = 1;

  private transient Semaphore callbackSemaphore;

  private Consumer<QueryMessageReceived> onReceiveQueryCallback;

  /**
   * Callback function to be called when an error occurs. Receives a typed KubeMQException for rich
   * error classification.
   */
  private Consumer<KubeMQException> onErrorCallback;

  @Setter private transient StreamObserver<Kubemq.Request> observer;

  /**
   * Raises the on receive message.
   *
   * @param receivedQuery the received query
   */
  public void raiseOnReceiveMessage(QueryMessageReceived receivedQuery) {
    if (onReceiveQueryCallback != null) {
      onReceiveQueryCallback.accept(receivedQuery);
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

  /** Cancels this operation. */
  public void cancel() {
    if (observer != null) {
      observer.onCompleted();
      LOG.debug("Subscription Cancelled");
    }
  }

  /**
   * @throws ValidationException if the channel or callback is not set.
   */
  public void validate() {
    if (channel == null || channel.isEmpty()) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message("Query subscription must have a channel.")
          .operation("QueriesSubscription.validate")
          .build();
    }
    if (onReceiveQueryCallback == null) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message("Query subscription must have a on_receive_query_callback function.")
          .operation("QueriesSubscription.validate")
          .channel(channel)
          .build();
    }
  }

  /**
   * Encodes into protocol buffer format.
   *
   * @param clientId the client id
   * @param cQClient the c qclient
   * @return the result
   */
  public Subscribe encode(String clientId, CQClient cQClient) {
    this.callbackSemaphore = new Semaphore(Math.max(1, maxConcurrentCallbacks));
    Executor resolvedExecutor = callbackExecutor;
    if (resolvedExecutor == null) {
      resolvedExecutor = cQClient.getCallbackExecutor();
    }
    if (resolvedExecutor == null) {
      resolvedExecutor = Runnable::run;
    }
    final Executor executor = resolvedExecutor;
    final AtomicInteger inFlight = cQClient.getInFlightOperations();

    Subscribe request =
        Subscribe.newBuilder()
            .setChannel(this.channel)
            .setGroup(this.group != null ? this.group : "")
            .setClientID(clientId)
            .setSubscribeTypeData(Subscribe.SubscribeType.Queries)
            .setSubscribeTypeDataValue(Subscribe.SubscribeType.Queries_VALUE)
            .build();

    observer =
        new StreamObserver<Kubemq.Request>() {
          @Override
          public void onNext(Kubemq.Request messageReceive) {
            LOG.debug("Query message received", "requestId", messageReceive.getRequestID());
            executor.execute(
                () -> {
                  if (inFlight != null) {
                    inFlight.incrementAndGet();
                  }
                  try {
                    callbackSemaphore.acquire();
                    try {
                      raiseOnReceiveMessage(QueryMessageReceived.decode(messageReceive));
                    } catch (Exception userException) {
                      HandlerException handlerError =
                          HandlerException.builder()
                              .message(
                                  "User handler threw exception: " + userException.getMessage())
                              .operation("onReceiveQuery")
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
                      "subscribeToQueries",
                      channel,
                      null,
                      false);
              raiseOnError(mapped);
              if (mapped.isRetryable()) {
                reconnect(cQClient);
              }
            } else {
              KubeMQException transportError =
                  io.kubemq.sdk.exception.TransportException.builder()
                      .message("Stream error: " + t.getMessage())
                      .operation("subscribeToQueries")
                      .channel(channel)
                      .cause(t)
                      .build();
              raiseOnError(transportError);
            }
          }

          @Override
          public void onCompleted() {
            LOG.debug("QueriesSubscription Stream completed.");
          }
        };

    return request;
  }

  private void reconnect(CQClient cQClient) {
    if (reconnectHandler == null) {
      reconnectHandler =
          new SubscriptionReconnectHandler(
              RECONNECT_EXECUTOR,
              cQClient.getReconnectIntervalInMillis(),
              channel,
              "subscribeToQueries");
    }
    reconnectHandler.scheduleReconnect(
        () -> {
          cQClient
              .getAsyncClient()
              .subscribeToRequests(
                  this.encode(cQClient.getClientId(), cQClient), this.getObserver());
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
    return "QueriesSubscription: channel=" + channel + ", group=" + group;
  }
}
