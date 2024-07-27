package io.kubemq.sdk.queues;

import lombok.*;

/**
 * QueuesChannel represents a channel in KubeMQ for queuing messages.
 * It contains details about the channel's name, type, last activity time, status, and statistics for incoming and outgoing messages.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueuesChannel {

    /**
     * The name of the queue channel.
     */
    private String name;

    /**
     * The type of the queue channel.
     */
    private String type;

    /**
     * The timestamp of the last activity in the queue channel.
     */
    private long lastActivity;

    /**
     * Indicates whether the queue channel is currently active.
     */
    private boolean isActive;

    /**
     * The statistics for incoming messages in the queue channel.
     */
    private QueuesStats incoming;

    /**
     * The statistics for outgoing messages in the queue channel.
     */
    private QueuesStats outgoing;

    /**
     * Returns a string representation of the QueuesChannel.
     *
     * @return A string representing the name, type, last activity, status, incoming, and outgoing statistics of the channel.
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

