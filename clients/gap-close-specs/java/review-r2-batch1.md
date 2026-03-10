# Java SDK Specs -- Implementability Review (Round 2, Batch 1)

**Reviewer:** Senior Java Developer (Implementer)
**Specs Reviewed:** 01-error-handling, 02-connection-transport, 03-auth-security, 07-code-quality
**Date:** 2026-03-09

---

## Round 1 Fix Verification

| R1 Issue | Status | Verification |
|----------|--------|-------------|
| C-1: Invalid Java import alias | VERIFIED | 01-spec section 8.2.1 line 1333 now uses `import com.google.rpc.Status;` with a disambiguation comment. However, the line `import com.google.rpc.Status; // Note: fully qualify as com.google.rpc.Status where needed` is slightly misleading -- see Minor m-2 below for a concrete naming issue in the method body. The import itself compiles. |
| C-2: MessageBuffer race condition | VERIFIED | 02-spec section 2.3.3 now uses a CAS loop for the ERROR policy. The implementation correctly uses `compareAndSet` on `currentSizeBytes` to atomically reserve space before enqueueing. |
| C-3: BufferFullException vs BackpressureException | VERIFIED | 02-spec section 2.3.5 now explicitly states "Do NOT create a separate BufferFullException class" and uses `BackpressureException` throughout. Stub strategy provided for the case where ERR-1 is not yet implemented. |
| C-4: AuthenticationException not extending KubeMQException | VERIFIED | 03-spec stub strategy (cross-category dependencies section) now shows `AuthenticationException extends KubeMQException` with `@Deprecated` on stub constructors and migration notes. |
| C-5: AuthInterceptor static vs dynamic token | VERIFIED | 07-spec section 2.3.2 now uses `Supplier<String> tokenSupplier` pattern, reading the token per-call. Compatible with REQ-AUTH-1 mutable token and REQ-AUTH-4 credential provider. |
| M-1: Silent parameter clamping | VERIFIED | 01-spec section 5.2.1 now uses `requireInRange()` methods that throw `IllegalArgumentException` for out-of-range values. |
| M-2: CancellationException name clash | VERIFIED | Renamed to `OperationCancelledException` throughout 01-spec. |
| M-3: Recursive CAS in transitionTo() | VERIFIED | 02-spec section 3.3.3 now uses a `while(true)` CAS loop instead of recursion. |
| M-4: Static ThreadLocalRandom field | VERIFIED | 02-spec section 2.3.6 has a comment "Do NOT store ThreadLocalRandom.current() as a static field" and uses `ThreadLocalRandom.current()` at the call site in `computeDelay()`. |
| M-5: ConfigurationException not extending KubeMQException | VERIFIED | 03-spec section 3.2.8 now shows `ConfigurationException extends KubeMQException`. |
| M-6: Busy-wait polling in ensureReady() | PARTIALLY VERIFIED | The R1 fix notes say "Replaced busy-wait with CompletableFuture-based approach" but I could not find the actual `ensureReady()` implementation in the spec text. See Critical C-1 below -- the `ensureReady()` method body is missing from the spec. |
| M-7: No state transition validation | VERIFIED | 02-spec section 3.3.3 now includes `isValidTransition()` with explicit allowed transitions. |
| M-8: CredentialException wrapping as bare RuntimeException | VERIFIED | 03-spec section 3.4.3 `CredentialManager.refreshToken()` now wraps retryable errors as `ConnectionException` instead of bare `RuntimeException`. |
| M-9: TransportFactory for cross-package access | VERIFIED | 07-spec section 2.3.1 now includes `TransportFactory.create()` as the public factory. |
| M-10: error/ vs exception/ package inconsistency | PARTIALLY VERIFIED | 07-spec section 6.2 GS Target Structure still shows `error/` in one place (line 931: `io.kubemq.sdk.error`). See Minor m-1 below. |
| M-11: enableRetry() in 03-spec | VERIFIED | 03-spec section 3.2.9 now has a comment: "NOTE: .enableRetry() intentionally removed per REQ-ERR-3". |
| m-2: RetryExecutor semaphore scope | VERIFIED | 01-spec section 5.2.3 now has a design intent comment clarifying the semaphore limits concurrent backoff storms. |
| m-6: ConnectionNotReadyException undefined | VERIFIED | 02-spec changed to extend `KubeMQException` with a note on error hierarchy integration. |
| m-9: Checkstyle Lombok accommodation | VERIFIED | 07-spec section 4.2.3 checkstyle.xml now includes `allowedAnnotations` property with `Getter,Setter,Builder,Data,Value`. |
| m-10: OWASP in verify phase | VERIFIED | 07-spec section 5.2.5 now uses a Maven profile `-Psecurity` instead of default verify phase. |
| Missing-1: extractRichDetails() body | VERIFIED | 01-spec section 8.2.1 expanded. |
| Missing-2: RetryExecutor/connection state | NOT VERIFIED | Deferred to R2. See Major M-1. |
| Missing-3: sendBufferedMessage() undefined | NOT VERIFIED | Deferred to R2. See Major M-2. |
| Missing-4: ArchUnit enforcement | NOT VERIFIED | Deferred to R2. See Minor m-6. |

