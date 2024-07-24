package io.kubemq.sdk.pubsub;

import lombok.*;

/**
 * Represents the statistics for a Pub/Sub channel, including message counts and volume.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
     * Returns a string representation of the Pub/Sub statistics.
     * The string includes the number of messages and the volume of data.
     *
     * @return A string representation of the Pub/Sub statistics.
     */
    @Override
    public String toString() {
        return "Stats: messages=" + messages + ", volume=" + volume;
    }
}

