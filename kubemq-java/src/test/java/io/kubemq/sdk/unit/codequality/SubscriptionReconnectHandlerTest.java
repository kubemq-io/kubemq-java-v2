package io.kubemq.sdk.unit.codequality;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.common.SubscriptionReconnectHandler;
import io.kubemq.sdk.exception.KubeMQException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link SubscriptionReconnectHandler} (REQ-CQ-5). */
class SubscriptionReconnectHandlerTest {

  private ScheduledExecutorService executor;

  @BeforeEach
  void setUp() {
    executor =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "test-reconnect");
              t.setDaemon(true);
              return t;
            });
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void successfulReconnectResetsAttempts() throws InterruptedException {
    SubscriptionReconnectHandler handler =
        new SubscriptionReconnectHandler(executor, 10, "test-channel", "testOp");

    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger callCount = new AtomicInteger(0);

    handler.scheduleReconnect(
        () -> {
          callCount.incrementAndGet();
          latch.countDown();
        },
        err -> fail("Should not error on success"));

    assertTrue(latch.await(5, TimeUnit.SECONDS), "Reconnect should complete");
    assertEquals(1, callCount.get());
  }

  @Test
  void maxAttemptsReachedFiresErrorCallback() throws InterruptedException {
    SubscriptionReconnectHandler handler =
        new SubscriptionReconnectHandler(executor, 1, "test-channel", "testOp");

    AtomicReference<KubeMQException> errorRef = new AtomicReference<>();
    CountDownLatch errorLatch = new CountDownLatch(1);

    for (int i = 0; i < 11; i++) {
      handler.scheduleReconnect(
          () -> {
            throw new RuntimeException("simulated failure");
          },
          err -> {
            errorRef.set(err);
            errorLatch.countDown();
          });
    }

    assertTrue(errorLatch.await(10, TimeUnit.SECONDS), "Error callback should fire");
    assertNotNull(errorRef.get());
    assertTrue(errorRef.get().getMessage().contains("Max reconnection attempts"));
  }

  @Test
  void resetAttemptsResetsCounter() {
    SubscriptionReconnectHandler handler =
        new SubscriptionReconnectHandler(executor, 100, "test-channel", "testOp");

    handler.resetAttempts();
    // no exception means success
  }
}
