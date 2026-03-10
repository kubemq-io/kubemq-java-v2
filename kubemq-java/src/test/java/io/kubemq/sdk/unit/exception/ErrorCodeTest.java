package io.kubemq.sdk.unit.exception;

import io.kubemq.sdk.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ErrorCode enum — verifies all required codes exist.
 */
class ErrorCodeTest {

    @Test
    void allConnectionCodes_exist() {
        assertNotNull(ErrorCode.CONNECTION_FAILED);
        assertNotNull(ErrorCode.CONNECTION_TIMEOUT);
        assertNotNull(ErrorCode.CONNECTION_CLOSED);
    }

    @Test
    void allAuthCodes_exist() {
        assertNotNull(ErrorCode.AUTHENTICATION_FAILED);
        assertNotNull(ErrorCode.AUTHORIZATION_DENIED);
    }

    @Test
    void allTimeoutCodes_exist() {
        assertNotNull(ErrorCode.OPERATION_TIMEOUT);
        assertNotNull(ErrorCode.DEADLINE_EXCEEDED);
    }

    @Test
    void allValidationCodes_exist() {
        assertNotNull(ErrorCode.INVALID_ARGUMENT);
        assertNotNull(ErrorCode.FAILED_PRECONDITION);
        assertNotNull(ErrorCode.ALREADY_EXISTS);
        assertNotNull(ErrorCode.OUT_OF_RANGE);
    }

    @Test
    void allServerCodes_exist() {
        assertNotNull(ErrorCode.SERVER_INTERNAL);
        assertNotNull(ErrorCode.SERVER_UNIMPLEMENTED);
        assertNotNull(ErrorCode.DATA_LOSS);
        assertNotNull(ErrorCode.UNKNOWN_ERROR);
    }

    @Test
    void allTransientCodes_exist() {
        assertNotNull(ErrorCode.UNAVAILABLE);
        assertNotNull(ErrorCode.ABORTED);
    }

    @Test
    void allThrottlingCodes_exist() {
        assertNotNull(ErrorCode.RESOURCE_EXHAUSTED);
        assertNotNull(ErrorCode.BUFFER_FULL);
        assertNotNull(ErrorCode.RETRY_THROTTLED);
    }

    @Test
    void allCancellationCodes_exist() {
        assertNotNull(ErrorCode.CANCELLED_BY_CLIENT);
        assertNotNull(ErrorCode.CANCELLED_BY_SERVER);
    }

    @Test
    void otherCodes_exist() {
        assertNotNull(ErrorCode.NOT_FOUND);
        assertNotNull(ErrorCode.STREAM_BROKEN);
        assertNotNull(ErrorCode.HANDLER_ERROR);
        assertNotNull(ErrorCode.PARTIAL_FAILURE);
    }

    @Test
    void totalCodeCount() {
        assertEquals(27, ErrorCode.values().length);
    }
}
