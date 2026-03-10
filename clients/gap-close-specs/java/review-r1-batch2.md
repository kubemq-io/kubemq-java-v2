# Java SDK Specs -- Architecture Review (Round 1, Batch 2)

**Reviewer:** Senior Java SDK Architect (Testing/Observability Expert)
**Specs Reviewed:** 04-testing, 05-observability, 06-documentation, 08-api-completeness, 09-api-design-dx
**Date:** 2026-03-09

---

## 1. Review Summary

| Spec | Verdict | Critical | Major | Minor | Nit |
|------|---------|----------|-------|-------|-----|
| 04-testing-spec.md | **APPROVE with changes** | 0 | 3 | 3 | 2 |
| 05-observability-spec.md | **APPROVE with changes** | 1 | 4 | 3 | 1 |
| 06-documentation-spec.md | **APPROVE** | 0 | 1 | 2 | 1 |
| 08-api-completeness-spec.md | **APPROVE with changes** | 0 | 2 | 2 | 0 |
| 09-api-design-dx-spec.md | **APPROVE with changes** | 0 | 2 | 3 | 1 |
| **Totals** | | **1** | **12** | **13** | **5** |

**Overall Assessment:** All five specs are architecturally sound and demonstrate thorough gap analysis against both the Golden Standard and source code. The observability spec is the most ambitious (15-23 days) and carries the highest risk; one critical issue around `provided` scope + runtime class loading must be resolved before implementation. The remaining specs have major issues that are individually tractable and do not require structural redesign.

---

## 2. Spec-by-Spec Findings

### 2.1 Category 04: Testing Spec

**File:** `clients/gap-close-specs/java/04-testing-spec.md`

#### M-1: InProcessServer tests may not exercise production channel initialization path (MAJOR)

**Section:** 3.3.1 (New Test Dependency: gRPC InProcessServer)

The spec recommends `grpc-testing` + `grpc-inprocess` for new tests while keeping existing Mockito tests. This is correct in principle, but the spec does not address a practical gap: `InProcessServer` bypasses `ManagedChannelBuilder.forTarget()`, TLS handshake, and `MetadataInterceptor`. This means the new tests will not exercise the auth interceptor chain or TLS path that the spec claims they will (Section 3.3.1: "tests the full gRPC interceptor chain, including the error mapping interceptor from REQ-ERR-6 and auth interceptor from REQ-AUTH-1").

**Recommendation:** Clarify that InProcessServer tests exercise server-side interceptors and response behavior, not client-side `ManagedChannel` construction. Client interceptor chain testing requires either (a) registering client interceptors on the `InProcessChannel` via `InProcessChannelBuilder.intercept()`, or (b) separate unit tests that verify interceptor behavior in isolation. Add a note specifying which test approach covers which layer.

#### M-2: Closed-client guard test creates real gRPC channel to localhost:50000 (MAJOR)

**Section:** 3.3.4 (Closed-Client Guard Tests)

The test code at line 355-358 creates a `PubSubClient.builder().address("localhost:50000").clientId("test").build()`. In the current codebase, `build()` calls `initChannel()` which creates a real `ManagedChannel` via `ManagedChannelBuilder.forTarget()`. This means the test will attempt a real TCP connection (or at least channel initialization) to `localhost:50000`. If no server is running, the channel enters `IDLE` state and the test may still pass, but this is fragile and environment-dependent.

**Recommendation:** Either (a) use `InProcessChannelBuilder` for these tests, (b) mock the channel creation via the Transport interface from 07-code-quality-spec.md REQ-CQ-1, or (c) document that these tests rely on lazy connection behavior and will not fail without a server. Option (b) is the cleanest and aligns with the layered architecture spec.

#### M-3: Resource leak test relies on "kubemq-" thread name prefix that does not exist (MAJOR)

**Section:** 3.3.5 (Resource Leak Detection Tests)

The test uses `SDK_THREAD_PREFIX = "kubemq-"` to filter threads. Verified in source: the SDK does not name any threads with a "kubemq-" prefix. gRPC creates threads with names like `grpc-default-executor-*` and `grpc-timer-*`. Scheduled executors in `QueueMessageReceived` (line 14: `Executors.newSingleThreadScheduledExecutor()`) use default JVM thread names.

**Recommendation:** Either (a) add named `ThreadFactory` instances to SDK-created executors (e.g., `Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "kubemq-visibility-timer"))`) as a prerequisite, or (b) change the leak detection approach to track `ManagedChannel.isShutdown()`/`isTerminated()` and `ExecutorService.isTerminated()` (which the spec already mentions as options 1 and 2 in the Javadoc but does not implement). The spec should state which executors must be named and with what prefix.

#### m-1: Phase target inconsistency between spec and GS (Minor)

**Section:** 3.2, AC-1

The spec states "Coverage meets phased target (Phase 1: 40%)" as the current target. With 75.1% existing coverage, the SDK is already at Phase 2 (>=60%). The JaCoCo `check` goal in REQ-TEST-5 should use Phase 2 threshold (0.60) as the initial enforced minimum, not Phase 1 (0.40), to prevent regression.

**Recommendation:** Set JaCoCo `<minimum>0.60</minimum>` initially (Phase 2), with a plan to raise to 0.80 (Phase 3) once the new tests from this spec are complete.

#### m-2: Missing test for `@Timeout` propagation (Minor)

**Section:** 3.3

