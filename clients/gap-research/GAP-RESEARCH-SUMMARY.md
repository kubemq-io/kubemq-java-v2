# SDK Gap Research -- Java SDK Summary

**Generated:** 2026-03-09
**SDKs Analyzed:** Java

---

## Gap Heatmap (by Category)

| GS Category | Tier | Java |
|-------------|------|------|
| 01 Error Handling | 1 | P0 |
| 02 Connection | 1 | P0 |
| 03 Auth & Security | 1 | P0 |
| 04 Testing | 1 | P0 |
| 05 Observability | 1 | P0 |
| 06 Documentation | 1 | P0 |
| 07 Code Quality | 1 | P1 |
| 08 API Completeness | 2 | P3 |
| 09 API Design & DX | 2 | P2 |
| 10 Concurrency | 2 | P2 |
| 11 Packaging | 2 | P2 |
| 12 Compatibility | 2 | P2 |
| 13 Performance | 2 | P2 |

**Legend:** P0 = Blocker, P1 = Critical, P2 = Important, P3 = Nice-to-have, C = Compliant

---

## Gap Heatmap (by REQ -- Tier 1 only)

| Requirement | Java |
|------------|------|
| REQ-ERR-1: Typed Error Hierarchy | M |
| REQ-ERR-2: Error Classification | M |
| REQ-ERR-3: Auto-Retry with Configurable Policy | M |
| REQ-ERR-4: Per-Operation Timeouts | P |
| REQ-ERR-5: Actionable Error Messages | P |
| REQ-ERR-6: gRPC Error Mapping | M |
| REQ-ERR-7: Retry Throttling | ? |
| REQ-ERR-8: Streaming Error Handling | P |
| REQ-ERR-9: Async Error Propagation | P |
| REQ-CONN-1: Auto-Reconnection with Buffering | P |
| REQ-CONN-2: Connection State Machine | M |
| REQ-CONN-3: gRPC Keepalive Configuration | P |
| REQ-CONN-4: Graceful Shutdown / Drain | P |
| REQ-CONN-5: Connection Configuration | P |
| REQ-CONN-6: Connection Reuse | P |
| REQ-AUTH-1: Token Authentication | P |
| REQ-AUTH-2: TLS Encryption | P |
| REQ-AUTH-3: Mutual TLS (mTLS) | P |
| REQ-AUTH-4: Credential Provider Interface | ? |
| REQ-AUTH-5: Security Best Practices | P |
| REQ-AUTH-6: TLS Credentials During Reconnection | ? |
| REQ-TEST-1: Unit Tests with Mocked Transport | P |
| REQ-TEST-2: Integration Tests Against Real Server | P |
| REQ-TEST-3: CI Pipeline | M |
| REQ-TEST-4: Test Organization | P |
| REQ-TEST-5: Coverage Tools | P |
| REQ-OBS-1: OpenTelemetry Trace Instrumentation | M |
| REQ-OBS-2: W3C Trace Context Propagation | M |
| REQ-OBS-3: OpenTelemetry Metrics | M |
| REQ-OBS-4: Near-Zero Cost When Not Configured | ? |
| REQ-OBS-5: Structured Logging Hooks | P |
| REQ-DOC-1: Auto-Generated API Reference | M |
| REQ-DOC-2: README | P |
| REQ-DOC-3: Quick Start | P |
| REQ-DOC-4: Code Examples / Cookbook | P |
| REQ-DOC-5: Troubleshooting Guide | M |
| REQ-DOC-6: CHANGELOG | M |
| REQ-DOC-7: Migration Guide | M |
| REQ-CQ-1: Layered Architecture | P |
| REQ-CQ-2: Internal vs Public API Separation | P |
| REQ-CQ-3: Linting and Formatting | M |
| REQ-CQ-4: Minimal Dependencies | P |
| REQ-CQ-5: Consistent Code Organization | P |
| REQ-CQ-6: Code Review Standards | ? |
| REQ-CQ-7: Secure Defaults | P |

**Legend:** C = Compliant, P = Partial, M = Missing, ? = Not Assessed, X = Excess

---

## Critical Path

**P0 items:** 30

