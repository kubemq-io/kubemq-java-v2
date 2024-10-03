package io.kubemq.sdk.queues;

import io.grpc.stub.StreamObserver;
import kubemq.Kubemq.QueuesDownstreamRequest;
import kubemq.Kubemq.QueuesDownstreamRequestType;
import kubemq.Kubemq.QueuesDownstreamResponse;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

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
    @Getter
    private boolean isTransactionCompleted;
    @Builder.Default
    private List<Long> activeOffsets = new ArrayList<>();
    private StreamObserver<QueuesDownstreamRequest> responseHandler;
    private String receiverClientId;
    private int visibilitySeconds;
    @Getter
    private boolean isAutoAcked;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Queue<String> msgQueue = new LinkedList<>();


    public void ackAll() {
        doOperation(QueuesDownstreamRequestType.AckAll, null);
        this.closeStream();
    }

    public void rejectAll() {
        doOperation(QueuesDownstreamRequestType.NAckAll, null);
        this.closeStream();
    }

    public void reQueueAll(String channel) {
        doOperation(QueuesDownstreamRequestType.ReQueueAll, channel);
        this.closeStream();
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

    public  QueuesPollResponse decode(
            QueuesDownstreamResponse response,
            String receiverClientId,
            StreamObserver<QueuesDownstreamRequest> responseHandler,
            int visibilitySeconds,
            boolean isAutoAcked
    ) {
        //QueuesPollResponse pollResponse = new QueuesPollResponse();
        this.refRequestId = response.getRefRequestId();
        this.transactionId = response.getTransactionId();
        this.error = response.getError();
        this.isError = response.getIsError();
        this.isTransactionCompleted = response.getTransactionComplete();
        this.activeOffsets.addAll(response.getActiveOffsetsList());
        this.responseHandler = responseHandler;
        this.receiverClientId = receiverClientId;
        this.visibilitySeconds = visibilitySeconds;
        this.isAutoAcked = isAutoAcked;

        response.getMessagesList().forEach(message -> {
            QueueMessageReceived receivedMessage = new QueueMessageReceived().decode(
                    message,
                    response.getTransactionId(),
                    response.getTransactionComplete(),
                    receiverClientId,
                    responseHandler,
                    visibilitySeconds,
                    isAutoAcked,
                    this
            );
            this.messages.add(receivedMessage);
            this.msgQueue.add(receivedMessage.getId());
        });
        //If auto ack is true OR auto ack is false and no messages received, close stream after receiving.
        if(isAutoAcked == true || this.messages.isEmpty()){
            this.closeStream();
        }

        return this;
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

    private void closeStream(){
        if(responseHandler != null){
            responseHandler.onCompleted();
        }
        msgQueue.clear();
    }

    public void checkAndCloseStream(String id) {
        if (msgQueue.contains(id)) {
            msgQueue.remove(id);
        }
        // If the queue is empty after processing, trigger another function
        if (msgQueue.isEmpty()) {
            this.closeStream();
        }
    }

    public boolean isError() {
        return this.isError;
    }

    @Override
    public String toString() {
        return String.format(
                "QueuesPollResponse: refRequestId=%s, transactionId=%s, error=%s, isError=%s, " +
                        "isTransactionCompleted=%s, activeOffsets=%s, messages=%s",
                refRequestId, transactionId, error, isError, isTransactionCompleted, activeOffsets, messages);
    }

}
