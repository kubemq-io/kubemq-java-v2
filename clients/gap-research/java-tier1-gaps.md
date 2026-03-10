# Java SDK -- Tier 1 Gap Research Report

**Assessment Score:** 3.10 / 5.0 (capped at 3.0 with gating)
**Target Score:** 4.0+
**Gap:** +0.90
**Assessment Date:** 2026-03-09
**Repository:** github.com/kubemq-io/kubemq-java-v2

---

## Executive Summary

### Gap Overview

| GS Category | Assessment Cat # | Current | Target | Gap | Status | Priority | Effort |
|-------------|-----------------|---------|--------|-----|--------|----------|--------|
| 01 Error Handling | 4 | 2.27 | 4.0 | +1.73 | MISSING | P0 | 2S+4M+2L+1XL |
| 02 Connection | 3 | 3.14 | 4.0 | +0.86 | MISSING | P0 | 1S+2M+3L+0XL |
| 03 Auth & Security | 5 | 2.56 | 4.0 | +1.44 | MISSING | P0 | 1S+2M+2L+0XL |
| 04 Testing | 9 | 3.25 | 4.0 | +0.75 | MISSING | P0 | 1S+1M+2L+0XL |
| 05 Observability | 7 | 1.86 | 4.0 | +2.14 | MISSING | P0 | 0S+1M+3L+1XL |
| 06 Documentation | 10 | 3.00 | 4.0 | +1.00 | MISSING | P1 | 3S+3M+1L+0XL |
| 07 Code Quality | 8 | 3.48 | 4.0 | +0.52 | PARTIAL | P1 | 2S+3M+1L+0XL |

### Unassessed Requirements (added post-assessment)

11 requirements have no assessment coverage. These were added to the Golden Standard after the SDK assessment was conducted and require fresh evaluation:
- REQ-ERR-7: Retry Throttling
- REQ-ERR-8: Streaming Error Handling (partial overlap with assessment 4.3/4.4 but specific acceptance criteria unassessed)
- REQ-ERR-9: Async Error Propagation (partial overlap with assessment but specific criteria unassessed)
- REQ-CONN-6: Connection Reuse (partial evidence exists but not formally assessed)
- REQ-AUTH-4: Credential Provider Interface
- REQ-AUTH-5: Security Best Practices (partial overlap with 5.2 but distinct criteria)
- REQ-AUTH-6: TLS Credentials During Reconnection
- REQ-OBS-4: Near-Zero Cost When Not Configured
- REQ-DOC-6: CHANGELOG
- REQ-DOC-7: Migration Guide
- REQ-CQ-6: Code Review Standards

### Critical Path (P0 items that must be fixed first)
1. REQ-ERR-1 (Typed Error Hierarchy) -- Foundation for all error handling improvements
2. REQ-ERR-2 (Error Classification) -- Required by retry logic and gRPC mapping
3. REQ-ERR-3 (Auto-Retry with Configurable Policy) -- Core resilience requirement
4. REQ-ERR-6 (gRPC Error Mapping) -- All 17 status codes must be mapped
5. REQ-OBS-1 (OpenTelemetry Trace Instrumentation) -- Enterprise observability requirement
6. REQ-OBS-3 (OpenTelemetry Metrics) -- Core metrics requirement
7. REQ-CONN-1 (Auto-Reconnection with Buffering) -- Missing buffering, DNS re-resolve, state interaction
8. REQ-CONN-2 (Connection State Machine) -- No public state query or callbacks
9. REQ-AUTH-4 (Credential Provider Interface) -- Unassessed, must implement
10. REQ-TEST-3 (CI Pipeline) -- No CI exists at all

### Quick Wins (high impact, low effort)
1. REQ-CONN-3: gRPC keepalive is already configurable -- needs default adjustment (10s vs current 60s) and documentation (S)
2. REQ-DOC-6: Add CHANGELOG.md (S)
3. REQ-CQ-2: Make internal classes package-private (S)
4. REQ-CQ-7: Add WARN log for InsecureSkipVerify/plaintext connections (S)
5. REQ-DOC-2: README structure improvement -- many sections exist, need reorganization (S)

### Features to Remove or Deprecate
- `gRPC enableRetry()`: Assessment 4.3.1 notes gRPC channel has `enableRetry()` set. Per REQ-ERR-3, gRPC-level retry MUST be disabled; all retry logic handled by SDK. This must be removed to prevent double-retry amplification.
- `grpc-alts` dependency: Assessment 11.1.4 notes this adds unnecessary weight. Not required for gRPC-only transport.
- Logback hard dependency: Assessment 7.1.3 notes direct cast to Logback's `LoggerContext`. Must be replaced with SDK-defined logger interface per REQ-OBS-5.

---

## Category 01: Error Handling & Resilience

**Current Score:** 2.27 (Assessment Cat 4) | **Target:** 4.0+ | **Gap:** +1.73 | **Priority:** P0

### REQ-ERR-1: Typed Error Hierarchy

**Status:** MISSING

**Current State:**
Assessment 4.1.1-4.1.5 documents only 4 flat exception classes: `GRPCException`, `CreateChannelException`, `DeleteChannelException`, `ListChannelsException` -- all extending `RuntimeException` directly. No base `KubeMQException`. No fields for Code, Operation, Channel, IsRetryable, RequestID. `GRPCException` wraps messages as strings, not gRPC codes. Error wrapping is inconsistent -- many places use `new RuntimeException(e.getMessage())` losing the cause chain.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| All methods return SDK-typed errors | MISSING | Most errors thrown as generic `RuntimeException` with string messages (4.1.1) |
| Error types support unwrapping (getCause()) | PARTIAL | Some exceptions use message+cause constructor, but `GRPCException` lacks it. Many places lose cause chain (4.1.5) |
| Error codes documented and stable | MISSING | No error code system exists (4.2.4) |
| Error codes follow SemVer | MISSING | No error codes exist to version |

**Remediation:**
- **What:** Create `io.kubemq.sdk.error` package with: `KubeMQException` base class extending `RuntimeException` with fields: `code` (enum `ErrorCode`), `message`, `operation`, `channel`, `isRetryable`, `cause`, `requestId`. Create subclasses: `ConnectionException`, `AuthenticationException`, `AuthorizationException`, `TimeoutException`, `ValidationException`, `ServerException`, `ThrottlingException`, `CancellationException`, `BackpressureException`, `StreamBrokenException`. Each must support `getCause()` for unwrapping. Define `ErrorCode` enum with stable, documented codes. Migrate all `throw new RuntimeException(...)` call sites to typed exceptions.
- **Complexity:** M (2-3 days -- ~20 throw sites to migrate, plus new class hierarchy)
- **Dependencies:** None -- this is the foundation
- **Language-specific:** Java idiom: extend `RuntimeException` (unchecked) for SDK errors. Use `@Getter` (Lombok) for error fields. Implement `toString()` with structured output. Consider implementing `Serializable` for remote exception scenarios.

### REQ-ERR-2: Error Classification

**Status:** MISSING

**Current State:**
Assessment 4.1.3: "No retryable/non-retryable classification on exceptions." Assessment 4.3.5: "All `StatusRuntimeException` triggers retry -- including `UNAUTHENTICATED`, `PERMISSION_DENIED`, `INVALID_ARGUMENT` which are non-retryable. No status code inspection."

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Every error has a classification | MISSING | No classification system exists (4.1.3) |
| IsRetryable flag is accurate | MISSING | No IsRetryable flag exists (4.1.3) |
| Classification is documented | MISSING | No error reference documentation |
| BufferFullError classified as Backpressure | MISSING | No buffer and no BufferFullError exists (3.2.7) |

**Remediation:**
- **What:** Add `ErrorCategory` enum (TRANSIENT, TIMEOUT, THROTTLING, AUTHENTICATION, AUTHORIZATION, VALIDATION, NOT_FOUND, FATAL, CANCELLATION, BACKPRESSURE). Add `category` field and `isRetryable()` method to `KubeMQException`. Create `ErrorClassifier` utility that maps gRPC status codes to categories. Create `BufferFullException` extending `KubeMQException` with category=BACKPRESSURE. Update subscription reconnect logic to check `isRetryable()` before retrying.
- **Complexity:** M (1-2 days -- depends on REQ-ERR-1 being done)
- **Dependencies:** REQ-ERR-1 (typed error hierarchy)
- **Language-specific:** Java: enum-based classification. `isRetryable()` as a method on the base exception class.

### REQ-ERR-3: Auto-Retry with Configurable Policy

**Status:** MISSING

**Current State:**
Assessment 4.3.1-4.3.5: Subscription retry exists with exponential backoff but no jitter (4.3.2). Queue send/receive has no retry (4.3.1). Max delay hardcoded at 60s, max attempts at 10 (4.3.3). No per-operation retry config. All `StatusRuntimeException` triggers retry without status code inspection (4.3.5). gRPC `enableRetry()` is called (4.3.1), violating the requirement that SDK handles all retry.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Transient/timeout errors retried automatically | PARTIAL | Subscriptions retry on StatusRuntimeException but don't distinguish transient vs non-retryable (4.3.5) |
| Retry policy configurable via builder | PARTIAL | Only `reconnectIntervalSeconds` configurable; max delay/attempts/jitter hardcoded (4.3.3) |
| Retries can be disabled (maxRetries=0) | MISSING | No way to disable retries |
| Each retry logged at DEBUG | PARTIAL | Reconnection logged but not at standardized DEBUG level with attempt info |
| After exhausting retries, last error returned with context | PARTIAL | Logs "Max reconnect attempts reached" but limited context (4.3.4) |
| Non-retryable errors returned immediately | MISSING | All StatusRuntimeException retried regardless (4.3.5) |
| Non-idempotent ops not retried on ambiguous failures | MISSING | No operation-type safety classification |
| gRPC-level retry disabled | MISSING | gRPC `enableRetry()` is called (4.3.1) -- MUST be removed |
| Retry policy immutable after creation | PARTIAL | Reconnect interval set at construction, but not formally immutable |
| Worst-case latency documented | MISSING | No documentation of retry latency |

**Remediation:**
- **What:** Create `RetryPolicy` class with fields: maxRetries (default 3), initialBackoff (500ms), maxBackoff (30s), multiplier (2.0), jitterType (FULL/EQUAL/NONE). Create `RetryExecutor` that implements the retry loop with full jitter algorithm. Add `RetryPolicy` to client builder. Remove `enableRetry()` from gRPC channel construction. Implement operation-type safety table (Events=safe, Queue Send=not safe on DEADLINE_EXCEEDED, etc.). Add retry attempt span events. Log each attempt at DEBUG. Document worst-case latency calculation.
- **Complexity:** L (3-5 days -- retry executor, integration with all operation paths, operation safety classification)
- **Dependencies:** REQ-ERR-2 (error classification needed to know what to retry)
- **Language-specific:** Java: `RetryPolicy` as immutable class with builder. Use `java.util.concurrent.ThreadLocalRandom` for jitter. `Duration` for time parameters.

