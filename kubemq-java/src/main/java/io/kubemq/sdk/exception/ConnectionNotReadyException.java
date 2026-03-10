package io.kubemq.sdk.exception;

/**
 * Thrown when an operation is attempted while the connection is not READY
 * and waitForReady is false. This is a non-retryable error -- the caller
 * should wait for the connection to become ready rather than retrying.
 */
public class ConnectionNotReadyException extends KubeMQException {

    private static final long serialVersionUID = 1L;

    protected ConnectionNotReadyException(Builder builder) {
        super(builder);
    }

    public static ConnectionNotReadyException create(String stateDescription) {
        return new Builder()
            .message("Connection is not ready (state=" + stateDescription
                + "). Set waitForReady=true to block until ready.")
            .build();
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        public Builder() {
            code(ErrorCode.UNAVAILABLE);
            category(ErrorCategory.TRANSIENT);
            retryable(false);
        }

        @Override
        public ConnectionNotReadyException build() {
            return new ConnectionNotReadyException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
