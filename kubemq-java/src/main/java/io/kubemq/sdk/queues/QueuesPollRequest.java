package io.kubemq.sdk.queues;

import kubemq.Kubemq.QueuesDownstreamRequest;
import kubemq.Kubemq.QueuesDownstreamRequestType;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Class representing a request to poll messages from a queue.
 */
@Data
@Builder
@Slf4j
public class QueuesPollRequest {
    private String channel;
    @Builder.Default
    private int pollMaxMessages = 1;
    @Builder.Default
    private int pollWaitTimeoutInSeconds = 60;
    @Builder.Default
    private boolean autoAckMessages = false;

    public void validate() {
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("Queue subscription must have a channel.");
        }
        if (pollMaxMessages < 1) {
            throw new IllegalArgumentException("Queue subscription pollMaxMessages must be greater than 0.");
        }
        if (pollWaitTimeoutInSeconds < 1) {
            throw new IllegalArgumentException("Queue subscription pollWaitTimeoutInSeconds must be greater than 0.");
        }
    }

    public QueuesDownstreamRequest encode(String clientId) {
        return QueuesDownstreamRequest.newBuilder()
                .setRequestID(UUID.randomUUID().toString())
                .setClientID(clientId)
                .setChannel(channel)
                .setMaxItems(pollMaxMessages)
                .setWaitTimeout(pollWaitTimeoutInSeconds * 1000)
                .setAutoAck(autoAckMessages)
                .setRequestTypeData(QueuesDownstreamRequestType.Get)
                .build();
    }

    @Override
    public String toString() {
        return String.format(
                "QueuesPollRequest: channel=%s, pollMaxMessages=%d, pollWaitTimeoutInSeconds=%d, autoAckMessages=%s",
                channel, pollMaxMessages, pollWaitTimeoutInSeconds, autoAckMessages
        );
    }
}