### REQ-ERR-4: Per-Operation Timeouts

**Status:** PARTIAL

**Current State:**
Assessment 4.4.1: Queue operations use `CompletableFuture.get(timeout)` with configurable timeout. Blocking stub calls (ping, command, query) use message-level timeout but no per-call deadline. Subscriptions have no timeout on subscribe call. Assessment 4.4.2: No `Context` or `CancellationToken` support.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Java: every method accepts Duration timeout or uses CompletableFuture.orTimeout() | PARTIAL | Queue ops have timeout; commands/queries use message-level timeout; subscriptions/events have none (4.4.1) |
| Default timeouts applied when user doesn't specify | PARTIAL | Queue default 30s exists; no defaults for send/publish, subscribe initial connection (4.4.1) |
| Timeout errors classified as retryable (with caution) | MISSING | No timeout error classification (4.1.3) |

**Remediation:**
- **What:** Add optional `Duration timeout` parameter to all public operation methods: `sendEventsMessage`, `sendEventsStoreMessage`, `sendCommandRequest`, `sendQueryRequest`, `sendQueuesMessage`. Apply via gRPC deadline: `stub.withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS)`. Set defaults per GS table (Send/Publish=5s, Subscribe initial=10s, RPC=10s, Queue single=10s, Queue streaming=30s). Classify timeout errors as retryable with caution per REQ-ERR-2.
- **Complexity:** M (1-2 days -- each public method needs timeout overload)
- **Dependencies:** REQ-ERR-1 (for TimeoutException type)
- **Language-specific:** Java: use `java.time.Duration` parameter. Provide overloads with and without timeout. Apply via `blockingStub.withDeadlineAfter()`.

### REQ-ERR-5: Actionable Error Messages

**Status:** PARTIAL

**Current State:**
Assessment 4.2.1-4.2.4: Messages describe what happened but don't suggest fixes. Limited context -- queue errors include request ID but not channel name. No consistent error format. No error code system.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Error messages include operation name | PARTIAL | Some messages mention operation ("Failed to send...") but not consistently (4.2.4) |
| Error messages include target channel/queue | PARTIAL | Queue errors include request ID but not channel name (4.2.2) |
| Error messages include suggestion for resolution | MISSING | Messages don't suggest fixes (4.2.1) |
| Retry exhaustion includes attempt count and duration | PARTIAL | Includes attempt count but not total duration (4.3.4) |
| Error messages never expose internal details | COMPLIANT | No stack traces or raw gRPC frames in error messages (4.2.3) |

**Remediation:**
- **What:** Create `ErrorMessageBuilder` utility that formats messages with: operation, channel, cause, suggestion, retry context. Add suggestion map: `UNAUTHENTICATED` -> "Check your auth token configuration", `UNAVAILABLE` -> "Check server connectivity and firewall rules", etc. Update all throw sites to use the builder. Include total retry duration in exhaustion messages.
- **Complexity:** S (< 1 day -- templating utility + update throw sites, can be done alongside REQ-ERR-1 migration)
- **Dependencies:** REQ-ERR-1 (error fields must exist to populate)
- **Language-specific:** Java: `String.format()` or `StringBuilder` for message construction. Consider `MessageFormat` for i18n future.

### REQ-ERR-6: gRPC Error Mapping

**Status:** MISSING

**Current State:**
Assessment 4.1.4: "`StatusRuntimeException` caught but status code never extracted or mapped. `GRPCException` wraps messages as strings, not gRPC codes."

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| All 17 gRPC status codes mapped | MISSING | No status code extraction or mapping (4.1.4) |
| Original gRPC error preserved in chain | PARTIAL | Some wrapping preserves cause, some loses it (4.1.5) |
| Rich error details from google.rpc.Status extracted | MISSING | No rich error detail extraction |
| CANCELLED split between client/server initiated | MISSING | No distinction made |
| UNKNOWN retried at most once | MISSING | No special handling for UNKNOWN |
| Error events recorded as OTel span events | MISSING | No OTel integration (7.3) |

**Remediation:**
- **What:** Create `GrpcErrorMapper` class that maps `StatusRuntimeException.getStatus().getCode()` to SDK error types. Map all 17 codes per the GS table. Extract `Status.getDescription()` and `Status.getCause()` into error chain. Extract rich error details via `StatusProto.fromThrowable()` when present. Implement CANCELLED distinction: check if local context is cancelled (client-initiated) vs server-initiated. Implement UNKNOWN single-retry cap. Wrap all gRPC call sites with the mapper.
- **Complexity:** M (2-3 days -- mapper implementation + wrapping all gRPC call sites)
- **Dependencies:** REQ-ERR-1 (error types), REQ-ERR-2 (classification)
- **Language-specific:** Java: use `io.grpc.Status.Code` enum for mapping. Use `io.grpc.protobuf.StatusProto` for rich error details. Intercept via gRPC `ClientInterceptor` for centralized mapping.

### REQ-ERR-7: Retry Throttling

**Status:** NOT_ASSESSED

**Current State:**
No assessment coverage for this requirement. Added post-assessment. No concurrent retry limiting exists in the SDK.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Concurrent retry attempts limited per client | MISSING | No retry throttling mechanism |
| Limit configurable via builder | MISSING | No configuration exists |
| When limit reached, errors returned immediately with throttle indicator | MISSING | No throttle indicator |
| Retry attempts throttled to prevent retry storms | MISSING | No throttle mechanism |

**Remediation:**
- **What:** Add `java.util.concurrent.Semaphore` with configurable permits (default 10) to `RetryExecutor`. Before each retry attempt, `tryAcquire()`. If cannot acquire, return error immediately with `RetryThrottledException`. Add `maxConcurrentRetries` to `RetryPolicy` builder. Release permit in finally block.
- **Complexity:** S (< 1 day -- simple semaphore wrapper around retry executor)
- **Dependencies:** REQ-ERR-3 (retry policy must exist)
- **Language-specific:** Java: `java.util.concurrent.Semaphore` is the idiomatic choice. Thread-safe by design.

### REQ-ERR-8: Streaming Error Handling

**Status:** PARTIAL

**Current State:**
Assessment 4.3.1: Queue streams have no auto-reconnect -- `closeStreamWithError()` completes all pending. Assessment 3.2.6: Subscriptions auto-reconnect on `StatusRuntimeException`. Assessment 4.4.3: Queue batch individual messages can be ack'd/rejected independently. No distinction between stream-level and per-message errors.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Stream-level errors trigger stream reconnection with backoff | PARTIAL | Subscription streams reconnect; queue streams do not (4.3.1, 3.2.6) |
| Per-message errors do not terminate stream | PARTIAL | Queue poll responses handle per-message ack/reject independently (4.4.3) but stream errors terminate all pending |
| StreamBrokenError reports unacknowledged message IDs | MISSING | No StreamBrokenError type; pending futures completed with generic error |
| Stream state independent of connection state | MISSING | No formal stream vs connection state separation |

**Remediation:**
- **What:** Create `StreamBrokenException` extending `KubeMQException` with `List<String> unacknowledgedMessageIds` field. Implement stream reconnection for queue upstream/downstream handlers using the same backoff policy as operation retry. Separate stream error handling from connection error handling: stream errors reconnect the stream only, connection errors trigger connection reconnection. Track in-flight message IDs in queue handlers so they can be reported on stream break.
- **Complexity:** L (3-5 days -- queue stream reconnection is complex, in-flight tracking requires careful state management)
- **Dependencies:** REQ-ERR-1 (error types), REQ-ERR-3 (backoff policy), REQ-CONN-2 (state machine)
- **Language-specific:** Java: use `ConcurrentHashMap` for in-flight message tracking. `CompletableFuture` completion with `StreamBrokenException` for pending operations.

### REQ-ERR-9: Async Error Propagation

**Status:** PARTIAL

**Current State:**
Assessment notes subscription-level `onErrorCallback` exists (8.5.2). Assessment 4.2.3: "Visibility timer expiration: exception is caught and logged but not re-thrown (intentional). Reconnection failures logged and callback invoked." No distinction between transport errors and handler errors.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Subscription/consumer ops accept error callback | PARTIAL | `onErrorCallback` exists on subscriptions but not formalized for all async operations |
| Transport errors and handler errors distinguishable | MISSING | No type distinction between transport and handler errors |
| Handler errors do not terminate subscription | NOT_ASSESSED | Not explicitly tested in assessment |
| Unhandled async errors logged at ERROR level | PARTIAL | Some errors logged, but logging level not consistently ERROR |
| Async errors propagated to user-registered handlers | PARTIAL | `onErrorCallback` invoked for reconnection failures (4.3.4) |

**Remediation:**
- **What:** Create `TransportException` and `HandlerException` as distinct subtypes of `KubeMQException`. Ensure all subscription `StreamObserver.onError()` implementations wrap errors as `TransportException`. Wrap user callback execution in try-catch; catch `Exception` and wrap as `HandlerException`, then invoke error callback without terminating subscription. If no error callback registered, log at ERROR level via SDK logger. Formalize error callback on all async operations including queue downstream.
- **Complexity:** M (1-2 days)
- **Dependencies:** REQ-ERR-1 (error types)
- **Language-specific:** Java: wrap `Consumer<T>` callbacks in try-catch. Use `@FunctionalInterface` for error handler: `Consumer<KubeMQException>`.

---

## Category 02: Connection & Transport

**Current Score:** 3.14 (Assessment Cat 3) | **Target:** 4.0+ | **Gap:** +0.86 | **Priority:** P0

### REQ-CONN-1: Auto-Reconnection with Buffering

**Status:** PARTIAL

**Current State:**
Assessment 3.2.3: gRPC channel-level state listener on `TRANSIENT_FAILURE` calls `resetConnectBackoff()`. Subscription-level exponential backoff reconnection exists. Queue streams have no auto-reconnect. Assessment 3.2.7: "No message buffering. Queue upstream handler: if stream fails, all pending futures completed with error." Assessment 3.2.4: Backoff has no jitter, max 60s cap, max 10 attempts hardcoded. Assessment 3.2.6: Subscriptions auto-reconnect with cached parameters. EventsStore reconnects with same store type/sequence/time.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Connection drops detected within keepalive timeout | PARTIAL | Keepalive configured but defaults are 60s/30s, not 10s/5s per GS (3.1.6) |
| Reconnection starts automatically with backoff | PARTIAL | Subscription-level yes; queue streams no (3.2.3) |
| Messages buffered during reconnection | MISSING | No message buffering exists (3.2.7) |
| Subscriptions restored per recovery semantics | PARTIAL | Subscriptions reconnect; EventsStore resumes with same parameters; queue streams fail (3.2.6) |
| Buffer overflow configurable (error vs block) | MISSING | No buffer exists |
| Reconnection attempts logged at INFO | PARTIAL | Logged but level not verified as INFO |
| Successful reconnection logged at INFO | PARTIAL | Logged but level not verified |
| DNS re-resolved on each attempt | MISSING | No evidence of DNS re-resolution |
| Backoff reset after successful reconnection | NOT_ASSESSED | Not explicitly verified |
| Operation retries suspended during RECONNECTING | MISSING | No formal state machine interaction |
| Stream errors distinguished from connection errors | MISSING | No formal distinction (3.2.3) |
| Buffered messages sent FIFO after reconnection | MISSING | No buffer exists |
| Buffered messages discarded on CLOSED with callback | MISSING | No buffer or callback |

