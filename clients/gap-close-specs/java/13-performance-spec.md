# Implementation Specification: Category 13 -- Performance

**SDK:** KubeMQ Java v2
**Category:** 13 -- Performance
**GS Source:** `clients/golden-standard/13-performance.md`
**Gap Research:** `clients/gap-research/java-gap-research.md` (lines 1686-1788)
**Assessment:** `clients/assesments/JAVA_ASSESSMENT_REPORT.md` (Category 13, lines 697-723)
**Current Score:** 2.10 / 5.0 | **Target:** 4.0+
**Tier:** 2 (Should-have, 4% weight)
**Total Estimated Effort:** 5-9 days
**Date:** 2026-03-09

---

## Table of Contents

1. [Summary of REQ Items](#1-summary-of-req-items)
2. [Implementation Order](#2-implementation-order)
3. [REQ-PERF-1: Published Benchmarks](#3-req-perf-1-published-benchmarks)
4. [REQ-PERF-2: Connection Reuse](#4-req-perf-2-connection-reuse)
5. [REQ-PERF-3: Efficient Serialization](#5-req-perf-3-efficient-serialization)
6. [REQ-PERF-4: Batch Operations](#6-req-perf-4-batch-operations)
7. [REQ-PERF-5: Performance Documentation](#7-req-perf-5-performance-documentation)
8. [REQ-PERF-6: Performance Tips Documentation](#8-req-perf-6-performance-tips-documentation)
9. [Cross-Category Dependencies](#9-cross-category-dependencies)
10. [Breaking Changes](#10-breaking-changes)
11. [Open Questions](#11-open-questions)

---

## 1. Summary of REQ Items

| REQ | Status | Gap | Effort | Priority | Impl Order |
|-----|--------|-----|--------|----------|------------|
| REQ-PERF-1 | MISSING | Full benchmark suite needed | M-L (2-4 days) | P2 | 1 |
| REQ-PERF-2 | COMPLIANT | Doc-only gap (one criterion) | S (< 0.5 day) | P3 | 5 |
| REQ-PERF-3 | COMPLIANT | Doc-only gap (note on body copy) | S (< 0.5 day) | P3 | 6 |
| REQ-PERF-4 | PARTIAL | Batch send missing | M (1-2 days) | P2 | 2 |
| REQ-PERF-5 | MISSING | Performance docs needed | S (0.5-1 day) | P2 | 3 |
| REQ-PERF-6 | MISSING | Performance tips needed | S (0.5 day) | P2 | 4 |

**Total estimated effort:** 5-9 days

---

## 2. Implementation Order

```
REQ-PERF-4 (batch send)  ─┐
                           ├──> REQ-PERF-1 (benchmarks, needs batch send to benchmark)
REQ-PERF-2 (confirm)     ─┘        │
REQ-PERF-3 (confirm)               │
                                    ├──> REQ-PERF-5 (perf docs, uses benchmark results)
                                    └──> REQ-PERF-6 (perf tips, references batch send)
```

**Rationale:** Batch send (PERF-4) should be implemented before benchmarks (PERF-1) so benchmarks can include batch throughput measurements. Documentation (PERF-5, PERF-6) comes last because it references benchmark results and batch API.

---

## 3. REQ-PERF-1: Published Benchmarks

**Status:** MISSING
**Effort:** M-L (2-4 days)
**Priority:** P2

### 3.1 Current State

Assessment 13.1.1 through 13.1.4 all score 1. No benchmarks exist in the repository. No JMH dependencies. No benchmark Maven profile. No published performance numbers or methodology documentation.

### 3.2 Acceptance Criteria

| # | Criterion | GS Ref | Status |
|---|-----------|--------|--------|
| 1 | Benchmarks exist in the SDK repo (language-native benchmark framework) | REQ-PERF-1 AC-1 | MISSING |
| 2 | Benchmarks are runnable with a single command | REQ-PERF-1 AC-2 | MISSING |
| 3 | Results are documented in the repo (baseline numbers) | REQ-PERF-1 AC-3 | MISSING |
| 4 | Benchmark methodology is documented (hardware, server config, message count) | REQ-PERF-1 AC-4 | MISSING |

### 3.3 Framework Decision: JMH vs Timing-Based

The GS states: "JMH preferred; simpler timing-based benchmark acceptable if reproducible." (Per review R1 M-13.)

**Decision: Use JMH.** Rationale:
- JMH is the industry standard for Java benchmarks (created by the OpenJDK team)
- JMH handles JVM warm-up, dead code elimination, and measurement stabilization automatically
- JMH produces reliable p50/p99 latency numbers via `@BenchmarkMode(Mode.SampleTime)`
- The incremental cost over timing-based benchmarks is primarily Maven dependency setup (one-time)
- If JMH proves too heavyweight for CI, a simpler timing-based fallback can be added later

**Alternative accepted:** If integrating JMH's annotation processor into the build proves problematic, a timing-based approach using `System.nanoTime()` with manual warm-up loops is acceptable. The key requirement is reproducibility: documented warm-up iterations, measurement iterations, and fork count.

### 3.4 Implementation

#### 3.4.1 Maven Dependencies (test scope)

Add to `kubemq-java/pom.xml`:

```xml
<properties>
    <jmh.version>1.37</jmh.version>
</properties>

<dependencies>
    <!-- JMH benchmark framework (test scope) -->
    <dependency>
        <groupId>org.openjdk.jmh</groupId>
        <artifactId>jmh-core</artifactId>
        <version>${jmh.version}</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.openjdk.jmh</groupId>
        <artifactId>jmh-generator-annprocess</artifactId>
        <version>${jmh.version}</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

#### 3.4.2 Maven Profile

Add a `benchmark` Maven profile to `kubemq-java/pom.xml`. **Note:** JMH requires annotation processing at compile time to generate benchmark handler classes and a `META-INF/BenchmarkList` file. The `exec-maven-plugin` approach does not handle this correctly. Use `maven-shade-plugin` to create a self-contained `benchmarks.jar` (the standard JMH approach):

```xml
<profiles>
    <profile>
        <id>benchmark</id>
        <build>
            <plugins>
                <!-- JMH requires a shaded uber-jar with META-INF/BenchmarkList -->
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-shade-plugin</artifactId>
                    <version>3.5.1</version>
                    <executions>
                        <execution>
                            <id>shade-benchmarks</id>
                            <phase>package</phase>
                            <goals>
                                <goal>shade</goal>
                            </goals>
                            <configuration>
                                <finalName>benchmarks</finalName>
                                <transformers>
                                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                        <mainClass>org.openjdk.jmh.Main</mainClass>
                                    </transformer>
                                    <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                                </transformers>
                                <filters>
                                    <filter>
                                        <artifact>*:*</artifact>
                                        <excludes>
                                            <exclude>META-INF/*.SF</exclude>
                                            <exclude>META-INF/*.DSA</exclude>
                                            <exclude>META-INF/*.RSA</exclude>
                                        </excludes>
                                    </filter>
                                </filters>
                            </configuration>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

**Single-command invocation:**

```bash
# Build the benchmark jar
mvn package -Pbenchmark -DskipTests

# Run all benchmarks (requires running KubeMQ server)
java -jar target/benchmarks.jar io.kubemq.sdk.benchmark.* -rf json -rff target/benchmark-results.json

# Or run a specific benchmark
java -jar target/benchmarks.jar PublishThroughputBenchmark
```

**Developer setup for benchmark execution:**
- **Docker:** `docker run -d -p 50000:50000 kubemq/kubemq:latest`
- Benchmarks are excluded from `mvn test` by default (only built with `-Pbenchmark`)
- Benchmarks should never run accidentally in CI; the `benchmark` profile is opt-in only

#### 3.4.3 Benchmark Directory Structure

```
kubemq-java/src/test/java/io/kubemq/sdk/benchmark/
    PublishThroughputBenchmark.java
    PublishLatencyBenchmark.java
    QueueRoundtripBenchmark.java
    ConnectionSetupBenchmark.java
    BenchmarkConfig.java
```

#### 3.4.4 Required Benchmarks

**Benchmark 1: PublishThroughputBenchmark**

```java
package io.kubemq.sdk.benchmark;

import io.kubemq.sdk.pubsub.EventMessage;
import io.kubemq.sdk.pubsub.PubSubClient;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class PublishThroughputBenchmark {

    private PubSubClient client;
    private EventMessage message;

    @Param({"1024"})  // 1KB payload; SHOULD also test 64, 65536
    private int payloadSize;

    @Setup(Level.Trial)
    public void setup() {
        client = PubSubClient.builder()
                .address(BenchmarkConfig.getAddress())
                .clientId("bench-throughput")
                .build();

        // Pre-allocate payload in @Setup to avoid allocation noise in the benchmark loop
        byte[] payload = new byte[payloadSize];
        message = EventMessage.builder()
                .channel("bench-throughput")
                .body(payload)
                .build();
    }

    @Benchmark
    public void publishEvent(Blackhole bh) {
        // sendEventsMessage is void (fire-and-forget). Use Blackhole.consume()
        // to prevent JIT dead-code elimination of the void call.
        client.sendEventsMessage(message);
        bh.consume(true);
    }

    @TearDown(Level.Trial)
    public void teardown() {
        client.close();
    }
}
```

**Benchmark 2: PublishLatencyBenchmark**

```java
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class PublishLatencyBenchmark {
    // Measures p50, p99 for EventStore publish (has ack, so latency is meaningful)
    // Uses EventStoreMessage.sendEventsStoreMessage() which returns EventSendResult

    private PubSubClient client;
    private EventStoreMessage message;

    @Setup(Level.Trial)
    public void setup() { /* ... build client, create 1KB EventStoreMessage ... */ }

    @Benchmark
    public EventSendResult publishEventStore() {
        return client.sendEventsStoreMessage(message);
    }

    @TearDown(Level.Trial)
    public void teardown() { client.close(); }
}
```

**Benchmark 3: QueueRoundtripBenchmark**

```java
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class QueueRoundtripBenchmark {
    // Measures send + receive + ack latency for a single 1KB queue message
    // Uses QueuesClient.sendQueuesMessage() then receiveQueuesMessages()

    private QueuesClient client;
    private static final String CHANNEL = "bench-queue-roundtrip";

    @Setup(Level.Trial)
    public void setup() { /* ... build client, create channel ... */ }

    @Benchmark
    public QueuesPollResponse roundtrip() {
        QueueMessage msg = QueueMessage.builder()
                .channel(CHANNEL)
                .body(new byte[1024])
                .build();
        client.sendQueuesMessage(msg);

        QueuesPollRequest poll = QueuesPollRequest.builder()
                .channel(CHANNEL)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(5)
                .autoAckMessages(true)
                .build();
        return client.receiveQueuesMessages(poll);
    }

    @TearDown(Level.Trial)
    public void teardown() { client.close(); }
}
```

**Benchmark 4: ConnectionSetupBenchmark**

```java
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Measurement(iterations = 20)
@Fork(1)
public class ConnectionSetupBenchmark {
    // Measures time from client creation to first successful ping

    @Benchmark
    public ServerInfo connectAndPing() {
        try (PubSubClient client = PubSubClient.builder()
                .address(BenchmarkConfig.getAddress())
                .clientId("bench-connect")
                .build()) {
            return client.ping();
        }
    }
}
```

#### 3.4.5 BenchmarkConfig

```java
package io.kubemq.sdk.benchmark;

/**
 * Shared configuration for benchmarks.
 * Server address can be overridden via system property.
 */
public final class BenchmarkConfig {
    private BenchmarkConfig() {}

    public static String getAddress() {
        return System.getProperty("kubemq.address", "localhost:50000");
    }
}
```

#### 3.4.6 SHOULD Benchmarks (Recommended, Not Required)

These are recommended by the GS but not required for compliance:

| Benchmark | Description |
|-----------|-------------|
| Multi-payload matrix | Add `@Param({"64", "1024", "65536"})` to `PublishThroughputBenchmark.payloadSize` |
| Concurrent publishers | Parameterize `@Threads({1, 10, 100})` on `PublishThroughputBenchmark` |
| Subscribe throughput | Measure messages/sec received via subscription callback |
| Batch send throughput | Measure `sendQueuesMessages(List<QueueMessage>)` msgs/sec (after PERF-4) |

#### 3.4.7 Benchmark Results Documentation

Create `kubemq-java/BENCHMARKS.md`:

```markdown
# KubeMQ Java SDK Benchmarks

## Methodology

- **Framework:** JMH 1.37
- **JDK:** OpenJDK 17.x
- **Hardware:** [CPU model], [RAM], [OS]
- **KubeMQ Server:** [version], single node, default configuration
- **Payload:** 1KB (1024 bytes) unless otherwise noted
- **Warm-up:** 3 iterations x 5 seconds
- **Measurement:** 5 iterations x 10 seconds
- **Forks:** 1

## Results

| Benchmark | Metric | Value | Unit |
|-----------|--------|-------|------|
| Publish throughput (1KB) | Throughput | [TBD] | msgs/sec |
| Publish latency (1KB) | p50 | [TBD] | us |
| Publish latency (1KB) | p99 | [TBD] | us |
| Queue roundtrip (1KB) | p50 | [TBD] | ms |
| Queue roundtrip (1KB) | p99 | [TBD] | ms |
| Connection setup | Mean | [TBD] | ms |

## Running Benchmarks

```bash
# Requires a running KubeMQ server
# Start KubeMQ via Docker: docker run -d -p 50000:50000 kubemq/kubemq:latest

# Build the benchmark jar
mvn package -Pbenchmark -DskipTests

# Run all benchmarks
java -jar target/benchmarks.jar io.kubemq.sdk.benchmark.* -rf json -rff target/benchmark-results.json

# Custom server address
java -jar target/benchmarks.jar -Dkubemq.address=myhost:50000
```

## Notes

- Results vary by hardware and network. These numbers are baselines for comparison.
- Raw JMH JSON output is written to `target/benchmark-results.json`.
```

**Important:** The `[TBD]` values must be populated after running the benchmarks. The spec does not prescribe specific numbers -- they are empirical.

### 3.5 Verification

| # | Check | How to Verify |
|---|-------|---------------|
| 1 | Benchmarks compile | `mvn package -Pbenchmark -DskipTests` succeeds and produces `target/benchmarks.jar` |
| 2 | Benchmarks run | `java -jar target/benchmarks.jar` produces output (requires running KubeMQ server) |
| 3 | Results file | `target/benchmark-results.json` is created after run |
| 4 | BENCHMARKS.md | File exists with methodology section and baseline numbers |
| 5 | p50/p99 latency | JMH `SampleTime` mode produces percentile breakdown |

---

## 4. REQ-PERF-2: Connection Reuse

**Status:** COMPLIANT (documentation gap only)
**Effort:** S (< 0.5 day)
**Priority:** P3

### 4.1 Current State

Assessment 13.2.6 (score 4) confirms a single `ManagedChannel` is shared across all operations. The `KubeMQClient` class (`kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`) creates one `ManagedChannel` in `initChannel()` and creates blocking and async stubs from it once. All subclasses (`PubSubClient`, `QueuesClient`, `CQClient`) inherit this shared channel.

gRPC HTTP/2 multiplexing ensures concurrent operations share the same TCP connection.

### 4.2 Acceptance Criteria

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | Single Client uses one long-lived gRPC channel | COMPLIANT | `KubeMQClient.initChannel()` creates one `ManagedChannel` stored as instance field |
| 2 | Multiple concurrent operations multiplex over same channel | COMPLIANT | gRPC HTTP/2 multiplexing inherent in `ManagedChannel` |
| 3 | Documentation advises against Client-per-operation | MISSING | No documentation on this pattern |
| 4 | No per-operation connection overhead | COMPLIANT | Stubs created once in constructor; no per-call channel creation |

### 4.3 Implementation

**One doc-only change:** Add connection reuse guidance to README (combined with PERF-5/PERF-6 documentation below). The specific text is:

> **Reuse the client instance.** A single `PubSubClient`, `QueuesClient`, or `CQClient` instance uses one gRPC channel that multiplexes all operations. Do not create a new client per operation -- this wastes resources and adds connection setup overhead.

This sentence will be placed in the Performance Tips section specified under REQ-PERF-6.

### 4.4 Verification

| # | Check | How to Verify |
|---|-------|---------------|
| 1 | Documentation exists | README contains connection reuse guidance |
| 2 | No code changes | Source code unchanged for this REQ |

---

## 5. REQ-PERF-3: Efficient Serialization

**Status:** COMPLIANT (documentation note only)
**Effort:** S (< 0.5 day)
**Priority:** P3

### 5.1 Current State

- **Protobuf standard runtime:** The SDK uses `protobuf-java` 4.28.2 (assessment 8.4.5, score 5). All message encoding uses generated protobuf builders. No custom serialization.
- **Body copy:** `ByteString.copyFrom(body)` in `QueueMessage.encodeMessage()` (line 128), `EventMessage.encode()` (line 79), and similar methods creates a copy of the `byte[]` body. This is inherent to the protobuf Java API -- `ByteString` is immutable and always copies. The alternative `ByteString.copyFrom(ByteBuffer)` also copies. True zero-copy requires `UnsafeByteOperations.unsafeWrap()` which is internal API and not recommended.
- **Buffer pooling:** No buffer pooling exists (assessment 13.2.1, score 1). Per the GS: "Buffer pooling is recommended only when benchmarks demonstrate allocation pressure." Since no benchmarks exist yet, the absence of buffer pooling is **correct behavior** -- not a gap.

### 5.2 Acceptance Criteria

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | Protobuf serialization uses standard runtime | COMPLIANT | `protobuf-java` 4.28.2; all encode/decode methods use generated builders |
| 2 | Avoid unnecessary memory copies of message bodies | COMPLIANT | One copy per message is unavoidable with protobuf Java API. No additional unnecessary copies. |
| 3 | Buffer pooling recommended only when benchmarks show allocation pressure | COMPLIANT | No buffer pooling, no benchmarks -- correct per GS. Re-evaluate after REQ-PERF-1 benchmarks exist. (Per review R2 m-4.) |

### 5.3 Implementation

**Doc-only:** Add a note to the performance documentation (PERF-5) explaining the body copy behavior:

> **Serialization:** Messages are serialized using Protocol Buffers. The message body (`byte[]`) is copied once during protobuf encoding -- this is inherent to the protobuf Java API. For very large messages (approaching the 100 MB default `maxReceiveSize`), be aware of this allocation. Buffer pooling may be added in a future release if benchmarks demonstrate allocation pressure.

### 5.4 Verification

| # | Check | How to Verify |
|---|-------|---------------|
| 1 | No buffer pooling added prematurely | Grep for `ByteBuffer.allocate` or `pool` -- should find none |
| 2 | Documentation note exists | Performance docs reference protobuf body copy |

---

## 6. REQ-PERF-4: Batch Operations

**Status:** PARTIAL
**Effort:** M (1-2 days)
**Priority:** P2

### 6.1 Current State

- **Batch receive:** EXISTS. `QueuesPollRequest.pollMaxMessages` supports retrieving multiple messages in a single gRPC call. The downstream handler sends one `QueuesDownstreamRequest` with `maxItems` set, and receives multiple messages in the response. This is a single-call batch.
- **Batch send:** MISSING. `QueuesClient.sendQueuesMessage()` accepts a single `QueueMessage`. The upstream handler wraps it in a `QueuesUpstreamRequest` with one message (see `QueueMessage.encode()`, line 112: `pbQueueStreamBuilder.addMessages(pbMessage)` -- singular). However, the server proto **does** support batch send via `SendQueueMessagesBatch` RPC (defined in `kubemq.proto` line 11), which accepts `QueueMessagesBatchRequest` containing `repeated QueueMessage Messages`.

### 6.2 Acceptance Criteria

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | Batch operations use a single gRPC call (not N individual calls) | PARTIAL | Receive: single call via `QueuesDownstreamRequest.maxItems`. Send: N/A (no batch send) |
| 2 | Batch size is configurable | PARTIAL | Receive: `pollMaxMessages` is configurable. Send: N/A |

### 6.3 Implementation

#### 6.3.1 New Method: `QueuesClient.sendQueuesMessages(List<QueueMessage>)`

Add to `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueuesClient.java`:

```java
/**
 * Sends a batch of messages to queue channels in a single gRPC call.
 * Each message in the batch can target a different channel.
 *
 * @param messages List of queue messages to send. Must not be null or empty.
 *                 Maximum batch size is 1000 messages.
 * @return QueueMessagesBatchResponse containing per-message send results.
 * @throws IllegalArgumentException if messages is null, empty, exceeds max batch size,
 *         or any individual message fails validation.
 */
public QueueMessagesBatchResponse sendQueuesMessages(List<QueueMessage> messages) {
    validateBatch(messages);
    return this.queueUpstreamHandler.sendQueuesMessagesBatch(messages);
}

private void validateBatch(List<QueueMessage> messages) {
    if (messages == null || messages.isEmpty()) {
        throw new IllegalArgumentException("Messages list must not be null or empty.");
    }
    if (messages.size() > MAX_BATCH_SIZE) {
        throw new IllegalArgumentException(
            "Batch size " + messages.size() + " exceeds maximum allowed batch size of " + MAX_BATCH_SIZE + ".");
    }
    for (QueueMessage message : messages) {
        message.validate();
    }
}

private static final int MAX_BATCH_SIZE = 1000;
```

#### 6.3.2 New Method: `QueueUpstreamHandler.sendQueuesMessagesBatch()`

Add to `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueUpstreamHandler.java`:

```java
/**
 * Sends a batch of queue messages using the SendQueueMessagesBatch RPC.
 * This uses a single gRPC call (unary, not streaming) for the entire batch.
 */
public QueueMessagesBatchResponse sendQueuesMessagesBatch(List<QueueMessage> messages) {
    String batchId = UUID.randomUUID().toString();

    Kubemq.QueueMessagesBatchRequest.Builder requestBuilder =
            Kubemq.QueueMessagesBatchRequest.newBuilder()
                    .setBatchID(batchId);

    for (QueueMessage message : messages) {
        requestBuilder.addMessages(message.encodeMessage(kubeMQClient.getClientId()));
    }

    Kubemq.QueueMessagesBatchResponse pbResponse =
            kubeMQClient.getClient().sendQueueMessagesBatch(requestBuilder.build());

    return QueueMessagesBatchResponse.decode(pbResponse);
}
```

**Design note:** The batch send uses the unary `SendQueueMessagesBatch` RPC, NOT the bidirectional streaming `QueuesUpstream` RPC. This is a deliberate choice:
- The unary RPC is simpler (request/response, no stream management)
- It maps naturally to a synchronous `List<QueueMessage> -> BatchResponse` API
- The proto already defines this RPC and its request/response types
- The streaming upstream is better suited for individual sends with back-pressure

**IMPORTANT -- Pre-implementation verification required:** The `SendQueueMessagesBatch` RPC is from the older (pre-v2) server API. The current v2 SDK exclusively uses `QueuesUpstream` and `QueuesDownstream` streaming RPCs. Before implementing:

1. **Verify with KubeMQ server team** that `SendQueueMessagesBatch` is still supported on current server versions (2.x).
2. **If the legacy RPC is deprecated or unsupported server-side**, implement batch send as N rapid writes over the existing `QueuesUpstream` bidirectional stream, collecting all N responses. This maintains consistency with the current architecture.

   **Dependency note:** The fallback uses `sendQueuesMessageAsync(message)` which is currently private in `QueueUpstreamHandler.java` (line 163). Per CONC-4 (spec 10, Sub-phase A), this method must be made public (or package-private). **PERF-4 fallback depends on CONC-4 visibility change being implemented first.**

   **Fallback implementation (complete specification):**
   ```java
   // Fallback approach if legacy unary RPC is unavailable.
   // Uses the existing QueuesUpstream bidirectional stream for N individual writes.
   public QueueMessagesBatchResponse sendQueuesMessagesBatch(List<QueueMessage> messages) {
       String batchId = UUID.randomUUID().toString();
       List<CompletableFuture<QueueSendResult>> futures = new ArrayList<>(messages.size());

       // Send all messages concurrently over the existing stream
       for (QueueMessage message : messages) {
           futures.add(sendQueuesMessageAsync(message));
       }

       // Collect all results, preserving order
       List<QueueSendResult> results = new ArrayList<>(messages.size());
       boolean haveErrors = false;
       for (CompletableFuture<QueueSendResult> f : futures) {
           try {
               QueueSendResult result = f.get(requestTimeoutSeconds, TimeUnit.SECONDS);
               results.add(result);
               if (result.isError()) haveErrors = true;
           } catch (TimeoutException e) {
               // Create a synthetic error result for this message
               QueueSendResult errorResult = new QueueSendResult();
               errorResult.setIsError(true);
               errorResult.setError("Batch item timed out after " + requestTimeoutSeconds + "s");
               results.add(errorResult);
               haveErrors = true;
           } catch (ExecutionException e) {
               QueueSendResult errorResult = new QueueSendResult();
               errorResult.setIsError(true);
               errorResult.setError("Batch item failed: " + e.getCause().getMessage());
               results.add(errorResult);
               haveErrors = true;
           } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
               throw new RuntimeException("Batch send interrupted", e);
           }
       }

       return QueueMessagesBatchResponse.builder()
               .batchId(batchId)
               .results(results)
               .haveErrors(haveErrors)
               .build();
   }
   ```

   **Return type consistency:** The fallback returns the same `QueueMessagesBatchResponse` type as the unary RPC path. The `batchId` is generated client-side (UUID). The `haveErrors` flag is computed by scanning results. Per-message results are in the same order as input messages.

3. **Add this verification as a blocking task** in the implementation plan before writing batch send code. This is a day-1 task for PERF-4.

#### 6.3.3 New Response Class: `QueueMessagesBatchResponse`

Create `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueMessagesBatchResponse.java`:

```java
package io.kubemq.sdk.queues;

import kubemq.Kubemq;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Response from a batch queue message send operation.
 * Contains per-message results and an aggregate error flag.
 */
@Data
@Builder
public class QueueMessagesBatchResponse {

    /** The batch ID correlating this response to the request. */
    private String batchId;

    /** Per-message send results, in the same order as the request messages. */
    @Builder.Default
    private List<QueueSendResult> results = new ArrayList<>();

    /** True if any message in the batch had an error. */
    private boolean haveErrors;

    /**
     * Decodes a protobuf QueueMessagesBatchResponse to this SDK type.
     *
     * <p><b>Decode pattern:</b> This method uses the existing {@code QueueSendResult.decode()}
     * instance method pattern (via {@code new QueueSendResult().decode(pbResult)}) for
     * backward consistency with the rest of the codebase. While a static factory method
     * like {@code QueueSendResult.fromProto(pbResult)} would be architecturally cleaner
     * (and consistent with the builder pattern used for the outer response), maintaining
     * one decode pattern across the codebase reduces cognitive overhead. The outer
     * {@code QueueMessagesBatchResponse} itself uses the builder pattern via this static
     * factory.</p>
     *
     * <p><b>Proto field:</b> The proto's {@code QueueMessagesBatchResponse} contains
     * {@code repeated SendQueueMessageResult Results = 2;}, so {@code getResultsList()}
     * is the correct accessor (not {@code getMessagesList()}).</p>
     */
    public static QueueMessagesBatchResponse decode(Kubemq.QueueMessagesBatchResponse pbResponse) {
        List<QueueSendResult> results = new ArrayList<>();
        for (Kubemq.SendQueueMessageResult pbResult : pbResponse.getResultsList()) {
            results.add(new QueueSendResult().decode(pbResult));
        }
        return QueueMessagesBatchResponse.builder()
                .batchId(pbResponse.getBatchID())
                .results(results)
                .haveErrors(pbResponse.getHaveErrors())
                .build();
    }
}
```

#### 6.3.4 Batch Size Constant

Define `MAX_BATCH_SIZE = 1000` in `QueuesClient`. This is a **client-side guard** to prevent excessively large requests and does not reflect a verified server-side limit. The server may have its own (potentially different) limit. The value 1000 is a reasonable default based on typical gRPC message size constraints. If the server-side limit is determined to be lower, this constant should be reduced to match. This constant can be made configurable via builder in a future release if user feedback warrants it.

### 6.4 Files Changed

| File | Change |
|------|--------|
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueuesClient.java` | Add `sendQueuesMessages(List<QueueMessage>)` and `validateBatch()` |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueUpstreamHandler.java` | Add `sendQueuesMessagesBatch()` |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueMessagesBatchResponse.java` | NEW file |

### 6.5 Unit Tests

Create `kubemq-java/src/test/java/io/kubemq/sdk/unit/queues/QueueMessagesBatchResponseTest.java`:

| Test | Description |
|------|-------------|
| `testDecodeEmptyBatch` | Decode a protobuf response with zero results |
| `testDecodeMultipleResults` | Decode a response with 3 results (2 success, 1 error) |
| `testHaveErrorsFlag` | Verify `haveErrors` is true when any result has an error |

Create `kubemq-java/src/test/java/io/kubemq/sdk/unit/queues/QueuesClientBatchTest.java`:

| Test | Description |
|------|-------------|
| `testValidateBatchNull` | Null list throws `IllegalArgumentException` |
| `testValidateBatchEmpty` | Empty list throws `IllegalArgumentException` |
| `testValidateBatchExceedsMax` | List > 1000 throws `IllegalArgumentException` |
| `testValidateBatchInvalidMessage` | List containing invalid message throws `IllegalArgumentException` |
| `testValidateBatchValid` | List of valid messages passes validation |

### 6.6 Integration Tests

Add to existing integration test suite (requires running KubeMQ server):

| Test | Description |
|------|-------------|
| `testSendBatchSingleChannel` | Send 10 messages to same channel, verify all results success |
| `testSendBatchMultipleChannels` | Send messages to 3 different channels, verify routing |
| `testSendBatchPartialFailure` | Send batch with one invalid channel, verify `haveErrors` is true |
| `testSendBatchReceiveAll` | Send batch of 5 messages, poll with `pollMaxMessages=5`, verify all received |

### 6.7 Verification

| # | Check | How to Verify |
|---|-------|---------------|
| 1 | Batch send compiles | `mvn compile` succeeds |
| 2 | Unit tests pass | `mvn test` includes batch tests |
| 3 | Single gRPC call | Batch send uses `sendQueueMessagesBatch` RPC, not N individual calls |
| 4 | Batch size configurable | `MAX_BATCH_SIZE` constant enforced; `pollMaxMessages` already configurable for receive |
| 5 | Per-message results | `QueueMessagesBatchResponse.results` contains one result per input message |

---

## 7. REQ-PERF-5: Performance Documentation

**Status:** MISSING
**Effort:** S (0.5-1 day)
**Priority:** P2

### 7.1 Current State

Assessment 10.2.5 (score 1) confirms no performance documentation exists. The `maxReceiveSize` setting is mentioned in builder docs but there is no comprehensive performance section.

### 7.2 Acceptance Criteria

| # | Criterion | Status |
|---|-----------|--------|
| 1 | README or separate doc includes performance characteristics | MISSING |
| 2 | Tuning guidance: when to use batching, optimal batch sizes, connection sharing | MISSING |
| 3 | Known limitations documented (max message size, max concurrent streams) | PARTIAL -- `maxReceiveSize` in builder, but no centralized doc |

### 7.3 Implementation

Add a "Performance" section to `kubemq-java/README.md`. Exact content:

```markdown
## Performance

### Performance Characteristics

See [BENCHMARKS.md](BENCHMARKS.md) for reproducible benchmark results and methodology.

Key performance characteristics of the KubeMQ Java SDK:

| Characteristic | Value | Notes |
|---------------|-------|-------|
| Max message size | 100 MB | Configurable via `maxReceiveSize` builder parameter |
| Connection model | Single gRPC channel | All operations multiplex over one HTTP/2 connection |
| Serialization | Protocol Buffers | Message body is copied once during encoding (inherent to protobuf) |
| Batch receive | Supported | Use `pollMaxMessages` in `QueuesPollRequest` |
| Batch send | Supported | Use `sendQueuesMessages(List<QueueMessage>)` |

### Tuning Guidance

**When to use batching:**
- Use batch send (`sendQueuesMessages`) when sending multiple queue messages in quick succession.
  A single batch call has lower overhead than N individual `sendQueuesMessage` calls.
- Use batch receive (`pollMaxMessages > 1`) when consuming from high-throughput queues.
  Polling multiple messages per call reduces gRPC round-trips.

**Optimal batch sizes:**
- Start with batch sizes of 10-100 messages and measure throughput.
- The maximum batch size is 1000 messages per call.
- Larger batches increase per-call latency but improve throughput.

**Connection sharing:**
- Reuse client instances. One `QueuesClient` or `PubSubClient` handles all operations efficiently.
- Do not create a client per operation -- each client creates a new gRPC channel.

### Known Limitations

| Limitation | Value | Configurable |
|-----------|-------|--------------|
| Max inbound message size | 100 MB (default) | Yes, via `maxReceiveSize` in builder |
| Max batch send size | 1000 messages | Client-side limit |
| Queue poll timeout | Minimum 1 second | `pollWaitTimeoutInSeconds` parameter |
| Request timeout | 30 seconds (default) | Server-side timeout for queue upstream requests |
```

### 7.4 Verification

| # | Check | How to Verify |
|---|-------|---------------|
| 1 | Performance section in README | Section header "## Performance" exists |
| 2 | Known limitations table | Max message size, batch size limits documented |
| 3 | Tuning guidance | Batching, connection sharing guidance present |

---

## 8. REQ-PERF-6: Performance Tips Documentation

**Status:** MISSING
**Effort:** S (0.5 day)
**Priority:** P2

### 8.1 Current State

No performance tips documentation exists in the SDK.

### 8.2 Acceptance Criteria

| # | Criterion | Status |
|---|-----------|--------|
| 1 | SDK documentation includes a "Performance Tips" section covering: reuse client, use batching, don't block callbacks, close streams | MISSING |

### 8.3 Implementation

Add a "Performance Tips" subsection within the Performance section of `kubemq-java/README.md`:

```markdown
### Performance Tips

1. **Reuse the client instance.** A single `PubSubClient`, `QueuesClient`, or `CQClient` multiplexes
   all operations over one gRPC channel. Creating a client per operation wastes resources and adds
   connection setup latency.

2. **Use batching for high-throughput queue sends.** Call `sendQueuesMessages(List<QueueMessage>)` instead
   of calling `sendQueuesMessage()` in a loop. A single batch uses one gRPC call for all messages.
   For batch receive, set `pollMaxMessages` to retrieve multiple messages per call.

3. **Do not block subscription callbacks.** Event and command/query subscription callbacks
   (`onReceiveEventCallback`, `onReceiveCommandCallback`, etc.) run on gRPC executor threads.
   Blocking these threads (e.g., with `Thread.sleep()`, synchronous I/O, or long computations)
   delays processing of subsequent messages. Offload heavy work to a separate executor.

4. **Close clients and streams when done.** All client classes implement `AutoCloseable`.
   Use try-with-resources or call `close()` explicitly to release gRPC channels and executor threads.

   ```java
   try (QueuesClient client = QueuesClient.builder()
           .address("localhost:50000")
           .clientId("my-client")
           .build()) {
       // ... use client ...
   } // channel closed automatically
   ```
```

### 8.4 Verification

| # | Check | How to Verify |
|---|-------|---------------|
| 1 | Performance Tips heading | "### Performance Tips" exists in README |
| 2 | Four tips present | All four topics covered: reuse, batching, callbacks, close |
| 3 | Code example | try-with-resources example shown |

---

## 9. Cross-Category Dependencies

| This REQ | Depends On | Nature of Dependency |
|----------|-----------|---------------------|
| REQ-PERF-1 (benchmarks) | Running KubeMQ server | Benchmarks are integration benchmarks; require a live server |
| REQ-PERF-1 (benchmarks) | REQ-PERF-4 (batch send) | Benchmarks should include batch throughput measurement |
| REQ-PERF-4 (batch send) | None | Uses existing proto `SendQueueMessagesBatch` RPC |
| REQ-PERF-5 (perf docs) | REQ-PERF-1 (benchmarks) | Docs reference BENCHMARKS.md for numbers |
| REQ-PERF-5 (perf docs) | REQ-PERF-4 (batch send) | Docs describe batch send tuning |
| REQ-PERF-6 (perf tips) | REQ-PERF-4 (batch send) | Tips reference batch API |
| REQ-PERF-2 (connection reuse doc) | REQ-PERF-6 (perf tips) | Connection reuse text lives in Performance Tips section |

**Cross-category dependencies:**

| This REQ | Depends On (Other Category) | Nature |
|----------|---------------------------|--------|
| REQ-PERF-4 (batch send) | REQ-API-1 (API Completeness, Cat 08) | Batch send is also listed as a missing API in API Completeness. Single implementation satisfies both. |
| REQ-PERF-5 (perf docs) | REQ-DOC-3 (Documentation, Cat 06) | Performance docs are part of overall documentation. |
| REQ-PERF-1 (benchmarks) | REQ-TEST-3 (CI, Cat 04) | Benchmarks could be added to CI as a non-blocking job. Not required for initial implementation. |

---

## 10. Breaking Changes

**None.** All changes in this specification are additive:

| Change | Breaking? | Reason |
|--------|-----------|--------|
| New `sendQueuesMessages(List<QueueMessage>)` method | No | New method on existing class; no existing signatures changed |
| New `QueueMessagesBatchResponse` class | No | New class in existing package |
| JMH dependencies (test scope) | No | Test-scope dependencies do not affect consumers |
| New Maven profile `benchmark` | No | Profiles are opt-in |
| README additions | No | Documentation only |
| `BENCHMARKS.md` | No | New file |

---

## 11. Open Questions

| # | Question | Impact | Proposed Resolution |
|---|----------|--------|-------------------|
| 1 | Should batch send also be exposed via the streaming upstream (in addition to unary RPC)? | Could enable streaming batch for very high throughput | Defer. The unary `SendQueueMessagesBatch` RPC is simpler and sufficient. Streaming batch can be added later if benchmarks show the unary approach is a bottleneck. |
| 2 | Should benchmarks run in CI? | CI benchmarks can detect performance regressions but are noisy in shared runners | Defer. Run benchmarks manually for now. Add to CI as a non-blocking job when REQ-TEST-3 CI infrastructure is in place. Use relative comparison (% regression) not absolute numbers. |
| 3 | Should `MAX_BATCH_SIZE` be configurable via builder? | Some users may need larger batches | Start with a constant. If user feedback requests configurability, add `maxBatchSize(int)` to `QueuesClient.Builder` in a minor release. |
| 4 | What is the server-side batch size limit? | Client-side limit of 1000 may be higher or lower than server limit | Investigate server documentation. If the server has a lower limit, reduce the client constant to match. If higher, 1000 is still a reasonable client-side guard. |
| 5 | Can `BENCHMARKS.md` be merged with `[TBD]` placeholder values? | Blocks PR if actual numbers are required | Yes. Merge with `[TBD]` placeholders. The infrastructure (JMH classes + Maven profile + `benchmarks.jar`) is the deliverable. Actual numbers are populated in a follow-up after running on reference hardware. |
