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
public class CommandsSubscription {

    private String channel;
    private String group;
    private Consumer<CommandMessageReceived> onReceiveCommandCallback;
    private Consumer<String> onErrorCallback;
    /**
     * Observer for the subscription.
     * This field is excluded from the builder and setter.
     */
    @Setter(onMethod_ = @__(@java.lang.SuppressWarnings("unused")))
    private transient StreamObserver<Kubemq.Request> observer;

    public void raiseOnReceiveMessage(CommandMessageReceived receivedCommand) {
        if (onReceiveCommandCallback != null) {
            onReceiveCommandCallback.accept(receivedCommand);
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
            log.error("Subscription Cancelled");
        }
    }

    public void validate() {
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("command subscription must have a channel.");
        }
        if (onReceiveCommandCallback == null) {
            throw new IllegalArgumentException("command subscription must have a on_receive_command_callback function.");
        }
    }

    public kubemq.Kubemq.Subscribe encode(String clientId, CQClient cQClient) {
        Subscribe request =  Subscribe.newBuilder()
                .setChannel(this.channel)
                .setGroup(this.group != null ? this.group : "")
                .setClientID(clientId)
                .setSubscribeTypeData(Subscribe.SubscribeType.Commands)
                .setSubscribeTypeDataValue(Subscribe.SubscribeType.Commands_VALUE)
                .build();

        observer = new StreamObserver<Kubemq.Request>() {
            @Override
            public void onNext(Kubemq.Request messageReceive) {
                log.debug("CommandsSubscription-> CommandMessageReceived Received: '{}'", messageReceive);
                raiseOnReceiveMessage(CommandMessageReceived.decode(messageReceive));
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
                log.debug("CommandsSubscription Stream completed.");
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
        return "CommandsSubscription: channel=" + channel + ", group=" + group;
    }
}
