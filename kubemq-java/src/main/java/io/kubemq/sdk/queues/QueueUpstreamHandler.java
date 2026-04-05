package io.kubemq.sdk.queues;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.Internal;
import io.kubemq.sdk.observability.KubeMQLogger;
import io.kubemq.sdk.observability.KubeMQLoggerFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.concurrent.ThreadSafe;
import kubemq.Kubemq;

/**
 * Manages upstream queue message sending over a gRPC bidirectional stream.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Multiple threads may call {@link
 * #sendQueuesMessage(QueueMessage)} concurrently. Stream writes are serialized via {@code
 * synchronized(sendRequestLock)}.
 */
@ThreadSafe
@Internal
public class QueueUpstreamHandler {

  private static final KubeMQLogger LOG = KubeMQLoggerFactory.getLogger(QueueUpstreamHandler.class);

  private final KubeMQClient kubeMQClient;
  private final AtomicBoolean isConnected = new AtomicBoolean(false);

  // The lock below ensures that writes to requestsObserver happen one at a time
  // when actually sending the messages upstream.
  private final Object sendRequestLock = new Object();

  private volatile StreamObserver<Kubemq.QueuesUpstreamRequest> requestsObserver;
  private volatile StreamObserver<Kubemq.QueuesUpstreamResponse> responsesObserver;

  private final Map<String, CompletableFuture<QueueSendResult>> pendingResponses =
      new ConcurrentHashMap<>();
  private final Map<String, CompletableFuture<List<QueueSendResult>>> pendingBatchResponses =
      new ConcurrentHashMap<>();

  private static final long REQUEST_TIMEOUT_MS = 60000; // 60 seconds
  private final Map<String, Long> requestTimestamps = new ConcurrentHashMap<>();

