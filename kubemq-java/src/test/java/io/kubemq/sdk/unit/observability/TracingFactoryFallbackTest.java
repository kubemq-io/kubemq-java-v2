package io.kubemq.sdk.unit.observability;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.observability.NoOpTracing;
import io.kubemq.sdk.observability.Tracing;
import io.kubemq.sdk.observability.TracingFactory;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for TracingFactory edge cases and fallback paths. */
@DisplayName("TracingFactory edge cases")
class TracingFactoryFallbackTest {

  @Test
  @DisplayName("null sdkVersion falls back to SdkVersion.get()")
  void nullSdkVersion_usesDefault() {
    Tracing result = TracingFactory.create(null, null, "client", "localhost", 50000);
    assertNotNull(result);
    assertFalse(result instanceof NoOpTracing);
  }

  @Test
  @DisplayName("null clientId still creates tracing")
  void nullClientId_createsTracing() {
    Tracing result = TracingFactory.create(null, "1.0.0", null, "localhost", 50000);
    assertNotNull(result);
    assertFalse(result instanceof NoOpTracing);
  }

  @Test
  @DisplayName("null serverAddress still creates tracing")
  void nullServerAddress_createsTracing() {
    Tracing result = TracingFactory.create(null, "1.0.0", "client", null, 50000);
    assertNotNull(result);
    assertFalse(result instanceof NoOpTracing);
  }

  @Test
  @DisplayName("zero port still creates tracing")
  void zeroPort_createsTracing() {
    Tracing result = TracingFactory.create(null, "1.0.0", "client", "localhost", 0);
    assertNotNull(result);
  }

  @Test
  @DisplayName("all null params still creates tracing")
  void allNullParams_createsTracing() {
    Tracing result = TracingFactory.create(null, null, null, null, 0);
    assertNotNull(result);
  }

  @Test
  @DisplayName("with explicit SdkTracerProvider creates KubeMQTracing")
  void explicitProvider_createsKubeMQTracing() {
    InMemorySpanExporter exporter = InMemorySpanExporter.create();
    SdkTracerProvider provider =
        SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
    try {
      Tracing result = TracingFactory.create(provider, "2.0.0", "test-client", "localhost", 50000);
      assertNotNull(result);
      assertFalse(result instanceof NoOpTracing);

      // Exercise the created tracing to ensure it works
      Object span = result.startSpan(SpanKind.PRODUCER, "publish", "test-ch", "msg-1", null);
      result.setBodySize(span, new byte[] {1, 2, 3});
      result.setConsumerGroup(span, "group");
      result.recordRetryEvent(span, 1, 0.5, "transient");
      result.endSpan(span);

      assertEquals(1, exporter.getFinishedSpanItems().size());
    } finally {
      provider.close();
    }
  }
}
