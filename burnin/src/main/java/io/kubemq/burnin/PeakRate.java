// Peak rate tracking and HdrHistogram-based latency accumulation.
// Thread-safe with synchronized.

package io.kubemq.burnin;

import org.HdrHistogram.Histogram;

/**
 * 10-bucket sliding window peak rate tracker.
 * Each bucket represents one second. advance() is called once per second.
 * Thread-safe via synchronized.
 */
class PeakRateTracker {

    private static final int WINDOW_SIZE = 10;

    private final long[] buckets = new long[WINDOW_SIZE];
    private int idx;
    private double peak;

    /**
     * Record one event in the current bucket.
     */
    public synchronized void record() {
        buckets[idx]++;
    }

    /**
     * Advance the window by one second. Computes average over the window
     * and updates peak if the new average exceeds it.
     */
    public synchronized void advance() {
        long total = 0;
        for (int i = 0; i < WINDOW_SIZE; i++) total += buckets[i];
        double avg = (double) total / WINDOW_SIZE;
        if (avg > peak) peak = avg;
        idx = (idx + 1) % WINDOW_SIZE;
        buckets[idx] = 0;
    }

    /**
     * Get the peak average rate observed.
     */
    public synchronized double getPeak() {
        return peak;
    }

    /**
     * Reset all buckets and peak.
     */
    public synchronized void reset() {
        for (int i = 0; i < WINDOW_SIZE; i++) buckets[i] = 0;
        peak = 0;
    }
}

/**
 * 30-bucket sliding window rate tracker for actual_rate computation.
 * Each bucket represents one second. advance() is called once per second.
 * getRate() returns average msgs/sec over the 30-second window.
 * Thread-safe via synchronized.
 */
class SlidingRateTracker {

    private static final int WINDOW_SIZE = 30;

    private final long[] buckets = new long[WINDOW_SIZE];
    private int idx;

    public synchronized void record() {
        buckets[idx]++;
    }

    public synchronized void advance() {
        idx = (idx + 1) % WINDOW_SIZE;
        buckets[idx] = 0;
    }

    public synchronized double getRate() {
        long total = 0;
        for (int i = 0; i < WINDOW_SIZE; i++) total += buckets[i];
        return (double) total / WINDOW_SIZE;
    }

    public synchronized void reset() {
        for (int i = 0; i < WINDOW_SIZE; i++) buckets[i] = 0;
    }
}

/**
 * Latency accumulator using HdrHistogram for accurate percentile computation.
 * Records latencies in microseconds internally; reports percentiles in milliseconds.
 * Range: 1 microsecond to 60,000,000 microseconds (60 seconds), 3 significant digits.
 * Thread-safe via synchronized.
 */
class LatencyAccumulator {

    // Track microseconds: 1 us to 60 seconds (60,000,000 us).
    // 2 significant digits reduces memory from ~1.8MB to ~200KB per instance
    // while maintaining <1% accuracy for percentile computation.
    private Histogram hist = new Histogram(1L, 60_000_000L, 2);

    /**
     * Record a latency in seconds.
     */
    public synchronized void record(double durationSec) {
        long micros = Math.max(1, Math.min(60_000_000L, Math.round(durationSec * 1_000_000.0)));
        hist.recordValue(micros);
    }

    /**
     * Get the value at the given percentile, in milliseconds.
     */
    public synchronized double percentileMs(double percentile) {
        if (hist.getTotalCount() == 0) return 0;
        return hist.getValueAtPercentile(percentile) / 1000.0;
    }

    /**
     * Get the total number of recorded values.
     */
    public synchronized long count() {
        return hist.getTotalCount();
    }

    /**
     * Reset the histogram.
     */
    public synchronized void reset() {
        hist.reset();
    }
}
