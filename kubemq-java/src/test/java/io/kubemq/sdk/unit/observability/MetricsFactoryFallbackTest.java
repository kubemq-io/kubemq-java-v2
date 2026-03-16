package io.kubemq.sdk.unit.observability;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.observability.CardinalityConfig;
import io.kubemq.sdk.observability.Metrics;
import io.kubemq.sdk.observability.MetricsFactory;
import io.kubemq.sdk.observability.NoOpMetrics;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for MetricsFactory edge cases and fallback paths. Since OTelAvailability is package-private
 * and static final, we test through the constructor failure path (passing wrong types) and null
 * parameter handling.
 */
@DisplayName("MetricsFactory edge cases")
class MetricsFactoryFallbackTest {

  @Test
  @DisplayName("null sdkVersion falls back to SdkVersion.get()")
  void nullSdkVersion_usesDefault() {
    Metrics result = MetricsFactory.create(null, null, null);
    assertNotNull(result);
    // With OTel on classpath and null sdkVersion, should still create real metrics
    assertFalse(result instanceof NoOpMetrics);
  }

  @Test
  @DisplayName("null meterProvider uses GlobalOpenTelemetry")
  void nullMeterProvider_usesGlobal() {
    Metrics result = MetricsFactory.create(null, "1.0.0", null);
    assertNotNull(result);
    assertFalse(result instanceof NoOpMetrics);
  }

  @Test
  @DisplayName("with CardinalityConfig creates metrics")
  void withCardinalityConfig_createsMetrics() {
    CardinalityConfig config = new CardinalityConfig(100, null);
    Metrics result = MetricsFactory.create(null, "2.0.0", config);
    assertNotNull(result);
    assertFalse(result instanceof NoOpMetrics);
  }

  @Test
  @DisplayName("with explicit SdkMeterProvider creates KubeMQMetrics")
  void explicitProvider_createsKubeMQMetrics() {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    SdkMeterProvider provider = SdkMeterProvider.builder().registerMetricReader(reader).build();
    try {
      Metrics result = MetricsFactory.create(provider, "1.0.0", null);
      assertNotNull(result);
      assertFalse(result instanceof NoOpMetrics);
      // Exercise the created metrics to ensure it works
      result.recordSentMessage("publish", "test-ch");
      result.recordOperationDuration(0.05, "publish", "test-ch", null);
      result.recordConsumedMessage("process", "test-ch");
      result.recordConnectionOpened();
      result.recordConnectionClosed();
      result.recordReconnectionAttempt();
      result.recordRetryAttempt("publish", "transient");
      result.recordRetryExhausted("publish", "timeout");
    } finally {
      provider.close();
    }
  }

  @Test
  @DisplayName("with CardinalityConfig and explicit provider creates metrics")
  void explicitProviderAndConfig_createsKubeMQMetrics() {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    SdkMeterProvider provider = SdkMeterProvider.builder().registerMetricReader(reader).build();
    CardinalityConfig config = new CardinalityConfig(50, null);
    try {
      Metrics result = MetricsFactory.create(provider, "2.0.0", config);
      assertNotNull(result);
      assertFalse(result instanceof NoOpMetrics);
    } finally {
      provider.close();
    }
  }
}
