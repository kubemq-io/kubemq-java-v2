# Gap-Close Specification: Category 07 -- Code Quality & Architecture

**SDK:** KubeMQ Java v2 (`kubemq-java-v2`)
**Version Under Spec:** 2.1.1
**Golden Standard:** `clients/golden-standard/07-code-quality.md`
**Gap Research:** `clients/gap-research/java-gap-research.md` (lines 1048-1213)
**Assessment:** `clients/assesments/JAVA_ASSESSMENT_REPORT.md` (Category 8, lines 449-513)
**Current Score:** 3.48 | **Target:** 4.0+ | **Gap:** +0.52 | **Priority:** P1

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [REQ-CQ-1: Layered Architecture](#2-req-cq-1-layered-architecture)
3. [REQ-CQ-2: Internal vs Public API Separation](#3-req-cq-2-internal-vs-public-api-separation)
4. [REQ-CQ-3: Linting and Formatting](#4-req-cq-3-linting-and-formatting)
5. [REQ-CQ-4: Minimal Dependencies](#5-req-cq-4-minimal-dependencies)
6. [REQ-CQ-5: Consistent Code Organization](#6-req-cq-5-consistent-code-organization)
7. [REQ-CQ-6: Code Review Standards](#7-req-cq-6-code-review-standards)
8. [REQ-CQ-7: Secure Defaults](#8-req-cq-7-secure-defaults)
9. [Implementation Order](#9-implementation-order)
10. [Cross-Category Dependencies](#10-cross-category-dependencies)
11. [GS Internal Inconsistencies](#11-gs-internal-inconsistencies)
12. [Future Enhancements](#12-future-enhancements)
13. [Test Plan](#13-test-plan)

---

## 1. Executive Summary

| REQ | Status | Effort | Breaking? | Summary |
|-----|--------|--------|-----------|---------|
| REQ-CQ-1 | PARTIAL | XL (5-8d) | No (additive) | Extract 3-layer architecture: Transport, Protocol, Public API |
| REQ-CQ-2 | PARTIAL | S (<1d) | Yes (minor) | Restrict access modifiers on internal classes |
| REQ-CQ-3 | MISSING | M (1-3d) | No | Add Error Prone, Spotless, Checkstyle to build |
| REQ-CQ-4 | PARTIAL | M (1-2d) | No | Remove commons-lang3 and logback-classic; add OWASP |
| REQ-CQ-5 | PARTIAL | S (<1d) | No | Add new packages (transport/); enhance existing exception/; defer cq rename |
| REQ-CQ-6 | NOT_ASSESSED | S (<1d) | No | Branch protection, PR template, remove dead code |
| REQ-CQ-7 | PARTIAL | S (<1d) | No | Audit toString()/error messages for credential leaks |

**Total Estimated Effort:** 10-16 days

---

## 2. REQ-CQ-1: Layered Architecture

**Gap Status:** PARTIAL
**Effort:** XL (5-8 days)
**Breaking Change:** No -- additive refactoring; existing public API preserved

### 2.1 Current State

The SDK has no formal layered architecture. gRPC is directly referenced across all layers:

| File | gRPC Coupling Evidence |
|------|----------------------|
| `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java` (lines 4-11) | Imports `io.grpc.*`, `io.grpc.netty.shaded.*`, creates `ManagedChannel` directly in `initChannel()` (line 261) |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueUpstreamHandler.java` (line 3) | Imports `io.grpc.stub.StreamObserver`, operates on `Kubemq.QueuesUpstreamRequest` |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueDownstreamHandler.java` (line 3) | Imports `io.grpc.stub.StreamObserver`, operates on `Kubemq.QueuesDownstreamRequest` |
| `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventStreamHelper.java` (line 3) | Imports `io.grpc.stub.StreamObserver`, operates on `Kubemq.Event` |
| `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/PubSubClient.java` (lines 3-4) | Imports `io.grpc.stub.StreamObserver`, `kubemq.Kubemq` |
| `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsSubscription.java` (lines 3-4) | Imports `io.grpc.stub.StreamObserver`, `kubemq.Kubemq`, contains reconnection logic |
| `kubemq-java/src/main/java/io/kubemq/sdk/cq/CQClient.java` (lines 5-6) | Imports `kubemq.Kubemq` for Request/Response directly |
| `kubemq-java/src/main/java/io/kubemq/sdk/common/KubeMQUtils.java` (lines 10-11) | Imports `kubemq.Kubemq`, `kubemq.Kubemq.Request/Response` directly |

The only interface in the codebase is `RequestSender` (`kubemq-java/src/main/java/io/kubemq/sdk/queues/RequestSender.java`), which is package-private and limited to queue downstream flow.

The `MetadataInterceptor` inner class in `KubeMQClient.java` (line 432) is the only gRPC `ClientInterceptor`. It handles auth token injection. There is no interceptor chain for error mapping, retry, or observability.

### 2.2 Acceptance Criteria Status

| Criterion | Current | Target |
|-----------|---------|--------|
| Public API types don't reference gRPC/protobuf | PARTIAL -- Message `encode()`/`decode()` reference protobuf internally but public fields are Java-native | Must move encode/decode to transport adapters |
| Protocol layer handles error wrapping, retry, auth, OTel | MISSING -- No protocol layer exists | Create `ClientInterceptor` chain |
| Transport layer is only gRPC importer | MISSING -- gRPC imported across 8+ classes | Consolidate into `GrpcTransport` |
| Layers communicate via interfaces | MISSING -- One interface exists (`RequestSender`) | Define `Transport` interface |
| Users can import SDK without gRPC-internal types | PARTIAL -- gRPC types on classpath but not in public signatures | Ensure no gRPC types in public method signatures |
| Dependencies flow downward only | MISSING -- No layered structure to enforce direction | Layer separation makes this measurable |

### 2.3 Implementation Specification

#### 2.3.1 Transport Layer: `io.kubemq.sdk.transport` (new package)

Create a `Transport` interface and `GrpcTransport` implementation. All gRPC channel management, stub creation, keepalive, and reconnection logic moves here.

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/transport/Transport.java` (NEW)

```java
package io.kubemq.sdk.transport;

import io.kubemq.sdk.common.ServerInfo;
import java.util.concurrent.CompletableFuture;

/**
 * Transport defines the low-level communication contract with the KubeMQ server.
 * This is the only layer that imports gRPC packages.
 */
public interface Transport extends AutoCloseable {

    /** Ping the server. */
    ServerInfo ping();

    /** Send a unary request and return the raw response. */
    TransportResponse sendRequest(TransportRequest request);

    /** Open a bidirectional stream for queue upstream. */
    UpstreamStream openUpstreamStream();

    /** Open a bidirectional stream for queue downstream. */
    DownstreamStream openDownstreamStream();

    /** Open a server-streaming subscription for events. */
    void subscribe(SubscribeRequest request, SubscriptionObserver observer);

    /** Open a client-streaming channel for event publishing. */
    EventPublishStream openEventPublishStream();

    /** Return true if the transport is connected and ready. */
    boolean isReady();

    /** Shut down the transport, releasing all resources. */
    @Override
    void close();
}
```

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/transport/GrpcTransport.java` (NEW)

This class absorbs the following responsibilities currently in `KubeMQClient`:
- `initChannel()` (KubeMQClient.java lines 261-325) -- ManagedChannel creation
- `validateTlsConfiguration()` (lines 209-255) -- TLS setup
- `addChannelStateListener()` / `handleStateChange()` (lines 327-346) -- connectivity monitoring
- `close()` channel shutdown (lines 370-384)
- MetadataInterceptor (lines 431-449) -- auth token injection

And absorbs gRPC stub creation currently done in client classes:
- `kubeMQClient.getAsyncClient().queuesUpstream()` from `QueueUpstreamHandler.java` (line 108)
- `kubeMQClient.getAsyncClient().queuesDownstream()` from `QueueDownstreamHandler.java` (line 110)
- `kubeMQClient.getAsyncClient().subscribeToEvents()` from `EventsSubscription.java` (line 177)
- `kubeMQClient.getClient().sendRequest()` from `CQClient.java`, `KubeMQUtils.java`

```java
package io.kubemq.sdk.transport;

// Only this class and its package import gRPC
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import kubemq.kubemqGrpc;

import java.util.List;

/**
 * gRPC-based implementation of {@link Transport}.
 * This is the ONLY class in the SDK that imports gRPC packages.
 */
class GrpcTransport implements Transport {

    private final TransportConfig config;
    private ManagedChannel managedChannel;
    private kubemqGrpc.kubemqBlockingStub blockingStub;
    private kubemqGrpc.kubemqStub asyncStub;
    private final List<ClientInterceptor> interceptors;

    GrpcTransport(TransportConfig config) {
        this.config = config;
        // Build interceptor chain internally from config -- no gRPC types leak out
        this.interceptors = buildInterceptors(config);
        initChannel();
    }

    private static List<ClientInterceptor> buildInterceptors(TransportConfig config) {
        List<ClientInterceptor> chain = new java.util.ArrayList<>();
        if (config.getTokenSupplier() != null) {
            chain.add(new AuthInterceptor(config.getTokenSupplier()));
        }
        // Future: chain.add(new ErrorMappingInterceptor());
        // Future: chain.add(new TracingInterceptor());
        return chain;
    }

    private void initChannel() {
        // Move initChannel() logic from KubeMQClient here
        // Apply interceptors as a chain
    }

    // ... implement all Transport methods
}
```

**Factory for cross-package instantiation:** Since `GrpcTransport` is package-private in `io.kubemq.sdk.transport` and `KubeMQClient` is in `io.kubemq.sdk.client`, a public factory method is needed to create `Transport` instances from outside the package.

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/transport/TransportFactory.java` (NEW)

```java
package io.kubemq.sdk.transport;

/**
 * Factory for creating Transport instances.
 * This is the public entry point for the transport package.
 * Clients use this factory; they never instantiate GrpcTransport directly.
 *
 * <p>The factory signature does NOT expose gRPC types (e.g., ClientInterceptor).
 * Interceptors are built internally by GrpcTransport based on TransportConfig,
 * which carries the token supplier and feature flags needed to configure the
 * interceptor chain. This keeps gRPC types isolated within the transport package
 * per REQ-CQ-1.</p>
 */
public final class TransportFactory {

    private TransportFactory() {}

    /**
     * Creates a new gRPC-based Transport.
     * Interceptors (auth, error mapping, etc.) are built internally from config.
     *
     * @param config transport configuration (no gRPC types)
     * @return a new Transport instance
     */
    public static Transport create(TransportConfig config) {
        return new GrpcTransport(config);
    }
}
```

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/transport/TransportConfig.java` (NEW)

```java
package io.kubemq.sdk.transport;

import lombok.Builder;
import lombok.Getter;

/**
 * Configuration for the transport layer. No gRPC types -- pure Java.
 * The token supplier enables dynamic token retrieval per-call without
 * leaking gRPC's ClientInterceptor type into the public API.
 */
@Getter
@Builder
public class TransportConfig {
    private final String address;
    private final boolean tls;
    private final String tlsCertFile;
    private final String tlsKeyFile;
    private final String caCertFile;
    private final int maxReceiveSize;
    private final boolean keepAlive;
    private final int keepAliveTimeSeconds;
    private final int keepAliveTimeoutSeconds;

    /**
     * Supplier for the current auth token. Called per-gRPC-call by the
     * internally-created AuthInterceptor. Returns null/empty if no token.
     * Typically backed by AtomicReference or CredentialManager.
     */
    private final java.util.function.Supplier<String> tokenSupplier;
}
```

Additional transport-layer DTOs (all in `io.kubemq.sdk.transport`, all package-private):
- `TransportRequest` / `TransportResponse` -- wraps the serialized protobuf bytes for unary calls
- `UpstreamStream`, `DownstreamStream`, `EventPublishStream` -- stream abstractions
- `SubscribeRequest`, `SubscriptionObserver` -- subscription abstractions

#### 2.3.2 Protocol Layer: gRPC ClientInterceptor Chain

The protocol layer is implemented as a chain of `ClientInterceptor` instances applied to the gRPC channel in `GrpcTransport`. This is the idiomatic Java/gRPC pattern for cross-cutting concerns.

**Interceptor chain (applied in order):**

| Interceptor | File (NEW) | Purpose | Depends On |
|-------------|-----------|---------|------------|
| `AuthInterceptor` | `transport/AuthInterceptor.java` | Inject auth token into metadata | Replaces `MetadataInterceptor` in KubeMQClient.java line 432 |
| `ErrorMappingInterceptor` | `transport/ErrorMappingInterceptor.java` | Wrap gRPC `StatusRuntimeException` to SDK error types | REQ-ERR-6 |
| `RetryInterceptor` | `transport/RetryInterceptor.java` | Retry retryable errors with exponential backoff | REQ-ERR-3 |
| `TracingInterceptor` | `transport/TracingInterceptor.java` | Create OTel spans per operation | REQ-OBS-1 (future) |
| `MetricsInterceptor` | `transport/MetricsInterceptor.java` | Record OTel metrics per operation | REQ-OBS-3 (future) |
| `LoggingInterceptor` | `transport/LoggingInterceptor.java` | Log operations at DEBUG level | REQ-OBS-5 |

**Initial implementation:** Only `AuthInterceptor` and `ErrorMappingInterceptor` are needed immediately. Others are added as their dependent specs (REQ-ERR-3, REQ-OBS-*) are implemented.

```java
package io.kubemq.sdk.transport;

import io.grpc.*;

/**
 * Replaces the inner MetadataInterceptor from KubeMQClient.
 * Injects authorization token into gRPC metadata dynamically per-call.
 *
 * <p>Design note: The token is read via a {@code Supplier<String>} on every call,
 * supporting mutable tokens (REQ-AUTH-1) and credential providers (REQ-AUTH-4).
 * See 03-auth-security-spec.md section 3.1.2 for the full dynamic token design.
 */
class AuthInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> AUTH_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final java.util.function.Supplier<String> tokenSupplier;

    /**
     * @param tokenSupplier supplies the current auth token per-call.
     *        Returns null or empty string if no token is configured.
     *        Typically backed by an AtomicReference or CredentialManager.
     */
    AuthInterceptor(java.util.function.Supplier<String> tokenSupplier) {
        this.tokenSupplier = tokenSupplier;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(
                next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String token = tokenSupplier.get();
                if (token != null && !token.isEmpty()) {
                    headers.put(AUTH_KEY, token);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
```

#### 2.3.3 Public API Layer Modifications

After extracting transport, the public client classes (`PubSubClient`, `QueuesClient`, `CQClient`) no longer import gRPC types. They delegate to `Transport`.

**Modifications to `KubeMQClient.java`:**

The abstract base class is refactored to hold a `Transport` instead of raw gRPC objects.

Current state (lines 64-69):
```java
@Setter
private ManagedChannel managedChannel;
@Setter
private kubemqGrpc.kubemqBlockingStub blockingStub;
@Setter
private kubemqGrpc.kubemqStub asyncStub;
@Setter
private Metadata metadata;
```

New state:
```java
private final Transport transport;
```

The `getClient()` and `getAsyncClient()` methods (lines 353-364) are deprecated or made internal. Client classes call `transport.sendRequest()`, `transport.subscribe()`, etc. instead.

**Backward compatibility:** To avoid breaking existing code that calls `getClient()`/`getAsyncClient()`, these methods are retained but deprecated with `@Deprecated(since = "2.2.0", forRemoval = true)`.

#### 2.3.4 Migration Steps

1. Create the `io.kubemq.sdk.transport` package with interfaces and DTOs
2. Create `GrpcTransport` by extracting from `KubeMQClient.initChannel()` and `close()`
3. Extract `MetadataInterceptor` from `KubeMQClient` to `AuthInterceptor`
4. Update `KubeMQClient` to hold `Transport` instead of raw gRPC stubs
5. Update `QueueUpstreamHandler` and `QueueDownstreamHandler` to receive `Transport`
6. Update `EventStreamHelper` to receive `Transport`
7. Update subscription classes to use `Transport.subscribe()` instead of direct gRPC
8. Deprecate `getClient()` and `getAsyncClient()` on `KubeMQClient`
9. Move protobuf encode/decode from message classes to transport adapters
10. Verify all tests pass at each step

### 2.4 Risk Assessment

This is the single largest change (XL effort). Key risks:
- **Regression risk:** Refactoring transport logic may introduce subtle stream lifecycle bugs
- **No CI safety net:** REQ-TEST-3 (CI pipeline) does not exist yet
- **Mitigation:** Implement REQ-TEST-3 first (see [Implementation Order](#9-implementation-order) for recommended sequencing)

---

## 3. REQ-CQ-2: Internal vs Public API Separation

**Gap Status:** PARTIAL
**Effort:** S (<1 day)
**Breaking Change:** Yes (minor -- only if users import internal classes)

### 3.1 Current State

The following classes are `public` but are internal implementation details:

| Class | File | Line | Should Be |
|-------|------|------|-----------|
| `QueueUpstreamHandler` | `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueUpstreamHandler.java` | 14 | Package-private |
| `QueueDownstreamHandler` | `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueDownstreamHandler.java` | 14 | Package-private |
| `QueueDownStreamProcessor` | `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueDownStreamProcessor.java` | 11 | Remove entirely (unused) |
| `EventStreamHelper` | `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventStreamHelper.java` | 14 | Package-private |
| `ChannelDecoder` | `kubemq-java/src/main/java/io/kubemq/sdk/common/ChannelDecoder.java` | 17 | Package-private |
| `KubeMQUtils` | `kubemq-java/src/main/java/io/kubemq/sdk/common/KubeMQUtils.java` | 23 | Package-private |
| `MetadataInterceptor` | `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java` (inner class, line 432) | 432 | Package-private (already inner, but `public`) |

Classes that SHOULD remain public (intentional public API):
- `KubeMQClient`, `PubSubClient`, `QueuesClient`, `CQClient` -- client entry points
- All message types: `EventMessage`, `EventStoreMessage`, `QueueMessage`, `CommandMessage`, `QueryMessage`
- All received message types: `EventMessageReceived`, `EventStoreMessageReceived`, `QueueMessageReceived`, etc.
- All response types: `EventSendResult`, `QueueSendResult`, `CommandResponseMessage`, `QueryResponseMessage`, `QueuesPollResponse`
- All subscription types: `EventsSubscription`, `EventsStoreSubscription`, `CommandsSubscription`, `QueriesSubscription`
- All request types: `QueuesPollRequest`
- All channel/stats types: `PubSubChannel`, `QueuesChannel`, `CQChannel`, `PubSubStats`, `QueuesStats`, `CQStats`
- Enums: `EventsStoreType`, `SubscribeType`, `RequestType`
- `ServerInfo`
- Exception types: `GRPCException`, `CreateChannelException`, `DeleteChannelException`, `ListChannelsException`

### 3.2 Implementation

#### 3.2.1 Remove `QueueDownStreamProcessor`

This class is unused (Assessment 8.4.2). It has a static initializer that starts a thread unconditionally (line 20-36), which is a resource leak if loaded.

**Action:** Delete `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueDownStreamProcessor.java`

Also remove from `KubeMQClient.shutdownAllExecutors()` if referenced (it is not -- the shutdown hook references `QueueDownstreamHandler` and `QueueUpstreamHandler`, not `QueueDownStreamProcessor`).

#### 3.2.2 Change Access Modifiers

For each class listed in section 3.1, change `public class` to `class` (package-private):

**`QueueUpstreamHandler.java` line 14:**
```java
// Before:
public class QueueUpstreamHandler {
// After:
class QueueUpstreamHandler {
```

**`QueueDownstreamHandler.java` line 14:**
```java
// Before:
public class QueueDownstreamHandler {
// After:
class QueueDownstreamHandler {
```

**`EventStreamHelper.java` line 14:**
```java
// Before:
public class EventStreamHelper {
// After:
class EventStreamHelper {
```

**`ChannelDecoder.java` line 17:**
```java
// Before:
public class ChannelDecoder {
// After:
class ChannelDecoder {
```

**`KubeMQUtils.java` line 23:**
```java
// Before:
public class KubeMQUtils {
// After:
class KubeMQUtils {
```

Note: `KubeMQUtils` is used by `QueuesClient` (same-package? No -- `QueuesClient` is in `io.kubemq.sdk.queues`, `KubeMQUtils` is in `io.kubemq.sdk.common`). Making `KubeMQUtils` package-private would break `QueuesClient`. Two options:
1. Move `KubeMQUtils` static methods into each client class that uses them
2. Keep `KubeMQUtils` public but document it as internal via `@ApiStatus.Internal` annotation

**Recommended approach:** Option 1 -- inline the utility methods into the client classes. `KubeMQUtils` is essentially a helper that builds gRPC `Request` objects. After REQ-CQ-1 refactoring, these will move to the transport layer anyway. For now, keep `KubeMQUtils` public but annotate:

```java
/**
 * Internal utility class. Not part of the public API.
 * This class may be moved or removed without notice.
 * @deprecated Internal use only. Will be moved to transport layer.
 */
@Deprecated
public class KubeMQUtils {
```

**`MetadataInterceptor` inner class in KubeMQClient.java line 432:**
```java
// Before:
public class MetadataInterceptor implements ClientInterceptor {
// After:
class MetadataInterceptor implements ClientInterceptor {
```

#### 3.2.3 Static Executor Accessors

The `getCleanupExecutor()` and `getReconnectExecutor()` methods on internal classes are currently `public static` because they are called by `KubeMQClient.shutdownAllExecutors()` (line 144-185) from a different package. After making the handler classes package-private, these accessors need to remain accessible cross-package. Options:
1. Keep the getter methods `public` on the now-package-private classes (they become inaccessible externally because the class itself is package-private)
2. Move executor shutdown coordination to the transport layer

**Recommended:** After REQ-CQ-1 transport extraction, executor lifecycle will be managed by `GrpcTransport.close()`. Until then, keep the static getters -- they are only reachable from within the SDK since the owning classes are package-private.

### 3.3 Public API Surface Documentation

Create a public API inventory document or Javadoc marker. Using the `@ApiStatus.Internal` pattern from JetBrains annotations or a custom annotation:

```java
package io.kubemq.sdk.common;

import java.lang.annotation.*;

/**
 * Marks a type as internal to the SDK. Internal types may be changed
 * or removed without notice in minor releases.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Internal {
}
```

Apply to any type that must remain `public` for cross-package reasons but is not part of the user-facing API.

---

## 4. REQ-CQ-3: Linting and Formatting

**Gap Status:** MISSING
**Effort:** M (1-3 days)
**Breaking Change:** No
**Depends On:** REQ-TEST-3 (CI pipeline) for enforcement

### 4.1 Current State

- No linter configured (Assessment 8.2.1, 9.3.3)
- No formatter plugin (Assessment 8.2.3)
- No Error Prone, Checkstyle, PMD, or SpotBugs in `pom.xml`
- Code appears hand-formatted consistently (Assessment 8.2.3)

### 4.2 Implementation

#### 4.2.1 Error Prone Compiler Plugin

Add to `kubemq-java/pom.xml`, within the existing `maven-compiler-plugin` configuration (lines 147-165):

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.13.0</version>
    <configuration>
        <source>11</source>
        <target>11</target>
        <includes>
            <include>**/*.java</include>
        </includes>
        <compilerArgs>
            <arg>-XDcompilePolicy=simple</arg>
            <arg>-Xplugin:ErrorProne
                -XepDisableWarningsInGeneratedCode
                -XepExcludedPaths:.*/target/generated-sources/.*
            </arg>
        </compilerArgs>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>1.18.36</version>
            </path>
            <path>
                <groupId>com.google.errorprone</groupId>
                <artifactId>error_prone_core</artifactId>
                <version>2.28.0</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

Note: Error Prone requires Java 11+ at compile time, which matches the current `maven.compiler.source` of 11.

**Excluded paths:** `target/generated-sources/` covers protobuf-generated code (from `protobuf-maven-plugin`).

#### 4.2.2 Spotless (google-java-format)

Add Spotless plugin to `pom.xml` `<plugins>` section:

```xml
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <version>2.43.0</version>
    <configuration>
        <java>
            <googleJavaFormat>
                <version>1.22.0</version>
                <style>GOOGLE</style>
            </googleJavaFormat>
            <excludes>
                <exclude>target/generated-sources/**</exclude>
            </excludes>
        </java>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
            <phase>verify</phase>
        </execution>
    </executions>
</plugin>
```

**Initial formatting pass:** Run `mvn spotless:apply` once to format the entire codebase. This will be a single large commit. All subsequent changes are enforced by `spotless:check` in the `verify` phase.

#### 4.2.3 Checkstyle for Javadoc Rules

Add Checkstyle for enforcing Javadoc on public API (ties to REQ-DOC-1):

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <version>3.4.0</version>
    <configuration>
        <configLocation>checkstyle.xml</configLocation>
        <suppressionsLocation>checkstyle-suppressions.xml</suppressionsLocation>
        <consoleOutput>true</consoleOutput>
        <failsOnError>true</failsOnError>
        <excludeGeneratedSources>true</excludeGeneratedSources>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
            <phase>verify</phase>
        </execution>
    </executions>
</plugin>
```

**File:** `kubemq-java/checkstyle.xml` (NEW) -- start with a minimal ruleset:

```xml
<?xml version="1.0"?>
<!DOCTYPE module PUBLIC
    "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
    "https://checkstyle.org/dtds/configuration_1_3.dtd">
<module name="Checker">
    <module name="TreeWalker">
        <module name="JavadocMethod">
            <property name="accessModifiers" value="public"/>
        </module>
        <module name="JavadocType">
            <property name="scope" value="public"/>
        </module>
        <module name="MissingJavadocMethod">
            <property name="scope" value="public"/>
            <property name="allowMissingPropertyJavadoc" value="true"/>
            <!-- Suppress for Lombok-generated methods (getters/setters/builders) -->
            <property name="allowedAnnotations" value="Getter,Setter,Builder,Data,Value"/>
        </module>
    </module>
</module>
```

**File:** `kubemq-java/checkstyle-suppressions.xml` (NEW) -- suppress for generated code:

```xml
<?xml version="1.0"?>
<!DOCTYPE suppressions PUBLIC
    "-//Checkstyle//DTD SuppressionFilter Configuration 1.2//EN"
    "https://checkstyle.org/dtds/suppressions_1_2.dtd">
<suppressions>
    <suppress files="target[\\/]generated-sources[\\/]" checks=".*"/>
</suppressions>
```

#### 4.2.4 CI Integration

When REQ-TEST-3 (CI pipeline) is implemented, add a lint job:

```yaml
# In .github/workflows/ci.yml
lint:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with:
        java-version: '11'
        distribution: 'temurin'
        cache: 'maven'
    - run: mvn verify -DskipTests
```

The `spotless:check` and `checkstyle:check` goals run in the `verify` phase, so `mvn verify` covers both.

---

## 5. REQ-CQ-4: Minimal Dependencies

**Gap Status:** PARTIAL
**Effort:** M (1-2 days)
**Breaking Change:** No (dependency removal is transparent to users unless they relied on transitive deps)

### 5.1 Current State (from `kubemq-java/pom.xml`)

| Dependency | Version | Scope | GS Status |
|-----------|---------|-------|-----------|
| `grpc-netty-shaded` | 1.75.0 | compile | Allowed (gRPC runtime) |
| `grpc-alts` | 1.75.0 | compile | **REMOVE** -- not needed, adds Google auth transitives |
| `grpc-protobuf` | 1.75.0 | compile | Allowed (gRPC runtime) |
| `grpc-stub` | 1.75.0 | compile | Allowed (gRPC runtime) |
| `protobuf-java` | 4.28.2 | compile | Allowed (protobuf runtime) |
| `commons-lang3` | 3.14.0 | compile | **REMOVE** -- zero imports found in source code |
| `lombok` | 1.18.36 | provided | OK (compile-only) |
| `logback-classic` | 1.4.12 | compile | **MOVE to test** -- GS requires no logging framework dep |
| `javax.annotation-api` | 1.3.2 | compile | Justified (required for gRPC generated code on Java 9+) |
| `jackson-databind` | 2.17.0 | compile | Justified (channel list JSON decoding) -- evaluate replacement |

**Direct deps excluding gRPC/protobuf/OTel:** commons-lang3, logback-classic, jackson-databind, javax.annotation-api = 4.
After removal: jackson-databind, javax.annotation-api = 2. Well within the GS limit of 5.

### 5.2 Implementation

#### 5.2.1 Remove `commons-lang3`

Grep confirms zero imports of `org.apache.commons` in `kubemq-java/src/main/java/`. This dependency is unused.

**Action:** Remove from `pom.xml` (lines 71-75):
```xml
<!-- DELETE this block -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-lang3</artifactId>
    <version>3.14.0</version>
</dependency>
```

Verify tests still pass: `mvn test`

#### 5.2.2 Remove `grpc-alts`

`grpc-alts` provides Google ALTS (Application Layer Transport Security) for GCE/GKE environments. KubeMQ does not use ALTS. This dependency pulls in `google-auth-library-oauth2-http` and other Google Cloud dependencies.

**Action:** Remove from `pom.xml` (lines 50-54):
```xml
<!-- DELETE this block -->
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-alts</artifactId>
    <version>${grpc.version}</version>
</dependency>
```

Verify no code imports ALTS types. Grep confirms no `io.grpc.alts` imports in source.

#### 5.2.3 Move `logback-classic` to Test Scope

This is blocked by REQ-OBS-5 (pluggable logger interface). The current code directly references `ch.qos.logback.classic.LoggerContext` in `KubeMQClient.setLogLevel()` (line 391-395). This must be replaced before logback can be test-only.

**Phase 1 (immediate):** Change scope from compile to runtime, and add SLF4J API as explicit dependency:
```xml
<!-- Add SLF4J API explicitly -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.13</version>
</dependency>

<!-- Change logback to test scope -->
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.4.12</version>
    <scope>test</scope>
</dependency>
```

**Phase 1 also requires:** Removing the direct logback dependency in `KubeMQClient.setLogLevel()`:

**Current code (KubeMQClient.java lines 2-3, 390-395):**
```java
import ch.qos.logback.classic.LoggerContext;
// ...
private void setLogLevel() {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    ch.qos.logback.classic.Logger sdkLogger = loggerContext.getLogger("io.kubemq.sdk");
    sdkLogger.setLevel(ch.qos.logback.classic.Level.valueOf(logLevel.name()));
    log.debug("Set SDK log level to: {}", logLevel);
}
```

**Replacement:** Remove `setLogLevel()` entirely. Log level configuration should be the responsibility of the application's logging framework, not the SDK. The `Level` enum and `logLevel` field on `KubeMQClient` become no-ops or are deprecated.

```java
/**
 * @deprecated SDK no longer controls log levels directly. Configure your
 * SLF4J provider (logback, log4j2) instead. This method is a no-op.
 */
@Deprecated
private void setLogLevel() {
    // No-op. Log level is controlled by the application's SLF4J binding.
}
```

**Phase 2 (with REQ-OBS-5):** Replace `@Slf4j` with SDK's own `KubeMQLogger` interface, falling back to SLF4J when available via classpath detection.

#### 5.2.4 Evaluate `jackson-databind` Replacement

Jackson is used **only** in `ChannelDecoder.java` (3 methods, all doing `objectMapper.readValue(...)` for JSON list decoding). The JSON payloads are simple channel metadata from the server.

**Options:**
1. **Keep jackson-databind** -- it works, is well-tested, and channel list operations are infrequent
2. **Replace with `protobuf-java-util`** -- `com.google.protobuf:protobuf-java-util` is a transitive dependency of gRPC and includes `JsonFormat`. However, the channel list response is plain JSON, not protobuf JSON format
3. **Replace with lightweight parser** -- use `javax.json` (part of Jakarta EE) or manual parsing

**Recommendation:** Keep `jackson-databind` for now. It is justified per GS ("Must be justified"). The justification: required for server channel list API which returns JSON, not protobuf. Jackson has frequent CVEs but is widely used and well-maintained. Add OWASP scanning to detect when CVEs arise.

#### 5.2.5 Add OWASP Dependency-Check Plugin

Add to `pom.xml` `<plugins>` section (plugin definition only, no default execution):

```xml
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>10.0.3</version>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>
        <suppressionFiles>
            <suppressionFile>dependency-check-suppressions.xml</suppressionFile>
        </suppressionFiles>
    </configuration>
    <!-- No default execution -- activated via -Psecurity profile only -->
</plugin>
```

**Important:** Do NOT bind OWASP to the default `verify` phase. It downloads the NVD database and takes 1-5 minutes, which would slow every `mvn verify` and `mvn install`. Instead, activate via a Maven profile:

```xml
<profiles>
    <profile>
        <id>security</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.owasp</groupId>
                    <artifactId>dependency-check-maven</artifactId>
                    <executions>
                        <execution>
                            <goals><goal>check</goal></goals>
                            <phase>verify</phase>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

**Usage:** `mvn verify -Psecurity` (CI only). Local builds run without the scan.

**File:** `kubemq-java/dependency-check-suppressions.xml` (NEW) -- initially empty, used to suppress known false positives:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
    <!-- Add suppressions for false positives here -->
</suppressions>
```

#### 5.2.6 Maven BOM for gRPC (from R1 review recommendation)

Add gRPC BOM to `<dependencyManagement>` for consistent version management:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-bom</artifactId>
            <version>${grpc.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then remove explicit `<version>` tags from individual gRPC dependencies.

### 5.3 Post-Implementation Dependency Count

| Dependency | Scope | Counts Toward Limit? |
|-----------|-------|---------------------|
| grpc-netty-shaded | compile | No (gRPC runtime) |
| grpc-protobuf | compile | No (gRPC runtime) |
| grpc-stub | compile | No (gRPC runtime) |
| protobuf-java | compile | No (protobuf runtime) |
| lombok | provided | No (compile-only) |
| slf4j-api | compile | Yes -- 1 |
| jackson-databind | compile | Yes -- 2 |
| javax.annotation-api | compile | Yes -- 3 |

**Total: 3** (well under limit of 5)

---

## 6. REQ-CQ-5: Consistent Code Organization

**Gap Status:** PARTIAL
**Effort:** S (<1 day)
**Breaking Change:** No (new packages only)

### 6.1 Current State

```
kubemq-java/src/main/java/io/kubemq/sdk/
  client/         # KubeMQClient (abstract base)
  common/         # KubeMQUtils, ChannelDecoder, ServerInfo, SubscribeType, RequestType
  cq/             # CQClient, Commands, Queries, Subscriptions
  exception/      # 4 exception classes
  pubsub/         # PubSubClient, Events, EventsStore, Subscriptions
  queues/         # QueuesClient, QueueMessage, Handlers, Subscriptions
```

### 6.2 GS Target Structure

```
kubemq-java/src/main/java/io/kubemq/sdk/
  client/         # KubeMQClient, ClientOptions (existing, enhanced)
  common/         # ServerInfo, SubscribeType, RequestType (existing, reduced)
  cq/             # CQClient, Commands, Queries (existing, preserved)
  exception/      # Error types, classification, exceptions (existing package -- enhanced by REQ-ERR-1)
  pubsub/         # PubSubClient, Events, EventsStore (existing, preserved)
  queues/         # QueuesClient, QueueMessage (existing, preserved)
  transport/      # GrpcTransport, interceptors (NEW -- from REQ-CQ-1)
```

### 6.3 Implementation

#### 6.3.1 New Packages (Non-Breaking)

| Package | Created By | Contents |
|---------|-----------|----------|
| `io.kubemq.sdk.transport` | REQ-CQ-1 | `Transport`, `GrpcTransport`, `TransportConfig`, interceptors |
| `io.kubemq.sdk.exception` | REQ-ERR-1 | `KubeMQException`, error code enum, error classification (enhances existing package) |

These are additive changes -- no existing imports break.

#### 6.3.2 `cq` Package Rename -- DEFERRED

The GS recommends `commands/` and `queries/` instead of `cq/`. Renaming this package would change all import statements for users:

```java
// Before:
import io.kubemq.sdk.cq.CQClient;
// After:
import io.kubemq.sdk.commands.CommandsClient;
```

This is a **MAJOR breaking change** requiring a semver major version bump (v3.0). Per the gap research review (R1 M-14), this is explicitly deferred.

**Action for this version:** No change to `cq` package name.

#### 6.3.3 Extract Duplicated Reconnection Logic

Reconnection logic is duplicated across 4 subscription classes (Assessment 8.2.7):
- `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsSubscription.java` (lines 156-189)
- `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsStoreSubscription.java` (similar pattern)
- `kubemq-java/src/main/java/io/kubemq/sdk/cq/CommandsSubscription.java` (similar pattern)
- `kubemq-java/src/main/java/io/kubemq/sdk/cq/QueriesSubscription.java` (similar pattern)

All four have identical:
- `MAX_RECONNECT_ATTEMPTS = 10`
- Static `reconnectExecutor` with daemon thread
- `reconnect()` method with exponential backoff: `base * 2^(attempt-1)`, capped at 60s
- `resetReconnectAttempts()` method

**Action:** Extract to a shared base or utility:

```java
package io.kubemq.sdk.common;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared reconnection logic for subscription classes.
 * Package-private -- not part of the public API.
 */
@Slf4j
class ReconnectionManager {

    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final long MAX_BACKOFF_MS = 60_000L;

    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final ScheduledExecutorService executor;
    private final long baseIntervalMs;
    private final String channelName;

    ReconnectionManager(ScheduledExecutorService executor, long baseIntervalMs, String channelName) {
        this.executor = executor;
        this.baseIntervalMs = baseIntervalMs;
        this.channelName = channelName;
    }

    /**
     * Schedule a reconnection attempt with exponential backoff.
     *
     * @param reconnectAction the action to perform on reconnection
     * @param onMaxAttemptsReached callback when max attempts exceeded
     */
    void scheduleReconnect(Runnable reconnectAction, Runnable onMaxAttemptsReached) {
        int attempt = reconnectAttempts.incrementAndGet();

        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            log.error("Max reconnection attempts ({}) reached for channel: {}",
                      MAX_RECONNECT_ATTEMPTS, channelName);
            onMaxAttemptsReached.run();
            return;
        }

        long delay = Math.min(baseIntervalMs * (1L << (attempt - 1)), MAX_BACKOFF_MS);

        log.info("Scheduling reconnection attempt {} for channel {} in {}ms",
                 attempt, channelName, delay);

        executor.schedule(() -> {
            try {
                reconnectAction.run();
                reconnectAttempts.set(0);
                log.info("Successfully reconnected to channel {} after {} attempts",
                         channelName, attempt);
            } catch (Exception e) {
                log.error("Reconnection attempt {} failed for channel {}", attempt, channelName, e);
                scheduleReconnect(reconnectAction, onMaxAttemptsReached);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    void resetAttempts() {
        reconnectAttempts.set(0);
    }
}
```

This reduces each subscription class's reconnection logic to a single delegation call.

### 6.4 Acceptance Criteria Status

| Criterion | Current | After Implementation |
|-----------|---------|---------------------|
| Directory structure follows conventions | PARTIAL | COMPLIANT (with new packages) |
| Each messaging pattern has own file/module | COMPLIANT | COMPLIANT |
| Shared types in common location | PARTIAL | COMPLIANT (exception/, common/) |
| No circular dependencies | COMPLIANT | COMPLIANT |
| File names consistent | COMPLIANT | COMPLIANT |

---

## 7. REQ-CQ-6: Code Review Standards

**Gap Status:** NOT_ASSESSED
**Effort:** S (<1 day)
**Breaking Change:** No

### 7.1 Current State

- Single maintainer (Assessment 12.2.6)
- No branch protection rules
- No PR template
- Zero TODO/FIXME in code (COMPLIANT -- Assessment 8.4.1)
- `QueueDownStreamProcessor` appears unused (Assessment 8.4.2)

### 7.2 Implementation

#### 7.2.1 GitHub Branch Protection

Configure via GitHub Settings > Branches > Branch protection rules for `main`:
- Require pull request reviews before merging: 1 approval
- Require status checks to pass before merging (after REQ-TEST-3 CI exists)
- Require branches to be up to date before merging

#### 7.2.2 PR Template

**File:** `kubemq-java/.github/PULL_REQUEST_TEMPLATE.md` (NEW)

```markdown
## Description

<!-- What does this PR change? Why? -->

## Type of Change

- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Dependency update
- [ ] Documentation

## Checklist

- [ ] Tests added/updated for new functionality
- [ ] All existing tests pass (`mvn test`)
- [ ] Breaking changes are documented
- [ ] Javadoc updated for public API changes
- [ ] No credential material in logs or error messages
```

#### 7.2.3 Remove Dead Code

Delete `QueueDownStreamProcessor.java` (covered in REQ-CQ-2, section 3.2.1).

### 7.3 Acceptance Criteria Status

| Criterion | Current | After Implementation |
|-----------|---------|---------------------|
| All PRs require review | MISSING | COMPLIANT |
| PRs include tests | NOT_ASSESSED | COMPLIANT (via template checklist) |
| Breaking changes labeled | NOT_ASSESSED | COMPLIANT (via template) |
| No TODO/FIXME in released code | COMPLIANT | COMPLIANT |
| Dead code removed | PARTIAL | COMPLIANT (QueueDownStreamProcessor deleted) |

---

## 8. REQ-CQ-7: Secure Defaults

**Gap Status:** PARTIAL
**Effort:** S (<1 day)
**Breaking Change:** No

### 8.1 Current State

- Auth token not logged (Assessment 5.2.2) -- COMPLIANT
- Default is plaintext, no TLS warning (Assessment 5.2.1) -- MISSING
- `toString()` methods not audited for credential leaks -- NOT VERIFIED
- Error messages not audited for credential leaks -- NOT VERIFIED

### 8.2 Implementation

#### 8.2.1 Audit `toString()` on Config Classes

The main class holding credentials is `KubeMQClient`. It uses `@Getter` (line 31) but no `@ToString`. The `authToken` field (line 43) is accessible via getter but does not appear in any `toString()` output.

**Verification needed:** Grep for `toString()` on classes holding sensitive data:

- `KubeMQClient` -- no custom `toString()`, Lombok `@Getter` does not generate `toString()`. `@AllArgsConstructor` does not generate `toString()`. **SAFE** -- no toString leaks authToken.
- `TransportConfig` (new, from REQ-CQ-1) -- use `@ToString(exclude = {"authToken"})`:

```java
@Getter
@Builder
@ToString(exclude = {"tokenSupplier"})
public class TransportConfig {
    // ...
    private final java.util.function.Supplier<String> tokenSupplier;
}
```

#### 8.2.2 Audit Error Messages for Credential Leaks

Current exception types are simple message-passing exceptions. No current exception includes credentials. However, the `validateTlsConfiguration()` method in `KubeMQClient.java` (line 219-224) includes file paths in error messages (e.g., "CA certificate file does not exist: " + caFile). File paths are not credentials -- this is acceptable.

**When REQ-ERR-1 error hierarchy is implemented,** the new error classes must explicitly exclude:
- `authToken` from error message templates
- Certificate file contents (not paths) from error messages
- Any credential material from `toString()`, `getMessage()`, or structured fields

Add this requirement to the REQ-ERR-1 spec as a cross-reference.

#### 8.2.3 TLS Warning for InsecureSkipVerify

This is primarily covered by REQ-AUTH-2. When TLS verification is disabled (currently no such option exists in the SDK), log a WARN:

```java
if (insecureSkipVerify) {
    log.warn("TLS certificate verification is disabled. This is insecure and should "
           + "only be used for development/testing. Set InsecureSkipVerify=false for production.");
}
```

**Current state:** The SDK does not have an `InsecureSkipVerify` option. TLS verification is always enabled when TLS is on. This is **already secure by default**. The WARN log is only needed when the option is added (per REQ-AUTH-2).

#### 8.2.4 No Credential Material in OTel Span Attributes

When REQ-OBS-1 is implemented, ensure that `authToken` is never added as a span attribute. This is a forward-looking requirement -- document it as a constraint for the OTel implementation.

### 8.3 Acceptance Criteria Status

| Criterion | Current | After Implementation |
|-----------|---------|---------------------|
| No credential material in log output | COMPLIANT | COMPLIANT |
| No credential material in error messages or OTel | PARTIAL | COMPLIANT (audit + forward-looking constraint) |
| TLS verification enabled by default | COMPLIANT | COMPLIANT |
| Disabling TLS verification produces WARN | MISSING (no option exists) | N/A until REQ-AUTH-2 adds InsecureSkipVerify |

---

## 9. Implementation Order

The following order accounts for dependencies, risk, and the recommendation from R2 review M-6 to establish CI before large refactoring:

### Phase 1: Foundation (Week 1)

| Step | REQ | Action | Effort |
|------|-----|--------|--------|
| 1 | REQ-CQ-6 | Branch protection + PR template | 0.5d |
| 2 | REQ-CQ-4 (partial) | Remove commons-lang3, grpc-alts | 0.5d |
| 3 | REQ-CQ-2 (partial) | Delete QueueDownStreamProcessor, restrict handler access | 0.5d |
| 4 | REQ-CQ-7 | toString() audit, credential leak prevention | 0.5d |
| 5 | REQ-CQ-3 | Add Error Prone + Spotless + Checkstyle; format codebase | 2d |

### Phase 2: Architecture (Weeks 2-3)

| Step | REQ | Action | Effort |
|------|-----|--------|--------|
| 6 | REQ-CQ-5 | Create transport/ package, extract ReconnectionManager | 1d |
| 7 | REQ-CQ-1 | Extract GrpcTransport, AuthInterceptor, Transport interface | 5-8d |
| 8 | REQ-CQ-4 (rest) | Move logback to test scope, add OWASP plugin, add BOM | 1d |

**Critical note on sequencing:** The gap research places REQ-CQ-1 before CI (REQ-TEST-3). The R2 review (M-6) correctly identifies this as risky. **Strongly recommend implementing REQ-TEST-3 (CI pipeline) before starting Phase 2.** The Phase 1 steps can be done safely without CI since they are small, low-risk changes.

---

## 10. Cross-Category Dependencies

### 10.1 Dependencies FROM This Category

| This Spec Item | Depends On | Reason |
|----------------|-----------|--------|
| REQ-CQ-1 (Transport layer) | None | Foundational -- enables all other architectural improvements |
| REQ-CQ-3 (CI enforcement) | REQ-TEST-3 (CI pipeline) | Linter needs CI to block merges |
| REQ-CQ-4 (logback removal) | REQ-OBS-5 (pluggable logger) | Must replace direct logback API calls first |
| REQ-CQ-4 (OWASP in CI) | REQ-TEST-3 (CI pipeline) | Vuln scanning needs CI |
| REQ-CQ-7 (TLS warning) | REQ-AUTH-2 (InsecureSkipVerify) | Warning needed only when option exists |

### 10.2 Dependencies ON This Category

| Other Spec Item | Depends On | Reason |
|-----------------|-----------|--------|
| REQ-ERR-6 (error mapping interceptor) | REQ-CQ-1 | Interceptor chain lives in transport layer |
| REQ-ERR-3 (retry interceptor) | REQ-CQ-1 | Retry interceptor lives in protocol layer |
| REQ-OBS-1 (OTel traces) | REQ-CQ-1 | Tracing interceptor lives in protocol layer |
| REQ-OBS-3 (OTel metrics) | REQ-CQ-1 | Metrics interceptor lives in protocol layer |
| REQ-CONN-1 (reconnection) | REQ-CQ-1 | Reconnection manager lives in transport layer |
| REQ-ERR-1 (error hierarchy) | REQ-CQ-5 | Error types go in existing `exception/` package |

### 10.3 Dependency Graph (A --> B means "B depends on A, do A first")

```
REQ-CQ-1 (architecture) --> REQ-ERR-6 (error mapping interceptor)
REQ-CQ-1 (architecture) --> REQ-ERR-3 (retry interceptor)
REQ-CQ-1 (architecture) --> REQ-OBS-1 (tracing interceptor)
REQ-CQ-1 (architecture) --> REQ-OBS-3 (metrics interceptor)
REQ-CQ-1 (architecture) --> REQ-CONN-1 (reconnection in transport)
REQ-CQ-5 (package structure) --> REQ-ERR-1 (exception/ package)
REQ-TEST-3 (CI) --> REQ-CQ-3 (lint enforcement)
REQ-OBS-5 (logger interface) --> REQ-CQ-4 (logback removal)
REQ-AUTH-2 (InsecureSkipVerify) --> REQ-CQ-7 (TLS warning)
```

---

## 11. GS Internal Inconsistencies

| GS Reference | Inconsistency | Recommendation |
|-------------|---------------|----------------|
| REQ-CQ-5 Java layout | GS shows `commands/` and `queries/` as separate packages. Current SDK has `cq/` combining both. | Defer rename to v3.0 major version. Adding `commands/` and `queries/` as aliases is not idiomatic Java. |
| REQ-CQ-5 vs REQ-CQ-1 | GS layout shows `internal/` package in Java structure, but REQ-CQ-2 says "Package-private classes (no `public` modifier)" is the Java mechanism. Java `internal/` packages have no compiler enforcement unlike Go. | Use package-private access modifiers (REQ-CQ-2 approach). Do not create an `internal/` package -- it provides no enforcement in Java and adds confusing structure. |
| GS Maven coordinates | GS states `io.kubemq:kubemq-sdk-java`. Current pom.xml has `io.kubemq.sdk:kubemq-sdk-Java`. | The current coordinates are published to Maven Central and cannot be changed without a new artifact. Flag as a future major version consideration. |

---

## 12. Future Enhancements

### 12.1 Java Module System (JPMS)

From R1 review: adding `module-info.java` would provide compile-time enforcement of internal API separation. This is significant effort and many libraries still skip JPMS. Recommend as a v3.0 consideration:

```java
module io.kubemq.sdk {
    exports io.kubemq.sdk.client;
    exports io.kubemq.sdk.pubsub;
    exports io.kubemq.sdk.queues;
    exports io.kubemq.sdk.cq;
    exports io.kubemq.sdk.common;
    exports io.kubemq.sdk.exception;

    requires io.grpc;
    requires io.grpc.stub;
    requires com.google.protobuf;
    requires static lombok;
    requires org.slf4j;

    // Internal packages not exported
    // io.kubemq.sdk.transport is not exported
}
```

### 12.2 Lombok Migration to Records

Java 16+ records could replace `@Value`/`@Data` on immutable message types. This is a major API change (removes setters) and must be deferred to v3.0. See R1 review recommendation #2.

### 12.3 Jackson Replacement

If OWASP scanning reveals persistent Jackson CVEs, consider replacing with:
- `protobuf-java-util` `JsonFormat` (already a transitive dep)
- Minimal JSON parsing library
- Manual JSON parsing (the channel list JSON structure is simple)

### 12.4 `cq` Package Rename

Defer to v3.0 as documented in section 6.3.2.

---

## 13. Test Plan

### 13.1 REQ-CQ-1 Tests (Layered Architecture)

| Test | Type | Validates |
|------|------|-----------|
| `GrpcTransportTest` -- connect/disconnect lifecycle | Unit | Transport layer isolation |
| `AuthInterceptorTest` -- token injection | Unit | Protocol layer interceptor |
| `GrpcTransportTest` -- TLS configuration variants | Unit | Transport handles all TLS configs |
| Architecture dependency test: no `io.grpc` imports in client/pubsub/cq/queues packages | Build-time | Layer dependency direction |
| Integration: send/receive through Transport interface | Integration | End-to-end through new layers |

**Architecture enforcement test** (add to build):

Create a test or ArchUnit rule that verifies no classes outside `io.kubemq.sdk.transport` import `io.grpc.*`:

```java
@Test
void publicApiLayerDoesNotImportGrpc() {
    JavaClasses importedClasses = new ClassFileImporter()
        .importPackages("io.kubemq.sdk");

    ArchRule rule = noClasses()
        .that().resideInAnyPackage(
            "io.kubemq.sdk.client..",
            "io.kubemq.sdk.pubsub..",
            "io.kubemq.sdk.cq..",
            "io.kubemq.sdk.queues..",
            "io.kubemq.sdk.common..",
            "io.kubemq.sdk.exception..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("io.grpc..", "kubemq..");

    rule.check(importedClasses);
}
```

Add ArchUnit as a test dependency:
```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.3.0</version>
    <scope>test</scope>
</dependency>
```

### 13.2 REQ-CQ-2 Tests (API Separation)

| Test | Type | Validates |
|------|------|-----------|
| Compile test: internal classes not accessible from external package | Unit | Package-private enforcement |
| Reflection test: list all public classes, assert only expected ones | Unit | Public API surface contract |

### 13.3 REQ-CQ-3 Tests (Linting)

| Test | Type | Validates |
|------|------|-----------|
| `mvn spotless:check` passes | Build | Formatting enforced |
| `mvn checkstyle:check` passes | Build | Javadoc rules enforced |
| Error Prone compilation succeeds | Build | Static analysis passes |
| CI lint job blocks on violations | CI | Enforcement in pipeline |

### 13.4 REQ-CQ-4 Tests (Dependencies)

| Test | Type | Validates |
|------|------|-----------|
| `mvn dependency:tree` shows no commons-lang3, no grpc-alts | Build | Removed deps are gone |
| `mvn dependency:tree` shows logback only in test scope | Build | Logback scope change |
| `mvn dependency-check:check` passes | Build | No known vulnerabilities |
| All existing tests pass after dependency changes | Unit | No regressions |

### 13.5 REQ-CQ-7 Tests (Secure Defaults)

| Test | Type | Validates |
|------|------|-----------|
| `TransportConfig.toString()` does not contain authToken value | Unit | Credential exclusion |
| Error message construction does not include token values | Unit | No credential leaks in errors |
| Log output during connection does not contain authToken | Unit/Integration | No credential leaks in logs |

```java
@Test
void transportConfigToStringExcludesAuthToken() {
    TransportConfig config = TransportConfig.builder()
        .address("localhost:50000")
        .authToken("secret-token-value")
        .build();

    String str = config.toString();
    assertFalse(str.contains("secret-token-value"),
        "toString() must not contain auth token value");
}
```

---

## Appendix A: Files Modified/Created Summary

### New Files

| File | REQ | Purpose |
|------|-----|---------|
| `kubemq-java/src/main/java/io/kubemq/sdk/transport/Transport.java` | CQ-1 | Transport interface |
| `kubemq-java/src/main/java/io/kubemq/sdk/transport/GrpcTransport.java` | CQ-1 | gRPC transport impl |
| `kubemq-java/src/main/java/io/kubemq/sdk/transport/TransportConfig.java` | CQ-1 | Transport configuration |
| `kubemq-java/src/main/java/io/kubemq/sdk/transport/AuthInterceptor.java` | CQ-1 | Auth token interceptor |
| `kubemq-java/src/main/java/io/kubemq/sdk/transport/ErrorMappingInterceptor.java` | CQ-1, ERR-6 | gRPC error mapping |
| `kubemq-java/src/main/java/io/kubemq/sdk/common/ReconnectionManager.java` | CQ-5 | Shared reconnect logic |
| `kubemq-java/src/main/java/io/kubemq/sdk/common/Internal.java` | CQ-2 | @Internal annotation |
| `kubemq-java/checkstyle.xml` | CQ-3 | Checkstyle config |
| `kubemq-java/checkstyle-suppressions.xml` | CQ-3 | Checkstyle suppressions |
| `kubemq-java/dependency-check-suppressions.xml` | CQ-4 | OWASP suppressions |
| `kubemq-java/.github/PULL_REQUEST_TEMPLATE.md` | CQ-6 | PR template |

### Modified Files

| File | REQ | Change |
|------|-----|--------|
| `kubemq-java/pom.xml` | CQ-3, CQ-4 | Add plugins, remove deps, add BOM |
| `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java` | CQ-1, CQ-2, CQ-4 | Replace gRPC with Transport, deprecate setLogLevel() |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueUpstreamHandler.java` | CQ-2 | Make package-private |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueDownstreamHandler.java` | CQ-2 | Make package-private |
| `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventStreamHelper.java` | CQ-2 | Make package-private |
| `kubemq-java/src/main/java/io/kubemq/sdk/common/ChannelDecoder.java` | CQ-2 | Make package-private |
| `kubemq-java/src/main/java/io/kubemq/sdk/common/KubeMQUtils.java` | CQ-2 | Deprecate as internal |
| `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsSubscription.java` | CQ-5 | Use ReconnectionManager |
| `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsStoreSubscription.java` | CQ-5 | Use ReconnectionManager |
| `kubemq-java/src/main/java/io/kubemq/sdk/cq/CommandsSubscription.java` | CQ-5 | Use ReconnectionManager |
| `kubemq-java/src/main/java/io/kubemq/sdk/cq/QueriesSubscription.java` | CQ-5 | Use ReconnectionManager |

### Deleted Files

| File | REQ | Reason |
|------|-----|--------|
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueDownStreamProcessor.java` | CQ-2, CQ-6 | Unused dead code |
