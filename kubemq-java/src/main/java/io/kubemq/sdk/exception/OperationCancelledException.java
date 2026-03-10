package io.kubemq.sdk.exception;

/**
 * Thrown when a KubeMQ operation is cancelled by the client.
 */
public class OperationCancelledException extends KubeMQException {

    private static final long serialVersionUID = 1L;

    protected OperationCancelledException(Builder builder) {
        super(builder);
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        public Builder() {
            code(ErrorCode.CANCELLED_BY_CLIENT);
            category(ErrorCategory.CANCELLATION);
            retryable(false);
        }

        @Override
        public OperationCancelledException build() {
            return new OperationCancelledException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
