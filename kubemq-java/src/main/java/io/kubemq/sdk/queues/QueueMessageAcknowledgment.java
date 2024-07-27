package io.kubemq.sdk.queues;

import kubemq.Kubemq;
import lombok.Builder;
import lombok.Data;

/**
 * A class representing a response to acknowledge all queue messages.
 */
@Data
@Builder
public class QueueMessageAcknowledgment {
    /**
     * The unique identifier for the request.
     */
    private String requestId;

    /**
     * The number of messages affected by the acknowledgment.
     */
    private long affectedMessages;

    /**
     * Indicates if there was an error.
     */
    private boolean isError;

    /**
     * The error message, if any.
     */
    private String error;

    /**
     * Decodes a protocol buffer message into an QueueMessageAcknowledgment object.
     *
     * @param pbResponse the protocol buffer message to decode.
     * @return the decoded QueueMessageAcknowledgment object.
     */
    public static QueueMessageAcknowledgment decode(Kubemq.AckAllQueueMessagesResponse pbResponse) {
        return QueueMessageAcknowledgment.builder()
                .requestId(pbResponse.getRequestID())
                .affectedMessages(pbResponse.getAffectedMessages())
                .isError(pbResponse.getIsError())
                .error(pbResponse.getError())
                .build();
    }

    @Override
    public String toString() {
        return "AckAllQueueMessagesResponseWrapper{" +
                "requestId='" + requestId + '\'' +
                ", affectedMessages=" + affectedMessages +
                ", isError=" + isError +
                ", error='" + error + '\'' +
                '}';
    }
}
