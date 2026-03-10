# Java SDK -- Tier 2 Gap Research Report

**Assessment Score:** 3.10 / 5.0 (weighted), 3.00 effective (gating applied)
**Target Score:** 4.0+
**Gap:** +0.90 (weighted) / +1.00 (effective)
**Assessment Date:** 2026-03-09
**SDK Version:** v2.1.1

## Executive Summary

### Gap Overview

| GS Category | Assessment Cat # | Current | Target | Gap | Status | Priority | Effort |
|-------------|-----------------|---------|--------|-----|--------|----------|--------|
| 08 API Completeness | 1 | 4.54 | 4.0 | -0.54 (above target) | PARTIAL | P3 | M |
| 09 API Design & DX | 2 | 3.63 | 4.0 | +0.37 | PARTIAL | P2 | M |
| 10 Concurrency | 6 | 3.50 | 4.0 | +0.50 | PARTIAL | P2 | L |
| 11 Packaging | 11 | 3.30 | 4.0 | +0.70 | PARTIAL | P2 | M |
| 12 Compatibility | 12 | 1.80 | 4.0 | +2.20 | MISSING | P2 | L |
| 13 Performance | 13 | 2.10 | 4.0 | +1.90 | MISSING | P2 | L |

### Unassessed Requirements (added post-assessment)

Count: 0 -- All REQ-* items in Tier 2 Golden Standard categories have corresponding assessment coverage (though matching is by semantic content, not by number).

### Critical Path

1. **REQ-COMPAT-1 (Compatibility Matrix)** and **REQ-COMPAT-3 (Language Version Support)** -- foundation for REQ-PKG-3 (Automated Release Pipeline) which needs CI with multi-version matrix.
2. **REQ-PERF-1 (Published Benchmarks)** -- independent, but large effort. No dependencies.
3. **REQ-CONC-2 (Cancellation & Timeout)** and **REQ-CONC-4 (Async-First)** -- adding public `CompletableFuture` API is a prerequisite for proper cancellation support.

### Quick Wins (S effort, high impact)

1. Add server compatibility matrix document (REQ-COMPAT-1 partial)
2. Add deprecation annotations policy to CONTRIBUTING.md (REQ-COMPAT-2 partial)
3. Document thread-safety guarantees on all public types (REQ-CONC-1)
4. Document performance characteristics in README (REQ-PERF-5, REQ-PERF-6)
5. Add CHANGELOG.md (REQ-PKG-2, REQ-PKG-4)

### Features to Remove

None identified. No Tier 2 requirements call for removal of existing features.

---

## Category 08: API Completeness

**Assessment Category:** 1 (API Completeness & Feature Parity)
**Assessment Score:** 4.54 / 5.0
**Target:** 4.0+
**Status:** Above target, minor gaps remain

### REQ-API-1: Core Feature Coverage

