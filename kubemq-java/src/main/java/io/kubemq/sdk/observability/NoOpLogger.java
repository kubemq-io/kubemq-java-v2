package io.kubemq.sdk.observability;

/**
 * Default logger when no SLF4J is on classpath and no user logger is configured. All methods are
 * no-ops. Completely inlined by JIT.
 */
public final class NoOpLogger implements KubeMQLogger {

  public static final NoOpLogger INSTANCE = new NoOpLogger();

  private NoOpLogger() {}

  /**
   * Performs the trace operation.
   *
   * @param msg the msg
   * @param keysAndValues the keys and values
   */
  @Override
  public void trace(String msg, Object... keysAndValues) {}

  /** {@inheritDoc} */
  @Override
  public void debug(String msg, Object... keysAndValues) {}

  /** {@inheritDoc} */
  @Override
  public void info(String msg, Object... keysAndValues) {}

  /** {@inheritDoc} */
  @Override
  public void warn(String msg, Object... keysAndValues) {}

  /** {@inheritDoc} */
  @Override
  public void error(String msg, Object... keysAndValues) {}

  /** {@inheritDoc} */
  @Override
  public void error(String msg, Throwable cause, Object... keysAndValues) {}

  /** {@inheritDoc} */
  @Override
  public boolean isEnabled(LogLevel level) {
    return false;
  }
}
