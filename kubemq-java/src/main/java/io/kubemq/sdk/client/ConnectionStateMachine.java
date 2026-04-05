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

  // JV-1b: Track when READY was entered and whether it was a reconnection,
  // so callers can enforce a post-reconnect stabilization window.
  private volatile long readySinceNanos = 0;
  private volatile boolean readyAfterReconnect = false;

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

      // JV-1b: Track READY entry time and whether it followed RECONNECTING
      if (newState == ConnectionState.READY) {
        readyAfterReconnect = (oldState == ConnectionState.RECONNECTING);
        readySinceNanos = System.nanoTime();
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

  /**
   * Returns {@code true} if the connection is READY and has been stable for at least {@code
   * stabilizationMs} after a reconnection. If the last READY transition was from CONNECTING
   * (initial connect) rather than RECONNECTING, no stabilization window applies.
   *
   * <p>This prevents the race where senders resume immediately after reconnection but subscription
   * responders have not yet re-established, causing server-side timeouts.
   *
   * @param stabilizationMs grace period in milliseconds after reconnection
   * @return true if the connection is READY and stabilized
   */
  public boolean isReadyAndStabilized(long stabilizationMs) {
    if (state.get() != ConnectionState.READY) {
      return false;
    }
    if (!readyAfterReconnect) {
      return true; // initial connect — no grace period needed
    }
    long elapsedMs = (System.nanoTime() - readySinceNanos) / 1_000_000;
    return elapsedMs >= stabilizationMs;
  }

  /**
   * @return true if the most recent READY transition was from RECONNECTING
   */
  public boolean isReadyAfterReconnect() {
    return readyAfterReconnect;
  }

  /**
   * @return the nanoTime when state last transitioned to READY, or 0 if never
   */
  public long getReadySinceNanos() {
    return readySinceNanos;
  }

  /**
   * Fire {@link ConnectionStateListener#onDisconnected()} on all registered listeners without
   * changing the connection state. Used for external channel TRANSIENT_FAILURE notifications where
   * the client should stay alive (FR-5).
   */
  public void fireDisconnected() {
    try {
      listenerExecutor.submit(
          () -> {
            for (ConnectionStateListener listener : listeners) {
              try {
                listener.onDisconnected();
              } catch (Exception e) {
                LOG.error("Error in connection state listener: {}", e.getMessage(), e);
              }
            }
          });
    } catch (RejectedExecutionException e) {
      for (ConnectionStateListener listener : listeners) {
        try {
          listener.onDisconnected();
        } catch (Exception ex) {
          LOG.error("Error in connection state listener: {}", ex.getMessage(), ex);
        }
      }
    }
  }

  /**
   * Fire {@link ConnectionStateListener#onConnected()} on all registered listeners without changing
   * the connection state. Used for external channel recovery from TRANSIENT_FAILURE to READY
   * (FR-5).
   */
  public void fireConnected() {
    try {
      listenerExecutor.submit(
          () -> {
            for (ConnectionStateListener listener : listeners) {
              try {
                listener.onConnected();
              } catch (Exception e) {
                LOG.error("Error in connection state listener: {}", e.getMessage(), e);
              }
            }
          });
    } catch (RejectedExecutionException e) {
      for (ConnectionStateListener listener : listeners) {
        try {
          listener.onConnected();
        } catch (Exception ex) {
          LOG.error("Error in connection state listener: {}", ex.getMessage(), ex);
        }
      }
    }
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
