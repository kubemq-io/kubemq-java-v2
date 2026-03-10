package io.kubemq.sdk.client;

/**
 * Listener for connection state transitions.
 * All methods have default no-op implementations, allowing users to override
 * only the events they care about.
 *
 * <p>Listeners are invoked asynchronously on a dedicated executor and must not
 * block the connection.</p>
 */
public interface ConnectionStateListener {

    /** Invoked when the client establishes its initial connection. */
    default void onConnected() {}

    /** Invoked when the connection is lost. */
    default void onDisconnected() {}

    /**
     * Invoked when a reconnection attempt starts.
     * @param attempt the current attempt number (1-based)
     */
    default void onReconnecting(int attempt) {}

    /** Invoked when a reconnection attempt succeeds. */
    default void onReconnected() {}

    /** Invoked when the client is permanently closed. */
    default void onClosed() {}
}
