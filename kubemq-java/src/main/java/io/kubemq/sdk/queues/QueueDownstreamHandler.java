package io.kubemq.sdk.queues;

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
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.concurrent.ThreadSafe;
import kubemq.Kubemq;

/**
 * Manages downstream queue message receiving over a gRPC bidirectional stream.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Stream writes are serialized via {@code
 * synchronized(sendRequestLock)}.
 */
@ThreadSafe
@Internal
public class QueueDownstreamHandler {

  private static final KubeMQLogger LOG =
      KubeMQLoggerFactory.getLogger(QueueDownstreamHandler.class);

  private final AtomicBoolean isConnected = new AtomicBoolean(false);
  private final Object sendRequestLock = new Object();

  private volatile StreamObserver<Kubemq.QueuesDownstreamRequest> requestsObserver = null;
  private volatile StreamObserver<Kubemq.QueuesDownstreamResponse> responsesObserver = null;

  private final KubeMQClient kubeMQClient;

  private final Map<String, CompletableFuture<QueuesPollResponse>> pendingResponses =
      new ConcurrentHashMap<>();
  private final Map<String, QueuesPollRequest> pendingRequests = new ConcurrentHashMap<>();

  private static final long REQUEST_TIMEOUT_MS = 60000; // 60 seconds
  private final Map<String, Long> requestTimestamps = new ConcurrentHashMap<>();

