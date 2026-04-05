package io.kubemq.sdk.queues;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import kubemq.Kubemq.QueueMessage;
import kubemq.Kubemq.QueuesDownstreamRequest;
import kubemq.Kubemq.QueuesDownstreamRequestType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents a message received from a KubeMQ queue.
 *
 * <p>After processing, call {@link #ack()} to acknowledge, {@link #reject()} to return the message
 * to the queue, or {@link #reQueue(String)} to route it to a different channel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class QueueMessageReceived {

  /** Unique message identifier. */
  private String id;

  /** Queue channel from which the message was received. */
  private String channel;

  /** Application-defined metadata. */
  private String metadata;

  /** Message payload as raw bytes. */
  private byte[] body;

  /** Client ID of the message sender. */
  private String fromClientId;

  /** Key-value tags attached to the message. */
  @Builder.Default private Map<String, String> tags = new HashMap<>();

  /** Timestamp when the message was originally sent. */
  private Instant timestamp;

  /** Server-assigned sequence number. */
  private long sequence;

  /** Number of times this message has been received (for retry tracking). */
  private int receiveCount;

  /** Whether this message was re-routed from another queue. */
  private boolean isReRouted;

  /** Original queue channel if the message was re-routed. */
  private String reRouteFromQueue;

  /** Expiration timestamp, or null if no expiration was set. */
  private Instant expiredAt;

  /** Delayed delivery timestamp, or null if no delay was set. */
  private Instant delayedTo;

  /** Unique identifier for the queue transaction. */
  private String transactionId;

  /** The client ID of the receiver. */
  private String receiverClientId;

  /** Number of seconds the message remains invisible after being received. */
  private int visibilitySeconds;

  /** Whether the transaction has been completed. */
  @Getter private boolean isTransactionCompleted;

  /** Whether the message was auto-acknowledged. */
  @Getter private boolean isAutoAcked;

  private static final ScheduledExecutorService VISIBILITY_EXECUTOR =
      Executors.newScheduledThreadPool(
          2,
          r -> {
            Thread t = new Thread(r, "kubemq-visibility-timer");
            t.setDaemon(true);
            return t;
          });

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private ScheduledFuture<?> visibilityFuture;

  /** Whether the message operation (ack/reject/reQueue) has been completed. */
  private boolean messageCompleted;

  /** Whether the visibility timer has expired. */
  private boolean timerExpired;

  // Expose executor for shutdown hook
  public static ScheduledExecutorService getVisibilityExecutor() {
    return VISIBILITY_EXECUTOR;
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
  /** Acknowledges this message. */
  public void ack() {
    doOperation(QueuesDownstreamRequestType.AckRange, null);
  }

  // Method to reject() a message
  /** Rejects this message. */
  public void reject() {
    doOperation(QueuesDownstreamRequestType.NAckRange, null);
  }

  // Method to reQueue() a message
  /**
   * Re-queues this message.
   *
   * @param reQueueChannel the re queue channel
   */
  public void reQueue(String reQueueChannel) {
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

    QueuesDownstreamRequest.Builder requestBuilder =
        QueuesDownstreamRequest.newBuilder()
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

    if (queuesPollResponse != null) {
      queuesPollResponse.markMessageCompleted(id);
    }
  }

  /**
   * Decodes from protocol buffer format.
   *
   * @param message the message
   * @param transactionId the transaction id
   * @param transactionIsCompleted the transaction is completed
   * @param receiverClientId the receiver client id
   * @param visibilitySeconds the visibility seconds
   * @param isAutoAcked the is auto acked
   * @param queuesPollResponse the queues poll response
   * @return the result
   */
  public QueueMessageReceived decode(
      QueueMessage message,
      String transactionId,
      boolean transactionIsCompleted,
      String receiverClientId,
      int visibilitySeconds,
      boolean isAutoAcked,
      QueuesPollResponse queuesPollResponse) {

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
    this.expiredAt =
        Instant.ofEpochSecond(message.getAttributes().getExpirationAt() / 1_000_000_000L);
    this.delayedTo = Instant.ofEpochSecond(message.getAttributes().getDelayedTo() / 1_000_000_000L);
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
      visibilityFuture =
          VISIBILITY_EXECUTOR.schedule(
              this::onVisibilityExpired, visibilitySeconds, TimeUnit.SECONDS);
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

  /**
   * Extends the visibility timer.
   *
   * @param additionalSeconds the additional seconds
   */
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

  /**
   * Resets the visibility timer.
   *
   * @param newVisibilitySeconds the new visibility seconds
   */
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
  /** Marks the transaction completed. */
  public void markTransactionCompleted() {
    messageCompleted = true;
    isTransactionCompleted = true;
    if (visibilityFuture != null) {
      visibilityFuture.cancel(false);
      visibilityFuture = null;
    }
  }
}