**Remediation:**
- **What:** Implement `ReconnectionManager` class managing reconnection lifecycle. Add `MessageBuffer` (bounded `ConcurrentLinkedQueue` with configurable max size in bytes, default 8MB). Implement buffer overflow policy: `BufferOverflowPolicy.ERROR` (return `BufferFullException`) or `BufferOverflowPolicy.BLOCK` (block until space). On reconnect, flush buffer in FIFO order. Add `OnBufferDrain` callback for discard notification on `close()`. Force DNS re-resolution by using `ManagedChannelBuilder.forTarget()` with `NameResolver.Factory`. Add queue stream auto-reconnect logic. Reset backoff counters after successful reconnection. Add jitter to reconnection backoff.
- **Complexity:** L (3-5 days -- buffering, DNS re-resolve, queue stream reconnection are all substantial)
- **Dependencies:** REQ-CONN-2 (state machine needed), REQ-ERR-1 (BufferFullException type)
- **Language-specific:** Java: `ConcurrentLinkedQueue` for buffer, `AtomicLong` for byte tracking, `ReentrantLock` with `Condition` for blocking mode. Use `io.grpc.NameResolver.Factory` for DNS re-resolution.

### REQ-CONN-2: Connection State Machine

**Status:** MISSING

**Current State:**
Assessment 3.2.5: "No public connection state callback/listener API. Internal `notifyWhenStateChanged()` only logs. Users cannot register `onConnect/onDisconnect/onReconnect` handlers." Assessment 3.4.3: "No `isConnected()` method. Channel state not exposed publicly."

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Current state queryable via method | MISSING | No isConnected() or getState() method (3.4.3) |
| State transitions fire callbacks | MISSING | No public state callbacks (3.2.5) |
| Handlers for OnConnected/OnDisconnected/OnReconnecting/OnReconnected/OnClosed | MISSING | No handler registration API (3.2.5) |
| Handlers invoked asynchronously | MISSING | No handlers exist |
| State included in log messages during transitions | PARTIAL | Some state changes logged internally |

**Remediation:**
- **What:** Create `ConnectionState` enum: IDLE, CONNECTING, READY, RECONNECTING, CLOSED. Add `getState()` method to client classes. Create `ConnectionStateListener` interface with methods: `onConnected()`, `onDisconnected()`, `onReconnecting(int attempt)`, `onReconnected()`, `onClosed()`. Add `addConnectionStateListener(ConnectionStateListener)` to client builder. Invoke listeners asynchronously via a dedicated single-thread executor. Include state in all connection-related log messages.
- **Complexity:** M (1-2 days)
- **Dependencies:** None
- **Language-specific:** Java: `@FunctionalInterface` not suitable here (multiple methods); use interface with default no-op methods. `ExecutorService` for async invocation. `volatile` for state field or `AtomicReference<ConnectionState>`.

### REQ-CONN-3: gRPC Keepalive Configuration

**Status:** PARTIAL

**Current State:**
Assessment 3.1.6: "Configurable via `keepAlive`, `pingIntervalInSeconds`, `pingTimeoutInSeconds`. Defaults: 60s interval, 30s timeout." GS requires defaults of 10s interval, 5s timeout, permit without stream = true.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Keepalive enabled by default | PARTIAL | Configurable but defaults differ from GS (60s vs 10s interval) |
| All three parameters configurable | COMPLIANT | All three are configurable (3.1.6) |
| Dead connections detected within keepalive_time + keepalive_timeout | PARTIAL | With current defaults: 90s detection vs GS target of 15s |
| Parameters compatible with KubeMQ server enforcement | COMPLIANT | Parameters work with server (3.1.6) |

**Remediation:**
- **What:** Change default keepalive time from 60s to 10s, keepalive timeout from 30s to 5s. Ensure `keepAliveWithoutCalls(true)` is set by default. Update documentation.
- **Complexity:** S (< 1 day -- default value changes)
- **Dependencies:** None
- **Language-specific:** None specific.

### REQ-CONN-4: Graceful Shutdown / Drain

**Status:** PARTIAL

**Current State:**
Assessment 3.2.2: "`close()` calls `managedChannel.shutdown().awaitTermination(5, SECONDS)`." Assessment 3.4.2: "No drain API. No documentation of SIGTERM integration." JVM shutdown hook registered for executor cleanup.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Close()/Shutdown() initiates graceful shutdown | COMPLIANT | `close()` exists and works (3.2.2) |
| Optional timeout parameter (default 5s) | PARTIAL | 5s hardcoded, not configurable |
| In-flight operations complete before close | PARTIAL | `awaitTermination` waits but no explicit in-flight tracking |
| Buffered messages flushed before close | MISSING | No buffer exists (3.2.7) |
| New operations after Close() return ErrClientClosed | MISSING | No post-close operation guard |
| Close() is idempotent | NOT_ASSESSED | Not explicitly tested |
| Close() during RECONNECTING cancels and discards | MISSING | No formal reconnecting state handling |

**Remediation:**
- **What:** Add configurable `shutdownTimeout` to builder (default 5s). Add `AtomicBoolean closed` flag; check in all public methods, throw `ClientClosedException` if true. Implement drain: stop accepting new operations, flush message buffer (when buffer exists), wait for in-flight `CompletableFuture`s to complete within timeout, then close channel. Ensure `close()` is idempotent via `compareAndSet`. Handle RECONNECTING state: cancel reconnection, discard buffer, fire `OnBufferDrain` callback.
- **Complexity:** M (1-2 days)
- **Dependencies:** REQ-CONN-1 (buffering), REQ-CONN-2 (state machine)
- **Language-specific:** Java: `AtomicBoolean` for closed flag. `CountDownLatch` or `CompletableFuture.allOf()` for waiting on in-flight operations.

### REQ-CONN-5: Connection Configuration

**Status:** PARTIAL

**Current State:**
Assessment 3.1.1: `ManagedChannel` created with configurable options. Assessment 3.2.8: "No explicit connection timeout configuration." Assessment 3.1.7: `maxReceiveSize` default 100MB. Assessment 2.2.2: only `address` and `clientId` required.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| All connection parameters configurable via builder | PARTIAL | Address, max receive size, keepalive configurable; connection timeout missing (3.2.8) |
| Defaults match GS table | PARTIAL | Address default not documented as localhost:50000; max receive 100MB matches; no connection timeout default; no WaitForReady |
| Connection timeout applies to initial connection only | MISSING | No connection timeout config (3.2.8) |
| Invalid config rejected at construction (fail-fast) | PARTIAL | TLS cert paths validated; no general config validation documented |
| WaitForReady applies to both states | MISSING | No WaitForReady configuration |

**Remediation:**
- **What:** Add `connectionTimeout` builder parameter (default 10s). Apply via `ManagedChannelBuilder.idleTimeout()` or wrap initial ping with timeout. Add `waitForReady` builder parameter (default true). Apply via gRPC `CallOptions.withWaitForReady()` on stubs. Add `maxSendMessageSize` parameter (default 100MB). Add fail-fast validation: reject empty address, negative timeouts, negative message sizes at build time.
- **Complexity:** M (1-2 days)
- **Dependencies:** None
- **Language-specific:** Java: validation in builder's `build()` method with `IllegalArgumentException`.

### REQ-CONN-6: Connection Reuse

**Status:** COMPLIANT

**Current State:**
Assessment 13.2.6: "Single `ManagedChannel` shared across all operations. Stream handlers reuse connections. No per-operation channel creation. Stubs created once and reused." This was not formally assessed as a separate requirement but evidence supports compliance.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Single Client uses one gRPC channel | COMPLIANT | Single ManagedChannel shared (13.2.6) |
| Multiple concurrent operations multiplex | COMPLIANT | Stream handlers reuse connections (13.2.6) |
| Documentation advises single Client shared across threads | MISSING | No thread-safety documentation (6.1.4) |
| Per-operation channel creation prohibited | COMPLIANT | No per-operation creation found (13.2.6) |

**Remediation:**
- **What:** Add Javadoc to client classes documenting thread safety and advising single-client-per-application pattern. Add usage example to README.
- **Complexity:** S (< 1 day)
- **Dependencies:** None
- **Language-specific:** Java: `@ThreadSafe` annotation (from JSR-305 or custom).

---

## Category 03: Auth & Security

**Current Score:** 2.56 (Assessment Cat 5) | **Target:** 4.0+ | **Gap:** +1.44 | **Priority:** P0

### REQ-AUTH-1: Token Authentication

**Status:** PARTIAL

**Current State:**
Assessment 5.1.1: `authToken` parameter passed via gRPC metadata "authorization" header. `MetadataInterceptor` merges into all calls. Example exists. Assessment 5.1.2: "No token refresh mechanism. Token set once in constructor. No setter method. Would require creating new client."

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Static token via client options/builder | COMPLIANT | `authToken` builder parameter exists (5.1.1) |
| Token sent as gRPC metadata on every request | COMPLIANT | `MetadataInterceptor` injects "authorization" header (5.1.1, 3.1.5) |
| Token updatable without recreating client | MISSING | Token immutable after construction (5.1.2) |
| Missing token produces clear AuthenticationError | MISSING | No AuthenticationError type; generic error returned |
| Token never logged (even DEBUG) | COMPLIANT | Auth token not logged (5.2.2) |

**Remediation:**
- **What:** Add `setAuthToken(String token)` method or accept `Supplier<String>` (token provider) in builder. Update `MetadataInterceptor` to read token from `AtomicReference` instead of final field. When server returns `UNAUTHENTICATED` and no token is set, throw `AuthenticationException` with message "Server requires authentication. Set auth token via builder.authToken() or provide a CredentialProvider."
- **Complexity:** S (< 1 day for mutable token; CredentialProvider is separate REQ-AUTH-4)
- **Dependencies:** REQ-ERR-1 (for AuthenticationException type)
- **Language-specific:** Java: `AtomicReference<String>` for thread-safe token updates. Or `Supplier<String>` functional interface for lazy token retrieval.

### REQ-AUTH-2: TLS Encryption

**Status:** PARTIAL

