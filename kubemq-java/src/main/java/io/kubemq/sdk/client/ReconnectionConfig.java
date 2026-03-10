package io.kubemq.sdk.client;

import lombok.Builder;
import lombok.Getter;

/**
 * Configuration for automatic reconnection behavior.
 * <p>
 * All parameters have sensible defaults and are configurable via the builder.
 */
@Getter
@Builder
public class ReconnectionConfig {

    /** Maximum number of reconnection attempts. -1 = unlimited. Default: -1. */
    @Builder.Default
    private final int maxReconnectAttempts = -1;

    /** Initial delay before the first reconnection attempt. Default: 500ms. */
    @Builder.Default
    private final long initialReconnectDelayMs = 500;

    /** Maximum delay between reconnection attempts. Default: 30_000ms (30s). */
    @Builder.Default
    private final long maxReconnectDelayMs = 30_000;

    /** Backoff multiplier applied after each failed attempt. Default: 2.0. */
    @Builder.Default
    private final double reconnectBackoffMultiplier = 2.0;

    /** Enable full jitter on backoff delays. Default: true. */
    @Builder.Default
    private final boolean reconnectJitterEnabled = true;

    /** Maximum size of the reconnection message buffer in bytes. Default: 8MB. */
    @Builder.Default
    private final long reconnectBufferSizeBytes = 8 * 1024 * 1024;

    /** Buffer overflow policy. Default: ERROR. */
    @Builder.Default
    private final BufferOverflowPolicy bufferOverflowPolicy = BufferOverflowPolicy.ERROR;
}
