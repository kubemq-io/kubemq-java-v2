package io.kubemq.sdk.exception;

/**
 * Thrown when a transport-layer (gRPC) error occurs.
 * Used internally to distinguish transport errors from handler errors.
 */
public class TransportException extends KubeMQException {

    private static final long serialVersionUID = 1L;

    protected TransportException(Builder builder) {
        super(builder);
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        public Builder() {
            code(ErrorCode.CONNECTION_FAILED);
            category(ErrorCategory.TRANSIENT);
            retryable(true);
        }

        @Override
        public TransportException build() {
            return new TransportException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
