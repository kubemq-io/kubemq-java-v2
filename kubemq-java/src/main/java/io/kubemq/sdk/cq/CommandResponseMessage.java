package io.kubemq.sdk.cq;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents the response to a command request in KubeMQ.
 *
 * <p>When handling incoming commands via subscription, construct a response using the builder with
 * the received command's metadata. When receiving a response from {@link
 * CQClient#sendCommandRequest(CommandMessage)}, this object indicates whether the command was
 * executed successfully.
 *
 * <p>Instances are either constructed by the SDK when decoding a server response, or built by
 * command handlers to send an execution acknowledgment back to the caller.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandResponseMessage {

  /** The command message that this response acknowledges. */
  private CommandMessageReceived commandReceived;

  /** Client ID of the responder. */
  private String clientId;

  /** Request ID correlating this response to the original command request. */
  private String requestId;

  /** Whether the command was executed successfully. */
  private boolean isExecuted;

  /** Timestamp when the response was created. */
  private LocalDateTime timestamp;

  /** Error message if execution failed; null or empty if successful. */
  private String error;

  /**
   * Validates this object.
   *
   * @return the result
   */
  public CommandResponseMessage validate() {
    if (commandReceived == null) {
      throw new IllegalArgumentException("Command response must have a command request.");
    } else if (commandReceived.getReplyChannel() == null
        || commandReceived.getReplyChannel().isEmpty()) {
      throw new IllegalArgumentException("Command response must have a reply channel.");
    }
    return this;
  }

  /**
   * Decodes from protocol buffer format.
   *
   * @param pbResponse the pb response
   * @return the result
   */
  public CommandResponseMessage decode(kubemq.Kubemq.Response pbResponse) {
    this.clientId = pbResponse.getClientID();
    this.requestId = pbResponse.getRequestID();
    this.isExecuted = pbResponse.getExecuted();
    this.error = pbResponse.getError();
    this.timestamp =
        LocalDateTime.ofInstant(
            Instant.ofEpochSecond(pbResponse.getTimestamp() / 1_000_000_000L), ZoneOffset.UTC);
    return this;
  }

  /**
   * Encodes into protocol buffer format.
   *
   * @param clientId the client id
   * @return the result
   */
  public kubemq.Kubemq.Response encode(String clientId) {
    return kubemq.Kubemq.Response.newBuilder()
        .setClientID(clientId)
        .setRequestID(this.commandReceived.getId())
        .setReplyChannel(this.commandReceived.getReplyChannel())
        .setExecuted(this.isExecuted)
        .setError(this.error != null ? this.error : "")
        .setTimestamp(
            this.timestamp != null
                ? (this.timestamp.toEpochSecond(ZoneOffset.UTC) * 1_000_000_000L)
                : Instant.now().toEpochMilli())
        .build();
  }

  /**
   * Returns a string representation.
   *
   * @return the result
   */
  @Override
  public String toString() {
    return "CommandResponseMessage: clientId="
        + clientId
        + ", requestId="
        + requestId
        + ", isExecuted="
        + isExecuted
        + ", error="
        + error
        + ", timestamp="
        + timestamp;
  }
}
