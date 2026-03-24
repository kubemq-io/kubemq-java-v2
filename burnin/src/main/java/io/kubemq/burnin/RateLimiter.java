// True token-bucket rate limiter with 1-second burst capacity.
// Spec Section 4.1: +/-5% accuracy over 10s windows.
// Uses nanoTime + LockSupport.parkNanos for sub-ms pacing at high rates.

package io.kubemq.burnin;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * Token-bucket rate limiter. Bucket size = 1 second of tokens (burst capacity).
 * Uses LockSupport.parkNanos for sub-ms precision, with busy-spin for waits under 100us.
 * Thread-safe via synchronized.
 * <p>
 * {@code waitForRate(stopFlag)} returns true if a token was consumed,
 * false if the stop flag is set.
 */
public final class RateLimiter {

    private final double rate;
    private final double maxTokens;
    private double tokens;
    private long lastRefillNanos;

    // Below this threshold (100 microseconds), busy-spin instead of park
    private static final long SPIN_THRESHOLD_NANOS = 100_000L;

    /**
     * Create a rate limiter with the given rate in messages per second.
     * Rate of 0 or less means unlimited (waitForRate returns immediately).
     */
    public RateLimiter(double rate) {
        this.rate = rate;
        this.maxTokens = Math.max(rate, 1.0);
        this.tokens = maxTokens;
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * Wait until a token is available or the stop flag is set.
     * Returns true if a token was consumed, false if stopped.
     */
    public boolean waitForRate(AtomicBoolean stopFlag) {
        if (stopFlag.get()) return false;
        if (rate <= 0) return true; // unlimited

        while (!stopFlag.get()) {
            long waitNanos;
            synchronized (this) {
                refill();
                if (tokens >= 1.0) {
                    tokens -= 1.0;
                    return true;
                }
                // Calculate how long to wait for next token in nanoseconds.
                waitNanos = (long) (((1.0 - tokens) / rate) * 1_000_000_000.0);
            }

            if (waitNanos <= 0) {
                continue;
            }

            // For very short waits, busy-spin for accuracy
            if (waitNanos <= SPIN_THRESHOLD_NANOS) {
                long deadline = System.nanoTime() + waitNanos;
                while (System.nanoTime() < deadline) {
                    if (stopFlag.get()) return false;
                    Thread.onSpinWait();
                }
            } else {
                // For longer waits, use parkNanos (sub-ms capable, unlike Thread.sleep)
                LockSupport.parkNanos(waitNanos);
                if (Thread.interrupted()) {
                    return false;
                }
            }

            // After delay, try again.
            synchronized (this) {
                refill();
                if (tokens >= 1.0) {
                    tokens -= 1.0;
                    return true;
                }
            }
        }

        return false;
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsedSec = (now - lastRefillNanos) / 1_000_000_000.0;
        tokens = Math.min(maxTokens, tokens + elapsedSec * rate);
        lastRefillNanos = now;
    }
}
