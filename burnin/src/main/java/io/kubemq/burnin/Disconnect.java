// Forced disconnect manager: close client, wait configured duration, recreate.
// Increments forced_disconnects counter on each disconnect cycle.

package io.kubemq.burnin;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interface for objects that can close and recreate the KubeMQ client connection.
 */
interface ClientRecreator {

    /**
     * Close the current client connection. Should not throw.
     */
    void closeClient();

    /**
     * Recreate and reconnect the client. Should not throw.
     */
    void recreateClient();
}

/**
 * Manages forced disconnections at a configurable interval and duration.
 * Calls closeClient(), waits the configured duration, then calls recreateClient().
 * Increments the burnin_forced_disconnects_total counter on each cycle.
 */
final class DisconnectManager {

    private final double intervalSec;
    private final double durationSec;
    private final ClientRecreator recreator;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread loopThread;

    /**
     * Create a disconnect manager.
     *
     * @param intervalSec Seconds between forced disconnections. 0 = disabled.
     * @param durationSec Seconds to remain disconnected.
     * @param recreator   The client recreator to call during disconnect cycles.
     */
    public DisconnectManager(double intervalSec, double durationSec, ClientRecreator recreator) {
        this.intervalSec = intervalSec;
        this.durationSec = durationSec;
        this.recreator = recreator;
    }

    /**
     * Whether forced disconnection is enabled (interval > 0).
     */
    public boolean isEnabled() {
        return intervalSec > 0;
    }

    /**
     * Start the disconnect cycle loop. Does nothing if not enabled.
     */
    public void start() {
        if (!isEnabled()) return;
        if (!running.compareAndSet(false, true)) return;

        loopThread = new Thread(this::runLoop, "burnin-disconnect-manager");
        loopThread.setDaemon(true);
        loopThread.start();
    }

    /**
     * Stop the disconnect cycle loop gracefully.
     */
    public void stop() {
        running.set(false);
        Thread t = loopThread;
        if (t != null) {
            t.interrupt();
            try {
                t.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void runLoop() {
        while (running.get()) {
            // Wait for the configured interval before disconnecting.
            try {
                Thread.sleep((long) (intervalSec * 1000));
            } catch (InterruptedException e) {
                break;
            }

            if (!running.get()) break;

            disconnectCycle();
        }
    }

    private void disconnectCycle() {
        System.out.println("forced disconnect: closing client");
        Metrics.incForcedDisconnects();

        try {
            recreator.closeClient();
        } catch (Exception ex) {
            System.err.println("disconnect close error: " + ex.getMessage());
        }

        // Wait the configured disconnect duration.
        try {
            Thread.sleep((long) (durationSec * 1000));
        } catch (InterruptedException e) {
            return;
        }

        if (!running.get()) return;

        System.out.println("forced disconnect: recreating client");

        try {
            recreator.recreateClient();
        } catch (Exception ex) {
            System.err.println("disconnect recreate error: " + ex.getMessage());
        }
    }
}
