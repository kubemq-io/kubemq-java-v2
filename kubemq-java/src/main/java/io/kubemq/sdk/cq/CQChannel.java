package io.kubemq.sdk.cq;

import lombok.*;

/**
 * CQChannel represents a channel in a KubeMQ system.
 * It contains information about the channel's name, type, activity status, and statistics.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CQChannel {

    /**
     * The name of the channel.
     */
    private String name;

    /**
     * The type of the channel.
     */
    private String type;

    /**
     * The timestamp of the last activity on the channel.
     */
    private long lastActivity;

    /**
     * Indicates whether the channel is currently active.
     */
    private boolean isActive;

    /**
     * Statistics about incoming messages to the channel.
     */
    private CQStats incoming;

    /**
     * Statistics about outgoing messages from the channel.
     */
    private CQStats outgoing;

    /**
     * Returns a string representation of the CQChannel.
     *
     * @return A string representing the channel's name, type, last activity time, active status, and statistics.
     */
    @Override
    public String toString() {
        return "Channel: name=" + name + ", type=" + type + ", last_activity=" + lastActivity +
                ", is_active=" + isActive + ", incoming=" + incoming + ", outgoing=" + outgoing;
    }

    public boolean getIsActive() {
        return isActive;
    }
}
