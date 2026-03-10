# Java SDK Specs -- Implementability Review (Round 2, Batch 3)

**Reviewer:** Senior Java Developer (Implementer)
**Specs Reviewed:** 10-concurrency, 11-packaging, 12-compatibility, 13-performance
**Date:** 2026-03-09
**Review Focus:** R1 fix verification, practical implementability, missing details, test feasibility, incremental implementation

---

## Executive Summary

All 20 R1 fixes were verified as correctly applied. The specs are generally implementable, but several practical issues remain that would cause friction or rework during implementation. The biggest concerns are: (1) the async API in spec 10 is a massive undertaking (27+ new methods) that should be explicitly staged into sub-phases, (2) the `defaultCallbackExecutor` being a single-threaded executor creates a bottleneck when multiple subscriptions and async operations share it, (3) batch send's dependency on the legacy `SendQueueMessagesBatch` RPC is still unresolved and blocks implementation, and (4) several test scenarios require infrastructure (running KubeMQ server, mock gRPC) that is not specified.

**Finding Counts:**

| Severity | Count |
|----------|-------|
| BLOCKER | 1 |
| MAJOR | 4 |
| MINOR | 7 |
| NIT | 3 |

---

## 1. R1 Fix Verification

All 20 R1 fixes verified against the updated specs:

| R1 Issue | Fix Claimed | Verified? | Notes |
|----------|-------------|-----------|-------|
| C-13-1 (CRITICAL): decode pattern | Added Javadoc documenting pattern choice; confirmed `getResultsList()` | YES | Javadoc in Section 6.3.3 clearly documents the decode pattern choice and proto accessor. |
| C-10-1 (MAJOR): `@Immutable` misapplied | Replaced with Javadoc-only | YES | Section 3.3.4 now uses Javadoc "safe to read" instead of `@Immutable` annotation. Explanation of why `@Immutable` is inappropriate is present. |
| C-10-2 (MAJOR): `unwrapException` + cancellation | Fixed to wrap in `KubeMQException`; added unary vs streaming section; added RPC mapping table | YES | Section 4.3.7 now returns `KubeMQException` instead of `RuntimeException`. Section 4.3.6 includes the mapping table and unary/streaming distinction. Forward reference to `inFlightOperations` added. |
| C-11-1 / C-12-1 (MAJOR): duplicate SDK version | `CompatibilityConfig.SDK_VERSION` delegates to `KubeMQVersion.getVersion()` | YES | Section 3.3 in spec 12 shows `SDK_VERSION = KubeMQVersion.getVersion()`. Dependency noted in Javadoc and cross-category tables. |
| C-13-2 (MAJOR): legacy batch RPC | Added pre-implementation verification + fallback streaming approach | YES | Section 6.3.2 includes fallback code and blocking verification task. |
| X-1 (MAJOR): CI workflow overlap | Spec 04 designated canonical owner; specs 11/12 reference only | YES | Sections 5.3 (spec 11) and 5.3 (spec 12) now state requirements only, no full YAML. |
| C-10-3 (MINOR): callbackExecutor undefined | Added note before async methods; defined in KubeMQClient Section 5.3.3 | YES | Note in Section 4.3.2 and definition in Section 5.3.3. |
| C-10-4 (MINOR): inFlightOperations forward ref | Added forward reference | YES | Note in Section 4.3.6 mapping table. |
| C-10-5 (MINOR): JSR-305 choice | Added note about alternatives | YES | Section 3.3.1 mentions `org.jetbrains:annotations` as alternative. |
| C-10-6 (NIT): sendEventsMessageAsync Javadoc | Revised to clarify stream write vs server ack | YES | Section 4.3.2 Javadoc now says "completes after the message is written to the gRPC stream." |
| C-11-2 (MINOR): release name vv prefix | Changed to `"Release ${{ github.ref_name }}"` | YES | Section 5.2 in spec 11 shows corrected template. |
| C-11-3 (MINOR): CI overlap | Merged with X-1 | YES | See X-1 above. |
| C-12-2 (MINOR): pre-release version handling | Added Javadoc to parseVersion | YES | Section 3.3 `parseVersion()` Javadoc documents pre-release stripping behavior. |
| C-12-3 (MINOR): lazy check thread safety | Replaced with `AtomicBoolean.compareAndSet` | YES | Section 3.3 shows exact `compareAndSet` pattern. |
| C-12-4 (MINOR): OWASP threshold | Changed to CVSS 7 | YES | Section 6.5 shows `failBuildOnCVSS>7`. |
| C-12-5 (NIT): matrix vs MAX_SERVER_VERSION | Added clarifying notes | YES | COMPATIBILITY.md notes explain relationship. |
| C-12-6 (MINOR): CycloneDX version | Added verification comment | YES | XML comment added. |
| C-13-4 (MINOR): BenchmarkConfig property name | Changed to `kubemq.address` | YES | Section 3.4.5 uses `kubemq.address`. |
| C-13-5 (NIT): MAX_BATCH_SIZE docs | Elevated to implementation text | YES | Section 6.3.4 documents as client-side guard. |
| X-3/X-4 (MINOR): duplicate CHANGELOG/CONTRIBUTING | Spec 12 references spec 11 as canonical | YES | Sections 4.2 and 4.4 in spec 12 reference spec 11. |

**R1 Fix Verdict: ALL 20 VERIFIED. 2 intentionally skipped (C-13-3, C-11-4) -- acknowledged and acceptable.**

