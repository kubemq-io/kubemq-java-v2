package io.kubemq.sdk.observability;

/**
 * Detects whether OpenTelemetry API is available at runtime. Used by factories to decide between
 * OTel and no-op implementations.
 */
final class OTelAvailability {

  private static final boolean AVAILABLE;

  static {
    boolean available;
    try {
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      if (cl == null) {
        cl = OTelAvailability.class.getClassLoader();
      }
      Class.forName("io.opentelemetry.api.trace.Tracer", false, cl);
      available = true;
    } catch (ClassNotFoundException e) {
      available = false;
    }
    AVAILABLE = available;
  }

  static boolean isAvailable() {
    return AVAILABLE;
  }

  private OTelAvailability() {}
}
