package io.kubemq.sdk.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages connection-level reconnection with exponential backoff + jitter.
 * Integrates with the ConnectionStateMachine and MessageBuffer.
 *
 * <p>This class centralizes all reconnection logic, replacing the
 * per-subscription reconnection previously in EventsSubscription,
 * EventsStoreSubscription, CommandsSubscription, and QueriesSubscription.</p>
 */
public class ReconnectionManager {

    private static final Logger log = LoggerFactory.getLogger(ReconnectionManager.class);

    private final ReconnectionConfig config;
    private final ConnectionStateMachine stateMachine;
    private final AtomicInteger attemptCounter = new AtomicInteger(0);
    private final ScheduledExecutorService scheduler;

    private volatile ScheduledFuture<?> pendingReconnect;

    public ReconnectionManager(ReconnectionConfig config, ConnectionStateMachine stateMachine) {
        this.config = config;
        this.stateMachine = stateMachine;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kubemq-reconnect-manager");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Start the reconnection cycle. Called when a connection loss is detected.
     *
     * @param reconnectAction the action to perform on each attempt (e.g., re-create channel)
     */
    public void startReconnection(Runnable reconnectAction) {
        int attempt = attemptCounter.incrementAndGet();
        int maxAttempts = config.getMaxReconnectAttempts();

        if (maxAttempts != -1 && attempt > maxAttempts) {
            log.error("Max reconnection attempts ({}) exhausted", maxAttempts);
            stateMachine.transitionTo(ConnectionState.CLOSED);
            return;
        }

        long delay = computeDelay(attempt);
        log.info("Scheduling reconnection attempt {}{} in {}ms",
                 attempt,
                 maxAttempts == -1 ? "" : "/" + maxAttempts,
                 delay);

        stateMachine.setCurrentReconnectAttempt(attempt);
        stateMachine.transitionTo(ConnectionState.RECONNECTING);

        pendingReconnect = scheduler.schedule(() -> {
            try {
                reconnectAction.run();
                attemptCounter.set(0);
                stateMachine.transitionTo(ConnectionState.READY);
                log.info("Reconnection successful after {} attempts", attempt);
            } catch (Exception e) {
                log.warn("Reconnection attempt {} failed: {}", attempt, e.getMessage());
                startReconnection(reconnectAction);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Cancel any pending reconnection attempt. Called during graceful shutdown.
     */
    public void cancel() {
        if (pendingReconnect != null) {
            pendingReconnect.cancel(false);
        }
        attemptCounter.set(0);
    }

    /**
     * Shutdown the scheduler. Called during client close.
     */
    public void shutdown() {
        cancel();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public long computeDelay(int attempt) {
        double baseDelay = config.getInitialReconnectDelayMs()
            * Math.pow(config.getReconnectBackoffMultiplier(), attempt - 1);
        long cappedDelay = (long) Math.min(baseDelay, config.getMaxReconnectDelayMs());

        if (config.isReconnectJitterEnabled()) {
            long minDelay = config.getInitialReconnectDelayMs() / 2;
            long jitteredDelay = ThreadLocalRandom.current().nextLong(cappedDelay + 1);
            return Math.max(minDelay, jitteredDelay);
        }
        return cappedDelay;
    }

    /** @return current attempt count */
    public int getAttemptCount() {
        return attemptCounter.get();
    }
}
