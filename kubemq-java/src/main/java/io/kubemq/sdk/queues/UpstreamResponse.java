package io.kubemq.sdk.queues;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the response from a queue upstream (send) operation.
 *
 * <p>Contains the per-message send results and any error information
 * from the server.</p>
 */
@Data
@ToString
@Builder
public class UpstreamResponse {

    private String    refRequestId;
    @Builder.Default
    private List<QueueSendResult> results = new ArrayList<>();
    private boolean isError;
    private String error;

}
