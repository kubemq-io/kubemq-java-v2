package io.kubemq.sdk.cq;

import com.google.protobuf.ByteString;
import kubemq.Kubemq;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a command message that can be sent over a communication channel.
 * This class contains information such as the message ID, channel, metadata, body, tags, and timeout.
 */
@Data
@Builder
public class CommandMessage {

    private String id;
    private String channel;
    private String metadata;
    @Builder.Default
    private byte[] body = new byte[0];
    @Builder.Default
    private Map<String, String> tags = new HashMap<>();
    private int timeoutInSeconds;

    /**
     * Constructs a CommandMessage instance with the provided attributes.
     *
     * @param id The ID of the command message.
     * @param channel The channel through which the command message will be sent.
     * @param metadata Additional metadata associated with the command message.
     * @param body The body of the command message as bytes.
     * @param tags A dictionary of key-value pairs representing tags associated with the command message.
     * @param timeoutInSeconds The maximum time in seconds for which the command message is valid.
     */
    public CommandMessage(String id, String channel, String metadata, byte[] body,
                          Map<String, String> tags, int timeoutInSeconds) {
        this.id = id;
        this.channel = channel;
        this.metadata = metadata;
        this.body = body != null ? body : new byte[0];
        this.tags = tags != null ? tags : new HashMap<>();
        this.timeoutInSeconds = timeoutInSeconds;
    }

    /**
     * Validates the command message.
     *
     * @return The current CommandMessage instance.
     * @throws IllegalArgumentException If the message is invalid. This includes cases where:
     *                                   - The channel is null or empty.
     *                                   - None of metadata, body, or tags are provided.
     *                                   - The timeoutInSeconds is less than or equal to 0.
     */
    public CommandMessage validate() {
        if (this.channel == null || this.channel.isEmpty()) {
            throw new IllegalArgumentException("Command message must have a channel.");
        }

        if ((this.metadata == null || this.metadata.isEmpty()) &&
                (this.body == null || this.body.length == 0) &&
                (this.tags == null || this.tags.isEmpty())) {
            throw new IllegalArgumentException("Command message must have at least one of the following: metadata, body, or tags.");
        }

        if (this.timeoutInSeconds <= 0) {
            throw new IllegalArgumentException("Command message timeout must be a positive integer.");
        }
        return this;
    }

    /**
     * Encodes the command message into a protocol buffer message.
     *
     * @param clientId The client ID for the message.
     * @return The encoded protocol buffer message.
     */
    public Kubemq.Request encode(String clientId) {
        tags.put("x-kubemq-client-id", clientId);
        return Kubemq.Request.newBuilder()
                .setRequestID(id != null ? id : UUID.randomUUID().toString())
                .setClientID(clientId)
                .setChannel(channel)
                .setRequestTypeData(Kubemq.Request.RequestType.Command)
                .setRequestTypeDataValue(Kubemq.Request.RequestType.Command_VALUE)
                .setMetadata(metadata != null ? metadata : "")
                .putAllTags(tags)
                .setBody(ByteString.copyFrom(body))
                .setTimeout(timeoutInSeconds * 1000)
                .build();
    }

    @Override
    public String toString() {
        return String.format("CommandMessage: id=%s, channel=%s, metadata=%s, body=%s, tags=%s, timeoutInSeconds=%d",
                id, channel, metadata, new String(body), tags, timeoutInSeconds);
    }
}
