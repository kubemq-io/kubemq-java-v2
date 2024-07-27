package io.kubemq.sdk.pubsub;

import com.google.protobuf.ByteString;
import kubemq.Kubemq;
import lombok.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents a message to be stored in the event store of KubeMQ.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventStoreMessage {
    /**
     * The unique identifier of the message.
     */
    private String id;

    /**
     * The channel to which the message belongs.
     */
    private String channel;

    /**
     * The metadata associated with the message.
     */
    private String metadata;

    /**
     * The body of the message.
     */
    private byte[] body;

    /**
     * The tags associated with the message.
     */
    @Builder.Default
    private Map<String, String> tags = new HashMap<>();

    /**
     * Validates that the necessary fields are present in the message.
     *
     * @throws IllegalArgumentException If any required field is missing.
     */
    public void validate() {
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("Event Store message must have a channel.");
        }

        if (metadata == null && body == null && (tags == null || tags.isEmpty())) {
            throw new IllegalArgumentException("Event Store message must have at least one of the following: metadata, body, or tags.");
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