**Current State:**
Assessment covers this extensively across criteria 1.1-1.5. Evidence shows 40 of 44 features scored 2 (Complete), 3 scored 1 (Partial), 1 scored 0 (Missing).

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Ping (Core) | COMPLIANT | `KubeMQClient.ping()` verified (1.5.1, score 2) |
| Channel List (Core) | COMPLIANT | `listEventsChannels()` etc. (1.5.3, score 2) |
| Server Info (Extended) | COMPLIANT | `ServerInfo` class (1.5.2, score 2) |
| Channel Create (Extended) | COMPLIANT | `createEventsChannel()` etc. (1.5.4, score 2) |
| Channel Delete (Extended) | COMPLIANT | `deleteEventsChannel()` etc. (1.5.5, score 2) |
| Events: Publish | COMPLIANT | `PubSubClient.sendEventsMessage()` (1.1.1, score 2) |
| Events: Subscribe with callback | COMPLIANT | `PubSubClient.subscribeToEvents()` (1.1.2, score 2) |
| Events: Wildcard subscribe | COMPLIANT | Channel name passed to server (1.1.4, score 2) |
| Events: Group subscribe | COMPLIANT | `EventsSubscription.group` field (1.1.7, score 2) |
| Events: Unsubscribe | PARTIAL | No per-subscription cancel; only `close()` tears down all (1.1.6, score 1) |
| Events Store: Publish | COMPLIANT | `sendEventsStoreMessage()` (1.2.1, score 2) |
| Events Store: Subscribe from beginning | COMPLIANT | `EventsStoreType.StartFromFirst` (1.2.4, score 2) |
| Events Store: Subscribe from sequence | COMPLIANT | `EventsStoreType.StartAtSequence` (1.2.6, score 2) |
| Events Store: Subscribe from timestamp | COMPLIANT | `EventsStoreType.StartAtTime` (1.2.7, score 2) |
| Events Store: Subscribe from time delta | COMPLIANT | `EventsStoreType.StartAtTimeDelta` (1.2.8, score 2) |
| Events Store: Subscribe from last | COMPLIANT | `EventsStoreType.StartFromLast` (1.2.5, score 2) |
| Events Store: Subscribe new only | COMPLIANT | `EventsStoreType.StartNewOnly` (1.2.3, score 2) |
| Events Store: Unsubscribe | PARTIAL | Same limitation as Events unsubscribe |
| Queue stream upstream | COMPLIANT | `QueueUpstreamHandler` (1.3.1, score 2) |
| Queue stream downstream | COMPLIANT | `QueueDownstreamHandler` (1.3.6, score 2) |
| Queue: Visibility timeout | COMPLIANT | `QueuesPollRequest.visibilitySeconds` (1.3.4, score 2) |
| Queue: Ack | COMPLIANT | `QueueMessageReceived.ack()` (1.3.5, score 2) |
| Queue: Reject | COMPLIANT | `QueueMessageReceived.reject()` (1.3.5, score 2) |
| Queue: Requeue | COMPLIANT | `QueueMessageReceived.reQueue()` (1.3.5, score 2) |
| Queue: DLQ | COMPLIANT | `attemptsBeforeDeadLetterQueue`, `deadLetterQueue` (1.3.9, score 2) |
| Queue: Delayed messages | COMPLIANT | `QueueMessage.delayInSeconds` (1.3.7, score 2) |
| Queue: Message expiration | COMPLIANT | `QueueMessage.expirationInSeconds` (1.3.8, score 2) |
| Queue: Simple send (non-stream) | COMPLIANT | `QueuesClient.sendQueuesMessage()` (1.3.1, score 2) |
| Queue: Send batch | PARTIAL | No explicit batch API; users must loop individually (1.3.2, score 1) |
| Queue: Receive (single pull) | COMPLIANT | `QueuesClient.receiveQueuesMessages()` (1.3.3, score 2) |
| Queue: Peek | COMPLIANT | `QueuesClient.waiting()` with `isPeak=true` (1.3.11, score 2) |
| RPC Commands: Send | COMPLIANT | `CQClient.sendCommandRequest()` (1.4.1, score 2) |
| RPC Commands: Subscribe | COMPLIANT | `CQClient.subscribeToCommands()` (1.4.2, score 2) |
| RPC Commands: Group subscribe | COMPLIANT | `CommandsSubscription.group` (1.4.10, score 2) |
| RPC Commands: Send response | COMPLIANT | `CQClient.sendResponseMessage()` (1.4.3, score 2) |
| RPC Queries: Send | COMPLIANT | `CQClient.sendQueryRequest()` (1.4.5, score 2) |
| RPC Queries: Subscribe | COMPLIANT | `CQClient.subscribeToQueries()` (1.4.6, score 2) |
| RPC Queries: Group subscribe | COMPLIANT | `QueriesSubscription.group` (1.4.10, score 2) |
| RPC Queries: Send response | COMPLIANT | `CQClient.sendResponseMessage(QueryResponseMessage)` (1.4.7, score 2) |
| RPC Queries: Cache-enabled | COMPLIANT | `QueryMessage.cacheKey`, `cacheTtlInSeconds` (1.4.11, score 2) |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Unsubscribe (Events/Events Store) | `subscribeToEvents()` must return a `Subscription` handle with `cancel()`. Store `StreamObserver` reference and expose cancel. | M | None | Low -- users work around via `close()` | P3 |
| Batch send for queues | Add `sendQueuesMessages(List<QueueMessage>)` using server's `QueueMessagesBatchRequest`. Single gRPC call. | S | None | Low -- individual sends work | P3 |

### REQ-API-2: Feature Matrix Document

**Current State:** No feature matrix document exists in the `clients/` directory. Assessment report (section "Competitor Comparison") provides a comparison but not in the required format.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Feature matrix document exists and is current | MISSING | No document in `clients/` |
| Matrix reviewed/updated with each release | MISSING | No process exists |
| Features categorized as Core/Extended | NOT_ASSESSED | Assessment didn't evaluate this |
| Gaps documented with rationale | MISSING | No gap documentation |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Feature matrix | Create `clients/feature-matrix.md` with all features per SDK. Populate Java column from assessment. | S | None | Low | P3 |

### REQ-API-3: No Silent Feature Gaps

**Current State:** Assessment 1.3.12 shows `purge queue` scores 0 (Missing). No `ErrNotImplemented` error for this operation.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Missing features documented in matrix | MISSING | No feature matrix |
| Return `ErrNotImplemented` error | MISSING | Missing features silently absent |
| Tracking issue for implementation | NOT_ASSESSED | Not checked |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| NotImplemented error | Add `NotImplementedException` class. For any server feature not exposed (e.g., purge), add a stub method that throws `NotImplementedException`. | S | Error hierarchy (Tier 1 REQ-ERR-1) | Low | P3 |

---

## Category 09: API Design & DX

**Assessment Category:** 2 (API Design & DX)
**Assessment Score:** 3.63 / 5.0
**Target:** 4.0+
**Status:** Below target

### REQ-DX-1: Language-Idiomatic Configuration

