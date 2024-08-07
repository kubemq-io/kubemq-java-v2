package io.kubemq.sdk.pubsub;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import kubemq.Kubemq;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@Data
@Slf4j
public class EventStreamHelper {

    private StreamObserver<Kubemq.Event> queuesUpStreamHandler = null;
    private StreamObserver<Kubemq.Result> resultStreamObserver;
    private CompletableFuture<EventSendResult> futureResponse;

    public EventStreamHelper() {
        futureResponse = new CompletableFuture<>();
        this.resultStreamObserver =  new StreamObserver<Kubemq.Result>() {
            @Override
            public void onNext(Kubemq.Result result) {
                log.debug("Received EventSendResult: '{}'", result);
                futureResponse.complete(EventSendResult.decode(result));
            }
            @Override
            public void onError(Throwable t) {
                log.error("Error in EventSendResult: ", t);
                EventSendResult sendResult = new EventSendResult();
                sendResult.setError(t.getMessage());
                futureResponse.complete(sendResult);
            }
            @Override
            public void onCompleted() {
                log.debug("EventSendResult onCompleted.");
            }
        };
    }

    public void sendEventMessage(KubeMQClient kubeMQClient, Kubemq.Event event) {

        if (queuesUpStreamHandler == null) {
            queuesUpStreamHandler = kubeMQClient.getAsyncClient().sendEventsStream(resultStreamObserver);
        }
        queuesUpStreamHandler.onNext(event);
        log.debug("Event Message sent");
    }

    public EventSendResult sendEventStoreMessage(KubeMQClient kubeMQClient, Kubemq.Event event) {

        if (queuesUpStreamHandler == null) {
            queuesUpStreamHandler = kubeMQClient.getAsyncClient().sendEventsStream(resultStreamObserver);
        }
        queuesUpStreamHandler.onNext(event);
        log.debug("Event store Message send waiting for response");
        try {
            return futureResponse.get();
        } catch (Exception e) {
            log.error("Error waiting for response: ", e);
            throw new RuntimeException("Failed to get response", e);
        }
    }
}
