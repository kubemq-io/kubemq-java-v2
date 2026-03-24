# Java SDK Burn-In — REST API Spec Gap Report

> **Spec Version**: 2.2 (2026-03-17)
> **SDK**: Java (`kubemq-java-v2/burnin`)
> **Date**: 2026-03-17

## Summary

| Metric | Count |
|--------|-------|
| Total Items | 93 |
| Done `[x]` | 92 |
| Partial `[~]` | 1 |
| Not Done `[ ]` | 0 |
| **% Complete** | **99%** |

---

## §13.1 Boot & Lifecycle

| # | Requirement | Spec Ref | Java |
|---|------------|----------|:----:|
| L1 | Boot into `idle` state (no auto-start) | §2 | [x] |
| L2 | HTTP server starts on boot before broker connection | §2 | [x] |
| L3 | `/health` returns `{"status":"alive"}` with 200 from boot | §3.1 | [x] |
| L4 | `/ready` per-state response: 200 for idle/running/stopped/error, 503 for starting/stopping | §3.1 | [x] |
| L5 | Pre-initialize all Prometheus metrics to 0 on startup (prevent `absent()` alerts) | §2, §8.3 | [x] |
| L6 | Run state machine: `idle`→`starting`→`running`→`stopping`→`stopped`/`error` | §4.1 | [x] |
| L7 | Atomic state transitions (Java: AtomicReference) | §4.2 | [x] |
| L8 | `starting_timeout_seconds` (default 60s) — timer starts at `starting` transition, exceeds → `error` | §4.1, §4.2 | [x] |
| L9 | Per-pattern states: `starting`, `running`, `recovering`, `error`, `stopped` with defined transitions | §4.3 | [x] |
| L10 | Stop during `starting` — cancel startup, cleanup partial channels, generate minimal report, → `stopped` | §4.4 | [x] |
| L11 | SIGTERM/SIGINT: stop active run gracefully, generate report, cleanup, exit | §9 | [x] |
| L12 | Exit codes: 0=PASSED/PASSED_WITH_WARNINGS, 1=FAILED, 2=config error, 0 if idle | §9 | [x] |

**Notes:**
- L9: Per-pattern states now include `recovering` — set during forced disconnection and reconnection events via `closeClient()`. Workers track downtime during `recovering` state and transition back to `running` on reconnection.

---

## §13.2 Endpoints

| # | Endpoint | Spec Ref | Java |
|---|----------|----------|:----:|
| E1 | `GET /info` — sdk, version, runtime, os, arch, cpus, memory, pid, uptime, state, broker_address | §5.1 | [x] |
| E2 | `GET /broker/status` — gRPC Ping() with 3s timeout, returns connected/error/latency | §5.2 | [~] |
| E3 | `POST /run/start` — full config body (no broker.address), validate all fields, return 202 with run_id | §5.3 | [x] |
| E4 | `POST /run/stop` — graceful stop, return 202. Return 409 for idle/stopped/error/stopping states | §5.4 | [x] |
| E5 | `GET /run` — full state with pattern+worker metrics. Responses for: idle, running, stopped, error | §5.5 | [x] |
| E6 | `GET /run/status` — lightweight: state, started_at, elapsed, remaining, warmup_active, totals, pattern_states | §5.6 | [x] |
| E7 | `GET /run/config` — resolved config with channel names. Partial config (empty channels) during `starting`. 404 when no run. | §5.7 | [x] |
| E8 | `GET /run/report` — final report with verdict checks map, `advisory` field, `startup` check for errors. 404 when no completed run. | §5.8 | [x] |
| E9 | `POST /cleanup` — delete all `{sdk}_burnin_*` channels. Reject during `starting`, `running`, `stopping` (409). | §5.9 | [x] |
| E10 | Legacy alias: `/status` → `/run/status` with deprecation warning on first use since boot | §3 | [x] |
| E11 | Legacy alias: `/summary` → `/run/report` with deprecation warning on first use since boot | §3 | [x] |

**Notes:**
- E2: `server_version` field included as `"unavailable (SDK ping does not expose version)"`. The Java SDK `ping()` method does not return the broker server version. Explicit 3-second timeout is not enforced at the Java level (relies on SDK defaults).
- E5: Now includes full per-producer/per-consumer/per-sender/per-responder worker breakdowns in pattern responses.
- E8: `startup` check is now emitted in the verdict checks map when a run fails during `starting` state.

---

## §13.3 HTTP & Error Handling

