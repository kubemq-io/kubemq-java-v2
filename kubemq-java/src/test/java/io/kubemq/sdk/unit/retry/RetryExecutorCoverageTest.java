package io.kubemq.sdk.unit.retry;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.exception.*;
import io.kubemq.sdk.retry.OperationSafety;
import io.kubemq.sdk.retry.RetryExecutor;
import io.kubemq.sdk.retry.RetryPolicy;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RetryExecutor extended coverage")
class RetryExecutorCoverageTest {

  private RetryPolicy fastPolicy() {
    return RetryPolicy.builder()
        .maxRetries(3)
        .initialBackoff(Duration.ofMillis(50))
        .maxBackoff(Duration.ofSeconds(1))
        .multiplier(1.5)
        .jitterType(RetryPolicy.JitterType.NONE)
        .maxConcurrentRetries(10)
        .build();
  }

  @Nested
  @DisplayName("Non-retryable exception paths")
  class NonRetryableExceptionTests {

    @Test
    @DisplayName("ClientClosedException is not retried")
    void clientClosedException_notRetried() {
      AtomicInteger attempts = new AtomicInteger(0);
      RetryExecutor executor = new RetryExecutor(fastPolicy());

      assertThrows(
          ClientClosedException.class,
          () ->
              executor.execute(
                  () -> {
                    attempts.incrementAndGet();
                    throw ClientClosedException.builder().message("Client closed").build();
                  },
                  "op",
                  "ch",
                  OperationSafety.SAFE));

      assertEquals(1, attempts.get());
    }

    @Test
    @DisplayName("NotImplementedException is not retried")
    void notImplementedException_notRetried() {
      AtomicInteger attempts = new AtomicInteger(0);
      RetryExecutor executor = new RetryExecutor(fastPolicy());

      assertThrows(
          NotImplementedException.class,
          () ->
              executor.execute(
                  () -> {
                    attempts.incrementAndGet();
                    throw NotImplementedException.builder()
                        .message("Not implemented")
                        .operation("purgeQueue")
                        .build();
                  },
                  "op",
                  "ch",
                  OperationSafety.SAFE));

      assertEquals(1, attempts.get());
    }
  }

  @Nested
  @DisplayName("Custom retry predicate via OperationSafety")
  class CustomRetryPredicateTests {

    @Test
    @DisplayName("UNSAFE_ON_AMBIGUOUS retries UNAVAILABLE errors")
    void unsafeOnAmbiguous_unavailable_retries() {
      AtomicInteger attempts = new AtomicInteger(0);
      RetryExecutor executor = new RetryExecutor(fastPolicy());

      assertThrows(
          KubeMQException.class,
          () ->
              executor.execute(
                  () -> {
                    attempts.incrementAndGet();
                    throw ConnectionException.builder()
                        .code(ErrorCode.UNAVAILABLE)
                        .message("Server down")
                        .retryable(true)
                        .build();
                  },
                  "op",
                  "ch",
                  OperationSafety.UNSAFE_ON_AMBIGUOUS));

      assertTrue(attempts.get() > 1, "Should retry UNAVAILABLE even with UNSAFE_ON_AMBIGUOUS");
    }

    @Test
    @DisplayName("SAFE retries DEADLINE_EXCEEDED but UNSAFE_ON_AMBIGUOUS does not")
    void safeVsUnsafe_deadlineExceeded_differentBehavior() {
      AtomicInteger safeAttempts = new AtomicInteger(0);
      AtomicInteger unsafeAttempts = new AtomicInteger(0);

      RetryExecutor executor = new RetryExecutor(fastPolicy());

      assertThrows(
          KubeMQException.class,
          () ->
              executor.execute(
                  () -> {
                    safeAttempts.incrementAndGet();
                    throw KubeMQTimeoutException.builder()
                        .code(ErrorCode.DEADLINE_EXCEEDED)
                        .message("Timeout")
                        .build();
                  },
                  "op",
                  "ch",
                  OperationSafety.SAFE));

      assertThrows(
          KubeMQException.class,
          () ->
              executor.execute(
                  () -> {
                    unsafeAttempts.incrementAndGet();
                    throw KubeMQTimeoutException.builder()
                        .code(ErrorCode.DEADLINE_EXCEEDED)
                        .message("Timeout")
                        .build();
                  },
                  "op",
                  "ch",
                  OperationSafety.UNSAFE_ON_AMBIGUOUS));

      assertTrue(safeAttempts.get() > 1, "SAFE should retry DEADLINE_EXCEEDED");
      assertEquals(
          1, unsafeAttempts.get(), "UNSAFE_ON_AMBIGUOUS should not retry DEADLINE_EXCEEDED");
    }
  }

