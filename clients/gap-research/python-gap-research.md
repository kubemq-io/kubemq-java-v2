# Python SDK — Combined Gap Research Report

**Overall Assessment Score:** 2.96 / 5.0 (unweighted)
**Tier 1 Gap:** +1.04 | **Tier 2 Weighted Score:** 3.46 / 5.0 | **Tier 2 Gap:** +0.54
**Target Score:** 4.0+
**Assessment Date:** 2026-03-09
**Repository:** `github.com/kubemq-io/kubemq-Python`

---

## Executive Summary

### Gap Overview

| GS Category | Tier | Assessment Cat # | Current | Target | Gap | Status | Priority | Effort |
|-------------|------|-----------------|---------|--------|-----|--------|----------|--------|
| 01 Error Handling | 1 | 4 | 2.50 | 4.0 | +1.50 | MISSING | P0 | XL (~21d) |
| 02 Connection & Transport | 1 | 3 | 3.00 | 4.0 | +1.00 | MISSING | P0 | XL (~15d) |
| 03 Auth & Security | 1 | 5 | 2.60 | 4.0 | +1.40 | MISSING | P0 | XL (~13d) |
| 04 Testing | 1 | 9 | 2.73 | 4.0 | +1.27 | MISSING | P0 | XL (~11d) |
| 05 Observability | 1 | 7 | 1.50 | 4.0 | +2.50 | MISSING | P0 | XL (~19d) |
| 06 Documentation | 1 | 10 | 2.00 | 4.0 | +2.00 | MISSING | P0 | XL (~15d) |
| 07 Code Quality | 1 | 8 | 3.48 | 4.0 | +0.52 | PARTIAL | P1 | L (~12d) |
| 08 API Completeness | 2 | 1 | 4.07 | 4.0 | -0.07 | PARTIAL | P2 | S |
| 09 API Design & DX | 2 | 2 | 3.63 | 4.0 | +0.37 | PARTIAL | P3 | M |
| 10 Concurrency | 2 | 6 | 3.75 | 4.0 | +0.25 | MISSING | P2 | M |
| 11 Packaging | 2 | 11 | 2.90 | 4.0 | +1.10 | PARTIAL | P2 | L |
| 12 Compatibility | 2 | 12 | 1.90 | 4.0 | +2.10 | MISSING | P2 | L |
| 13 Performance | 2 | 13 | 2.60 | 4.0 | +1.40 | MISSING | P2 | L |

**Tier 1 (Categories 01–07):** Gate-blocker categories. Total estimated effort: ~110 person-days. Of 45 REQ-* items, 26 are P0 (2 additional items — REQ-AUTH-4, REQ-AUTH-6 — are NOT_ASSESSED; likely P0 when assessed).

**Tier 2 (Categories 08–13):** Should-have categories. Total estimated effort: ~28–38 person-days. Of 28 Tier 2 REQ-* items, 4 are COMPLIANT (14%), 9 are PARTIAL (32%), and 15 are MISSING (54%). The 4 COMPLIANT items (REQ-API-1, REQ-DX-1, REQ-CONC-4, REQ-PERF-4) require no remediation effort. Category 08 (API Completeness) is the only category meeting its score target (4.07 vs 4.0), but even it has a MISSING REQ (feature matrix). The largest gaps are in Category 12 (Compatibility, +2.10) and Category 13 (Performance, +1.40), both dominated by missing documentation and process artifacts.

### Unassessed Requirements

**Tier 1: 2 entire REQs + ~17 individual acceptance criteria = ~19 total**

#### Entire REQs (added post-assessment):
1. **REQ-AUTH-4**: Credential Provider Interface — pluggable `GetToken()` with reactive/proactive refresh (9 criteria)
2. **REQ-AUTH-6**: TLS Credentials During Reconnection — certificate reload for cert-manager rotation (5 criteria)

#### Individual criteria within assessed Tier 1 REQs:
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

**Tier 2: 13 acceptance criteria across 8 REQ-* items**

1. **REQ-API-3** (No Silent Feature Gaps): `ErrNotImplemented` error pattern and tracking issues — not evaluated
2. **REQ-DX-4** (Fail-Fast Validation): Whether validation errors are classified as non-retryable — depends on `is_retryable` property (absent, covered in Tier 1)
3. **REQ-CONC-2** (Cancellation): Whether cancelled operations produce a distinct cancellation error (vs generic timeout) — not evaluated
4. **REQ-CONC-3** (Callback Behavior): Default callback concurrency, concurrency control mechanism, event loop blocking — 3 criteria not evaluated
5. **REQ-CONC-5** (Shutdown Safety): Whether `Close()` waits for in-flight callbacks with timeout, whether post-`Close()` operations return `ErrClientClosed` — 2 criteria not evaluated (likely added post-assessment; references REQ-CONN-4 drain concept from Tier 1)
6. **REQ-PKG-3** (Release Pipeline): Whether failed releases prevent partial artifact publishing — not evaluated
7. **REQ-COMPAT-1** (Compatibility Matrix): Whether SDK validates server version on connection and logs warnings — 2 criteria not evaluated
8. **REQ-COMPAT-3** (Language Version Support): Whether dropping a Python version is treated as a breaking change — not evaluated

### Critical Path

#### Tier 1 P0 Items (must be fixed first)

1. **REQ-ERR-1** — Add `Operation`, `Channel`, `IsRetryable`, `RequestID` fields to error hierarchy
2. **REQ-ERR-2** — Implement error classification with retryable/non-retryable determination for all categories
3. **REQ-ERR-3** — Implement auto-retry with configurable exponential backoff and jitter
4. **REQ-ERR-5** — Make error messages actionable with operation, channel, and suggestion context
5. **REQ-ERR-6** — Map all 17 gRPC status codes; split CANCELLED handling
6. **REQ-ERR-7** — Add retry throttling to prevent retry storms
7. **REQ-ERR-8** — Distinguish stream-level errors from connection errors; add `StreamBrokenError`
8. **REQ-ERR-9** — Add error callback parameter to subscriptions; distinguish transport vs handler errors
9. **REQ-CONN-1** — Implement async auto-reconnection with exponential backoff, buffering, and subscription recovery
10. **REQ-CONN-2** — Implement connection state machine (IDLE→CONNECTING→READY→RECONNECTING→CLOSED) with user callbacks
11. **REQ-CONN-4** — Add drain/flush before close, drain timeout, `ErrClientClosed` for post-close operations
12. **REQ-AUTH-2** — Add `InsecureSkipVerify` as named option, TLS 1.2 minimum, handshake failure classification
13. **REQ-AUTH-3** — Add PEM bytes support, cert reload on reconnect, env var cert loading docs
14. **REQ-AUTH-5** — Comprehensive credential exclusion from logs/errors/spans
15. **REQ-TEST-1** — Add coverage enforcement in CI, error classification tests, resource leak detection
16. **REQ-TEST-3** — Create CI pipeline running on every PR with lint, test matrix, integration tests
17. **REQ-TEST-5** — Configure coverage tools in CI with threshold enforcement
18. **REQ-OBS-1** — Implement OTel trace instrumentation for all messaging operations
19. **REQ-OBS-2** — Implement W3C Trace Context injection/extraction via message tags
20. **REQ-OBS-3** — Implement OTel metrics (duration histogram, message counters, connection/retry counters)
21. **REQ-OBS-4** — Implement OTel as optional dependency with near-zero overhead when unconfigured
22. **REQ-OBS-5** — Define structured `Logger` Protocol with key-value fields and no-op default
23. **REQ-DOC-3** — Create copy-paste-ready quick start for each messaging pattern
24. **REQ-DOC-5** — Create troubleshooting guide with minimum 11 entries
25. **REQ-DOC-6** — Create CHANGELOG.md following Keep a Changelog format
26. **REQ-DOC-7** — Create v3→v4 migration guide with before/after code examples

**NOT_ASSESSED Items (likely P0 when assessed — recommend immediate assessment):**
- **REQ-AUTH-4** — Implement `CredentialProvider` interface with reactive/proactive refresh (9 criteria)
- **REQ-AUTH-6** — Implement TLS credential reload during reconnection (5 criteria)

#### Tier 2 Critical Path

No Tier 2 items are P0 (blockers) or P1 (critical prerequisites for Tier 1). However, several Tier 2 items have **cross-tier dependencies** on Tier 1 work:

1. **REQ-CONC-2** (cancellation error types) depends on Tier 1 REQ-ERR-* (error classification, `is_retryable`)
2. **REQ-CONC-5** (shutdown safety) depends on Tier 1 REQ-CONN-4 (graceful drain)
3. **REQ-PKG-3** (release pipeline) depends on Tier 1 REQ-TEST-* (CI pipeline with tests)
4. **REQ-COMPAT-3** (CI version matrix) depends on Tier 1 REQ-TEST-* (CI infrastructure)
5. **REQ-DX-4** (non-retryable classification) depends on Tier 1 REQ-ERR-* (retryable classification)

**Recommendation:** Complete Tier 1 error handling, connection, and testing work BEFORE the dependent Tier 2 items.

### Quick Wins (high impact, low effort)

#### Tier 1 Quick Wins

| Item | REQ | What to Do |
|---|---|---|
| Verify keepalive defaults | REQ-CONN-3 | Verify keepalive defaults match GS (10s/5s/true); adjust if needed |
| Adjust connection timeout | REQ-CONN-5 | Adjust default connection timeout from 5s to 10s; document WaitForReady behavior |
| Fix lint errors | REQ-CQ-3 | Fix 204 ruff errors, add ruff/mypy to CI pipeline |
| Move grpcio-tools | REQ-CQ-4 | Move `grpcio-tools` from runtime to dev dependencies |
| Create CHANGELOG | REQ-DOC-6 | Create CHANGELOG.md (template-based, low effort) |
| TLS verification warning | REQ-CQ-7 | Log WARNING when TLS verification disabled |
| Quickstart code | REQ-DOC-3 | Add quickstart code to README for each messaging pattern |
| Test utilities | REQ-TEST-4 | Add `testutil` package for shared fixtures |
| Per-operation timeouts | REQ-ERR-4 | Add explicit `timeout` parameter to all methods; adjust defaults to match GS |
| Create `py.typed` marker | REQ-CQ-5 | Create empty `py.typed` file in package root for PEP 561 type checker support (5 minutes) |
| Delete legacy debug script | REQ-CQ-6 | Remove `grpc/client.py` containing `print()` statements (1 minute) |

#### Tier 2 Quick Wins (P3, Effort S)

| Item | REQ | What to Do |
|---|---|---|
| Feature matrix document | REQ-API-2 | Create `clients/feature-matrix.md` with all features × 5 SDKs |
| Thread safety docstrings | REQ-CONC-1 | Add concurrency guarantees to all public type docstrings |
| Fix version consistency | REQ-PKG-2 | Single source of truth in `pyproject.toml`, read in `__init__.py` |
| CHANGELOG.md | REQ-PKG-4 | Create with v4.0.0-dev entries, retroactive v3.x entries |
| EOL policy | REQ-COMPAT-5 | Document in README: "previous major gets 12 months security patches" |
| Performance tips doc | REQ-PERF-6 | Add "Performance Tips" section to README or separate doc |
| Connection reuse docs | REQ-PERF-2 | Document single-client-per-app pattern |
| Default address | REQ-DX-2 | Add `address: str = "localhost:50000"` default to ClientConfig (5 minutes) |

### Features to Remove or Deprecate

| Feature | Location | Issue | Recommendation |
|---|---|---|---|
| `grpcio-tools` as runtime dependency | `pyproject.toml` dependencies | Build-time tool in runtime deps increases install footprint | Move to `[project.optional-dependencies]` dev group |
| Legacy `*_async` thread-wrapped methods | Various clients | Already marked `@deprecated_async_method`. Three-tier async architecture is excessive | Schedule removal in v5 or next major version. Remove after deprecation period; keep only sync + native async |
| `common/exceptions.py` deprecated classes | `common/exceptions.py` | Legacy error types still present alongside new hierarchy in `core/exceptions.py` | Remove after deprecation period (2 minor versions / 6 months) |
| `grpc/client.py` legacy debug script | `grpc/client.py` | Contains `print()` statements | Remove entirely |

---

## Category 01: Error Handling & Resilience

**GS Category:** 01 | **Assessment Category:** 4 | **Score:** 2.50 / 5.0 | **Target:** 4.0 | **Gap:** +1.50

### REQ-ERR-1: Typed Error Hierarchy

**Current State:**
- 8 typed exception classes in `core/exceptions.py` (line 12–92): `KubeMQError`, `KubeMQConnectionError`, `KubeMQAuthenticationError`, `KubeMQTimeoutError`, `KubeMQValidationError`, `KubeMQChannelError`, `KubeMQMessageError`, `KubeMQTransactionError`, `KubeMQCircuitOpenError`
- All store `message`, `code`, `details` dict, `cause`
- `from_grpc_error()` maps 12 gRPC status codes (line 102)
- Uses `from e` for cause chaining
- Assessment 4.1.1–4.1.5: sub-score 3.40

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | All SDK methods return SDK-typed errors, not raw gRPC | PARTIAL | Most methods wrap; `queues/async_client.py:543` uses bare `Exception` |
| 2 | Error types support Python `__cause__` unwrapping | COMPLIANT | Uses `raise ... from e` pattern |
| 3 | Error codes are documented and stable | MISSING | No public documentation of error codes |
| 4 | Error codes follow semantic versioning | NOT_ASSESSED | Assessment didn't evaluate versioning policy for error codes |

**Required fields gap:**

| Field | GS Requirement | Status | Detail |
|-------|---------------|--------|--------|
| Code | enum/const | PARTIAL | Stores gRPC status code as int, not SDK-defined enum |
| Message | string | COMPLIANT | Present |
| Operation | string | MISSING | Not present — no operation context in errors |
| Channel | string | MISSING | Not present — no channel context in errors |
| IsRetryable | boolean | MISSING | Explicitly confirmed missing (assessment 4.1.3: 1/5) |
| Cause | error | COMPLIANT | `cause` attribute, `__cause__` via `from e` |
| RequestID | string | MISSING | Not present — no request correlation |

**REQ-ERR-1 Overall Status: PARTIAL (4 MISSING criteria + 4 MISSING fields)**
**Priority: P0**

**Remediation:**
- **What:** Extend `KubeMQError` in `core/exceptions.py` with `operation: str`, `channel: str | None`, `is_retryable: bool`, `request_id: str` fields. Define `ErrorCode` enum with semantic codes (`CONNECTION_TIMEOUT`, `AUTH_FAILED`, `CHANNEL_NOT_FOUND`, etc.). Populate `operation` and `channel` at each call site. Auto-generate `request_id` via `uuid4()` at operation start. Remove bare `Exception` usage in `queues/async_client.py`.
- **Complexity:** M (2 days) — field additions straightforward; populating at call sites requires touching all client methods
- **Dependencies:** None (foundational)
- **Risk:** Without `is_retryable`, users cannot implement their own retry logic safely. Without `operation`/`channel`, error diagnosis in production is impaired.
- **Breaking change:** No — additive fields with defaults. Existing `except KubeMQError` patterns still work.
- **Language-specific:** Use `@property` for `is_retryable` to allow subclass override. Use Python `Enum` for error codes. Consider `__slots__` for performance.

### REQ-ERR-2: Error Classification

**Current State:**
- `from_grpc_error()` maps gRPC codes to exception types but has no retryable concept
- No `BufferFullError` or `Backpressure` category
- Assessment 4.1.3: 1/5 for retryable classification

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Every error has a classification | MISSING | No classification system exists |
| 2 | `IsRetryable` flag is accurate for all types | MISSING | No `is_retryable` property |
| 3 | Classification is documented | MISSING | No error reference documentation |
| 4 | `BufferFullError` classified as Backpressure | MISSING | No `BufferFullError` type |

**Body text requirements gap:**

| Requirement | Status | Detail |
|------------|--------|--------|
| Transient category (UNAVAILABLE, ABORTED) | PARTIAL | Maps to types but no retryable flag |
| Timeout category (DEADLINE_EXCEEDED) | PARTIAL | Maps to `KubeMQTimeoutError` but no retryable flag |
| Throttling category (RESOURCE_EXHAUSTED) | PARTIAL | Maps to `KubeMQMessageError`, no throttling distinction |
| Authentication (UNAUTHENTICATED) | COMPLIANT | Maps to `KubeMQAuthenticationError` |
| Authorization (PERMISSION_DENIED) | MISSING | Not separately mapped |
| Validation (INVALID_ARGUMENT, FAILED_PRECONDITION) | PARTIAL | Maps to `KubeMQValidationError` but no FAILED_PRECONDITION |
| Not Found (NOT_FOUND) | COMPLIANT | Maps to `KubeMQChannelError` |
| Fatal (INTERNAL, UNIMPLEMENTED, DATA_LOSS) | MISSING | Not all mapped; no fatal classification |
| Cancellation (CANCELLED) | MISSING | Not mapped at all |
| Backpressure (SDK-generated) | MISSING | No concept exists |

**REQ-ERR-2 Overall Status: MISSING**
**Priority: P0**

**Remediation:**
- **What:** Create `ErrorCategory` enum in `core/exceptions.py`: `TRANSIENT`, `TIMEOUT`, `THROTTLING`, `AUTHENTICATION`, `AUTHORIZATION`, `VALIDATION`, `NOT_FOUND`, `FATAL`, `CANCELLATION`, `BACKPRESSURE`. Add `category: ErrorCategory` property to `KubeMQError`. Implement `is_retryable` as `return self.category in (TRANSIENT, TIMEOUT, THROTTLING)`. Add `BufferFullError(KubeMQError)` with `category=BACKPRESSURE`. Add `KubeMQAuthorizationError(KubeMQError)` for `PERMISSION_DENIED`.
- **Complexity:** M (2 days)
- **Dependencies:** REQ-ERR-1 (fields must exist first)
- **Risk:** Without classification, retry logic cannot make correct decisions. Auth failures would be retried unnecessarily.
- **Breaking change:** No — additive. New exception subclasses don't break existing catch clauses catching `KubeMQError`.
- **Language-specific:** Use Python `Enum` for categories. `is_retryable` as `@property` allows future refinement.

### REQ-ERR-3: Auto-Retry with Configurable Policy

**Current State:**
- Zero retry mechanism exists (assessment 4.3: sub-score 1.00)
- Subscription loops reconnect with fixed-interval sleep — not a general retry mechanism
- No exponential backoff anywhere in the SDK
- Assessment 4.3.1–4.3.5: all 1/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Transient/timeout errors retried automatically | MISSING | No retry mechanism |
| 2 | Retry policy configurable via options | MISSING | No retry policy |
| 3 | Retries can be disabled (maxRetries=0) | MISSING | No retry mechanism |
| 4 | Each retry logged at DEBUG level | MISSING | No retry logging |
| 5 | After exhausting, last error returned with context | MISSING | No retry exhaustion handling |
| 6 | Non-retryable errors returned immediately | MISSING | No retryable classification |
| 7 | Non-idempotent ops not retried on DEADLINE_EXCEEDED | MISSING | No operation-type safety rules |
| 8 | gRPC-level retry is disabled | NOT_ASSESSED | Assessment didn't check gRPC retry config. Could be verified by searching source for `EnableRetry`, `service_config`, or retry-related gRPC channel options. Recommend assessing before implementation. |
| 9 | Retry policy immutable after construction | MISSING | No retry policy exists |
| 10 | Worst-case latency documented | MISSING | No retry documentation |

**Body text requirements gap:**

| Requirement | Status | Detail |
|------------|--------|--------|
| Default retry policy (3 retries, 500ms initial, 30s max, 2x multiplier, full jitter) | MISSING | Nothing exists |
| Retry Safety by Operation Type table | MISSING | No operation-type safety rules |
| gRPC retry disabled (no EnableRetry) | NOT_ASSESSED | Not verified in assessment |
| Independent backoff policies (operation vs connection) | MISSING | Only fixed-interval reconnection exists |

**REQ-ERR-3 Overall Status: MISSING**
**Priority: P0**

**Remediation:**
- **What:** Create `RetryPolicy` frozen dataclass in `core/retry.py` with fields: `max_retries: int = 3`, `initial_backoff_seconds: float = 0.5`, `max_backoff_seconds: float = 30.0`, `backoff_multiplier: float = 2.0`, `jitter: JitterStrategy = JitterStrategy.FULL`. Implement `RetryExecutor` class wrapping operation callables with backoff calculation using `random.uniform()` for jitter. Add `retry_policy` parameter to `ClientConfig`. Implement operation-type safety rules: events publish safe to retry, queue send and RPC not retried on `DEADLINE_EXCEEDED`. Add `RetryExhaustedError` wrapping the last error with attempt count and total duration. Verify `grpc.EnableRetry()` is not called anywhere. Ensure retry policy is frozen (`@dataclass(frozen=True)`).
- **Complexity:** L (4 days) — retry logic itself is moderate but must handle both sync and async paths, integrate with error classification, respect operation-type safety
- **Dependencies:** REQ-ERR-1 (is_retryable field), REQ-ERR-2 (error classification)
- **Risk:** Without retry, every transient failure immediately surfaces to user. This is the #1 production readiness gap.
- **Breaking change:** No — new behavior with opt-out (max_retries=0).
- **Language-specific:** Python async retry requires `asyncio.sleep()` not `time.sleep()`. Use `typing.Protocol` for retry policy abstraction. Consider `tenacity` library patterns but implement inline to avoid dependency.

**Future Enhancement notes:**
- Design `PartialFailureError` type now (even if unused) for future per-message batch status
- Design message types with room for `IdempotencyKey` field

### REQ-ERR-4: Per-Operation Timeouts

**Current State:**
- `asyncio.wait_for()` wraps all async operations in `async_transport.py`
- `default_timeout_seconds = 30` in config
- Commands/queries accept per-request `timeout_seconds`
- Assessment 4.4.1: 4/5, 4.4.2: 4/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Python: every method accepts `timeout` parameter (seconds as float) | PARTIAL | Async methods use `wait_for` but not all expose explicit `timeout` kwarg; sync relies on gRPC deadline |
| 2 | Default timeouts applied when user doesn't specify | PARTIAL | 30s global default; GS specifies different defaults per operation type (send=5s, subscribe=10s, etc.) |
| 3 | Timeout errors classified as retryable (with caution) | MISSING | No retryable classification exists |

**Body text default timeout gap:**

| Operation | GS Default | Current Default | Match? |
|-----------|-----------|----------------|--------|
| Send / Publish | 5s | 30s | NO |
| Subscribe (initial) | 10s | 30s | NO |
| Request / Query (RPC) | 10s | Per-request timeout_seconds | PARTIAL |
| Queue Receive (single) | 10s | 30s | NO |
| Queue Receive (streaming) | 30s | 30s | YES |
| Connection establishment | 10s (per REQ-CONN-5) | 5s (ping timeout) | NO |

**REQ-ERR-4 Overall Status: PARTIAL (1 MISSING, 2 PARTIAL)**
**Priority: P1**

