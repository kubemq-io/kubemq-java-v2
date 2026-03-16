package io.kubemq.sdk.observability;

/**
 * No-op metrics implementation. Used when OTel API is not on the classpath. All methods are no-ops;
 * completely inlined by JIT.
 */
public final class NoOpMetrics implements Metrics {

  public static final NoOpMetrics INSTANCE = new NoOpMetrics();

  private NoOpMetrics() {}

  /**
   * Records the operation duration.
   *
   * @param d the d
   * @param op the op
   * @param ch the ch
   * @param err the err
   */
  @Override
  public void recordOperationDuration(double d, String op, String ch, String err) {}

  /** {@inheritDoc} */
  @Override
  public void recordSentMessage(String op, String ch) {}

  /** {@inheritDoc} */
  @Override
  public void recordConsumedMessage(String op, String ch) {}

  /** {@inheritDoc} */
  @Override
  public void recordConnectionOpened() {}

  /** {@inheritDoc} */
  @Override
  public void recordConnectionClosed() {}

  /** {@inheritDoc} */
  @Override
  public void recordReconnectionAttempt() {}

  /** {@inheritDoc} */
  @Override
  public void recordRetryAttempt(String op, String err) {}

  /** {@inheritDoc} */
  @Override
  public void recordRetryExhausted(String op, String err) {}
}
