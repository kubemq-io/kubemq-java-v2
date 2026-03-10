package io.kubemq.sdk.observability;

/**
 * Extracts OTel trace context for log correlation.
 * Returns empty arrays when OTel is not configured (no-op).
 * <p>
 * Uses fully-qualified OTel class names in the extraction method
 * to avoid class-level imports. The method is only invoked after
 * an {@code OTelAvailability} guard, so OTel types are resolved
 * lazily by the JVM (HotSpot). For GraalVM native-image, move
 * {@link #getTraceContextFromOTel()} to a separate class.
 */
final class LogContextProvider {

    private static final Object[] EMPTY = new Object[0];

    private LogContextProvider() {}

    /**
     * Returns additional key-value pairs for trace correlation.
     * Returns {@code ["trace_id", "<hex>", "span_id", "<hex>"]} when OTel is active,
     * or empty array when not.
     */
    static Object[] getTraceContext() {
        if (!OTelAvailability.isAvailable()) {
            return EMPTY;
        }
        return getTraceContextFromOTel();
    }

    private static Object[] getTraceContextFromOTel() {
        io.opentelemetry.api.trace.SpanContext ctx =
            io.opentelemetry.api.trace.Span.current().getSpanContext();
        if (ctx.isValid()) {
            return new Object[]{
                "trace_id", ctx.getTraceId(),
                "span_id", ctx.getSpanId()
            };
        }
        return EMPTY;
    }
}
