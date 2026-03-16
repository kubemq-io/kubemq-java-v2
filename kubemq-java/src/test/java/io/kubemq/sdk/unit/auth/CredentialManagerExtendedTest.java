package io.kubemq.sdk.unit.auth;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.auth.CredentialException;
import io.kubemq.sdk.auth.CredentialManager;
import io.kubemq.sdk.auth.CredentialProvider;
import io.kubemq.sdk.auth.TokenResult;
import io.kubemq.sdk.exception.ConnectionException;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

/**
 * Extended coverage tests for CredentialManager: - Proactive refresh with past expiry (negative
 * lifetime) - Proactive refresh with very short lifetime (buffer enforcement) - Proactive refresh
 * task failure - Cancel proactive refresh - Concurrent invalidate + getToken
 */
@DisplayName("CredentialManager extended coverage")
class CredentialManagerExtendedTest {

  @Nested
  @DisplayName("Proactive refresh scheduling edge cases")
  class ProactiveRefreshTests {

    @Test
    @DisplayName("token with past expiresAt skips proactive refresh")
    void pastExpiry_skipsProactiveRefresh() throws Exception {
      AtomicInteger callCount = new AtomicInteger(0);
      ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

      CredentialProvider provider =
          () -> {
            callCount.incrementAndGet();
            // Token already expired
            return new TokenResult("expired-token", Instant.now().minusSeconds(60));
          };

      CredentialManager manager = new CredentialManager(provider, scheduler);

      assertEquals("expired-token", manager.getToken());
      assertEquals(1, callCount.get());

      // Wait to verify no proactive refresh is triggered
      Thread.sleep(200);
      assertEquals(1, callCount.get(), "No proactive refresh should fire for past expiry");

      manager.shutdown();
      scheduler.shutdownNow();
    }

    @Test
    @DisplayName("very short token lifetime enforces minimum buffer")
    void shortLifetime_enforcesMinBuffer() throws Exception {
      AtomicInteger callCount = new AtomicInteger(0);
      ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

      CredentialProvider provider =
          () -> {
            int count = callCount.incrementAndGet();
            // Very short lifetime (100ms) — buffer (10ms) < MIN_REFRESH_BUFFER (30s)
            // So delay = lifetime - 30s = negative → delay = ZERO → immediate refresh
            return new TokenResult("token-v" + count, Instant.now().plusMillis(100));
          };

      CredentialManager manager = new CredentialManager(provider, scheduler);

      assertEquals("token-v1", manager.getToken());

      // With 100ms lifetime and 30s min buffer, delay is negative → ZERO
      // So proactive refresh fires immediately
      Thread.sleep(300);
      assertTrue(
          callCount.get() >= 2,
          "Proactive refresh should fire for short-lived token, count=" + callCount.get());

      manager.shutdown();
      scheduler.shutdownNow();
    }

    @Test
    @DisplayName("proactive refresh task failure is handled gracefully")
    void proactiveRefreshFailure_isHandled() throws Exception {
      AtomicInteger callCount = new AtomicInteger(0);
      ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

      CredentialProvider provider =
          () -> {
            int count = callCount.incrementAndGet();
            if (count == 1) {
              return new TokenResult("initial-token", Instant.now().plusMillis(100));
            }
            throw new CredentialException("vault unavailable", true);
          };

      CredentialManager manager = new CredentialManager(provider, scheduler);

      assertEquals("initial-token", manager.getToken());

      // Wait for proactive refresh to fire and fail
      Thread.sleep(300);
      assertTrue(callCount.get() >= 2, "Proactive refresh should have attempted");

      // Original cached token should be cleared but we can still call getToken
      // which will try to fetch again (and fail with ConnectionException)
      assertThrows(ConnectionException.class, () -> manager.getToken());

      manager.shutdown();
      scheduler.shutdownNow();
    }

    @Test
    @DisplayName("null scheduler disables proactive refresh")
    void nullScheduler_disablesProactiveRefresh() throws Exception {
      AtomicInteger callCount = new AtomicInteger(0);

      CredentialProvider provider =
          () -> {
            callCount.incrementAndGet();
            return new TokenResult("token", Instant.now().plusSeconds(3600));
          };

      CredentialManager manager = new CredentialManager(provider, null);

      assertEquals("token", manager.getToken());
      Thread.sleep(200);
      assertEquals(1, callCount.get(), "No proactive refresh without scheduler");

      manager.shutdown();
    }

    @Test
    @DisplayName("null expiresAt disables proactive refresh")
    void nullExpiresAt_disablesProactiveRefresh() throws Exception {
      AtomicInteger callCount = new AtomicInteger(0);
      ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

      CredentialProvider provider =
          () -> {
            callCount.incrementAndGet();
            return new TokenResult("static-token"); // no expiry
          };

      CredentialManager manager = new CredentialManager(provider, scheduler);

      assertEquals("static-token", manager.getToken());
      Thread.sleep(200);
      assertEquals(1, callCount.get(), "No proactive refresh without expiresAt");

      manager.shutdown();
      scheduler.shutdownNow();
    }
  }

  @Nested
  @DisplayName("Invalidate and cancel tests")
  class InvalidateTests {

    @Test
    @DisplayName("invalidate clears cache and cancels proactive refresh")
    void invalidate_clearsCacheAndCancelsRefresh() throws Exception {
      AtomicInteger callCount = new AtomicInteger(0);
      ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

      CredentialProvider provider =
          () -> {
            int count = callCount.incrementAndGet();
            return new TokenResult("token-v" + count, Instant.now().plusSeconds(3600));
          };

      CredentialManager manager = new CredentialManager(provider, scheduler);

      assertEquals("token-v1", manager.getToken());

      manager.invalidate();

      assertEquals("token-v2", manager.getToken());
      assertEquals(2, callCount.get());

      manager.shutdown();
      scheduler.shutdownNow();
    }

    @Test
    @DisplayName("shutdown is idempotent")
    void shutdown_isIdempotent() {
      CredentialProvider provider = () -> new TokenResult("token");
      CredentialManager manager = new CredentialManager(provider, null);

      assertDoesNotThrow(
          () -> {
            manager.shutdown();
            manager.shutdown();
          });
    }

    @Test
    @DisplayName("concurrent invalidate and getToken are safe")
    void concurrentInvalidateAndGetToken() throws Exception {
      AtomicInteger callCount = new AtomicInteger(0);

      CredentialProvider provider =
          () -> {
            int count = callCount.incrementAndGet();
            return new TokenResult("token-v" + count);
          };

      CredentialManager manager = new CredentialManager(provider, null);

      int threadCount = 20;
      ExecutorService executor = Executors.newFixedThreadPool(threadCount);
      CountDownLatch latch = new CountDownLatch(threadCount);

      for (int i = 0; i < threadCount; i++) {
        final int idx = i;
        executor.submit(
            () -> {
              try {
                if (idx % 3 == 0) {
                  manager.invalidate();
                }
                String token = manager.getToken();
                assertNotNull(token);
                assertTrue(token.startsWith("token-v"));
              } finally {
                latch.countDown();
              }
            });
      }

      assertTrue(latch.await(5, TimeUnit.SECONDS));
      executor.shutdown();
      manager.shutdown();
    }
  }
}
