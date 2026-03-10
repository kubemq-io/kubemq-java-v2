# Python SDK — Tier 2 Gap Research Report

**Overall Assessment Score:** 2.96 / 5.0
**Tier 2 Weighted Score:** 3.46 / 5.0
**Target Score:** 4.0+
**Tier 2 Gap:** +0.54

---

## Executive Summary

### Gap Overview

| GS Category | Assessment Cat # | Current | Target | Gap | Status | Priority | Effort |
|---|---|---|---|---|---|---|---|
| 08 API Completeness | 1 | 4.07 | 4.0 | -0.07 | MISSING | P2 | S |
| 09 API Design & DX | 2 | 3.63 | 4.0 | +0.37 | PARTIAL | P3 | M |
| 10 Concurrency | 6 | 3.75 | 4.0 | +0.25 | MISSING | P2 | M |
| 11 Packaging | 11 | 2.90 | 4.0 | +1.10 | MISSING | P2 | L |
| 12 Compatibility | 12 | 1.90 | 4.0 | +2.10 | MISSING | P2 | L |
| 13 Performance | 13 | 2.60 | 4.0 | +1.40 | MISSING | P2 | L |

**Summary:** Of 28 Tier 2 REQ-* items, 4 are COMPLIANT (14%), 9 are PARTIAL (32%), and 15 are MISSING (54%). Category 08 (API Completeness) is the only category meeting its score target (4.07 vs 4.0), but even it has a MISSING REQ (feature matrix). The largest gaps are in Category 12 (Compatibility, +2.10) and Category 13 (Performance, +1.40), both dominated by missing documentation and process artifacts.

### Unassessed Requirements

**Count: 13 acceptance criteria across 8 REQ-* items were NOT_ASSESSED.**

These criteria were either not covered by the assessment methodology or were added post-assessment via the adjudication process:

1. **REQ-API-3** (No Silent Feature Gaps): `ErrNotImplemented` error pattern and tracking issues — not evaluated
2. **REQ-DX-4** (Fail-Fast Validation): Whether validation errors are classified as non-retryable — depends on `is_retryable` property (absent, covered in Tier 1)
3. **REQ-CONC-2** (Cancellation): Whether cancelled operations produce a distinct cancellation error (vs generic timeout) — not evaluated
4. **REQ-CONC-3** (Callback Behavior): Default callback concurrency, concurrency control mechanism, event loop blocking — 3 criteria not evaluated
5. **REQ-CONC-5** (Shutdown Safety): Whether `Close()` waits for in-flight callbacks with timeout, whether post-`Close()` operations return `ErrClientClosed` — 2 criteria not evaluated (likely added post-assessment; references REQ-CONN-4 drain concept from Tier 1)
6. **REQ-PKG-3** (Release Pipeline): Whether failed releases prevent partial artifact publishing — not evaluated
7. **REQ-COMPAT-1** (Compatibility Matrix): Whether SDK validates server version on connection and logs warnings — 2 criteria not evaluated
8. **REQ-COMPAT-3** (Language Version Support): Whether dropping a Python version is treated as a breaking change — not evaluated

### Critical Path

No Tier 2 items are P0 (blockers) or P1 (critical prerequisites for Tier 1). However, several Tier 2 items have **cross-tier dependencies** on Tier 1 work:

1. **REQ-CONC-2** (cancellation error types) depends on Tier 1 REQ-ERR-* (error classification, `is_retryable`)
2. **REQ-CONC-5** (shutdown safety) depends on Tier 1 REQ-CONN-4 (graceful drain)
3. **REQ-PKG-3** (release pipeline) depends on Tier 1 REQ-TEST-* (CI pipeline with tests)
4. **REQ-COMPAT-3** (CI version matrix) depends on Tier 1 REQ-TEST-* (CI infrastructure)
5. **REQ-DX-4** (non-retryable classification) depends on Tier 1 REQ-ERR-* (retryable classification)

**Recommendation:** Complete Tier 1 error handling, connection, and testing work BEFORE the dependent Tier 2 items.

### Quick Wins (P3, Effort S)

| Item | REQ | What to Do |
|---|---|---|
| Feature matrix document | REQ-API-2 | Create `clients/feature-matrix.md` with all features × 5 SDKs |
| Thread safety docstrings | REQ-CONC-1 | Add concurrency guarantees to all public type docstrings |
| Fix version consistency | REQ-PKG-2 | Single source of truth in `pyproject.toml`, read in `__init__.py` |
| CHANGELOG.md | REQ-PKG-4 | Create with v4.0.0-dev entries, retroactive v3.x entries |
| EOL policy | REQ-COMPAT-5 | Document in README: "previous major gets 12 months security patches" |
| Performance tips doc | REQ-PERF-6 | Add "Performance Tips" section to README or separate doc |
| Connection reuse docs | REQ-PERF-2 | Document single-client-per-app pattern |

