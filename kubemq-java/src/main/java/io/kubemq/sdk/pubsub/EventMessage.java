package io.kubemq.sdk.pubsub;

import com.google.protobuf.ByteString;
import kubemq.Kubemq;
import lombok.*;

import java.util.*;

/**
 * Represents an event message to be sent to KubeMQ.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventMessage {

    /**
     * Unique identifier for the event message.
     */
    private String id;

    /**
     * The channel to which the event message is sent.
     * This field is mandatory.
     */
    private String channel;

    /**
     * Metadata associated with the event message.
     */
    private String metadata;

    /**
     * Body of the event message in bytes.
     */
    @Builder.Default
    private byte[] body = new byte[0];

    /**
     * Tags associated with the event message as key-value pairs.
     */
    @Builder.Default
    private Map<String, String> tags = new HashMap<>();

    /**
     * Validates the event message.
     * Ensures that the channel is not null or empty and that at least one of metadata, body, or tags is present.
     *
     * @throws IllegalArgumentException if validation fails.
     */
    public void validate() {
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("Event message must have a channel.");
        }

        if (metadata == null && body.length == 0 && (tags == null || tags.isEmpty())) {
            throw new IllegalArgumentException("Event message must have at least one of the following: metadata, body, or tags.");
        }
    }

    /**
     * Encodes the event message into a KubeMQ event.
     *
     * @param clientId the client ID to be added as a tag and set in the KubeMQ event.
     * @return the encoded KubeMQ event.
     */
    public Kubemq.Event encode(String clientId) {
        tags.put("x-kubemq-client-id", clientId);
        return Kubemq.Event.newBuilder()
                .setChannel(channel)
                .setMetadata(Optional.ofNullable(metadata).orElse(""))
                .setBody(ByteString.copyFrom(body))
                .setEventID(id != null ? id : UUID.randomUUID().toString())
                .setClientID(clientId)
                .setStore(false)
                .putAllTags(Optional.ofNullable(tags).orElse(new HashMap<>()))
                .build();
    }

    /**
     * Returns a string representation of the event message.
     *
     * @return a string containing the event message details.
     */
    @Override
    public String toString() {
        return "EventMessage: id=" + id + ", channel=" + channel +
                ", metadata=" + metadata + ", body=" + Arrays.toString(body) +
                ", tags=" + tags;
    }
}
