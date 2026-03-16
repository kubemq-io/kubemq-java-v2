package io.kubemq.sdk.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SLF4J adapter for KubeMQLogger. Default when SLF4J is on the classpath.
 *
 * <p>Converts structured key-value pairs to SLF4J parameterized messages. Compatible with SLF4J
 * 1.7.x and 2.x.
 */
public final class Slf4jLoggerAdapter implements KubeMQLogger {

  private final Logger delegate;

  /**
   * Constructs a new instance.
   *
   * @param loggerName the logger name
   */
  public Slf4jLoggerAdapter(String loggerName) {
    this.delegate = LoggerFactory.getLogger(loggerName);
  }

  /**
   * Constructs a new instance.
   *
   * @param clazz the clazz
   */
  public Slf4jLoggerAdapter(Class<?> clazz) {
    this.delegate = LoggerFactory.getLogger(clazz);
  }

  /**
   * Performs the trace operation.
   *
   * @param msg the msg
   * @param keysAndValues the keys and values
   */
  @Override
  public void trace(String msg, Object... keysAndValues) {
    if (delegate.isTraceEnabled()) {
      delegate.trace(formatMessage(msg, keysAndValues));
    }
  }

  /**
   * Performs the debug operation.
   *
   * @param msg the msg
   * @param keysAndValues the keys and values
   */
  @Override
  public void debug(String msg, Object... keysAndValues) {
    if (delegate.isDebugEnabled()) {
      delegate.debug(formatMessage(msg, keysAndValues));
    }
  }

  /**
   * Performs the info operation.
   *
   * @param msg the msg
   * @param keysAndValues the keys and values
   */
  @Override
  public void info(String msg, Object... keysAndValues) {
    if (delegate.isInfoEnabled()) {
      delegate.info(formatMessage(msg, keysAndValues));
    }
  }

  /**
   * Performs the warn operation.
   *
   * @param msg the msg
   * @param keysAndValues the keys and values
   */
  @Override
  public void warn(String msg, Object... keysAndValues) {
    if (delegate.isWarnEnabled()) {
      delegate.warn(formatMessage(msg, keysAndValues));
    }
  }

  /**
   * Performs the error operation.
   *
   * @param msg the msg
   * @param keysAndValues the keys and values
   */
  @Override
  public void error(String msg, Object... keysAndValues) {
    if (delegate.isErrorEnabled()) {
      delegate.error(formatMessage(msg, keysAndValues));
    }
  }

  /**
   * Performs the error operation.
   *
   * @param msg the msg
   * @param cause the cause
   * @param keysAndValues the keys and values
   */
  @Override
  public void error(String msg, Throwable cause, Object... keysAndValues) {
    if (delegate.isErrorEnabled()) {
      delegate.error(formatMessage(msg, keysAndValues), cause);
    }
  }

  /**
   * Returns whether enabled.
   *
   * @param level the level
   * @return the result
   */
  @Override
  public boolean isEnabled(LogLevel level) {
    switch (level) {
      case TRACE:
        return delegate.isTraceEnabled();
      case DEBUG:
        return delegate.isDebugEnabled();
      case INFO:
        return delegate.isInfoEnabled();
      case WARN:
        return delegate.isWarnEnabled();
      case ERROR:
        return delegate.isErrorEnabled();
      default:
        return false;
    }
  }

  /**
   * Convert structured key-value pairs to a human-readable log message. Format: "msg [key1=value1,
   * key2=value2]"
   *
   * @param msg the msg
   * @param keysAndValues the keys and values
   * @return the result
   */
  public static String formatMessage(String msg, Object... keysAndValues) {
    if (keysAndValues == null || keysAndValues.length == 0) {
      return msg;
    }
    StringBuilder sb = new StringBuilder(msg).append(" [");
    for (int i = 0; i < keysAndValues.length; i += 2) {
      if (i > 0) {
        sb.append(", ");
      }
      String key = String.valueOf(keysAndValues[i]);
      Object value = (i + 1 < keysAndValues.length) ? keysAndValues[i + 1] : "MISSING_VALUE";
      sb.append(key).append('=').append(value);
    }
    sb.append(']');
    return sb.toString();
  }
}
