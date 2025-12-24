package io.kubemq.sdk.cq;

import io.grpc.stub.StreamObserver;
import kubemq.Kubemq;
import kubemq.Kubemq.Subscribe;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class CommandsSubscription {

    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final ScheduledExecutorService reconnectExecutor =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kubemq-commands-reconnect");
            t.setDaemon(true);
            return t;
        });

    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    // Expose executor for shutdown hook
    public static ScheduledExecutorService getReconnectExecutor() {
        return reconnectExecutor;
    }

    private String channel;
    private String group;
    private Consumer<CommandMessageReceived> onReceiveCommandCallback;
    private Consumer<String> onErrorCallback;
    /**
     * Observer for the subscription.
     * This field is excluded from the builder and setter.
     */
    @Setter
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
            log.info("Subscription cancelled for channel: {}", channel);
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
                // IF gRPC exception attempts to retry
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
        int attempt = reconnectAttempts.incrementAndGet();

        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            log.error("Max reconnection attempts ({}) reached for channel: {}",
                      MAX_RECONNECT_ATTEMPTS, channel);
            raiseOnError("Max reconnection attempts reached after " + attempt + " tries");
            return;
        }

        // Exponential backoff: base * 2^(attempt-1), capped at 60 seconds
        long delay = Math.min(
            cQClient.getReconnectIntervalInMillis() * (1L << (attempt - 1)),
            60000L
        );

        log.info("Scheduling reconnection attempt {} for channel {} in {}ms",
                 attempt, channel, delay);

        reconnectExecutor.schedule(() -> {
            try {
                cQClient.getAsyncClient().subscribeToRequests(
                    this.encode(cQClient.getClientId(), cQClient),
                    this.getObserver()
                );
                reconnectAttempts.set(0); // Reset on success
                log.info("Successfully reconnected to channel {} after {} attempts",
                         channel, attempt);
            } catch (Exception e) {
                log.error("Reconnection attempt {} failed for channel {}", attempt, channel, e);
                reconnect(cQClient); // Schedule next attempt (not recursive stack)
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Resets the reconnection attempt counter. Call this on successful message receive.
     */
    public void resetReconnectAttempts() {
        reconnectAttempts.set(0);
    }

    @Override
    public String toString() {
        return "CommandsSubscription: channel=" + channel + ", group=" + group;
    }
}