---

## Review Summary

| Spec | Issues Found | Critical | Major | Minor |
|------|-------------|----------|-------|-------|
| 01-error-handling | 5 | 0 | 2 | 3 |
| 02-connection-transport | 6 | 1 | 3 | 2 |
| 03-auth-security | 4 | 0 | 2 | 2 |
| 07-code-quality | 3 | 0 | 1 | 2 |
| Cross-spec | 2 | 0 | 1 | 1 |
| **Total** | **20** | **1** | **9** | **10** |

---

## Critical Issues (MUST FIX)

### C-1: `ensureReady()` method body missing from 02-connection-transport-spec.md

**Spec:** 02-connection-transport-spec.md
**Section:** REQ-CONN-5 (section 6.3.5, referenced from R1 M-6 fix)
**Problem:** R1 fix M-6 states that `ensureReady()` was replaced with a CompletableFuture-based approach, but the actual method implementation is not present in the spec. The spec references `ensureReady()` in the WaitForReady discussion (REQ-CONN-5) and the GS requires WaitForReady to block during CONNECTING/RECONNECTING states. Without the implementation, I cannot build this -- it is a critical integration point between the state machine, the retry executor, and every public operation method.
**Fix:** Add the `ensureReady()` method body to section 6.3.5 with:
1. How it registers a one-shot listener on `ConnectionStateMachine`
2. How it creates a `CompletableFuture` that completes when state becomes READY
3. How it applies the operation timeout to the future (via `orTimeout()` or `get(timeout)`)
4. What exception it throws on timeout (should be `ConnectionNotReadyException`)
5. Whether it is called before or after `ensureNotClosed()`
**Impact:** Cannot implement WaitForReady behavior. Every public method needs to call either `ensureNotClosed()` then `ensureReady()`, or a combined guard. The calling convention must be specified.

---

## Major Issues (SHOULD FIX)

### M-1: RetryExecutor does not check connection state between retry attempts

**Spec:** 01-error-handling-spec.md
**Section:** 5.2.3 RetryExecutor
**Problem:** The R1 review noted this as Missing-2 and deferred to R2. The GS REQ-CONN-1 AC-10 states "Operation retries suspended during RECONNECTING." The `RetryExecutor.execute()` loop sleeps and retries without checking whether the connection is still READY. If the connection drops during the backoff sleep, the next retry attempt will fail immediately with a transport error, wasting the retry budget. Worse, if WaitForReady is false, the retry will throw `ConnectionNotReadyException` -- which is not retryable -- consuming the retry.
**Fix:** Before each retry attempt in `RetryExecutor.execute()`, call `ensureReady()` (from REQ-CONN-5). If the connection is RECONNECTING and WaitForReady is true, the retry waits for reconnection. If WaitForReady is false, skip the retry and throw immediately. The `RetryExecutor` needs access to the connection state -- pass a `Supplier<ConnectionState>` or the `ConnectionStateMachine` as a constructor parameter.

### M-2: `sendBufferedMessage()` method is never defined

**Spec:** 02-connection-transport-spec.md
**Section:** 5.3 (close() method)
**Problem:** The R1 review noted this as Missing-3 and deferred to R2. The `close()` method in section 5.3 calls `messageBuffer.flush(this::sendBufferedMessage)` but `sendBufferedMessage()` is never defined anywhere in the spec. The `BufferedMessage` interface has `Object grpcRequest()` which returns an opaque object. The implementer needs to know: (a) how to dispatch a `BufferedMessage` back through the correct client method (events vs. queue vs. command), (b) what happens if sending a buffered message fails during the flush, (c) whether the `Consumer<BufferedMessage>` in `flush()` should swallow exceptions or propagate them.
**Fix:** Define `sendBufferedMessage(BufferedMessage msg)` on `KubeMQClient`. It should use a type discriminator on `BufferedMessage` (e.g., an enum `MessageType { EVENT, EVENT_STORE, QUEUE }`) to route to the correct send path. Failed sends during flush should be logged at WARN and dropped (not re-buffered, to avoid infinite loops). Alternatively, add the send function as a field on `BufferedMessage` itself (a `Runnable` or `Supplier` captured at buffer time).

### M-3: `ConnectionStateMachine.transitionTo()` has broken indentation suggesting copy-paste error

