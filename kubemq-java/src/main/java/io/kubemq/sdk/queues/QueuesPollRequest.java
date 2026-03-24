package io.kubemq.sdk.queues;

import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.ValidationException;
import java.util.UUID;
import javax.annotation.concurrent.NotThreadSafe;
import kubemq.Kubemq.QueuesDownstreamRequest;
import kubemq.Kubemq.QueuesDownstreamRequestType;
import lombok.Builder;
import lombok.Data;

/**
 * Class representing a request to poll messages from a queue.
 *
 * <p><b>Thread Safety:</b> This class is NOT thread-safe. Create a new instance for each operation.
 * Do not share instances across threads.
 */
@NotThreadSafe
@Data
@Builder
public class QueuesPollRequest {
  /** Queue channel to poll messages from. Must not be null or empty. */
  private String channel;

  /** Maximum number of messages to return per poll. Default: 1. */
  @Builder.Default private int pollMaxMessages = 1;

  /** Maximum time in seconds to wait for messages. Default: 1. */
  @Builder.Default private int pollWaitTimeoutInSeconds = 1;

  /**
   * When true, messages are automatically acknowledged upon receipt. Default: false. Cannot be
   * combined with {@link #visibilitySeconds}.
   */
  @Builder.Default private boolean autoAckMessages = false;

  /**
   * Duration in seconds that received messages are hidden from other consumers. 0 = no visibility
   * timeout. Cannot be combined with {@link #autoAckMessages}.
   */
  @Builder.Default private int visibilitySeconds = 0;

  /**
   * Validates the poll request fields.
   *
   * @throws ValidationException if any field is invalid.
   */
  public void validate() {
    if (channel == null || channel.isEmpty()) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message("Queue subscription must have a channel.")
          .operation("QueuesPollRequest.validate")
          .build();
    }
    if (pollMaxMessages < 1) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message("pollMaxMessages must be greater than 0.")
          .operation("QueuesPollRequest.validate")
          .channel(channel)
          .build();
    }
    if (pollWaitTimeoutInSeconds < 1) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message("pollWaitTimeoutInSeconds must be greater than 0.")
          .operation("QueuesPollRequest.validate")
          .channel(channel)
          .build();
    }
    if (visibilitySeconds < 0) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message("Visibility timeout must be a non-negative integer.")
          .operation("QueuesPollRequest.validate")
          .channel(channel)
          .build();
    }
    if (autoAckMessages && visibilitySeconds > 0) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message("autoAckMessages and visibilitySeconds cannot be set together.")
          .operation("QueuesPollRequest.validate")
          .channel(channel)
          .build();
    }
  }

  /**
   * Encodes the poll request to a gRPC QueuesDownstreamRequest. Note: visibilitySeconds is handled
   * client-side via QueueMessageReceived timer, not sent to server. See {@link
   * QueueMessageReceived#extendVisibilityTimer(int)}.
   *
   * @param clientId the client id
   * @return the result
   */
  public QueuesDownstreamRequest encode(String clientId) {
    return QueuesDownstreamRequest.newBuilder()
        .setRequestID(UUID.randomUUID().toString())
        .setClientID(clientId)
        .setChannel(channel)
        .setMaxItems(pollMaxMessages)
        .setWaitTimeout(pollWaitTimeoutInSeconds * 1000)
        .setAutoAck(autoAckMessages)
        .setRequestTypeData(QueuesDownstreamRequestType.Get)
        .build();
  }

  /**
   * Returns a string representation.
   *
   * @return the result
   */
  @Override
  public String toString() {
    return String.format(
        "QueuesPollRequest: channel=%s, pollMaxMessages=%d, pollWaitTimeoutInSeconds=%d, autoAckMessages=%s, visibilitySeconds=%d",
        channel, pollMaxMessages, pollWaitTimeoutInSeconds, autoAckMessages, visibilitySeconds);
  }
}