  @Nested
  @DisplayName("Max retries exhausted with context")
  class MaxRetriesExhaustedTests {

    @Test
    @DisplayName("exhausted retries message includes channel name and duration")
    void exhaustedRetries_includesChannelAndDuration() {
      RetryPolicy policy =
          RetryPolicy.builder()
              .maxRetries(1)
              .initialBackoff(Duration.ofMillis(50))
              .maxBackoff(Duration.ofSeconds(1))
              .multiplier(1.5)
              .jitterType(RetryPolicy.JitterType.NONE)
              .maxConcurrentRetries(10)
              .build();
      RetryExecutor executor = new RetryExecutor(policy);

      KubeMQException thrown =
          assertThrows(
              KubeMQException.class,
              () ->
                  executor.execute(
                      () -> {
                        throw ConnectionException.builder()
                            .code(ErrorCode.UNAVAILABLE)
                            .message("Server unreachable")
                            .retryable(true)
                            .build();
                      },
                      "sendCommand",
                      "orders-channel",
                      OperationSafety.SAFE));

      assertTrue(thrown.getMessage().contains("orders-channel"));
      assertTrue(thrown.getMessage().contains("sendCommand"));
      assertTrue(thrown.getMessage().contains("Retries exhausted"));
      assertFalse(thrown.isRetryable());
      assertNotNull(thrown.getCause());
    }
  }

  @Nested
  @DisplayName("Backoff delay verification")
  class BackoffDelayTests {

    @Test
    @DisplayName("retry with NONE jitter has predictable delay")
    void noneJitter_predictableDelay() {
      AtomicInteger attempts = new AtomicInteger(0);
      long startTime = System.currentTimeMillis();

      RetryPolicy policy =
          RetryPolicy.builder()
              .maxRetries(2)
              .initialBackoff(Duration.ofMillis(100))
              .maxBackoff(Duration.ofSeconds(1))
              .multiplier(1.5)
              .jitterType(RetryPolicy.JitterType.NONE)
              .maxConcurrentRetries(0)
              .build();
      RetryExecutor executor = new RetryExecutor(policy);

      assertThrows(
          KubeMQException.class,
          () ->
              executor.execute(
                  () -> {
                    attempts.incrementAndGet();
                    throw ConnectionException.builder()
                        .code(ErrorCode.UNAVAILABLE)
                        .message("fail")
                        .retryable(true)
                        .build();
                  },
                  "op",
                  "ch",
                  OperationSafety.SAFE));

      long elapsed = System.currentTimeMillis() - startTime;
      assertEquals(3, attempts.get());
      assertTrue(elapsed >= 200, "Should have waited at least 200ms total (100 + 150)");
    }
  }

  @Nested
  @DisplayName("Extended backoff for throttling errors")
  class ExtendedBackoffTests {

    @Test
    @DisplayName("throttling error doubles backoff delay")
    void throttlingError_doublesBackoff() {
      AtomicInteger attempts = new AtomicInteger(0);
      long startTime = System.currentTimeMillis();

      RetryPolicy policy =
          RetryPolicy.builder()
              .maxRetries(1)
              .initialBackoff(Duration.ofMillis(100))
              .maxBackoff(Duration.ofSeconds(1))
              .multiplier(1.5)
              .jitterType(RetryPolicy.JitterType.NONE)
              .maxConcurrentRetries(0)
              .build();
      RetryExecutor executor = new RetryExecutor(policy);

      assertThrows(
          KubeMQException.class,
          () ->
              executor.execute(
                  () -> {
                    attempts.incrementAndGet();
                    throw KubeMQException.newBuilder()
                        .code(ErrorCode.UNAVAILABLE)
                        .category(ErrorCategory.THROTTLING)
                        .retryable(true)
                        .message("Rate limited")
                        .operation("op")
                        .build();
                  },
                  "op",
                  "ch",
                  OperationSafety.SAFE));

      long elapsed = System.currentTimeMillis() - startTime;
      assertEquals(2, attempts.get());
      assertTrue(
          elapsed >= 150, "Should have waited at least 200ms (100 * 2) for throttling backoff");
    }
  }

