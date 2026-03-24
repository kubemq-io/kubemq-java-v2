package io.kubemq.sdk.cq;

import com.google.protobuf.ByteString;
import io.kubemq.sdk.common.KubeMQUtils;
import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.ValidationException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.concurrent.NotThreadSafe;
import kubemq.Kubemq;
import lombok.Builder;
import lombok.Data;

/**
 * Represents a command message that can be sent over a communication channel.
 *
 * <p><b>Thread Safety:</b> This class is NOT thread-safe. Create a new instance for each operation.
 * Do not share instances across threads.
 *
 * <p>Message objects should be treated as immutable after construction. Use the builder pattern
 * exclusively. Setter methods are provided for backward compatibility and will be removed in v3.0.
 *
 * @see CQClient#sendCommandRequest
 * @see CommandResponseMessage
 */
@NotThreadSafe
@Data
@Builder
public class CommandMessage {

  /** Unique message identifier. Auto-generated (UUID) if not set. */
  private String id;

  /** Target channel for the command. Must not be null or empty. */
  private String channel;

  /** Application-defined string metadata. May be null. */
  private String metadata;

  /** Command payload as raw bytes. Maximum size is 100MB. */
  @Builder.Default private byte[] body = new byte[0];

  /** Key-value string pairs for message filtering. May be empty. */
  @Builder.Default private Map<String, String> tags = new HashMap<>();

  /** Maximum time in seconds to wait for a response. Must be greater than 0. */
  private int timeoutInSeconds;

  /**
   * Constructs a new instance.
   *
   * @param id the id
   * @param channel the channel
   * @param metadata the metadata
   * @param body the body
   * @param tags the tags
   * @param timeoutInSeconds the timeout in seconds
   */
  public CommandMessage(
      String id,
      String channel,
      String metadata,
      byte[] body,
      Map<String, String> tags,
      int timeoutInSeconds) {
    this.id = id;
    this.channel = channel;
    this.metadata = metadata;
    this.body = body != null ? body : new byte[0];
    this.tags = tags != null ? tags : new HashMap<>();
    this.timeoutInSeconds = timeoutInSeconds;
  }

  /** Custom builder with build-time channel format validation. */
  public static class CommandMessageBuilder {
    /**
     * Builds a validated {@link CommandMessage} from this builder's state.
     *
     * @return a new CommandMessage instance
     */
    public CommandMessage build() {
      byte[] effectiveBody = this.body$set ? this.body$value : new byte[0];
      Map<String, String> effectiveTags = this.tags$set ? this.tags$value : new HashMap<>();
      CommandMessage message =
          new CommandMessage(id, channel, metadata, effectiveBody, effectiveTags, timeoutInSeconds);
      if (message.channel != null && !message.channel.isEmpty()) {
        KubeMQUtils.validateChannelName(message.channel, "CommandMessage.build");
      }
      return message;
    }
  }

  /**
   * Validates the command message.
   *
   * @return The current CommandMessage instance.
   * @throws ValidationException If the message is invalid. This includes cases where: - The channel
   *     is null or empty. - None of metadata, body, or tags are provided. - The timeoutInSeconds is
   *     less than or equal to 0.
   */
  public CommandMessage validate() {
    KubeMQUtils.validateChannelName(this.channel, "CommandMessage.validate");

    if ((this.metadata == null || this.metadata.isEmpty())
        && (this.body == null || this.body.length == 0)
        && (this.tags == null || this.tags.isEmpty())) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message(
              "Command message must have at least one of the following: metadata, body, or tags.")
          .operation("CommandMessage.validate")
          .channel(channel)
          .build();
    }

    final int MAX_BODY_SIZE = 104857600; // 100 MB in bytes
    if (body.length > MAX_BODY_SIZE) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message(
              "Command message body size exceeds the maximum allowed size of "
                  + MAX_BODY_SIZE
                  + " bytes.")
          .operation("CommandMessage.validate")
          .channel(channel)
          .build();
    }

    if (this.timeoutInSeconds <= 0) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message("Command message timeout must be a positive integer.")
          .operation("CommandMessage.validate")
          .channel(channel)
          .build();
    }
    return this;
  }

  /**
   * Encodes the command message into a protocol buffer message.
   *
   * @param clientId The client ID for the message.
   * @return The encoded protocol buffer message.
   */
  public Kubemq.Request encode(String clientId) {
    java.util.Map<String, String> encodedTags = new java.util.HashMap<>(tags != null ? tags : java.util.Collections.emptyMap());
    encodedTags.put("x-kubemq-client-id", clientId);
    return Kubemq.Request.newBuilder()
        .setRequestID(id != null ? id : UUID.randomUUID().toString())
        .setClientID(clientId)
        .setChannel(channel)
        .setRequestTypeData(Kubemq.Request.RequestType.Command)
        .setRequestTypeDataValue(Kubemq.Request.RequestType.Command_VALUE)
        .setMetadata(metadata != null ? metadata : "")
        .putAllTags(encodedTags)
        .setBody(ByteString.copyFrom(body))
        .setTimeout((int) Math.min((long) timeoutInSeconds * 1000L, Integer.MAX_VALUE))
        .build();
  }

  /**
   * Returns a string representation of this object.
   *
   * @return the result
   */
  @Override
  public String toString() {
    return String.format(
        "CommandMessage: id=%s, channel=%s, metadata=%s, body=%s, tags=%s, timeoutInSeconds=%d",
        id, channel, metadata, new String(body), tags, timeoutInSeconds);
  }
}
