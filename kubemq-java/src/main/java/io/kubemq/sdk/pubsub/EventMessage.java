package io.kubemq.sdk.pubsub;

import com.google.protobuf.ByteString;
import io.kubemq.sdk.common.KubeMQUtils;
import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.ValidationException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.concurrent.NotThreadSafe;
import kubemq.Kubemq;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents an event message to be sent to KubeMQ.
 *
 * <p><b>Thread Safety:</b> This class is NOT thread-safe. Create a new instance for each send
 * operation. Do not share instances across threads. Use the builder pattern to construct instances.
 *
 * <p>Message objects should be treated as immutable after construction. Use the builder pattern
 * exclusively. Setter methods are provided for backward compatibility and will be removed in v3.0.
 */
@NotThreadSafe
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventMessage {

  /** Unique message identifier. Auto-generated (UUID) if not set. */
  private String id;

  /**
   * Target channel name for this event. Must not be null or empty. Channels are created
   * automatically on first publish.
   */
  private String channel;

  /**
   * Application-defined string metadata. Not indexed by the server. Use {@link #tags} for
   * filterable key-value data. May be null.
   */
  private String metadata;

  /**
   * Message payload as raw bytes. Maximum size is 100MB (configurable server-side). An empty array
   * is permitted.
   */
  @Builder.Default private byte[] body = new byte[0];

  /** Key-value string pairs for message filtering and routing. May be null or empty. */
  @Builder.Default private Map<String, String> tags = new HashMap<>();

  /**
   * Custom builder with build-time channel format validation. Lombok generates all builder fields
   * and setters; only build() is overridden.
   */
  public static class EventMessageBuilder {
    /**
     * Builds a validated {@link EventMessage} from this builder's state.
     *
     * @return a new EventMessage instance
     */
    public EventMessage build() {
      byte[] effectiveBody = this.body$set ? this.body$value : new byte[0];
      Map<String, String> effectiveTags = this.tags$set ? this.tags$value : new HashMap<>();
      EventMessage message = new EventMessage(id, channel, metadata, effectiveBody, effectiveTags);
      if (message.channel != null && !message.channel.isEmpty()) {
        KubeMQUtils.validateChannelName(message.channel, "EventMessage.build");
      }
      return message;
    }
  }

  /**
   * Validates the event message. Ensures that the channel is not null or empty and that at least
   * one of metadata, body, or tags is present.
   *
   * @throws ValidationException if validation fails.
   */
  public void validate() {
    KubeMQUtils.validateChannelName(channel, "EventMessage.validate");

    if (metadata == null && body.length == 0 && (tags == null || tags.isEmpty())) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message(
              "Event message must have at least one of the following: metadata, body, or tags.")
          .operation("EventMessage.validate")
          .channel(channel)
          .build();
    }

    final int MAX_BODY_SIZE = 104857600; // 100 MB in bytes
    if (body.length > MAX_BODY_SIZE) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message(
              "Event message body size exceeds the maximum allowed size of "
                  + MAX_BODY_SIZE
                  + " bytes.")
          .operation("EventMessage.validate")
          .channel(channel)
          .build();
    }
  }

  /**
   * Encodes the event message into a KubeMQ event.
   *
   * @param clientId the client ID to be added as a tag and set in the KubeMQ event.
   * @return the encoded KubeMQ event.
   */
  public Kubemq.Event encode(String clientId) {
    Map<String, String> encodedTags =
        new HashMap<>(tags != null ? tags : java.util.Collections.emptyMap());
    encodedTags.put("x-kubemq-client-id", clientId);
    return Kubemq.Event.newBuilder()
        .setChannel(channel)
        .setMetadata(Optional.ofNullable(metadata).orElse(""))
        .setBody(ByteString.copyFrom(body))
        .setEventID(id != null ? id : UUID.randomUUID().toString())
        .setClientID(clientId)
        .setStore(false)
        .putAllTags(encodedTags)
        .build();
  }

  /**
   * Returns a string representation of the event message.
   *
   * @return a string containing the event message details.
   */
  @Override
  public String toString() {
    return "EventMessage: id="
        + id
        + ", channel="
        + channel
        + ", metadata="
        + metadata
        + ", body="
        + Arrays.toString(body)
        + ", tags="
        + tags;
  }
}
