package io.kubemq.sdk.unit.apicompleteness;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.exception.ErrorCategory;
import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.NotImplementedException;
import io.kubemq.sdk.queues.QueuesClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for NotImplementedException and purgeQueue stub (REQ-API-3). */
class NotImplementedExceptionTest {

  private QueuesClient client;

  @BeforeEach
  void setup() {
    client = QueuesClient.builder().address("localhost:50000").clientId("test-client").build();
  }

  @AfterEach
  void teardown() {
    if (client != null) client.close();
  }

  @Test
  @DisplayName("API-9: NotImplementedException has correct error code")
  void notImplementedException_hasCorrectErrorCode() {
    NotImplementedException ex =
        NotImplementedException.builder().message("test feature").operation("testOp").build();

    assertEquals(ErrorCode.FEATURE_NOT_IMPLEMENTED, ex.getCode());
    assertEquals(ErrorCategory.FATAL, ex.getCategory());
    assertFalse(ex.isRetryable());
  }

  @Test
  @DisplayName("API-10: NotImplementedException carries operation context")
  void notImplementedException_carriesOperationContext() {
    NotImplementedException ex =
        NotImplementedException.builder()
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
  void purgeQueue_isImplemented() {
    // purgeQueue is not implemented in this SDK version; it always throws NotImplementedException
    assertThrows(NotImplementedException.class, () -> client.purgeQueue(null));
    assertThrows(NotImplementedException.class, () -> client.purgeQueue(""));
  }

  @Test
  @DisplayName("API-12: purgeQueue throws NotImplementedException for any channel")
  void purgeQueue_validatesChannel() {
    assertThrows(NotImplementedException.class, () -> client.purgeQueue(""));
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
    NotImplementedException ex = NotImplementedException.builder().message("test").build();

    assertInstanceOf(io.kubemq.sdk.exception.KubeMQException.class, ex);
    assertInstanceOf(RuntimeException.class, ex);
  }
}
