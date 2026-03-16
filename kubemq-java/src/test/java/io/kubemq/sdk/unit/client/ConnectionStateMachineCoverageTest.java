package io.kubemq.sdk.unit.client;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.client.ConnectionState;
import io.kubemq.sdk.client.ConnectionStateListener;
import io.kubemq.sdk.client.ConnectionStateMachine;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Additional coverage tests for ConnectionStateMachine to cover paths not exercised by the primary
 * ConnectionStateMachineTest.
 */
class ConnectionStateMachineCoverageTest {

  private ConnectionStateMachine sm;

  @BeforeEach
  void setUp() {
    sm = new ConnectionStateMachine();
  }

  @AfterEach
  void tearDown() {
    sm.shutdown();
  }

  @Nested
  class ShutdownTests {

    @Test
    void shutdown_isIdempotent() {
      assertDoesNotThrow(
          () -> {
            sm.shutdown();
            sm.shutdown();
          });
    }

    @Test
    void shutdown_afterTransitions_succeeds() {
      sm.transitionTo(ConnectionState.CONNECTING);
      sm.transitionTo(ConnectionState.READY);

      assertDoesNotThrow(() -> sm.shutdown());
    }

    @Test
    void transitionAfterShutdown_stillWorks() throws InterruptedException {
      sm.transitionTo(ConnectionState.CONNECTING);
      sm.transitionTo(ConnectionState.READY);
      sm.shutdown();

      sm.transitionTo(ConnectionState.CLOSED);
      assertEquals(ConnectionState.CLOSED, sm.getState());
    }
  }

  @Nested
  class RemoveListenerTests {

    @Test
    void removeListener_stopsNotifications() throws InterruptedException {
      AtomicBoolean called = new AtomicBoolean(false);
      ConnectionStateListener listener =
          new ConnectionStateListener() {
            @Override
            public void onConnected() {
              called.set(true);
            }
          };

      sm.addListener(listener);
      sm.removeListener(listener);
      sm.transitionTo(ConnectionState.CONNECTING);
      sm.transitionTo(ConnectionState.READY);

      Thread.sleep(200);
      assertFalse(called.get());
    }

    @Test
    void removeListener_withNonExistentListener_doesNotThrow() {
      ConnectionStateListener listener = new ConnectionStateListener() {};

      assertDoesNotThrow(() -> sm.removeListener(listener));
    }

    @Test
    void removeListener_withNull_doesNotThrow() {
      assertDoesNotThrow(() -> sm.removeListener(null));
    }

    @Test
    void removeListener_otherListenersStillNotified() throws InterruptedException {
      CountDownLatch latch = new CountDownLatch(1);
      AtomicBoolean removedCalled = new AtomicBoolean(false);
      AtomicBoolean keptCalled = new AtomicBoolean(false);

      ConnectionStateListener toRemove =
          new ConnectionStateListener() {
            @Override
            public void onConnected() {
              removedCalled.set(true);
            }
          };

      ConnectionStateListener toKeep =
          new ConnectionStateListener() {
            @Override
            public void onConnected() {
              keptCalled.set(true);
              latch.countDown();
            }
          };

      sm.addListener(toRemove);
      sm.addListener(toKeep);
      sm.removeListener(toRemove);

      sm.transitionTo(ConnectionState.CONNECTING);
      sm.transitionTo(ConnectionState.READY);

      assertTrue(latch.await(2, TimeUnit.SECONDS));
      assertFalse(removedCalled.get());
      assertTrue(keptCalled.get());
    }
  }

  @Nested
  class EdgeCaseTransitionTests {

    @Test
    void connectingToClosed_isValid() {
      sm.transitionTo(ConnectionState.CONNECTING);
      sm.transitionTo(ConnectionState.CLOSED);
      assertEquals(ConnectionState.CLOSED, sm.getState());
    }

    @Test
    void reconnectingToClosed_isValid() {
      sm.transitionTo(ConnectionState.CONNECTING);
      sm.transitionTo(ConnectionState.READY);
      sm.transitionTo(ConnectionState.RECONNECTING);
      sm.transitionTo(ConnectionState.CLOSED);
      assertEquals(ConnectionState.CLOSED, sm.getState());
    }

    @Test
    void idleToReconnecting_isInvalid() {
      sm.transitionTo(ConnectionState.RECONNECTING);
      assertEquals(ConnectionState.IDLE, sm.getState());
    }

    @Test
    void idleToClosed_isInvalid() {
      sm.transitionTo(ConnectionState.CLOSED);
      assertEquals(ConnectionState.IDLE, sm.getState());
    }

    @Test
    void readyToConnecting_isInvalid() {
      sm.transitionTo(ConnectionState.CONNECTING);
      sm.transitionTo(ConnectionState.READY);
      sm.transitionTo(ConnectionState.CONNECTING);
      assertEquals(ConnectionState.READY, sm.getState());
    }
  }

  @Nested
  class ListenerCallbackTests {

    @Test
    void onDisconnected_notCalledForConnectingToReconnecting() throws InterruptedException {
      AtomicBoolean disconnectedCalled = new AtomicBoolean(false);

      sm.addListener(
          new ConnectionStateListener() {
            @Override
            public void onDisconnected() {
              disconnectedCalled.set(true);
            }
          });

      sm.transitionTo(ConnectionState.CONNECTING);
      // CONNECTING -> RECONNECTING is not a valid transition, so no callback
      sm.transitionTo(ConnectionState.RECONNECTING);

      Thread.sleep(200);
      assertFalse(disconnectedCalled.get());
    }

    @Test
    void setCurrentReconnectAttempt_reflectedInCallback() throws InterruptedException {
      CountDownLatch latch = new CountDownLatch(1);
      java.util.concurrent.atomic.AtomicInteger received =
          new java.util.concurrent.atomic.AtomicInteger(-1);

      sm.addListener(
          new ConnectionStateListener() {
            @Override
            public void onReconnecting(int attempt) {
              received.set(attempt);
              latch.countDown();
            }
          });

      sm.setCurrentReconnectAttempt(7);
      sm.transitionTo(ConnectionState.CONNECTING);
      sm.transitionTo(ConnectionState.READY);
      sm.transitionTo(ConnectionState.RECONNECTING);

      assertTrue(latch.await(2, TimeUnit.SECONDS));
      assertEquals(7, received.get());
    }

    @Test
    void closedAfterReconnecting_firesClosed() throws InterruptedException {
      CountDownLatch latch = new CountDownLatch(1);
      AtomicBoolean closedCalled = new AtomicBoolean(false);

      sm.addListener(
          new ConnectionStateListener() {
            @Override
            public void onClosed() {
              closedCalled.set(true);
              latch.countDown();
            }
          });

      sm.transitionTo(ConnectionState.CONNECTING);
      sm.transitionTo(ConnectionState.READY);
      sm.transitionTo(ConnectionState.RECONNECTING);
      sm.transitionTo(ConnectionState.CLOSED);

      assertTrue(latch.await(2, TimeUnit.SECONDS));
      assertTrue(closedCalled.get());
    }
  }
}
