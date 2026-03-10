package io.kubemq.sdk.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;

import java.util.List;

/**
 * Centralized metrics instrumentation for the KubeMQ SDK.
 * Implements the {@link Metrics} interface and is loaded only when OTel API
 * is confirmed available on the classpath (via {@link MetricsFactory}).
 * <p>
 * When no OTel SDK is registered, all instruments are no-ops with near-zero overhead.
 */
public final class KubeMQMetrics implements Metrics {

    private final DoubleHistogram operationDuration;
    private final LongCounter sentMessages;
    private final LongCounter consumedMessages;
    private final LongUpDownCounter connectionCount;
    private final LongCounter reconnections;
    private final LongCounter retryAttempts;
    private final LongCounter retryExhausted;
    private final CardinalityManager cardinalityManager;

    private static final List<Double> HISTOGRAM_BOUNDARIES = List.of(
            0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.075, 0.1,
            0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0, 30.0, 60.0
    );

    /**
     * Constructor called reflectively by {@link MetricsFactory}.
     *
     * @param meterProviderObj  OTel MeterProvider (null = use GlobalOpenTelemetry)
     * @param sdkVersion        SDK version for instrumentation scope
     * @param cardinalityConfig cardinality management configuration (null for defaults)
     */
    public KubeMQMetrics(Object meterProviderObj, String sdkVersion,
                          CardinalityConfig cardinalityConfig) {
        MeterProvider provider;
        if (meterProviderObj != null) {
            provider = (MeterProvider) meterProviderObj;
        } else {
            provider = GlobalOpenTelemetry.getMeterProvider();
        }

        Meter meter = provider.meterBuilder(KubeMQSemconv.INSTRUMENTATION_SCOPE_NAME)
                .setInstrumentationVersion(sdkVersion != null ? sdkVersion : "unknown")
                .build();

        this.operationDuration = meter.histogramBuilder("messaging.client.operation.duration")
                .setUnit("s")
                .setDescription("Duration of each messaging operation")
                .setExplicitBucketBoundariesAdvice(HISTOGRAM_BOUNDARIES)
                .build();

        this.sentMessages = meter.counterBuilder("messaging.client.sent.messages")
                .setUnit("{message}")
                .setDescription("Total messages sent")
                .build();

        this.consumedMessages = meter.counterBuilder("messaging.client.consumed.messages")
                .setUnit("{message}")
                .setDescription("Total messages consumed")
                .build();

        this.connectionCount = meter.upDownCounterBuilder("messaging.client.connection.count")
                .setUnit("{connection}")
                .setDescription("Active connections")
                .build();

        this.reconnections = meter.counterBuilder("messaging.client.reconnections")
                .setUnit("{attempt}")
                .setDescription("Reconnection attempts")
                .build();

        this.retryAttempts = meter.counterBuilder("kubemq.client.retry.attempts")
                .setUnit("{attempt}")
                .setDescription("Retry attempts")
                .build();

        this.retryExhausted = meter.counterBuilder("kubemq.client.retry.exhausted")
                .setUnit("{attempt}")
                .setDescription("Retries exhausted")
                .build();

        this.cardinalityManager = new CardinalityManager(
                cardinalityConfig != null ? cardinalityConfig : CardinalityConfig.defaults());
    }

    @Override
    public void recordOperationDuration(double durationSeconds,
                                         String operationName,
                                         String channel,
                                         String errorType) {
        AttributesBuilder attrs = baseChannelAttrs(operationName, channel);
        if (errorType != null) {
            attrs.put(KubeMQSemconv.ERROR_TYPE, errorType);
        }
        operationDuration.record(durationSeconds, attrs.build());
    }

    @Override
    public void recordSentMessage(String operationName, String channel) {
        sentMessages.add(1, baseChannelAttrs(operationName, channel).build());
    }

    @Override
    public void recordConsumedMessage(String operationName, String channel) {
        consumedMessages.add(1, baseChannelAttrs(operationName, channel).build());
    }

    @Override
    public void recordConnectionOpened() {
        connectionCount.add(1, Attributes.of(
                KubeMQSemconv.MESSAGING_SYSTEM, KubeMQSemconv.MESSAGING_SYSTEM_VALUE));
    }

    @Override
    public void recordConnectionClosed() {
        connectionCount.add(-1, Attributes.of(
                KubeMQSemconv.MESSAGING_SYSTEM, KubeMQSemconv.MESSAGING_SYSTEM_VALUE));
    }

    @Override
    public void recordReconnectionAttempt() {
        reconnections.add(1, Attributes.of(
                KubeMQSemconv.MESSAGING_SYSTEM, KubeMQSemconv.MESSAGING_SYSTEM_VALUE));
    }

    @Override
    public void recordRetryAttempt(String operationName, String errorType) {
        retryAttempts.add(1, retryAttrs(operationName, errorType));
    }

    @Override
    public void recordRetryExhausted(String operationName, String errorType) {
        retryExhausted.add(1, retryAttrs(operationName, errorType));
    }

    private AttributesBuilder baseChannelAttrs(String operationName, String channel) {
        AttributesBuilder attrs = Attributes.builder()
                .put(KubeMQSemconv.MESSAGING_SYSTEM, KubeMQSemconv.MESSAGING_SYSTEM_VALUE)
                .put(KubeMQSemconv.MESSAGING_OPERATION_NAME, operationName);
        if (channel != null && cardinalityManager.shouldIncludeChannel(channel)) {
            attrs.put(KubeMQSemconv.MESSAGING_DESTINATION_NAME, channel);
        }
        return attrs;
    }

    private static Attributes retryAttrs(String operationName, String errorType) {
        return Attributes.of(
                KubeMQSemconv.MESSAGING_SYSTEM, KubeMQSemconv.MESSAGING_SYSTEM_VALUE,
                KubeMQSemconv.MESSAGING_OPERATION_NAME, operationName,
                KubeMQSemconv.ERROR_TYPE, errorType != null ? errorType : "unknown");
    }
}
