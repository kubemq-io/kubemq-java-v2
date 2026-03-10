package io.kubemq.sdk.exception;

/**
 * Thrown when the SDK detects a configuration error that cannot be resolved
 * at runtime (e.g., TLS version mismatch, invalid option combinations).
 * Non-retryable.
 */
public class ConfigurationException extends KubeMQException {

    private static final long serialVersionUID = 1L;

    protected ConfigurationException(Builder builder) {
        super(builder);
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        public Builder() {
            code(ErrorCode.FAILED_PRECONDITION);
            category(ErrorCategory.VALIDATION);
            retryable(false);
        }

        @Override
        public ConfigurationException build() {
            return new ConfigurationException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
