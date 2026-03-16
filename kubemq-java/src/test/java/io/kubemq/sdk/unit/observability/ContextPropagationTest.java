package io.kubemq.sdk.unit.observability;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.observability.KubeMQTracing;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("W3C Trace Context Propagation (REQ-OBS-2)")
class ContextPropagationTest {

  private InMemorySpanExporter exporter;
  private SdkTracerProvider tracerProvider;
  private KubeMQTracing tracing;
  private OpenTelemetrySdk otelSdk;

  @BeforeEach
  void setUp() {
    GlobalOpenTelemetry.resetForTest();
    exporter = InMemorySpanExporter.create();
    tracerProvider =
        SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
    otelSdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal();
    tracing = new KubeMQTracing(tracerProvider, "2.1.1", "test-client", "localhost", 50000);
  }

  @AfterEach
  void tearDown() {
    tracerProvider.close();
    GlobalOpenTelemetry.resetForTest();
  }

  @Test
  @DisplayName("injectContext adds traceparent to tags map")
  void injectAddsTraceparent() {
    Object spanObj = tracing.startSpan(SpanKind.PRODUCER, "publish", "ch", "msg-1", null);
    try (AutoCloseable scope = tracing.makeCurrent(spanObj)) {
      Map<String, String> tags = new HashMap<>();
      tracing.injectContext(null, tags);

      assertTrue(tags.containsKey("traceparent"), "traceparent should be injected into tags");
      String traceparent = tags.get("traceparent");
      assertTrue(traceparent.startsWith("00-"), "traceparent should follow W3C format");
    } catch (Exception e) {
      fail("Unexpected exception", e);
    } finally {
      tracing.endSpan(spanObj);
    }
  }

  @Test
  @DisplayName("extractContext recovers trace ID from tags")
  void extractRecoversContext() {
    Object producerSpan = tracing.startSpan(SpanKind.PRODUCER, "publish", "ch", "msg-1", null);
    Map<String, String> tags = new HashMap<>();
    try (AutoCloseable scope = tracing.makeCurrent(producerSpan)) {
      tracing.injectContext(null, tags);
    } catch (Exception e) {
      fail("Unexpected exception", e);
    } finally {
      tracing.endSpan(producerSpan);
    }

    Object extractedCtx = tracing.extractContext(tags);
    assertNotNull(extractedCtx, "Extracted context should not be null");

    Span extractedSpan = Span.fromContext((Context) extractedCtx);
    assertTrue(extractedSpan.getSpanContext().isValid(), "Extracted span context should be valid");

    SpanData originalSpan = exporter.getFinishedSpanItems().get(0);
    assertEquals(
        originalSpan.getTraceId(),
        extractedSpan.getSpanContext().getTraceId(),
        "Trace IDs should match after round-trip");
  }

  @Test
  @DisplayName("extractContext with empty tags returns current context gracefully")
  void extractWithEmptyTags() {
    Object ctx = tracing.extractContext(new HashMap<>());
    assertNotNull(ctx, "Should return a context even with empty tags");
  }

  @Test
  @DisplayName("extractContext with null tags returns current context")
  void extractWithNullTags() {
    Object ctx = tracing.extractContext(null);
    assertNotNull(ctx);
  }

  @Test
  @DisplayName("injectContext with null tags is a no-op")
  void injectWithNullTags() {
    assertDoesNotThrow(() -> tracing.injectContext(null, null));
  }

  @Test
  @DisplayName("round-trip: inject on producer, extract and link on consumer")
  void roundTripInjectExtract() {
    Object producerSpan = tracing.startSpan(SpanKind.PRODUCER, "publish", "ch", "msg-1", null);
    Map<String, String> tags = new HashMap<>();
    try (AutoCloseable scope = tracing.makeCurrent(producerSpan)) {
      tracing.injectContext(null, tags);
    } catch (Exception e) {
      fail("Unexpected exception", e);
    } finally {
      tracing.endSpan(producerSpan);
    }

    Object producerCtx = tracing.extractContext(tags);
    Object consumerSpan = tracing.startLinkedConsumerSpan("process", "ch", "msg-1", producerCtx);
    tracing.endSpan(consumerSpan);

    List<SpanData> spans = exporter.getFinishedSpanItems();
    assertEquals(2, spans.size());

    SpanData consumer = spans.get(1);
    assertFalse(consumer.getLinks().isEmpty(), "Consumer span should have a link to producer");
    assertEquals(
        spans.get(0).getTraceId(),
        consumer.getLinks().get(0).getSpanContext().getTraceId(),
        "Link should point to producer's trace");
  }
}