**Current State:**
Assessment 3.3.1-3.3.5: TLS via `NettyChannelBuilder` with `NegotiationType.TLS`. Custom CA via `SslContextBuilder.trustManager()`. No cipher suite configuration, no TLS version selection (3.3.4). No InsecureSkipVerify as separate option. No warning when using plaintext (3.3.5). No TLS handshake failure classification.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| TLS enabled with single option | COMPLIANT | `.tls(true)` works (3.3.1) |
| Custom CA certificates (file or PEM bytes) | PARTIAL | File path supported; PEM bytes not (3.3.2) |
| Server name override supported | MISSING | No server name override option |
| InsecureSkipVerify as separately named option | MISSING | No InsecureSkipVerify option (3.3.5) |
| SDK logs WARNING for disabled cert verification | MISSING | No warning logged (5.2.1) |
| TLS 1.2 minimum enforced | MISSING | No TLS version configuration (3.3.4) |
| System CA bundle used by default | NOT_ASSESSED | Not explicitly tested |
| TLS handshake failures classified | MISSING | No failure classification |

**Remediation:**
- **What:** Add `WithInsecureSkipVerify()` builder method that sets `InsecureTrustManagerFactory` on SSL context. Log WARNING "certificate verification is disabled" on every connection attempt when active. Add `serverNameOverride` builder parameter applied via `SslContextBuilder`. Enforce TLS 1.2 minimum via `SslContextBuilder.protocols("TLSv1.3", "TLSv1.2")`. Add PEM bytes overloads: `tlsCertPem(byte[])`, `tlsKeyPem(byte[])`, `caCertPem(byte[])` using `ByteArrayInputStream`. Classify TLS handshake failures: catch `SSLException` subtypes and map to AuthenticationError (cert validation), TransientError (network), or ConfigurationError (version/cipher mismatch). Use system CA bundle by default when TLS enabled without custom CA.
- **Complexity:** M (2-3 days -- multiple TLS configuration additions, failure classification)
- **Dependencies:** REQ-ERR-1 (for error classification types)
- **Language-specific:** Java: Netty `SslContextBuilder` API. `InsecureTrustManagerFactory.INSTANCE` for skip verify. `SslProvider.OPENSSL` preferred over JDK for better TLS 1.3 support.

### REQ-AUTH-3: Mutual TLS (mTLS)

**Status:** PARTIAL

**Current State:**
Assessment 3.3.3: mTLS supported via `tlsCertFile` + `tlsKeyFile` for client cert/key. Applied via `SslContextBuilder.keyManager()`. Validated that both provided together. Assessment 3.3.4: No certificate rotation support.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| mTLS configurable with 3 parameters | COMPLIANT | Client cert, client key, CA cert all supported (3.3.3) |
| File paths and PEM bytes accepted | PARTIAL | Only file paths supported; no PEM bytes API (3.3.2) |
| Invalid certificates produce clear error at connection time | PARTIAL | File existence validated; cert content errors may be generic |
| Certificate errors classified as AuthenticationError | MISSING | No error classification |
| mTLS documented with examples | COMPLIANT | Example at TLSConnectionExample.java |
| TLS credentials reloaded on reconnection | MISSING | No certificate rotation support (3.3.4) |
| Changed cert files used on reconnection | MISSING | No cert reload on reconnect |
| Documentation for cert loading from env vars | MISSING | No env var example |

