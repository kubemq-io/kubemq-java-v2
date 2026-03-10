package io.kubemq.sdk.unit.exception;

import io.kubemq.sdk.exception.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for REQ-ERR-2: Error classification via ErrorClassifier.
 */
class ErrorClassifierTest {

    @Test
    void shouldRetry_returnsTrueForRetryableException() {
        KubeMQException ex = ConnectionException.builder()
            .message("Unavailable")
            .retryable(true)
            .build();

        assertTrue(ErrorClassifier.shouldRetry(ex));
    }

    @Test
    void shouldRetry_returnsFalseForNonRetryableException() {
        KubeMQException ex = AuthenticationException.builder()
            .message("Invalid token")
            .build();

        assertFalse(ErrorClassifier.shouldRetry(ex));
    }

    @Test
    void shouldUseExtendedBackoff_trueForThrottling() {
        KubeMQException ex = ThrottlingException.builder()
            .message("Rate limited")
            .build();

        assertTrue(ErrorClassifier.shouldUseExtendedBackoff(ex));
    }

    @Test
    void shouldUseExtendedBackoff_falseForOtherCategories() {
        KubeMQException ex = ConnectionException.builder()
            .message("Unavailable")
            .build();

        assertFalse(ErrorClassifier.shouldUseExtendedBackoff(ex));
    }

    @Test
    void shouldUseExtendedBackoff_falseForNullCategory() {
        KubeMQException ex = KubeMQException.newBuilder()
            .message("test")
            .build();

        assertFalse(ErrorClassifier.shouldUseExtendedBackoff(ex));
    }

    @Test
    void toOtelErrorType_mapsTransientToLowercase() {
        KubeMQException ex = ConnectionException.builder()
            .message("test")
            .build();

        assertEquals("transient", ErrorClassifier.toOtelErrorType(ex));
    }

    @Test
    void toOtelErrorType_mapsAuthenticationToLowercase() {
        KubeMQException ex = AuthenticationException.builder()
            .message("test")
            .build();

        assertEquals("authentication", ErrorClassifier.toOtelErrorType(ex));
    }

    @Test
    void toOtelErrorType_mapsTimeoutToLowercase() {
        KubeMQException ex = KubeMQTimeoutException.builder()
            .message("test")
            .build();

        assertEquals("timeout", ErrorClassifier.toOtelErrorType(ex));
    }

    @Test
    void toOtelErrorType_mapsThrottlingToLowercase() {
        KubeMQException ex = ThrottlingException.builder()
            .message("test")
            .build();

        assertEquals("throttling", ErrorClassifier.toOtelErrorType(ex));
    }

    @Test
    void toOtelErrorType_returnsUnknownForNullCategory() {
        KubeMQException ex = KubeMQException.newBuilder()
            .message("test")
            .build();

        assertEquals("unknown", ErrorClassifier.toOtelErrorType(ex));
    }

    @Test
    void toOtelErrorType_allCategoriesMapped() {
        for (ErrorCategory cat : ErrorCategory.values()) {
            KubeMQException ex = KubeMQException.newBuilder()
                .message("test")
                .category(cat)
                .build();

            assertEquals(cat.name().toLowerCase(), ErrorClassifier.toOtelErrorType(ex));
        }
    }
}
