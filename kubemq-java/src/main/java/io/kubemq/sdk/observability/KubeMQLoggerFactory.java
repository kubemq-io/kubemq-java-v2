package io.kubemq.sdk.observability;

/**
 * Factory that resolves the default KubeMQLogger.
 *
 * <p>Resolution order:
 *
 * <ol>
 *   <li>User-provided logger via client builder (always wins)
 *   <li>SLF4J adapter if SLF4J is on the classpath (backward-compatible default)
 *   <li>NoOpLogger (silent default when no logging framework is present)
 * </ol>
 */
public final class KubeMQLoggerFactory {

  private static final boolean SLF4J_AVAILABLE;

  static {
    boolean available;
    try {
      Class.forName("org.slf4j.Logger", false, KubeMQLoggerFactory.class.getClassLoader());
      available = true;
    } catch (ClassNotFoundException e) {
      available = false;
    }
    SLF4J_AVAILABLE = available;
  }

  private KubeMQLoggerFactory() {}

  /**
   * Returns the default logger for the given name. Returns Slf4jLoggerAdapter if SLF4J is on
   * classpath, NoOpLogger otherwise.
   *
   * @param name the name
   * @return the result
   */
  public static KubeMQLogger getLogger(String name) {
    if (SLF4J_AVAILABLE) {
      return new Slf4jLoggerAdapter(name);
    }
    return NoOpLogger.INSTANCE;
  }

  /**
   * Returns the default logger for the given class.
   *
   * @param clazz the clazz
   * @return the result
   */
  public static KubeMQLogger getLogger(Class<?> clazz) {
    if (SLF4J_AVAILABLE) {
      return new Slf4jLoggerAdapter(clazz);
    }
    return NoOpLogger.INSTANCE;
  }

  /**
   * Returns true if SLF4J is available on the classpath.
   *
   * @return the result
   */
  public static boolean isSlf4jAvailable() {
    return SLF4J_AVAILABLE;
  }
}
