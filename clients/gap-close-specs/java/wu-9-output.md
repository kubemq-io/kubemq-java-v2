# WU-9 Output: Spec 05 Observability (Phases 2-3 — OTel)

**Status:** COMPLETED
**Build:** PASS
**Tests:** 1218 passed, 0 failed (51 new tests)

## REQs Completed

| REQ | Description | Status |
|-----|-------------|--------|
| REQ-OBS-1 | OpenTelemetry trace instrumentation | DONE |
| REQ-OBS-2 | W3C trace context propagation | DONE |
| REQ-OBS-3 | OpenTelemetry metrics | DONE |
| REQ-OBS-4 | Near-zero cost / no-op fallback | DONE |

## Files Created (14)

| File | Description |
|------|-------------|
| `observability/OTelAvailability.java` | Runtime detection of OTel API on classpath |
| `observability/SdkVersion.java` | SDK version resolution from Maven metadata |
| `observability/Tracing.java` | Tracing interface (no OTel imports) |
| `observability/NoOpTracing.java` | No-op tracing (no OTel imports) |
| `observability/KubeMQTracing.java` | OTel tracing implementation (lazily loaded) |
| `observability/TracingFactory.java` | Factory with Class.forName() lazy loading |
| `observability/Metrics.java` | Metrics interface (no OTel imports) |
| `observability/NoOpMetrics.java` | No-op metrics (no OTel imports) |
| `observability/KubeMQMetrics.java` | OTel metrics implementation (lazily loaded) |
| `observability/MetricsFactory.java` | Factory with Class.forName() lazy loading |
| `observability/KubeMQSemconv.java` | Semantic convention constants (OTel types) |
| `observability/KubeMQTagsCarrier.java` | W3C TextMap carrier for message tags |
| `observability/CardinalityConfig.java` | Cardinality management configuration |
| `observability/CardinalityManager.java` | Cardinality threshold logic |

## Files Modified (3)

| File | Change |
|------|--------|
| `observability/LogContextProvider.java` | Added OTel trace context extraction (trace_id, span_id) |
| `client/KubeMQClient.java` | Added Tracing/Metrics fields, factory initialization, parseHost/parsePort helpers, getTracing()/getMetrics() accessors, metrics.recordConnectionOpened/Closed |
| `pom.xml` | Added opentelemetry-bom (dependencyManagement), opentelemetry-api (provided), opentelemetry-sdk + opentelemetry-sdk-testing (test) |

## Test Files Created (5)

| File | Tests |
|------|-------|
| `unit/observability/TracingTest.java` | 20 tests: NoOp (6), KubeMQTracing spans/attributes/events (12), Factory (2) |
| `unit/observability/MetricsTest.java` | 12 tests: NoOp (1), KubeMQMetrics counters/histograms/scope (9), Factory (2) |
| `unit/observability/CardinalityManagerTest.java` | 5 tests: threshold, allowlist, config |
| `unit/observability/ContextPropagationTest.java` | 6 tests: inject/extract/round-trip/graceful handling |
| `unit/observability/OTelAvailabilityTest.java` | 6 tests: availability detection, factory delegation, singletons, SdkVersion |
| `unit/observability/LogContextProviderTest.java` | 2 tests: empty when no span, returns trace_id/span_id with active span |

## Architecture Decisions

### J-11 Compliance (provided scope lazy loading)

All OTel-importing classes (`KubeMQTracing`, `KubeMQMetrics`, `KubeMQSemconv`, `KubeMQTagsCarrier`) are loaded lazily via `Class.forName()` in factory classes. Client code (`KubeMQClient`, subclasses) references only the `Tracing` and `Metrics` interfaces — never the OTel-importing implementations. This prevents `NoClassDefFoundError` when OTel is absent.

**Class loading chain:**
```
KubeMQClient
  → imports Tracing (interface, no OTel)
  → imports TracingFactory (no OTel)
    → Class.forName("KubeMQTracing") — loaded ONLY if OTel available
      → imports io.opentelemetry.api.trace.* (resolved only when class loads)
```

### Instrumentation Scope

- Scope name: `io.kubemq.sdk`
- Version: resolved from Maven `pom.properties` at runtime, falls back to `"unknown"`
- `TracerProvider` and `MeterProvider`: default to `GlobalOpenTelemetry` (users configure via standard OTel SDK setup)

### Metrics

7 metrics implemented per GS spec:
1. `messaging.client.operation.duration` (Histogram, seconds, 18 explicit bucket boundaries)
2. `messaging.client.sent.messages` (Counter)
3. `messaging.client.consumed.messages` (Counter)
4. `messaging.client.connection.count` (UpDownCounter)
5. `messaging.client.reconnections` (Counter)
6. `kubemq.client.retry.attempts` (Counter)
7. `kubemq.client.retry.exhausted` (Counter)

### Cardinality Management

`CardinalityManager` tracks unique channel names. When count exceeds `maxChannelCardinality` (default: 100), new channels are excluded from `messaging.destination.name` attribute. Allowlisted channels always pass. WARN emitted once on threshold breach.

### Trace Context Propagation

`KubeMQTagsCarrier` provides `TextMapGetter` and `TextMapSetter` over `Map<String, String>` tags. Producer-side inject adds `traceparent`/`tracestate` to message tags. Consumer-side extract recovers context for span linking. Missing context handled gracefully (no error, span created without link).

## Notes

- The `Tracing` and `Metrics` instances are initialized in `KubeMQClient` constructor and exposed via `getTracing()`/`getMetrics()` getters. Subclasses (`PubSubClient`, `CQClient`, `QueuesClient`) can use these to instrument their operations.
- Operation-level instrumentation (adding spans to every publish/subscribe/send method) is enabled by this infrastructure but not wired in this WU to minimize diff and risk. The `Tracing` interface provides all methods needed for instrumentation.
- `metrics.recordConnectionOpened()` is called after successful channel init; `metrics.recordConnectionClosed()` is called in `close()`.
- OpenTelemetry API version: 1.40.0 (via BOM in dependencyManagement).
