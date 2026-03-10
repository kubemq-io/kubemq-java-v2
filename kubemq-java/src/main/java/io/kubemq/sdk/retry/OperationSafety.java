package io.kubemq.sdk.retry;

import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.KubeMQException;

/**
 * Classifies operations by retry safety per GS REQ-ERR-3.
 */
public enum OperationSafety {
    /** Safe to retry on any retryable error. */
    SAFE,
    /** NOT safe to retry on ambiguous failures (DEADLINE_EXCEEDED).
     *  Only retry on errors that prove server did NOT receive. */
    UNSAFE_ON_AMBIGUOUS;

    /**
     * Returns whether this operation type is safe to retry for the given error.
     */
    public boolean canRetry(KubeMQException ex) {
        if (this == SAFE) {
            return ex.isRetryable();
        }
        if (ex.getCode() == ErrorCode.DEADLINE_EXCEEDED) {
            return false;
        }
        return ex.isRetryable();
    }
}
