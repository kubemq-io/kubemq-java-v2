package io.kubemq.sdk.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.List;
import java.util.Map;

/**
 * Centralized tracing instrumentation for the KubeMQ SDK.
 * Implements the {@link Tracing} interface and is loaded only when OTel API
 * is confirmed available on the classpath (via {@link TracingFactory}).
 * <p>
 * When no OTel SDK is registered, all operations use OTel's built-in no-op
 * implementations with near-zero overhead (REQ-OBS-4).
 */
public final class KubeMQTracing implements Tracing {

    private final Tracer tracer;
    private final String clientId;
    private final String serverAddress;
    private final int serverPort;

    /**
     * Constructor called reflectively by {@link TracingFactory}.
     *
     * @param tracerProviderObj OTel TracerProvider (null = use GlobalOpenTelemetry)
     * @param sdkVersion        SDK version for instrumentation scope
     * @param clientId          client ID for messaging.client.id attribute
     * @param serverAddress     server hostname
     * @param serverPort        server port
     */
    public KubeMQTracing(Object tracerProviderObj, String sdkVersion,
                          String clientId, String serverAddress, int serverPort) {
        TracerProvider provider;
        if (tracerProviderObj != null) {
            provider = (TracerProvider) tracerProviderObj;
        } else {
            provider = GlobalOpenTelemetry.getTracerProvider();
        }
        this.tracer = provider.get(
                KubeMQSemconv.INSTRUMENTATION_SCOPE_NAME,
                sdkVersion != null ? sdkVersion : "unknown");
        this.clientId = clientId;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    @Override
    public Object startSpan(Object spanKindObj, String operationName, String channel,
                             String messageId, Object parentContextObj) {
        SpanBuilder builder = newSpanBuilder((SpanKind) spanKindObj, operationName, channel);

        if (messageId != null) {
            builder.setAttribute(KubeMQSemconv.MESSAGING_MESSAGE_ID, messageId);
        }
        if (parentContextObj != null) {
            builder.setParent((Context) parentContextObj);
        }

        return builder.startSpan();
    }

    @Override
    public Object startLinkedConsumerSpan(String operationName, String channel,
                                           String messageId, Object producerContextObj) {
        SpanBuilder builder = newSpanBuilder(SpanKind.CONSUMER, operationName, channel);

        if (messageId != null) {
            builder.setAttribute(KubeMQSemconv.MESSAGING_MESSAGE_ID, messageId);
        }

        addProducerLink(builder, producerContextObj);

        return builder.startSpan();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object startBatchReceiveSpan(String channel, int messageCount,
                                          List<?> producerContextsRaw) {
        SpanBuilder builder = newSpanBuilder(SpanKind.CONSUMER, KubeMQSemconv.OP_RECEIVE, channel)
                .setAttribute(KubeMQSemconv.MESSAGING_BATCH_MESSAGE_COUNT,
                              (long) messageCount);

        List<Context> producerContexts = (List<Context>) (List<?>) producerContextsRaw;
        if (producerContexts != null) {
            int limit = Math.min(producerContexts.size(), 128);
            for (int i = 0; i < limit; i++) {
                addProducerLink(builder, producerContexts.get(i));
            }
        }

        return builder.startSpan();
    }

    @Override
    public void recordRetryEvent(Object spanObj, int attempt,
                                  double delaySeconds, String errorType) {
        Span span = (Span) spanObj;
        if (span.isRecording()) {
            span.addEvent(KubeMQSemconv.RETRY_EVENT_NAME, Attributes.of(
                    KubeMQSemconv.RETRY_ATTEMPT, (long) attempt,
                    KubeMQSemconv.RETRY_DELAY_SECONDS, delaySeconds,
                    KubeMQSemconv.ERROR_TYPE, errorType != null ? errorType : "unknown"
            ));
        }
    }

    @Override
    public void recordDlqEvent(Object spanObj) {
        Span span = (Span) spanObj;
        if (span.isRecording()) {
            span.addEvent(KubeMQSemconv.DLQ_EVENT_NAME);
        }
    }

    @Override
    public void setError(Object spanObj, Throwable error, String errorTypeValue) {
        Span span = (Span) spanObj;
        if (span.isRecording()) {
            span.setStatus(StatusCode.ERROR, error.getMessage());
            span.setAttribute(KubeMQSemconv.ERROR_TYPE,
                              errorTypeValue != null ? errorTypeValue : "unknown");
            span.recordException(error);
        }
    }

    @Override
    public void setBodySize(Object spanObj, byte[] body) {
        Span span = (Span) spanObj;
        if (span.isRecording() && body != null) {
            span.setAttribute(KubeMQSemconv.MESSAGING_MESSAGE_BODY_SIZE,
                              (long) body.length);
        }
    }

    @Override
    public void setConsumerGroup(Object spanObj, String groupName) {
        Span span = (Span) spanObj;
        if (span.isRecording() && groupName != null && !groupName.isEmpty()) {
            span.setAttribute(KubeMQSemconv.MESSAGING_CONSUMER_GROUP_NAME,
                              groupName);
        }
    }

    @Override
    public void endSpan(Object spanObj) {
        ((Span) spanObj).end();
    }

    @Override
    public AutoCloseable makeCurrent(Object spanObj) {
        return ((Span) spanObj).makeCurrent();
    }

    @Override
    public void injectContext(Object currentContextObj, Map<String, String> tags) {
        if (tags == null) {
            return;
        }
        Context ctx = currentContextObj != null ? (Context) currentContextObj : Context.current();
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                .inject(ctx, tags, KubeMQTagsCarrier.SETTER);
    }

    @Override
    public Object extractContext(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Context.current();
        }
        return GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.current(), tags, KubeMQTagsCarrier.GETTER);
    }

    private SpanBuilder newSpanBuilder(SpanKind kind, String operationName, String channel) {
        return tracer.spanBuilder(operationName + " " + channel)
                .setSpanKind(kind)
                .setAttribute(KubeMQSemconv.MESSAGING_SYSTEM,
                              KubeMQSemconv.MESSAGING_SYSTEM_VALUE)
                .setAttribute(KubeMQSemconv.MESSAGING_OPERATION_NAME, operationName)
                .setAttribute(KubeMQSemconv.MESSAGING_OPERATION_TYPE, operationName)
                .setAttribute(KubeMQSemconv.MESSAGING_DESTINATION_NAME, channel)
                .setAttribute(KubeMQSemconv.MESSAGING_CLIENT_ID, clientId)
                .setAttribute(KubeMQSemconv.SERVER_ADDRESS, serverAddress)
                .setAttribute(KubeMQSemconv.SERVER_PORT, (long) serverPort);
    }

    private static void addProducerLink(SpanBuilder builder, Object contextObj) {
        if (contextObj != null) {
            Span producerSpan = Span.fromContext((Context) contextObj);
            if (producerSpan.getSpanContext().isValid()) {
                builder.addLink(producerSpan.getSpanContext());
            }
        }
    }

    /**
     * Returns the underlying Tracer for advanced use cases.
     */
    public Tracer getTracer() {
        return tracer;
    }
}
