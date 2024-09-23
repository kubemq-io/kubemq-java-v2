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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Timer;
import java.util.TimerTask;

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
    private int visibilitySeconds;
    private boolean isAutoAcked;

    private Timer visibilityTimer;
    private boolean messageCompleted;
    private boolean timerExpired;

    private final Lock lock = new ReentrantLock();

    // Method to ack() a message
    public synchronized void ack() {
        doOperation(QueuesDownstreamRequestType.AckRange, null);

//        QueuesDownstreamRequest request = QueuesDownstreamRequest.newBuilder()
//                .setRequestID(UUID.randomUUID().toString())
//                .setClientID(receiverClientId)
//                .setChannel(channel)
//                .setRequestTypeData(QueuesDownstreamRequestType.AckRange)
//                .setRefTransactionId(transactionId)
//                .addSequenceRange(sequence)
//                .build();
//
//        this.addTaskToThreadSafeQueue(request);
//
//        messageCompleted = true;
//        if (visibilityTimer != null && !timerExpired) {
//            visibilityTimer.cancel();
//        }
    }

    // Method to reject() a message
    public synchronized void reject() {
        doOperation(QueuesDownstreamRequestType.NAckRange, null);
    }

    // Method to reQueue() a message
    public synchronized void reQueue(String reQueueChannel) {
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
        if (responseHandler == null) {
            throw new IllegalStateException("Response handler not set");
        }

            QueuesDownstreamRequest.Builder requestBuilder = QueuesDownstreamRequest.newBuilder()
                    .setRequestID(UUID.randomUUID().toString())
                    .setClientID(receiverClientId)
                    .setChannel(channel)
                    .setRequestTypeData(requestType)
                    .setRefTransactionId(transactionId)
                    .addSequenceRange(sequence);
            if (reQueueChannel != null && requestType == QueuesDownstreamRequestType.ReQueueRange) {
                requestBuilder.setReQueueChannel(reQueueChannel);
            }

            QueuesDownstreamRequest request = requestBuilder.build();
            this.addTaskToThreadSafeQueue(request);

            messageCompleted = true;
            if (visibilityTimer != null && !timerExpired) {
                visibilityTimer.cancel();
            }
    }

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

    public static QueueMessageReceived decode(
            QueueMessage message,
            String transactionId,
            boolean transactionIsCompleted,
            String receiverClientId,
            StreamObserver<QueuesDownstreamRequest> responseHandler,
            int visibilitySeconds,
            boolean isAutoAcked
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
        received.visibilitySeconds = visibilitySeconds;
        received.isAutoAcked = isAutoAcked;

        if (received.visibilitySeconds > 0) {
            received.startVisibilityTimer();
        }

        return received;
    }

    private void startVisibilityTimer() {
        // Use the shared lock from the SharedLock class
        Lock lock = QueueSharedLock.getLock();
        lock.lock();
        try {
            visibilityTimer = new Timer();
            visibilityTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    onVisibilityExpired();
                }
            }, visibilitySeconds * 1000);
        } finally {
            lock.unlock();
        }
    }

    private void onVisibilityExpired() {
        // Use the shared lock from the SharedLock class
        Lock lock = QueueSharedLock.getLock();
        lock.lock();
        try {
            timerExpired = true;
            visibilityTimer = null;
        } finally {
            lock.unlock();
        }
        reject();
        throw new IllegalStateException("Message visibility expired");
    }

    public void extendVisibilityTimer(int additionalSeconds) {
        // Use the shared lock from the SharedLock class
        Lock lock = QueueSharedLock.getLock();
        lock.lock();
        try {
        if (additionalSeconds <= 0) {
            throw new IllegalArgumentException("additionalSeconds must be greater than 0");
        }
        if (visibilityTimer == null) {
            throw new IllegalStateException("Cannot extend, timer not active");
        }
        if (timerExpired) {
            throw new IllegalStateException("Cannot extend, timer has expired");
        }
        if (messageCompleted) {
            throw new IllegalStateException("Message transaction is already completed");
        }
            visibilityTimer.cancel(); // Cancel the existing timer
            visibilitySeconds += additionalSeconds; // Extend the duration
            startVisibilityTimer(); // Restart the timer with the new duration
        } finally {
                lock.unlock();
        }
    }


    // Method to mark the transaction as completed
    public void markTransactionCompleted() {
        // Use the shared lock from the SharedLock class
        Lock lock = QueueSharedLock.getLock();
        lock.lock();
        try {
            messageCompleted = true;
            isTransactionCompleted = true;
            if (visibilityTimer != null) {
                visibilityTimer.cancel();
            }
        } finally {
            lock.unlock();
        }
    }
}
