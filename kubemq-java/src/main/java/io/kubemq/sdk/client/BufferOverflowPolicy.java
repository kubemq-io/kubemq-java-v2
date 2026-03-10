package io.kubemq.sdk.client;

/**
 * Policy for handling buffer overflow during reconnection.
 */
public enum BufferOverflowPolicy {
    /**
     * Return a BackpressureException immediately when the buffer is full.
     * This is non-blocking and the default policy.
     */
    ERROR,

    /**
     * Block the calling thread until space becomes available in the buffer.
     * Use with caution -- this can cause thread starvation if reconnection is slow.
     */
    BLOCK
}
