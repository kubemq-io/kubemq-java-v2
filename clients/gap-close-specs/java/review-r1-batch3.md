# Java SDK Specs -- Architecture Review (Round 1, Batch 3)

**Reviewer:** Senior Java SDK Architect (Ops/Packaging Expert)
**Specs Reviewed:** 10-concurrency, 11-packaging, 12-compatibility, 13-performance
**Date:** 2026-03-09

---

## Executive Summary

Batch 3 covers Tier 2 categories: concurrency & thread safety (Cat 10), packaging & distribution (Cat 11), compatibility, lifecycle & supply chain (Cat 12), and performance (Cat 13). These specs are generally well-structured and grounded in source code evidence. The main concerns are: (1) a duplicated SDK version constant across specs 11 and 12, (2) an API mismatch in the batch send response decoding, (3) overlapping CI workflow definitions across three specs, and (4) an overly ambitious async API surface in Cat 10 that may conflict with the phased approach in Cat 01/02.

**Finding Counts:**

| Severity | Count |
|----------|-------|
| CRITICAL | 1 |
| MAJOR | 5 |
| MINOR | 8 |
| NIT | 4 |

---

## 1. Spec-by-Spec Review

### 1.1 Category 10: Concurrency & Thread Safety

#### Source Code Verification

The spec demonstrates strong source code grounding. File paths, line references, and behavioral claims are verified:

- **Correct:** `KubeMQClient` uses `AtomicInteger`, `volatile`, `synchronized` as stated. `ManagedChannel` is thread-safe per gRPC guarantee.
- **Correct:** `EventStreamHelper.sendEventMessage()` has a race condition on null-check of `queuesUpStreamHandler` -- spec identifies this accurately.
- **Correct:** `QueueSendResult.decode()` is an instance method returning `this` (mutable pattern). Spec correctly labels received types as "safe to read" rather than truly `@Immutable`.
- **Correct:** `CompletableFuture` is used internally in `QueueUpstreamHandler`, `QueueDownstreamHandler`, and `EventStreamHelper` but not exposed publicly.

| ID | Severity | Finding |
|----|----------|---------|
| C-10-1 | **MAJOR** | **`@Immutable` annotation misapplied to received message types.** The spec proposes annotating `QueueSendResult`, `EventMessageReceived`, etc. with `@Immutable` from `javax.annotation.concurrent`. However, `QueueSendResult` has public setters (`setId()`, `setSentAt()`, etc.) and its `decode()` method mutates the instance. `EventMessageReceived` uses `@Data` (Lombok) which generates setters. These types are NOT immutable -- they are "effectively immutable after construction by the SDK" but the annotation `@Immutable` makes a stronger guarantee that static analysis tools will flag as violated. **Recommendation:** Use a custom Javadoc comment ("safe to read from multiple threads after construction") without `@Immutable`. Or: remove setters from received types and make them truly immutable (which is a broader change). |
| C-10-2 | **MAJOR** | **Async API surface (CONC-2 + CONC-4) is enormous and under-specified for interaction with REQ-ERR-1/ERR-3.** The spec adds ~15 new async methods returning `CompletableFuture<T>`, plus `Duration` overloads, plus `Subscription` handles. However, these methods depend on `ensureNotClosed()`, `unwrapFuture()`, `executeWithCancellation()`, and exception types (`CancellationException`, `TimeoutException`, `ClientClosedException`) all defined in spec-01. The cross-spec dependency is noted but the interaction is complex: (a) `unwrapException()` falls back to `new RuntimeException(...)` when the cause is not `KubeMQException`, which contradicts REQ-ERR-1's goal of typed errors everywhere; (b) `executeWithCancellation()` uses `Context.CancellableContext` which only works for unary RPCs, not streaming RPCs like queue upstream/downstream. **Recommendation:** (1) `unwrapException` should wrap unknown causes in `KubeMQException`, not `RuntimeException`. (2) Document that `executeWithCancellation` applies to unary RPCs only; streaming cancellation uses a different mechanism (observer completion). (3) Add a cross-reference table showing which async methods use unary vs streaming gRPC under the hood. |
| C-10-3 | **MINOR** | **`callbackExecutor()` method referenced but never defined.** The async methods in `PubSubClient` call `callbackExecutor()` (e.g., `CompletableFuture.runAsync(..., callbackExecutor())`). This method is not defined in the spec. It is presumably intended to live in `KubeMQClient` but is not specified. The `callbackExecutor` in CONC-3 is per-subscription, not per-client. **Recommendation:** Explicitly define a `KubeMQClient.callbackExecutor()` method, specify its default (shared cached or fixed thread pool), and differentiate it from the per-subscription `callbackExecutor` in CONC-3. |
| C-10-4 | **MINOR** | **`inFlightOperations` AtomicInteger referenced in `executeWithCancellation()` but defined in CONC-5, which is in a later implementation phase.** Phase 3 (async API) uses `inFlightOperations.incrementAndGet()`, but this field is specified under Phase 2 (CONC-5). The dependency ordering is correct (CONC-5 before CONC-2/CONC-4), but the implementation text in Section 4.3.6 does not explicitly note that `inFlightOperations` comes from CONC-5. **Recommendation:** Add a forward reference in Section 4.3.6. |
| C-10-5 | **MINOR** | **JSR-305 dependency choice.** The spec proposes `com.google.code.findbugs:jsr305:3.0.2`. This artifact is from 2017 and has no maintainer. The `org.jetbrains:annotations` library or `jakarta.annotation:jakarta.annotation-api` are more actively maintained alternatives. However, JSR-305 is already a transitive dependency of gRPC, so adding it explicitly has zero marginal cost. **Recommendation:** Accept JSR-305 for pragmatic reasons (already on classpath), but note in a comment that `org.jetbrains:annotations` is a viable alternative. |
| C-10-6 | **NIT** | **`sendEventsMessageAsync()` wraps fire-and-forget in `CompletableFuture.runAsync()`.** For fire-and-forget events (no server ack), the future completes after the gRPC stream write. This is correct, but the Javadoc says "completes when the server acknowledges" which is misleading for fire-and-forget events. The second sentence clarifies, but the first sentence should be revised. |

