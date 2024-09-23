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
    @Builder.Default
    private int visibilitySeconds = 0;  // New field added with default value

    // Validate method to check the validity of input fields
    public void validate() {
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("Queue subscription must have a channel.");
        }
        if (pollMaxMessages < 1) {
            throw new IllegalArgumentException("pollMaxMessages must be greater than 0.");
        }
        if (pollWaitTimeoutInSeconds < 1) {
            throw new IllegalArgumentException("pollWaitTimeoutInSeconds must be greater than 0.");
        }
        if (visibilitySeconds < 0) {
            throw new IllegalArgumentException("Visibility timeout must be a non-negative integer.");
        }
        if (autoAckMessages && visibilitySeconds > 0) {
            throw new IllegalArgumentException("autoAckMessages and visibilitySeconds cannot be set together.");
        }
    }

    // Encode method to build the request with visibilitySeconds included
    public QueuesDownstreamRequest encode(String clientId) {
        return QueuesDownstreamRequest.newBuilder()
                .setRequestID(UUID.randomUUID().toString())
                .setClientID(clientId)
                .setChannel(channel)
                .setMaxItems(pollMaxMessages)
                .setWaitTimeout(pollWaitTimeoutInSeconds * 1000)
                .setAutoAck(autoAckMessages)
                //.setVisibilitySeconds(visibilitySeconds)  // New field included in the encoding, not supported by gRPC
                .setRequestTypeData(QueuesDownstreamRequestType.Get)
                .build();
    }

    @Override
    public String toString() {
        return String.format(
                "QueuesPollRequest: channel=%s, pollMaxMessages=%d, pollWaitTimeoutInSeconds=%d, autoAckMessages=%s, visibilitySeconds=%d",
                channel, pollMaxMessages, pollWaitTimeoutInSeconds, autoAckMessages, visibilitySeconds
        );
    }
}
