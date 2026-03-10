package io.kubemq.sdk.unit.retry;

import io.kubemq.sdk.exception.*;
import io.kubemq.sdk.retry.OperationSafety;
import io.kubemq.sdk.retry.RetryPolicy;
import io.kubemq.sdk.retry.RetryExecutor;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for REQ-ERR-3 and REQ-ERR-7: RetryExecutor with retry and throttling.
 */
class RetryExecutorTest {

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
    class SuccessfulExecutionTests {

        @Test
        void execute_successOnFirstAttempt() {
            RetryExecutor executor = new RetryExecutor(fastPolicy());

            String result = executor.execute(
                () -> "hello", "testOp", "ch1", OperationSafety.SAFE);

            assertEquals("hello", result);
        }

        @Test
        void execute_successAfterTransientFailures() {
            AtomicInteger attempts = new AtomicInteger(0);
            RetryExecutor executor = new RetryExecutor(fastPolicy());

            String result = executor.execute(
                () -> {
                    if (attempts.incrementAndGet() < 3) {
                        throw ConnectionException.builder()
                            .message("Unavailable")
                            .retryable(true)
                            .build();
                    }
                    return "recovered";
                },
                "testOp", "ch1", OperationSafety.SAFE);

            assertEquals("recovered", result);
            assertEquals(3, attempts.get());
        }
    }

    @Nested
    class NonRetryableErrorTests {

        @Test
        void execute_nonRetryable_throwsImmediately() {
            AtomicInteger attempts = new AtomicInteger(0);
            RetryExecutor executor = new RetryExecutor(fastPolicy());

            KubeMQException thrown = assertThrows(KubeMQException.class, () ->
                executor.execute(
                    () -> {
                        attempts.incrementAndGet();
                        throw AuthenticationException.builder()
                            .message("Invalid token")
                            .build();
                    },
                    "testOp", "ch1", OperationSafety.SAFE));

            assertEquals(1, attempts.get());
            assertInstanceOf(AuthenticationException.class, thrown);
        }

        @Test
        void execute_validationError_notRetried() {
            AtomicInteger attempts = new AtomicInteger(0);
            RetryExecutor executor = new RetryExecutor(fastPolicy());

            assertThrows(ValidationException.class, () ->
                executor.execute(
                    () -> {
                        attempts.incrementAndGet();
                        throw ValidationException.builder()
                            .message("Bad input")
                            .build();
                    },
                    "testOp", "ch1", OperationSafety.SAFE));

            assertEquals(1, attempts.get());
        }
    }

    @Nested
    class RetryExhaustionTests {

        @Test
        void execute_allRetriesExhausted_throwsWithContext() {
            RetryExecutor executor = new RetryExecutor(fastPolicy());

            KubeMQException thrown = assertThrows(KubeMQException.class, () ->
                executor.execute(
                    () -> {
                        throw ConnectionException.builder()
                            .code(ErrorCode.UNAVAILABLE)
                            .message("Server down")
                            .retryable(true)
                            .build();
                    },
                    "sendEvent", "orders", OperationSafety.SAFE));

            assertTrue(thrown.getMessage().contains("Retries exhausted"));
            assertTrue(thrown.getMessage().contains("sendEvent"));
            assertFalse(thrown.isRetryable());
        }

        @Test
        void execute_maxRetriesAttempted() {
            AtomicInteger attempts = new AtomicInteger(0);
            RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(2)
                .initialBackoff(Duration.ofMillis(50))
                .maxBackoff(Duration.ofSeconds(1))
                .multiplier(1.5)
                .jitterType(RetryPolicy.JitterType.NONE)
                .maxConcurrentRetries(10)
                .build();
            RetryExecutor executor = new RetryExecutor(policy);

            assertThrows(KubeMQException.class, () ->
                executor.execute(
                    () -> {
                        attempts.incrementAndGet();
                        throw ConnectionException.builder()
                            .message("fail")
                            .retryable(true)
                            .build();
                    },
                    "op", "ch", OperationSafety.SAFE));

            assertEquals(3, attempts.get()); // 1 initial + 2 retries
        }
    }