**Remediation:**
- **What:** Add explicit `timeout: float | None = None` parameter to all public methods in `PubSubClient`, `AsyncPubSubClient`, `QueuesClient`, `AsyncQueuesClient`, `CQClient`, `AsyncCQClient`. Define per-operation-type default timeouts matching GS table. Use `DEFAULT_SEND_TIMEOUT = 5.0`, `DEFAULT_SUBSCRIBE_TIMEOUT = 10.0`, etc. as module-level constants. When `timeout` is `None`, use the operation-type default; when explicitly set, use the user value.
- **Complexity:** M (1-2 days) — parameter addition is mechanical, but designing and implementing `legacy_timeout_mode` compatibility bridge, writing tests for both modes, updating documentation, and adding deprecation warnings adds significant scope.
- **Dependencies:** None
- **Risk:** Changing default from 30s to 5s for send operations is a **breaking change** that will break users with slow servers, large messages, or network latency. This should be sequenced with the v4.0.0 release to avoid a double breaking change.
- **Breaking change:** YES — default timeout values change. Provide `ClientConfig.default_timeout_seconds` as override. Document migration: "Default send timeout changed from 30s to 5s. Set `default_timeout_seconds=30` to restore old behavior."
- **Backward compatibility:** Add `ClientConfig.legacy_timeout_mode: bool = False` that restores 30s for all operations. Emit deprecation warning when used.
- **Language-specific:** Use `float` type for seconds (Python convention). `None` means "use default".

### REQ-ERR-5: Actionable Error Messages

**Current State:**
- Messages include gRPC details but rarely suggest fixes
- Assessment 4.2.1: 2/5, 4.2.2: 3/5
- Missing: channel name, operation name in most errors
- Example: `"Failed to connect to localhost:50000: ..."` — no fix suggestion

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Error messages include operation name | MISSING | Not included in error messages |
| 2 | Error messages include target channel/queue | MISSING | Not included |
| 3 | Error messages include suggestion for resolution | MISSING | No suggestions in any error |
| 4 | Retry exhaustion includes attempt count and duration | MISSING | No retry system exists |
| 5 | Error messages never expose internal details | PARTIAL | gRPC details could leak; no explicit sanitization |

**REQ-ERR-5 Overall Status: MISSING**
**Priority: P0**

**Remediation:**
- **What:** Rewrite `KubeMQError.__str__()` to include operation, channel, suggestion. Create `_ERROR_SUGGESTIONS: dict[ErrorCategory, str]` mapping each category to actionable advice (e.g., `AUTHENTICATION: "Check your auth token. Token refresh may be needed."`, `TRANSIENT: "This error is temporary. The SDK will retry automatically."`). Add retry context formatting: `f"Retries exhausted: {attempts}/{max_attempts} over {total_duration:.1f}s"`. Sanitize error messages to remove raw gRPC frame data.
- **Complexity:** M (2 days)
- **Dependencies:** REQ-ERR-1 (operation/channel fields), REQ-ERR-2 (categories for suggestions), REQ-ERR-3 (retry context)
- **Risk:** Poor error messages are the #2 developer friction point after missing retry.
- **Breaking change:** YES — `str(error)` output format changes. Users parsing error strings will break. Document new format.
- **Language-specific:** Use Python f-strings. Implement `__str__` and `__repr__` separately (repr for debugging, str for user display).

### REQ-ERR-6: gRPC Error Mapping

**Current State:**
- `from_grpc_error()` at `core/exceptions.py:102` maps 12 gRPC status codes
- Maps: UNAVAILABLE→Connection, UNAUTHENTICATED→Auth, DEADLINE_EXCEEDED→Timeout, NOT_FOUND→Channel, INVALID_ARGUMENT→Validation, RESOURCE_EXHAUSTED→Message, ABORTED→Transaction
- Supports both `grpc.RpcError` and `grpc.aio.AioRpcError`
- Assessment 4.1.4: 4/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | All 17 gRPC status codes mapped (0-16) | PARTIAL | Maps 12, missing: OK (0), CANCELLED (1), UNKNOWN (2), ALREADY_EXISTS (6), OUT_OF_RANGE (11), UNIMPLEMENTED (12), DATA_LOSS (15) |
| 2 | Original gRPC error preserved in chain | COMPLIANT | Uses `from e` and `cause` attribute |
| 3 | Rich error details from `google.rpc.Status` extracted | NOT_ASSESSED | Assessment didn't evaluate rich error detail extraction |
| 4 | CANCELLED split: client-initiated (not retryable) vs server-initiated (retryable) | MISSING | CANCELLED not mapped at all |
| 5 | UNKNOWN retried at most once | MISSING | No retry system; UNKNOWN not mapped |
| 6 | Error events recorded as OTel span events | NOT_ASSESSED | No OTel integration exists (see REQ-OBS-1) |

**Body text mapping gap (unmapped codes):**

| gRPC Code | GS SDK Category | Current SDK Mapping | Status |
|-----------|----------------|---------------------|--------|
| OK (0) | (no error) | — | COMPLIANT (handled implicitly) |
| CANCELLED (1) | Cancellation/Transient | Not mapped | MISSING |
| UNKNOWN (2) | Transient (max 1 retry) | Not mapped | MISSING |
| ALREADY_EXISTS (6) | Validation | Not mapped | MISSING |
| OUT_OF_RANGE (11) | Validation | Not mapped | MISSING |
| UNIMPLEMENTED (12) | Fatal | Not mapped | MISSING |
| DATA_LOSS (15) | Fatal | Not mapped | MISSING |

**REQ-ERR-6 Overall Status: PARTIAL (4 MISSING, 2 NOT_ASSESSED)**
**Priority: P0**

**Remediation:**
- **What:** Add mappings for all 5 missing gRPC codes in `from_grpc_error()`. Add `KubeMQCancellationError` with client/server-initiated distinction (check if local context/token is cancelled to determine). Add `KubeMQFatalError` for UNIMPLEMENTED/DATA_LOSS. Map ALREADY_EXISTS and OUT_OF_RANGE to `KubeMQValidationError`. Map UNKNOWN to `KubeMQTransientError` with `max_retries=1` override. Extract `google.rpc.Status` details via `grpc.StatusCode` metadata when available.
- **Complexity:** M (2 days)
- **Dependencies:** REQ-ERR-2 (error categories must exist)
- **Risk:** Unmapped codes currently fall through to generic `KubeMQError`, giving users no actionable information.
- **Breaking change:** No — adding new exception subclasses is additive. Users catching `KubeMQError` base still work.
- **Language-specific:** Use `grpc.StatusCode` enum for matching. Use `google.rpc.status_pb2` for rich error extraction. CANCELLED distinction requires checking `asyncio.CancelledError` context.

**Future Enhancement notes:**
- Implement `Retry-After` header respect for `RESOURCE_EXHAUSTED` when server provides timing hints via gRPC metadata.

### REQ-ERR-7: Retry Throttling

**Current State:**
- No retry mechanism exists, so no retry throttling
- No concurrent retry limiting of any kind

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Concurrent retry attempts limited per client | MISSING | No retry system exists |
| 2 | Limit configurable via options | MISSING | No retry system |
| 3 | When limit reached, errors returned immediately with throttle indicator | MISSING | No retry system |
| 4 | Retry storms prevented during brownouts | MISSING | No retry system |

**REQ-ERR-7 Overall Status: MISSING**
**Priority: P0**

**Remediation:**
- **What:** Add `max_concurrent_retries: int = 10` to `RetryPolicy`. Implement via `asyncio.Semaphore` (async) / `threading.Semaphore` (sync) in `RetryExecutor`. When semaphore cannot be acquired, return error immediately with `retry_throttled=True` flag. Log throttled retries at WARN level.
- **Complexity:** M (2 days)
- **Dependencies:** REQ-ERR-3 (retry system must exist first)
- **Risk:** Without throttling, a server brownout causes all clients to retry simultaneously, making the brownout worse.
- **Breaking change:** No — new feature.
- **Language-specific:** Use `asyncio.Semaphore(10)` for async path. `threading.Semaphore(10)` for sync. Note: `asyncio.Semaphore` must only be used within a single event loop. If the SDK supports a mixed sync/async context (sync client internally using async transport), ensure the correct semaphore type is used at each layer.

### REQ-ERR-8: Streaming Error Handling

**Current State:**
- Sync subscription loops catch errors, log, sleep, retry (`pubsub/client.py:468-482`)
- Async: no built-in stream recovery
- No distinction between stream-level and connection-level errors
- No `StreamBrokenError` with unacknowledged message IDs
- Assessment 3.2.6: 2/5, 4.4.3: 3/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Stream errors trigger stream reconnection with backoff, not connection reconnection | MISSING | No stream/connection distinction |
| 2 | Per-message errors don't terminate the stream | PARTIAL | Batch sends track per-item errors; async streams propagate exceptions |
| 3 | `StreamBrokenError` reports unacknowledged message IDs | MISSING | No such error type |
| 4 | Stream state independent of connection state | MISSING | No stream state tracking |

**REQ-ERR-8 Overall Status: MISSING**
**Priority: P0**

**Remediation:**
- **What:** Create `StreamBrokenError(KubeMQError)` with `unacked_message_ids: list[str]` field. In `AsyncTransport`, distinguish `grpc.StatusCode.UNAVAILABLE` (connection) from server-side stream closure (stream). Implement stream-level reconnection using the same backoff policy as operation retry. Track in-flight messages per stream via `dict[str, datetime]` to populate `StreamBrokenError`. Add stream-level error callback separate from connection callbacks.
- **Complexity:** L-XL (5-7 days) — requires rearchitecting stream management in both sync and async transports, including `StreamBrokenError` with message ID tracking, stream-level vs connection-level error distinction in both transports, stream-level reconnection with shared backoff, in-flight message tracking per stream, and separate stream error callbacks
- **Dependencies:** REQ-ERR-3 (backoff policy for stream reconnection), REQ-CONN-2 (connection state separation)
- **Risk:** Without this, any stream error can cascade into full connection reset, disrupting all operations.
- **Breaking change:** No — new error types and callbacks are additive.
- **Language-specific:** Use `weakref.WeakSet` for stream tracking — this prevents the transport from keeping strong references to streams that should be garbage collected, avoiding memory leaks from abandoned subscriptions. Async streams should use `asyncio.Task` with proper cancellation.

### REQ-ERR-9: Async Error Propagation

**Current State:**
- Sync subscriptions use callbacks (no separate error callback)
- Async subscriptions use `AsyncIterator` pattern
- Handler errors in subscription loops are caught and logged (sync)
- Default logging effectively silent (`CRITICAL + 1` level)
- Assessment 4.4.3: 3/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Subscription/consumer accept error callback parameter | PARTIAL | Sync has `on_receive_event_callback` but no separate error callback |
| 2 | Transport errors and handler errors are distinguishable | MISSING | Same exception handling for both |
| 3 | Handler errors don't terminate subscription | PARTIAL | Sync loops catch and continue; async depends on iterator usage |
| 4 | Unhandled async errors logged at ERROR level | MISSING | Default logging level suppresses all output |
| 5 | Async errors propagated to user-registered error handlers | PARTIAL | Sync: errors swallowed in loop; async: exception propagates to caller |

**REQ-ERR-9 Overall Status: PARTIAL (3 MISSING)**
**Priority: P0**

**Remediation:**
- **What:** Add `on_error_callback: Callable[[KubeMQError], None] | None = None` parameter to all subscription methods. Create `TransportError(KubeMQError)` and `HandlerError(KubeMQError)` to distinguish error sources. Wrap user callback invocation in try/except; on handler error, invoke error callback (if registered) with `HandlerError`, otherwise log at ERROR level using the SDK logger. Change default log level from `CRITICAL + 1` to `WARNING` so unhandled errors are visible. Ensure async subscription errors are propagated via error callback, not silently swallowed.
- **Complexity:** M (2 days)
- **Dependencies:** REQ-ERR-1 (error types), REQ-OBS-5 (logger must exist)
- **Risk:** Silent error swallowing is the most dangerous pattern — users don't know their subscriptions are failing.
- **Breaking change:** YES — changing default log level from silent to WARNING means users will see log output they didn't before. Provide migration note.
- **Language-specific:** Use `typing.Callable` for error callback type. Consider Python `Protocol` for stronger typing. `asyncio.Task` exception handlers via `loop.set_exception_handler()`.

---

## Category 02: Connection & Transport

**GS Category:** 02 | **Assessment Category:** 3 | **Score:** 3.00 / 5.0 | **Target:** 4.0 | **Gap:** +1.00

### REQ-CONN-1: Auto-Reconnection with Buffering

**Current State:**
- Sync: `ChannelManager.recreate_channel()` with fixed-interval sleep (1s default) in `channel_manager.py:150`
- Async: **NO auto-reconnection at all** in `AsyncTransport`
- `EventSender` uses `queue.Queue` for outbound buffering (unbounded, no size limit)
- Sync subscription recovery: auto-retry loop in `pubsub/client.py:468-482`
- Async subscription recovery: none — exception propagates to caller
- Assessment 3.2.3: 2/5, 3.2.4: 1/5, 3.2.6: 2/5, 3.2.7: 2/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Connection drops detected within keepalive timeout (15s) | PARTIAL | Keepalive configured but detection timing not verified |
| 2 | Reconnection starts with exponential backoff | MISSING | Fixed 1s interval, no backoff |
| 3 | Messages buffered during reconnection and sent on reconnect | PARTIAL | Sync only; unbounded queue; no size limit |
| 4 | Subscriptions restored after reconnection (per recovery table) | PARTIAL | Sync only; no Events Store sequence tracking |
| 5 | Buffer overflow configurable (error vs block) | MISSING | Unbounded queue, no overflow handling |
| 6 | Reconnection logged at INFO level | MISSING | Default logging suppresses all output |
| 7 | Successful reconnection logged at INFO level | MISSING | Same logging issue |
| 8 | DNS re-resolved on each reconnection attempt | NOT_ASSESSED | Assessment didn't verify DNS behavior |
| 9 | Backoff delay and attempt counter reset after success | MISSING | No backoff to reset |
| 10 | Operation retries suspended during RECONNECTING | MISSING | No retry system, no state machine |
| 11 | Stream errors distinguished from connection errors | MISSING | No distinction |
| 12 | Buffered messages sent in FIFO order | PARTIAL | `queue.Queue` is FIFO; async has no buffer |
| 13 | Buffered messages discarded on CLOSED with callback | MISSING | No `OnBufferDrain` callback |

**Body text requirements gap:**

| Requirement | Status | Detail |
|------------|--------|--------|
| Unlimited max reconnect attempts (default) | PARTIAL | Sync loops indefinitely but no configurable limit |
| Configurable reconnect buffer size (8MB default) | MISSING | Unbounded buffer |
| BufferFullError when buffer full | MISSING | No buffer limit, no error |
| Events Store: track last sequence, re-subscribe from seq+1 | MISSING | No sequence tracking |
| Queue stream: re-establish stream, unacked via visibility | PARTIAL | No explicit re-establishment logic |
| Stream vs connection error distinction | MISSING | Not implemented |
| Buffer lifecycle (FIFO, discard on CLOSED) | MISSING | No lifecycle management |

**REQ-CONN-1 Overall Status: MISSING**
**Priority: P0**

**Remediation:**
- **What:**
  1. Implement `ReconnectionPolicy` dataclass: `max_attempts=-1`, `initial_delay=0.5`, `max_delay=30.0`, `multiplier=2.0`, `jitter=FULL`, `buffer_size_bytes=8_388_608`
  2. Add auto-reconnection to `AsyncTransport.connect()` with exponential backoff using `asyncio.sleep()`
  3. Create `ReconnectBuffer` class with bounded `collections.deque` tracking byte size
  4. Implement `BufferFullError(KubeMQError)` raised when buffer exceeds limit
  5. Add subscription recovery logic:
     - Events: re-subscribe to same channel
     - Events Store: track `_last_sequence: int` per subscription; re-subscribe with `StartFromSequence(last_seq + 1)`
     - Queue stream: re-establish stream
     - RPC: re-subscribe handlers
  6. Implement DNS re-resolution by creating new `grpc.aio.Channel` on each reconnect attempt
  7. Add `OnBufferDrain` callback for CLOSED transition
- **Complexity:** XL (7 days) — most complex single requirement; affects both sync and async paths, requires buffer management, subscription tracking, and state machine integration
- **Dependencies:** REQ-CONN-2 (state machine), REQ-ERR-3 (backoff calculation can be shared)
- **Risk:** Without async reconnection, any network glitch permanently breaks async clients. This is the #1 infrastructure reliability gap.
- **Breaking change:** No — additive behavior. Existing clients that handle reconnection manually will now get automatic reconnection.
- **Language-specific:** Use `asyncio.Event` for reconnection signaling. `weakref.finalize()` for buffer cleanup (ensures cleanup even if user forgets `close()`, without the reliability issues of `__del__`). Track buffer byte size via serialized message size: `len(message.body) + len(message.metadata.encode()) + sum(len(k)+len(v) for k,v in message.tags.items())` or `message.encode().ByteSize()` for protobuf wire format. Do NOT use `sys.getsizeof()` — it returns CPython object overhead, not payload size.

### REQ-CONN-2: Connection State Machine

**Current State:**
- Internal `ConnectionState` in `ChannelManager` tracks state
- `_monitor_connection` thread in QueuesClient logs changes
- **No user-facing callbacks** exposed
- Assessment 3.2.5: 2/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Current state queryable via method | PARTIAL | `is_connected` property exists on clients; no `state()` method returning enum |
| 2 | State transitions fire callbacks/events | MISSING | No user callbacks |
| 3 | Users can register handlers (OnConnected, OnDisconnected, etc.) | MISSING | No handler registration |
| 4 | Handlers invoked asynchronously | MISSING | No handlers exist |
| 5 | State included in log messages during transitions | MISSING | Logging effectively off |

**REQ-CONN-2 Overall Status: MISSING**
**Priority: P0**

**Remediation:**
- **What:** Create `ConnectionState` enum: `IDLE`, `CONNECTING`, `READY`, `RECONNECTING`, `CLOSED`. Add `state: ConnectionState` property to all client classes. Create `ConnectionCallbacks` dataclass with optional callables: `on_connected`, `on_disconnected`, `on_reconnecting`, `on_reconnected`, `on_closed`. Accept `callbacks` parameter in `ClientConfig`. Fire callbacks in background `asyncio.Task` (async) or `threading.Thread` (sync) to never block connection logic. Include state in all log messages: `logger.info("Connection state: RECONNECTING", state=state.name)`.
- **Complexity:** L (4 days)
- **Dependencies:** None (foundational for REQ-CONN-1)
- **Risk:** Without state machine, users cannot implement health checks, dashboards, or circuit breakers.
- **Breaking change:** No — additive.
- **Language-specific:** Use `enum.Enum` with `auto()`. Callbacks as `Optional[Callable[[ConnectionState], None]]`. Async callbacks via `asyncio.create_task()`.

### REQ-CONN-3: gRPC Keepalive Configuration

**Current State:**
- `KeepAliveConfig` in transport with `ping_interval_in_seconds`, `ping_timeout_in_seconds`, `permit_without_calls`
- Maps to 5 gRPC channel options
- Assessment 3.1.6: 4/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Keepalive enabled by default | COMPLIANT | Enabled |
| 2 | All three parameters configurable | COMPLIANT | Yes |
| 3 | Dead connections detected within keepalive_time + timeout (15s) | PARTIAL | Need to verify defaults match GS (10s + 5s) vs current SDK defaults |
| 4 | Compatible with server enforcement policy | PARTIAL | Need to verify server policy compatibility |

**REQ-CONN-3 Overall Status: PARTIAL**
**Priority: P2**

**Remediation:**
- **What:** Verify that `KeepAliveConfig` defaults are `ping_interval=10`, `ping_timeout=5`, `permit_without_calls=True`. If different, adjust. Add documentation note about cloud load balancer idle timeout considerations. Add server idle timeout note (5 min).
- **Complexity:** S (0.5 days)
- **Dependencies:** None
- **Risk:** Low — keepalive already works. Risk is only if defaults don't match GS.
- **Breaking change:** Potentially if defaults change — document the change.

### REQ-CONN-4: Graceful Shutdown / Drain

**Current State:**
- `close()` is idempotent with `_closing` flag
- `AsyncTransport.close()` cancels all active streams via `_active_streams` set
- No explicit `drain()` API
- No SIGTERM integration docs
- Assessment 3.4.2: 3/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Close()/Shutdown() initiates graceful shutdown | COMPLIANT | `close()` exists |
| 2 | Optional timeout for max drain duration (default: 5s) | MISSING | No drain timeout |
| 3 | In-flight operations complete before close | PARTIAL | Active streams cancelled, not drained |
| 3a | Sync transport close() reliable in all contexts | MISSING | `SyncTransport.close()` uses `get_event_loop().run_until_complete()` (assessment 2.1.5: 4/5) — raises `RuntimeError: This event loop is already running` in async-compatible environments (Jupyter, FastAPI, nested async). Known Python anti-pattern. |
| 4 | Buffered messages flushed before close | MISSING | No explicit flush |
| 5 | New operations after Close() return ErrClientClosed | MISSING | No evidence of post-close error |
| 6 | Close() is idempotent | COMPLIANT | `_closing` flag |
| 7 | Close() during RECONNECTING cancels reconnection and discards buffers | MISSING | No state machine |

**REQ-CONN-4 Overall Status: MISSING (3+ MISSING)**
**Priority: P0**

**Remediation:**
- **What:** Add `drain_timeout: float = 5.0` parameter to `close()`. Implement drain sequence: (1) set `_draining` flag to reject new operations, (2) flush `ReconnectBuffer`, (3) `await asyncio.wait(active_tasks, timeout=drain_timeout)`, (4) close channel, (5) fire `OnClosed` callback. Add `KubeMQClientClosedError` returned when operations are attempted on a closed/draining client. Handle `close()` during RECONNECTING: cancel reconnection task, discard buffer, fire `OnBufferDrain` if registered.
- **Complexity:** M (2 days)
- **Dependencies:** REQ-CONN-1 (buffer), REQ-CONN-2 (state machine)
- **Risk:** Without drain, in-flight messages are lost on shutdown. Critical for queue producers.
- **Breaking change:** No — `close()` signature unchanged (timeout is optional kwarg).
- **Language-specific:** Use `asyncio.wait()` with `return_when=ALL_COMPLETED` and timeout. Use `asyncio.shield()` to protect in-flight callbacks from cancellation during the drain period. `atexit.register()` for process exit hook. For sync transport close, replace `get_event_loop().run_until_complete()` with `asyncio.run()` (Python 3.7+) or detect if a loop is running and use `loop.call_soon_threadsafe()` for cross-thread cleanup. Do NOT implement `__del__` for resource cleanup — it is unreliable in Python (not guaranteed to run, GC timing varies, cannot access other objects being collected). Use context managers (`__enter__`/`__exit__`) and explicit `close()` as the SDK already does.

### REQ-CONN-5: Connection Configuration

**Current State:**
- `ClientConfig` with `address`, `default_timeout_seconds=30`
- `max_send_size`/`max_receive_size` default 100MB
- Initial ping verification with 5s timeout in `async_transport.py:118`
- No `WaitForReady` option
- Assessment 3.1.1: 4/5, 3.2.8: 3/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | All connection parameters configurable | PARTIAL | Most present; missing `WaitForReady`, default address |
| 2 | Defaults match GS table | PARTIAL | Address no default (`localhost:50000` in GS); connection timeout 5s vs 10s in GS; max message sizes match |
| 3 | Connection timeout applies to initial only | PARTIAL | 5s ping timeout exists, but value should be 10s per GS |
| 4 | Invalid config rejected at construction (fail-fast) | COMPLIANT | `ClientConfig.__post_init__` validates |
| 5 | WaitForReady applies to both CONNECTING and RECONNECTING | NOT_ASSESSED | WaitForReady not mentioned in assessment |