**Current State:** Assessment 2.1.2 (score 4) and 2.2.2 (score 4) confirm builder pattern usage. Assessment 2.1.3 (score 2) flags error handling pattern issues. Assessment 2.1.7 (score 3) notes mixed null handling.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Configuration follows language-idiomatic pattern | COMPLIANT | Lombok `@Builder` pattern (2.1.2) |
| Required parameters enforced at compile/construction time | PARTIAL | `address` and `clientId` required at runtime only. No compile-time enforcement. Builder doesn't validate until `build()` is called, and even then validation is incomplete. |
| Optional parameters have documented default values | PARTIAL | Defaults exist (`maxReceiveSize=100MB`, `reconnectInterval=1s`) but not documented in Javadoc (10.1.2 score 1). |
| Invalid configuration rejected at construction (fail-fast) | PARTIAL | TLS cert file paths validated at construction. But address format not validated; connection errors surface on first operation (Developer Journey Step 2). |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Address validation | Add address format validation in builder `build()` -- check non-empty, valid `host:port` format. | S | None | Low | P3 |
| Default value docs | Add Javadoc to builder fields with `@Builder.Default` documenting the default value. | S | Javadoc task (Tier 1) | Low | P3 |
| Connection validation at build | Add optional `validateOnBuild(true)` that pings server during `build()`. Default false for backward compat. | S | None | Medium -- changes construction semantics | P3 |

### REQ-DX-2: Minimal Code Happy Path

**Current State:** Assessment 2.2.1 (score 4) shows publish is ~4 lines. Developer Journey Step 3 confirms concise publish.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Events publish: <=3 lines | PARTIAL | 2 lines after client creation, but `EventMessage.builder().channel("ch").body("msg".getBytes()).build()` is verbose. Could be condensed with a convenience method. |
| Queue send: <=3 lines | PARTIAL | Same verbosity with `QueueMessage.builder()...build()` |
| RPC command/query: <=3 lines | PARTIAL | Same pattern |
| Subscribe/receive with ack: <=10 lines | COMPLIANT | Assessment 2.2.1 confirms ~3 lines for subscribe callback |
| Defaults (localhost:50000, no auth) for local dev | PARTIAL | `address` is required (no default). `clientId` is required (no default). |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Convenience publish methods | Add shorthand: `client.publishEvent("channel", body)` that internally builds `EventMessage`. Similarly for queue/command/query. | S | None | Low | P3 |
| Default address | Make `address` default to `"localhost:50000"` in builder. Make `clientId` default to auto-generated UUID. | S | None | Low -- backward compatible (both still settable) | P3 |

### REQ-DX-3: Consistent Verbs Across SDKs

**Current State:** Assessment 2.4.3 (score 3) notes Java methods are longer than typical cross-SDK convention. Assessment 2.2.4 (score 4) confirms consistent internal pattern.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| All SDKs use same verbs | PARTIAL | Java uses `sendEventsMessage` vs standard `publishEvent`. `sendQueuesMessage` vs `sendQueueMessage`. `subscribeToEvents` vs `subscribeEvents`. Names are close but not aligned to the verb table. |
| Method names predictable cross-SDK | PARTIAL | Generally predictable but not exact (assessment 2.4.3 score 3) |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Verb alignment | Add alias methods matching the standard verb table: `publishEvent()`, `sendQueueMessage()`, `subscribeToEvents()` (already close), `ackMessage()`, `rejectMessage()`. Deprecate old names over 2 minor versions. | M | REQ-COMPAT-2 (deprecation policy) | Medium -- breaking change risk if old names removed | P2 |

### REQ-DX-4: Fail-Fast Validation

**Current State:** Assessment 5.2.4 (score 3) covers input validation. Assessment 2.1.3 (score 2) notes error handling issues. Developer Journey Step 5 highlights all errors as `RuntimeException`.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Invalid inputs produce clear error messages | PARTIAL | Channel names validated non-empty. Body size validated. Timeout validated > 0. But address format not validated, channel name format not validated (special chars, length). |
| Validation errors classified non-retryable | MISSING | No retryable/non-retryable classification (4.1.3 score 1). Depends on error hierarchy (Tier 1). |
| Validation happens before network call | COMPLIANT | Message validation in `encode()` methods runs before gRPC call. |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Validation error classification | Add `ValidationException extends KubeMQException` marked non-retryable. All input validation throws this type. | S | Error hierarchy (Tier 1 REQ-ERR-1) | Low | P3 |
| Address validation | Validate address format (non-empty, host:port pattern) in builder. | S | None | Low | P3 |
| ClientId validation | Validate clientId non-empty in builder. | S | None | Low | P3 |

### REQ-DX-5: Message Builder/Factory

**Current State:** Assessment 2.3.1 (score 4) confirms separate message types per pattern with builders.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Message types have builder/factory methods | COMPLIANT | Lombok `@Builder` on all message types (2.1.2) |
| Required fields enforced at build time | PARTIAL | Channel validated in `encode()` (before send) but not in `build()`. Body validated but allows empty. |
| Optional fields have sensible defaults | COMPLIANT | Empty byte array body, empty HashMap tags, null metadata encoded as empty string (1.6.4) |
| Messages immutable after construction | PARTIAL | Lombok generates setters via `@Data`. Messages are mutable. Should use `@Value` or `@Builder` without `@Data`. |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Build-time validation | Move channel/required field validation from `encode()` to builder `build()` method (custom builder). | S | None | Low | P3 |
| Message immutability | Replace `@Data` with `@Value` on message classes, or use `@Getter` only (no setters). May require migration for internal usage. | M | None | Medium -- breaking change for users who mutate messages | P2 |

