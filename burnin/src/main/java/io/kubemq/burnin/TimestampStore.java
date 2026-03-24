// Send timestamp store for end-to-end latency measurement.
// Maps "producerId:seq" -> System.nanoTime() value.
// Thread-safe via ConcurrentHashMap.

package io.kubemq.burnin;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe store mapping "producerId:seq" to high-resolution timestamps (System.nanoTime).
 */
public final class TimestampStore {

    private final ConcurrentHashMap<String, Long> store = new ConcurrentHashMap<>();

    /**
     * Build the composite key for a producer and sequence number.
     */
    private static String key(String producerId, long seq) {
        return producerId + ":" + seq;
    }

    /**
     * Store the current high-resolution timestamp for the given producer and sequence.
     */
    public void store(String producerId, long seq) {
        store.put(key(producerId, seq), System.nanoTime());
    }

    /**
     * Load and atomically delete the timestamp for the given producer and sequence.
     * Returns null if not found.
     */
    public Long loadAndDelete(String producerId, long seq) {
        return store.remove(key(producerId, seq));
    }

    /**
     * Remove entries older than the specified maximum age in nanoseconds.
     * Returns the number of entries purged.
     */
    public int purge(long maxAgeNanos) {
        long cutoff = System.nanoTime() - maxAgeNanos;
        int removed = 0;
        Iterator<Map.Entry<String, Long>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (entry.getValue() < cutoff) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    /**
     * Get the current number of stored timestamps.
     */
    public int size() {
        return store.size();
    }

    /**
     * Convert a nanoTime delta to seconds.
     */
    public static double nanoToSeconds(long startNano, long endNano) {
        return (double) (endNano - startNano) / 1_000_000_000.0;
    }

    /**
     * Get elapsed seconds from a start nanoTime to now.
     */
    public static double elapsedSeconds(long startNano) {
        return nanoToSeconds(startNano, System.nanoTime());
    }
}
