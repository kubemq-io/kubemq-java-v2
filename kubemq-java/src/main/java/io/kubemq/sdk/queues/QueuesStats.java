package io.kubemq.sdk.queues;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

/**
 * QueuesStats represents the statistics of a queue in KubeMQ.
 * It contains details about the number of messages, volume, waiting messages, expired messages, and delayed messages.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueuesStats {

    /**
     * The number of messages in the queue.
     */
    private int messages;

    /**
     * The volume of messages in the queue.
     */
    private int volume;

    /**
     * The number of messages waiting in the queue.
     */
    private int waiting;

    /**
     * The number of expired messages in the queue.
     */
    private int expired;

    /**
     * The number of delayed messages in the queue.
     */
    private int delayed;

    private int responses;

    @Override
    public String toString() {
        return "QueuesStats{" +
                "messages=" + messages +
                ", volume=" + volume +
                ", waiting=" + waiting +
                ", expired=" + expired +
                ", delayed=" + delayed +
                ", responses=" + responses +
                '}';
    }
}
