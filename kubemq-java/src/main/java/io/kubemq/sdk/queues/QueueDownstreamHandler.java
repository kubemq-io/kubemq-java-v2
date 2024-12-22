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
public class QueueDownstreamHandler {

    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final Object sendRequestLock = new Object();

    private volatile StreamObserver<Kubemq.QueuesDownstreamRequest> requestsObserver = null;
    private volatile StreamObserver<Kubemq.QueuesDownstreamResponse> responsesObserver = null;

    private final KubeMQClient kubeMQClient;

    private final Map<String, CompletableFuture<QueuesPollResponse>> pendingResponses = new ConcurrentHashMap<>();
    private final Map<String, QueuesPollRequest> pendingRequests = new ConcurrentHashMap<>();

    public QueueDownstreamHandler(KubeMQClient kubeMQClient) {
        this.kubeMQClient = kubeMQClient;
    }

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
                responsesObserver = new StreamObserver<Kubemq.QueuesDownstreamResponse>() {
                    @Override
                    public void onNext(Kubemq.QueuesDownstreamResponse messageReceive) {
                        String refRequestId = messageReceive.getRefRequestId();

                        CompletableFuture<QueuesPollResponse> future = pendingResponses.remove(refRequestId);
                        QueuesPollRequest queuesPollRequest = pendingRequests.remove(refRequestId);

                        if (future != null && queuesPollRequest != null) {
                            QueuesPollResponse qpResp = QueuesPollResponse.builder()
                                    .refRequestId(refRequestId)
                                    .activeOffsets(messageReceive.getActiveOffsetsList())
                                    .receiverClientId(messageReceive.getTransactionId())
                                    .isTransactionCompleted(messageReceive.getTransactionComplete())
                                    .transactionId(messageReceive.getTransactionId())
                                    .error(messageReceive.getError())
                                    .isError(messageReceive.getIsError())
                                    .build();

                            for (Kubemq.QueueMessage queueMessage : messageReceive.getMessagesList()) {
                                QueueMessageReceived messageReceived = new QueueMessageReceived().decode(
                                        queueMessage,
                                        qpResp.getTransactionId(),
                                        qpResp.isTransactionCompleted(),
                                        qpResp.getReceiverClientId(),
                                        queuesPollRequest.getVisibilitySeconds(),
                                        queuesPollRequest.isAutoAckMessages(),
                                        qpResp
                                );
                                qpResp.getMessages().add(messageReceived);
                            }
                            future.complete(qpResp);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        log.error("Error in QueuesDownstreamResponse StreamObserver: ", t);
                        closeStreamWithError(t.getMessage(), t);
                    }

                    @Override
                    public void onCompleted() {
                        log.info("QueuesDownstreamResponse onCompleted.");
                        closeStreamWithError("Stream completed", null);
                    }
                };

                // Create the request observer from gRPC client
                requestsObserver = kubeMQClient.getAsyncClient().queuesDownstream(responsesObserver);
                isConnected.set(true);

            } catch (Exception e) {
                log.error("Error in QueuesDownstreamResponse StreamObserver: ", e);
                // Mark as disconnected so future attempts to connect can retry
                isConnected.set(false);
            }
        }
    }

    /**
     * Closes the stream when errors or onCompleted events arrive.
     * Notifies all pending futures with the given error message.
     */
    private void closeStreamWithError(String message, Throwable t) {
        isConnected.set(false);
        // Complete all futures with an error
        pendingResponses.forEach((id, future) -> {
            future.complete(
                    QueuesPollResponse.builder()
                            .error(message)
                            .isError(true)
                            .build()
            );
        });

        pendingRequests.clear();
        pendingResponses.clear();
    }

    /**
     * Asynchronous call that returns a CompletableFuture. Each call is independent,
     * so multiple threads calling this can run concurrently.
     */
    private CompletableFuture<QueuesPollResponse> receiveQueuesMessagesAsync(QueuesPollRequest queuesPollRequest) {
        String requestId = generateRequestId();
        CompletableFuture<QueuesPollResponse> responseFuture = new CompletableFuture<>();

        pendingResponses.put(requestId, responseFuture);
        pendingRequests.put(requestId, queuesPollRequest);

        try {
            Kubemq.QueuesDownstreamRequest request = queuesPollRequest.encode(kubeMQClient.getClientId())
                    .toBuilder()
                    .setRequestID(requestId)
                    .build();
            sendRequest(request);

        } catch (Exception e) {
            pendingRequests.remove(requestId);
            pendingResponses.remove(requestId);
            log.error("Error polling message: ", e);
            responseFuture.completeExceptionally(e);
        }
        return responseFuture;
    }

    /**
     * Keep the existing synchronous API, but internally use the async approach.
     * Each thread calling here will block on its own future, not block all threads.
     */
    public QueuesPollResponse receiveQueuesMessages(QueuesPollRequest queuesPollRequest) {
        try {
            CompletableFuture<QueuesPollResponse> future = receiveQueuesMessagesAsync(queuesPollRequest);
            QueuesPollResponse response = future.get();
            // Let the response object handle any flow (e.g., Ack) by passing sendRequest back in
            response.complete(this::sendRequest);
            return response;
        } catch (Exception e) {
            log.error("Error waiting for Queue Message response: ", e);
            throw new RuntimeException("Failed to get response", e);
        }
    }

    private void sendRequest(Kubemq.QueuesDownstreamRequest request) {
        // Ensure we are connected; if not, attempt connection
        if (!isConnected.get()) {
            connect();
        }
        // Keep updating the client ID if needed
        request = request.toBuilder()
                .setClientID(kubeMQClient.getClientId())
                .build();

        // Use this lock to serialize writes to the gRPC request stream
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
