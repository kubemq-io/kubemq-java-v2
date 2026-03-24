// Bitset-based sequence tracker with sliding window anchored at highContiguous.
// Detects loss, duplication, and out-of-order delivery. Thread-safe with synchronized.

package io.kubemq.burnin;

import java.util.HashMap;
import java.util.Map;

/**
 * Result of recording a sequence number.
 */
class RecordResult {
    final boolean isDuplicate;
    final boolean isOutOfOrder;

    RecordResult(boolean isDuplicate, boolean isOutOfOrder) {
        this.isDuplicate = isDuplicate;
        this.isOutOfOrder = isOutOfOrder;
    }
}

/**
 * Per-producer tracking state.
 */
class ProducerState {
    long highContiguous;
    final int[] window;
    final int windowBits;
    long received;
    long duplicates;
    long outOfOrder;
    long confirmedLost;
    long lastReportedLost;
    long lastSeen;
    boolean initialized;

    ProducerState(int windowBits) {
        this.windowBits = windowBits;
        this.window = new int[(windowBits + 31) / 32];
    }
}

/**
 * Bitset sliding window tracker. Same algorithm as the JS/C# implementation:
 * highContiguous pointer, int[] bit array, slideTo counting gaps, detectGaps returning delta.
 * Thread-safe: all public methods are synchronized.
 */
public final class Tracker {

    private final int reorderWindow;
    private final Map<String, ProducerState> producers = new HashMap<>();

    public Tracker(int reorderWindow) {
        this.reorderWindow = reorderWindow;
    }

    public Tracker() {
        this(10_000);
    }

    /**
     * Record a received sequence number for the given producer.
     */
    public synchronized RecordResult record(String producerId, long seq) {
        ProducerState state = producers.get(producerId);
        if (state == null) {
            state = new ProducerState(reorderWindow);
            producers.put(producerId, state);
        }

        if (!state.initialized) {
            state.highContiguous = seq;
            state.lastSeen = seq;
            state.initialized = true;
            state.received++;
            return new RecordResult(false, false);
        }

        state.received++;

        // Sequence at or below highContiguous is a duplicate.
        if (seq <= state.highContiguous) {
            state.duplicates++;
            return new RecordResult(true, false);
        }

        long offset = seq - state.highContiguous - 1;

        // If offset exceeds window, slide forward.
        if (offset >= state.windowBits) {
            slideTo(state, seq);
        }

        long off2 = seq - state.highContiguous - 1;
        if (getBit(state.window, off2)) {
            state.duplicates++;
            return new RecordResult(true, false);
        }

        setBit(state.window, off2);

        boolean isOOO = seq < state.lastSeen;
        if (isOOO) state.outOfOrder++;
        state.lastSeen = Math.max(state.lastSeen, seq);

        // Advance highContiguous through contiguous set bits.
        while (getBit(state.window, 0)) {
            state.highContiguous++;
            shiftRight1(state.window);
        }

        return new RecordResult(false, isOOO);
    }

    /**
     * Detect gaps since last call. Returns a map of producerId to new lost count delta.
     */
    public synchronized Map<String, Long> detectGaps() {
        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<String, ProducerState> entry : producers.entrySet()) {
            ProducerState state = entry.getValue();
            if (!state.initialized) continue;
            long delta = state.confirmedLost - state.lastReportedLost;
            if (delta > 0) {
                result.put(entry.getKey(), delta);
                state.lastReportedLost = state.confirmedLost;
            }
        }
        return result;
    }

    public synchronized long totalReceived() {
        long total = 0;
        for (ProducerState s : producers.values()) total += s.received;
        return total;
    }

    public synchronized long totalDuplicates() {
        long total = 0;
        for (ProducerState s : producers.values()) total += s.duplicates;
        return total;
    }

    public synchronized long totalOutOfOrder() {
        long total = 0;
        for (ProducerState s : producers.values()) total += s.outOfOrder;
        return total;
    }

    public synchronized long totalLost() {
        long total = 0;
        for (ProducerState s : producers.values()) total += s.confirmedLost;
        return total;
    }

    public synchronized void reset() {
        producers.clear();
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Slide the window forward so that newSeq fits. Counts gaps as confirmed lost.
     */
    private static void slideTo(ProducerState state, long newSeq) {
        long targetHC = newSeq - state.windowBits;
        if (targetHC <= state.highContiguous) return;
        long advance = targetHC - state.highContiguous;
        for (long i = 0; i < advance; i++) {
            if (!getBit(state.window, 0)) {
                state.confirmedLost++;
            }
            state.highContiguous++;
            shiftRight1(state.window);
        }
    }

    private static void setBit(int[] arr, long offset) {
        int w = (int) (offset >> 5);
        int b = (int) (offset & 31);
        if (w < arr.length) {
            arr[w] |= 1 << b;
        }
    }

    private static boolean getBit(int[] arr, long offset) {
        int w = (int) (offset >> 5);
        int b = (int) (offset & 31);
        return w < arr.length && (arr[w] & (1 << b)) != 0;
    }

    private static void shiftRight1(int[] arr) {
        for (int i = 0; i < arr.length - 1; i++) {
            arr[i] = (arr[i] >>> 1) | ((arr[i + 1] & 1) << 31);
        }
        arr[arr.length - 1] >>>= 1;
    }
}
