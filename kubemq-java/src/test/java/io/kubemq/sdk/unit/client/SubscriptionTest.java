package io.kubemq.sdk.unit.client;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.client.Subscription;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Unit tests for the Subscription handle class. */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class SubscriptionTest {

  @Nested
  class CancelTests {

    @Test
    void cancel_invokesCancelAction() {
      AtomicInteger callCount = new AtomicInteger(0);
      Subscription sub = new Subscription(callCount::incrementAndGet);

      sub.cancel();

      assertEquals(1, callCount.get());
    }

    @Test
    void cancel_isIdempotent_secondCallIsNoOp() {
      AtomicInteger callCount = new AtomicInteger(0);
      Subscription sub = new Subscription(callCount::incrementAndGet);

      sub.cancel();
      sub.cancel();
      sub.cancel();

      assertEquals(1, callCount.get());
    }
  }

  @Nested
  class CancelAsyncTests {

    @Test
    void cancelAsync_returnsCompletableFuture() throws Exception {
      AtomicInteger callCount = new AtomicInteger(0);
      Subscription sub = new Subscription(callCount::incrementAndGet);

      CompletableFuture<Void> future = sub.cancelAsync();
      future.get(5, TimeUnit.SECONDS);

      assertEquals(1, callCount.get());
    }

    @Test
    void cancelAsync_afterCancel_completesWithoutInvokingAgain() throws Exception {
      AtomicInteger callCount = new AtomicInteger(0);
      Subscription sub = new Subscription(callCount::incrementAndGet);

      sub.cancel();
      CompletableFuture<Void> future = sub.cancelAsync();
      future.get(5, TimeUnit.SECONDS);

      assertEquals(1, callCount.get());
    }

    @Test
    void cancelAsync_withCustomExecutor_usesProvidedExecutor() throws Exception {
      AtomicInteger callCount = new AtomicInteger(0);
      Executor executor = Executors.newSingleThreadExecutor();
      Subscription sub = new Subscription(callCount::incrementAndGet, executor);

      CompletableFuture<Void> future = sub.cancelAsync();
      future.get(5, TimeUnit.SECONDS);

      assertEquals(1, callCount.get());
    }
  }

  @Nested
  class IsCancelledTests {

    @Test
    void isCancelled_beforeCancel_returnsFalse() {
      Subscription sub = new Subscription(() -> {});

      assertFalse(sub.isCancelled());
    }

    @Test
    void isCancelled_afterCancel_returnsTrue() {
      Subscription sub = new Subscription(() -> {});

      sub.cancel();

      assertTrue(sub.isCancelled());
    }

    @Test
    void isCancelled_afterCancelAsync_returnsTrue() throws Exception {
      Subscription sub = new Subscription(() -> {});

      sub.cancelAsync().get(5, TimeUnit.SECONDS);

      assertTrue(sub.isCancelled());
    }
  }

  @Nested
  class ConstructorTests {

    @Test
    void constructorWithRunnableOnly_usesCommonPool() throws Exception {
      AtomicInteger callCount = new AtomicInteger(0);
      Subscription sub = new Subscription(callCount::incrementAndGet);

      assertNotNull(sub);
      assertFalse(sub.isCancelled());

      sub.cancelAsync().get(5, TimeUnit.SECONDS);
      assertEquals(1, callCount.get());
      assertTrue(sub.isCancelled());
    }

    @Test
    void constructorWithRunnableAndExecutor_usesProvidedExecutor() throws Exception {
      AtomicInteger callCount = new AtomicInteger(0);
      Executor customExecutor = Executors.newSingleThreadExecutor();
      Subscription sub = new Subscription(callCount::incrementAndGet, customExecutor);

      assertNotNull(sub);
      assertFalse(sub.isCancelled());

      sub.cancelAsync().get(5, TimeUnit.SECONDS);
      assertEquals(1, callCount.get());
      assertTrue(sub.isCancelled());
    }
  }

  @Nested
  class ThreadSafetyTests {

    @Test
    void concurrentCancels_actionInvokedExactlyOnce() throws Exception {
      AtomicInteger callCount = new AtomicInteger(0);
      Subscription sub = new Subscription(callCount::incrementAndGet);

      int threadCount = 10;
      CompletableFuture<?>[] futures = new CompletableFuture[threadCount];
      for (int i = 0; i < threadCount; i++) {
        futures[i] = CompletableFuture.runAsync(sub::cancel);
      }
      CompletableFuture.allOf(futures).get(5, TimeUnit.SECONDS);

      assertEquals(1, callCount.get());
      assertTrue(sub.isCancelled());
    }
  }
}