The spec adds `@Timeout(value = 30, unit = TimeUnit.SECONDS)` at class level on new test classes. However, the 47 existing test classes do not have this annotation. The spec should document whether existing tests need to be retrofitted, or if timeout enforcement applies only to new tests.

**Recommendation:** Add a single sentence to the implementation order specifying that `@Timeout` should be added to all existing `*Test.java` files in Phase 2 as a batch update.

#### m-3: Missing Testcontainers version specification (Minor)

**Section:** REQ-TEST-2 (Integration Tests)

The spec references Testcontainers for KubeMQ server lifecycle in integration tests but does not specify the dependency or version to add to `pom.xml`. The existing integration tests appear to rely on an external server.

**Recommendation:** Add a `<dependency>` block for `org.testcontainers:testcontainers` and `org.testcontainers:junit-jupiter` with a specific version to the pom.xml section (Section 9).

#### n-1: Test file in error package vs existing convention (Nit)

**Section:** 3.3.2

New test classes are placed in `io.kubemq.sdk.unit.error` package. The existing exception tests are at `io.kubemq.sdk.unit.exception`. This creates two test packages for error-related tests. Consider using a consistent package name.

#### n-2: Test implementations left as comments (Nit)

Several test methods contain `// implementation` placeholder comments instead of actual test bodies. This is acceptable for a spec document, but the spec should note that these are pseudocode stubs, not copy-paste-ready implementations.

---

### 2.2 Category 05: Observability Spec

**File:** `clients/gap-close-specs/java/05-observability-spec.md`

#### C-1: `provided` scope + runtime class loading creates ClassNotFoundException risk (CRITICAL)

**Section:** 4.3.1 (Maven Dependency), 7.3.1 (Dependency Scope), 7.3.2 (OTelAvailability)

The spec correctly uses `<scope>provided</scope>` for `opentelemetry-api`, meaning it is NOT on the runtime classpath unless users add it. However, the `KubeMQTracing` class (Section 4.3.4) directly imports and uses OTel API types (`io.opentelemetry.api.trace.*`, `io.opentelemetry.api.common.Attributes`, `io.opentelemetry.context.Context`). If OTel API is not on the classpath at runtime, loading `KubeMQTracing` will throw `NoClassDefFoundError` the moment the class is loaded by the JVM -- not when methods are called.

The `OTelAvailability` class (Section 7.3.2) detects this, but the spec's integration pattern in Section 4.3.5 (instrumentation points) has every client class directly calling `KubeMQTracing` methods. The `KubeMQTracing` class itself will fail to load if OTel is absent.

This is fundamentally different from Go's approach (where unused imports are compile errors but the binary always includes all dependencies). In Java, `provided` scope means the JAR is not transitively included, so runtime ClassNotFoundException/NoClassDefFoundError is a real risk.

**Recommendation:** The spec must adopt one of these patterns:
1. **Proxy pattern:** Create a `TracingProxy` that is loaded only via reflection when `OTelAvailability.isAvailable()` returns true. All client code calls `TracingProxy` methods, which are no-ops when OTel is absent. This avoids loading any OTel-importing class until confirmed available.
2. **Optional dependency (not provided):** Change scope from `provided` to `compile` with `<optional>true</optional>`. This still means users must add it, but it IS on the classpath when present. However, this changes the semantics -- the OTel API JAR would be in the compile classpath of downstream projects.
3. **Multi-module split:** Put OTel instrumentation in a separate `kubemq-java-otel` artifact that users add only when they want tracing. This is the cleanest but highest effort.

Pattern 1 is recommended as it matches the spec's intent with minimal structural change. The spec should show the proxy pattern and how `KubeMQClient` instantiates either the real `KubeMQTracing` or a no-op stub based on `OTelAvailability`.

#### M-4: LoggerFactory name collision with SLF4J (MAJOR)

**Section:** 3.3.4 (LoggerFactory)

The SDK-internal class is named `LoggerFactory` in package `io.kubemq.sdk.observability`. SLF4J also has `org.slf4j.LoggerFactory`. While fully qualified names avoid ambiguity, any class that imports both will get a compilation error. The `Slf4jLoggerAdapter` already imports `org.slf4j.LoggerFactory` (line 219), and the `CardinalityManager` (Section 6.3.6) imports `io.kubemq.sdk.observability.LoggerFactory`. If a class ever needs both, it creates confusion.

**Recommendation:** Rename to `KubeMQLoggerFactory` or `LoggerResolver` to avoid the collision. This is a new class so no backward compatibility concern.

#### M-5: Slf4jLoggerAdapter loses exception stack traces (MAJOR)

**Section:** 3.3.3 (Slf4jLoggerAdapter)

The `error()` method calls `delegate.error(formatMessage(msg, keysAndValues))` but SLF4J's `error(String)` method does not pass a `Throwable`. When an exception is part of the log context, the stack trace is lost. The SLF4J convention is to pass the `Throwable` as the last argument to `error(String, Throwable)`.

The `KubeMQLogger` interface uses `Object... keysAndValues` which has no convention for passing exceptions. A caller doing `logger.error("Failed", "cause", exception)` will have the exception `toString()` in the formatted message but no stack trace in the SLF4J output.

**Recommendation:** Add a `KubeMQLogger.error(String msg, Throwable cause, Object... keysAndValues)` overload, or specify a convention where if the last vararg value is a `Throwable` and the varargs length is odd, it is treated as the exception parameter. The `Slf4jLoggerAdapter` should detect this and delegate to `delegate.error(formattedMsg, throwable)`.