### Features to Remove / EXCESS

| Feature | Location | Issue | Recommendation |
|---|---|---|---|
| `grpcio-tools` in runtime deps | `pyproject.toml` dependencies | Build-time tool in runtime deps increases install footprint | Move to `[project.optional-dependencies]` dev group |
| Legacy `common/exceptions.py` | `common/exceptions.py` | Deprecated exception classes still importable | Remove after deprecation period (2 minor versions / 6 months) |
| Legacy `*_async` thread-wrapped methods | Various clients | Three-tier async architecture is excessive | Remove after deprecation period; keep only sync + native async |

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
- **Backward compatibility:** Keep old method names as deprecated aliases. Use `warnings.warn("Use publish_event() instead", DeprecationWarning, stacklevel=2)`.
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
- **Python-specific notes:** Use `asyncio.wait()` with timeout for async callback tracking. For sync, use `threading.Event` with timeout. The 30s default should be a `close_timeout` parameter on `ClientConfig`.

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
- **Python-specific notes:** PEP 440 uses different pre-release syntax than SemVer: `4.0.0a1` (alpha), `4.0.0b1` (beta), `4.0.0rc1` (release candidate), `4.0.0.dev1` (dev). `pyproject.toml` and `__init__.py` should use PEP 440 format. The `hatchling` build backend supports `dynamic = ["version"]` with `[tool.hatch.version]` source configuration.

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
- **Complexity:** S (< 1 day for CHANGELOG; M if adopting full Conventional Commits)
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

- **What:** (1) Fix README to say "Python 3.9+" (or narrow to 3.11+ if desired). (2) Add CI matrix testing for Python 3.11, 3.12, 3.13 (GS-specified minimum). (3) Document in CONTRIBUTING.md that dropping a Python version requires a MAJOR version bump.
- **Complexity:** M (1-3 days) — primarily CI configuration
- **Dependencies:** Tier 1 REQ-TEST-* (CI pipeline must exist)
- **Risk:** Low — may discover compatibility issues in older Python versions
- **Breaking change:** If narrowing support from 3.9 to 3.11, this IS a breaking change requiring MAJOR bump. Recommendation: keep 3.9 for v4.x, narrow in v5.
- **Backward compatibility:** Continue supporting 3.9 in v4.x. CI matrix should include 3.9 as floor in addition to latest 3 releases.
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

**Overall Status: MISSING**

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

## Dependency Graph

```
Tier 1 (must complete first)
├── REQ-ERR-* (error classification, is_retryable)
│   ├── → REQ-DX-4 (validation errors non-retryable)
│   └── → REQ-CONC-2 (cancellation error types)
├── REQ-CONN-4 (graceful drain)
│   └── → REQ-CONC-5 (shutdown-callback safety)
├── REQ-TEST-* (CI pipeline, test infrastructure)
│   ├── → REQ-PKG-3 (tests in release pipeline)
│   └── → REQ-COMPAT-3 (CI version matrix)
│
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
```

## Implementation Sequence

### Wave 1: Quick Wins — No Dependencies (S, 1-3 days total)

| # | Item | REQ | Effort |
|---|---|---|---|
| 1 | Create feature matrix document | REQ-API-2 | S |
| 2 | Add thread safety docstrings | REQ-CONC-1 | S |
| 3 | Add callback behavior documentation | REQ-CONC-3 | S |
| 4 | Fix version consistency | REQ-PKG-2 | S |
| 5 | Create CHANGELOG.md | REQ-PKG-4 | S |
| 6 | Document EOL policy | REQ-COMPAT-5 | S |
| 7 | Write performance tips | REQ-PERF-6 | S |
| 8 | Document connection reuse | REQ-PERF-2 | S |
| 9 | Add `KubeMQNotImplementedError` | REQ-API-3 | S |

### Wave 2: Documentation & Metadata (S-M, 3-5 days)

| # | Item | REQ | Effort |
|---|---|---|---|
| 10 | Fix README Python version | REQ-COMPAT-3 | S |
| 11 | Complete PyPI metadata | REQ-PKG-1 | S |
| 12 | Add default address `localhost:50000` | REQ-DX-2 | S |
| 13 | Improve input validation | REQ-DX-4 | M |
| 14 | Make outbound messages immutable | REQ-DX-5 | S |
| 15 | Write performance documentation | REQ-PERF-5 | M |
| 16 | Deprecation policy + migration guide | REQ-COMPAT-2 | M |
| 17 | Create compatibility matrix | REQ-COMPAT-1 | M |

