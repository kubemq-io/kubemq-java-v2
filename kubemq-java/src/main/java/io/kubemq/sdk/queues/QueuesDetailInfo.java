package io.kubemq.sdk.queues;

import kubemq.Kubemq;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class QueuesDetailInfo {

    private  String refRequestID;
    private int totalQueue;
    private long sent;
    private long delivered;
    private long waiting;
    private List<Kubemq.QueueInfo> queues;

    public List<Kubemq.QueueInfo> getQueues() {
        if(queues == null){
            queues = new ArrayList<>();
        }
        return queues;
    }
}