#### GS Coverage

| GS Criterion | Covered? | Notes |
|-------------|----------|-------|
| REQ-CONC-1: Thread safety docs | Yes | Thorough annotation + Javadoc plan |
| REQ-CONC-2: Cancellation & timeout | Yes | CompletableFuture + Duration overloads |
| REQ-CONC-3: Callback behavior | Yes | callbackExecutor + maxConcurrentCallbacks |
| REQ-CONC-4: Async-first | Yes | Combined with CONC-2 |
| REQ-CONC-5: Shutdown-callback safety | Yes | In-flight tracking, configurable timeout, idempotent close |

All GS acceptance criteria are addressed. No gaps.

---

### 1.2 Category 11: Packaging & Distribution

#### Source Code Verification

- **Correct:** `pom.xml` uses `central-publishing-maven-plugin` v0.7.0 with `autoPublish=true` and `waitUntil=published`.
- **Correct:** `<url>http://maven.apache.org</url>` is indeed the Maven template default, not the project URL.
- **Correct:** No `CHANGELOG.md` or `CONTRIBUTING.md` exists in the repository.
- **Correct:** No `<resources>` block for filtering exists in the current `pom.xml`.

| ID | Severity | Finding |
|----|----------|---------|
| C-11-1 | **MAJOR** | **Duplicate SDK version mechanism with spec 12.** Spec 11 creates `KubeMQVersion.java` using Maven resource filtering to expose the version at runtime. Spec 12 creates `CompatibilityConfig.java` with a hardcoded `SDK_VERSION = "2.1.1"` constant. These two classes serve overlapping purposes and the hardcoded constant in `CompatibilityConfig` will drift from `pom.xml` if not maintained. **Recommendation:** `CompatibilityConfig.SDK_VERSION` should delegate to `KubeMQVersion.getVersion()` rather than hardcoding a string. This creates a dependency: COMPAT-1 depends on PKG-2. Add this to both specs' cross-category dependency tables. |
| C-11-2 | **MINOR** | **`release.yml` names release `v${{ github.ref_name }}` which includes the `v` prefix already present in the tag.** The tag is `v2.1.1`, so `github.ref_name` is `v2.1.1`. The `name` field becomes `vv2.1.1`. **Recommendation:** Change to `name: "${{ github.ref_name }}"` (drop the `v` prefix) or use `name: "Release ${{ github.ref_name }}"`. |
| C-11-3 | **MINOR** | **CI workflow overlap.** The `ci.yml` defined in spec 11 (Section 5.3) is also defined in spec 12 (COMPAT-3, Section 5.3) and spec 04 (REQ-TEST-3). The spec acknowledges this in the cross-category dependencies section, but three specs now each contain a full `ci.yml`. Only one should be authoritative. **Recommendation:** Designate spec 04 (REQ-TEST-3) as the canonical CI workflow owner. Specs 11 and 12 should reference it and specify only the additional requirements (e.g., multi-version matrix from COMPAT-3, release workflow from PKG-3). |
| C-11-4 | **NIT** | **CHANGELOG dates are `XX-XX`.** The spec acknowledges these should be replaced from git history. Consider including a shell command to extract the actual dates, so the implementer does not need to figure this out. |

