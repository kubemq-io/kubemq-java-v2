package io.kubemq.sdk.queues;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import kubemq.Kubemq;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class QueueUpstreamHandler {

    private final KubeMQClient kubeMQClient;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);

    // The lock below ensures that writes to requestsObserver happen one at a time
    // when actually sending the messages upstream.
    private final Object sendRequestLock = new Object();

    private volatile StreamObserver<Kubemq.QueuesUpstreamRequest> requestsObserver;
    private volatile StreamObserver<Kubemq.QueuesUpstreamResponse> responsesObserver;

    private final Map<String, CompletableFuture<QueueSendResult>> pendingResponses = new ConcurrentHashMap<>();

    public QueueUpstreamHandler(KubeMQClient kubeMQClient) {
        this.kubeMQClient = kubeMQClient;
    }

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
                responsesObserver = new StreamObserver<Kubemq.QueuesUpstreamResponse>() {
                    @Override
                    public void onNext(Kubemq.QueuesUpstreamResponse receivedResponse) {
                        String refRequestID = receivedResponse.getRefRequestID();
                        CompletableFuture<QueueSendResult> future =
                                pendingResponses.remove(refRequestID);

                        if (future != null) {
                            if (receivedResponse.getIsError()) {
                                future.complete(QueueSendResult.builder()
                                        .id(refRequestID)
                                        .isError(true)
                                        .error(receivedResponse.getError())
                                        .build());
                                return;
                            }
                            if (receivedResponse.getResultsCount() == 0) {
                                future.complete(QueueSendResult.builder()
                                        .id(refRequestID)
                                        .isError(true)
                                        .error("no results")
                                        .build());
                                return;
                            }

                            // Success path
                            Kubemq.SendQueueMessageResult result = receivedResponse.getResults(0);
                            QueueSendResult queueSendResult = new QueueSendResult().decode(result);
                            future.complete(queueSendResult);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        log.error("Error in QueuesUpstreamResponse StreamObserver: ", t);
                        closeStreamWithError(t.getMessage());
                    }

                    @Override
                    public void onCompleted() {
                        log.info("QueuesUpstreamResponse onCompleted.");
                        closeStreamWithError("Stream completed");
                    }
                };

                // Create the request observer from the KubeMQ client
                requestsObserver = kubeMQClient.getAsyncClient().queuesUpstream(responsesObserver);
                isConnected.set(true);
            } catch (Exception e) {
                log.error("Error initializing QueuesUpstreamResponse StreamObserver: ", e);
                isConnected.set(false);
            }
        }
    }

    /**
     * Completes all pending futures with an error and marks the handler as disconnected.
     */
    private void closeStreamWithError(String message) {
        isConnected.set(false);
        pendingResponses.forEach((id, future) -> {
            future.complete(
                    QueueSendResult.builder()
                            .error(message)
                            .isError(true)
                            .build()
            );
        });
        pendingResponses.clear();
    }

    /**
     * Asynchronously send a single message upstream and return a CompletableFuture.
     */
    private CompletableFuture<QueueSendResult> sendQueuesMessageAsync(QueueMessage queueMessage) {
        String requestId = generateRequestId();
        CompletableFuture<QueueSendResult> responseFuture = new CompletableFuture<>();

        pendingResponses.put(requestId, responseFuture);
        try {
            Kubemq.QueuesUpstreamRequest request = queueMessage.encode(kubeMQClient.getClientId())
                    .toBuilder()
                    .setRequestID(requestId)
                    .build();
            sendRequest(request);
        } catch (Exception e) {
            // Clean up on failure
            pendingResponses.remove(requestId);
            log.error("Error sending queue message: ", e);
            responseFuture.completeExceptionally(e);
        }
        return responseFuture;
    }

    /**
     * Synchronous method that blocks on the async call.
     */
    public QueueSendResult sendQueuesMessage(QueueMessage queueMessage) {
        try {
            return sendQueuesMessageAsync(queueMessage).get();
        } catch (Exception e) {
            log.error("Error waiting for send Queue Message response: ", e);
            throw new RuntimeException("Failed to get response", e);
        }
    }

    /**
     * Ensures the connection is established and then synchronously sends the request
     * via the gRPC stream (requestsObserver).
     */
    private void sendRequest(Kubemq.QueuesUpstreamRequest request) {
        if (!isConnected.get()) {
            connect();
        }
        // Serialize writes to gRPC observer
        synchronized (sendRequestLock) {
            if (requestsObserver != null) {
                requestsObserver.onNext(request);
            } else {
                log.warn("RequestsObserver is null; unable to send request.");
            }
        }
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString();
    }
}
