package io.kubemq.sdk.unit.retry;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.exception.*;
import io.kubemq.sdk.retry.OperationSafety;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for REQ-ERR-3: OperationSafety enum. */
class OperationSafetyTest {

  @Nested
  class SafeOperationTests {

    @Test
    void canRetry_retryableError_returnsTrue() {
      KubeMQException ex =
          ConnectionException.builder().message("Unavailable").retryable(true).build();

      assertTrue(OperationSafety.SAFE.canRetry(ex));
    }

    @Test
    void canRetry_nonRetryableError_returnsFalse() {
      KubeMQException ex = AuthenticationException.builder().message("Denied").build();

      assertFalse(OperationSafety.SAFE.canRetry(ex));
    }

    @Test
    void canRetry_deadlineExceeded_withRetryable_returnsTrue() {
      KubeMQException ex =
          KubeMQTimeoutException.builder()
              .code(ErrorCode.DEADLINE_EXCEEDED)
              .message("Timeout")
              .retryable(true)
              .build();

      assertTrue(OperationSafety.SAFE.canRetry(ex));
    }
  }

  @Nested
  class UnsafeOnAmbiguousTests {

    @Test
    void canRetry_deadlineExceeded_returnsFalse() {
      KubeMQException ex =
          KubeMQTimeoutException.builder()
              .code(ErrorCode.DEADLINE_EXCEEDED)
              .message("Timeout")
              .retryable(true)
              .build();

      assertFalse(OperationSafety.UNSAFE_ON_AMBIGUOUS.canRetry(ex));
    }

    @Test
    void canRetry_retryableNonTimeout_returnsTrue() {
      KubeMQException ex =
          ConnectionException.builder()
              .code(ErrorCode.UNAVAILABLE)
              .message("Server down")
              .retryable(true)
              .build();

      assertTrue(OperationSafety.UNSAFE_ON_AMBIGUOUS.canRetry(ex));
    }

    @Test
    void canRetry_nonRetryable_returnsFalse() {
      KubeMQException ex = ValidationException.builder().message("Bad input").build();

      assertFalse(OperationSafety.UNSAFE_ON_AMBIGUOUS.canRetry(ex));
    }
  }
}
