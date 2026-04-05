package io.kubemq.sdk.retry;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Immutable retry policy for KubeMQ operations. Created via builder and cannot be modified after
 * construction.
 */
public final class RetryPolicy {

  public enum JitterType {
    FULL,
    EQUAL,
    NONE
  }

  public static final RetryPolicy DEFAULT = RetryPolicy.builder().build();
  public static final RetryPolicy DISABLED = RetryPolicy.builder().maxRetries(0).build();

  private final int maxRetries;
  private final Duration initialBackoff;
  private final Duration maxBackoff;
  private final double multiplier;
  private final JitterType jitterType;
  private final int maxConcurrentRetries;

  private RetryPolicy(Builder builder) {
    this.maxRetries = requireInRange(builder.maxRetries, 0, 10, "maxRetries");
    this.initialBackoff =
        requireDurationInRange(
            builder.initialBackoff, Duration.ofMillis(50), Duration.ofSeconds(5), "initialBackoff");
    this.maxBackoff =
        requireDurationInRange(
            builder.maxBackoff, Duration.ofSeconds(1), Duration.ofSeconds(120), "maxBackoff");
    this.multiplier = requireInRange(builder.multiplier, 1.5, 3.0, "multiplier");
    this.jitterType = builder.jitterType;
    this.maxConcurrentRetries =
        requireInRange(builder.maxConcurrentRetries, 0, 100, "maxConcurrentRetries");
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public Duration getInitialBackoff() {
    return initialBackoff;
  }

  public Duration getMaxBackoff() {
    return maxBackoff;
  }

  public double getMultiplier() {
    return multiplier;
  }

  public JitterType getJitterType() {
    return jitterType;
  }

  public int getMaxConcurrentRetries() {
    return maxConcurrentRetries;
  }

  /**
   * Returns true if retries are enabled (maxRetries &gt; 0).
   *
   * @return {@code true} if this policy will retry failed operations, {@code false} if retries are
   *     disabled
   */
  public boolean isEnabled() {
    return maxRetries > 0;
  }

  /**
   * Computes backoff duration for a given attempt number (0-indexed). Uses
   * ThreadLocalRandom.current() at point of use (per J-7 constraint).
   *
   * @param attempt the zero-based attempt number (0 for the first retry, 1 for the second, etc.)
   * @return the backoff duration for this attempt, capped at {@link #getMaxBackoff()} and
   *     randomized according to the configured {@link JitterType}
   */
  public Duration computeBackoff(int attempt) {
    double baseMs = initialBackoff.toMillis() * Math.pow(multiplier, attempt);
    long cappedMs = (long) Math.min(baseMs, maxBackoff.toMillis());

    switch (jitterType) {
      case FULL:
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(0, cappedMs + 1));
      case EQUAL:
        long half = cappedMs / 2;
        return Duration.ofMillis(half + ThreadLocalRandom.current().nextLong(0, half + 1));
      case NONE:
        return Duration.ofMillis(cappedMs);
      default:
        return Duration.ofMillis(cappedMs);
    }
  }

  /**
   * Returns the worst-case total latency for this policy with a given operation timeout.
   *
   * @param operationTimeout the timeout of a single operation attempt (used to calculate total time
   *     including all retries)
   * @return the maximum total duration including all retry attempts, backoff delays, and operation
   *     timeouts
   */
  public Duration worstCaseLatency(Duration operationTimeout) {
    long totalMs = operationTimeout.toMillis();
    for (int i = 0; i < maxRetries; i++) {
      long backoffMs =
          (long)
              Math.min(initialBackoff.toMillis() * Math.pow(multiplier, i), maxBackoff.toMillis());
      totalMs += backoffMs + operationTimeout.toMillis();
    }
    return Duration.ofMillis(totalMs);
  }

  /**
   * Creates a new builder for RetryPolicy.
   *
   * @return a new Builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for constructing RetryPolicy instances. */
  public static final class Builder {
    private int maxRetries = 3;
    private Duration initialBackoff = Duration.ofMillis(500);
    private Duration maxBackoff = Duration.ofSeconds(30);
    private double multiplier = 2.0;
    private JitterType jitterType = JitterType.FULL;
    private int maxConcurrentRetries = 10;

    /**
     * Sets the maximum number of retries.
     *
     * @param maxRetries the maximum number of retry attempts (0 to disable retries, max 10);
     *     defaults to 3
     * @return this builder for method chaining
     * @throws IllegalArgumentException if the value is outside the range [0, 10]
     */
    public Builder maxRetries(int maxRetries) {
      this.maxRetries = maxRetries;
      return this;
    }

    /**
     * Sets the initial backoff duration.
     *
     * @param d the delay before the first retry, in the range [50ms, 5s]; defaults to 500ms
     * @return this builder for method chaining
     * @throws IllegalArgumentException if the duration is outside the range [50ms, 5s]
     */
    public Builder initialBackoff(Duration d) {
      this.initialBackoff = d;
      return this;
    }

    /**
     * Sets the maximum backoff duration.
     *
     * @param d the upper bound on backoff duration, in the range [1s, 120s]; defaults to 30s.
     *     Backoff will never exceed this value regardless of the multiplier.
     * @return this builder for method chaining
     * @throws IllegalArgumentException if the duration is outside the range [1s, 120s]
     */
    public Builder maxBackoff(Duration d) {
      this.maxBackoff = d;
      return this;
    }

    /**
     * Sets the backoff multiplier.
     *
     * @param m the factor by which the backoff duration increases after each attempt, in the range
     *     [1.5, 3.0]; defaults to 2.0 (exponential backoff)
     * @return this builder for method chaining
     * @throws IllegalArgumentException if the value is outside the range [1.5, 3.0]
     */
    public Builder multiplier(double m) {
      this.multiplier = m;
      return this;
    }

    /**
     * Sets the jitter type.
     *
     * @param j the randomization strategy for backoff durations: {@link JitterType#FULL} randomizes
     *     over [0, backoff], {@link JitterType#EQUAL} over [backoff/2, backoff], and {@link
     *     JitterType#NONE} uses the exact computed backoff; defaults to {@link JitterType#FULL}
     * @return this builder for method chaining
     */
    public Builder jitterType(JitterType j) {
      this.jitterType = j;
      return this;
    }

    /**
     * Sets the maximum number of concurrent retries.
     *
     * @param n the maximum number of operations that can be retrying concurrently, in the range [0,
     *     100]; defaults to 10. Use 0 to disable the concurrency limit.
     * @return this builder for method chaining
     * @throws IllegalArgumentException if the value is outside the range [0, 100]
     */
    public Builder maxConcurrentRetries(int n) {
      this.maxConcurrentRetries = n;
      return this;
    }

    /**
     * Builds the RetryPolicy.
     *
     * @return the constructed RetryPolicy
     */
    public RetryPolicy build() {
      return new RetryPolicy(this);
    }
  }

  private static int requireInRange(int value, int min, int max, String name) {
    if (value < min || value > max) {
      throw new IllegalArgumentException(
          name + " must be in range [" + min + ", " + max + "], got: " + value);
    }
    return value;
  }

  private static double requireInRange(double value, double min, double max, String name) {
    if (value < min || value > max) {
      throw new IllegalArgumentException(
          name + " must be in range [" + min + ", " + max + "], got: " + value);
    }
    return value;
  }

  private static Duration requireDurationInRange(
      Duration value, Duration min, Duration max, String name) {
    if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
      throw new IllegalArgumentException(
          name + " must be in range [" + min + ", " + max + "], got: " + value);
    }
    return value;
  }
}