**REQ-CONN-5 Overall Status: PARTIAL (1 NOT_ASSESSED, rest PARTIAL)**
**Priority: P1**

**Remediation:**
- **What:** Add `wait_for_ready: bool = True` to `ClientConfig`. Change default connection timeout from 5s to 10s. Set default address to `"localhost:50000"`. Implement `WaitForReady` behavior: when true, operations block during CONNECTING/RECONNECTING (via `asyncio.Event.wait()` or `threading.Event.wait()`); when false, raise `ConnectionNotReadyError` immediately if not READY.
- **Complexity:** S (0.5 days)
- **Dependencies:** REQ-CONN-2 (state machine for WaitForReady)
- **Risk:** Low — configuration adjustments.
- **Breaking change:** YES — default connection timeout changes from 5s to 10s. Default address changes from required to optional with default.

### REQ-CONN-6: Connection Reuse

**Current State:**
- Single gRPC channel per client, reused for all operations
- Assessment 13.2.6: 4/5
- No per-operation channel creation

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Single Client uses one gRPC channel | COMPLIANT | Confirmed |
| 2 | Multiple operations multiplex over same channel | COMPLIANT | Confirmed |
| 3 | Documentation advises sharing client across threads | MISSING | No threading guidance docs |
| 4 | New channel per operation prohibited | COMPLIANT | Single channel reused |

**REQ-CONN-6 Overall Status: PARTIAL (1 MISSING)**
**Priority: P1**

**Remediation:**
- **What:** Add thread safety documentation to client class docstrings: "KubeMQ clients are thread-safe. Create one client instance and share it across threads/coroutines. Do not create a new client per request."
- **Complexity:** S (0.5 days)
- **Dependencies:** None
- **Risk:** Low — documentation only.
- **Breaking change:** No.
- **See also:** REQ-PERF-2 (connection reuse documentation — same core message, different location). Ensure consistent wording across both.

---

## Category 03: Auth & Security

**GS Category:** 03 | **Assessment Category:** 5 | **Score:** 2.60 / 5.0 | **Target:** 4.0 | **Gap:** +1.40

### REQ-AUTH-1: Token Authentication

**Current State:**
- Auth token passed via gRPC metadata `("authorization", auth_token)` through interceptors in `transport/interceptors.py`
- No token refresh — rotation requires new client instance
- `ClientConfig.__repr__()` masks auth_token with `'***'`
- Assessment 5.1.1: 4/5, 5.1.2: 1/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Static token via client options | COMPLIANT | `auth_token` in `ClientConfig` |
| 2 | Token sent as gRPC metadata on every request | COMPLIANT | Via interceptors |
| 3 | Token can be updated without recreating client | MISSING | Requires new client instance |
| 4 | Missing token → clear AuthenticationError | PARTIAL | gRPC returns UNAUTHENTICATED, mapped to `KubeMQAuthenticationError`; message could be clearer |
| 5 | Token never logged (even at DEBUG) | PARTIAL | `__repr__` masks; interceptor stores plaintext |

**REQ-AUTH-1 Overall Status: PARTIAL (1 MISSING)**
**Priority: P1**

**Remediation:**
- **What:** Make auth token mutable via `client.update_token(new_token: str)` method. Internally, update the interceptor's token reference. Use `threading.Lock` for thread-safe update. Ensure no code path logs the token value — audit all `logger.*` calls for token references. Add `token_present: bool` log field instead of token value.
- **Complexity:** M (2 days) — interceptor token needs to be a mutable reference; thread safety required
- **Dependencies:** None
- **Risk:** Without rotation, certificate/token lifecycle management requires client restart, causing message loss.
- **Breaking change:** No — additive method.
- **Language-specific:** Store token in `threading.local()` or use `property` with lock. Consider `weakref` for interceptor-to-config link.

### REQ-AUTH-2: TLS Encryption

**Current State:**
- TLS supported via `grpc.aio.secure_channel()` with `ssl_channel_credentials`
- `TLSConfig` accepts `ca_file`, `cert_file`, `key_file`
- TLS **disabled** by default — `grpc.insecure_channel()` used unless TLS explicitly configured
- No warning logged when using insecure mode
- No TLS version or cipher configuration
- Assessment 5.2.1: 1/5, 3.3: 3.40

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | TLS enabled with single option | COMPLIANT | `TLSConfig` |
| 2 | Custom CA certificates (file path or PEM bytes) | PARTIAL | File path only; no PEM bytes support |
| 3 | Server name override supported | MISSING | Not implemented |
| 4 | InsecureSkipVerify as separately named option | MISSING | No such option |
| 5 | WARNING log "certificate verification is disabled" on every connection with skip_verify | MISSING | No warning logged |
| 6 | TLS 1.2 minimum enforced | MISSING | No min TLS version enforcement |
| 7 | System CA bundle used by default when TLS enabled without custom CA | PARTIAL | Relies on gRPC defaults |
| 8 | TLS handshake failures classified per table | MISSING | No failure classification (auth vs transient vs config) |

**Body text gap:**

| Requirement | Status | Detail |
|------------|--------|--------|
| Localhost vs remote default (TLS auto for remote) | MISSING | TLS always off by default |
| Hostname verification documentation | MISSING | No hostname verification docs |

**REQ-AUTH-2 Overall Status: MISSING (5 MISSING)**
**Priority: P0**

**Remediation:**
- **What:**
  1. Add `insecure_skip_verify: bool = False` as named field on `TLSConfig` (not a generic boolean)
  2. Log `WARNING` containing "certificate verification is disabled" on every connection when `insecure_skip_verify=True`
  3. Add `server_name_override: str | None = None` to `TLSConfig`
  4. Add `min_tls_version: str = "1.2"` to `TLSConfig`
  5. Accept PEM bytes (`ca_pem: bytes | None`, `cert_pem: bytes | None`, `key_pem: bytes | None`) alongside file paths
  6. Implement TLS default: detect localhost addresses (`localhost`, `127.0.0.1`, `::1`, `[::1]`); auto-enable TLS for remote addresses
  7. Classify TLS handshake failures: cert validation → `AuthenticationError`, network → `TransientError`, version/cipher → `ConfigurationError`
- **Complexity:** M (2 days)
- **Dependencies:** REQ-ERR-2 (error classification for handshake failures)
- **Risk:** TLS disabled by default is a security vulnerability. Any production deployment without explicit TLS config is unencrypted.
- **Breaking change:** YES — TLS auto-enabled for remote addresses changes default behavior. Users connecting to remote servers without TLS will break. Provide `tls_enabled=False` explicit opt-out with WARNING log.
- **Backward compatibility:** Detect if user explicitly set `tls=None` (unset) vs `tls=TLSConfig(enabled=False)` (explicit opt-out). Only auto-enable TLS when unset.

### REQ-AUTH-3: Mutual TLS (mTLS)

**Current State:**
- `TLSConfig` accepts `cert_file`, `key_file`, `ca_file`
- Mapped to `private_key` and `certificate_chain` in `ssl_channel_credentials`
- Assessment 3.3.3: 4/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | mTLS configurable with 3 params | COMPLIANT | cert_file, key_file, ca_file |
| 2 | Both file paths and in-memory PEM bytes accepted | PARTIAL | File paths only |
| 3 | Invalid certificates produce clear error at connection (fail-fast) | PARTIAL | gRPC reports error but message may not be clear |
| 4 | Certificate errors classified as AuthenticationError | PARTIAL | Unclear if cert errors map correctly |
| 5 | mTLS documented with examples | MISSING | No auth guide |
| 6 | On reconnection, reload TLS credentials from original source | MISSING | No credential reload on reconnect |
| 7 | Changed certificates on disk used on reconnect | MISSING | No reload mechanism |
| 8 | Documentation with env var cert loading via PEM bytes | MISSING | No PEM bytes API |

**REQ-AUTH-3 Overall Status: MISSING (4 MISSING)**
**Priority: P0**

**Remediation:**
- **What:** Add PEM bytes parameters to `TLSConfig` (see REQ-AUTH-2). On reconnection, re-read certificate files from disk or re-invoke PEM provider callback. Add `on_cert_reload` callback for observability. Create mTLS example showing file-based and env-var-based cert loading. Document Kubernetes cert-manager rotation workflow.
- **Complexity:** M (2 days) — PEM bytes support is straightforward; credential reload requires integration with reconnection logic
- **Dependencies:** REQ-CONN-1 (reconnection lifecycle for cert reload), REQ-AUTH-2 (PEM bytes support)
- **Risk:** Without cert reload, Kubernetes cert-manager rotation breaks mTLS connections until pod restart.
- **Breaking change:** No — additive parameters.

### REQ-AUTH-4: Credential Provider Interface

**Current State:**
- NOT_ASSESSED — Assessment didn't evaluate this. No credential provider interface exists.
- Static token only, set at construction time
- No token refresh, no OIDC, no vault integration

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | `CredentialProvider` interface with `GetToken()` → `(token, expiresAt, error)` | NOT_ASSESSED | Added post-assessment. Requires fresh evaluation. |
| 2 | Static token as built-in provider | NOT_ASSESSED | Added post-assessment. |
| 3 | Custom providers (vault, OIDC) possible | NOT_ASSESSED | Added post-assessment. |
| 4 | Reactive refresh on UNAUTHENTICATED | NOT_ASSESSED | Added post-assessment. |
| 5 | Proactive refresh when expiresAt provided | NOT_ASSESSED | Added post-assessment. |
| 6 | Serialized calls to provider | NOT_ASSESSED | Added post-assessment. |
| 7 | Provider during CONNECTING and RECONNECTING | NOT_ASSESSED | Added post-assessment. |
| 8 | Provider errors classified (auth vs transient) | NOT_ASSESSED | Added post-assessment. |
| 9 | OIDC provider example documented | NOT_ASSESSED | Added post-assessment. |

**REQ-AUTH-4 Overall Status: NOT_ASSESSED**
**Priority: NOT_ASSESSED — Likely P0 when assessed; recommend immediate assessment**

**Remediation:**
- **What:** Define `CredentialProvider` Protocol:
  ```python
  class CredentialProvider(Protocol):
      def get_token(self) -> tuple[str, datetime | None]:
          """Return (token, optional_expiry). Raise on failure."""
          ...
  ```
  Implement `StaticTokenProvider(CredentialProvider)` for backward compatibility. Add `credential_provider: CredentialProvider | None = None` to `ClientConfig`. Implement reactive refresh: on `UNAUTHENTICATED` response, invalidate cached token, re-invoke provider. Implement proactive refresh: when `expires_at` is provided, schedule refresh at `expires_at - 30s` via `asyncio.create_task()`. Serialize provider calls with `asyncio.Lock()` / `threading.Lock()`. On CONNECTING/RECONNECTING, invoke provider subject to connection timeout. Create OIDC example using `authlib` or `requests-oauthlib`.
- **Complexity:** L (4 days) — new abstraction with complex lifecycle management
- **Dependencies:** REQ-CONN-2 (state machine for provider invocation timing), REQ-ERR-2 (error classification for provider errors)
- **Risk:** Without credential provider, enterprise deployments (Vault, OIDC, cloud IAM) cannot integrate. Token rotation requires client restart.
- **Breaking change:** No — additive. Existing `auth_token` field continues to work via `StaticTokenProvider`.
- **Language-specific:** Use `typing.Protocol` (PEP 544) for structural subtyping. `datetime` for expiry. `asyncio.Lock` for serialization.

### REQ-AUTH-5: Security Best Practices

**Current State:**
- `ClientConfig.__repr__()` masks auth_token with `'***'` at `config.py:143`
- Interceptors store plaintext token
- No warning for insecure connections
- Assessment 5.2: 3.00

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Credentials excluded from: logs, errors, OTel spans, interceptor metadata, toString | PARTIAL | `__repr__` masks; interceptor stores plaintext; no OTel audit |
| 2 | Log only `token_present: true/false` for debugging | MISSING | No such pattern |
| 3 | TLS files validated at construction time (fail-fast) | PARTIAL | Connect-time validation, not construction-time |
| 4 | Security configuration guide with examples | MISSING | No security guide |
| 5 | InsecureSkipVerify emits warning on every connection | MISSING | No warning |

**REQ-AUTH-5 Overall Status: MISSING (3 MISSING)**
**Priority: P0**

**Remediation:**
- **What:** Audit all code paths for credential logging. Replace interceptor's plaintext token storage with a `SecureString` wrapper that returns `"***"` from `__repr__`/`__str__`. Validate TLS certificate files exist and are readable at `ClientConfig.__post_init__()`. Add `token_present: bool` log field. Create `docs/security.md` covering token auth, TLS, mTLS, and credential rotation.
- **Complexity:** M (1-2 days) — comprehensive credential audit across all code paths (0.5-1d), `SecureString` implementation + TLS validation changes (0.5d), `docs/security.md` covering all auth methods (0.5-1d).
- **Dependencies:** REQ-AUTH-2 (InsecureSkipVerify)
- **Risk:** Credential leakage in logs is a security vulnerability.
- **Breaking change:** No.

### REQ-AUTH-6: TLS Credentials During Reconnection

**Current State:**
- NOT_ASSESSED — Assessment didn't evaluate this. No credential reload on reconnection.

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | On reconnection, reload certificates from configured source | NOT_ASSESSED | Added post-assessment. Requires fresh evaluation. |
| 2 | Source error (file missing, permission denied) treated as transient, retried per backoff | NOT_ASSESSED | Added post-assessment. |
| 3 | TLS handshake failure after reload classified per REQ-AUTH-2 | NOT_ASSESSED | Added post-assessment. |
| 4 | Certificate reload logged at DEBUG | NOT_ASSESSED | Added post-assessment. |
| 5 | Certificate reload errors logged at ERROR | NOT_ASSESSED | Added post-assessment. |

**REQ-AUTH-6 Overall Status: NOT_ASSESSED**
**Priority: NOT_ASSESSED — Likely P0 when assessed; recommend immediate assessment**

**Remediation:**
- **What:** On each reconnection attempt in `AsyncTransport` and `SyncTransport`, re-read certificate files from disk (or re-invoke PEM provider if using callback). Cache credentials with timestamp; skip reload if files haven't changed (check `os.path.getmtime()`). If file read fails, treat as transient error and retry per reconnection backoff. Log reload at DEBUG, errors at ERROR.
- **Complexity:** M (2 days)
- **Dependencies:** REQ-CONN-1 (reconnection lifecycle), REQ-AUTH-2 (TLS configuration), REQ-AUTH-3 (mTLS credentials)
- **Risk:** Without this, cert-manager rotation in Kubernetes breaks mTLS until pod restart.
- **Breaking change:** No — behavior change is transparent.

---

## Category 04: Testing

**GS Category:** 04 | **Assessment Category:** 9 | **Score:** 2.73 / 5.0 | **Target:** 4.0 | **Gap:** +1.27

### REQ-TEST-1: Unit Tests with Mocked Transport

**Current State:**
- 851 unit tests across 53 test files, all pass
- Coverage estimated 60-70% (not enforced in CI)
- Tests cover core logic, encode/decode, config validation, exception hierarchy
- `pytest-mock` used for transport mocking
- Assessment 9.1: 3.50

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Coverage meets phased target (Phase 1: ≥40%) | PARTIAL | Likely meets Phase 1/2, but not enforced in CI |
| 2 | All error classification paths tested | PARTIAL | Exception hierarchy tested; no retryable classification to test |
| 3 | All retry scenarios tested | MISSING | No retry system exists |
| 4 | Configuration validation tested | COMPLIANT | Assessment confirms |
| 5 | Coverage enforced in CI — build fails below threshold | MISSING | No CI test pipeline |
| 6 | Client close + resource leak check | MISSING | No leak detection (e.g., no unclosed coroutine check) |
| 7 | Operations on closed client return ErrClientClosed | MISSING | Not tested/implemented |
| 8 | Oversized messages produce validation error | NOT_ASSESSED | Assessment didn't verify |
| 9 | Empty/nil payloads handled correctly | PARTIAL | Model validators tested; edge cases may be missing |
| 10 | Per-test timeout enforced (30s unit, 60s integration) | NOT_ASSESSED | Assessment didn't check test timeouts |

**Body text requirements gap:**

| Requirement | Status | Detail |
|------------|--------|--------|
| Error classification for all 17 gRPC codes via mocked transport | MISSING | No such tests |
| Retry policy behavior tested | MISSING | No retry exists |
| Connection state machine transitions tested | MISSING | No state machine |
| Auth token injection verified via mocked transport | PARTIAL | Interceptor tests may exist |
| Concurrent publish doesn't corrupt state | MISSING | No concurrent tests |
| Mock via `grpc_testing` | PARTIAL | Uses `pytest-mock`, not `grpc_testing` |

**REQ-TEST-1 Overall Status: MISSING (5+ MISSING)**
**Priority: P0**

**Remediation:**
- **What:** Add tests for: (1) error classification for all 17 gRPC codes, (2) retry policy (backoff calculation, exhaustion, non-retryable bypass), (3) connection state machine transitions, (4) auth token injection into gRPC metadata, (5) concurrent publish safety. Add `pytest-timeout` with `@pytest.mark.timeout(30)` for unit tests. Add resource leak detection: `@pytest.fixture(autouse=True)` checking for unclosed coroutines via `asyncio.all_tasks()`. Enforce coverage in CI with `--cov-fail-under=40` (Phase 1).
- **Complexity:** L (4 days) — tests require new features to exist first
- **Dependencies:** REQ-ERR-2 (classification), REQ-ERR-3 (retry), REQ-CONN-2 (state machine), REQ-TEST-3 (CI pipeline)
- **Risk:** Without test enforcement, regressions go undetected.
- **Breaking change:** No.
- **Language-specific:** Use `pytest-asyncio` for async tests. `grpc_testing` for mocked transport. `hypothesis` for property-based testing of retry backoff calculation.

### REQ-TEST-2: Integration Tests Against Real Server

**Current State:**
- 3 integration test files: `test_async_pubsub.py`, `test_async_queues.py`, `test_async_cq.py`
- Require live KubeMQ server
- Async only — no sync integration tests
- Assessment 9.2: 2.60

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Integration tests for all 4 messaging patterns | PARTIAL | Async PubSub, Queues, CQ present; no sync tests |
| 2 | Clearly separated from unit tests | COMPLIANT | `tests/integration/` with `@pytest.mark.integration` |
| 3 | Can be skipped when no server | COMPLIANT | Markers/env var |
| 4 | Each test independent — no shared state | PARTIAL | No evidence of isolation |
| 5 | Tests clean up resources | NOT_ASSESSED | Assessment didn't verify cleanup |
| 6 | Unsubscribe while messages in flight completes without leaks | MISSING | No such test |
| 7 | Each test uses unique channel name | NOT_ASSESSED | Assessment didn't verify channel naming |

**Body text requirements gap:**

| Requirement | Status | Detail |
|------------|--------|--------|
| Auth token validation (valid, invalid, expired) | MISSING | No auth integration tests |
| TLS connection test | MISSING | No TLS integration tests |
| Reconnection after server restart | MISSING | No reconnection tests |
| Graceful shutdown / drain test | MISSING | No shutdown tests |
| Server via Docker or subprocess | PARTIAL | Manual server required |

**REQ-TEST-2 Overall Status: PARTIAL (1+ MISSING, several NOT_ASSESSED)**
**Priority: P1**

**Remediation:**
- **What:** Add sync integration tests mirroring async ones. Add unique channel names via `f"test-{pattern}-{uuid4()}"`. Add `conftest.py` with auto-cleanup fixture (delete test channels after each test). Add auth, TLS, and reconnection integration tests. Set up KubeMQ server via Docker in test fixtures using `docker` Python package or `subprocess`.
- **Complexity:** L (4 days)
- **Dependencies:** REQ-TEST-3 (CI pipeline to run them), REQ-CONN-1 (reconnection to test)
- **Risk:** Without integration tests, regressions in server interaction go undetected.
- **Breaking change:** No.

### REQ-TEST-3: CI Pipeline

**Current State:**
- `.github/workflows/deploy.yml` handles PyPI deployment only (tag push)
- No test pipeline — tests never run in CI
- No linting in CI — 204 ruff errors exist
- Single Python version (`3.x`)
- Assessment 9.3: 1.20

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | CI runs on every PR and push to main | MISSING | Tag push only |
| 2 | Unit tests across 2-3 language versions | MISSING | Single version |
| 3 | Integration tests against real server in CI | MISSING | No CI integration |
| 4 | Linter runs and blocks merge | MISSING | 204 existing errors |
| 5 | Coverage reported to Codecov | MISSING | No coverage reporting |
| 6 | Coverage threshold enforced per phase | MISSING | No threshold |

**REQ-TEST-3 Overall Status: MISSING**
**Priority: P0**

**Remediation:**
- **What:** Create `.github/workflows/ci.yml`:
  ```yaml
  on: [pull_request, push: {branches: [main, v4]}]
  jobs:
    lint: {runs-on: ubuntu-latest, steps: [ruff check, ruff format --check, mypy]}
    unit-tests: {strategy: {matrix: {python: ['3.9', '3.11', '3.13']}}}
    integration: {runs-on: ubuntu-latest, services: {kubemq: {image: kubemq/kubemq:latest}}}
    coverage: {steps: [pytest --cov --cov-report=xml, codecov/codecov-action]}
  ```
- **Complexity:** M (2 days) — GitHub Actions setup is well-documented; integration server setup requires Docker service
- **Dependencies:** REQ-CQ-3 (fix lint errors first)
- **Risk:** Without CI, broken code merges to main. Deployment publishes untested code to PyPI.
- **Breaking change:** No.
- **Language-specific:** Use `actions/setup-python@v5` with matrix. `pytest-cov` for coverage. KubeMQ Docker image as GitHub Actions service container.

### REQ-TEST-4: Test Organization

**Current State:**
- `tests/unit/` and `tests/integration/` directories
- `@pytest.mark.integration` markers
- Assessment 9.1, 9.2

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Unit and integration tests separated | COMPLIANT | Separate directories |
| 2 | Default test command runs unit tests only | PARTIAL | Likely needs `-m "not integration"` |
| 3 | Integration tests require explicit flag | COMPLIANT | `@pytest.mark.integration` |
| 4 | Test helpers/fixtures shared via testutil package | PARTIAL | Some shared fixtures may exist; no `testutil` package confirmed |

**REQ-TEST-4 Overall Status: PARTIAL**
**Priority: P2**

**Remediation:**
- **What:** Add `pytest.ini` or `pyproject.toml` config: `addopts = "-m 'not integration'"` so `pytest` runs unit tests by default. Create `tests/testutil/` package with shared fixtures (mock transport factory, test message builders, unique channel name generator).
- **Complexity:** S (0.5 days)
- **Dependencies:** None
- **Risk:** Low.
- **Breaking change:** No.

### REQ-TEST-5: Coverage Tools