**Spec:** 02-connection-transport-spec.md
**Section:** 3.3.3 ConnectionStateMachine
**Problem:** In the `transitionTo()` method (around line 892), the code after the CAS succeeds is indented at a different level from the `while(true)` block. The `log.info(...)` and `listenerExecutor.submit(...)` lines appear to be outside the CAS success path, but they should be inside. Looking at the structure: after `state.compareAndSet(oldState, newState)` succeeds, the code falls through to `log.info` and listener notification, which is correct. However, the indentation is severely misaligned (the `log.info` on line 892 is at 8-space indent while the CAS block is at 12-space indent), making the code appear to be outside the `while` loop when it is actually inside it.
**Fix:** Fix the indentation so the log and listener notification are clearly inside the `while(true)` block, after the CAS success. This is a spec formatting issue, not a logic bug, but as the implementer I need to be certain about the execution flow.

### M-4: `BackpressureException` stub in 02-spec uses wrong constructor signature

**Spec:** 02-connection-transport-spec.md
**Section:** 2.3.5
**Problem:** The stub `BackpressureException` (for when REQ-ERR-1 is not yet implemented) extends `RuntimeException` with a single `String message` constructor. But the `MessageBuffer.add()` ERROR policy path (section 2.3.3, CAS loop) constructs it as `throw new BackpressureException("Reconnection buffer full ...")`. When REQ-ERR-1 is implemented, this constructor will not exist -- the builder pattern is required. Meanwhile, the recommended usage in section 2.3.5 shows `BackpressureException.builder().code(...).message(...).build()`. These two call sites are inconsistent -- the CAS loop uses the constructor while the recommended usage uses the builder.
**Fix:** Make the CAS loop code in `MessageBuffer.add()` use the builder pattern: `throw BackpressureException.builder().code(ErrorCode.BUFFER_FULL).message("...").build()`, with a note that during the stub phase, a simplified constructor is acceptable. Better yet, extract a helper method `BackpressureException.bufferFull(long current, long max, long messageSize)` that works with both the stub and the full implementation.

### M-5: `CredentialManager` references undefined `ConnectionException`

**Spec:** 03-auth-security-spec.md
**Section:** 3.4.3 CredentialManager
**Problem:** The `refreshToken()` method (line 1170) throws `new ConnectionException("Transient credential provider failure: ..." , e)`. But `ConnectionException` is defined in 01-error-handling-spec.md with a builder pattern, not a `(String, Throwable)` constructor. The stub strategy in the cross-category dependencies section provides stubs for `AuthenticationException` and `ConfigurationException` but NOT for `ConnectionException`. If implementing 03 before 01, `ConnectionException` does not exist.
**Fix:** Either (a) add `ConnectionException` to the stub strategy section in 03-spec, or (b) change the retryable wrapping to use a generic `KubeMQException` stub with `retryable=true`, or (c) explicitly state that `CredentialManager` can only be implemented after REQ-ERR-1 provides `ConnectionException`. Option (a) is simplest.

### M-6: `ReconnectionManager.startReconnection()` is recursive

**Spec:** 02-connection-transport-spec.md
**Section:** 2.3.6 ReconnectionManager
**Problem:** The `startReconnection()` method schedules a reconnect via `scheduler.schedule(...)`. Inside the scheduled lambda, on failure, it calls `startReconnection(reconnectAction)` recursively. While this is not stack recursion (it runs on the scheduler thread), the `attemptCounter.incrementAndGet()` is called once per `startReconnection()` invocation. If the reconnect action fails fast (e.g., throws immediately), the scheduler will rapidly schedule the next attempt without any delay-based throttling on the *scheduling* itself. The delay is applied to the *execution*, but if `computeDelay()` returns 0 (jitter could return 0), the scheduler floods with tasks.
**Fix:** Ensure `computeDelay()` always returns at least `initialReconnectDelayMs / 2` (a minimum floor). The full jitter `ThreadLocalRandom.current().nextLong(cappedDelay + 1)` can return 0. Add: `return Math.max(config.getInitialReconnectDelayMs() / 2, ThreadLocalRandom.current().nextLong(cappedDelay + 1))`.

### M-7: `TransportFactory.create()` exposes `io.grpc.ClientInterceptor` in its public API

**Spec:** 07-code-quality-spec.md
**Section:** 2.3.1 TransportFactory
**Problem:** `TransportFactory.create(TransportConfig config, List<ClientInterceptor> interceptors)` is `public` but its second parameter is `List<io.grpc.ClientInterceptor>`. This leaks gRPC types into the public factory API, contradicting the REQ-CQ-1 goal of isolating gRPC to the transport package. Any code calling `TransportFactory.create()` must import `io.grpc.ClientInterceptor`.
**Fix:** Since `TransportFactory` is called only by `KubeMQClient` (which is in `io.kubemq.sdk.client`), change the interceptor list to be built internally by `GrpcTransport` based on the `TransportConfig`. The `TransportConfig` should carry the token supplier and any flags needed to configure interceptors. The `TransportFactory.create()` signature becomes `create(TransportConfig config)` with no gRPC types. Alternatively, make `TransportFactory` package-private and provide a public builder on `Transport` that hides gRPC types.

### M-8: 03-spec `classifyTlsException()` falls back to `new RuntimeException(...)` for network errors

