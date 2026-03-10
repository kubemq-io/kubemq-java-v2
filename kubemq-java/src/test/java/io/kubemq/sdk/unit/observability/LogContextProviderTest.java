package io.kubemq.sdk.unit.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LogContextProvider")
class LogContextProviderTest {

    private Object[] callGetTraceContext() throws Exception {
        Class<?> clazz = Class.forName("io.kubemq.sdk.observability.LogContextProvider");
        Method method = clazz.getDeclaredMethod("getTraceContext");
        method.setAccessible(true);
        return (Object[]) method.invoke(null);
    }

    @Test
    @DisplayName("returns empty array when no active span")
    void emptyWhenNoSpan() throws Exception {
        Object[] result = callGetTraceContext();
        assertEquals(0, result.length);
    }

    @Test
    @DisplayName("returns trace_id and span_id when OTel span is active")
    void returnsTraceContext() throws Exception {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        Tracer tracer = provider.get("test");

        Span span = tracer.spanBuilder("test-span").startSpan();
        try (Scope scope = span.makeCurrent()) {
            Object[] result = callGetTraceContext();
            assertEquals(4, result.length);
            assertEquals("trace_id", result[0]);
            assertNotNull(result[1]);
            assertEquals("span_id", result[2]);
            assertNotNull(result[3]);

            assertEquals(span.getSpanContext().getTraceId(), result[1]);
            assertEquals(span.getSpanContext().getSpanId(), result[3]);
        } finally {
            span.end();
            provider.close();
        }
    }
}
