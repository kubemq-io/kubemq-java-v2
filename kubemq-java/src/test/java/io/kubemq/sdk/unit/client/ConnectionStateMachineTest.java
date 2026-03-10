package io.kubemq.sdk.unit.client;

import io.kubemq.sdk.client.ConnectionState;
import io.kubemq.sdk.client.ConnectionStateListener;
import io.kubemq.sdk.client.ConnectionStateMachine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionStateMachineTest {

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
    class StateTransitionTests {

        @Test
        void initialState_isIdle() {
            assertEquals(ConnectionState.IDLE, sm.getState());
        }

        @Test
        void validTransition_idleToConnecting() {
            sm.transitionTo(ConnectionState.CONNECTING);
            assertEquals(ConnectionState.CONNECTING, sm.getState());
        }

        @Test
        void validTransition_connectingToReady() {
            sm.transitionTo(ConnectionState.CONNECTING);
            sm.transitionTo(ConnectionState.READY);
            assertEquals(ConnectionState.READY, sm.getState());
        }

        @Test
        void validTransition_readyToReconnecting() {
            sm.transitionTo(ConnectionState.CONNECTING);
            sm.transitionTo(ConnectionState.READY);
            sm.transitionTo(ConnectionState.RECONNECTING);
            assertEquals(ConnectionState.RECONNECTING, sm.getState());
        }

        @Test
        void validTransition_reconnectingToReady() {
            sm.transitionTo(ConnectionState.CONNECTING);
            sm.transitionTo(ConnectionState.READY);
            sm.transitionTo(ConnectionState.RECONNECTING);
            sm.transitionTo(ConnectionState.READY);
            assertEquals(ConnectionState.READY, sm.getState());
        }

        @Test
        void validTransition_readyToClosed() {
            sm.transitionTo(ConnectionState.CONNECTING);
            sm.transitionTo(ConnectionState.READY);
            sm.transitionTo(ConnectionState.CLOSED);
            assertEquals(ConnectionState.CLOSED, sm.getState());
        }

        @Test
        void closedIsTerminal_rejectsTransitions() {
            sm.transitionTo(ConnectionState.CONNECTING);
            sm.transitionTo(ConnectionState.CLOSED);
            sm.transitionTo(ConnectionState.READY);
            assertEquals(ConnectionState.CLOSED, sm.getState());
        }

        @Test
        void invalidTransition_idleToReady_rejected() {
            sm.transitionTo(ConnectionState.READY);
            assertEquals(ConnectionState.IDLE, sm.getState());
        }

        @Test
        void sameStateTransition_isNoOp() {
            sm.transitionTo(ConnectionState.CONNECTING);
            sm.transitionTo(ConnectionState.CONNECTING);
            assertEquals(ConnectionState.CONNECTING, sm.getState());
        }
    }

    @Nested
    class ListenerTests {

        @Test
        void onConnected_firesAfterInitialConnection() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean called = new AtomicBoolean(false);

            sm.addListener(new ConnectionStateListener() {
                @Override
                public void onConnected() {
                    called.set(true);
                    latch.countDown();
                }
            });

            sm.transitionTo(ConnectionState.CONNECTING);
            sm.transitionTo(ConnectionState.READY);

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertTrue(called.get());
        }

        @Test
        void onDisconnected_firesOnConnectionLoss() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean called = new AtomicBoolean(false);

            sm.addListener(new ConnectionStateListener() {
                @Override
                public void onDisconnected() {
                    called.set(true);
                    latch.countDown();
                }
            });

            sm.transitionTo(ConnectionState.CONNECTING);
            sm.transitionTo(ConnectionState.READY);
            sm.transitionTo(ConnectionState.RECONNECTING);

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertTrue(called.get());
        }

        @Test
        void onReconnecting_firesWithAttemptCount() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger receivedAttempt = new AtomicInteger(-1);

            sm.addListener(new ConnectionStateListener() {
                @Override
                public void onReconnecting(int attempt) {
                    receivedAttempt.set(attempt);
                    latch.countDown();
                }
            });

            sm.setCurrentReconnectAttempt(3);
            sm.transitionTo(ConnectionState.CONNECTING);
            sm.transitionTo(ConnectionState.READY);
            sm.transitionTo(ConnectionState.RECONNECTING);

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals(3, receivedAttempt.get());
        }

        @Test
        void onReconnected_firesAfterSuccessfulReconnection() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean called = new AtomicBoolean(false);

            sm.addListener(new ConnectionStateListener() {
                @Override
                public void onReconnected() {
                    called.set(true);
                    latch.countDown();
                }
            });

            sm.transitionTo(ConnectionState.CONNECTING);
            sm.transitionTo(ConnectionState.READY);
            sm.transitionTo(ConnectionState.RECONNECTING);
            sm.transitionTo(ConnectionState.READY);

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertTrue(called.get());
        }

        @Test
        void onClosed_firesOnClose() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean called = new AtomicBoolean(false);

            sm.addListener(new ConnectionStateListener() {
                @Override
                public void onClosed() {
                    called.set(true);
                    latch.countDown();
                }
            });

            sm.transitionTo(ConnectionState.CONNECTING);
            sm.transitionTo(ConnectionState.READY);
            sm.transitionTo(ConnectionState.CLOSED);

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertTrue(called.get());
        }

        @Test
        void listenerException_doesNotCrashStateMachine() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean secondListenerCalled = new AtomicBoolean(false);

            sm.addListener(new ConnectionStateListener() {
                @Override
                public void onConnected() {
                    throw new RuntimeException("Boom!");
                }
            });
            sm.addListener(new ConnectionStateListener() {
                @Override
                public void onConnected() {
                    secondListenerCalled.set(true);
                    latch.countDown();
                }
            });

            sm.transitionTo(ConnectionState.CONNECTING);
            sm.transitionTo(ConnectionState.READY);

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertTrue(secondListenerCalled.get());
        }

        @Test
        void listenersInvokedAsynchronously() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> threadName = new AtomicReference<>();

            sm.addListener(new ConnectionStateListener() {
                @Override
                public void onConnected() {
                    threadName.set(Thread.currentThread().getName());
                    latch.countDown();
                }
            });

            sm.transitionTo(ConnectionState.CONNECTING);
            sm.transitionTo(ConnectionState.READY);

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals("kubemq-state-listener", threadName.get());
        }

        @Test
        void removeListener_stopsNotifications() throws InterruptedException {
            AtomicBoolean called = new AtomicBoolean(false);
            ConnectionStateListener listener = new ConnectionStateListener() {
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
        void nullListener_ignored() {
            assertDoesNotThrow(() -> sm.addListener(null));
        }
    }

    @Nested
    class GetStateTests {

        @Test
        void getState_returnsCurrentState() {
            sm.transitionTo(ConnectionState.CONNECTING);
            sm.transitionTo(ConnectionState.READY);
            assertEquals(ConnectionState.READY, sm.getState());
        }
    }
}