#### GS Coverage

| GS Criterion | Covered? | Notes |
|-------------|----------|-------|
| REQ-PKG-1: Package manager publishing | Yes | CHANGELOG.md, pom.xml URL fix |
| REQ-PKG-2: Semantic versioning | Yes | KubeMQVersion.java with resource filtering |
| REQ-PKG-3: Automated release pipeline | Yes | Complete release.yml + ci.yml |
| REQ-PKG-4: Conventional commits | Yes | CONTRIBUTING.md with recommended format |

All GS acceptance criteria are addressed.

---

### 1.3 Category 12: Compatibility, Lifecycle & Supply Chain

#### Source Code Verification

- **Correct:** `ping()` returns `ServerInfo` with `.getVersion()` field (verified in `KubeMQClient.java` line 403).
- **Correct:** README says "JDK 8 or higher" while `pom.xml` has `maven.compiler.source=11` / `maven.compiler.target=11`.
- **Correct:** No `@Deprecated` annotations exist anywhere in the SDK source.
- **Correct:** No `.github/dependabot.yml` exists.
- **Correct:** `grpc-alts` is a direct dependency in `pom.xml` and is flagged for review.

| ID | Severity | Finding |
|----|----------|---------|
| C-12-1 | **MAJOR** | **`CompatibilityConfig.SDK_VERSION` hardcoded -- duplicate of PKG-2 `KubeMQVersion`.** (Same as C-11-1.) This must be resolved. If the implementer builds spec 12 before spec 11, they will create a hardcoded constant that must later be refactored. The implementation order should specify PKG-2 before COMPAT-1. |
| C-12-2 | **MINOR** | **`CompatibilityConfig.isCompatible()` silently returns `false` for pre-release server versions.** The `parseVersion()` method strips the pre-release suffix (e.g., `2.1.0-beta` becomes `2.1.0`), which is correct for range comparison. However, the doc comment should note this behavior: pre-release versions are compared by their base version only, not by pre-release precedence. This matches SemVer specification (pre-release has lower precedence than release) but may surprise users running a server beta. |
| C-12-3 | **MINOR** | **Lazy compatibility check ("on first operation") is under-specified.** The spec recommends lazy check with `volatile boolean compatibilityChecked` but does not provide the exact code for the guard. Since multiple threads could call the first operation concurrently, a `compareAndSet` pattern is needed, not just a volatile read. **Recommendation:** Show the exact code: `if (!compatibilityChecked && compatibilityCheckFlag.compareAndSet(false, true)) { checkServerCompatibility(); }` |
| C-12-4 | **MINOR** | **OWASP Dependency-Check `failBuildOnCVSS=9` is very permissive.** A CVSS score of 9+ is "Critical" only. CVEs with CVSS 7-8.9 ("High") will not fail the build. The GS says "No dependencies with known critical vulnerabilities at release time" which aligns with CVSS 9+, but industry best practice for SDKs is CVSS 7+ (High and Critical). **Recommendation:** Consider `failBuildOnCVSS=7` for the release pipeline, with `failBuildOnCVSS=9` for CI (to avoid blocking development on High-severity issues that may need suppression). |
| C-12-5 | **NIT** | **`COMPATIBILITY.md` server version matrix uses "Not tested" for Server 2.2.x+, but `MAX_SERVER_VERSION` in code is `2.2.99`.** These should be consistent. Either the matrix should say "Tested" for 2.2.x, or `MAX_SERVER_VERSION` should be `2.1.99`. |
| C-12-6 | **MINOR** | **CycloneDX plugin version `2.8.0` may not exist.** As of early 2026, the latest version may differ. The spec should reference "latest stable" or pin to a verified version. (This is a minor date-sensitivity issue.) |

