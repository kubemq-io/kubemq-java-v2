package io.kubemq.sdk.cq;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import kubemq.Kubemq.Request;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a query message received from a KubeMQ subscription.
 *
 * <p>Contains the query payload, metadata, and reply channel for sending responses. Query handlers
 * use this to inspect the incoming request and construct a {@link QueryResponseMessage}.
 */
@Data
@NoArgsConstructor
public class QueryMessageReceived {

  /** Unique identifier for this query request. */
  private String id;

  /** Client ID of the sender. */
  private String fromClientId;

  /** Timestamp when the message was received. */
  private LocalDateTime timestamp;

  /** Channel on which the query was received. */
  private String channel;

  /** Optional metadata associated with the query. */
  private String metadata;

  /** Query payload. */
  private byte[] body;

  /** Channel to send the response to. */
  private String replyChannel;

  /** Key-value tags for routing or filtering. */
  private Map<String, String> tags = new HashMap<>();

  /**
   * Decodes the protocol buffer message.
   *
   * @param queryReceive the query receive
   * @return the result
   */
  public static QueryMessageReceived decode(Request queryReceive) {
    QueryMessageReceived message = new QueryMessageReceived();
    message.id = queryReceive.getRequestID();
    message.fromClientId = queryReceive.getClientID();
    message.timestamp = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
    message.channel = queryReceive.getChannel();
    message.metadata = queryReceive.getMetadata();
    message.body = queryReceive.getBody().toByteArray();
    message.replyChannel = queryReceive.getReplyChannel();
    message.tags = queryReceive.getTagsMap();
    return message;
  }

  /**
   * Returns a string representation of this object.
   *
   * @return the result
   */
  @Override
  public String toString() {
    return "QueryMessageReceived: id="
        + id
        + ", channel="
        + channel
        + ", metadata="
        + metadata
        + ", body="
        + new String(body)
        + ", fromClientId="
        + fromClientId
        + ", timestamp="
        + timestamp
        + ", replyChannel="
        + replyChannel
        + ", tags="
        + tags;
  }
}
