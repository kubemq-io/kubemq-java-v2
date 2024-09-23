package io.kubemq.sdk.queues;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import kubemq.Kubemq;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class QueueStreamHelper {

    private StreamObserver<Kubemq.QueuesUpstreamRequest> queuesUpStreamHandler = null;
    private StreamObserver<Kubemq.QueuesDownstreamRequest> queuesDownstreamHandler = null;
    private CompletableFuture<QueueSendResult> futureResponse;

    public QueueStreamHelper() {
        this.futureResponse = new CompletableFuture<>();
    }

    public QueueSendResult sendMessage(KubeMQClient kubeMQClient, Kubemq.QueuesUpstreamRequest queueMessage) {
        // Initialize the upstream handler once for the entire session
        if (queuesUpStreamHandler == null) {
            StreamObserver<Kubemq.QueuesUpstreamResponse> request = new StreamObserver<Kubemq.QueuesUpstreamResponse>() {
                @Override
                public void onNext(Kubemq.QueuesUpstreamResponse messageReceive) {
                    log.debug("QueuesUpstreamResponse Received Message send result: '{}'", messageReceive);
                    // Handle the response message
                    QueueSendResult qsr = new QueueSendResult();
                    if (!messageReceive.getIsError()) {
                        qsr.decode(messageReceive.getResults(0));
                    } else {
                        qsr.setIsError(true);
                        qsr.setError(messageReceive.getError());
                    }
                    futureResponse.complete(qsr);
                }

                @Override
                public void onError(Throwable t) {
                    log.error("Error in QueuesUpstreamResponse Error message sending: ", t);
                    QueueSendResult qpResp = QueueSendResult.builder()
                            .error(t.getMessage())
                            .isError(true).build();
                    futureResponse.complete(qpResp);
                }

                @Override
                public void onCompleted() {
                    log.debug("QueuesUpstreamResponse onCompleted.");
                }
            };

            queuesUpStreamHandler = kubeMQClient.getAsyncClient().queuesUpstream(request);
        }

        // Synchronize sending messages to avoid concurrent issues
        synchronized (this) {
            try {
                log.debug("Sending message");
                queuesUpStreamHandler.onNext(queueMessage);
            } catch (Exception e) {
                log.error("Error sending message: ", e);
                throw new RuntimeException("Failed to send message", e);
            }
        }

        try {
            log.debug("Retrieving response from futureResponse.get()");
            return futureResponse.get();
        } catch (Exception e) {
            log.error("Error waiting for response: ", e);
            throw new RuntimeException("Failed to get response", e);
        }
    }


    public QueuesPollResponse receiveMessage(KubeMQClient kubeMQClient, QueuesPollRequest queuesPollRequest) {
        CompletableFuture<QueuesPollResponse> futureResponse = new CompletableFuture<>();
        if (queuesDownstreamHandler == null) {
            StreamObserver<Kubemq.QueuesDownstreamResponse> request = new StreamObserver<Kubemq.QueuesDownstreamResponse>() {

                @Override
                public void onNext(Kubemq.QueuesDownstreamResponse messageReceive) {
                    log.debug("QueuesDownstreamResponse Received Metadata: '{}'", messageReceive);
                    // Handle the downstream response
                    QueuesPollResponse qpResp = QueuesPollResponse.builder()
                            .refRequestId(messageReceive.getRefRequestId())
                            .activeOffsets(messageReceive.getActiveOffsetsList())
                            .receiverClientId(messageReceive.getTransactionId())
                            .isTransactionCompleted(messageReceive.getTransactionComplete())
                            .transactionId(messageReceive.getTransactionId())
                            .error(messageReceive.getError())
                            .isError(messageReceive.getIsError())
                            .build();
                    for (Kubemq.QueueMessage queueMessage : messageReceive.getMessagesList()) {
                        qpResp.getMessages().add(QueueMessageReceived.decode(queueMessage, qpResp.getTransactionId(),
                                qpResp.isTransactionCompleted(), qpResp.getReceiverClientId(), queuesDownstreamHandler,
                                queuesPollRequest.getVisibilitySeconds(),queuesPollRequest.isAutoAckMessages()));
                    }
                    futureResponse.complete(qpResp);
                }

                @Override
                public void onError(Throwable t) {
                    log.error("Error in QueuesDownstreamResponse StreamObserver: ", t);
                    QueuesPollResponse qpResp = QueuesPollResponse.builder()
                            .error(t.getMessage())
                            .isError(true)
                            .build();
                    futureResponse.complete(qpResp);
                }

                @Override
                public void onCompleted() {
                    log.debug("QueuesDownstreamResponse StreamObserver completed.");
                }
            };
            queuesDownstreamHandler = kubeMQClient.getAsyncClient().queuesDownstream(request);
        }
        // Synchronize sending messages to avoid concurrent issues
        synchronized (this) {
            try {
                queuesDownstreamHandler.onNext(queuesPollRequest.encode(kubeMQClient.getClientId()));
            } catch (Exception e) {
                log.error("Error polling message: ", e);
                throw new RuntimeException("Failed to polling message", e);
            }
        }

        try {
            return futureResponse.get();
        } catch (Exception e) {
            log.error("Error waiting for Queue Message response: ", e);
            throw new RuntimeException("Failed to get response", e);
        }
    }
}