**Current State:**
- Coverage configured in `pyproject.toml` (`[tool.coverage]`) with branch coverage
- Not run in CI
- No Codecov integration
- Assessment 9.1.2: 3/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Coverage tool configured and runs in CI | MISSING | Configured but not in CI |
| 2 | Coverage report in standard format | PARTIAL | Configuration present, generation not verified |
| 3 | Coverage uploaded to Codecov or equivalent | MISSING | No upload |
| 4 | Generated/vendored code excluded | NOT_ASSESSED | Need to verify exclusion rules |

**REQ-TEST-5 Overall Status: MISSING (2 MISSING)**
**Priority: P0 (3+ MISSING when including CI dependency)**

**Remediation:**
- **What:** Add `--cov=kubemq --cov-report=xml --cov-fail-under=40` to CI pytest invocation. Add `codecov/codecov-action@v4` step in CI. Exclude `src/kubemq/grpc/` (generated protobuf) from coverage via `[tool.coverage.run] omit`.
- **Complexity:** S (0.5 days)
- **Dependencies:** REQ-TEST-3 (CI pipeline must exist)
- **Risk:** Without coverage enforcement, test quality degrades over time.
- **Breaking change:** No.

---

## Category 05: Observability

**GS Category:** 05 | **Assessment Category:** 7 | **Score:** 1.50 / 5.0 | **Target:** 4.0 | **Gap:** +2.50

### REQ-OBS-1: OpenTelemetry Trace Instrumentation

**Current State:**
- No span creation anywhere in the SDK
- No OpenTelemetry dependency
- Assessment 7.3: 1.00

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Spans created for all messaging operations | MISSING | No OTel integration |
| 2 | All required attributes set on every span | MISSING | No spans exist |
| 3 | Failed operations set span status to ERROR | MISSING | No spans exist |
| 4 | Batch operations set `messaging.batch.message_count` | MISSING | No spans exist |
| 5 | Span names follow `{operation} {channel}` format | MISSING | No spans exist |
| 6 | Retry attempts recorded as span events | MISSING | No retry, no spans |
| 7 | Batch consume follows receive/process pattern with links | MISSING | No spans exist |

**REQ-OBS-1 Overall Status: MISSING**
**Priority: P0**

**Remediation:**
- **What:** Add `opentelemetry-api` as optional dependency (`pip install kubemq[otel]`). Create `_internal/telemetry.py` module. Define semconv constants in `_internal/semconv.py` (all attribute names as `str` constants). Create `TracingMiddleware` class that wraps operations with spans. For each operation: create span with correct kind (PRODUCER/CONSUMER/CLIENT/SERVER), set required attributes (`messaging.system="kubemq"`, `messaging.operation.name`, `messaging.destination.name`, `messaging.message.id`, `messaging.client.id`, `server.address`, `server.port`). On failure, set span status to ERROR. Record retry attempts as span events with `retry.attempt`, `retry.delay_seconds`, `error.type` attributes. For batch operations, set `messaging.batch.message_count`. Use span links for producer-consumer correlation.
- **Complexity:** XL (7 days) — comprehensive instrumentation across all operations, both sync and async
- **Dependencies:** REQ-ERR-3 (retry events to record), REQ-OBS-4 (architecture for optional dependency)
- **Risk:** Without tracing, production debugging is limited to logs. End-to-end message flow is invisible.
- **Breaking change:** No — optional dependency, no-op when not configured.
- **Language-specific:** Use `opentelemetry-api` (not `opentelemetry-sdk`). `from opentelemetry import trace` with guard for import. Python `contextmanager`/`asynccontextmanager` for span lifecycle. Use `importlib.util.find_spec("opentelemetry")` for feature detection.

### REQ-OBS-2: W3C Trace Context Propagation

**Current State:**
- No trace context propagation
- Tags are `dict[str, str]` which could carry trace context
- Assessment 7.3.1: 1/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | `traceparent`/`tracestate` injected into published messages | MISSING | No injection |
| 2 | Consumers extract trace context and create linked spans | MISSING | No extraction |
| 3 | Trace context survives round-trip | MISSING | No propagation |
| 4 | Batch publishes inject per-message context | MISSING | No injection |
| 5 | Missing trace context handled gracefully | MISSING | No extraction code |
| 6 | Trace context preserved through requeue/DLQ | MISSING | No propagation |

**REQ-OBS-2 Overall Status: MISSING**
**Priority: P0**

**Remediation:**
- **What:** Implement `KubeMQTagsCarrier` class implementing OTel `TextMapCarrier` over message `tags` dict. On publish: `propagator.inject(carrier=KubeMQTagsCarrier(message.tags))` — adds `traceparent` and `tracestate` to tags. On consume: `ctx = propagator.extract(carrier=KubeMQTagsCarrier(message.tags))` — extracts context. Create consumer span linked to extracted context (not parented). Handle missing trace context gracefully (create new root span). For requeue/DLQ: preserve `traceparent`/`tracestate` tags through operations.
- **Complexity:** L (4 days) — carrier implementation is moderate; integration with all messaging patterns requires touching many files
- **Dependencies:** REQ-OBS-1 (spans must exist), REQ-OBS-4 (optional dependency architecture)
- **Risk:** Without trace propagation, distributed tracing cannot cross KubeMQ message boundaries.
- **Breaking change:** No — adds tags transparently. Existing tag keys are preserved.
- **Language-specific:** Use `opentelemetry.propagators.textmap.TextMapPropagator`. Python Protocol for carrier interface.

### REQ-OBS-3: OpenTelemetry Metrics

**Current State:**
- No metrics hooks or callbacks
- No counters, histograms, or gauges
- Assessment 7.2: 1.00

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | All required metrics emitted | MISSING | No metrics system |
| 2 | Correct instrument types | MISSING | No metrics |
| 3 | Names follow OTel conventions | MISSING | No metrics |
| 4 | Required attributes present | MISSING | No metrics |
| 5 | Duration histograms use specified bucket boundaries | MISSING | No metrics |
| 6 | Cardinality management implemented | MISSING | No metrics |

**REQ-OBS-3 Overall Status: MISSING**
**Priority: P0**

**Remediation:**
- **What:** Create `MetricsMiddleware` in `_internal/telemetry.py`. Create Meter with SDK instrumentation scope. Implement all 7 required metrics:
  - `messaging.client.operation.duration` (Histogram, seconds)
  - `messaging.client.sent.messages` (Counter)
  - `messaging.client.consumed.messages` (Counter)
  - `messaging.client.connection.count` (UpDownCounter)
  - `messaging.client.reconnections` (Counter)
  - `kubemq.client.retry.attempts` (Counter)
  - `kubemq.client.retry.exhausted` (Counter)
  Set histogram boundaries to GS-specified values. Add `error.type` mapping from error categories. Implement cardinality management for `messaging.destination.name`: track unique channel count, omit attribute above threshold (default: 100), support allowlist, WARN log on threshold exceeded.
- **Complexity:** L (4 days) — metrics implementation is systematic but touches all operations
- **Dependencies:** REQ-OBS-4 (optional dependency), REQ-ERR-2 (error.type mapping), REQ-CONN-2 (connection state for connection metrics)
- **Risk:** Without metrics, ops teams cannot monitor SDK health.
- **Breaking change:** No — optional, no-op by default.

### REQ-OBS-4: Near-Zero Cost When Not Configured

**Current State:**
- No OTel dependency at all
- Assessment: N/A (no OTel system exists)

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | OTel API only dependency (not SDK) | MISSING | No OTel at all |
| 2 | No-op provider when SDK not registered | MISSING | No OTel |
| 3 | TracerProvider/MeterProvider injectable via options | MISSING | No OTel |
| 4 | Guard with `span.IsRecording()` | MISSING | No OTel |
| 5 | OTel integration documented with setup example | MISSING | No OTel |
| 6 | Less than 1% latency overhead with no-op | MISSING | No OTel |

**REQ-OBS-4 Overall Status: MISSING**
**Priority: P0**

**Remediation:**
- **What:** Add `opentelemetry-api>=1.20` as optional dependency in `pyproject.toml` under `[project.optional-dependencies] otel`. Use `importlib.util.find_spec("opentelemetry")` to check availability at import time. When unavailable, use stub no-op classes. When available but no provider registered, OTel API returns no-op automatically. Accept `tracer_provider` and `meter_provider` in `ClientConfig`. If not provided, use `trace.get_tracer_provider()` / `metrics.get_meter_provider()` globals. Guard expensive attribute computation: `if span.is_recording(): attrs = {...}`. Document minimum supported OTel API version in README. Create `examples/observability/otel_setup.py` showing Jaeger/OTLP export.
- **Complexity:** M (2 days) — architecture decision; implementation follows from REQ-OBS-1/2/3
- **Dependencies:** None (foundational for REQ-OBS-1/2/3)
- **Risk:** Without optional dependency architecture, OTel becomes a forced dependency.
- **Breaking change:** No.
- **Language-specific:** Use `extras_require` / `project.optional-dependencies` for optional dep. Feature detection via `importlib`. Python's `typing.TYPE_CHECKING` for import-time avoidance.

### REQ-OBS-5: Structured Logging Hooks

**Current State:**
- Uses Python's standard `logging` module throughout
- Plain f-string formatting, no structured key-value fields
- Default level: `CRITICAL + 1` (effectively silent)
- Logger configurable externally via `logging.getLogger("kubemq")`
- Assessment 7.1: 2.33

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Logger interface defined with structured key-value fields | MISSING | Uses stdlib logging with plain strings |
| 2 | Default logger is no-op | PARTIAL | Default level suppresses all output; but still allocates logger |
| 3 | User can inject preferred logger | PARTIAL | Stdlib logging allows external config; no SDK-level injection point |
| 4 | Log entries include `trace_id`/`span_id` when OTel active | MISSING | No OTel integration |
| 5 | Sensitive data never logged at any level | PARTIAL | Some masking, not comprehensive |
| 6 | Log levels appropriate (no INFO spam) | PARTIAL | Current levels seem reasonable |
| 7 | Per-message logging at DEBUG/TRACE only | PARTIAL | Needs audit |

**REQ-OBS-5 Overall Status: MISSING (3+ MISSING)**
**Priority: P0**

**Remediation:**
- **What:** Define `Logger` Protocol matching GS spec:
  ```python
  class Logger(Protocol):
      def debug(self, msg: str, **kwargs: Any) -> None: ...
      def info(self, msg: str, **kwargs: Any) -> None: ...
      def warn(self, msg: str, **kwargs: Any) -> None: ...
      def error(self, msg: str, **kwargs: Any) -> None: ...
  ```
  Implement `NoopLogger` as default. Implement `StdlibLoggerAdapter` wrapping `logging.Logger` with structured kwargs. Accept `logger: Logger | None = None` in `ClientConfig`. Add `trace_id` and `span_id` to kwargs when OTel context is active. Define log events per GS table: DEBUG for retries/keepalive/state transitions, INFO for connection/reconnection/subscription, WARN for insecure config/buffer near capacity, ERROR for connection failure/auth failure. Change default from `CRITICAL + 1` to using `NoopLogger` (no output unless user injects logger).
- **Complexity:** M (2 days) — interface definition + adapter + audit all log sites
- **Dependencies:** REQ-OBS-4 (OTel context for trace_id/span_id)
- **Risk:** Without structured logging, production debugging requires reading source code.
- **Breaking change:** YES — changing from stdlib `logging.getLogger("kubemq")` to injected `Logger` Protocol. Users who configured `kubemq` logger via stdlib will need to adapt. Provide `StdlibLoggerAdapter` for backward compatibility.
- **Backward compatibility:** If no logger injected, check if `logging.getLogger("kubemq")` has handlers configured; if so, automatically create a `StdlibLoggerAdapter` wrapping that logger, preserving existing configuration. The `Logger` Protocol should be designed to be structurally compatible with `logging.Logger` so that `logging.getLogger("kubemq")` can be passed directly without wrapping (i.e., `Logger.debug(msg, **kwargs)` matches `logging.Logger.debug(msg, **kwargs)`). This ensures seamless migration for users who configure logging via `logging.basicConfig()`, `dictConfig`, or frameworks like Django. Additionally, `structlog` (the most popular Python structured logging library) should be a design target: the `Logger` Protocol should be designed so that a `structlog.BoundLogger` instance satisfies it, enabling `ClientConfig(logger=structlog.get_logger())` without an adapter.

---

## Category 06: Documentation

**GS Category:** 06 | **Assessment Category:** 10 | **Score:** 2.00 / 5.0 | **Target:** 4.0 | **Gap:** +2.00

### REQ-DOC-1: Auto-Generated API Reference

**Current State:**
- ~80% of public methods have docstrings
- `mkdocs` + `mkdocstrings` in optional deps (planned, not built)
- No published API documentation site
- Assessment 10.1: 2.40

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | 100% of public types/methods/constants have doc comments | PARTIAL | ~80% coverage |
| 2 | Doc comment linter runs in CI (pydocstyle/ruff) | MISSING | Not in CI |
| 3 | API reference published and accessible via URL | MISSING | Not built |
| 4 | API reference regenerated on every release | MISSING | No build pipeline |

**REQ-DOC-1 Overall Status: PARTIAL (2 MISSING)**
**Priority: P1**

**Remediation:**
- **What:** Complete docstrings for remaining ~20% of public methods. Enable ruff `D` rules (pydocstyle) in `pyproject.toml`. Build mkdocs site with `mkdocstrings[python]`. Publish to GitHub Pages via CI on release. Ensure summary lines don't restate method names.
- **Complexity:** L (4 days) — docstring completion + mkdocs setup + CI integration
- **Dependencies:** REQ-TEST-3 (CI pipeline for linter enforcement)
- **Risk:** Without API docs, developers read source code. Increases support burden.
- **Breaking change:** No.

### REQ-DOC-2: README

**Current State:**
- README has install instructions but no quickstart code
- Incorrect Python version stated (says 3.2+, actual 3.9+)
- No troubleshooting, error handling, or contributing sections
- Assessment 10.4: 1.60

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | All 10 required sections present | MISSING | Missing: quick start, messaging patterns, error handling, troubleshooting, contributing |
| 2 | Installation instructions work for current published version | PARTIAL | v3 works; v4 not published |
| 3 | All code examples compile/run without errors | MISSING | README has no working code examples |
| 4 | Links use absolute URLs | NOT_ASSESSED | Not verified — trivially verifiable by reading the current README. Recommend assessing before implementation. |

**Body text requirements gap:**

| Required Section | Status |
|-----------------|--------|
| Title and badges | PARTIAL (no CI/coverage badges) |
| Description | PARTIAL |
| Installation | PARTIAL (version wrong) |
| Quick Start | MISSING |
| Messaging Patterns (with comparison table) | MISSING |
| Configuration | MISSING |
| Error Handling | MISSING |
| Troubleshooting | MISSING |
| Contributing | MISSING |
| License | PARTIAL |

**REQ-DOC-2 Overall Status: MISSING (2+ MISSING sections)**
**Priority: P1**

> **Severity note:** While only 2 formal acceptance criteria are MISSING, the body text requirements table reveals 7 of 10 required sections are MISSING or incomplete (Quick Start, Messaging Patterns, Configuration, Error Handling, Troubleshooting, Contributing, plus multiple PARTIAL sections). The README is the primary developer touchpoint and the GS emphasizes "first message in 5 minutes." The actual gap severity is closer to P0 despite the formal P1 classification.

**Remediation:**
- **What:** Rewrite README with all 10 required sections. Fix Python version to 3.9+. Add CI/coverage badges. Add messaging pattern comparison table. Add quickstart code examples. Add configuration options table. Add error handling section with retry defaults. Link to troubleshooting guide. Add Contributing section linking to CONTRIBUTING.md. Use absolute URLs throughout.
- **Complexity:** M (2 days)
- **Dependencies:** REQ-DOC-3 (quickstart content), REQ-DOC-5 (troubleshooting link)
- **Risk:** README is the first thing developers see. Poor README = poor first impression.
- **Breaking change:** No.

### REQ-DOC-3: Quick Start (First Message in 5 Minutes)

**Current State:**
- No quickstart code in README
- No per-pattern quickstart
- Assessment 10.4.2: 1/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Quick start works with zero config against localhost:50000 | MISSING | No quickstart exists |
| 2 | Code is copy-paste ready — no placeholders | MISSING | No quickstart |
| 3 | Each pattern (Events, Queues, RPC) has quickstart | MISSING | No quickstart |
| 4 | Total time from git clone to first message < 5 minutes | MISSING | No quickstart |

**REQ-DOC-3 Overall Status: MISSING**
**Priority: P0**

**Remediation:**
- **What:** Create quickstart examples for Events, Queues, and RPC patterns. Each should be ≤10 lines for send, ≤10 lines for receive. Include expected output. Test against `localhost:50000`. Embed in README under "Quick Start" section.
  ```python
  # Events - Send
  from kubemq import PubSubClient, EventMessage
  with PubSubClient(address="localhost:50000") as client:
      client.send_events_message(EventMessage(channel="events.hello", body=b"Hello KubeMQ!"))
  ```
- **Complexity:** S (0.5 days)
- **Dependencies:** None
- **Risk:** Without quickstart, time-to-first-message exceeds 5 minutes.
- **Breaking change:** No.

### REQ-DOC-4: Code Examples / Cookbook

**Current State:**
- 31 in-repo examples in `examples/` (v4 API, syntax valid)
- 27 cookbook examples (v2/v3 API, **all broken** with v4)
- Assessment 10.3: 2.50

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Every example self-contained and runnable | PARTIAL | In-repo: valid syntax. Cookbook: all broken |
| 2 | Examples have inline comments | PARTIAL | Some comments, not comprehensive |
| 3 | Examples directory has own README | NOT_ASSESSED | Not verified — trivially verifiable by checking if `examples/README.md` exists. Recommend assessing before implementation. |
| 4 | Examples tested in CI (compile check) | MISSING | No CI verification |
| 5 | Examples compile in CI — failures block merge | MISSING | No CI |

**Required examples gap:**

| Pattern | Status | Detail |
|---------|--------|--------|
| Events (basic, wildcard, multiple subscribers) | PARTIAL | Basic exists; wildcard/multi may be missing |
| Events Store (persistent, replay from seq/time) | PARTIAL | Basic exists |
| Queues (send/receive, ack/reject, DLQ, delay, peek, batch) | PARTIAL | Some exist |
| Queues Stream (upstream, downstream, visibility) | MISSING | Not in examples |
| RPC Commands (send, handle) | PARTIAL | Basic exists |
| RPC Queries (send, handle, cached) | PARTIAL | Basic exists |
| Configuration (TLS, mTLS, token auth, timeouts) | MISSING | No config examples for v4 |
| Observability (OTel setup with Jaeger/OTLP) | MISSING | No OTel |

**REQ-DOC-4 Overall Status: PARTIAL (2 MISSING)**
**Priority: P1**

**Remediation:**
- **What:** Rewrite cookbook for v4 API. Add missing examples: queue stream operations, TLS/mTLS/auth configuration, observability setup. Add `examples/README.md` listing all examples. Add CI step: `python -m py_compile examples/**/*.py` to verify syntax.
- **Complexity:** L-XL (5-7 days) — 27+ cookbook examples to rewrite plus new queue stream, TLS, auth, and OTel examples
- **Dependencies:** REQ-OBS-1 (OTel example requires OTel), REQ-AUTH-2 (TLS example requires TLS improvements)
- **Risk:** Broken cookbook means users cannot learn advanced features.
- **Breaking change:** No.

### REQ-DOC-5: Troubleshooting Guide

**Current State:**
- No troubleshooting guide exists
- Assessment 10.2.6: 1/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Minimum 11 entries covering required issues | MISSING | No guide |
| 2 | Each includes exact error message | MISSING | No guide |
| 3 | Solutions actionable | MISSING | No guide |
| 4 | Entries link to relevant sections | MISSING | No guide |

**REQ-DOC-5 Overall Status: MISSING**
**Priority: P0**

**Remediation:**
- **What:** Create `docs/TROUBLESHOOTING.md` with 11+ entries covering: connection refused/timeout, auth failed, authorization denied, channel not found, message too large, timeout/deadline exceeded, rate limiting, internal server error, TLS handshake failure, no messages received, queue message not acknowledged. Each entry: symptom, exact error message (from SDK error types), cause, step-by-step solution, code example if applicable. Link from README.
- **Complexity:** M (2 days) — content creation
- **Dependencies:** REQ-ERR-5 (error message format must be stable)
- **Risk:** Without troubleshooting guide, every error becomes a support ticket.
- **Breaking change:** No.

### REQ-DOC-6: CHANGELOG

**Current State:**
- No CHANGELOG.md
- `pyproject.toml` references URL that likely doesn't exist
- Assessment 10.4.5: 1/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | CHANGELOG.md exists in repo root | MISSING | No file |
| 2 | Entries grouped by version and date | MISSING | No file |
| 3 | Categories: Added, Changed, Deprecated, Removed, Fixed, Security | MISSING | No file |
| 4 | Breaking changes prominently marked | MISSING | No file |
| 5 | Each entry links to PR or commit | MISSING | No file |

**REQ-DOC-6 Overall Status: MISSING**
**Priority: P0**

**Remediation:**
- **What:** Create `CHANGELOG.md` in repo root following Keep a Changelog format. Retrospectively document v4.0.0-dev changes (breaking changes from v3, new async clients, new config system). Mark all breaking changes with `**BREAKING:**` prefix. Link to relevant commits/PRs.
- **Complexity:** S (0.5 days)
- **Dependencies:** None
- **Risk:** Without changelog, users cannot determine what changed between versions.
- **Breaking change:** No.
- **Note:** This is the same deliverable as REQ-PKG-4 (Tier 2). Effort should be counted only once; REQ-PKG-4 effort is deduplicated to 0.

### REQ-DOC-7: Migration Guide

**Current State:**
- No v3→v4 migration guide despite major API changes
- v4 changes all import paths, class names, configuration approach
- Assessment 10.2.4: 1/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Migration guide for every major version upgrade | MISSING | No v3→v4 guide |
| 2 | Every breaking change has before/after code example | MISSING | No guide |
| 3 | Guide linked from CHANGELOG and README | MISSING | No guide |

**REQ-DOC-7 Overall Status: MISSING**
**Priority: P0**

**Remediation:**
- **What:** Create `docs/MIGRATION-V3-V4.md` with: (1) breaking changes table (old import → new import, old class → new class, old method → new method), (2) before/after code snippets for every renamed/removed method, (3) step-by-step upgrade procedure. Document: import path changes (`from kubemq.basic.grpc_client import GrpcClient` → `from kubemq import PubSubClient`), config changes, async API changes, subscription model changes.
- **Complexity:** M (2 days) — requires mapping all v3→v4 changes
- **Dependencies:** None
- **Risk:** Without migration guide, v3 users cannot upgrade. Blocks v4 adoption.
- **Breaking change:** No.

---

## Category 07: Code Quality & Architecture

**GS Category:** 07 | **Assessment Category:** 8 | **Score:** 3.48 / 5.0 | **Target:** 4.0 | **Gap:** +0.52

### REQ-CQ-1: Layered Architecture

