package io.kubemq.sdk.pubsub;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Represents a Pub/Sub channel with various statistics and status information. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PubSubChannel {

  /** The name of the Pub/Sub channel. */
  private String name;

  /** The type of the Pub/Sub channel. */
  private String type;

  /** The timestamp of the last activity on the channel, represented in milliseconds since epoch. */
  private long lastActivity;

  /** Indicates whether the channel is active or not. */
  @com.fasterxml.jackson.annotation.JsonProperty("isActive")
  private boolean isActive;

  /** The statistics related to incoming messages for this channel. */
  private PubSubStats incoming;

  /** The statistics related to outgoing messages for this channel. */
  private PubSubStats outgoing;

  /**
   * Returns a string representation of the Pub/Sub channel. The string includes the channel's name,
   * type, last activity timestamp, activity status, and the statistics for incoming and outgoing
   * messages.
   *
   * @return A string representation of the Pub/Sub channel.
   */
  @Override
  public String toString() {
    return "Channel: name="
        + name
        + ", type="
        + type
        + ", lastActivity="
        + lastActivity
        + ", isActive="
        + isActive
        + ", incoming="
        + incoming
        + ", outgoing="
        + outgoing;
  }
}
