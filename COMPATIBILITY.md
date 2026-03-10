# KubeMQ Java SDK Compatibility Matrix

## SDK Version vs. KubeMQ Server Version

| SDK Version | Server 2.0.x | Server 2.1.x | Server 2.2.x+ | Notes |
|-------------|-------------|-------------|---------------|-------|
| 2.0.x       | Tested      | Tested      | Not tested    | `MAX_SERVER_VERSION` covers up to 2.2.99 for forward-compat |
| 2.1.x       | Tested      | Tested      | Not tested    | Server 2.2.x may work (within version range) but is not CI-tested |

## Java Runtime Compatibility

| SDK Version | Java 11 (LTS) | Java 17 (LTS) | Java 21 (LTS) |
|-------------|---------------|----------------|----------------|
| 2.1.x       | Tested (CI)   | Tested (CI)    | Tested (CI)    |

## gRPC Protocol Compatibility

| SDK Version | gRPC Version | Protobuf Version | Notes |
|-------------|-------------|-----------------|-------|
| 2.1.1       | 1.75.0      | 4.28.2          |       |

## Dependency Audit

| Dependency | Purpose | Risk |
|-----------|---------|------|
| `grpc-netty-shaded` | Core gRPC transport for all server communication | Low |
| `grpc-protobuf` | Protobuf serialization for KubeMQ protocol messages | Low |
| `grpc-stub` | gRPC stub generation for client stubs | Low |
| `protobuf-java` | Protobuf runtime for message serialization/deserialization | Low |
| `lombok` | Compile-time annotation processing (no runtime footprint) | Low |
| `slf4j-api` | Logging facade API | Low |
| `jackson-databind` | JSON serialization for channel list responses | Medium (CVE surface) |
| `javax.annotation-api` | Annotations for gRPC generated code (removed from JDK 11+) | Low |
| `jsr305` | Thread safety annotations (`@ThreadSafe`, `@GuardedBy`) | Low |
| `opentelemetry-api` | Optional OpenTelemetry tracing/metrics (provided scope) | Low |

## How to Read This Matrix

- **Tested**: This combination is tested in CI and supported.
- **Not tested**: May work but is not part of the CI matrix. Use at your own risk.
- **Incompatible**: Known incompatibility. Do not use this combination.
