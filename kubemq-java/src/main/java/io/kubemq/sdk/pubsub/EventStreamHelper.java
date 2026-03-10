package io.kubemq.sdk.pubsub;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.Internal;
import io.kubemq.sdk.observability.KubeMQLogger;
import io.kubemq.sdk.observability.KubeMQLoggerFactory;
import kubemq.Kubemq;
import lombok.Getter;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Helper for sending events and event store messages over a gRPC stream.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Multiple threads may call
 * {@link #sendEventMessage} and {@link #sendEventStoreMessage} concurrently.
 * Stream handler initialization is synchronized to prevent race conditions.</p>
 */
@ThreadSafe
@Internal
public class EventStreamHelper {

    private static final KubeMQLogger log = KubeMQLoggerFactory.getLogger(EventStreamHelper.class);

    private StreamObserver<Kubemq.Event> queuesUpStreamHandler = null;
    @Getter
    private StreamObserver<Kubemq.Result> resultStreamObserver;

    private final Map<String, CompletableFuture<EventSendResult>> pendingResponses = new ConcurrentHashMap<>();
    private final Map<String, Long> requestTimestamps = new ConcurrentHashMap<>();

    private static final long REQUEST_TIMEOUT_MS = 60000; // 60 seconds

    // Shared cleanup executor for all instances
    private static final ScheduledExecutorService cleanupExecutor =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kubemq-event-cleanup");
            t.setDaemon(true);
            return t;
        });

    private volatile boolean cleanupStarted = false;

    public EventStreamHelper() {
        this.resultStreamObserver = new StreamObserver<Kubemq.Result>() {
            @Override
            public void onNext(Kubemq.Result result) {
                log.debug("Received EventSendResult", "eventId", result.getEventID());
                String eventId = result.getEventID();
                CompletableFuture<EventSendResult> future = pendingResponses.remove(eventId);
                requestTimestamps.remove(eventId);
                if (future != null) {
                    future.complete(EventSendResult.decode(result));
                } else {
                    log.warn("Received response for unknown event ID", "eventId", eventId);
                }
            }
            @Override
            public void onError(Throwable t) {
                log.error("Error in EventSendResult", t);
                // Complete all pending futures with error
                pendingResponses.forEach((id, future) -> {
                    EventSendResult sendResult = new EventSendResult();
                    sendResult.setError(t.getMessage());
                    future.complete(sendResult);
                });
                pendingResponses.clear();
                requestTimestamps.clear();
            }
            @Override
            public void onCompleted() {
                log.debug("EventSendResult onCompleted.");
            }
        };
    }

    private void startCleanupTask() {
        if (!cleanupStarted) {
            synchronized (this) {
                if (!cleanupStarted) {
                    cleanupExecutor.scheduleAtFixedRate(() -> {
                        long now = System.currentTimeMillis();
                        pendingResponses.entrySet().removeIf(entry -> {
                            Long timestamp = requestTimestamps.get(entry.getKey());
                            if (timestamp != null && (now - timestamp) > REQUEST_TIMEOUT_MS) {
                                EventSendResult timeoutResult = new EventSendResult();
                                timeoutResult.setError("Request timed out after " + REQUEST_TIMEOUT_MS + "ms");
                                timeoutResult.setSent(false);
                                entry.getValue().complete(timeoutResult);
                                requestTimestamps.remove(entry.getKey());
                                log.warn("Cleaned up stale event request", "requestId", entry.getKey());
                                return true;
                            }
                            return false;
                        });
                    }, 30, 30, TimeUnit.SECONDS);
                    cleanupStarted = true;
                }
            }
        }
    }

    // Expose executor for shutdown hook
    public static ScheduledExecutorService getCleanupExecutor() {
        return cleanupExecutor;
    }

    public synchronized void sendEventMessage(KubeMQClient kubeMQClient, Kubemq.Event event) {
        if (queuesUpStreamHandler == null) {
            queuesUpStreamHandler = kubeMQClient.getAsyncClient().sendEventsStream(resultStreamObserver);
            startCleanupTask();
        }
        queuesUpStreamHandler.onNext(event);
        log.debug("Event Message sent");
    }

    /**
     * Sends an event store message asynchronously.
     *
     * @return a CompletableFuture that completes with the send result
     */
    public CompletableFuture<EventSendResult> sendEventStoreMessageAsync(
            KubeMQClient kubeMQClient, Kubemq.Event event) {
        synchronized (this) {
            if (queuesUpStreamHandler == null) {
                queuesUpStreamHandler = kubeMQClient.getAsyncClient()
                    .sendEventsStream(resultStreamObserver);
                startCleanupTask();
            }
        }

        String requestId = UUID.randomUUID().toString();
        CompletableFuture<EventSendResult> responseFuture = new CompletableFuture<>();
        pendingResponses.put(requestId, responseFuture);
        requestTimestamps.put(requestId, System.currentTimeMillis());

        Kubemq.Event eventWithId = event.toBuilder().setEventID(requestId).build();
        synchronized (this) {
            queuesUpStreamHandler.onNext(eventWithId);
        }
        log.debug("Event store message sent async", "requestId", requestId);

        return responseFuture;
    }

    public EventSendResult sendEventStoreMessage(KubeMQClient kubeMQClient, Kubemq.Event event) {
        try {
            return sendEventStoreMessageAsync(kubeMQClient, event)
                .get(kubeMQClient.getRequestTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("Timeout waiting for response");
            EventSendResult timeoutResult = new EventSendResult();
            timeoutResult.setError("Request timed out after " + kubeMQClient.getRequestTimeoutSeconds() + " seconds");
            timeoutResult.setSent(false);
            return timeoutResult;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted waiting for response", e);
            EventSendResult interruptedResult = new EventSendResult();
            interruptedResult.setError("Request interrupted");
            interruptedResult.setSent(false);
            return interruptedResult;
        } catch (Exception e) {
            log.error("Error waiting for response", e);
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