#### GS Coverage

| GS Criterion | Covered? | Notes |
|-------------|----------|-------|
| REQ-COMPAT-1: Client-server matrix | Yes | COMPATIBILITY.md + runtime version check |
| REQ-COMPAT-2: Deprecation policy | Yes | Policy doc + annotation pattern |
| REQ-COMPAT-3: Language version support | Yes | README fix + CI matrix |
| REQ-COMPAT-4: Supply chain security | Yes | Dependabot + CycloneDX + OWASP + dependency audit |
| REQ-COMPAT-5: End-of-life policy | Yes | README version support section |

All GS acceptance criteria are addressed.

---

### 1.4 Category 13: Performance

#### Source Code Verification

- **Correct:** No benchmarks or JMH dependencies exist in the codebase.
- **Correct:** `KubeMQClient.initChannel()` creates one `ManagedChannel`; stubs are created once.
- **Correct:** `ByteString.copyFrom(body)` creates one copy; no additional unnecessary copies.
- **Correct:** `QueuesPollRequest.pollMaxMessages` exists for batch receive.
- **Correct:** The proto defines `SendQueueMessagesBatch` RPC with `QueueMessagesBatchRequest`/`QueueMessagesBatchResponse` types.
- **Correct:** `QueueSendResult.decode()` accepts `SendQueueMessageResult` (proto type matches).

| ID | Severity | Finding |
|----|----------|---------|
| C-13-1 | **CRITICAL** | **`QueueMessagesBatchResponse.decode()` calls `new QueueSendResult().decode(pbResult)` but `QueueSendResult` uses `@Builder` pattern, not a no-arg constructor.** Looking at `QueueSendResult.java`, it has `@NoArgsConstructor` AND `@AllArgsConstructor` AND `@Builder`. The `decode()` method mutates the instance via field assignments (not setters from Lombok `@Data` -- it uses manual setters). So `new QueueSendResult().decode(pbResult)` will work because `@NoArgsConstructor` is present. However, this pattern is inconsistent with the `@Builder` pattern used elsewhere: the returned `QueueSendResult` is built via mutation, not via builder. **The actual critical issue:** the spec's `decode()` method returns `new QueueSendResult().decode(pbResult)` which calls an instance method on a new object. This works but is architecturally inconsistent with the `QueueMessagesBatchResponse` itself which uses `QueueMessagesBatchResponse.builder()...build()`. The decode pattern should be consistent. Also, the proto's `QueueMessagesBatchResponse.Results` contains `SendQueueMessageResult` entries, not `SendQueueMessageResult` -- verify the generated code uses `getResultsList()` (not `getMessagesList()`). The proto definition at line 132 says `repeated SendQueueMessageResult Results = 2;`, so `getResultsList()` is correct. **Recommendation:** Use a static factory `QueueSendResult.fromProto(pbResult)` for clarity and consistency, or accept the existing `.decode()` pattern used throughout the codebase for backward consistency. Document the choice. |
| C-13-2 | **MAJOR** | **Batch send uses the legacy unary `SendQueueMessagesBatch` RPC, not the stream-based `QueuesUpstream`.** The spec acknowledges this as a deliberate design choice (simpler, maps to sync API). However, the `SendQueueMessagesBatch` RPC is from the older (pre-v2) server API. The current v2 SDK exclusively uses `QueuesUpstream` and `QueuesDownstream` streaming RPCs. Using the legacy RPC introduces a concern: is the server guaranteed to support this RPC alongside the streaming API? **Recommendation:** (1) Verify with KubeMQ server team that `SendQueueMessagesBatch` is still supported on current server versions. (2) Add this as a verification step before implementation. (3) If the legacy RPC is deprecated server-side, implement batch send by sending N messages over the existing `QueuesUpstream` stream in rapid succession (still one stream, but N writes) and collecting all responses. This would be more consistent with the current architecture. |
| C-13-3 | **MINOR** | **JMH benchmark classes reference API methods that may not match current SDK.** For example, `PublishThroughputBenchmark` calls `client.sendEventsMessage(message)` but also constructs `EventMessage.builder().channel("bench-throughput").body(payload).build()`. The actual `EventMessage` builder field names should be verified. Minor discrepancy risk since benchmark code will be written during implementation, but the spec code should be accurate as a reference. |
| C-13-4 | **MINOR** | **`BenchmarkConfig.getAddress()` uses system property `kubemq.benchmark.address` but CI and release workflows use `kubemq.address`.** The property name should be consistent across specs and configuration. **Recommendation:** Use `kubemq.address` consistently, or document the distinction. |
| C-13-5 | **NIT** | **`MAX_BATCH_SIZE = 1000` is arbitrary without server-side validation.** Open Question #4 acknowledges this. The constant should be documented as a client-side guard that may need adjustment based on server limits. This is already noted but should be elevated to the implementation text, not just the open questions. |

