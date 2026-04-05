package io.kubemq.sdk.pubsub;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.Internal;
import io.kubemq.sdk.observability.KubeMQLogger;
import io.kubemq.sdk.observability.KubeMQLoggerFactory;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.concurrent.ThreadSafe;
import kubemq.Kubemq;
import lombok.Getter;

/**
 * Helper for sending events and event store messages over a gRPC stream.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Multiple threads may call {@link
 * #sendEventMessage} and {@link #sendEventStoreMessage} concurrently. Stream handler initialization
 * is synchronized to prevent race conditions.
 */
@ThreadSafe
@Internal
public class EventStreamHelper {

  private static final KubeMQLogger LOG = KubeMQLoggerFactory.getLogger(EventStreamHelper.class);

  private StreamObserver<Kubemq.Event> queuesUpStreamHandler = null;
  @Getter private StreamObserver<Kubemq.Result> resultStreamObserver;

  private final Map<String, CompletableFuture<EventSendResult>> pendingResponses =
      new ConcurrentHashMap<>();
  private final Map<String, Long> requestTimestamps = new ConcurrentHashMap<>();

  private static final long REQUEST_TIMEOUT_MS = 60000; // 60 seconds

  // Shared cleanup executor for all instances
  private static final ScheduledExecutorService CLEANUP_EXECUTOR =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "kubemq-event-cleanup");
            t.setDaemon(true);
            return t;
          });

  private static volatile boolean cleanupStarted = false;

  /** Constructs a new instance. */
  public EventStreamHelper() {
    this.resultStreamObserver =
        new StreamObserver<Kubemq.Result>() {
          @Override
          public void onNext(Kubemq.Result result) {
            LOG.debug("Received EventSendResult", "eventId", result.getEventID());
            String eventId = result.getEventID();
            CompletableFuture<EventSendResult> future = pendingResponses.remove(eventId);
            requestTimestamps.remove(eventId);
            if (future != null) {
              future.complete(EventSendResult.decode(result));
            } else {
              LOG.warn("Received response for unknown event ID", "eventId", eventId);
            }
          }

          @Override
          public void onError(Throwable t) {
            LOG.error("Error in EventSendResult", t);
            synchronized (EventStreamHelper.this) {
              queuesUpStreamHandler = null;
              pendingResponses.forEach(
                  (id, future) -> {
                    EventSendResult sendResult = new EventSendResult();
                    sendResult.setError(t.getMessage());
                    future.complete(sendResult);
                  });
              pendingResponses.clear();
              requestTimestamps.clear();
            }
          }

          @Override
          public void onCompleted() {
            LOG.debug("EventSendResult onCompleted.");
            synchronized (EventStreamHelper.this) {
              queuesUpStreamHandler = null;
            }
          }
        };
  }

  private void startCleanupTask() {
    if (!cleanupStarted) {
      synchronized (EventStreamHelper.class) {
        if (!cleanupStarted) {
          CLEANUP_EXECUTOR.scheduleAtFixedRate(
              this::cleanupStaleRequests, 30, 30, TimeUnit.SECONDS);
          cleanupStarted = true;
        }
      }
    }
  }

  private void cleanupStaleRequests() {
    long now = System.currentTimeMillis();
    pendingResponses
        .entrySet()
        .removeIf(
            entry -> {
              Long timestamp = requestTimestamps.get(entry.getKey());
              if (timestamp != null && (now - timestamp) > REQUEST_TIMEOUT_MS) {
                EventSendResult timeoutResult = new EventSendResult();
                timeoutResult.setError("Request timed out after " + REQUEST_TIMEOUT_MS + "ms");
                timeoutResult.setSent(false);
                entry.getValue().complete(timeoutResult);
                requestTimestamps.remove(entry.getKey());
                LOG.warn("Cleaned up stale event request", "requestId", entry.getKey());
                return true;
              }
              return false;
            });
  }

  // Expose executor for shutdown hook
  public static ScheduledExecutorService getCleanupExecutor() {
    return CLEANUP_EXECUTOR;
  }

  /**
   * Sends the event message.
   *
   * @param kubeMQClient the kube mqclient
   * @param event the event
   */
  public synchronized void sendEventMessage(KubeMQClient kubeMQClient, Kubemq.Event event) {
    if (queuesUpStreamHandler == null) {
      queuesUpStreamHandler = kubeMQClient.getAsyncClient().sendEventsStream(resultStreamObserver);
      startCleanupTask();
    }
    queuesUpStreamHandler.onNext(event);
    LOG.debug("Event Message sent");
  }

  /**
   * Sends an event store message asynchronously.
   *
   * @param kubeMQClient the KubeMQ client
   * @param event the event to send
   * @return a CompletableFuture that completes with the send result
   */
  public CompletableFuture<EventSendResult> sendEventStoreMessageAsync(
      KubeMQClient kubeMQClient, Kubemq.Event event) {
    String requestId = UUID.randomUUID().toString();
    CompletableFuture<EventSendResult> responseFuture = new CompletableFuture<>();
    Kubemq.Event eventWithId = event.toBuilder().setEventID(requestId).build();

    // Single lock covers register + send as atomic unit: the future must be
    // in pendingResponses before onNext, otherwise a fast server response
    // could find no registered future. Holding lock during onNext is safe
    // because gRPC onNext is non-blocking for standard-sized messages.
    synchronized (this) {
      if (queuesUpStreamHandler == null) {
        queuesUpStreamHandler =
            kubeMQClient.getAsyncClient().sendEventsStream(resultStreamObserver);
        startCleanupTask();
      }
      pendingResponses.put(requestId, responseFuture);
      requestTimestamps.put(requestId, System.currentTimeMillis());
      queuesUpStreamHandler.onNext(eventWithId);
    }
    LOG.debug("Event store message sent async", "requestId", requestId);

    return responseFuture;
  }

  /**
   * Sends the event store message.
   *
   * @param kubeMQClient the kube mqclient
   * @param event the event
   * @return the result
   */
  public EventSendResult sendEventStoreMessage(KubeMQClient kubeMQClient, Kubemq.Event event) {
    try {
      return sendEventStoreMessageAsync(kubeMQClient, event)
          .get(kubeMQClient.getRequestTimeoutSeconds(), TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      LOG.error("Timeout waiting for response");
      EventSendResult timeoutResult = new EventSendResult();
      timeoutResult.setError(
          "Request timed out after " + kubeMQClient.getRequestTimeoutSeconds() + " seconds");
      timeoutResult.setSent(false);
      return timeoutResult;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.error("Interrupted waiting for response", e);
      EventSendResult interruptedResult = new EventSendResult();
      interruptedResult.setError("Request interrupted");
      interruptedResult.setSent(false);
      return interruptedResult;
    } catch (Exception e) {
      LOG.error("Error waiting for response", e);
      throw io.kubemq.sdk.exception.KubeMQException.newBuilder()
          .code(io.kubemq.sdk.exception.ErrorCode.UNKNOWN_ERROR)
          .category(io.kubemq.sdk.exception.ErrorCategory.FATAL)
          .retryable(false)
          .message("Failed to get response: " + e.getMessage())
          .operation("sendEventStoreMessage")
          .cause(e)
          .build();
    }
  }

  // Getter for stream handler (for testing)
  public StreamObserver<Kubemq.Event> getQueuesUpStreamHandler() {
    return queuesUpStreamHandler;
  }

  // Setter for stream handler (for testing)
  public void setQueuesUpStreamHandler(StreamObserver<Kubemq.Event> handler) {
    this.queuesUpStreamHandler = handler;
  }
}
