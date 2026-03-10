package io.kubemq.sdk.exception;

/**
 * Thrown when the client lacks required permissions for a KubeMQ operation.
 */
public class AuthorizationException extends KubeMQException {

    private static final long serialVersionUID = 1L;

    protected AuthorizationException(Builder builder) {
        super(builder);
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        public Builder() {
            code(ErrorCode.AUTHORIZATION_DENIED);
            category(ErrorCategory.AUTHORIZATION);
            retryable(false);
        }

        @Override
        public AuthorizationException build() {
            return new AuthorizationException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
