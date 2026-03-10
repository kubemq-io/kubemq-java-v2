# Java SDK — Gap Close Spec Summary

**Generated:** 2026-03-09
**SDK:** Java (KubeMQ Java v2, version 2.1.1)
**Specs Generated:** 13
**Coverage:** 73/73 gap items (100%) + 18 cross-category dependencies + 5 remediation items = 96/96 total items

---

## Spec Overview

| # | Category | Spec File | Status | Priority | Effort | Breaking Changes | Files Created | Files Modified |
|---|----------|-----------|--------|----------|--------|-----------------|---------------|----------------|
| 01 | Error Handling & Resilience | `01-error-handling-spec.md` | 9 REQs (5 MISSING, 4 PARTIAL) | P0 | 15-22 days | Yes (3: error callback type, exception types, enableRetry removal) | 24 new files | 7+ existing files |
| 02 | Connection & Transport | `02-connection-transport-spec.md` | 6 REQs (1 MISSING, 5 PARTIAL) | P0 | 8-12 days | No | 8+ new files | 6+ existing files |
| 03 | Auth & Security | `03-auth-security-spec.md` | 6 REQs (2 NOT_ASSESSED, 4 PARTIAL) | P0 | 7-12 days | Yes (4: TLS field type, TLS defaults, interceptor rename, metadata field) | 9 new files | 4 existing files |
| 04 | Testing | `04-testing-spec.md` | 5 REQs (1 MISSING, 4 PARTIAL) | P0 | 12-18 days | No | 10+ new test files | pom.xml, CI config |
| 05 | Observability | `05-observability-spec.md` | 5 REQs (3 MISSING, 2 PARTIAL) | P0 | 18-25 days | Yes (4: Logback removal, Level enum, setLogLevel, trace context tags) | 15+ new files | 15+ existing files |
| 06 | Documentation | `06-documentation-spec.md` | 7 REQs (4 MISSING, 3 PARTIAL) | P0 | 14-22 days | No | 4+ new docs | 50+ files (Javadoc) |
| 07 | Code Quality & Architecture | `07-code-quality-spec.md` | 7 REQs (1 MISSING, 5 PARTIAL, 1 NOT_ASSESSED) | P1 | 10-16 days | Yes (minor: internal class visibility) | 11 new files | 11 existing files, 1 deleted |
| 08 | API Completeness | `08-api-completeness-spec.md` | 3 REQs (2 MISSING, 1 PARTIAL) | P3 | 3-5 days | Yes (4: binary-incompatible subscribe return types) | 2 new files | 4 existing files |
| 09 | API Design & DX | `09-api-design-dx-spec.md` | 5 REQs (all PARTIAL) | P2-P3 | 8-12 days | Yes (2 narrowly breaking: ValidationException, default address; 2 deferred to v3.0) | 0 new files | All client + message classes |
| 10 | Concurrency & Thread Safety | `10-concurrency-spec.md` | 5 REQs (2 MISSING, 3 PARTIAL) | P2-P3 | 10-15 days | No | 1 new file (Subscription.java) | 20+ existing files |
| 11 | Packaging & Distribution | `11-packaging-spec.md` | 4 REQs (2 MISSING, 2 PARTIAL) | P2-P3 | 5-8 days | No | 5 new files | pom.xml |
| 12 | Compatibility & Lifecycle | `12-compatibility-spec.md` | 5 REQs (4 MISSING, 1 PARTIAL) | P2-P3 | 5-8 days | No | 5+ new files | pom.xml, README |
| 13 | Performance | `13-performance-spec.md` | 6 REQs (3 MISSING, 2 COMPLIANT, 1 PARTIAL) | P2 | 5-9 days | No | 7 new files | pom.xml, README |

---

## Implementation Sequence

### Phase 1 — Foundation (Weeks 1-4)

| Order | Spec | Items | Effort | Rationale |
|-------|------|-------|--------|-----------|
| 1 | 01 Error Handling | Error hierarchy, classification, gRPC mapping, retry | 15-22d | Foundation for all other specs; most specs depend on error types |
| 2 | 02 Connection & Transport | State machine, reconnection, buffering, keepalive | 8-12d | Required by auth (AUTH-6), observability (OBS-3), concurrency (CONC-5) |
| 3 | 03 Auth & Security | Mutable token, TLS, credential provider | 7-12d | Depends on 01 (error types), 02 (reconnection) |
| 4 | 07 Code Quality | Linting, dependency cleanup, transport extraction | 10-16d | Enables clean architecture for all subsequent work |

