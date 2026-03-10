package io.kubemq.sdk.unit.observability;

import io.kubemq.sdk.observability.KubeMQTracing;
import io.kubemq.sdk.observability.NoOpTracing;
import io.kubemq.sdk.observability.Tracing;
import io.kubemq.sdk.observability.TracingFactory;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tracing")
class TracingTest {

    @Nested
    @DisplayName("NoOpTracing")
    class NoOpTests {

        @Test
        @DisplayName("startSpan returns non-null object")
        void startSpanReturnsNonNull() {
            Object span = NoOpTracing.INSTANCE.startSpan(
                    null, "publish", "test-channel", "msg-1", null);
            assertNotNull(span);
        }

        @Test
        @DisplayName("all methods are no-ops without exceptions")
        void allMethodsAreNoOps() {
            Tracing t = NoOpTracing.INSTANCE;
            Object span = t.startSpan(null, "op", "ch", null, null);
            assertDoesNotThrow(() -> {
                t.recordRetryEvent(span, 1, 0.5, "transient");
                t.recordDlqEvent(span);
                t.setError(span, new RuntimeException("test"), "fatal");
                t.setBodySize(span, new byte[]{1, 2, 3});
                t.setConsumerGroup(span, "group");
                t.endSpan(span);
            });
        }

        @Test
        @DisplayName("makeCurrent returns closeable scope")
        void makeCurrentReturnsScope() throws Exception {
            AutoCloseable scope = NoOpTracing.INSTANCE.makeCurrent(new Object());
            assertNotNull(scope);
            scope.close();
        }

        @Test
        @DisplayName("injectContext and extractContext are no-ops")
        void contextPropagationNoOps() {
            NoOpTracing.INSTANCE.injectContext(null, Collections.emptyMap());
            Object ctx = NoOpTracing.INSTANCE.extractContext(Collections.emptyMap());
            assertNull(ctx);
        }

        @Test
        @DisplayName("startLinkedConsumerSpan returns non-null")
        void linkedConsumerSpanReturnsNonNull() {
            Object span = NoOpTracing.INSTANCE.startLinkedConsumerSpan(
                    "process", "ch", "msg-1", null);
            assertNotNull(span);
        }

        @Test
        @DisplayName("startBatchReceiveSpan returns non-null")
        void batchReceiveReturnsNonNull() {
            Object span = NoOpTracing.INSTANCE.startBatchReceiveSpan(
                    "ch", 5, Collections.emptyList());
            assertNotNull(span);
        }
    }

    @Nested
    @DisplayName("KubeMQTracing with OTel SDK")
    class OTelTracingTests {

        private InMemorySpanExporter exporter;
        private SdkTracerProvider tracerProvider;
        private KubeMQTracing tracing;

        @BeforeEach
        void setUp() {
            exporter = InMemorySpanExporter.create();
            tracerProvider = SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build();
            tracing = new KubeMQTracing(tracerProvider, "2.1.1",
                    "test-client", "localhost", 50000);
        }

        @AfterEach
        void tearDown() {
            tracerProvider.close();
        }

        @Test
        @DisplayName("startSpan creates PRODUCER span with correct attributes")
        void producerSpanAttributes() {
            Object spanObj = tracing.startSpan(SpanKind.PRODUCER, "publish",
                    "events-channel", "msg-123", null);
            tracing.endSpan(spanObj);

            List<SpanData> spans = exporter.getFinishedSpanItems();
            assertEquals(1, spans.size());

            SpanData span = spans.get(0);
            assertEquals("publish events-channel", span.getName());
            assertEquals(SpanKind.PRODUCER, span.getKind());
            assertEquals("kubemq", span.getAttributes().get(
                    io.opentelemetry.api.common.AttributeKey.stringKey("messaging.system")));
            assertEquals("publish", span.getAttributes().get(
                    io.opentelemetry.api.common.AttributeKey.stringKey("messaging.operation.name")));
            assertEquals("events-channel", span.getAttributes().get(
                    io.opentelemetry.api.common.AttributeKey.stringKey("messaging.destination.name")));
            assertEquals("msg-123", span.getAttributes().get(
                    io.opentelemetry.api.common.AttributeKey.stringKey("messaging.message.id")));
            assertEquals("test-client", span.getAttributes().get(
                    io.opentelemetry.api.common.AttributeKey.stringKey("messaging.client.id")));
            assertEquals("localhost", span.getAttributes().get(
                    io.opentelemetry.api.common.AttributeKey.stringKey("server.address")));
            assertEquals(50000L, span.getAttributes().get(
                    io.opentelemetry.api.common.AttributeKey.longKey("server.port")));
        }

