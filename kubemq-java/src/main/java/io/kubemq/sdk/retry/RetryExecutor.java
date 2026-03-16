package io.kubemq.sdk.retry;

import io.kubemq.sdk.exception.ErrorCategory;
import io.kubemq.sdk.exception.ErrorClassifier;
import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.KubeMQException;
import io.kubemq.sdk.exception.OperationCancelledException;
import io.kubemq.sdk.exception.RetryThrottledException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes operations with retry according to the configured RetryPolicy. Thread-safe. One instance
 * per client.
 */
public class RetryExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(RetryExecutor.class);

  private final RetryPolicy policy;
  private final Semaphore concurrencyLimiter;
  private final Supplier<Boolean> connectionReadySupplier;

  /**
   * @param policy the retry policy
   * @param connectionReadySupplier returns true when the connection is READY. May be null during
   *     unit tests or when connection state is not available.
   */
  public RetryExecutor(RetryPolicy policy, Supplier<Boolean> connectionReadySupplier) {
    this.policy = policy;
    this.connectionReadySupplier = connectionReadySupplier;
    this.concurrencyLimiter =
        policy.getMaxConcurrentRetries() > 0
            ? new Semaphore(policy.getMaxConcurrentRetries())
            : null;
  }

  /**
   * Convenience constructor for when connection state is not tracked.
   *
   * @param policy the policy
   */
  public RetryExecutor(RetryPolicy policy) {
    this(policy, null);
  }

  /**
   * Executes the given operation with retry.
   *
   * @param operation the callable to execute
   * @param operationName name for logging/error messages
   * @param channel the target channel (for error context)
   * @param safety the retry safety classification for this operation
   * @param <T> return type
   * @return the result of the operation
   * @throws KubeMQException if all retries exhausted or non-retryable error
   */
  public <T> T execute(
      Callable<T> operation, String operationName, String channel, OperationSafety safety) {
    if (!policy.isEnabled()) {
      return executeOnce(operation, operationName, channel);
    }

    KubeMQException lastError = null;
    Instant startTime = Instant.now();
    int maxAttempts = policy.getMaxRetries() + 1;

    for (int attempt = 0; attempt < maxAttempts; attempt++) {
      try {
        return executeOnce(operation, operationName, channel);
      } catch (KubeMQException ex) {
        lastError = ex;

        if (!safety.canRetry(ex)) {
          LOG.debug(
              "[{}] Non-retryable error on attempt {}: {}",
              operationName,
              attempt + 1,
              ex.getCode());
          throw ex;
        }

        if (ex.getCode() == ErrorCode.UNKNOWN_ERROR && attempt >= 1) {
          LOG.debug("[{}] UNKNOWN error retried once, giving up", operationName);
          throw ex;
        }

        if (attempt == maxAttempts - 1) {
          break;
        }

        if (concurrencyLimiter != null && !concurrencyLimiter.tryAcquire()) {
          throw RetryThrottledException.builder()
              .message(
                  "Retry throttled: concurrent retry limit reached ("
                      + policy.getMaxConcurrentRetries()
                      + ")")
              .operation(operationName)
              .channel(channel)
              .cause(ex)
              .build();
        }

        try {
          if (connectionReadySupplier != null && !connectionReadySupplier.get()) {
            LOG.debug("[{}] Connection not ready, abandoning retry", operationName);
            throw ex;
          }

          Duration backoff = policy.computeBackoff(attempt);
          if (ErrorClassifier.shouldUseExtendedBackoff(ex)) {
            backoff = backoff.multipliedBy(2);
          }

          LOG.debug(
              "[{}] Retry attempt {}/{} after {}ms (error: {})",
              operationName,
              attempt + 1,
              policy.getMaxRetries(),
              backoff.toMillis(),
              ex.getCode());

          Thread.sleep(backoff.toMillis());
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw OperationCancelledException.builder()
              .code(ErrorCode.CANCELLED_BY_CLIENT)
              .message("Retry interrupted")
              .operation(operationName)
              .channel(channel)
              .cause(ie)
              .build();
        } finally {
          if (concurrencyLimiter != null) {
            concurrencyLimiter.release();
          }
        }
      }
    }

    Duration totalDuration = Duration.between(startTime, Instant.now());
    throw KubeMQException.newBuilder()
        .code(lastError.getCode())
        .category(lastError.getCategory())
        .retryable(false)
        .message(
            String.format(
                "%s failed on channel \"%s\": %s. Retries exhausted: %d/%d attempts over %s",
                operationName,
                channel,
                lastError.getMessage(),
                policy.getMaxRetries(),
                policy.getMaxRetries(),
                formatDuration(totalDuration)))
        .operation(operationName)
        .channel(channel)
        .cause(lastError)
        .build();
  }

  private <T> T executeOnce(Callable<T> operation, String operationName, String channel) {
    try {
      return operation.call();
    } catch (KubeMQException ex) {
      throw ex;
    } catch (Exception ex) {
      throw KubeMQException.newBuilder()
          .code(ErrorCode.UNKNOWN_ERROR)
          .category(ErrorCategory.FATAL)
          .retryable(false)
          .message(operationName + " failed: " + ex.getMessage())
          .operation(operationName)
          .channel(channel)
          .cause(ex)
          .build();
    }
  }

  private static String formatDuration(Duration d) {
    long millis = d.toMillis();
    if (millis < 1000) {
      return millis + "ms";
    }
    return String.format("%.1fs", millis / 1000.0);
  }
}
