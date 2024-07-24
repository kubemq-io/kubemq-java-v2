package io.kubemq.sdk.pubsub;

import kubemq.Kubemq;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an event message received from KubeMQ.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventMessageReceived {

    /**
     * Unique identifier for the received event message.
     */
    private String id;

    /**
     * The client ID from which the event message was sent.
     */
    private String fromClientId;

    /**
     * The timestamp when the event message was received.
     */
    private Instant timestamp;

    /**
     * The channel from which the event message was received.
     */
    private String channel;

    /**
     * Metadata associated with the received event message.
     */
    private String metadata;

    /**
     * Body of the received event message in bytes.
     */
    private byte[] body;

    /**
     * Tags associated with the received event message as key-value pairs.
     */
    private Map<String, String> tags = new HashMap<>();

    /**
     * Decodes a {@link Kubemq.EventReceive} object into an {@link EventMessageReceived} instance.
     *
     * @param event The {@link Kubemq.EventReceive} object to decode.
     * @return The decoded {@link EventMessageReceived} instance.
     */
    public static EventMessageReceived decode(Kubemq.EventReceive event) {
        EventMessageReceived message = new EventMessageReceived();
        message.setId(event.getEventID());
        message.setFromClientId(event.getTagsOrDefault("x-kubemq-client-id", ""));
        message.setChannel(event.getChannel());
        message.setMetadata(event.getMetadata());
        message.setBody(event.getBody().toByteArray());
        message.setTags(event.getTagsMap());
        return message;
    }

    /**
     * Returns a string representation of the received event message.
     *
     * @return a string containing the received event message details.
     */
    @Override
    public String toString() {
        return String.format("EventMessageReceived: id=%s, channel=%s, metadata=%s, body=%s, fromClientId=%s, timestamp=%s, tags=%s",
                id, channel, metadata, java.util.Arrays.toString(body), fromClientId, timestamp, tags);
    }
}
