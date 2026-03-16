package io.kubemq.sdk.unit.client;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.client.ConnectionState;
import io.kubemq.sdk.client.ConnectionStateMachine;
import io.kubemq.sdk.client.ReconnectionConfig;
import io.kubemq.sdk.client.ReconnectionManager;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReconnectionManagerTest {

  private ConnectionStateMachine sm;

  @BeforeEach
  void setUp() {
    sm = new ConnectionStateMachine();
    sm.transitionTo(ConnectionState.CONNECTING);
    sm.transitionTo(ConnectionState.READY);
  }

  @AfterEach
  void tearDown() {
    sm.shutdown();
  }

  @Nested
  class BackoffTests {

    @Test
    void computeDelay_withoutJitter_isExponential() {
      ReconnectionConfig config =
          ReconnectionConfig.builder()
              .initialReconnectDelayMs(500)
              .reconnectBackoffMultiplier(2.0)
              .maxReconnectDelayMs(30_000)
              .reconnectJitterEnabled(false)
              .build();
      ReconnectionManager mgr = new ReconnectionManager(config, sm);

      assertEquals(500, mgr.computeDelay(1));
      assertEquals(1000, mgr.computeDelay(2));
      assertEquals(2000, mgr.computeDelay(3));

      mgr.shutdown();
    }

    @Test
    void computeDelay_withoutJitter_cappedAtMax() {
      ReconnectionConfig config =
          ReconnectionConfig.builder()
              .initialReconnectDelayMs(1000)
              .reconnectBackoffMultiplier(2.0)
              .maxReconnectDelayMs(5000)
              .reconnectJitterEnabled(false)
              .build();
      ReconnectionManager mgr = new ReconnectionManager(config, sm);

      assertEquals(5000, mgr.computeDelay(10));

      mgr.shutdown();
    }

    @Test
    void computeDelay_withJitter_isNonDeterministic() {
      ReconnectionConfig config =
          ReconnectionConfig.builder()
              .initialReconnectDelayMs(500)
              .reconnectBackoffMultiplier(2.0)
              .maxReconnectDelayMs(30_000)
              .reconnectJitterEnabled(true)
              .build();
      ReconnectionManager mgr = new ReconnectionManager(config, sm);

      Set<Long> delays = new HashSet<>();
      for (int i = 0; i < 50; i++) {
        delays.add(mgr.computeDelay(5));
      }
      assertTrue(delays.size() > 1, "Jittered delays should produce varying values");

      mgr.shutdown();
    }

    @Test
    void computeDelay_withJitter_hasMinimumFloor() {
      ReconnectionConfig config =
          ReconnectionConfig.builder()
              .initialReconnectDelayMs(500)
              .reconnectBackoffMultiplier(2.0)
              .maxReconnectDelayMs(30_000)
              .reconnectJitterEnabled(true)
              .build();
      ReconnectionManager mgr = new ReconnectionManager(config, sm);

      long minFloor = 500 / 2;
      for (int i = 0; i < 100; i++) {
        long delay = mgr.computeDelay(1);
        assertTrue(delay >= minFloor, "Delay " + delay + " should be >= " + minFloor);
      }

      mgr.shutdown();
    }
  }

  @Nested
  class ReconnectionLifecycleTests {

    @Test
    void startReconnection_transitionsToReconnecting() throws InterruptedException {
      ReconnectionConfig config =
          ReconnectionConfig.builder()
              .initialReconnectDelayMs(50)
              .reconnectJitterEnabled(false)
              .build();
      ReconnectionManager mgr = new ReconnectionManager(config, sm);
      CountDownLatch latch = new CountDownLatch(1);

      mgr.startReconnection(() -> latch.countDown());
      assertEquals(ConnectionState.RECONNECTING, sm.getState());

      assertTrue(latch.await(2, TimeUnit.SECONDS));

      mgr.shutdown();
    }

    @Test
    void successfulReconnection_resetsAttemptCounter() throws InterruptedException {
      ReconnectionConfig config =
          ReconnectionConfig.builder()
              .initialReconnectDelayMs(50)
              .reconnectJitterEnabled(false)
              .build();
      ReconnectionManager mgr = new ReconnectionManager(config, sm);
      CountDownLatch latch = new CountDownLatch(1);

      mgr.startReconnection(() -> latch.countDown());
      assertTrue(latch.await(2, TimeUnit.SECONDS));

      Thread.sleep(100);
      assertEquals(0, mgr.getAttemptCount());

      mgr.shutdown();
    }

    @Test
    void maxAttemptsExhausted_transitionsToClosed() throws InterruptedException {
      ReconnectionConfig config =
          ReconnectionConfig.builder()
              .maxReconnectAttempts(2)
              .initialReconnectDelayMs(50)
              .reconnectJitterEnabled(false)
              .build();
      ReconnectionManager mgr = new ReconnectionManager(config, sm);
      AtomicInteger attempts = new AtomicInteger(0);

      mgr.startReconnection(
          () -> {
            int a = attempts.incrementAndGet();
            if (a <= 3) {
              throw new RuntimeException("fail");
            }
          });

      Thread.sleep(2000);
      assertEquals(ConnectionState.CLOSED, sm.getState());

      mgr.shutdown();
    }

    @Test
    void cancel_preventsScheduledReconnection() throws InterruptedException {
      ReconnectionConfig config =
          ReconnectionConfig.builder()
              .initialReconnectDelayMs(5000)
              .reconnectJitterEnabled(false)
              .build();
      ReconnectionManager mgr = new ReconnectionManager(config, sm);
      AtomicInteger called = new AtomicInteger(0);

      mgr.startReconnection(called::incrementAndGet);
      mgr.cancel();

      Thread.sleep(500);
      assertEquals(0, called.get());

      mgr.shutdown();
    }
  }
}
