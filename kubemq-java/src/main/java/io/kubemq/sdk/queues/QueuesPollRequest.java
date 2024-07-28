package io.kubemq.sdk.queues;

import kubemq.Kubemq.QueuesDownstreamRequest;
import kubemq.Kubemq.QueuesDownstreamRequestType;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Class representing a request to poll messages from a queue.
 */
@Data
@Builder
@Slf4j
public class QueuesPollRequest {
    private String channel;
    private int pollMaxMessages = 1;
    private int pollWaitTimeoutInSeconds = 60;
    private boolean autoAckMessages = false;
    /**
     * Callback function to be invoked when message is received.
     */
    private Consumer<QueuesPollResponse> onReceiveMessageCallback;

    /**
     * Callback function to be invoked when an error occurs.
     */
    private Consumer<String> onErrorCallback;

    /**
     * Invokes the onReceiveMessageCallback with the given message.
     *
     * @param pollResponse The received message.
     */
    public void raiseOnReceiveMessage(QueuesPollResponse pollResponse) {
        if (onReceiveMessageCallback != null) {
            onReceiveMessageCallback.accept(pollResponse);
        }
    }

    /**
     * Invokes the onErrorCallback with the given error message.
     *
     * @param msg The error message.
     */
    public void raiseOnError(String msg) {
        if (onErrorCallback != null) {
            onErrorCallback.accept(msg);
        }
    }

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