#### M-6: LogContextProvider.getTraceContext() returns Object[] that cannot be merged into varargs cleanly (MAJOR)

**Section:** 3.3.5 (OTel Trace Correlation)

The spec shows trace context being merged into log calls:

```java
logger.debug("Ping successful",
    "address", address,
    LogContextProvider.getTraceContext()  // appended to keysAndValues
);
```

This will not compile. Java varargs expand `Object...` at the call site, so `getTraceContext()` would be passed as a single `Object[]` element within the varargs, not spliced into the array. The spec acknowledges this with "create a `LogHelper.merge(Object[]... arrays)` utility" but does not include the utility in the specification.

**Recommendation:** Define the `LogHelper.merge()` utility in the spec with implementation, and update all example call sites to use it: `logger.debug("Ping successful", LogHelper.merge(new Object[]{"address", address}, LogContextProvider.getTraceContext()))`. Alternatively, have each logger implementation handle trace context injection internally rather than at each call site.

#### M-7: Missing specification for no-op fallback of KubeMQTracing and KubeMQMetrics (MAJOR)

**Section:** 7.3.2 (OTel API Availability Detection)

The spec states "When OTel API is not available: `KubeMQTracing` is replaced with a no-op implementation" but does not define this no-op implementation. All client code calls `tracing.startSpan(...)` and `metrics.recordOperationDuration(...)`. Without a concrete no-op class that has the same method signatures, the client code must have null checks or conditional dispatching everywhere.

**Recommendation:** Define `NoOpTracing` and `NoOpMetrics` classes that implement the same public interface as `KubeMQTracing` and `KubeMQMetrics` but do nothing. Better yet, extract an interface (`Tracing`, `Metrics`) that both the real and no-op implementations satisfy, and inject the appropriate one at client construction time based on `OTelAvailability.isAvailable()`.

#### m-4: KubeMQSemconv uses OTel AttributeKey types directly (Minor)

**Section:** 4.3.2 (KubeMQSemconv)

`KubeMQSemconv` imports `io.opentelemetry.api.common.AttributeKey` at class level. If this class is loaded when OTel is not on the classpath, it will throw `NoClassDefFoundError`. This compounds the C-1 issue.

**Recommendation:** If the proxy pattern from C-1 is adopted, `KubeMQSemconv` must be loaded only within the OTel-available code path, never from main client classes.

#### m-5: Histogram bucket boundaries use List.of() (Java 9+) (Minor)

**Section:** 6.3.5 (KubeMQMetrics)

Line 1369: `private static final List<Double> HISTOGRAM_BOUNDARIES = List.of(...)`. The SDK's pom.xml targets Java 11 as minimum. While Java 11 supports `List.of()`, this should be explicitly noted since the gap research mentions "Only Java 11 targeted" and there was consideration for Java 8 compatibility.

**Recommendation:** Add a comment confirming Java 11+ is the minimum and `List.of()` is intentional. If Java 8 support is ever needed, this must change to `Arrays.asList()` or `Collections.unmodifiableList()`.

#### m-6: Missing `setExplicitBucketBoundariesAdvice` API version note (Minor)

**Section:** 6.3.5 (KubeMQMetrics)

The method `setExplicitBucketBoundariesAdvice()` was introduced in OTel API 1.32.0. The spec states minimum supported version is 1.31.0. This is a conflict -- using 1.31.0 would fail at runtime.

**Recommendation:** Update the minimum supported OTel API version to 1.32.0, or use the older `setExplicitBucketBoundariesAdvice` equivalent if one exists, or document that 1.32.0 is actually the minimum.

#### n-3: Typo in method name `isSLf4jAvailable` (Nit)

**Section:** 3.3.4 (LoggerFactory)

Method is named `isSLf4jAvailable()` with inconsistent casing (capital L after S). Should be `isSlf4jAvailable()`.

---

### 2.3 Category 06: Documentation Spec

**File:** `clients/gap-close-specs/java/06-documentation-spec.md`

#### M-8: Javadoc effort estimate may be unrealistic for Lombok-heavy codebase (MAJOR)

**Section:** REQ-DOC-1 (Auto-Generated API Reference)

The spec estimates 5-8 days for Javadoc across 50+ files. Verified in source: all message types use `@Data` + `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor`. Lombok-generated builder methods, getters, setters, equals, hashCode, and toString are not visible in source and cannot have Javadoc added directly. The spec mentions "delombok for Javadoc generation" but does not specify how this integrates with the build.

For `@Builder` specifically, the generated `builder()`, `build()`, and per-field setter methods need Javadoc. The Lombok approach is to use `@Builder(builderMethodName="", buildMethodName="")` with a manual builder inner class, or use `lombok.config` settings. Neither is specified.

**Recommendation:** Add a concrete subsection specifying the Lombok Javadoc strategy: (a) add `lombok-maven-plugin` with `delombok` goal for Javadoc generation, and (b) document which public methods need manual Javadoc vs which are covered by delombok. For builder fields specifically, use class-level Javadoc with `@param` equivalents or field-level Javadoc comments that Lombok 1.18.22+ propagates to builder setters.

#### m-7: Troubleshooting guide error messages reference types that do not yet exist (Minor)

**Section:** REQ-DOC-5 (Troubleshooting Guide)

