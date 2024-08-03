package io.kubemq.sdk.queues;

import kubemq.Kubemq.QueueMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a received queue message.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class QueueMessageWaitingPulled {
    private String id;
    private String channel;
    private String metadata;
    private byte[] body;
    private String fromClientId;
    @Builder.Default
    private Map<String, String> tags = new HashMap<>();
    private Instant timestamp;
    private long sequence;
    private int receiveCount;
    private boolean isReRouted;
    private String reRouteFromQueue;
    private Instant expiredAt;
    private Instant delayedTo;
    private String receiverClientId;


    public static QueueMessageWaitingPulled decode(
            QueueMessage message,
            String receiverClientId
    ) {
        QueueMessageWaitingPulled received = new QueueMessageWaitingPulled();
        received.id = message.getMessageID();
        received.channel = message.getChannel();
        received.metadata = message.getMetadata();
        received.body = message.getBody().toByteArray();
        received.fromClientId = message.getClientID();
        received.tags = new HashMap<>(message.getTagsMap());
        received.timestamp = Instant.ofEpochSecond(message.getAttributes().getTimestamp() / 1_000_000_000L);
        received.sequence = message.getAttributes().getSequence();
        received.receiveCount = message.getAttributes().getReceiveCount();
        received.isReRouted = message.getAttributes().getReRouted();
        received.reRouteFromQueue = message.getAttributes().getReRoutedFromQueue();
        received.expiredAt = Instant.ofEpochSecond(message.getAttributes().getExpirationAt() / 1_000_000L);
        received.delayedTo = Instant.ofEpochSecond(message.getAttributes().getDelayedTo() / 1_000_000L);
        received.receiverClientId = receiverClientId;

        return received;
    }

    @Override
    public String toString() {
        return String.format(
                "QueueMessageWaitingPulled: id=%s, channel=%s, metadata=%s, body=%s, fromClientId=%s, timestamp=%s, sequence=%d, receiveCount=%d, isReRouted=%s, reRouteFromQueue=%s, expiredAt=%s, delayedTo=%s, tags=%s",
                id, channel, metadata, new String(body), fromClientId, timestamp, sequence, receiveCount, isReRouted, reRouteFromQueue, expiredAt, delayedTo, tags
        );
    }
}