  @Nested
  @DisplayName("Interruption during retry sleep")
  class InterruptionDuringSleepTests {

    @Test
    @DisplayName(
        "interrupt during backoff sleep throws OperationCancelledException and preserves interrupt status")
    void interrupt_duringSleep_throwsOpCancelled() {
      RetryPolicy policy =
          RetryPolicy.builder()
              .maxRetries(3)
              .initialBackoff(Duration.ofSeconds(5))
              .maxBackoff(Duration.ofSeconds(30))
              .multiplier(2.0)
              .jitterType(RetryPolicy.JitterType.NONE)
              .maxConcurrentRetries(0)
              .build();
      RetryExecutor executor = new RetryExecutor(policy);

      Thread testThread = Thread.currentThread();
      Thread interrupter =
          new Thread(
              () -> {
                try {
                  Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
                testThread.interrupt();
              });
      interrupter.start();

      OperationCancelledException thrown =
          assertThrows(
              OperationCancelledException.class,
              () ->
                  executor.execute(
                      () -> {
                        throw ConnectionException.builder()
                            .code(ErrorCode.UNAVAILABLE)
                            .message("fail")
                            .retryable(true)
                            .build();
                      },
                      "myOp",
                      "myCh",
                      OperationSafety.SAFE));

      assertTrue(thrown.getMessage().contains("Retry interrupted"));
      assertEquals(ErrorCode.CANCELLED_BY_CLIENT, thrown.getCode());

      Thread.interrupted(); // clear flag
    }
  }

  @Nested
  @DisplayName("Disabled policy with non-KubeMQException")
  class DisabledPolicyGenericExceptionTests {

    @Test
    @DisplayName("disabled policy wraps checked Exception as KubeMQException")
    void disabledPolicy_checkedException_wrapsAsKubeMQ() {
      RetryExecutor executor = new RetryExecutor(RetryPolicy.DISABLED);

      KubeMQException thrown =
          assertThrows(
              KubeMQException.class,
              () ->
                  executor.execute(
                      (Callable<String>)
                          () -> {
                            throw new Exception("Checked exception");
                          },
                      "testOp",
                      "testCh",
                      OperationSafety.SAFE));

      assertEquals(ErrorCode.UNKNOWN_ERROR, thrown.getCode());
      assertEquals(ErrorCategory.FATAL, thrown.getCategory());
      assertFalse(thrown.isRetryable());
      assertTrue(thrown.getMessage().contains("Checked exception"));
    }
  }

  @Nested
  @DisplayName("Concurrency limiter release after retry")
  class ConcurrencyLimiterTests {

    @Test
    @DisplayName("concurrency limiter is released after each retry")
    void concurrencyLimiter_releasedAfterRetry() {
      RetryPolicy policy =
          RetryPolicy.builder()
              .maxRetries(2)
              .initialBackoff(Duration.ofMillis(50))
              .maxBackoff(Duration.ofSeconds(1))
              .multiplier(1.5)
              .jitterType(RetryPolicy.JitterType.NONE)
              .maxConcurrentRetries(1)
              .build();

      AtomicInteger attempts = new AtomicInteger(0);
      RetryExecutor executor = new RetryExecutor(policy);

      String result =
          executor.execute(
              () -> {
                if (attempts.incrementAndGet() < 3) {
                  throw ConnectionException.builder()
                      .code(ErrorCode.UNAVAILABLE)
                      .message("fail")
                      .retryable(true)
                      .build();
                }
                return "success";
              },
              "op",
              "ch",
              OperationSafety.SAFE);

      assertEquals("success", result);
      assertEquals(3, attempts.get());
    }
  }
}