**Spec:** 03-auth-security-spec.md
**Section:** 3.2.8
**Problem:** The network error path in `classifyTlsException()` (line 569) returns `new RuntimeException("TLS handshake failed due to network error: ...")`. The comment says "Will be TransientException once REQ-ERR-1 is implemented" but provides no stub and no timeline. If I implement 03 before 01 (which the implementation order allows for AUTH-1 through AUTH-5), this code throws a bare `RuntimeException`, violating the GS rule that SDK methods never throw raw `RuntimeException`.
**Fix:** Use `ConnectionException` (with a stub if needed) or at minimum `KubeMQException` stub for this path. Add `ConnectionException` to the stub strategy section.

---

## Minor Issues (NICE TO FIX)

### m-1: 07-spec section 6.2 still references `io.kubemq.sdk.error` package

**Spec:** 07-code-quality-spec.md
**Section:** 6.2 GS Target Structure, 6.3.1
**Problem:** Line 931 shows `io.kubemq.sdk.error` in the "New Packages" table. R1 fix M-10 standardized on `exception/` but missed this one reference. Section 6.2 also shows `exception/` correctly, creating an internal inconsistency within section 6.
**Fix:** Change line 931 from `io.kubemq.sdk.error` to `io.kubemq.sdk.exception`.

### m-2: `GrpcErrorMapper` has unresolvable import collision in method body

**Spec:** 01-error-handling-spec.md
**Section:** 8.2.1 GrpcErrorMapper
**Problem:** Line 1333 imports both `io.grpc.Status` and `com.google.rpc.Status` with a comment to "fully qualify as needed." But the method body at line 1359 declares `Status status = grpcError.getStatus()` which uses `io.grpc.Status`. The `com.google.rpc.Status` is used only in `extractRichDetails()`. The import statement `import com.google.rpc.Status;` will cause a compile error because `Status` is ambiguous. The implementer must either: (a) import only `io.grpc.Status` and fully qualify `com.google.rpc.Status`, or (b) import neither and fully qualify both.
**Fix:** Remove the `com.google.rpc.Status` import. Fully qualify it in `extractRichDetails()` as `com.google.rpc.Status rpcStatus = StatusProto.fromThrowable(grpcError)`.

### m-3: `RetryPolicy.computeBackoff()` has `ThreadLocalRandom` usage in a potentially non-thread-local context

**Spec:** 01-error-handling-spec.md
**Section:** 5.2.1 RetryPolicy
**Problem:** `computeBackoff()` is an instance method on the immutable `RetryPolicy` object. It calls `ThreadLocalRandom.current()` which is correct for thread-safety. However, `RetryPolicy` is shared across all operations and threads (it is a client-level configuration). This is fine because `ThreadLocalRandom.current()` returns the caller's thread-local instance. No actual issue -- just noting that the spec should document that `computeBackoff()` is thread-safe despite being on a shared object. This avoids implementer confusion about whether `RetryPolicy` needs synchronization.
**Fix:** Add a brief `@implNote This method is thread-safe` Javadoc note on `computeBackoff()`.

### m-4: `EventsStoreSubscription` reconnect logic casts sequence to `int`

**Spec:** 02-connection-transport-spec.md
**Section:** 2.3.7
**Problem:** In `buildReconnectSubscribe()`, the line `setEventsStoreTypeValue((int) (lastSeq + 1))` casts a `long` to `int`. If the sequence number exceeds `Integer.MAX_VALUE`, this silently overflows. The protobuf field `EventsStoreTypeValue` is likely an `int32` in the proto definition, but the spec should verify this. If the server uses `int64` for sequences, this cast is lossy.
**Fix:** Verify the protobuf definition for `eventsStoreTypeValue`. If it is `int32`, document the 2^31 sequence limit. If it is `int64`, change the setter. Either way, add a range check.

### m-5: `CredentialManager.scheduleProactiveRefresh()` body is truncated

**Spec:** 03-auth-security-spec.md
**Section:** 3.4.3
**Problem:** The method body is cut off at line 1199 with the comment "// Refresh at (lifetime - buffer) or (lifetime * 0.9), whichever is smaller". The actual scheduling logic (computing the delay and calling `scheduler.schedule()`) is not shown. The implementer can infer the intent but the exact implementation is missing.
**Fix:** Complete the method body showing the delay computation and the `scheduler.schedule(() -> refreshToken(), delay, TimeUnit.MILLISECONDS)` call.

### m-6: No ArchUnit or dependency enforcement specified

**Spec:** 07-code-quality-spec.md
**Problem:** R1 Missing-4 noted the lack of ArchUnit tests and deferred to R2. The GS REQ-CQ-1 AC-6 requires "Dependencies flow downward only." Without enforcement, the layering invariant will degrade over time as developers add gRPC imports to convenience methods outside the transport package. An ArchUnit test like `noClasses().that().resideInAPackage("..client..").should().dependOnClassesThat().resideInAPackage("io.grpc..")` would catch violations at test time.
**Fix:** Add an ArchUnit test specification in the test plan section of 07-spec. Even a single test class with 2-3 rules would be sufficient.

