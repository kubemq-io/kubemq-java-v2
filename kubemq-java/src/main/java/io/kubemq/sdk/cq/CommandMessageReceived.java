package io.kubemq.sdk.cq;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import kubemq.Kubemq;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a received command message. This class contains information such as the message ID,
 * client ID, timestamp, channel, metadata, body, reply channel, and tags.
 *
 * @see CQClient#subscribeToCommands
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandMessageReceived {

  /** Unique message identifier. */
  private String id;

  /** Sender's client identifier. */
  private String fromClientId;

  /** Server timestamp when message was received. */
  private Instant timestamp;

  /** The channel name the command was received on. */
  private String channel;

  /** Additional metadata string. */
  private String metadata;

  /** Message payload as byte array. */
  private byte[] body;

  /** Channel for sending the response back. */
  private String replyChannel;

  /** Key-value metadata tags. */
  @Builder.Default private Map<String, String> tags = new HashMap<>();

  /**
   * Decodes a protocol buffer request into a CommandMessageReceived instance.
   *
   * @param commandReceive The protocol buffer request to decode.
   * @return The decoded CommandMessageReceived instance.
   */
  public static CommandMessageReceived decode(Kubemq.Request commandReceive) {
    return CommandMessageReceived.builder()
        .id(commandReceive.getRequestID())
        .fromClientId(commandReceive.getClientID())
        .timestamp(Instant.now()) // Placeholder for actual timestamp if available
        .channel(commandReceive.getChannel())
        .metadata(commandReceive.getMetadata())
        .body(commandReceive.getBody().toByteArray())
        .replyChannel(commandReceive.getReplyChannel())
        .build();
  }

  /**
   * Returns a string representation of the CommandMessageReceived object.
   *
   * @return A string representation of the CommandMessageReceived object.
   */
  @Override
  public String toString() {
    return String.format(
        "CommandMessageReceived: id=%s, fromClientId=%s, timestamp=%s, channel=%s, metadata=%s, body=%s, replyChannel=%s, tags=%s",
        id, fromClientId, timestamp, channel, metadata, new String(body), replyChannel, tags);
  }
}
