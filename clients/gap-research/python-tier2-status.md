# Python SDK — Tier 2 Gap Status (Compact)

**Tier 2 Weighted Score:** 3.46 / 5.0 | **Target:** 4.0+ | **Gap:** +0.54

## Gap Overview

| GS Cat | Assessment Cat # | Current | Target | Gap | Status | Priority | Effort |
|---|---|---|---|---|---|---|---|
| 08 API Completeness | 1 | 4.07 | 4.0 | -0.07 | PARTIAL | P2 | S |
| 09 API Design & DX | 2 | 3.63 | 4.0 | +0.37 | PARTIAL | P3 | M |
| 10 Concurrency | 6 | 3.75 | 4.0 | +0.25 | MISSING | P2 | M |
| 11 Packaging | 11 | 2.90 | 4.0 | +1.10 | PARTIAL | P2 | L |
| 12 Compatibility | 12 | 1.90 | 4.0 | +2.10 | MISSING | P2 | L |
| 13 Performance | 13 | 2.60 | 4.0 | +1.40 | MISSING | P2 | L |

## Effort Summary

| Effort | Count | Est. Days |
|---|---|---|
| S (< 1 day) | 14 items | ~7 |
| M (1-3 days) | 9 items | ~15 |
| L (3-5 days) | 3 items | ~12 |
| XL (5+ days) | 0 items | 0 |
| **Total** | **28 items** | **~28-38 days** |

## P2 Items with MISSING Acceptance Criteria

| REQ | Category | What's Missing |
|---|---|---|
| REQ-API-2 | 08 | Feature matrix document — no cross-SDK parity matrix exists |
| REQ-CONC-1 | 10 | Thread safety documentation — no concurrency guarantees on any public type |
| REQ-CONC-3 | 10 | Subscription callback behavior — no documentation, no concurrency control |
| REQ-CONC-5 | 10 | Shutdown-callback safety — `Close()` does not wait for callbacks, no `ErrClientClosed` |
| REQ-PKG-3 | 11 | Automated release pipeline — no tests in release, no GitHub Release with changelog |
| REQ-PKG-4 | 11 | CHANGELOG — no CHANGELOG.md exists (effort dedup w/ REQ-DOC-6) |
| REQ-COMPAT-1 | 12 | Client-server compatibility matrix — no version matrix documentation |
| REQ-COMPAT-2 | 12 | Deprecation policy — no CHANGELOG entries, no migration guide, non-standard annotations |
| REQ-COMPAT-3 | 12 | Language version CI matrix — single Python version in CI, incorrect README version |
| REQ-COMPAT-4 | 12 | Supply chain security — no Dependabot/Renovate, no SBOM |
| REQ-COMPAT-5 | 12 | EOL policy — no end-of-life policy documented anywhere |
| REQ-PERF-1 | 13 | Published benchmarks — incomplete suite, no docs, no published baselines |
| REQ-PERF-2 | 13 | Connection reuse docs — implementation correct (3/4 criteria COMPLIANT) but no documentation advising reuse. Overall: PARTIAL. |
| REQ-PERF-5 | 13 | Performance documentation — no performance characteristics or tuning guidance |
| REQ-PERF-6 | 13 | Performance tips — no tips section covering client reuse, batching, callbacks, streams |

## NOT_ASSESSED Items (13 criteria across 8 REQs)

| REQ | Criterion | Reason |
|---|---|---|
| REQ-API-3 | `ErrNotImplemented` error pattern | Assessment didn't evaluate error patterns for missing features |
| REQ-API-3 | Tracking issues for gaps | Assessment didn't check GitHub issues |
| REQ-DX-4 | Validation errors classified as non-retryable | Depends on `is_retryable` (absent; Tier 1 scope) |
| REQ-CONC-2 | Cancelled ops produce distinct cancellation error | Assessment didn't distinguish cancellation from timeout errors |
| REQ-CONC-3 | Default callback concurrency is 1 | Assessment didn't evaluate callback concurrency defaults |
| REQ-CONC-3 | Concurrency control mechanism exists | Assessment didn't check for `max_concurrent_callbacks` |
| REQ-CONC-3 | Callbacks don't block internal event loop | Assessment didn't evaluate event loop blocking |
| REQ-CONC-5 | `Close()` waits for in-flight callbacks with timeout | Likely post-assessment requirement (references REQ-CONN-4) |
| REQ-CONC-5 | Operations after `Close()` return `ErrClientClosed` | Assessment didn't test post-close behavior |
| REQ-PKG-3 | Failed releases prevent partial artifact publishing | Assessment didn't evaluate release failure handling |
| REQ-COMPAT-1 | SDK validates server version on connection | Assessment didn't check for version validation logic |
| REQ-COMPAT-1 | SDK logs warning for untested server versions | Assessment didn't check for version warnings |
| REQ-COMPAT-3 | Dropping Python version treated as breaking change | Assessment didn't evaluate versioning policy |

**Total NOT_ASSESSED:** 13 criteria
