package io.kubemq.sdk.queues;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Represents a response received when requesting waiting queue messages. */
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class QueueMessagesWaiting {
  /** The list of messages received in this response. */
  private List<QueueMessageWaitingPulled> messages;

  /** Indicates if there was an error. */
  private boolean isError;

  /** The error message, if any. */
  private String error;

  /**
   * Returns the list of waiting messages.
   *
   * @return the list of waiting messages
   */
  public List<QueueMessageWaitingPulled> getMessages() {
    if (messages == null) {
      messages = new ArrayList<>();
    }
    return messages;
  }
}
