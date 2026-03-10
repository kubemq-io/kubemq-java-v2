package io.kubemq.sdk.exception;

/**
 * Thrown when an SDK method is called for a feature that is recognized by the
 * KubeMQ server but not yet implemented in this SDK version.
 *
 * <p>This exception signals an intentional gap, not a bug. The feature is documented
 * in the feature matrix ({@code clients/feature-matrix.md}) with rationale and
 * tracking information.</p>
 *
 * <p>This is never retryable -- the feature is structurally absent, not transiently
 * unavailable.</p>
 */
public class NotImplementedException extends KubeMQException {

    private static final long serialVersionUID = 1L;

    private NotImplementedException(Builder builder) {
        super(builder);
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        public Builder() {
            code(ErrorCode.FEATURE_NOT_IMPLEMENTED);
            category(ErrorCategory.FATAL);
            retryable(false);
        }

        @Override
        public NotImplementedException build() {
            return new NotImplementedException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
