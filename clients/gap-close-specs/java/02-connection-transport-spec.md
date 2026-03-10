# Gap-Close Specification: Category 02 — Connection & Transport

**SDK:** KubeMQ Java v2
**Version Assessed:** v2.1.1
**Current Score:** 3.14 / 5.0
**Target Score:** 4.0+
**Golden Standard:** `clients/golden-standard/02-connection-transport.md`
**Gap Research:** `clients/gap-research/java-gap-research.md` (Category 02, lines 319-470)
**Assessment:** `clients/assesments/JAVA_ASSESSMENT_REPORT.md` (Category 3, lines 224-293)
**Date:** 2026-03-09

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [REQ-CONN-1: Auto-Reconnection with Buffering](#2-req-conn-1-auto-reconnection-with-buffering)
3. [REQ-CONN-2: Connection State Machine](#3-req-conn-2-connection-state-machine)
4. [REQ-CONN-3: gRPC Keepalive Configuration](#4-req-conn-3-grpc-keepalive-configuration)
5. [REQ-CONN-4: Graceful Shutdown / Drain](#5-req-conn-4-graceful-shutdown--drain)
6. [REQ-CONN-5: Connection Configuration](#6-req-conn-5-connection-configuration)
7. [REQ-CONN-6: Connection Reuse](#7-req-conn-6-connection-reuse)
8. [Cross-Category Dependencies](#8-cross-category-dependencies)
9. [Implementation Order](#9-implementation-order)
10. [GS Internal Inconsistencies](#10-gs-internal-inconsistencies)
11. [Future Enhancements](#11-future-enhancements)

---

## 1. Executive Summary

| REQ | Status | Effort | Priority | Summary |
|-----|--------|--------|----------|---------|
| REQ-CONN-1 | PARTIAL | L (3-5 days) | P0 | No buffering, no DNS re-resolve, no queue stream reconnect, no jitter, EventsStore recovery semantics wrong |
| REQ-CONN-2 | MISSING | M (1-2 days) | P0 | No state machine, no public state query, no state callbacks |
| REQ-CONN-3 | PARTIAL | S (< 1 day) | P0 | Defaults wrong (60s/30s vs 10s/5s) |
| REQ-CONN-4 | PARTIAL | M (1-2 days) | P0 | No configurable timeout, no post-close guard, no buffer flush, no idempotency |
| REQ-CONN-5 | PARTIAL | M (1-2 days) | P0 | No connection timeout, no WaitForReady, no fail-fast validation |
| REQ-CONN-6 | PARTIAL | S (< 1 day) | P2 | Missing thread-safety documentation only |

**Total estimated effort:** 8-12 days

**Implementation dependency chain:**
```
REQ-CONN-3 (no deps) ─┐
REQ-CONN-5 (no deps) ─┤
REQ-CONN-6 (no deps) ─┤
REQ-CONN-2 (no deps) ─┼──> REQ-CONN-1 (depends on CONN-2, ERR-1) ──> REQ-CONN-4 (depends on CONN-1, CONN-2)
```

---

## 2. REQ-CONN-1: Auto-Reconnection with Buffering

**Status:** PARTIAL | **Priority:** P0 | **Effort:** L (3-5 days)

### 2.1 Current State

**Source files examined:**

| File | Path | Relevant Lines |
|------|------|----------------|
| KubeMQClient | `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java` | Lines 327-346 (state listener), 370-384 (close) |
| EventsSubscription | `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsSubscription.java` | Lines 156-189 (reconnect) |
| EventsStoreSubscription | `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsStoreSubscription.java` | Lines 200-230 (reconnect) |
| CommandsSubscription | `kubemq-java/src/main/java/io/kubemq/sdk/cq/CommandsSubscription.java` | Lines 116-149 (reconnect) |
| QueueUpstreamHandler | `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueUpstreamHandler.java` | Lines 96-98 (onError -- no reconnect) |
| QueueDownstreamHandler | `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueDownstreamHandler.java` | Lines 97-99 (onError -- no reconnect) |

**What exists:**
- gRPC channel-level: `addChannelStateListener()` on `TRANSIENT_FAILURE` calls `resetConnectBackoff()` (KubeMQClient lines 327-346)
- Subscription-level reconnection: exponential backoff `Math.min(base * 2^(attempt-1), 60000)` in each subscription class
- Max 10 attempts hardcoded, capped at 60s, no jitter
- Reconnect counter reset on success

**What is missing or broken:**
1. **No message buffering** -- queue upstream/downstream handlers complete all pending futures with error on stream failure (QueueUpstreamHandler line 120-132, QueueDownstreamHandler line 125-140)
2. **No queue stream reconnection** -- both handlers set `isConnected = false` but never attempt reconnection
3. **No jitter** on backoff (collision risk during reconnect storms)
4. **No DNS re-resolution** -- `forTarget()` is used (which does support re-resolution), but no forced invalidation of cached addresses
5. **EventsStore recovery semantics wrong** -- `EventsStoreSubscription.reconnect()` (line 221) resubscribes using cached `this.subscribe` which contains the *original* `StartFrom` parameters, not `StartFromSequence(lastSeq+1)` as required by GS
6. **No stream vs. connection error distinction** -- all `StatusRuntimeException` instances trigger reconnection, including server-initiated stream closures that should trigger re-subscribe only
7. **No buffer overflow policy** (error vs block)
8. **No `OnBufferDrain` callback**
9. **No backoff reset after successful reconnection at the state machine level** (subscription-level reset exists)
10. **No operation retry suspension during RECONNECTING** (no state machine exists)

### 2.2 Acceptance Criteria Mapping

| # | GS Acceptance Criterion | Current Status | Implementation Required |
|---|------------------------|----------------|------------------------|
| AC-1 | Connection drops detected within keepalive timeout (default 15s) | PARTIAL | Fix defaults per REQ-CONN-3 |
| AC-2 | Reconnection starts automatically with exponential backoff | PARTIAL | Add jitter, add queue stream reconnect |
| AC-3 | Messages buffered during reconnection | MISSING | Implement `MessageBuffer` |
| AC-4 | Subscriptions restored per recovery semantics table | PARTIAL | Fix EventsStore recovery; add queue stream reconnect |
| AC-5 | Buffer overflow configurable (error vs block) | MISSING | Implement `BufferOverflowPolicy` |
| AC-6 | Reconnection attempts logged at INFO | PARTIAL | Verify log levels |
| AC-7 | Successful reconnection logged at INFO | PARTIAL | Verify log levels |
| AC-8 | DNS re-resolved on each reconnection attempt | MISSING | Force DNS re-resolution |
| AC-9 | Backoff reset after successful reconnection | MISSING | Integrate with state machine |
| AC-10 | Operation retries suspended during RECONNECTING | MISSING | Integrate with state machine (REQ-CONN-2) |
| AC-11 | Stream errors distinguished from connection errors | MISSING | Add error classification |
| AC-12 | Buffered messages sent FIFO after reconnection | MISSING | Implement buffer flush |
| AC-13 | Buffered messages discarded on CLOSED with callback | MISSING | Implement OnBufferDrain |

### 2.3 Specification

#### 2.3.1 New Class: `ReconnectionConfig`

**Package:** `io.kubemq.sdk.client`
**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/ReconnectionConfig.java` (new)

```java
package io.kubemq.sdk.client;

import lombok.Builder;
import lombok.Getter;

/**
 * Configuration for automatic reconnection behavior.
 * <p>
 * All parameters have sensible defaults and are configurable via the builder.
 */
@Getter
@Builder
public class ReconnectionConfig {

    /** Maximum number of reconnection attempts. -1 = unlimited. Default: -1. */
    @Builder.Default
    private final int maxReconnectAttempts = -1;

    /** Initial delay before the first reconnection attempt. Default: 500ms. */
    @Builder.Default
    private final long initialReconnectDelayMs = 500;

    /** Maximum delay between reconnection attempts. Default: 30_000ms (30s). */
    @Builder.Default
    private final long maxReconnectDelayMs = 30_000;

    /** Backoff multiplier applied after each failed attempt. Default: 2.0. */
    @Builder.Default
    private final double reconnectBackoffMultiplier = 2.0;

    /** Enable full jitter on backoff delays. Default: true. */
    @Builder.Default
    private final boolean reconnectJitterEnabled = true;

    /** Maximum size of the reconnection message buffer in bytes. Default: 8MB. */
    @Builder.Default
    private final long reconnectBufferSizeBytes = 8 * 1024 * 1024;

    /** Buffer overflow policy. Default: ERROR. */
    @Builder.Default
    private final BufferOverflowPolicy bufferOverflowPolicy = BufferOverflowPolicy.ERROR;
}
```

#### 2.3.2 New Enum: `BufferOverflowPolicy`

**Package:** `io.kubemq.sdk.client`
**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/BufferOverflowPolicy.java` (new)

```java
package io.kubemq.sdk.client;

/**
 * Policy for handling buffer overflow during reconnection.
 */
public enum BufferOverflowPolicy {
    /**
     * Return a BackpressureException immediately when the buffer is full.
     * This is non-blocking and the default policy.
     */
    ERROR,

    /**
     * Block the calling thread until space becomes available in the buffer.
     * Use with caution -- this can cause thread starvation if reconnection is slow.
     */
    BLOCK
}
```

#### 2.3.3 New Class: `MessageBuffer`

**Package:** `io.kubemq.sdk.client`
**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/MessageBuffer.java` (new)

```java
package io.kubemq.sdk.client;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntConsumer;

/**
 * Bounded FIFO buffer for messages published during reconnection.
 * Thread-safe. Messages are buffered while the client is in RECONNECTING state
 * and flushed in FIFO order when the connection is re-established.
 */
public class MessageBuffer {

    private final ConcurrentLinkedQueue<BufferedMessage> queue = new ConcurrentLinkedQueue<>();
    private final AtomicLong currentSizeBytes = new AtomicLong(0);
    private final long maxSizeBytes;
    private final BufferOverflowPolicy overflowPolicy;

    // Used only for BLOCK policy
    private final ReentrantLock blockLock = new ReentrantLock();
    private final Condition spaceAvailable = blockLock.newCondition();

    private volatile IntConsumer onBufferDrainCallback;

    public MessageBuffer(long maxSizeBytes, BufferOverflowPolicy overflowPolicy) {
        this.maxSizeBytes = maxSizeBytes;
        this.overflowPolicy = overflowPolicy;
    }

    /**
     * Register a callback invoked when buffered messages are discarded.
     * The callback receives the count of discarded messages.
     */
    public void setOnBufferDrainCallback(IntConsumer callback) {
        this.onBufferDrainCallback = callback;
    }

    /**
     * Add a message to the buffer.
     *
     * @param message the message to buffer
     * @throws BackpressureException if policy is ERROR and buffer is full
     * @throws InterruptedException if policy is BLOCK and thread is interrupted while waiting
     */
    public void add(BufferedMessage message) throws InterruptedException {
        long messageSize = message.estimatedSizeBytes();

        if (overflowPolicy == BufferOverflowPolicy.BLOCK) {
            blockLock.lock();
            try {
                while (currentSizeBytes.get() + messageSize > maxSizeBytes) {
                    spaceAvailable.await();
                }
                enqueue(message, messageSize);
            } finally {
                blockLock.unlock();
            }
        } else {
            // ERROR policy: use CAS loop to atomically check-and-reserve space.
            // This prevents concurrent threads from both passing the check and
            // exceeding maxSizeBytes by up to N * maxMessageSize.
            while (true) {
                long current = currentSizeBytes.get();
                if (current + messageSize > maxSizeBytes) {
                    // Use helper method that works with both stub and full implementation.
                    // During stub phase: BackpressureException(String) constructor is acceptable.
                    // After REQ-ERR-1: builder pattern with ErrorCode.BUFFER_FULL is used.
                    throw BackpressureException.bufferFull(current, maxSizeBytes, messageSize);
                }
                if (currentSizeBytes.compareAndSet(current, current + messageSize)) {
                    queue.add(message);
                    break;
                }
                // CAS failed due to concurrent update -- retry
            }
        }
    }

    private void enqueue(BufferedMessage message, long messageSize) {
        queue.add(message);
        currentSizeBytes.addAndGet(messageSize);
    }

    /**
     * Drain all buffered messages in FIFO order.
     * Called after successful reconnection.
     *
     * @param sender the function to send each message
     */
    public void flush(java.util.function.Consumer<BufferedMessage> sender) {
        BufferedMessage msg;
        while ((msg = queue.poll()) != null) {
            long size = msg.estimatedSizeBytes();
            currentSizeBytes.addAndGet(-size);
            sender.accept(msg);
            if (overflowPolicy == BufferOverflowPolicy.BLOCK) {
                blockLock.lock();
                try {
                    spaceAvailable.signalAll();
                } finally {
                    blockLock.unlock();
                }
            }
        }
    }

    /**
     * Discard all buffered messages. Invokes OnBufferDrain callback with count.
     * Called when transitioning to CLOSED state.
     */
    public void discardAll() {
        int count = 0;
        while (queue.poll() != null) {
            count++;
        }
        currentSizeBytes.set(0);

        if (overflowPolicy == BufferOverflowPolicy.BLOCK) {
            blockLock.lock();
            try {
                spaceAvailable.signalAll();
            } finally {
                blockLock.unlock();
            }
        }

        if (count > 0 && onBufferDrainCallback != null) {
            onBufferDrainCallback.accept(count);
        }
    }

    public int size() {
        return queue.size();
    }

    public long currentSizeBytes() {
        return currentSizeBytes.get();
    }
}
```

#### 2.3.4 New Interface: `BufferedMessage`

**Package:** `io.kubemq.sdk.client`
**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/BufferedMessage.java` (new)

```java
package io.kubemq.sdk.client;

/**
 * Marker interface for messages that can be buffered during reconnection.
 * Implementations must provide a size estimate for buffer capacity tracking.
 */
public interface BufferedMessage {
    /**
     * @return estimated size of this message in bytes, used for buffer capacity tracking
     */
    long estimatedSizeBytes();

    /**
     * @return the gRPC request object to resend after reconnection
     */
    Object grpcRequest();

    /**
     * @return the message type for routing during flush via sendBufferedMessage()
     */
    MessageType messageType();

    /** Discriminator for routing buffered messages to the correct send path. */
    enum MessageType { EVENT, EVENT_STORE, QUEUE }
}
```

#### 2.3.5 Buffer Full Exception

**Resolution:** Do NOT create a separate `BufferFullException` class. Use `BackpressureException` from the error hierarchy (01-error-handling-spec.md, section 3.2.4) with `ErrorCode.BUFFER_FULL`. This avoids having two exception types for the same semantic error and maintains the single-hierarchy invariant from REQ-ERR-1.

**If REQ-ERR-1 is implemented first (recommended):** Use `BackpressureException` directly:
```java
import io.kubemq.sdk.exception.BackpressureException;
import io.kubemq.sdk.exception.ErrorCode;

throw BackpressureException.builder()
    .code(ErrorCode.BUFFER_FULL)
    .message("Reconnection buffer full (" + current + "/" + maxSizeBytes + " bytes)")
    .operation("buffer.add")
    .retryable(false)
    .build();
```

**If REQ-ERR-1 is NOT yet implemented:** Create a minimal stub that already extends `KubeMQException` (or at minimum `RuntimeException` with a TODO):
```java
package io.kubemq.sdk.exception;

/**
 * Stub for BackpressureException -- will be replaced by REQ-ERR-1 full implementation.
 * Thrown when the reconnection message buffer is full and the overflow policy is ERROR.
 */
public class BackpressureException extends RuntimeException {
    public BackpressureException(String message) {
        super(message);
    }

    /**
     * Factory method that works with both the stub and the full REQ-ERR-1 implementation.
     * When REQ-ERR-1 is implemented, this method should use the builder:
     *   BackpressureException.builder().code(ErrorCode.BUFFER_FULL).message(...).build()
     */
    public static BackpressureException bufferFull(long currentBytes, long maxBytes, long messageSize) {
        return new BackpressureException(
            "Reconnection buffer full (" + currentBytes + "/" + maxBytes
            + " bytes). Message size: " + messageSize);
    }
}
```

**Required import in MessageBuffer.java:**
```java
import io.kubemq.sdk.exception.BackpressureException;
```

#### 2.3.6 New Class: `ReconnectionManager`

**Package:** `io.kubemq.sdk.client`
**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/ReconnectionManager.java` (new)

This class centralizes all reconnection logic currently scattered across `EventsSubscription`, `EventsStoreSubscription`, `CommandsSubscription`, and `QueriesSubscription`.

```java
package io.kubemq.sdk.client;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages connection-level reconnection with exponential backoff + jitter.
 * Integrates with the ConnectionState machine and MessageBuffer.
 *
 * <p>This class replaces the per-subscription reconnection logic currently in
 * EventsSubscription, EventsStoreSubscription, CommandsSubscription, and
 * QueriesSubscription with a centralized, connection-level approach.</p>
 */
public class ReconnectionManager {

    private static final Logger log = LoggerFactory.getLogger(ReconnectionManager.class);
    // NOTE: Do NOT store ThreadLocalRandom.current() as a static field.
    // ThreadLocalRandom instances are thread-local and must not be shared across threads.
    // Use ThreadLocalRandom.current() at the call site in computeDelay() instead.

    private final ReconnectionConfig config;
    private final ConnectionStateMachine stateMachine;
    private final AtomicInteger attemptCounter = new AtomicInteger(0);
    private final ScheduledExecutorService scheduler;

    private volatile ScheduledFuture<?> pendingReconnect;

    public ReconnectionManager(ReconnectionConfig config, ConnectionStateMachine stateMachine) {
        this.config = config;
        this.stateMachine = stateMachine;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kubemq-reconnect-manager");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Start the reconnection cycle. Called when a connection loss is detected.
     *
     * @param reconnectAction the action to perform on each attempt (e.g., re-create channel)
     */
    public void startReconnection(Runnable reconnectAction) {
        int attempt = attemptCounter.incrementAndGet();
        int maxAttempts = config.getMaxReconnectAttempts();

        if (maxAttempts != -1 && attempt > maxAttempts) {
            log.error("Max reconnection attempts ({}) exhausted", maxAttempts);
            stateMachine.transitionTo(ConnectionState.CLOSED);
            return;
        }

        long delay = computeDelay(attempt);
        log.info("Scheduling reconnection attempt {}{} in {}ms",
                 attempt,
                 maxAttempts == -1 ? "" : "/" + maxAttempts,
                 delay);

        stateMachine.transitionTo(ConnectionState.RECONNECTING);

        pendingReconnect = scheduler.schedule(() -> {
            try {
                reconnectAction.run();
                attemptCounter.set(0); // Reset on success
                stateMachine.transitionTo(ConnectionState.READY);
                log.info("Reconnection successful after {} attempts", attempt);
            } catch (Exception e) {
                log.warn("Reconnection attempt {} failed: {}", attempt, e.getMessage());
                startReconnection(reconnectAction); // Schedule next attempt
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Cancel any pending reconnection attempt. Called during graceful shutdown.
     */
    public void cancel() {
        if (pendingReconnect != null) {
            pendingReconnect.cancel(false);
        }
        attemptCounter.set(0);
    }

    /**
     * Shutdown the scheduler. Called during client close.
     */
    public void shutdown() {
        cancel();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private long computeDelay(int attempt) {
        double baseDelay = config.getInitialReconnectDelayMs()
            * Math.pow(config.getReconnectBackoffMultiplier(), attempt - 1);
        long cappedDelay = (long) Math.min(baseDelay, config.getMaxReconnectDelayMs());

        // Minimum floor to prevent scheduler flooding when jitter returns 0
        long minDelay = config.getInitialReconnectDelayMs() / 2;

        if (config.isReconnectJitterEnabled()) {
            // Full jitter: uniform random in [0, cappedDelay], with minimum floor
            long jitteredDelay = ThreadLocalRandom.current().nextLong(cappedDelay + 1);
            return Math.max(minDelay, jitteredDelay);
        }
        return cappedDelay;
    }
}
```

#### 2.3.7 Subscription Recovery Semantics — Required Changes

**EventsStoreSubscription** (file: `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsStoreSubscription.java`)

The current `reconnect()` method (line 200) resubscribes using cached `this.subscribe` which contains the original `StartFrom` parameters. Per GS, this is incorrect.

**Required change:** Track last received sequence number and re-subscribe with `StartFromSequence(lastSeq + 1)`.

Add field:
```java
private final AtomicLong lastReceivedSequence = new AtomicLong(-1);
```

In the `StreamObserver.onNext()` handler (currently line 174), after decoding the message, update:
```java
@Override
public void onNext(Kubemq.EventReceive messageReceive) {
    long sequence = messageReceive.getSequence();
    lastReceivedSequence.set(sequence); // Track for reconnection
    raiseOnReceiveMessage(EventStoreMessageReceived.decode(messageReceive));
}
```

In the `reconnect()` method, build a new subscribe request using `lastReceivedSequence`:
```java
private Kubemq.Subscribe buildReconnectSubscribe(String clientId) {
    long lastSeq = lastReceivedSequence.get();
    Kubemq.Subscribe.Builder builder = Kubemq.Subscribe.newBuilder()
        .setSubscribeTypeData(Kubemq.Subscribe.SubscribeType.forNumber(
            SubscribeType.EventsStore.getValue()))
        .setClientID(clientId)
        .setChannel(channel)
        .setGroup(Optional.ofNullable(group).orElse(""));

    if (lastSeq >= 0) {
        // Reconnection: resume from last received + 1
        builder.setEventsStoreTypeData(Kubemq.Subscribe.EventsStoreType.forNumber(
            EventsStoreType.StartAtSequence.getValue()))
            .setEventsStoreTypeValue((int) (lastSeq + 1));
    } else {
        // First connection: use original parameters
        builder.setEventsStoreTypeData(Kubemq.Subscribe.EventsStoreType.forNumber(
            eventsStoreType == null ? 0 : eventsStoreType.getValue()))
            .setEventsStoreTypeValue(eventsStoreStartTime != null
                ? (int) eventsStoreStartTime.getEpochSecond()
                : eventsStoreSequenceValue);
    }

    return builder.build();
}
```

**Queue stream handlers** (files: `QueueUpstreamHandler.java`, `QueueDownstreamHandler.java`)

Currently, both handlers complete all pending futures with error on stream failure (upstream line 120-132, downstream line 125-140) and set `isConnected = false` but never attempt reconnection.

**Required change:** Add reconnection logic to both handlers. On `onError()`:

1. Set `isConnected = false`
2. If the error is a connection-level error (determined by error classification from section 2.3.8), notify the `ReconnectionManager`
3. If the error is a stream-level error, attempt to re-establish the stream on the existing connection
4. Buffer outgoing messages during reconnection (upstream handler only)

For `QueueUpstreamHandler.onError()` (currently line 95-98):
```java
@Override
public void onError(Throwable t) {
    log.error("Error in upstream stream: {}", t.getMessage());
    if (isStreamError(t)) {
        // Stream-level: re-establish stream on existing connection
        reestablishStream();
    } else {
        // Connection-level: delegate to ReconnectionManager
        isConnected.set(false);
        // Do NOT complete pending futures with error yet -- they will be
        // retried after reconnection or timed out
    }
}
```

For `QueueDownstreamHandler`, unacked messages will return to the queue via visibility timeout (GS-compliant behavior). No buffering needed for downstream.

#### 2.3.8 Stream vs. Connection Error Classification

Add a utility method (can be in `ReconnectionManager` or a shared utility):

```java
/**
 * Distinguish stream-level errors from connection-level errors.
 *
 * Stream errors: server-initiated stream closure (CANCELLED, UNAVAILABLE with
 * stream-specific metadata, NOT_FOUND for deleted channels).
 *
 * Connection errors: transport failure (UNAVAILABLE without stream metadata,
 * DEADLINE_EXCEEDED at transport level).
 */
static boolean isStreamError(Throwable t) {
    if (!(t instanceof io.grpc.StatusRuntimeException)) {
        return false; // Unknown error type -- treat as connection error
    }
    io.grpc.StatusRuntimeException sre = (io.grpc.StatusRuntimeException) t;
    io.grpc.Status.Code code = sre.getStatus().getCode();

    switch (code) {
        case CANCELLED:
        case NOT_FOUND:
            return true; // Server closed stream or channel deleted
        case UNAVAILABLE:
            // UNAVAILABLE can be either stream or connection.
            // Check if the channel itself is still in READY state.
            // If channel is READY, this is a stream-level error.
            // This check is done by the caller using ManagedChannel.getState().
            return false; // Caller must check channel state
        default:
            return false; // Treat as connection error
    }
}
```

#### 2.3.9 DNS Re-Resolution

The current code already uses `ManagedChannelBuilder.forTarget(address)` (KubeMQClient line 273, 299), which leverages gRPC's built-in DNS resolver. However, gRPC caches DNS results by default.

**Required change:** Configure the channel to use a custom `NameResolverProvider` that forces re-resolution, or use the `dns:///` URI scheme which triggers gRPC's built-in DNS re-resolution on reconnection:

In `KubeMQClient.initChannel()`, change the address format:
```java
// If address doesn't already have a scheme, prepend dns:/// to force DNS re-resolution
String resolvedAddress = address;
if (!address.contains("://")) {
    resolvedAddress = "dns:///" + address;
}
```

This ensures that on each reconnection attempt, the DNS resolver re-queries the address rather than using cached IP addresses.

#### 2.3.10 Removing Per-Subscription Reconnection

After implementing the centralized `ReconnectionManager`, remove the following duplicated reconnection code:

| File | Lines to Remove | Replacement |
|------|----------------|-------------|
| `EventsSubscription.java` | Lines 25-26 (`MAX_RECONNECT_ATTEMPTS`, `reconnectExecutor`), 33-38 (executor accessor), 39 (`reconnectAttempts`), 156-189 (`reconnect()`) | Delegate to `ReconnectionManager` |
| `EventsStoreSubscription.java` | Lines 31-32, 39-44, 39, 200-230 | Delegate to `ReconnectionManager` |
| `CommandsSubscription.java` | Lines 22-23, 30-35, 30, 116-149 | Delegate to `ReconnectionManager` |
| `QueriesSubscription.java` | Equivalent lines (same pattern) | Delegate to `ReconnectionManager` |

Each subscription's `onError()` handler will instead call:
```java
@Override
public void onError(Throwable t) {
    log.error("Subscription error on channel {}: {}", channel, t.getMessage());
    raiseOnError(t.getMessage());
    // Delegate reconnection to centralized manager
    // The manager checks stream vs connection error and acts accordingly
}
```

The subscription re-establishment is triggered by the `ReconnectionManager` after a successful connection-level reconnection, using callbacks registered by each subscription.

### 2.4 Breaking Changes

- **None for public API.** The `reconnectIntervalSeconds` builder parameter will be deprecated in favor of `ReconnectionConfig` but will continue to work with backward-compatible mapping.
- **Internal behavioral change:** Queue upstream operations that previously failed immediately on stream error will now be buffered (transparently to the caller, except the result may arrive later).

### 2.5 Test Scenarios

| # | Scenario | Type | Assertion |
|---|----------|------|-----------|
| T1 | Subscription reconnects after connection drop | Integration | Subscription receives messages after simulated network interruption |
| T2 | EventsStore resumes from lastSeq+1 after reconnect | Integration | No duplicate messages; sequence continuity verified |
| T3 | Queue upstream buffers messages during reconnect | Integration | Messages sent during outage are delivered after reconnection, in FIFO order |
| T4 | Buffer overflow returns BackpressureException (ERROR policy) | Unit | `BackpressureException` with `ErrorCode.BUFFER_FULL` thrown when buffer exceeds `reconnectBufferSizeBytes` |
| T5 | Buffer overflow blocks (BLOCK policy) | Unit | Calling thread blocks until space available; verify with timeout |
| T6 | OnBufferDrain callback fires on close during RECONNECTING | Unit | Callback invoked with correct count of discarded messages |
| T7 | DNS re-resolution on reconnect | Integration | After server IP change, client reconnects to new IP |
| T8 | Backoff has jitter (delay is not deterministic) | Unit | Multiple delay computations for same attempt produce different values |
| T9 | Backoff resets after successful reconnection | Unit | After reconnect success, next failure starts from initial delay |
| T10 | Max reconnect attempts exhausted transitions to CLOSED | Unit | State machine enters CLOSED after configured max attempts |
| T11 | Stream error triggers re-subscribe, not reconnect | Unit | Stream closure on READY channel re-subscribes without full reconnection |
| T12 | Queue downstream unacked messages return via visibility timeout | Integration | Messages not acked during outage become available to other consumers |

---

## 3. REQ-CONN-2: Connection State Machine

**Status:** MISSING | **Priority:** P0 | **Effort:** M (1-2 days)

### 3.1 Current State

**Source files examined:**
- `KubeMQClient.java` lines 327-346: internal `notifyWhenStateChanged()` on `TRANSIENT_FAILURE` and `SHUTDOWN` only logs/resets backoff
- No public method for querying connection state (assessment 3.4.3)
- No callback registration API (assessment 3.2.5)

**What exists:** Internal gRPC `ConnectivityState` monitoring, but not exposed publicly and does not map to the GS-required states.

**What is missing:** Everything -- no state enum, no state machine, no public query, no callbacks.

### 3.2 Acceptance Criteria Mapping

| # | GS Acceptance Criterion | Current Status | Implementation Required |
|---|------------------------|----------------|------------------------|
| AC-1 | Current state queryable via method | MISSING | Add `getConnectionState()` |
| AC-2 | State transitions fire callbacks/events | MISSING | Implement listener pattern |
| AC-3 | Handlers for OnConnected/OnDisconnected/OnReconnecting/OnReconnected/OnClosed | MISSING | Implement `ConnectionStateListener` |
| AC-4 | Handlers invoked asynchronously | MISSING | Dedicate executor for callbacks |
| AC-5 | State included in log messages during transitions | PARTIAL | Formalize log format |

### 3.3 Specification

#### 3.3.1 New Enum: `ConnectionState`

**Package:** `io.kubemq.sdk.client`
**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/ConnectionState.java` (new)

```java
package io.kubemq.sdk.client;

/**
 * Represents the connection state of a KubeMQ client.
 *
 * <pre>
 * IDLE ──> CONNECTING ──> READY
 *   ^          |             |
 *   |          v             v
 *   |    RECONNECTING ──> READY
 *   |          |
 *   v          v
 *        CLOSED (terminal)
 * </pre>
 */
public enum ConnectionState {
    /** Created but not yet connected. */
    IDLE,
    /** Initial connection in progress. */
    CONNECTING,
    /** Connected and operational. */
    READY,
    /** Connection lost, attempting to reconnect. */
    RECONNECTING,
    /** Permanently closed (terminal state). */
    CLOSED
}
```

#### 3.3.2 New Interface: `ConnectionStateListener`

**Package:** `io.kubemq.sdk.client`
**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/ConnectionStateListener.java` (new)

```java
package io.kubemq.sdk.client;

/**
 * Listener for connection state transitions.
 * All methods have default no-op implementations, allowing users to override
 * only the events they care about.
 *
 * <p>Listeners are invoked asynchronously on a dedicated executor and must not
 * block the connection.</p>
 */
public interface ConnectionStateListener {

    /** Invoked when the client establishes its initial connection. */
    default void onConnected() {}

    /** Invoked when the connection is lost. */
    default void onDisconnected() {}

    /**
     * Invoked when a reconnection attempt starts.
     * @param attempt the current attempt number (1-based)
     */
    default void onReconnecting(int attempt) {}

    /** Invoked when a reconnection attempt succeeds. */
    default void onReconnected() {}

    /** Invoked when the client is permanently closed. */
    default void onClosed() {}
}
```

#### 3.3.3 New Class: `ConnectionStateMachine`

**Package:** `io.kubemq.sdk.client`
**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/ConnectionStateMachine.java` (new)

```java
package io.kubemq.sdk.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe connection state machine with asynchronous listener notification.
 */
public class ConnectionStateMachine {

    private static final Logger log = LoggerFactory.getLogger(ConnectionStateMachine.class);

    private final AtomicReference<ConnectionState> state =
        new AtomicReference<>(ConnectionState.IDLE);
    private final List<ConnectionStateListener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService listenerExecutor;

    // Track reconnection attempt for onReconnecting callback
    private volatile int currentReconnectAttempt = 0;

    public ConnectionStateMachine() {
        this.listenerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "kubemq-state-listener");
            t.setDaemon(true);
            return t;
        });
    }

    /** @return the current connection state */
    public ConnectionState getState() {
        return state.get();
    }

    /** Register a listener for state transitions. */
    public void addListener(ConnectionStateListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /** Remove a previously registered listener. */
    public void removeListener(ConnectionStateListener listener) {
        listeners.remove(listener);
    }

    /**
     * Transition to a new state. Invalid transitions are logged and ignored.
     * Listeners are notified asynchronously.
     */
    public void transitionTo(ConnectionState newState) {
        // CAS loop instead of recursion to avoid StackOverflowError under contention
        while (true) {
            ConnectionState oldState = state.get();

            if (oldState == ConnectionState.CLOSED) {
                log.warn("Cannot transition from CLOSED to {} -- CLOSED is terminal", newState);
                return;
            }

            if (oldState == newState) {
                return; // No-op for same state
            }

            // Validate transition against allowed state transitions
            if (!isValidTransition(oldState, newState)) {
                log.warn("Invalid state transition: {} -> {} (rejected)", oldState, newState);
                return;
            }

            if (!state.compareAndSet(oldState, newState)) {
                // CAS failed due to concurrent modification -- loop and retry
                continue;
            }

            log.info("Connection state: {} -> {}", oldState, newState);

            // Fire callbacks asynchronously
            listenerExecutor.submit(() -> {
                for (ConnectionStateListener listener : listeners) {
                    try {
                        switch (newState) {
                            case CONNECTING:
                                // No specific callback for CONNECTING
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
                        log.error("Error in connection state listener: {}", e.getMessage(), e);
                    }
                }
            });

            break; // Exit CAS loop after successful transition
        } // end while(true)
    }

    /**
     * Validates state transitions against the allowed state diagram:
     * IDLE -> CONNECTING, CONNECTING -> READY, CONNECTING -> CLOSED,
     * READY -> RECONNECTING, READY -> CLOSED,
     * RECONNECTING -> READY, RECONNECTING -> CLOSED.
     */
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
                return false; // Terminal state -- handled above
            default:
                return false;
        }
    }

    /** Update the current reconnect attempt number for listener callbacks. */
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
```

#### 3.3.4 Modifications to `KubeMQClient`

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

Add the following fields (after line 53):
```java
private final ConnectionStateMachine connectionStateMachine;
private final ReconnectionManager reconnectionManager;
private final ReconnectionConfig reconnectionConfig;
```

Add new constructor parameter and builder support:
```java
// In the constructor, add parameters:
public KubeMQClient(String address, String clientId, ...,
                    ReconnectionConfig reconnectionConfig,
                    ConnectionStateListener connectionStateListener) {
    // ... existing init ...
    this.reconnectionConfig = reconnectionConfig != null
        ? reconnectionConfig
        : ReconnectionConfig.builder().build();
    this.connectionStateMachine = new ConnectionStateMachine();
    if (connectionStateListener != null) {
        this.connectionStateMachine.addListener(connectionStateListener);
    }
    this.reconnectionManager = new ReconnectionManager(
        this.reconnectionConfig, this.connectionStateMachine);

    this.connectionStateMachine.transitionTo(ConnectionState.CONNECTING);
    initChannel();
    this.connectionStateMachine.transitionTo(ConnectionState.READY);
    // ... rest of existing init ...
}
```

Add public methods:
```java
/**
 * Returns the current connection state.
 * @return the current {@link ConnectionState}
 */
public ConnectionState getConnectionState() {
    return connectionStateMachine.getState();
}

/**
 * Register a listener for connection state transitions.
 * @param listener the listener to add
 */
public void addConnectionStateListener(ConnectionStateListener listener) {
    connectionStateMachine.addListener(listener);
}
```

Replace `addChannelStateListener()` (currently lines 327-330) and `handleStateChange()` (lines 332-346) with state machine integration:
```java
private void addChannelStateListener() {
    monitorChannelState(managedChannel.getState(false));
}

private void monitorChannelState(ConnectivityState currentState) {
    if (connectionStateMachine.getState() == ConnectionState.CLOSED) {
        return; // Stop monitoring
    }
    managedChannel.notifyWhenStateChanged(currentState, () -> {
        ConnectivityState newState = managedChannel.getState(false);
        switch (newState) {
            case TRANSIENT_FAILURE:
                log.debug("gRPC channel TRANSIENT_FAILURE detected");
                managedChannel.resetConnectBackoff();
                reconnectionManager.startReconnection(this::reconnectChannel);
                break;
            case SHUTDOWN:
                log.debug("gRPC channel SHUTDOWN detected");
                connectionStateMachine.transitionTo(ConnectionState.CLOSED);
                break;
            case READY:
                // Connection restored at gRPC level
                break;
            default:
                break;
        }
        // Continue monitoring
        monitorChannelState(newState);
    });
}

private void reconnectChannel() {
    // Force DNS re-resolution by recreating the channel
    // (preserving the existing approach but with DNS:/// scheme)
    initChannel();
}
```

### 3.4 Breaking Changes

- **New constructor parameters** added to `KubeMQClient`. Since subclasses (`PubSubClient`, `QueuesClient`, `CQClient`) use `@Builder`, the builder pattern absorbs new optional fields without breaking existing usage.
- `addChannelStateListener()` and `handleStateChange()` are private methods -- changing their implementation is not a breaking change.

### 3.5 Test Scenarios

| # | Scenario | Type | Assertion |
|---|----------|------|-----------|
| T1 | State starts as IDLE, moves to CONNECTING, then READY | Unit | State sequence verified |
| T2 | OnConnected callback fires after initial connection | Unit | Listener receives `onConnected()` call |
| T3 | OnDisconnected fires on connection loss | Unit | Listener receives `onDisconnected()` call |
| T4 | OnReconnecting fires with attempt count | Unit | Listener receives `onReconnecting(1)`, `onReconnecting(2)`, etc. |
| T5 | OnReconnected fires after successful reconnection | Unit | Listener receives `onReconnected()` call |
| T6 | OnClosed fires on close() | Unit | Listener receives `onClosed()` call |
| T7 | `getConnectionState()` returns current state | Unit | Returns READY when connected |
| T8 | CLOSED is terminal -- transitions from CLOSED are rejected | Unit | Log warning, state unchanged |
| T9 | Listener exceptions do not crash the state machine | Unit | Bad listener throws; other listeners still notified |
| T10 | Listeners invoked asynchronously (not on caller thread) | Unit | Thread name matches `kubemq-state-listener` |

---

## 4. REQ-CONN-3: gRPC Keepalive Configuration

**Status:** PARTIAL | **Priority:** P0 | **Effort:** S (< 1 day)

### 4.1 Current State

**Source file:** `KubeMQClient.java`
- TLS path: lines 288-292 -- keepalive applied conditionally when `keepAlive != null`
- Plaintext path: lines 303-306 -- same conditional logic
- Defaults: `pingIntervalInSeconds == 0 ? 60 : pingIntervalInSeconds` (line 289), `pingTimeoutInSeconds == 0 ? 30 : pingTimeoutInSeconds` (line 290)
- `keepAliveWithoutCalls(keepAlive)` only applied when `keepAlive != null` (meaning it is *not* enabled by default)

### 4.2 Acceptance Criteria Mapping

| # | GS Acceptance Criterion | Current Status | Implementation Required |
|---|------------------------|----------------|------------------------|
| AC-1 | Keepalive enabled by default | PARTIAL | Change: enable keepalive unconditionally with defaults |
| AC-2 | All three parameters configurable | COMPLIANT | Already configurable |
| AC-3 | Dead connections detected within keepalive_time + keepalive_timeout (default 15s) | PARTIAL | Change defaults: 60s->10s interval, 30s->5s timeout |
| AC-4 | Parameters compatible with KubeMQ server enforcement | COMPLIANT | Already compatible |

### 4.3 Specification

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

**Change 1:** Default keepalive values (lines 289-291, 304-306)

Replace:
```java
pingIntervalInSeconds == 0 ? 60 : pingIntervalInSeconds
pingTimeoutInSeconds == 0 ? 30 : pingTimeoutInSeconds
```

With:
```java
pingIntervalInSeconds == 0 ? 10 : pingIntervalInSeconds
pingTimeoutInSeconds == 0 ? 5 : pingTimeoutInSeconds
```

**Change 2:** Enable keepalive by default (lines 288, 303)

Replace the conditional `if(keepAlive != null)` blocks with unconditional keepalive configuration:

For the TLS path (around line 288):
```java
// Always enable keepalive with defaults
int keepaliveTime = pingIntervalInSeconds == 0 ? 10 : pingIntervalInSeconds;
int keepaliveTimeout = pingTimeoutInSeconds == 0 ? 5 : pingTimeoutInSeconds;
boolean keepAliveWithoutCalls = keepAlive != null ? keepAlive : true;

ncb = ncb.keepAliveTime(keepaliveTime, TimeUnit.SECONDS)
          .keepAliveTimeout(keepaliveTimeout, TimeUnit.SECONDS)
          .keepAliveWithoutCalls(keepAliveWithoutCalls);
```

Apply the same pattern for the plaintext path (around line 303).

**Change 3:** Update field default documentation in Javadoc for `pingIntervalInSeconds` and `pingTimeoutInSeconds` to reflect new defaults (10s and 5s respectively).

### 4.4 Breaking Changes

- **Behavioral change:** Keepalive is now always enabled (previously only when `keepAlive` was explicitly set). This sends periodic pings that were not sent before. This is compatible with KubeMQ server and all major cloud load balancers. Clients that explicitly set `keepAlive = false` will need to adjust if they relied on zero pings.
- **Timing change:** Dead connection detection drops from 90s (60+30) to 15s (10+5). Existing code that relied on the longer detection window is unlikely, but the change should be noted in release notes.

### 4.5 Test Scenarios

| # | Scenario | Type | Assertion |
|---|----------|------|-----------|
| T1 | Default keepalive is 10s interval, 5s timeout | Unit | Channel builder receives correct values |
| T2 | Custom keepalive values are respected | Unit | Non-default values passed through |
| T3 | keepAliveWithoutCalls defaults to true | Unit | Channel builder receives `true` |
| T4 | Dead connection detected within 15s | Integration | Simulated failure detected within 15s window |

---

## 5. REQ-CONN-4: Graceful Shutdown / Drain

**Status:** PARTIAL | **Priority:** P0 | **Effort:** M (1-2 days)

### 5.1 Current State

**Source file:** `KubeMQClient.java` lines 370-384

```java
public void close() {
    if (managedChannel != null) {
        try {
            managedChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("Channel shutdown interrupted", e);
            Thread.currentThread().interrupt();
        }
    }
    int remaining = clientCount.decrementAndGet();
    if (remaining == 0) {
        log.debug("Last client closed, executors will be cleaned up on JVM shutdown");
    }
}
```

**What exists:**
- `close()` method with 5s hardcoded timeout
- JVM shutdown hook for static executor cleanup
- `AutoCloseable` implementation (try-with-resources compatible)

**What is missing:**
1. No configurable shutdown timeout
2. No `closed` flag to prevent post-close operations
3. No buffer flush before close
4. No idempotency check (`close()` can be called multiple times and will fail on second call)
5. No handling of RECONNECTING state during close
6. No `OnClosed` callback firing
7. No in-flight operation tracking

### 5.2 Acceptance Criteria Mapping

| # | GS Acceptance Criterion | Current Status | Implementation Required |
|---|------------------------|----------------|------------------------|
| AC-1 | Close()/Shutdown() initiates graceful shutdown | COMPLIANT | Already exists |
| AC-2 | Optional timeout parameter (default 5s) | PARTIAL | Make configurable |
| AC-3 | In-flight operations complete before close | PARTIAL | Add explicit tracking |
| AC-4 | Buffered messages flushed before close | MISSING | Depends on REQ-CONN-1 buffer |
| AC-5 | New operations after Close() return ErrClientClosed | MISSING | Add closed flag + guard |
| AC-6 | Close() is idempotent | NOT_ASSESSED | Add compareAndSet guard |
| AC-7 | Close() during RECONNECTING cancels and discards | MISSING | Integrate with state machine |

### 5.3 Specification

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

**New field** (add near line 37):
```java
private final AtomicBoolean closed = new AtomicBoolean(false);
```

**New builder parameter:**
```java
/** Maximum time to wait for in-flight operations during shutdown. Default: 5s. */
private int shutdownTimeoutSeconds = 5;
```

**Replace `close()` method** (lines 370-384):
```java
@Override
public void close() {
    // Idempotent: only execute shutdown once
    if (!closed.compareAndSet(false, true)) {
        return;
    }

    log.info("Initiating graceful shutdown (timeout={}s, state={})",
             shutdownTimeoutSeconds,
             connectionStateMachine.getState());

    ConnectionState currentState = connectionStateMachine.getState();

    if (currentState == ConnectionState.RECONNECTING) {
        // Cancel reconnection, discard buffered messages
        reconnectionManager.cancel();
        if (messageBuffer != null) {
            messageBuffer.discardAll(); // Fires OnBufferDrain callback
        }
    } else {
        // Normal shutdown: flush buffer first
        if (messageBuffer != null && messageBuffer.size() > 0) {
            log.info("Flushing {} buffered messages before shutdown", messageBuffer.size());
            messageBuffer.flush(this::sendBufferedMessage);
        }
    }

    // Wait for in-flight operations
    // (In-flight tracking will be added as part of this spec)

    // Close gRPC channel
    if (managedChannel != null) {
        try {
            managedChannel.shutdown()
                .awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("Channel shutdown interrupted", e);
            Thread.currentThread().interrupt();
        } finally {
            if (!managedChannel.isTerminated()) {
                managedChannel.shutdownNow();
            }
        }
    }

    // Shutdown internal executors
    reconnectionManager.shutdown();
    connectionStateMachine.transitionTo(ConnectionState.CLOSED);
    connectionStateMachine.shutdown();

    // Decrement client count
    int remaining = clientCount.decrementAndGet();
    if (remaining == 0) {
        log.debug("Last client closed");
    }
}
```

**New exception:** `ClientClosedException`

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/exception/ClientClosedException.java` (new; in `exception` package for consistency with C-3 and C-4 resolution)

```java
package io.kubemq.sdk.exception;

/**
 * Thrown when an operation is attempted on a closed client.
 * This is a non-retryable error.
 *
 * <p>Extends KubeMQException for consistency with the error hierarchy.
 * Uses KubeMQException stub if REQ-ERR-1 is not yet implemented.</p>
 */
public class ClientClosedException extends KubeMQException {
    public ClientClosedException() {
        super("Client has been closed. Create a new client instance for further operations.");
    }
}
```

**Buffered message dispatch method** to add to `KubeMQClient`:

```java
/**
 * Sends a buffered message through the appropriate client method.
 * Called by MessageBuffer.flush() during graceful shutdown or after reconnection.
 *
 * <p>BufferedMessage carries a MessageType discriminator set at buffer time
 * to route to the correct send path. Failed sends during flush are logged
 * at WARN and dropped (not re-buffered) to avoid infinite retry loops.</p>
 */
private void sendBufferedMessage(BufferedMessage msg) {
    try {
        switch (msg.messageType()) {
            case EVENT:
                getAsyncClient().sendEvent((Kubemq.Event) msg.grpcRequest(), /* observer */);
                break;
            case EVENT_STORE:
                getAsyncClient().sendEvent((Kubemq.Event) msg.grpcRequest(), /* observer */);
                break;
            case QUEUE:
                // Route through QueueUpstreamHandler's stream
                // The handler must expose a package-private send method for buffered messages
                break;
            default:
                log.warn("Unknown buffered message type: {}", msg.messageType());
        }
    } catch (Exception e) {
        log.warn("Failed to send buffered message (type={}, size={}B): {}",
                 msg.messageType(), msg.estimatedSizeBytes(), e.getMessage());
        // Drop the message -- do NOT re-buffer to avoid infinite loops
    }
}
```

**Note:** The `BufferedMessage` interface (section 2.3.4) includes the `messageType()` method and `MessageType` enum used by this dispatch method.

**Guard method** to add to `KubeMQClient`:
```java
/**
 * Check if the client is closed and throw if so.
 * Must be called at the start of every public operation method.
 */
protected void ensureNotClosed() {
    if (closed.get()) {
        throw new ClientClosedException();
    }
}
```

**Apply guard to all subclass public methods.** Each public method in `PubSubClient`, `QueuesClient`, and `CQClient` must call `ensureNotClosed()` as its first statement. Examples:

In `PubSubClient.sendEventsMessage()` (line 35):
```java
public void sendEventsMessage(EventMessage message) {
    ensureNotClosed();  // ADD THIS LINE
    try {
        // ... existing code ...
    }
}
```

In `QueuesClient.sendQueuesMessage()` (line 67):
```java
public QueueSendResult sendQueuesMessage(QueueMessage queueMessage) {
    ensureNotClosed();  // ADD THIS LINE
    queueMessage.validate();
    return this.queueUpstreamHandler.sendQueuesMessage(queueMessage);
}
```

Apply the same pattern to all public methods in `CQClient` (`sendCommandRequest`, `sendQueryRequest`, `sendResponseMessage`, `subscribeToCommands`, `subscribeToQueries`, all channel CRUD methods).

### 5.4 Breaking Changes

- **New exception type:** `ClientClosedException` will be thrown from operations attempted after `close()`. Previously, these would either succeed (if the channel hadn't fully shut down) or throw `StatusRuntimeException`. Callers catching `RuntimeException` will still work, but callers relying on specific exception types may need updating.
- **Behavioral change:** `close()` is now idempotent -- second call is a no-op. Previously, second call would try `shutdown()` on an already-shutdown channel.

### 5.5 Test Scenarios

| # | Scenario | Type | Assertion |
|---|----------|------|-----------|
| T1 | Operations after close() throw ClientClosedException | Unit | All public methods throw after close() |
| T2 | close() is idempotent | Unit | Second call is a no-op, no exception |
| T3 | close() configurable timeout is respected | Unit | Custom timeout value passed to awaitTermination |
| T4 | close() during RECONNECTING cancels reconnection | Unit | ReconnectionManager.cancel() called |
| T5 | close() during RECONNECTING discards buffer and fires callback | Unit | Buffer drained, callback invoked with count |
| T6 | close() flushes buffer during normal operation | Unit | All buffered messages sent before channel shutdown |
| T7 | OnClosed callback fires after shutdown | Unit | Listener receives onClosed() |
| T8 | close() with InterruptedException restores interrupt flag | Unit | Thread.currentThread().isInterrupted() is true |
| T9 | shutdownNow() called if awaitTermination times out | Unit | Channel forced shutdown after timeout |

---

## 6. REQ-CONN-5: Connection Configuration

**Status:** PARTIAL | **Priority:** P0 | **Effort:** M (1-2 days)

### 6.1 Current State

**Source file:** `KubeMQClient.java`

**Currently configurable:**
- `address` (String, required) -- no default value
- `clientId` (String, required)
- `maxReceiveSize` (int, default 100MB) -- line 106
- `reconnectIntervalSeconds` (int, default 1) -- line 107
- `keepAlive` (Boolean, null = disabled) -- line 108
- `pingIntervalInSeconds` (int, default 60 via fallback) -- line 109
- `pingTimeoutInSeconds` (int, default 30 via fallback) -- line 110
- TLS parameters (certFile, keyFile, caCertFile)
- `authToken`
- `logLevel`

**Not configurable (missing per GS):**
- Connection timeout (assessment 3.2.8: "No explicit connection timeout configuration")
- Max send message size (only receive size is configurable)
- WaitForReady behavior (no such concept exists in current SDK)
- Default address (`localhost:50000` per GS)

**Validation (currently):**
- `address` and `clientId` null check (line 93-95)
- TLS cert file existence (lines 209-255)
- No validation for negative timeouts, negative message sizes, empty address string

### 6.2 Acceptance Criteria Mapping

| # | GS Acceptance Criterion | Current Status | Implementation Required |
|---|------------------------|----------------|------------------------|
| AC-1 | All connection parameters configurable via builder | PARTIAL | Add connectionTimeout, maxSendMessageSize, waitForReady |
| AC-2 | Defaults match GS table | PARTIAL | Fix address default, add missing defaults |
| AC-3 | Connection timeout applies to initial connection only | MISSING | Implement initial connection timeout |
| AC-4 | Invalid config rejected at construction (fail-fast) | PARTIAL | Expand validation |
| AC-5 | WaitForReady applies to CONNECTING and RECONNECTING | MISSING | Implement SDK-level WaitForReady |

### 6.3 Specification

#### 6.3.1 New Builder Parameters

Add the following fields to `KubeMQClient`:

```java
/** Connection timeout for the initial connection. Default: 10s. Not applied to reconnection. */
private int connectionTimeoutSeconds = 10;

/** Maximum outbound gRPC message size. Default: 100MB (104857600 bytes). */
private int maxSendMessageSize = 104857600;

/**
 * When true, operations block during CONNECTING and RECONNECTING states until
 * READY or operation timeout. When false, operations fail immediately with
 * ConnectionNotReadyException. Default: true.
 */
private boolean waitForReady = true;
```

#### 6.3.2 Default Address

Change the constructor to accept `null` address with a default:

In constructor (currently line 93-95):
```java
// Replace:
if (address == null || clientId == null) {
    throw new IllegalArgumentException("Address and clientId are required");
}

// With:
if (clientId == null || clientId.isEmpty()) {
    throw new IllegalArgumentException("clientId is required and must not be empty");
}
this.address = (address == null || address.isEmpty()) ? "localhost:50000" : address;
```

#### 6.3.3 Connection Timeout

Apply `connectionTimeoutSeconds` to the initial connection using a ping with timeout:

After `initChannel()` in the constructor, add:
```java
// Validate initial connection with timeout
try {
    connectionStateMachine.transitionTo(ConnectionState.CONNECTING);
    // Use managedChannel.getState(true) to trigger connection, then wait
    CompletableFuture<Void> connectionFuture = new CompletableFuture<>();
    waitForChannelReady(connectionFuture);
    connectionFuture.get(connectionTimeoutSeconds, TimeUnit.SECONDS);
    connectionStateMachine.transitionTo(ConnectionState.READY);
} catch (TimeoutException e) {
    connectionStateMachine.transitionTo(ConnectionState.CLOSED);
    close();
    throw new RuntimeException(
        "Failed to connect to KubeMQ server at " + address
        + " within " + connectionTimeoutSeconds + " seconds");
} catch (Exception e) {
    connectionStateMachine.transitionTo(ConnectionState.CLOSED);
    close();
    throw new RuntimeException("Failed to connect to KubeMQ server at " + address, e);
}
```

Helper method:
```java
private void waitForChannelReady(CompletableFuture<Void> future) {
    ConnectivityState state = managedChannel.getState(true); // true = request connection
    if (state == ConnectivityState.READY) {
        future.complete(null);
        return;
    }
    if (state == ConnectivityState.SHUTDOWN) {
        future.completeExceptionally(new RuntimeException("Channel shutdown"));
        return;
    }
    managedChannel.notifyWhenStateChanged(state, () -> waitForChannelReady(future));
}
```

#### 6.3.4 Max Send Message Size

In `initChannel()`, add to both TLS and plaintext paths:

For plaintext path (around line 299):
```java
ManagedChannelBuilder mcb = ManagedChannelBuilder.forTarget(resolvedAddress)
    .maxInboundMessageSize(maxReceiveSize)
    .maxRetryAttempts(0) // SDK handles retries, not gRPC
    .usePlaintext();
```

Note: gRPC Java does not have a `maxOutboundMessageSize()` on `ManagedChannelBuilder`. Instead, outbound message size limits should be enforced at the SDK level by validating message size before sending. Add to message validation:

```java
// In message encode methods (EventMessage, QueueMessage, etc.)
if (encodedMessage.getSerializedSize() > maxSendMessageSize) {
    throw new IllegalArgumentException(
        "Message size " + encodedMessage.getSerializedSize()
        + " exceeds maximum send size " + maxSendMessageSize);
}
```

#### 6.3.5 SDK-Level WaitForReady

**Important:** The GS explicitly states this is an SDK-level behavior, not just gRPC's native `WaitForReady`. The SDK must integrate with the connection state machine.

**Calling convention for all public operation methods:**
Every public method on `PubSubClient`, `QueuesClient`, and `CQClient` MUST follow this guard sequence:
1. `ensureReady(operationTimeoutMs)` -- which internally calls `ensureNotClosed()` first, then blocks/fails based on connection state
2. Parameter validation (e.g., `message.validate()`)
3. The operation itself (gRPC call)

`ensureReady()` is the single entry-point guard -- it combines the closed check and the ready check. Do NOT call `ensureNotClosed()` separately before `ensureReady()` as it is redundant. Example:
```java
public void sendEventsMessage(EventMessage message) {
    ensureReady(Defaults.SEND_TIMEOUT.toMillis());
    message.validate();
    // ... gRPC call ...
}
```

Add method to `KubeMQClient`:

```java
/**
 * Block until the connection is READY, or fail if not waitForReady.
 * Called at the start of every operation.
 *
 * @param operationTimeoutMs per-operation timeout in milliseconds
 * @throws ConnectionNotReadyException if waitForReady is false and not READY
 * @throws RuntimeException if timeout expires while waiting
 */
protected void ensureReady(long operationTimeoutMs) {
    ensureNotClosed();

    ConnectionState currentState = connectionStateMachine.getState();
    if (currentState == ConnectionState.READY) {
        return;
    }

    if (!waitForReady) {
        throw new ConnectionNotReadyException(
            "Connection is not ready (state=" + currentState
            + "). Set waitForReady=true to block until ready.");
    }

    // Block until READY or timeout using a CompletableFuture signaled by the state machine.
    // This avoids busy-wait polling (Thread.sleep) which wastes CPU and adds latency.
    CompletableFuture<Void> readyFuture = new CompletableFuture<>();

    ConnectionStateListener readyListener = new ConnectionStateListener() {
        @Override public void onConnected() { readyFuture.complete(null); }
        @Override public void onReconnected() { readyFuture.complete(null); }
        @Override public void onDisconnected() { /* still waiting */ }
        @Override public void onReconnecting(int attempt) { /* still waiting */ }
        @Override public void onClosed() {
            readyFuture.completeExceptionally(new ClientClosedException());
        }
    };

    connectionStateMachine.addListener(readyListener);
    try {
        // Re-check after registering listener to avoid missed signal
        if (connectionStateMachine.getState() == ConnectionState.READY) {
            return;
        }
        if (connectionStateMachine.getState() == ConnectionState.CLOSED) {
            throw new ClientClosedException();
        }
        readyFuture.get(operationTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    } catch (java.util.concurrent.TimeoutException e) {
        throw new ConnectionNotReadyException(
            "Timed out waiting for connection to become READY (state="
            + connectionStateMachine.getState() + ")");
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Interrupted while waiting for connection", e);
    } catch (java.util.concurrent.ExecutionException e) {
        if (e.getCause() instanceof ClientClosedException) {
            throw (ClientClosedException) e.getCause();
        }
        throw new RuntimeException("Error while waiting for connection", e.getCause());
    } finally {
        connectionStateMachine.removeListener(readyListener);
    }
}
```

**New exception:** `ConnectionNotReadyException`

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/exception/ConnectionNotReadyException.java` (new)

```java
package io.kubemq.sdk.exception;

/**
 * Thrown when an operation is attempted while the connection is not READY
 * and waitForReady is false. This is a non-retryable error -- the caller
 * should wait for the connection to become ready rather than retrying.
 *
 * <p>Package: {@code io.kubemq.sdk.exception}</p>
 * <p>When REQ-ERR-1 is implemented, this uses:
 *   ErrorCode.UNAVAILABLE, ErrorCategory.TRANSIENT, retryable=false</p>
 * <p>retryable=false because the caller should enable WaitForReady or
 * wait for the connection state change rather than blindly retrying.</p>
 */
public class ConnectionNotReadyException extends KubeMQException {
    public ConnectionNotReadyException(String message) {
        super(message);
    }
}
```

**Note:** This class extends `KubeMQException` from the error hierarchy. If REQ-ERR-1 is not yet implemented, use the minimal `KubeMQException` stub (see 03-auth-security-spec.md stub strategy).

#### 6.3.6 Expanded Fail-Fast Validation

Add to the constructor, after existing validation (line 97):

```java
// Expanded fail-fast validation
if (maxReceiveSize < 0) {
    throw new IllegalArgumentException("maxReceiveSize must be non-negative, got: " + maxReceiveSize);
}
if (maxSendMessageSize < 0) {
    throw new IllegalArgumentException("maxSendMessageSize must be non-negative, got: " + maxSendMessageSize);
}
if (connectionTimeoutSeconds < 0) {
    throw new IllegalArgumentException("connectionTimeoutSeconds must be non-negative, got: " + connectionTimeoutSeconds);
}
if (pingIntervalInSeconds < 0) {
    throw new IllegalArgumentException("pingIntervalInSeconds must be non-negative, got: " + pingIntervalInSeconds);
}
if (pingTimeoutInSeconds < 0) {
    throw new IllegalArgumentException("pingTimeoutInSeconds must be non-negative, got: " + pingTimeoutInSeconds);
}
if (reconnectIntervalSeconds < 0) {
    throw new IllegalArgumentException("reconnectIntervalSeconds must be non-negative, got: " + reconnectIntervalSeconds);
}
```

### 6.4 GS Configuration Defaults Summary

| Option | GS Default | Current Default | Change Required |
|--------|-----------|-----------------|-----------------|
| Address | `localhost:50000` | Required (no default) | Add default |
| Connection timeout | 10s | None | Add parameter |
| Max receive message size | 100 MB | 100 MB | No change |
| Max send message size | 100 MB | No limit (gRPC default) | Add SDK-level validation |
| Wait for ready | true | N/A | Add parameter |

### 6.5 Breaking Changes

- **Address is now optional** with default `localhost:50000`. Previously, `null` address threw `IllegalArgumentException`. Code passing `null` will now get the default. Code passing empty string `""` will also get the default.
- **Connection timeout:** The constructor may now throw on initial connection failure after timeout. Previously, connection was lazy and errors surfaced on first operation.
- **New exceptions:** `ConnectionNotReadyException` may be thrown if `waitForReady=false` and the connection is not ready.

### 6.6 Test Scenarios

| # | Scenario | Type | Assertion |
|---|----------|------|-----------|
| T1 | Default address is localhost:50000 when null | Unit | Address resolves to `localhost:50000` |
| T2 | Connection timeout expires | Integration | RuntimeException with timeout message |
| T3 | Negative timeout rejected at construction | Unit | IllegalArgumentException thrown |
| T4 | Empty address uses default | Unit | Address resolves to `localhost:50000` |
| T5 | WaitForReady=true blocks during RECONNECTING | Integration | Operation completes after reconnection |
| T6 | WaitForReady=false fails during RECONNECTING | Unit | ConnectionNotReadyException thrown |
| T7 | Max send message size enforced | Unit | IllegalArgumentException when message exceeds limit |
| T8 | All config parameters accessible via builder | Unit | Builder sets and getters return correct values |

---

## 7. REQ-CONN-6: Connection Reuse

**Status:** PARTIAL | **Priority:** P2 | **Effort:** S (< 1 day)

### 7.1 Current State

**Source files:**
- `KubeMQClient.java` lines 65-69: Single `ManagedChannel`, single `blockingStub`, single `asyncStub`
- `PubSubClient.java`: uses inherited stubs
- `QueuesClient.java` line 28-29: `QueueDownstreamHandler` and `QueueUpstreamHandler` reuse the client's stubs
- `CQClient.java`: uses inherited stubs directly

**What exists (COMPLIANT):**
- Single `ManagedChannel` shared across all operations
- Stream handlers reuse the connection
- No per-operation channel creation
- Stubs created once and reused

**What is missing:**
- Thread-safety documentation (no `@ThreadSafe` annotation, no Javadoc mentioning thread safety)

### 7.2 Acceptance Criteria Mapping

| # | GS Acceptance Criterion | Current Status | Implementation Required |
|---|------------------------|----------------|------------------------|
| AC-1 | Single Client uses one gRPC channel | COMPLIANT | No change |
| AC-2 | Multiple concurrent operations multiplex | COMPLIANT | No change |
| AC-3 | Documentation advises single Client shared across threads | MISSING | Add Javadoc |
| AC-4 | Per-operation channel creation prohibited | COMPLIANT | No change |

### 7.3 Specification

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

Replace the class-level Javadoc (lines 24-29) with:

```java
/**
 * KubeMQClient is the base client for communicating with a KubeMQ server using gRPC.
 * This client supports both plain and TLS (Transport Layer Security) connections.
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe. A single client instance
 * should be created and shared across all threads in the application. The underlying
 * gRPC channel multiplexes concurrent operations over a single connection. Creating
 * multiple client instances to the same server is unnecessary and wastes resources.</p>
 *
 * <p><strong>Usage pattern:</strong></p>
 * <pre>{@code
 * // Create once, share everywhere
 * PubSubClient client = PubSubClient.builder()
 *     .address("localhost:50000")
 *     .clientId("my-service")
 *     .build();
 *
 * // Safe to use from multiple threads concurrently
 * executorService.submit(() -> client.sendEventsMessage(msg1));
 * executorService.submit(() -> client.sendEventsMessage(msg2));
 *
 * // Close when application shuts down
 * client.close();
 * }</pre>
 *
 * <p>Implements {@link AutoCloseable} for use with try-with-resources.</p>
 */
```

Apply similar Javadoc to `PubSubClient`, `QueuesClient`, and `CQClient` class declarations.

### 7.4 Breaking Changes

None. Documentation-only change.

### 7.5 Test Scenarios

| # | Scenario | Type | Assertion |
|---|----------|------|-----------|
| T1 | Concurrent operations from multiple threads succeed | Integration | 10 threads sending concurrently, all succeed |
| T2 | Single ManagedChannel used (no channel leak) | Unit | Assert only one ManagedChannel instance exists |

---

## 8. Cross-Category Dependencies

### 8.1 Dependencies FROM This Category

| This REQ | Depends On | Category | Nature |
|----------|-----------|----------|--------|
| REQ-CONN-1 | REQ-CONN-2 | Same | State machine required for RECONNECTING state, buffer lifecycle |
| REQ-CONN-1 | REQ-ERR-1 | 01-Error Handling | Uses `BackpressureException` from error hierarchy (no separate `BufferFullException` class) |
| REQ-CONN-4 | REQ-CONN-1 | Same | Buffer flush during drain |
| REQ-CONN-4 | REQ-CONN-2 | Same | CLOSED state transition, OnClosed callback |
| REQ-CONN-5 | REQ-CONN-2 | Same | WaitForReady integrates with state machine |

### 8.2 Dependencies ON This Category

| Other REQ | Depends On | Nature |
|-----------|-----------|--------|
| REQ-ERR-3 (Operation Retry) | REQ-CONN-2 | Retries suspended during RECONNECTING |
| REQ-OBS-1 (Tracing) | REQ-CONN-2 | Connection state spans |
| REQ-OBS-3 (Metrics) | REQ-CONN-2 | Connection state gauge metric |
| REQ-CONC-5 (Graceful Shutdown) | REQ-CONN-4 | Callback completion timeout (30s) is separate from drain timeout (5s) |
| REQ-TEST-1 (Unit Tests) | REQ-CONN-2 | Connection state assertions, resource leak detection via `ManagedChannel.isTerminated()` |

---

## 9. Implementation Order

**Phase 1 (No dependencies, parallelizable):**

1. **REQ-CONN-3** -- Keepalive defaults change (S, < 1 day). Pure value changes, zero risk.
2. **REQ-CONN-6** -- Thread-safety documentation (S, < 1 day). Javadoc only.
3. **REQ-CONN-2** -- Connection state machine (M, 1-2 days). No dependencies. Creates the foundation.

**Phase 2 (Depends on Phase 1):**

4. **REQ-CONN-5** -- Connection configuration (M, 1-2 days). Depends on REQ-CONN-2 for WaitForReady. Can start in parallel with CONN-2 and integrate WaitForReady after.
5. **REQ-CONN-1** -- Auto-reconnection with buffering (L, 3-5 days). Depends on REQ-CONN-2 (state machine) and benefits from REQ-ERR-1 (error types). Largest single item.

**Phase 3 (Depends on Phase 2):**

6. **REQ-CONN-4** -- Graceful shutdown/drain (M, 1-2 days). Depends on REQ-CONN-1 (buffer) and REQ-CONN-2 (state machine). Finishes the category.

**Note on REQ-CQ-1 (Architecture) dependency:** The gap research review (R2, M-4) identified that REQ-CONN-1 has a practical dependency on REQ-CQ-1 (layered architecture) because the `ReconnectionManager` belongs in the Transport layer. However, the `ReconnectionManager` can be implemented in the `io.kubemq.sdk.client` package now and moved to a `transport` package later when REQ-CQ-1 is implemented. This avoids blocking connection improvements on the large architectural refactoring.

---

## 10. GS Internal Inconsistencies

The following inconsistencies were identified in the Golden Standard and are documented for tracking.

| # | GS Location | Inconsistency | Resolution Used |
|---|------------|---------------|-----------------|
| 1 | REQ-CONN-1 AC-1 says "default 20s" vs REQ-CONN-3 computes 10s+5s=15s | The acceptance criterion text "default 20s" conflicts with the keepalive defaults table (10s+5s=15s) | Use 15s (the computed value from REQ-CONN-3 defaults). Flag in AC table as "default 15s per REQ-CONN-3" |
| 2 | REQ-CONN-4 drain timeout "default 5s" vs REQ-CONC-5 callback timeout "default 30s" | These appear to be two different timeouts: drain timeout (5s) is for flushing in-flight gRPC operations; callback completion timeout (30s) is for waiting on user subscription callbacks to finish | Implement as two separate configurable timeouts. `shutdownTimeoutSeconds = 5` for drain, `callbackCompletionTimeoutSeconds = 30` for callback wait (implemented in REQ-CONC-5 spec) |

---

## 11. Future Enhancements

These items are mentioned in the GS or identified during analysis but are not part of the current gap-close scope:

1. **gRPC Health Check endpoint** -- GS REQ-CONN-3 note: "A gRPC health check endpoint (`grpc.health.v1.Health`) will be added to the KubeMQ server in a future version." When available, the SDK should add health check support with graceful fallback. Design the connection state monitoring to accommodate this.

2. **Publisher and consumer flow control** -- GS scope note: "Publisher and consumer flow control (backpressure signaling, send windows, consumer prefetch) is tracked as a separate work item." The current `MessageBuffer` provides a basic form of publisher flow control (block when full), but full backpressure signaling from the server is future work.

3. **gRPC compression** -- Assessment 3.1.8 scored 1/5. Not in GS Category 02 requirements, but could be added as a configuration option (`compressorName("gzip")` on stubs).

4. **Circuit breaker pattern** -- Not in GS, but complementary to the reconnection manager. Consider adding a circuit breaker that transitions to CLOSED faster when the server is consistently failing with non-transient errors.

5. **Connection pool** -- GS explicitly requires single channel per client. However, for extremely high-throughput scenarios, a connection pool with configurable size could be a future enhancement. This would be transparent to users.

---

## Appendix A: Files Modified Summary

| File | Action | REQ |
|------|--------|-----|
| `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java` | MODIFY | CONN-2, CONN-3, CONN-4, CONN-5, CONN-6 |
| `kubemq-java/src/main/java/io/kubemq/sdk/client/ConnectionState.java` | NEW | CONN-2 |
| `kubemq-java/src/main/java/io/kubemq/sdk/client/ConnectionStateListener.java` | NEW | CONN-2 |
| `kubemq-java/src/main/java/io/kubemq/sdk/client/ConnectionStateMachine.java` | NEW | CONN-2 |
| `kubemq-java/src/main/java/io/kubemq/sdk/client/ReconnectionConfig.java` | NEW | CONN-1 |
| `kubemq-java/src/main/java/io/kubemq/sdk/client/ReconnectionManager.java` | NEW | CONN-1 |
| `kubemq-java/src/main/java/io/kubemq/sdk/client/MessageBuffer.java` | NEW | CONN-1 |
| `kubemq-java/src/main/java/io/kubemq/sdk/client/BufferedMessage.java` | NEW | CONN-1 |
| `kubemq-java/src/main/java/io/kubemq/sdk/client/BufferOverflowPolicy.java` | NEW | CONN-1 |
| ~~`kubemq-java/src/main/java/io/kubemq/sdk/client/BufferFullException.java`~~ | REMOVED -- use `BackpressureException` from `io.kubemq.sdk.exception` | CONN-1 |
| `kubemq-java/src/main/java/io/kubemq/sdk/exception/ClientClosedException.java` | NEW | CONN-4 |
| `kubemq-java/src/main/java/io/kubemq/sdk/exception/ConnectionNotReadyException.java` | NEW | CONN-5 |
| `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsSubscription.java` | MODIFY | CONN-1 (remove per-subscription reconnect) |
| `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsStoreSubscription.java` | MODIFY | CONN-1 (fix recovery semantics, remove per-subscription reconnect) |
| `kubemq-java/src/main/java/io/kubemq/sdk/cq/CommandsSubscription.java` | MODIFY | CONN-1 (remove per-subscription reconnect) |
| `kubemq-java/src/main/java/io/kubemq/sdk/cq/QueriesSubscription.java` | MODIFY | CONN-1 (remove per-subscription reconnect) |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueUpstreamHandler.java` | MODIFY | CONN-1 (add reconnect + buffering) |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueDownstreamHandler.java` | MODIFY | CONN-1 (add reconnect) |
| `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/PubSubClient.java` | MODIFY | CONN-4 (add ensureNotClosed guards) |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueuesClient.java` | MODIFY | CONN-4 (add ensureNotClosed guards) |
| `kubemq-java/src/main/java/io/kubemq/sdk/cq/CQClient.java` | MODIFY | CONN-4 (add ensureNotClosed guards) |

## Appendix B: Complete REQ Checklist

Every REQ-CONN-* item from the GS is accounted for:

- [x] REQ-CONN-1: Auto-Reconnection with Buffering (Section 2)
- [x] REQ-CONN-2: Connection State Machine (Section 3)
- [x] REQ-CONN-3: gRPC Keepalive Configuration (Section 4)
- [x] REQ-CONN-4: Graceful Shutdown / Drain (Section 5)
- [x] REQ-CONN-5: Connection Configuration (Section 6)
- [x] REQ-CONN-6: Connection Reuse (Section 7)

No orphaned items. All 6 REQ-CONN-* requirements have specifications with concrete implementation details.
