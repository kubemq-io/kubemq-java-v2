package io.kubemq.sdk.exception;

/**
 * Classification of KubeMQ SDK errors. Maps to gRPC status codes per GS table.
 */
public enum ErrorCategory {
    /** Transient server issue. Auto-retry with backoff. */
    TRANSIENT(true),
    /** Operation timed out. Auto-retry with caution. */
    TIMEOUT(true),
    /** Rate limited by server. Auto-retry with extended backoff. */
    THROTTLING(true),
    /** Invalid credentials. Do not retry; refresh credentials. */
    AUTHENTICATION(false),
    /** Insufficient permissions. Do not retry; fix permissions. */
    AUTHORIZATION(false),
    /** Invalid input or precondition. Do not retry; fix input. */
    VALIDATION(false),
    /** Resource does not exist. Do not retry. */
    NOT_FOUND(false),
    /** Unrecoverable server error. Do not retry. */
    FATAL(false),
    /** Operation cancelled by caller. Do not retry. */
    CANCELLATION(false),
    /** SDK buffer full. Wait for reconnection or increase buffer. */
    BACKPRESSURE(false);

    private final boolean defaultRetryable;

    ErrorCategory(boolean defaultRetryable) {
        this.defaultRetryable = defaultRetryable;
    }

    /**
     * Returns whether errors in this category are retryable by default.
     */
    public boolean isDefaultRetryable() {
        return defaultRetryable;
    }
}