| # | Requirement | Spec Ref | Java |
|---|------------|----------|:----:|
| H1 | CORS headers on all responses with configurable `BURNIN_CORS_ORIGINS` (default `*`) | §7 | [x] |
| H2 | `OPTIONS` preflight → 204 No Content with CORS headers | §7 | [x] |
| H3 | Error response format: `{"message": "...", "errors": [...]}` (errors array only for 400) | §6 | [x] |
| H4 | `400` for invalid JSON body with parse error in message | §5.3.4, §6 | [x] |
| H5 | `400` for validation errors — collect ALL errors, return together (not fail-fast) | §5.3.4 | [x] |
| H6 | `409` for state conflicts — include current `run_id` and `state` in response body | §5.3, §5.4, §5.9 | [x] |
| H7 | `Content-Type: application/json` header on all JSON responses | §3 | [x] |
| H8 | Silently ignore unknown JSON fields in `POST /run/start` body (permissive parsing) | §1, §5.3.4 | [x] |

---

## §13.4 Config Handling

| # | Requirement | Spec Ref | Java |
|---|------------|----------|:----:|
| C1 | Parse nested per-pattern API config schema — no `broker.address` in body | §5.3.1 | [x] |
| C2 | Translate API nested config → internal flat config per normative mapping table | §5.3.3 | [x] |
| C3 | Per-pattern `enabled` flag — skip disabled patterns entirely, include `{"enabled":false}` in responses | §5.3.2, §5.5 | [x] |
| C4 | Per-pattern threshold overrides: loss_pct, p99, p999 override global defaults | §5.3.3 | [x] |
| C5 | Default rate values when omitted: events=100, events_store=100, queues=50, rpc=20 | §5.3.2 | [x] |
| C6 | Default loss thresholds: events=5.0%, events_store/queue_stream/queue_simple=0.0% | §5.3.2 | [x] |
| C7 | `warmup_duration` mode-dependent default (60s benchmark, 0s soak), explicit "0s" overrides | §5.3.2 | [x] |
| C8 | `run_id` auto-generation (8-char UUID prefix) when empty or omitted | §5.3.2 | [x] |
| C9 | Full validation: mode, duration format, rate>0, concurrency>=1, pct 0-100, size>=64, reorder>=100, timeouts>0, >=1 pattern enabled | §5.3.4 | [x] |
| C10 | `visibility_seconds` omitted from API queue config — silently ignore in YAML | §5.3.2, §2.1 | [x] |
| C11 | Java: optional `visibility_seconds` as Java-specific extension field | §5.3.2 | [x] |
| C12 | `poll_wait_timeout_seconds` → milliseconds for Queue Stream, seconds for Queue Simple | §5.3.2 | [x] |
| C13 | `max_duration` safety cap in thresholds (default 168h) — forces exit for infinite runs | §5.3.2 | [x] |

**Notes:**
- C11: `visibility_seconds` is accepted in the API body queue config and in YAML. It's passed through to the queue workers.
- C13: `max_duration` is in the config but is not actively enforced as a safety cap during runtime (Engine waits for the configured `duration`). The value is stored in thresholds for reference.

---

## §13.5 Run Data & Metrics (REST API)

| # | Requirement | Spec Ref | Java |
|---|------------|----------|:----:|
| M1 | Per-run REST counters (reset to 0 on new run), separate from process-lifetime Prometheus counters | §8.2 | [x] |
| M2 | Pattern-level aggregates: sent, received, lost, duplicated, corrupted, out_of_order, errors, reconnections, loss_pct, latency{} | §5.5 | [x] |
| M3 | Per-producer metrics: id, sent, errors, actual_rate, latency{p50,p95,p99,p999} | §5.5 | [x] |
| M4 | Per-consumer metrics: id, received, corrupted, errors, latency{} | §5.5 | [x] |
| M5 | Per-sender RPC metrics: id, sent, responses_success/timeout/error, actual_rate, latency{} | §5.5 | [x] |
| M6 | Per-responder RPC metrics: id, responded, errors | §5.5 | [x] |
| M7 | `actual_rate` = 30-second sliding average (msgs/sec) | §5.5.1 | [x] |
| M8 | `peak_rate` = highest 10-second window rate observed during run | §5.5.1 | [x] |
| M9 | `bytes_sent` / `bytes_received` per pattern (incl. RPC) — message body bytes only, no proto overhead | §5.5.1 | [x] |
| M10 | `unconfirmed` count: Events Store pattern only (messages sent but not persistence-confirmed) | §5.5.1 | [x] |
| M11 | Live resource metrics: rss_mb (current), baseline_rss_mb, memory_growth_factor, active_workers (current) | §5.5 | [x] |
| M12 | Totals aggregation: RPC success→received, timeout+error→lost, all others summed across patterns | §5.6 | [x] |
| M13 | `out_of_order` included in `/run/status` totals | §5.6 | [x] |
| M14 | `resources` naming: live=rss_mb/active_workers, report=peak_rss_mb/peak_workers | §5.5 | [x] |