The spec notes dependency on REQ-ERR-1 for error messages. The troubleshooting entries should use provisional error messages with a note that exact text will be updated after REQ-ERR-1 implementation.

#### m-8: Examples compile check in CI lacks specificity (Minor)

**Section:** REQ-DOC-4 (Code Examples / Cookbook)

The spec says "Add examples module to CI build so compilation failures block merge" but does not specify how the examples module is structured in Maven. The existing examples appear to be in `kubemq-java-example/` (a separate directory). The spec should clarify if this is a Maven module in the parent POM or a standalone project.

#### n-4: Feature matrix location inconsistency (Nit)

The 06-documentation-spec and 08-api-completeness-spec both reference the feature matrix. The 08 spec places it at `clients/feature-matrix.md`, while the 06 spec references it as part of REQ-DOC-2 (README section 5). Ensure a single authoritative location is established.

---

### 2.4 Category 08: API Completeness Spec

**File:** `clients/gap-close-specs/java/08-api-completeness-spec.md`

#### M-9: Batch send response handling has race condition potential (MAJOR)

**Section:** 3.3.2 and 3.3.3 (QueueUpstreamHandler batch methods)

The spec adds a `pendingBatchResponses` ConcurrentHashMap alongside the existing `pendingResponses` map. The `onNext` handler checks `pendingResponses.remove(refRequestID)` first, then `pendingBatchResponses.remove(refRequestID)`. Both maps use the same `requestId` namespace (from `generateRequestId()`). Since both single and batch sends use the same gRPC stream and same ID generator, there is no risk of ID collision between maps (each request gets a unique ID). However, the cleanup task and `closeStreamWithError()` now need to iterate two maps, and the `requestTimestamps` map is shared between both.

The spec shows `requestTimestamps.put(requestId, ...)` for both single and batch sends, but only `requestTimestamps.remove(refRequestID)` in the single-message `onNext` path. If a single-message response arrives for a batch request ID (or vice versa), the timestamp is cleaned up by whichever map removes it first, but the other map's timeout detection becomes broken.

**Recommendation:** Add an explicit note that `requestId` values are globally unique across both maps (guaranteed by UUID generation), and verify that `requestTimestamps.remove()` is called in both the single and batch `onNext` paths (the spec shows it for single but not explicitly for batch -- line 357 removes from single, but the batch path at line 366 only removes from `pendingBatchResponses`, not `requestTimestamps`). Add `requestTimestamps.remove(refRequestID)` to the batch response handling block.

#### M-10: Binary compatibility break not sufficiently called out for subscribe return type change (MAJOR)

**Section:** 3.2.3 (Breaking Change Assessment) and 7 (Breaking Changes)

The spec correctly identifies the binary incompatibility: "code compiled against the old SDK will get `NoSuchMethodError` at runtime if it uses the new SDK jar without recompilation." However, the conclusion is "acceptable in a minor version release" which contradicts SemVer. The method descriptor change from `()V` to `()Lio/kubemq/sdk/pubsub/EventsSubscription;` is a binary-breaking change per the Java Language Specification.

The spec offers the alternative of deferring to v2.2.0, but this is also a minor version which still has the same SemVer concern.

**Recommendation:** Either (a) explicitly acknowledge this as a minor-release binary break with justification (the API is v2.x with no binary compatibility guarantee documented, and the practical impact is minimal since users typically recompile), or (b) adopt the additive pattern: keep the existing `void subscribeToEvents(EventsSubscription)` method unchanged and add a new `EventsSubscription subscribeToEventsReturning(EventsSubscription)` method, deprecating the void version. Option (a) is pragmatically correct for this SDK's maturity level but should be stated explicitly.

#### m-9: QueueSendResult.decode() method not shown (Minor)

**Section:** 3.3.3 (Response Handler Changes)

The batch response handling calls `new QueueSendResult().decode(result)`. Verified in source: `QueueSendResult` exists but the `decode()` method signature should be verified against the existing codebase. The spec assumes this method exists and returns the decoded result, but does not show the method signature.

#### m-10: Feature matrix only fills Java column (Minor)

**Section:** 4.2 (Implementation)

The feature matrix template fills only the Java column. Other SDK columns are empty. This is acknowledged but may cause confusion when the matrix is published. Consider adding a header note: "Other SDK columns will be populated during their respective gap-close processes."

---

### 2.5 Category 09: API Design & DX Spec

**File:** `clients/gap-close-specs/java/09-api-design-dx-spec.md`

#### M-11: ValidationException replaces IllegalArgumentException -- narrowly breaking (MAJOR)

**Section:** 3.2.1 (Address Format Validation)

The spec acknowledges that changing from `IllegalArgumentException` to `ValidationException` is breaking for callers catching `IllegalArgumentException`. The mitigation offered is "acceptable in a minor version because the GS requires it." This is pragmatically reasonable but should be more precisely documented.

Currently `KubeMQClient` line 93-95 throws `IllegalArgumentException`. Any production code doing:
```java
try { client = PubSubClient.builder()...build(); }
catch (IllegalArgumentException e) { handleValidation(e); }
```
will break silently (the `IllegalArgumentException` catch will no longer fire).

**Recommendation:** Have `ValidationException` extend `IllegalArgumentException` instead of (or in addition to) `KubeMQException`. This preserves the catch behavior. If the error hierarchy from 01-error-handling-spec requires `ValidationException extends KubeMQException extends RuntimeException`, then `KubeMQException` itself could be a sibling of `IllegalArgumentException`, and `ValidationException` could extend both via... no, Java single inheritance prevents this.

