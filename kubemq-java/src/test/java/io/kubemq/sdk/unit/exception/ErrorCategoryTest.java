package io.kubemq.sdk.unit.exception;

import io.kubemq.sdk.exception.ErrorCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ErrorCategory enum.
 */
class ErrorCategoryTest {

    @Test
    void transient_isRetryableByDefault() {
        assertTrue(ErrorCategory.TRANSIENT.isDefaultRetryable());
    }

    @Test
    void timeout_isRetryableByDefault() {
        assertTrue(ErrorCategory.TIMEOUT.isDefaultRetryable());
    }

    @Test
    void throttling_isRetryableByDefault() {
        assertTrue(ErrorCategory.THROTTLING.isDefaultRetryable());
    }

    @Test
    void authentication_isNotRetryableByDefault() {
        assertFalse(ErrorCategory.AUTHENTICATION.isDefaultRetryable());
    }

    @Test
    void authorization_isNotRetryableByDefault() {
        assertFalse(ErrorCategory.AUTHORIZATION.isDefaultRetryable());
    }

    @Test
    void validation_isNotRetryableByDefault() {
        assertFalse(ErrorCategory.VALIDATION.isDefaultRetryable());
    }

    @Test
    void notFound_isNotRetryableByDefault() {
        assertFalse(ErrorCategory.NOT_FOUND.isDefaultRetryable());
    }

    @Test
    void fatal_isNotRetryableByDefault() {
        assertFalse(ErrorCategory.FATAL.isDefaultRetryable());
    }

    @Test
    void cancellation_isNotRetryableByDefault() {
        assertFalse(ErrorCategory.CANCELLATION.isDefaultRetryable());
    }

    @Test
    void backpressure_isNotRetryableByDefault() {
        assertFalse(ErrorCategory.BACKPRESSURE.isDefaultRetryable());
    }

    @Test
    void allValues_exist() {
        assertEquals(10, ErrorCategory.values().length);
    }
}