---

## Category 10: Concurrency & Thread Safety

**Assessment Category:** 6 (Concurrency)
**Assessment Score:** 3.50 / 5.0
**Target:** 4.0+
**Status:** Below target

### REQ-CONC-1: Thread Safety Documentation

**Current State:** Assessment 6.1.4 (score 1) confirms no thread safety documentation exists. Assessment 6.1.1-6.1.3 (scores 4,4,4) confirm the types ARE thread-safe in practice.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Client type documented as thread-safe | MISSING | No Javadoc, no thread-safety annotations (6.1.4 score 1) |
| Doc comments on each public type state concurrency guarantee | MISSING | Zero Javadoc comments across 50 source files (10.1.4 score 1) |
| Non-thread-safe types document the restriction | MISSING | Message types not documented as non-thread-safe |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Thread-safety Javadoc | Add `@ThreadSafe` / `@NotThreadSafe` annotations (from `javax.annotation.concurrent` or JSR-305). Add Javadoc to `KubeMQClient`, `PubSubClient`, `QueuesClient`, `CQClient` stating thread-safe. Add Javadoc to `EventMessage`, `QueueMessage`, `CommandMessage`, `QueryMessage` stating NOT thread-safe. Add Javadoc to `EventMessageReceived`, `QueueMessageReceived` stating safe to read. | S | None | Low | P3 |

### REQ-CONC-2: Cancellation & Timeout Support

**Current State:** Assessment 4.4.2 (score 2) confirms no cancellation support. Assessment 6.2.J1 (score 2) confirms `CompletableFuture` used internally but not exposed publicly. Assessment 4.4.1 (score 3) shows partial timeout support.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| All blocking operations accept cancellation mechanism | MISSING | No `CompletableFuture<T>` return types in public API. No `Duration` timeout parameters on sync methods. |
| Cancellation propagated to underlying gRPC call | MISSING | No cancellation propagation mechanism |
| Cancelled operations produce clear cancellation error | MISSING | No cancellation error type |
| Long-lived subscriptions accept/honor cancellation | PARTIAL | No per-subscription cancel, but `close()` tears down subscriptions. No `Subscription` handle returned. |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Public async API | Add async variants of all operations returning `CompletableFuture<T>`: `sendEventsMessageAsync()`, `sendCommandRequestAsync()`, `sendQueuesMessageAsync()`, etc. Sync methods become wrappers calling `.get(timeout)`. | L | None | Medium -- large API surface change | P2 |
| Sync timeout parameter | Add `Duration timeout` parameter to sync methods or overloads. Default to `requestTimeoutSeconds` from builder. | M | Async API (above) | Low | P2 |
| Subscription handle | `subscribeToEvents()` returns `Subscription` with `cancel()` and `CompletableFuture<Void> cancelAsync()`. | M | None | Low | P2 |
| Cancellation error | Add `CancellationException` (or use `java.util.concurrent.CancellationException`). Map gRPC `CANCELLED` status. | S | Error hierarchy (Tier 1) | Low | P3 |

### REQ-CONC-3: Subscription Callback Behavior

**Current State:** Assessment 6.1.3 (score 4) shows independent `StreamObserver` per subscription. No explicit documentation of callback behavior.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Document whether callbacks may fire concurrently | MISSING | No documentation |
| Default callback concurrency is 1 (sequential) | PARTIAL | gRPC `StreamObserver.onNext()` is called sequentially by gRPC runtime for a single stream. But this is not documented or guaranteed by the SDK. |
| Mechanism to control callback concurrency | MISSING | No `maxConcurrentCallbacks` option |
| Callbacks must not block SDK's internal event loop | NOT_ASSESSED | Assessment didn't explicitly evaluate this. gRPC uses its own executor for callbacks, so user callbacks on that thread could block. |
| Long-running callback guidance documented | MISSING | No documentation |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Callback concurrency docs | Document in subscription Javadoc that callbacks fire sequentially per subscription (gRPC guarantee). | S | None | Low | P3 |
| Callback executor | Add `callbackExecutor(Executor)` option to subscription builders. Default: caller thread (gRPC executor). Allow user-provided thread pool. | M | None | Low | P3 |
| maxConcurrentCallbacks | Add `maxConcurrentCallbacks(int)` on subscription builders. Default 1. Use Semaphore to limit concurrency. Dispatch to provided executor. | M | Callback executor (above) | Low | P3 |
| Long-running callback guidance | Add Javadoc and README section: "Do not block in callbacks. Use a worker pool for heavy processing." | S | None | Low | P3 |

### REQ-CONC-4: Async-First Where Idiomatic

**Current State:** Assessment 6.2.J1 (score 2) confirms no public async API. Java convention is both sync and async.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Primary API style matches language convention (both sync and async for Java) | PARTIAL | Only sync API exists. Java convention requires both. |
| Async APIs don't block the calling thread | MISSING | No async API to evaluate |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Async API | Same as REQ-CONC-2 async remediation. Add `CompletableFuture`-returning methods for all operations. | L | None | Medium | P2 |

### REQ-CONC-5: Shutdown-Callback Safety

