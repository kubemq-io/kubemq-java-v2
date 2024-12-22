package io.kubemq.sdk.queues;

import io.grpc.stub.StreamObserver;
import kubemq.Kubemq.QueuesDownstreamRequest;
import kubemq.Kubemq.QueuesDownstreamRequestType;
import kubemq.Kubemq.QueuesDownstreamResponse;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

    private String receiverClientId;
    private int visibilitySeconds;
    @Getter
    private boolean isAutoAcked;

//    @Getter(AccessLevel.NONE)
//    @Setter(AccessLevel.NONE)
//    @Builder.Default
//    private Queue<String> msgQueue = new LinkedList<>();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private RequestSender requestSender;


    private final Map<String,String> receivedMessages = new ConcurrentHashMap<>();

    void complete(RequestSender requestSender) {
        if (isAutoAcked) {
            return;
        }
        this.requestSender = requestSender;
        for (QueueMessageReceived message : messages) {
            message.setRequestSender(requestSender);
            receivedMessages.put(message.getId(), message.getId());
        }
    }
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
        if (requestSender == null) {
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
        if (requestSender != null) {
            requestSender.send(request);
        }

        isTransactionCompleted = true;
        for (QueueMessageReceived message : messages) {
            message.markTransactionCompleted();
        }
        receivedMessages.clear();
    }



    public void markMessageCompleted(String id) {
        receivedMessages.remove(id);
        if (receivedMessages.isEmpty()) {
            isTransactionCompleted = true;
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
