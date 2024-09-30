package io.kubemq.sdk.queues;

import io.grpc.stub.StreamObserver;
import kubemq.Kubemq.QueuesDownstreamRequest;
import kubemq.Kubemq.QueuesDownstreamRequestType;
import kubemq.Kubemq.QueuesDownstreamResponse;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class QueuesPollResponse {
    private String refRequestId;
    private String transactionId;
    @Builder.Default
    private List<QueueMessageReceived> messages = new ArrayList<>();
    private String error;
    private boolean isError;
    private boolean isTransactionCompleted;
    @Builder.Default
    private List<Long> activeOffsets = new ArrayList<>();
    private StreamObserver<QueuesDownstreamRequest> responseHandler;
    private String receiverClientId;
    private int visibilitySeconds;
    private boolean isAutoAcked;

    public void ackAll() {
        doOperation(QueuesDownstreamRequestType.AckAll, null);
    }

    public void rejectAll() {
        doOperation(QueuesDownstreamRequestType.NAckAll, null);
    }

    public void reQueueAll(String channel) {
        doOperation(QueuesDownstreamRequestType.ReQueueAll, channel);
    }

    private void doOperation(QueuesDownstreamRequestType requestType, String reQueueChannel) {
        if (isAutoAcked) {
            throw new IllegalStateException("Transaction was set with auto ack, transaction operations are not allowed");
        }
        if (isTransactionCompleted) {
            throw new IllegalStateException("Transaction is already completed");
        }
        if (responseHandler == null) {
            throw new IllegalStateException("Response handler is not set");
        }

            QueuesDownstreamRequest.Builder requestBuilder = QueuesDownstreamRequest.newBuilder()
                    .setRequestID(UUID.randomUUID().toString())
                    .setClientID(receiverClientId)
                    .setRequestTypeData(requestType)
                    .setRefTransactionId(transactionId)
                    .addAllSequenceRange(activeOffsets);

            if (reQueueChannel != null && requestType == QueuesDownstreamRequestType.ReQueueAll) {
                requestBuilder.setReQueueChannel(reQueueChannel);
            }

            QueuesDownstreamRequest request = requestBuilder.build();
            this.addTaskToThreadSafeQueue(request);

            isTransactionCompleted = true;
            // Mark all messages as completed
            for (QueueMessageReceived message : messages) {
                message.markTransactionCompleted();
            }
    }

    public static QueuesPollResponse decode(
            QueuesDownstreamResponse response,
            String receiverClientId,
            StreamObserver<QueuesDownstreamRequest> responseHandler,
            int visibilitySeconds,
            boolean isAutoAcked
    ) {
        QueuesPollResponse pollResponse = new QueuesPollResponse();
        pollResponse.refRequestId = response.getRefRequestId();
        pollResponse.transactionId = response.getTransactionId();
        pollResponse.error = response.getError();
        pollResponse.isError = response.getIsError();
        pollResponse.isTransactionCompleted = response.getTransactionComplete();
        pollResponse.activeOffsets.addAll(response.getActiveOffsetsList());
        pollResponse.responseHandler = responseHandler;
        pollResponse.receiverClientId = receiverClientId;
        pollResponse.visibilitySeconds = visibilitySeconds;
        pollResponse.isAutoAcked = isAutoAcked;

        response.getMessagesList().forEach(message -> {
            QueueMessageReceived receivedMessage = QueueMessageReceived.decode(
                    message,
                    response.getTransactionId(),
                    response.getTransactionComplete(),
                    receiverClientId,
                    responseHandler,
                    visibilitySeconds,
                    isAutoAcked
            );
            pollResponse.messages.add(receivedMessage);
        });

        return pollResponse;
    }

    @Override
    public String toString() {
        return String.format(
                "QueuesPollResponse: refRequestId=%s, transactionId=%s, error=%s, isError=%s, " +
                        "isTransactionCompleted=%s, activeOffsets=%s, messages=%s",
                refRequestId, transactionId, error, isError, isTransactionCompleted, activeOffsets, messages);
    }

    // Method to add tasks to the queue
    private void addTaskToThreadSafeQueue(QueuesDownstreamRequest request) {
        QueueDownStreamProcessor.addTask(() -> {
            synchronized (responseHandler) {
                try {
                    responseHandler.onNext(request);
                    log.debug("{} message: {}", request.getRequestTypeData(), request.getRequestID());
                } catch (Exception e) {
                    log.error("Error processing {}: {}", request.getRequestTypeData(), e.getMessage());
                }
            }
        });
    }
    public boolean isError() {
        return this.isError;
    }
}
