package io.kubemq.sdk.exception;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when a gRPC stream breaks with in-flight messages.
 */
public class StreamBrokenException extends KubeMQException {

    private static final long serialVersionUID = 1L;
    private final List<String> unacknowledgedMessageIds;

    protected StreamBrokenException(Builder builder) {
        super(builder);
        this.unacknowledgedMessageIds = builder.unacknowledgedMessageIds != null
            ? Collections.unmodifiableList(builder.unacknowledgedMessageIds)
            : Collections.emptyList();
    }

    public List<String> getUnacknowledgedMessageIds() {
        return unacknowledgedMessageIds;
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        private List<String> unacknowledgedMessageIds;

        public Builder() {
            code(ErrorCode.STREAM_BROKEN);
            category(ErrorCategory.TRANSIENT);
            retryable(true);
        }

        public Builder unacknowledgedMessageIds(List<String> ids) {
            this.unacknowledgedMessageIds = ids;
            return this;
        }

        @Override
        public StreamBrokenException build() {
            return new StreamBrokenException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