---

## 2. Spec-by-Spec Implementability Review

### 2.1 Category 10: Concurrency & Thread Safety

#### Implementability Assessment

| REQ | Implementable? | Confidence | Key Risk |
|-----|---------------|------------|----------|
| CONC-1 | YES | High | Mechanical work. No risk. |
| CONC-2 + CONC-4 | YES with caveats | Medium | Massive scope (27+ methods); heavy dependency on specs 01/02 |
| CONC-3 | YES | High | Behavioral change (callback thread) needs careful testing |
| CONC-5 | YES | Medium | Integration with specs 01/02 close() logic |

#### Findings

| ID | Severity | Finding |
|----|----------|---------|
| I-10-1 | **MAJOR** | **Single-threaded `defaultCallbackExecutor` is a bottleneck for async operations.** Section 5.3.3 defines the default callback executor as `Executors.newSingleThreadExecutor()`. However, Section 4.3.2 uses `callbackExecutor()` for ALL async methods -- not just subscription callbacks. This means `sendEventsMessageAsync()`, `createEventsChannelAsync()`, `sendCommandRequestAsync()`, etc. ALL execute on a single thread. With 27+ async methods potentially called concurrently, this serializes all operations. **Impact:** An async-first API that runs everything on one thread defeats the purpose. The GS says "Async APIs don't block the calling thread" -- which is technically met (the calling thread returns immediately), but throughput is limited to one operation at a time. **Recommendation:** Use a bounded thread pool (e.g., `Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())`) as the default for async operations. Keep the single-threaded executor only as the default for subscription callbacks (where sequential processing is the desired default). Differentiate `asyncOperationExecutor()` from `callbackExecutor()`. |
| I-10-2 | **MAJOR** | **Async API scope is XL but lacks sub-phasing.** The spec estimates 5-8 days for 27+ new async methods, Duration overloads, Subscription handles, cancellation propagation, exception unwrapping, and refactoring existing internals. This is a single monolithic phase. If the implementer hits a blocking issue (e.g., `CQClient.sendCommandRequestAsync()` requires converting blocking stubs to async stubs), the entire phase stalls. **Recommendation:** Split Phase 3 into sub-phases: (A) expose existing internal async methods (QueuesClient -- already has internal CompletableFuture), (B) add async for PubSub (already has internal stream mechanism), (C) add async for CQ (requires converting blocking stubs to async stubs -- biggest effort), (D) add Duration overloads and Subscription handles. This lets you ship incrementally. |
| I-10-3 | **MINOR** | **`CQClient.sendCommandRequestAsync()` uses `CompletableFuture.supplyAsync()` with a blocking stub call inside.** Section 4.3.3 wraps `this.getClient().sendRequest(request)` (blocking stub) in `CompletableFuture.supplyAsync(..., callbackExecutor())`. This blocks a thread in the executor while waiting for the gRPC response. It is NOT truly async -- it is "blocking wrapped in a future." The truly async approach uses `this.getAsyncClient().sendRequest()` with a `StreamObserver` callback. **Impact:** With the single-threaded default executor (I-10-1), this completely serializes CQ operations. Even with a thread pool, it wastes threads. **Recommendation:** For CONC-4 compliance ("Async APIs don't block the calling thread"), the CQ async methods should use the async gRPC stub. Document this as the most complex part of the async migration: it requires creating `StreamObserver`-to-`CompletableFuture` adapters for unary RPCs. Provide a utility method: `protected <T> CompletableFuture<T> unaryCallToFuture(Consumer<StreamObserver<T>> grpcCall)`. |
| I-10-4 | **MINOR** | **`EventStreamHelper.sendEventStoreMessageAsync()` synchronizes on `this` for handler initialization, but `sendEventMessage()` uses `synchronized void sendEventMessage(...)`.** Both synchronize, but on different monitors: `synchronized(this)` block vs `synchronized` method modifier. Since they are the same instance, these are actually the same monitor. However, the spec shows two different synchronization styles in the same class, which is confusing during implementation. **Recommendation:** Use consistent synchronization style: either both as `synchronized(this)` blocks, or both as synchronized methods. |
| I-10-5 | **MINOR** | **`Subscription.cancelAsync()` uses `CompletableFuture.runAsync(this::cancel)` with the default ForkJoinPool.** Unlike all other async methods that use `callbackExecutor()`, this one uses the default `ForkJoinPool.commonPool()`. This is inconsistent and could be surprising in environments where the ForkJoinPool is restricted (e.g., application servers). **Recommendation:** Accept an `Executor` in the `Subscription` constructor or use `CompletableFuture.runAsync(this::cancel, executor)` with a provided executor. |
| I-10-6 | **MINOR** | **The `close()` method in Section 7.3.4 references `connectionStateMachine`, `reconnectionManager`, and `messageBuffer` from spec 02.** These do not exist in the current codebase. If spec 10 is implemented before spec 02, the `close()` method cannot be written as specified. **Recommendation:** The spec already notes the dependency on spec 02, but the close() code example mixes spec-02 constructs with spec-10 constructs. Provide a "standalone" close() that works without spec-02 constructs (just the callback waiting + executor shutdown + channel shutdown), with `// TODO: integrate with ConnectionStateMachine from spec 02` markers for the reconnection and buffer flush logic. |
| I-10-7 | **NIT** | **Thread name `"kubemq-callback"` is singular.** If users create multiple clients (e.g., one for pub/sub, one for queues), each gets its own executor with the same thread name. Use `"kubemq-callback-" + clientId` or an incrementing counter for debuggability. |

