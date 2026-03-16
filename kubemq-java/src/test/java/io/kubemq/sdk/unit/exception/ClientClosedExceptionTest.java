package io.kubemq.sdk.unit.exception;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.exception.ClientClosedException;
import io.kubemq.sdk.exception.ErrorCategory;
import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.KubeMQException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for ClientClosedException builder and factory method. */
class ClientClosedExceptionTest {

  @Nested
  @DisplayName("Factory Method Tests")
  class FactoryMethodTests {

    @Test
    @DisplayName("create() returns ClientClosedException with default fields")
    void create_returnsExceptionWithDefaults() {
      ClientClosedException ex = ClientClosedException.create();

      assertNotNull(ex);
      assertInstanceOf(ClientClosedException.class, ex);
      assertInstanceOf(KubeMQException.class, ex);
      assertEquals(ErrorCode.CONNECTION_CLOSED, ex.getCode());
      assertEquals(ErrorCategory.FATAL, ex.getCategory());
      assertFalse(ex.isRetryable());
      assertTrue(ex.getMessage().contains("Client has been closed"));
    }

    @Test
    @DisplayName("create() exception can be thrown and caught")
    void create_canBeThrownAndCaught() {
      assertThrows(
          ClientClosedException.class,
          () -> {
            throw ClientClosedException.create();
          });
    }
  }

  @Nested
  @DisplayName("Builder Pattern Tests")
  class BuilderTests {

    @Test
    @DisplayName("builder() with default values produces correct exception")
    void builder_defaultValues_producesCorrectException() {
      ClientClosedException ex = ClientClosedException.builder().build();

      assertEquals(ErrorCode.CONNECTION_CLOSED, ex.getCode());
      assertEquals(ErrorCategory.FATAL, ex.getCategory());
      assertFalse(ex.isRetryable());
      assertNotNull(ex.getMessage());
    }

    @Test
    @DisplayName("builder() with custom message overrides default message")
    void builder_customMessage_overridesDefault() {
      ClientClosedException ex =
          ClientClosedException.builder().message("Custom close message").build();

      assertEquals("Custom close message", ex.getMessage());
      assertEquals(ErrorCode.CONNECTION_CLOSED, ex.getCode());
    }

    @Test
    @DisplayName("builder() with custom code overrides default code")
    void builder_customCode_overridesDefault() {
      ClientClosedException ex =
          ClientClosedException.builder().code(ErrorCode.UNKNOWN_ERROR).build();

      assertEquals(ErrorCode.UNKNOWN_ERROR, ex.getCode());
    }

    @Test
    @DisplayName("builder() with operation sets operation")
    void builder_withOperation_setsOperation() {
      ClientClosedException ex = ClientClosedException.builder().operation("sendEvent").build();

      assertEquals("sendEvent", ex.getOperation());
    }

    @Test
    @DisplayName("builder() with channel sets channel")
    void builder_withChannel_setsChannel() {
      ClientClosedException ex = ClientClosedException.builder().channel("test-channel").build();

      assertEquals("test-channel", ex.getChannel());
    }

    @Test
    @DisplayName("builder() preserves non-retryable default")
    void builder_preservesNonRetryable() {
      ClientClosedException ex = ClientClosedException.builder().build();

      assertFalse(ex.isRetryable());
    }
  }

  @Nested
  @DisplayName("Inheritance Tests")
  class InheritanceTests {

    @Test
    @DisplayName("ClientClosedException extends KubeMQException")
    void extendsKubeMQException() {
      ClientClosedException ex = ClientClosedException.create();

      assertInstanceOf(KubeMQException.class, ex);
      assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    @DisplayName("ClientClosedException has timestamp set")
    void hasTimestampSet() {
      ClientClosedException ex = ClientClosedException.create();

      assertNotNull(ex.getTimestamp());
    }
  }
}
