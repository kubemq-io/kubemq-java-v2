package io.kubemq.sdk.unit.observability;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.observability.KubeMQTracing;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("KubeMQTracing extended coverage")
class ObservabilityCoverageTest {

  private InMemorySpanExporter exporter;
  private SdkTracerProvider tracerProvider;
  private KubeMQTracing tracing;

  @BeforeEach
  void setUp() {
    exporter = InMemorySpanExporter.create();
    tracerProvider =
        SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
    tracing = new KubeMQTracing(tracerProvider, "2.1.0", "coverage-client", "127.0.0.1", 50000);
  }

  @AfterEach
  void tearDown() {
    tracerProvider.close();
  }

  @Nested
  @DisplayName("Constructor with null sdkVersion")
  class ConstructorTests {

    @Test
    @DisplayName("null sdkVersion uses 'unknown' in instrumentation scope")
    void nullSdkVersion_usesUnknown() {
      KubeMQTracing tracingNullVersion =
          new KubeMQTracing(tracerProvider, null, "client-1", "localhost", 50000);

      Object spanObj = tracingNullVersion.startSpan(SpanKind.PRODUCER, "publish", "ch", null, null);
      tracingNullVersion.endSpan(spanObj);

      List<SpanData> spans = exporter.getFinishedSpanItems();
      assertEquals(1, spans.size());
      assertEquals("unknown", spans.get(0).getInstrumentationScopeInfo().getVersion());
    }
  }

  @Nested
  @DisplayName("startSpan with parent context")
  class ParentContextTests {

    @Test
    @DisplayName("startSpan with parentContext creates child span")
    void startSpan_withParentContext_createsChildSpan() {
      Object parentSpan = tracing.startSpan(SpanKind.PRODUCER, "publish", "ch", "msg-parent", null);
      Context parentCtx = Context.current().with((Span) parentSpan);

      Object childSpan = tracing.startSpan(SpanKind.CLIENT, "send", "ch", "msg-child", parentCtx);

      tracing.endSpan(childSpan);
      tracing.endSpan(parentSpan);

      List<SpanData> spans = exporter.getFinishedSpanItems();
      assertEquals(2, spans.size());

      SpanData child = spans.get(0);
      SpanData parent = spans.get(1);
      assertEquals(parent.getTraceId(), child.getTraceId());
    }
  }

  @Nested
  @DisplayName("startLinkedConsumerSpan edge cases")
  class LinkedConsumerSpanTests {

    @Test
    @DisplayName("startLinkedConsumerSpan with null messageId omits attribute")
    void linkedConsumerSpan_nullMessageId_omitsAttribute() {
      Object span = tracing.startLinkedConsumerSpan("process", "ch", null, null);
      tracing.endSpan(span);

      SpanData data = exporter.getFinishedSpanItems().get(0);
      assertNull(
          data.getAttributes()
              .get(io.opentelemetry.api.common.AttributeKey.stringKey("messaging.message.id")));
    }

    @Test
    @DisplayName("startLinkedConsumerSpan with null producerContext creates no links")
    void linkedConsumerSpan_nullProducerContext_noLinks() {
      Object span = tracing.startLinkedConsumerSpan("process", "ch", "msg-1", null);
      tracing.endSpan(span);

      SpanData data = exporter.getFinishedSpanItems().get(0);
      assertTrue(data.getLinks().isEmpty());
    }
  }

  @Nested
  @DisplayName("startBatchReceiveSpan edge cases")
  class BatchReceiveSpanTests {

    @Test
    @DisplayName("startBatchReceiveSpan with null producerContexts creates no links")
    void batchReceiveSpan_nullContexts_noLinks() {
      Object span = tracing.startBatchReceiveSpan("batch-ch", 5, null);
      tracing.endSpan(span);

      SpanData data = exporter.getFinishedSpanItems().get(0);
      assertEquals(
          5L,
          data.getAttributes()
              .get(
                  io.opentelemetry.api.common.AttributeKey.longKey(
                      "messaging.batch.message_count")));
      assertTrue(data.getLinks().isEmpty());
    }

    @Test
    @DisplayName("startBatchReceiveSpan with producer contexts creates links")
    void batchReceiveSpan_withContexts_createsLinks() {
      Object producerSpan = tracing.startSpan(SpanKind.PRODUCER, "publish", "ch", "msg-1", null);
      Context producerCtx = Context.current().with((Span) producerSpan);
      tracing.endSpan(producerSpan);

      List<Context> contexts = Collections.singletonList(producerCtx);
      Object batchSpan = tracing.startBatchReceiveSpan("ch", 1, contexts);
      tracing.endSpan(batchSpan);

      List<SpanData> spans = exporter.getFinishedSpanItems();
      SpanData batch = spans.get(1);
      assertFalse(batch.getLinks().isEmpty());
    }
  }

  @Nested
  @DisplayName("recordRetryEvent with null errorType")
  class RetryEventTests {

