package io.kubemq.sdk.observability;

/**
 * Abstraction for SDK metrics operations. Client code uses this interface exclusively; the
 * OTel-importing implementation ({@code KubeMQMetrics}) is loaded only when OTel is confirmed
 * available on the classpath.
 */
public interface Metrics {

  /**
   * Record the duration of a messaging operation.
   *
   * @param durationSeconds duration in seconds
   * @param operationName operation name (publish, process, receive, settle, send)
   * @param channel channel name
   * @param errorType error type string (null if success)
   */
  void recordOperationDuration(
      double durationSeconds, String operationName, String channel, String errorType);

  /**
   * Increment the sent messages counter.
   *
   * @param operationName the operation name
   * @param channel the channel
   */
  void recordSentMessage(String operationName, String channel);

  /**
   * Increment the consumed messages counter.
   *
   * @param operationName the operation name
   * @param channel the channel
   */
  void recordConsumedMessage(String operationName, String channel);

  /** Increment active connection count (call on CONNECTED/READY state). */
  void recordConnectionOpened();

  /** Decrement active connection count (call on CLOSED state). */
  void recordConnectionClosed();

  /** Increment reconnection attempts counter. */
  void recordReconnectionAttempt();

  /**
   * Increment retry attempts counter.
   *
   * @param operationName the operation name
   * @param errorType the error type
   */
  void recordRetryAttempt(String operationName, String errorType);

  /**
   * Increment retry exhausted counter.
   *
   * @param operationName the operation name
   * @param errorType the error type
   */
  void recordRetryExhausted(String operationName, String errorType);
}
