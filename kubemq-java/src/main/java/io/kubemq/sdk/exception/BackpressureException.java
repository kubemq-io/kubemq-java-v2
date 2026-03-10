package io.kubemq.sdk.exception;

/**
 * Thrown when the SDK send buffer is full and cannot accept more messages.
 */
public class BackpressureException extends KubeMQException {

    private static final long serialVersionUID = 1L;

    protected BackpressureException(Builder builder) {
        super(builder);
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        public Builder() {
            code(ErrorCode.BUFFER_FULL);
            category(ErrorCategory.BACKPRESSURE);
            retryable(false);
        }

        @Override
        public BackpressureException build() {
            return new BackpressureException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Factory for buffer-full errors during reconnection buffering.
     */
    public static BackpressureException bufferFull(long currentBytes, long maxBytes, long messageSize) {
        return BackpressureException.builder()
            .code(ErrorCode.BUFFER_FULL)
            .message("Reconnection buffer full (" + currentBytes + "/" + maxBytes
                + " bytes). Message size: " + messageSize)
            .operation("buffer.add")
            .retryable(false)
            .build();
    }
}
