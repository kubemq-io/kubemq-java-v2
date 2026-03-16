package io.kubemq.sdk.observability;

import java.util.List;
import java.util.Map;

/**
 * No-op tracing implementation. Used when OTel API is not on the classpath. All methods are no-ops;
 * completely inlined by JIT.
 */
public final class NoOpTracing implements Tracing {

  public static final NoOpTracing INSTANCE = new NoOpTracing();
  private static final Object NOOP_SPAN = new Object();
  private static final AutoCloseable NOOP_SCOPE = () -> {};

  private NoOpTracing() {}

  /**
   * Starts the span.
   *
   * @param spanKind the span kind
   * @param op the op
   * @param ch the ch
   * @param mid the mid
   * @param ctx the ctx
   * @return the result
   */
  @Override
  public Object startSpan(Object spanKind, String op, String ch, String mid, Object ctx) {
    return NOOP_SPAN;
  }

  /**
   * Starts the linked consumer span.
   *
   * @param op the op
   * @param ch the ch
   * @param mid the mid
   * @param ctx the ctx
   * @return the result
   */
  @Override
  public Object startLinkedConsumerSpan(String op, String ch, String mid, Object ctx) {
    return NOOP_SPAN;
  }

  /**
   * Starts the batch receive span.
   *
   * @param ch the ch
   * @param count the count
   * @param ctxs the ctxs
   * @return the result
   */
  @Override
  public Object startBatchReceiveSpan(String ch, int count, List<?> ctxs) {
    return NOOP_SPAN;
  }

  /**
   * Records the retry event.
   *
   * @param span the span
   * @param attempt the attempt
   * @param delay the delay
   * @param err the err
   */
  @Override
  public void recordRetryEvent(Object span, int attempt, double delay, String err) {}

  /** {@inheritDoc} */
  @Override
  public void recordDlqEvent(Object span) {}

  /** {@inheritDoc} */
  @Override
  public void setError(Object span, Throwable err, String errType) {}

  /** {@inheritDoc} */
  @Override
  public void setBodySize(Object span, byte[] body) {}

  /** {@inheritDoc} */
  @Override
  public void setConsumerGroup(Object span, String group) {}

  /** {@inheritDoc} */
  @Override
  public void endSpan(Object span) {}

  /** {@inheritDoc} */
  @Override
  public AutoCloseable makeCurrent(Object span) {
    return NOOP_SCOPE;
  }

  /**
   * Injects the context.
   *
   * @param ctx the ctx
   * @param tags the tags
   */
  @Override
  public void injectContext(Object ctx, Map<String, String> tags) {}

  /** {@inheritDoc} */
  @Override
  public Object extractContext(Map<String, String> tags) {
    return null;
  }
}