**Current State:** Assessment 2.1.5 (score 4) confirms `AutoCloseable` with 5-second shutdown timeout. Assessment 6.2.J4 (score 5) confirms proper cleanup.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| `Close()` waits for in-flight callbacks with configurable timeout (default 30s) | PARTIAL | `close()` calls `managedChannel.shutdown().awaitTermination(5, SECONDS)`. Timeout is 5s (not 30s). Not configurable. Does not explicitly wait for user callbacks to complete. |
| Operations after `Close()` return `ErrClientClosed` | MISSING | No `ErrClientClosed` error. Operations after close throw generic gRPC errors. |
| `Close()` is idempotent | NOT_ASSESSED | Assessment didn't explicitly test double-close behavior. |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Configurable shutdown timeout | Add `shutdownTimeoutSeconds(int)` to builder (default 30). Use in `close()`. | S | None | Low | P3 |
| Wait for callbacks | Track in-flight callbacks with `AtomicInteger` counter. In `close()`, wait for counter to reach 0 before channel shutdown. | M | None | Medium | P3 |
| ErrClientClosed | Add `closed` volatile boolean flag. Check in all public methods. Throw `ClientClosedException extends KubeMQException`. | S | Error hierarchy (Tier 1) | Low | P3 |
| Idempotent close | Add `AtomicBoolean closed` guard. Return immediately on second call. | S | None | Low | P3 |

---

## Category 11: Packaging & Distribution

**Assessment Category:** 11 (Packaging)
**Assessment Score:** 3.30 / 5.0
**Target:** 4.0+
**Status:** Below target

### REQ-PKG-1: Package Manager Publishing

**Current State:** Assessment 11.1.1-11.1.3 (scores 4,4,4) confirm Maven Central publishing is configured.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Package published and installable via single command | COMPLIANT | Maven dependency XML works (11.1.3). `central-publishing-maven-plugin` configured (11.1.1). |
| Package includes README, LICENSE, and CHANGELOG | PARTIAL | README and LICENSE present. No CHANGELOG.md (10.4.5 score 1). |
| Package metadata complete | COMPLIANT | pom.xml has name, description, url, license, SCM, developer info (11.1.2 score 4). |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| CHANGELOG | Create `CHANGELOG.md` retroactively covering 2.0.0 through 2.1.1. Follow Keep a Changelog format. | S | None | Low | P3 |

### REQ-PKG-2: Semantic Versioning

**Current State:** Assessment 11.2.1 (score 4) confirms SemVer compliance.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Version numbers follow SemVer | COMPLIANT | v2.0.3, v2.1.0, v2.1.1 follow SemVer (11.2.1) |
| Breaking changes only in MAJOR releases | COMPLIANT | No breaking changes observed in 2.x (12.1.4 score 3) |
| Pre-release versions clearly labeled | NOT_ASSESSED | No pre-release versions observed |
| Version embedded in package (queryable at runtime) | MISSING | No `getVersion()` method or version constant. Version only in pom.xml. |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Runtime version | Add `KubeMQ.VERSION` constant. Generate from `pom.xml` version using Maven resource filtering or a `version.properties` file. Add `KubeMQ.getVersion()` static method. | S | None | Low | P3 |

### REQ-PKG-3: Automated Release Pipeline

**Current State:** Assessment 9.3.1 (score 1) confirms no CI/CD pipeline exists.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Release triggered by git tag or merge to release branch | MISSING | No CI pipeline (9.3.1 score 1) |
| Publishing requires no manual steps after tagging | MISSING | Manual Maven Central publishing |
| GitHub Release created automatically with changelog | MISSING | No GitHub Releases content (11.2.3 score 1) |
| Failed releases don't publish partial artifacts | NOT_ASSESSED | No pipeline to evaluate |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Release pipeline | Create `.github/workflows/release.yml`: trigger on tag push `v*`, build with Maven, run tests, publish to Maven Central via Sonatype, create GitHub Release with changelog extract. Use `OSSRH_USERNAME`/`OSSRH_TOKEN`/`GPG_PRIVATE_KEY` secrets. | M | CI pipeline (Tier 1), Maven Central credentials | High -- no automated releases | P2 |

### REQ-PKG-4: Conventional Commits (Recommended)

**Current State:** Assessment shows commit messages are freeform (git log shows "Bump version...", "Add examples...", "Upgrade dependencies...").

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Commit format documented in CONTRIBUTING.md | MISSING | No CONTRIBUTING.md (11.3.4 score 1) |
| Commit linting configured | MISSING | No commitlint or equivalent |
| CHANGELOG maintained | MISSING | No CHANGELOG.md (10.4.5 score 1) |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| CONTRIBUTING.md | Create with commit format guidance. Document conventional commits as recommended. | S | None | Low | P3 |
| CHANGELOG | Create `CHANGELOG.md`. Can be manual or auto-generated. | S | None | Low | P3 |
| Commit linting | Add commitlint via pre-commit hook or CI check. Optional -- GS says "recommended, not required." | S | CI pipeline | Low | P3 |

---

## Category 12: Compatibility, Lifecycle & Supply Chain

**Assessment Category:** 12 (Compatibility & Lifecycle)
**Assessment Score:** 1.80 / 5.0
**Target:** 4.0+
**Status:** Major gaps -- lowest Tier 2 score

