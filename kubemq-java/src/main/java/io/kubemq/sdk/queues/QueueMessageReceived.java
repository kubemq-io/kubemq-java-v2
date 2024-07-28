package io.kubemq.sdk.queues;

import io.grpc.stub.StreamObserver;
import kubemq.Kubemq.QueueMessage;
import kubemq.Kubemq.QueuesDownstreamRequest;
import kubemq.Kubemq.QueuesDownstreamRequestType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a received queue message.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class QueueMessageReceived {
    private String id;
    private String channel;
    private String metadata;
    private byte[] body;
    private String fromClientId;
    @Builder.Default
    private Map<String, String> tags = new HashMap<>();
    private Instant timestamp;
    private long sequence;
    private int receiveCount;
    private boolean isReRouted;
    private String reRouteFromQueue;
    private Instant expiredAt;
    private Instant delayedTo;
    private String transactionId;
    private boolean isTransactionCompleted;
    private StreamObserver<QueuesDownstreamRequest> responseHandler;
    private String receiverClientId;

    public void ack() {
        if (isTransactionCompleted) {
            throw new IllegalStateException("Transaction is already completed");
        }

        QueuesDownstreamRequest request = QueuesDownstreamRequest.newBuilder()
                .setRequestID(UUID.randomUUID().toString())
                .setClientID(receiverClientId)
                .setChannel(channel)
                .setRequestTypeData(QueuesDownstreamRequestType.AckRange)
                .setRefTransactionId(transactionId)
                .addSequenceRange(sequence)
                .build();

        responseHandler.onNext(request);
    }

    public void reject() {
        if (isTransactionCompleted) {
            throw new IllegalStateException("Transaction is already completed");
        }

        QueuesDownstreamRequest request = QueuesDownstreamRequest.newBuilder()
                .setRequestID(UUID.randomUUID().toString())
                .setClientID(receiverClientId)
                .setChannel(channel)
                .setRequestTypeData(QueuesDownstreamRequestType.NAckRange)
                .setRefTransactionId(transactionId)
                .addSequenceRange(sequence)
                .build();

         responseHandler.onNext(request);
    }

    public void reQueue(String channel) {
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("Re-queue channel cannot be empty");
        }

        QueuesDownstreamRequest request = QueuesDownstreamRequest.newBuilder()
                .setRequestID(UUID.randomUUID().toString())
                .setClientID(receiverClientId)
                .setChannel(this.channel)
                .setRequestTypeData(QueuesDownstreamRequestType.ReQueueRange)
                .setRefTransactionId(transactionId)
                .addSequenceRange(sequence)
                .setReQueueChannel(channel)
                .build();

        responseHandler.onNext(request);
    }

    public static QueueMessageReceived decode(
            QueueMessage message,
            String transactionId,
            boolean transactionIsCompleted,
            String receiverClientId,
            StreamObserver<QueuesDownstreamRequest> responseHandler
    ) {
        QueueMessageReceived received = new QueueMessageReceived();
        received.id = message.getMessageID();
        received.channel = message.getChannel();
        received.metadata = message.getMetadata();
        received.body = message.getBody().toByteArray();
        received.fromClientId = message.getClientID();
        received.tags = new HashMap<>(message.getTagsMap());
        received.timestamp = Instant.ofEpochSecond(message.getAttributes().getTimestamp() / 1_000_000_000L);
        received.sequence = message.getAttributes().getSequence();
        received.receiveCount = message.getAttributes().getReceiveCount();
        received.isReRouted = message.getAttributes().getReRouted();
        received.reRouteFromQueue = message.getAttributes().getReRoutedFromQueue();
        received.expiredAt = Instant.ofEpochSecond(message.getAttributes().getExpirationAt() / 1_000_000L);
        received.delayedTo = Instant.ofEpochSecond(message.getAttributes().getDelayedTo() / 1_000_000L);
        received.transactionId = transactionId;
        received.isTransactionCompleted = transactionIsCompleted;
        received.receiverClientId = receiverClientId;
        received.responseHandler = responseHandler;

        return received;
    }

    @Override
    public String toString() {
        return String.format(
                "QueueMessageReceived: id=%s, channel=%s, metadata=%s, body=%s, fromClientId=%s, timestamp=%s, sequence=%d, receiveCount=%d, isReRouted=%s, reRouteFromQueue=%s, expiredAt=%s, delayedTo=%s, transactionId=%s, isTransactionCompleted=%s, tags=%s",
                id, channel, metadata, new String(body), fromClientId, timestamp, sequence, receiveCount, isReRouted, reRouteFromQueue, expiredAt, delayedTo, transactionId, isTransactionCompleted, tags
        );
    }
}
