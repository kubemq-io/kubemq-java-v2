package io.kubemq.sdk.unit.exception;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.exception.ErrorCategory;
import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.GRPCException;
import io.kubemq.sdk.exception.KubeMQException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Additional coverage tests for GRPCException constructors. */
@SuppressWarnings("deprecation")
class GRPCExceptionCoverageTest {

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("no-arg constructor sets UNKNOWN_ERROR code and TRANSIENT category")
    void noArgConstructor_setsCodeAndCategory() {
      GRPCException ex = new GRPCException();

      assertEquals(ErrorCode.UNKNOWN_ERROR, ex.getCode());
      assertEquals(ErrorCategory.TRANSIENT, ex.getCategory());
      assertTrue(ex.isRetryable());
      assertNull(ex.getMessage());
      assertNull(ex.getCause());
    }

    @Test
    @DisplayName("message constructor sets message and retains defaults")
    void messageConstructor_setsMessage() {
      GRPCException ex = new GRPCException("gRPC call failed");

      assertEquals("gRPC call failed", ex.getMessage());
      assertEquals(ErrorCode.UNKNOWN_ERROR, ex.getCode());
      assertEquals(ErrorCategory.TRANSIENT, ex.getCategory());
      assertTrue(ex.isRetryable());
      assertNull(ex.getCause());
    }

    @Test
    @DisplayName("cause constructor with non-null cause sets message and cause")
    void causeConstructor_nonNull_setsCause() {
      RuntimeException cause = new RuntimeException("underlying error");
      GRPCException ex = new GRPCException(cause);

      assertEquals("underlying error", ex.getMessage());
      assertSame(cause, ex.getCause());
      assertEquals(ErrorCode.UNKNOWN_ERROR, ex.getCode());
      assertTrue(ex.isRetryable());
    }

    @Test
    @DisplayName("cause constructor with null cause sets null message")
    void causeConstructor_null_setsNullMessage() {
      GRPCException ex = new GRPCException((Throwable) null);

      assertNull(ex.getMessage());
      assertNull(ex.getCause());
      assertEquals(ErrorCode.UNKNOWN_ERROR, ex.getCode());
    }

    @Test
    @DisplayName("message-and-cause constructor sets both")
    void messageAndCauseConstructor_setsBoth() {
      RuntimeException cause = new RuntimeException("root");
      GRPCException ex = new GRPCException("Custom message", cause);

      assertEquals("Custom message", ex.getMessage());
      assertSame(cause, ex.getCause());
      assertEquals(ErrorCode.UNKNOWN_ERROR, ex.getCode());
      assertEquals(ErrorCategory.TRANSIENT, ex.getCategory());
      assertTrue(ex.isRetryable());
    }

    @Test
    @DisplayName("message-and-cause constructor with null cause")
    void messageAndCauseConstructor_nullCause() {
      GRPCException ex = new GRPCException("message only", null);

      assertEquals("message only", ex.getMessage());
      assertNull(ex.getCause());
      assertEquals(ErrorCode.UNKNOWN_ERROR, ex.getCode());
    }

    @Test
    @DisplayName("message-and-cause constructor with null message")
    void messageAndCauseConstructor_nullMessage() {
      RuntimeException cause = new RuntimeException("cause");
      GRPCException ex = new GRPCException(null, cause);

      assertNull(ex.getMessage());
      assertSame(cause, ex.getCause());
    }
  }

  @Nested
  @DisplayName("Inheritance Tests")
  class InheritanceTests {

    @Test
    @DisplayName("GRPCException extends KubeMQException")
    void extendsKubeMQException() {
      GRPCException ex = new GRPCException();

      assertInstanceOf(KubeMQException.class, ex);
      assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    @DisplayName("GRPCException can be caught as KubeMQException")
    void canBeCaughtAsKubeMQException() {
      assertThrows(
          KubeMQException.class,
          () -> {
            throw new GRPCException("test");
          });
    }

    @Test
    @DisplayName("GRPCException toString includes class name")
    void toString_includesClassName() {
      GRPCException ex = new GRPCException("test error");
      String str = ex.toString();

      assertTrue(str.contains("GRPCException"));
    }
  }
}
