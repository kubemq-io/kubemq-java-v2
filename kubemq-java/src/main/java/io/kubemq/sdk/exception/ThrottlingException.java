package io.kubemq.sdk.exception;

/**
 * Thrown when the server is rate limiting requests.
 */
public class ThrottlingException extends KubeMQException {

    private static final long serialVersionUID = 1L;

    protected ThrottlingException(Builder builder) {
        super(builder);
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        public Builder() {
            code(ErrorCode.RESOURCE_EXHAUSTED);
            category(ErrorCategory.THROTTLING);
            retryable(true);
        }

        @Override
        public ThrottlingException build() {
            return new ThrottlingException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
