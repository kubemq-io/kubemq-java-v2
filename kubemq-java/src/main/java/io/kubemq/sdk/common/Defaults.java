package io.kubemq.sdk.common;

import java.time.Duration;

/**
 * Default timeout values per GS REQ-ERR-4.
 */
public final class Defaults {
    private Defaults() {}

    public static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration SUBSCRIBE_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration RPC_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration QUEUE_RECEIVE_SINGLE_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration QUEUE_RECEIVE_STREAMING_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10);
}
