package io.kubemq.sdk.pubsub;

import kubemq.Kubemq;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a message received from the event store in KubeMQ.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventStoreMessageReceived {
    /**
     * The unique identifier of the message.
     */
    private String id;

    /**
     * The ID of the client that sent the message.
     */
    private String fromClientId;

    /**
     * The timestamp when the message was received, in seconds.
     */
    private long timestamp;

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
     * The sequence number of the message.
     */
    private long sequence;

    /**
     * The tags associated with the message.
     */
    private Map<String, String> tags = new HashMap<>();

    /**
     * Decodes a Kubemq.EventReceive object into an EventStoreMessageReceived instance.
     * <p>
     * Converts the fields of the provided `EventReceive` object into the corresponding
     * fields of the `EventStoreMessageReceived` instance.
     *
     * @param eventReceive The Kubemq.EventReceive object to decode.
     * @return An instance of EventStoreMessageReceived with the decoded values.
     */
    public static EventStoreMessageReceived decode(Kubemq.EventReceive eventReceive) {
        EventStoreMessageReceived message = new EventStoreMessageReceived();
        message.setId(eventReceive.getEventID());
        message.setFromClientId(eventReceive.getTagsOrDefault("x-kubemq-client-id", ""));
        message.setTimestamp(eventReceive.getTimestamp() / 1000000000L); // Convert nanoseconds to seconds
        message.setChannel(eventReceive.getChannel());
        message.setMetadata(eventReceive.getMetadata());
        message.setBody(eventReceive.getBody().toByteArray());
        message.setSequence(eventReceive.getSequence());
        message.setTags(eventReceive.getTagsMap());
        return message;
    }

    @Override
    public String toString() {
        return String.format("EventStoreMessageReceived: id=%s, channel=%s, metadata=%s, body=%s, fromClientId=%s, timestamp=%s, sequence=%d, tags=%s",
                id, channel, metadata, body != null ? new String(body) : "", fromClientId, timestamp, sequence, tags);
    }
}