        @Test
        @DisplayName("startSpan creates CLIENT span for commands")
        void clientSpanForCommands() {
            Object spanObj = tracing.startSpan(SpanKind.CLIENT, "send",
                    "cmd-channel", "req-1", null);
            tracing.endSpan(spanObj);

            SpanData span = exporter.getFinishedSpanItems().get(0);
            assertEquals("send cmd-channel", span.getName());
            assertEquals(SpanKind.CLIENT, span.getKind());
        }

        @Test
        @DisplayName("startLinkedConsumerSpan creates CONSUMER span with link")
        void linkedConsumerSpan() {
            Object producerSpan = tracing.startSpan(SpanKind.PRODUCER, "publish",
                    "ch", "msg-1", null);
            Context producerCtx = Context.current().with((Span) producerSpan);
            tracing.endSpan(producerSpan);

            Object consumerSpan = tracing.startLinkedConsumerSpan("process",
                    "ch", "msg-1", producerCtx);
            tracing.endSpan(consumerSpan);

            List<SpanData> spans = exporter.getFinishedSpanItems();
            assertEquals(2, spans.size());

            SpanData consumer = spans.get(1);
            assertEquals(SpanKind.CONSUMER, consumer.getKind());
            assertEquals("process ch", consumer.getName());
            assertFalse(consumer.getLinks().isEmpty(), "Consumer span should have a link to producer");
        }

        @Test
        @DisplayName("startBatchReceiveSpan sets batch message count")
        void batchReceiveSpan() {
            Object spanObj = tracing.startBatchReceiveSpan("queue-ch", 10, Collections.emptyList());
            tracing.endSpan(spanObj);

            SpanData span = exporter.getFinishedSpanItems().get(0);
            assertEquals("receive queue-ch", span.getName());
            assertEquals(SpanKind.CONSUMER, span.getKind());
            assertEquals(10L, span.getAttributes().get(
                    io.opentelemetry.api.common.AttributeKey.longKey("messaging.batch.message_count")));
        }

        @Test
        @DisplayName("setError sets ERROR status and records exception")
        void setErrorOnSpan() {
            Object spanObj = tracing.startSpan(SpanKind.PRODUCER, "publish",
                    "ch", null, null);
            RuntimeException err = new RuntimeException("connection lost");
            tracing.setError(spanObj, err, "transient");
            tracing.endSpan(spanObj);

            SpanData span = exporter.getFinishedSpanItems().get(0);
            assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
            assertEquals("transient", span.getAttributes().get(
                    io.opentelemetry.api.common.AttributeKey.stringKey("error.type")));
            assertTrue(span.getEvents().stream()
                    .anyMatch(e -> e.getName().equals("exception")));
        }

        @Test
        @DisplayName("recordRetryEvent adds retry span event")
        void retryEvent() {
            Object spanObj = tracing.startSpan(SpanKind.PRODUCER, "publish",
                    "ch", null, null);
            tracing.recordRetryEvent(spanObj, 2, 1.5, "timeout");
            tracing.endSpan(spanObj);

            SpanData span = exporter.getFinishedSpanItems().get(0);
            List<EventData> events = span.getEvents();
            assertEquals(1, events.size());
            assertEquals("retry", events.get(0).getName());
            assertEquals(2L, events.get(0).getAttributes().get(
                    io.opentelemetry.api.common.AttributeKey.longKey("retry.attempt")));
            assertEquals(1.5, events.get(0).getAttributes().get(
                    io.opentelemetry.api.common.AttributeKey.doubleKey("retry.delay_seconds")));
        }

