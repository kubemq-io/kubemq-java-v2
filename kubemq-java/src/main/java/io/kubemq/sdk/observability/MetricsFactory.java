package io.kubemq.sdk.observability;

/**
 * Factory for creating {@link Metrics} instances with lazy loading.
 * <p>
 * Uses {@link Class#forName(String)} to load {@code KubeMQMetrics} only when
 * OTel API is confirmed available on the classpath. Falls back to
 * {@link NoOpMetrics} when OTel is absent (per J-11 constraint).
 */
public final class MetricsFactory {

    private MetricsFactory() {}

    /**
     * Create a Metrics instance.
     *
     * @param meterProvider     OTel MeterProvider as Object (null = use GlobalOpenTelemetry)
     * @param sdkVersion        SDK version for instrumentation scope
     * @param cardinalityConfig cardinality management configuration (null for defaults)
     * @return KubeMQMetrics if OTel is available, NoOpMetrics otherwise
     */
    public static Metrics create(Object meterProvider, String sdkVersion,
                                  CardinalityConfig cardinalityConfig) {
        if (!OTelAvailability.isAvailable()) {
            return NoOpMetrics.INSTANCE;
        }
        String version = sdkVersion != null ? sdkVersion : SdkVersion.get();
        try {
            return (Metrics) Class.forName("io.kubemq.sdk.observability.KubeMQMetrics")
                .getConstructor(Object.class, String.class, CardinalityConfig.class)
                .newInstance(meterProvider, version, cardinalityConfig);
        } catch (Exception | NoClassDefFoundError e) {
            return NoOpMetrics.INSTANCE;
        }
    }
}
