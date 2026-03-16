package io.kubemq.sdk.client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Thread-safe connection state machine with asynchronous listener notification. */
public class ConnectionStateMachine {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectionStateMachine.class);
  private static final int MAX_CAS_RETRIES = 100;

  private final AtomicReference<ConnectionState> state =
      new AtomicReference<>(ConnectionState.IDLE);
  private final List<ConnectionStateListener> listeners = new CopyOnWriteArrayList<>();
  private final ExecutorService listenerExecutor;

  private volatile int currentReconnectAttempt = 0;

  /** Constructs a new instance. */
  public ConnectionStateMachine() {
    this.listenerExecutor =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "kubemq-state-listener");
              t.setDaemon(true);
              return t;
            });
  }

  /**
   * @return the current connection state
   */
  public ConnectionState getState() {
    return state.get();
  }

  /**
   * Register a listener for state transitions.
   *
   * @param listener the listener
   */
  public void addListener(ConnectionStateListener listener) {
    if (listener != null) {
      listeners.add(listener);
    }
  }

  /**
   * Remove a previously registered listener.
   *
   * @param listener the listener
   */
  public void removeListener(ConnectionStateListener listener) {
    listeners.remove(listener);
  }

  /**
   * Transition to a new state. Invalid transitions are logged and ignored. Listeners are notified
   * asynchronously.
   *
   * @param newState the new state
   */
  public void transitionTo(ConnectionState newState) {
    for (int i = 0; i < MAX_CAS_RETRIES; i++) {
      ConnectionState oldState = state.get();

      if (oldState == ConnectionState.CLOSED) {
        LOG.warn("Cannot transition from CLOSED to {} -- CLOSED is terminal", newState);
        return;
      }

      if (oldState == newState) {
        return;
      }

      if (!isValidTransition(oldState, newState)) {
        LOG.warn("Invalid state transition: {} -> {} (rejected)", oldState, newState);
        return;
      }

      if (!state.compareAndSet(oldState, newState)) {
        Thread.onSpinWait();
        continue;
      }

      LOG.info("Connection state: {} -> {}", oldState, newState);

      final ConnectionState capturedOldState = oldState;
      try {
        listenerExecutor.submit(() -> notifyListeners(capturedOldState, newState));
      } catch (RejectedExecutionException e) {
        notifyListeners(capturedOldState, newState);
      }

      return;
    }
    LOG.warn("CAS loop exhausted after {} retries for transition to {}", MAX_CAS_RETRIES, newState);
  }

  private void notifyListeners(ConnectionState oldState, ConnectionState newState) {
    for (ConnectionStateListener listener : listeners) {
      try {
        switch (newState) {
          case CONNECTING:
            break;
          case READY:
            if (oldState == ConnectionState.RECONNECTING) {
              listener.onReconnected();
            } else {
              listener.onConnected();
            }
            break;
          case RECONNECTING:
            if (oldState == ConnectionState.READY) {
              listener.onDisconnected();
            }
            listener.onReconnecting(currentReconnectAttempt);
            break;
          case CLOSED:
            listener.onClosed();
            break;
          default:
            break;
        }
      } catch (Exception e) {
        LOG.error("Error in connection state listener: {}", e.getMessage(), e);
      }
    }
  }

  private boolean isValidTransition(ConnectionState from, ConnectionState to) {
    switch (from) {
      case IDLE:
        return to == ConnectionState.CONNECTING;
      case CONNECTING:
        return to == ConnectionState.READY || to == ConnectionState.CLOSED;
      case READY:
        return to == ConnectionState.RECONNECTING || to == ConnectionState.CLOSED;
      case RECONNECTING:
        return to == ConnectionState.READY || to == ConnectionState.CLOSED;
      case CLOSED:
        return false;
      default:
        return false;
    }
  }

  /**
   * Update the current reconnect attempt number for listener callbacks.
   *
   * @param attempt the attempt
   */
  public void setCurrentReconnectAttempt(int attempt) {
    this.currentReconnectAttempt = attempt;
  }

  /** Shutdown the listener executor. */
  public void shutdown() {
    listenerExecutor.shutdown();
    try {
      if (!listenerExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
        listenerExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      listenerExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
