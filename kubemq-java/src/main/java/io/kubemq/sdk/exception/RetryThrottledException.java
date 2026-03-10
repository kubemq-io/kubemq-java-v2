package io.kubemq.sdk.exception;

/**
 * Thrown when concurrent retry limit is reached and a retry attempt is rejected.
 */
public class RetryThrottledException extends KubeMQException {

    private static final long serialVersionUID = 1L;

    protected RetryThrottledException(Builder builder) {
        super(builder);
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        public Builder() {
            code(ErrorCode.RETRY_THROTTLED);
            category(ErrorCategory.THROTTLING);
            retryable(false);
        }

        @Override
        public RetryThrottledException build() {
            return new RetryThrottledException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
