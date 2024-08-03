package io.kubemq.sdk.queues;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a response received when requesting waiting queue messages.
 */
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class QueueMessagesWaiting {
    /**
     * The list of messages received in this response.
     */
    private List<QueueMessageWaitingPulled> messages;

    /**
     * Indicates if there was an error.
     */
    private boolean isError;

    /**
     * The error message, if any.
     */
    private String error;

    /**
     * The number of messages received.
     */
    public List<QueueMessageWaitingPulled> getMessages() {
        if(messages == null){
            messages = new ArrayList<>();
        }
        return messages;
    }
}
