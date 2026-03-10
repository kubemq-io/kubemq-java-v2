package io.kubemq.sdk.observability;

import java.util.List;
import java.util.Map;

/**
 * Abstraction for SDK tracing operations. Client code uses this interface
 * exclusively; the OTel-importing implementation ({@code KubeMQTracing}) is loaded
 * only when OTel is confirmed available on the classpath.
 * <p>
 * All OTel-specific types are represented as {@code Object} to avoid
 * {@code NoClassDefFoundError} when OTel is absent.
 */
public interface Tracing {

    /**
     * Start a span for a messaging operation.
     *
     * @param spanKind      OTel SpanKind as Object (PRODUCER, CONSUMER, CLIENT, SERVER)
     * @param operationName one of publish, process, receive, settle, send
     * @param channel       destination channel name
     * @param messageId     message ID (may be null)
     * @param parentContext OTel Context as Object (may be null for root spans)
     * @return started span as Object (caller must end via {@link #endSpan(Object)})
     */
    Object startSpan(Object spanKind, String operationName, String channel,
                     String messageId, Object parentContext);

    /**
     * Start a consumer span linked (not parented) to a producer span context.
     *
     * @param operationName operation name (typically "process")
     * @param channel       destination channel name
     * @param messageId     message ID (may be null)
     * @param producerContext OTel Context from the producer side
     * @return started span as Object
     */
    Object startLinkedConsumerSpan(String operationName, String channel,
                                   String messageId, Object producerContext);

    /**
     * Start a batch receive span with message count and links to producer spans.
     *
     * @param channel          channel name
     * @param messageCount     number of messages in the batch
     * @param producerContexts producer contexts for linking (capped at 128)
     * @return started span as Object
     */
    Object startBatchReceiveSpan(String channel, int messageCount,
                                  List<?> producerContexts);

    /**
     * Record a retry attempt as a span event.
     */
    void recordRetryEvent(Object span, int attempt, double delaySeconds, String errorType);

    /**
     * Record a DLQ transition as a span event.
     */
    void recordDlqEvent(Object span);

    /**
     * Set error status on a span for a failed operation.
     */
    void setError(Object span, Throwable error, String errorTypeValue);

    /**
     * Set the body size attribute on a span (guarded by isRecording).
     */
    void setBodySize(Object span, byte[] body);

    /**
     * Set the consumer group attribute on a span.
     */
    void setConsumerGroup(Object span, String groupName);

    /**
     * End a span.
     */
    void endSpan(Object span);

    /**
     * Make a span current and return a scope that must be closed.
     */
    AutoCloseable makeCurrent(Object span);

    /**
     * Inject trace context into a tags map (W3C Trace Context propagation).
     *
     * @param currentContext OTel Context (null to use Context.current())
     * @param tags           mutable map to inject traceparent/tracestate into
     */
    void injectContext(Object currentContext, Map<String, String> tags);

    /**
     * Extract trace context from a tags map.
     *
     * @param tags message tags that may contain traceparent/tracestate
     * @return extracted OTel Context as Object
     */
    Object extractContext(Map<String, String> tags);
}