  private static final ScheduledExecutorService CLEANUP_EXECUTOR =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "kubemq-upstream-cleanup");
            t.setDaemon(true);
            return t;
          });

  private static volatile boolean cleanupStarted = false;

  // Expose executor for shutdown hook
  public static ScheduledExecutorService getCleanupExecutor() {
    return CLEANUP_EXECUTOR;
  }

  /**
   * Constructs a new instance.
   *
   * @param kubeMQClient the kube mqclient
   */
  public QueueUpstreamHandler(KubeMQClient kubeMQClient) {
    this.kubeMQClient = kubeMQClient;
  }

  /** Establishes the connection. */
  public void connect() {
    // Double-checked locking to avoid repeated synchronization
    if (isConnected.get()) {
      return;
    }
    synchronized (this) {
      if (isConnected.get()) {
        return;
      }
      try {
        startCleanupTask();

        responsesObserver =
            new StreamObserver<Kubemq.QueuesUpstreamResponse>() {
              @Override
              public void onNext(Kubemq.QueuesUpstreamResponse receivedResponse) {
                String refRequestID = receivedResponse.getRefRequestID();

                CompletableFuture<QueueSendResult> singleFuture =
                    pendingResponses.remove(refRequestID);

                if (singleFuture != null) {
                  requestTimestamps.remove(refRequestID);
                  if (receivedResponse.getIsError()) {
                    singleFuture.complete(
                        QueueSendResult.builder()
                            .id(refRequestID)
                            .isError(true)
                            .error(receivedResponse.getError())
                            .build());
                    return;
                  }
                  if (receivedResponse.getResultsCount() == 0) {
                    singleFuture.complete(
                        QueueSendResult.builder()
                            .id(refRequestID)
                            .isError(true)
                            .error("no results")
                            .build());
                    return;
                  }
                  Kubemq.SendQueueMessageResult result = receivedResponse.getResults(0);
                  QueueSendResult queueSendResult = new QueueSendResult().decode(result);
                  singleFuture.complete(queueSendResult);
                  return;
                }

                CompletableFuture<List<QueueSendResult>> batchFuture =
                    pendingBatchResponses.remove(refRequestID);
                if (batchFuture != null) {
                  requestTimestamps.remove(refRequestID);
                  if (receivedResponse.getIsError()) {
                    List<QueueSendResult> errorResults = new ArrayList<>();
                    errorResults.add(
                        QueueSendResult.builder()
                            .id(refRequestID)
                            .isError(true)
                            .error(receivedResponse.getError())
                            .build());
                    batchFuture.complete(errorResults);
                    return;
                  }
                  List<QueueSendResult> results = new ArrayList<>();
                  for (Kubemq.SendQueueMessageResult r : receivedResponse.getResultsList()) {
                    results.add(new QueueSendResult().decode(r));
                  }
                  batchFuture.complete(results);
                }
              }

              @Override
              public void onError(Throwable t) {
                LOG.error("Error in QueuesUpstreamResponse StreamObserver", t);
                closeStreamWithError(t.getMessage());
              }

              @Override
              public void onCompleted() {
                LOG.info("QueuesUpstreamResponse onCompleted.");
                closeStreamWithError("Stream completed");
              }
            };

        // Create the request observer from the KubeMQ client
        requestsObserver = kubeMQClient.getAsyncClient().queuesUpstream(responsesObserver);
        isConnected.set(true);
      } catch (Exception e) {
        LOG.error("Error initializing QueuesUpstreamResponse StreamObserver", e);
        isConnected.set(false);
      }
    }
  }

  /** Completes all pending futures with an error and marks the handler as disconnected. */
  private void closeStreamWithError(String message) {
    isConnected.set(false);
    pendingResponses.forEach(
        (id, future) -> {
          future.complete(QueueSendResult.builder().error(message).isError(true).build());
        });
    pendingResponses.clear();
    pendingBatchResponses.forEach(
        (id, future) -> {
          future.complete(
              Collections.singletonList(
                  QueueSendResult.builder().error(message).isError(true).build()));
        });
    pendingBatchResponses.clear();
    requestTimestamps.clear();
  }

  private void startCleanupTask() {
    if (!cleanupStarted) {
      synchronized (QueueUpstreamHandler.class) {
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
                entry
                    .getValue()
                    .complete(
                        QueueSendResult.builder()
                            .error("Request timed out after " + REQUEST_TIMEOUT_MS + "ms")
                            .isError(true)
                            .build());
                requestTimestamps.remove(entry.getKey());
                LOG.warn("Cleaned up stale pending request", "requestId", entry.getKey());
                return true;
              }
              return false;
            });
    pendingBatchResponses
        .entrySet()
        .removeIf(
            entry -> {
              Long timestamp = requestTimestamps.get(entry.getKey());
              if (timestamp != null && (now - timestamp) > REQUEST_TIMEOUT_MS) {
                entry
                    .getValue()
                    .complete(
                        Collections.singletonList(
                            QueueSendResult.builder()
                                .error("Batch request timed out after " + REQUEST_TIMEOUT_MS + "ms")
                                .isError(true)
                                .build()));
                requestTimestamps.remove(entry.getKey());
                LOG.warn("Cleaned up stale pending batch request", "requestId", entry.getKey());
                return true;
              }
              return false;
            });
  }

  /**
   * Asynchronously send a single message upstream and return a CompletableFuture.
   *
   * @param queueMessage the queue message
   * @return the result
   */
  public CompletableFuture<QueueSendResult> sendQueuesMessageAsync(QueueMessage queueMessage) {
    String requestId = generateRequestId();
    CompletableFuture<QueueSendResult> responseFuture = new CompletableFuture<>();

    pendingResponses.put(requestId, responseFuture);
    requestTimestamps.put(requestId, System.currentTimeMillis());
    try {
      Kubemq.QueuesUpstreamRequest request =
          queueMessage.encode(kubeMQClient.getClientId()).toBuilder()
              .setRequestID(requestId)
              .build();
      sendRequest(request);
    } catch (Exception e) {
      // Clean up on failure
      pendingResponses.remove(requestId);
      requestTimestamps.remove(requestId);
      LOG.error("Error sending queue message", e);
      responseFuture.completeExceptionally(e);
    }
    return responseFuture;
  }

  /**
   * Synchronous method that blocks on the async call with timeout.
   *
   * @param queueMessage the queue message
   * @return the result
   */
  public QueueSendResult sendQueuesMessage(QueueMessage queueMessage) {
    try {
      return sendQueuesMessageAsync(queueMessage)
          .get(kubeMQClient.getRequestTimeoutSeconds(), TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      LOG.error("Timeout waiting for send Queue Message response");
      QueueSendResult result = new QueueSendResult();
      result.setError(
          "Request timed out after " + kubeMQClient.getRequestTimeoutSeconds() + " seconds");
      result.setIsError(true);
      return result;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); // Preserve interrupt status
      LOG.error("Interrupted waiting for send Queue Message response");
      QueueSendResult result = new QueueSendResult();
      result.setError("Request interrupted");
      result.setIsError(true);
      return result;
    } catch (Exception e) {
      LOG.error("Error waiting for send Queue Message response", e);
      throw io.kubemq.sdk.exception.KubeMQException.newBuilder()
          .code(io.kubemq.sdk.exception.ErrorCode.UNKNOWN_ERROR)
          .category(io.kubemq.sdk.exception.ErrorCategory.FATAL)
          .retryable(false)
          .message("Failed to get response: " + e.getMessage())
          .operation("sendQueuesMessage")
          .cause(e)
          .build();
    }
  }

  /**
   * Sends multiple messages in a single QueuesUpstreamRequest. Uses the same stream as
   * single-message sends.
   *
   * @param queueMessages the queue messages
   * @return the result
   */
  public List<QueueSendResult> sendQueuesMessages(List<QueueMessage> queueMessages) {
    String requestId = generateRequestId();
    CompletableFuture<List<QueueSendResult>> responseFuture = new CompletableFuture<>();

    pendingBatchResponses.put(requestId, responseFuture);
    requestTimestamps.put(requestId, System.currentTimeMillis());

    try {
      Kubemq.QueuesUpstreamRequest.Builder requestBuilder =
          Kubemq.QueuesUpstreamRequest.newBuilder().setRequestID(requestId);

      for (QueueMessage msg : queueMessages) {
        requestBuilder.addMessages(msg.encodeMessage(kubeMQClient.getClientId()));
      }

      sendRequest(requestBuilder.build());

      return responseFuture.get(kubeMQClient.getRequestTimeoutSeconds(), TimeUnit.SECONDS);

    } catch (TimeoutException e) {
      pendingBatchResponses.remove(requestId);
      requestTimestamps.remove(requestId);
      LOG.error("Timeout waiting for batch send response");
      List<QueueSendResult> errorResults = new ArrayList<>();
      for (int i = 0; i < queueMessages.size(); i++) {
        QueueSendResult r = new QueueSendResult();
        r.setError(
            "Batch request timed out after "
                + kubeMQClient.getRequestTimeoutSeconds()
                + " seconds");
        r.setIsError(true);
        errorResults.add(r);
      }
      return errorResults;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      pendingBatchResponses.remove(requestId);
      requestTimestamps.remove(requestId);
      LOG.error("Interrupted waiting for batch send response");
      List<QueueSendResult> errorResults = new ArrayList<>();
      for (int i = 0; i < queueMessages.size(); i++) {
        QueueSendResult r = new QueueSendResult();
        r.setError("Batch request interrupted");
        r.setIsError(true);
        errorResults.add(r);
      }
      return errorResults;
    } catch (Exception e) {
      pendingBatchResponses.remove(requestId);
      requestTimestamps.remove(requestId);
      LOG.error("Error sending batch queue messages", e);
      throw io.kubemq.sdk.exception.KubeMQException.newBuilder()
          .code(io.kubemq.sdk.exception.ErrorCode.UNKNOWN_ERROR)
          .category(io.kubemq.sdk.exception.ErrorCategory.FATAL)
          .retryable(false)
          .message("Failed to send batch: " + e.getMessage())
          .operation("sendQueuesMessages")
          .cause(e)
          .build();
    }
  }

  /**
   * Ensures the connection is established and then synchronously sends the request via the gRPC
   * stream (requestsObserver).
   *
   * @throws IllegalStateException if the stream observer is null after connection attempt
   */
  private void sendRequest(Kubemq.QueuesUpstreamRequest request) {
    if (!isConnected.get()) {
      connect();
    }
    // Serialize writes to gRPC observer
    synchronized (sendRequestLock) {
      if (requestsObserver != null) {
        try {
          requestsObserver.onNext(request);
        } catch (Exception e) {
          // Stream broke between connect check and onNext — mark disconnected
          // so the next attempt reconnects. Caller handles future completion.
          isConnected.set(false);
          throw e;
        }
      } else {
        LOG.warn("RequestsObserver is null; unable to send request.");
      }
    }
  }

  private String generateRequestId() {
    return UUID.randomUUID().toString();
  }
}
