package io.kubemq.sdk.queues;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Represents a response received when requesting queue messages. */
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class QueueMessagesReceived {

  /** The request ID associated with this response. */
  private String requestID;

  /** The list of messages received in this response. */
  private List<QueueMessageReceived> messages;

  /** The number of messages received. */
  private int messagesReceived;

  /** The number of messages that expired. */
  private int messagesExpired;

  /** Indicates if the queue is at its peak. */
  private boolean isPeak;

  /** Indicates if there was an error. */
  private boolean isError;

  /** The error message, if any. */
  private String error;

  /**
   * Returns the messages.
   *
   * @return the result
   */
  public List<QueueMessageReceived> getMessages() {
    if (messages == null) {
      messages = new ArrayList<>();
    }
    return messages;
  }
}
