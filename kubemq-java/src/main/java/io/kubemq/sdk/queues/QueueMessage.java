package io.kubemq.sdk.queues;

import com.google.protobuf.ByteString;
import io.kubemq.sdk.common.KubeMQUtils;
import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.ValidationException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.concurrent.NotThreadSafe;
import kubemq.Kubemq;
import kubemq.Kubemq.QueueMessagePolicy;
import kubemq.Kubemq.QueuesUpstreamRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Encapsulates the content, metadata, and delivery policy for a message sent to a KubeMQ queue
 * channel.
 *
 * <p><b>Thread Safety:</b> This class is NOT thread-safe. Create a new instance for each operation.
 * Do not share instances across threads.
 *
 * <p>Message objects should be treated as immutable after construction. Use the builder pattern
 * exclusively. Setter methods are provided for backward compatibility and will be removed in v3.0.
 */
@NotThreadSafe
@Data
@Builder
@AllArgsConstructor
public class QueueMessage {

  /** Unique message identifier. Auto-generated (UUID) if not set. */
  private String id;

  /** Target queue channel name. Must not be null or empty. */
  private String channel;

  /** Application-defined string metadata. May be null. */
  private String metadata;

  /** Message payload as raw bytes. Maximum size is 100MB. */
  @Builder.Default private byte[] body = new byte[0];

  /** Key-value string pairs for message filtering. May be empty. */
  @Builder.Default private Map<String, String> tags = new HashMap<>();

  /** Delay delivery of the message by this many seconds. 0 = immediate delivery. */
  private int delayInSeconds;

  /** Message expires after this many seconds. 0 = no expiration. */
  private int expirationInSeconds;

  /** Number of receive attempts before routing to the dead letter queue. 0 = disabled. */
  private int attemptsBeforeDeadLetterQueue;

  /**
   * Channel name for the dead letter queue. Used when {@link #attemptsBeforeDeadLetterQueue} is
   * exceeded.
   */
  private String deadLetterQueue;

  /** Custom builder with build-time channel format validation. */
  public static class QueueMessageBuilder {
    /**
     * Builds a validated {@link QueueMessage} from this builder's state.
     *
     * @return a new QueueMessage instance
     */
    public QueueMessage build() {
      byte[] effectiveBody = this.body$set ? this.body$value : new byte[0];
      Map<String, String> effectiveTags = this.tags$set ? this.tags$value : new HashMap<>();
      QueueMessage message =
          new QueueMessage(
              id,
              channel,
              metadata,
              effectiveBody,
              effectiveTags,
              delayInSeconds,
              expirationInSeconds,
              attemptsBeforeDeadLetterQueue,
              deadLetterQueue);
      if (message.channel != null && !message.channel.isEmpty()) {
        KubeMQUtils.validateChannelName(message.channel, "QueueMessage.build");
      }
      return message;
    }
  }

  /**
   * Validates the message attributes and ensures that the required attributes are set.
   *
   * @throws ValidationException if any of the required attributes are not set or invalid.
   */
  public void validate() {
    KubeMQUtils.validateChannelName(channel, "QueueMessage.validate");

    if ((metadata == null || metadata.isEmpty()) && body.length == 0 && tags.isEmpty()) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message(
              "Queue message must have at least one of the following: metadata, body, or tags.")
          .operation("QueueMessage.validate")
          .channel(channel)
          .build();
    }

