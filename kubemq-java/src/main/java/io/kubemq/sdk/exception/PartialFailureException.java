package io.kubemq.sdk.exception;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a partial batch failure. Reserved for future use when the server
 * supports per-message batch status. Added per GS Future Enhancement guidance.
 */
public class PartialFailureException extends KubeMQException {

    private static final long serialVersionUID = 1L;
    private final Map<String, KubeMQException> perMessageErrors;

    protected PartialFailureException(Builder builder) {
        super(builder);
        this.perMessageErrors = builder.perMessageErrors != null
            ? Collections.unmodifiableMap(builder.perMessageErrors)
            : Collections.emptyMap();
    }

    public Map<String, KubeMQException> getPerMessageErrors() {
        return perMessageErrors;
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        private Map<String, KubeMQException> perMessageErrors;

        public Builder() {
            code(ErrorCode.PARTIAL_FAILURE);
            category(ErrorCategory.TRANSIENT);
            retryable(false);
        }

        public Builder perMessageErrors(Map<String, KubeMQException> errors) {
            this.perMessageErrors = errors;
            return this;
        }

        @Override
        public PartialFailureException build() {
            return new PartialFailureException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
