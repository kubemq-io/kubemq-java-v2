package io.kubemq.sdk.cq;

import kubemq.Kubemq;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a received command message.
 * This class contains information such as the message ID, client ID, timestamp, channel, metadata, body, reply channel, and tags.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandMessageReceived {

    private String id;
    private String fromClientId;
    private Instant timestamp;
    private String channel;
    private String metadata;
    private byte[] body;
    private String replyChannel;
    @Builder.Default
    private Map<String, String> tags = new HashMap<>();

    /**
     * Decodes a protocol buffer request into a CommandMessageReceived instance.
     *
     * @param commandReceive The protocol buffer request to decode.
     * @return The decoded CommandMessageReceived instance.
     */
    public static CommandMessageReceived decode(Kubemq.Request commandReceive) {
        return CommandMessageReceived.builder()
                .id(commandReceive.getRequestID())
                .fromClientId(commandReceive.getClientID())
                .timestamp(Instant.now()) // Placeholder for actual timestamp if available
                .channel(commandReceive.getChannel())
                .metadata(commandReceive.getMetadata())
                .body(commandReceive.getBody().toByteArray())
                .replyChannel(commandReceive.getReplyChannel())
                .build();
    }

    /**
     * Returns a string representation of the CommandMessageReceived object.
     *
     * @return A string representation of the CommandMessageReceived object.
     */
    @Override
    public String toString() {
        return String.format("CommandMessageReceived: id=%s, fromClientId=%s, timestamp=%s, channel=%s, metadata=%s, body=%s, replyChannel=%s, tags=%s",
                id, fromClientId, timestamp, channel, metadata, new String(body), replyChannel, tags);
    }
}
