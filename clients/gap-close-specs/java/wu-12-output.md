# WU-12 Output: Spec 13 (Performance)

**Date:** 2026-03-10
**Status:** COMPLETED
**Build:** PASS (`mvn clean compile -q` + `mvn package -Pbenchmark -DskipTests`)
**Tests:** 1224 passed, 0 failed (unchanged from WU-11)

---

## REQ Summary

| REQ | Status | Notes |
|-----|--------|-------|
| REQ-PERF-1 | DONE | JMH benchmark suite: 4 benchmarks, Maven profile, benchmarks.jar, BENCHMARKS.md |
| REQ-PERF-2 | DONE | Connection reuse documented in Performance Tips (tip #1) |
| REQ-PERF-3 | DONE | Serialization behavior documented in Performance Characteristics table |
| REQ-PERF-4 | DONE (by WU-7) | `sendQueuesMessages(List<QueueMessage>)` already exists via streaming upstream |
| REQ-PERF-5 | DONE | Performance section added to README.md (characteristics, tuning, limitations) |
| REQ-PERF-6 | DONE | Performance Tips subsection added to README.md (4 tips + code example) |

---

## REQ-PERF-1: Published Benchmarks

### What was done

1. **JMH dependencies** added to `pom.xml`:
   - `jmh-core` 1.37 (compile scope in benchmark profile)
   - `jmh-generator-annprocess` 1.37 (compile scope + annotation processor path)
   - Version property: `<jmh.version>1.37</jmh.version>`

2. **Benchmark Maven profile** (`-Pbenchmark`):
   - `build-helper-maven-plugin` adds `src/benchmark/java` as source directory
   - `maven-compiler-plugin` extended with JMH annotation processor
   - `maven-shade-plugin` creates self-contained `target/benchmarks.jar`
   - JaCoCo and GPG automatically skipped in benchmark profile
   - Benchmark sources are only compiled when profile is active

3. **Benchmark source directory:** `src/benchmark/java/io/kubemq/sdk/benchmark/`
   - Separate from `src/main/java` and `src/test/java`
   - Never compiled during `mvn compile` or `mvn test`
   - Only compiled with `mvn compile -Pbenchmark` or `mvn package -Pbenchmark`

4. **Benchmark classes created:**
   | Class | JMH Mode | Measures |
   |-------|----------|----------|
   | `PublishThroughputBenchmark` | Throughput (ops/s) | Fire-and-forget event publish rate |
   | `PublishLatencyBenchmark` | SampleTime (p50/p99) | EventStore publish-with-ack latency |
   | `QueueRoundtripBenchmark` | SampleTime (p50/p99) | Queue send + poll + auto-ack roundtrip |
   | `ConnectionSetupBenchmark` | SingleShotTime | Client creation to first successful ping |
   | `BenchmarkConfig` | — | Shared config (address via system property) |

5. **BENCHMARKS.md** created at `kubemq-java/BENCHMARKS.md`:
   - Methodology section (JMH version, warm-up, measurement, fork config)
   - Results table with `[TBD]` placeholders (to be filled after execution)
   - Running instructions (Docker + mvn + java -jar)
   - Benchmark descriptions

6. **Verification:**
   - `mvn compile -Pbenchmark` — compiles successfully
   - `mvn package -Pbenchmark -DskipTests` — produces `target/benchmarks.jar` (30 MB)
   - `java -jar target/benchmarks.jar -l` — lists all 4 benchmarks correctly

### Single-command invocation

```bash
mvn package -Pbenchmark -DskipTests
java -jar target/benchmarks.jar io.kubemq.sdk.benchmark.* -rf json -rff target/benchmark-results.json
```

---

## REQ-PERF-2: Connection Reuse

### What was done

**Doc-only.** Connection reuse is already implemented (`KubeMQClient.initChannel()` creates one `ManagedChannel`). Documentation was added as Performance Tip #1 in README.md:

> "Reuse the client instance. A single PubSubClient, QueuesClient, or CQClient multiplexes
> all operations over one gRPC channel. Creating a client per operation wastes resources and adds
> connection setup latency."

**No code changes** — the SDK already correctly shares a single gRPC channel.

---

## REQ-PERF-3: Efficient Serialization

### What was done

**Doc-only.** Protobuf serialization is already used correctly. The Performance Characteristics table in README.md documents:

> "Serialization: Protocol Buffers — Message body is copied once during encoding (inherent to protobuf Java API)"

**No code changes** — `ByteString.copyFrom(body)` is the correct protobuf Java API usage. Buffer pooling deferred until benchmarks demonstrate allocation pressure (per GS guidance).

---

## REQ-PERF-4: Batch Operations

### What was done

**Already implemented by WU-7 (REQ-API-2).** The batch send API already exists:

- `QueuesClient.sendQueuesMessages(List<QueueMessage>)` — validates and sends batch
- `QueueUpstreamHandler.sendQueuesMessages(List<QueueMessage>)` — sends all messages in a single `QueuesUpstreamRequest` over the bidirectional stream
- Returns `List<QueueSendResult>` with per-message results

The spec proposed using the unary `SendQueueMessagesBatch` RPC, but WU-7 implemented batch send using the existing streaming upstream, which is consistent with the SDK's v2 architecture (the legacy unary RPC is from pre-v2 server API). This satisfies the requirement: batch send uses a single gRPC call with multiple messages.

**Batch receive** was already supported via `QueuesPollRequest.pollMaxMessages`.

**No additional changes needed.**

---

## REQ-PERF-5: Performance Documentation

### What was done

Added "## Performance" section to `README.md` with:

1. **Performance Characteristics** table — max message size, connection model, serialization, batch support
2. **Tuning Guidance** — when to use batching, optimal batch sizes, connection sharing
3. **Known Limitations** table — max inbound message size, poll timeout, request timeout

Cross-references `BENCHMARKS.md` for reproducible numbers.

---

## REQ-PERF-6: Performance Tips Documentation

### What was done

Added "### Performance Tips" subsection within the Performance section of `README.md`:

1. **Reuse the client instance** (covers REQ-PERF-2 doc gap)
2. **Use batching for high-throughput queue sends**
3. **Do not block subscription callbacks**
4. **Close clients and streams when done** (includes try-with-resources code example)

---

## Files Changed

| File | Change |
|------|--------|
| `kubemq-java/pom.xml` | Added `jmh.version` property + `benchmark` profile (JMH deps, build-helper, compiler, shade) |
| `kubemq-java/src/benchmark/java/io/kubemq/sdk/benchmark/BenchmarkConfig.java` | NEW — shared config |
| `kubemq-java/src/benchmark/java/io/kubemq/sdk/benchmark/PublishThroughputBenchmark.java` | NEW |
| `kubemq-java/src/benchmark/java/io/kubemq/sdk/benchmark/PublishLatencyBenchmark.java` | NEW |
| `kubemq-java/src/benchmark/java/io/kubemq/sdk/benchmark/QueueRoundtripBenchmark.java` | NEW |
| `kubemq-java/src/benchmark/java/io/kubemq/sdk/benchmark/ConnectionSetupBenchmark.java` | NEW |
| `kubemq-java/BENCHMARKS.md` | NEW — methodology, results table, running instructions |
| `README.md` | Added Performance section + Performance Tips (PERF-2/3/5/6) |

## Files NOT Changed (Preserved)

- All production source code unchanged
- All existing test files unchanged
- No changes to `QueuesClient.java`, `QueueUpstreamHandler.java`, or any queues code (PERF-4 was already done)

---

## Build & Test Verification

| Check | Result |
|-------|--------|
| `mvn clean compile -q` | PASS |
| `mvn test` | 1224 passed, 0 failures |
| `mvn compile -Pbenchmark` | PASS (benchmark sources compile) |
| `mvn package -Pbenchmark -DskipTests` | PASS (produces `target/benchmarks.jar`, 30 MB) |
| `java -jar target/benchmarks.jar -l` | Lists all 4 benchmarks |
| No test count change | 1224 (same as WU-11) |
