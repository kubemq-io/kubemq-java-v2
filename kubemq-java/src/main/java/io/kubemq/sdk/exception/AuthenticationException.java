package io.kubemq.sdk.exception;

/**
 * Thrown when authentication with KubeMQ server fails.
 */
public class AuthenticationException extends KubeMQException {

    private static final long serialVersionUID = 1L;

    protected AuthenticationException(Builder builder) {
        super(builder);
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        public Builder() {
            code(ErrorCode.AUTHENTICATION_FAILED);
            category(ErrorCategory.AUTHENTICATION);
            retryable(false);
        }

        @Override
        public AuthenticationException build() {
            return new AuthenticationException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