**Remediation:**
- **What:** Add PEM bytes API (see REQ-AUTH-2). On reconnection, reload certificates from file paths (re-read files, don't cache `SslContext`). Create new `SslContext` on each reconnection attempt using current file contents. Add example showing cert loading from environment variables via PEM bytes API. Classify cert errors as `AuthenticationException`.
- **Complexity:** L (3-5 days -- cert reload on reconnection requires `SslContext` recreation per connection attempt, integration with reconnection manager)
- **Dependencies:** REQ-CONN-1 (reconnection manager), REQ-ERR-1 (error types)
- **Language-specific:** Java: Netty `SslContext` is immutable; must create new instance on each reconnection. Consider `SslContextBuilder` factory pattern.

### REQ-AUTH-4: Credential Provider Interface

**Status:** NOT_ASSESSED

**Current State:**
No assessment coverage for this requirement. Added post-assessment. Assessment 5.1.2 confirms: "No token refresh mechanism. Token set once in constructor."

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| CredentialProvider interface defined with GetToken() | MISSING | No interface exists |
| Static token as built-in provider | MISSING | Static token exists but not as a provider |
| Custom providers supported | MISSING | No extensibility point |
| Reactive refresh on UNAUTHENTICATED | MISSING | No refresh mechanism |
| Proactive refresh (RECOMMENDED) | MISSING | No expiry tracking |
| Provider calls serialized | MISSING | No provider exists |
| Provider invoked during CONNECTING and RECONNECTING | MISSING | No provider exists |
| Provider errors classified | MISSING | No provider exists |
| OIDC provider example documented | MISSING | No OIDC documentation (5.1.3) |

**Remediation:**
- **What:** Create `CredentialProvider` interface: `TokenResult getToken() throws CredentialException`. `TokenResult` with fields: `token` (String), `expiresAt` (Instant, nullable). Create `StaticTokenProvider` implementing the interface. Create `CredentialManager` that: caches token, serializes calls via `ReentrantLock`, invalidates on UNAUTHENTICATED, optionally schedules proactive refresh via `ScheduledExecutorService` when `expiresAt` is provided. Update `MetadataInterceptor` to get token from `CredentialManager`. Add to builder: `credentialProvider(CredentialProvider)`. Classify provider errors: credential errors -> AuthenticationException, infrastructure errors -> TransientError. Write OIDC example using the interface.
- **Complexity:** L (3-5 days -- interface design, caching, serialization, proactive refresh, integration with interceptor and reconnection)
- **Dependencies:** REQ-ERR-1 (error types), REQ-CONN-2 (state machine for CONNECTING/RECONNECTING awareness)
- **Language-specific:** Java: interface with `@FunctionalInterface` not possible (returns complex type). Use `ReentrantLock` for serialization. `ScheduledExecutorService` for proactive refresh. `AtomicReference<TokenResult>` for cache.

### REQ-AUTH-5: Security Best Practices

**Status:** PARTIAL

**Current State:**
Assessment 5.2.2: "Auth token not logged. TLS cert paths logged at debug (acceptable)." Assessment 5.2.1: "Default is plaintext. No warning when connecting without TLS." No OTel integration exists so no span attribute concern yet.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Credentials excluded from logs/errors/OTel/toString() | PARTIAL | Token not logged (5.2.2); no OTel yet; toString() not verified |
| Log only token_present: true/false | MISSING | No token presence logging |
| TLS cert files validated at construction (fail-fast) | COMPLIANT | File existence validated in constructor |
| Security configuration guide with examples | PARTIAL | Auth token example exists; no comprehensive security guide |
| InsecureSkipVerify emits warning on every connection | MISSING | No InsecureSkipVerify option or warning (5.2.1) |

**Remediation:**
- **What:** Audit all `toString()` methods on client/config classes to ensure no credential leakage. Add `token_present: true/false` to connection log messages. Create security configuration guide document. Implement InsecureSkipVerify warning (covered in REQ-AUTH-2).
- **Complexity:** S (< 1 day -- mostly documentation and toString() audit)
- **Dependencies:** REQ-AUTH-2 (InsecureSkipVerify implementation)
- **Language-specific:** Java: override `toString()` on builder/options classes with Lombok `@ToString(exclude = {"authToken"})`.

### REQ-AUTH-6: TLS Credentials During Reconnection

**Status:** NOT_ASSESSED

**Current State:**
No assessment coverage for this specific requirement. Assessment 3.3.4 notes: "No certificate rotation support." This confirms the feature is missing.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Certificates reloaded from source on reconnection | MISSING | No cert reload on reconnect (3.3.4) |
| Source error treated as transient, retried per backoff | MISSING | No reconnection cert handling |
| TLS handshake failure classified per REQ-AUTH-2 | MISSING | No failure classification |
| Certificate reload logged at DEBUG | MISSING | No reload exists |
| Certificate reload errors logged at ERROR | MISSING | No reload exists |

**Remediation:**
- **What:** In reconnection manager, before establishing new connection, reload TLS certificates from configured paths. Create new `SslContext` with fresh certificates. If file read fails (missing, permission), classify as transient and continue reconnection backoff. If TLS handshake fails after reload, classify per REQ-AUTH-2 rules. Log cert reload at DEBUG, reload errors at ERROR.
- **Complexity:** M (covered largely by REQ-AUTH-3 remediation -- 1-2 days additional for error handling and logging)
- **Dependencies:** REQ-AUTH-2 (TLS failure classification), REQ-AUTH-3 (mTLS cert reload), REQ-CONN-1 (reconnection manager)
- **Language-specific:** Java: file I/O with proper exception handling. `SslContextBuilder` recreation.

---

## Category 04: Testing

**Current Score:** 3.25 (Assessment Cat 9) | **Target:** 4.0+ | **Gap:** +0.75 | **Priority:** P0

### REQ-TEST-1: Unit Tests with Mocked Transport

**Status:** PARTIAL

**Current State:**
Assessment 9.1.1-9.1.6: 795 unit tests pass. 75.1% instruction coverage (9.1.2). Mockito used for gRPC stub mocking (9.1.4). Tests cover validation, encode/decode, error paths, TLS config, builders (9.1.3). No parameterized tests (9.1.5). No resource leak detection.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Coverage meets phased target (Phase 1: 40%) | COMPLIANT | 75.1% exceeds Phase 1 and Phase 2 targets (9.1.2) |
| All error classification paths tested | MISSING | No error classification exists to test (4.1.3) |
| All retry scenarios tested | PARTIAL | Production readiness test for reconnection recursion exists; no retry policy unit tests |
| Config validation tested | COMPLIANT | BuilderValidationTests exist (9.1.3) |
| Coverage threshold enforced in CI | MISSING | No CI exists (9.3.1) |
| Client close + leak check | MISSING | No resource leak detection (no goleak equivalent) |
| Operations on closed client return ErrClientClosed | MISSING | No post-close guard |
| Oversized messages produce validation error | PARTIAL | Body size validated at 100MB (5.2.4) but test coverage not confirmed |
| Empty/nil payloads handled correctly | NOT_ASSESSED | Not explicitly tested |
| Per-test timeout (30s unit, 60s integration) | NOT_ASSESSED | No timeout enforcement noted |

**Remediation:**
- **What:** Add unit tests for error classification once REQ-ERR-2 is implemented (test all 17 gRPC codes map correctly). Add retry policy unit tests (success on first try, success on retry, exhaustion, non-retryable bypass). Add resource leak check: assert thread count before/after client lifecycle, verify no unclosed streams. Add `@Timeout(30)` JUnit 5 annotation on all unit test classes. Add tests for operations on closed client. Add oversized message test. Add empty/null payload tests. Configure JaCoCo `<minimum>` threshold in pom.xml. Add `@ParameterizedTest` with `@EnumSource` for gRPC status code mapping tests.
- **Complexity:** L (3-5 days -- many new test categories, depends on error handling implementation)
- **Dependencies:** REQ-ERR-1, REQ-ERR-2, REQ-ERR-3 (tests depend on implementation existing)
- **Language-specific:** Java: JUnit 5 `@Timeout`, `@ParameterizedTest`, `@EnumSource`. Thread leak detection: capture `Thread.getAllStackTraces().size()` before/after. Consider `assertj` for fluent assertions.

### REQ-TEST-2: Integration Tests Against Real Server

**Status:** PARTIAL

**Current State:**
Assessment 9.2.1-9.2.5: 5 integration test files exist covering all 4 messaging patterns. Limited error scenario coverage (9.2.3). Tests use UUID-based channel names but no parallel config (9.2.5). No reconnection integration test assertions. No auth failure integration test.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Integration tests for all 4 patterns | COMPLIANT | Events, Events Store, Queues, Commands/Queries covered (9.2.2) |
| Clearly separated from unit tests | COMPLIANT | Maven Failsafe Plugin configured; separate IT files (9.2.1) |
| Skippable when no server available | PARTIAL | Failsafe plugin configured but skip mechanism not documented |
| Each test independent | PARTIAL | UUID channel names for isolation; some shared setup (9.2.4) |
| Tests clean up resources | PARTIAL | Some cleanup in tests (9.2.4) |
| Unsubscribe during in-flight completes without leaks | MISSING | No unsubscribe API exists |
| Unique channel names per test | COMPLIANT | UUID-based channel names (9.2.5) |

**Remediation:**
- **What:** Add integration tests for: auth failure (invalid token), timeout scenarios, reconnection after server restart (assert state transitions READY->RECONNECTING->READY), message buffering during reconnection, buffer overflow producing BufferFullError, subscription re-establishment. Add env var check to skip integration tests (`KUBEMQ_SERVER_ADDRESS`). Ensure all tests clean up subscriptions and channels in `@AfterEach`. Add `@Timeout(60)` on integration test classes.
- **Complexity:** L (3-5 days -- reconnection tests require server restart coordination, likely Testcontainers)
- **Dependencies:** REQ-CONN-1, REQ-CONN-2, REQ-ERR-1 (features must exist to test them)
- **Language-specific:** Java: Testcontainers for KubeMQ server lifecycle in integration tests. `@EnabledIfEnvironmentVariable` for conditional execution.

### REQ-TEST-3: CI Pipeline

**Status:** MISSING

**Current State:**
Assessment 9.3.1-9.3.5: "No `.github/workflows/` directory. No CI configuration of any kind." No linter, no multi-version testing, no security scanning.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| CI runs on every PR and push to main | MISSING | No CI pipeline (9.3.1) |
| Unit tests across 2-3 language versions | MISSING | Only Java 11 targeted (9.3.4) |
| Integration tests against real server in CI | MISSING | No CI (9.3.2) |
| Linter blocks merge on violations | MISSING | No linter configured (9.3.3) |
| Coverage reported to Codecov | MISSING | No CI (9.3.5) |
| Coverage threshold enforced per phase | MISSING | No CI |

**Remediation:**
- **What:** Create `.github/workflows/ci.yml` with jobs: lint (Error Prone + google-java-format check), unit-tests (matrix: Java 11, 17, 21 on ubuntu-latest), integration (KubeMQ Docker service container), coverage (JaCoCo report upload to Codecov). Add coverage threshold enforcement via JaCoCo `<minimum>` rule. Add Dependabot configuration for dependency updates.
- **Complexity:** M (1-3 days)
- **Dependencies:** REQ-CQ-3 (linter must be configured first)
- **Language-specific:** Java: GitHub Actions `setup-java` action. Maven Surefire for unit tests, Maven Failsafe for integration tests. `services:` block for KubeMQ container. JaCoCo Maven plugin for coverage.

### REQ-TEST-4: Test Organization

**Status:** PARTIAL

**Current State:**
Assessment 9.2.1: Integration tests at `src/test/java/**/*IT.java` via Maven Failsafe. Unit tests at `src/test/java/**/*Test.java`. Assessment 9.1.1: 47 unit test files.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Unit/integration tests in separate dirs or tagged | COMPLIANT | *Test.java vs *IT.java naming convention with Failsafe (9.2.1) |
| Default test command runs only unit tests | COMPLIANT | `mvn test` runs Surefire (unit only); `mvn verify` runs Failsafe (integration) |
| Integration tests require explicit flag/env var | PARTIAL | Failsafe runs on `mvn verify` but no explicit skip mechanism documented |
| Test helpers in testutil/fixtures package | PARTIAL | BaseIntegrationTest provides shared setup but no formal testutil package |

**Remediation:**
- **What:** Add `-DskipIntegrationTests` property to Failsafe configuration. Create `src/test/java/io/kubemq/sdk/testutil/` package with shared test utilities: `TestChannelNames` (UUID generator), `TestAssertions` (common assert patterns), `MockGrpcServer` (reusable in-process server setup).
- **Complexity:** S (< 1 day)
- **Dependencies:** None
- **Language-specific:** Java: Maven Failsafe `skipITs` property. Shared test utilities as package-private classes.

### REQ-TEST-5: Coverage Tools

**Status:** PARTIAL

**Current State:**
Assessment 9.1.2: "JaCoCo configured and generates reports." 75.1% coverage. No CI enforcement.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Coverage tool configured and runs in CI | PARTIAL | JaCoCo configured but no CI exists (9.1.2, 9.3.1) |
| Coverage report in standard format | COMPLIANT | JaCoCo generates standard reports |
| Coverage uploaded to Codecov | MISSING | No CI, no upload |
| Generated/vendored code excluded | COMPLIANT | Protobuf excluded per assessment (9.1.2) |

**Remediation:**
- **What:** Add JaCoCo `check` goal with `<minimum>0.40</minimum>` (Phase 1) in pom.xml. Configure Codecov upload in CI workflow. Ensure protobuf-generated code is excluded from JaCoCo via `<exclude>` patterns.
- **Complexity:** S (< 1 day -- JaCoCo config is mostly done)
- **Dependencies:** REQ-TEST-3 (CI pipeline for upload)
- **Language-specific:** Java: JaCoCo Maven plugin `check` goal. `<rule>` element with `BUNDLE` counter.

---

## Category 05: Observability

**Current Score:** 1.86 (Assessment Cat 7) | **Target:** 4.0+ | **Gap:** +2.14 | **Priority:** P0

### REQ-OBS-1: OpenTelemetry Trace Instrumentation

**Status:** MISSING

**Current State:**
Assessment 7.3.1-7.3.3: "No W3C Trace Context propagation. No span creation. No OpenTelemetry dependency or integration."

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Spans created for all messaging operations | MISSING | No span creation (7.3.2) |
| All required attributes set | MISSING | No OTel integration |
| Failed operations set span status to ERROR | MISSING | No spans exist |
| Batch operations set message_count attribute | MISSING | No spans exist |
| Span names follow {operation} {channel} format | MISSING | No spans exist |
| Retry attempts recorded as span events | MISSING | No spans or retry events |
| Batch consume operations follow receive/process pattern | MISSING | No spans exist |

**Remediation:**
- **What:** Add `opentelemetry-api` as `provided` scope dependency in pom.xml. Create `KubeMQTracing` class that creates spans for all operations per the GS span configuration table. Create `TextMapCarrier` adapter over KubeMQ message tags (`Map<String, String>`). Define all semconv attribute names as constants in `KubeMQSemconv` class. Create OTel interceptor/wrapper that instruments all gRPC calls. Add span creation around: publish/send, subscribe callback, queue receive, queue settle, command/query send, command/query response. Set all required attributes. Record retry attempts as span events. Handle batch operations with receive + per-message process spans.
- **Complexity:** XL (5+ days -- comprehensive instrumentation across all operation paths, span lifecycle management, batch patterns, retry events)
- **Dependencies:** REQ-CQ-1 (protocol layer separation for clean instrumentation), REQ-ERR-3 (retry events)
- **Language-specific:** Java: `io.opentelemetry:opentelemetry-api` as `provided` scope. Use `Tracer` from `GlobalOpenTelemetry.getTracer()`. `SpanBuilder` for span creation. `TextMapPropagator` for context injection/extraction.

### REQ-OBS-2: W3C Trace Context Propagation

**Status:** MISSING

**Current State:**
Assessment 7.3.1: "No W3C Trace Context propagation. Tags map could carry trace headers but no built-in support."

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| traceparent/tracestate injected into published messages | MISSING | No trace context injection (7.3.1) |
| Consumers extract context and create linked spans | MISSING | No extraction |
| Context survives round-trip | MISSING | No context propagation |
| Batch publishes inject per-message context | MISSING | No batch trace context |
| Missing context handled gracefully | MISSING | No context handling |
| Context preserved through requeue/DLQ | MISSING | No context preservation |

**Remediation:**
- **What:** Implement `KubeMQTagsCarrier` implementing OTel `TextMapGetter` and `TextMapSetter` over `Map<String, String>` tags. In publish operations, inject trace context via `GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject()`. In consume operations, extract via `.extract()` and create linked span. For queue stream downstream: one stream-level span, per-message process spans linked to producer. For RPC: sender injects into command/query, responder extracts and responds with context. Preserve trace context on requeue and DLQ operations.
- **Complexity:** L (3-5 days -- carrier implementation, injection/extraction at all operation points, RPC round-trip, DLQ preservation)
- **Dependencies:** REQ-OBS-1 (span creation must exist)
- **Language-specific:** Java: implement `TextMapGetter<Map<String, String>>` and `TextMapSetter<Map<String, String>>`. Use `Context.current()` for active context.

### REQ-OBS-3: OpenTelemetry Metrics

**Status:** MISSING

**Current State:**
Assessment 7.2.1-7.2.3: "No metrics infrastructure. No hooks, callbacks, or interfaces. No Prometheus or OpenTelemetry integration."

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| All required metrics emitted | MISSING | No metrics exist (7.2.1) |
| Correct instrument types | MISSING | No metrics |
| Metric names follow OTel conventions | MISSING | No metrics |
| Required attributes on metrics | MISSING | No metrics |
| Duration histograms use specified buckets | MISSING | No metrics |
| Cardinality management implemented | MISSING | No metrics |

**Remediation:**
- **What:** Add OTel metrics API dependency (`provided` scope). Create `KubeMQMetrics` class with: `messaging.client.operation.duration` (DoubleHistogram), `messaging.client.sent.messages` (LongCounter), `messaging.client.consumed.messages` (LongCounter), `messaging.client.connection.count` (LongUpDownCounter), `messaging.client.reconnections` (LongCounter), `kubemq.client.retry.attempts` (LongCounter), `kubemq.client.retry.exhausted` (LongCounter). Configure histogram with specified bucket boundaries. Add required attributes to all metrics. Implement cardinality management: `ConcurrentHashMap` tracking unique channel names, configurable threshold (default 100), allowlist, WARN log on threshold exceeded.
- **Complexity:** L (3-5 days -- 7 metrics with attributes, cardinality management, integration with all operations)
- **Dependencies:** REQ-OBS-1 (shared OTel setup), REQ-CONN-2 (connection state for connection metrics)
- **Language-specific:** Java: `io.opentelemetry:opentelemetry-api` Meter API. `DoubleHistogramBuilder` with explicit bucket boundaries. `Attributes` builder for metric attributes.

### REQ-OBS-4: Near-Zero Cost When Not Configured

**Status:** NOT_ASSESSED

**Current State:**
No OTel integration exists, so this is moot until REQ-OBS-1/2/3 are implemented. However, the architectural pattern must be established.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| OTel API is only observability dependency | MISSING | No OTel dependency at all |
| No-op provider when OTel SDK not registered | MISSING | No OTel integration |
| TracerProvider/MeterProvider injectable via options | MISSING | No injection point |
| Guard expensive computation with span.IsRecording() | MISSING | No spans |
| OTel documented with setup example | MISSING | No OTel documentation |
| Less than 1% latency overhead with no-op | MISSING | No OTel integration to measure |

**Remediation:**
- **What:** Add `opentelemetry-api` as `provided`/`compileOnly` scope (not runtime). Accept optional `TracerProvider` and `MeterProvider` via builder. Fall back to `GlobalOpenTelemetry.getTracerProvider()` / `GlobalOpenTelemetry.getMeterProvider()`. Guard expensive attribute computation (message body size, tag serialization) with `span.isRecording()` check. Document minimum supported OTel API version in README. Create example showing OTel setup with OTLP exporter.
- **Complexity:** M (covered as part of REQ-OBS-1/2/3 implementation -- architecture decision, not separate work)
- **Dependencies:** REQ-OBS-1 (part of OTel implementation)
- **Language-specific:** Java: `provided` scope in Maven means OTel API is compile-time only, not bundled. Users bring their own OTel SDK at runtime.

### REQ-OBS-5: Structured Logging Hooks

**Status:** PARTIAL

**Current State:**
Assessment 7.1.1-7.1.6: Uses SLF4J with Logback. Parameterized logging but not truly structured (7.1.1). Configurable log level (7.1.2). Directly casts to Logback's `LoggerContext` (7.1.3). No MDC context (7.1.6). Sensitive data excluded from logs (7.1.5).

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Logger interface defined with structured fields | MISSING | No SDK-defined logger interface; direct SLF4J/Logback coupling (7.1.3) |
| Default logger is no-op | MISSING | Logback is required runtime dependency |
| User can inject preferred logger | MISSING | Logback hardcoded via LoggerContext cast (7.1.3) |
| Log entries include trace_id/span_id when OTel active | MISSING | No OTel integration, no MDC (7.1.6) |
| Sensitive data never logged | COMPLIANT | Token not logged (7.1.5) |
| Log levels appropriate | PARTIAL | Levels exist but per-message logging level not verified |
| Per-message logging at DEBUG/TRACE only | NOT_ASSESSED | Not explicitly verified |

**Remediation:**
- **What:** Define `KubeMQLogger` interface with methods: `debug(String msg, Object... keysAndValues)`, `info(...)`, `warn(...)`, `error(...)`. Create `NoOpLogger` as default implementation. Create `Slf4jLoggerAdapter` that wraps SLF4J logger (without Logback dependency). Remove Logback runtime dependency from pom.xml (make it `test` scope only). Add `logger(KubeMQLogger)` to builder. When OTel context is active, include `trace_id` and `span_id` in log entries via `Span.current().getSpanContext()`. Remove `LoggerContext` cast.
- **Complexity:** M (1-3 days -- interface definition, adapter, migration of all log calls, removal of Logback dependency)
- **Dependencies:** None (but benefits from REQ-OBS-1 for trace correlation)
- **Language-specific:** Java: SLF4J as the facade (optional, not required). Logback becomes test-only. Key-value logging via varargs `Object...`. Consider compatibility with Log4j2, java.util.logging via adapter pattern.

---

## Category 06: Documentation

**Current Score:** 3.00 (Assessment Cat 10) | **Target:** 4.0+ | **Gap:** +1.00 | **Priority:** P1

### REQ-DOC-1: Auto-Generated API Reference

**Status:** MISSING

**Current State:**
Assessment 10.1.1-10.1.5: "No Javadoc comments in source code. Generated Javadocs would show Lombok method signatures without descriptions." Zero Javadoc comments across 50 source files. No published Javadoc site.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| 100% of public types/methods have doc comments | MISSING | Zero Javadoc comments (10.1.4) |
| Doc comment linter in CI | MISSING | No linter, no CI (10.1.2, 9.3.3) |
| API reference published and accessible | MISSING | No published Javadoc (10.1.5) |
| API reference regenerated on every release | MISSING | No release automation |

**Remediation:**
- **What:** Add Javadoc comments to all public classes, methods, constructors, and constants. For Lombok-generated methods, add `@param`/`@return` via `@Builder` class-level Javadoc or use `delombok` for Javadoc generation. Include: one-sentence summary (not restating method name), `@param` for each parameter with type/description/default/range, `@return` description, `@throws` for each possible exception. Configure Checkstyle Javadoc rules. Publish to javadoc.io via Maven Central (automatic for Central artifacts).
- **Complexity:** L (3-5 days -- 50+ source files, all public methods)
- **Dependencies:** None
- **Language-specific:** Java: Lombok `@Builder` methods need special handling -- either class-level Javadoc or `lombok.config` with `lombok.addJavadocTag = true`. Maven Javadoc Plugin for generation. Checkstyle `JavadocMethod`, `JavadocType` rules.

### REQ-DOC-2: README

**Status:** PARTIAL

**Current State:**
Assessment 10.4.1-10.4.5: README is 1900 lines. Has installation, quick start examples, per-pattern documentation. Missing: badges, messaging pattern comparison table, error handling section, troubleshooting, contributing link, changelog.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| All 10 sections present | PARTIAL | Missing: badges, error handling section, troubleshooting, contributing link |
| Installation instructions work | COMPLIANT | Maven dependency XML provided (10.4.1) |
| Code examples compile/run | COMPLIANT | Examples reference correct SDK classes (10.4.2) |
| Links use absolute URLs | NOT_ASSESSED | Not explicitly verified |

**Remediation:**
- **What:** Restructure README to include all 10 required sections: (1) add CI/coverage badges, (2) add description, (3) installation exists, (4) quick start exists, (5) add messaging pattern comparison table, (6) add configuration options table, (7) add error handling section, (8) add troubleshooting top 5, (9) add link to CONTRIBUTING.md, (10) license exists. Convert all links to absolute URLs.
- **Complexity:** M (1-2 days)
- **Dependencies:** REQ-DOC-5 (troubleshooting content), REQ-DOC-6 (CHANGELOG)
- **Language-specific:** None specific.

### REQ-DOC-3: Quick Start (First Message in 5 Minutes)

**Status:** PARTIAL

**Current State:**
Assessment 10.4.2: "Copy-paste ready code examples for each pattern in README." Assessment 2.2.1: Basic publish in ~4 lines. Quick start works with default localhost:50000.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Works with zero config against localhost:50000 | COMPLIANT | Default address works (2.2.2) |
| Copy-paste ready, no placeholders | COMPLIANT | Examples are complete (10.4.2) |
| Each pattern has own quick start | PARTIAL | Patterns documented but not in explicit "quick start" format |
| Total time from git clone to first message < 5 min | COMPLIANT | Maven build + simple example achievable in < 5 min |

**Remediation:**
- **What:** Restructure pattern examples into explicit "Quick Start" format with: prerequisites (3-4 bullets), send code (<=10 lines), receive code (<=10 lines), expected output. Ensure Events, Queues, and RPC each have dedicated quick start.
- **Complexity:** S (< 1 day -- restructuring existing content)
- **Dependencies:** None
- **Language-specific:** None specific.

### REQ-DOC-4: Code Examples / Cookbook

**Status:** PARTIAL

**Current State:**
Assessment 10.3.1-10.3.6: 46 example files exist. All patterns covered. Examples compile. Real-world scenarios included. TLS, auth, delayed messages, DLQ examples exist. Cookbook repo is a stub.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Every example self-contained and runnable | COMPLIANT | Examples are runnable (10.3.3) |
| Inline comments explaining each step | PARTIAL | Some examples have comments; not systematically verified |
| Examples directory has own README | NOT_ASSESSED | Not explicitly verified |
| Examples tested in CI (compile check) | MISSING | No CI exists (9.3.1) |
| Examples compile in main CI, block merge | MISSING | No CI |

**Remediation:**
- **What:** Add README to examples directory listing all examples with descriptions. Ensure all examples have inline comments. Add examples for: Observability (OTel setup with OTLP export), queue stream upstream/downstream. Add examples module to CI build so compilation failures block merge. Fill or archive the stub cookbook repo.
- **Complexity:** M (1-2 days)
- **Dependencies:** REQ-TEST-3 (CI for compile check), REQ-OBS-1 (OTel for observability example)
- **Language-specific:** Java: examples as separate Maven module with `<dependency>` on SDK.

### REQ-DOC-5: Troubleshooting Guide

**Status:** MISSING

**Current State:**
Assessment 10.2.6: "No troubleshooting guide. No common error documentation."

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Minimum 11 entries | MISSING | No troubleshooting guide exists |
| Each entry includes exact error message | MISSING | No error documentation |
| Solutions are actionable | MISSING | No solutions documented |
| Entries link to relevant sections | MISSING | No cross-references |

**Remediation:**
- **What:** Create `TROUBLESHOOTING.md` with entries for all 11 required issues: connection refused/timeout, auth failed, authorization denied, channel not found, message too large, deadline exceeded, rate limiting, internal server error, TLS handshake failure, no messages received, queue message not acknowledged. Each entry with: symptom, exact error message, cause, step-by-step solution, code example if applicable.
- **Complexity:** M (1-2 days)
- **Dependencies:** REQ-ERR-1 (error messages must be defined to document them)
- **Language-specific:** Java-specific error messages and stack traces in examples.

### REQ-DOC-6: CHANGELOG

**Status:** MISSING

**Current State:**
Assessment 10.4.5: "No CHANGELOG.md. Changes tracked only in git commit messages."

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| CHANGELOG.md exists | MISSING | No CHANGELOG (10.4.5) |
| Grouped by version and date | MISSING | No CHANGELOG |
| Categories: Added/Changed/Deprecated/Removed/Fixed/Security | MISSING | No CHANGELOG |
| Breaking changes marked | MISSING | No CHANGELOG |
| Entries link to PR/commit | MISSING | No CHANGELOG |

**Remediation:**
- **What:** Create `CHANGELOG.md` following Keep a Changelog format. Populate entries for all releases: 2.0.3, 2.1.0, 2.1.1. Categorize changes as Added/Changed/Fixed. Link to git commits/tags.
- **Complexity:** S (< 1 day)
- **Dependencies:** None
- **Language-specific:** None.

### REQ-DOC-7: Migration Guide

**Status:** MISSING

**Current State:**
Assessment 10.2.4: "No migration guide from v1 to v2."

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Migration guide for every major version upgrade | MISSING | No v1->v2 migration guide (10.2.4) |
| Every breaking change has before/after example | MISSING | No guide |
| Linked from CHANGELOG and README | MISSING | No guide or CHANGELOG |

**Remediation:**
- **What:** Create `MIGRATION.md` documenting v1 -> v2 changes: breaking changes table (what changed, old behavior, new behavior), before/after code snippets for renamed/removed methods, step-by-step upgrade procedure. Link from README and CHANGELOG.
- **Complexity:** M (1-2 days -- requires understanding v1 API to document differences)
- **Dependencies:** REQ-DOC-6 (CHANGELOG to link from)
- **Language-specific:** Java: Maven dependency coordinate changes, import changes, API differences.

---

## Category 07: Code Quality & Architecture

**Current Score:** 3.48 (Assessment Cat 8) | **Target:** 4.0+ | **Gap:** +0.52 | **Priority:** P1

### REQ-CQ-1: Layered Architecture

**Status:** PARTIAL

**Current State:**
Assessment 8.1.2: "Slight blending: message classes contain both domain and proto conversion." Assessment 8.1.4: "`RequestSender` is the only interface. No interfaces for clients, handlers, or transport. KubeMQClient is abstract class, not interface." Assessment 8.5.3: "gRPC tightly coupled. `KubeMQClient` directly creates `ManagedChannel`."

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Public API types don't reference gRPC/protobuf | PARTIAL | Message encode/decode methods reference protobuf types internally (8.1.2) but public fields are Java native |
| Protocol layer handles error wrapping, retry, auth, OTel | MISSING | No protocol layer; error wrapping, retry at various levels (8.1.2) |
| Transport layer is only gRPC importer | MISSING | gRPC referenced in client, handlers, subscriptions directly (8.5.3) |
| Layers communicate via interfaces | MISSING | Only one interface exists (8.1.4) |
| Users can import SDK without gRPC-internal types | PARTIAL | Public API uses Java types but gRPC types on classpath |
| Dependencies flow downward only | PARTIAL | Mostly downward but no formal layer separation (8.1.5) |

**Remediation:**
- **What:** Refactor into 3-layer architecture: (1) Public API: client classes, message types, options -- no gRPC imports. (2) Protocol: create `ProtocolInterceptor` chain for error mapping (REQ-ERR-6), retry (REQ-ERR-3), auth injection, OTel instrumentation. (3) Transport: extract `GrpcTransport` class owning `ManagedChannel`, stubs, keepalive, reconnection. Define `Transport` interface between Protocol and Transport layers. Move protobuf encode/decode into transport layer adapters. This is the largest architectural change and enables all other improvements.
- **Complexity:** L (3-5 days -- significant refactoring of client/handler structure)
- **Dependencies:** None -- this is foundational
- **Language-specific:** Java: use interfaces + package-private implementations. Protocol interceptors as a chain pattern. Transport interface with methods like `sendEvent()`, `subscribe()`, etc.

### REQ-CQ-2: Internal vs Public API Separation

**Status:** PARTIAL

**Current State:**
Assessment 8.1.7: "`QueueDownStreamProcessor`, handler classes, and internal helper classes are public. No `internal` package. Proto types leak via generated code."

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Internal details not importable by users | PARTIAL | Handler classes and helpers are public (8.1.7) |
| Only intentional public API exported | PARTIAL | Some internal classes are public unnecessarily |
| Moving internal code doesn't break users | PARTIAL | No formal public API contract |

**Remediation:**
- **What:** Make internal classes package-private: `QueueUpstreamHandler`, `QueueDownstreamHandler`, `EventStreamHelper`, `QueueDownStreamProcessor`, `MetadataInterceptor`, `KubeMQUtils` (where not needed externally). Remove `public` modifier from classes not intended for user use. Consider creating an `internal` sub-package (Java convention: package-private, not enforced like Go's `internal/`). Document which classes constitute the public API surface.
- **Complexity:** S (< 1 day -- access modifier changes)
- **Dependencies:** None
- **Language-specific:** Java: remove `public` modifier. Note: this is a breaking change if users import these classes. Check before removing.

### REQ-CQ-3: Linting and Formatting

**Status:** MISSING

**Current State:**
Assessment 8.2.1: "No linter configured in build. No spotbugs, checkstyle, or PMD in pom.xml." Assessment 9.3.3: "No linter configured in build or CI."

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Linter config file in repo root | MISSING | No linter config (8.2.1) |
| CI runs linter, blocks merge | MISSING | No CI or linter (9.3.3) |
| Zero linter warnings | NOT_ASSESSED | No linter to produce warnings |
| Formatting enforced | MISSING | No formatter plugin (8.2.3) |
| Type checking at strictest level | PARTIAL | javac compiles; no Error Prone or NullAway |
| Protobuf-generated code excluded | NOT_ASSESSED | No linter to exclude from |

**Remediation:**
- **What:** Add Error Prone compiler plugin to pom.xml. Add google-java-format via Spotless Maven plugin. Configure Error Prone with recommended checks. Add Checkstyle for Javadoc rules. Run `mvn spotless:apply` to format codebase. Add exclusion patterns for protobuf-generated code. Add to CI pipeline as lint job.
- **Complexity:** M (1-3 days -- initial formatting pass may require fixing many violations)
- **Dependencies:** REQ-TEST-3 (CI for enforcement)
- **Language-specific:** Java: Error Prone via `maven-compiler-plugin` annotation processor. Spotless for google-java-format. Checkstyle via `maven-checkstyle-plugin`.

### REQ-CQ-4: Minimal Dependencies

**Status:** PARTIAL

**Current State:**
Assessment 11.1.4: Dependencies include gRPC (4 modules including grpc-alts), protobuf, commons-lang3, logback-classic, jackson-databind, lombok.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Total direct deps <= 5 (excl gRPC/protobuf/OTel) | PARTIAL | commons-lang3, logback-classic, jackson-databind, lombok = 4 (within limit, but logback should be removed per REQ-OBS-5) |
| No logging framework dependency | MISSING | logback-classic is runtime dependency (7.1.3) |
| No HTTP client dependency | COMPLIANT | gRPC only |
| No utility library dependencies | PARTIAL | commons-lang3 is a utility library |
| Dependencies pinned | COMPLIANT | Versions specified in pom.xml |
| Dependency tree reviewed for vulnerabilities | MISSING | No vulnerability scanning (9.3.5) |
| CI runs vulnerability scanning | MISSING | No CI (9.3.5) |

**Remediation:**
- **What:** Remove `logback-classic` from compile/runtime scope (move to test only). Replace `commons-lang3` usage with inline helpers (assess what's used -- likely `StringUtils` which is trivial to inline). Remove `grpc-alts` if not needed. Add OWASP dependency-check-maven-plugin or Snyk to CI. Evaluate `jackson-databind` -- if only used for channel decoding, consider replacing with manual JSON parsing or lightweight alternative.
- **Complexity:** M (1-2 days -- dependency audit, inline replacements, OWASP plugin setup)
- **Dependencies:** REQ-OBS-5 (logger interface replaces Logback), REQ-TEST-3 (CI for vulnerability scanning)
- **Language-specific:** Java: `provided` scope for Lombok (compile-only, already correct). OWASP `dependency-check-maven-plugin` for vulnerability scanning.

### REQ-CQ-5: Consistent Code Organization

**Status:** PARTIAL

**Current State:**
Assessment 8.1.1: "Clean package structure: `client`, `common`, `cq`, `exception`, `pubsub`, `queues`." Assessment 8.2.7: "Reconnection logic duplicated across 4 subscription classes."

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| Directory structure follows conventions | PARTIAL | Structure exists but doesn't match GS recommended layout (missing `error/`, `auth/`, `transport/` packages) |
| Each messaging pattern has own file/module | COMPLIANT | Separate packages for pubsub, cq, queues (8.1.1) |
| Shared types in common location | PARTIAL | `common` and `exception` packages exist but `cq` name is unclear (8.2.4) |
| No circular dependencies | COMPLIANT | Clean dependency graph (8.1.5) |
| File names consistent | COMPLIANT | Consistent naming (8.1.6) |

**Remediation:**
- **What:** Rename `cq` package to `commands` and `queries` (or keep as `cq` but document it clearly). Create `error/` package for error types (from REQ-ERR-1). Create `auth/` package for credential provider (from REQ-AUTH-4). Create `transport/` package for gRPC connection management (from REQ-CQ-1). Extract duplicated reconnection logic to a shared `ReconnectableSubscription` base class. Align with GS recommended Java directory structure.
- **Complexity:** M (1-2 days -- package restructuring is a refactoring exercise)
- **Dependencies:** REQ-CQ-1 (layered architecture defines target structure)
- **Language-specific:** Java: package renaming is a breaking change for import statements. Consider doing in a major version bump.

### REQ-CQ-6: Code Review Standards

**Status:** NOT_ASSESSED

**Current State:**
No assessment coverage for this requirement. Assessment notes single maintainer (12.2.6). No formal review process documented.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| All PRs require review before merge | MISSING | No branch protection rules documented |
| PRs include tests for new functionality | NOT_ASSESSED | No PR process documented |
| Breaking changes labeled | NOT_ASSESSED | No PR process |
| No TODO/FIXME in released code | COMPLIANT | Zero TODO/FIXME found (8.4.1) |
| Dead code removed | PARTIAL | QueueDownStreamProcessor appears unused (8.4.2) |

**Remediation:**
- **What:** Enable GitHub branch protection on `main` requiring 1 review. Create PR template with checklist: tests added, breaking changes noted, docs updated. Remove or deprecate `QueueDownStreamProcessor` if truly unused. Create CONTRIBUTING.md with code review standards.
- **Complexity:** S (< 1 day)
- **Dependencies:** None
- **Language-specific:** None.

### REQ-CQ-7: Secure Defaults

**Status:** PARTIAL

**Current State:**
Assessment 5.2.2: "Auth token not logged." Assessment 5.2.1: "Default is plaintext. No warning when connecting without TLS." No OTel span attribute concern yet.

**Gap Analysis:**

| Acceptance Criterion | Status | Detail |
|---------------------|--------|--------|
| No credentials in logs/errors/OTel/toString() | PARTIAL | Token not logged (5.2.2); toString() not verified |
| TLS verification enabled by default | COMPLIANT | TLS with verification is the default when TLS enabled |
| Disabling TLS verification produces WARN log | MISSING | No InsecureSkipVerify or warning (5.2.1) |

**Remediation:**
- **What:** Add WARN log message when `InsecureSkipVerify` is set (covered in REQ-AUTH-2). Audit `toString()` on all config/options classes to exclude credentials. Ensure future OTel span attributes never include token values.
- **Complexity:** S (< 1 day -- mostly covered by REQ-AUTH-2 work)
- **Dependencies:** REQ-AUTH-2 (InsecureSkipVerify implementation)
- **Language-specific:** Java: Lombok `@ToString(exclude = {"authToken"})`.

---

## Dependency Graph

```
REQ-CQ-1 (architecture) ──> REQ-ERR-1 (typed errors) ──> REQ-ERR-2 (classification) ──> REQ-ERR-3 (retry)
REQ-ERR-1 (typed errors) ──> REQ-ERR-5 (actionable messages)
REQ-ERR-1 (typed errors) ──> REQ-ERR-6 (gRPC mapping)
REQ-ERR-2 (classification) ──> REQ-ERR-6 (gRPC mapping)
REQ-ERR-3 (retry policy) ──> REQ-ERR-7 (retry throttling)
REQ-ERR-3 (retry policy) ──> REQ-ERR-8 (streaming errors)
REQ-ERR-1 (typed errors) ──> REQ-ERR-9 (async errors)
REQ-CONN-2 (state machine) ──> REQ-CONN-1 (reconnection with buffering)
REQ-ERR-1 (typed errors) ──> REQ-CONN-1 (BufferFullException)
REQ-CONN-1 (reconnection) ──> REQ-CONN-4 (graceful shutdown drain)
REQ-ERR-1 (typed errors) ──> REQ-AUTH-1 (AuthenticationException)
REQ-CONN-2 (state machine) ──> REQ-AUTH-4 (credential provider state awareness)
REQ-ERR-1 (typed errors) ──> REQ-AUTH-4 (error classification)
REQ-AUTH-2 (TLS) ──> REQ-AUTH-6 (cert reconnection)
REQ-AUTH-3 (mTLS) ──> REQ-AUTH-6 (cert reconnection)
REQ-CONN-1 (reconnection) ──> REQ-AUTH-6 (reconnection manager)
REQ-CQ-1 (architecture) ──> REQ-OBS-1 (protocol layer instrumentation)
REQ-OBS-1 (traces) ──> REQ-OBS-2 (context propagation)
REQ-OBS-1 (traces) ──> REQ-OBS-3 (metrics -- shared OTel setup)
REQ-OBS-1 (traces) ──> REQ-OBS-4 (near-zero cost architecture)
REQ-TEST-3 (CI) ──> REQ-CQ-3 (linter in CI)
REQ-TEST-3 (CI) ──> REQ-TEST-5 (coverage in CI)
REQ-ERR-1 (typed errors) ──> REQ-TEST-1 (error classification tests)
REQ-OBS-5 (logger) ──> REQ-CQ-4 (remove Logback dependency)
REQ-DOC-6 (CHANGELOG) ──> REQ-DOC-7 (migration guide links)
REQ-ERR-1 (typed errors) ──> REQ-DOC-5 (troubleshooting error messages)
```

---

## Implementation Sequence

Recommended order of implementation based on dependencies and priority:

### Phase 1: Foundation (must do first)
1. REQ-CQ-1: Layered architecture refactoring (L) -- enables clean error wrapping and OTel instrumentation
2. REQ-ERR-1: Typed error hierarchy (M) -- foundation for all error handling
3. REQ-ERR-2: Error classification (M) -- required by retry and gRPC mapping
4. REQ-CONN-2: Connection state machine (M) -- required by reconnection and credential provider
5. REQ-TEST-3: CI pipeline (M) -- enables all quality enforcement
6. REQ-CQ-3: Linting and formatting (M) -- should be in place before heavy development
7. REQ-CQ-2: Internal API separation (S) -- quick access modifier cleanup

### Phase 2: Core Features
1. REQ-ERR-3: Auto-retry with configurable policy (L) -- core resilience
2. REQ-ERR-6: gRPC error mapping (M) -- all 17 status codes
3. REQ-ERR-4: Per-operation timeouts (M) -- production requirement
4. REQ-CONN-1: Auto-reconnection with buffering (L) -- message buffering, DNS re-resolve
5. REQ-CONN-3: Keepalive defaults (S) -- quick win
6. REQ-CONN-4: Graceful shutdown/drain (M) -- production requirement
7. REQ-CONN-5: Connection configuration (M) -- timeout, WaitForReady
8. REQ-AUTH-1: Token auth improvements (S) -- mutable token
9. REQ-AUTH-2: TLS improvements (M) -- InsecureSkipVerify, PEM bytes, TLS version
10. REQ-AUTH-3: mTLS improvements (L) -- PEM bytes, cert reload
11. REQ-AUTH-4: Credential provider interface (L) -- pluggable auth
12. REQ-OBS-5: Structured logging hooks (M) -- remove Logback dependency
13. REQ-CQ-4: Minimal dependencies (M) -- remove Logback, commons-lang3

### Phase 3: Observability & Documentation
1. REQ-OBS-1: OpenTelemetry trace instrumentation (XL) -- comprehensive instrumentation
2. REQ-OBS-2: W3C Trace Context propagation (L) -- context injection/extraction
3. REQ-OBS-3: OpenTelemetry metrics (L) -- 7 required metrics
4. REQ-OBS-4: Near-zero cost (M, covered by OBS-1/2/3 architecture)
5. REQ-DOC-1: Javadoc on all public APIs (L) -- 50+ source files
6. REQ-DOC-2: README restructure (M) -- 10 required sections
7. REQ-DOC-5: Troubleshooting guide (M) -- 11 entries
8. REQ-DOC-6: CHANGELOG (S) -- backfill 3 versions
9. REQ-DOC-7: Migration guide (M) -- v1 to v2

### Phase 4: Polish & Hardening
1. REQ-ERR-5: Actionable error messages (S) -- message templates
2. REQ-ERR-7: Retry throttling (S) -- semaphore around retry
3. REQ-ERR-8: Streaming error handling (L) -- queue stream reconnection
4. REQ-ERR-9: Async error propagation (M) -- transport vs handler errors
5. REQ-AUTH-5: Security best practices (S) -- toString audit
6. REQ-AUTH-6: TLS credentials during reconnection (M) -- cert reload
7. REQ-TEST-1: Additional unit tests (L) -- error classification, retry, leak detection
8. REQ-TEST-2: Additional integration tests (L) -- reconnection, auth failure
9. REQ-TEST-4: Test organization (S) -- testutil package
10. REQ-TEST-5: Coverage enforcement (S) -- JaCoCo threshold
11. REQ-CONN-6: Connection reuse documentation (S) -- thread safety docs
12. REQ-DOC-3: Quick start restructure (S)
13. REQ-DOC-4: Examples improvements (M)
14. REQ-CQ-5: Code organization (M) -- package restructuring
15. REQ-CQ-6: Code review standards (S) -- branch protection, PR template
16. REQ-CQ-7: Secure defaults (S) -- toString audit, WARN logs

---

## Effort Summary

| Priority | Count | Effort Distribution |
|----------|-------|-------------------|
| P0 | 23 | 4S + 8M + 8L + 3XL |
| P1 | 15 | 6S + 7M + 2L + 0XL |
| P2 | 7 | 4S + 2M + 1L + 0XL |
| P3 | 0 | 0S + 0M + 0L + 0XL |
| **Total** | **45** | **14S + 17M + 11L + 3XL** |

---

## Cross-Category Dependencies

| This Gap | Depends On | Reason |
|----------|-----------|--------|
| REQ-ERR-2 (classification) | REQ-ERR-1 (typed errors) | Classification needs error types to classify into |
| REQ-ERR-3 (retry) | REQ-ERR-2 (classification) | Retry logic needs to know which errors are retryable |
| REQ-ERR-5 (messages) | REQ-ERR-1 (typed errors) | Error message fields must exist on error types |
| REQ-ERR-6 (gRPC mapping) | REQ-ERR-1 + REQ-ERR-2 | Mapping requires error types and classification |
| REQ-ERR-7 (throttling) | REQ-ERR-3 (retry) | Throttling wraps the retry executor |
| REQ-ERR-8 (streaming) | REQ-ERR-1 + REQ-ERR-3 + REQ-CONN-2 | Needs error types, backoff policy, and state machine |
| REQ-ERR-9 (async errors) | REQ-ERR-1 (typed errors) | Needs TransportException and HandlerException types |
| REQ-CONN-1 (reconnection) | REQ-CONN-2 (state machine) + REQ-ERR-1 | State machine drives reconnection; BufferFullException needed |
| REQ-CONN-4 (shutdown) | REQ-CONN-1 + REQ-CONN-2 | Drain requires buffer and state awareness |
| REQ-AUTH-1 (token update) | REQ-ERR-1 (typed errors) | AuthenticationException on missing token |
| REQ-AUTH-4 (credential provider) | REQ-ERR-1 + REQ-CONN-2 | Error classification for provider failures; state awareness |
| REQ-AUTH-6 (cert reconnect) | REQ-AUTH-2 + REQ-AUTH-3 + REQ-CONN-1 | TLS config, mTLS config, reconnection manager |
| REQ-OBS-1 (traces) | REQ-CQ-1 (architecture) | Protocol layer needed for clean instrumentation |
| REQ-OBS-2 (propagation) | REQ-OBS-1 (traces) | Needs spans to link to |
| REQ-OBS-3 (metrics) | REQ-OBS-1 (shared setup) + REQ-CONN-2 | Shared OTel setup; connection state for metrics |
| REQ-OBS-4 (zero cost) | REQ-OBS-1 (architecture) | Part of OTel implementation architecture |
| REQ-CQ-3 (linting) | REQ-TEST-3 (CI) | Linter must run in CI to enforce |
| REQ-CQ-4 (minimal deps) | REQ-OBS-5 (logger) | Must replace Logback before removing it |
| REQ-TEST-1 (unit tests) | REQ-ERR-1 + REQ-ERR-2 + REQ-ERR-3 | Tests need implementations to test |
| REQ-TEST-5 (coverage) | REQ-TEST-3 (CI) | Coverage upload needs CI |
| REQ-DOC-5 (troubleshoot) | REQ-ERR-1 (typed errors) | Error messages must be defined to document |
| REQ-DOC-7 (migration) | REQ-DOC-6 (CHANGELOG) | Migration guide linked from CHANGELOG |