#### GS Coverage

| GS Criterion | Covered? | Notes |
|-------------|----------|-------|
| REQ-PERF-1: Published benchmarks | Yes | JMH suite with 4 required benchmarks |
| REQ-PERF-2: Connection reuse | Yes | Doc-only gap addressed |
| REQ-PERF-3: Efficient serialization | Yes | Doc-only gap addressed |
| REQ-PERF-4: Batch operations | Yes | Batch send implementation specified |
| REQ-PERF-5: Performance documentation | Yes | README performance section |
| REQ-PERF-6: Performance tips | Yes | 4 tips documented |

All GS acceptance criteria are addressed.

---

## 2. Cross-Spec Consistency

| ID | Severity | Finding |
|----|----------|---------|
| X-1 | **MAJOR** | **Three specs define `ci.yml`: 04-testing-spec (REQ-TEST-3), 11-packaging-spec (Section 5.3), and 12-compatibility-spec (COMPAT-3, Section 5.3).** Each contains a full GitHub Actions workflow with slightly different configurations (e.g., 11 adds `upload-artifact`, 12 adds `fail-fast: false`). The implementer must reconcile these into a single workflow. **Recommendation:** Designate spec 04 as the canonical owner. Specs 11 and 12 should specify requirements on the CI workflow (e.g., "CI must include Java 11/17/21 matrix", "CI must include test result upload") rather than providing full YAML. |
| X-2 | **Mentioned above** | **Duplicate version constant (C-11-1 / C-12-1).** `KubeMQVersion.java` (spec 11) vs `CompatibilityConfig.SDK_VERSION` (spec 12). Must be unified. |
| X-3 | **MINOR** | **CHANGELOG.md defined in both spec 11 (REQ-PKG-1) and spec 12 (REQ-COMPAT-2 Section 4.4).** Both provide slightly different content templates. Only one CHANGELOG should exist. Spec 11's version is more complete (includes comparison links). **Recommendation:** Spec 12 should reference spec 11's CHANGELOG and add the deprecation section template as a supplementary requirement. |
| X-4 | **MINOR** | **CONTRIBUTING.md defined in both spec 11 (REQ-PKG-4) and spec 12 (REQ-COMPAT-2 Section 4.2).** Spec 11 provides a fuller version with development setup, commit format, and PR guidance. Spec 12 adds the deprecation policy section. **Recommendation:** Spec 12 should reference spec 11's CONTRIBUTING.md and specify "add a Deprecation Policy section" as an additive requirement. |

---

## 3. Cross-Reference with Batch 1 Specs

