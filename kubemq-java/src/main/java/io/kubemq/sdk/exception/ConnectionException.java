package io.kubemq.sdk.exception;

/**
 * Thrown when a connection to KubeMQ server fails or is lost.
 */
public class ConnectionException extends KubeMQException {

    private static final long serialVersionUID = 1L;

    protected ConnectionException(Builder builder) {
        super(builder);
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        public Builder() {
            code(ErrorCode.CONNECTION_FAILED);
            category(ErrorCategory.TRANSIENT);
            retryable(true);
        }

        @Override
        public ConnectionException build() {
            return new ConnectionException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