Alternative: During the v2.x transition, make the builder throw both: keep the `IllegalArgumentException` check and add `ValidationException` as a wrapper. Or document this as a known breaking change requiring users to update catch blocks, and include it in the migration guide with before/after code.

#### M-12: Default address to localhost:50000 may cause silent connection to wrong service (MAJOR)

**Section:** REQ-DX-2 (Minimal Code Happy Path)

The spec proposes defaulting `address` to `"localhost:50000"`. While this improves the local development experience, it means that if a user forgets to set the address in production, the client silently connects to localhost:50000 (which likely does not exist, causing connection failures) rather than failing at construction time.

The current behavior (null check, fail immediately) is arguably safer for production.

**Recommendation:** Default to `"localhost:50000"` only when an environment variable like `KUBEMQ_ADDRESS` is not set. Or log a WARN when using the default address: "Using default address localhost:50000. Set address explicitly for production use." This matches the GS intent (local dev works out of the box) while protecting production deployments.

#### m-11: Convenience method naming inconsistency (Minor)

**Section:** REQ-DX-2 and REQ-DX-3

The spec proposes `publishEvent("channel", body)` as a convenience method (DX-2) and `publishEvent()` as a verb-aligned alias for `sendEventsMessage()` (DX-3). These appear to be the same method name but with different signatures -- one is a convenience shorthand (takes channel + body), the other is a 1:1 alias for the existing method (takes `EventMessage`). The spec should clarify whether `publishEvent(EventMessage)` and `publishEvent(String, byte[])` are both added, and how they relate to the deprecated `sendEventsMessage()`.

#### m-12: Message immutability deferred without interim mitigation (Minor)

**Section:** REQ-DX-5 (Message Builder/Factory)

The spec correctly defers `@Data` to `@Value` migration to v3.0. However, no interim step is specified. Adding `@Deprecated` on setter methods would signal intent. Lombok supports `@Setter(AccessLevel.NONE)` at field level, which could be used selectively for new message types without breaking existing ones.

**Recommendation:** In v2.x, add `@Setter(AccessLevel.NONE)` on message fields that should be immutable after construction (e.g., `channel`, `body`), keeping setters on fields users legitimately mutate (e.g., `tags`). Document the deprecation plan.

#### m-13: validateOnBuild creates constructor side effect (Minor)

**Section:** 3.2.4 (Optional Eager Connection Validation)

The `validateOnBuild` option sends a ping during `build()`. If the ping fails, the spec shows cleanup of `managedChannel.shutdownNow()`. However, the builder pattern convention is that `build()` should be a pure construction method. A network call in `build()` violates the principle of least surprise. Some dependency injection frameworks call `build()` during wiring, where network availability is not guaranteed.

**Recommendation:** Document that `validateOnBuild` is opt-in (already specified as default false) and add a Javadoc warning that it performs a network call. Also ensure the error message (ConnectionException) clearly states this was an optional validation step.

#### n-5: Address validation regex does not handle IPv6 (Nit)

**Section:** 3.2.1 (Address Format Validation)

The validation uses `lastIndexOf(':')` to split host and port. IPv6 addresses like `[::1]:50000` would split incorrectly (colon inside the address). While IPv6 KubeMQ usage is likely rare, the validation should handle `[host]:port` format or document that IPv6 addresses must use the bracketed format.

---

## 3. Cross-Spec Consistency Issues

### X-1: Observability spec's dependency on error handling types not yet implemented (Major cross-spec)

The 05-observability-spec heavily references types from 01-error-handling-spec:
- `ErrorClassifier.toOtelErrorType()` (used in REQ-OBS-1 retry events, REQ-OBS-3 metrics)
- `ErrorCategory` enum (used in REQ-OBS-3 error.type mapping)

The 04-testing-spec similarly references `GrpcErrorMapper`, `KubeMQException`, `ClientClosedException`.

Both specs provide "fallback if Category 01 not yet implemented" notes. However, if Batch 1 specs are implemented first (as intended), these types should exist by the time Batch 2 begins. The specs should be clear about whether the fallback paths are temporary scaffolding or permanent alternatives.

**Recommendation:** Add a "Prerequisites" section to both 04 and 05 specs that explicitly states: "This spec assumes REQ-ERR-1, REQ-ERR-2, and REQ-ERR-3 from 01-error-handling-spec.md are implemented. If not, use the fallback stubs documented in each section. Remove fallback code once error handling is in place."

### X-2: Logger interface (05-spec) and logging migration (04, 06, 08, 09 specs)

The 05-observability-spec defines `KubeMQLogger` and the migration from `@Slf4j`. The 04-testing-spec adds new test classes that use `log.debug(...)` (e.g., line 304 in test stubs). If logging migration happens before test development, the test classes should use the new logger. If tests come first, they use `@Slf4j` and must be migrated later.

**Recommendation:** The 04-testing-spec should note that new test classes do not need `KubeMQLogger` (test classes can use SLF4J directly since Logback remains in test scope). Only production code migrates to `KubeMQLogger`.

### X-3: Feature matrix referenced in two specs

Both 06-documentation-spec (REQ-DOC-2) and 08-api-completeness-spec (REQ-API-2) create/reference the feature matrix. The 08 spec owns the creation and specifies the format; the 06 spec should reference the 08 spec's artifact rather than duplicating the structure.

