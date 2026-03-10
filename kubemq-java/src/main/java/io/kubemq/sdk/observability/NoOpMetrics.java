package io.kubemq.sdk.observability;

/**
 * No-op metrics implementation. Used when OTel API is not on the classpath.
 * All methods are no-ops; completely inlined by JIT.
 */
public final class NoOpMetrics implements Metrics {

    public static final NoOpMetrics INSTANCE = new NoOpMetrics();

    private NoOpMetrics() {}

    @Override public void recordOperationDuration(double d, String op, String ch, String err) {}
    @Override public void recordSentMessage(String op, String ch) {}
    @Override public void recordConsumedMessage(String op, String ch) {}
    @Override public void recordConnectionOpened() {}
    @Override public void recordConnectionClosed() {}
    @Override public void recordReconnectionAttempt() {}
    @Override public void recordRetryAttempt(String op, String err) {}
    @Override public void recordRetryExhausted(String op, String err) {}
}
