package io.kubemq.sdk.cq;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import kubemq.Kubemq.Response;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the response to a query request in KubeMQ.
 *
 * <p>When handling incoming queries via subscription, construct a response using the builder and
 * the received query's metadata. When receiving a response from {@link
 * CQClient#sendQueryRequest(QueryMessage)}, this object contains the responder's reply.
 *
 * <p>Instances are either constructed by the SDK when decoding a server response, or built by query
 * handlers to send a reply back to the caller.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResponseMessage {

  /** The query message that this response replies to. */
  private QueryMessageReceived queryReceived;

  /** Client ID of the responder. */
  private String clientId;

  /** Request ID correlating this response to the original query request. */
  private String requestId;

  /** Whether the query was executed successfully. */
  private boolean isExecuted;

  /** Timestamp when the response was created. */
  private LocalDateTime timestamp;

  /** Error message if execution failed; null or empty if successful. */
  private String error;

  /** Optional metadata associated with the response. */
  private String metadata;

  /** Response payload; empty byte array if no body. */
  @Builder.Default private byte[] body = new byte[0];

  /** Key-value tags for routing or filtering. */
  @Builder.Default private Map<String, String> tags = new HashMap<>();

  /**
   * Validates this object.
   *
   * @return the result
   */
  public QueryResponseMessage validate() {
    if (queryReceived == null) {
      throw io.kubemq.sdk.exception.ValidationException.builder()
          .code(io.kubemq.sdk.exception.ErrorCode.INVALID_ARGUMENT)
          .message("Query response must have a query request.")
          .operation("QueryResponseMessage.validate")
          .build();
    }
    if (queryReceived.getReplyChannel() == null
        || queryReceived.getReplyChannel().isEmpty()) {
      throw io.kubemq.sdk.exception.ValidationException.builder()
          .code(io.kubemq.sdk.exception.ErrorCode.INVALID_ARGUMENT)
          .message("Query response must have a reply channel.")
          .operation("QueryResponseMessage.validate")
          .build();
    }
    return this;
  }

  /**
   * Decodes from protocol buffer format.
   *
   * @param pbResponse the pb response
   * @return the result
   */
  public QueryResponseMessage decode(Response pbResponse) {
    this.clientId = pbResponse.getClientID();
    this.requestId = pbResponse.getRequestID();
    this.isExecuted = pbResponse.getExecuted();
    this.error = pbResponse.getError();
    this.timestamp =
        LocalDateTime.ofInstant(
            Instant.ofEpochSecond(pbResponse.getTimestamp() / 1_000_000_000),
            ZoneId.systemDefault());
    this.metadata = pbResponse.getMetadata();
    this.body = pbResponse.getBody().toByteArray();
    this.tags = pbResponse.getTagsMap();
    return this;
  }

  /**
   * Encodes into protocol buffer format.
   *
   * @param clientId the client id
   * @return the result
   */
  public Response encode(String clientId) {
    return Response.newBuilder()
        .setClientID(clientId)
        .setRequestID(this.queryReceived.getId())
        .setReplyChannel(this.queryReceived.getReplyChannel())
        .setExecuted(this.isExecuted)
        .setError(this.error != null ? this.error : "")
        .setTimestamp(
            this.timestamp != null
                ? (this.timestamp.toEpochSecond(java.time.ZoneOffset.UTC) * 1_000_000_000L)
                : (Instant.now().getEpochSecond() * 1_000_000_000L))
        .setMetadata(this.metadata != null ? this.metadata : "")
        .setBody(com.google.protobuf.ByteString.copyFrom(this.body))
        .putAllTags(this.tags)
        .build();
  }

  /**
   * Returns a string representation.
   *
   * @return the result
   */
  @Override
  public String toString() {
    return "QueryResponseMessage: clientId="
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
