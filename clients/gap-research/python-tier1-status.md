# Python SDK — Tier 1 Gap Status (Compact)

**Assessment Score:** 2.96 / 5.0 | **Target:** 4.0+ | **Gap:** +1.04 | **Date:** 2026-03-09

---

## Gap Overview

| GS Category | Assessment Cat # | Current | Target | Gap | Status | Priority | Effort |
|-------------|-----------------|---------|--------|-----|--------|----------|--------|
| 01 Error Handling | 4 | 2.50 | 4.0 | +1.50 | MISSING | P0 | XL (~21d) |
| 02 Connection & Transport | 3 | 3.00 | 4.0 | +1.00 | MISSING | P0 | XL (~15d) |
| 03 Auth & Security | 5 | 2.60 | 4.0 | +1.40 | MISSING | P0 | XL (~13d) |
| 04 Testing | 9 | 2.73 | 4.0 | +1.27 | MISSING | P0 | XL (~11d) |
| 05 Observability | 7 | 1.50 | 4.0 | +2.50 | MISSING | P0 | XL (~19d) |
| 06 Documentation | 10 | 2.00 | 4.0 | +2.00 | MISSING | P0 | XL (~15d) |
| 07 Code Quality | 8 | 3.48 | 4.0 | +0.52 | PARTIAL | P1 | L (~12d) |

---

## Effort Summary

| Priority | Count | Total Effort |
|----------|-------|-------------|
| P0 | 26 | ~71 days |
| P1 | 11 | ~24 days |
| P2 | 6 | ~12 days |
| NOT_ASSESSED | 2 | ~6 days (estimated) |
| **Total** | **45** | **~110 days** |

| Phase | Items | Effort | Calendar Weeks |
|-------|-------|--------|----------------|
| Phase 1: Foundation | 10 | ~18 days | 3 weeks |
| Phase 2: Core Features | 15 | ~36 days | 5 weeks |
| Phase 3: Quality & Polish | 20 | ~56 days | 6 weeks |
| **Total** | **45** | **~110 days** | **~14 weeks** |

---

## All P0 Items

1. **REQ-ERR-1** (M, 2d) — Add Operation, Channel, IsRetryable, RequestID fields to error hierarchy
2. **REQ-ERR-2** (M, 2d) — Implement error classification with retryable/non-retryable for all categories
3. **REQ-ERR-3** (L, 4d) — Implement auto-retry with configurable exponential backoff and jitter
4. **REQ-ERR-5** (M, 2d) — Make error messages actionable with operation, channel, suggestion
5. **REQ-ERR-6** (M, 2d) — Map all 17 gRPC status codes; split CANCELLED handling
6. **REQ-ERR-7** (M, 2d) — Add retry throttling to prevent retry storms
7. **REQ-ERR-8** (L-XL, 6d) — Distinguish stream vs connection errors; add StreamBrokenError
8. **REQ-ERR-9** (M, 2d) — Add error callbacks to subscriptions; distinguish transport vs handler errors
9. **REQ-CONN-1** (XL, 7d) — Async auto-reconnection with exponential backoff, buffering, subscription recovery
10. **REQ-CONN-2** (L, 4d) — Connection state machine (IDLE→CONNECTING→READY→RECONNECTING→CLOSED) with callbacks
11. **REQ-CONN-4** (M, 2d) — Drain/flush before close, drain timeout, ErrClientClosed
12. **REQ-AUTH-2** (M, 2d) — InsecureSkipVerify named option, TLS 1.2 min, handshake classification
13. **REQ-AUTH-3** (M, 2d) — PEM bytes support, cert reload on reconnect, documentation
14. **REQ-AUTH-5** (M, 1.5d) — Credential exclusion from logs/errors/spans
15. **REQ-TEST-1** (L, 4d) — Coverage enforcement in CI, error/retry/state tests, leak detection
16. **REQ-TEST-3** (M, 2d) — CI pipeline: lint + test matrix + integration + coverage
17. **REQ-TEST-5** (S, 0.5d) — Coverage tools in CI with threshold enforcement
18. **REQ-OBS-1** (XL, 7d) — OTel trace instrumentation for all messaging operations
19. **REQ-OBS-2** (L, 4d) — W3C Trace Context injection/extraction via message tags
20. **REQ-OBS-3** (L, 4d) — OTel metrics (duration, counters, connection/retry metrics)
21. **REQ-OBS-4** (M, 2d) — OTel as optional dependency with near-zero overhead
22. **REQ-OBS-5** (M, 2d) — Structured Logger Protocol with key-value fields and no-op default
23. **REQ-DOC-3** (S, 0.5d) — Copy-paste-ready quickstart for each messaging pattern
24. **REQ-DOC-5** (M, 2d) — Troubleshooting guide with minimum 11 entries
25. **REQ-DOC-6** (S, 0.5d) — CHANGELOG.md following Keep a Changelog format
26. **REQ-DOC-7** (M, 2d) — v3→v4 migration guide with before/after code examples

**NOT_ASSESSED Items (likely P0 when assessed — recommend immediate assessment):**
- **REQ-AUTH-4** (L, 4d est.) — CredentialProvider interface with reactive/proactive refresh
- **REQ-AUTH-6** (M, 2d est.) — TLS credential reload during reconnection

---

## NOT_ASSESSED Items

**Count: 2 entire REQs + ~17 individual acceptance criteria = ~19 total**

### Entire REQs (added post-assessment):
1. **REQ-AUTH-4**: Credential Provider Interface — pluggable GetToken() with reactive/proactive refresh (9 criteria)
2. **REQ-AUTH-6**: TLS Credentials During Reconnection — certificate reload for cert-manager rotation (5 criteria)

### Individual criteria within assessed REQs:
3. REQ-ERR-1: "Error codes follow semantic versioning"
4. REQ-ERR-3: "gRPC-level retry is disabled"
5. REQ-ERR-6: "Rich error details from google.rpc.Status extracted"
6. REQ-ERR-6: "Error events recorded as OTel span events"
7. REQ-CONN-1: "DNS is re-resolved on each reconnection attempt"
8. REQ-CONN-5: "WaitForReady applies to both CONNECTING and RECONNECTING states"
9. REQ-TEST-1: "Oversized messages produce validation error"
10. REQ-TEST-1: "Per-test timeout enforced (30s unit, 60s integration)"
11. REQ-TEST-2: "Tests clean up resources after completion"
12. REQ-TEST-2: "Each test uses unique channel name"
13. REQ-TEST-2: "Unsubscribe while messages in flight without leaks"
14. REQ-CQ-3: "Protobuf-generated code excluded from linter/coverage"
15. REQ-CQ-6: "All PRs require at least one review"
16. REQ-CQ-6: "PRs include tests for new functionality"
17. REQ-CQ-6: "Breaking changes labeled in PR description"
18. REQ-DOC-2: "Links use absolute URLs"
19. REQ-DOC-4: "Examples directory has own README"
