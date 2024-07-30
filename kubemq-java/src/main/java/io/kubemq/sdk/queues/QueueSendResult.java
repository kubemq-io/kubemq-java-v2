package io.kubemq.sdk.queues;

import kubemq.Kubemq.SendQueueMessageResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * QueueSendResult represents the result of sending a message to a queue.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueSendResult {

    /**
     * The unique identifier of the message.
     */
    private String id;

    /**
     * The timestamp when the message was sent.
     */
    private LocalDateTime sentAt;

    /**
     * The timestamp when the message will expire.
     */
    private LocalDateTime expiredAt;

    /**
     * The timestamp when the message will be delivered.
     */
    private LocalDateTime delayedTo;

    /**
     * Indicates if there was an error while sending the message.
     */
    private boolean isError;

    /**
     * The error message if {@code isError} is true.
     */
    private String error;

    /**
     * Decodes the given SendQueueMessageResult and sets the attributes of the QueueSendResult instance accordingly.
     *
     * @param result the result to decode.
     * @return the updated QueueSendResult instance.
     */
    public QueueSendResult decode(SendQueueMessageResult result) {
        this.id = result.getMessageID() != null ? result.getMessageID() : "";
        this.sentAt = result.getSentAt() > 0 ? LocalDateTime.ofInstant(Instant.ofEpochSecond(result.getSentAt() / 1000000000), ZoneId.systemDefault()) : null;
        this.expiredAt = result.getExpirationAt() > 0 ? LocalDateTime.ofInstant(Instant.ofEpochSecond(result.getExpirationAt() / 1000000000), ZoneId.systemDefault()) : null;
        this.delayedTo = result.getDelayedTo() > 0 ? LocalDateTime.ofInstant(Instant.ofEpochSecond(result.getDelayedTo() / 1000000000), ZoneId.systemDefault()) : null;
        this.isError = result.getIsError();
        this.error = result.getError() != null ? result.getError() : "";
        return this;
    }

    @Override
    public String toString() {
        return "QueueSendResult{" +
                "id='" + id + '\'' +
                ", sentAt=" + sentAt +
                ", expiredAt=" + expiredAt +
                ", delayedTo=" + delayedTo +
                ", isError=" + isError +
                ", error='" + error + '\'' +
                '}';
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public void setExpiredAt(LocalDateTime expiredAt) {
        this.expiredAt = expiredAt;
    }

    public void setDelayedTo(LocalDateTime delayedTo) {
        this.delayedTo = delayedTo;
    }

    public void setIsError(boolean isError) {
        this.isError = isError;
    }

    public void setError(String error) {
        this.error = error;
    }
}