**Current State:**
- Clean src layout: `core/`, `transport/`, `common/`, `pubsub/`, `queues/`, `cq/`, `grpc/`
- Transport separate from business logic
- Message models separate from clients
- No transport interface — concrete classes only
- Assessment 8.1: 4.00

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Public API types don't reference gRPC/protobuf | PARTIAL | Pydantic models wrap protobuf (users don't see gRPC), but transport layer leaks slightly |
| 2 | Protocol layer handles error wrapping, retry, auth, observability | PARTIAL | Auth interceptors exist; no retry or observability layer |
| 3 | Transport layer only imports gRPC packages | PARTIAL | Transport imports gRPC; some gRPC awareness in core |
| 4 | Layers communicate via interfaces (not concrete types) | PARTIAL | No transport interface (assessment 8.5.3: 2/5); `BaseClient(ABC)` exists |
| 5 | Users import SDK without pulling gRPC internals | COMPLIANT | `__all__` defines clean public surface |
| 6 | Dependencies flow downward only | PARTIAL | Mostly clean; minor violations |

**REQ-CQ-1 Overall Status: PARTIAL**
**Priority: P2**

**Remediation:**
- **What:** Create `TransportProtocol` using `typing.Protocol` defining the transport interface (connect, close, send_event, subscribe_to_events, etc.). Refactor `AsyncTransport` and `SyncTransport` to implement the protocol. Create `_internal/middleware/` package for retry, auth, and OTel interceptors (Protocol layer). Ensure error wrapping flows upward: Transport wraps gRPC errors → Protocol classifies → Public API surfaces typed errors.
- **Complexity:** L (4 days) — significant refactoring to introduce interface layer
- **Dependencies:** REQ-ERR-3 (retry middleware), REQ-OBS-1 (OTel middleware)
- **Risk:** Without transport interface, unit testing requires mocking concrete classes (brittle). Future transport changes break clients.
- **Breaking change:** No — internal refactoring, public API unchanged.
- **Language-specific:** Use `typing.Protocol` (structural subtyping, no base class needed). Consider `abc.ABC` for transport if explicit registration preferred.

### REQ-CQ-2: Internal vs Public API Separation

**Current State:**
- `__all__` exports 42 symbols in `__init__.py`
- Internal modules accessible via import (no `_internal` prefix convention)
- Assessment 8.1.7: 4/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Internal details not importable/accessible | PARTIAL | `_` prefix not used on internal modules; users could `from kubemq.transport import SyncTransport` |
| 2 | Only intentional public API exported | COMPLIANT | `__all__` defines public surface |
| 3 | Moving internal code doesn't break users | PARTIAL | Users importing non-`__all__` symbols would break |

**REQ-CQ-2 Overall Status: PARTIAL**
**Priority: P2**

**Remediation:**
- **What:** Rename internal packages to use `_` prefix: `transport/` → `_transport/`, `common/` → `_common/`, `core/` → `_core/`. Add `py.typed` marker file per PEP 561. Update all internal imports. Document that `_` prefixed modules are private and may change without notice.
- **Complexity:** M (2 days) — rename + update imports
- **Dependencies:** None
- **Risk:** Low — prevents accidental dependence on internals.
- **Breaking change:** YES — users importing internal modules directly (`from kubemq.transport import ...`) will break. Since v4 is unreleased, this can be included in v4.0.0.
- **Language-specific:** Python convention: `_` prefix for private modules. `py.typed` for PEP 561 type checker support.

### REQ-CQ-3: Linting and Formatting

**Current State:**
- Ruff configured in `pyproject.toml` with line-length=100
- **204 ruff errors** (42 auto-fixable)
- mypy configured in strict mode
- Not enforced in CI
- Assessment 8.2.1: 2/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Linter config exists in repo root | COMPLIANT | ruff in pyproject.toml |
| 2 | CI runs linter and blocks merge | MISSING | Not in CI |
| 3 | Zero linter warnings in codebase | MISSING | 204 errors |
| 4 | Formatting enforced (CI or pre-commit) | PARTIAL | Configured but not in CI |
| 5 | Type checking at strictest level | PARTIAL | mypy strict configured, not in CI |
| 6 | Generated protobuf code excluded from linter/coverage | NOT_ASSESSED | Not verified |

**Body text requirements:**

| Requirement | Status | Detail |
|------------|--------|--------|
| ruff minimum rule sets: E, W, F, I | PARTIAL | Need to verify enabled sets |

**REQ-CQ-3 Overall Status: MISSING (2 MISSING)**
**Priority: P1**

**Remediation:**
- **What:** Fix all 204 ruff errors (42 auto-fixable via `ruff check --fix`; remaining ~162 manual). Add ruff to CI: `ruff check src/ tests/` and `ruff format --check src/ tests/`. Add mypy to CI: `mypy src/kubemq/`. Exclude `src/kubemq/grpc/` from linting. Ensure minimum rule sets E, W, F, I are enabled.
- **Complexity:** M (2-3 days) — 162 manual ruff errors at ~10min each = ~27 hours. Some (e.g., B010 `setattr` with constant) may indicate logic errors requiring context understanding, not just reformatting. CI setup is an additional 0.5 days.
- **Dependencies:** REQ-TEST-3 (CI pipeline)
- **Risk:** 204 lint errors indicate potential bugs (unused imports, shadowed variables, B010 violations).
- **Breaking change:** No — code quality improvements only.

### REQ-CQ-4: Minimal Dependencies

**Current State:**
- 6 runtime dependencies
- `grpcio-tools` arguably should be dev-only
- `pip-audit` shows no CVEs
- Assessment 11.1.4: 3/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Total direct deps ≤ 5 (excluding gRPC/protobuf/OTel) | PARTIAL | 6 runtime deps; removing grpcio-tools brings it to 5 |
| 2 | No logging framework dependency | COMPLIANT | Uses stdlib logging |
| 3 | No HTTP client dependency | COMPLIANT | gRPC only |
| 4 | No utility library deps | PARTIAL | pydantic is a utility dep; needs justification |
| 5 | Dependencies pinned to specific versions | PARTIAL | Uses `>=` ranges, not pinned |
| 6 | Dependency tree reviewed for vulnerabilities | PARTIAL | pip-audit clean, but not automated in CI |
| 7 | CI runs vulnerability scanning (pip-audit) | MISSING | Not in CI |

**REQ-CQ-4 Overall Status: PARTIAL (1 MISSING)**
**Priority: P1**

**Remediation:**
- **What:** Move `grpcio-tools` to dev dependencies. Add `pip-audit` to CI pipeline. Review pinning strategy (keep `>=` for flexibility but add upper bounds for major versions).
- **Pydantic justification:** Pydantic is justified for message model validation, serialization, and configuration management. It replaces manual validation boilerplate and provides type-safe models with IDE support. This is a standard Python pattern for data-heavy libraries and is consistent with the GS recommendation for idiomatic configuration.
- **Pydantic version note:** The SDK uses Pydantic v2 (confirmed by `ConfigDict` usage in config models and `model_copy()` patterns). All remediation recommendations in this report use Pydantic v2 syntax (`ConfigDict(frozen=True)`, `field_validator`, `model_copy()`). Ensure `pydantic>=2.0` minimum is enforced in `pyproject.toml`.
- **Complexity:** S (0.5 days)
- **Dependencies:** REQ-TEST-3 (CI pipeline for pip-audit)
- **Risk:** Supply chain attacks via unpinned dependencies.
- **Breaking change:** No — moving dev dep doesn't affect runtime users.

### REQ-CQ-5: Consistent Code Organization

**Current State:**
- Current: `core/`, `transport/`, `common/`, `pubsub/`, `queues/`, `cq/`, `grpc/`
- GS recommends: `client.py`, `events.py`, `queues.py`, `commands.py`, `queries.py`, `_internal/`
- Assessment 8.1: 4.00

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | Directory follows language conventions | PARTIAL | Current structure is Pythonic but doesn't match GS template |
| 2 | Each messaging pattern has own file/module | COMPLIANT | Separate directories |
| 3 | Shared types in common location | COMPLIANT | `core/`, `common/` |
| 4 | No circular dependencies | COMPLIANT | Clean hierarchy |
| 5 | File names consistent | COMPLIANT | All lowercase with underscores |
| 6 | `py.typed` marker file (PEP 561) | MISSING | No `py.typed` file in package root. Required for mypy/pyright/pytype to recognize the package as typed. GS Python directory template explicitly lists this file. Trivial to add (S, <5 minutes). |

**REQ-CQ-5 Overall Status: PARTIAL**
**Priority: P2**

**Remediation:**
- **What:** Current structure is functional and clean. GS template is a recommendation, not a hard requirement. Recommended: align naming closer to GS (e.g., `errors.py` instead of `core/exceptions.py`). Add `py.typed` marker per PEP 561. Consider restructuring to GS layout only if doing REQ-CQ-2 internal separation simultaneously to minimize churn.
- **Complexity:** L (4 days) if full restructure; S (0.5 days) if minimal alignment
- **Dependencies:** REQ-CQ-2 (combine with internal separation if restructuring)
- **Risk:** Low — current structure works well.
- **Breaking change:** YES if restructuring — all import paths change. Should be combined with v4.0.0 release.

### REQ-CQ-6: Code Review Standards

**Current State:**
- Zero TODO/FIXME/HACK comments (assessment 8.4.1: 5/5)
- Legacy dead code exists (`grpc/client.py`, deprecated `common/exceptions.py`)
- PR review practices not assessed

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | All PRs require at least one review | NOT_ASSESSED | Process not evaluated |
| 2 | PRs include tests for new functionality | NOT_ASSESSED | Process not evaluated |
| 3 | Breaking changes labeled in PR description | NOT_ASSESSED | Process not evaluated |
| 4 | No TODO/FIXME in released code | COMPLIANT | Zero found |
| 5 | Dead code removed, not commented out | PARTIAL | `grpc/client.py` legacy script, deprecated `common/exceptions.py` |

**REQ-CQ-6 Overall Status: PARTIAL (3 NOT_ASSESSED, 1 PARTIAL)**
**Priority: P2**

**Remediation:**
- **What:** Remove `grpc/client.py` legacy debug script. Remove deprecated classes from `common/exceptions.py`. Enable GitHub branch protection requiring 1 review. Add PR template with breaking change checkbox.
- **Complexity:** S (0.5 days)
- **Dependencies:** None
- **Risk:** Low — process improvement.
- **Breaking change:** No — removing unused files. Deprecated exception classes removed only after migration to new hierarchy.

### REQ-CQ-7: Secure Defaults

**Current State:**
- `ClientConfig.__repr__()` masks auth_token
- TLS disabled by default
- No warning for insecure connections
- Assessment 5.2.1: 1/5

**Gap Analysis:**

| # | Acceptance Criterion | Status | Detail |
|---|---------------------|--------|--------|
| 1 | No credential in log output at any level | PARTIAL | __repr__ masks; audit needed for all log paths |
| 2 | No credential in error messages or OTel spans | PARTIAL | Error messages don't include tokens; no OTel exists |
| 3 | TLS verification enabled by default | MISSING | TLS disabled by default entirely |
| 4 | Disabling TLS verification produces WARN log | MISSING | No warning |

**REQ-CQ-7 Overall Status: MISSING (2 MISSING)**
**Priority: P1**

**Remediation:**
- **What:** See REQ-AUTH-2 for TLS default changes. Add `logger.warn("Using insecure connection to {address}. Set tls=TLSConfig() to enable TLS.", address=self._address)` when TLS is disabled and address is not localhost. Add `logger.warn("TLS certificate verification is disabled. This is insecure and should only be used for testing.")` when `insecure_skip_verify=True`.
- **Complexity:** S (0.5 days)
- **Dependencies:** REQ-AUTH-2 (TLS defaults), REQ-OBS-5 (logger interface)
- **Risk:** Insecure defaults expose production deployments to MITM attacks.
- **Breaking change:** YES — TLS behavior change (see REQ-AUTH-2).

---

## Category 08: API Completeness

**Assessment Category:** 1 (API Completeness & Feature Parity)
**Current Score:** 4.07 / 5.0
**Target:** 4.0+
**Gap:** -0.07 (above target)

### REQ-API-1: Core Feature Coverage

**Overall Status: COMPLIANT**

#### Current State

Assessment sections 1.1–1.5 confirm comprehensive coverage of all core messaging patterns. Score: all sub-sections at 5.0 except Queues (4.67, due to missing Purge) and Operational Semantics (3.40, due to missing dedup/ordering docs).

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| **Client Management** | | |
| Ping (Core) | COMPLIANT | `BaseClient.ping()`, `NativeAsyncBaseClient.ping()` return `ServerInfo`. Assessment 1.5.1 = 2/2. |
| Channel List (Core) | COMPLIANT | 5 typed listing methods (`list_events_channels()`, etc.). Assessment 1.5.3 = 2/2. |
| Server Info (Extended) | COMPLIANT | `ServerInfo` with host, version, uptime. Assessment 1.5.2 = 2/2. |
| Channel Create (Extended) | COMPLIANT | `create_events_channel()`, etc. Assessment 1.5.4 = 2/2. |
| Channel Delete (Extended) | COMPLIANT | `delete_*_channel()` methods. Assessment 1.5.5 = 2/2. |
| **Events (Pub/Sub)** | | |
| Publish event | COMPLIANT | `send_events_message()` (sync), `send_event()` (async). Assessment 1.1.1 = 2/2. |
| Subscribe with callback/handler | COMPLIANT | `subscribe_to_events()` with callback (sync) and AsyncIterator (async). Assessment 1.1.2 = 2/2. |
| Wildcard/pattern subscribe | COMPLIANT | Channel name passed to server for wildcard resolution. Assessment 1.1.4 = 2/2. |
| Group subscription | COMPLIANT | `EventsSubscription.group` field. Assessment 1.1.7 = 2/2. |
| Unsubscribe | COMPLIANT | `CancellationToken.cancel()` / `AsyncCancellationToken.cancel()`. Assessment 1.1.6 = 2/2. |
| **Events Store** | | |
| Publish to store | COMPLIANT | `send_events_store_message()` (sync), `send_event_store()` (async). Assessment 1.2.1 = 2/2. |
| Subscribe from beginning | COMPLIANT | `EventsStoreType.StartFromFirst` (value 2). Assessment 1.2.4 = 2/2. |
| Subscribe from sequence | COMPLIANT | `EventsStoreType.StartAtSequence`. Assessment 1.2.6 = 2/2. |
| Subscribe from timestamp | COMPLIANT | `EventsStoreType.StartAtTime` with `datetime`. Assessment 1.2.7 = 2/2. |
| Subscribe from time delta | COMPLIANT | `EventsStoreType.StartAtTimeDelta`. Assessment 1.2.8 = 2/2. |
| Subscribe from last | COMPLIANT | `EventsStoreType.StartFromLast`. Assessment 1.2.5 = 2/2. |
| Subscribe new only | COMPLIANT | `EventsStoreType.StartNewOnly`. Assessment 1.2.3 = 2/2. |
| Unsubscribe | COMPLIANT | Via `CancellationToken`. |
| **Queues (Stream-based)** | | |
| Queue stream upstream | COMPLIANT | `QueuesUpstream` streaming RPC. Assessment 1.3.1 = 2/2. |
| Queue stream downstream | COMPLIANT | `QueuesDownstream` bidirectional streaming. Assessment 1.3.3 = 2/2, 1.3.6 = 2/2. |
| Visibility timeout | COMPLIANT | `visibility_seconds` parameter. Assessment 1.3.4 = 2/2. |
| Ack message | COMPLIANT | `ack_all_messages()`, individual ack via downstream. Assessment 1.3.5 = 2/2. |
| Reject message | COMPLIANT | `reject_all_messages()`. Assessment 1.3.5 = 2/2. |
| Requeue message | COMPLIANT | `re_queue_all_messages()`. Assessment 1.3.5 = 2/2. |
| DLQ | COMPLIANT | `attempts_before_dead_letter_queue`, `dead_letter_queue` fields. Assessment 1.3.9 = 2/2. |
| Delayed messages | COMPLIANT | `delay_in_seconds` with validation. Assessment 1.3.7 = 2/2. |
| Message expiration | COMPLIANT | `expiration_in_seconds`. Assessment 1.3.8 = 2/2. |
| **Queues (Simple)** | | |
| Send single (non-stream) | COMPLIANT | `send_queues_message()`. Assessment 1.3.1 = 2/2. |
| Send batch | COMPLIANT | `send_queue_messages_batch()` via `SendQueueMessagesBatch` unary RPC. Assessment 1.3.2 = 2/2. |
| Receive (single pull) | COMPLIANT | `pull()` using `ReceiveQueueMessages` unary. Assessment 1.3.3 = 2/2. |
| Peek | COMPLIANT | `waiting()` with `IsPeak=True` (sync), `peek_queue_messages()` (async). Assessment 1.3.11 = 2/2. |
| **RPC — Commands** | | |
| Send command | COMPLIANT | `send_command_request()` (sync), `send_command()` (async). Assessment 1.4.1 = 2/2. |
| Subscribe to commands | COMPLIANT | `subscribe_to_commands()`. Assessment 1.4.2 = 2/2. |
| Subscribe with group | COMPLIANT | `CommandsSubscription.group`. Assessment 1.4.10 = 2/2. |
| Send response | COMPLIANT | `send_response_message()` (sync), `send_response()` (async). Assessment 1.4.3 = 2/2. |
| **RPC — Queries** | | |
| Send query | COMPLIANT | `send_query_request()` (sync), `send_query()` (async). Assessment 1.4.5 = 2/2. |
| Subscribe to queries | COMPLIANT | `subscribe_to_queries()`. Assessment 1.4.6 = 2/2. |
| Subscribe with group | COMPLIANT | `QueriesSubscription.group`. Assessment 1.4.10 = 2/2. |
| Send response | COMPLIANT | `QueryResponseMessage`. Assessment 1.4.7 = 2/2. |
| Cache-enabled queries | COMPLIANT | `cache_key`, `cache_ttl_int_seconds`, `CacheHit` decode. Assessment 1.4.11 = 2/2. |

#### Remediation

No remediation needed. All core features are implemented.

---

### REQ-API-2: Feature Matrix Document

**Overall Status: MISSING**

#### Current State

No feature matrix document exists in the `clients/` directory. The `sdk-golden-standard.md` has an empty cross-SDK parity matrix (all cells blank). Assessment did not evaluate this requirement — the feature matrix concept was defined in the Golden Standard after the assessment was conducted.

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Feature matrix document exists and is current | MISSING | No `feature-matrix.md` or equivalent found in repo |
| Feature matrix reviewed with each release | MISSING | No process exists |
| Features categorized as Core / Extended | MISSING | No categorization document |
| Gaps documented with rationale | MISSING | No gap documentation |

#### Remediation