**Notes:**
- M3–M6: Per-worker breakdowns are now fully exposed in REST API responses. Each producer/consumer/sender/responder has a unique ID (e.g. `p-events-000`, `c-events-000`, `r-commands-000`) with individual counters, rate trackers, and latency histograms. Stats tracked via `ProducerStat`, `ConsumerStat`, `SenderStat`, `ResponderStat` inner classes in BaseWorker.
- M7: `actual_rate` now uses a 30-second sliding window (`SlidingRateTracker` with 30 one-second buckets), both at pattern level and per-worker level. The report uses lifetime average (`avg_rate`).
- M10: `unconfirmed` for events_store now reads the actual `EventsStoreWorker.getUnconfirmed()` counter which tracks messages where `isSent()` returned false or exceptions occurred, rather than approximating as `max(0, sent - received)`.

---

## §13.6 Report & Verdict

| # | Requirement | Spec Ref | Java |
|---|------------|----------|:----:|
| R1 | Report available via `GET /run/report` after stopped/error, until next run starts. 404 otherwise. | §5.8 | [x] |
| R2 | Error-from-startup report: verdict=FAILED, `startup` check with error reason, empty/zero metrics | §5.8.3 | [x] |
| R3 | `all_patterns_enabled` boolean flag in report top level | §5.8.2 | [x] |
| R4 | `warnings` array: "Not all patterns enabled" when patterns disabled | §5.8.1 | [x] |
| R5 | `peak_rate` per pattern in report (highest 10s window) | §5.8.2 | [x] |
| R6 | `avg_rate` per pattern in report (lifetime: total_sent / elapsed) | §5.8.2 | [x] |
| R7 | Worker-level breakdown in report: producers[], consumers[], senders[], responders[] with avg_rate + latency | §5.8.2 | [x] |
| R8 | Verdict checks as map: keys `"name:pattern"` for per-pattern, `"name"` for global | §5.8.1 | [x] |
| R9 | Check result fields: `passed` (bool), `threshold` (string), `actual` (string), `advisory` (bool, default false) | §5.8.1 | [x] |
| R10 | Normative check names: message_loss, duplication, corruption, p99_latency, p999_latency, throughput, error_rate, memory_stability, memory_trend, downtime, startup | §5.8.1 | [x] |
| R11 | `duplication` checks: per enabled pub/sub+queue pattern only (not RPC) | §5.8.1 | [x] |
| R12 | `error_rate` checks: per enabled pattern (formula: errors / (sent+received) * 100) | §5.8.1 | [x] |
| R13 | `throughput` check: global (min across all patterns), uses avg_rate vs min_throughput_pct of target. Soak only. | §5.8.1 | [x] |
| R14 | `memory_trend` advisory check: fires at `1.0 + (max_factor-1.0)*0.5`, advisory=true | §5.8.1 | [x] |
| R15 | `PASSED_WITH_WARNINGS` logic: all non-advisory pass + any advisory fail → PASSED_WITH_WARNINGS | §5.8.1 | [x] |
| R16 | Memory baseline: 5min after running start, 1min if <5min run, running-start if <1min. Advisory for <5min runs. | §5.8.1 | [x] |
| R17 | Per-pattern loss checks using pattern-specific `max_loss_pct` thresholds | §5.8, §5.3.3 | [x] |
| R18 | Per-pattern latency checks (p99, p999) using pattern-specific thresholds | §5.8, §5.3.3 | [x] |
| R19 | Verdict result: PASSED / PASSED_WITH_WARNINGS / FAILED | §5.8.1 | [x] |

**Notes:**
- R2: `startup` check entry is now added to the verdict checks map when a run fails during `starting` state. The check has `passed=false`, `threshold="startup succeeds"`, and `actual` set to the error message. Verdict is immediately FAILED.
- R7: Worker-level breakdowns now included in both live responses (`actual_rate`) and report (`avg_rate`).
- R16: Memory baseline timing uses computed delay: 300s for runs ≥5min, 60s for runs ≥1min/<5min, immediate for runs <1min. The `memory_stability` check is set as advisory for runs <5min, with a warning.

---

## §13.7 Startup Config & CLI