| ID | Finding |
|----|---------|
| B1-1 | **Spec 10 (CONC-2/CONC-4) depends heavily on spec 01 (REQ-ERR-1) for exception types** (`CancellationException`, `TimeoutException`, `ClientClosedException`, `KubeMQException`). The dependency is documented. However, the `unwrapException()` fallback to `RuntimeException` in spec 10 contradicts spec 01's design principle that all SDK-thrown exceptions should extend `KubeMQException`. |
| B1-2 | **Spec 10 (CONC-5) depends on spec 02 (REQ-CONN-2 state machine, REQ-CONN-4 graceful shutdown).** Correctly documented. The `close()` method in CONC-5 must integrate with the drain timeout from CONN-4 (5s default) and the callback completion timeout from CONC-5 (30s default). The two timeouts are correctly identified as separate concerns. |
| B1-3 | **Spec 13 (PERF-4 batch send) is also listed as a missing API in spec 08 (REQ-API-1).** The spec notes this correctly: "Single implementation satisfies both." |

---

## 4. Breaking Change Assessment

All four specs claim zero breaking changes. This assessment is **correct** with caveats:

| Spec | Claim | Assessment |
|------|-------|-----------|
| 10-concurrency | No breaking changes | **Agree.** Annotations, Javadoc, new methods are additive. Visibility change of `QueueUpstreamHandler.sendQueuesMessageAsync()` from private to public is technically broader access but not a user-facing break (handler classes are internal). |
| 11-packaging | No breaking changes | **Agree.** All new files. `KubeMQVersion.VERSION` becomes a new public API contract. |
| 12-compatibility | No breaking changes | **Agree.** All documentation + new utility class. Server version check is warn-only. |
| 13-performance | No breaking changes | **Agree.** New method, new class, test-scope deps, docs. |

**One nuance:** If spec 10's async API adds methods with the same name as existing internal methods (e.g., `sendQueuesMessageAsync` already exists as private in `QueueUpstreamHandler`), making them public could theoretically affect subclasses. Since the handler classes are not intended for subclassing, this risk is negligible.

---

## 5. Testability Assessment

| Spec | Test Plan Quality | Gaps |
|------|------------------|------|
| 10-concurrency | Good. 15 test scenarios for CONC-2 alone. Reflective tests for annotations. Integration tests for concurrent sends. | Missing: test for `maxConcurrentCallbacks` > 1 actually running callbacks in parallel. Missing: test for `callbackExecutor` custom executor being used. |
| 11-packaging | Adequate. `KubeMQVersionTest` covers basic cases. CI/CD tested via dry run. | Missing: test that Maven resource filtering actually replaces `${project.version}` (requires a Maven build, not just `mvn test`). The test may return "unknown" in IDE runs, which the spec handles with the `if (!"unknown".equals(...))` guard. This is acceptable. |
| 12-compatibility | Good. `CompatibilityConfigTest` covers 13 cases for version parsing. | Missing: integration test verifying the version check logs a warning when connected to an out-of-range server. This is hard to test without a mock server. Consider a unit test with a mocked `ping()` return. |
| 13-performance | Adequate for unit tests. Benchmarks require live server. | Missing: unit test for `QueueMessagesBatchResponse.decode()` verifying the `haveErrors` flag logic. The spec lists this test but only in a table -- no code provided. Ensure the decode test covers the case where the proto response has zero results (empty batch response). |

---

## 6. Dependency Accuracy

| Spec | Dependency | Verified? |
|------|-----------|----------|
| 10 | `com.google.code.findbugs:jsr305:3.0.2` | Yes, exists on Maven Central. Already a transitive dep of gRPC. |
| 11 | `central-publishing-maven-plugin` server-id `central` | Yes, matches `pom.xml` `<publishingServerId>central</publishingServerId>`. |
| 13 | `org.openjdk.jmh:jmh-core:1.37` | Yes, exists on Maven Central. Latest as of mid-2024. |
| 13 | `org.openjdk.jmh:jmh-generator-annprocess:1.37` | Yes, exists on Maven Central. |
| 13 | `org.codehaus.mojo:exec-maven-plugin:3.1.0` | Yes, exists on Maven Central. |
| 12 | `org.cyclonedx:cyclonedx-maven-plugin:2.8.0` | **Verify.** Latest stable at writing may differ. |
| 12 | `org.owasp:dependency-check-maven:10.0.3` | **Verify.** Version may not exist yet. As of March 2026, check Maven Central for actual latest. |