- REQ-ERR-1: Typed Error Hierarchy -- only 4 flat exceptions, no KubeMQException base, no error fields
- REQ-ERR-2: Error Classification -- no retryable/non-retryable classification
- REQ-ERR-3: Auto-Retry with Configurable Policy -- no operation-level retry, gRPC retry enabled (must disable)
- REQ-ERR-4: Per-Operation Timeouts -- partial, missing on events/subscribe
- REQ-ERR-6: gRPC Error Mapping -- no status code extraction or mapping of 17 codes
- REQ-ERR-7: Retry Throttling -- not assessed, nothing exists
- REQ-ERR-8: Streaming Error Handling -- queue streams have no reconnect, no StreamBrokenError
- REQ-ERR-9: Async Error Propagation -- no transport/handler error distinction
- REQ-CONN-1: Auto-Reconnection with Buffering -- no message buffering, no DNS re-resolve
- REQ-CONN-2: Connection State Machine -- no public state query or callbacks
- REQ-CONN-4: Graceful Shutdown/Drain -- no buffer flush, no post-close guard, no ErrClientClosed
- REQ-CONN-5: Connection Configuration -- no connection timeout, no WaitForReady
- REQ-AUTH-1: Token Authentication -- token immutable after construction
- REQ-AUTH-2: TLS Encryption -- no InsecureSkipVerify, no PEM bytes, no TLS version enforcement
- REQ-AUTH-3: Mutual TLS -- no PEM bytes, no cert reload on reconnection
- REQ-AUTH-4: Credential Provider Interface -- not assessed, nothing exists
- REQ-AUTH-6: TLS Credentials During Reconnection -- not assessed, no cert reload
- REQ-OBS-1: OpenTelemetry Trace Instrumentation -- no spans, no OTel dependency
- REQ-OBS-2: W3C Trace Context Propagation -- no trace context injection/extraction
- REQ-OBS-3: OpenTelemetry Metrics -- no metrics infrastructure
- REQ-OBS-5: Structured Logging Hooks -- Logback hardcoded, no pluggable interface
- REQ-TEST-1: Unit Tests -- missing error classification tests, leak detection, CI enforcement
- REQ-TEST-3: CI Pipeline -- no CI pipeline exists at all
- REQ-DOC-1: Auto-Generated API Reference -- zero Javadoc comments, no published docs
- REQ-DOC-2: README -- missing badges, error handling section, troubleshooting
- REQ-DOC-3: Quick Start -- patterns not in explicit quick start format
- REQ-DOC-4: Code Examples -- no CI compile check, examples not tested
- REQ-DOC-5: Troubleshooting Guide -- no troubleshooting guide exists
- REQ-DOC-6: CHANGELOG -- no CHANGELOG.md
- REQ-DOC-7: Migration Guide -- no v1->v2 migration guide

**Unassessed requirements:** 19 (11 Tier 1, 8 Tier 2)

**Assessment Score:** 3.10 / 5.0 (capped at 3.0 with gating)

**Target Score:** 4.0+

**Estimated total effort:** 33S + 27M + 11L + 4XL (~137 developer-days)

---

## Quick Wins (high impact, low effort)

**Tier 1:**
- REQ-CONN-3: Adjust gRPC keepalive defaults (10s vs current 60s) and document (S)
- REQ-DOC-6: Add CHANGELOG.md (S)
- REQ-CQ-2: Make internal classes package-private (S)
- REQ-CQ-7: Add WARN log for InsecureSkipVerify/plaintext connections (S)
- REQ-DOC-2: README structure improvement -- many sections exist, need reorganization (S)

**Tier 2:**
- Add server compatibility matrix document (REQ-COMPAT-1 partial, S)
- Add deprecation annotations policy to CONTRIBUTING.md (REQ-COMPAT-2 partial, S)
- Document thread-safety guarantees on all public types (REQ-CONC-1, S)
- Document performance characteristics in README (REQ-PERF-5, REQ-PERF-6, S)
- Add CHANGELOG.md (REQ-PKG-2, REQ-PKG-4, S)

---

## Features to Remove or Deprecate

- **gRPC `enableRetry()`:** Per REQ-ERR-3, gRPC-level retry MUST be disabled; all retry logic handled by SDK. Must be removed to prevent double-retry amplification.
- **`grpc-alts` dependency:** Adds unnecessary weight. Not required for gRPC-only transport.
- **Logback hard dependency:** Direct cast to Logback's `LoggerContext`. Must be replaced with SDK-defined logger interface per REQ-OBS-5.

---

## Implementation Sequence

### Phase 1: Foundation (must do first)

**Tier 1 foundation work -- all other phases depend on this:**

1. REQ-CQ-1: Layered architecture refactoring (XL) -- enables clean error wrapping and OTel instrumentation
2. REQ-ERR-1: Typed error hierarchy (M) -- foundation for all error handling
3. REQ-ERR-2: Error classification (M) -- required by retry and gRPC mapping
4. REQ-CONN-2: Connection state machine (M) -- required by reconnection and credential provider
5. REQ-TEST-3: CI pipeline (M) -- enables all quality enforcement
6. REQ-CQ-3: Linting and formatting (M) -- should be in place before heavy development
7. REQ-CQ-2: Internal API separation (S) -- quick access modifier cleanup

**Tier 2 quick documentation wins (no code changes, can run in parallel):**

8. REQ-CONC-1: Thread-safety Javadoc/annotations (S)
9. REQ-PERF-5: Performance documentation (S)
10. REQ-PERF-6: Performance Tips (S)
11. REQ-PERF-2: Connection reuse documentation (S)
12. REQ-COMPAT-1: Compatibility matrix document (S, partial)
13. REQ-COMPAT-2: Deprecation policy in CONTRIBUTING.md (S)
14. REQ-COMPAT-5: EOL policy in README (S)
15. REQ-COMPAT-3: Fix README Java version to 11+ (S)
16. REQ-PKG-1: CHANGELOG.md (S)
17. REQ-PKG-2: Runtime version constant (S)
18. REQ-PKG-4: CONTRIBUTING.md with commit format (S)

