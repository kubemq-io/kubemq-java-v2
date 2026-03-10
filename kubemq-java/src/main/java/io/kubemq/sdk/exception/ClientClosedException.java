package io.kubemq.sdk.exception;

/**
 * Thrown when an operation is attempted on a closed client.
 * This is a non-retryable error.
 */
public class ClientClosedException extends KubeMQException {

    private static final long serialVersionUID = 1L;

    protected ClientClosedException(Builder builder) {
        super(builder);
    }

    public static ClientClosedException create() {
        return new Builder().build();
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        public Builder() {
            code(ErrorCode.CONNECTION_CLOSED);
            category(ErrorCategory.FATAL);
            retryable(false);
            message("Client has been closed. Create a new client instance for further operations.");
        }

        @Override
        public ClientClosedException build() {
            return new ClientClosedException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
