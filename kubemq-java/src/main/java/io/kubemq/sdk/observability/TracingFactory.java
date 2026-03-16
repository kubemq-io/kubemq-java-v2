package io.kubemq.sdk.observability;

/**
 * Factory for creating {@link Tracing} instances with lazy loading.
 *
 * <p>Uses {@link Class#forName(String)} to load {@code KubeMQTracing} only when OTel API is
 * confirmed available on the classpath. Falls back to {@link NoOpTracing} when OTel is absent (per
 * J-11 constraint).
 */
public final class TracingFactory {

  private TracingFactory() {}

  /**
   * Create a Tracing instance.
   *
   * @param tracerProvider OTel TracerProvider as Object (null = use GlobalOpenTelemetry)
   * @param sdkVersion SDK version for instrumentation scope
   * @param clientId client ID for messaging.client.id attribute
   * @param serverAddress server hostname
   * @param serverPort server port
   * @return KubeMQTracing if OTel is available, NoOpTracing otherwise
   */
  public static Tracing create(
      Object tracerProvider,
      String sdkVersion,
      String clientId,
      String serverAddress,
      int serverPort) {
    if (!OTelAvailability.isAvailable()) {
      return NoOpTracing.INSTANCE;
    }
    String version = sdkVersion != null ? sdkVersion : SdkVersion.get();
    try {
      return (Tracing)
          Class.forName("io.kubemq.sdk.observability.KubeMQTracing")
              .getConstructor(Object.class, String.class, String.class, String.class, int.class)
              .newInstance(tracerProvider, version, clientId, serverAddress, serverPort);
    } catch (Exception | NoClassDefFoundError e) {
      return NoOpTracing.INSTANCE;
    }
  }
}
