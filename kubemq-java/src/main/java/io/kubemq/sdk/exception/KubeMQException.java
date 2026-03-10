package io.kubemq.sdk.exception;

import java.io.Serializable;
import java.time.Instant;

/**
 * Base exception for all KubeMQ SDK errors.
 * Extends RuntimeException (unchecked) per Java SDK convention.
 * All SDK methods throw subtypes of this class -- never raw RuntimeException or gRPC exceptions.
 */
public class KubeMQException extends RuntimeException implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;
    private final String operation;
    private final String channel;
    private final boolean retryable;
    private final String requestId;

    private final String messageId;
    private final int statusCode;
    private final Instant timestamp;
    private final String serverAddress;

    private final ErrorCategory category;

    /**
     * Full constructor used by builder.
     */
    protected KubeMQException(Builder<?> builder) {
        super(builder.message, builder.cause);
        this.code = builder.code;
        this.operation = builder.operation;
        this.channel = builder.channel;
        this.retryable = builder.retryable;
        this.requestId = builder.requestId;
        this.messageId = builder.messageId;
        this.statusCode = builder.statusCode;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.serverAddress = builder.serverAddress;
        this.category = builder.category;
    }

    public ErrorCode getCode() { return code; }
    public String getOperation() { return operation; }
    public String getChannel() { return channel; }
    public boolean isRetryable() { return retryable; }
    public String getRequestId() { return requestId; }
    public String getMessageId() { return messageId; }
    public int getStatusCode() { return statusCode; }
    public Instant getTimestamp() { return timestamp; }
    public String getServerAddress() { return serverAddress; }
    public ErrorCategory getCategory() { return category; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append("[code=").append(code);
        if (operation != null) sb.append(", operation=").append(operation);
        if (channel != null) sb.append(", channel=").append(channel);
        sb.append(", retryable=").append(retryable);
        if (requestId != null) sb.append(", requestId=").append(requestId);
        if (statusCode >= 0) sb.append(", grpcStatus=").append(statusCode);
        sb.append("]: ").append(getMessage());
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static abstract class Builder<T extends Builder<T>> {
        private ErrorCode code;
        private String message;
        private String operation;
        private String channel;
        private boolean retryable;
        private String requestId;
        private Throwable cause;
        private String messageId;
        private int statusCode = -1;
        private Instant timestamp;
        private String serverAddress;
        private ErrorCategory category;

        public T code(ErrorCode code) { this.code = code; return (T) this; }
        public T message(String message) { this.message = message; return (T) this; }
        public T operation(String operation) { this.operation = operation; return (T) this; }
        public T channel(String channel) { this.channel = channel; return (T) this; }
        public T retryable(boolean retryable) { this.retryable = retryable; return (T) this; }
        public T requestId(String requestId) { this.requestId = requestId; return (T) this; }
        public T cause(Throwable cause) { this.cause = cause; return (T) this; }
        public T messageId(String messageId) { this.messageId = messageId; return (T) this; }
        public T statusCode(int statusCode) { this.statusCode = statusCode; return (T) this; }
        public T timestamp(Instant timestamp) { this.timestamp = timestamp; return (T) this; }
        public T serverAddress(String serverAddress) { this.serverAddress = serverAddress; return (T) this; }
        public T category(ErrorCategory category) { this.category = category; return (T) this; }

        public abstract KubeMQException build();
    }

    public static class DefaultBuilder extends Builder<DefaultBuilder> {
        @Override
        public KubeMQException build() {
            return new KubeMQException(this);
        }
    }

    /**
     * Creates a builder for constructing a generic KubeMQException.
     * Subclasses provide their own {@code builder()} factory method.
     */
    public static DefaultBuilder newBuilder() {
        return new DefaultBuilder();
    }
}