- **What:** Create `clients/feature-matrix.md` with a table listing all features from REQ-API-1 against all 5 SDKs (Go, Java, C#, Python, JS/TS). Mark each as ✅/⚠️/❌/N/A. Add a release review checklist to CONTRIBUTING.md or release process docs.
- **Complexity:** S (< 1 day — document creation only)
- **Dependencies:** None; can be done immediately
- **Risk:** Low; purely documentation
- **Breaking change:** No
- **Python-specific notes:** None; this is a cross-SDK artifact

---

### REQ-API-3: No Silent Feature Gaps

**Overall Status: PARTIAL**

#### Current State

Assessment 1.3.12 identifies one missing feature (Purge queue, score 0/2). However, Purge is NOT listed as a Core or Extended feature in REQ-API-1, so its absence is not a standard violation. The SDK has no `ErrNotImplemented` pattern for future missing features. No feature gap tracking issues exist.

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Missing Core features documented in feature matrix | PARTIAL | No feature matrix exists, but all Core features from REQ-API-1 are implemented (so no Core gaps to document currently) |
| Returns `ErrNotImplemented` error | NOT_ASSESSED | No evidence of this error type in the exception hierarchy. `core/exceptions.py` has 8 exception classes, none named `NotImplemented`. |
| Tracking issue for implementation | NOT_ASSESSED | Assessment did not check for GitHub issues tracking feature gaps |

#### Remediation

- **What:** (1) Add `KubeMQNotImplementedError` to the exception hierarchy in `core/exceptions.py`. (2) Use it as the pattern for any future stubs (e.g., if a method is defined in the interface but not yet implemented). (3) If Purge queue is intended to be supported, add a tracking issue.
- **Complexity:** S (< 1 day)
- **Dependencies:** None
- **Risk:** Low
- **Breaking change:** No — additive only (new exception class)
- **Python-specific notes:** Python has a built-in `NotImplementedError`. The SDK could use that directly or wrap it in a `KubeMQNotImplementedError` subclass for consistency with the exception hierarchy.

---

## Category 09: API Design & Developer Experience

**Assessment Category:** 2 (API Design & Developer Experience)
**Current Score:** 3.63 / 5.0
**Target:** 4.0+
**Gap:** +0.37

### REQ-DX-1: Language-Idiomatic Configuration

**Overall Status: COMPLIANT**

#### Current State

Assessment 2.1.2 = 5/5: `ClientConfig` dataclass with defaults, `from_env()`, `from_file()` (TOML), `from_dotenv()`. `TLSConfig` and `KeepAliveConfig` frozen dataclasses. Clients accept both flat kwargs and `config=ClientConfig(...)`. Assessment 5.2.4 confirms `ClientConfig.__post_init__` validates address.

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Configuration follows language-idiomatic pattern | COMPLIANT | Python kwargs with defaults. `ClientConfig` dataclass. Assessment 2.1.2 = 5/5. `core/config.py`. |
| Required parameters enforced at construction time | COMPLIANT | `ClientConfig.__post_init__` validates address. Assessment 5.2.4. |
| Optional parameters have documented default values | COMPLIANT | Defaults visible in dataclass definition. Assessment 2.2.2 = 5/5: auto-generated `client_id`, 100MB message size, 30s timeout. |
| Invalid configuration rejected at construction | COMPLIANT | Pydantic validators and `__post_init__` validation. Assessment 2.1.3 = 4/5. |

#### Remediation

No remediation needed.

---

### REQ-DX-2: Minimal Code Happy Path

**Overall Status: PARTIAL**

#### Current State

Assessment 2.2.1 = 4/5: basic publish ~8 lines including imports. Assessment Developer Journey shows functional but with friction. No default address (users must always specify `address=`).

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Events publish ≤3 lines | COMPLIANT | `client.send_events_message(EventMessage(channel="ch", body=b"msg"))` — 1 line after client creation. Assessment 2.2.1 = 4/5. |
| Queue send ≤3 lines | COMPLIANT | `client.send_queues_message(QueueMessage(channel="q", body=b"msg"))` — 1 line after client creation. |
| RPC command/query ≤3 lines | COMPLIANT | `client.send_command_request(CommandMessage(channel="cmd", body=b"x", timeout_in_seconds=10))` — 1 line. |
| Subscribe/receive with ack ≤10 lines | COMPLIANT | Assessment Developer Journey step 4: ~5-6 lines for subscription setup. Assessment 2.2.1 = 4/5. |
| Defaults (localhost:50000, no auth) work for local dev | PARTIAL | Auth defaults to none (correct). Address has no default — user must always specify. Assessment 3.4.1: "Default address not set to localhost:50000." |

#### Remediation

- **What:** Add `address: str = "localhost:50000"` as default in `ClientConfig` (in `core/config.py`). This enables `PubSubClient()` with zero arguments for local development.
- **Complexity:** S (< 1 day)
- **Dependencies:** None
- **Risk:** Low — additive default, does not change existing behavior for users who specify address
- **Breaking change:** No — adding a default to a previously required parameter is backward-compatible
- **Python-specific notes:** Python dataclass fields with defaults must come after fields without defaults. Reorder if needed. Verify `__post_init__` validation still works with the default value.

---

### REQ-DX-3: Consistent Verbs Across SDKs

**Overall Status: PARTIAL**

#### Current State

Assessment 2.4.1 = 3/5 and 2.2.4 = 3/5: significant naming inconsistencies between sync and async clients, and deviation from GS verb vocabulary.

| GS Verb | GS Example | Python Sync | Python Async | Issue |
|---|---|---|---|---|
| Publish | `PublishEvent` | `send_events_message` | `send_event` | Uses "send" instead of "publish"; sync/async names differ |
| Subscribe | `SubscribeEvents` | `subscribe_to_events` | `subscribe_to_events` | Consistent but adds "to" |
| Send (queue) | `SendQueueMessage` | `send_queues_message` | `send_queue_message` | Plural inconsistency (queues vs queue) |
| Receive | `ReceiveQueueMessages` | `receive_queues_messages` | `receive_queue_messages` | Plural inconsistency |
| Send (command) | `SendCommand` | `send_command_request` | `send_command` | Sync adds "_request" suffix |
| Send (query) | `SendQuery` | `send_query_request` | `send_query` | Sync adds "_request" suffix |

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| All SDKs use same verbs (casing adapted) | PARTIAL | Events use "send" instead of "publish". Sync/async naming diverges. Assessment 2.4.1 = 3/5. |
| Method names predictable across SDKs | PARTIAL | Sync has `_request` suffixes absent in async. Plural inconsistencies (`queues` vs `queue`). Assessment 2.4.3 = 3/5. |

#### Remediation

- **What:** Normalize method names to match GS verb vocabulary. Events: `publish_event()` / `publish_event_store()`. Queues: `send_queue_message()`. Sync/async should use identical names. Add `@deprecated` aliases for old names.
- **Complexity:** L (3-5 days) — requires updating all clients, tests, examples, and adding deprecation aliases
- **Dependencies:** REQ-API-2 (feature matrix for verb coordination across SDKs)
- **Risk:** Medium — **BREAKING CHANGE** if old method names are removed immediately
- **Breaking change:** **YES.** Method renames are breaking. Mitigation: add new names as primary, add `@deprecated` aliases for old names, remove aliases in next major version (per REQ-COMPAT-2: 6 months / 2 minor versions notice).
- **Backward compatibility:** Keep old method names as deprecated aliases. Use `warnings.warn("Use publish_event() instead", DeprecationWarning, stacklevel=2)`. For Python 3.13+, consider `warnings.deprecated` decorator (built-in); for 3.9-3.12, use `typing_extensions.deprecated` (backport) which provides IDE integration (strikethrough in autocomplete).
- **Python-specific notes:** Python convention is `snake_case`. Use `publish_event`, `send_queue_message`, `send_command`, `send_query` (matching GS verbs). The `subscribe_to_events` pattern is Pythonic and acceptable even if other SDKs use `subscribe_events`.

---

### REQ-DX-4: Fail-Fast Validation

**Overall Status: PARTIAL**

#### Current State

Assessment 5.2.4 = 3/5: Pydantic validates message fields (channel non-empty, delay limits). `ClientConfig.__post_init__` validates address. No channel name injection-safe validation. No message body size pre-check against gRPC max.

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Invalid inputs produce clear error messages at construction/call time | PARTIAL | Pydantic validators catch most invalid inputs. Missing: channel name character validation, message body size pre-check, TLS cert path existence check. Assessment 5.2.4 = 3/5. |
| Validation errors classified as non-retryable | NOT_ASSESSED | `KubeMQValidationError` exists but no `is_retryable` property on any exception. Depends on Tier 1 REQ-ERR-* work. |
| Validation happens before any network call | COMPLIANT | Pydantic validation occurs at model construction (before `encode()` and RPC call). Assessment confirms validators run eagerly. |

#### Remediation

- **What:** (1) Add channel name validation (non-empty, valid characters — alphanumeric, `.`, `-`, `_`, `*`, `>`). (2) Add message body size pre-check against `max_send_size` config before RPC call. (3) Add TLS certificate file existence validation at `TLSConfig` construction. (4) After Tier 1 error work: mark `KubeMQValidationError.is_retryable = False`.
- **Complexity:** M (1-3 days)
- **Dependencies:** `is_retryable` classification depends on Tier 1 REQ-ERR-* (error classification)
- **Risk:** Low — validation additions are non-breaking
- **Breaking change:** No — adding validation to previously unvalidated inputs may cause `KubeMQValidationError` on inputs that previously passed silently. This is technically behavior-changing but is a correctness improvement. Document in CHANGELOG.
- **Backward compatibility:** Users passing invalid channel names (e.g., empty strings) that were previously sent to the server (and failed there) will now get client-side errors. This is safer behavior.
- **Python-specific notes:** Use Pydantic `field_validator` decorators. For TLS cert paths, use `pathlib.Path.exists()` check.

---

### REQ-DX-5: Message Builder/Factory

**Overall Status: PARTIAL**

#### Current State

Assessment 2.3.1 = 4/5: Pydantic models for all messages with type hints and validators. Assessment 2.2.3 = 5/5: optional fields have defaults. Message construction uses Python kwargs pattern (idiomatic). However, message immutability is incomplete.

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Message types have builder/factory methods | COMPLIANT | Pydantic models with kwargs constructor. `EventMessage(channel="ch", body=b"msg")`. Python-idiomatic (no separate builder class needed). |
| Required fields enforced at build time | COMPLIANT | Pydantic validators enforce required fields. Assessment confirms channel is required, timeout required on commands/queries. |
| Optional fields have sensible defaults | COMPLIANT | Empty metadata, no expiration, auto-generated ID. Assessment 2.2.2 = 5/5. |
| Messages immutable after construction | PARTIAL | `TLSConfig` and `KeepAliveConfig` are frozen dataclasses. `EventMessage`, `QueueMessage`, `CommandMessage`, `QueryMessage` — assessment 13.2.4 mentions "Frozen Pydantic models" but refers to config models. Message models are likely mutable Pydantic BaseModel instances. |

#### Remediation

- **What:** Add `model_config = ConfigDict(frozen=True)` to all outbound message models (`EventMessage`, `EventStoreMessage`, `QueueMessage`, `CommandMessage`, `QueryMessage`). Received/response messages can remain mutable if needed.
- **Complexity:** S (< 1 day)
- **Dependencies:** None
- **Risk:** Medium — **potentially breaking** if users modify message objects after construction
- **Breaking change:** **YES** — users who do `msg.body = new_body` after construction will get `ValidationError`. Mitigation: use Pydantic `model_copy(update={...})` for creating modified copies. Document the change and migration path.
- **Backward compatibility:** Add `model_copy()` usage to examples and migration guide. Monitor for user complaints.
- **Python-specific notes:** Pydantic v2 `ConfigDict(frozen=True)` makes instances hashable and immutable. `model_copy()` is the idiomatic way to create modified copies. This is standard Python practice for value objects.

---

## Category 10: Concurrency & Thread Safety

**Assessment Category:** 6 (Concurrency & Thread Safety)
**Current Score:** 3.75 / 5.0
**Target:** 4.0+
**Gap:** +0.25

### REQ-CONC-1: Thread Safety Documentation

**Overall Status: MISSING**

#### Current State

Assessment 6.1.4 = 1/5: "No thread safety documentation anywhere. No docstring mentions thread safety." Assessment 6.1.1–6.1.3 confirm the SDK IS thread-safe (uses `threading.RLock`, `threading.Lock`, `queue.Queue`, `asyncio.Lock`), but this is not documented.

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Client type documented as thread-safe | MISSING | Assessment 6.1.4 = 1/5. `BaseClient` uses `threading.RLock` (6.1.1 = 4/5) but no docstring states this. |
| Doc comments on each public type state concurrency guarantee | MISSING | No public type has a concurrency guarantee in its docstring. |
| Non-thread-safe types document the restriction | MISSING | No documentation of which types are not safe to share. |

#### Remediation

- **What:** Add concurrency guarantees to docstrings of all public types following the GS table:
  - `BaseClient` / `NativeAsyncBaseClient`: "Thread-safe. Share one instance per application."
  - `EventsSubscription`, `EventsStoreSubscription`, etc.: "Thread-safe. Can be cancelled from any thread."
  - `EventMessage`, `QueueMessage`, `CommandMessage`, `QueryMessage`: "Not thread-safe. Create per-send, do not share."
  - `EventSendResult`, `QueueSendResult`, `QueuesPollResponse`: "Safe to read from multiple threads. Do not modify."
- **Complexity:** S (< 1 day)
- **Dependencies:** None
- **Risk:** Low — documentation only
- **Breaking change:** No
- **Python-specific notes:** Use standard docstring convention. Add a "Thread Safety" section to each class docstring. Consider also adding a "Concurrency" section to README.

---

### REQ-CONC-2: Cancellation & Timeout Support

**Overall Status: PARTIAL**

#### Current State

Assessment 4.4.2 = 4/5: `CancellationToken` (sync) and `AsyncCancellationToken` (async) with parent-child cascading. Assessment 3.2.9 = 4/5: `asyncio.wait_for()` wraps all async operations. For Python, the GS requires `timeout` parameter + `asyncio.wait_for`.

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| All blocking operations accept language-appropriate cancellation mechanism | PARTIAL | Async ops use `asyncio.wait_for()` with `default_timeout_seconds`. Not all async methods expose `timeout` as an explicit kwarg — it's applied internally. Commands/queries accept per-request `timeout_seconds`. Subscriptions use `CancellationToken`. |
| Cancellation propagated to underlying gRPC call | COMPLIANT | `AsyncTransport` calls `call.cancel()` on gRPC stream (`async_transport.py:388`). Assessment 4.4.2. |
| Cancelled operations produce clear cancellation error | NOT_ASSESSED | Assessment doesn't verify whether cancellation produces a distinct error (e.g., `KubeMQCancellationError`) vs generic timeout. `DEADLINE_EXCEEDED` maps to `KubeMQTimeoutError`. CancellationToken cancellation is handled differently (loop exit). |
| Long-lived subscriptions honor cancellation | COMPLIANT | `CancellationToken.cancel()` terminates subscription loops. Assessment 1.1.6 = 2/2. |

#### Remediation

- **What:** (1) Add explicit `timeout: float | None = None` kwarg to all public async methods (overrides `default_timeout_seconds` when specified). (2) Distinguish cancellation from timeout: add `KubeMQCancellationError` to exception hierarchy, raise it when `CancellationToken.cancel()` is called (vs `KubeMQTimeoutError` for deadline expiry).
- **Complexity:** M (1-3 days)
- **Dependencies:** Tier 1 REQ-ERR-* (error hierarchy improvements)
- **Risk:** Low — additive changes
- **Breaking change:** No — adding optional `timeout` kwarg is backward-compatible. New exception type is additive.
- **Python-specific notes:** Python convention is `timeout: float | None` as kwarg. Follow `asyncio.wait_for` semantics: `None` means no timeout. Use `asyncio.CancelledError` internally and wrap it in `KubeMQCancellationError` for user-facing API.

---

### REQ-CONC-3: Subscription Callback Behavior

**Overall Status: MISSING**

#### Current State

Assessment 6.1.4 = 1/5 (no documentation). Sync subscriptions use callback in daemon thread. Async subscriptions use `AsyncIterator` pattern (sequential by nature). No documentation of callback concurrency behavior.

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Document whether callbacks may fire concurrently | MISSING | No documentation. Assessment 6.1.4 = 1/5. |
| Default callback concurrency is 1 (sequential) | NOT_ASSESSED | Sync: each subscription callback runs in its own daemon thread — multiple subscriptions = concurrent callbacks. Async: `AsyncIterator` is inherently sequential. The default behavior differs between sync/async and is not documented. |
| Mechanism to control callback concurrency | NOT_ASSESSED | No `max_concurrent_callbacks` option or equivalent. Sync has no concurrency control beyond thread-per-subscription. |
| Callbacks don't block internal event loop | NOT_ASSESSED | Sync: callbacks in daemon threads — separate from connection reader. Async: user controls iteration speed via `async for`. Assessment doesn't evaluate whether slow async iteration blocks the gRPC reader. |
| Long-running callback guidance documented | MISSING | No guidance in docstrings, README, or examples. |

#### Remediation

- **What:** (1) Document callback behavior: sync subscriptions fire callbacks in dedicated daemon threads (one per subscription, concurrent across subscriptions); async uses `AsyncIterator` (sequential, user-controlled). (2) Add guidance for long-running callbacks: "Use `asyncio.create_task()` for async, `concurrent.futures.ThreadPoolExecutor` for sync." (3) Consider adding `max_concurrent_callbacks` parameter to subscription options for sync callbacks (not blocking for 4.0).
- **Complexity:** M (1-3 days) for documentation + S for optional concurrency control
- **Dependencies:** None
- **Risk:** Low for documentation; medium for concurrency control (new feature)
- **Breaking change:** No — documentation and optional parameter additions
- **Python-specific notes:** Python's GIL limits true parallel execution of callbacks, but I/O-bound callbacks (which messaging callbacks typically are) benefit from threading. Document GIL implications. For async, the `AsyncIterator` pattern inherently gives users control over concurrency via `asyncio.gather()` or `asyncio.create_task()`.

**Future Enhancement (from GS body text):** `maxConcurrentCallbacks` option for sync subscriptions. Not required for 4.0 but should be designed-ahead for.

---

### REQ-CONC-4: Async-First Where Idiomatic

**Overall Status: COMPLIANT**

#### Current State

Assessment 6.2.P1 = 5/5 and 6.2.P2 = 5/5: full native asyncio via `AsyncClient` classes. Both sync and async variants available. Three tiers: sync, legacy thread-wrapped (deprecated), native async.

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Primary API style matches language convention | COMPLIANT | Python GS: "Both sync and async. Sync as primary API, async as opt-in." SDK provides both. Assessment 6.2.P2 = 5/5. |
| Async APIs don't block the calling thread/event loop | COMPLIANT | Native `grpc.aio` with `async/await`. No `run_until_complete()` calls in async path (except sync transport's `close()` fallback). Assessment 6.2.P1 = 5/5. |

#### Remediation

No remediation needed.

---

### REQ-CONC-5: Shutdown-Callback Safety

**Overall Status: MISSING**

#### Current State

Assessment 3.2.2 = 4/5: `AsyncTransport.close()` cancels active streams, idempotent with `_closing` flag. But assessment doesn't evaluate whether `Close()` waits for in-flight callbacks or whether post-close operations return appropriate errors.

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| `Close()` waits for in-flight callbacks with configurable timeout (default 30s) | NOT_ASSESSED | Assessment 3.2.2 mentions stream cancellation but not callback waiting. `AsyncTransport.close()` cancels active streams (`async_transport.py:217-236`) but likely does not wait for user callbacks to complete. This criterion was likely added post-assessment. |
| Operations after `Close()` return `ErrClientClosed` | NOT_ASSESSED | Assessment doesn't check this. `_closing` flag exists but it's unclear if post-close operations raise a specific error. No `KubeMQClientClosedError` in exception hierarchy. |
| `Close()` is idempotent | COMPLIANT | `_closing` flag prevents double-close. Assessment 3.2.2 = 4/5. |

#### Remediation

- **What:** (1) Implement callback drain in `Close()`: track in-flight callbacks, wait up to configurable `close_timeout` (default 30s) for them to complete before cancelling. (2) Add `KubeMQClientClosedError` to exception hierarchy. (3) Check `_closing` flag at the start of all public methods and raise `KubeMQClientClosedError` if set.
- **Complexity:** M (1-3 days)
- **Dependencies:** Tier 1 REQ-CONN-4 (graceful drain) — the drain concept must be designed consistently with connection-level drain
- **Risk:** Medium — affects shutdown behavior, needs testing with long-running callbacks
- **Breaking change:** Potentially — if users currently catch generic exceptions on post-close operations, they'll now get `KubeMQClientClosedError`. This is a correctness improvement.
- **Backward compatibility:** `KubeMQClientClosedError` should extend `KubeMQError` so existing `except KubeMQError` handlers continue to work.
- **Python-specific notes:** Use `asyncio.wait()` with timeout for async callback tracking. Use `asyncio.shield()` to protect in-flight callbacks from cancellation during the drain period — without shielding, `asyncio.wait()` with timeout may cancel tasks prematurely. For sync, use `threading.Event` with timeout. The 30s default should be a `close_timeout` parameter on `ClientConfig`.

---

## Category 11: Packaging & Distribution

**Assessment Category:** 11 (Packaging & Distribution)
**Current Score:** 2.90 / 5.0
**Target:** 4.0+
**Gap:** +1.10

### REQ-PKG-1: Package Manager Publishing

**Overall Status: PARTIAL**

#### Current State

Assessment 11.1.1 = 4/5: published to PyPI as `kubemq` with 30 releases. v4.0.0 not yet published. Assessment 11.1.2 = 3/5: metadata incomplete (missing keywords, incomplete classifiers). Assessment 10.4.5 = 1/5: no CHANGELOG.

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Package published and installable via `pip install` | PARTIAL | `pip install kubemq` installs v3.6.0. v4 not yet on PyPI. Assessment 11.1.1 = 4/5. |
| Package includes README, LICENSE, and CHANGELOG | PARTIAL | README exists (but has errors like "Python 3.2+"). LICENSE in `pyproject.toml`. No CHANGELOG.md. Assessment 10.4.5 = 1/5. |
| Package metadata complete | PARTIAL | Missing keywords, incomplete classifiers in `pyproject.toml`. Assessment 11.1.2 = 3/5. |

#### Remediation

- **What:** (1) Publish v4.0.0 to PyPI (after Tier 1 critical fixes). (2) Create `CHANGELOG.md` with v4.0.0 entries. (3) Add keywords and complete classifiers to `pyproject.toml`: `keywords = ["kubemq", "messaging", "grpc", "pubsub", "queue"]`, add `Programming Language :: Python :: 3.9` through `3.13` classifiers.
- **Complexity:** S (< 1 day for metadata; publishing is part of release process)
- **Dependencies:** v4 publishing depends on Tier 1 critical fixes (error handling, auth, testing)
- **Risk:** Low for metadata. v4 publishing has higher risk if critical gaps remain.
- **Breaking change:** No (metadata/documentation changes)
- **Python-specific notes:** Use `hatchling` dynamic version reading from `__init__.py` or `pyproject.toml` to eliminate version duplication.

---

### REQ-PKG-2: Semantic Versioning

**Overall Status: PARTIAL**

#### Current State

Assessment 11.2.1 = 4/5: follows semver for major versions. Assessment 11.2.5 = 2/5: version inconsistency between `pyproject.toml` ("4.0.0-dev") and `__init__.py` ("4.0.0").

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Version numbers follow SemVer | COMPLIANT | Major version bumps for breaking changes (1.x → 2.x → 3.x → 4.x). Assessment 11.2.1 = 4/5. |
| Breaking changes only in MAJOR releases | COMPLIANT | v4 breaks v3 API with major version bump. Assessment 12.1.4 = 3/5. |
| Pre-release versions clearly labeled | PARTIAL | `pyproject.toml` says `4.0.0-dev` but `__init__.py` says `4.0.0`. Inconsistent. Assessment 11.2.5 = 2/5. |
| Version embedded in package (queryable at runtime) | COMPLIANT | `__init__.py` contains `__version__`. `kubemq.__version__` works at runtime. |

#### Remediation

- **What:** (1) Establish single source of truth for version. Use `hatchling` version plugin to read from `src/kubemq/__init__.py` or use `hatch-vcs` to derive from git tags. (2) Ensure pre-release versions use proper SemVer format: `4.0.0-dev.1` or `4.0.0a1` (Python PEP 440 compatible).
- **Complexity:** S (< 1 day)
- **Dependencies:** None
- **Risk:** Low
- **Breaking change:** No
- **Python-specific notes:** **Important: Python uses PEP 440 version format, not SemVer syntax.** `4.0.0-dev` is invalid PEP 440 and will be rejected by PyPI; use `4.0.0.dev1`. `4.0.0-alpha.1` → `4.0.0a1`. `4.0.0-rc.1` → `4.0.0rc1`. The current `pyproject.toml` value `4.0.0-dev` must be corrected. `pyproject.toml` and `__init__.py` should use PEP 440 format. The `hatchling` build backend supports `dynamic = ["version"]` with `[tool.hatch.version]` source configuration.

---

### REQ-PKG-3: Automated Release Pipeline

**Overall Status: MISSING**

#### Current State

Assessment 9.3.1 = 2/5: `.github/workflows/deploy.yml` handles deployment on tag push. Assessment 9.3.2 = 1/5: tests do NOT run on PR or during release. No GitHub Releases with changelogs. The pipeline is: tag → build → publish to PyPI (no tests, no CHANGELOG validation, no GitHub Release).

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Release triggered by git tag or merge to release branch | COMPLIANT | `deploy.yml` triggers on tag push. Assessment 9.3.1 = 2/5. |
| Publishing requires no manual steps after tagging | PARTIAL | Build and PyPI publish are automated. But tests are NOT run — a human must manually verify tests pass before tagging. Assessment 9.3.2 = 1/5. |
| GitHub Release created automatically with changelog | MISSING | No GitHub Release descriptions. No CHANGELOG. Assessment 11.2.3 = 1/5. |
| Failed releases don't publish partial artifacts | NOT_ASSESSED | Assessment didn't evaluate this. Without tests in the pipeline, "failed" is undefined. |

#### Remediation

- **What:** Rewrite `.github/workflows/deploy.yml` to implement the full GS pipeline:
  1. Trigger on tag push (`v*`)
  2. Run full test suite (unit + lint)
  3. Validate CHANGELOG entry exists for this version
  4. Build package (`hatchling`)
  5. Publish to PyPI using Trusted Publishing (OIDC, no API keys)
  6. Create GitHub Release with CHANGELOG body
  7. Fail the entire workflow if any step fails (prevent partial publish)
