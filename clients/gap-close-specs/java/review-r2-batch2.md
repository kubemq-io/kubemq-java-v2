# Java SDK Specs -- Implementability Review (Round 2, Batch 2)

**Reviewer:** Senior Java Developer (Implementer)
**Specs Reviewed:** 04-testing, 05-observability, 06-documentation, 08-api-completeness, 09-api-design-dx
**Date:** 2026-03-09
**Prior Review:** `clients/gap-close-specs/java/review-r1-batch2.md`

---

## 1. Review Summary

| Spec | R1 Verdict | R2 Verdict | Open Issues | Implementable? |
|------|-----------|------------|-------------|----------------|
| 04-testing-spec.md | APPROVE with changes | **APPROVE** | 0 critical, 1 minor | Yes -- ready to implement |
| 05-observability-spec.md | APPROVE with changes | **APPROVE with minor changes** | 0 critical, 3 minor | Yes -- proxy pattern is sound, minor gaps remain |
| 06-documentation-spec.md | APPROVE | **APPROVE** | 0 critical, 1 minor | Yes -- ready to implement |
| 08-api-completeness-spec.md | APPROVE with changes | **APPROVE** | 0 critical, 1 minor | Yes -- ready to implement |
| 09-api-design-dx-spec.md | APPROVE with changes | **APPROVE** | 0 critical, 1 minor | Yes -- ready to implement |

**Overall Assessment:** All R1 fixes were applied correctly. The specs are implementable as written. The remaining minor issues are implementation-time concerns that do not require spec revisions -- they can be resolved by the developer during coding. No blocking issues remain.

---

## 2. R1 Fix Verification

### 2.1 Critical Fixes

| R1 ID | Fix Description | Verified? | Notes |
|-------|----------------|-----------|-------|
| C-1 (05-obs: `provided` scope OTel NoClassDefFoundError) | Added `Tracing`/`Metrics` interfaces, `NoOpTracing`/`NoOpMetrics`, `TracingFactory`/`MetricsFactory` with reflection-based loading | **YES** | Proxy pattern correctly isolates OTel-importing classes. `TracingFactory` uses `Class.forName()` with `NoClassDefFoundError` catch. The `Tracing` interface uses `Object` parameter types to avoid OTel imports in the interface itself. Sound approach. |

### 2.2 Major Fixes

