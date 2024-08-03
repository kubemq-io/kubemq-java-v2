package io.kubemq.sdk.cq;

import io.grpc.stub.StreamObserver;
import kubemq.Kubemq;
import kubemq.Kubemq.Subscribe;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class QueriesSubscription {

    private String channel;
    private String group;
    private Consumer<QueryMessageReceived> onReceiveQueryCallback;
    private Consumer<String> onErrorCallback;
    /**
     * Observer for the subscription.
     * This field is excluded from the builder and setter.
     */
    @Setter(onMethod_ = @__(@java.lang.SuppressWarnings("unused")))
    private transient StreamObserver<Kubemq.Request> observer;

    public void raiseOnReceiveMessage(QueryMessageReceived receivedQuery) {
        if (onReceiveQueryCallback != null) {
            onReceiveQueryCallback.accept(receivedQuery);
        }
    }

    public void raiseOnError(String msg) {
        if (onErrorCallback != null) {
            onErrorCallback.accept(msg);
        }
    }

    /**
     * Cancel the subscription
     */
    public void cancel() {
        if (observer != null) {
            observer.onCompleted();
            log.debug("Subscription Cancelled");
        }
    }

    public void validate() {
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("query subscription must have a channel.");
        }
        if (onReceiveQueryCallback == null) {
            throw new IllegalArgumentException("query subscription must have a on_receive_query_callback function.");
        }
    }

    public Subscribe encode(String clientId,CQClient cQClient) {
        Subscribe request = Subscribe.newBuilder()
                .setChannel(this.channel)
                .setGroup(this.group != null ? this.group :"")
                .setClientID(clientId)
                .setSubscribeTypeData(Subscribe.SubscribeType.Queries)
                .setSubscribeTypeDataValue(Subscribe.SubscribeType.Queries_VALUE)
                .build();

        observer = new StreamObserver<Kubemq.Request>() {
            @Override
            public void onNext(Kubemq.Request messageReceive) {
                log.debug("QueriesSubscription-> QueryMessageReceived Received: '{}'", messageReceive);
                raiseOnReceiveMessage(QueryMessageReceived.decode(messageReceive));
            }

            @Override
            public void onError(Throwable t) {
                log.error("Error:-- > "+t.getMessage());
                raiseOnError(t.getMessage());
                // IF gRPC exception attempt to retry
                if(t instanceof io.grpc.StatusRuntimeException){
                    io.grpc.StatusRuntimeException se =(io.grpc.StatusRuntimeException)t;
                    reconnect(cQClient);
                }
            }

            @Override
            public void onCompleted() {
                log.debug("QueriesSubscription Stream completed.");
            }
        };

        return request;
    }

    private void reconnect(CQClient cQClient) {
            try {
                Thread.sleep(cQClient.getReconnectIntervalSeconds());
                log.debug("Attempting to re-subscribe...");
                cQClient.getAsyncClient().subscribeToRequests(this.encode(cQClient.getClientId(), cQClient), this.getObserver());
                log.debug("Re-subscribed successfully");
            } catch (Exception e) {
                log.error("Re-subscribe attempt failed", e);
                this.reconnect(cQClient);
        }
    }

    @Override
    public String toString() {
        return "QueriesSubscription: channel=" + channel + ", group=" + group;
    }
}