### Wave 3: Infrastructure (M-L, depends on Tier 1)

| # | Item | REQ | Effort | Depends On |
|---|---|---|---|---|
| 18 | Automated release pipeline | REQ-PKG-3 | M | Tier 1 CI |
| 19 | CI version matrix | REQ-COMPAT-3 | M | Tier 1 CI |
| 20 | Supply chain (Dependabot, pip-audit) | REQ-COMPAT-4 | M | REQ-PKG-3 |
| 21 | Cancellation improvements | REQ-CONC-2 | M | Tier 1 errors |
| 22 | Shutdown-callback safety | REQ-CONC-5 | M | Tier 1 drain |
| 23 | Callback concurrency control | REQ-CONC-3 | M | — |

### Wave 4: Major Items (L, depends on Waves 1-3)

| # | Item | REQ | Effort | Depends On |
|---|---|---|---|---|
| 24 | Comprehensive benchmarks | REQ-PERF-1 | L | — |
| 25 | Normalize method names (breaking) | REQ-DX-3 | L | REQ-API-2, next major |
| 26 | Serialize optimization | REQ-PERF-3 | S | REQ-PERF-1 |

## Effort Summary

| Effort | Count | Items |
|---|---|---|
| S (< 1 day) | 14 | REQ-API-2, REQ-API-3, REQ-DX-2, REQ-DX-5, REQ-CONC-1, REQ-PKG-1 (metadata), REQ-PKG-2, REQ-PKG-4, REQ-COMPAT-5, REQ-PERF-2, REQ-PERF-3, REQ-PERF-6, REQ-COMPAT-3 (README fix) |
| M (1-3 days) | 9 | REQ-DX-4, REQ-CONC-2, REQ-CONC-3, REQ-CONC-5, REQ-PKG-3, REQ-COMPAT-1, REQ-COMPAT-2, REQ-COMPAT-4, REQ-PERF-5 |
| L (3-5 days) | 3 | REQ-DX-3, REQ-PERF-1, REQ-COMPAT-3 (CI matrix) |
| XL (5+ days) | 0 | — |
| **Total estimated** | | **~25-35 days** |

## Cross-Category Dependencies

| From (Tier 2) | To (Tier 1/2) | Nature |
|---|---|---|
| REQ-DX-4 (fail-fast validation) | Tier 1 REQ-ERR-* | `is_retryable` property needed for validation error classification |
| REQ-CONC-2 (cancellation) | Tier 1 REQ-ERR-* | Error hierarchy must include cancellation error type |
| REQ-CONC-5 (shutdown safety) | Tier 1 REQ-CONN-4 | Drain concept must be designed at connection level first |
| REQ-PKG-3 (release pipeline) | Tier 1 REQ-TEST-* | CI must run tests before release pipeline can validate them |
| REQ-COMPAT-3 (version matrix CI) | Tier 1 REQ-TEST-* | CI infrastructure must exist before adding version matrix |
| REQ-COMPAT-4 (supply chain) | REQ-PKG-3 | SBOM generation runs as part of release pipeline |
| REQ-PKG-1 (includes CHANGELOG) | REQ-PKG-4 | CHANGELOG must exist before it can be included in package |
| REQ-PKG-3 (validate CHANGELOG) | REQ-PKG-4 | CHANGELOG must exist before CI can validate it |
| REQ-COMPAT-2 (deprecation CHANGELOG) | REQ-PKG-4 | CHANGELOG must exist before deprecations can be documented in it |
| REQ-DX-3 (verb normalization) | REQ-API-2 | Feature matrix needed for cross-SDK verb coordination |
| REQ-PERF-5 (perf docs) | REQ-PERF-1 | Benchmarks needed for accurate performance documentation |
| REQ-PERF-3 (serialization opt) | REQ-PERF-1 | Benchmarks needed to justify optimization work |

## GS Internal Inconsistencies

No internal inconsistencies were found between Tier 2 Golden Standard specifications. All cross-references (e.g., REQ-CONC-5 referencing REQ-CONN-4's drain timeout) are consistent.

**Note:** REQ-CONC-5 defines a 30-second callback completion timeout as separate from REQ-CONN-4's 5-second drain timeout. These are intentionally different timeouts for different purposes (callback completion vs. in-flight operation flushing).
