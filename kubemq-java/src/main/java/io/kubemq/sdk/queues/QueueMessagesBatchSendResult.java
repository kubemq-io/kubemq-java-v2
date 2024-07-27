package io.kubemq.sdk.queues;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueMessagesBatchSendResult {

    private String batchId;
    private List<QueueSendResult> results;
    private boolean haveErrors;

    public List<QueueSendResult> getResults() {
        if(results == null){
            results = new ArrayList<>();
        }
        return results;
    }
}