    @Nested
    class DisabledRetryTests {

        @Test
        void execute_disabledPolicy_noRetries() {
            AtomicInteger attempts = new AtomicInteger(0);
            RetryExecutor executor = new RetryExecutor(RetryPolicy.DISABLED);

            assertThrows(KubeMQException.class, () ->
                executor.execute(
                    () -> {
                        attempts.incrementAndGet();
                        throw ConnectionException.builder()
                            .message("fail")
                            .retryable(true)
                            .build();
                    },
                    "op", "ch", OperationSafety.SAFE));

            assertEquals(1, attempts.get());
        }
    }

    @Nested
    class OperationSafetyTests {

        @Test
        void unsafeOnAmbiguous_deadlineExceeded_notRetried() {
            AtomicInteger attempts = new AtomicInteger(0);
            RetryExecutor executor = new RetryExecutor(fastPolicy());

            assertThrows(KubeMQTimeoutException.class, () ->
                executor.execute(
                    () -> {
                        attempts.incrementAndGet();
                        throw KubeMQTimeoutException.builder()
                            .code(ErrorCode.DEADLINE_EXCEEDED)
                            .message("Timeout")
                            .build();
                    },
                    "sendCommand", "ch", OperationSafety.UNSAFE_ON_AMBIGUOUS));

            assertEquals(1, attempts.get());
        }

        @Test
        void safe_deadlineExceeded_retried() {
            AtomicInteger attempts = new AtomicInteger(0);
            RetryExecutor executor = new RetryExecutor(fastPolicy());

            assertThrows(KubeMQException.class, () ->
                executor.execute(
                    () -> {
                        attempts.incrementAndGet();
                        throw KubeMQTimeoutException.builder()
                            .code(ErrorCode.DEADLINE_EXCEEDED)
                            .message("Timeout")
                            .build();
                    },
                    "subscribe", "ch", OperationSafety.SAFE));

            assertEquals(4, attempts.get()); // 1 initial + 3 retries
        }
    }

    @Nested
    class ConnectionReadinessTests {

        @Test
        void connectionNotReady_stopsRetrying() {
            AtomicInteger attempts = new AtomicInteger(0);
            RetryExecutor executor = new RetryExecutor(
                fastPolicy(), () -> false);

            KubeMQException thrown = assertThrows(KubeMQException.class, () ->
                executor.execute(
                    () -> {
                        attempts.incrementAndGet();
                        throw ConnectionException.builder()
                            .message("fail")
                            .retryable(true)
                            .build();
                    },
                    "op", "ch", OperationSafety.SAFE));

            assertEquals(1, attempts.get());
            assertInstanceOf(ConnectionException.class, thrown);
        }

        @Test
        void connectionReady_allowsRetry() {
            AtomicInteger attempts = new AtomicInteger(0);
            RetryExecutor executor = new RetryExecutor(
                fastPolicy(), () -> true);

            assertThrows(KubeMQException.class, () ->
                executor.execute(
                    () -> {
                        attempts.incrementAndGet();
                        throw ConnectionException.builder()
                            .message("fail")
                            .retryable(true)
                            .build();
                    },
                    "op", "ch", OperationSafety.SAFE));

            assertTrue(attempts.get() > 1);
        }

        @Test
        void nullConnectionSupplier_allowsRetry() {
            AtomicInteger attempts = new AtomicInteger(0);
            RetryExecutor executor = new RetryExecutor(fastPolicy(), null);

            assertThrows(KubeMQException.class, () ->
                executor.execute(
                    () -> {
                        attempts.incrementAndGet();
                        throw ConnectionException.builder()
                            .message("fail")
                            .retryable(true)
                            .build();
                    },
                    "op", "ch", OperationSafety.SAFE));

            assertTrue(attempts.get() > 1);
        }
    }

    @Nested
    class UnknownErrorHandlingTests {

