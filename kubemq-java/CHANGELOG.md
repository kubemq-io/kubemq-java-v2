# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [3.1.0] - 2026-04-05

### Features
- Add `grpcChannel(ManagedChannel)` builder parameter for external gRPC channel injection (9b24e16)
- Add `interceptors(List<ClientInterceptor>)` builder parameter for custom interceptor support (9b24e16)
- Add `fireDisconnected()` and `fireConnected()` to ConnectionStateMachine for external channel state notifications (9b24e16)

### Improvements
- Format Javadoc for improved readability and consistency across exceptions and tests (38493aa)
- Remove `VisibilityTimeoutExample` and update examples to use `long` for time values (df73a42)

## [3.0.0] - 2026-04-04

### Added
- Typed exception hierarchy rooted at `KubeMQException` with `ErrorCode`, `ErrorCategory`, and `ErrorClassifier`
- `GrpcErrorMapper` for automatic gRPC status code to SDK exception mapping
- `RetryPolicy` framework with configurable backoff strategies
- `ErrorMessageBuilder` for structured, context-rich error messages
- `ConnectionStateMachine` with `DISCONNECTED → CONNECTING → CONNECTED → RECONNECTING` lifecycle
- `ReconnectionManager` with exponential backoff and `MessageBuffer` for offline buffering
- `ReconnectionConfig` for configurable reconnection behavior
- `ConnectionStateListener` interface for connection event notifications
- `CredentialProvider` interface with `StaticTokenProvider` and `CredentialManager`
- mTLS support via `tlsCaCertFile` builder option
- Certificate hot-reload support in `CredentialManager`
- `@Internal` annotation to mark package-private implementation details
- `GrpcTransport` layer extracted from client classes for clean separation
- `KubeMQLogger` abstraction with `Slf4jLoggerAdapter` and `NoOpLogger` fallback
- `KubeMQLoggerFactory` for structured logging with `LogHelper` utilities
- OpenTelemetry trace instrumentation with W3C trace context propagation
- `KubeMQMetrics` and `KubeMQTracing` with no-op fallback when OTel is absent
- `CardinalityManager` for metrics cardinality control
- `Subscription` handle returned from all subscribe methods for lifecycle management
- Batch `sendQueuesMessages(List)` API for queue message batching
- `NotImplementedException` with `purgeQueue()` stub
- Fail-fast validation via `ValidationException` on all message builders
- Default address resolution chain: explicit → `KUBEMQ_ADDRESS` env var → `localhost:50000`
- Auto-generated `clientId` (UUID) when not specified
- Async API (`*Async` methods returning `CompletableFuture`) on all client classes
- `@ThreadSafe` / `@NotThreadSafe` annotations (JSR-305)
- Semaphore-based callback concurrency control with in-flight tracking
- JMH benchmark infrastructure (`PublishThroughputBenchmark`, `PublishLatencyBenchmark`, etc.)
- `KubeMQVersion` class for runtime SDK version access
- `BENCHMARKS.md` with methodology and benchmark descriptions
- Server compatibility check on connection with version range validation
- `CompatibilityConfig` class for server version range validation
- `COMPATIBILITY.md` with SDK-to-server version matrix and dependency audit
- `MIGRATION.md` with v1-to-v2 migration guidance
- `TROUBLESHOOTING.md` with 11 common issue resolutions
- `CONTRIBUTING.md` with deprecation policy
- CycloneDX SBOM generation plugin
- Comprehensive Javadoc on all public API classes and methods
- CI pipeline (GitHub Actions) with Java 11/17/21 matrix, JaCoCo coverage, and Dependabot

### Changed
- README restructured into 10-section format with quick starts, configuration table, and troubleshooting
- README now correctly states Java 11 (was incorrectly listed as JDK 8)
- Enhanced Dependabot configuration with protobuf dependency grouping
- All `subscribe*` methods now return `Subscription` handle (was `void`) — **BREAKING**
- `validate()` methods throw `ValidationException` instead of `IllegalArgumentException`
- Logging migrated from `@Slf4j` to `KubeMQLogger` abstraction (13 files)
- `commons-lang3` and `grpc-alts` dependencies removed; `logback` moved to test scope

### Fixed
- Race condition in `EventStreamHelper` during concurrent stream operations
- Resource leaks in client `close()` with in-flight request draining

## [2.1.1] - 2025-06-01

### Changed
- Bump version to 2.1.1
- Update Maven Central publishing configuration

## [2.1.0] - 2025-05-01

### Added
- Extensive unit and integration test suite (795 tests)
- TLS connection examples
- Authentication token examples
- Channel search examples
- Event sourcing examples
- `RequestSender` functional interface for queue requests

### Changed
- Upgrade dependencies and Java version compatibility
- Upgrade gRPC to 1.75.0, protobuf to 4.28.2

### Fixed
- Edge cases in queue handling
- Various stability improvements

## [2.0.3] - 2025-03-01

### Fixed
- Bug fixes and stability improvements

## [2.0.0] - 2025-01-01

### Added
- Complete rewrite of KubeMQ Java SDK (v2)
- Events pub/sub support
- Events Store persistent pub/sub support
- Queue stream upstream and downstream
- RPC Commands and Queries
- Builder pattern for all message types
- gRPC-based transport with connection management

[Unreleased]: https://github.com/kubemq-io/kubemq-java-v2/compare/v3.0.0...HEAD
[3.0.0]: https://github.com/kubemq-io/kubemq-java-v2/compare/v2.1.1...v3.0.0
[2.1.1]: https://github.com/kubemq-io/kubemq-java-v2/compare/v2.1.0...v2.1.1
[2.1.0]: https://github.com/kubemq-io/kubemq-java-v2/compare/v2.0.3...v2.1.0
[2.0.3]: https://github.com/kubemq-io/kubemq-java-v2/compare/v2.0.0...v2.0.3
[2.0.0]: https://github.com/kubemq-io/kubemq-java-v2/releases/tag/v2.0.0
