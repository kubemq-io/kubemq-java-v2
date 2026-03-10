package io.kubemq.sdk.retry;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Immutable retry policy for KubeMQ operations.
 * Created via builder and cannot be modified after construction.
 */
public final class RetryPolicy {

    public enum JitterType { FULL, EQUAL, NONE }

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
        this.initialBackoff = requireDurationInRange(builder.initialBackoff,
            Duration.ofMillis(50), Duration.ofSeconds(5), "initialBackoff");
        this.maxBackoff = requireDurationInRange(builder.maxBackoff,
            Duration.ofSeconds(1), Duration.ofSeconds(120), "maxBackoff");
        this.multiplier = requireInRange(builder.multiplier, 1.5, 3.0, "multiplier");
        this.jitterType = builder.jitterType;
        this.maxConcurrentRetries = requireInRange(builder.maxConcurrentRetries, 0, 100, "maxConcurrentRetries");
    }

    public int getMaxRetries() { return maxRetries; }
    public Duration getInitialBackoff() { return initialBackoff; }
    public Duration getMaxBackoff() { return maxBackoff; }
    public double getMultiplier() { return multiplier; }
    public JitterType getJitterType() { return jitterType; }
    public int getMaxConcurrentRetries() { return maxConcurrentRetries; }

    /**
     * Returns true if retries are enabled (maxRetries &gt; 0).
     */
    public boolean isEnabled() { return maxRetries > 0; }

    /**
     * Computes backoff duration for a given attempt number (0-indexed).
     * Uses ThreadLocalRandom.current() at point of use (per J-7 constraint).
     */
    public Duration computeBackoff(int attempt) {
        double baseMs = initialBackoff.toMillis() * Math.pow(multiplier, attempt);
        long cappedMs = (long) Math.min(baseMs, maxBackoff.toMillis());

        switch (jitterType) {
            case FULL:
                return Duration.ofMillis(
                    ThreadLocalRandom.current().nextLong(0, cappedMs + 1));
            case EQUAL:
                long half = cappedMs / 2;
                return Duration.ofMillis(
                    half + ThreadLocalRandom.current().nextLong(0, half + 1));
            case NONE:
                return Duration.ofMillis(cappedMs);
            default:
                return Duration.ofMillis(cappedMs);
        }
    }

    /**
     * Returns the worst-case total latency for this policy with a given operation timeout.
     */
    public Duration worstCaseLatency(Duration operationTimeout) {
        long totalMs = operationTimeout.toMillis();
        for (int i = 0; i < maxRetries; i++) {
            long backoffMs = (long) Math.min(
                initialBackoff.toMillis() * Math.pow(multiplier, i),
                maxBackoff.toMillis());
            totalMs += backoffMs + operationTimeout.toMillis();
        }
        return Duration.ofMillis(totalMs);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int maxRetries = 3;
        private Duration initialBackoff = Duration.ofMillis(500);
        private Duration maxBackoff = Duration.ofSeconds(30);
        private double multiplier = 2.0;
        private JitterType jitterType = JitterType.FULL;
        private int maxConcurrentRetries = 10;

        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }
        public Builder initialBackoff(Duration d) { this.initialBackoff = d; return this; }
        public Builder maxBackoff(Duration d) { this.maxBackoff = d; return this; }
        public Builder multiplier(double m) { this.multiplier = m; return this; }
        public Builder jitterType(JitterType j) { this.jitterType = j; return this; }
        public Builder maxConcurrentRetries(int n) { this.maxConcurrentRetries = n; return this; }

        public RetryPolicy build() { return new RetryPolicy(this); }
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

    private static Duration requireDurationInRange(Duration value, Duration min, Duration max, String name) {
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException(
                name + " must be in range [" + min + ", " + max + "], got: " + value);
        }
        return value;
    }
}
