package io.kubemq.sdk.observability;

/**
 * Utility for merging structured key-value arrays for logging.
 * Java varargs cannot splice Object[] inline; this utility
 * concatenates multiple key-value arrays into a single flat array.
 */
public final class LogHelper {

    private static final Object[] EMPTY = new Object[0];

    private LogHelper() {}

    /**
     * Merges multiple key-value arrays into one flat array.
     * Example: merge(new Object[]{"a", 1}, new Object[]{"b", 2}) =&gt; ["a", 1, "b", 2]
     */
    public static Object[] merge(Object[]... arrays) {
        int totalLength = 0;
        for (Object[] arr : arrays) {
            if (arr != null) totalLength += arr.length;
        }
        if (totalLength == 0) return EMPTY;

        Object[] result = new Object[totalLength];
        int pos = 0;
        for (Object[] arr : arrays) {
            if (arr != null && arr.length > 0) {
                System.arraycopy(arr, 0, result, pos, arr.length);
                pos += arr.length;
            }
        }
        return result;
    }
}
