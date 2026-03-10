package io.kubemq.sdk.client;

import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handle for a live subscription. Allows cancellation from any thread.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. {@link #cancel()} and
 * {@link #cancelAsync()} can be called from any thread. Cancellation is idempotent.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * Subscription sub = pubSubClient.subscribeToEventsWithHandle(subscription);
 * // ... later, from any thread:
 * sub.cancel();  // synchronous cancel
 * // or:
 * sub.cancelAsync().thenRun(() -> System.out.println("Unsubscribed"));
 * }</pre>
 */
@ThreadSafe
public class Subscription {

    private final Runnable cancelAction;
    private final Executor executor;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * @param cancelAction the action to perform when cancelling
     * @param executor     the executor to use for async cancellation
     */
    public Subscription(Runnable cancelAction, Executor executor) {
        this.cancelAction = cancelAction;
        this.executor = executor;
    }

    /**
     * Convenience constructor using ForkJoinPool.commonPool().
     *
     * @param cancelAction the action to perform when cancelling
     */
    public Subscription(Runnable cancelAction) {
        this(cancelAction, ForkJoinPool.commonPool());
    }

    /**
     * Cancel the subscription synchronously.
     * Idempotent -- calling multiple times is safe.
     */
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            cancelAction.run();
        }
    }

    /**
     * Cancel the subscription asynchronously.
     *
     * @return a CompletableFuture that completes when the cancellation is done
     */
    public CompletableFuture<Void> cancelAsync() {
        return CompletableFuture.runAsync(this::cancel, executor);
    }

    /**
     * @return true if this subscription has been cancelled
     */
    public boolean isCancelled() {
        return cancelled.get();
    }
}
