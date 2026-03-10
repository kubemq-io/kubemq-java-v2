package io.kubemq.sdk.exception;

/**
 * Thrown when a KubeMQ operation times out.
 */
public class KubeMQTimeoutException extends KubeMQException {

    private static final long serialVersionUID = 1L;

    protected KubeMQTimeoutException(Builder builder) {
        super(builder);
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        public Builder() {
            code(ErrorCode.OPERATION_TIMEOUT);
            category(ErrorCategory.TIMEOUT);
            retryable(true);
        }

        @Override
        public KubeMQTimeoutException build() {
            return new KubeMQTimeoutException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