### Phase 2: Core Features

**Tier 1 core resilience and security:**

1. REQ-ERR-3: Auto-retry with configurable policy (L)
2. REQ-ERR-6: gRPC error mapping (M) -- all 17 status codes
3. REQ-ERR-4: Per-operation timeouts (M)
4. REQ-CONN-1: Auto-reconnection with buffering (L)
5. REQ-CONN-3: Keepalive defaults (S)
6. REQ-CONN-4: Graceful shutdown/drain (M)
7. REQ-CONN-5: Connection configuration (M)
8. REQ-AUTH-1: Token auth improvements (S)
9. REQ-AUTH-2: TLS improvements (M)
10. REQ-AUTH-3: mTLS improvements (L)
11. REQ-AUTH-4: Credential provider interface (L)
12. REQ-OBS-5: Structured logging hooks (M)
13. REQ-CQ-4: Minimal dependencies (M)

**Tier 2 small code changes (can interleave):**

14. REQ-DX-1: Address validation in builder (S)
15. REQ-DX-2: Default address/clientId, convenience publish methods (S)
16. REQ-API-1: Batch send for queues (S)
17. REQ-API-1: Subscription handle with cancel() (M)
18. REQ-COMPAT-1: Server version check on connection (M)
19. REQ-COMPAT-4: Dependabot configuration (S)
20. REQ-CONC-5: Idempotent close, ErrClientClosed (S)

### Phase 3: Quality, Observability & Polish

**Tier 1 observability and documentation:**

1. REQ-OBS-1: OpenTelemetry trace instrumentation (XL)
2. REQ-OBS-2: W3C Trace Context propagation (L)
3. REQ-OBS-3: OpenTelemetry metrics (L)
4. REQ-OBS-4: Near-zero cost (M)
5. REQ-DOC-1: Javadoc on all public APIs (L)
6. REQ-DOC-2: README restructure (M)
7. REQ-DOC-5: Troubleshooting guide (M)
8. REQ-DOC-6: CHANGELOG (S)
9. REQ-DOC-7: Migration guide (M)

**Tier 1 hardening:**

10. REQ-ERR-5: Actionable error messages (S)
11. REQ-ERR-7: Retry throttling (S)
12. REQ-ERR-8: Streaming error handling (L)
13. REQ-ERR-9: Async error propagation (M)
14. REQ-AUTH-5: Security best practices (S)
15. REQ-AUTH-6: TLS credentials during reconnection (M)
16. REQ-TEST-1: Additional unit tests (L)
17. REQ-TEST-2: Additional integration tests (L)
18. REQ-TEST-4: Test organization (S)
19. REQ-TEST-5: Coverage enforcement (S)
20. REQ-CONN-6: Connection reuse documentation (S)
21. REQ-DOC-3: Quick start restructure (S)
22. REQ-DOC-4: Examples improvements (M)
23. REQ-CQ-5: Code organization (M)
24. REQ-CQ-6: Code review standards (S)
25. REQ-CQ-7: Secure defaults (S)

**Tier 2 medium-large code changes:**

26. REQ-DX-3: Verb alignment with deprecation (M)
27. REQ-DX-5: Message immutability (M, breaking change -- major version)
28. REQ-CONC-3: Callback executor and concurrency control (M)
29. REQ-PKG-3: Automated release pipeline (M)
30. REQ-PERF-4: Batch send implementation (M)
31. REQ-CONC-2 + REQ-CONC-4: Public async API with CompletableFuture (L)
32. REQ-PERF-1: JMH benchmark suite (L)
33. REQ-COMPAT-3: Multi-version CI matrix (M)

---

## Effort Totals

| Priority | Count | Effort Distribution |
|----------|-------|-------------------|
| P0 | 30 | 7S + 11M + 8L + 4XL |
| P1 | 8 | 2S + 4M + 1L + 1XL |
| P2 | 20 | 4S + 2M + 1L (T1) + varied (T2) |
| P3 | 17 | 18S + 10M + 2L (T2) |
| **Total** | **75** | **33S + 27M + 11L + 4XL (~137 days)** |

---

## Review Impact

- **Round 1:** 36 issues found (6 critical / 18 major / 12 minor), 22 fixed, 8 skipped (beyond targeted fix scope)
- **Round 2:** 15 issues found (0 critical / 8 major / 7 minor), 11 fixed, 4 skipped

---

## Recommended Next Step

Use this gap research as the foundation for creating a detailed implementation plan for the Java SDK, starting with Phase 1 foundation items (REQ-CQ-1 architecture, REQ-ERR-1 typed errors). The sequencing risk note in Phase 1 should be evaluated: consider standing up CI (REQ-TEST-3) before the large REQ-CQ-1 refactoring to reduce regression risk.
