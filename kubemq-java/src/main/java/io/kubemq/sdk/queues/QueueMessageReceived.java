package io.kubemq.sdk.queues;

import kubemq.Kubemq.QueueMessage;
import kubemq.Kubemq.QueuesDownstreamRequest;
import kubemq.Kubemq.QueuesDownstreamRequestType;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
    private String receiverClientId;
    private int visibilitySeconds;

    @Getter
    private boolean isTransactionCompleted;
    @Getter
    private boolean isAutoAcked;

    private static final ScheduledExecutorService visibilityExecutor =
        Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "kubemq-visibility-timer");
            t.setDaemon(true);
            return t;
        });

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private ScheduledFuture<?> visibilityFuture;
    private boolean messageCompleted;
    private boolean timerExpired;

    // Expose executor for shutdown hook
    public static ScheduledExecutorService getVisibilityExecutor() {
        return visibilityExecutor;
    }

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private QueuesPollResponse queuesPollResponse;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private RequestSender requestSender;


    void setRequestSender(RequestSender requestSender) {
        this.requestSender = requestSender;
    }
    // Method to ack() a message
    public  void ack() {
        doOperation(QueuesDownstreamRequestType.AckRange, null);
    }

    // Method to reject() a message
    public  void reject() {
        doOperation(QueuesDownstreamRequestType.NAckRange, null);
    }

    // Method to reQueue() a message
    public  void reQueue(String reQueueChannel) {
        if (reQueueChannel == null || reQueueChannel.isEmpty()) {
            throw new IllegalArgumentException("Re-queue channel cannot be empty");
        }
        doOperation(QueuesDownstreamRequestType.ReQueueRange, reQueueChannel);
    }

    // Common method to perform message operations
    private void doOperation(QueuesDownstreamRequestType requestType, String reQueueChannel) {
        if (isAutoAcked) {
            throw new IllegalStateException("Auto-acked message, operations are not allowed");
        }
        if (isTransactionCompleted || messageCompleted) {
            throw new IllegalStateException("Transaction already completed");
        }
        if (requestSender == null) {
            throw new IllegalStateException("Response handler not set");
        }

            QueuesDownstreamRequest.Builder requestBuilder = QueuesDownstreamRequest.newBuilder()
                    .setRequestID(UUID.randomUUID().toString())
                    .setClientID(receiverClientId)
                    .setRequestTypeData(requestType)
                    .setRefTransactionId(transactionId)
                    .addSequenceRange(sequence);
            if (reQueueChannel != null && requestType == QueuesDownstreamRequestType.ReQueueRange) {
                requestBuilder.setReQueueChannel(reQueueChannel);
            }

            QueuesDownstreamRequest request = requestBuilder.build();
            requestSender.send(request);

            messageCompleted = true;
            if (visibilityFuture != null && !timerExpired) {
                visibilityFuture.cancel(false);
                visibilityFuture = null;
            }

        if(queuesPollResponse != null) {
            queuesPollResponse.markMessageCompleted(id);
        }
    }



    public QueueMessageReceived decode(
            QueueMessage message,
            String transactionId,
            boolean transactionIsCompleted,
            String receiverClientId,
            int visibilitySeconds,
            boolean isAutoAcked,
            QueuesPollResponse queuesPollResponse
    ) {

        this.id = message.getMessageID();
        this.channel = message.getChannel();
        this.metadata = message.getMetadata();
        this.body = message.getBody().toByteArray();
        this.fromClientId = message.getClientID();
        this.tags = new HashMap<>(message.getTagsMap());
        this.timestamp = Instant.ofEpochSecond(message.getAttributes().getTimestamp() / 1_000_000_000L);
        this.sequence = message.getAttributes().getSequence();
        this.receiveCount = message.getAttributes().getReceiveCount();
        this.isReRouted = message.getAttributes().getReRouted();
        this.reRouteFromQueue = message.getAttributes().getReRoutedFromQueue();
        this.expiredAt = Instant.ofEpochSecond(message.getAttributes().getExpirationAt() / 1_000_000L);
        this.delayedTo = Instant.ofEpochSecond(message.getAttributes().getDelayedTo() / 1_000_000L);
        this.transactionId = transactionId;
        this.isTransactionCompleted = transactionIsCompleted;
        this.receiverClientId = receiverClientId;
        this.visibilitySeconds = visibilitySeconds;
        this.isAutoAcked = isAutoAcked;
        this.queuesPollResponse = queuesPollResponse;


        if (this.visibilitySeconds > 0) {
            this.startVisibilityTimer();
        }

        return this;
    }

    private void startVisibilityTimer() {
        if (visibilitySeconds > 0 && !isAutoAcked) {
            visibilityFuture = visibilityExecutor.schedule(
                this::onVisibilityExpired,
                visibilitySeconds,
                TimeUnit.SECONDS
            );
        }
    }

    private void onVisibilityExpired() {
        timerExpired = true;
        visibilityFuture = null;
        try {
            reject();
            log.warn("Message visibility expired, auto-rejected message: {}", id);
        } catch (Exception e) {
            log.error("Failed to auto-reject message {} on visibility expiry", id, e);
        }
        // Do NOT throw - we're in a background thread
    }

    public void extendVisibilityTimer(int additionalSeconds) {
        if (additionalSeconds <= 0) {
            throw new IllegalArgumentException("additionalSeconds must be greater than 0");
        }
        if (visibilityFuture == null) {
            throw new IllegalStateException("Cannot extend, timer not active");
        }
        if (timerExpired) {
            throw new IllegalStateException("Cannot extend, timer has expired");
        }
        if (messageCompleted) {
            throw new IllegalStateException("Message transaction is already completed");
        }
        visibilityFuture.cancel(false); // Cancel the existing timer
        visibilityFuture = null;
        visibilitySeconds += additionalSeconds; // Extend the duration
        startVisibilityTimer(); // Restart the timer with the new duration
    }

    public void resetVisibilityTimer(int newVisibilitySeconds) {
        if (newVisibilitySeconds <= 0) {
            throw new IllegalArgumentException("additionalSeconds must be greater than 0");
        }
        if (visibilityFuture == null) {
            throw new IllegalStateException("Cannot extend, timer not active");
        }
        if (timerExpired) {
            throw new IllegalStateException("Cannot extend, timer has expired");
        }
        if (messageCompleted) {
            throw new IllegalStateException("Message transaction is already completed");
        }
        visibilityFuture.cancel(false); // Cancel the existing timer
        visibilityFuture = null;
        visibilitySeconds = newVisibilitySeconds; // Reset the duration
        startVisibilityTimer(); // Restart the timer with the new duration
    }


    // Method to mark the transaction as completed
    public void markTransactionCompleted() {
        messageCompleted = true;
        isTransactionCompleted = true;
        if (visibilityFuture != null) {
            visibilityFuture.cancel(false);
            visibilityFuture = null;
        }
    }
}
