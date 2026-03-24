package io.kubemq.sdk.cq;

import io.kubemq.sdk.common.KubeMQUtils;
import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.ValidationException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.concurrent.NotThreadSafe;
import kubemq.Kubemq.Request;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a query message that can be sent over a communication channel.
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
@NoArgsConstructor
@AllArgsConstructor
public class QueryMessage {

  /** Unique message identifier. Auto-generated (UUID) if not set. */
  private String id;

  /** Target channel for the query. Must not be null or empty. */
  private String channel;

  /** Application-defined string metadata. May be null. */
  private String metadata;

  /** Query payload as raw bytes. Maximum size is 100MB. */
  @Builder.Default private byte[] body = new byte[0];

  /** Key-value string pairs for message filtering. May be empty. */
  @Builder.Default private Map<String, String> tags = new HashMap<>();

  /** Maximum time in seconds to wait for a response. Must be greater than 0. */
  private int timeoutInSeconds;

  /** Cache key for server-side response caching. Null or empty disables caching. */
  private String cacheKey;

  /** Cache time-to-live in seconds. Only used when {@link #cacheKey} is set. */
  private int cacheTtlInSeconds;

  /** Custom builder with build-time channel format validation. */
  public static class QueryMessageBuilder {
    /**
     * Builds a validated {@link QueryMessage} from this builder's state.
     *
     * @return a new QueryMessage instance
     */
    public QueryMessage build() {
      byte[] effectiveBody = this.body$set ? this.body$value : new byte[0];
      Map<String, String> effectiveTags = this.tags$set ? this.tags$value : new HashMap<>();
      QueryMessage message =
          new QueryMessage(
              id,
              channel,
              metadata,
              effectiveBody,
              effectiveTags,
              timeoutInSeconds,
              cacheKey,
              cacheTtlInSeconds);
      if (message.channel != null && !message.channel.isEmpty()) {
        KubeMQUtils.validateChannelName(message.channel, "QueryMessage.build");
      }
      return message;
    }
  }

  /**
   * Validates the query message.
   *
   * @throws ValidationException if the message is invalid.
   */
  public void validate() {
    KubeMQUtils.validateChannelName(channel, "QueryMessage.validate");

    if ((metadata == null || metadata.isEmpty())
        && (body == null || body.length == 0)
        && (tags == null || tags.isEmpty())) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message(
              "Query message must have at least one of the following: metadata, body, or tags.")
          .operation("QueryMessage.validate")
          .channel(channel)
          .build();
    }

    final int MAX_BODY_SIZE = 104857600; // 100 MB in bytes
    if (body.length > MAX_BODY_SIZE) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message(
              "Query message body size exceeds the maximum allowed size of "
                  + MAX_BODY_SIZE
                  + " bytes.")
          .operation("QueryMessage.validate")
          .channel(channel)
          .build();
    }

    if (timeoutInSeconds <= 0) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message("Query message timeout must be a positive integer.")
          .operation("QueryMessage.validate")
          .channel(channel)
          .build();
    }
  }

  /**
   * Encodes the query message into a protocol buffer request.
   *
   * @param clientId the client ID for the request
   * @return the encoded protocol buffer request
   */
  public Request encode(String clientId) {
    Request.Builder pbQueryBuilder = Request.newBuilder();
    java.util.Map<String, String> encodedTags = new java.util.HashMap<>(tags != null ? tags : java.util.Collections.emptyMap());
    encodedTags.put("x-kubemq-client-id", clientId);
    pbQueryBuilder
        .setRequestID(id != null ? id : UUID.randomUUID().toString())
        .setClientID(clientId)
        .setChannel(channel)
        .setMetadata(metadata != null ? metadata : "")
        .setBody(com.google.protobuf.ByteString.copyFrom(body))
        .setTimeout((int) Math.min((long) timeoutInSeconds * 1000L, Integer.MAX_VALUE))
        .setRequestTypeData(Request.RequestType.Query)
        .putAllTags(encodedTags)
        .setCacheKey(cacheKey != null ? cacheKey : "")
        .setCacheTTL((int) Math.min((long) cacheTtlInSeconds * 1000L, Integer.MAX_VALUE));

    return pbQueryBuilder.build();
  }

  /**
   * Returns a string representation.
   *
   * @return the result
   */
  @Override
  public String toString() {
    return "QueryMessage: id="
        + id
        + ", channel="
        + channel
        + ", metadata="
        + metadata
        + ", body="
        + new String(body)
        + ", tags="
        + tags
        + ", timeoutInSeconds="
        + timeoutInSeconds
        + ", cacheKey="
        + cacheKey
        + ", cacheTtlInSeconds="
        + cacheTtlInSeconds;
  }
}