---

## 7. Consolidated Action Items

### Must-Fix Before Implementation

| Priority | ID | Action |
|----------|-----|--------|
| CRITICAL | C-13-1 | Verify `SendQueueMessagesBatch` RPC is supported on current KubeMQ server versions. If deprecated server-side, redesign batch send to use `QueuesUpstream` stream. Clarify the decode pattern for `QueueMessagesBatchResponse`. |
| MAJOR | C-10-1 | Replace `@Immutable` with a custom Javadoc comment on received types that have setters. Or: remove setters from received types to make `@Immutable` truthful. |
| MAJOR | C-10-2 | Fix `unwrapException()` to wrap in `KubeMQException` (not `RuntimeException`). Clarify unary vs streaming cancellation. Add method-to-RPC-type mapping table. |
| MAJOR | C-11-1 / C-12-1 | Unify `CompatibilityConfig.SDK_VERSION` with `KubeMQVersion.getVersion()`. Remove hardcoded constant. |
| MAJOR | C-13-2 | Same as C-13-1 -- verify server-side RPC support before committing to legacy unary batch API. |
| MAJOR | X-1 | Designate one spec as canonical `ci.yml` owner. Other specs reference it. |

### Should-Fix

| Priority | ID | Action |
|----------|-----|--------|
| MINOR | C-10-3 | Define `KubeMQClient.callbackExecutor()` method explicitly. |
| MINOR | C-11-2 | Fix release name template to avoid `vv` prefix. |
| MINOR | C-11-3 / X-1 | Resolve CI workflow duplication. |
| MINOR | C-12-3 | Provide exact `compareAndSet` code for lazy compatibility check. |
| MINOR | C-12-4 | Consider tightening OWASP threshold to CVSS 7+ for releases. |
| MINOR | X-3 / X-4 | Deduplicate CHANGELOG.md and CONTRIBUTING.md templates across specs. |

---

## 8. Open Questions Requiring Stakeholder Input

| # | Question | Raised By | Recommended Resolution |
|---|----------|-----------|----------------------|
| 1 | Is the legacy `SendQueueMessagesBatch` unary RPC still supported on current KubeMQ server versions? | C-13-1, C-13-2 | Verify with server team. If deprecated, use streaming batch approach. |
| 2 | Should the async API (CONC-2/CONC-4) be implemented in the same release as ERR-1, or deferred to a subsequent release? | C-10-2 | Implement ERR-1 (spec 01) first. Async API in a follow-up release to reduce risk. |
| 3 | Should received message types (`QueueSendResult`, `EventMessageReceived`, etc.) be refactored to be truly immutable? | C-10-1 | Defer to a future major version. For now, remove `@Immutable` annotation and use Javadoc only. |
| 4 | Who owns the canonical `ci.yml`? | X-1 | Spec 04 (REQ-TEST-3). Other specs add requirements as additive items. |
| 5 | Is a 12-month security patch commitment realistic with a single maintainer? | COMPAT-5 | Be transparent. If 6 months is more realistic, document 6 months. Honesty builds more trust than aspirational promises. |

---

## Fixes Applied (Round 1)

