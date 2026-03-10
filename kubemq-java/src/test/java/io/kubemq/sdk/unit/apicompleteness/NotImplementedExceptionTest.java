package io.kubemq.sdk.unit.apicompleteness;

import io.kubemq.sdk.exception.ErrorCategory;
import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.NotImplementedException;
import io.kubemq.sdk.queues.QueuesClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NotImplementedException and purgeQueue stub (REQ-API-3).
 */
class NotImplementedExceptionTest {

    private QueuesClient client;

    @BeforeEach
    void setup() {
        client = QueuesClient.builder()
                .address("localhost:50000")
                .clientId("test-client")
                .build();
    }

    @AfterEach
    void teardown() {
        if (client != null) client.close();
    }

    @Test
    @DisplayName("API-9: NotImplementedException has correct error code")
    void notImplementedException_hasCorrectErrorCode() {
        NotImplementedException ex = NotImplementedException.builder()
                .message("test feature")
                .operation("testOp")
                .build();

        assertEquals(ErrorCode.FEATURE_NOT_IMPLEMENTED, ex.getCode());
        assertEquals(ErrorCategory.FATAL, ex.getCategory());
        assertFalse(ex.isRetryable());
    }

    @Test
    @DisplayName("API-10: NotImplementedException carries operation context")
    void notImplementedException_carriesOperationContext() {
        NotImplementedException ex = NotImplementedException.builder()
                .message("test feature")
                .operation("purgeQueue")
                .channel("my-channel")
                .build();

        assertEquals("purgeQueue", ex.getOperation());
        assertEquals("my-channel", ex.getChannel());
        assertTrue(ex.getMessage().contains("test feature"));
    }

    @Test
    @DisplayName("API-11: purgeQueue throws NotImplementedException")
    void purgeQueue_throwsNotImplementedException() {
        NotImplementedException ex = assertThrows(
                NotImplementedException.class,
                () -> client.purgeQueue("test-queue")
        );

        assertEquals(ErrorCode.FEATURE_NOT_IMPLEMENTED, ex.getCode());
        assertEquals("purgeQueue", ex.getOperation());
        assertEquals("test-queue", ex.getChannel());
        assertTrue(ex.getMessage().contains("purgeQueue"));
        assertTrue(ex.getMessage().contains("not implemented"));
    }

    @Test
    @DisplayName("API-12: purgeQueue includes channel in error")
    void purgeQueue_includesChannelInError() {
        NotImplementedException ex = assertThrows(
                NotImplementedException.class,
                () -> client.purgeQueue("my-important-queue")
        );

        assertEquals("my-important-queue", ex.getChannel());
    }

    @Test
    @DisplayName("API-13: FEATURE_NOT_IMPLEMENTED exists in ErrorCode enum")
    void featureNotImplemented_existsInErrorCodeEnum() {
        assertNotNull(ErrorCode.FEATURE_NOT_IMPLEMENTED);
        assertDoesNotThrow(() -> ErrorCode.valueOf("FEATURE_NOT_IMPLEMENTED"));
    }

    @Test
    @DisplayName("API-14: NotImplementedException extends KubeMQException")
    void notImplementedException_extendsKubeMQException() {
        NotImplementedException ex = NotImplementedException.builder()
                .message("test")
                .build();

        assertInstanceOf(io.kubemq.sdk.exception.KubeMQException.class, ex);
        assertInstanceOf(RuntimeException.class, ex);
    }
}