### Phase 2 — Core Features (Weeks 5-8)

| Order | Spec | Items | Effort | Rationale |
|-------|------|-------|--------|-----------|
| 5 | 04 Testing (Phase 1) | CI infrastructure, JaCoCo, test deps | 3-5d | No Batch 1 deps for Phase 1; enables quality gates |
| 6 | 05 Observability (Phase 1) | Logger interface, SLF4J adapter | 1-3d | No Batch 1 deps for logger; unblocks Logback removal |
| 7 | 08 API Completeness | Subscribe return types, batch send | 3-5d | No Batch 1 deps; small scope |
| 8 | 09 API Design & DX (Phase 1) | Validation, defaults, convenience methods | 4-6d | Depends on 01 (ValidationException) |
| 9 | 05 Observability (Phases 2-3) | OTel tracing, metrics, trace propagation | 15-22d | Depends on 01, 02, 07 |

### Phase 3 — Quality & Polish (Weeks 9-12)

| Order | Spec | Items | Effort | Rationale |
|-------|------|-------|--------|-----------|
| 10 | 10 Concurrency | Thread safety docs, async API, shutdown safety | 10-15d | Depends on 01, 02 |
| 11 | 11 Packaging | CHANGELOG, version constant, release pipeline | 5-8d | No blockers for Phase 1 items |
| 12 | 12 Compatibility | Version matrix, deprecation policy, supply chain | 5-8d | Depends on 11 (PKG-2 for version) |
| 13 | 13 Performance | Benchmarks, batch send, perf docs | 5-9d | Batch send blocked on RPC verification |
| 14 | 06 Documentation | Javadoc, README, troubleshooting, migration guide | 14-22d | Can run in parallel throughout |
| 15 | 04 Testing (Phases 2-3) | New unit + integration tests | 9-13d | Depends on features being implemented |
| 16 | 09 API Design & DX (Phase 2) | Verb alignment, message validation | 4-6d | Depends on 08 (subscribe return types) |

---

## Breaking Changes Summary

| Spec | Change | Severity | Release Target |
|------|--------|----------|----------------|
| 01 | Error callback `Consumer<String>` -> `Consumer<KubeMQException>` | Medium | v2.2.0 (with deprecated overload) |
| 01 | Methods throw `KubeMQException` instead of `RuntimeException` | Low (subtype of RuntimeException) | v2.2.0 |
| 01 | `.enableRetry()` removed from gRPC channel | Low | v2.2.0 |
| 03 | `tls` field type `boolean` -> `Boolean` | Low | v2.2.0 |
| 03 | Remote addresses default to TLS | Medium | v2.2.0 |
| 03 | `MetadataInterceptor` renamed to `AuthInterceptor` | Low (internal) | v2.2.0 |
| 05 | Logback removed from compile scope | Medium | v2.2.0 |
| 05 | `KubeMQClient.Level` enum replaced | Low (deprecated alias kept) | v2.2.0 |
| 05 | Trace context keys added to message tags | Low | v2.2.0 |
| 07 | Internal classes made package-private | Low (only affects users importing internals) | v2.2.0 |
| 08 | Subscribe methods return subscription instead of void | Binary-incompatible (source-compatible) | v2.2.0 |
| 09 | `IllegalArgumentException` -> `ValidationException` | Low (unlikely catch pattern) | v2.2.0 |
| 09 | Remove message setters (immutability) | High | **Deferred to v3.0** |
| 09 | Remove deprecated method names | High | **Deferred to v3.0** |

---

## New Files to Create (Combined)

