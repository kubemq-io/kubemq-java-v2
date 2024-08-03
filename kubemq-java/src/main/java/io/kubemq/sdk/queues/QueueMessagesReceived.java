package io.kubemq.sdk.queues;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a response received when requesting queue messages.
 */
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class QueueMessagesReceived {

    /**
     * The request ID associated with this response.
     */
    private String requestID;

    /**
     * The list of messages received in this response.
     */
    private List<QueueMessageReceived> messages;

    /**
     * The number of messages received.
     */
    private int messagesReceived;

    /**
     * The number of messages that expired.
     */
    private int messagesExpired;

    /**
     * Indicates if the queue is at its peak.
     */
    private boolean isPeak;

    /**
     * Indicates if there was an error.
     */
    private boolean isError;

    /**
     * The error message, if any.
     */
    private String error;

    public List<QueueMessageReceived> getMessages() {
        if(messages == null){
            messages = new ArrayList<>();
        }
        return messages;
    }
}
