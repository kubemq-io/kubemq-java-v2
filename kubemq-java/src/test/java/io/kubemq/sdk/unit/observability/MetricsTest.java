package io.kubemq.sdk.unit.observability;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.observability.KubeMQMetrics;
import io.kubemq.sdk.observability.Metrics;
import io.kubemq.sdk.observability.MetricsFactory;
import io.kubemq.sdk.observability.NoOpMetrics;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.Collection;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Metrics")
class MetricsTest {

  @Nested
  @DisplayName("NoOpMetrics")
  class NoOpTests {

    @Test
    @DisplayName("all methods are no-ops without exceptions")
    void allMethodsAreNoOps() {
      Metrics m = NoOpMetrics.INSTANCE;
      assertDoesNotThrow(
          () -> {
            m.recordOperationDuration(0.5, "publish", "ch", null);
            m.recordSentMessage("publish", "ch");
            m.recordConsumedMessage("process", "ch");
            m.recordConnectionOpened();
            m.recordConnectionClosed();
            m.recordReconnectionAttempt();
            m.recordRetryAttempt("publish", "transient");
            m.recordRetryExhausted("publish", "timeout");
          });
    }
  }

  @Nested
  @DisplayName("KubeMQMetrics with OTel SDK")
  class OTelMetricsTests {

    private InMemoryMetricReader reader;
    private SdkMeterProvider meterProvider;
    private KubeMQMetrics metrics;

    @BeforeEach
    void setUp() {
      reader = InMemoryMetricReader.create();
      meterProvider = SdkMeterProvider.builder().registerMetricReader(reader).build();
      metrics = new KubeMQMetrics(meterProvider, "2.1.1", null);
    }

    @AfterEach
    void tearDown() {
      meterProvider.close();
    }

    @Test
    @DisplayName("recordOperationDuration records histogram")
    void operationDuration() {
      metrics.recordOperationDuration(0.05, "publish", "test-ch", null);

      Collection<MetricData> data = reader.collectAllMetrics();
      Optional<MetricData> histogram =
          data.stream()
              .filter(m -> m.getName().equals("messaging.client.operation.duration"))
              .findFirst();
      assertTrue(histogram.isPresent(), "operation duration histogram should be recorded");
    }

    @Test
    @DisplayName("recordSentMessage increments counter")
    void sentMessages() {
      metrics.recordSentMessage("publish", "test-ch");

      Collection<MetricData> data = reader.collectAllMetrics();
      Optional<MetricData> counter =
          data.stream()
              .filter(m -> m.getName().equals("messaging.client.sent.messages"))
              .findFirst();
      assertTrue(counter.isPresent(), "sent messages counter should be recorded");
    }

    @Test
    @DisplayName("recordConsumedMessage increments counter")
    void consumedMessages() {
      metrics.recordConsumedMessage("process", "test-ch");

      Collection<MetricData> data = reader.collectAllMetrics();
      Optional<MetricData> counter =
          data.stream()
              .filter(m -> m.getName().equals("messaging.client.consumed.messages"))
              .findFirst();
      assertTrue(counter.isPresent(), "consumed messages counter should be recorded");
    }

    @Test
    @DisplayName("recordConnectionOpened and closed adjust counter")
    void connectionCount() {
      metrics.recordConnectionOpened();
      metrics.recordConnectionOpened();
      metrics.recordConnectionClosed();

      Collection<MetricData> data = reader.collectAllMetrics();
      Optional<MetricData> counter =
          data.stream()
              .filter(m -> m.getName().equals("messaging.client.connection.count"))
              .findFirst();
      assertTrue(counter.isPresent(), "connection count should be recorded");
    }

    @Test
    @DisplayName("recordReconnectionAttempt increments counter")
    void reconnections() {
      metrics.recordReconnectionAttempt();

      Collection<MetricData> data = reader.collectAllMetrics();
      Optional<MetricData> counter =
          data.stream()
              .filter(m -> m.getName().equals("messaging.client.reconnections"))
              .findFirst();
      assertTrue(counter.isPresent(), "reconnections counter should be recorded");
    }

    @Test
    @DisplayName("recordRetryAttempt increments counter with attributes")
    void retryAttempts() {
      metrics.recordRetryAttempt("publish", "transient");

      Collection<MetricData> data = reader.collectAllMetrics();
      Optional<MetricData> counter =
          data.stream().filter(m -> m.getName().equals("kubemq.client.retry.attempts")).findFirst();
      assertTrue(counter.isPresent(), "retry attempts counter should be recorded");
    }

    @Test
    @DisplayName("recordRetryExhausted increments counter")
    void retryExhausted() {
      metrics.recordRetryExhausted("publish", "timeout");

      Collection<MetricData> data = reader.collectAllMetrics();
      Optional<MetricData> counter =
          data.stream()
              .filter(m -> m.getName().equals("kubemq.client.retry.exhausted"))
              .findFirst();
      assertTrue(counter.isPresent(), "retry exhausted counter should be recorded");
    }

    @Test
    @DisplayName("operation duration records with error type on failure")
    void operationDurationWithError() {
      metrics.recordOperationDuration(1.5, "publish", "ch", "transient");

      Collection<MetricData> data = reader.collectAllMetrics();
      Optional<MetricData> histogram =
          data.stream()
              .filter(m -> m.getName().equals("messaging.client.operation.duration"))
              .findFirst();
      assertTrue(histogram.isPresent());
    }

    @Test
    @DisplayName("instrumentation scope has correct name and version")
    void instrumentationScope() {
      metrics.recordSentMessage("publish", "ch");

      Collection<MetricData> data = reader.collectAllMetrics();
      Optional<MetricData> metric =
          data.stream()
              .filter(m -> m.getName().equals("messaging.client.sent.messages"))
              .findFirst();
      assertTrue(metric.isPresent());
      assertEquals("io.kubemq.sdk", metric.get().getInstrumentationScopeInfo().getName());
      assertEquals("2.1.1", metric.get().getInstrumentationScopeInfo().getVersion());
    }
  }

  @Nested
  @DisplayName("MetricsFactory")
  class FactoryTests {

    @Test
    @DisplayName("creates KubeMQMetrics when OTel is available")
    void createsOTelMetrics() {
      InMemoryMetricReader reader = InMemoryMetricReader.create();
      SdkMeterProvider provider = SdkMeterProvider.builder().registerMetricReader(reader).build();
      try {
        Metrics metrics = MetricsFactory.create(provider, "1.0.0", null);
        assertNotNull(metrics);
        assertFalse(metrics instanceof NoOpMetrics);
      } finally {
        provider.close();
      }
    }

    @Test
    @DisplayName("creates KubeMQMetrics with null provider")
    void createsWithNullProvider() {
      Metrics metrics = MetricsFactory.create(null, "1.0.0", null);
      assertNotNull(metrics);
      assertFalse(metrics instanceof NoOpMetrics);
    }
  }
}
