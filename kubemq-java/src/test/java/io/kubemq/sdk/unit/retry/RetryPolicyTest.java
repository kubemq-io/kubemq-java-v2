package io.kubemq.sdk.unit.retry;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.retry.RetryPolicy;
import io.kubemq.sdk.retry.RetryPolicy.JitterType;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/** Unit tests for REQ-ERR-3: RetryPolicy configuration and backoff computation. */
class RetryPolicyTest {

  @Nested
  class DefaultPolicyTests {

    @Test
    void default_hasCorrectValues() {
      RetryPolicy policy = RetryPolicy.DEFAULT;

      assertEquals(3, policy.getMaxRetries());
      assertEquals(Duration.ofMillis(500), policy.getInitialBackoff());
      assertEquals(Duration.ofSeconds(30), policy.getMaxBackoff());
      assertEquals(2.0, policy.getMultiplier());
      assertEquals(JitterType.FULL, policy.getJitterType());
      assertEquals(10, policy.getMaxConcurrentRetries());
    }

    @Test
    void default_isEnabled() {
      assertTrue(RetryPolicy.DEFAULT.isEnabled());
    }
  }

  @Nested
  class DisabledPolicyTests {

    @Test
    void disabled_hasZeroRetries() {
      RetryPolicy policy = RetryPolicy.DISABLED;

      assertEquals(0, policy.getMaxRetries());
    }

    @Test
    void disabled_isNotEnabled() {
      assertFalse(RetryPolicy.DISABLED.isEnabled());
    }
  }

  @Nested
  class BuilderTests {

    @Test
    void customValues_applied() {
      RetryPolicy policy =
          RetryPolicy.builder()
              .maxRetries(5)
              .initialBackoff(Duration.ofMillis(100))
              .maxBackoff(Duration.ofSeconds(10))
              .multiplier(1.5)
              .jitterType(JitterType.EQUAL)
              .maxConcurrentRetries(20)
              .build();

      assertEquals(5, policy.getMaxRetries());
      assertEquals(Duration.ofMillis(100), policy.getInitialBackoff());
      assertEquals(Duration.ofSeconds(10), policy.getMaxBackoff());
      assertEquals(1.5, policy.getMultiplier());
      assertEquals(JitterType.EQUAL, policy.getJitterType());
      assertEquals(20, policy.getMaxConcurrentRetries());
    }

    @Test
    void maxRetries_belowRange_throws() {
      assertThrows(
          IllegalArgumentException.class, () -> RetryPolicy.builder().maxRetries(-1).build());
    }

    @Test
    void maxRetries_aboveRange_throws() {
      assertThrows(
          IllegalArgumentException.class, () -> RetryPolicy.builder().maxRetries(11).build());
    }

    @Test
    void multiplier_belowRange_throws() {
      assertThrows(
          IllegalArgumentException.class, () -> RetryPolicy.builder().multiplier(1.0).build());
    }

    @Test
    void multiplier_aboveRange_throws() {
      assertThrows(
          IllegalArgumentException.class, () -> RetryPolicy.builder().multiplier(4.0).build());
    }

    @Test
    void initialBackoff_belowRange_throws() {
      assertThrows(
          IllegalArgumentException.class,
          () -> RetryPolicy.builder().initialBackoff(Duration.ofMillis(10)).build());
    }

    @Test
    void initialBackoff_aboveRange_throws() {
      assertThrows(
          IllegalArgumentException.class,
          () -> RetryPolicy.builder().initialBackoff(Duration.ofSeconds(10)).build());
    }

    @Test
    void maxBackoff_belowRange_throws() {
      assertThrows(
          IllegalArgumentException.class,
          () -> RetryPolicy.builder().maxBackoff(Duration.ofMillis(500)).build());
    }

    @Test
    void maxBackoff_aboveRange_throws() {
      assertThrows(
          IllegalArgumentException.class,
          () -> RetryPolicy.builder().maxBackoff(Duration.ofSeconds(200)).build());
    }

