package io.kubemq.sdk.observability;

/**
 * SDK-defined logger interface with structured key-value fields.
 *
 * <p>Users implement this interface to integrate their preferred logging framework. The {@code
 * keysAndValues} parameter accepts alternating key-value pairs: {@code logger.info("Connected",
 * "address", "localhost:50000", "clientId", "my-client")}
 *
 * <p>Odd-length varargs: the last value is logged with key "MISSING_VALUE".
 */
public interface KubeMQLogger {

  /**
   * Log at TRACE level. Used for per-message detail (individual publish/receive events).
   *
   * @param msg the msg
   * @param keysAndValues the keys and values
   */
  void trace(String msg, Object... keysAndValues);

  /**
   * Log at DEBUG level. Used for retry attempts, keepalive pings, state transitions, individual
   * publish/receive summaries.
   *
   * @param msg the msg
   * @param keysAndValues the keys and values
   */
  void debug(String msg, Object... keysAndValues);

  /**
   * Log at INFO level. Used for connection established, reconnection, subscription created,
   * graceful shutdown.
   *
   * @param msg the msg
   * @param keysAndValues the keys and values
   */
  void info(String msg, Object... keysAndValues);

  /**
   * Log at WARN level. Used for insecure configuration, buffer near capacity, deprecated API usage,
   * cardinality threshold exceeded.
   *
   * @param msg the msg
   * @param keysAndValues the keys and values
   */
  void warn(String msg, Object... keysAndValues);

  /**
   * Log at ERROR level. Used for connection failed (after retries exhausted), auth failure,
   * unrecoverable error.
   *
   * @param msg the msg
   * @param keysAndValues the keys and values
   */
  void error(String msg, Object... keysAndValues);

  /**
   * Log at ERROR level with an associated exception. The {@code cause} is passed to the underlying
   * logger so that stack traces are preserved (e.g., SLF4J's {@code error(String, Throwable)}
   * method).
   *
   * @param msg the msg
   * @param cause the cause
   * @param keysAndValues the keys and values
   */
  void error(String msg, Throwable cause, Object... keysAndValues);

  /**
   * Returns true if the given level is enabled. Used to guard expensive computation.
   *
   * @param level the level
   * @return the result
   */
  boolean isEnabled(LogLevel level);

  /** Log levels for the {@link #isEnabled(LogLevel)} check. */
  enum LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR
  }
}
