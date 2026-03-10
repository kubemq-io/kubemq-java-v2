package io.kubemq.sdk.observability;

/**
 * Default logger when no SLF4J is on classpath and no user logger is configured.
 * All methods are no-ops. Completely inlined by JIT.
 */
public final class NoOpLogger implements KubeMQLogger {

    public static final NoOpLogger INSTANCE = new NoOpLogger();

    private NoOpLogger() {}

    @Override public void trace(String msg, Object... keysAndValues) {}
    @Override public void debug(String msg, Object... keysAndValues) {}
    @Override public void info(String msg, Object... keysAndValues) {}
    @Override public void warn(String msg, Object... keysAndValues) {}
    @Override public void error(String msg, Object... keysAndValues) {}
    @Override public void error(String msg, Throwable cause, Object... keysAndValues) {}
    @Override public boolean isEnabled(LogLevel level) { return false; }
}
