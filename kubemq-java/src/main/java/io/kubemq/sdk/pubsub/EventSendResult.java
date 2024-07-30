package io.kubemq.sdk.pubsub;

import kubemq.Kubemq;
import lombok.*;

/**
 * Represents the result of sending an event message to KubeMQ.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventSendResult {

    /**
     * Unique identifier for the sent event message.
     */
    private String id;

    /**
     * Indicates whether the event message was successfully sent.
     */
    private boolean sent;

    /**
     * Error message if the event message was not sent successfully.
     */
    private String error;

    /**
     * Decodes a {@link Kubemq.Result} object into an {@link EventSendResult} instance.
     *
     * @param result The {@link Kubemq.Result} object to decode.
     * @return The decoded {@link EventSendResult} instance.
     */
    public static EventSendResult decode(Kubemq.Result result) {
        return new EventSendResult(
                result.getEventID(),
                result.getSent(),
                result.getError()
        );
    }

    /**
     * Returns a string representation of the event send result.
     *
     * @return a string containing the event send result details.
     */
    @Override
    public String toString() {
        return String.format("EventSendResult: id=%s, sent=%s, error=%s",
                id, sent, error);
    }


}

