# Java SDK — Implementation Plan

**Generated:** 2026-03-10
**Source:** clients/gap-close-specs/java/SPEC-SUMMARY.md

## Open Question Resolutions

| # | Question | User's Answer |
|---|----------|--------------|
| 1 | Should `TimeoutException` be renamed to avoid JDK clash? | Yes — rename to `KubeMQTimeoutException` (new type, no breaking change) |
| 2 | Should received message types be made truly immutable? | Deferred to v3.0 (per spec breaking changes section) |
| 3 | Is 12-month security patch commitment realistic? | Operational — not a code question |
| 4 | Who owns the canonical `ci.yml`? | Resolved: spec 04 (REQ-TEST-3) |
| 5 | Can BENCHMARKS.md be merged with TBD placeholders? | Resolved: yes |
| 6 | How does `WaitForReady` interact with `RetryExecutor`? | Layered — WaitForReady at gRPC channel level, RetryExecutor at application level on top |
| 7 | Should the `Transport` interface be in the public API? | Internal — package-private, not part of public API |
| 8 | Implementation ordering between specs 01 and 07? | Resolved: 01 first |

## Work Unit Execution Order

| WU | Spec | Phase | Dependencies | Parallel Group | Status |
|----|------|-------|-------------|----------------|--------|
| WU-1 | 01-error-handling-spec.md | 1 | — | — | PENDING |
| WU-2 | 02-connection-transport-spec.md | 1 | WU-1 | — | PENDING |
| WU-3 | 03-auth-security-spec.md | 1 | WU-1, WU-2 | — | PENDING |
| WU-4 | 07-code-quality-spec.md | 1 | WU-1 | — | PENDING |
| WU-5 | 04-testing-spec.md (Phase 1) | 2 | — | PARALLEL-A | PENDING |
| WU-6 | 05-observability-spec.md (Phase 1) | 2 | — | PARALLEL-A | PENDING |
| WU-7 | 08-api-completeness-spec.md | 2 | — | PARALLEL-A | PENDING |
| WU-8 | 09-api-design-dx-spec.md (Phase 1) | 2 | WU-1 | — | PENDING |
| WU-9 | 05-observability-spec.md (Phases 2-3) | 2 | WU-1, WU-2, WU-4 | — | PENDING |
| WU-10 | 10-concurrency-spec.md | 3 | WU-1, WU-2 | — | PENDING |
| WU-11 | 11-packaging-spec.md | 3 | — | PARALLEL-B | PENDING |
| WU-12 | 13-performance-spec.md | 3 | — | PARALLEL-B | PENDING |
| WU-13 | 12-compatibility-spec.md | 3 | WU-11 | — | PENDING |
| WU-14 | 06-documentation-spec.md | 3 | — | PARALLEL-B | PENDING |
| WU-15 | 04-testing-spec.md (Phases 2-3) | 3 | all Phase 2 | — | PENDING |
| WU-16 | 09-api-design-dx-spec.md (Phase 2) | 3 | WU-7 | — | PENDING |

## Sub-Phase Scope Filters

| WU | Spec | Phase | Scoped REQ-IDs |
|----|------|-------|----------------|
| WU-5 | 04-testing-spec.md | Phase 1 | REQ-TEST-3, REQ-TEST-4, REQ-TEST-5 |
| WU-6 | 05-observability-spec.md | Phase 1 | REQ-OBS-5 |
| WU-8 | 09-api-design-dx-spec.md | Phase 1 | REQ-DX-1, REQ-DX-2, REQ-DX-4, REQ-DX-5 |
| WU-9 | 05-observability-spec.md | Phases 2-3 | REQ-OBS-1, REQ-OBS-2, REQ-OBS-3, REQ-OBS-4 |
| WU-15 | 04-testing-spec.md | Phases 2-3 | REQ-TEST-1, REQ-TEST-2 |
| WU-16 | 09-api-design-dx-spec.md | Phase 2 | REQ-DX-3 |

## File Conflict Analysis

| Parallel Group | WUs | Shared Files | Resolution |
|---------------|-----|-------------|------------|
| PARALLEL-A | WU-5, WU-6, WU-7 | pom.xml (WU-5 JaCoCo config, WU-6 SLF4J dep change) | SERIALIZE-WU-5-BEFORE-WU-6; WU-7 PARALLEL with both |
| PARALLEL-B | WU-11, WU-12, WU-14 | pom.xml (WU-11, WU-12), README.md (WU-11, WU-12, WU-14) | SERIALIZE-ALL (WU-11 → WU-12 → WU-14) |

## Breaking Changes

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
