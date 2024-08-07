package io.kubemq.sdk.queues;

import io.grpc.stub.StreamObserver;
import kubemq.Kubemq.QueuesDownstreamRequest;
import kubemq.Kubemq.QueuesDownstreamRequestType;
import kubemq.Kubemq.QueuesDownstreamResponse;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
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

    public void ackAll() {
        if (isTransactionCompleted) {
            throw new IllegalStateException("Transaction is already completed");
        }
        QueuesDownstreamRequest request = QueuesDownstreamRequest.newBuilder()
                .setRequestID(UUID.randomUUID().toString())
                .setClientID(receiverClientId)
                .setRequestTypeData(QueuesDownstreamRequestType.AckAll)
                .setRefTransactionId(transactionId)
                .addAllSequenceRange(activeOffsets)
                .build();
        if(responseHandler != null)
        responseHandler.onNext(request);
    }

    public void rejectAll() {
        if (isTransactionCompleted) {
            throw new IllegalStateException("Transaction is already completed");
        }
        QueuesDownstreamRequest request = QueuesDownstreamRequest.newBuilder()
                .setRequestID(UUID.randomUUID().toString())
                .setClientID(receiverClientId)
                .setRequestTypeData(QueuesDownstreamRequestType.NAckAll)
                .setRefTransactionId(transactionId)
                .addAllSequenceRange(activeOffsets)
                .build();
        if(responseHandler != null)
         responseHandler.onNext(request);
    }

    public void reQueueAll(String channel) {
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("Re-queue channel cannot be empty");
        }
        QueuesDownstreamRequest request = QueuesDownstreamRequest.newBuilder()
                .setRequestID(UUID.randomUUID().toString())
                .setClientID(receiverClientId)
                .setRequestTypeData(QueuesDownstreamRequestType.ReQueueAll)
                .setRefTransactionId(transactionId)
                .addAllSequenceRange(activeOffsets)
                .setReQueueChannel(channel)
                .build();
        if(responseHandler != null)
         responseHandler.onNext(request);
    }

    public QueuesPollResponse decode(
            QueuesDownstreamResponse response,
            String receiverClientId,
            StreamObserver<QueuesDownstreamRequest> responseHandler) {
        this.refRequestId = response.getRefRequestId();
        this.transactionId = response.getTransactionId();
        this.error = response.getError();
        this.isError = response.getIsError();
        this.isTransactionCompleted = response.getTransactionComplete();
        this.activeOffsets.addAll(response.getActiveOffsetsList());
        this.responseHandler = responseHandler;
        this.receiverClientId = receiverClientId;
        response.getMessagesList().forEach(message -> {
            QueueMessageReceived receivedMessage = new QueueMessageReceived().decode(
                    message,
                    response.getTransactionId(),
                    response.getTransactionComplete(),
                    receiverClientId,
                    responseHandler);
            this.messages.add(receivedMessage);
        });
        return this;
    }

    @Override
    public String toString() {
        return String.format(
                "QueuesPollResponse: refRequestId=%s, transactionId=%s, error=%s, isError=%s, " +
                        "isTransactionCompleted=%s, activeOffsets=%s, messages=%s",
                refRequestId, transactionId, error, isError, isTransactionCompleted, activeOffsets, messages);
    }


    public void setRefRequestId(String refRequestId) {
        this.refRequestId = refRequestId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public void setMessages(List<QueueMessageReceived> messages) {
        this.messages = messages;
    }

    public void setError(String error) {
        this.error = error;
    }

    public void setIsError(boolean error) {
        this.isError = error;
    }

    public boolean getIsError(){
        return this.isError;
    }

    public boolean isError(){
        return this.isError;
    }

    public void setTransactionCompleted(boolean transactionCompleted) {
        isTransactionCompleted = transactionCompleted;
    }

    public void setActiveOffsets(List<Long> activeOffsets) {
        this.activeOffsets = activeOffsets;
    }

    public void setResponseHandler(StreamObserver<QueuesDownstreamRequest> responseHandler) {
        this.responseHandler = responseHandler;
    }

    public void setReceiverClientId(String receiverClientId) {
        this.receiverClientId = receiverClientId;
    }
}