### m-7: Error Prone plugin configuration may not work with Java 11

**Spec:** 07-code-quality-spec.md
**Section:** 4.2.1
**Problem:** Error Prone 2.28.0 requires Java 11+ for compilation, which matches the SDK target. However, Error Prone uses `-XDcompilePolicy=simple` and `-Xplugin:ErrorProne` compiler args that require the `javac` from JDK 11+. If the build uses JDK 11 (not 17+), some Error Prone checks may not be available or may require additional `--add-exports` JVM flags for the compiler process. The spec should note the minimum JDK version for building (not just the target bytecode version).
**Fix:** Add a note: "Building with Error Prone requires JDK 11+ (JDK 17+ recommended). Add `--add-exports` and `--add-opens` flags if building with JDK 16+ where strong encapsulation is enforced."

### m-8: `ClientClosedException` does not extend `KubeMQException`

**Spec:** 02-connection-transport-spec.md
**Section:** 5.3
**Problem:** `ClientClosedException extends RuntimeException` with a note "move to error package when REQ-ERR-1 is done." This is the same pattern that R1 fixed for `BufferFullException` (C-3) and `AuthenticationException` (C-4). For consistency, `ClientClosedException` should either extend `KubeMQException` from the start (using the stub) or be explicitly listed in the stub strategy.
**Fix:** Change to `extends KubeMQException` using the stub, consistent with the resolution for C-3 and C-4.

### m-9: `ConnectionNotReadyException` placement unclear

**Spec:** 02-connection-transport-spec.md
**Problem:** R1 m-6 was marked FIXED (changed to extend `KubeMQException`), but the actual class definition with its new parent class is not shown in the spec. The implementer needs to know: what package, what `ErrorCode`, what `ErrorCategory`, and whether it is retryable.
**Fix:** Add a brief class definition: `ConnectionNotReadyException extends KubeMQException` with `ErrorCode.UNAVAILABLE`, `ErrorCategory.TRANSIENT`, `retryable=false` (because the caller should wait for ready, not retry), in `io.kubemq.sdk.exception`.

### m-10: `@AllArgsConstructor` on `KubeMQClient` conflicts with new fields

**Spec:** Multiple (02-spec adds `connectionStateMachine`, `reconnectionManager`, `reconnectionConfig`; 03-spec adds `authTokenRef`)
**Problem:** The existing `KubeMQClient` uses `@AllArgsConstructor` (line 32 of `KubeMQClient.java`). Adding new fields (`connectionStateMachine`, `reconnectionManager`, `closed`, `messageBuffer`, `authTokenRef`) will change the generated constructor signature, which is a breaking change for all three subclasses (`PubSubClient`, `QueuesClient`, `CQClient`) that call `super(...)`. Since these subclasses use `@Builder` and their builders call the parent constructor, every new field requires updating all three subclasses.
**Fix:** The spec should acknowledge this and recommend replacing `@AllArgsConstructor` with an explicit constructor that takes a `ClientConfig` or builder object, consolidating all parameters. This is a prerequisite step before any field additions. Without this, each spec's field additions will cascade into subclass modifications.

---

## Missing Implementation Details

1. **`ensureReady()` full implementation** (see C-1). This is the most critical missing piece. It ties together: state machine, WaitForReady config, operation timeout, and the calling convention for every public method.

2. **`BufferedMessage` concrete implementations.** The spec defines `BufferedMessage` as an interface with `estimatedSizeBytes()` and `grpcRequest()`. What classes implement this? Presumably wrapper classes for each message type (EventMessage, QueueMessage, etc.). The spec should show at least one concrete implementation so the implementer knows the pattern.

3. **How `ReconnectionManager` triggers subscription re-establishment.** The spec says subscriptions register callbacks with the manager, but no registration API is shown. How does `EventsSubscription` register with `ReconnectionManager` for re-subscribe after reconnection? Is it via `ConnectionStateListener.onReconnected()`?

4. **Thread model for `MessageBuffer.flush()`.** Which thread calls `flush()`? The `ReconnectionManager` scheduler thread? The state machine listener thread? This matters because `flush()` calls `sender.accept(msg)` which sends gRPC requests -- if this runs on the single-thread listener executor, it blocks all further state transitions.