    final int MAX_BODY_SIZE = 104857600; // 100 MB in bytes
    if (body.length > MAX_BODY_SIZE) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message(
              "Queue message body size exceeds the maximum allowed size of "
                  + MAX_BODY_SIZE
                  + " bytes.")
          .operation("QueueMessage.validate")
          .channel(channel)
          .build();
    }

    if (attemptsBeforeDeadLetterQueue < 0) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message("Queue message attempts_before_dead_letter_queue must be a positive number.")
          .operation("QueueMessage.validate")
          .channel(channel)
          .build();
    }

    if (delayInSeconds < 0) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message("Queue message delay_in_seconds must be a positive number.")
          .operation("QueueMessage.validate")
          .channel(channel)
          .build();
    }

    if (expirationInSeconds < 0) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message("Queue message expiration_in_seconds must be a positive number.")
          .operation("QueueMessage.validate")
          .channel(channel)
          .build();
    }
  }

  /**
   * Encodes the message into a protocol buffer format for sending to the queue server.
   *
   * @param clientId the client ID to associate with the message.
   * @return the encoded QueuesUpstreamRequest object.
   */
  public QueuesUpstreamRequest encode(String clientId) {
    QueuesUpstreamRequest.Builder pbQueueStreamBuilder = QueuesUpstreamRequest.newBuilder();
    pbQueueStreamBuilder.setRequestID(UUID.randomUUID().toString());
    Kubemq.QueueMessage pbMessage = encodeMessage(clientId);
    pbQueueStreamBuilder.addMessages(pbMessage);
    return pbQueueStreamBuilder.build();
  }

  /**
   * Encodes the message into a protocol buffer format.
   *
   * @param clientId the client ID to associate with the message.
   * @return the encoded QueueMessage object.
   */
  public Kubemq.QueueMessage encodeMessage(String clientId) {
    Kubemq.QueueMessage.Builder pbQueueBuilder = Kubemq.QueueMessage.newBuilder();
    pbQueueBuilder.setMessageID(id != null ? id : UUID.randomUUID().toString());
    pbQueueBuilder.setClientID(clientId);
    pbQueueBuilder.setChannel(channel);
    pbQueueBuilder.setMetadata(metadata != null ? metadata : "");
    pbQueueBuilder.setBody(ByteString.copyFrom(body));
    pbQueueBuilder.putAllTags(tags);

    Kubemq.QueueMessagePolicy.Builder policyBuilder = QueueMessagePolicy.newBuilder();
    policyBuilder.setDelaySeconds(delayInSeconds);
    policyBuilder.setExpirationSeconds(expirationInSeconds);
    policyBuilder.setMaxReceiveCount(attemptsBeforeDeadLetterQueue);
    policyBuilder.setMaxReceiveQueue(deadLetterQueue != null ? deadLetterQueue : "");

    pbQueueBuilder.setPolicy(policyBuilder);

    return pbQueueBuilder.build();
  }

  /**
   * Decodes a protocol buffer message into a QueueMessageWrapper object.
   *
   * @param pbMessage the protocol buffer message to decode.
   * @return the decoded QueueMessageWrapper object.
   */
  public static QueueMessage decode(Kubemq.QueueMessage pbMessage) {
    QueueMessagePolicy policy = pbMessage.getPolicy();
    return QueueMessage.builder()
        .id(pbMessage.getMessageID())
        .channel(pbMessage.getChannel())
        .metadata(pbMessage.getMetadata())
        .body(pbMessage.getBody().toByteArray())
        .tags(pbMessage.getTagsMap())
        .delayInSeconds(policy.getDelaySeconds())
        .expirationInSeconds(policy.getExpirationSeconds())
        .attemptsBeforeDeadLetterQueue(policy.getMaxReceiveCount())
        .deadLetterQueue(policy.getMaxReceiveQueue())
        .build();
  }

  /**
   * Returns a string representation.
   *
   * @return the result
   */
  @Override
  public String toString() {
    return "QueueMessage{"
        + "id='"
        + id
        + '\''
        + ", channel='"
        + channel
        + '\''
        + ", metadata='"
        + metadata
        + '\''
        + ", body="
        + new String(body)
        + ", tags="
        + tags
        + ", delayInSeconds="
        + delayInSeconds
        + ", expirationInSeconds="
        + expirationInSeconds
        + ", attemptsBeforeDeadLetterQueue="
        + attemptsBeforeDeadLetterQueue
        + ", deadLetterQueue='"
        + deadLetterQueue
        + '\''
        + '}';
  }
}
