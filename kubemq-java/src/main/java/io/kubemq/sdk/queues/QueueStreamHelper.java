package io.kubemq.sdk.queues;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import kubemq.Kubemq;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@Data
@Slf4j
public class QueueStreamHelper {

    private StreamObserver<Kubemq.QueuesUpstreamRequest> queuesUpStreamHandler = null;
    private StreamObserver<Kubemq.QueuesDownstreamRequest> queuesDownstreamHandler = null;

    public QueueSendResult sendMessage(KubeMQClient kubeMQClient, Kubemq.QueuesUpstreamRequest queueMessage) {

        CompletableFuture<QueueSendResult> futureResponse = new CompletableFuture<>();

        if (queuesUpStreamHandler == null) {
            StreamObserver<Kubemq.QueuesUpstreamResponse> request = new StreamObserver<Kubemq.QueuesUpstreamResponse>() {
                @Override
                public void onNext(Kubemq.QueuesUpstreamResponse messageReceive) {
                    log.debug("QueuesUpstreamResponse Received Message send result: '{}'", messageReceive);
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
                    QueueSendResult qpResp = QueueSendResult.builder()
                            .error(t.getMessage())
                            .isError(true).build();
                    futureResponse.complete(qpResp);
                }

                @Override
                public void onCompleted() {
                    log.debug("QueuesUpstreamResponse onCompleted.");
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

    public QueuesPollResponse receiveMessage(KubeMQClient kubeMQClient, QueuesPollRequest queuesPollRequest){
        CompletableFuture<QueuesPollResponse> futureResponse = new CompletableFuture<>();
         if(queuesDownstreamHandler == null) {
             StreamObserver<Kubemq.QueuesDownstreamResponse> request = new StreamObserver<Kubemq.QueuesDownstreamResponse>() {

                 @Override
                 public void onNext(Kubemq.QueuesDownstreamResponse messageReceive) {
                     log.debug("QueuesDownstreamResponse Received Metadata: '{}'", messageReceive);
                     // Send the received message to the consumer
                     QueuesPollResponse qpResp = QueuesPollResponse.builder()
                             .refRequestId(messageReceive.getRefRequestId())
                             .activeOffsets(messageReceive.getActiveOffsetsList())
                             .receiverClientId(messageReceive.getTransactionId())
                             .isTransactionCompleted(messageReceive.getTransactionComplete())
                             .transactionId(messageReceive.getTransactionId())
                             .error(messageReceive.getError())
                             .isError(messageReceive.getIsError())
                             .build();
                     for (Kubemq.QueueMessage queueMessage : messageReceive.getMessagesList()) {
                         qpResp.getMessages().add(QueueMessageReceived.decode(queueMessage, qpResp.getTransactionId(),
                                 qpResp.isTransactionCompleted(), qpResp.getReceiverClientId(), queuesDownstreamHandler));
                     }
                     futureResponse.complete(qpResp);
                 }

                 @Override
                 public void onError(Throwable t) {
                     log.error("Error in QueuesDownstreamResponse StreamObserver: ", t);
                     QueuesPollResponse qpResp = QueuesPollResponse.builder()
                             .error(t.getMessage())
                             .isError(true)
                             .build();
                     futureResponse.complete(qpResp);
                 }

                 @Override
                 public void onCompleted() {
                     log.debug("QueuesDownstreamResponse StreamObserver completed.");
                 }
             };
             queuesDownstreamHandler = kubeMQClient.getAsyncClient().queuesDownstream(request);
         }
        queuesDownstreamHandler.onNext(queuesPollRequest.encode(kubeMQClient.getClientId()));
        try {
            return futureResponse.get();
        } catch (Exception e) {
            log.error("Error waiting for Queue Message response: ", e);
            throw new RuntimeException("Failed to get response", e);
        }
    }
}
