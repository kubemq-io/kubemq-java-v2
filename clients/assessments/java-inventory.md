# KubeMQ Java SDK — Codebase Inventory

## Directory Structure

```
/Users/liornabat/development/projects/kubemq/clients/kubemq-java-v2/
├── .github/
│   ├── workflows/
│   │   ├── ci.yml
│   │   └── release.yml
│   └── dependabot.yml
├── .claude/
│   └── skills/
├── clients/
│   ├── assessments/
│   ├── golden-standard/
│   └── skills/
├── kubemq-java/              # Main SDK module
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/         # Production source code
│   │   │   ├── proto/        # Protocol buffer definitions
│   │   │   └── resources/    # Configuration and version files
│   │   ├── test/
│   │   │   ├── java/         # Unit and integration tests
│   │   │   └── resources/    # Test fixtures
│   │   └── benchmark/        # JMH benchmark source
│   ├── target/               # Build output
│   ├── pom.xml               # Maven configuration
│   ├── checkstyle.xml        # Code style rules
│   ├── dependency-check-suppressions.xml
│   ├── CHANGELOG.md
│   ├── CONTRIBUTING.md
│   ├── BENCHMARKS.md
│   └── [other docs]
├── kubemq-java-example/      # Example applications
│   ├── src/
│   └── target/
├── README.md                 # SDK documentation
├── MIGRATION.md              # v1 to v2 migration guide
├── TROUBLESHOOTING.md        # Common issues and solutions
├── COMPATIBILITY.md          # Version compatibility matrix
├── kubemq.proto              # Main protocol buffer definition
└── codecov.yml               # Code coverage configuration
```

## File Counts

- **Source files (.java in src/main)**: 185
- **Test files (src/test)**: 113
- **Documentation files (.md)**: 56
- **Proto files**: 3

## Package Manifest (pom.xml)

- **groupId**: io.kubemq.sdk
- **artifactId**: kubemq-sdk-Java
- **version**: 2.1.1
- **packaging**: JAR
- **license**: MIT License
- **Java Target**: 11 (source 11, target 11)
- **Protocol Buffers**: 4.28.2
- **gRPC**: 1.75.0
- **OpenTelemetry BOM**: 1.40.0

**Core Dependencies:**
- grpc-netty-shaded, grpc-protobuf, grpc-stub
- protobuf-java (4.28.2)
- lombok (1.18.36, provided)
- slf4j-api (2.0.13)
- jackson-databind (2.17.0)
- opentelemetry-api (provided)
- jsr305 (3.0.2, provided)

**Test Dependencies:**
- JUnit Jupiter 5.10.3
- Mockito 5.14.2
- Awaitility 4.2.0
- gRPC testing libraries
- OpenTelemetry SDK for testing
- Logback 1.4.12

**Build Plugins:**
- maven-compiler-plugin, maven-gpg-plugin, maven-source-plugin
- maven-javadoc-plugin, protobuf-maven-plugin, central-publishing-maven-plugin
- maven-surefire-plugin, maven-failsafe-plugin
- jacoco-maven-plugin (60% minimum threshold)
- dependency-check-maven (OWASP), cyclonedx-maven-plugin (SBOM)

## Documentation Files

- **README.md** — Installation, quick start, configuration, error handling, troubleshooting
- **MIGRATION.md** — v1 to v2 migration guide
- **TROUBLESHOOTING.md** — Common issues and solutions
- **COMPATIBILITY.md** — SDK-to-server version matrix
- **CHANGELOG.md** — Release history (v2.0.0 through v2.1.1)
- **CONTRIBUTING.md** — Developer guidelines, build instructions, PR process
- **BENCHMARKS.md** — JMH benchmark methodology and results

## CI/CD Configuration

**ci.yml** — Lint (Java 17), Unit Tests (Java 11/17/21 matrix), Integration Tests (KubeMQ Docker), Coverage (JaCoCo + Codecov)
**release.yml** — Release workflow
**dependabot.yml** — Automated dependency updates

## Source Package Structure

Main package: `io.kubemq.sdk`
- `client` — PubSubClient, QueuesClient, CQClient
- `pubsub` — Events and EventStore
- `queues` — Queue send/receive
- `cq` — Command/Query request-response
- `transport` — GrpcTransport, TransportFactory, TransportAuthInterceptor
- `retry` — RetryPolicy, RetryExecutor, OperationSafety
- `auth` — CredentialProvider, StaticTokenProvider, CredentialManager
- `observability` — KubeMQLogger, KubeMQMetrics, KubeMQTracing
- `common` — KubeMQVersion, ServerInfo, ChannelDecoder, CompatibilityConfig
- `exception` — KubeMQException, ConnectionException, ValidationException, etc.

## Proto Files

- kubemq.proto — Core gRPC service: 12 RPC endpoints, 20+ message types
