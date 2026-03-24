package io.kubemq.sdk.common;

import io.kubemq.sdk.exception.ErrorCategory;
import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.KubeMQException;
import io.kubemq.sdk.observability.KubeMQLogger;
import io.kubemq.sdk.observability.KubeMQLoggerFactory;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Shared reconnection logic for subscription classes. Implements exponential backoff with a
 * configurable max attempt count.
 *
 * <p>Replaces duplicated reconnection code across EventsSubscription, EventsStoreSubscription,
 * CommandsSubscription, and QueriesSubscription.
 */
@Internal
public class SubscriptionReconnectHandler {

  private static final KubeMQLogger LOG =
      KubeMQLoggerFactory.getLogger(SubscriptionReconnectHandler.class);
  private static final int DEFAULT_MAX_RECONNECT_ATTEMPTS = 50;
  private static final long MAX_BACKOFF_MS = 60_000L;
  // JV-1c: Short poll interval used when waiting for connection READY.
  // This ensures subscriptions re-establish quickly once the connection is back,
  // rather than waiting up to baseIntervalMs (typically 1000ms) per poll cycle.
  private static final long READY_POLL_INTERVAL_MS = 200L;

  private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
  private final ScheduledExecutorService executor;
  private final long baseIntervalMs;
  private final String channel;
  private final String operationName;
  private final int maxReconnectAttempts;
  // JV-8: Optional readiness check — subscription reconnect waits for channel READY
  private volatile BooleanSupplier connectionReadyCheck;

  /**
   * Constructs a new instance with default max reconnect attempts (50).
   *
   * @param executor the executor
   * @param baseIntervalMs the base interval ms
   * @param channel the channel
   * @param operationName the operation name
   */
  public SubscriptionReconnectHandler(
      ScheduledExecutorService executor,
      long baseIntervalMs,
      String channel,
      String operationName) {
    this(executor, baseIntervalMs, channel, operationName, DEFAULT_MAX_RECONNECT_ATTEMPTS);
  }

  /**
   * Constructs a new instance with configurable max reconnect attempts.
   *
   * @param executor the executor
   * @param baseIntervalMs the base interval ms
   * @param channel the channel
   * @param operationName the operation name
   * @param maxReconnectAttempts maximum reconnection attempts before giving up
   */
  public SubscriptionReconnectHandler(
      ScheduledExecutorService executor,
      long baseIntervalMs,
      String channel,
      String operationName,
      int maxReconnectAttempts) {
    this.executor = executor;
    this.baseIntervalMs = baseIntervalMs;
    this.channel = channel;
    this.operationName = operationName;
    this.maxReconnectAttempts = maxReconnectAttempts > 0 ? maxReconnectAttempts : DEFAULT_MAX_RECONNECT_ATTEMPTS;
  }

  /**
   * Schedule a reconnection attempt with exponential backoff.
   *
   * @param reconnectAction action to perform reconnection (may throw)
   * @param onError callback for error reporting
   */
  public void scheduleReconnect(Runnable reconnectAction, Consumer<KubeMQException> onError) {
    int attempt = reconnectAttempts.incrementAndGet();

    if (attempt > maxReconnectAttempts) {
      LOG.error(
          "Max reconnection attempts reached",
          "maxAttempts",
          maxReconnectAttempts,
          "channel",
          channel);
      onError.accept(
          KubeMQException.newBuilder()
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

    LOG.info(
        "Scheduling reconnection attempt",
        "attempt",
        attempt,
        "channel",
        channel,
        "delay_ms",
        delay);

    executor.schedule(
        () -> {
          try {
            // JV-8: Wait for channel-level READY before attempting resubscription
            BooleanSupplier readyCheck = connectionReadyCheck;
            if (readyCheck != null && !readyCheck.getAsBoolean()) {
              LOG.info(
                  "Connection not ready, delaying subscription reconnect",
                  "channel",
                  channel,
                  "attempt",
                  attempt);
              // Don't increment attempt counter — this isn't a failed attempt,
              // just a deferred one. Use a short poll interval (JV-1c) so that
              // the subscription re-establishes quickly once READY, rather than
              // waiting the full exponential backoff delay.
              reconnectAttempts.decrementAndGet();
              scheduleReadyPoll(reconnectAction, onError);
              return;
            }
            reconnectAction.run();
            reconnectAttempts.set(0);
            LOG.info("Successfully reconnected", "channel", channel, "attempts", attempt);
          } catch (Exception e) {
            LOG.error("Reconnection attempt failed", e, "attempt", attempt, "channel", channel);
            scheduleReconnect(reconnectAction, onError);
          }
        },
        delay,
        TimeUnit.MILLISECONDS);
  }

  /**
   * JV-1c: Schedule a fast poll to check for connection READY. Uses a short fixed interval
   * ({@value #READY_POLL_INTERVAL_MS}ms) instead of the exponential backoff delay, ensuring
   * subscriptions re-establish within ~200ms of the connection becoming READY rather than
   * up to 1000ms+ with the standard backoff. Once READY is detected, this delegates back to
   * {@link #scheduleReconnect} for the actual resubscription attempt.
   */
  private void scheduleReadyPoll(Runnable reconnectAction, Consumer<KubeMQException> onError) {
    executor.schedule(
        () -> {
          BooleanSupplier readyCheck = connectionReadyCheck;
          if (readyCheck != null && !readyCheck.getAsBoolean()) {
            // Still not ready — poll again quickly
            scheduleReadyPoll(reconnectAction, onError);
          } else {
            // Connection is ready — attempt resubscription immediately
            try {
              int attempt = reconnectAttempts.incrementAndGet();
              reconnectAction.run();
              reconnectAttempts.set(0);
              LOG.info("Successfully reconnected after ready poll", "channel", channel,
                  "attempts", attempt);
            } catch (Exception e) {
              LOG.error("Reconnection after ready poll failed", e, "channel", channel);
              scheduleReconnect(reconnectAction, onError);
            }
          }
        },
        READY_POLL_INTERVAL_MS,
        TimeUnit.MILLISECONDS);
  }

  /**
   * Sets a readiness check that is evaluated before attempting resubscription.
   * If the check returns false, the reconnection attempt is delayed.
   *
   * @param readyCheck returns true if the connection is ready for resubscription
   */
  public void setConnectionReadyCheck(BooleanSupplier readyCheck) {
    this.connectionReadyCheck = readyCheck;
  }

  /** Resets the attempts. */
  public void resetAttempts() {
    reconnectAttempts.set(0);
  }
}
