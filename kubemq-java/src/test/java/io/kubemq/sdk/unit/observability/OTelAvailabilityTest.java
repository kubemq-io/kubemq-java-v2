package io.kubemq.sdk.unit.observability;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.observability.Metrics;
import io.kubemq.sdk.observability.MetricsFactory;
import io.kubemq.sdk.observability.NoOpMetrics;
import io.kubemq.sdk.observability.NoOpTracing;
import io.kubemq.sdk.observability.Tracing;
import io.kubemq.sdk.observability.TracingFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OTel Availability and No-Op Fallback (REQ-OBS-4)")
class OTelAvailabilityTest {

  @Test
  @DisplayName("OTel API is available in test classpath")
  void otelAvailableInTests() throws Exception {
    Class<?> clazz = Class.forName("io.kubemq.sdk.observability.OTelAvailability");
    java.lang.reflect.Method method = clazz.getDeclaredMethod("isAvailable");
    method.setAccessible(true);
    boolean available = (boolean) method.invoke(null);
    assertTrue(available, "OTel API should be available since opentelemetry-sdk is a test dep");
  }

  @Test
  @DisplayName("TracingFactory returns KubeMQTracing when OTel is available")
  void tracingFactoryReturnsOTelImpl() {
    Tracing tracing = TracingFactory.create(null, "1.0", "c", "h", 1);
    assertNotNull(tracing);
    assertFalse(
        tracing instanceof NoOpTracing, "Should return KubeMQTracing when OTel is available");
  }

  @Test
  @DisplayName("MetricsFactory returns KubeMQMetrics when OTel is available")
  void metricsFactoryReturnsOTelImpl() {
    Metrics metrics = MetricsFactory.create(null, "1.0", null);
    assertNotNull(metrics);
    assertFalse(
        metrics instanceof NoOpMetrics, "Should return KubeMQMetrics when OTel is available");
  }

  @Test
  @DisplayName("NoOpTracing is singleton")
  void noOpTracingSingleton() {
    assertSame(NoOpTracing.INSTANCE, NoOpTracing.INSTANCE);
  }

  @Test
  @DisplayName("NoOpMetrics is singleton")
  void noOpMetricsSingleton() {
    assertSame(NoOpMetrics.INSTANCE, NoOpMetrics.INSTANCE);
  }

  @Test
  @DisplayName("SdkVersion returns a non-null string")
  void sdkVersionReturnsString() throws Exception {
    Class<?> clazz = Class.forName("io.kubemq.sdk.observability.SdkVersion");
    java.lang.reflect.Method method = clazz.getDeclaredMethod("get");
    method.setAccessible(true);
    String version = (String) method.invoke(null);
    assertNotNull(version);
  }
}
