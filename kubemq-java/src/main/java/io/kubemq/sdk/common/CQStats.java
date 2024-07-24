package io.kubemq.sdk.common;

import lombok.*;

/**
 * CQStats represents the statistics for a KubeMQ channel.
 * It contains information about the number of messages, the volume of data, and the number of responses.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
     * Returns a string representation of the CQStats.
     *
     * @return A string representing the number of messages, volume of data, and number of responses.
     */
    @Override
    public String toString() {
        return "Stats: messages=" + messages + ", volume=" + volume + ", responses=" + responses;
    }
}