    @Test
    void maxConcurrentRetries_belowRange_throws() {
      assertThrows(
          IllegalArgumentException.class,
          () -> RetryPolicy.builder().maxConcurrentRetries(-1).build());
    }

    @Test
    void maxConcurrentRetries_aboveRange_throws() {
      assertThrows(
          IllegalArgumentException.class,
          () -> RetryPolicy.builder().maxConcurrentRetries(101).build());
    }
  }

  @Nested
  class BackoffComputationTests {

    @Test
    void noneJitter_returnsExactExponentialBackoff() {
      RetryPolicy policy =
          RetryPolicy.builder()
              .initialBackoff(Duration.ofMillis(100))
              .multiplier(2.0)
              .maxBackoff(Duration.ofSeconds(10))
              .jitterType(JitterType.NONE)
              .build();

      assertEquals(Duration.ofMillis(100), policy.computeBackoff(0));
      assertEquals(Duration.ofMillis(200), policy.computeBackoff(1));
      assertEquals(Duration.ofMillis(400), policy.computeBackoff(2));
    }

    @Test
    void noneJitter_cappedAtMaxBackoff() {
      RetryPolicy policy =
          RetryPolicy.builder()
              .initialBackoff(Duration.ofSeconds(1))
              .multiplier(3.0)
              .maxBackoff(Duration.ofSeconds(5))
              .jitterType(JitterType.NONE)
              .build();

      Duration backoff = policy.computeBackoff(10);
      assertTrue(backoff.toMillis() <= 5000);
    }

    @RepeatedTest(10)
    void fullJitter_withinRange() {
      RetryPolicy policy =
          RetryPolicy.builder()
              .initialBackoff(Duration.ofMillis(100))
              .multiplier(2.0)
              .maxBackoff(Duration.ofSeconds(10))
              .jitterType(JitterType.FULL)
              .build();

      Duration backoff = policy.computeBackoff(0);
      assertTrue(backoff.toMillis() >= 0);
      assertTrue(backoff.toMillis() <= 100);
    }

    @RepeatedTest(10)
    void equalJitter_withinRange() {
      RetryPolicy policy =
          RetryPolicy.builder()
              .initialBackoff(Duration.ofMillis(100))
              .multiplier(2.0)
              .maxBackoff(Duration.ofSeconds(10))
              .jitterType(JitterType.EQUAL)
              .build();

      Duration backoff = policy.computeBackoff(0);
      assertTrue(backoff.toMillis() >= 50);
      assertTrue(backoff.toMillis() <= 100);
    }
  }

  @Nested
  class WorstCaseLatencyTests {

    @Test
    void worstCaseLatency_withNoRetries() {
      RetryPolicy policy = RetryPolicy.DISABLED;
      Duration result = policy.worstCaseLatency(Duration.ofSeconds(5));
      assertEquals(5000, result.toMillis());
    }

    @Test
    void worstCaseLatency_withRetries() {
      RetryPolicy policy =
          RetryPolicy.builder()
              .maxRetries(2)
              .initialBackoff(Duration.ofMillis(500))
              .multiplier(2.0)
              .maxBackoff(Duration.ofSeconds(30))
              .jitterType(JitterType.NONE)
              .build();

      Duration opTimeout = Duration.ofSeconds(5);
      Duration result = policy.worstCaseLatency(opTimeout);

      // first op: 5000ms
      // retry 0 backoff: 500ms + op: 5000ms
      // retry 1 backoff: 1000ms + op: 5000ms
      // total: 5000 + 500 + 5000 + 1000 + 5000 = 16500ms
      assertEquals(16500, result.toMillis());
    }
  }

  @Nested
  class IsEnabledTests {

    @Test
    void zeroRetries_notEnabled() {
      RetryPolicy policy = RetryPolicy.builder().maxRetries(0).build();
      assertFalse(policy.isEnabled());
    }

    @Test
    void positiveRetries_enabled() {
      RetryPolicy policy = RetryPolicy.builder().maxRetries(1).build();
      assertTrue(policy.isEnabled());
    }
  }
}
