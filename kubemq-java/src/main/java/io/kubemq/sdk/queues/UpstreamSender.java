package io.kubemq.sdk.queues;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import kubemq.Kubemq;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Data
@Slf4j
public class UpstreamSender {

    private StreamObserver<Kubemq.QueuesUpstreamRequest> queuesUpStreamHandler = null;

    public QueueSendResult sendMessage(KubeMQClient kubeMQClient, Kubemq.QueuesUpstreamRequest queueMessage) {

        CompletableFuture<QueueSendResult> futureResponse = new CompletableFuture<>();

        if (queuesUpStreamHandler == null) {
            StreamObserver<Kubemq.QueuesUpstreamResponse> request = new StreamObserver<Kubemq.QueuesUpstreamResponse>() {
                @Override
                public void onNext(Kubemq.QueuesUpstreamResponse messageReceive) {
                    log.trace("QueuesUpstreamResponse Received Message send result: '{}'", messageReceive);
                    QueueSendResult qsr = new QueueSendResult();
                    if(!messageReceive.getIsError()) {
                        qsr.decode(messageReceive.getResults(0));
                    }else{
                        qsr.setIsError(true);
                        qsr.setError(messageReceive.getError());
                    }
                    futureResponse.complete(qsr);
                }

                @Override
                public void onError(Throwable t) {
                    log.error("Error in QueuesUpstreamResponse Error message sending: ", t);
                    UpstreamResponse qpResp = UpstreamResponse.builder()
                            .error(t.getMessage())
                            .isError(true).build();
                    futureResponse.completeExceptionally(t);
                }

                @Override
                public void onCompleted() {
                    log.trace("QueuesUpstreamResponse onCompleted.");
                }
            };

            queuesUpStreamHandler = kubeMQClient.getAsyncClient().queuesUpstream(request);
        }
        queuesUpStreamHandler.onNext(queueMessage);

        try {
            return futureResponse.get();
        } catch (Exception e) {
            log.error("Error waiting for response: ", e);
            throw new RuntimeException("Failed to get response", e);
        }
    }
}