### REQ-COMPAT-1: Client-Server Compatibility Matrix

**Current State:** Assessment 12.1.1 (score 1) confirms no compatibility documentation.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Compatibility matrix maintained in SDK repo or central docs | MISSING | No document (12.1.1 score 1) |
| Matrix updated when SDK/server versions add features | MISSING | No process |
| SDK validates server version on connection and warns if incompatible | MISSING | `ping()` returns `ServerInfo` with version but no comparison logic |
| SDK logs warning if server version outside tested range | MISSING | No version check |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Compatibility matrix doc | Create `COMPATIBILITY.md` documenting tested server versions. Add to README. | S | None | Medium -- users don't know what works | P2 |
| Server version check | After successful connection, call `ping()`, compare `ServerInfo.version` against known compatible range. Log warning via SLF4J if outside range. Do NOT fail connection. Store compatible range as constants. | M | None | Medium | P2 |

### REQ-COMPAT-2: Deprecation Policy

**Current State:** Assessment 12.1.3 (score 1) confirms no deprecation policy.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Deprecated APIs have language-appropriate annotations | MISSING | No `@Deprecated` annotations anywhere (8.4.2) |
| Deprecation notice includes replacement API name | MISSING | No deprecation notices |
| CHANGELOG entries document deprecations | MISSING | No CHANGELOG |
| Removed APIs listed in migration guides | MISSING | No migration guide (10.2.4 score 1) |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Deprecation policy | Document policy in CONTRIBUTING.md: use `@Deprecated(since="X.Y", forRemoval=true)` + Javadoc `@deprecated Use X instead.`. Minimum 2 minor versions / 6 months before removal. | S | CONTRIBUTING.md creation | Low | P3 |
| Migration guide | Create `MIGRATION.md` for v1 to v2 migration. | M | None | Low | P3 |

### REQ-COMPAT-3: Language Version Support

**Current State:** Assessment 12.1.2 (score 2) shows README states "JDK 8 or higher" but pom.xml targets Java 11. Assessment 9.3.4 (score 1) confirms no multi-version testing.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Minimum language version documented in README | PARTIAL | README says "JDK 8 or higher" but pom.xml targets Java 11. Inconsistent. |
| CI tests against specified version matrix (Java 11, 17, 21) | MISSING | No CI pipeline, no multi-version testing (9.3.4 score 1) |
| Dropping language version support is MAJOR bump | NOT_ASSESSED | No process documented |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Fix README | Update README to state "Java 11+" (matching pom.xml source/target). | S | None | Low | P2 |
| Multi-version CI | Add CI matrix testing Java 11, 17, 21. Use `actions/setup-java` with Temurin distribution. | M | CI pipeline (Tier 1) | Medium -- may reveal compatibility issues | P2 |

### REQ-COMPAT-4: Supply Chain Security

**Current State:** Assessment 12.2.3 (score 1), 12.2.5 (score 1), 9.3.5 (score 1) confirm no supply chain security measures.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Dependencies scanned for vulnerabilities | MISSING | No Dependabot, Renovate, or OWASP plugin (12.2.3, 9.3.5) |
| SBOM generated (recommended) | MISSING | No CycloneDX or SPDX plugin (12.2.5 score 1) |
| Direct dependencies audited and justified | MISSING | No audit document. `grpc-alts` may be unnecessary (11.1.4). |
| No critical vulnerabilities at release time | NOT_ASSESSED | No scanning tool to verify |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Dependabot | Add `.github/dependabot.yml` for Maven ecosystem. Weekly schedule. | S | GitHub repo access | Medium -- unscanned deps are risk | P2 |
| SBOM | Add `cyclonedx-maven-plugin` to pom.xml. Generate SBOM on `mvn package`. Attach to GitHub Release. | S | Release pipeline | Low -- recommended, not required | P3 |
| Dependency audit | Review `grpc-alts` necessity. Document justification for each direct dependency in a comment block in pom.xml or separate doc. | S | None | Low | P3 |

### REQ-COMPAT-5: End-of-Life Policy

**Current State:** No EOL policy documented. Assessment 12.2.6 (score 2) notes single maintainer.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| EOL policy documented in README | MISSING | No EOL policy |
| Previous major versions receive security patches for 12 months | NOT_ASSESSED | v1.x status unknown |
| EOL status clearly marked in repository | MISSING | No EOL marking |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| EOL policy | Add "Version Support" section to README: "Current major version (v2.x) is actively maintained. Previous major versions receive security patches for 12 months after next major GA." | S | None | Low | P3 |
| v1 EOL marking | If v1 repo exists, add banner: "This version is end-of-life. Migrate to v2." | S | None | Low | P3 |

---

## Category 13: Performance

**Assessment Category:** 13 (Performance)
**Assessment Score:** 2.10 / 5.0
**Target:** 4.0+
**Status:** Major gaps

### REQ-PERF-1: Published Benchmarks

