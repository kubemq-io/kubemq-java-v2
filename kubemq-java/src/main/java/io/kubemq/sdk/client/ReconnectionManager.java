package io.kubemq.sdk.client;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages connection-level reconnection with exponential backoff + jitter. Integrates with the
 * ConnectionStateMachine and MessageBuffer.
 *
 * <p>This class centralizes all reconnection logic, replacing the per-subscription reconnection
 * previously in EventsSubscription, EventsStoreSubscription, CommandsSubscription, and
 * QueriesSubscription.
 */
public class ReconnectionManager {

  private static final Logger LOG = LoggerFactory.getLogger(ReconnectionManager.class);

  private final ReconnectionConfig config;
  private final ConnectionStateMachine stateMachine;

  private final AtomicInteger attemptCounter = new AtomicInteger(0);
  // JV-3: Guard to prevent concurrent reconnection loops
  private final AtomicBoolean reconnecting = new AtomicBoolean(false);

  private final ScheduledExecutorService scheduler;

  private volatile ScheduledFuture<?> pendingReconnect;

  // JV-2: Optional ping check to verify channel is actually connected before marking READY
  private volatile Runnable pingCheck;

  /**
   * Constructs a new instance.
   *
   * @param config the config
   * @param stateMachine the state machine
   */
  public ReconnectionManager(ReconnectionConfig config, ConnectionStateMachine stateMachine) {
    this.config = config;
    this.stateMachine = stateMachine;
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
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
    // JV-3: Prevent concurrent reconnection loops
    if (!reconnecting.compareAndSet(false, true)) {
      LOG.debug("Reconnection already in progress, skipping duplicate request");
      return;
    }

    scheduleReconnectionAttempt(reconnectAction);
  }

  private void scheduleReconnectionAttempt(Runnable reconnectAction) {
    int attempt = attemptCounter.incrementAndGet();
    int maxAttempts = config.getMaxReconnectAttempts();

    if (maxAttempts != -1 && attempt > maxAttempts) {
      LOG.error("Max reconnection attempts ({}) exhausted", maxAttempts);
      reconnecting.set(false);
      stateMachine.transitionTo(ConnectionState.CLOSED);
      return;
    }

    long delay = computeDelay(attempt);
    LOG.info(
        "Scheduling reconnection attempt {}{} in {}ms",
        attempt,
        maxAttempts == -1 ? "" : "/" + maxAttempts,
        delay);

    stateMachine.setCurrentReconnectAttempt(attempt);
    stateMachine.transitionTo(ConnectionState.RECONNECTING);

    pendingReconnect =
        scheduler.schedule(
            () -> {
              try {
                reconnectAction.run();
                // JV-2: Verify channel is actually connected before marking READY
                if (pingCheck != null) {
                  try {
                    pingCheck.run();
                  } catch (Exception pingEx) {
                    LOG.warn(
                        "Reconnection attempt {} succeeded but ping verification failed: {}",
                        attempt,
                        pingEx.getMessage());
                    scheduleReconnectionAttempt(reconnectAction);
                    return;
                  }
                }
                attemptCounter.set(0);
                reconnecting.set(false);
                stateMachine.transitionTo(ConnectionState.READY);
                LOG.info("Reconnection successful after {} attempts", attempt);
              } catch (Exception e) {
                LOG.warn("Reconnection attempt {} failed: {}", attempt, e.getMessage());
                scheduleReconnectionAttempt(reconnectAction);
              }
            },
            delay,
            TimeUnit.MILLISECONDS);
  }

  /**
   * Sets a ping check that will be executed after a successful reconnect action but before
   * transitioning to READY state. This verifies the channel is actually connected.
   *
   * @param pingCheck a runnable that throws on failure (e.g., ping with short deadline)
   */
  public void setPingCheck(Runnable pingCheck) {
    this.pingCheck = pingCheck;
  }

  /** Cancel any pending reconnection attempt. Called during graceful shutdown. */
  public void cancel() {
    if (pendingReconnect != null) {
      pendingReconnect.cancel(false);
    }
    attemptCounter.set(0);
    reconnecting.set(false);
  }

  /** Shutdown the scheduler. Called during client close. */
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

  /**
   * Computes the delay.
   *
   * @param attempt the attempt
   * @return the result
   */
  public long computeDelay(int attempt) {
    double baseDelay =
        config.getInitialReconnectDelayMs()
            * Math.pow(config.getReconnectBackoffMultiplier(), attempt - 1);
    long cappedDelay = (long) Math.min(baseDelay, config.getMaxReconnectDelayMs());

    if (config.isReconnectJitterEnabled()) {
      long minDelay = config.getInitialReconnectDelayMs() / 2;
      long jitteredDelay = ThreadLocalRandom.current().nextLong(cappedDelay + 1);
      return Math.max(minDelay, jitteredDelay);
    }
    return cappedDelay;
  }

  /**
   * @return current attempt count
   */
  public int getAttemptCount() {
    return attemptCounter.get();
  }
}
