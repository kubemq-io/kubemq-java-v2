package io.kubemq.sdk.exception;

/**
 * Thrown when user callback code throws an exception during async processing.
 */
public class HandlerException extends KubeMQException {

    private static final long serialVersionUID = 1L;

    protected HandlerException(Builder builder) {
        super(builder);
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        public Builder() {
            code(ErrorCode.HANDLER_ERROR);
            category(ErrorCategory.FATAL);
            retryable(false);
        }

        @Override
        public HandlerException build() {
            return new HandlerException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