**Current State:** Assessment 13.1.1-13.1.4 (all score 1) confirm no benchmarks exist.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Benchmarks exist in SDK repo (JMH) | MISSING | No benchmarks (13.1.1 score 1) |
| Benchmarks runnable with single command | MISSING | No benchmark infrastructure |
| Results documented in repo | MISSING | No published numbers (13.1.4 score 1) |
| Benchmark methodology documented | MISSING | No methodology (13.1.3 score 1) |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| JMH benchmark suite | Create `src/test/java/io/kubemq/sdk/benchmark/` with JMH benchmarks. Add `jmh-core` and `jmh-generator-annprocess` dependencies (test scope). Create benchmarks: `PublishThroughputBenchmark` (1KB payload, msgs/sec), `PublishLatencyBenchmark` (p50/p99), `QueueRoundtripBenchmark` (send + receive + ack latency), `ConnectionSetupBenchmark` (time to first message). Add Maven profile `benchmark` to run with `mvn test -Pbenchmark`. | L | Running KubeMQ server for integration benchmarks | Medium -- no baseline data | P2 |
| Benchmark docs | Create `BENCHMARKS.md` with methodology (hardware, server config, message count) and baseline results. | S | Benchmarks (above) | Low | P2 |

### REQ-PERF-2: Connection Reuse

**Current State:** Assessment 13.2.6 (score 4) confirms single `ManagedChannel` shared across operations.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Single Client uses one long-lived gRPC channel | COMPLIANT | Single `ManagedChannel` in `KubeMQClient` (13.2.6) |
| Multiple concurrent operations multiplex over same channel | COMPLIANT | gRPC multiplexing inherent in ManagedChannel |
| Documentation advises against Client-per-operation | MISSING | No documentation on this |
| No per-operation connection overhead | COMPLIANT | Stubs created once and reused (13.2.6) |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Connection reuse docs | Add to README/Performance Tips: "Reuse the client instance. Do not create a new client per operation. A single client multiplexes all operations over one gRPC channel." | S | None | Low | P3 |

### REQ-PERF-3: Efficient Serialization

**Current State:** Assessment 8.3.2 (score 4) confirms protobuf wrapping. Assessment 13.2.4 (score 3) notes `byte[]` body copied during protobuf conversion.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Protobuf serialization uses standard runtime | COMPLIANT | Uses `protobuf-java` 4.28.2 (8.4.5 score 5) |
| Avoid unnecessary memory copies of message bodies | PARTIAL | `ByteString.copyFrom(body)` in encode creates a copy. This is inherent to protobuf Java API. Could use `ByteString.copyFrom(ByteBuffer)` with zero-copy for large messages, but complexity may not be justified. |
| Buffer pooling recommended only when benchmarks show allocation pressure | COMPLIANT | No premature optimization. No buffer pooling (13.2.1 score 1), which is correct per this criterion -- should only add when benchmarks justify. |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Memory copy documentation | Document that protobuf conversion creates a body copy. For very large messages, note the `maxReceiveSize` setting. Only optimize after benchmarks show pressure. | S | Benchmarks | Low | P3 |

### REQ-PERF-4: Batch Operations

**Current State:** Assessment 13.2.2 (score 2) and 1.3.2 (score 1) confirm batch receive exists but batch send is missing.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| Batch operations use single gRPC call | PARTIAL | Batch receive: `pollMaxMessages` retrieves multiple in one call. Batch send: not implemented (1.3.2 score 1). |
| Batch size configurable | PARTIAL | Receive: `pollMaxMessages` configurable. Send: N/A (no batch send). |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Batch send | Add `sendQueuesMessages(List<QueueMessage>)` using server proto `SendQueueMessagesBatch` RPC. Single gRPC call for N messages. Add `maxBatchSize` validation (e.g., cap at 1000). | M | None | Low | P2 |

### REQ-PERF-5: Performance Documentation

**Current State:** Assessment 10.2.5 (score 1) confirms no performance documentation.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| README/doc includes performance characteristics | MISSING | No performance docs (10.2.5 score 1) |
| Tuning guidance: batching, batch sizes, connection sharing | MISSING | No tuning docs |
| Known limitations documented (max message size, max streams) | PARTIAL | `maxReceiveSize` mentioned in builder docs. No comprehensive limitations doc. |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Performance docs | Add "Performance" section to README covering: max message size (100MB default), connection sharing guidance, batch receive via `pollMaxMessages`, known limitations. | S | None | Low | P2 |

### REQ-PERF-6: Performance Tips Documentation

**Current State:** No performance tips documentation exists.

| Acceptance Criterion | Status | Evidence / Gap |
|---------------------|--------|----------------|
| "Performance Tips" covering: reuse client, use batching, don't block callbacks, close streams | MISSING | No such section |

**Remediation:**

| Gap | What Needs to Change | Complexity | Dependencies | Risk | Priority |
|-----|---------------------|------------|--------------|------|----------|
| Performance Tips | Add "Performance Tips" section to README: (1) Reuse the client instance, (2) Use `pollMaxMessages` for batch receive / batch send when available, (3) Do not block subscription callbacks, (4) Close streams and client when done. | S | None | Low | P2 |

---

## Dependency Graph