        @Test
        @DisplayName("recordDlqEvent adds DLQ span event")
        void dlqEvent() {
            Object spanObj = tracing.startSpan(SpanKind.CONSUMER, "settle",
                    "ch", null, null);
            tracing.recordDlqEvent(spanObj);
            tracing.endSpan(spanObj);

            SpanData span = exporter.getFinishedSpanItems().get(0);
            assertTrue(span.getEvents().stream()
                    .anyMatch(e -> e.getName().equals("message.dead_lettered")));
        }

        @Test
        @DisplayName("setBodySize sets body size attribute")
        void bodySize() {
            Object spanObj = tracing.startSpan(SpanKind.PRODUCER, "publish",
                    "ch", null, null);
            tracing.setBodySize(spanObj, new byte[42]);
            tracing.endSpan(spanObj);

            SpanData span = exporter.getFinishedSpanItems().get(0);
            assertEquals(42L, span.getAttributes().get(
                    io.opentelemetry.api.common.AttributeKey.longKey("messaging.message.body.size")));
        }

        @Test
        @DisplayName("setConsumerGroup sets consumer group attribute")
        void consumerGroup() {
            Object spanObj = tracing.startSpan(SpanKind.CONSUMER, "process",
                    "ch", null, null);
            tracing.setConsumerGroup(spanObj, "my-group");
            tracing.endSpan(spanObj);

            SpanData span = exporter.getFinishedSpanItems().get(0);
            assertEquals("my-group", span.getAttributes().get(
                    io.opentelemetry.api.common.AttributeKey.stringKey("messaging.consumer.group.name")));
        }

        @Test
        @DisplayName("instrumentation scope has correct name")
        void instrumentationScope() {
            Object spanObj = tracing.startSpan(SpanKind.PRODUCER, "publish",
                    "ch", null, null);
            tracing.endSpan(spanObj);

            SpanData span = exporter.getFinishedSpanItems().get(0);
            assertEquals("io.kubemq.sdk", span.getInstrumentationScopeInfo().getName());
            assertEquals("2.1.1", span.getInstrumentationScopeInfo().getVersion());
        }

        @Test
        @DisplayName("null messageId does not set attribute")
        void nullMessageId() {
            Object spanObj = tracing.startSpan(SpanKind.PRODUCER, "publish",
                    "ch", null, null);
            tracing.endSpan(spanObj);

            SpanData span = exporter.getFinishedSpanItems().get(0);
            assertNull(span.getAttributes().get(
                    io.opentelemetry.api.common.AttributeKey.stringKey("messaging.message.id")));
        }

        @Test
        @DisplayName("setBodySize with null body is no-op")
        void nullBodySize() {
            Object spanObj = tracing.startSpan(SpanKind.PRODUCER, "publish",
                    "ch", null, null);
            tracing.setBodySize(spanObj, null);
            tracing.endSpan(spanObj);

            SpanData span = exporter.getFinishedSpanItems().get(0);
            assertNull(span.getAttributes().get(
                    io.opentelemetry.api.common.AttributeKey.longKey("messaging.message.body.size")));
        }
    }

    @Nested
    @DisplayName("TracingFactory")
    class FactoryTests {

        @Test
        @DisplayName("creates KubeMQTracing when OTel is available")
        void createsOTelTracing() {
            SdkTracerProvider provider = SdkTracerProvider.builder().build();
            try {
                Tracing tracing = TracingFactory.create(provider, "1.0.0",
                        "client-1", "localhost", 50000);
                assertNotNull(tracing);
                assertFalse(tracing instanceof NoOpTracing);
            } finally {
                provider.close();
            }
        }

        @Test
        @DisplayName("creates KubeMQTracing with null provider (uses GlobalOpenTelemetry)")
        void createsWithNullProvider() {
            Tracing tracing = TracingFactory.create(null, "1.0.0",
                    "client-1", "localhost", 50000);
            assertNotNull(tracing);
            assertFalse(tracing instanceof NoOpTracing);
        }
    }
}
