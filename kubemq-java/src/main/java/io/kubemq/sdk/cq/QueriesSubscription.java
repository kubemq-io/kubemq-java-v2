package io.kubemq.sdk.cq;

import io.grpc.stub.StreamObserver;
import kubemq.Kubemq;
import kubemq.Kubemq.Subscribe;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;
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

    private AtomicInteger retryCount = new AtomicInteger(0);
    private void reconnect(CQClient cQClient) {

        int maxRetries = 50000;  // Set your maximum retry attempts
        long retryInterval = 1000 * cQClient.getReconnectIntervalSeconds();

        while (retryCount.get() < maxRetries) {
            try {
                log.debug("Attempting to re-subscribe... Attempt #" + retryCount.incrementAndGet());
                // Your method to subscribe again
                cQClient.subscribeToQueries(this);
                log.debug("Re-subscribed successfully");
                break;
            } catch (Exception e) {
                log.error("Re-subscribe attempt failed", e);
                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("Re-subscribe sleep interrupted", ie);
                    break;
                }
            }
        }

        if (retryCount.get() >= maxRetries) {
            log.error("Max retries reached. Could not re-subscribe to queries.");
            raiseOnError("Max retries reached. Could not re-subscribe to queries.");
        }
    }

    @Override
    public String toString() {
        return "QueriesSubscription: channel=" + channel + ", group=" + group;
    }
}
