package io.kubemq.sdk.queues;

import com.google.protobuf.ByteString;
import kubemq.Kubemq;
import kubemq.Kubemq.QueueMessagePolicy;
import kubemq.Kubemq.QueuesUpstreamRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A class representing a message in a queue.
 */
@Data
@Builder
@AllArgsConstructor
public class QueueMessage {

    /**
     * The unique identifier for the message.
     */
    private String id;

    /**
     * The channel of the message.
     */
    private String channel;

    /**
     * The metadata associated with the message.
     */
    private String metadata;

    /**
     * The body of the message.
     */
    @Builder.Default
    private byte[] body =new byte[0];

    /**
     * The tags associated with the message.
     */
    @Builder.Default
    private Map<String, String> tags = new HashMap<>();

    /**
     * The delay in seconds before the message becomes available in the queue.
     */
    private int delayInSeconds;

    /**
     * The expiration time in seconds for the message.
     */
    private int expirationInSeconds;

    /**
     * The number of receive attempts allowed for the message before it is moved to the dead letter queue.
     */
    private int attemptsBeforeDeadLetterQueue;

    /**
     * The dead letter queue where the message will be moved after reaching the maximum receive attempts.
     */
    private String deadLetterQueue;

    /**
     * Validates the message attributes and ensures that the required attributes are set.
     *
     * @throws IllegalArgumentException if any of the required attributes are not set or invalid.
     */
    public void validate() {
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("Queue message must have a channel.");
        }

        if ((metadata == null || metadata.isEmpty()) && body.length == 0 && tags.isEmpty()) {
            throw new IllegalArgumentException("Queue message must have at least one of the following: metadata, body, or tags.");
        }

        if (attemptsBeforeDeadLetterQueue < 0) {
            throw new IllegalArgumentException("Queue message attempts_before_dead_letter_queue must be a positive number.");
        }

        if (delayInSeconds < 0) {
            throw new IllegalArgumentException("Queue message delay_in_seconds must be a positive number.");
        }

        if (expirationInSeconds < 0) {
            throw new IllegalArgumentException("Queue message expiration_in_seconds must be a positive number.");
        }
    }

    /**
     * Encodes the message into a protocol buffer format for sending to the queue server.
     *
     * @param clientId the client ID to associate with the message.
     * @return the encoded QueuesUpstreamRequest object.
     */
    public QueuesUpstreamRequest encode(String clientId) {
        QueuesUpstreamRequest.Builder pbQueueStreamBuilder = QueuesUpstreamRequest.newBuilder();
        pbQueueStreamBuilder.setRequestID(UUID.randomUUID().toString());
        Kubemq.QueueMessage pbMessage = encodeMessage(clientId);
        pbQueueStreamBuilder.addMessages(pbMessage);
        return pbQueueStreamBuilder.build();
    }

    /**
     * Encodes the message into a protocol buffer format.
     *
     * @param clientId the client ID to associate with the message.
     * @return the encoded QueueMessage object.
     */
    public Kubemq.QueueMessage encodeMessage(String clientId) {
        Kubemq.QueueMessage.Builder pbQueueBuilder = Kubemq.QueueMessage.newBuilder();
        pbQueueBuilder.setMessageID(id != null ? id : UUID.randomUUID().toString());
        pbQueueBuilder.setClientID(clientId);
        pbQueueBuilder.setChannel(channel);
        pbQueueBuilder.setMetadata(metadata != null ? metadata : "");
        pbQueueBuilder.setBody(ByteString.copyFrom(body));
        pbQueueBuilder.putAllTags(tags);

        Kubemq.QueueMessagePolicy.Builder policyBuilder = QueueMessagePolicy.newBuilder();
        policyBuilder.setDelaySeconds(delayInSeconds);
        policyBuilder.setExpirationSeconds(expirationInSeconds);
        policyBuilder.setMaxReceiveCount(attemptsBeforeDeadLetterQueue);
        policyBuilder.setMaxReceiveQueue(deadLetterQueue != null ? deadLetterQueue :"");

        pbQueueBuilder.setPolicy(policyBuilder);

        return pbQueueBuilder.build();
    }

    /**
     * Decodes a protocol buffer message into a QueueMessageWrapper object.
     *
     * @param pbMessage the protocol buffer message to decode.
     * @return the decoded QueueMessageWrapper object.
     */
    public static QueueMessage decode(Kubemq.QueueMessage pbMessage) {
        QueueMessagePolicy policy = pbMessage.getPolicy();
        return QueueMessage.builder()
                .id(pbMessage.getMessageID())
                .channel(pbMessage.getChannel())
                .metadata(pbMessage.getMetadata())
                .body(pbMessage.getBody().toByteArray())
                .tags(pbMessage.getTagsMap())
                .delayInSeconds(policy.getDelaySeconds())
                .expirationInSeconds(policy.getExpirationSeconds())
                .attemptsBeforeDeadLetterQueue(policy.getMaxReceiveCount())
                .deadLetterQueue(policy.getMaxReceiveQueue())
                .build();
    }

    @Override
    public String toString() {
        return "QueueMessage{" +
                "id='" + id + '\'' +
                ", channel='" + channel + '\'' +
                ", metadata='" + metadata + '\'' +
                ", body=" + new String(body) +
                ", tags=" + tags +
                ", delayInSeconds=" + delayInSeconds +
                ", expirationInSeconds=" + expirationInSeconds +
                ", attemptsBeforeDeadLetterQueue=" + attemptsBeforeDeadLetterQueue +
                ", deadLetterQueue='" + deadLetterQueue + '\'' +
                '}';
    }
}