### X-4: Subscription return type change (08-spec) and verb alignment (09-spec) overlap

The 08-api-completeness-spec changes `subscribeToEvents()` to return `EventsSubscription`. The 09-api-design-dx-spec adds alias methods (e.g., `subscribeEvents()` as a verb-aligned alias). These changes touch the same methods. The implementation order should be: 08-spec changes return types first, then 09-spec adds aliases that also return the subscription.

---

## 4. GS Coverage Verification

| GS Requirement | Spec Section | Coverage Assessment |
|----------------|-------------|---------------------|
| REQ-TEST-1 AC: Coverage threshold enforced | 04-spec 3.2 AC-5 | Covered via REQ-TEST-5 |
| REQ-TEST-1 AC: Per-test timeout | 04-spec 3.3 | Covered (`@Timeout` annotation) |
| REQ-TEST-1 AC: Concurrent publish no-corruption | 04-spec 3.2 AC-11 | Partially covered (existing test exists, enhancement specified) |
| REQ-TEST-2 AC: Unique channel names per test | 04-spec | Already compliant; not re-specified |
| REQ-TEST-2 AC: Unsubscribe during in-flight | 04-spec | **Gap:** Not explicitly covered in test plan. Depends on 08-spec unsubscribe handle. |
| REQ-TEST-3 AC: All pipeline jobs | 04-spec REQ-TEST-3 | Covered (lint, unit matrix, integration, coverage) |
| REQ-OBS-1 AC: Instrumentation scope | 05-spec 4.3.8 | Covered (SdkVersion class) |
| REQ-OBS-2 AC: All 6 acceptance criteria | 05-spec 5.2 | Fully mapped |
| REQ-OBS-3 AC: Cardinality management | 05-spec 6.3.6 | Covered (CardinalityManager) |
| REQ-OBS-4 AC: `<1% latency overhead` | 05-spec 7.3.6 | Covered (micro-benchmark specified) |
| REQ-OBS-5 AC: Per-message DEBUG/TRACE | 05-spec 3.3.8 | Covered (log level audit table) |
| REQ-DOC-1 AC: Linter in CI | 06-spec REQ-DOC-1 | Covered (Checkstyle Javadoc rules) |
| REQ-DOC-2 AC: All 10 sections | 06-spec REQ-DOC-2 | Covered |
| REQ-DOC-3 AC: Per-pattern quick start | 06-spec REQ-DOC-3 | Covered |
| REQ-DOC-5 AC: Minimum 11 entries | 06-spec REQ-DOC-5 | Covered |
| REQ-DOC-6 AC: Keep a Changelog format | 06-spec REQ-DOC-6 | Covered |
| REQ-DOC-7 AC: Before/after code | 06-spec REQ-DOC-7 | Covered |
| REQ-API-1 AC: All core features | 08-spec 3.1 | Covered (40/44 with gaps identified) |
| REQ-API-2 AC: Feature matrix format | 08-spec 4.2 | Covered |
| REQ-API-3 AC: NotImplementedException | 08-spec 5.3 | Covered |
| REQ-DX-1 AC: Fail-fast at construction | 09-spec 3.2 | Covered (address + clientId validation) |
| REQ-DX-2 AC: 3-line publish | 09-spec REQ-DX-2 | Covered (convenience methods) |
| REQ-DX-3 AC: Same verbs cross-SDK | 09-spec REQ-DX-3 | Covered (alias + deprecation) |
| REQ-DX-4 AC: Validation before network | 09-spec REQ-DX-4 | Covered |
| REQ-DX-5 AC: Messages immutable | 09-spec REQ-DX-5 | Partially covered (deferred to v3.0; build-time validation in v2.x) |

**GS Coverage Gap:** REQ-TEST-2 AC "Unsubscribe while messages are in flight completes without resource leaks" is not explicitly addressed in the test plan. The 08-api-completeness-spec adds the unsubscribe handle, but the 04-testing-spec does not include an integration test for this scenario.

---

## 5. Dependency Accuracy Audit

| Dependency | Spec | Version | Scope | Assessment |
|-----------|------|---------|-------|-----------|
| `grpc-testing` | 04-testing | `${grpc.version}` | test | Correct -- matches existing gRPC version |
| `grpc-inprocess` | 04-testing | `${grpc.version}` | test | Correct |
| `opentelemetry-api` | 05-observability | 1.40.0 | provided | **Issue C-1:** Runtime ClassNotFoundException risk |
| `opentelemetry-bom` | 05-observability | 1.40.0 | import | Correct pattern for BOM |
| `testcontainers` | 04-testing (implied) | Not specified | test | **Missing:** Version should be specified |
| Logback scope change | 05-observability | 1.4.12 | test (from compile) | Correct -- breaks transitive dependency, documented |
| SLF4J scope change | 05-observability | N/A | provided (from compile) | Correct pattern |

---

## 6. Effort Estimate Assessment

| Spec | Stated Effort | Assessment | Notes |
|------|--------------|------------|-------|
| 04-testing | 12-18 days | Reasonable | Heavily depends on Batch 1 completion. CI setup (Phase 1) is independent and should be fast. |
| 05-observability | 15-23 days | Optimistic on low end | OTel instrumentation of 15+ source files with proper no-op fallback is complex. The proxy pattern (from C-1 fix) adds 2-3 days. Recommend 18-25 days. |
| 06-documentation | 14-22 days | Reasonable for scope | Javadoc alone (200+ methods) is the bottleneck. Consider splitting Javadoc work across multiple developers. |
| 08-api-completeness | 3-5 days | Accurate | Small, well-scoped changes. Batch send is the most complex item. |
| 09-api-design-dx | 8-12 days | Accurate | Verb deprecation cycle requires coordination with release planning. |