5. **`GrpcErrorMapper` integration points.** The spec says "wrap all gRPC call sites with the mapper" but does not specify whether this is done via a `ClientInterceptor` (as suggested in 07-spec's `ErrorMappingInterceptor`) or via explicit calls at each catch site. The 07-spec shows an `ErrorMappingInterceptor` in the interceptor chain, but the 01-spec shows `GrpcErrorMapper` as a static utility. These are different patterns. Clarify: is the mapper called from an interceptor (centralized) or from catch blocks (distributed)?

6. **Order of guard checks in public methods.** Each public method needs: (a) `ensureNotClosed()`, (b) `ensureReady()`, (c) parameter validation, (d) the operation itself. The spec should specify this order explicitly, because `ensureReady()` may block and `ensureNotClosed()` must come first.

---

## Required: Commit Sequence

### Spec 01: Error Handling & Resilience

| # | Commit | Files | Can Test Independently? |
|---|--------|-------|------------------------|
| 1 | Add `ErrorCode` enum and `ErrorCategory` enum | `exception/ErrorCode.java`, `exception/ErrorCategory.java` | Yes -- enum value tests |
| 2 | Add `KubeMQException` base class with builder | `exception/KubeMQException.java` | Yes -- builder tests, toString tests, serialization test |
| 3 | Add all exception subclasses | `exception/ConnectionException.java`, `exception/AuthenticationException.java`, ...(14 files) | Yes -- default code/category/retryable tests per subclass |
| 4 | Deprecate legacy exceptions, reparent under KubeMQException | `exception/GRPCException.java`, `exception/CreateChannelException.java`, etc. | Yes -- instanceof KubeMQException test |
| 5 | Add `ErrorClassifier` utility | `exception/ErrorClassifier.java` | Yes -- classification tests |
| 6 | Add `ErrorMessageBuilder` | `exception/ErrorMessageBuilder.java` | Yes -- suggestion map tests |
| 7 | Add `GrpcErrorMapper` | `exception/GrpcErrorMapper.java` | Yes -- all 17 gRPC code mapping tests |
| 8 | Migrate throw sites (20+ locations) | `PubSubClient.java`, `CQClient.java`, `KubeMQClient.java`, `KubeMQUtils.java`, `EventStreamHelper.java`, `QueueUpstreamHandler.java`, `QueueDownstreamHandler.java` | Yes -- existing tests should pass with new exception types (behavioral) |
| 9 | Add `Defaults` class and timeout overloads | `common/Defaults.java`, all client classes | Yes -- verify default timeout applied |
| 10 | Add `RetryPolicy`, `OperationSafety`, `RetryExecutor` | `retry/RetryPolicy.java`, `retry/OperationSafety.java`, `retry/RetryExecutor.java` | Yes -- retry logic unit tests |
| 11 | Remove `.enableRetry()` from gRPC channel builder | `KubeMQClient.java` (2 lines) | Yes -- verify gRPC retry disabled |
| 12 | Integrate RetryExecutor into client operations | All client classes | Yes -- integration tests with mock failures |

### Spec 02: Connection & Transport

| # | Commit | Files | Can Test Independently? |
|---|--------|-------|------------------------|
| 1 | Add `ConnectionState` enum, `ConnectionStateListener` interface | `client/ConnectionState.java`, `client/ConnectionStateListener.java` | Yes -- enum and interface compilation |
| 2 | Add `ConnectionStateMachine` | `client/ConnectionStateMachine.java` | Yes -- state transition tests, invalid transition rejection, listener notification |
| 3 | Add `ReconnectionConfig`, `BufferOverflowPolicy` | `client/ReconnectionConfig.java`, `client/BufferOverflowPolicy.java` | Yes -- builder defaults, enum values |
| 4 | Add `BufferedMessage` interface, `MessageBuffer` | `client/BufferedMessage.java`, `client/MessageBuffer.java` | Yes -- add/flush/discard unit tests, CAS loop test, BLOCK policy test |
| 5 | Add `ReconnectionManager` | `client/ReconnectionManager.java` | Yes -- backoff computation, jitter, max attempts, cancel |
| 6 | Integrate state machine + reconnection into `KubeMQClient` | `client/KubeMQClient.java` | Yes -- state transitions during connect/close |
| 7 | Fix EventsStore recovery semantics | `pubsub/EventsStoreSubscription.java` | Yes -- sequence tracking test |
| 8 | Add DNS re-resolution (`dns:///` scheme) | `client/KubeMQClient.java` | Yes -- verify address format |
| 9 | Update keepalive defaults | `client/KubeMQClient.java` | Yes -- verify 10s/5s defaults |
| 10 | Add `close()` improvements (idempotent, closed flag, configurable timeout) | `client/KubeMQClient.java`, `client/ClientClosedException.java` | Yes -- close behavior tests |
| 11 | Add connection config validation + WaitForReady + `ensureReady()` | `client/KubeMQClient.java` | Yes -- fail-fast validation tests |
| 12 | Remove per-subscription reconnection, delegate to ReconnectionManager | All subscription classes | Yes -- verify centralized reconnection |

### Spec 03: Auth & Security

| # | Commit | Files | Can Test Independently? |
|---|--------|-------|------------------------|
| 1 | Add mutable token via `AtomicReference` + `setAuthToken()` | `client/KubeMQClient.java` | Yes -- token update tests |
| 2 | Replace `MetadataInterceptor` with `AuthInterceptor` (dynamic token) | `client/KubeMQClient.java` | Yes -- interceptor tests with mock |
| 3 | Add `AuthenticationException` stub (extends `KubeMQException` stub) | `exception/AuthenticationException.java`, `exception/KubeMQException.java` (stub) | Yes -- if implementing before 01 |
| 4 | Add `wrapGrpcException()` for UNAUTHENTICATED | `client/KubeMQClient.java` | Yes -- exception type assertion |
| 5 | Add TLS builder fields + address-aware TLS defaulting | `client/KubeMQClient.java` | Yes -- localhost detection, TLS defaults |
| 6 | Add PEM bytes support, server name override, InsecureSkipVerify | `client/KubeMQClient.java` | Yes -- validation tests |
| 7 | Add TLS 1.2 minimum + handshake failure classification | `client/KubeMQClient.java`, `exception/ConfigurationException.java` | Yes -- SSLException classification |
| 8 | Add mTLS PEM validation | `client/KubeMQClient.java` | Yes -- validation tests |
| 9 | Add `auth/` package: `TokenResult`, `CredentialProvider`, `CredentialException`, `StaticTokenProvider` | 4 new files in `auth/` | Yes -- unit tests for each class |
| 10 | Add `CredentialManager` | `auth/CredentialManager.java` | Yes -- caching, serialization, invalidation tests |
| 11 | Integrate CredentialManager with AuthInterceptor | `client/KubeMQClient.java` | Yes -- end-to-end token refresh test |

### Spec 07: Code Quality & Architecture

| # | Commit | Files | Can Test Independently? |
|---|--------|-------|------------------------|
| 1 | Delete `QueueDownStreamProcessor`, restrict handler access modifiers | 6 files | Yes -- compilation test |
| 2 | Add `@Internal` annotation, deprecate `KubeMQUtils` | `common/Internal.java`, `common/KubeMQUtils.java` | Yes -- compilation |
| 3 | Remove `commons-lang3` and `grpc-alts` dependencies | `pom.xml` | Yes -- `mvn compile` |
| 4 | Move logback to test scope, deprecate `setLogLevel()`, add SLF4J API | `pom.xml`, `client/KubeMQClient.java` | Yes -- `mvn test` |
| 5 | Add Spotless plugin, run initial format | `pom.xml`, all Java files | Yes -- `mvn spotless:check` |
| 6 | Add Error Prone plugin | `pom.xml` | Yes -- `mvn compile` |
| 7 | Add Checkstyle plugin + config files | `pom.xml`, `checkstyle.xml`, `checkstyle-suppressions.xml` | Yes -- `mvn checkstyle:check` |
| 8 | Add OWASP plugin (in `-Psecurity` profile) | `pom.xml`, `dependency-check-suppressions.xml` | Yes -- `mvn verify -Psecurity` |
| 9 | Add PR template + branch protection documentation | `.github/PULL_REQUEST_TEMPLATE.md` | Yes -- GitHub configuration |
| 10 | Credential leak audit: add `@ToString(exclude)` on TransportConfig | `transport/TransportConfig.java` | Yes -- toString test |
| 11 | Create `transport/` package: `Transport` interface, `TransportConfig`, `TransportFactory` | New package | Yes -- compilation |
| 12 | Create `GrpcTransport` by extracting from KubeMQClient | Major refactor | Yes but requires comprehensive regression testing |
| 13 | Extract `AuthInterceptor` to transport package | `transport/AuthInterceptor.java` | Yes -- interceptor unit test |
| 14 | Update client classes to use `Transport` instead of raw stubs | All client classes | Yes -- all existing tests must pass |

---

## Open Questions

1. **Q-1: What is the calling convention for public methods?** Should every public method call `ensureNotClosed()` then `ensureReady()` then validate params? Or should there be a single `preOperation()` method that combines all three? The spec leaves this to the implementer but consistency across 30+ public methods requires a documented pattern.

2. **Q-2: Should `BufferedMessage.grpcRequest()` return `Object` or a typed wrapper?** Returning `Object` requires casting in `sendBufferedMessage()`. A typed wrapper (e.g., `sealed interface` with per-message-type variants) would be safer but requires Java 17+. For Java 11, consider an enum-based type discriminator.

3. **Q-3: How does `ReconnectionManager` interact with queue stream reconnection?** Queue streams (`QueueUpstreamHandler`, `QueueDownstreamHandler`) have their own stream lifecycle independent of the gRPC channel. The spec says to add reconnection to these handlers (section 2.3.7) but also says to centralize reconnection in `ReconnectionManager` (section 2.3.10). Which component is responsible for queue stream reconnection -- the handler or the manager?

4. **Q-4: What is the implementation order between 01 and 07?** Spec 07 REQ-CQ-1 (layered architecture) creates the `transport/` package. Spec 01 REQ-ERR-6 creates `GrpcErrorMapper` which references gRPC types. If 01 is implemented first, `GrpcErrorMapper` goes into `exception/` with gRPC imports. If 07 is implemented first, it goes into `transport/` as `ErrorMappingInterceptor`. The specs present both designs without resolving which one wins. The recommended order appears to be: implement 01 error hierarchy first (commits 1-8), then 07 CQ-3 through CQ-7 (the low-hanging fruit), then 07 CQ-1 (transport extraction) which moves the mapper into the interceptor chain.

5. **Q-5: Should the `Spotless` initial format be a separate commit or combined with the plugin addition?** A whole-codebase reformat will touch every file, making git blame useless for those files. Best practice is to do it as a single "format codebase" commit that can be ignored in `git blame` via `.git-blame-ignore-revs`.

---

## Fixes Applied (Round 2)

| Issue | Status | Change Made |
|-------|--------|-------------|
| C-1: `ensureReady()` method body missing | FIXED | Method was already present at 02-spec section 6.3.5 (lines 1527-1580). Added calling convention documentation specifying guard sequence: ensureReady() calls ensureNotClosed() internally; every public method calls ensureReady() then validates params then executes. |
| M-1: RetryExecutor does not check connection state | FIXED | Added `connectionReadySupplier` parameter to RetryExecutor constructor. Before each retry, checks if connection is READY; abandons retry if not. Supports REQ-CONN-1 AC-10. |
| M-2: `sendBufferedMessage()` undefined | FIXED | Added `sendBufferedMessage(BufferedMessage)` method definition on KubeMQClient in 02-spec section 5.3. Uses `MessageType` enum discriminator on `BufferedMessage` interface to route to correct send path. Failed sends logged and dropped. |
| M-3: `transitionTo()` broken indentation | FIXED | Re-indented log.info and listenerExecutor.submit blocks to be clearly inside the while(true) CAS loop after successful compareAndSet. |
| M-4: BackpressureException constructor vs builder inconsistency | FIXED | CAS loop now calls `BackpressureException.bufferFull(current, max, size)` helper. Added `bufferFull()` factory method to stub class. Note added that full implementation should use builder pattern. |
| M-5: CredentialManager references undefined ConnectionException | FIXED | Added `ConnectionException` stub to 03-spec stub strategy section, alongside existing AuthenticationException and ConfigurationException stubs. |
| M-6: ReconnectionManager `computeDelay()` can return 0 | FIXED | Added minimum floor `config.getInitialReconnectDelayMs() / 2` to computeDelay(). Full jitter result is now `Math.max(minDelay, jitteredDelay)`. |
| M-7: TransportFactory exposes ClientInterceptor in public API | FIXED | Changed `TransportFactory.create(config, interceptors)` to `create(config)`. Interceptors built internally by GrpcTransport from TransportConfig. Replaced `authToken` field on TransportConfig with `tokenSupplier` (Supplier<String>). Removed `io.grpc.ClientInterceptor` import from factory. |
| M-8: classifyTlsException() uses bare RuntimeException | FIXED | Changed network error path to throw `ConnectionException` instead of `RuntimeException`. Added ConnectionException to stub strategy in 03-spec. |
| m-1: 07-spec references `io.kubemq.sdk.error` | FIXED | Changed to `io.kubemq.sdk.exception` in section 6.3.1 table, module-info example, and ArchUnit test rule. |
| m-2: GrpcErrorMapper import collision | FIXED | Removed `import com.google.rpc.Status`. Added comment to use fully qualified name in extractRichDetails(). Already was fully qualified in method body. |
| m-3: computeBackoff() thread-safety undocumented | FIXED | Added `@implNote This method is thread-safe` Javadoc to computeBackoff(). |
| m-4: EventsStoreSubscription long-to-int cast | SKIPPED | Requires verifying protobuf definition; spec already has a comment about the cast. Low risk for R2. |
| m-5: scheduleProactiveRefresh() body truncated | SKIPPED (already complete) | Method body at 03-spec lines 1186-1221 is complete with delay computation and scheduler.schedule() call. |
| m-6: No ArchUnit enforcement | SKIPPED (already present) | ArchUnit test and dependency already specified at 07-spec section 13.1 (lines 1338-1371). |
| m-7: Error Prone Java version note | SKIPPED | Subjective build documentation note; existing spec already notes Java 11+ requirement. |
| m-8: ClientClosedException not extending KubeMQException | FIXED | Changed to `extends KubeMQException`, moved to `io.kubemq.sdk.exception` package. Updated file inventory table. |
| m-9: ConnectionNotReadyException placement unclear | FIXED | Added explicit class definition with package `io.kubemq.sdk.exception`, documented ErrorCode.UNAVAILABLE, ErrorCategory.TRANSIENT, retryable=false with rationale. Updated file inventory table. |
| m-10: @AllArgsConstructor conflict with new fields | SKIPPED | Architectural concern about Lombok constructor; applies across all specs. Best addressed during implementation, not spec-level. |

**Total:** 15 fixed, 5 skipped
