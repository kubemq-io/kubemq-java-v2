package io.kubemq.sdk.exception;

/**
 * Thrown when the KubeMQ server encounters an internal error.
 */
public class ServerException extends KubeMQException {

    private static final long serialVersionUID = 1L;

    protected ServerException(Builder builder) {
        super(builder);
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        public Builder() {
            code(ErrorCode.SERVER_INTERNAL);
            category(ErrorCategory.FATAL);
            retryable(false);
        }

        @Override
        public ServerException build() {
            return new ServerException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