---

## 7. Action Items Summary

### Must Fix Before Implementation

| ID | Severity | Spec | Finding | Action |
|----|----------|------|---------|--------|
| C-1 | Critical | 05-observability | `provided` scope OTel causes NoClassDefFoundError | Adopt proxy/lazy-loading pattern for all OTel-importing classes |
| M-4 | Major | 05-observability | LoggerFactory name collision | Rename to `KubeMQLoggerFactory` |
| M-5 | Major | 05-observability | Slf4jLoggerAdapter loses stack traces | Add Throwable-aware error method |
| M-9 | Major | 08-api-completeness | Batch response missing timestamp cleanup | Add `requestTimestamps.remove()` in batch path |
| M-10 | Major | 08-api-completeness | Binary-breaking subscribe return type | Document explicitly as accepted minor break or add new method |

### Should Fix Before Implementation

| ID | Severity | Spec | Finding | Action |
|----|----------|------|---------|--------|
| M-1 | Major | 04-testing | InProcessServer scope misunderstanding | Clarify which test pattern covers which layer |
| M-2 | Major | 04-testing | Closed-client test creates real channel | Use mock transport or InProcess |
| M-3 | Major | 04-testing | Thread prefix "kubemq-" does not exist | Name SDK threads or change detection approach |
| M-6 | Major | 05-observability | LogContextProvider varargs merge | Define LogHelper.merge() or internal injection |
| M-7 | Major | 05-observability | Missing no-op tracing/metrics classes | Define NoOpTracing and NoOpMetrics |
| M-8 | Major | 06-documentation | Lombok Javadoc strategy unspecified | Add delombok or field-level Javadoc approach |
| M-11 | Major | 09-api-design-dx | ValidationException breaks IAE catch | Document or have ValidationException extend IAE |
| M-12 | Major | 09-api-design-dx | Default localhost:50000 silent in prod | Add WARN log or env var fallback |

### Nice to Fix

| ID | Severity | Spec | Finding |
|----|----------|------|---------|
| m-1 | Minor | 04-testing | Phase target should start at Phase 2 (60%) |
| m-4 | Minor | 05-observability | KubeMQSemconv loads OTel types eagerly |
| m-6 | Minor | 05-observability | OTel minimum version should be 1.32.0 not 1.31.0 |
| m-11 | Minor | 09-api-design-dx | Convenience method naming overlap with aliases |
| m-13 | Minor | 09-api-design-dx | validateOnBuild network call in constructor |

---

## 8. Conclusion

The Batch 2 specs are well-researched, with accurate source code references and thorough GS coverage. The critical finding (C-1) around OTel `provided` scope in Java must be resolved architecturally before the observability spec proceeds -- this is a Java-specific pitfall that does not exist in Go or Python. The remaining major findings are tractable and can be addressed through spec amendments without structural redesign.

The testing spec (04) provides a solid foundation but should be careful about creating tests that inadvertently depend on real server connectivity. The API completeness (08) and API design (09) specs make reasonable, well-scoped changes with honest breaking change assessments.

Recommended implementation order within Batch 2:
1. **04-testing Phase 1** (CI infrastructure) -- no dependencies, enables all subsequent work
2. **05-observability REQ-OBS-5** (logger interface) -- no dependencies, unblocks Logback removal
3. **08-api-completeness** (subscribe return types, batch send) -- small, independent
4. **09-api-design-dx Phase 1** (validation) -- depends on 01-error-handling from Batch 1
5. **05-observability REQ-OBS-1/2/3** (full OTel) -- depends on 01 and 07 from Batch 1
6. **06-documentation** (Javadoc, troubleshooting) -- can run in parallel with any phase
7. **04-testing Phases 2-3** (new tests) -- depends on features being implemented

---

## Fixes Applied (Round 1)

