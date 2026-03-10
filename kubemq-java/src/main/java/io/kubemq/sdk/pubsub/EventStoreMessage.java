package io.kubemq.sdk.pubsub;

import com.google.protobuf.ByteString;
import io.kubemq.sdk.common.KubeMQUtils;
import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.ValidationException;
import kubemq.Kubemq;
import lombok.*;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents a message to be stored in the event store of KubeMQ.
 *
 * <p><b>Thread Safety:</b> This class is NOT thread-safe. Create a new instance for
 * each operation. Do not share instances across threads.</p>
 *
 * <p>Message objects should be treated as immutable after construction.
 * Use the builder pattern exclusively. Setter methods are provided for
 * backward compatibility and will be removed in v3.0.</p>
 */
@NotThreadSafe
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventStoreMessage {

    /**
     * Unique message identifier. Auto-generated (UUID) if not set.
     */
    private String id;

    /**
     * Target channel name for this persistent event.
     * Must not be null or empty. Channels are created automatically on first publish.
     */
    private String channel;

    /**
     * Application-defined string metadata. Not indexed by the server.
     * Use {@link #tags} for filterable key-value data. May be null.
     */
    private String metadata;

    /**
     * Message payload as raw bytes.
     * Maximum size is 100MB (configurable server-side). An empty array is permitted.
     */
    @Builder.Default
    private byte[] body = new byte[0];

    /**
     * Key-value string pairs for message filtering and routing.
     * May be null or empty.
     */
    @Builder.Default
    private Map<String, String> tags = new HashMap<>();

    /**
     * Custom builder with build-time channel format validation.
     */
    public static class EventStoreMessageBuilder {
        public EventStoreMessage build() {
            byte[] effectiveBody = this.body$set ? this.body$value : new byte[0];
            Map<String, String> effectiveTags = this.tags$set ? this.tags$value : new HashMap<>();
            EventStoreMessage message = new EventStoreMessage(id, channel, metadata, effectiveBody, effectiveTags);
            if (message.channel != null && !message.channel.isEmpty()) {
                KubeMQUtils.validateChannelName(message.channel, "EventStoreMessage.build");
            }
            return message;
        }
    }

    /**
     * Validates that the necessary fields are present in the message.
     *
     * @throws ValidationException If any required field is missing.
     */
    public void validate() {
        KubeMQUtils.validateChannelName(channel, "EventStoreMessage.validate");

        if (metadata == null && body.length == 0 && (tags == null || tags.isEmpty())) {
            throw ValidationException.builder()
                .code(ErrorCode.INVALID_ARGUMENT)
                .message("Event Store message must have at least one of the following: metadata, body, or tags.")
                .operation("EventStoreMessage.validate")
                .channel(channel)
                .build();
        }

        final int MAX_BODY_SIZE = 104857600; // 100 MB in bytes
        if (body.length > MAX_BODY_SIZE) {
            throw ValidationException.builder()
                .code(ErrorCode.INVALID_ARGUMENT)
                .message("Event Store message body size exceeds the maximum allowed size of " + MAX_BODY_SIZE + " bytes.")
                .operation("EventStoreMessage.validate")
                .channel(channel)
                .build();
        }
    }

    /**
     * Encodes the message into a Kubemq.Event object for transmission.
     *
     * @param clientId The client ID to be added as a tag.
     * @return The encoded pbEvent object.
     */
    public Kubemq.Event encode(String clientId) {
        if (tags == null) {
            tags = new HashMap<>();
        }
        tags.put("x-kubemq-client-id", clientId);
        return Kubemq.Event.newBuilder()
                .setEventID(id != null ? id : UUID.randomUUID().toString())
                .setClientID(clientId)
                .setChannel(channel)
                .setMetadata(Optional.ofNullable(metadata).orElse(""))
                .setBody(ByteString.copyFrom(body))
                .setStore(true)
                .putAllTags(Optional.ofNullable(tags).orElse(new HashMap<>()))
                .build();
    }

    @Override
    public String toString() {
        return String.format("EventStoreMessage: id=%s, channel=%s, metadata=%s, body=%s, tags=%s",
                id, channel, metadata, body != null ? new String(body) : "", tags);
    }
}


