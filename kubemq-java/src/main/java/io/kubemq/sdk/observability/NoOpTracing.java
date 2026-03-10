package io.kubemq.sdk.observability;

import java.util.List;
import java.util.Map;

/**
 * No-op tracing implementation. Used when OTel API is not on the classpath.
 * All methods are no-ops; completely inlined by JIT.
 */
public final class NoOpTracing implements Tracing {

    public static final NoOpTracing INSTANCE = new NoOpTracing();
    private static final Object NOOP_SPAN = new Object();
    private static final AutoCloseable NOOP_SCOPE = () -> {};

    private NoOpTracing() {}

    @Override
    public Object startSpan(Object spanKind, String op, String ch,
                             String mid, Object ctx) {
        return NOOP_SPAN;
    }

    @Override
    public Object startLinkedConsumerSpan(String op, String ch,
                                           String mid, Object ctx) {
        return NOOP_SPAN;
    }

    @Override
    public Object startBatchReceiveSpan(String ch, int count,
                                          List<?> ctxs) {
        return NOOP_SPAN;
    }

    @Override public void recordRetryEvent(Object span, int attempt,
                                            double delay, String err) {}
    @Override public void recordDlqEvent(Object span) {}
    @Override public void setError(Object span, Throwable err, String errType) {}
    @Override public void setBodySize(Object span, byte[] body) {}
    @Override public void setConsumerGroup(Object span, String group) {}
    @Override public void endSpan(Object span) {}
    @Override public AutoCloseable makeCurrent(Object span) { return NOOP_SCOPE; }
    @Override public void injectContext(Object ctx, Map<String, String> tags) {}
    @Override public Object extractContext(Map<String, String> tags) { return null; }
}