  private static final ScheduledExecutorService CLEANUP_EXECUTOR =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "kubemq-downstream-cleanup");
            t.setDaemon(true);
            return t;
          });

  private volatile boolean cleanupStarted = false;

  // Expose executor for shutdown hook
  public static ScheduledExecutorService getCleanupExecutor() {
    return CLEANUP_EXECUTOR;
  }

  /**
   * Constructs a new instance.
   *
   * @param kubeMQClient the kube mqclient
   */
  public QueueDownstreamHandler(KubeMQClient kubeMQClient) {
    this.kubeMQClient = kubeMQClient;
  }

  /** Establishes the connection. */
  public void connect() {
    // Double-checked to minimize synchronization overhead
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
            new StreamObserver<Kubemq.QueuesDownstreamResponse>() {
              @Override
              public void onNext(Kubemq.QueuesDownstreamResponse messageReceive) {
                String refRequestId = messageReceive.getRefRequestId();

                CompletableFuture<QueuesPollResponse> future =
                    pendingResponses.remove(refRequestId);
                QueuesPollRequest queuesPollRequest = pendingRequests.remove(refRequestId);
                requestTimestamps.remove(refRequestId);

                if (future != null && queuesPollRequest != null) {
                  QueuesPollResponse qpResp =
                      QueuesPollResponse.builder()
                          .refRequestId(refRequestId)
                          .activeOffsets(messageReceive.getActiveOffsetsList())
                          .receiverClientId(messageReceive.getTransactionId())
                          .isTransactionCompleted(messageReceive.getTransactionComplete())
                          .transactionId(messageReceive.getTransactionId())
                          .error(messageReceive.getError())
                          .isError(messageReceive.getIsError())
                          .build();

                  for (Kubemq.QueueMessage queueMessage : messageReceive.getMessagesList()) {
                    QueueMessageReceived messageReceived =
                        new QueueMessageReceived()
                            .decode(
                                queueMessage,
                                qpResp.getTransactionId(),
                                qpResp.isTransactionCompleted(),
                                qpResp.getReceiverClientId(),
                                queuesPollRequest.getVisibilitySeconds(),
                                queuesPollRequest.isAutoAckMessages(),
                                qpResp);
                    qpResp.getMessages().add(messageReceived);
                  }
                  future.complete(qpResp);
                }
              }

              @Override
              public void onError(Throwable t) {
                LOG.error("Error in QueuesDownstreamResponse StreamObserver", t);
                closeStreamWithError(t.getMessage(), t);
              }

              @Override
              public void onCompleted() {
                LOG.info("QueuesDownstreamResponse onCompleted.");
                closeStreamWithError("Stream completed", null);
              }
            };

        // Create the request observer from gRPC client
        requestsObserver = kubeMQClient.getAsyncClient().queuesDownstream(responsesObserver);
        isConnected.set(true);

      } catch (Exception e) {
        LOG.error("Error in QueuesDownstreamResponse StreamObserver", e);
        // Mark as disconnected so future attempts to connect can retry
        isConnected.set(false);
      }
    }
  }

  /**
   * Closes the stream when errors or onCompleted events arrive. Notifies all pending futures with
   * the given error message.
   */
  private void closeStreamWithError(String message, Throwable t) {
    isConnected.set(false);
    // Complete all futures with an error
    pendingResponses.forEach(
        (id, future) -> {
          future.complete(QueuesPollResponse.builder().error(message).isError(true).build());
        });

    pendingRequests.clear();
    pendingResponses.clear();
    requestTimestamps.clear();
  }

  private void startCleanupTask() {
    if (!cleanupStarted) {
      synchronized (this) {
        if (!cleanupStarted) {
          CLEANUP_EXECUTOR.scheduleAtFixedRate(
              () -> {
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
                                    QueuesPollResponse.builder()
                                        .error(
                                            "Request timed out after " + REQUEST_TIMEOUT_MS + "ms")
                                        .isError(true)
                                        .build());
                            pendingRequests.remove(entry.getKey());
                            requestTimestamps.remove(entry.getKey());
                            LOG.warn(
                                "Cleaned up stale pending request", "requestId", entry.getKey());
                            return true;
                          }
                          return false;
                        });
              },
              30,
              30,
              TimeUnit.SECONDS);
          cleanupStarted = true;
        }
      }
    }
  }

  /**
   * Asynchronous call that returns a CompletableFuture. Each call is independent, so multiple
   * threads calling this can run concurrently.
   *
   * @param queuesPollRequest the poll request configuration
   * @return the result
   */
  public CompletableFuture<QueuesPollResponse> receiveQueuesMessagesAsync(
      QueuesPollRequest queuesPollRequest) {
    String requestId = generateRequestId();
    CompletableFuture<QueuesPollResponse> responseFuture = new CompletableFuture<>();

    pendingResponses.put(requestId, responseFuture);
    pendingRequests.put(requestId, queuesPollRequest);
    requestTimestamps.put(requestId, System.currentTimeMillis());

    try {
      Kubemq.QueuesDownstreamRequest request =
          queuesPollRequest.encode(kubeMQClient.getClientId()).toBuilder()
              .setRequestID(requestId)
              .build();
      sendRequest(request);

    } catch (Exception e) {
      pendingRequests.remove(requestId);
      pendingResponses.remove(requestId);
      requestTimestamps.remove(requestId);
      LOG.error("Error polling message", e);
      responseFuture.completeExceptionally(e);
    }
    return responseFuture;
  }

  /**
   * Keep the existing synchronous API, but internally use the async approach with timeout. Each
   * thread calling here will block on its own future, not block all threads.
   *
   * @param queuesPollRequest the queues poll request
   * @return the result
   */
  public QueuesPollResponse receiveQueuesMessages(QueuesPollRequest queuesPollRequest) {
    try {
      CompletableFuture<QueuesPollResponse> future = receiveQueuesMessagesAsync(queuesPollRequest);
      int timeout =
          Math.max(
              queuesPollRequest.getPollWaitTimeoutInSeconds() + 5,
              kubeMQClient.getRequestTimeoutSeconds());
      QueuesPollResponse response = future.get(timeout, TimeUnit.SECONDS);
      // Let the response object handle any flow (e.g., Ack) by passing sendRequest back in
      response.complete(this::sendRequest);
      return response;
    } catch (TimeoutException e) {
      LOG.error("Timeout waiting for Queue Message response");
      return QueuesPollResponse.builder().error("Request timed out").isError(true).build();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); // Preserve interrupt status
      LOG.error("Interrupted waiting for Queue Message response");
      return QueuesPollResponse.builder().error("Request interrupted").isError(true).build();
    } catch (Exception e) {
      LOG.error("Error waiting for Queue Message response", e);
      throw io.kubemq.sdk.exception.KubeMQException.newBuilder()
          .code(io.kubemq.sdk.exception.ErrorCode.UNKNOWN_ERROR)
          .category(io.kubemq.sdk.exception.ErrorCategory.FATAL)
          .retryable(false)
          .message("Failed to get response: " + e.getMessage())
          .operation("receiveQueuesMessages")
          .cause(e)
          .build();
    }
  }

  private void sendRequest(Kubemq.QueuesDownstreamRequest request) {
    // Ensure we are connected; if not, attempt connection
    if (!isConnected.get()) {
      connect();
    }
    // Keep updating the client ID if needed
    request = request.toBuilder().setClientID(kubeMQClient.getClientId()).build();

    // Use this lock to serialize writes to the gRPC request stream
    synchronized (sendRequestLock) {
      if (requestsObserver != null) {
        requestsObserver.onNext(request);
      } else {
        LOG.warn("RequestsObserver is null; unable to send request.");
      }
    }
  }

  private String generateRequestId() {
    return UUID.randomUUID().toString();
  }
}
