package io.kubemq.sdk.pubsub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

/**
 * Represents the statistics for a Pub/Sub channel, including message counts and volume.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PubSubStats {

    /**
     * The number of messages for the Pub/Sub channel.
     */
    private int messages;

    /**
     * The volume of data (in bytes) for the Pub/Sub channel.
     */
    private int volume;

    /**
     * The number of messages currently waiting to be processed by the Pub/Sub channel.
     */
    private int waiting;

    /**
     * The number of messages that have expired in the Pub/Sub channel.
     */
    private int expired;

    /**
     * The number of messages that have been delayed in the Pub/Sub channel.
     */
    private int delayed;

    /**
     * The number of responses sent by the Pub/Sub channel.
     */
    private int responses;

    /**
     * Returns a string representation of the Pub/Sub statistics.
     * The string includes the number of messages, the volume of data,
     * the number of waiting, expired, delayed, and responses.
     *
     * @return A string representation of the Pub/Sub statistics.
     */
    @Override
    public String toString() {
        return "PubSubStats{" +
                "messages=" + messages +
                ", volume=" + volume +
                ", waiting=" + waiting +
                ", expired=" + expired +
                ", delayed=" + delayed +
                ", responses=" + responses +
                '}';
    }
}