| Issue | Status | Change Made |
|-------|--------|-------------|
| C-1 (05-obs: `provided` scope OTel NoClassDefFoundError) | **FIXED** | Added proxy/lazy-loading pattern with `Tracing`/`Metrics` interfaces, `NoOpTracing`/`NoOpMetrics`, `TracingFactory`/`MetricsFactory`. Updated effort estimate to 18-25 days. Added prerequisite note (X-1). |
| M-1 (04-test: InProcessServer scope) | **FIXED** | Added scope clarification note distinguishing server-side vs client-side interceptor testing approaches. |
| M-2 (04-test: Closed-client test creates real channel) | **FIXED** | Added Review R1 M-2 notes and TODO markers on all test methods using `localhost:50000`, recommending mock transport or InProcessChannel. |
| M-3 (04-test: Thread prefix "kubemq-" doesn't exist) | **FIXED** | Added prerequisite note listing SDK executors that must use named ThreadFactory, and fallback to channel/executor state assertions. |
| M-4 (05-obs: LoggerFactory name collision) | **FIXED** | Renamed all SDK `LoggerFactory` references to `KubeMQLoggerFactory`. Fixed SLF4J `LoggerFactory` references that were incorrectly renamed. |
| M-5 (05-obs: Slf4jLoggerAdapter loses stack traces) | **FIXED** | Added `error(String msg, Throwable cause, Object... keysAndValues)` overload to `KubeMQLogger` interface, `NoOpLogger`, and `Slf4jLoggerAdapter`. |
| M-6 (05-obs: LogContextProvider varargs merge) | **FIXED** | Defined `LogHelper.merge()` utility class with implementation and updated call site examples. |
| M-7 (05-obs: Missing no-op tracing/metrics) | **FIXED** | Defined `Tracing` interface, `NoOpTracing`, `Metrics` interface, `NoOpMetrics` classes. Updated files changed table. |
| M-8 (06-doc: Lombok Javadoc strategy) | **FIXED** | Added `lombok-maven-plugin` delombok configuration, `maven-javadoc-plugin` sourcepath config, Lombok version requirement (1.18.22+), and table of which methods need manual vs delombok Javadoc. |
| M-9 (08-api: Batch response missing timestamp cleanup) | **FIXED** | Added `requestTimestamps.remove(refRequestID)` in batch onNext path. Added note about UUID uniqueness guaranteeing no cross-map collisions. |
| M-10 (08-api: Binary-breaking subscribe return type) | **FIXED** | Replaced vague "acceptable in minor version" with explicit justification, required mitigations (CHANGELOG, migration guide, release notes), and documented why alternative approach was rejected. Updated breaking changes table. |
| M-11 (09-dx: ValidationException breaks IAE catch) | **FIXED** | Added before/after code for migration guide, explicit documentation requirement for CHANGELOG and REQ-DOC-7. |
| M-12 (09-dx: Default localhost:50000 silent in prod) | **FIXED** | Added `KUBEMQ_ADDRESS` env var fallback, WARN log when using default address, and address resolution order documentation. |
| m-1 (04-test: Phase target should start at Phase 2) | **FIXED** | Changed JaCoCo `<minimum>` from 0.40 to 0.60, updated phased threshold table, CI config, and open question resolution. |
| m-2 (04-test: @Timeout for existing tests) | **FIXED** | Added note that `junit-platform.properties` global timeout covers all existing tests; no per-file retrofit needed in Phase 1. |
| m-3 (04-test: Missing Testcontainers version) | SKIPPED | Already specified in Section 9 (pom.xml) with version 1.20.4 and both `testcontainers` and `junit-jupiter` artifacts. |
| m-4 (05-obs: KubeMQSemconv loads OTel eagerly) | **FIXED** | Added note that KubeMQSemconv must only be loaded within OTel-available code paths per C-1 proxy pattern. |
| m-5 (05-obs: List.of() Java 9+) | **FIXED** | Added comment confirming Java 11+ minimum and noting Java 8 alternative. |
| m-6 (05-obs: OTel min version 1.31.0 vs 1.32.0) | **FIXED** | Updated all references from 1.31.0 to 1.32.0 (required for `setExplicitBucketBoundariesAdvice`). |
| m-7 (06-doc: Troubleshooting error messages) | **FIXED** | Added note to use provisional error messages marked `[provisional]` pending REQ-ERR-1. |
| m-8 (06-doc: Examples module structure) | **FIXED** | Clarified that `kubemq-java-example/` is a standalone Maven project, not a submodule. |
| m-9 (08-api: QueueSendResult.decode() not shown) | SKIPPED | Method exists in codebase; spec correctly assumes it. Verification is implementation-time concern. |
| m-10 (08-api: Feature matrix other SDK columns) | SKIPPED | Subjective; the matrix header already notes it is Java-focused. |
| m-11 (09-dx: Convenience method naming overlap) | **FIXED** | Added clarification distinguishing `publishEvent(String, byte[])` (DX-2 convenience) from `publishEvent(EventMessage)` (DX-3 alias), and their relationship to deprecated `sendEventsMessage()`. |
| m-12 (09-dx: Message immutability interim) | SKIPPED | Subjective design decision; `@Setter(AccessLevel.NONE)` approach is valid but adds complexity for a deferred feature. |
| m-13 (09-dx: validateOnBuild network call) | **FIXED** | Added Javadoc warning about network call in `build()` and DI framework incompatibility. |
| n-1 (04-test: error vs exception package) | SKIPPED | Nit; package naming is a style choice. |
| n-2 (04-test: Pseudocode stubs) | SKIPPED | Nit; spec context makes this clear. |
| n-3 (05-obs: isSLf4jAvailable typo) | **FIXED** | Corrected to `isSlf4jAvailable`. |
| n-4 (06-doc: Feature matrix location) | SKIPPED | Nit; cross-spec reference is clear enough. |
| n-5 (09-dx: IPv6 address validation) | SKIPPED | Nit; IPv6 KubeMQ usage is rare; documenting as a known limitation is sufficient. |
| X-1 (Cross-spec: Error handling deps) | **FIXED** | Added Prerequisites sections to both 04-testing-spec and 05-observability-spec. |
| X-2 (Cross-spec: Logger in test classes) | **FIXED** | Added note to 04-testing-spec that test classes use SLF4J directly, not KubeMQLogger. |
| X-3 (Cross-spec: Feature matrix location) | SKIPPED | Nit; both specs reference the same artifact path. |
| X-4 (Cross-spec: Subscribe return type + verb alias order) | **FIXED** | Added implementation ordering note to 09-api-design-dx-spec cross-category dependencies. |

**Total:** 25 fixed, 8 skipped
