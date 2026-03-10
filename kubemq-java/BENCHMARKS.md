# KubeMQ Java SDK Benchmarks

## Methodology

- **Framework:** JMH 1.37
- **JDK:** OpenJDK 17.x (or later)
- **Hardware:** \[CPU model\], \[RAM\], \[OS\]
- **KubeMQ Server:** \[version\], single node, default configuration
- **Payload:** 1KB (1024 bytes) unless otherwise noted
- **Warm-up:** 3 iterations x 5 seconds
- **Measurement:** 5 iterations x 10 seconds
- **Forks:** 1

## Results

| Benchmark | Metric | Value | Unit |
|-----------|--------|-------|------|
| Publish throughput (1KB) | Throughput | \[TBD\] | msgs/sec |
| Publish latency (1KB) | p50 | \[TBD\] | us |
| Publish latency (1KB) | p99 | \[TBD\] | us |
| Queue roundtrip (1KB) | p50 | \[TBD\] | ms |
| Queue roundtrip (1KB) | p99 | \[TBD\] | ms |
| Connection setup | Mean | \[TBD\] | ms |

> **Note:** `[TBD]` values are populated after running the benchmarks on reference hardware.
> The infrastructure (JMH classes, Maven profile, benchmarks.jar) is the deliverable;
> actual numbers are filled in after execution.

## Running Benchmarks

Benchmarks require a running KubeMQ server.

```bash
# Start KubeMQ via Docker
docker run -d -p 50000:50000 kubemq/kubemq:latest

# Build the benchmark jar (opt-in profile, skips unit tests)
mvn package -Pbenchmark -DskipTests

# Run all benchmarks
java -jar target/benchmarks.jar io.kubemq.sdk.benchmark.* \
  -rf json -rff target/benchmark-results.json

# Run a specific benchmark
java -jar target/benchmarks.jar PublishThroughputBenchmark

# Custom server address
java -jar target/benchmarks.jar -jvmArgs "-Dkubemq.address=myhost:50000"
```

## Benchmark Descriptions

| Benchmark | Mode | What It Measures |
|-----------|------|------------------|
| `PublishThroughputBenchmark` | Throughput (ops/s) | Fire-and-forget event publish rate |
| `PublishLatencyBenchmark` | SampleTime (p50/p99) | EventStore publish-with-ack latency |
| `QueueRoundtripBenchmark` | SampleTime (p50/p99) | Queue send + poll + auto-ack roundtrip |
| `ConnectionSetupBenchmark` | SingleShotTime | Client creation to first successful ping |

## Notes

- Results vary by hardware and network. These numbers are baselines for comparison.
- Raw JMH JSON output is written to `target/benchmark-results.json`.
- Benchmarks are never run during `mvn test` — they require the `-Pbenchmark` profile.
- The benchmark jar is self-contained (shaded uber-jar) and can be copied to other machines.