        @Test
        void unknownError_retriedOnceOnly() {
            AtomicInteger attempts = new AtomicInteger(0);
            RetryExecutor executor = new RetryExecutor(fastPolicy());

            assertThrows(KubeMQException.class, () ->
                executor.execute(
                    () -> {
                        attempts.incrementAndGet();
                        throw ConnectionException.builder()
                            .code(ErrorCode.UNKNOWN_ERROR)
                            .message("Unknown")
                            .retryable(true)
                            .build();
                    },
                    "op", "ch", OperationSafety.SAFE));

            assertEquals(2, attempts.get());
        }
    }

    @Nested
    class NonKubeMQExceptionTests {

        @Test
        void genericException_wrappedAsKubeMQException() {
            RetryExecutor executor = new RetryExecutor(RetryPolicy.DISABLED);

            KubeMQException thrown = assertThrows(KubeMQException.class, () ->
                executor.execute(
                    (Callable<String>) () -> {
                        throw new IllegalStateException("unexpected");
                    },
                    "op", "ch", OperationSafety.SAFE));

            assertEquals(ErrorCode.UNKNOWN_ERROR, thrown.getCode());
            assertEquals(ErrorCategory.FATAL, thrown.getCategory());
            assertFalse(thrown.isRetryable());
            assertInstanceOf(IllegalStateException.class, thrown.getCause());
        }
    }

    @Nested
    class RetryThrottlingTests {

        @Test
        void throttling_whenConcurrencyLimitIsOne_retriesStillWork() {
            RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(2)
                .initialBackoff(Duration.ofMillis(50))
                .maxBackoff(Duration.ofSeconds(1))
                .multiplier(1.5)
                .jitterType(RetryPolicy.JitterType.NONE)
                .maxConcurrentRetries(1)
                .build();

            AtomicInteger attempts = new AtomicInteger(0);
            RetryExecutor executor = new RetryExecutor(policy);

            String result = executor.execute(
                () -> {
                    if (attempts.incrementAndGet() < 3) {
                        throw ConnectionException.builder()
                            .message("fail")
                            .retryable(true)
                            .build();
                    }
                    return "ok";
                },
                "op", "ch", OperationSafety.SAFE);

            assertEquals("ok", result);
            assertEquals(3, attempts.get());
        }

        @Test
        void throttling_zeroConcurrency_neverThrottles() {
            RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(2)
                .initialBackoff(Duration.ofMillis(50))
                .maxBackoff(Duration.ofSeconds(1))
                .multiplier(1.5)
                .jitterType(RetryPolicy.JitterType.NONE)
                .maxConcurrentRetries(0)
                .build();

            AtomicInteger attempts = new AtomicInteger(0);
            RetryExecutor executor = new RetryExecutor(policy);

            assertThrows(KubeMQException.class, () ->
                executor.execute(
                    () -> {
                        attempts.incrementAndGet();
                        throw ConnectionException.builder()
                            .message("fail")
                            .retryable(true)
                            .build();
                    },
                    "op", "ch", OperationSafety.SAFE));

            assertEquals(3, attempts.get()); // 1 initial + 2 retries, no throttling
        }
    }

    @Nested
    class InterruptionTests {

        @Test
        void interruptedDuringSleep_throwsOperationCancelledException() {
            RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(3)
                .initialBackoff(Duration.ofSeconds(5))
                .maxBackoff(Duration.ofSeconds(30))
                .multiplier(2.0)
                .jitterType(RetryPolicy.JitterType.NONE)
                .maxConcurrentRetries(10)
                .build();
            RetryExecutor executor = new RetryExecutor(policy);

            Thread testThread = Thread.currentThread();

            Thread interrupter = new Thread(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
                testThread.interrupt();
            });
            interrupter.start();

            assertThrows(OperationCancelledException.class, () ->
                executor.execute(
                    () -> {
                        throw ConnectionException.builder()
                            .message("fail")
                            .retryable(true)
                            .build();
                    },
                    "op", "ch", OperationSafety.SAFE));

            Thread.interrupted(); // clear flag
        }
    }
}