```
REQ-DX-3 (Verb alignment) --> REQ-COMPAT-2 (Deprecation policy)
REQ-DX-4 (Validation classification) --> Tier 1 REQ-ERR-1 (Error hierarchy)
REQ-DX-5 (Message immutability) --> standalone (breaking change, plan carefully)
REQ-CONC-2 (Cancellation) --> REQ-CONC-4 (Async API) [same work]
REQ-CONC-5 (ErrClientClosed) --> Tier 1 REQ-ERR-1 (Error hierarchy)
REQ-PKG-3 (Release pipeline) --> Tier 1 CI pipeline
REQ-COMPAT-3 (Multi-version CI) --> Tier 1 CI pipeline
REQ-COMPAT-4 (Dependabot) --> GitHub repo settings
REQ-PERF-1 (Benchmarks) --> Running KubeMQ server
REQ-PERF-4 (Batch send) --> standalone
REQ-PERF-5/6 (Performance docs) --> standalone (quick wins)
```

## Implementation Sequence

### Wave 1: Quick Documentation Wins (S effort, no code changes)
1. REQ-CONC-1: Thread-safety Javadoc/annotations
2. REQ-PERF-5: Performance documentation
3. REQ-PERF-6: Performance Tips
4. REQ-PERF-2: Connection reuse documentation
5. REQ-COMPAT-1: Compatibility matrix document (partial)
6. REQ-COMPAT-2: Deprecation policy in CONTRIBUTING.md
7. REQ-COMPAT-5: EOL policy in README
8. REQ-COMPAT-3: Fix README Java version (11+)
9. REQ-PKG-1: CHANGELOG.md
10. REQ-PKG-2: Runtime version constant
11. REQ-PKG-4: CONTRIBUTING.md with commit format

### Wave 2: Small Code Changes (S-M effort)
12. REQ-DX-1: Address validation in builder
13. REQ-DX-2: Default address/clientId, convenience publish methods
14. REQ-API-1: Batch send for queues
15. REQ-API-1: Subscription handle with cancel()
16. REQ-COMPAT-1: Server version check on connection
17. REQ-COMPAT-4: Dependabot configuration
18. REQ-CONC-5: Idempotent close, ErrClientClosed (after Tier 1 error hierarchy)

### Wave 3: Medium Code Changes (M-L effort)
19. REQ-DX-3: Verb alignment with deprecation
20. REQ-DX-5: Message immutability (breaking change -- major version?)
21. REQ-CONC-3: Callback executor and concurrency control
22. REQ-PKG-3: Automated release pipeline (after Tier 1 CI)
23. REQ-PERF-4: Batch send implementation

### Wave 4: Large Code Changes (L effort)
24. REQ-CONC-2 + REQ-CONC-4: Public async API with CompletableFuture
25. REQ-PERF-1: JMH benchmark suite
26. REQ-COMPAT-3: Multi-version CI matrix (after Tier 1 CI)

## Effort Summary

| Effort | Count | Items |
|--------|-------|-------|
| S (< 1 day) | 18 | REQ-CONC-1 docs, REQ-PERF-2 docs, REQ-PERF-5 docs, REQ-PERF-6 docs, REQ-COMPAT-1 doc, REQ-COMPAT-2 policy, REQ-COMPAT-5 EOL, REQ-COMPAT-3 README fix, REQ-PKG-1 CHANGELOG, REQ-PKG-2 version, REQ-PKG-4 CONTRIBUTING, REQ-DX-1 address validation, REQ-DX-2 defaults, REQ-API-2 feature matrix, REQ-API-3 NotImplemented, REQ-COMPAT-4 Dependabot, REQ-CONC-5 idempotent close, REQ-PERF-3 docs |
| M (1-3 days) | 10 | REQ-DX-3 verb alignment, REQ-DX-5 immutability, REQ-CONC-2 subscription handle, REQ-CONC-3 callback executor, REQ-CONC-5 callback drain, REQ-PKG-3 release pipeline, REQ-COMPAT-1 server version check, REQ-COMPAT-3 multi-version CI, REQ-PERF-4 batch send, REQ-COMPAT-2 migration guide |
| L (3-5 days) | 2 | REQ-CONC-2+4 async API, REQ-PERF-1 JMH benchmarks |
| XL (> 5 days) | 0 | -- |

**Total estimated effort:** ~25-35 developer-days

## Cross-Category Dependencies

| Tier 2 REQ | Depends on Tier 1 REQ | Nature |
|-----------|----------------------|--------|
| REQ-DX-4 (Validation errors non-retryable) | REQ-ERR-1 (Error hierarchy) | ValidationException type needed |
| REQ-CONC-5 (ErrClientClosed) | REQ-ERR-1 (Error hierarchy) | ClientClosedException type needed |
| REQ-PKG-3 (Release pipeline) | REQ-TEST-CI (CI pipeline) | Pipeline foundation needed |
| REQ-COMPAT-3 (Multi-version CI) | REQ-TEST-CI (CI pipeline) | CI must exist first |
| REQ-COMPAT-4 (Dependabot) | REQ-TEST-CI (CI pipeline) | PRs need CI to validate |
| REQ-DX-3 (Verb alignment) | REQ-COMPAT-2 (Deprecation policy) | Must deprecate old names properly |

Cross-tier dependencies are significant: the Tier 1 error hierarchy and CI pipeline are prerequisites for ~6 Tier 2 items. Implementing Tier 1 first is strongly recommended.