| Package / Location | Files | Source Spec |
|-------------------|-------|-------------|
| `io.kubemq.sdk.exception` | `KubeMQException`, `ErrorCode`, `ErrorCategory`, `ErrorClassifier`, `ErrorMessageBuilder`, `GrpcErrorMapper`, `ConnectionException`, `AuthenticationException`, `AuthorizationException`, `TimeoutException`, `ValidationException`, `ServerException`, `ThrottlingException`, `OperationCancelledException`, `BackpressureException`, `StreamBrokenException`, `TransportException`, `HandlerException`, `RetryThrottledException`, `PartialFailureException`, `NotImplementedException`, `ConnectionNotReadyException`, `ClientClosedException`, `ConfigurationException` | 01, 02, 03, 08 |
| `io.kubemq.sdk.retry` | `RetryPolicy`, `RetryExecutor`, `OperationSafety` | 01 |
| `io.kubemq.sdk.common` | `Defaults`, `Internal`, `KubeMQVersion` | 01, 07, 11 |
| `io.kubemq.sdk.client` | `ConnectionState`, `ConnectionStateListener`, `ConnectionStateMachine`, `ReconnectionConfig`, `BufferOverflowPolicy`, `BufferedMessage`, `MessageBuffer`, `ReconnectionManager`, `Subscription` | 02, 10 |
| `io.kubemq.sdk.auth` | `CredentialProvider`, `TokenResult`, `CredentialException`, `StaticTokenProvider`, `CredentialManager` | 03 |
| `io.kubemq.sdk.transport` | `Transport`, `GrpcTransport`, `TransportConfig`, `TransportFactory`, `AuthInterceptor`, `ErrorMappingInterceptor` | 07 |
| `io.kubemq.sdk.observability` | `KubeMQLogger`, `NoOpLogger`, `Slf4jLoggerAdapter`, `KubeMQLoggerFactory`, `LogHelper`, `LogContextProvider`, `Tracing`, `NoOpTracing`, `Metrics`, `NoOpMetrics`, `OTelAvailability`, `TracingFactory`, `MetricsFactory`, `KubeMQSemconv`, `KubeMQTracing`, `KubeMQMetrics`, `SdkVersion`, `KubeMQTagsCarrier`, `CardinalityConfig`, `CardinalityManager` | 05 |
| Build config | `checkstyle.xml`, `checkstyle-suppressions.xml`, `dependency-check-suppressions.xml` | 07 |
| GitHub config | `.github/PULL_REQUEST_TEMPLATE.md`, `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `.github/dependabot.yml` | 04, 07, 11, 12 |
| Documentation | `CHANGELOG.md`, `CONTRIBUTING.md`, `TROUBLESHOOTING.md`, `MIGRATION.md`, `COMPATIBILITY.md`, `BENCHMARKS.md`, `feature-matrix.md` | 06, 08, 11, 12, 13 |
| Benchmarks | `PublishThroughputBenchmark`, `PublishLatencyBenchmark`, `QueueRoundtripBenchmark`, `ConnectionSetupBenchmark`, `BenchmarkConfig` | 13 |
| Test utilities | `testutil/` package, `junit-platform.properties` | 04 |

**Estimated total new files: ~85+**

---

## Files to Modify (Combined)

| File | Modified By Specs |
|------|------------------|
| `KubeMQClient.java` | 01, 02, 03, 05, 07, 09, 10, 12 |
| `PubSubClient.java` | 01, 03, 05, 08, 09, 10 |
| `CQClient.java` | 01, 03, 05, 08, 09, 10 |
| `QueuesClient.java` | 01, 03, 05, 08, 09, 10, 13 |
| `QueueUpstreamHandler.java` | 01, 07, 08, 10 |
| `QueueDownstreamHandler.java` | 01, 07, 10 |
| `EventStreamHelper.java` | 01, 07, 10 |
| `EventsSubscription.java` | 01, 02, 07, 10 |
| `EventsStoreSubscription.java` | 01, 02, 07, 10 |
| `CommandsSubscription.java` | 01, 02, 07, 10 |
| `QueriesSubscription.java` | 01, 02, 07, 10 |
| `KubeMQUtils.java` | 07, 09 |
| `ChannelDecoder.java` | 07 |
| `pom.xml` | 04, 05, 06, 07, 12, 13 |
| `README.md` | 06, 11, 12, 13 |
| All message classes (6 files) | 09, 10 |
| All received message types (5 files) | 10 |
| All existing exception classes (4 files) | 01 |

**Estimated total modified files: ~40+**

**Deleted files: 1** (`QueueDownStreamProcessor.java` -- unused dead code)

---

## Total Effort

| Metric | Estimate |
|--------|----------|
| Total effort (all specs) | 120-184 days |
| Phase 1 — Foundation | 40-62 days |
| Phase 2 — Core Features | 26-41 days |
| Phase 3 — Quality & Polish | 54-81 days |
| Parallelizable reduction (est.) | 30-40% |
| **Adjusted calendar time (1 developer)** | **~84-130 days** |

---

## Risks and Open Questions

### Critical Risks

1. **Legacy `SendQueueMessagesBatch` RPC support** (Spec 13, PERF-4): Must verify with KubeMQ server team whether this RPC is still supported on current server versions. Blocks batch send implementation. Fallback streaming approach specified but requires additional design work.

2. **OTel `provided` scope class loading** (Spec 05): Java's `provided` scope causes `NoClassDefFoundError` if OTel-importing classes are loaded when OTel is absent. Mitigated via proxy/lazy-loading pattern (resolved in R1), but complexity remains high.

3. **Batch 1 completion gates Batch 2/3**: Error types (spec 01) are foundational. Delays cascade to ~40% of Batch 2 effort and most of Batch 3.

### Open Questions (from all specs)

| # | Question | Source |
|---|----------|--------|
| 1 | Should `TimeoutException` be renamed to avoid JDK clash? | 01 |
| 2 | Should received message types be made truly immutable? | 10 |
| 3 | Is 12-month security patch commitment realistic with a single maintainer? | 12 |
| 4 | Who owns the canonical `ci.yml`? | 04/11/12 (resolved: spec 04) |
| 5 | Can BENCHMARKS.md be merged with TBD placeholders? | 13 (resolved: yes) |
| 6 | How does `WaitForReady` interact with `RetryExecutor`? | 01/02 |
| 7 | Should the `Transport` interface be in the public API? | 07 |
| 8 | Implementation ordering between specs 01 and 07? | 01/07 (resolved: 01 first) |

---

## Review Impact

### Round 1 (Architecture Review)

Reviewed by Senior Java SDK Architect across 3 batches. Focus: architectural soundness, Java-specific correctness, cross-spec consistency, GS coverage.

| Batch | Specs Reviewed | Critical | Major | Minor | Fixed | Skipped |
|-------|---------------|----------|-------|-------|-------|---------|
| Batch 1 | 01, 02, 03, 07 | 5 | 11 | 10 | 22 | 9 |
| Batch 2 | 04, 05, 06, 08, 09 | 1 | 12 | 13 | 25 | 8 |
| Batch 3 | 10, 11, 12, 13 | 1 | 5 | 8 | 20 | 2 |
| **Total R1** | **13 specs** | **7** | **28** | **31** | **67** | **19** |

Key R1 fixes:
- Invalid Java import syntax (import aliases do not exist in Java)
- Race condition in MessageBuffer CAS logic
- Duplicate exception types across specs (BufferFullException vs BackpressureException)
- Static `ThreadLocalRandom` field (concurrency bug)
- Recursive CAS (StackOverflow risk)
- OTel `provided` scope `NoClassDefFoundError` (proxy pattern added)
- LoggerFactory name collision with SLF4J
- Three specs defining conflicting `ci.yml`
- Duplicate SDK version constant across specs 11 and 12

### Round 2 (Implementability Review)

Reviewed by Senior Java Developer (implementer perspective) across 3 batches. Focus: practical implementability, missing details, test feasibility, commit sequences.

| Batch | Specs Reviewed | Critical/Blocker | Major | Minor | Fixed | Skipped |
|-------|---------------|-----------------|-------|-------|-------|---------|
| Batch 1 | 01, 02, 03, 07 | 1 | 8 | 10 | 15 | 5 |
| Batch 2 | 04, 05, 06, 08, 09 | 0 | 0 | 6 | 3 | 6 |
| Batch 3 | 10, 11, 12, 13 | 1 | 4 | 7 | 13 | 0 |
| **Total R2** | **13 specs** | **2** | **12** | **23** | **31** | **11** |

Key R2 fixes:
- Missing `ensureReady()` method body (critical integration point)
- `sendBufferedMessage()` method never defined
- RetryExecutor does not check connection state between retries
- Batch send blocked on legacy RPC verification
- Single-threaded async executor bottleneck (split into two executors)
- JMH `exec-maven-plugin` replaced with `maven-shade-plugin`
- `.github/` workflow paths corrected to repository root
- Missing `ConnectionException` stub for cross-spec implementation ordering

---

## Recommended Next Steps

1. **Verify `SendQueueMessagesBatch` RPC** with KubeMQ server team (day 1 blocker for PERF-4)
2. **Implement Spec 01 (Error Handling)** -- foundation for everything else
3. **Set up CI** (Spec 04 Phase 1) -- in parallel with spec 01; no dependencies
4. **Begin Spec 11 Phase 1** (CHANGELOG, CONTRIBUTING, KubeMQVersion) -- zero dependencies, immediate value
5. **Begin Spec 12 quick wins** (README JDK fix, EOL policy) -- zero dependencies
6. **Plan for breaking changes** -- all v2.2.0 breaking changes should be batched into a single release with comprehensive migration guide (Spec 06, REQ-DOC-7)
7. **Assign canonical `ci.yml` ownership** to Spec 04 (REQ-TEST-3) -- other specs reference requirements only
