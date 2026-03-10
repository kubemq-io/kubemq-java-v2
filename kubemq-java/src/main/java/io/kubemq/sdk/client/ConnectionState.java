package io.kubemq.sdk.client;

/**
 * Represents the connection state of a KubeMQ client.
 *
 * <pre>
 * IDLE ──&gt; CONNECTING ──&gt; READY
 *   ^          |             |
 *   |          v             v
 *   |    RECONNECTING ──&gt; READY
 *   |          |
 *   v          v
 *        CLOSED (terminal)
 * </pre>
 */
public enum ConnectionState {
    /** Created but not yet connected. */
    IDLE,
    /** Initial connection in progress. */
    CONNECTING,
    /** Connected and operational. */
    READY,
    /** Connection lost, attempting to reconnect. */
    RECONNECTING,
    /** Permanently closed (terminal state). */
    CLOSED
}