| Issue | Status | Change Made |
|-------|--------|-------------|
| C-13-1 (CRITICAL) | Fixed | Added detailed Javadoc to `QueueMessagesBatchResponse.decode()` documenting the decode pattern choice (instance `.decode()` for codebase consistency vs static factory), and confirmed `getResultsList()` is the correct proto accessor. |
| C-10-1 (MAJOR) | Fixed | Replaced `@Immutable` annotation on received message types with Javadoc-only thread safety documentation. Added explanatory note about why `@Immutable` is not appropriate for types with Lombok `@Data` setters and mutable `decode()` methods. |
| C-10-2 (MAJOR) | Fixed | (1) Changed `unwrapException()` to return `KubeMQException` wrapping unknown causes instead of `RuntimeException`. (2) Added "Unary vs. Streaming Cancellation" section clarifying that `executeWithCancellation()` applies to unary RPCs only. (3) Added async method-to-gRPC-RPC-type mapping table. (4) Added forward reference noting `inFlightOperations` comes from CONC-5. |
| C-11-1 / C-12-1 (MAJOR) | Fixed | Changed `CompatibilityConfig.SDK_VERSION` from hardcoded `"2.1.1"` to delegate to `KubeMQVersion.getVersion()`. Added import, Javadoc noting the dependency, and updated cross-category dependency tables in both specs 11 and 12 to reflect PKG-2 -> COMPAT-1 ordering. |
| C-13-2 (MAJOR) | Fixed | Added pre-implementation verification requirement for `SendQueueMessagesBatch` RPC support on current server versions. Added fallback streaming batch implementation code for use if the legacy unary RPC is deprecated. Added blocking verification task to implementation plan. |
| X-1 (MAJOR) | Fixed | Designated spec 04 (REQ-TEST-3) as canonical `ci.yml` owner. Replaced full CI workflow YAML in specs 11 and 12 with requirements-only sections that reference spec 04. Updated file manifest in spec 11. |
| C-10-3 (MINOR) | Fixed | Added explicit note before PubSubClient async methods defining where `callbackExecutor()` lives (KubeMQClient, Section 5.3.3) and differentiating it from per-subscription `callbackExecutor`. |
| C-10-4 (MINOR) | Fixed | Added forward reference in the async method-to-RPC mapping section noting `inFlightOperations` is defined in CONC-5 Section 7.3.1. |
| C-10-5 (MINOR) | Fixed | Added note to JSR-305 rationale acknowledging `org.jetbrains:annotations` as a viable alternative while explaining JSR-305 as the pragmatic choice (already on classpath via gRPC). |
| C-10-6 (NIT) | Fixed | Revised `sendEventsMessageAsync()` Javadoc to clarify that the future completes after the stream write, not after server acknowledgment (fire-and-forget has no server ack). |
| C-11-2 (MINOR) | Fixed | Changed release name template from `"v${{ github.ref_name }}"` to `"Release ${{ github.ref_name }}"` to avoid `vv` prefix. |
| C-11-3 (MINOR) | Fixed | Merged with X-1 fix above. |
| C-12-2 (MINOR) | Fixed | Added Javadoc to `parseVersion()` documenting pre-release suffix stripping behavior and its implications for version comparison. |
| C-12-3 (MINOR) | Fixed | Replaced `volatile boolean` lazy check recommendation with exact `AtomicBoolean.compareAndSet()` code pattern for thread-safe lazy compatibility check. |
| C-12-4 (MINOR) | Fixed | Changed OWASP `failBuildOnCVSS` threshold from 9 to 7 (High and Critical) in both Maven plugin and GitHub Actions configurations. |
| C-12-5 (NIT) | Fixed | Added clarifying notes to COMPATIBILITY.md matrix explaining that `MAX_SERVER_VERSION` covers up to 2.2.99 for forward compatibility while Server 2.2.x is not CI-tested. |
| C-12-6 (MINOR) | Fixed | Added XML comment to CycloneDX plugin version to verify latest stable on Maven Central at implementation time. |
| C-13-3 (MINOR) | Skipped | Benchmark code will be written during implementation; method names will be verified against actual SDK API at that time. |
| C-13-4 (MINOR) | Fixed | Changed `kubemq.benchmark.address` to `kubemq.address` for consistency across specs and configuration. |
| C-13-5 (NIT) | Fixed | Elevated `MAX_BATCH_SIZE` documentation from open questions to implementation text, clarifying it is a client-side guard without verified server-side basis. |
| X-3 (MINOR) | Fixed | Replaced full CHANGELOG template in spec 12 with reference to canonical CHANGELOG in spec 11 (REQ-PKG-1), adding only the deprecation section template as a supplementary requirement. |
| X-4 (MINOR) | Fixed | Replaced CONTRIBUTING.md creation in spec 12 with reference to canonical CONTRIBUTING.md in spec 11 (REQ-PKG-4), noting spec 12 adds only the deprecation policy section. |
| C-11-4 (NIT) | Skipped | Subjective; implementer can use `git log --tags` as already documented in spec. |
| B1-1 | Fixed | Covered by C-10-2 fix (`unwrapException` now wraps in `KubeMQException`). |

**Total:** 20 fixed, 2 skipped