| R1 ID | Fix Description | Verified? | Notes |
|-------|----------------|-----------|-------|
| M-1 (04-test: InProcessServer scope) | Added scope clarification distinguishing server-side vs client-side testing | **YES** | Clear note at Section 3.3.1 explaining three test layers. |
| M-2 (04-test: Closed-client test real channel) | Added TODO markers recommending mock transport or InProcessChannel | **YES** | Acceptable for spec -- developer will use InProcessChannel at implementation time. |
| M-3 (04-test: Thread prefix doesn't exist) | Added prerequisite for named ThreadFactory and fallback to channel/executor state assertions | **YES** | Verified in source: `QueueUpstreamHandler` line 32 already uses `"kubemq-upstream-cleanup"` thread name. The spec's prerequisite is partially met by existing code. |
| M-4 (05-obs: LoggerFactory name collision) | Renamed to `KubeMQLoggerFactory` | **YES** | All references updated correctly. `isSlf4jAvailable()` casing also fixed (n-3). |
| M-5 (05-obs: Slf4jLoggerAdapter loses stack traces) | Added `error(String, Throwable, Object...)` overload | **YES** | Present on `KubeMQLogger`, `NoOpLogger`, and `Slf4jLoggerAdapter`. Adapter delegates to `delegate.error(formattedMsg, cause)`. |
| M-6 (05-obs: LogContextProvider varargs merge) | Added `LogHelper.merge()` utility | **YES** | Implementation shown with `System.arraycopy`. Call site examples updated. |
| M-7 (05-obs: Missing no-op tracing/metrics) | Added `Tracing`/`Metrics` interfaces and `NoOpTracing`/`NoOpMetrics` | **YES** | Interface methods use `Object` types to avoid OTel imports. No-op implementations return sentinel objects. |
| M-8 (06-doc: Lombok Javadoc strategy) | Added `lombok-maven-plugin` delombok config, `maven-javadoc-plugin` sourcepath, Lombok version requirement | **YES** | Complete build integration specified. Method coverage table clarifies manual vs delombok. |
| M-9 (08-api: Batch response missing timestamp cleanup) | Added `requestTimestamps.remove(refRequestID)` in batch onNext path | **YES** | Line 371 in spec now includes the cleanup. UUID uniqueness note added. |
| M-10 (08-api: Binary-breaking subscribe return type) | Added explicit justification, required mitigations (CHANGELOG, migration guide, release notes) | **YES** | Section 7 now documents the binary compatibility concern with clear justification and mitigation checklist. |
| M-11 (09-dx: ValidationException breaks IAE catch) | Added before/after code for migration guide | **YES** | Section 3.2.1 includes code examples and explicit documentation requirements. |
| M-12 (09-dx: Default localhost:50000 silent in prod) | Added `KUBEMQ_ADDRESS` env var fallback and WARN log | **YES** | Section 4.2.1 shows three-tier resolution order with WARN log on default. |

### 2.3 Minor/Nit Fixes

All 13 additional fixes (m-1 through m-13, n-3, X-1 through X-4) verified as applied or appropriately skipped. No issues found with any skipped items.

---

## 3. Implementability Findings

### 3.1 Category 04: Testing Spec

**Verdict: APPROVE -- Ready to implement**

The spec provides a solid test plan with clear phasing. The R1 fixes addressed all architectural concerns.

#### I-1: `QueueUpstreamHandler` already has one named thread (Info)

**Section:** 3.3.5 (Resource Leak Detection)

The spec's R1 M-3 fix states "SDK executors must use named ThreadFactory" as a prerequisite. Verified in source: `QueueUpstreamHandler.java` line 31-36 already has `"kubemq-upstream-cleanup"` thread name. However, `QueueMessageReceived` uses `Executors.newSingleThreadScheduledExecutor()` without a named thread (confirmed no thread name in source). The implementer should name that executor (e.g., `"kubemq-visibility-timer"`) as part of the leak detection test work. This is not a spec deficiency -- the spec correctly identifies the prerequisite.

**Severity:** Info (no spec change needed)

#### I-2: Test class creation can proceed without Batch 1 completion (Info)

**Section:** Phase 2 (Unit Test Gaps)

The spec correctly notes that error classification tests depend on REQ-ERR-1/2/3 from Batch 1. Practically, the CI infrastructure (Phase 1) and non-error-dependent tests (closed-client guards, leak detection, timeout annotations, parameterized config tests) can start immediately. The spec's phasing supports this. Good incremental design.

**Severity:** Info (no spec change needed)

#### I-3: `junit-platform.properties` global timeout is the right approach (Info)

**Section:** R1 m-2 fix

The spec added a note that `junit-platform.properties` with `junit.jupiter.execution.timeout.default` covers all existing tests without per-file retrofit. This is the correct JUnit 5 approach and avoids touching 47 existing test files. The implementer should create `src/test/resources/junit-platform.properties` with the timeout value.

**Severity:** Info (no spec change needed)

---

### 3.2 Category 05: Observability Spec

**Verdict: APPROVE with minor changes**

The proxy pattern for OTel class loading is architecturally sound. The spec is the most complex in Batch 2 but well-structured. Three minor implementability concerns remain.

#### I-4: `TracingFactory` reflection constructor params not fully specified (Minor)

**Section:** 7.3.2 (Proxy/Lazy-Loading Pattern)

The `TracingFactory.create()` example shows `Class.forName("io.kubemq.sdk.observability.KubeMQTracing").getConstructor(/* param types */).newInstance(/* params */)` but the `KubeMQTracing` constructor takes `(TracerProvider, String, String, String, int)`. The factory must know these parameter types at compile time for the `getConstructor()` call. Since `TracerProvider` is an OTel type that may not be on the classpath, the factory cannot reference it directly.

**Practical resolution:** The factory should pass `Object` for the `TracerProvider` parameter, and `KubeMQTracing`'s constructor should accept `Object` and internally cast to `TracerProvider`. Alternatively, the factory can use `Class.getDeclaredConstructors()[0].newInstance(args)` with `setAccessible(true)`, but this is fragile.

**Recommendation for implementer:** Use a static factory method on `KubeMQTracing` itself (e.g., `KubeMQTracing.create(Object tracerProvider, String sdkVersion, ...)`) that is referenced by name string in `TracingFactory`. This avoids reflection on constructor signatures. The spec's pattern is correct in spirit -- this is an implementation-time detail.

**Severity:** Minor (implementer resolves at coding time, no spec revision needed)

#### I-5: `LogContextProvider.getTraceContextFromOTel()` has a class-loading subtlety (Minor)

**Section:** 3.3.5 (OTel Trace Correlation)

The `LogContextProvider` class has a private method `getTraceContextFromOTel()` that directly uses `io.opentelemetry.api.trace.Span`. This method is in the same class as the OTel-availability check. In Java, the JVM loads methods lazily -- a private method that is never called will not trigger class resolution of its parameter/return types on most JVMs. However, this is not guaranteed by the JLS. Some aggressive ahead-of-time compilers (GraalVM native-image) may load all referenced types eagerly.

**Practical resolution:** For standard JVM (HotSpot), this works correctly because `getTraceContextFromOTel()` is only called after the `OTEL_AVAILABLE` guard. For GraalVM native-image compatibility, the method should be moved to a separate class (similar to the `TracingFactory` pattern). This is a future-proofing concern, not a blocking issue.

**Severity:** Minor (works on HotSpot; document as known limitation for GraalVM native-image)

#### I-6: `KubeMQTracing` does not implement `Tracing` interface in its class declaration (Minor)

**Section:** 4.3.4

The `KubeMQTracing` class definition at line 915 shows `public final class KubeMQTracing` without `implements Tracing`. The spec states at line 890 "KubeMQTracing implements Tracing" but the actual code block does not show this. Additionally, the method signatures in `KubeMQTracing` use OTel-specific types (e.g., `SpanKind`, `Context`, `Span`) while the `Tracing` interface uses `Object`. The implementer must add `implements Tracing` and provide bridge methods that cast `Object` parameters to OTel types internally, while also keeping the type-safe methods for internal OTel-aware code.

**Practical resolution:** `KubeMQTracing` should implement `Tracing` with `@Override` methods that delegate to the type-safe internal methods. For example:
```java
@Override
public Object startSpan(Object spanKind, String op, String ch, String mid, Object ctx) {
    return startSpan((SpanKind) spanKind, op, ch, mid, (Context) ctx);
}
```

**Severity:** Minor (clear from spec intent, implementer resolves at coding time)

---

### 3.3 Category 06: Documentation Spec

**Verdict: APPROVE -- Ready to implement**

The spec is primarily documentation work. The R1 fix for Lombok Javadoc strategy (M-8) provides clear build integration. The effort estimate of 14-22 days is realistic given 50 files with zero Javadoc.

#### I-7: Checkstyle Javadoc rules will fail on Lombok-generated methods (Info)

**Section:** 3.2.4 (Checkstyle Configuration)

The `JavadocMethod` rule with `accessModifiers="public"` will flag Lombok-generated getters/setters/builders that lack Javadoc. Since the Checkstyle plugin runs on original source (not delombok output), it will not see these methods and thus will not flag them. This means Checkstyle only validates manually-written public methods, which is the correct behavior. The delombok output is used only for Javadoc generation, not for linting.

**Severity:** Info (no issue -- Checkstyle and delombok serve different purposes)

---

### 3.4 Category 08: API Completeness Spec

**Verdict: APPROVE -- Ready to implement**

Small, well-scoped changes. The subscribe return type change and batch send are straightforward.

#### I-8: Batch send error response returns single-element list instead of per-message errors (Minor)

**Section:** 3.3.3 (Response Handler Changes)

When the server returns `isError()` for a batch response (line 374-383), the spec creates a single `QueueSendResult` error and wraps it in a single-element list. This means the caller cannot correlate which messages failed. However, when the server returns a batch error, it typically means the entire batch failed (e.g., stream disconnected). Per-message errors come through the normal `getResultsList()` path. The single-error-element approach is correct for batch-level failures.

**Practical consideration:** The caller should check `results.size() < inputMessages.size()` to detect batch-level errors vs per-message results. The spec should document this behavior in the Javadoc for `sendQueuesMessages()`, but this can be handled at implementation time.

**Severity:** Minor (Javadoc clarification at implementation time)

---

### 3.5 Category 09: API Design & DX Spec

**Verdict: APPROVE -- Ready to implement**

The validation changes, convenience methods, and verb alignment are well-designed with clear backward compatibility analysis.

#### I-9: Channel name regex may reject valid wildcard subscriptions (Minor)

**Section:** 6.2.4 (Channel Name Format Validation)

The regex `^[a-zA-Z0-9._\\-/:]+$` does not include `*` (wildcard character). KubeMQ supports wildcard subscriptions (e.g., `events.*`). If `validateChannelName()` is called on subscription channel patterns, it will reject wildcards.

**Practical resolution:** Either (a) add `*` to the allowed characters, or (b) only call `validateChannelName()` on publish/send operations (where wildcards are not valid) and use a separate `validateChannelPattern()` for subscriptions. Option (b) is cleaner. The spec's validation is called from message `validate()` methods, which are publish-side, so this may not be an issue in practice. The implementer should verify whether subscription `validate()` methods also validate channel names and, if so, allow wildcards there.

**Severity:** Minor (implementer checks subscription validation path)

---

## 4. Cross-Spec Implementability

### 4.1 Dependency Chain Validation

The implementation dependencies are correctly ordered:

```
Batch 1 (error handling, connection, auth, code quality)
    |
    v
04-testing Phase 1 (CI infrastructure) -- no Batch 1 deps
    |
    v
05-observability REQ-OBS-5 (logger interface) -- no Batch 1 deps
    |
    v
08-api-completeness (subscribe return types, batch send) -- no Batch 1 deps
    |
    v
09-api-design-dx Phase 1 (validation) -- depends on 01-error-handling
    |
    v
05-observability REQ-OBS-1/2/3 (OTel) -- depends on 01, 02, 07
    |
    v
06-documentation (Javadoc, troubleshooting) -- parallel with any phase
    |
    v
04-testing Phases 2-3 (new tests) -- depends on features being implemented
```

**Key finding:** Three specs (04-testing Phase 1, 05-obs REQ-OBS-5, 08-api-completeness) have NO Batch 1 dependencies and can start immediately. This allows 3-5 days of productive work while Batch 1 completes.

### 4.2 Shared Code Conflicts

| File | Modified By | Conflict Risk | Resolution |
|------|-----------|--------------|------------|
| `KubeMQClient.java` | 05-obs (logger), 09-dx (validation, defaults) | Medium | Implement 09-dx validation first (constructor changes), then 05-obs logger injection. Both modify the constructor but in different sections. |
| `PubSubClient.java` | 05-obs (logger migration), 08-api (return types), 09-dx (convenience methods, verb aliases) | High | Implement in order: 08-api (return types) -> 09-dx (aliases + convenience) -> 05-obs (logger migration across all methods). |
| `QueuesClient.java` | 08-api (batch send), 09-dx (convenience, verb aliases) | Medium | 08-api first (adds new method), then 09-dx (adds aliases). |
| `pom.xml` | 04-test (test deps), 05-obs (OTel deps, logback scope), 06-doc (checkstyle, javadoc plugins) | Low | All changes are additive to different sections of pom.xml. |

### 4.3 09-dx Depends on 08-api for Verb Aliases

The spec correctly notes (R1 X-4 fix) that verb-aligned aliases in 09-dx must be added AFTER 08-api changes return types. Specifically, `publishEvent(EventMessage)` must return `void` to match the current `sendEventsMessage()` behavior, but `subscribeToEvents()` return type changes (08-api) should be reflected in any new subscription-related aliases. The specs handle this correctly.

---

## 5. Test Feasibility

### 5.1 Testing Spec (04) Test Feasibility

| Test Category | Dependencies | Feasible Without Server? | Notes |
|--------------|-------------|-------------------------|-------|
| Error classification (parameterized) | REQ-ERR-1/2/3 | Yes (unit test with mocks) | Straightforward `@ParameterizedTest` |
| Retry policy | REQ-ERR-3 | Yes (unit test with mocks) | Mock the transport, verify retry logic |
| Closed-client guards | None | Yes (unit test) | Build client, close, call method, assert exception |
| Leak detection | Named thread prerequisite | Yes (unit test) | Assert `ManagedChannel.isTerminated()` after close |
| InProcessServer tests | grpc-testing dep | Yes (embedded server) | Already specified in pom.xml additions |
| Reconnection integration | Live server | No -- requires Testcontainers | Spec correctly defers to Phase 3 |

### 5.2 Observability Spec (05) Test Feasibility

| Test Category | Dependencies | Feasible? | Notes |
|--------------|-------------|-----------|-------|
| Logger interface (T1-T8) | None | Yes | Pure unit tests, no server needed |
| OTel tracing (T9-T18) | OTel test SDK | Yes | Use `SdkTracerProvider` + `InMemorySpanExporter` in test scope |
| Trace propagation (T19-T26) | OTel test SDK | Yes (unit), server needed (integration) | Unit tests verify inject/extract; integration tests verify round-trip |
| Metrics (T27-T38) | OTel test SDK | Yes | Use `InMemoryMetricReader` |
| No-op overhead (T39) | Benchmark harness | Yes | Timing-based, no JMH required per spec |
| OTel API absent (T43) | Class isolation | **Tricky** | Testing with OTel absent requires a separate classloader or a separate Maven module with OTel excluded. Recommend a simple integration test that runs with OTel dependency removed from test classpath via Maven profile. |

### 5.3 API Completeness (08) Test Feasibility

All tests are straightforward Mockito-based unit tests. The batch send test needs a mock `StreamObserver` to simulate the gRPC response flow. The subscribe return type tests are trivial assertions. No feasibility concerns.

### 5.4 API Design/DX (09) Test Feasibility

Validation tests are pure unit tests. Convenience method tests verify delegation using mocks. Verb alignment tests use reflection to verify `@Deprecated` annotations. The `validateOnBuild` test requires a live server for the success path, but the failure path (bad address) is a pure unit test. No feasibility concerns.

---

## 6. Effort Estimate Assessment

| Spec | R1 Estimate | R2 Assessment | Adjusted Estimate | Notes |
|------|------------|--------------|-------------------|-------|
| 04-testing | 12-18 days | Accurate | 12-18 days | Phase 1 (CI) is quick; Phase 2-3 depend on Batch 1 |
| 05-observability | 18-25 days (R1 revised) | Slightly optimistic | 20-27 days | The `Tracing` interface `Object`-to-OTel-type bridging adds implementation complexity not fully captured. Logger migration across 15 files is mechanical but time-consuming. |
| 06-documentation | 14-22 days | Accurate | 14-22 days | Javadoc writing is the bottleneck; parallelizable |
| 08-api-completeness | 3-5 days | Accurate | 3-5 days | Small, well-scoped |
| 09-api-design-dx | 8-12 days | Accurate | 8-12 days | Straightforward changes |

**Total Batch 2:** 57-89 days (R1 estimate: 52-83 days). The increase is due to the observability spec adjustment.

---

## 7. Commit Sequence

### 7.1 Category 04: Testing

| # | Commit | Files | Depends On | Est. |
|---|--------|-------|-----------|------|
| 1 | Add test dependencies (grpc-testing, grpc-inprocess, testcontainers) | `pom.xml` | None | 0.5d |
| 2 | Create testutil package with shared utilities | `src/test/java/io/kubemq/sdk/testutil/` | None | 0.5d |
| 3 | Add JaCoCo `check` goal with Phase 2 threshold (0.60) | `pom.xml` | None | 0.5d |
| 4 | Create CI workflow (`.github/workflows/ci.yml`) | `.github/workflows/ci.yml` | Commits 1-3 | 1d |
| 5 | Add `junit-platform.properties` with global timeout | `src/test/resources/junit-platform.properties` | None | 0.25d |
| 6 | Add error classification parameterized tests | `ErrorClassificationTest.java` | Batch 1 (01-spec) | 1d |
| 7 | Add retry policy unit tests | `RetryPolicyTest.java` | Batch 1 (01-spec) | 1d |
| 8 | Add closed-client guard tests | `ClosedClientGuardTest.java` | None | 1d |
| 9 | Add resource leak detection tests | `ResourceLeakDetectionTest.java` | Named ThreadFactory on executors | 1d |
| 10 | Add null/empty payload and oversized message tests | `PayloadValidationTest.java` | None | 0.5d |
| 11 | Add integration tests (auth failure, reconnection, timeout) | `*IT.java` files | Batch 1 (02-spec, 03-spec) | 3-5d |

### 7.2 Category 05: Observability

| # | Commit | Files | Depends On | Est. |
|---|--------|-------|-----------|------|
| 1 | Add `KubeMQLogger` interface, `NoOpLogger`, `Slf4jLoggerAdapter` | `observability/*.java` (4 new files) | None | 1d |
| 2 | Add `KubeMQLoggerFactory` and `LogHelper` | `observability/*.java` (2 new files) | Commit 1 | 0.5d |
| 3 | Add `logger()` builder method to `KubeMQClient` | `KubeMQClient.java` | Commit 2 | 0.5d |
| 4 | Migrate `@Slf4j` to `KubeMQLogger` across 15 production classes | All client/handler/subscription classes | Commit 3 | 2-3d |
| 5 | Change logback to test scope, slf4j to provided scope in pom.xml | `pom.xml` | Commit 4 | 0.25d |
| 6 | Remove `setLogLevel()` Logback cast, deprecate `Level` enum | `KubeMQClient.java` | Commit 5 | 0.25d |
| 7 | Add `LogContextProvider` for OTel trace correlation | `observability/LogContextProvider.java` | Commit 2 | 0.5d |
| 8 | Add `Tracing`/`Metrics` interfaces and `NoOp` implementations | `observability/*.java` (4 new files) | None | 0.5d |
| 9 | Add `OTelAvailability`, `TracingFactory`, `MetricsFactory` | `observability/*.java` (3 new files) | Commit 8 | 0.5d |
| 10 | Add OTel dependencies (provided scope, BOM) to pom.xml | `pom.xml` | None | 0.25d |
| 11 | Add `KubeMQSemconv` constants | `observability/KubeMQSemconv.java` | Commit 10 | 0.5d |
| 12 | Add `KubeMQTracing` implementing `Tracing` interface | `observability/KubeMQTracing.java` | Commits 8, 11 | 2d |
| 13 | Add `SdkVersion` utility | `observability/SdkVersion.java` | None | 0.25d |
| 14 | Instrument all 15 operation points with tracing spans | All client/handler classes | Commits 9, 12 | 3-4d |
| 15 | Add `KubeMQTagsCarrier` for W3C trace context propagation | `observability/KubeMQTagsCarrier.java` | Commit 12 | 0.5d |
| 16 | Add inject/extract at all publish/consume points | All client/handler/subscription classes | Commit 15 | 2d |
| 17 | Add `KubeMQMetrics` implementing `Metrics` interface | `observability/KubeMQMetrics.java` | Commits 8, 11 | 1.5d |
| 18 | Add `CardinalityConfig` and `CardinalityManager` | `observability/Cardinality*.java` | Commit 1 (logger) | 0.5d |
| 19 | Instrument all operation points with metrics recording | All client/handler classes | Commits 9, 17 | 2d |
| 20 | Add `tracerProvider` and `meterProvider` to client builder | `KubeMQClient.java` | Commit 9 | 0.5d |
| 21 | Add performance verification benchmark | `test/benchmark/` or `test/.../OTelBenchmarkTest.java` | Commits 14, 19 | 1d |
| 22 | Add unit tests for all observability components (T1-T43) | `test/unit/observability/*.java` | All above | 3-4d |

### 7.3 Category 06: Documentation

| # | Commit | Files | Depends On | Est. |
|---|--------|-------|-----------|------|
| 1 | Create `CHANGELOG.md` with historical entries | `CHANGELOG.md` | None | 0.5d |
| 2 | Create `TROUBLESHOOTING.md` with 11+ entries | `TROUBLESHOOTING.md` | Provisional error messages | 1-2d |
| 3 | Add examples README and inline comment improvements | `kubemq-java-example/README.md`, example files | None | 1d |
| 4 | Add `lombok-maven-plugin` delombok and `maven-javadoc-plugin` config | `pom.xml`, `lombok.config` | None | 0.5d |
| 5 | Add Checkstyle Javadoc configuration | `checkstyle.xml`, `pom.xml` | None | 0.5d |
| 6 | Write Javadoc for client classes (3 files, ~20 methods) | `KubeMQClient.java`, `PubSubClient.java`, `CQClient.java`, `QueuesClient.java` | None | 2d |
| 7 | Write Javadoc for message request types (6 files, ~30 methods) | `EventMessage.java`, `EventStoreMessage.java`, etc. | None | 2d |
| 8 | Write Javadoc for response/result types (8 files, ~40 methods) | `EventSendResult.java`, `QueueSendResult.java`, etc. | None | 1.5d |
| 9 | Write Javadoc for subscription types (4 files, ~20 methods) | `EventsSubscription.java`, etc. | None | 1d |
| 10 | Write Javadoc for exception, enum, utility classes (12 files) | All remaining source files | None | 1.5d |
| 11 | Restructure `README.md` with all 10 GS sections | `README.md` | Commits 1, 2 | 1-2d |
| 12 | Restructure quick starts into explicit format | `README.md` | Commit 11 | 0.5d |
| 13 | Create migration guide | `MIGRATION.md` | Commits from 08, 09 specs | 1-2d |

### 7.4 Category 08: API Completeness

| # | Commit | Files | Depends On | Est. |
|---|--------|-------|-----------|------|
| 1 | Change subscribe methods to return subscription objects | `PubSubClient.java`, `CQClient.java` | None | 0.5d |
| 2 | Add `pendingBatchResponses` map and batch response handling | `QueueUpstreamHandler.java` | None | 1d |
| 3 | Add `sendQueuesMessages(List)` batch method | `QueuesClient.java`, `QueueUpstreamHandler.java` | Commit 2 | 0.5d |
| 4 | Add `NotImplementedException` and `purgeQueue()` stub | `exception/NotImplementedException.java`, `QueuesClient.java` | Batch 1 (01-spec, optional) | 0.5d |
| 5 | Create feature matrix document | `clients/feature-matrix.md` | None | 0.5d |
| 6 | Add unit tests for all new/changed methods | `PubSubClientTest.java`, `QueuesClientTest.java`, etc. | Commits 1-4 | 1d |
| 7 | Add integration tests (subscribe cancel, batch send) | `*IT.java` | Live server | 0.5d |

### 7.5 Category 09: API Design & DX

| # | Commit | Files | Depends On | Est. |
|---|--------|-------|-----------|------|
| 1 | Add `validateAddress()` and `validateClientId()` to `KubeMQClient` | `KubeMQClient.java` | Batch 1 (01-spec for ValidationException) | 1d |
| 2 | Add default address (`KUBEMQ_ADDRESS` env var fallback, localhost:50000) and default clientId | `KubeMQClient.java` | Commit 1 | 0.5d |
| 3 | Add `validateOnBuild` option | `KubeMQClient.java` | Commit 1 | 0.5d |
| 4 | Add `validateChannelName()` utility and integrate into message validate() methods | `KubeMQUtils.java`, all message classes | Batch 1 (01-spec for ValidationException) | 1d |
| 5 | Replace `IllegalArgumentException` with `ValidationException` in all validate() methods | All message classes, subscription classes | Batch 1 (01-spec) | 1d |
| 6 | Add convenience publish/send methods on all client classes | `PubSubClient.java`, `QueuesClient.java`, `CQClient.java` | Commit 2 | 1d |
| 7 | Add verb-aligned alias methods with `@Deprecated` on old methods | `PubSubClient.java`, `QueuesClient.java`, `CQClient.java` | 08-api (subscribe return types) | 1.5d |
| 8 | Add custom builder `build()` with format validation | All message classes | Commit 4 | 1d |
| 9 | Add default value Javadoc to `KubeMQClient` fields | `KubeMQClient.java` | None | 0.25d |
| 10 | Add unit tests for all validation, convenience, verb alignment | New test files | All above | 2d |

---

## 8. Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|-----------|
| Batch 1 delayed, blocking 09-dx validation and 05-obs OTel | Medium | High (blocks ~40% of Batch 2 effort) | Start with non-dependent work: 04-test CI, 05-obs logger, 08-api, 06-doc. Use `IllegalArgumentException` as temporary fallback per spec. |
| OTel `provided` scope + reflection pattern causes unexpected issues in CI or user environments | Low | Medium | The proxy pattern is well-tested in other Java libraries (e.g., Micrometer, Spring). Comprehensive T43 test verifies absent-OTel path. |
| Logback removal breaks downstream users | Medium | Medium | Prominent CHANGELOG entry. Users who depend on transitive Logback must add it to their own pom.xml. |
| Channel name regex rejects valid patterns (wildcards, Unicode) | Medium | Low | Start conservative (ASCII + wildcards); expand based on user feedback. Document as known limitation. |
| Binary incompatibility from subscribe return type change | Low | Low | Source-compatible; only affects users who swap JARs without recompiling, which is uncommon. |

---

## 9. Conclusion

All five Batch 2 specs pass the implementability review. The R1 fixes were correctly applied across all specs, with the critical C-1 fix (OTel proxy pattern) being architecturally sound for the Java runtime model. The remaining minor findings (I-4 through I-9) are implementation-time concerns that do not require spec revisions.

**Recommended implementation start order:**
1. **04-testing Phase 1** (CI) + **05-obs REQ-OBS-5** (logger) + **08-api-completeness** -- all can start immediately with no Batch 1 dependencies
2. **06-documentation** -- start CHANGELOG, troubleshooting, Javadoc in parallel
3. **09-api-design-dx Phase 1** -- once Batch 1 error hierarchy lands
4. **05-obs REQ-OBS-1/2/3** -- once Batch 1 error + connection specs land
5. **04-testing Phases 2-3** -- once features from above are implemented

The specs are ready for implementation.

---

## Fixes Applied (Round 2)

| Issue | Status | Change Made |
|-------|--------|-------------|
| I-1 (04-test: named thread info) | Skipped | Info severity -- no spec change needed |
| I-2 (04-test: batch 1 independence info) | Skipped | Info severity -- no spec change needed |
| I-3 (04-test: junit-platform.properties info) | Skipped | Info severity -- no spec change needed |
| I-4 (05-obs: TracingFactory reflection params) | Skipped | Minor, explicitly "no spec revision needed" -- implementer resolves at coding time |
| I-5 (05-obs: LogContextProvider GraalVM subtlety) | **Fixed** | Added NOTE comment in `getTraceContextFromOTel()` documenting HotSpot lazy loading behavior and GraalVM native-image limitation (05-observability-spec.md) |
| I-6 (05-obs: KubeMQTracing missing `implements Tracing`) | **Fixed** | Changed class declaration from `public final class KubeMQTracing` to `public final class KubeMQTracing implements Tracing` (05-observability-spec.md) |
| I-7 (06-doc: Checkstyle Javadoc info) | Skipped | Info severity -- no issue identified |
| I-8 (08-api: Batch send error response) | Skipped | Minor, Javadoc clarification at implementation time |
| I-9 (09-dx: Channel regex wildcards) | **Fixed** | Added NOTE comment above regex explaining publish-side vs subscription-side validation and recommending `validateChannelPattern()` for subscriptions (09-api-design-dx-spec.md) |
