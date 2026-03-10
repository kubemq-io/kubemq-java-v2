# Implementation Specification: Category 10 -- Concurrency & Thread Safety

**SDK:** KubeMQ Java v2
**Category:** 10 -- Concurrency & Thread Safety
**GS Source:** `clients/golden-standard/10-concurrency.md`
**Gap Research:** `clients/gap-research/java-gap-research.md` (lines 1411-1508)
**Assessment:** `clients/assesments/JAVA_ASSESSMENT_REPORT.md` (Category 6, lines 377-404)
**Current Score:** 3.50 / 5.0 | **Target:** 4.0+
**Tier:** 2 (Should-have)
**Priority:** P2-P3
**Total Estimated Effort:** 10-15 days

---

## Table of Contents

1. [Summary of REQ Items](#1-summary-of-req-items)
2. [Implementation Order](#2-implementation-order)
3. [REQ-CONC-1: Thread Safety Documentation](#3-req-conc-1-thread-safety-documentation)
4. [REQ-CONC-2: Cancellation & Timeout Support](#4-req-conc-2-cancellation--timeout-support)
5. [REQ-CONC-3: Subscription Callback Behavior](#5-req-conc-3-subscription-callback-behavior)
6. [REQ-CONC-4: Async-First Where Idiomatic](#6-req-conc-4-async-first-where-idiomatic)
7. [REQ-CONC-5: Shutdown-Callback Safety](#7-req-conc-5-shutdown-callback-safety)
8. [Cross-Category Dependencies](#8-cross-category-dependencies)
9. [Breaking Changes](#9-breaking-changes)
10. [Migration Guide](#10-migration-guide)
11. [Open Questions](#11-open-questions)

---

## 1. Summary of REQ Items

| REQ | Status | Gap | Effort | Impl Order | Priority |
|-----|--------|-----|--------|------------|----------|
| REQ-CONC-1 | MISSING | Thread-safety Javadoc and annotations | S (< 1 day) | 1 | P3 |
| REQ-CONC-2 | MISSING | Public async API, cancellation, subscription handles | XL (5-8 days) | 4 (with CONC-4) | P2 |
| REQ-CONC-3 | PARTIAL | Callback concurrency docs, executor, max concurrency | M (1-2 days) | 2 | P3 |
| REQ-CONC-4 | PARTIAL | Public CompletableFuture API for all operations | XL (combined with CONC-2) | 4 (with CONC-2) | P2 |
| REQ-CONC-5 | PARTIAL | Callback tracking, configurable timeout, idempotent close | M (2-3 days) | 3 | P3 |

**Note:** REQ-CONC-2 and REQ-CONC-4 share the same implementation work (async API). They are estimated as a combined XL (5-8 days), not additive.

---

## 2. Implementation Order

**Phase 1 -- Documentation (day 1):**
1. REQ-CONC-1 (thread safety annotations + Javadoc) -- no code dependencies

**Phase 2 -- Callback Infrastructure (days 2-4):**
2. REQ-CONC-3 (callback executor, maxConcurrentCallbacks) -- no code dependencies
3. REQ-CONC-5 (shutdown-callback safety, in-flight tracking) -- depends on REQ-CONN-2 (state machine from 02-spec), REQ-CONN-4 (graceful shutdown from 02-spec)

**Phase 3 -- Async API (days 5-12), split into sub-phases for incremental delivery:**

4A. REQ-CONC-2/CONC-4 Sub-phase A -- Queue async (days 5-6): Expose existing internal `CompletableFuture` methods in `QueueUpstreamHandler`/`QueueDownstreamHandler` as public async API on `QueuesClient`. Lowest risk because the async internals already exist.

4B. REQ-CONC-2/CONC-4 Sub-phase B -- PubSub async (days 7-8): Add async methods to `PubSubClient` using the existing `EventStreamHelper` stream mechanism. Add `Subscription` handle class and `subscribeToEventsWithHandle()` variants.

4C. REQ-CONC-2/CONC-4 Sub-phase C -- CQ async (days 9-11): Add async methods to `CQClient`. **This is the most complex sub-phase** because it requires converting blocking gRPC stubs to async stubs with `StreamObserver`-to-`CompletableFuture` adapters (see Section 4.3.3 implementation note).

4D. REQ-CONC-2/CONC-4 Sub-phase D -- Duration overloads & exception unwrapping (day 12): Add `Duration` overload variants for all sync methods, plus `unwrapFuture()` and `unwrapException()` utilities.

**Rationale for sub-phasing:** Each sub-phase produces a shippable increment. If Sub-phase C hits a blocking issue (e.g., async stub conversion is more complex than anticipated), Sub-phases A and B are already delivered. Dependencies: all sub-phases depend on REQ-ERR-1 (error hierarchy from 01-spec), REQ-CONC-5 (in-flight tracking), REQ-CONC-3 (callback executor).

```
REQ-CONC-1 (docs, no deps) ─────────────────────────────────────────┐
REQ-CONC-3 (callback executor, no deps) ────────────────────────────┤
REQ-CONC-5 (shutdown safety, depends on CONN-2, CONN-4) ───────────┤
REQ-ERR-1 (error hierarchy, from 01-spec) ─────────────────────────┤
                                                                     │
    ┌── Sub-phase A: Queue async (expose existing internals) ───────┤
    ├── Sub-phase B: PubSub async (stream mechanism) ───────────────┤
    ├── Sub-phase C: CQ async (async stub conversion) ──────────────┤
    └── Sub-phase D: Duration overloads + exception unwrapping ─────┘
```

---

## 3. REQ-CONC-1: Thread Safety Documentation

**Gap Status:** MISSING
**GS Reference:** 10-concurrency.md, REQ-CONC-1
**Assessment Evidence:** 6.1.1-6.1.3 (scores 4,4,4 -- types ARE thread-safe), 6.1.4 (score 1 -- zero documentation)
**Effort:** S (< 1 day)

### 3.1 Current State

**Source files examined:**

| File | Path | Thread-Safety Status | Evidence |
|------|------|---------------------|----------|
| `KubeMQClient` | `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java` | Thread-safe | `ManagedChannel` is thread-safe (gRPC guarantee). `blockingStub`/`asyncStub` immutable after construction. `AtomicInteger` clientCount, `volatile` shutdownHookRegistered, `synchronized` blocks. |
| `PubSubClient` | `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/PubSubClient.java` | Thread-safe | Delegates to thread-safe stubs and `EventStreamHelper`. |
| `CQClient` | `kubemq-java/src/main/java/io/kubemq/sdk/cq/CQClient.java` | Thread-safe | All methods delegate to thread-safe blocking stubs. |
| `QueuesClient` | `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueuesClient.java` | Thread-safe | Delegates to `QueueUpstreamHandler`/`QueueDownstreamHandler` which use `synchronized(sendRequestLock)` and `ConcurrentHashMap`. |
| `EventStreamHelper` | `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventStreamHelper.java` | Partially thread-safe | **Known issue:** `sendEventMessage()` (line 99-106) initializes `queuesUpStreamHandler` without synchronization. Race condition when multiple threads call `sendEventMessage()` concurrently while the handler is null. `ConcurrentHashMap` for `pendingResponses` is correct. |
| `EventMessage` | `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventMessage.java` | NOT thread-safe | `@Data` generates setters. Mutable after construction. |
| `QueueMessage` | `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueMessage.java` | NOT thread-safe | `@Data` generates setters. Mutable. |
| `CommandMessage` | `kubemq-java/src/main/java/io/kubemq/sdk/cq/CommandMessage.java` | NOT thread-safe | `@Data` generates setters. Mutable. |
| `QueryMessage` | `kubemq-java/src/main/java/io/kubemq/sdk/cq/QueryMessage.java` | NOT thread-safe | `@Data` generates setters. Mutable. |
| `EventMessageReceived` | `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventMessageReceived.java` | Safe to read | Effectively immutable after `decode()`. |
| `QueueMessageReceived` | `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueMessageReceived.java` | Safe to read | Effectively immutable after `decode()`. |
| `EventsSubscription` | `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsSubscription.java` | Thread-safe (with caveats) | `AtomicInteger` reconnectAttempts. Static `reconnectExecutor`. But `observer` field is `volatile` without full thread-safety for `cancel()`. |

**What exists:** Types ARE thread-safe in practice (assessment 6.1.1-6.1.3 score 4 each). Zero documentation of this fact.

**What is missing:**
1. No `@ThreadSafe` / `@NotThreadSafe` annotations on any class
2. No Javadoc mentioning thread safety on any class
3. No guidance about sharing vs. creating per-send for message types

### 3.2 Acceptance Criteria Mapping

| # | GS Acceptance Criterion | Current Status | Implementation Required |
|---|------------------------|----------------|------------------------|
| AC-1 | Client type explicitly documented as thread-safe | MISSING | Add annotation + Javadoc |
| AC-2 | Doc comments on each public type state concurrency guarantee | MISSING | Add Javadoc to all public types |
| AC-3 | Non-thread-safe types document the restriction | MISSING | Document message types |

### 3.3 Specification

#### 3.3.1 Dependency: JSR-305 / javax.annotation.concurrent

Add the `javax.annotation.concurrent` annotations to the project. These are compile-time only and add zero runtime dependency:

**File:** `kubemq-java/pom.xml`

Add to dependencies:
```xml
<dependency>
    <groupId>com.google.code.findbugs</groupId>
    <artifactId>jsr305</artifactId>
    <version>3.0.2</version>
    <scope>provided</scope>
</dependency>
```

**Rationale:** `@ThreadSafe` and `@NotThreadSafe` from this library are the standard Java mechanism for documenting thread-safety guarantees. The `provided` scope ensures zero runtime footprint. JSR-305 is already a transitive dependency of gRPC, so adding it explicitly has zero marginal cost. **Alternative:** `org.jetbrains:annotations` is a more actively maintained alternative, but JSR-305's presence on the classpath via gRPC makes it the pragmatic choice.

#### 3.3.2 Client Types -- Thread-Safe

Apply the following to all four client classes:

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

Add import and annotation:
```java
import javax.annotation.concurrent.ThreadSafe;

/**
 * KubeMQClient is a client for communicating with a KubeMQ server using gRPC.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. A single instance should be
 * shared across all threads in the application. Creating one instance per application
 * (or per KubeMQ server address) is the recommended usage pattern. All public methods
 * can be called concurrently from multiple threads without external synchronization.</p>
 *
 * <p>The underlying gRPC {@code ManagedChannel} is thread-safe (gRPC guarantee).
 * Internal shared state is protected by atomic operations and synchronized blocks.</p>
 *
 * @see PubSubClient
 * @see CQClient
 * @see QueuesClient
 */
@ThreadSafe
@Slf4j
@Getter
@AllArgsConstructor
public abstract class KubeMQClient implements AutoCloseable {
```

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/PubSubClient.java`
```java
import javax.annotation.concurrent.ThreadSafe;

/**
 * PubSubClient is a specialized client for publishing and subscribing to messages using KubeMQ.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Share a single instance across threads.
 * All publish, subscribe, and channel management methods can be called concurrently.</p>
 */
@ThreadSafe
@Slf4j
public class PubSubClient extends KubeMQClient {
```

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/cq/CQClient.java`
```java
import javax.annotation.concurrent.ThreadSafe;

/**
 * CQClient is a client for command and query operations on KubeMQ.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Share a single instance across threads.
 * All send, subscribe, and channel management methods can be called concurrently.</p>
 */
@ThreadSafe
@Slf4j
public class CQClient extends KubeMQClient {
```

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueuesClient.java`
```java
import javax.annotation.concurrent.ThreadSafe;

/**
 * QueuesClient is a client for queue messaging operations on KubeMQ.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Share a single instance across threads.
 * All send, receive, and channel management methods can be called concurrently.</p>
 */
@ThreadSafe
@Slf4j
public class QueuesClient extends KubeMQClient {
```

#### 3.3.3 Outbound Message Types -- NOT Thread-Safe

Apply to all outbound message classes:

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventMessage.java`
```java
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Represents an event message to be published via KubeMQ.
 *
 * <p><b>Thread Safety:</b> This class is NOT thread-safe. Create a new instance for
 * each send operation. Do not share instances across threads. Use the builder pattern
 * to construct instances:</p>
 *
 * <pre>{@code
 * EventMessage msg = EventMessage.builder()
 *     .channel("my-channel")
 *     .body("hello".getBytes())
 *     .build();
 * client.sendEventsMessage(msg);
 * }</pre>
 */
@NotThreadSafe
```

Apply the same `@NotThreadSafe` annotation and Javadoc pattern to:
- `EventStoreMessage.java`
- `QueueMessage.java`
- `CommandMessage.java`
- `QueryMessage.java`
- `QueuesPollRequest.java`

**Template for each:**
```java
/**
 * <p><b>Thread Safety:</b> This class is NOT thread-safe. Create a new instance for
 * each operation. Do not share instances across threads.</p>
 */
@NotThreadSafe
```

#### 3.3.4 Received Message Types -- Safe to Read

Apply to all received/response message classes:

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventMessageReceived.java`
```java
/**
 * Represents an event message received from a KubeMQ subscription.
 *
 * <p><b>Thread Safety:</b> This class is safe to read from multiple threads
 * after construction by the SDK. Instances are effectively immutable once
 * returned from a subscription callback or decode method. However, because
 * the class uses Lombok {@code @Data} (which generates setters) and the
 * {@code decode()} method mutates the instance during construction, the
 * class is NOT formally immutable per {@code javax.annotation.concurrent.Immutable}
 * semantics. Do not modify instances after they are returned from the SDK.
 * Do not modify the contents of byte arrays or maps obtained from getters.</p>
 */
```

Apply the same Javadoc pattern (without `@Immutable` annotation) to:
- `EventStoreMessageReceived.java`
- `QueueMessageReceived.java`
- `CommandMessageReceived.java`
- `QueryMessageReceived.java`
- `EventSendResult.java`
- `QueueSendResult.java`
- `CommandResponseMessage.java` (the received version)
- `QueryResponseMessage.java` (the received version)

**Note:** The `@Immutable` annotation from `javax.annotation.concurrent` is intentionally NOT applied to these types. Although they are safe to read after construction, they have public setters (from Lombok `@Data`) and mutable `decode()` methods, which means static analysis tools would flag `@Immutable` as violated. A Javadoc comment is used instead to communicate the intent without making a guarantee the code does not actually uphold. If a future version removes setters from received types, `@Immutable` can be reconsidered.

#### 3.3.5 Subscription Types -- Thread-Safe

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsSubscription.java`
```java
import javax.annotation.concurrent.ThreadSafe;

/**
 * Represents a subscription to events in KubeMQ.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. The {@link #cancel()} method
 * can be called from any thread to stop the subscription. Callbacks are invoked
 * sequentially per subscription (see REQ-CONC-3 callback behavior).</p>
 */
@ThreadSafe
```

Apply the same pattern to:
- `EventsStoreSubscription.java`
- `CommandsSubscription.java`
- `QueriesSubscription.java`

#### 3.3.6 Internal Types

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueUpstreamHandler.java`
```java
import javax.annotation.concurrent.ThreadSafe;

/**
 * Manages upstream queue message sending over a gRPC bidirectional stream.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Multiple threads may call
 * {@link #sendQueuesMessage(QueueMessage)} concurrently. Stream writes are
 * serialized via {@code synchronized(sendRequestLock)}.</p>
 */
@ThreadSafe
```

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventStreamHelper.java`
```java
import javax.annotation.concurrent.ThreadSafe;

/**
 * Helper for sending events and event store messages over a gRPC stream.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe after the fix to synchronize
 * stream handler initialization. Multiple threads may call
 * {@link #sendEventMessage} and {@link #sendEventStoreMessage} concurrently.</p>
 */
@ThreadSafe
```

**Important:** The `@ThreadSafe` annotation on `EventStreamHelper` requires the synchronization fix for `sendEventMessage()`. The current code has a race condition at line 100 (`if (queuesUpStreamHandler == null)`). This must be fixed by adding synchronization:

```java
public synchronized void sendEventMessage(KubeMQClient kubeMQClient, Kubemq.Event event) {
    if (queuesUpStreamHandler == null) {
        queuesUpStreamHandler = kubeMQClient.getAsyncClient().sendEventsStream(resultStreamObserver);
        startCleanupTask();
    }
    queuesUpStreamHandler.onNext(event);
    log.debug("Event Message sent");
}
```

Or use a volatile + double-checked locking pattern if contention is a concern (same pattern as `QueueUpstreamHandler.connect()`).

### 3.4 Breaking Changes

None. Adding annotations and Javadoc is purely additive.

### 3.5 Test Scenarios

| # | Scenario | Type | Assertion |
|---|----------|------|-----------|
| T1 | `@ThreadSafe` annotation present on all client classes | Compile-time | Annotation processor or reflective test |
| T2 | `@NotThreadSafe` annotation present on all outbound message classes | Compile-time | Reflective test |
| T3 | Thread-safety Javadoc present on all received message classes (no `@Immutable` annotation -- see Section 3.3.4) | Review / grep | Verify Javadoc contains "safe to read from multiple threads" text |
| T4 | Concurrent sends from 10 threads on PubSubClient do not corrupt state | Integration | No exceptions, all messages delivered |
| T5 | Concurrent sends from 10 threads on QueuesClient do not corrupt state | Integration | No exceptions, all results returned |
| T6 | EventStreamHelper race condition fix verified | Unit | Concurrent init does not create duplicate stream handlers |

---

## 4. REQ-CONC-2: Cancellation & Timeout Support

**Gap Status:** MISSING
**GS Reference:** 10-concurrency.md, REQ-CONC-2 (Java: `CompletableFuture<T>` async methods, `Duration` sync variants)
**Assessment Evidence:** 4.4.2 (score 2, no cancellation), 6.2.J1 (score 2, CompletableFuture internal only), 4.4.1 (score 3, partial timeout)
**Effort:** XL (5-8 days, combined with REQ-CONC-4)

### 4.1 Current State

**CompletableFuture usage in the SDK (internal, not public):**

| File | Internal Async Method | How Called Publicly | Issue |
|------|----------------------|--------------------|----|
| `QueueUpstreamHandler` | `sendQueuesMessageAsync()` (line 163) | `sendQueuesMessage()` calls `.get(timeout, SECONDS)` (line 190) | Private method. User cannot access the future. |
| `QueueDownstreamHandler` | `receiveQueuesMessagesAsync()` (line 173) | `receiveQueuesMessages()` calls `.get(timeout, SECONDS)` (line 209) | Private method. |
| `EventStreamHelper` | `pendingResponses` `CompletableFuture<EventSendResult>` (line 20) | `sendEventStoreMessage()` calls `.get(30, SECONDS)` (line 125) | Internal future. Hardcoded 30s timeout. |

**Timeout support:**

| Method | Timeout | Configurable? |
|--------|---------|---------------|
| `sendQueuesMessage()` | `requestTimeoutSeconds` (default 30) | Yes (builder) |
| `receiveQueuesMessages()` | `max(pollWaitTimeout+5, requestTimeoutSeconds)` | Partial |
| `sendEventStoreMessage()` | 30s hardcoded (line 125) | No |
| `sendCommandRequest()` | None (blocking stub, no deadline) | No |
| `sendQueryRequest()` | None (blocking stub, no deadline) | No |
| `sendEventsMessage()` | None (fire-and-forget, no confirmation) | N/A |

**Cancellation support:** None. No `CompletableFuture` exposed. No `Duration` timeout overloads. No subscription handle with `cancel()` returning a future.

### 4.2 Acceptance Criteria Mapping

| # | GS Acceptance Criterion | Current Status | Implementation Required |
|---|------------------------|----------------|------------------------|
| AC-1 | All blocking operations accept language-appropriate cancellation | MISSING | Async variants returning `CompletableFuture<T>` |
| AC-2 | Cancellation propagated to underlying gRPC call | MISSING | `Context.CancellableContext` for gRPC cancellation |
| AC-3 | Cancelled operations produce clear cancellation error | MISSING | Use `OperationCancelledException` from 01-spec |
| AC-4 | Long-lived subscriptions accept and honor cancellation | PARTIAL | `cancel()` exists but returns void, no future |

### 4.3 Specification

#### 4.3.1 Design Principles

1. **Async-first internal implementation:** All operations are implemented as async internally (CompletableFuture), with sync wrappers calling `.get(timeout)`.
2. **Both sync and async public API:** Per GS for Java -- both must exist.
3. **Cancellation via CompletableFuture.cancel():** Propagated to gRPC via `Context.CancellableContext`.
4. **Timeout via Duration parameter on sync methods, orTimeout() on async futures.**
5. **Exception unwrapping:** Sync wrappers unwrap `ExecutionException` and `CompletionException` to throw the underlying `KubeMQException` directly.

#### 4.3.2 Async API Methods -- PubSubClient

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/PubSubClient.java`

Add the following async method variants alongside existing sync methods.

**Note:** Async operation methods below use `asyncOperationExecutor()` (defined in `KubeMQClient` Section 5.3.3, REQ-CONC-3), which returns a bounded thread pool (`availableProcessors()` threads) by default. This is distinct from `callbackExecutor()` (single-threaded, used for subscription callbacks) and from the **per-subscription** `callbackExecutor` field on subscription builders (Section 5.3.1).

```java
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

// --- Async variants ---

/**
 * Sends an event message asynchronously.
 *
 * <p>The returned future completes after the message is written to the gRPC stream.
 * Since events are fire-and-forget (no server acknowledgment), the future completing
 * does NOT indicate server receipt -- only that the write to the local gRPC stream
 * buffer succeeded.</p>
 *
 * @param message the event message to send
 * @return a CompletableFuture that completes when the stream write is done
 * @throws ClientClosedException if the client has been closed
 */
public CompletableFuture<Void> sendEventsMessageAsync(EventMessage message) {
    ensureNotClosed();
    message.validate();
    Kubemq.Event event = message.encode(this.getClientId());
    return CompletableFuture.runAsync(() -> {
        eventStreamHelper.sendEventMessage(this, event);
    }, asyncOperationExecutor());
}

/**
 * Sends an event store message asynchronously.
 *
 * @param message the event store message to send
 * @return a CompletableFuture that completes with the send result
 * @throws ClientClosedException if the client has been closed
 */
public CompletableFuture<EventSendResult> sendEventsStoreMessageAsync(EventStoreMessage message) {
    ensureNotClosed();
    message.validate();
    Kubemq.Event event = message.encode(this.getClientId());
    return eventStreamHelper.sendEventStoreMessageAsync(this, event);
}

/**
 * Creates an events channel asynchronously.
 *
 * @param channelName the channel name
 * @return a CompletableFuture that completes with true if created successfully
 * @throws ClientClosedException if the client has been closed
 */
public CompletableFuture<Boolean> createEventsChannelAsync(String channelName) {
    ensureNotClosed();
    return CompletableFuture.supplyAsync(
        () -> createEventsChannel(channelName),
        asyncOperationExecutor()
    );
}

/**
 * Subscribes to events and returns a Subscription handle.
 *
 * <p>The returned {@link Subscription} can be used to cancel the subscription
 * from any thread. The cancel operation is propagated to the gRPC stream.</p>
 *
 * @param subscription the subscription configuration with callbacks
 * @return a Subscription handle for cancellation
 * @throws ClientClosedException if the client has been closed
 */
public Subscription subscribeToEventsWithHandle(EventsSubscription subscription) {
    ensureNotClosed();
    subscription.validate();
    Kubemq.Subscribe subscribe = subscription.encode(this.getClientId(), this);
    this.getAsyncClient().subscribeToEvents(subscribe, subscription.getObserver());
    return new Subscription(subscription::cancel, asyncOperationExecutor());
}
```

Apply the same pattern to existing sync methods -- add timeout overload:

```java
/**
 * Sends an event store message with a custom timeout.
 *
 * @param message the event store message to send
 * @param timeout the maximum time to wait for a response
 * @return the send result
 * @throws TimeoutException if the operation times out
 * @throws OperationCancelledException if the operation is cancelled
 * @throws ClientClosedException if the client has been closed
 */
public EventSendResult sendEventsStoreMessage(EventStoreMessage message, Duration timeout) {
    try {
        return sendEventsStoreMessageAsync(message)
            .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .get();
    } catch (ExecutionException e) {
        throw unwrapException(e);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new OperationCancelledException(/* from 01-spec */);
    } catch (java.util.concurrent.TimeoutException e) {
        throw new io.kubemq.sdk.exception.TimeoutException(/* from 01-spec */);
    }
}
```

#### 4.3.3 Async API Methods -- CQClient

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/cq/CQClient.java`

```java
/**
 * Sends a command request asynchronously.
 *
 * @param message the command message to send
 * @return a CompletableFuture that completes with the response
 * @throws ClientClosedException if the client has been closed
 */
public CompletableFuture<CommandResponseMessage> sendCommandRequestAsync(CommandMessage message) {
    ensureNotClosed();
    message.validate();
    // NOTE: This uses asyncOperationExecutor() with the blocking stub as an interim approach.
    // For true non-blocking behavior, this should be converted to use the async gRPC stub
    // with a StreamObserver-to-CompletableFuture adapter (see implementation note below).
    // This is the most complex part of the async migration (Sub-phase C).
    return CompletableFuture.supplyAsync(() -> {
        Kubemq.Request request = message.encode(this.getClientId());
        Kubemq.Response response = this.getClient().sendRequest(request);
        return CommandResponseMessage.builder().build().decode(response);
    }, asyncOperationExecutor());
}

/**
 * Sends a command request with a custom timeout.
 *
 * @param message the command message
 * @param timeout maximum time to wait
 * @return the command response
 */
public CommandResponseMessage sendCommandRequest(CommandMessage message, Duration timeout) {
    return unwrapFuture(sendCommandRequestAsync(message), timeout);
}

/**
 * Sends a query request asynchronously.
 */
public CompletableFuture<QueryResponseMessage> sendQueryRequestAsync(QueryMessage message) {
    ensureNotClosed();
    message.validate();
    // NOTE: Same interim approach as sendCommandRequestAsync -- see note above.
    return CompletableFuture.supplyAsync(() -> {
        Kubemq.Request request = message.encode(this.getClientId());
        Kubemq.Response response = this.getClient().sendRequest(request);
        return QueryResponseMessage.builder().build().decode(response);
    }, asyncOperationExecutor());
}

/**
 * <b>Implementation note for Sub-phase C (true non-blocking CQ):</b>
 * The interim approach above wraps blocking stubs in supplyAsync, which blocks a thread
 * in the async executor while waiting for the gRPC response. For true non-blocking behavior,
 * convert to use the async gRPC stub with a StreamObserver-to-CompletableFuture adapter:
 *
 * <pre>{@code
 * protected <T> CompletableFuture<T> unaryCallToFuture(Consumer<StreamObserver<T>> grpcCall) {
 *     CompletableFuture<T> future = new CompletableFuture<>();
 *     grpcCall.accept(new StreamObserver<T>() {
 *         public void onNext(T value) { future.complete(value); }
 *         public void onError(Throwable t) { future.completeExceptionally(t); }
 *         public void onCompleted() { /* no-op, onNext already completed */ }
 *     });
 *     return future;
 * }
 * }</pre>
 * This adapter converts unary gRPC async calls to CompletableFuture without blocking any thread.
 */

/**
 * Sends a query request with a custom timeout.
 */
public QueryResponseMessage sendQueryRequest(QueryMessage message, Duration timeout) {
    return unwrapFuture(sendQueryRequestAsync(message), timeout);
}

/**
 * Subscribes to commands and returns a cancellable Subscription handle.
 */
public Subscription subscribeToCommandsWithHandle(CommandsSubscription commandsSubscription) {
    ensureNotClosed();
    commandsSubscription.validate();
    Kubemq.Subscribe subscribe = commandsSubscription.encode(this.getClientId(), this);
    this.getAsyncClient().subscribeToRequests(subscribe, commandsSubscription.getObserver());
    return new Subscription(commandsSubscription::cancel, asyncOperationExecutor());
}

/**
 * Subscribes to queries and returns a cancellable Subscription handle.
 */
public Subscription subscribeToQueriesWithHandle(QueriesSubscription queriesSubscription) {
    ensureNotClosed();
    queriesSubscription.validate();
    Kubemq.Subscribe subscribe = queriesSubscription.encode(this.getClientId(), this);
    this.getAsyncClient().subscribeToRequests(subscribe, queriesSubscription.getObserver());
    return new Subscription(queriesSubscription::cancel, asyncOperationExecutor());
}
```

#### 4.3.4 Async API Methods -- QueuesClient

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueuesClient.java`

```java
/**
 * Sends a queue message asynchronously.
 *
 * @param queueMessage the message to send
 * @return a CompletableFuture that completes with the send result
 * @throws ClientClosedException if the client has been closed
 */
public CompletableFuture<QueueSendResult> sendQueuesMessageAsync(QueueMessage queueMessage) {
    ensureNotClosed();
    queueMessage.validate();
    return this.queueUpstreamHandler.sendQueuesMessageAsync(queueMessage);
}

/**
 * Sends a queue message with a custom timeout.
 */
public QueueSendResult sendQueuesMessage(QueueMessage queueMessage, Duration timeout) {
    return unwrapFuture(sendQueuesMessageAsync(queueMessage), timeout);
}

/**
 * Receives queue messages asynchronously.
 *
 * @param queuesPollRequest the poll request configuration
 * @return a CompletableFuture that completes with the poll response
 * @throws ClientClosedException if the client has been closed
 */
public CompletableFuture<QueuesPollResponse> receiveQueuesMessagesAsync(
        QueuesPollRequest queuesPollRequest) {
    ensureNotClosed();
    queuesPollRequest.validate();
    return this.queueDownstreamHandler.receiveQueuesMessagesAsync(queuesPollRequest);
}

/**
 * Receives queue messages with a custom timeout.
 */
public QueuesPollResponse receiveQueuesMessages(QueuesPollRequest queuesPollRequest,
                                                 Duration timeout) {
    return unwrapFuture(receiveQueuesMessagesAsync(queuesPollRequest), timeout);
}
```

**Required change to `QueueUpstreamHandler`:** Make `sendQueuesMessageAsync()` public (currently private at line 163).

**Required change to `QueueDownstreamHandler`:** Make `receiveQueuesMessagesAsync()` public (currently private at line 173).

#### 4.3.5 New Class: `Subscription`

**Package:** `io.kubemq.sdk.client`
**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/Subscription.java` (new)

```java
package io.kubemq.sdk.client;

import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handle for a live subscription. Allows cancellation from any thread.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. {@link #cancel()} and
 * {@link #cancelAsync()} can be called from any thread.</p>
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
     * @param cancelAction the action to perform when cancelling (typically calls
     *                     observer.onCompleted() on the underlying StreamObserver)
     * @param executor the executor to use for async cancellation (avoids using
     *                 ForkJoinPool.commonPool() which may be restricted in some environments)
     */
    public Subscription(Runnable cancelAction, Executor executor) {
        this.cancelAction = cancelAction;
        this.executor = executor;
    }

    /**
     * Convenience constructor that uses ForkJoinPool.commonPool() as the executor.
     * Prefer the two-argument constructor when an application-managed executor is available.
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
```

#### 4.3.6 Cancellation Propagation to gRPC

When a `CompletableFuture` returned by an async method is cancelled (via `future.cancel(true)`), the cancellation must propagate to the underlying gRPC call.

**Mechanism:** Use gRPC's `Context.CancellableContext` to create a context tied to each operation. When the CompletableFuture is cancelled, cancel the gRPC context.

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

Add utility method:

```java
/**
 * Execute a gRPC operation within a cancellable context, returning a CompletableFuture
 * that propagates cancellation to the gRPC call.
 *
 * @param operation the gRPC operation to execute
 * @param <T> the result type
 * @return a CompletableFuture wired to gRPC cancellation
 */
protected <T> CompletableFuture<T> executeWithCancellation(
        java.util.function.Supplier<T> operation) {
    io.grpc.Context.CancellableContext grpcContext =
        io.grpc.Context.current().withCancellation();

    CompletableFuture<T> future = new CompletableFuture<>();

    // Track in-flight operation for shutdown
    inFlightOperations.incrementAndGet();

    asyncOperationExecutor().execute(() -> {
        try {
            T result = grpcContext.call(() -> operation.get());
            future.complete(result);
        } catch (Exception e) {
            future.completeExceptionally(e);
        } finally {
            grpcContext.close();
            inFlightOperations.decrementAndGet();
        }
    });

    // Wire cancellation: if the user cancels the future, cancel the gRPC context
    future.whenComplete((result, ex) -> {
        if (future.isCancelled()) {
            grpcContext.cancel(new java.util.concurrent.CancellationException(
                "Operation cancelled by client"));
        }
    });

    return future;
}
```

**Note on OperationCancelledException:** Per 01-error-handling-spec, `OperationCancelledException` (at `io.kubemq.sdk.exception.OperationCancelledException`) is the SDK's typed cancellation error with `ErrorCode.CANCELLED_BY_CLIENT`. The `java.util.concurrent.CancellationException` is used at the CompletableFuture level; the gRPC mapping interceptor (from 01-spec REQ-ERR-6) converts gRPC `CANCELLED` status to the SDK's `OperationCancelledException`.

**Important: Unary vs. Streaming Cancellation.** The `executeWithCancellation()` method using `Context.CancellableContext` applies only to **unary RPCs** (request/response). For **streaming RPCs** (e.g., `QueuesUpstream`, `QueuesDownstream`, `subscribeToEvents`), cancellation is achieved by calling `StreamObserver.onCompleted()` or `StreamObserver.onError()` on the client-side observer, NOT via gRPC Context cancellation. The `Subscription.cancel()` mechanism (section 4.3.5) handles streaming cancellation correctly by invoking the subscription's cancel action.

**Async Method to gRPC RPC Type Mapping:**

| Async Method | gRPC RPC Type | Cancellation Mechanism |
|-------------|---------------|----------------------|
| `sendEventsMessageAsync()` | Streaming (client) | `StreamObserver.onCompleted()` |
| `sendEventsStoreMessageAsync()` | Streaming (client) | `StreamObserver.onCompleted()` |
| `sendCommandRequestAsync()` | Unary | `Context.CancellableContext` |
| `sendQueryRequestAsync()` | Unary | `Context.CancellableContext` |
| `sendQueuesMessageAsync()` | Streaming (bidi) | `StreamObserver.onCompleted()` |
| `receiveQueuesMessagesAsync()` | Streaming (bidi) | `StreamObserver.onCompleted()` |
| `create*ChannelAsync()` | Unary | `Context.CancellableContext` |
| `delete*ChannelAsync()` | Unary | `Context.CancellableContext` |
| `list*ChannelsAsync()` | Unary | `Context.CancellableContext` |
| `subscribeToEventsWithHandle()` | Streaming (server) | `Subscription.cancel()` -> observer completion |

**Note:** `inFlightOperations` (used in `executeWithCancellation()`) is defined under CONC-5 (Section 7.3.1). It must be implemented before the async API methods that reference it.

#### 4.3.7 Exception Unwrapping Utility

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

```java
/**
 * Unwrap a CompletableFuture result with proper exception handling.
 * Converts ExecutionException/CompletionException wrappers to the underlying
 * KubeMQException, and handles timeout and interruption.
 *
 * @param future the future to unwrap
 * @param timeout the maximum time to wait
 * @param <T> the result type
 * @return the result
 * @throws KubeMQException the unwrapped SDK exception
 */
protected <T> T unwrapFuture(CompletableFuture<T> future, Duration timeout) {
    try {
        return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (java.util.concurrent.ExecutionException e) {
        throw unwrapException(e);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        future.cancel(true); // Propagate cancellation to gRPC
        throw new io.kubemq.sdk.exception.OperationCancelledException(
            ErrorCode.CANCELLED_BY_CLIENT,
            "Operation interrupted",
            /* operation */ null,
            /* channel */ null,
            e
        );
    } catch (java.util.concurrent.TimeoutException e) {
        future.cancel(true); // Propagate cancellation to gRPC
        throw new io.kubemq.sdk.exception.TimeoutException(
            ErrorCode.OPERATION_TIMEOUT,
            "Operation timed out after " + timeout,
            /* operation */ null,
            /* channel */ null,
            e
        );
    }
}

/**
 * Unwrap ExecutionException to get the underlying cause.
 * If the cause is a KubeMQException, throw it directly.
 * Otherwise, wrap in a KubeMQException to maintain the typed exception hierarchy
 * (per REQ-ERR-1: all SDK-thrown exceptions extend KubeMQException).
 */
private KubeMQException unwrapException(java.util.concurrent.ExecutionException e) {
    Throwable cause = e.getCause();
    if (cause instanceof io.kubemq.sdk.exception.KubeMQException) {
        return (io.kubemq.sdk.exception.KubeMQException) cause;
    }
    return new io.kubemq.sdk.exception.KubeMQException(
        ErrorCode.INTERNAL_ERROR,
        "Async operation failed: " + cause.getMessage(),
        /* operation */ null,
        /* channel */ null,
        cause
    );
}
```

**Default timeout for sync methods without explicit Duration parameter:** Use `requestTimeoutSeconds` from the builder (default 30s). Existing sync methods (without Duration parameter) continue to use this default.

#### 4.3.8 Refactoring: EventStreamHelper Async Internal

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventStreamHelper.java`

Add a public async method for event store (the internal future mechanism already exists):

```java
/**
 * Sends an event store message asynchronously.
 *
 * @return a CompletableFuture that completes with the send result
 */
public CompletableFuture<EventSendResult> sendEventStoreMessageAsync(
        KubeMQClient kubeMQClient, Kubemq.Event event) {
    synchronized (this) {
        if (queuesUpStreamHandler == null) {
            queuesUpStreamHandler = kubeMQClient.getAsyncClient()
                .sendEventsStream(resultStreamObserver);
            startCleanupTask();
        }
    }

    String requestId = UUID.randomUUID().toString();
    CompletableFuture<EventSendResult> responseFuture = new CompletableFuture<>();
    pendingResponses.put(requestId, responseFuture);
    requestTimestamps.put(requestId, System.currentTimeMillis());

    Kubemq.Event eventWithId = event.toBuilder().setEventID(requestId).build();
    queuesUpStreamHandler.onNext(eventWithId);
    log.debug("Event store message sent async with ID {}", requestId);

    return responseFuture;
}
```

Refactor existing `sendEventStoreMessage()` to delegate:

```java
public EventSendResult sendEventStoreMessage(KubeMQClient kubeMQClient, Kubemq.Event event) {
    try {
        return sendEventStoreMessageAsync(kubeMQClient, event)
            .get(kubeMQClient.getRequestTimeoutSeconds(), TimeUnit.SECONDS);
    } catch (TimeoutException e) {
        // ... existing timeout handling
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        // ... existing interrupt handling
    } catch (Exception e) {
        throw new RuntimeException("Failed to get response", e);
    }
}
```

### 4.4 Breaking Changes

- **Additive only.** New `*Async()` methods and `Duration` overloads are added alongside existing methods.
- `QueueUpstreamHandler.sendQueuesMessageAsync()` and `QueueDownstreamHandler.receiveQueuesMessagesAsync()` change from `private` to `public`. These are handler classes, not part of the primary public API, but the visibility change is technically broader access.
- Existing sync methods retain identical signatures and behavior.
- The new `subscribeToEventsWithHandle()` methods are additive; existing `subscribeToEvents()` remains unchanged.

### 4.5 Test Scenarios

| # | Scenario | Type | Assertion |
|---|----------|------|-----------|
| T1 | `sendEventsStoreMessageAsync()` returns CompletableFuture | Unit | Future completes with EventSendResult |
| T2 | `sendCommandRequestAsync()` returns CompletableFuture | Unit | Future completes with CommandResponseMessage |
| T3 | `sendQueuesMessageAsync()` returns CompletableFuture | Unit | Future completes with QueueSendResult |
| T4 | `receiveQueuesMessagesAsync()` returns CompletableFuture | Unit | Future completes with QueuesPollResponse |
| T5 | `future.cancel(true)` propagates to gRPC context | Unit | gRPC call is cancelled, OperationCancelledException thrown |
| T6 | Sync method with Duration timeout fires TimeoutException | Unit | TimeoutException thrown after specified duration |
| T7 | Interrupted sync call produces OperationCancelledException | Unit | Thread interrupted, OperationCancelledException with CANCELLED_BY_CLIENT |
| T8 | ExecutionException unwrapping returns KubeMQException | Unit | KubeMQException thrown directly, not wrapped |
| T9 | CompletionException from async chain unwrapped | Unit | Inner KubeMQException extracted |
| T10 | `Subscription.cancel()` from separate thread cancels stream | Integration | StreamObserver.onCompleted() called, no more callbacks fire |
| T11 | `Subscription.cancel()` is idempotent | Unit | Second cancel() is a no-op |
| T12 | `subscribeToEventsWithHandle()` returns working Subscription | Integration | Subscription.isCancelled() starts false, becomes true after cancel |
| T13 | Async operations on closed client throw ClientClosedException | Unit | ensureNotClosed() guard fires |
| T14 | 10 concurrent async sends complete independently | Integration | All futures resolve, no corruption |
| T15 | `orTimeout()` on CompletableFuture works correctly | Unit | Future completes exceptionally after timeout |

---

## 5. REQ-CONC-3: Subscription Callback Behavior

**Gap Status:** PARTIAL
**GS Reference:** 10-concurrency.md, REQ-CONC-3
**Assessment Evidence:** 6.1.3 (score 4, independent StreamObserver per subscription)
**Effort:** M (1-2 days)

### 5.1 Current State

**Source files examined:**

| File | Callback Mechanism | Concurrency Model |
|------|--------------------|-------------------|
| `EventsSubscription` (line 130-152) | `StreamObserver.onNext()` calls `raiseOnReceiveMessage()` which invokes `onReceiveEventCallback.accept()` | Sequential per stream (gRPC guarantee) |
| `EventsStoreSubscription` | Same pattern as above | Sequential per stream |
| `CommandsSubscription` | Same pattern | Sequential per stream |
| `QueriesSubscription` | Same pattern | Sequential per stream |

**What exists:**
- gRPC `StreamObserver.onNext()` is called sequentially by the gRPC runtime for a single stream (gRPC specification guarantee)
- User callbacks are invoked directly inside `onNext()`, on the gRPC executor thread
- Each subscription creates an independent `StreamObserver`

**What is missing:**
1. No documentation of sequential callback guarantee
2. No `callbackExecutor(Executor)` option to offload callbacks from gRPC thread
3. No `maxConcurrentCallbacks(int)` option
4. User callbacks blocking inside `onNext()` will block the gRPC executor thread, preventing further messages from being received on that stream
5. No guidance about long-running callbacks

### 5.2 Acceptance Criteria Mapping

| # | GS Acceptance Criterion | Current Status | Implementation Required |
|---|------------------------|----------------|------------------------|
| AC-1 | Document whether callbacks may fire concurrently | MISSING | Add Javadoc |
| AC-2 | Default callback concurrency is 1 (sequential) | PARTIAL | Default is sequential (gRPC), but not documented |
| AC-3 | Mechanism to control callback concurrency | MISSING | Add `callbackExecutor()` and `maxConcurrentCallbacks()` |
| AC-4 | Callbacks must not block SDK's internal event loop | MISSING | Offload to separate executor by default |
| AC-5 | Long-running callback guidance documented | MISSING | Add Javadoc and README guidance |

### 5.3 Specification

#### 5.3.1 Callback Executor for Subscriptions

Add an optional `callbackExecutor` field to all subscription builder classes.

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsSubscription.java`

Add fields:
```java
/**
 * Executor for dispatching subscription callbacks. Default: SDK's shared callback
 * executor (separate from gRPC's internal executor). Set to a custom executor to
 * control thread pool size and behavior for callback processing.
 *
 * <p>If not set, callbacks are dispatched to a shared single-thread executor,
 * ensuring sequential processing (concurrency = 1).</p>
 */
@Builder.Default
private Executor callbackExecutor = null;

/**
 * Maximum number of callbacks that may execute concurrently for this subscription.
 * Default: 1 (sequential processing). Set to a higher value to enable parallel
 * callback processing. Requires a multi-threaded {@link #callbackExecutor}.
 *
 * <p>When maxConcurrentCallbacks > 1, callbacks for this subscription may fire
 * concurrently. The user is responsible for thread safety in the callback handler.</p>
 *
 * <p><b>Warning:</b> Setting this value > 1 means messages may be processed
 * out of order. Only use when ordering is not required.</p>
 */
@Builder.Default
private int maxConcurrentCallbacks = 1;
```

#### 5.3.2 Callback Dispatch with Concurrency Control

Modify the `StreamObserver.onNext()` in the `encode()` method to dispatch callbacks through the executor with semaphore-based concurrency limiting:

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsSubscription.java`

In the `encode()` method, replace the direct callback invocation with executor dispatch:

```java
// Add as a field (initialized in encode())
private transient Semaphore callbackSemaphore;

public Kubemq.Subscribe encode(String clientId, PubSubClient pubSubClient) {
    // Initialize concurrency control
    this.callbackSemaphore = new Semaphore(maxConcurrentCallbacks);
    final Executor executor = callbackExecutor != null
        ? callbackExecutor
        : pubSubClient.getCallbackExecutor(); // SDK default executor

    Kubemq.Subscribe subscribe = /* ... existing builder code ... */;

    observer = new StreamObserver<Kubemq.EventReceive>() {
        @Override
        public void onNext(Kubemq.EventReceive messageReceive) {
            log.debug("Event Received: EventID:'{}', Channel:'{}'",
                      messageReceive.getEventID(), messageReceive.getChannel());

            // Dispatch to callback executor with concurrency limit
            executor.execute(() -> {
                try {
                    callbackSemaphore.acquire();
                    try {
                        raiseOnReceiveMessage(EventMessageReceived.decode(messageReceive));
                    } finally {
                        callbackSemaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Callback dispatch interrupted for channel {}", channel);
                }
            });
        }

        @Override
        public void onError(Throwable t) {
            // ... existing error handling ...
        }

        @Override
        public void onCompleted() {
            log.debug("StreamObserver completed.");
        }
    };
    return subscribe;
}
```

Apply the same pattern to `EventsStoreSubscription`, `CommandsSubscription`, and `QueriesSubscription`.

#### 5.3.3 SDK Default Callback Executor

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

Add two executors: a **single-threaded executor for subscription callbacks** (sequential processing is the desired default for callbacks) and a **bounded thread pool for async operations** (to avoid serializing all async calls on one thread):

```java
/**
 * Executor for subscription callbacks. Single-threaded by default to ensure
 * sequential callback processing per subscription.
 * Users can override per-subscription via callbackExecutor() on the subscription builder.
 */
private final ExecutorService defaultCallbackExecutor =
    Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "kubemq-callback-" + getClientId());
        t.setDaemon(true);
        return t;
    });

/**
 * Executor for async operations (sendEventsMessageAsync, sendCommandRequestAsync, etc.).
 * Uses a bounded thread pool sized to the number of available processors to avoid
 * serializing all async operations on a single thread.
 * Users can override at construction time via the builder's asyncOperationExecutor() method.
 */
private final ExecutorService defaultAsyncOperationExecutor =
    Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors(),
        r -> {
            Thread t = new Thread(r, "kubemq-async-" + getClientId());
            t.setDaemon(true);
            return t;
        });

/**
 * Returns the executor for subscription callbacks. Single-threaded by default.
 *
 * @return the SDK's default callback executor
 */
protected ExecutorService callbackExecutor() {
    return defaultCallbackExecutor;
}

/**
 * Returns the executor for async operations. Bounded thread pool by default.
 * Async methods (sendEventsMessageAsync, sendCommandRequestAsync, etc.) use this
 * executor instead of the callback executor.
 *
 * @return the SDK's async operation executor
 */
protected ExecutorService asyncOperationExecutor() {
    return defaultAsyncOperationExecutor;
}
```

Shutdown this executor in `close()` (coordinated with REQ-CONC-5 in-flight tracking).

#### 5.3.4 Callback Documentation

Add the following Javadoc to all subscription classes:

```java
/**
 * <h3>Callback Behavior</h3>
 *
 * <p><b>Sequential by default:</b> Callbacks for a single subscription are invoked
 * sequentially (one at a time) in the order messages are received from the server.
 * This is the gRPC stream guarantee combined with the SDK's single-threaded callback
 * executor.</p>
 *
 * <p><b>Multiple subscriptions:</b> Callbacks for different subscriptions may fire
 * concurrently if they use different callback executors or if the shared executor
 * has multiple threads.</p>
 *
 * <p><b>Concurrent callbacks (opt-in):</b> Set {@code maxConcurrentCallbacks} > 1
 * and provide a multi-threaded {@code callbackExecutor} to enable parallel callback
 * processing for a single subscription. When enabled, messages may be processed
 * out of order and the callback handler must be thread-safe.</p>
 *
 * <p><b>Blocking:</b> Do not perform long-running or blocking operations inside
 * callbacks. Long-running callbacks will delay subsequent message processing for
 * this subscription. If heavy processing is needed, submit work to a separate worker
 * pool from within the callback:</p>
 *
 * <pre>{@code
 * subscription.onReceiveEventCallback(event -> {
 *     workerPool.submit(() -> processEvent(event)); // Non-blocking
 * });
 * }</pre>
 */
```

### 5.4 Breaking Changes

None. New fields on subscription builders use `@Builder.Default`, so existing usage without these fields is unchanged. The callback dispatch via executor is a behavioral change, but preserves sequential semantics by default.

**Subtle behavioral change:** Callbacks will now execute on the `kubemq-callback` thread instead of the gRPC executor thread. User code that relies on ThreadLocal state from the gRPC thread will break. This is unlikely but should be noted in release notes.

### 5.5 Test Scenarios

| # | Scenario | Type | Assertion |
|---|----------|------|-----------|
| T1 | Default subscription: callbacks fire sequentially | Unit | Second callback starts only after first completes |
| T2 | maxConcurrentCallbacks=1: slow callback does not block gRPC thread | Unit | gRPC StreamObserver returns immediately; callback runs on kubemq-callback thread |
| T3 | maxConcurrentCallbacks=3 with thread pool: callbacks fire concurrently | Unit | Up to 3 callbacks run simultaneously |
| T4 | maxConcurrentCallbacks=3: 4th callback blocks until one completes | Unit | Semaphore blocks 4th dispatch |
| T5 | Custom callbackExecutor is used when provided | Unit | Callback runs on user-provided executor thread |
| T6 | Default callbackExecutor is single-threaded | Unit | Thread name is `kubemq-callback` |
| T7 | Callback exception does not crash subscription | Unit | Next message still delivered after callback throws |
| T8 | Callback executor shutdown during close() | Unit | Executor terminated after close |

---

## 6. REQ-CONC-4: Async-First Where Idiomatic

**Gap Status:** PARTIAL
**GS Reference:** 10-concurrency.md, REQ-CONC-4 (Java: both sync and async)
**Assessment Evidence:** 6.2.J1 (score 2, CompletableFuture internal only)
**Effort:** Combined with REQ-CONC-2 (XL, 5-8 days)

### 6.1 Current State

The SDK exposes only synchronous public methods. Internally, `QueueUpstreamHandler`, `QueueDownstreamHandler`, and `EventStreamHelper` use `CompletableFuture` for response correlation, but all public API calls `.get()` to block.

| Public Method | Returns | Blocking? |
|--------------|---------|-----------|
| `PubSubClient.sendEventsMessage()` | `void` | Yes (stream write) |
| `PubSubClient.sendEventsStoreMessage()` | `EventSendResult` | Yes (30s hardcoded) |
| `PubSubClient.subscribeToEvents()` | `void` | No (subscription setup) |
| `CQClient.sendCommandRequest()` | `CommandResponseMessage` | Yes (blocking stub) |
| `CQClient.sendQueryRequest()` | `QueryResponseMessage` | Yes (blocking stub) |
| `QueuesClient.sendQueuesMessage()` | `QueueSendResult` | Yes (requestTimeoutSeconds) |
| `QueuesClient.receiveQueuesMessages()` | `QueuesPollResponse` | Yes (pollWait + buffer) |
| `QueuesClient.waiting()` | `QueueMessagesWaiting` | Yes (blocking stub) |
| `QueuesClient.pull()` | `QueueMessagesPulled` | Yes (blocking stub) |

### 6.2 Acceptance Criteria Mapping

| # | GS Acceptance Criterion | Current Status | Implementation Required |
|---|------------------------|----------------|------------------------|
| AC-1 | Primary API style matches language convention (both sync and async for Java) | PARTIAL | Add async variants for all operations |
| AC-2 | Async APIs don't block the calling thread | MISSING | CompletableFuture must not call .get() internally |

### 6.3 Specification

The async API is fully specified in section 4 (REQ-CONC-2) above. REQ-CONC-4's requirements are a subset of REQ-CONC-2's implementation. Specifically:

**Complete async API coverage (from REQ-CONC-2 section 4.3):**

| Sync Method | Async Variant | Returns |
|-------------|---------------|---------|
| `PubSubClient.sendEventsMessage()` | `sendEventsMessageAsync()` | `CompletableFuture<Void>` |
| `PubSubClient.sendEventsStoreMessage()` | `sendEventsStoreMessageAsync()` | `CompletableFuture<EventSendResult>` |
| `PubSubClient.createEventsChannel()` | `createEventsChannelAsync()` | `CompletableFuture<Boolean>` |
| `PubSubClient.createEventsStoreChannel()` | `createEventsStoreChannelAsync()` | `CompletableFuture<Boolean>` |
| `PubSubClient.deleteEventsChannel()` | `deleteEventsChannelAsync()` | `CompletableFuture<Boolean>` |
| `PubSubClient.deleteEventsStoreChannel()` | `deleteEventsStoreChannelAsync()` | `CompletableFuture<Boolean>` |
| `PubSubClient.listEventsChannels()` | `listEventsChannelsAsync()` | `CompletableFuture<List<PubSubChannel>>` |
| `PubSubClient.listEventsStoreChannels()` | `listEventsStoreChannelsAsync()` | `CompletableFuture<List<PubSubChannel>>` |
| `CQClient.sendCommandRequest()` | `sendCommandRequestAsync()` | `CompletableFuture<CommandResponseMessage>` |
| `CQClient.sendQueryRequest()` | `sendQueryRequestAsync()` | `CompletableFuture<QueryResponseMessage>` |
| `CQClient.sendResponseMessage(Command)` | `sendResponseMessageAsync()` | `CompletableFuture<Void>` |
| `CQClient.sendResponseMessage(Query)` | `sendResponseMessageAsync()` | `CompletableFuture<Void>` |
| `CQClient.createCommandsChannel()` | `createCommandsChannelAsync()` | `CompletableFuture<Boolean>` |
| `CQClient.createQueriesChannel()` | `createQueriesChannelAsync()` | `CompletableFuture<Boolean>` |
| `CQClient.deleteCommandsChannel()` | `deleteCommandsChannelAsync()` | `CompletableFuture<Boolean>` |
| `CQClient.deleteQueriesChannel()` | `deleteQueriesChannelAsync()` | `CompletableFuture<Boolean>` |
| `CQClient.listCommandsChannels()` | `listCommandsChannelsAsync()` | `CompletableFuture<List<CQChannel>>` |
| `CQClient.listQueriesChannels()` | `listQueriesChannelsAsync()` | `CompletableFuture<List<CQChannel>>` |
| `QueuesClient.sendQueuesMessage()` | `sendQueuesMessageAsync()` | `CompletableFuture<QueueSendResult>` |
| `QueuesClient.receiveQueuesMessages()` | `receiveQueuesMessagesAsync()` | `CompletableFuture<QueuesPollResponse>` |
| `QueuesClient.createQueuesChannel()` | `createQueuesChannelAsync()` | `CompletableFuture<Boolean>` |
| `QueuesClient.deleteQueuesChannel()` | `deleteQueuesChannelAsync()` | `CompletableFuture<Boolean>` |
| `QueuesClient.listQueuesChannels()` | `listQueuesChannelsAsync()` | `CompletableFuture<List<QueuesChannel>>` |
| `QueuesClient.waiting()` | `waitingAsync()` | `CompletableFuture<QueueMessagesWaiting>` |
| `QueuesClient.pull()` | `pullAsync()` | `CompletableFuture<QueueMessagesPulled>` |

**Non-blocking guarantee:** All async methods must NOT call `.get()` or `.join()` internally. They must complete the `CompletableFuture` via the gRPC async stub or a non-blocking executor dispatch.

**Sync methods become wrappers:**
```java
// Pattern: every existing sync method delegates to its async variant
public EventSendResult sendEventsStoreMessage(EventStoreMessage message) {
    return unwrapFuture(
        sendEventsStoreMessageAsync(message),
        Duration.ofSeconds(getRequestTimeoutSeconds())
    );
}
```

### 6.4 Breaking Changes

None. All async methods are additive. Existing sync methods retain identical signatures.

### 6.5 Test Scenarios

See REQ-CONC-2 test scenarios (section 4.5). Additionally:

| # | Scenario | Type | Assertion |
|---|----------|------|-----------|
| T1 | Every public method has an `*Async()` counterpart | Reflection | Verify via reflection over all client classes |
| T2 | Async method does not block calling thread | Unit | Call async, assert thread returns immediately (measured by timing) |
| T3 | Sync wrapper of async method returns same result | Unit | Compare sync and async results for same operation |

---

## 7. REQ-CONC-5: Shutdown-Callback Safety

**Gap Status:** PARTIAL
**GS Reference:** 10-concurrency.md, REQ-CONC-5
**Assessment Evidence:** 2.1.5 (score 4, AutoCloseable with 5s timeout), 6.2.J4 (score 5, proper cleanup)
**Effort:** M (2-3 days)

### 7.1 Current State

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
- `close()` with 5s hardcoded timeout for gRPC channel termination
- JVM shutdown hook shuts down static executors (lines 127-186)
- `AutoCloseable` interface -- try-with-resources compatible

**What is missing:**
1. No tracking of in-flight callbacks (callbacks may still be executing when `close()` returns)
2. No configurable callback completion timeout (GS says default 30s)
3. No `closed` flag to prevent post-close operations (specified in 02-connection-transport-spec REQ-CONN-4, but repeated here for callback context)
4. `close()` is not idempotent (second call will attempt shutdown on already-shutdown channel)
5. No waiting for in-flight async operations to complete
6. Callback executor not shut down (callbacks may fire after `close()` returns)

### 7.2 Acceptance Criteria Mapping

| # | GS Acceptance Criterion | Current Status | Implementation Required |
|---|------------------------|----------------|------------------------|
| AC-1 | Close() waits for in-flight callbacks with configurable timeout (default 30s) | PARTIAL | Add callback tracking + configurable timeout |
| AC-2 | Operations after Close() return ErrClientClosed | MISSING | Add `closed` flag + `ensureNotClosed()` guard (shared with 02-spec) |
| AC-3 | Close() is idempotent | NOT_ASSESSED | Add `AtomicBoolean` guard (shared with 02-spec) |

### 7.3 Specification

**Note:** Several mechanisms from REQ-CONC-5 overlap with REQ-CONN-4 (graceful shutdown) from the 02-connection-transport-spec. This section specifies the callback-specific additions. The `closed` flag, `ensureNotClosed()`, and idempotent close are defined in 02-spec and referenced here.

#### 7.3.1 In-Flight Operation Counter

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

Add fields:
```java
/**
 * Counter for in-flight asynchronous operations and active callbacks.
 * Incremented when an async operation starts or a callback begins execution.
 * Decremented when the operation completes or the callback finishes.
 * Used by close() to wait for completion.
 */
private final AtomicInteger inFlightOperations = new AtomicInteger(0);

/**
 * Latch that signals when all in-flight operations have completed during shutdown.
 */
private final CountDownLatch shutdownLatch = new CountDownLatch(1);

/**
 * Timeout for waiting for in-flight callbacks to complete during shutdown.
 * Separate from the gRPC channel drain timeout (shutdownTimeoutSeconds from 02-spec).
 * GS default: 30 seconds.
 */
private int callbackCompletionTimeoutSeconds = 30;
```

**Builder parameter:**
```java
/**
 * Maximum time to wait for in-flight callbacks to complete during close().
 * This is separate from the gRPC drain timeout (shutdownTimeoutSeconds).
 * Default: 30 seconds per GS REQ-CONC-5.
 *
 * <p>Timeline during close():
 * <ol>
 *   <li>Stop accepting new operations (set closed flag)</li>
 *   <li>Wait up to callbackCompletionTimeoutSeconds for in-flight callbacks</li>
 *   <li>Shutdown gRPC channel with shutdownTimeoutSeconds drain timeout</li>
 *   <li>Force-close remaining resources</li>
 * </ol></p>
 */
@Builder.Default
private int callbackCompletionTimeoutSeconds = 30;
```

#### 7.3.2 In-Flight Tracking in Subscription Callbacks

Modify the callback dispatch in subscription classes (from REQ-CONC-3 section 5.3.2):

```java
executor.execute(() -> {
    // Track this callback as an in-flight operation
    kubeMQClient.getInFlightOperations().incrementAndGet();
    try {
        callbackSemaphore.acquire();
        try {
            raiseOnReceiveMessage(EventMessageReceived.decode(messageReceive));
        } finally {
            callbackSemaphore.release();
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    } finally {
        kubeMQClient.getInFlightOperations().decrementAndGet();
    }
});
```

Add accessor on `KubeMQClient`:
```java
/**
 * Returns the in-flight operation counter for use by subscription handlers.
 * Package-private -- not part of the public API.
 */
AtomicInteger getInFlightOperations() {
    return inFlightOperations;
}
```

#### 7.3.3 In-Flight Tracking in Async Operations

The `executeWithCancellation()` method from section 4.3.6 already increments/decrements `inFlightOperations`.

For queue handlers, add tracking around the CompletableFuture lifecycle:

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueUpstreamHandler.java`

In `sendQueuesMessageAsync()`:
```java
public CompletableFuture<QueueSendResult> sendQueuesMessageAsync(QueueMessage queueMessage) {
    kubeMQClient.getInFlightOperations().incrementAndGet();

    String requestId = generateRequestId();
    CompletableFuture<QueueSendResult> responseFuture = new CompletableFuture<>();

    // Decrement counter when future completes (success, error, or cancel)
    responseFuture.whenComplete((result, ex) ->
        kubeMQClient.getInFlightOperations().decrementAndGet());

    pendingResponses.put(requestId, responseFuture);
    requestTimestamps.put(requestId, System.currentTimeMillis());
    // ... rest of existing code
}
```

Apply the same pattern to `QueueDownstreamHandler.receiveQueuesMessagesAsync()`.

#### 7.3.4 Enhanced close() Method

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

Replace the `close()` method. This builds on the 02-spec REQ-CONN-4 specification with callback-specific additions:

```java
/**
 * Gracefully shuts down the client.
 *
 * <p>Shutdown sequence:
 * <ol>
 *   <li>Set closed flag (prevents new operations via ensureNotClosed())</li>
 *   <li>Cancel reconnection attempts if in RECONNECTING state</li>
 *   <li>Wait for in-flight callbacks to complete (up to callbackCompletionTimeoutSeconds)</li>
 *   <li>Shutdown callback executor (no new callbacks accepted)</li>
 *   <li>Flush buffered messages if in READY state (02-spec)</li>
 *   <li>Shutdown gRPC channel (up to shutdownTimeoutSeconds)</li>
 *   <li>Transition to CLOSED state, fire OnClosed callback</li>
 * </ol></p>
 *
 * <p><b>Thread Safety:</b> This method is idempotent. Calling close() multiple times
 * is safe; only the first call performs the shutdown sequence.</p>
 */
@Override
public void close() {
    // Step 1: Idempotent guard (from 02-spec REQ-CONN-4)
    if (!closed.compareAndSet(false, true)) {
        return;
    }

    log.info("Initiating graceful shutdown (callbackTimeout={}s, drainTimeout={}s)",
             callbackCompletionTimeoutSeconds, shutdownTimeoutSeconds);

    // Step 2: Cancel reconnection if applicable
    // TODO: Integrate with ConnectionStateMachine from spec 02 when implemented.
    // Standalone behavior: skip reconnection cancellation (no reconnection manager yet).
    // When spec 02 is implemented, uncomment:
    // ConnectionState currentState = connectionStateMachine.getState();
    // if (currentState == ConnectionState.RECONNECTING) {
    //     reconnectionManager.cancel();
    //     if (messageBuffer != null) {
    //         messageBuffer.discardAll();
    //     }
    // }

    // Step 3: Wait for in-flight callbacks and async operations
    int inFlight = inFlightOperations.get();
    if (inFlight > 0) {
        log.info("Waiting for {} in-flight operations to complete (timeout={}s)",
                 inFlight, callbackCompletionTimeoutSeconds);
        long deadline = System.nanoTime()
            + TimeUnit.SECONDS.toNanos(callbackCompletionTimeoutSeconds);
        while (inFlightOperations.get() > 0
               && System.nanoTime() < deadline) {
            try {
                Thread.sleep(50); // Poll every 50ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for in-flight callbacks");
                break;
            }
        }
        int remaining = inFlightOperations.get();
        if (remaining > 0) {
            log.warn("{} in-flight operations did not complete within {}s timeout",
                     remaining, callbackCompletionTimeoutSeconds);
        }
    }

    // Step 4: Shutdown callback executor and async operation executor
    defaultCallbackExecutor.shutdown();
    defaultAsyncOperationExecutor.shutdown();
    try {
        if (!defaultCallbackExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
            defaultCallbackExecutor.shutdownNow();
        }
        if (!defaultAsyncOperationExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
            defaultAsyncOperationExecutor.shutdownNow();
        }
    } catch (InterruptedException e) {
        defaultCallbackExecutor.shutdownNow();
        defaultAsyncOperationExecutor.shutdownNow();
        Thread.currentThread().interrupt();
    }

    // Step 5: Flush buffer if in READY state
    // TODO: Integrate with ConnectionStateMachine and MessageBuffer from spec 02.
    // When spec 02 is implemented, uncomment:
    // if (currentState != ConnectionState.RECONNECTING
    //         && messageBuffer != null && messageBuffer.size() > 0) {
    //     log.info("Flushing {} buffered messages", messageBuffer.size());
    //     messageBuffer.flush(this::sendBufferedMessage);
    // }

    // Step 6: Shutdown gRPC channel
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

    // Step 7: Cleanup
    // TODO: Integrate with reconnectionManager and connectionStateMachine from spec 02.
    // When spec 02 is implemented, uncomment:
    // reconnectionManager.shutdown();
    // connectionStateMachine.transitionTo(ConnectionState.CLOSED);
    // connectionStateMachine.shutdown();

    int remainingClients = clientCount.decrementAndGet();
    if (remainingClients == 0) {
        log.debug("Last client closed");
    }

    log.info("Client shutdown complete");
}
```

#### 7.3.5 Post-Close Guard

Shared with 02-spec REQ-CONN-4. The `ensureNotClosed()` method and `ClientClosedException` are defined there. Referenced here for completeness:

```java
// From 02-connection-transport-spec section 5.3
protected void ensureNotClosed() {
    if (closed.get()) {
        throw new ClientClosedException();
    }
}
```

Every public operation method must call `ensureNotClosed()` as its first statement. This is already specified in 02-spec and applies to all async methods added in this spec.

#### 7.3.6 GS Timeout Conflict Resolution

The GS has conflicting defaults:
- **REQ-CONN-4 (02-connection-transport.md):** drain timeout default 5s -- applies to gRPC channel `awaitTermination()`
- **REQ-CONC-5 (10-concurrency.md):** callback completion timeout default 30s -- applies to waiting for in-flight callbacks

**Resolution:** These are **two separate timeouts** with separate builder parameters:

| Parameter | Default | Purpose |
|-----------|---------|---------|
| `shutdownTimeoutSeconds` | 5 | gRPC channel drain timeout (from 02-spec) |
| `callbackCompletionTimeoutSeconds` | 30 | Wait for in-flight callbacks (from this spec) |

Total worst-case shutdown duration: `callbackCompletionTimeoutSeconds` + `shutdownTimeoutSeconds` + callback executor shutdown (2s) = 37 seconds.

### 7.4 Breaking Changes

- **Behavioral:** `close()` now waits longer before returning (up to 37s vs. previous 5s). This is a behavioral change that could affect test teardown time. Mitigated by the fact that if no operations are in-flight, the callback timeout is skipped (instant check).
- **New builder parameter:** `callbackCompletionTimeoutSeconds` added with default 30. Additive, non-breaking.

### 7.5 Test Scenarios

| # | Scenario | Type | Assertion |
|---|----------|------|-----------|
| T1 | close() waits for in-flight callback to complete | Unit | Start a slow callback (500ms sleep), call close(), verify callback completed |
| T2 | close() times out on stuck callback | Unit | Start a callback that blocks indefinitely, verify close() returns after callbackCompletionTimeoutSeconds |
| T3 | close() is idempotent | Unit | Call close() twice, second call returns immediately |
| T4 | Operations after close() throw ClientClosedException | Unit | Call sendEventsMessage() after close(), expect ClientClosedException |
| T5 | Async operations after close() throw ClientClosedException | Unit | Call sendEventsMessageAsync() after close(), expect ClientClosedException |
| T6 | In-flight counter accurate during concurrent sends | Unit | 10 concurrent async sends, verify counter reaches 10 then returns to 0 |
| T7 | Callback executor shut down after close() | Unit | Verify defaultCallbackExecutor.isTerminated() after close() |
| T8 | close() during RECONNECTING cancels reconnection | Integration | Trigger reconnect, call close(), verify reconnect attempt stops |
| T9 | close() flushes buffer in READY state | Integration | Buffer messages, call close(), verify messages sent before channel shutdown |
| T10 | close() discards buffer in RECONNECTING state | Integration | Buffer messages during reconnect, call close(), verify OnBufferDrain fires with count |
| T11 | try-with-resources: close() called on exception | Integration | Use client in try block, throw exception, verify close() called |
| T12 | Thread leak detection after close() | Integration | Count `kubemq-*` named threads before and after, verify all cleaned up |
| T13 | gRPC channel terminated after close() | Unit | Verify `managedChannel.isTerminated()` after close() |
| T14 | ManagedChannel.shutdownNow() called if awaitTermination times out | Unit | Simulate slow channel shutdown, verify shutdownNow() called |

---

## 8. Cross-Category Dependencies

### 8.1 Dependencies FROM This Category

| This REQ | Depends On | Category | Why |
|----------|-----------|----------|-----|
| REQ-CONC-2 | REQ-ERR-1 (error hierarchy) | 01-Error Handling | `OperationCancelledException`, `TimeoutException`, `KubeMQException` base class required for async error handling |
| REQ-CONC-2 | REQ-ERR-6 (gRPC mapping) | 01-Error Handling | gRPC `CANCELLED` status must map to `OperationCancelledException` |
| REQ-CONC-2 | REQ-ERR-9 (async error propagation) | 01-Error Handling | CompletableFuture exception handling patterns |
| REQ-CONC-5 | REQ-CONN-2 (connection state machine) | 02-Connection | `ConnectionState`, `ConnectionStateMachine` used in close() |
| REQ-CONC-5 | REQ-CONN-4 (graceful shutdown) | 02-Connection | `closed` flag, `ensureNotClosed()`, `shutdownTimeoutSeconds`, `ClientClosedException` |
| REQ-CONC-5 | REQ-CONN-1 (reconnection + buffering) | 02-Connection | `MessageBuffer`, `ReconnectionManager` used in close() |

### 8.2 Dependencies ON This Category

| Other REQ | Depends On | Why |
|-----------|-----------|-----|
| REQ-OBS-1 (tracing) | REQ-CONC-4 | Async spans require async operation boundaries |
| REQ-TEST-1 (unit tests) | REQ-CONC-1 | Thread safety tests validate the documented guarantees |
| REQ-PERF-1 (benchmarks) | REQ-CONC-4 | Async benchmarks require async API |

### 8.3 Shared Implementation with 02-Connection-Transport-Spec

The following items are defined in the 02-spec and reused in this spec:

| Item | Defined In | Used In | Description |
|------|-----------|---------|-------------|
| `closed` AtomicBoolean | 02-spec 5.3 | CONC-5 7.3.4 | Prevents post-close operations |
| `ensureNotClosed()` | 02-spec 5.3 | CONC-2 4.3.2, CONC-5 7.3.5 | Guard method on all public operations |
| `ClientClosedException` | 02-spec 5.3 | CONC-2, CONC-5 | Exception for post-close operations |
| `shutdownTimeoutSeconds` | 02-spec 5.3 | CONC-5 7.3.4 | gRPC drain timeout (default 5s) |
| `ConnectionStateMachine` | 02-spec 3.3.3 | CONC-5 7.3.4 | State query during close() |
| `ReconnectionManager` | 02-spec 2.3.6 | CONC-5 7.3.4 | Cancel reconnection during close() |
| `MessageBuffer` | 02-spec 2.3.3 | CONC-5 7.3.4 | Flush/discard during close() |

### 8.4 Shared Implementation with 01-Error-Handling-Spec

| Item | Defined In | Used In | Description |
|------|-----------|---------|-------------|
| `KubeMQException` | 01-spec 3.2.3 | CONC-2 4.3.7 | Base exception for unwrapException() |
| `OperationCancelledException` | 01-spec (ErrorCode.CANCELLED_BY_CLIENT) | CONC-2 4.3.6-4.3.7 | Cancellation error |
| `TimeoutException` | 01-spec (ErrorCode.OPERATION_TIMEOUT) | CONC-2 4.3.7 | Timeout error |
| gRPC CANCELLED mapping | 01-spec REQ-ERR-6 | CONC-2 AC-3 | Maps gRPC CANCELLED to OperationCancelledException |

---

## 9. Breaking Changes

### 9.1 Summary

| Change | Type | Severity | Mitigation |
|--------|------|----------|------------|
| Callback dispatch moves from gRPC thread to `kubemq-callback` thread | Behavioral | Low | Unlikely any user code depends on gRPC thread identity |
| `close()` may take up to 37s instead of 5s | Behavioral | Low | Only if in-flight ops exist; 0 ops = instant |
| `EventStreamHelper.sendEventMessage()` becomes synchronized | Behavioral | None | Fixes existing race condition |
| `QueueUpstreamHandler.sendQueuesMessageAsync()` visibility `private` -> `public` | API | None (broader access) | Handler is not a primary API class |
| `QueueDownstreamHandler.receiveQueuesMessagesAsync()` visibility `private` -> `public` | API | None | Same as above |

**No breaking changes to the public API.** All changes are additive (new methods) or behavioral (callback threading, shutdown timing). Existing code compiles and runs without modification.

### 9.2 SemVer Impact

All changes in this spec are backward-compatible and can be released in a **minor version** (e.g., v2.2.0).

---

## 10. Migration Guide

### For Users of Existing Sync API

No migration needed. All existing sync methods continue to work identically.

### Adopting the Async API

```java
// Before (sync, blocking)
EventSendResult result = pubSubClient.sendEventsStoreMessage(message);

// After (async, non-blocking)
CompletableFuture<EventSendResult> future =
    pubSubClient.sendEventsStoreMessageAsync(message);

// Option 1: Chain callbacks
future.thenAccept(result -> System.out.println("Sent: " + result.isSent()))
      .exceptionally(ex -> { System.err.println("Error: " + ex); return null; });

// Option 2: Block with custom timeout
EventSendResult result = pubSubClient.sendEventsStoreMessage(message,
    Duration.ofSeconds(10));

// Option 3: Cancel
future.cancel(true); // Propagates to gRPC
```

### Using Subscription Handles

```java
// Before (no handle)
pubSubClient.subscribeToEvents(subscription);
// ... no way to cancel except client.close()

// After (with handle)
Subscription sub = pubSubClient.subscribeToEventsWithHandle(subscription);
// ... later, from any thread:
sub.cancel();
// or async:
sub.cancelAsync().thenRun(() -> log.info("Unsubscribed"));
```

### Configuring Callback Executor

```java
// Custom thread pool for heavy callback processing
ExecutorService workerPool = Executors.newFixedThreadPool(4);

EventsSubscription subscription = EventsSubscription.builder()
    .channel("my-channel")
    .callbackExecutor(workerPool)
    .maxConcurrentCallbacks(4)  // Enable parallel processing
    .onReceiveEventCallback(event -> processEvent(event))
    .build();
```

### Configuring Shutdown Timeouts

```java
PubSubClient client = PubSubClient.builder()
    .address("localhost:50000")
    .clientId("my-client")
    .shutdownTimeoutSeconds(10)            // gRPC drain: 10s (default 5s)
    .callbackCompletionTimeoutSeconds(60)  // Callback wait: 60s (default 30s)
    .build();
```

---

## 11. Open Questions

| # | Question | Recommendation | Impact |
|---|----------|---------------|--------|
| OQ-1 | Should existing sync `subscribeToEvents()` be deprecated in favor of `subscribeToEventsWithHandle()`? | No. Keep both. `subscribeToEvents()` is simpler for fire-and-forget subscriptions. `WithHandle` variant is for when cancellation is needed. | API design |
| OQ-2 | Should `callbackExecutor()` be on the client builder (global default) or only on subscription builders (per-subscription)? | Both. Client-level default via `defaultCallbackExecutor()` builder param, overridable per-subscription. | API design |
| OQ-3 | Should `sendEventsMessage()` (fire-and-forget events) have an async variant? | Yes, for consistency, even though the current implementation is effectively a stream write. The async variant allows users to chain error handling. | Completeness |
| OQ-4 | The `unwrapFuture()` utility references `CancellationException` and `TimeoutException` from 01-spec. If 01-spec is not implemented first, what should sync wrappers throw? | Use `RuntimeException` as a temporary stand-in with `// TODO: replace with OperationCancelledException when 01-spec implemented` comment. The 01-spec should be implemented first per the dependency chain. | Implementation order |
| OQ-5 | Should `inFlightOperations` polling in `close()` use a `CountDownLatch` or `Phaser` instead of polling? | A `Phaser` would be more elegant (register/arrive pattern), but adds complexity. The 50ms polling approach is simple, correct, and the 50ms granularity is acceptable for shutdown scenarios. Keep polling for simplicity. | Implementation detail |
| OQ-6 | Should `maxConcurrentCallbacks` be validated to be >= 1 in the builder? | Yes. Add validation in the builder: `if (maxConcurrentCallbacks < 1) throw new IllegalArgumentException("maxConcurrentCallbacks must be >= 1")`. | Validation |
