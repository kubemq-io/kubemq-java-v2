package io.kubemq.sdk.client;

/**
 * Marker interface for messages that can be buffered during reconnection.
 * Implementations must provide a size estimate for buffer capacity tracking.
 */
public interface BufferedMessage {
    /**
     * @return estimated size of this message in bytes, used for buffer capacity tracking
     */
    long estimatedSizeBytes();

    /**
     * @return the gRPC request object to resend after reconnection
     */
    Object grpcRequest();

    /**
     * @return the message type for routing during flush
     */
    MessageType messageType();

    /** Discriminator for routing buffered messages to the correct send path. */
    enum MessageType { EVENT, EVENT_STORE, QUEUE }
}
