package io.kubemq.sdk.common;

import io.kubemq.sdk.exception.ErrorCategory;
import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.KubeMQException;
import io.kubemq.sdk.observability.KubeMQLogger;
import io.kubemq.sdk.observability.KubeMQLoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Shared reconnection logic for subscription classes.
 * Implements exponential backoff with a configurable max attempt count.
 *
 * <p>Replaces duplicated reconnection code across EventsSubscription,
 * EventsStoreSubscription, CommandsSubscription, and QueriesSubscription.</p>
 */
@Internal
public class SubscriptionReconnectHandler {

    private static final KubeMQLogger log = KubeMQLoggerFactory.getLogger(SubscriptionReconnectHandler.class);
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final long MAX_BACKOFF_MS = 60_000L;

    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final ScheduledExecutorService executor;
    private final long baseIntervalMs;
    private final String channel;
    private final String operationName;

    public SubscriptionReconnectHandler(ScheduledExecutorService executor,
                                        long baseIntervalMs,
                                        String channel,
                                        String operationName) {
        this.executor = executor;
        this.baseIntervalMs = baseIntervalMs;
        this.channel = channel;
        this.operationName = operationName;
    }

    /**
     * Schedule a reconnection attempt with exponential backoff.
     *
     * @param reconnectAction action to perform reconnection (may throw)
     * @param onError         callback for error reporting
     */
    public void scheduleReconnect(Runnable reconnectAction, Consumer<KubeMQException> onError) {
        int attempt = reconnectAttempts.incrementAndGet();

        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            log.error("Max reconnection attempts reached",
                      "maxAttempts", MAX_RECONNECT_ATTEMPTS,
                      "channel", channel);
            onError.accept(KubeMQException.newBuilder()
                .code(ErrorCode.CONNECTION_FAILED)
                .category(ErrorCategory.TRANSIENT)
                .retryable(false)
                .message("Max reconnection attempts reached after " + attempt + " tries")
                .operation(operationName)
                .channel(channel)
                .build());
            return;
        }

        long delay = Math.min(baseIntervalMs * (1L << (attempt - 1)), MAX_BACKOFF_MS);

        log.info("Scheduling reconnection attempt",
                 "attempt", attempt,
                 "channel", channel,
                 "delay_ms", delay);

        executor.schedule(() -> {
            try {
                reconnectAction.run();
                reconnectAttempts.set(0);
                log.info("Successfully reconnected",
                         "channel", channel,
                         "attempts", attempt);
            } catch (Exception e) {
                log.error("Reconnection attempt failed", e,
                         "attempt", attempt,
                         "channel", channel);
                scheduleReconnect(reconnectAction, onError);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    public void resetAttempts() {
        reconnectAttempts.set(0);
    }
}