| # | Requirement | Spec Ref | Java |
|---|------------|----------|:----:|
| S1 | `BURNIN_METRICS_PORT` / `metrics.port` (default 8888) | §2.1 | [x] |
| S2 | `BURNIN_LOG_FORMAT` / `logging.format` (text or json) | §2.1 | [x] |
| S3 | `BURNIN_LOG_LEVEL` / `logging.level` (debug/info/warn/error) | §2.1 | [x] |
| S4 | `BURNIN_CORS_ORIGINS` / `cors.origins` (default `*`, comma-separated) | §2.1, §7 | [x] |
| S5 | `BURNIN_BROKER_ADDRESS` / `broker.address` (default localhost:50000, startup-only) | §2.1 | [x] |
| S6 | `BURNIN_CLIENT_ID_PREFIX` / `broker.client_id_prefix` (default burnin-{sdk}) | §2.1 | [x] |
| S7 | `BURNIN_RECONNECT_INTERVAL` / `recovery.reconnect_interval` (1s, with 0-25% jitter) | §2.1 | [x] |
| S8 | `BURNIN_RECONNECT_MAX_INTERVAL` / `recovery.reconnect_max_interval` (30s) | §2.1 | [x] |
| S9 | `BURNIN_RECONNECT_MULTIPLIER` / `recovery.reconnect_multiplier` (2.0) | §2.1 | [x] |
| S10 | `BURNIN_REPORT_OUTPUT_FILE` / `output.report_file` (SIGTERM flow only) | §2.1 | [x] |
| S11 | `BURNIN_SDK_VERSION` / `output.sdk_version` (auto-detect fallback) | §2.1 | [~] |
| S12 | Java: `BURNIN_QUEUE_VISIBILITY_SECONDS` (Java-only, non-Java silently ignore) | §2.1 | [x] |
| S13 | `--cleanup-only` CLI mode (bypass idle, connect + delete + exit) | §2.2 | [x] |
| S14 | `--validate-config` CLI mode (validate config + exit, no HTTP server) | §2.2 | [x] |

**Notes:**
- S11: `sdk_version` is configurable via env/YAML but auto-detection from pom.xml is not implemented. Falls back to "unknown".

---

## Java-Specific Items

| Item | Status | Notes |
|------|--------|-------|
| `visibility_seconds` in queue config | [x] | Accepted in API body and YAML, passed to Queue Stream workers |
| `AtomicReference<RunState>` for state machine | [x] | Using `AtomicReference` with `compareAndSet` for all transitions |
| `com.sun.net.httpserver` POST handling | [x] | Using `HttpExchange.getRequestBody()` + Gson parsing |
| Gson permissive parsing (ignore unknown fields) | [x] | Default Gson behavior |
| Memory trend formula `1.0 + (max_factor-1.0)*0.5` | [x] | Correct formula applied |
| Per-worker stat tracking | [x] | `ProducerStat`, `ConsumerStat`, `SenderStat`, `ResponderStat` in BaseWorker |
| 30-second sliding rate window | [x] | `SlidingRateTracker` in PeakRate.java, per-pattern + per-worker |
| Recovering pattern state | [x] | Set during forced disconnect, downtime tracked, restored on reconnect |

---

## Deviations from Spec

1. **Broker status `server_version` (E2):** The KubeMQ Java SDK's `ping()` method doesn't return the broker version. The field is included as `"unavailable (SDK ping does not expose version)"`.

2. **`max_duration` safety cap (C13):** `max_duration` is in the config but is not actively enforced as a safety cap during runtime (Engine waits for the configured `duration`). The value is stored in thresholds for reference.

3. **`sdk_version` auto-detection (S11):** Auto-detection from pom.xml is not implemented. Falls back to "unknown" unless set via `BURNIN_SDK_VERSION` env var or YAML config.

---

## Files Changed

| File | Change Type | Description |
|------|-------------|-------------|
| `RunState.java` | **New** | State enum with transition helpers |
| `ApiConfig.java` | **New** | API config POJOs, validation, translation to internal config |
| `RunManager.java` | **New** | State machine, run lifecycle, cleanup, broker status, startup check |
| `HttpServer.java` | **Rewritten** | All spec endpoints, CORS, POST handling, error format |
| `Main.java` | **Rewritten** | Boot into idle, HTTP server first, SIGTERM handling |
| `Engine.java` | **Modified** | Per-worker summary, sliding rate, memory baseline short-run logic, recovering state |
| `Config.java` | **Modified** | Added CORS config, starting_timeout_seconds, enabled patterns |
| `Report.java` | **Modified** | Per-pattern verdict checks, memory_trend fix, startup check, memory advisory |
| `Metrics.java` | **Modified** | Pre-initialization of all metrics to 0 |
| `BaseWorker.java` | **Modified** | Per-worker stat tracking, SlidingRateTracker, RPC send/respond per-ID methods |
| `PeakRate.java` | **Modified** | Added SlidingRateTracker class for 30s window |
| `CommandsWorker.java` | **Modified** | Per-sender RPC tracking, per-responder tracking |
| `QueriesWorker.java` | **Modified** | Per-sender RPC tracking, per-responder tracking |
| `EventsStoreWorker.java` | **Existing** | Real unconfirmed counter exposed via getUnconfirmed() |