- **Complexity:** M (1-3 days)
- **Dependencies:** Tier 1 REQ-TEST-* (CI pipeline must exist with test infrastructure). REQ-PKG-4 (CHANGELOG must exist to validate).
- **Risk:** Medium — changes to deployment pipeline must be carefully tested
- **Breaking change:** No — internal CI/CD improvement
- **Python-specific notes:** Use PyPI Trusted Publishing (OIDC) per GS specification. See [PyPI Trusted Publishing docs](https://docs.pypi.org/trusted-publishers/). Use `pypa/gh-action-pypi-publish@release/v1` GitHub Action. For GitHub Release creation, use `softprops/action-gh-release` or `gh release create`.

---

### REQ-PKG-4: Conventional Commits (Recommended)

**Overall Status: MISSING**

#### Current State

Assessment does not evaluate commit conventions. No CONTRIBUTING.md exists. No CHANGELOG.md exists. No commit linting configured.

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Commit format documented in CONTRIBUTING.md (if adopted) | MISSING | No CONTRIBUTING.md. Assessment 11.3.4 = 1/5. |
| Commit linting configured (if adopted) | MISSING | No commitlint or equivalent. |
| CHANGELOG maintained (manually or generated) | MISSING | No CHANGELOG.md. Assessment 10.4.5 = 1/5. |

*Note: Conventional Commits is RECOMMENDED, not required. The hard requirement is CHANGELOG maintenance (criterion 3).*

#### Remediation

- **What:** (1) Create `CHANGELOG.md` with retroactive entries for v3.x and v4.0.0-dev changes. Follow [Keep a Changelog](https://keepachangelog.com/) format. (2) Optionally adopt Conventional Commits and document in CONTRIBUTING.md. (3) Optionally add `commitlint` via pre-commit hooks.
- **Complexity:** 0 (deduplicated — same deliverable as REQ-DOC-6, Tier 1; effort counted there)
- **Dependencies:** None
- **Risk:** Low
- **Breaking change:** No
- **Python-specific notes:** Use `pre-commit` framework (already a Python ecosystem standard) for commit linting if adopted. Add `.pre-commit-config.yaml` with `commitlint` hook.

---

## Category 12: Compatibility, Lifecycle & Supply Chain

**Assessment Category:** 12 (Compatibility, Lifecycle & Supply Chain)
**Current Score:** 1.90 / 5.0
**Target:** 4.0+
**Gap:** +2.10

### REQ-COMPAT-1: Client-Server Compatibility Matrix

**Overall Status: MISSING**

#### Current State

Assessment 12.1.1 = 1/5: "No documentation of compatible KubeMQ server versions." No version checking on connection.

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Compatibility matrix maintained in SDK repo | MISSING | No compatibility documentation exists anywhere. Assessment 12.1.1 = 1/5. |
| Matrix updated when SDK or server versions add features | MISSING | No matrix exists to update. |
| SDK validates server version on connection and warns if incompatible | NOT_ASSESSED | `ping()` returns `ServerInfo` with `version` field, but assessment doesn't evaluate whether the SDK compares this against a known compatibility range. Likely not implemented. |
| SDK logs warning if server version outside tested range | NOT_ASSESSED | No evidence of version comparison logic. Likely MISSING. |

#### Remediation

- **What:** (1) Create `COMPATIBILITY.md` or a section in README with server version matrix. (2) After `ping()` on connection, compare `ServerInfo.version` against a `MIN_SERVER_VERSION` / `MAX_TESTED_SERVER_VERSION` constant. Log a warning if outside range but do NOT fail. (3) Update matrix with each SDK release.
- **Complexity:** M (1-3 days) — matrix creation is quick, but server version check logic needs testing against multiple server versions
- **Dependencies:** None
- **Risk:** Low — warning-only, does not affect connection behavior
- **Breaking change:** No — warning log only
- **Python-specific notes:** Use `packaging.version.Version` for SemVer comparison (already a transitive dependency via `setuptools`/`pip`). Use `logging.warning()` for the compatibility warning.

---

### REQ-COMPAT-2: Deprecation Policy

**Overall Status: MISSING**

#### Current State

Assessment 12.1.3 = 2/5: `@deprecated_async_method` custom decorator marks legacy methods. No `warnings.warn(DeprecationWarning)` usage (GS-specified Python annotation). No CHANGELOG entries for deprecations. No migration guide for v3→v4.

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Deprecated APIs have language-appropriate annotations | PARTIAL | Custom `@deprecated_async_method` decorator exists. GS specifies `warnings.warn("Use X instead", DeprecationWarning)` for Python. Current decorator may not emit `DeprecationWarning`. Assessment 12.1.3 = 2/5. |
| Deprecation notice includes replacement API name | COMPLIANT | Assessment: decorator "pointing to alternatives." |
| CHANGELOG entries document deprecations | MISSING | No CHANGELOG exists. Assessment 10.4.5 = 1/5. |
| Removed APIs listed in migration guides | MISSING | No v3→v4 migration guide. Assessment 10.2.4 = 1/5. |

#### Remediation

- **What:** (1) Replace custom `@deprecated_async_method` with standard `warnings.warn("Use X instead", DeprecationWarning, stacklevel=2)` calls (or use the `typing_extensions.deprecated` decorator from Python 3.13+ / `typing-extensions` backport). (2) Create `MIGRATION.md` for v3→v4. (3) Add deprecation entries to CHANGELOG. (4) Document deprecation policy in CONTRIBUTING.md: "Minimum 2 minor versions or 6 months before removal."
- **Complexity:** M (1-3 days)
- **Dependencies:** REQ-PKG-4 (CHANGELOG must exist)
- **Risk:** Low
- **Breaking change:** No — changing the deprecation mechanism from custom decorator to `warnings.warn` is internal
- **Backward compatibility:** Ensure the new deprecation warnings are visible to users running with `python -W default` but suppressed by default in production (`DeprecationWarning` is suppressed in `__main__` by default in Python).
- **Python-specific notes:** Python 3.13 added `warnings.deprecated` decorator. For 3.9-3.12 compatibility, use the `typing_extensions` backport or raw `warnings.warn()` in method body.

---

### REQ-COMPAT-3: Language Version Support

**Overall Status: MISSING**

#### Current State

Assessment 12.1.2 = 3/5: `requires-python = ">=3.9"` in pyproject.toml. README incorrectly says "Python 3.2+". Assessment 9.3.4 = 1/5: CI uses single `python-version: '3.x'`, no matrix testing.

GS specifies Python should test latest 3 releases (e.g., 3.11, 3.12, 3.13). The SDK supports >=3.9 which is broader than required but not tested.

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Minimum language version documented in README | PARTIAL | README says "Python 3.2+" (incorrect). `pyproject.toml` says `>=3.9` (correct). Assessment 12.1.2. |
| CI tests against specified version matrix | MISSING | Single `python-version: '3.x'` — resolves to latest only. No matrix. Assessment 9.3.4 = 1/5. |
| Dropping support for a language version is treated as breaking change | NOT_ASSESSED | Assessment didn't evaluate this policy. |

#### Remediation

- **What:** (1) Fix README to say "Python 3.9+" (or narrow to 3.11+ if desired). (2) Add CI matrix: `python-version: ['3.9', '3.11', '3.12', '3.13']` — 3.9 as the supported floor plus latest 3 releases. (3) Document in CONTRIBUTING.md that dropping a Python version requires a MAJOR version bump.
- **Complexity:** M (1-3 days) — primarily CI configuration
- **Dependencies:** Tier 1 REQ-TEST-* (CI pipeline must exist)
- **Risk:** Low — may discover compatibility issues in older Python versions
- **Breaking change:** If narrowing support from 3.9 to 3.11, this IS a breaking change requiring MAJOR bump. Recommendation: keep 3.9 for v4.x, narrow in v5.
- **Backward compatibility:** Continue supporting 3.9 in v4.x since `requires-python = ">=3.9"` is the declared floor. CI matrix should include 3.9 as floor in addition to latest 3 releases.
- **Python-specific notes:** Use `tox` or GitHub Actions matrix strategy. Python version matrix in `.github/workflows/test.yml`: `python-version: ["3.9", "3.10", "3.11", "3.12", "3.13"]`. Consider using `tox` for local multi-version testing.

---

### REQ-COMPAT-4: Supply Chain Security

**Overall Status: MISSING**

#### Current State

Assessment 12.2.3 = 1/5: no Dependabot or Renovate. Assessment 12.2.5 = 1/5: no SBOM. Assessment 5.2.5 = 4/5: `pip-audit` reports no known CVEs (one-time check, not automated).

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Dependencies scanned for vulnerabilities | MISSING | No automated scanning. No Dependabot/Renovate configured. Assessment 12.2.3 = 1/5. |
| SBOM generated (SHOULD, recommended) | MISSING | No SBOM. Assessment 12.2.5 = 1/5. This is SHOULD (recommended), not MUST. |
| Direct dependencies audited and justified | PARTIAL | `pip-audit` confirms no CVEs. Dependencies are minimal and mainstream (`grpcio`, `pydantic`, `PyJWT`). But `grpcio-tools` is in runtime deps unnecessarily. Assessment 11.1.4 = 3/5. |
| No dependencies with known critical CVEs at release | COMPLIANT | `pip-audit` clean. Assessment 5.2.5 = 4/5. |

#### Remediation

- **What:** (1) Enable Dependabot: add `.github/dependabot.yml` with `package-ecosystem: pip`, weekly schedule. (2) Add `pip-audit` to CI pipeline (run on every PR). (3) Move `grpcio-tools` from runtime to dev dependencies. (4) Optionally: add SBOM generation using `cyclonedx-python` or `syft` in release pipeline.
- **Complexity:** M (1-3 days)
- **Dependencies:** REQ-PKG-3 (release pipeline for SBOM step)
- **Risk:** Low — security improvement
- **Breaking change:** Moving `grpcio-tools` to dev deps: **potentially breaking** if any user imports from `grpc_tools` via the SDK. Unlikely but should be validated.
- **Backward compatibility:** Check if any public API depends on `grpcio-tools`. If not (likely), the move is safe.
- **Python-specific notes:** Dependabot supports `pip` ecosystem natively. Use `pip-audit` (from PyPA) for vulnerability scanning. For SBOM, `cyclonedx-bom` generates CycloneDX format from `pyproject.toml` / `pip freeze`.

---

### REQ-COMPAT-5: End-of-Life Policy

**Overall Status: MISSING**

#### Current State

Assessment 12.2.6 = 2/5: single maintainer, active development. No EOL policy documented. No statement about v3 support window after v4 GA.

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| EOL policy documented in SDK README | MISSING | No EOL policy anywhere. |
| Previous major versions receive security patches for 12 months | MISSING | No commitment stated. v3 will presumably receive no patches after v4 GA. |
| EOL status clearly marked in SDK repository | MISSING | No EOL markers. |

#### Remediation

- **What:** (1) Add "Version Support" section to README stating: "When a new major version reaches GA, the previous major version receives security patches for 12 months. After that, it is end-of-life." (2) When v4 reaches GA, add EOL notice to v3 branch README: "v3.x is in security-only maintenance until [date]. Upgrade to v4."
- **Complexity:** S (< 1 day)
- **Dependencies:** None
- **Risk:** Low — documentation only. Commits the project to 12 months of v3 security patches after v4 GA.
- **Breaking change:** No
- **Python-specific notes:** None; this is a cross-SDK policy.

---

## Category 13: Performance

**Assessment Category:** 13 (Performance)
**Current Score:** 2.60 / 5.0
**Target:** 4.0+
**Gap:** +1.40

### REQ-PERF-1: Published Benchmarks

**Overall Status: MISSING**

#### Current State

Assessment 13.1.1 = 3/5: `tests/benchmarks/` directory with 1 file. Assessment 13.1.3 = 1/5: no documentation on running benchmarks. Assessment 13.1.4 = 1/5: `BENCHMARK_BASELINE.md` is untracked and unpublished.

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Benchmarks exist in SDK repo (language-native framework) | PARTIAL | `tests/benchmarks/` has 1 file. Assessment 13.1.1 = 3/5. Not using `pytest-benchmark` (GS-specified Python framework). |
| Benchmarks runnable with single command | MISSING | No documentation on how to run. Assessment 13.1.3 = 1/5. |
| Results documented in repo (baseline numbers) | MISSING | `BENCHMARK_BASELINE.md` is untracked/unpublished. Assessment 13.1.4 = 1/5. |
| Benchmark methodology documented | MISSING | No hardware, server config, or methodology docs. |

**Required benchmark coverage:**

| Benchmark | Status | Evidence |
|---|---|---|
| Publish throughput (1KB, msg/sec) | PARTIAL | Single benchmark file; unclear which scenarios covered. Assessment 13.1.2 = 2/5. |
| Publish latency (1KB, p50/p99) | PARTIAL | Same file; latency measurement unclear. |
| Queue roundtrip latency (1KB, p50/p99) | PARTIAL | Likely not covered by single file. |
| Connection setup time | MISSING | Not mentioned in assessment. |

#### Remediation

- **What:** (1) Create comprehensive benchmark suite using `pytest-benchmark` covering all 4 required scenarios. (2) Add a `benchmarks/README.md` documenting: hardware requirements, server setup, how to run (`pytest tests/benchmarks/ --benchmark-json=results.json`), how to interpret results. (3) Run benchmarks on standard hardware (document specs) and commit baseline results to `BENCHMARKS.md`. (4) Add optional multi-payload matrix (64B, 1KB, 64KB) and concurrent publisher scaling.
- **Complexity:** L (3-5 days) — designing meaningful benchmarks, setting up reproducible server, running multiple scenarios, documenting
- **Dependencies:** None technically, but more meaningful after Tier 1 connection improvements (reconnect, backoff)
- **Risk:** Low — benchmarks are additive
- **Breaking change:** No
- **Python-specific notes:** Use `pytest-benchmark` (already in dev deps per GS recommendation). Structure benchmarks as parametrized pytest tests. Use `@pytest.mark.benchmark` decorator. For connection setup time, measure time from `PubSubClient()` construction to first successful `ping()`.

---

### REQ-PERF-2: Connection Reuse

**Overall Status: PARTIAL** (3 COMPLIANT + 1 MISSING — documentation gap only)

#### Current State

Assessment 13.2.6 = 4/5: "Single gRPC channel per client, reused for all operations. No per-operation channel creation." The implementation is correct, but documentation is missing.

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Single Client uses one long-lived gRPC channel | COMPLIANT | Single gRPC channel per client. Assessment 13.2.6 = 4/5. `async_transport.py` and `transport.py`. |
| Multiple concurrent operations multiplex over same channel | COMPLIANT | gRPC multiplexing inherent in the architecture. |
| Documentation advises against creating Client per operation | MISSING | No performance documentation exists. Assessment 13.1.3 = 1/5. |
| No per-operation connection overhead | COMPLIANT | Channel reused. Assessment 13.2.6 = 4/5. |

#### Remediation

- **What:** Add documentation (README section or separate `PERFORMANCE.md`) stating: "Reuse a single client instance per application. Do not create a new client for each operation — this wastes resources and prevents connection reuse."
- **Complexity:** S (< 1 day)
- **Dependencies:** None
- **Risk:** Low — documentation only
- **Breaking change:** No
- **Python-specific notes:** Include Python-specific guidance: "Use dependency injection or module-level singleton to share the client instance across your application."
- **See also:** REQ-CONN-6 (thread safety documentation — same core message, different location). Ensure consistent wording across both.

---

### REQ-PERF-3: Efficient Serialization

**Overall Status: PARTIAL**

#### Current State

Assessment 8.3.2 = 4/5: protobuf serialization via standard runtime. Assessment 13.2.4 = 3/5: "Risk: unbounded `queue.Queue` in sync sender."

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Protobuf serialization uses standard runtime | COMPLIANT | All messages use `encode()` → protobuf `SerializeToString()`. Assessment 8.3.2 = 4/5. |
| Avoid unnecessary memory copies of message bodies | PARTIAL | Sync `EventSender` uses unbounded `queue.Queue` — messages are copied into the queue and then read out. For large messages, this doubles memory. Assessment 13.2.4. |
| Buffer pooling recommended only when benchmarks justify | COMPLIANT | No pooling implemented; no benchmarks showing allocation pressure. Assessment 13.2.1 = 1/5, but GS says pooling is "recommended only when benchmarks demonstrate allocation pressure" — so absence is acceptable. |

#### Remediation

- **What:** (1) Add `maxsize` parameter to sync `EventSender`'s `queue.Queue` to bound memory usage (e.g., `maxsize=1000`). Make configurable. (2) Review message encoding path for unnecessary `bytes` copies (e.g., copying body into protobuf — protobuf `bytes` field assignment may copy; consider `memoryview` if benchmarks show this matters).
- **Complexity:** S (< 1 day for queue bounding; M if optimizing byte copies)
- **Dependencies:** REQ-PERF-1 (benchmarks needed to justify byte copy optimization)
- **Risk:** Low for queue bounding. Medium for byte copy optimization (premature optimization risk).
- **Breaking change:** Adding `maxsize` to queue could cause `queue.Full` exceptions if the queue is full and the producer is faster than the consumer. Must handle gracefully (block or raise `KubeMQError`).
- **Python-specific notes:** Python's `queue.Queue(maxsize=N)` blocks on `put()` when full (backpressure). This is actually desirable for flow control. Document the behavior.

---

### REQ-PERF-4: Batch Operations

**Overall Status: COMPLIANT**

#### Current State

Assessment 1.3.2 = 2/2: `SendQueueMessagesBatch` unary RPC. Async batch sends use `asyncio.Semaphore(max_concurrent=100)`. Events batch methods exist.

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Batch operations use single gRPC call (not N individual calls) | COMPLIANT | `SendQueueMessagesBatch` unary RPC (`async_transport.py:475`). Assessment 1.3.2 = 2/2. |
| Batch size configurable | COMPLIANT | User passes a list of messages — batch size is the list length. `max_concurrent` Semaphore controls concurrency for events batch. |

#### Remediation

No remediation needed.

---

### REQ-PERF-5: Performance Documentation

**Overall Status: MISSING**

#### Current State

Assessment 13.1.3 = 1/5: no benchmark documentation. No performance characteristics documented anywhere. Known limitations (max message size) are in code constants but not in user-facing docs.

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| README or doc includes performance characteristics | MISSING | No performance section in README or any doc. |
| Tuning guidance (batching, batch sizes, connection sharing) | MISSING | No tuning guidance anywhere. |
| Known limitations documented (max message size, max concurrent streams) | MISSING | `DEFAULT_MAX_MESSAGE_SIZE = 100 * 1024 * 1024` (100MB) is in code but not in docs. |

#### Remediation

- **What:** Create `PERFORMANCE.md` or add a "Performance" section to README covering:
  1. Performance characteristics: "Single gRPC channel per client, multiplexed operations."
  2. Tuning guidance: "Use batch sends for >100 msg/sec throughput. Optimal batch size: 100-1000 messages."
  3. Known limitations: max message size (100MB default, configurable), max concurrent streams (gRPC default), no message compression.
- **Complexity:** M (1-3 days) — requires running benchmarks to validate claims
- **Dependencies:** REQ-PERF-1 (benchmarks for accurate numbers)
- **Risk:** Low
- **Breaking change:** No
- **Python-specific notes:** Include Python-specific notes: GIL limitations for CPU-bound callbacks, asyncio recommendations for high-throughput scenarios, `uvloop` compatibility.

---

### REQ-PERF-6: Performance Tips Documentation

**Overall Status: MISSING**

#### Current State

No "Performance Tips" section exists anywhere in the SDK documentation.

#### Gap Analysis

| Acceptance Criterion | Status | Evidence |
|---|---|---|
| Performance Tips section covering: | MISSING | No performance tips documentation. |
| 1. Reuse client instance | MISSING | Not documented. |
| 2. Use batching for high-throughput queue sends | MISSING | Not documented. |
| 3. Do not block subscription callbacks | MISSING | Not documented. |
| 4. Close streams when done | MISSING | Not documented. |

#### Remediation

- **What:** Add "Performance Tips" section to README or `PERFORMANCE.md`:
  ```
  ## Performance Tips
  1. **Reuse the client instance** — Create one `PubSubClient` / `QueuesClient` per application. Do not create per-operation.
  2. **Use batching for high throughput** — Use `send_queue_messages_batch()` for >100 msg/sec. Batch sizes of 100-1000 perform best.
  3. **Don't block subscription callbacks** — Sync callbacks run in daemon threads. Keep them fast. Offload heavy work to a thread pool or task queue.
  4. **Close streams when done** — Use `with` / `async with` context managers to ensure cleanup. Unclosed streams leak resources.
  5. **Use async for I/O-heavy workloads** — `AsyncClient` avoids thread overhead for concurrent subscriptions.
  ```
- **Complexity:** S (< 1 day)
- **Dependencies:** None
- **Risk:** Low
- **Breaking change:** No
- **Python-specific notes:** Add Python-specific tip about `uvloop` for asyncio performance improvement. Add tip about `ProcessPoolExecutor` for CPU-bound callback work (bypasses GIL).

---

## Combined Dependency Graph

```
=== TIER 1 DEPENDENCIES ===

REQ-ERR-1 (Error fields)
  └─> REQ-ERR-2 (Classification) ─────────────────────┐
       └─> REQ-ERR-3 (Retry) ──────────────────────────┤
            ├─> REQ-ERR-7 (Retry throttling)            │
            └─> REQ-ERR-8 (Streaming errors)            │
       └─> REQ-ERR-6 (gRPC mapping)                    │
  └─> REQ-ERR-5 (Actionable messages) ◄────────────────┘
  └─> REQ-ERR-9 (Async propagation)

REQ-CONN-2 (State machine) ─────────────────────────────┐
  └─> REQ-CONN-1 (Auto-reconnection) ◄──────────────────┤
       └─> REQ-AUTH-6 (TLS reload on reconnect)         │
       └─> REQ-AUTH-3 (mTLS cert reload)                │
  └─> REQ-CONN-4 (Graceful shutdown)                    │
  └─> REQ-CONN-5 (WaitForReady)                         │
  └─> REQ-AUTH-4 (Credential provider) ◄────────────────┘

REQ-OBS-4 (Optional dependency) ─────────────────────────┐
  └─> REQ-OBS-1 (Traces) ◄──────────────────────────────┤
       └─> REQ-OBS-2 (Trace propagation)                │
  └─> REQ-OBS-3 (Metrics) ◄─────────────────────────────┤
  └─> REQ-OBS-5 (Structured logging)                    │
                                                         │
REQ-CQ-3 (Fix lint) ────────────────────────────────────┐│
  └─> REQ-TEST-3 (CI pipeline) ◄────────────────────────┤│
       └─> REQ-TEST-1 (Unit tests in CI)                ││
       └─> REQ-TEST-5 (Coverage in CI)                  ││
       └─> REQ-CQ-4 (Vulnerability scanning)           ││
                                                        ││
REQ-DOC-3 (Quickstart) ─┐                              ││
REQ-DOC-5 (Troubleshoot)│                              ││
REQ-DOC-6 (Changelog) ──┼─> REQ-DOC-2 (README)        ││
REQ-DOC-7 (Migration)   │                              ││
REQ-DOC-4 (Cookbook) ────┘                              ││
  └─> REQ-DOC-1 (API reference)                        ││

=== TIER 2 DEPENDENCIES ===

Tier 2 internal dependencies
├── REQ-API-2 (feature matrix)
│   └── → REQ-DX-3 (verb coordination across SDKs)
├── REQ-PKG-4 (CHANGELOG)
│   ├── → REQ-PKG-1 (package includes CHANGELOG)
│   ├── → REQ-PKG-3 (validate CHANGELOG in release)
│   └── → REQ-COMPAT-2 (deprecation entries in CHANGELOG)
├── REQ-PKG-3 (release pipeline)
│   └── → REQ-COMPAT-4 (SBOM in release pipeline)
├── REQ-PERF-1 (benchmarks)
│   ├── → REQ-PERF-3 (justify/skip buffer pooling)
│   └── → REQ-PERF-5 (accurate performance docs)

=== CROSS-TIER DEPENDENCIES (Tier 2 → Tier 1) ===

Tier 1 REQ-ERR-* (error classification, is_retryable)
├── → REQ-DX-4 (validation errors non-retryable)
└── → REQ-CONC-2 (cancellation error types)

Tier 1 REQ-CONN-4 (graceful drain)
└── → REQ-CONC-5 (shutdown-callback safety)

Tier 1 REQ-TEST-* (CI pipeline, test infrastructure)
├── → REQ-PKG-3 (tests in release pipeline)
└── → REQ-COMPAT-3 (CI version matrix)
```

---

## Unified Implementation Sequence

### Phase 1: Foundation (Weeks 1-3)
*Establish error handling, state machine, CI, and quick-win documentation — everything else depends on these.*

| Order | REQ | Tier | Description | Effort | Depends On |
|-------|-----|------|-------------|--------|------------|
| 1.1 | REQ-CQ-3 | 1 | Fix 204 lint errors, add ruff to CI | M | — |
| 1.2 | REQ-ERR-1 | 1 | Add Operation, Channel, IsRetryable, RequestID to errors | M | — |
| 1.3 | REQ-ERR-2 | 1 | Implement error classification | M | 1.2 |
| 1.4 | REQ-CONN-2 | 1 | Implement connection state machine | L | — |
| 1.5 | REQ-TEST-3 | 1 | Create CI pipeline (lint + unit tests + matrix) | M | 1.1 |
| 1.6 | REQ-ERR-6 | 1 | Map all 17 gRPC status codes | M | 1.3 |
| 1.7 | REQ-OBS-5 | 1 | Define Logger Protocol and NoopLogger | M | — |
| 1.8 | REQ-ERR-9 | 1 | Add error callbacks to subscriptions | M | 1.2, 1.7 |
| 1.9 | REQ-DOC-6 | 1 | Create CHANGELOG.md | S | — |
| 1.10 | REQ-CQ-4 | 1 | Move grpcio-tools to dev deps, add pip-audit to CI | S | 1.5 |
| 1.11 | REQ-API-2 | 2 | Create feature matrix document | S | — |
| 1.12 | REQ-CONC-1 | 2 | Add thread safety docstrings | S | — |
| 1.13 | REQ-PKG-2 | 2 | Fix version consistency | S | — |
| 1.14 | REQ-PKG-4 | 2 | Create CHANGELOG.md (Tier 2 items) — dedup w/ 1.9 | 0 | 1.9 |
| 1.15 | REQ-COMPAT-5 | 2 | Document EOL policy | S | — |
| 1.16 | REQ-PERF-6 | 2 | Write performance tips | S | — |
| 1.17 | REQ-PERF-2 | 2 | Document connection reuse | S | — |
| 1.18 | REQ-API-3 | 2 | Add `KubeMQNotImplementedError` | S | — |

### Phase 2: Core Features (Weeks 4-8)
*Retry, reconnection, auth, observability foundation, and Tier 2 documentation/metadata.*

| Order | REQ | Tier | Description | Effort | Depends On |
|-------|-----|------|-------------|--------|------------|
| 2.1 | REQ-ERR-3 | 1 | Implement auto-retry with configurable policy | L | 1.3 |
| 2.2 | REQ-CONN-1 | 1 | Implement async auto-reconnection with buffering | XL | 1.4, 2.1 |
| 2.3 | REQ-ERR-5 | 1 | Make error messages actionable | M | 1.2, 1.3, 2.1 |
| 2.4 | REQ-ERR-7 | 1 | Add retry throttling | M | 2.1 |
| 2.5 | REQ-ERR-8 | 1 | Implement streaming error handling | L-XL | 2.1, 1.4 |
| 2.6 | REQ-CONN-4 | 1 | Implement graceful shutdown/drain | M | 2.2 |
| 2.7 | REQ-AUTH-1 | 1 | Add token rotation support | M | — |
| 2.8 | REQ-AUTH-2 | 1 | TLS improvements (skip_verify, min version, PEM bytes) | M | 1.3 |
| 2.9 | REQ-AUTH-4 | 1 | Implement CredentialProvider interface | L | 1.4, 1.3 |
| 2.10 | REQ-AUTH-3 | 1 | mTLS improvements (PEM bytes, cert reload) | M | 2.2, 2.8 |
| 2.11 | REQ-AUTH-6 | 1 | TLS credential reload on reconnection | M | 2.2, 2.8 |
| 2.12 | REQ-ERR-4 | 1 | Adjust per-operation timeout defaults + legacy_timeout_mode | M | — |
| 2.13 | REQ-CONN-3 | 1 | Verify/adjust keepalive defaults | S | — |
| 2.14 | REQ-CONN-5 | 1 | Add WaitForReady, adjust connection timeout | S | 1.4 |
| 2.15 | REQ-OBS-4 | 1 | Implement OTel optional dependency architecture | M | — |
| 2.16 | REQ-DX-2 | 2 | Add default address `localhost:50000` | S | — |
| 2.17 | REQ-DX-4 | 2 | Improve input validation | M | Tier 1 ERR-* |
| 2.18 | REQ-DX-5 | 2 | Make outbound messages immutable | S | — |
| 2.19 | REQ-PKG-1 | 2 | Complete PyPI metadata | S | — |
| 2.20 | REQ-COMPAT-2 | 2 | Deprecation policy + migration guide | M | REQ-PKG-4 |
| 2.21 | REQ-COMPAT-1 | 2 | Create compatibility matrix | M | — |
| 2.22 | REQ-CONC-3 | 2 | Add callback behavior documentation | M | — |
| 2.23 | REQ-PERF-5 | 2 | Write performance documentation | M | — |
| 2.24 | REQ-COMPAT-3 | 2 | Fix README Python version | S | — |

### Phase 3: Quality & Polish (Weeks 9-14)
*Observability, documentation, testing maturity, code quality, and remaining Tier 2 infrastructure.*

| Order | REQ | Tier | Description | Effort | Depends On |
|-------|-----|------|-------------|--------|------------|
| 3.1 | REQ-OBS-1 | 1 | Implement OTel trace instrumentation | XL | 2.15, 2.1 |
| 3.2 | REQ-OBS-2 | 1 | Implement W3C trace context propagation | L | 3.1 |
| 3.3 | REQ-OBS-3 | 1 | Implement OTel metrics | L | 2.15, 1.4, 1.3 |
| 3.4 | REQ-TEST-1 | 1 | Add unit tests for new features (retry, state machine, etc.) | L | 2.1-2.11 |
| 3.5 | REQ-TEST-2 | 1 | Expand integration tests (sync, auth, reconnection) | L | 2.2 |
| 3.6 | REQ-TEST-5 | 1 | Add coverage enforcement to CI | S | 1.5 |
| 3.7 | REQ-DOC-3 | 1 | Create quickstart examples | S | — |
| 3.8 | REQ-DOC-2 | 1 | Rewrite README with all 10 sections | M | 3.7 |
| 3.9 | REQ-DOC-4 | 1 | Rewrite cookbook for v4, add missing examples | L-XL | 3.1 |
| 3.10 | REQ-DOC-5 | 1 | Create troubleshooting guide | M | 2.3 |
| 3.11 | REQ-DOC-7 | 1 | Create v3→v4 migration guide | M | — |
| 3.12 | REQ-DOC-1 | 1 | Complete docstrings, build API reference site | L | 1.5 |
| 3.13 | REQ-CQ-1 | 1 | Introduce TransportProtocol interface layer | L | 3.4 |
| 3.14 | REQ-CQ-2 | 1 | Rename internal packages with _ prefix | M | 3.13 |
| 3.15 | REQ-AUTH-5 | 1 | Security audit and credential exclusion | M | 2.8 |
| 3.16 | REQ-CQ-7 | 1 | Add TLS/insecure warnings | S | 2.8, 1.7 |
| 3.17 | REQ-CONN-6 | 1 | Add thread safety documentation | S | — |
| 3.18 | REQ-CQ-6 | 1 | Remove dead code, enable branch protection | S | — |
| 3.19 | REQ-CQ-5 | 1 | Code organization alignment (if needed) | S-L | 3.14 |
| 3.20 | REQ-TEST-4 | 1 | Add testutil package, default test config | S | — |
| 3.21 | REQ-PKG-3 | 2 | Automated release pipeline | M | Tier 1 CI |
| 3.22 | REQ-COMPAT-3 | 2 | CI version matrix | M | Tier 1 CI |
| 3.23 | REQ-COMPAT-4 | 2 | Supply chain (Dependabot, pip-audit) | M | REQ-PKG-3 |
| 3.24 | REQ-CONC-2 | 2 | Cancellation improvements | M | Tier 1 errors |
| 3.25 | REQ-CONC-5 | 2 | Shutdown-callback safety | M | Tier 1 drain |
| 3.26 | REQ-PERF-1 | 2 | Comprehensive benchmarks | L | — |
| 3.27 | REQ-DX-3 | 2 | Normalize method names (breaking) | L | REQ-API-2, next major |
| 3.28 | REQ-PERF-3 | 2 | Serialize optimization | S | REQ-PERF-1 |

---

## Combined Effort Summary

### By Priority (All Tiers)

| Priority | Count | Total Effort |
|----------|-------|-------------|
| P0 | 26 | ~71 days |
| P1 | 11 | ~24 days |
| P2 | 29 | ~38-41 days |
| P3 | 1 | ~4 days |
| NOT_ASSESSED | 2 | ~6 days (estimated; pending assessment) |
| COMPLIANT | 4 | 0 days |
| **Total** | **73** | **~143-146 days** |

### By Tier

| Tier | Items | Total Effort |
|------|-------|-------------|
| Tier 1 (Categories 01-07) | 45 | ~110 days |
| Tier 2 (Categories 08-13) | 28 | ~28-38 days |
| **Total** | **73** | **~138-148 days** |

*Note: 2 Tier 1 items (REQ-AUTH-4, REQ-AUTH-6) are NOT_ASSESSED with estimated effort (~6d) included in Tier 1 total. REQ-PKG-4 effort deduplicated to 0 (same deliverable as REQ-DOC-6).*

### Tier 1 Effort Breakdown

| REQ | Description | Priority | Effort | Phase |
|-----|-------------|----------|--------|-------|
| REQ-ERR-1 | Typed Error Hierarchy | P0 | M (2d) | 1 |
| REQ-ERR-2 | Error Classification | P0 | M (2d) | 1 |
| REQ-ERR-3 | Auto-Retry | P0 | L (4d) | 2 |
| REQ-ERR-4 | Per-Operation Timeouts | P1 | M (1.5d) | 2 |
| REQ-ERR-5 | Actionable Error Messages | P0 | M (2d) | 2 |
| REQ-ERR-6 | gRPC Error Mapping | P0 | M (2d) | 1 |
| REQ-ERR-7 | Retry Throttling | P0 | M (2d) | 2 |
| REQ-ERR-8 | Streaming Error Handling | P0 | L-XL (6d) | 2 |
| REQ-ERR-9 | Async Error Propagation | P0 | M (2d) | 1 |
| REQ-CONN-1 | Auto-Reconnection | P0 | XL (7d) | 2 |
| REQ-CONN-2 | Connection State Machine | P0 | L (4d) | 1 |
| REQ-CONN-3 | gRPC Keepalive | P2 | S (0.5d) | 2 |
| REQ-CONN-4 | Graceful Shutdown | P0 | M (2d) | 2 |
| REQ-CONN-5 | Connection Configuration | P1 | S (0.5d) | 2 |
| REQ-CONN-6 | Connection Reuse | P1 | S (0.5d) | 3 |
| REQ-AUTH-1 | Token Authentication | P1 | M (2d) | 2 |
| REQ-AUTH-2 | TLS Encryption | P0 | M (2d) | 2 |
| REQ-AUTH-3 | Mutual TLS | P0 | M (2d) | 2 |
| REQ-AUTH-4 | Credential Provider | NOT_ASSESSED | L (4d) | 2 |
| REQ-AUTH-5 | Security Best Practices | P0 | M (1.5d) | 3 |
| REQ-AUTH-6 | TLS Reconnection | NOT_ASSESSED | M (2d) | 2 |
| REQ-TEST-1 | Unit Tests | P0 | L (4d) | 3 |
| REQ-TEST-2 | Integration Tests | P1 | L (4d) | 3 |
| REQ-TEST-3 | CI Pipeline | P0 | M (2d) | 1 |
| REQ-TEST-4 | Test Organization | P2 | S (0.5d) | 3 |
| REQ-TEST-5 | Coverage Tools | P0 | S (0.5d) | 3 |
| REQ-OBS-1 | OTel Traces | P0 | XL (7d) | 3 |
| REQ-OBS-2 | W3C Trace Context | P0 | L (4d) | 3 |
| REQ-OBS-3 | OTel Metrics | P0 | L (4d) | 3 |
| REQ-OBS-4 | Near-Zero Cost | P0 | M (2d) | 2 |
| REQ-OBS-5 | Structured Logging | P0 | M (2d) | 1 |
| REQ-DOC-1 | API Reference | P1 | L (4d) | 3 |
| REQ-DOC-2 | README | P1 | M (2d) | 3 |
| REQ-DOC-3 | Quick Start | P0 | S (0.5d) | 3 |
| REQ-DOC-4 | Cookbook | P1 | L-XL (6d) | 3 |
| REQ-DOC-5 | Troubleshooting | P0 | M (2d) | 3 |
| REQ-DOC-6 | CHANGELOG | P0 | S (0.5d) | 1 |
| REQ-DOC-7 | Migration Guide | P0 | M (2d) | 3 |
| REQ-CQ-1 | Layered Architecture | P2 | L (4d) | 3 |
| REQ-CQ-2 | Internal Separation | P2 | M (2d) | 3 |
| REQ-CQ-3 | Linting & Formatting | P1 | M (2.5d) | 1 |
| REQ-CQ-4 | Minimal Dependencies | P1 | S (0.5d) | 1 |
| REQ-CQ-5 | Code Organization | P2 | S-L (0.5-4d) | 3 |
| REQ-CQ-6 | Code Review Standards | P2 | S (0.5d) | 3 |
| REQ-CQ-7 | Secure Defaults | P1 | S (0.5d) | 3 |

### Tier 2 Effort Breakdown

| REQ | Description | Priority | Effort | Phase |
|-----|-------------|----------|--------|-------|
| REQ-API-1 | Core Feature Coverage | — | — | COMPLIANT |
| REQ-API-2 | Feature Matrix | P2 | S | 1 |
| REQ-API-3 | No Silent Feature Gaps | P2 | S | 1 |
| REQ-DX-1 | Idiomatic Configuration | — | — | COMPLIANT |
| REQ-DX-2 | Minimal Code Happy Path | P2 | S | 2 |
| REQ-DX-3 | Consistent Verbs | P3 | L | 3 |
| REQ-DX-4 | Fail-Fast Validation | P2 | M | 2 |
| REQ-DX-5 | Message Builder/Factory | P2 | S | 2 |
| REQ-CONC-1 | Thread Safety Documentation | P2 | S | 1 |
| REQ-CONC-2 | Cancellation & Timeout | P2 | M | 3 |
| REQ-CONC-3 | Callback Behavior | P2 | M | 2 |
| REQ-CONC-4 | Async-First | — | — | COMPLIANT |
| REQ-CONC-5 | Shutdown-Callback Safety | P2 | M | 3 |
| REQ-PKG-1 | Package Publishing | P2 | S | 2 |
| REQ-PKG-2 | Semantic Versioning | P2 | S | 1 |
| REQ-PKG-3 | Release Pipeline | P2 | M | 3 |
| REQ-PKG-4 | Conventional Commits | P2 | 0 (dedup w/ DOC-6) | 1 |
| REQ-COMPAT-1 | Compatibility Matrix | P2 | M | 2 |
| REQ-COMPAT-2 | Deprecation Policy | P2 | M | 2 |
| REQ-COMPAT-3 | Language Version Support | P2 | M | 3 |
| REQ-COMPAT-4 | Supply Chain Security | P2 | M | 3 |
| REQ-COMPAT-5 | EOL Policy | P2 | S | 1 |
| REQ-PERF-1 | Published Benchmarks | P2 | L | 3 |
| REQ-PERF-2 | Connection Reuse Docs | P2 | S | 1 |
| REQ-PERF-3 | Efficient Serialization | P2 | S | 3 |
| REQ-PERF-4 | Batch Operations | — | — | COMPLIANT |
| REQ-PERF-5 | Performance Documentation | P2 | M | 2 |
| REQ-PERF-6 | Performance Tips | P2 | S | 1 |

### By Phase (All Tiers)

| Phase | Items | Effort | Calendar Weeks |
|-------|-------|--------|----------------|
| Phase 1: Foundation | 18 | ~22 days | 3-4 weeks |
| Phase 2: Core Features | 24 | ~41 days | 5-6 weeks |
| Phase 3: Quality & Polish | 29 | ~71 days | 7-8 weeks |
| **Total** | **71** | **~134-144 days** | **~15-18 weeks** |

*Note: Effort changes from R1 review: CQ-3 S→M (+1.5d), ERR-4 S→M (+1d), AUTH-5 S→M (+1d), ERR-8 L→L-XL (+2d), DOC-4 L→L-XL (+2d), PKG-4 dedup (-0.5d). Net: ~+7d.*

*Note: Phase headers (Weeks 1-3, 4-8, 9-14) represent 14 weeks of sequential execution. The 15-18 week estimate includes coordination overhead, inter-phase gaps, and schedule buffer.*

---

## Cross-Category Dependencies (All Tiers)

### Tier 1 Internal Dependencies

| Source REQ | Target REQ | Dependency Type | Notes |
|-----------|-----------|-----------------|-------|
| REQ-ERR-2 | REQ-ERR-1 | Must complete first | Error fields needed for classification |
| REQ-ERR-3 | REQ-ERR-2 | Must complete first | Classification needed for retry decisions |
| REQ-ERR-3 | REQ-CONN-2 | Should complete first | State machine for retry suspension during RECONNECTING |
| REQ-ERR-5 | REQ-ERR-1, ERR-2, ERR-3 | Must complete first | Error context, categories, and retry info for messages |
| REQ-ERR-6 | REQ-ERR-2 | Must complete first | Categories needed for gRPC mapping |
| REQ-ERR-7 | REQ-ERR-3 | Must complete first | Retry system must exist before throttling |
| REQ-ERR-8 | REQ-ERR-3, CONN-2 | Must complete first | Backoff policy, state machine for stream errors |
| REQ-ERR-9 | REQ-ERR-1, OBS-5 | Should complete first | Error types for callbacks, logger for defaults |
| REQ-CONN-1 | REQ-CONN-2, ERR-3 | Must complete first | State machine and backoff for reconnection |
| REQ-CONN-4 | REQ-CONN-1, CONN-2 | Must complete first | Buffer and state machine for drain |
| REQ-CONN-5 | REQ-CONN-2 | Should complete first | State machine for WaitForReady |
| REQ-AUTH-2 | REQ-ERR-2 | Should complete first | Error classification for TLS handshake failures |
| REQ-AUTH-3 | REQ-CONN-1, AUTH-2 | Must complete first | Reconnection for cert reload; PEM bytes from AUTH-2 |
| REQ-AUTH-4 | REQ-CONN-2, ERR-2 | Must complete first | State machine for provider timing; error classification |
| REQ-AUTH-6 | REQ-CONN-1, AUTH-2, AUTH-3 | Must complete first | Reconnection lifecycle, TLS config, mTLS config |
| REQ-OBS-1 | REQ-OBS-4, ERR-3 | Must complete first | Optional dep architecture; retry events |
| REQ-OBS-2 | REQ-OBS-1 | Must complete first | Spans must exist for context propagation |
| REQ-OBS-3 | REQ-OBS-4, CONN-2, ERR-2 | Must complete first | Optional dep; state for connection metrics; error types for error.type |
| REQ-TEST-1 | REQ-ERR-2, ERR-3, CONN-2 | Should complete first | Features must exist to test them |
| REQ-TEST-3 | REQ-CQ-3 | Should complete first | Fix lint errors before enforcing in CI |
| REQ-TEST-5 | REQ-TEST-3 | Must complete first | CI pipeline required for coverage enforcement |
| REQ-DOC-2 | REQ-DOC-3, DOC-5 | Content dependency | README references quickstart and troubleshooting |
| REQ-DOC-4 | REQ-OBS-1, AUTH-2 | Content dependency | Examples need OTel and TLS features |
| REQ-DOC-1 | REQ-TEST-3 | Process dependency | CI pipeline for doc linter enforcement |

### Tier 2 Internal Dependencies

| Source REQ | Target REQ | Dependency Type | Notes |
|-----------|-----------|-----------------|-------|
| REQ-DX-3 | REQ-API-2 | Content dependency | Feature matrix needed for cross-SDK verb coordination |
| REQ-PKG-1 (includes CHANGELOG) | REQ-PKG-4 | Must complete first | CHANGELOG must exist before it can be included in package |
| REQ-PKG-3 (validate CHANGELOG) | REQ-PKG-4 | Must complete first | CHANGELOG must exist before CI can validate it |
| REQ-COMPAT-2 (deprecation CHANGELOG) | REQ-PKG-4 | Must complete first | CHANGELOG must exist before deprecations can be documented in it |
| REQ-COMPAT-4 (supply chain) | REQ-PKG-3 | Must complete first | SBOM generation runs as part of release pipeline |
| REQ-PERF-5 (perf docs) | REQ-PERF-1 | Content dependency | Benchmarks needed for accurate performance documentation |
| REQ-PERF-3 (serialization opt) | REQ-PERF-1 | Content dependency | Benchmarks needed to justify optimization work |

### Cross-Tier Dependencies (Tier 2 → Tier 1)

| From (Tier 2) | To (Tier 1) | Nature |
|---|---|---|
| REQ-DX-4 (fail-fast validation) | Tier 1 REQ-ERR-* | `is_retryable` property needed for validation error classification |
| REQ-CONC-2 (cancellation) | Tier 1 REQ-ERR-* | Error hierarchy must include cancellation error type |
| REQ-CONC-5 (shutdown safety) | Tier 1 REQ-CONN-4 | Drain concept must be designed at connection level first |
| REQ-PKG-3 (release pipeline) | Tier 1 REQ-TEST-* | CI must run tests before release pipeline can validate them |
| REQ-COMPAT-3 (version matrix CI) | Tier 1 REQ-TEST-* | CI infrastructure must exist before adding version matrix |

---

## GS Internal Inconsistency Flags

### Tier 1

1. **Connection timeout default:** REQ-CONN-5 says connection timeout default is 10s. REQ-ERR-4 timeout table says "Connection establishment: See Category 2, REQ-CONN-5 (default: 10s)". Consistent — no conflict.

2. **Retry defaults vs reconnection defaults:** REQ-ERR-3 says initial backoff 500ms, max 30s, multiplier 2.0. REQ-CONN-1 says initial delay 500ms, max delay 30s, multiplier 2.0. GS explicitly states these are "independent policies with independent configuration" even though defaults are identical. No conflict — by design.

3. **Default send timeout:** REQ-ERR-4 says send/publish default is 5s. Current SDK uses 30s global default. This is a gap, not a GS inconsistency.

### Tier 2

No internal inconsistencies were found between Tier 2 Golden Standard specifications. All cross-references (e.g., REQ-CONC-5 referencing REQ-CONN-4's drain timeout) are consistent.

**Note:** REQ-CONC-5 defines a 30-second callback completion timeout as separate from REQ-CONN-4's 5-second drain timeout. These are intentionally different timeouts for different purposes (callback completion vs. in-flight operation flushing).
