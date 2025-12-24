package io.kubemq.sdk.pubsub;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import kubemq.Kubemq;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Slf4j
public class EventStreamHelper {

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
                log.debug("Received EventSendResult: '{}'", result);
                String eventId = result.getEventID();
                CompletableFuture<EventSendResult> future = pendingResponses.remove(eventId);
                requestTimestamps.remove(eventId);
                if (future != null) {
                    future.complete(EventSendResult.decode(result));
                } else {
                    log.warn("Received response for unknown event ID: {}", eventId);
                }
            }
            @Override
            public void onError(Throwable t) {
                log.error("Error in EventSendResult: ", t);
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
                                log.warn("Cleaned up stale event request: {}", entry.getKey());
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

    public void sendEventMessage(KubeMQClient kubeMQClient, Kubemq.Event event) {
        if (queuesUpStreamHandler == null) {
            queuesUpStreamHandler = kubeMQClient.getAsyncClient().sendEventsStream(resultStreamObserver);
            startCleanupTask();
        }
        queuesUpStreamHandler.onNext(event);
        log.debug("Event Message sent");
    }

    public EventSendResult sendEventStoreMessage(KubeMQClient kubeMQClient, Kubemq.Event event) {
        if (queuesUpStreamHandler == null) {
            queuesUpStreamHandler = kubeMQClient.getAsyncClient().sendEventsStream(resultStreamObserver);
            startCleanupTask();
        }

        String requestId = UUID.randomUUID().toString();
        CompletableFuture<EventSendResult> responseFuture = new CompletableFuture<>();
        pendingResponses.put(requestId, responseFuture);
        requestTimestamps.put(requestId, System.currentTimeMillis());

        // Set requestId on the event before sending
        Kubemq.Event eventWithId = event.toBuilder().setEventID(requestId).build();
        queuesUpStreamHandler.onNext(eventWithId);
        log.debug("Event store Message sent with ID {}, waiting for response", requestId);

        try {
            return responseFuture.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pendingResponses.remove(requestId);
            requestTimestamps.remove(requestId);
            log.error("Timeout waiting for response for event ID: {}", requestId);
            EventSendResult timeoutResult = new EventSendResult();
            timeoutResult.setError("Request timed out after 30 seconds");
            timeoutResult.setSent(false);
            return timeoutResult;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pendingResponses.remove(requestId);
            requestTimestamps.remove(requestId);
            log.error("Interrupted waiting for response: ", e);
            EventSendResult interruptedResult = new EventSendResult();
            interruptedResult.setError("Request interrupted");
            interruptedResult.setSent(false);
            return interruptedResult;
        } catch (Exception e) {
            pendingResponses.remove(requestId);
            requestTimestamps.remove(requestId);
            log.error("Error waiting for response: ", e);
            throw new RuntimeException("Failed to get response", e);
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