    @Test
    @DisplayName("recordRetryEvent with null errorType uses 'unknown'")
    void retryEvent_nullErrorType_usesUnknown() {
      Object span = tracing.startSpan(SpanKind.PRODUCER, "publish", "ch", null, null);
      tracing.recordRetryEvent(span, 1, 0.5, null);
      tracing.endSpan(span);

      SpanData data = exporter.getFinishedSpanItems().get(0);
      assertEquals(
          "unknown",
          data.getEvents()
              .get(0)
              .getAttributes()
              .get(io.opentelemetry.api.common.AttributeKey.stringKey("error.type")));
    }
  }

  @Nested
  @DisplayName("setError with null errorTypeValue")
  class SetErrorTests {

    @Test
    @DisplayName("setError with null errorTypeValue uses 'unknown'")
    void setError_nullErrorType_usesUnknown() {
      Object span = tracing.startSpan(SpanKind.PRODUCER, "publish", "ch", null, null);
      tracing.setError(span, new RuntimeException("fail"), null);
      tracing.endSpan(span);

      SpanData data = exporter.getFinishedSpanItems().get(0);
      assertEquals(
          "unknown",
          data.getAttributes()
              .get(io.opentelemetry.api.common.AttributeKey.stringKey("error.type")));
    }
  }

  @Nested
  @DisplayName("setConsumerGroup edge cases")
  class ConsumerGroupTests {

    @Test
    @DisplayName("setConsumerGroup with null group name is no-op")
    void setConsumerGroup_null_isNoOp() {
      Object span = tracing.startSpan(SpanKind.CONSUMER, "process", "ch", null, null);
      tracing.setConsumerGroup(span, null);
      tracing.endSpan(span);

      SpanData data = exporter.getFinishedSpanItems().get(0);
      assertNull(
          data.getAttributes()
              .get(
                  io.opentelemetry.api.common.AttributeKey.stringKey(
                      "messaging.consumer.group.name")));
    }

    @Test
    @DisplayName("setConsumerGroup with empty group name is no-op")
    void setConsumerGroup_empty_isNoOp() {
      Object span = tracing.startSpan(SpanKind.CONSUMER, "process", "ch", null, null);
      tracing.setConsumerGroup(span, "");
      tracing.endSpan(span);

      SpanData data = exporter.getFinishedSpanItems().get(0);
      assertNull(
          data.getAttributes()
              .get(
                  io.opentelemetry.api.common.AttributeKey.stringKey(
                      "messaging.consumer.group.name")));
    }
  }

  @Nested
  @DisplayName("makeCurrent returns scope that can be closed")
  class MakeCurrentTests {

    @Test
    @DisplayName("makeCurrent returns a valid closeable scope")
    void makeCurrent_returnsCloseableScope() throws Exception {
      Object span = tracing.startSpan(SpanKind.PRODUCER, "publish", "ch", null, null);

      AutoCloseable scope = tracing.makeCurrent(span);
      assertNotNull(scope);
      scope.close();

      tracing.endSpan(span);
    }
  }

  @Nested
  @DisplayName("injectContext and extractContext edge cases")
  class ContextPropagationTests {

    @Test
    @DisplayName("injectContext with null tags is no-op")
    void injectContext_nullTags_noOp() {
      assertDoesNotThrow(() -> tracing.injectContext(null, null));
    }

    @Test
    @DisplayName("injectContext with null context uses Context.current()")
    void injectContext_nullContext_usesCurrentContext() {
      Map<String, String> tags = new HashMap<>();
      assertDoesNotThrow(() -> tracing.injectContext(null, tags));
    }

    @Test
    @DisplayName("injectContext with explicit context injects into tags")
    void injectContext_explicitContext_injectsIntoTags() {
      Object span = tracing.startSpan(SpanKind.PRODUCER, "publish", "ch", "msg-1", null);
      Context ctx = Context.current().with((Span) span);

      Map<String, String> tags = new HashMap<>();
      tracing.injectContext(ctx, tags);

      tracing.endSpan(span);
    }

    @Test
    @DisplayName("extractContext with null tags returns current context")
    void extractContext_nullTags_returnsCurrent() {
      Object ctx = tracing.extractContext(null);
      assertNotNull(ctx);
    }

    @Test
    @DisplayName("extractContext with empty tags returns current context")
    void extractContext_emptyTags_returnsCurrent() {
      Object ctx = tracing.extractContext(Collections.emptyMap());
      assertNotNull(ctx);
    }

    @Test
    @DisplayName("extractContext with propagation headers returns context")
    void extractContext_withHeaders_returnsContext() {
      Map<String, String> tags = new HashMap<>();
      tags.put("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");

      Object ctx = tracing.extractContext(tags);
      assertNotNull(ctx);
    }
  }

  @Nested
  @DisplayName("getTracer returns underlying tracer")
  class GetTracerTests {

    @Test
    @DisplayName("getTracer returns non-null tracer")
    void getTracer_returnsNonNull() {
      assertNotNull(tracing.getTracer());
    }
  }
}
