package io.kubemq.sdk.exception;

/**
 * Thrown when an input validation fails for a KubeMQ operation.
 */
public class ValidationException extends KubeMQException {

    private static final long serialVersionUID = 1L;

    protected ValidationException(Builder builder) {
        super(builder);
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        public Builder() {
            code(ErrorCode.INVALID_ARGUMENT);
            category(ErrorCategory.VALIDATION);
            retryable(false);
        }

        @Override
        public ValidationException build() {
            return new ValidationException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
