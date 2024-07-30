package io.kubemq.sdk.cq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

/**
 * CQStats represents statistics for a CQ channel, including the number of messages processed,
 * total volume of data, number of responses, and other metrics.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CQStats {

    /**
     * The number of messages processed by the channel.
     */
    private int messages;

    /**
     * The total volume of data (in bytes) processed by the channel.
     */
    private int volume;

    /**
     * The number of responses sent by the channel.
     */
    private int responses;

    /**
     * The number of messages currently waiting to be processed by the channel.
     */
    private int waiting;

    /**
     * The number of messages that have expired in the channel.
     */
    private int expired;

    /**
     * The number of messages that have been delayed in the channel.
     */
    private int delayed;

    /**
     * Returns a string representation of the CQStats.
     *
     * @return A string representing the number of messages, volume of data, and number of responses.
     */
    @Override
    public String toString() {
        return "Stats: messages=" + messages + ", volume=" + volume + ", responses=" + responses;
    }
}
