package io.kubemq.sdk.queues;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

/**
 * Represents the response from a queue upstream (send) operation.
 *
 * <p>Contains the per-message send results and any error information from the server.
 */
@Data
@ToString
@Builder
public class UpstreamResponse {

  private String refRequestId;
  @Builder.Default private List<QueueSendResult> results = new ArrayList<>();
  private boolean isError;
  private String error;
}
