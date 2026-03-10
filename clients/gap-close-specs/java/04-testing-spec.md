# Implementation Specification: Category 04 -- Testing

**SDK:** KubeMQ Java v2
**Category:** 04 -- Testing
**GS Source:** `clients/golden-standard/04-testing.md`
**Gap Research:** `clients/gap-research/java-gap-research.md` (lines 628-753)
**Assessment:** `clients/assesments/JAVA_ASSESSMENT_REPORT.md` (Category 9, lines 517-556)
**Review R1:** `clients/gap-research/java-review-r1.md` (M-9, M-3)
**Review R2:** `clients/gap-research/java-review-r2.md` (M-3 leak detection idiom)
**Current Score:** 3.25 / 5.0 | **Target:** 4.0+
**Priority:** P0 (Tier 1 gate blocker)
**Total Estimated Effort:** 12-18 days

---

## Table of Contents

1. [Summary of REQ Items](#1-summary-of-req-items)
2. [Implementation Order](#2-implementation-order)
3. [REQ-TEST-1: Unit Tests with Mocked Transport](#3-req-test-1-unit-tests-with-mocked-transport)
4. [REQ-TEST-2: Integration Tests Against Real Server](#4-req-test-2-integration-tests-against-real-server)
5. [REQ-TEST-3: CI Pipeline](#5-req-test-3-ci-pipeline)
6. [REQ-TEST-4: Test Organization](#6-req-test-4-test-organization)
7. [REQ-TEST-5: Coverage Tools](#7-req-test-5-coverage-tools)
8. [Cross-Category Dependencies](#8-cross-category-dependencies)
9. [New Test Dependencies (pom.xml)](#9-new-test-dependencies-pomxml)
10. [Breaking Changes](#10-breaking-changes)
11. [Open Questions](#11-open-questions)

---

## 1. Summary of REQ Items

| REQ | Status | Gap | Effort | Impl Order |
|-----|--------|-----|--------|------------|
| REQ-TEST-1 | PARTIAL | Error classification tests, retry tests, leak detection, timeouts, parameterized tests, closed-client guard tests | L (3-5 days) | 2 |
| REQ-TEST-2 | PARTIAL | Auth failure, reconnection, timeout, buffer overflow integration tests; cleanup improvements | L (3-5 days) | 3 |
| REQ-TEST-3 | MISSING | Full CI pipeline (lint, unit matrix, integration, coverage) | M (1-3 days) | 1 |
| REQ-TEST-4 | PARTIAL | Skip mechanism, testutil package | S (< 1 day) | 1 (with TEST-3) |
| REQ-TEST-5 | PARTIAL | JaCoCo `check` goal, Codecov upload, threshold enforcement | S (< 1 day) | 1 (with TEST-3) |

---

## 1.1 Prerequisites (Review R1 X-1)

> This spec assumes REQ-ERR-1, REQ-ERR-2, and REQ-ERR-3 from `01-error-handling-spec.md` are
> implemented. Types referenced: `GrpcErrorMapper`, `KubeMQException`, `ErrorCategory`,
> `ClientClosedException`, `RetryPolicy`. If these types do not yet exist when test
> development begins, use the fallback stubs documented in each section. Remove fallback
> code once error handling is in place.

> **Test logger note (Review R1 X-2):** New test classes do NOT need to use `KubeMQLogger`
> from 05-observability-spec. Test classes can use SLF4J directly (`@Slf4j`) since Logback
> remains in test scope. Only production code migrates to `KubeMQLogger`.

---

## 2. Implementation Order

The order is driven by the principle that CI infrastructure should exist **before** adding new tests so that regressions are caught immediately. This addresses the risk identified in Review R2 M-6 (performing work without CI safety net).

**Phase 1 -- CI Infrastructure (days 1-3):**
1. REQ-TEST-4: Create `testutil` package, add `skipITs` property
2. REQ-TEST-5: Add JaCoCo `check` goal with Phase 2 threshold (0.60)
3. REQ-TEST-3: Create `.github/workflows/ci.yml`

**Phase 2 -- Unit Test Gaps (days 3-8):**
4. REQ-TEST-1: Error classification tests (depends on REQ-ERR-1, REQ-ERR-2 from `01-error-handling-spec.md`)
5. REQ-TEST-1: Retry policy tests (depends on REQ-ERR-3)
6. REQ-TEST-1: Closed-client guard, leak detection, timeout annotations, parameterized tests

**Phase 3 -- Integration Test Gaps (days 8-13):**
7. REQ-TEST-2: Auth failure integration tests (depends on REQ-AUTH-1 from `03-auth-security-spec.md`)
8. REQ-TEST-2: Reconnection integration tests (depends on REQ-CONN-1, REQ-CONN-2 from `02-connection-transport-spec.md`)
9. REQ-TEST-2: Timeout, buffer overflow, cleanup improvements

---

## 3. REQ-TEST-1: Unit Tests with Mocked Transport

**Gap Status:** PARTIAL
**GS Reference:** 04-testing.md, REQ-TEST-1
**Assessment Evidence:** 9.1.1 (795 unit tests), 9.1.2 (75.1% coverage), 9.1.4 (Mockito mocking), 9.1.5 (no parameterized tests)
**Effort:** L (3-5 days)

### 3.1 Current State

The SDK has 47 unit test files at `kubemq-java/src/test/java/io/kubemq/sdk/unit/` with 795 tests passing at 75.1% coverage. Mockito is used for gRPC stub mocking. Tests cover validation, encode/decode, error paths, TLS config, builders, and production readiness scenarios (concurrency, handler timeouts, reconnection recursion, visibility timer exceptions).

**Existing test structure:**

| Package | Files | Coverage Focus |
|---------|-------|----------------|
| `unit/client/` | 3 | Builder validation, TLS config, channel operations |
| `unit/common/` | 4 | Channel decoder, enums, utils, server info |
| `unit/cq/` | 8 | Command/Query message types, subscriptions, stats |
| `unit/exception/` | 1 | Exception types |
| `unit/pubsub/` | 10 | Event messages, subscriptions, stream helper, stats |
| `unit/queues/` | 12 | Queue messages, handlers, client, poll, stats |
| `unit/productionreadiness/` | 5 | Concurrency, handler timeout, logger/TLS, reconnection, visibility |

### 3.2 Acceptance Criteria Mapping

| # | GS Acceptance Criterion | Current Status | Implementation Required |
|---|------------------------|----------------|------------------------|
| AC-1 | Coverage meets phased target (Phase 1: 40%) | COMPLIANT | 75.1% exceeds Phase 1 and Phase 2 targets |
| AC-2 | All error classification paths tested | MISSING | Add tests for all 17 gRPC status code mappings per REQ-ERR-6 |
| AC-3 | All retry scenarios tested | MISSING | Add retry policy unit tests (first try, retry success, exhaustion, non-retryable bypass) |
| AC-4 | Config validation tested | COMPLIANT | `BuilderValidationTests` exist in `KubeMQClientTest.java` |
| AC-5 | Coverage threshold enforced in CI | MISSING | REQ-TEST-5 adds JaCoCo `check` goal; REQ-TEST-3 adds CI |
| AC-6 | Client close + leak check | MISSING | Add resource leak detection per Java idioms |
| AC-7 | Operations on closed client return `ErrClientClosed` | MISSING | Add closed-client guard tests |
| AC-8 | Oversized messages produce validation error | PARTIAL | Body size validated at 100MB; explicit test needed |
| AC-9 | Empty/nil payloads handled correctly | MISSING | Add null/empty payload tests |
| AC-10 | Per-test timeout (30s unit, 60s integration) | MISSING | Add `@Timeout` annotations |
| AC-11 | Concurrent publish from multiple threads does not corrupt state | PARTIAL | `EventStreamHelperConcurrencyTest` exists; add explicit no-corruption assertion |

### 3.3 Implementation Specification

#### 3.3.1 New Test Dependency: gRPC InProcessServer

The GS specifies `InProcessServer` as the Java mock pattern (GS 04-testing.md, mock approach table). This is superior to the current Mockito stub mocking because it tests the full gRPC interceptor chain, including the error mapping interceptor from REQ-ERR-6 and auth interceptor from REQ-AUTH-1 (Review R2 recommendation).

**Add to `pom.xml`:**

```xml
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-testing</artifactId>
    <version>${grpc.version}</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-inprocess</artifactId>
    <version>${grpc.version}</version>
    <scope>test</scope>
</dependency>
```

**Note:** Existing Mockito-based tests remain valid and do not need migration. `InProcessServer` is used for **new** tests that exercise the gRPC interceptor chain. Both patterns coexist.

> **Scope clarification (Review R1 M-1):** `InProcessServer` tests exercise server-side interceptors
> and response behavior, NOT client-side `ManagedChannel` construction, TLS handshake, or
> `MetadataInterceptor` behavior. For client-side interceptor chain testing:
> - Register client interceptors on the `InProcessChannel` via `InProcessChannelBuilder.intercept()`
>   to test interceptors in isolation.
> - Separate unit tests (using Mockito) should verify individual interceptor behavior (e.g.,
>   `MetadataInterceptor` injects auth token, error mapping interceptor transforms gRPC status).
> - Integration tests against a real server (REQ-TEST-2) exercise the full end-to-end path
>   including TLS and channel construction.

#### 3.3.2 Error Classification Tests (depends on REQ-ERR-2, REQ-ERR-6)

**File:** `kubemq-java/src/test/java/io/kubemq/sdk/unit/error/ErrorClassificationTest.java` (NEW)

Use `@ParameterizedTest` with `@EnumSource` to test all 17 gRPC status codes map to the correct SDK error category and `isRetryable()` flag. This addresses AC-2 and assessment 9.1.5 (no parameterized tests).

```java
package io.kubemq.sdk.unit.error;

import io.grpc.Status;
import io.kubemq.sdk.error.ErrorCategory;
import io.kubemq.sdk.error.GrpcErrorMapper;
import io.kubemq.sdk.error.KubeMQException;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests gRPC status code to SDK error category mapping per REQ-ERR-6.
 * Each gRPC code must map to exactly one ErrorCategory with correct isRetryable flag.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ErrorClassificationTest {

    /**
     * Setup: Each gRPC Status.Code is an enum value.
     * Action: Map each code through GrpcErrorMapper.
     * Assert: Category matches the REQ-ERR-6 mapping table; isRetryable matches.
     */
    @ParameterizedTest(name = "gRPC {0} maps to correct SDK error category")
    @EnumSource(Status.Code.class)
    void grpcStatusCode_mapsToCorrectCategory(Status.Code code) {
        // Act
        KubeMQException exception = GrpcErrorMapper.map(
                Status.fromCode(code)
                    .withDescription("test error")
                    .asRuntimeException(),
                "testOperation",
                "testChannel"
        );

        // Assert -- category is non-null
        assertNotNull(exception.getCategory(),
                "gRPC code " + code + " must map to a non-null ErrorCategory");

        // Assert -- retryable flag is consistent with category
        switch (code) {
            // Retryable codes per REQ-ERR-6 mapping table
            case UNAVAILABLE:
            case DEADLINE_EXCEEDED:
            case RESOURCE_EXHAUSTED:
            case ABORTED:
                assertTrue(exception.isRetryable(),
                        code + " must be retryable");
                break;
            // Non-retryable codes
            case INVALID_ARGUMENT:
            case NOT_FOUND:
            case ALREADY_EXISTS:
            case PERMISSION_DENIED:
            case UNAUTHENTICATED:
            case FAILED_PRECONDITION:
            case OUT_OF_RANGE:
            case UNIMPLEMENTED:
            case INTERNAL:
            case DATA_LOSS:
            case CANCELLED:
            case UNKNOWN:
            case OK:
                assertFalse(exception.isRetryable(),
                        code + " must not be retryable");
                break;
        }
    }
}
```

**Note on class references:** `GrpcErrorMapper`, `KubeMQException`, `ErrorCategory` are types defined in `01-error-handling-spec.md` (REQ-ERR-1, REQ-ERR-2, REQ-ERR-6). If those types do not yet exist when test development begins, create stub interfaces/classes with TODO markers. See Section 8 for stub strategy.

#### 3.3.3 Retry Policy Unit Tests (depends on REQ-ERR-3)

**File:** `kubemq-java/src/test/java/io/kubemq/sdk/unit/error/RetryPolicyTest.java` (NEW)

```java
package io.kubemq.sdk.unit.error;

import io.kubemq.sdk.error.RetryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RetryPolicy per REQ-ERR-3.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class RetryPolicyTest {

    // --- Scenario 1: Success on first try ---

    /**
     * Setup: Create a RetryPolicy with maxRetries=3.
     * Action: Execute an operation that succeeds on the first attempt.
     * Assert: Operation result is returned; retry count is 0.
     */
    @Test
    void successOnFirstTry_noRetries() {
        // implementation
    }

    // --- Scenario 2: Success on retry ---

    /**
     * Setup: Create a RetryPolicy with maxRetries=3.
     * Action: Execute an operation that fails twice then succeeds.
     * Assert: Operation result is returned; retry count is 2.
     */
    @Test
    void successOnThirdAttempt_retriesTwice() {
        // implementation
    }

    // --- Scenario 3: Exhaustion ---

    /**
     * Setup: Create a RetryPolicy with maxRetries=3.
     * Action: Execute an operation that fails on all 4 attempts (1 initial + 3 retries).
     * Assert: RetryExhaustedException thrown; wraps the last error; attempt count is 4.
     */
    @Test
    void allAttemptsFail_throwsRetryExhaustedException() {
        // implementation
    }

    // --- Scenario 4: Non-retryable error bypasses retry ---

    /**
     * Setup: Create a RetryPolicy with maxRetries=3.
     * Action: Execute an operation that throws a non-retryable error (e.g., INVALID_ARGUMENT).
     * Assert: Error propagated immediately; retry count is 0.
     */
    @Test
    void nonRetryableError_propagatesImmediately() {
        // implementation
    }

    // --- Scenario 5: Backoff calculation ---

    /**
     * Setup: Create a RetryPolicy with initialBackoff=100ms, maxBackoff=5s, multiplier=2.0.
     * Action: Calculate backoff for attempts 1, 2, 3, 4.
     * Assert: Backoff values are 100ms, 200ms, 400ms, 800ms (within jitter tolerance).
     *         All values are <= maxBackoff.
     */
    @Test
    void backoffCalculation_followsExponentialWithCap() {
        // implementation
    }

    // --- Scenario 6: Jitter applied ---

    /**
     * Setup: Create a RetryPolicy with jitter enabled.
     * Action: Calculate backoff 100 times for the same attempt number.
     * Assert: Not all values are identical (jitter introduces variation).
     *         All values are within [0, baseBackoff * 2] range.
     */
    @Test
    void jitter_producesVariation() {
        // implementation
    }

    // --- Scenario 7: Independent backoff policies ---

    /**
     * Setup: Create separate RetryPolicy for operation retry and reconnection.
     * Action: Configure different backoff parameters for each.
     * Assert: Operation retry policy and reconnection policy calculate independently.
     *         (Per REQ-ERR-3 GS body text: independent policies with independent configuration.)
     */
    @Test
    void operationRetryAndReconnection_areIndependentPolicies() {
        // implementation
    }
}
```

#### 3.3.4 Closed-Client Guard Tests (depends on REQ-CONN-4)

**File:** `kubemq-java/src/test/java/io/kubemq/sdk/unit/client/ClosedClientTest.java` (NEW)

```java
package io.kubemq.sdk.unit.client;

import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.error.ClientClosedException;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.QueuesClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every operation on a closed client must throw ClientClosedException.
 * Tests per AC-7 of REQ-TEST-1.
 *
 * Review R1 M-2: These tests must NOT create real gRPC channels to localhost:50000.
 * Use the Transport interface from 07-code-quality-spec.md REQ-CQ-1 (mock transport),
 * or InProcessChannelBuilder if Transport is not yet available. Creating real channels
 * in unit tests is fragile, environment-dependent, and may create IDLE connections.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ClosedClientTest {

    // Helper method to create a client with mock/in-process transport.
    // When Transport interface (07-spec REQ-CQ-1) is available, use mock transport.
    // Otherwise, use InProcessChannelBuilder.forName("test").directExecutor().build()
    // private PubSubClient createTestClient() { ... }

    /**
     * Setup: Create a PubSubClient (via mock transport), then close it.
     * Action: Call sendEventsMessage().
     * Assert: Throws ClientClosedException.
     */
    @Test
    void pubSubClient_sendAfterClose_throwsClientClosedException() {
        // Review R1 M-2: Use mock transport or InProcessChannel, not real channel
        PubSubClient client = PubSubClient.builder()
                .address("localhost:50000")
                .clientId("test")
                .build(); // TODO: Replace with mock transport once REQ-CQ-1 lands
        client.close();

        assertThrows(ClientClosedException.class, () -> {
            client.sendEventsMessage(/* minimal EventMessage */);
        });
    }

    /**
     * Setup: Create a QueuesClient, then close it.
     * Action: Call sendQueuesMessage().
     * Assert: Throws ClientClosedException.
     */
    @Test
    void queuesClient_sendAfterClose_throwsClientClosedException() {
        // implementation
    }

    /**
     * Setup: Create a CQClient, then close it.
     * Action: Call sendCommandRequest().
     * Assert: Throws ClientClosedException.
     */
    @Test
    void cqClient_sendAfterClose_throwsClientClosedException() {
        // implementation
    }

    /**
     * Setup: Create a PubSubClient, then close it.
     * Action: Call subscribeToEvents().
     * Assert: Throws ClientClosedException.
     */
    @Test
    void pubSubClient_subscribeAfterClose_throwsClientClosedException() {
        // implementation
    }

    /**
     * Setup: Create any client, close it twice.
     * Action: Call close() a second time.
     * Assert: Does not throw (idempotent close per REQ-CONN-4).
     */
    @Test
    void close_calledTwice_doesNotThrow() {
        // Review R1 M-2: Use mock transport or InProcessChannel, not real channel
        PubSubClient client = PubSubClient.builder()
                .address("localhost:50000")
                .clientId("test")
                .build(); // TODO: Replace with mock transport once REQ-CQ-1 lands
        client.close();
        assertDoesNotThrow(() -> client.close());
    }
}
```

**Note:** `ClientClosedException` is a type from `01-error-handling-spec.md` (REQ-ERR-1). The closed-client guard itself is specified in `02-connection-transport-spec.md` (REQ-CONN-4, AC "Operations on closed client return ErrClientClosed").

#### 3.3.5 Resource Leak Detection Tests

**File:** `kubemq-java/src/test/java/io/kubemq/sdk/unit/client/ResourceLeakTest.java` (NEW)

Per Review R2 M-3, raw `Thread.getAllStackTraces().size()` is unreliable due to non-deterministic JVM daemon threads. Use targeted Java idioms instead.

```java
package io.kubemq.sdk.unit.client;

import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.QueuesClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Resource leak detection per REQ-TEST-1 AC-6.
 *
 * Java-specific approach (per Review R2 M-3):
 * 1. gRPC leaks: assert ManagedChannel.isShutdown() and isTerminated()
 * 2. Executor leaks: assert ExecutorService.isTerminated()
 * 3. SDK thread leaks: filter by thread name prefix "kubemq-"
 * 4. Deadlock detection: ThreadMXBean.findDeadlockedThreads()
 *
 * Review R1 M-3: The SDK currently does NOT name any threads with a "kubemq-" prefix.
 * gRPC creates threads with names like "grpc-default-executor-*" and "grpc-timer-*".
 * SDK-created executors (e.g., in QueueMessageReceived) use default JVM thread names.
 *
 * PREREQUISITE: Before thread-name-based leak detection can work, add named ThreadFactory
 * instances to all SDK-created executors:
 *   - QueueMessageReceived visibility timer: "kubemq-visibility-timer"
 *   - QueueUpstreamHandler cleanup task: "kubemq-upstream-cleanup"
 *   - QueueDownstreamHandler: "kubemq-downstream-handler"
 *   - Any other SDK-created scheduled/pooled executors
 *
 * Example: Executors.newSingleThreadScheduledExecutor(
 *     r -> new Thread(r, "kubemq-visibility-timer"))
 *
 * If the thread naming prerequisite is not yet met, use approach 1/2 (channel/executor
 * state assertions) as the primary leak detection mechanism, and skip thread-name filtering.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ResourceLeakTest {

    // Review R1 M-3: This prefix requires SDK executors to use named ThreadFactory.
    // See prerequisite note above.
    private static final String SDK_THREAD_PREFIX = "kubemq-";

    /**
     * Setup: Record SDK-specific threads (prefix "kubemq-") before creating client.
     * Action: Create PubSubClient, use it, close it, wait for termination.
     * Assert: No new SDK-specific threads remain after close.
     */
    @Test
    void pubSubClient_afterClose_noLeakedSdkThreads() {
        Set<String> threadsBefore = getSdkThreadNames();

        // Review R1 M-2: Use mock transport or InProcessChannel in production impl
        PubSubClient client = PubSubClient.builder()
                .address("localhost:50000")
                .clientId("leak-test")
                .build(); // TODO: Replace with mock transport once REQ-CQ-1 lands
        client.close();

        // Allow threads to terminate
        awaitTermination(2000);

        Set<String> threadsAfter = getSdkThreadNames();
        threadsAfter.removeAll(threadsBefore);

        assertTrue(threadsAfter.isEmpty(),
                "Leaked SDK threads after close: " + threadsAfter);
    }

    /**
     * Setup: Create and close a client.
     * Action: Check for JVM deadlocks.
     * Assert: ThreadMXBean.findDeadlockedThreads() returns null (no deadlocks).
     */
    @Test
    void afterClientLifecycle_noDeadlockedThreads() {
        CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("deadlock-test")
                .build();
        client.close();

        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();
        assertNull(deadlockedThreads,
                "Deadlocked threads detected after client lifecycle");
    }

    /**
     * Setup: Create and close a QueuesClient.
     * Action: Access internal ManagedChannel via reflection (test-only).
     * Assert: Channel isShutdown() == true and isTerminated() == true.
     */
    @Test
    void queuesClient_afterClose_channelTerminated() {
        // Use reflection to access the internal ManagedChannel
        // Assert isShutdown() and isTerminated()
        // implementation
    }

    private Set<String> getSdkThreadNames() {
        return Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .filter(name -> name.startsWith(SDK_THREAD_PREFIX))
                .collect(Collectors.toSet());
    }

    private void awaitTermination(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

#### 3.3.6 Oversized Message and Empty Payload Tests

**File:** `kubemq-java/src/test/java/io/kubemq/sdk/unit/common/MessageValidationTest.java` (NEW)

```java
package io.kubemq.sdk.unit.common;

import io.kubemq.sdk.pubsub.EventMessage;
import io.kubemq.sdk.queues.QueueMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Message validation edge cases per REQ-TEST-1 AC-8 and AC-9.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class MessageValidationTest {

    // --- AC-8: Oversized messages ---

    /**
     * Setup: Create a message with body exceeding MaxSendMessageSize (100MB).
     * Action: Attempt to validate/send the message.
     * Assert: Validation error thrown (not a transport error).
     */
    @Test
    void eventMessage_exceedsMaxSize_throwsValidationError() {
        byte[] oversizedBody = new byte[100 * 1024 * 1024 + 1]; // 100MB + 1 byte
        // Build EventMessage with oversizedBody
        // Assert that validation/send throws a validation error
    }

    /**
     * Setup: Create a queue message with body exceeding MaxSendMessageSize.
     * Action: Attempt to validate/send.
     * Assert: Validation error thrown.
     */
    @Test
    void queueMessage_exceedsMaxSize_throwsValidationError() {
        // implementation
    }

    // --- AC-9: Empty/null payloads ---

    /**
     * Setup: Create an EventMessage with null body.
     * Action: Validate/encode the message.
     * Assert: Either succeeds (empty body is valid) or throws descriptive validation error.
     *         Must not throw NullPointerException.
     */
    @Test
    void eventMessage_nullBody_handledGracefully() {
        assertDoesNotThrow(() -> {
            EventMessage msg = EventMessage.builder()
                    .channel("test-channel")
                    .body(null)
                    .build();
            // encode or validate
        });
    }

    /**
     * Setup: Create an EventMessage with empty byte array body.
     * Action: Validate/encode the message.
     * Assert: Succeeds (empty body is valid per protobuf semantics).
     */
    @Test
    void eventMessage_emptyBody_succeeds() {
        assertDoesNotThrow(() -> {
            EventMessage msg = EventMessage.builder()
                    .channel("test-channel")
                    .body(new byte[0])
                    .build();
            // encode or validate
        });
    }

    /**
     * Setup: Create a QueueMessage with null body and null metadata.
     * Action: Validate/encode.
     * Assert: Handled gracefully.
     */
    @Test
    void queueMessage_nullBodyAndMetadata_handledGracefully() {
        // implementation
    }
}
```

#### 3.3.7 Per-Test Timeout Annotations

Add `@Timeout(value = 30, unit = TimeUnit.SECONDS)` to **all existing** unit test classes. This addresses AC-10 and detects hangs.

**Implementation approach:** Rather than editing 47 files individually, configure a global default timeout in `src/test/resources/junit-platform.properties`:

**File:** `kubemq-java/src/test/resources/junit-platform.properties` (NEW)

```properties
# Default timeout for all unit tests (30 seconds) per REQ-TEST-1 AC-10
junit.jupiter.execution.timeout.default = 30s
# Default timeout for integration tests (60 seconds)
# Integration tests can override with @Timeout(60)
junit.jupiter.execution.timeout.testmethod.default = 30s
```

Individual integration test classes override this with `@Timeout(value = 60, unit = TimeUnit.SECONDS)`.

> **Review R1 m-2:** The `junit-platform.properties` global timeout applies to all test classes
> including the 47 existing ones. No per-file `@Timeout` annotation retrofit is needed for
> existing tests in Phase 1. If any existing test consistently exceeds 30s, add an explicit
> `@Timeout` override on that specific class during Phase 2.

#### 3.3.8 Concurrent Publish No-Corruption Test Enhancement

The existing `EventStreamHelperConcurrencyTest` tests thread safety of the `EventStreamHelper` but does not explicitly assert that no state corruption occurs (shared data integrity assertion). Enhance:

**File:** `kubemq-java/src/test/java/io/kubemq/sdk/unit/productionreadiness/EventStreamHelperConcurrencyTest.java` (MODIFY)

Add a test method:

```java
/**
 * Setup: Create an EventStreamHelper with mocked stub.
 * Action: Publish 100 messages from 10 concurrent threads (1000 total).
 * Assert: All 1000 messages have unique request IDs in the pending map.
 *         No duplicate IDs. No lost messages. No ConcurrentModificationException.
 */
@Test
void concurrentPublish_noStateCorruption() {
    int threadCount = 10;
    int messagesPerThread = 100;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger errorCount = new AtomicInteger(0);

    // Submit all tasks, synchronized by start latch
    for (int t = 0; t < threadCount; t++) {
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int m = 0; m < messagesPerThread; m++) {
                    // Publish via helper -- implementation
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                errorCount.incrementAndGet();
            }
        });
    }

    startLatch.countDown(); // release all threads simultaneously
    executor.shutdown();
    assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

    assertEquals(threadCount * messagesPerThread, successCount.get(),
            "All messages must succeed without corruption");
    assertEquals(0, errorCount.get(),
            "No errors expected from concurrent publish");
}
```

#### 3.3.9 InProcessServer-Based gRPC Integration Chain Test

**File:** `kubemq-java/src/test/java/io/kubemq/sdk/unit/transport/GrpcInterceptorChainTest.java` (NEW)

This tests the full interceptor chain (error mapping, auth token injection, retry) against an in-process gRPC server. This is distinct from Mockito-based tests that mock individual stubs.

```java
package io.kubemq.sdk.unit.transport;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import kubemq.kubemqGrpc;
import kubemq.Kubemq;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the full gRPC interceptor chain using InProcessServer.
 * Validates that error mapping, auth token injection, and retry behavior
 * work correctly as an integrated chain.
 *
 * Depends on: REQ-ERR-6 (error mapping interceptor), REQ-AUTH-1 (auth interceptor)
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class GrpcInterceptorChainTest {

    private Server server;
    private ManagedChannel channel;

    @BeforeEach
    void setUp() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new FakeKubeMQService())
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * Setup: InProcessServer that returns UNAVAILABLE for first call, OK for second.
     * Action: Send a request through the interceptor chain with retry enabled.
     * Assert: First call fails (UNAVAILABLE), retry succeeds, result returned.
     *         Error is mapped through GrpcErrorMapper before retry decision.
     */
    @Test
    void unavailableResponse_triggersRetryThroughChain() {
        // implementation
    }

    /**
     * Setup: InProcessServer that returns UNAUTHENTICATED.
     * Action: Send a request through the chain.
     * Assert: Error is mapped to AuthenticationException (not retried).
     */
    @Test
    void unauthenticatedResponse_mapsToAuthException_noRetry() {
        // implementation
    }

    /**
     * Setup: InProcessServer; client configured with auth token.
     * Action: Send a request.
     * Assert: Server receives the auth token in gRPC metadata headers.
     */
    @Test
    void authToken_injectedIntoMetadata() {
        // implementation -- verify via FakeKubeMQService that captures headers
    }

    /**
     * Fake KubeMQ gRPC service for testing.
     * Configurable to return specific status codes.
     */
    static class FakeKubeMQService extends kubemqGrpc.kubemqImplBase {
        // Configurable response behavior for testing
    }
}
```

### 3.4 Test Summary for REQ-TEST-1

| New Test File | Test Count (approx) | Depends On |
|---------------|---------------------|------------|
| `unit/error/ErrorClassificationTest.java` | 17 (one per gRPC code) | REQ-ERR-1, REQ-ERR-2, REQ-ERR-6 |
| `unit/error/RetryPolicyTest.java` | 7 | REQ-ERR-3 |
| `unit/client/ClosedClientTest.java` | 5 | REQ-CONN-4 |
| `unit/client/ResourceLeakTest.java` | 3 | None |
| `unit/common/MessageValidationTest.java` | 5 | None |
| `unit/transport/GrpcInterceptorChainTest.java` | 3 | REQ-ERR-6, REQ-AUTH-1 |
| Modified: `EventStreamHelperConcurrencyTest.java` | +1 | None |
| `junit-platform.properties` (config) | N/A | None |
| **Total new tests** | **~41** | |

---

## 4. REQ-TEST-2: Integration Tests Against Real Server

**Gap Status:** PARTIAL
**GS Reference:** 04-testing.md, REQ-TEST-2
**Assessment Evidence:** 9.2.1 (5 integration test files), 9.2.2 (all 4 patterns), 9.2.3 (limited error scenarios), 9.2.4 (some cleanup), 9.2.5 (UUID channels, no parallel config)
**Effort:** L (3-5 days)

### 4.1 Current State

5 integration test files exist at `kubemq-java/src/test/java/io/kubemq/sdk/integration/`:

| File | Coverage |
|------|----------|
| `BaseIntegrationTest.java` | Shared setup: address config, UUID channel names, connection verification |
| `PubSubIntegrationTest.java` | Events and Events Store happy paths |
| `QueuesIntegrationTest.java` | Queue send/receive/ack happy paths |
| `DlqIntegrationTest.java` | Dead letter queue scenarios |
| `CQIntegrationTest.java` | Commands and Queries happy paths |

**What is missing:**
- Auth failure integration tests (invalid token)
- Timeout scenario tests
- Reconnection integration tests (server restart, state transitions)
- Buffer overflow tests
- Subscription re-establishment tests
- Consistent `@AfterEach` cleanup across all tests
- `@Timeout(60)` on integration test classes

### 4.2 Acceptance Criteria Mapping

| # | GS Acceptance Criterion | Current Status | Implementation Required |
|---|------------------------|----------------|------------------------|
| AC-1 | Integration tests for all 4 messaging patterns | COMPLIANT | Already covered |
| AC-2 | Clearly separated from unit tests | COMPLIANT | Failsafe plugin with `**/integration/**` includes |
| AC-3 | Skippable when no server available | PARTIAL | Add `@EnabledIfEnvironmentVariable` or `-DskipITs` |
| AC-4 | Each test independent | PARTIAL | UUID channels provide isolation; improve cleanup |
| AC-5 | Tests clean up resources | PARTIAL | Add consistent `@AfterEach` cleanup |
| AC-6 | Unsubscribe while in-flight completes without leaks | MISSING | Depends on unsubscribe API (REQ-CONN-4) |
| AC-7 | Unique channel names per test | COMPLIANT | `uniqueChannel()` in `BaseIntegrationTest` |

### 4.3 Implementation Specification

#### 4.3.1 Server Provisioning: Testcontainers

**File:** `kubemq-java/src/test/java/io/kubemq/sdk/integration/KubeMQContainer.java` (NEW)

```java
package io.kubemq.sdk.integration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Testcontainers wrapper for KubeMQ server.
 * Used for CI integration tests and reconnection testing (start/stop lifecycle).
 */
public class KubeMQContainer extends GenericContainer<KubeMQContainer> {

    private static final String IMAGE = "kubemq/kubemq:latest";
    private static final int GRPC_PORT = 50000;
    private static final int REST_PORT = 9090;
    private static final int API_PORT = 8080;

    public KubeMQContainer() {
        super(IMAGE);
        withExposedPorts(GRPC_PORT, REST_PORT, API_PORT);
        withEnv("KUBEMQ_TOKEN", ""); // No license token needed for testing
        waitingFor(Wait.forListeningPort());
    }

    public String getGrpcAddress() {
        return getHost() + ":" + getMappedPort(GRPC_PORT);
    }
}
```

**Add to `pom.xml`:**

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>
```

#### 4.3.2 Auth Failure Integration Test

**File:** `kubemq-java/src/test/java/io/kubemq/sdk/integration/AuthFailureIT.java` (NEW)

```java
package io.kubemq.sdk.integration;

import io.kubemq.sdk.error.AuthenticationException;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.pubsub.EventMessage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for auth failure scenarios.
 * Requires a KubeMQ server configured with auth token validation.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@EnabledIfEnvironmentVariable(named = "KUBEMQ_ADDRESS", matches = ".+")
class AuthFailureIT extends BaseIntegrationTest {

    /**
     * Setup: KubeMQ server with auth enabled; client with invalid token.
     * Action: Attempt to send an event message.
     * Assert: AuthenticationException thrown (not a generic RuntimeException).
     */
    @Test
    void sendWithInvalidToken_throwsAuthenticationException() {
        PubSubClient client = PubSubClient.builder()
                .address(kubemqAddress)
                .clientId(uniqueClientId("auth-fail"))
                .authToken("invalid-token-xyz")
                .build();

        try {
            EventMessage msg = EventMessage.builder()
                    .channel(uniqueChannel("auth-fail"))
                    .body("test".getBytes())
                    .build();

            assertThrows(AuthenticationException.class, () -> {
                client.sendEventsMessage(msg);
            });
        } finally {
            client.close();
        }
    }

    /**
     * Setup: KubeMQ server with auth enabled; client with no token.
     * Action: Attempt to subscribe.
     * Assert: AuthenticationException thrown.
     */
    @Test
    void subscribeWithoutToken_throwsAuthenticationException() {
        // implementation
    }
}
```

#### 4.3.3 Reconnection Integration Test

**File:** `kubemq-java/src/test/java/io/kubemq/sdk/integration/ReconnectionIT.java` (NEW)

```java
package io.kubemq.sdk.integration;

import io.kubemq.sdk.client.ConnectionState;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.pubsub.EventMessage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for reconnection behavior per REQ-CONN-1.
 * Uses Testcontainers to control server lifecycle (start/stop/restart).
 *
 * Depends on: REQ-CONN-1 (reconnection), REQ-CONN-2 (state machine with callbacks)
 */
@Testcontainers
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@EnabledIfEnvironmentVariable(named = "KUBEMQ_INTEGRATION_TESTS", matches = "true")
class ReconnectionIT {

    @Container
    private KubeMQContainer kubemq = new KubeMQContainer();

    // --- Scenario 1: State transitions on server restart ---

    /**
     * Setup: Start KubeMQ via Testcontainers. Create client with state callback.
     * Action: Stop the container. Wait for state change. Start the container again.
     * Assert: State transitions observed: READY -> RECONNECTING -> READY.
     *         Assert on state transitions (not wall-clock timing per GS).
     */
    @Test
    void serverRestart_stateTransitions_readyReconnectingReady() throws Exception {
        List<ConnectionState> stateHistory =
                Collections.synchronizedList(new ArrayList<>());
        CountDownLatch reconnectedLatch = new CountDownLatch(1);

        PubSubClient client = PubSubClient.builder()
                .address(kubemq.getGrpcAddress())
                .clientId(uniqueClientId("recon"))
                .onStateChange(state -> {
                    stateHistory.add(state);
                    if (state == ConnectionState.READY && stateHistory.size() > 1) {
                        reconnectedLatch.countDown();
                    }
                })
                .build();

        try {
            // Verify initial READY
            assertEquals(ConnectionState.READY, stateHistory.get(stateHistory.size() - 1));

            // Stop server
            kubemq.stop();

            // Wait for reconnection state
            Thread.sleep(2000); // Allow keepalive detection

            // Restart server
            kubemq.start();

            // Wait for READY state
            assertTrue(reconnectedLatch.await(30, TimeUnit.SECONDS),
                    "Client did not reconnect within 30s");

            // Assert state transition sequence contains RECONNECTING
            assertTrue(stateHistory.contains(ConnectionState.RECONNECTING),
                    "Expected RECONNECTING in state history: " + stateHistory);
            assertEquals(ConnectionState.READY,
                    stateHistory.get(stateHistory.size() - 1),
                    "Final state must be READY");
        } finally {
            client.close();
        }
    }

    // --- Scenario 2: Message buffering during reconnection ---

    /**
     * Setup: Start KubeMQ. Create client with message buffering enabled.
     * Action: Stop server. Publish N messages (buffered). Restart server.
     * Assert: All N messages delivered after reconnection.
     */
    @Test
    void messageBuffering_duringReconnection_messagesDeliveredAfterReconnect() {
        // implementation -- depends on REQ-CONN-1 message buffering
    }

    // --- Scenario 3: Buffer overflow ---

    /**
     * Setup: Create client with buffer capacity of 10 messages.
     * Action: Stop server. Publish 11 messages.
     * Assert: 11th message produces BufferFullError.
     */
    @Test
    void bufferOverflow_producesBufferFullError() {
        // implementation -- depends on REQ-CONN-1 BufferOverflowPolicy
    }

    // --- Scenario 4: Subscription re-establishment ---

    /**
     * Setup: Subscribe to events channel. Verify messages received.
     * Action: Stop server. Restart server.
     * Assert: Subscription is automatically re-established.
     *         New messages after restart are received without re-subscribing.
     */
    @Test
    void subscriptionReestablishment_afterServerRestart() {
        // implementation -- depends on REQ-CONN-1 subscription recovery
    }

    private String uniqueClientId(String prefix) {
        return prefix + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private String uniqueChannel(String prefix) {
        return prefix + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
```

#### 4.3.4 Timeout Integration Test

**File:** `kubemq-java/src/test/java/io/kubemq/sdk/integration/TimeoutIT.java` (NEW)

```java
package io.kubemq.sdk.integration;

import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.cq.CommandMessage;
import io.kubemq.sdk.error.TimeoutException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for timeout behavior per REQ-ERR-4.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@EnabledIfEnvironmentVariable(named = "KUBEMQ_ADDRESS", matches = ".+")
class TimeoutIT extends BaseIntegrationTest {

    /**
     * Setup: Send a command to a channel with no responder.
     * Action: Wait for per-operation timeout to expire.
     * Assert: TimeoutException thrown within expected duration.
     */
    @Test
    void commandWithNoResponder_throwsTimeoutException() {
        CQClient client = CQClient.builder()
                .address(kubemqAddress)
                .clientId(uniqueClientId("timeout"))
                .build();

        try {
            CommandMessage cmd = CommandMessage.builder()
                    .channel(uniqueChannel("timeout-cmd"))
                    .body("test".getBytes())
                    .timeout(2) // 2 second timeout
                    .build();

            assertThrows(TimeoutException.class, () -> {
                client.sendCommandRequest(cmd);
            });
        } finally {
            client.close();
        }
    }
}
```

#### 4.3.5 Cleanup Improvements to Existing Integration Tests

Modify all existing integration test classes to add consistent `@AfterEach` cleanup:

**Files to modify:**
- `kubemq-java/src/test/java/io/kubemq/sdk/integration/PubSubIntegrationTest.java`
- `kubemq-java/src/test/java/io/kubemq/sdk/integration/QueuesIntegrationTest.java`
- `kubemq-java/src/test/java/io/kubemq/sdk/integration/DlqIntegrationTest.java`
- `kubemq-java/src/test/java/io/kubemq/sdk/integration/CQIntegrationTest.java`

**Pattern to add/ensure in each:**

```java
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@EnabledIfEnvironmentVariable(named = "KUBEMQ_ADDRESS", matches = ".+")
class XxxIntegrationTest extends BaseIntegrationTest {

    private List<AutoCloseable> resources = new ArrayList<>();

    @AfterEach
    void cleanupResources() {
        for (AutoCloseable resource : resources) {
            try {
                resource.close();
            } catch (Exception e) {
                // log and continue cleanup
            }
        }
        resources.clear();
    }

    // Track all created clients/subscriptions:
    private <T extends AutoCloseable> T track(T resource) {
        resources.add(resource);
        return resource;
    }
}
```

### 4.4 Test Summary for REQ-TEST-2

| New/Modified Test File | Test Count (approx) | Depends On |
|------------------------|---------------------|------------|
| `integration/KubeMQContainer.java` | N/A (test utility) | None |
| `integration/AuthFailureIT.java` | 2 | REQ-AUTH-1 |
| `integration/ReconnectionIT.java` | 4 | REQ-CONN-1, REQ-CONN-2 |
| `integration/TimeoutIT.java` | 1 | REQ-ERR-4 |
| Modified: `PubSubIntegrationTest.java` | 0 (cleanup only) | None |
| Modified: `QueuesIntegrationTest.java` | 0 (cleanup only) | None |
| Modified: `DlqIntegrationTest.java` | 0 (cleanup only) | None |
| Modified: `CQIntegrationTest.java` | 0 (cleanup only) | None |
| **Total new tests** | **~7** | |

---

## 5. REQ-TEST-3: CI Pipeline

**Gap Status:** MISSING
**GS Reference:** 04-testing.md, REQ-TEST-3
**Assessment Evidence:** 9.3.1-9.3.5 (all score 1 -- no CI exists)
**Effort:** M (1-3 days)

### 5.1 Current State

No `.github/workflows/` directory exists. No CI configuration of any kind (assessment 9.3.1). No linter, no multi-version testing, no security scanning.

### 5.2 Acceptance Criteria Mapping

| # | GS Acceptance Criterion | Current Status | Implementation Required |
|---|------------------------|----------------|------------------------|
| AC-1 | CI runs on every PR and push to main | MISSING | Create GitHub Actions workflow |
| AC-2 | Unit tests across 2-3 language versions | MISSING | Matrix: Java 11, 17, 21 |
| AC-3 | Integration tests against real server in CI | MISSING | KubeMQ Docker service container |
| AC-4 | Linter blocks merge on violations | MISSING | Depends on REQ-CQ-3 (07-code-quality-spec.md) |
| AC-5 | Coverage reported to Codecov | MISSING | JaCoCo report + Codecov upload |
| AC-6 | Coverage threshold enforced per phase | MISSING | JaCoCo `check` goal (REQ-TEST-5) |

### 5.3 Implementation Specification

#### 5.3.1 GitHub Actions Workflow

**File:** `.github/workflows/ci.yml` (NEW)

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

# Cancel in-progress runs for the same branch/PR
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  lint:
    name: Lint
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: 'maven'

      # Linting per REQ-CQ-3 (07-code-quality-spec.md)
      # Error Prone runs as part of compilation
      # Spotless checks formatting
      - name: Check formatting (Spotless)
        working-directory: kubemq-java
        run: mvn spotless:check -B

      - name: Compile with Error Prone
        working-directory: kubemq-java
        run: mvn compile -B -Perror-prone

  unit-tests:
    name: Unit Tests (Java ${{ matrix.java }})
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        java: ['11', '17', '21']
    steps:
      - uses: actions/checkout@v4

      - name: Set up Java ${{ matrix.java }}
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: ${{ matrix.java }}
          cache: 'maven'

      - name: Run unit tests
        working-directory: kubemq-java
        run: mvn test -B

      - name: Upload JaCoCo report
        if: matrix.java == '17'
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: kubemq-java/target/site/jacoco/

  integration:
    name: Integration Tests
    runs-on: ubuntu-latest
    needs: unit-tests
    services:
      kubemq:
        image: kubemq/kubemq:latest
        ports:
          - 50000:50000
          - 9090:9090
          - 8080:8080
        options: >-
          --health-cmd "curl -f http://localhost:8080/health || exit 1"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: 'maven'

      - name: Run integration tests
        working-directory: kubemq-java
        env:
          KUBEMQ_ADDRESS: localhost:50000
        run: mvn verify -B -DskipUnitTests=true

  coverage:
    name: Coverage
    runs-on: ubuntu-latest
    needs: unit-tests
    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: 'maven'

      - name: Run tests with coverage
        working-directory: kubemq-java
        run: mvn test jacoco:report jacoco:check -B

      - name: Upload to Codecov
        uses: codecov/codecov-action@v4
        with:
          token: ${{ secrets.CODECOV_TOKEN }}
          files: kubemq-java/target/site/jacoco/jacoco.xml
          flags: unittests
          fail_ci_if_error: false
```

**Notes on the workflow:**

1. **Lint job** depends on REQ-CQ-3 from `07-code-quality-spec.md`. If Spotless/Error Prone are not yet configured, the lint job will be a no-op placeholder until REQ-CQ-3 is implemented. The workflow structure is ready.

2. **Java version matrix:** 11, 17, 21. Java 11 is the current `maven.compiler.source`/`target` in pom.xml. Java 17 and 21 are the two most recent LTS releases. Tests are compiled targeting Java 11 but run on all three runtimes to verify compatibility.

3. **Integration job:** Uses GitHub Actions `services:` block for KubeMQ Docker container. This provides a real server without Testcontainers overhead. Testcontainers is used for tests that need server lifecycle control (restart/stop).

4. **Maven cache:** `setup-java@v4` with `cache: 'maven'` caches `~/.m2/repository` per Review R2 recommendation about CI build time.

5. **Coverage job:** Runs `jacoco:check` to enforce the threshold (REQ-TEST-5). Uploads XML report to Codecov.

#### 5.3.2 Dependabot Configuration

**File:** `.github/dependabot.yml` (NEW)

```yaml
version: 2
updates:
  - package-ecosystem: "maven"
    directory: "/kubemq-java"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 10
    labels:
      - "dependencies"
    groups:
      grpc:
        patterns:
          - "io.grpc:*"
      testing:
        patterns:
          - "org.junit*"
          - "org.mockito*"
          - "org.testcontainers*"
          - "org.awaitility*"

  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
    labels:
      - "ci"
```

### 5.4 Maven Configuration Changes for CI

Add the `-DskipUnitTests` property to Surefire for the integration-only CI job:

**File:** `kubemq-java/pom.xml` (MODIFY Surefire plugin section)

Add to Surefire `<configuration>`:

```xml
<skipTests>${skipUnitTests}</skipTests>
```

And in `<properties>`:

```xml
<skipUnitTests>false</skipUnitTests>
```

This allows `mvn verify -DskipUnitTests=true` to run only integration tests.

---

## 6. REQ-TEST-4: Test Organization

**Gap Status:** PARTIAL
**GS Reference:** 04-testing.md, REQ-TEST-4
**Assessment Evidence:** 9.2.1 (*Test.java vs *IT.java naming)
**Effort:** S (< 1 day)

### 6.1 Current State

- Unit tests: `kubemq-java/src/test/java/io/kubemq/sdk/unit/**/*Test.java` (Surefire)
- Integration tests: `kubemq-java/src/test/java/io/kubemq/sdk/integration/**/*Test.java` and `**/*IT.java` (Failsafe)
- `BaseIntegrationTest` provides shared setup (address config, UUID channel names)
- No formal `testutil` package
- No `-DskipIntegrationTests` property documented

### 6.2 Acceptance Criteria Mapping

| # | GS Acceptance Criterion | Current Status | Implementation Required |
|---|------------------------|----------------|------------------------|
| AC-1 | Unit/integration in separate dirs or tagged | COMPLIANT | `unit/` vs `integration/` directories |
| AC-2 | Default test command runs only unit tests | COMPLIANT | `mvn test` runs Surefire (unit only) |
| AC-3 | Integration tests require explicit flag/env var | PARTIAL | Add `-DskipITs` and `@EnabledIfEnvironmentVariable` |
| AC-4 | Test helpers in testutil package | PARTIAL | Create `testutil` package |

### 6.3 Implementation Specification

#### 6.3.1 Integration Test Skip Mechanism

**File:** `kubemq-java/pom.xml` (MODIFY Failsafe plugin)

Add to Failsafe `<configuration>`:

```xml
<skipITs>${skipITs}</skipITs>
```

Add to `<properties>`:

```xml
<skipITs>false</skipITs>
```

This allows:
- `mvn verify` -- runs both unit and integration tests
- `mvn verify -DskipITs=true` -- runs only unit tests during verify phase
- `mvn test` -- runs only unit tests (Surefire, already configured)

Additionally, add `@EnabledIfEnvironmentVariable(named = "KUBEMQ_ADDRESS", matches = ".+")` to all integration test classes so they self-skip when no server is configured (already shown in Section 4.3).

#### 6.3.2 Test Utilities Package

**Directory:** `kubemq-java/src/test/java/io/kubemq/sdk/testutil/` (NEW)

**File:** `kubemq-java/src/test/java/io/kubemq/sdk/testutil/TestChannelNames.java` (NEW)

```java
package io.kubemq.sdk.testutil;

import java.util.UUID;

/**
 * Utility for generating unique channel and client names in tests.
 * Prevents test interference when running in parallel.
 */
public final class TestChannelNames {

    private TestChannelNames() {} // utility class

    /**
     * Creates a unique channel name: "{prefix}-{pattern}-{uuid8}".
     * Example: "test-events-a1b2c3d4"
     */
    public static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Creates a unique client ID: "{prefix}-client-{uuid8}".
     */
    public static String uniqueClientId(String prefix) {
        return prefix + "-client-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
```

**File:** `kubemq-java/src/test/java/io/kubemq/sdk/testutil/TestAssertions.java` (NEW)

```java
package io.kubemq.sdk.testutil;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Shared assertion utilities for SDK tests.
 */
public final class TestAssertions {

    private static final String SDK_THREAD_PREFIX = "kubemq-";

    private TestAssertions() {}

    /**
     * Asserts no SDK-specific threads are running (leaked).
     * Filters by thread name prefix "kubemq-".
     */
    public static void assertNoSdkThreadLeaks() {
        Set<String> sdkThreads = Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .filter(name -> name.startsWith(SDK_THREAD_PREFIX))
                .collect(Collectors.toSet());
        assertTrue(sdkThreads.isEmpty(),
                "Leaked SDK threads: " + sdkThreads);
    }

    /**
     * Asserts no JVM deadlocks exist.
     */
    public static void assertNoDeadlocks() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long[] deadlocked = bean.findDeadlockedThreads();
        assertNull(deadlocked, "Deadlocked threads detected");
    }

    /**
     * Asserts the exception is of the expected SDK error type and contains the expected message substring.
     */
    public static void assertSdkException(Exception e, Class<?> expectedType, String messageSubstring) {
        assertTrue(expectedType.isInstance(e),
                "Expected " + expectedType.getSimpleName() + " but got " + e.getClass().getSimpleName());
        if (messageSubstring != null) {
            assertTrue(e.getMessage().contains(messageSubstring),
                    "Expected message containing '" + messageSubstring + "' but got: " + e.getMessage());
        }
    }
}
```

**File:** `kubemq-java/src/test/java/io/kubemq/sdk/testutil/MockGrpcServer.java` (NEW)

```java
package io.kubemq.sdk.testutil;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.BindableService;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Reusable in-process gRPC server for unit tests.
 * Wraps InProcessServerBuilder/InProcessChannelBuilder for convenience.
 *
 * Usage:
 * <pre>
 * MockGrpcServer server = MockGrpcServer.create(new FakeKubeMQService());
 * ManagedChannel channel = server.getChannel();
 * // ... run tests ...
 * server.shutdown();
 * </pre>
 */
public final class MockGrpcServer implements AutoCloseable {

    private final Server server;
    private final ManagedChannel channel;

    private MockGrpcServer(Server server, ManagedChannel channel) {
        this.server = server;
        this.channel = channel;
    }

    /**
     * Creates and starts an in-process gRPC server with the given service implementation.
     */
    public static MockGrpcServer create(BindableService... services) throws IOException {
        String serverName = InProcessServerBuilder.generateName();
        InProcessServerBuilder builder = InProcessServerBuilder.forName(serverName)
                .directExecutor();
        for (BindableService service : services) {
            builder.addService(service);
        }
        Server server = builder.build().start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        return new MockGrpcServer(server, channel);
    }

    public ManagedChannel getChannel() {
        return channel;
    }

    public Server getServer() {
        return server;
    }

    @Override
    public void close() throws Exception {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
}
```

---

## 7. REQ-TEST-5: Coverage Tools

**Gap Status:** PARTIAL
**GS Reference:** 04-testing.md, REQ-TEST-5
**Assessment Evidence:** 9.1.2 (JaCoCo configured, 75.1% coverage, no CI enforcement)
**Effort:** S (< 1 day)

### 7.1 Current State

JaCoCo Maven plugin 0.8.11 is configured in `kubemq-java/pom.xml` with:
- `prepare-agent` execution (generates `jacoco.agent.argLine` property)
- `report` execution in `test` phase

**What is missing:**
- No `check` goal (threshold enforcement)
- No Codecov upload (no CI)
- Protobuf exclusion not explicitly verified

### 7.2 Acceptance Criteria Mapping

| # | GS Acceptance Criterion | Current Status | Implementation Required |
|---|------------------------|----------------|------------------------|
| AC-1 | Coverage tool configured and runs in CI | PARTIAL | JaCoCo configured; CI missing |
| AC-2 | Coverage report in standard format | COMPLIANT | JaCoCo generates standard XML/HTML reports |
| AC-3 | Coverage uploaded to Codecov | MISSING | Codecov upload in CI (REQ-TEST-3) |
| AC-4 | Generated/vendored code excluded | COMPLIANT | Protobuf excluded per assessment 9.1.2 |

### 7.3 Implementation Specification

#### 7.3.1 JaCoCo `check` Goal with Phased Threshold

**File:** `kubemq-java/pom.xml` (MODIFY JaCoCo plugin section)

Add `check` execution after the existing `report` execution:

```xml
<execution>
    <id>check</id>
    <phase>test</phase>
    <goals>
        <goal>check</goal>
    </goals>
    <configuration>
        <rules>
            <rule>
                <element>BUNDLE</element>
                <limits>
                    <limit>
                        <counter>INSTRUCTION</counter>
                        <value>COVEREDRATIO</value>
                        <!--
                            Phase 1: 0.40 (testable architecture in place)
                            Phase 2: 0.60 (mock infrastructure complete)
                            Phase 3: 0.80 (mature / production-ready)
                            Current: Phase 2 target (Review R1 m-1: 75.1% already exceeds Phase 2).
                            Update this value as the SDK matures through phases.
                        -->
                        <minimum>0.60</minimum>
                    </limit>
                </limits>
            </rule>
        </rules>
        <excludes>
            <!-- Exclude generated protobuf code -->
            <exclude>kubemq/Kubemq*</exclude>
            <exclude>kubemq/kubemqGrpc*</exclude>
        </excludes>
    </configuration>
</execution>
```

**Phased threshold update plan:**

| Phase | Threshold | When to Update |
|-------|-----------|----------------|
| Phase 2 | `<minimum>0.60</minimum>` | Now (Review R1 m-1: 75.1% already exceeds Phase 2, start here to prevent regression) |
| Phase 3 | `<minimum>0.80</minimum>` | After full test suite including OTel, auth, all error paths |

**Note (Review R1 m-1):** The current 75.1% coverage already exceeds Phase 2. The threshold starts at Phase 2 (0.60) rather than Phase 1 (0.40) because the lower threshold would provide no meaningful regression protection. Even with new production code from Batch 1 specs temporarily reducing coverage, 60% provides adequate headroom while still catching significant coverage drops. Raise to 0.80 (Phase 3) once the new tests from this spec are complete.

#### 7.3.2 Codecov Upload

Handled in the CI workflow (Section 5.3.1, `coverage` job). The `codecov/codecov-action@v4` reads the JaCoCo XML report at `kubemq-java/target/site/jacoco/jacoco.xml`.

#### 7.3.3 Codecov Configuration

**File:** `codecov.yml` (NEW, at repository root)

```yaml
codecov:
  require_ci_to_pass: true

coverage:
  precision: 2
  round: down
  range: "40...80"

  status:
    project:
      default:
        # Phase 2 threshold (Review R1 m-1) -- update as SDK matures
        target: 60%
        threshold: 2%
    patch:
      default:
        target: 60%

ignore:
  - "kubemq-java/src/main/java/kubemq/**"  # Generated protobuf
```

---

## 8. Cross-Category Dependencies

### 8.1 Inbound Dependencies (this spec depends on)

| Dependency | From Spec | Reason | Blocking? |
|-----------|-----------|--------|-----------|
| REQ-ERR-1 (typed error hierarchy) | `01-error-handling-spec.md` | `KubeMQException`, `ClientClosedException`, `AuthenticationException`, `TimeoutException`, `ErrorCategory` types needed for test assertions | Yes for REQ-TEST-1 sections 3.3.2, 3.3.3, 3.3.4 |
| REQ-ERR-2 (error classification) | `01-error-handling-spec.md` | Error category enum for parameterized tests | Yes for REQ-TEST-1 section 3.3.2 |
| REQ-ERR-3 (retry policy) | `01-error-handling-spec.md` | `RetryPolicy` class for retry unit tests | Yes for REQ-TEST-1 section 3.3.3 |
| REQ-ERR-6 (gRPC mapping) | `01-error-handling-spec.md` | `GrpcErrorMapper` for classification tests | Yes for REQ-TEST-1 section 3.3.2 |
| REQ-ERR-4 (per-operation timeouts) | `01-error-handling-spec.md` | `TimeoutException` for timeout integration test | Yes for REQ-TEST-2 section 4.3.4 |
| REQ-CONN-1 (reconnection) | `02-connection-transport-spec.md` | Reconnection behavior for integration tests | Yes for REQ-TEST-2 section 4.3.3 |
| REQ-CONN-2 (state machine) | `02-connection-transport-spec.md` | `ConnectionState` enum and callbacks for reconnection tests | Yes for REQ-TEST-2 section 4.3.3 |
| REQ-CONN-4 (graceful shutdown) | `02-connection-transport-spec.md` | Post-close guard behavior for closed-client tests | Yes for REQ-TEST-1 section 3.3.4 |
| REQ-AUTH-1 (token auth) | `03-auth-security-spec.md` | Auth token injection for interceptor chain test and auth failure IT | Yes for REQ-TEST-1 section 3.3.9, REQ-TEST-2 section 4.3.2 |
| REQ-CQ-3 (linting) | `07-code-quality-spec.md` | Spotless and Error Prone for lint CI job | Yes for REQ-TEST-3 lint job |

### 8.2 Outbound Dependencies (other specs depend on this)

| Dependency | To Spec | Reason |
|-----------|---------|--------|
| CI pipeline | All specs | All other specs implicitly depend on CI existing to enforce quality gates |
| JaCoCo threshold | `07-code-quality-spec.md` | REQ-CQ-3 lint job defined in CI workflow |
| Integration test infrastructure | `02-connection-transport-spec.md` | Reconnection tests validate CONN-1 behavior |

### 8.3 Stub Strategy for Blocked Dependencies

Tests that depend on types from `01-error-handling-spec.md` can proceed before the full error hierarchy exists by using stub types:

```java
// Temporary stubs in src/test/java/io/kubemq/sdk/testutil/stubs/
// These compile-only stubs allow test structure to be written early.
// Replace with real types when REQ-ERR-1 is implemented.

// Option A: Write test code with TODO markers
@Test
@Disabled("Requires REQ-ERR-1: KubeMQException")
void grpcUnavailable_mapsToTransientError() { /* ... */ }

// Option B: Use generic assertions that will be tightened later
assertThrows(RuntimeException.class, () -> { /* ... */ });
// TODO: Change to assertThrows(KubeMQException.class) after REQ-ERR-1
```

**Recommended approach:** Option A (`@Disabled` with clear REQ reference). This makes the test visible in test reports as "pending" and documents the dependency explicitly.

### 8.4 Implementation Sequencing with Batch 1

The CI pipeline (REQ-TEST-3) and test organization (REQ-TEST-4) have **no blocking dependencies** on other specs and should be implemented first. This provides the safety net for all subsequent implementation work, addressing the risk noted in Review R2 M-6.

**Recommended cross-batch sequence:**

```
Phase 0: REQ-TEST-3 (CI) + REQ-TEST-4 (organization) + REQ-TEST-5 (coverage)
    |
    v
Phase 1: REQ-CQ-3 (linting, enables lint CI job)
    |
    v
Phase 2: REQ-ERR-1 through REQ-ERR-6 (error handling, enables error tests)
    |
    v
Phase 3: REQ-TEST-1 (unit test gaps -- unblocked by error hierarchy)
    |
    v
Phase 4: REQ-CONN-1, REQ-CONN-2 (connection, enables reconnection tests)
    |
    v
Phase 5: REQ-TEST-2 (integration test gaps -- unblocked by connection features)
```

---

## 9. New Test Dependencies (pom.xml)

Summary of all new dependencies required for this spec:

```xml
<!-- gRPC in-process testing (REQ-TEST-1, InProcessServer mock approach) -->
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-testing</artifactId>
    <version>${grpc.version}</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-inprocess</artifactId>
    <version>${grpc.version}</version>
    <scope>test</scope>
</dependency>

<!-- Testcontainers for integration tests (REQ-TEST-2, server lifecycle control) -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>

<!-- JUnit 5 parameterized tests (REQ-TEST-1, @ParameterizedTest + @EnumSource) -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter-params</artifactId>
    <version>5.10.3</version>
    <scope>test</scope>
</dependency>
```

**Note:** `junit-jupiter-params` may already be transitively included by `junit-jupiter-api`. Verify and add explicitly if not present.

---

## 10. Breaking Changes

This specification introduces **no breaking changes**. All changes are additive:

- New test files (do not affect production code)
- New CI workflow (does not change build output)
- JaCoCo `check` goal (fails build only if coverage drops below 40%, which is well below current 75.1%)
- New pom.xml properties (`skipUnitTests`, `skipITs`) with safe defaults (`false`)
- New test dependencies (all `<scope>test</scope>`)

---

## 11. Open Questions

| # | Question | Impact | Proposed Resolution |
|---|----------|--------|---------------------|
| OQ-1 | Does the KubeMQ Docker image require a license token for CI? | If yes, integration tests in CI need `KUBEMQ_TOKEN` secret | Test with `kubemq/kubemq:latest` without token; fall back to `kubemq/kubemq-community` if token required |
| OQ-2 | Should Testcontainers be used for ALL integration tests or only reconnection tests? | Testcontainers adds Docker dependency to test runtime | Use Testcontainers only for lifecycle tests (reconnection). Use GitHub Actions `services:` block for standard integration tests in CI. Local dev uses `KUBEMQ_ADDRESS` env var. |
| OQ-3 | Should the initial coverage threshold be 40% or 60%? Current coverage is 75.1%. | Starting at 40% allows headroom for new uncovered code. Starting at 60% is more protective. | **Resolved (Review R1 m-1):** Start at 60% (Phase 2). The 40% threshold provides no meaningful regression protection given current 75.1% coverage. |
| OQ-4 | Should `@EnabledIfEnvironmentVariable` or Maven `skipITs` be the primary skip mechanism? | Both achieve the same goal; using both creates redundancy | Use both: `skipITs` for Maven-level control, `@EnabledIfEnvironmentVariable` for self-documenting test-level control. Belt and suspenders. |
| OQ-5 | Is `kubemq/kubemq:latest` acceptable for CI or should a pinned digest be used? | GS recommends pinned digest for reproducibility | Start with `kubemq/kubemq:latest`. Pin to specific digest after first successful CI run. Update pin intentionally per GS recommendation. |