#### Missing Implementation Details

1. **No guidance on how `waiting()` and `pull()` async variants work.** These methods on `QueuesClient` use blocking stubs (`getClient().receiveQueueMessages()` and `getClient().ackAllQueueMessages()`) but are listed in the async API table (Section 6.3). Same issue as CQ -- wrapping blocking stubs in supplyAsync is not truly async.

2. **No test infrastructure for async tests.** Testing `CompletableFuture` completion, cancellation propagation, and timeout behavior requires either a real gRPC server or a mock. The spec lists 15 test scenarios for CONC-2 but does not specify whether they use mocking (Mockito), an in-process gRPC server (`grpc-testing`), or a live server. Recommendation: add `io.grpc:grpc-testing` as a test dependency and use `InProcessServerBuilder` for async tests.

#### Test Feasibility

| Test | Feasible? | Notes |
|------|-----------|-------|
| T1-T3 (annotations present) | YES | Reflective tests, straightforward |
| T4-T5 (concurrent sends) | YES with server | Requires running KubeMQ or mock |
| T6 (race condition fix) | YES | Can test with concurrent threads and assertion on handler count |
| CONC-2 T1-T9 (async methods) | YES with mock | Need `grpc-testing` for mock server |
| CONC-2 T10-T12 (subscription handles) | PARTIAL | Need streaming mock for subscription |
| CONC-2 T13-T15 (closed client, concurrent, timeout) | YES | Unit testable |
| CONC-3 T1-T8 (callback executor) | YES | Unit testable with latches and executors |
| CONC-5 T1-T14 (shutdown) | PARTIAL | T8-T10 require spec-02 constructs that don't exist yet |

---

### 2.2 Category 11: Packaging & Distribution

#### Implementability Assessment

| REQ | Implementable? | Confidence | Key Risk |
|-----|---------------|------------|----------|
| PKG-1 | YES | High | Mechanical. Git log for dates. |
| PKG-2 | YES | High | Standard Maven resource filtering. Well-specified. |
| PKG-3 | YES | Medium | Requires GitHub Secrets configuration -- not a code issue. |
| PKG-4 | YES | High | Documentation only. |

#### Findings

| ID | Severity | Finding |
|----|----------|---------|
| I-11-1 | **MINOR** | **`KubeMQVersion` class placed in `io.kubemq.sdk.common` but the `common` package currently contains utility/model classes (`ChannelDecoder`, `KubeMQUtils`, `RequestType`, `ServerInfo`, `SubscribeType`).** This is a reasonable location, but `KubeMQVersion` is fundamentally different from these (metadata vs domain logic). **Recommendation:** Accept as-is. The `common` package is the right place for cross-cutting concerns. No action needed. |
| I-11-2 | **MINOR** | **Maven resource filtering may break other resource files.** The spec correctly uses a two-`<resource>` block approach (filtering=true for version properties, filtering=false for everything else). However, the current `pom.xml` has no `<resources>` section at all, which means Maven uses the default (`src/main/resources` with no filtering). Adding the explicit `<resources>` block changes the behavior -- but only for the version properties file. Verify that no other `.properties` files in `src/main/resources/` contain `${...}` tokens that would be accidentally filtered. **Current state:** No `src/main/resources/` directory exists, so this is a non-issue for the initial implementation. The spec creates the directory fresh. |
| I-11-3 | **NIT** | **`release.yml` workflow places `.github/workflows/release.yml` under `kubemq-java/.github/`.** The `.github/` directory for GitHub Actions must be at the repository root, not inside the `kubemq-java/` subdirectory. Looking at the repository structure, `kubemq-java/` IS the repository root (it's the Maven project). However, the git repo root is actually `kubemq-java-v2/` (one level up). The workflow file path should be `kubemq-java-v2/.github/workflows/release.yml`, and `working-directory: kubemq-java` handles the Maven execution context. **Impact:** If placed at `kubemq-java/.github/workflows/`, GitHub will not detect it. **Recommendation:** Clarify that `.github/workflows/release.yml` is at the git repository root (`kubemq-java-v2/.github/workflows/release.yml`), with `working-directory: kubemq-java` in each step that runs Maven commands. The spec's file manifest says `kubemq-java/.github/workflows/release.yml` -- this needs correction. |

#### Missing Implementation Details

None significant. The spec is well-detailed for implementation.

#### Test Feasibility

| Test | Feasible? | Notes |
|------|-----------|-------|
| KubeMQVersionTest | YES | Only concern: version returns "unknown" in IDE without Maven build. Spec handles this with conditional assertion. |
| CI/CD pipeline validation | YES | Dry run on fork as described. |
| CHANGELOG validation | YES | Manual checklist. |

---

### 2.3 Category 12: Compatibility, Lifecycle & Supply Chain

#### Implementability Assessment

| REQ | Implementable? | Confidence | Key Risk |
|-----|---------------|------------|----------|
| COMPAT-1 | YES | High | Clean implementation. CompatibilityConfig is simple utility. |
| COMPAT-2 | YES | High | Documentation/policy only. |
| COMPAT-3 | YES | High | README fix is trivial. CI depends on spec 04. |
| COMPAT-4 | YES | Medium | OWASP plugin version 10.0.3 needs verification; CycloneDX 2.8.0 needs verification. |
| COMPAT-5 | YES | High | Documentation only. |

#### Findings

| ID | Severity | Finding |
|----|----------|---------|
| I-12-1 | **MINOR** | **`CompatibilityConfig.SDK_VERSION` is a `static final` field initialized from `KubeMQVersion.getVersion()`.** Since `KubeMQVersion.getVersion()` reads from a properties file at class load time, this works. However, `static final String SDK_VERSION = KubeMQVersion.getVersion()` means `SDK_VERSION` is NOT a compile-time constant. It cannot be used in `switch` cases or annotation values. This is fine for the version check use case, but the Javadoc should not suggest it can be used as a compile-time constant. The spec's `KubeMQVersion` Javadoc already notes this limitation -- good. |
| I-12-2 | **MINOR** | **OWASP `dependency-check-maven:10.0.3` -- verify this version exists.** The OWASP Dependency-Check plugin had version 9.x as of late 2024; 10.0.3 may or may not exist as of March 2026. **Recommendation:** At implementation time, check Maven Central for the latest stable version. The spec already has a comment about this from R1 fix C-12-6. This is just a reminder that the implementer must verify. |
| I-12-3 | **NIT** | **`checkCompatibilityOnce()` uses a double-check pattern: `if (!compatibilityChecked.get() && compatibilityChecked.compareAndSet(false, true))`.** The `get()` before `compareAndSet()` is a minor optimization (avoids the CAS overhead on subsequent calls) but is not strictly necessary -- `compareAndSet(false, true)` alone is sufficient and simpler. **Recommendation:** Accept as-is; the optimization is harmless and the pattern is documented. |

#### Missing Implementation Details

1. **No specification for where `checkCompatibilityOnce()` is called.** The spec says "Call at the beginning of `ping()`, `send*()`, or `subscribe*()` methods" but does not specify the exact integration point. Since `KubeMQClient` is abstract and the subclasses override nothing related to connection init, the implementer must add the call to every public method in `PubSubClient`, `CQClient`, and `QueuesClient` -- or add it to a shared entry point. **Recommendation:** Add a note suggesting the implementer put it in `ensureNotClosed()` (from spec 02) since that method is already called at the start of every public operation. One line: `checkCompatibilityOnce(); ensureNotClosed();` Or if spec 02 is not yet implemented, add a `preOperationCheck()` method to `KubeMQClient` that subclasses call.

2. **Dependency audit action items (grpc-alts, commons-lang3, jackson-databind) are listed but have no acceptance criteria.** The spec says "evaluate removal" but does not define what the outcome should be. Is the audit complete when a comment is added to pom.xml, or when the dependency is actually removed? **Recommendation:** Define the done-criteria: "Each dependency has either (a) a justification comment in pom.xml, or (b) been removed. Removal is a separate PR."

#### Test Feasibility

| Test | Feasible? | Notes |
|------|-----------|-------|
| CompatibilityConfigTest (13 cases) | YES | Pure unit tests, no dependencies. Straightforward. |
| Integration test for version check logging | PARTIAL | Requires either a live server or mocked `ping()`. Can be done with Mockito by mocking the `KubeMQClient.ping()` method in a subclass test. |
| CI matrix (Java 11/17/21) | YES | Standard GitHub Actions. |

---

### 2.4 Category 13: Performance

#### Implementability Assessment

| REQ | Implementable? | Confidence | Key Risk |
|-----|---------------|------------|----------|
| PERF-1 | YES | Medium | JMH setup straightforward; benchmarks require live KubeMQ server |
| PERF-2 | YES | High | Doc-only change |
| PERF-3 | YES | High | Doc-only change |
| PERF-4 | BLOCKED | Low | Legacy RPC support unverified |
| PERF-5 | YES | High | Doc-only change |
| PERF-6 | YES | High | Doc-only change |

#### Findings

| ID | Severity | Finding |
|----|----------|---------|
| I-13-1 | **BLOCKER** | **Batch send implementation is blocked until `SendQueueMessagesBatch` RPC is verified with the server team.** The spec correctly identifies this as a pre-implementation verification task (Section 6.3.2). However, the fallback approach (N writes over existing `QueuesUpstream` stream) is sketched but not fully specified. The fallback calls `sendQueuesMessageAsync(message)` for each message, but `sendQueuesMessageAsync` is private in the current code (see `QueueUpstreamHandler.java` line 163). The fallback code also references `sendQueuesMessageAsync` (singular, not the public version from CONC-4) and `QueueSendResult` in a `CompletableFuture`, but the batch response type `QueueMessagesBatchResponse` has different fields than what the streaming fallback would produce. **Impact:** The implementer cannot start PERF-4 until the RPC is verified, and if the fallback is needed, it requires more design work. **Recommendation:** (1) Make the RPC verification a day-1 task. (2) If the fallback is needed, provide a complete specification: the streaming batch should return the same `QueueMessagesBatchResponse` type, with `batchId` generated client-side and `haveErrors` computed by scanning results. (3) The fallback should use `queueUpstreamHandler.sendQueuesMessageAsync(msg)` -- but this is private. It must be made public (or package-private) per the CONC-4 spec change. Clarify the dependency: PERF-4 fallback depends on CONC-4 visibility change. |
| I-13-2 | **MAJOR** | **JMH benchmarks are integration benchmarks that require a running KubeMQ server, but no guidance is provided for CI or developer setup.** The spec says "Run benchmarks manually for now" (Open Question #2), but a developer picking up this spec will need a running KubeMQ server instance. **Recommendation:** Add a "Developer Setup" section to the benchmark spec: (a) Docker command: `docker run -d -p 50000:50000 kubemq/kubemq:latest`, (b) note that benchmarks are excluded from `mvn test` by default (only run with `-Pbenchmark`), (c) consider adding a `@Tag("benchmark")` to JUnit or a Maven skip property so benchmarks never run accidentally in CI. |
| I-13-3 | **MAJOR** | **JMH `exec-maven-plugin` approach may not work as specified.** The spec uses `exec-maven-plugin` to invoke `java -classpath ... org.openjdk.jmh.Main`. However, JMH requires annotation processing at compile time to generate benchmark handler classes. With `exec-maven-plugin`, the classpath may not include the generated benchmark code correctly. The standard JMH approach uses `maven-shade-plugin` to create an uber-jar with a `META-INF/BenchmarkList` file. **Impact:** The benchmarks may not compile or run as specified. **Recommendation:** Either (a) use the standard JMH approach with `maven-shade-plugin` in the benchmark profile to create `benchmarks.jar`, or (b) use `jmh-maven-plugin` (org.openjdk.jmh:jmh-maven-plugin) which handles the compilation and execution correctly. The invocation would change to `java -jar target/benchmarks.jar`. Document this in the spec. |
| I-13-4 | **MINOR** | **`PublishThroughputBenchmark` calls `client.sendEventsMessage(message)` which is void (fire-and-forget).** JMH's `@Benchmark` methods should either return a value or use a `Blackhole` to prevent dead-code elimination. A void benchmark that calls a void method could be optimized away by the JIT compiler if JMH cannot detect the side effect. **Recommendation:** Use `Blackhole.consume()` or change the benchmark to measure `sendEventsStoreMessage()` (which returns a result) for the throughput test. The current spec has a separate `PublishLatencyBenchmark` for EventStore; the throughput benchmark should also be EventStore-based for measurability. |
| I-13-5 | **NIT** | **`QueueRoundtripBenchmark` creates a new `QueueMessage` in every `@Benchmark` iteration.** Object allocation in the benchmark loop adds noise to measurements. Pre-allocate the message payload in `@Setup` and reuse the byte array (the message builder creates a new object each time anyway, but the payload allocation can be hoisted). This is a minor JMH best practice. |

#### Missing Implementation Details

1. **No `QueueSendResult.decode(SendQueueMessageResult)` -- the existing method is an instance method that mutates `this`.** The spec's `QueueMessagesBatchResponse.decode()` calls `new QueueSendResult().decode(pbResult)`. This works (verified by reading `QueueSendResult.java` -- it has `@NoArgsConstructor` and the instance `decode()` returns `this`). However, the Javadoc in the spec correctly notes this is architecturally inconsistent. For implementation, this is fine as-is -- no issue.

2. **No guidance on whether `BENCHMARKS.md` TBD values block the PR.** Can the benchmarks be merged with `[TBD]` placeholders, or must actual numbers be populated first? **Recommendation:** Merge with `[TBD]` -- the infrastructure (JMH classes + Maven profile) is the deliverable. Actual numbers are populated in a follow-up after running on reference hardware.

#### Test Feasibility

| Test | Feasible? | Notes |
|------|-----------|-------|
| QueueMessagesBatchResponseTest | YES | Pure unit test with protobuf builders |
| QueuesClientBatchTest | YES | Validation logic is testable without server |
| Integration batch tests | BLOCKED | Depends on RPC verification |
| Benchmark compilation | YES | `mvn test-compile -Pbenchmark` |
| Benchmark execution | REQUIRES SERVER | Need running KubeMQ instance |

---

## 3. Cross-Spec Implementability Issues

| ID | Severity | Finding |
|----|----------|---------|
| X-R2-1 | **MAJOR** | **Specs 10, 11, 12, and 13 all depend on specs 01 and 02 being implemented first, but the dependency chain is circular for testing.** Spec 10 (CONC-5 close()) depends on spec 02 (ConnectionStateMachine). Spec 10 (CONC-2 async) depends on spec 01 (error types). Spec 12 (COMPAT-1) depends on spec 11 (PKG-2 version). These are documented correctly. However, the implementer needs a concrete implementation order across batches, not just within each spec. **Recommendation:** Provide a global implementation sequence in a master document. For Batch 3, the implementable-without-dependencies items are: PKG-1 (CHANGELOG), PKG-2 (KubeMQVersion), PKG-4 (CONTRIBUTING), CONC-1 (annotations), COMPAT-2 (deprecation policy), COMPAT-3 (README fix), COMPAT-5 (EOL policy), PERF-1 (JMH setup), PERF-2/3/5/6 (docs). These can be done TODAY with zero dependencies. |
| X-R2-2 | **MINOR** | **`release.yml` and `dependabot.yml` file paths.** Spec 11 says `kubemq-java/.github/workflows/release.yml`; spec 12 says `.github/dependabot.yml`. The `.github/` directory must be at the git repository root. Since the repo root is `kubemq-java-v2/` (not `kubemq-java/`), the correct paths are: `kubemq-java-v2/.github/workflows/release.yml` and `kubemq-java-v2/.github/dependabot.yml`. Both specs should use repository-root-relative paths for `.github/` files. |

---

## 4. Commit Sequences

### 4.1 Spec 10: Concurrency & Thread Safety

```
Commit 1: CONC-1 -- Thread safety annotations and Javadoc
  Files: KubeMQClient.java, PubSubClient.java, CQClient.java, QueuesClient.java,
         EventMessage.java, EventStoreMessage.java, QueueMessage.java,
         CommandMessage.java, QueryMessage.java, QueuesPollRequest.java,
         EventMessageReceived.java, EventStoreMessageReceived.java,
         QueueMessageReceived.java, CommandMessageReceived.java,
         QueryMessageReceived.java, EventSendResult.java, QueueSendResult.java,
         EventsSubscription.java, EventsStoreSubscription.java,
         CommandsSubscription.java, QueriesSubscription.java,
         EventStreamHelper.java, QueueUpstreamHandler.java,
         pom.xml (JSR-305 dep)
  Tests: ThreadSafetyAnnotationTest (reflective)
  Deps: None

Commit 2: CONC-1 fix -- EventStreamHelper race condition
  Files: EventStreamHelper.java (add synchronized to sendEventMessage)
  Tests: EventStreamHelperConcurrencyTest
  Deps: None

Commit 3: CONC-3 -- Callback executor infrastructure
  Files: KubeMQClient.java (defaultCallbackExecutor, callbackExecutor()),
         EventsSubscription.java (callbackExecutor field, maxConcurrentCallbacks,
           dispatch via executor + semaphore),
         EventsStoreSubscription.java (same pattern),
         CommandsSubscription.java (same pattern),
         QueriesSubscription.java (same pattern)
  Tests: CallbackExecutorTest, CallbackConcurrencyTest
  Deps: None

Commit 4: CONC-5 -- Shutdown safety (standalone, without spec 02)
  Files: KubeMQClient.java (inFlightOperations, callbackCompletionTimeoutSeconds,
         closed AtomicBoolean, ensureNotClosed(), enhanced close())
  Tests: ShutdownSafetyTest, IdempotentCloseTest, PostCloseOperationTest
  Deps: None (spec 02 integration done later)

Commit 5: CONC-2/CONC-4 Phase A -- Queue async (expose existing internals)
  Files: QueueUpstreamHandler.java (sendQueuesMessageAsync visibility),
         QueueDownstreamHandler.java (receiveQueuesMessagesAsync visibility),
         QueuesClient.java (async methods + Duration overloads)
  Tests: QueuesClientAsyncTest
  Deps: Commit 4 (ensureNotClosed)

Commit 6: CONC-2/CONC-4 Phase B -- PubSub async
  Files: EventStreamHelper.java (sendEventStoreMessageAsync public),
         PubSubClient.java (async methods + Duration overloads),
         Subscription.java (new class)
  Tests: PubSubClientAsyncTest, SubscriptionTest
  Deps: Commit 4

Commit 7: CONC-2/CONC-4 Phase C -- CQ async (requires async stub conversion)
  Files: CQClient.java (async methods using async stub + Duration overloads)
  Tests: CQClientAsyncTest
  Deps: Commit 4

Commit 8: CONC-2 -- Exception unwrapping utilities
  Files: KubeMQClient.java (unwrapFuture, unwrapException, executeWithCancellation)
  Tests: ExceptionUnwrappingTest
  Deps: Spec 01 error types (or use placeholder RuntimeException)
```

### 4.2 Spec 11: Packaging & Distribution

```
Commit 1: PKG-1 + PKG-4 -- CHANGELOG and CONTRIBUTING
  Files: CHANGELOG.md (new), CONTRIBUTING.md (new)
  Tests: None (documentation)
  Deps: None

Commit 2: PKG-2 -- Runtime version constant
  Files: pom.xml (resources block, url fix),
         src/main/resources/kubemq-sdk-version.properties (new),
         KubeMQVersion.java (new),
         KubeMQVersionTest.java (new)
  Tests: KubeMQVersionTest
  Deps: None

Commit 3: PKG-3 -- Release pipeline
  Files: .github/workflows/release.yml (new, at repo root)
  Tests: Manual dry run on fork
  Deps: GitHub Secrets configuration
```

### 4.3 Spec 12: Compatibility, Lifecycle & Supply Chain

```
Commit 1: COMPAT-3 -- Fix README Java version
  Files: README.md (JDK 8 -> JDK 11, version support section)
  Tests: None (documentation)
  Deps: None

Commit 2: COMPAT-1 -- Compatibility matrix + version check
  Files: COMPATIBILITY.md (new),
         CompatibilityConfig.java (new),
         KubeMQClient.java (checkServerCompatibility, checkCompatibilityOnce),
         CompatibilityConfigTest.java (new),
         README.md (add compatibility link)
  Tests: CompatibilityConfigTest (13 cases)
  Deps: PKG-2 (KubeMQVersion) from spec 11

Commit 3: COMPAT-2 -- Deprecation policy + MIGRATION.md
  Files: CONTRIBUTING.md (add deprecation section, references spec 11 version),
         MIGRATION.md (new)
  Tests: None (documentation)
  Deps: CONTRIBUTING.md from spec 11 Commit 1

Commit 4: COMPAT-4 -- Supply chain security
  Files: .github/dependabot.yml (new, at repo root),
         pom.xml (CycloneDX plugin, OWASP plugin, BOM, dep audit comments),
         dependency-check-suppression.xml (new)
  Tests: mvn dependency-check:check, mvn package (SBOM generation)
  Deps: None

Commit 5: COMPAT-5 -- EOL policy
  Files: README.md (version support policy section)
  Tests: None (documentation)
  Deps: None
```

### 4.4 Spec 13: Performance

```
Commit 1: PERF-4 -- Batch send (after RPC verification)
  Files: QueuesClient.java (sendQueuesMessages, validateBatch, MAX_BATCH_SIZE),
         QueueUpstreamHandler.java (sendQueuesMessagesBatch),
         QueueMessagesBatchResponse.java (new),
         QueueMessagesBatchResponseTest.java (new),
         QueuesClientBatchTest.java (new)
  Tests: Unit tests for validation and decode
  Deps: RPC verification with server team

Commit 2: PERF-1 -- JMH benchmark infrastructure
  Files: pom.xml (JMH deps, benchmark profile with shade/jmh plugin),
         PublishThroughputBenchmark.java (new),
         PublishLatencyBenchmark.java (new),
         QueueRoundtripBenchmark.java (new),
         ConnectionSetupBenchmark.java (new),
         BenchmarkConfig.java (new),
         BENCHMARKS.md (new, with TBD values)
  Tests: mvn test-compile -Pbenchmark (compilation only)
  Deps: None for infra; live server for execution

Commit 3: PERF-2/3/5/6 -- Performance documentation
  Files: README.md (Performance section, Performance Tips, Known Limitations)
  Tests: None (documentation)
  Deps: PERF-4 (for batch send references in docs)
```

---

## 5. Global Implementation Order (Batch 3, No-Dependency-First)

Items that can be implemented immediately (zero cross-batch dependencies):

| Order | Item | Spec | Effort | Deps |
|-------|------|------|--------|------|
| 1 | CHANGELOG.md + CONTRIBUTING.md | 11 | 0.5d | None |
| 2 | KubeMQVersion (resource filtering) | 11 | 0.5d | None |
| 3 | README fix (JDK 8 -> 11) | 12 | 0.5h | None |
| 4 | Thread safety annotations + Javadoc | 10 | 0.5d | None |
| 5 | EventStreamHelper race condition fix | 10 | 0.5h | None |
| 6 | EOL policy in README | 12 | 0.5h | None |
| 7 | COMPATIBILITY.md + CompatibilityConfig | 12 | 1.5d | Item 2 |
| 8 | Deprecation policy + MIGRATION.md | 12 | 0.5d | Item 1 |
| 9 | Supply chain (Dependabot, CycloneDX, OWASP) | 12 | 1d | None |
| 10 | Performance docs (README sections) | 13 | 0.5d | None |

Items requiring cross-batch dependencies or verification:

| Order | Item | Spec | Effort | Blocker |
|-------|------|------|--------|---------|
| 11 | Callback executor infrastructure | 10 | 1.5d | None (can start) |
| 12 | Shutdown safety | 10 | 2d | Spec 02 for full integration |
| 13 | Batch send | 13 | 1.5d | RPC verification |
| 14 | JMH benchmarks | 13 | 2d | Running KubeMQ server |
| 15 | Async API (Queues) | 10 | 2d | Spec 01 error types |
| 16 | Async API (PubSub) | 10 | 2d | Spec 01 error types |
| 17 | Async API (CQ) | 10 | 3d | Spec 01 error types + async stub conversion |
| 18 | Release pipeline | 11 | 1d | GitHub Secrets |

**Total: Items 1-10 = ~5.5 days of unblocked work. Items 11-18 = ~15 days with dependencies.**

---

## 6. Summary of Action Items

### Must-Fix Before Implementation

| Priority | ID | Action |
|----------|-----|--------|
| BLOCKER | I-13-1 | Verify `SendQueueMessagesBatch` RPC with server team. Fully specify the streaming fallback if legacy RPC is unavailable. |
| MAJOR | I-10-1 | Change `defaultCallbackExecutor` to a bounded thread pool for async operations. Keep single-thread executor for subscription callbacks only. |
| MAJOR | I-10-2 | Split CONC-2/CONC-4 Phase 3 into sub-phases A/B/C/D for incremental delivery. |
| MAJOR | I-13-3 | Fix JMH Maven integration to use `maven-shade-plugin` or `jmh-maven-plugin` instead of `exec-maven-plugin`. |
| MAJOR | X-R2-1 | Provide concrete global implementation order showing which items are immediately implementable. (Provided above in Section 5.) |

### Should-Fix

| Priority | ID | Action |
|----------|-----|--------|
| MINOR | I-10-3 | Document that CQ async methods should use async gRPC stubs for true non-blocking behavior, not `supplyAsync` with blocking stubs. |
| MINOR | I-10-5 | `Subscription.cancelAsync()` should accept an executor parameter or use the client's executor. |
| MINOR | I-10-6 | Provide standalone close() without spec-02 constructs, with TODO markers. |
| MINOR | I-11-3 / X-R2-2 | Correct `.github/` file paths to repository root (not `kubemq-java/`). |
| MINOR | I-12-2 | Verify OWASP plugin version at implementation time. |
| MINOR | I-13-2 | Add Docker setup instructions for benchmark execution. |
| MINOR | I-13-4 | Use `Blackhole.consume()` in fire-and-forget throughput benchmarks. |

### Nice-to-Have

| Priority | ID | Action |
|----------|-----|--------|
| NIT | I-10-4 | Use consistent synchronization style in EventStreamHelper. |
| NIT | I-10-7 | Include clientId in callback thread name for debuggability. |
| NIT | I-13-5 | Pre-allocate benchmark payload in `@Setup`. |

---

## Fixes Applied (Round 2)

| Issue | Status | Change Made |
|-------|--------|-------------|
| I-13-1 (BLOCKER) | FIXED | Spec 13 Section 6.3.2: Fully specified streaming fallback with complete error handling (TimeoutException, ExecutionException), client-side batchId generation, haveErrors computation. Added explicit dependency note that PERF-4 fallback depends on CONC-4 visibility change. Made RPC verification a day-1 task. |
| I-10-1 (MAJOR) | FIXED | Spec 10 Section 5.3.3: Split single `defaultCallbackExecutor` into two executors: `defaultCallbackExecutor` (single-threaded, for subscription callbacks) and `defaultAsyncOperationExecutor` (bounded thread pool sized to `availableProcessors()`, for async operations). Added `asyncOperationExecutor()` accessor. Updated all async method examples to use `asyncOperationExecutor()` instead of `callbackExecutor()`. Updated `executeWithCancellation()` to use `asyncOperationExecutor()`. Updated `close()` to shut down both executors. |
| I-10-2 (MAJOR) | FIXED | Spec 10 Section 2: Split Phase 3 into Sub-phases A (Queue async -- expose existing internals), B (PubSub async -- stream mechanism), C (CQ async -- async stub conversion, most complex), D (Duration overloads + exception unwrapping). Each sub-phase is independently shippable. Added rationale for sub-phasing. |
| I-13-3 (MAJOR) | FIXED | Spec 13 Section 3.4.2: Replaced `exec-maven-plugin` with `maven-shade-plugin` to create `benchmarks.jar` (standard JMH approach). Updated invocation to `java -jar target/benchmarks.jar`. Added developer setup section with Docker command. Updated verification section and BENCHMARKS.md template to match new approach. |
| X-R2-1 (MAJOR) | FIXED | Review Section 5 already provides the global implementation order. Spec 10 Phase 3 sub-phasing enables incremental delivery. No additional document needed as the review itself serves as the master sequencing reference for Batch 3. |
| I-10-3 (MINOR) | FIXED | Spec 10 Section 4.3.3: Added implementation notes documenting that CQ async methods use blocking stubs wrapped in `supplyAsync` as interim approach, and should be converted to async gRPC stubs in Sub-phase C. Added `unaryCallToFuture()` utility adapter code for `StreamObserver`-to-`CompletableFuture` conversion. |
| I-10-5 (MINOR) | FIXED | Spec 10 Section 4.3.5: Added `Executor` field and two-argument constructor to `Subscription` class. `cancelAsync()` now uses the provided executor instead of `ForkJoinPool.commonPool()`. All `subscribeToXxxWithHandle()` methods pass `asyncOperationExecutor()` to the Subscription constructor. |
| I-10-6 (MINOR) | FIXED | Spec 10 Section 7.3.4: Replaced spec-02 constructs in `close()` with TODO markers. Steps 2, 5, and 7 now have commented-out spec-02 code with `// TODO: Integrate with ... from spec 02 when implemented` markers. Standalone close() works without spec-02: idempotent guard, in-flight wait, executor shutdown, gRPC channel shutdown. |
| I-11-3 / X-R2-2 (MINOR) | FIXED | Spec 11: Corrected `.github/workflows/release.yml` path from `kubemq-java/.github/` to repo root `.github/`. Updated file manifest. Added note that GitHub only detects workflows at repo root. Spec 12: Corrected `.github/dependabot.yml` path with same clarification. |
| I-13-2 (MINOR) | FIXED | Spec 13 Section 3.4.2: Added developer setup section with Docker command (`docker run -d -p 50000:50000 kubemq/kubemq:latest`), note that benchmarks are excluded from `mvn test` by default, and that the `benchmark` profile is opt-in only. |
| I-13-4 (MINOR) | FIXED | Spec 13 Section 3.4.4 Benchmark 1: Added `Blackhole` parameter to `publishEvent(Blackhole bh)` method and `bh.consume(true)` call to prevent JIT dead-code elimination of the void `sendEventsMessage()` call. |
| I-10-7 (NIT) | FIXED | Spec 10 Section 5.3.3: Thread names now include clientId: `"kubemq-callback-" + getClientId()` and `"kubemq-async-" + getClientId()` for debuggability when multiple clients exist. |
| I-13-5 (NIT) | FIXED | Spec 13 Section 3.4.4 Benchmark 1: Added comment in `@Setup` noting that payload is pre-allocated to avoid allocation noise in the benchmark loop. |
| I-12-missing-1 (MINOR) | FIXED | Spec 12 Section 3.3: Added integration point recommendation for `checkCompatibilityOnce()` -- suggests placing it in `ensureNotClosed()` (if spec 02 is available) or in a new `preOperationCheck()` method on `KubeMQClient`. Includes code example. |
| I-12-missing-2 (MINOR) | FIXED | Spec 12 Section 6.4: Added done-criteria for dependency audit: each dependency has either (a) a justification comment in pom.xml, or (b) been removed in a separate PR. |
| OQ-5 (spec 13) | FIXED | Spec 13 Open Questions: Added question #5 confirming BENCHMARKS.md can be merged with `[TBD]` placeholders; infrastructure is the deliverable, numbers populated in follow-up. |
