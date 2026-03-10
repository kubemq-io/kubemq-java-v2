# Implementation Specification: Category 01 -- Error Handling & Resilience

**SDK:** KubeMQ Java v2
**Category:** 01 -- Error Handling & Resilience
**GS Source:** `clients/golden-standard/01-error-handling.md`
**Gap Research:** `clients/gap-research/java-gap-research.md` (lines 104-318)
**Current Score:** 2.27 / 5.0 | **Target:** 4.0+
**Priority:** P0 (Tier 1 gate blocker)
**Total Estimated Effort:** 15-22 days

---

## Table of Contents

1. [Summary of REQ Items](#1-summary-of-req-items)
2. [Implementation Order](#2-implementation-order)
3. [REQ-ERR-1: Typed Error Hierarchy](#3-req-err-1-typed-error-hierarchy)
4. [REQ-ERR-2: Error Classification](#4-req-err-2-error-classification)
5. [REQ-ERR-3: Auto-Retry with Configurable Policy](#5-req-err-3-auto-retry-with-configurable-policy)
6. [REQ-ERR-4: Per-Operation Timeouts](#6-req-err-4-per-operation-timeouts)
7. [REQ-ERR-5: Actionable Error Messages](#7-req-err-5-actionable-error-messages)
8. [REQ-ERR-6: gRPC Error Mapping](#8-req-err-6-grpc-error-mapping)
9. [REQ-ERR-7: Retry Throttling](#9-req-err-7-retry-throttling)
10. [REQ-ERR-8: Streaming Error Handling](#10-req-err-8-streaming-error-handling)
11. [REQ-ERR-9: Async Error Propagation](#11-req-err-9-async-error-propagation)
12. [Cross-Category Dependencies](#12-cross-category-dependencies)
13. [Breaking Changes](#13-breaking-changes)
14. [Open Questions](#14-open-questions)
15. [Migration Guide](#15-migration-guide)

---

## 1. Summary of REQ Items

| REQ | Status | Gap | Effort | Impl Order |
|-----|--------|-----|--------|------------|
| REQ-ERR-1 | MISSING | Full hierarchy needed | M-L (3-4 days) | 1 |
| REQ-ERR-2 | MISSING | Classification system needed | M (1-2 days) | 2 |
| REQ-ERR-5 | PARTIAL | Message builder needed | S (< 1 day) | 3 (with ERR-1) |
| REQ-ERR-6 | MISSING | gRPC mapper needed | M (2-3 days) | 4 |
| REQ-ERR-4 | PARTIAL | Timeout params needed | M (1-2 days) | 5 |
| REQ-ERR-3 | MISSING | Retry executor needed | L (3-5 days) | 6 |
| REQ-ERR-7 | NOT_ASSESSED (treat as MISSING) | Throttle needed | S (< 1 day) | 7 (with ERR-3) |
| REQ-ERR-9 | PARTIAL | Error type distinction needed | M (1-2 days) | 8 |
| REQ-ERR-8 | PARTIAL | Stream reconnect for queues | L (3-5 days) | 9 |

---

## 2. Implementation Order

The order reflects dependency chains. Items within the same phase can be parallelized.

**Phase 1 -- Foundation (days 1-5):**
1. REQ-ERR-1 (error hierarchy) -- no dependencies
2. REQ-ERR-2 (classification) -- depends on ERR-1
3. REQ-ERR-5 (message builder) -- depends on ERR-1; can be done concurrently with ERR-2

**Phase 2 -- gRPC Integration (days 4-8):**
4. REQ-ERR-6 (gRPC mapping) -- depends on ERR-1, ERR-2
5. REQ-ERR-4 (timeouts) -- depends on ERR-1 (for TimeoutException)

**Phase 3 -- Retry (days 7-13):**
6. REQ-ERR-3 (retry policy + executor) -- depends on ERR-2, ERR-6
7. REQ-ERR-7 (retry throttling) -- depends on ERR-3

**Phase 4 -- Streaming & Async (days 12-18):**
8. REQ-ERR-9 (async error propagation) -- depends on ERR-1
9. REQ-ERR-8 (streaming error handling) -- depends on ERR-1, ERR-3; also depends on REQ-CONN-2 (connection state machine, Category 02)

---

## 3. REQ-ERR-1: Typed Error Hierarchy

**Gap Status:** MISSING
**GS Reference:** 01-error-handling.md, REQ-ERR-1
**Assessment Evidence:** 4.1.1 (4 flat exception classes), 4.1.2 (no base class), 4.1.5 (cause chain lost)
**Effort:** M-L (3-4 days including tests)

### 3.1 Current State

The SDK has 4 exception classes in `kubemq-java/src/main/java/io/kubemq/sdk/exception/`:

| File | Extends | Constructors | Issue |
|------|---------|-------------|-------|
| `GRPCException.java` | `RuntimeException` | `()`, `(String)`, `(Throwable)` | No `(String, Throwable)` constructor; loses cause chain |
| `CreateChannelException.java` | `RuntimeException` | `()`, `(String)`, `(String, Throwable)` | No structured fields |
| `DeleteChannelException.java` | `RuntimeException` | `()`, `(String)`, `(String, Throwable)` | No structured fields |
| `ListChannelsException.java` | `RuntimeException` | `()`, `(String)`, `(String, Throwable)` | No structured fields |

Additionally, 20+ throw sites use `throw new RuntimeException(...)` directly:
- `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java` lines 296, 416
- `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/PubSubClient.java` lines 43, 62, 92, 122, 153, 157, 187, 191, 210, 229, 259, 289
- `kubemq-java/src/main/java/io/kubemq/sdk/common/KubeMQUtils.java` lines 138, 179, 221
- `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventStreamHelper.java` line 147
- `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueUpstreamHandler.java` line 207
- `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueDownstreamHandler.java` line 228

### 3.2 Target Design

#### 3.2.1 New Package

Create package: `io.kubemq.sdk.exception` (reuse existing package).

#### 3.2.2 ErrorCode Enum

```java
package io.kubemq.sdk.exception;

/**
 * Machine-readable error codes for KubeMQ SDK errors.
 * Stable across releases per SemVer: new codes may be added in minor versions,
 * existing codes are never removed or changed in meaning within a major version.
 */
public enum ErrorCode {
    // Connection errors
    CONNECTION_FAILED,
    CONNECTION_TIMEOUT,
    CONNECTION_CLOSED,

    // Authentication / Authorization
    AUTHENTICATION_FAILED,
    AUTHORIZATION_DENIED,

    // Timeout
    OPERATION_TIMEOUT,
    DEADLINE_EXCEEDED,

    // Validation
    INVALID_ARGUMENT,
    FAILED_PRECONDITION,
    ALREADY_EXISTS,
    OUT_OF_RANGE,

    // Not found
    NOT_FOUND,

    // Server errors
    SERVER_INTERNAL,
    SERVER_UNIMPLEMENTED,
    DATA_LOSS,
    UNKNOWN_ERROR,

    // Transient
    UNAVAILABLE,
    ABORTED,

    // Throttling / Backpressure
    RESOURCE_EXHAUSTED,
    BUFFER_FULL,
    RETRY_THROTTLED,

    // Cancellation
    CANCELLED_BY_CLIENT,
    CANCELLED_BY_SERVER,

    // Streaming
    STREAM_BROKEN,

    // Async
    HANDLER_ERROR,

    // Partial failure (future-proofing)
    PARTIAL_FAILURE
}
```

#### 3.2.3 KubeMQException Base Class

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/exception/KubeMQException.java` (new file)

```java
package io.kubemq.sdk.exception;

import java.io.Serializable;
import java.time.Instant;

/**
 * Base exception for all KubeMQ SDK errors.
 * Extends RuntimeException (unchecked) per Java SDK convention.
 * All SDK methods throw subtypes of this class -- never raw RuntimeException or gRPC exceptions.
 */
public class KubeMQException extends RuntimeException implements Serializable {

    private static final long serialVersionUID = 1L;

    // --- Required fields (GS REQ-ERR-1) ---
    private final ErrorCode code;
    private final String operation;
    private final String channel;
    private final boolean retryable;
    private final String requestId;

    // --- Optional fields (GS recommended) ---
    private final String messageId;
    private final int statusCode;       // gRPC status code numeric value (-1 if not applicable)
    private final Instant timestamp;
    private final String serverAddress;

    // --- Error category (GS REQ-ERR-2, populated by subclasses) ---
    private final ErrorCategory category;

    /**
     * Full constructor used by builder.
     */
    protected KubeMQException(Builder<?> builder) {
        super(builder.message, builder.cause);
        this.code = builder.code;
        this.operation = builder.operation;
        this.channel = builder.channel;
        this.retryable = builder.retryable;
        this.requestId = builder.requestId;
        this.messageId = builder.messageId;
        this.statusCode = builder.statusCode;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.serverAddress = builder.serverAddress;
        this.category = builder.category;
    }

    // --- Getters for all fields ---
    public ErrorCode getCode() { return code; }
    public String getOperation() { return operation; }
    public String getChannel() { return channel; }
    public boolean isRetryable() { return retryable; }
    public String getRequestId() { return requestId; }
    public String getMessageId() { return messageId; }
    public int getStatusCode() { return statusCode; }
    public Instant getTimestamp() { return timestamp; }
    public String getServerAddress() { return serverAddress; }
    public ErrorCategory getCategory() { return category; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append("[code=").append(code);
        if (operation != null) sb.append(", operation=").append(operation);
        if (channel != null) sb.append(", channel=").append(channel);
        sb.append(", retryable=").append(retryable);
        if (requestId != null) sb.append(", requestId=").append(requestId);
        if (statusCode >= 0) sb.append(", grpcStatus=").append(statusCode);
        sb.append("]: ").append(getMessage());
        return sb.toString();
    }

    // --- Builder pattern ---
    @SuppressWarnings("unchecked")
    public static abstract class Builder<T extends Builder<T>> {
        private ErrorCode code;
        private String message;
        private String operation;
        private String channel;
        private boolean retryable;
        private String requestId;
        private Throwable cause;
        private String messageId;
        private int statusCode = -1;
        private Instant timestamp;
        private String serverAddress;
        private ErrorCategory category;

        public T code(ErrorCode code) { this.code = code; return (T) this; }
        public T message(String message) { this.message = message; return (T) this; }
        public T operation(String operation) { this.operation = operation; return (T) this; }
        public T channel(String channel) { this.channel = channel; return (T) this; }
        public T retryable(boolean retryable) { this.retryable = retryable; return (T) this; }
        public T requestId(String requestId) { this.requestId = requestId; return (T) this; }
        public T cause(Throwable cause) { this.cause = cause; return (T) this; }
        public T messageId(String messageId) { this.messageId = messageId; return (T) this; }
        public T statusCode(int statusCode) { this.statusCode = statusCode; return (T) this; }
        public T timestamp(Instant timestamp) { this.timestamp = timestamp; return (T) this; }
        public T serverAddress(String serverAddress) { this.serverAddress = serverAddress; return (T) this; }
        public T category(ErrorCategory category) { this.category = category; return (T) this; }

        public abstract KubeMQException build();
    }

    public static class DefaultBuilder extends Builder<DefaultBuilder> {
        @Override
        public KubeMQException build() {
            return new KubeMQException(this);
        }
    }

    public static DefaultBuilder builder() {
        return new DefaultBuilder();
    }
}
```

#### 3.2.4 Exception Subclass Hierarchy

All subclasses extend `KubeMQException`. Each subclass sets sensible defaults for `code`, `category`, and `retryable` in its builder.

| Class Name | Default ErrorCode | Default Category | Default Retryable | Package |
|-----------|------------------|-----------------|-------------------|---------|
| `ConnectionException` | `CONNECTION_FAILED` | `TRANSIENT` | `true` | `io.kubemq.sdk.exception` |
| `AuthenticationException` | `AUTHENTICATION_FAILED` | `AUTHENTICATION` | `false` | `io.kubemq.sdk.exception` |
| `AuthorizationException` | `AUTHORIZATION_DENIED` | `AUTHORIZATION` | `false` | `io.kubemq.sdk.exception` |
| `TimeoutException` | `OPERATION_TIMEOUT` | `TIMEOUT` | `true` | `io.kubemq.sdk.exception` |
| `ValidationException` | `INVALID_ARGUMENT` | `VALIDATION` | `false` | `io.kubemq.sdk.exception` |
| `ServerException` | `SERVER_INTERNAL` | `FATAL` | `false` | `io.kubemq.sdk.exception` |
| `ThrottlingException` | `RESOURCE_EXHAUSTED` | `THROTTLING` | `true` | `io.kubemq.sdk.exception` |
| `OperationCancelledException` | `CANCELLED_BY_CLIENT` | `CANCELLATION` | `false` | `io.kubemq.sdk.exception` |
| `BackpressureException` | `BUFFER_FULL` | `BACKPRESSURE` | `false` | `io.kubemq.sdk.exception` |
| `StreamBrokenException` | `STREAM_BROKEN` | `TRANSIENT` | `true` | `io.kubemq.sdk.exception` |
| `TransportException` | varies | varies | varies | `io.kubemq.sdk.exception` |
| `HandlerException` | `HANDLER_ERROR` | `FATAL` | `false` | `io.kubemq.sdk.exception` |
| `RetryThrottledException` | `RETRY_THROTTLED` | `THROTTLING` | `false` | `io.kubemq.sdk.exception` |
| `PartialFailureException` | `PARTIAL_FAILURE` | `TRANSIENT` | `false` | `io.kubemq.sdk.exception` |

**Example subclass implementation (pattern for all):**

```java
package io.kubemq.sdk.exception;

/**
 * Thrown when a KubeMQ operation times out.
 */
public class TimeoutException extends KubeMQException {

    private static final long serialVersionUID = 1L;

    protected TimeoutException(Builder builder) {
        super(builder);
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        public Builder() {
            code(ErrorCode.OPERATION_TIMEOUT);
            category(ErrorCategory.TIMEOUT);
            retryable(true);
        }

        @Override
        public TimeoutException build() {
            return new TimeoutException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
```

**StreamBrokenException has an additional field:**

```java
package io.kubemq.sdk.exception;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when a gRPC stream breaks with in-flight messages.
 */
public class StreamBrokenException extends KubeMQException {

    private static final long serialVersionUID = 1L;
    private final List<String> unacknowledgedMessageIds;

    protected StreamBrokenException(Builder builder) {
        super(builder);
        this.unacknowledgedMessageIds = builder.unacknowledgedMessageIds != null
            ? Collections.unmodifiableList(builder.unacknowledgedMessageIds)
            : Collections.emptyList();
    }

    public List<String> getUnacknowledgedMessageIds() {
        return unacknowledgedMessageIds;
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        private List<String> unacknowledgedMessageIds;

        public Builder() {
            code(ErrorCode.STREAM_BROKEN);
            category(ErrorCategory.TRANSIENT);
            retryable(true);
        }

        public Builder unacknowledgedMessageIds(List<String> ids) {
            this.unacknowledgedMessageIds = ids;
            return this;
        }

        @Override
        public StreamBrokenException build() {
            return new StreamBrokenException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
```

**PartialFailureException (future-proofing per GS Future Enhancements):**

```java
package io.kubemq.sdk.exception;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a partial batch failure. Reserved for future use when the server
 * supports per-message batch status. Added now per GS Future Enhancement guidance.
 */
public class PartialFailureException extends KubeMQException {

    private static final long serialVersionUID = 1L;
    private final Map<String, KubeMQException> perMessageErrors;

    protected PartialFailureException(Builder builder) {
        super(builder);
        this.perMessageErrors = builder.perMessageErrors != null
            ? Collections.unmodifiableMap(builder.perMessageErrors)
            : Collections.emptyMap();
    }

    public Map<String, KubeMQException> getPerMessageErrors() {
        return perMessageErrors;
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        private Map<String, KubeMQException> perMessageErrors;

        public Builder() {
            code(ErrorCode.PARTIAL_FAILURE);
            category(ErrorCategory.TRANSIENT);
            retryable(false);
        }

        public Builder perMessageErrors(Map<String, KubeMQException> errors) {
            this.perMessageErrors = errors;
            return this;
        }

        @Override
        public PartialFailureException build() {
            return new PartialFailureException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
```

#### 3.2.5 Legacy Exception Deprecation

The existing 4 exception classes will be deprecated (not removed) in v2.x and removed in v3.0:

| Current Class | Replacement | Migration |
|--------------|-------------|-----------|
| `GRPCException` | `ConnectionException`, `ServerException`, or mapped subtype | Callers catching `GRPCException` should catch `KubeMQException` |
| `CreateChannelException` | `ServerException` or `ValidationException` | Callers catching `CreateChannelException` should catch `KubeMQException` |
| `DeleteChannelException` | `ServerException` or `ValidationException` | Same as above |
| `ListChannelsException` | `ServerException` or `ValidationException` | Same as above |

During v2.x transition period, legacy classes extend `KubeMQException` instead of `RuntimeException`:

```java
@Deprecated(since = "2.2.0", forRemoval = true)
public class GRPCException extends KubeMQException {
    // Backward-compatible constructors that delegate to KubeMQException builder
}
```

#### 3.2.6 Throw Site Migration Table

Every `throw new RuntimeException(...)` must be migrated. The table below lists each site and its target exception type:

| File | Line | Current | Target Exception | Operation | Notes |
|------|------|---------|-----------------|-----------|-------|
| `KubeMQClient.java` | 296 | `new RuntimeException(e)` (SSLException) | `ConnectionException` | `connect` | SSL setup failure |
| `KubeMQClient.java` | 416 | `new RuntimeException(e)` (StatusRuntimeException) | Mapped via `GrpcErrorMapper` | `ping` | gRPC call |
| `PubSubClient.java` | 43 | `new RuntimeException(e)` | Mapped via `GrpcErrorMapper` | `sendEventsMessage` | |
| `PubSubClient.java` | 62 | `new RuntimeException(e)` | Mapped via `GrpcErrorMapper` | `sendEventsStoreMessage` | |
| `PubSubClient.java` | 92 | `new RuntimeException(e)` | Mapped via `GrpcErrorMapper` | `createEventsChannel` | |
| `PubSubClient.java` | 122 | `new RuntimeException(e)` | Mapped via `GrpcErrorMapper` | `createEventsStoreChannel` | |
| `PubSubClient.java` | 153 | `new RuntimeException(response.getError())` | `ServerException` | `listEventsChannels` | Server-side error |
| `PubSubClient.java` | 157 | `new RuntimeException(e)` | Mapped via `GrpcErrorMapper` | `listEventsChannels` | gRPC call |
| `PubSubClient.java` | 187 | `new RuntimeException(response.getError())` | `ServerException` | `listEventsStoreChannels` | Server-side error |
| `PubSubClient.java` | 191 | `new RuntimeException(e)` | Mapped via `GrpcErrorMapper` | `listEventsStoreChannels` | gRPC call |
| `PubSubClient.java` | 210 | `new RuntimeException(e)` | Mapped via `GrpcErrorMapper` | `subscribeToEvents` | |
| `PubSubClient.java` | 229 | `new RuntimeException(e)` | Mapped via `GrpcErrorMapper` | `subscribeToEventsStore` | |
| `PubSubClient.java` | 259 | `new RuntimeException(e)` | Mapped via `GrpcErrorMapper` | `deleteEventsChannel` | |
| `PubSubClient.java` | 289 | `new RuntimeException(e)` | Mapped via `GrpcErrorMapper` | `deleteEventsStoreChannel` | |
| `KubeMQUtils.java` | 138 | `new RuntimeException(e)` | Mapped via `GrpcErrorMapper` | `listQueuesChannels` | |
| `KubeMQUtils.java` | 179 | `new RuntimeException(e)` | Mapped via `GrpcErrorMapper` | `listPubSubChannels` | |
| `KubeMQUtils.java` | 221 | `new RuntimeException(e)` | Mapped via `GrpcErrorMapper` | `listCQChannels` | |
| `EventStreamHelper.java` | 147 | `new RuntimeException("Failed to get response", e)` | `TimeoutException` or mapped | `sendEventMessage` | |
| `QueueUpstreamHandler.java` | 207 | `new RuntimeException("Failed to get response", e)` | `TimeoutException` or mapped | `sendQueuesMessage` | |
| `QueueDownstreamHandler.java` | 228 | `new RuntimeException("Failed to get response", e)` | `TimeoutException` or mapped | `receiveQueuesMessages` | |

**Credential exclusion requirement (from review R2 M-1):** The `toString()` and `getMessage()` implementations in `KubeMQException` MUST NOT include authentication tokens, certificate contents, or other credential material. The `serverAddress` field is safe to include. Audit all error message construction to verify no credential leakage.

### 3.3 Test Scenarios

| # | Test | Setup | Action | Assert |
|---|------|-------|--------|--------|
| T1 | KubeMQException fields populated | Create exception with builder | Build with all fields | All getters return correct values |
| T2 | getCause() preserves chain | Wrap a StatusRuntimeException | Build ConnectionException with cause | `getCause()` returns original exception |
| T3 | toString() structured output | Build exception with operation + channel | Call toString() | Contains code, operation, channel, message |
| T4 | Serializable round-trip | Create exception, serialize, deserialize | ObjectOutputStream/ObjectInputStream | Deserialized fields match original |
| T5 | Each subclass default code | Create each subclass via builder | Check code, category, retryable | Defaults match table in 3.2.4 |
| T6 | StreamBrokenException message IDs | Build with list of IDs | Get unacknowledgedMessageIds | Returns unmodifiable list with correct IDs |
| T7 | Legacy GRPCException instanceof | Create deprecated GRPCException | `instanceof KubeMQException` | Returns true |
| T8 | No credentials in toString | Build exception with serverAddress | Call toString() | No token or cert content present |

---

## 4. REQ-ERR-2: Error Classification

**Gap Status:** MISSING
**GS Reference:** 01-error-handling.md, REQ-ERR-2
**Assessment Evidence:** 4.1.3 (no retryable classification), 4.3.5 (all StatusRuntimeException retried)
**Effort:** M (1-2 days including tests)
**Depends On:** REQ-ERR-1

### 4.1 Current State

No error classification system exists. Subscription reconnect logic retries all `StatusRuntimeException` without inspecting the status code (see `EventsSubscription.java` line 143-145). This means non-retryable errors like `UNAUTHENTICATED` and `PERMISSION_DENIED` are retried, wasting resources.

### 4.2 Target Design

#### 4.2.1 ErrorCategory Enum

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/exception/ErrorCategory.java` (new file)

```java
package io.kubemq.sdk.exception;

/**
 * Classification of KubeMQ SDK errors. Maps to gRPC status codes per GS table.
 */
public enum ErrorCategory {
    /** Transient server issue. Auto-retry with backoff. */
    TRANSIENT(true),
    /** Operation timed out. Auto-retry with caution. */
    TIMEOUT(true),
    /** Rate limited by server. Auto-retry with extended backoff. */
    THROTTLING(true),
    /** Invalid credentials. Do not retry; refresh credentials. */
    AUTHENTICATION(false),
    /** Insufficient permissions. Do not retry; fix permissions. */
    AUTHORIZATION(false),
    /** Invalid input or precondition. Do not retry; fix input. */
    VALIDATION(false),
    /** Resource does not exist. Do not retry. */
    NOT_FOUND(false),
    /** Unrecoverable server error. Do not retry. */
    FATAL(false),
    /** Operation cancelled by caller. Do not retry. */
    CANCELLATION(false),
    /** SDK buffer full. Wait for reconnection or increase buffer. */
    BACKPRESSURE(false);

    private final boolean defaultRetryable;

    ErrorCategory(boolean defaultRetryable) {
        this.defaultRetryable = defaultRetryable;
    }

    /**
     * Returns whether errors in this category are retryable by default.
     */
    public boolean isDefaultRetryable() {
        return defaultRetryable;
    }
}
```

#### 4.2.2 Integration with KubeMQException

The `category` field already exists on `KubeMQException` (see section 3.2.3). The `isRetryable()` method returns the `retryable` field, which is set by each subclass's builder defaults per the table in section 3.2.4. Callers can override the default via the builder if needed.

#### 4.2.3 ErrorClassifier Utility

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/exception/ErrorClassifier.java` (new file)

```java
package io.kubemq.sdk.exception;

/**
 * Utility to classify any KubeMQException.
 * Used internally by retry logic to determine retry eligibility.
 */
public final class ErrorClassifier {

    private ErrorClassifier() {}

    /**
     * Returns true if the error should be retried based on its classification.
     * This is the single source of truth for retry decisions.
     */
    public static boolean shouldRetry(KubeMQException ex) {
        return ex.isRetryable();
    }

    /**
     * Returns true if the error should use extended backoff (throttling).
     */
    public static boolean shouldUseExtendedBackoff(KubeMQException ex) {
        return ex.getCategory() == ErrorCategory.THROTTLING;
    }

    /**
     * Returns the error.type string for OTel attributes (dependency on Category 05).
     * Maps ErrorCategory to lowercase string per GS REQ-OBS-3.
     */
    public static String toOtelErrorType(KubeMQException ex) {
        return ex.getCategory() != null ? ex.getCategory().name().toLowerCase() : "unknown";
    }
}
```

### 4.3 Test Scenarios

| # | Test | Setup | Action | Assert |
|---|------|-------|--------|--------|
| T1 | Each ErrorCategory retryable default | Iterate all enum values | Check isDefaultRetryable() | Matches GS table |
| T2 | TRANSIENT is retryable | Create ConnectionException | `isRetryable()` | true |
| T3 | AUTHENTICATION not retryable | Create AuthenticationException | `isRetryable()` | false |
| T4 | BufferFullException classified BACKPRESSURE | Create BackpressureException | `getCategory()` | BACKPRESSURE |
| T5 | BackpressureException not retryable | Create BackpressureException | `isRetryable()` | false |
| T6 | OTel error type mapping | Create each subclass | `ErrorClassifier.toOtelErrorType()` | Returns lowercase category name |

---

## 5. REQ-ERR-3: Auto-Retry with Configurable Policy

**Gap Status:** MISSING
**GS Reference:** 01-error-handling.md, REQ-ERR-3
**Assessment Evidence:** 4.3.1-4.3.5 (subscription retry only, no jitter, hardcoded, no status inspection, gRPC enableRetry called)
**Effort:** L (3-5 days including tests)
**Depends On:** REQ-ERR-2, REQ-ERR-6

### 5.1 Current State

- Subscription reconnect uses exponential backoff without jitter (`EventsSubscription.java` lines 167-168)
- Max delay hardcoded at 60s, max attempts at 10 (`EventsSubscription.java` line 25)
- gRPC `enableRetry()` is called (`KubeMQClient.java` lines 276, 302) -- **MUST be removed**
- Queue operations have no retry at all
- No per-operation retry config
- All `StatusRuntimeException` triggers retry without status code inspection

### 5.2 Target Design

#### 5.2.1 RetryPolicy (Immutable)

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/retry/RetryPolicy.java` (new file)

```java
package io.kubemq.sdk.retry;

import java.time.Duration;

/**
 * Immutable retry policy for KubeMQ operations.
 * Created via builder and cannot be modified after construction.
 * To change retry behavior, create a new client instance.
 */
public final class RetryPolicy {

    public enum JitterType { FULL, EQUAL, NONE }

    public static final RetryPolicy DEFAULT = RetryPolicy.builder().build();
    public static final RetryPolicy DISABLED = RetryPolicy.builder().maxRetries(0).build();

    private final int maxRetries;             // default 3, range 0-10
    private final Duration initialBackoff;    // default 500ms, range 50ms-5s
    private final Duration maxBackoff;        // default 30s, range 1s-120s
    private final double multiplier;          // default 2.0, range 1.5-3.0
    private final JitterType jitterType;      // default FULL
    private final int maxConcurrentRetries;   // default 10, range 0-100 (REQ-ERR-7)

    private RetryPolicy(Builder builder) {
        this.maxRetries = requireInRange(builder.maxRetries, 0, 10, "maxRetries");
        this.initialBackoff = requireDurationInRange(builder.initialBackoff,
            Duration.ofMillis(50), Duration.ofSeconds(5), "initialBackoff");
        this.maxBackoff = requireDurationInRange(builder.maxBackoff,
            Duration.ofSeconds(1), Duration.ofSeconds(120), "maxBackoff");
        this.multiplier = requireInRange(builder.multiplier, 1.5, 3.0, "multiplier");
        this.jitterType = builder.jitterType;
        this.maxConcurrentRetries = requireInRange(builder.maxConcurrentRetries, 0, 100, "maxConcurrentRetries");
    }

    // --- Getters ---
    public int getMaxRetries() { return maxRetries; }
    public Duration getInitialBackoff() { return initialBackoff; }
    public Duration getMaxBackoff() { return maxBackoff; }
    public double getMultiplier() { return multiplier; }
    public JitterType getJitterType() { return jitterType; }
    public int getMaxConcurrentRetries() { return maxConcurrentRetries; }

    /**
     * Returns true if retries are enabled (maxRetries > 0).
     */
    public boolean isEnabled() { return maxRetries > 0; }

    /**
     * Computes backoff duration for a given attempt number (0-indexed).
     * Full jitter:  sleep = random(0, min(maxBackoff, initialBackoff * multiplier ^ attempt))
     * Equal jitter: temp = min(maxBackoff, initialBackoff * multiplier ^ attempt);
     *               sleep = temp/2 + random(0, temp/2)
     *
     * @implNote This method is thread-safe. It uses {@code ThreadLocalRandom.current()}
     * which returns the calling thread's local instance. The RetryPolicy object itself
     * is immutable and shared across threads -- no synchronization is needed.
     */
    public Duration computeBackoff(int attempt) {
        double baseMs = initialBackoff.toMillis() * Math.pow(multiplier, attempt);
        long cappedMs = (long) Math.min(baseMs, maxBackoff.toMillis());

        switch (jitterType) {
            case FULL:
                return Duration.ofMillis(
                    java.util.concurrent.ThreadLocalRandom.current().nextLong(0, cappedMs + 1));
            case EQUAL:
                long half = cappedMs / 2;
                return Duration.ofMillis(
                    half + java.util.concurrent.ThreadLocalRandom.current().nextLong(0, half + 1));
            case NONE:
                return Duration.ofMillis(cappedMs);
            default:
                return Duration.ofMillis(cappedMs);
        }
    }

    /**
     * Returns the worst-case total latency for the default policy with a given operation timeout.
     * Formula: sum of (operationTimeout + backoff[i]) for i in 0..maxRetries.
     * With NONE jitter: 5s + 0.5s + 5s + 1s + 5s + 2s + 5s = 23.5s for 3 retries, 5s timeout.
     */
    public Duration worstCaseLatency(Duration operationTimeout) {
        long totalMs = operationTimeout.toMillis(); // initial attempt
        for (int i = 0; i < maxRetries; i++) {
            long backoffMs = (long) Math.min(
                initialBackoff.toMillis() * Math.pow(multiplier, i),
                maxBackoff.toMillis());
            totalMs += backoffMs + operationTimeout.toMillis();
        }
        return Duration.ofMillis(totalMs);
    }

    // --- Builder ---
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int maxRetries = 3;
        private Duration initialBackoff = Duration.ofMillis(500);
        private Duration maxBackoff = Duration.ofSeconds(30);
        private double multiplier = 2.0;
        private JitterType jitterType = JitterType.FULL;
        private int maxConcurrentRetries = 10;

        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }
        public Builder initialBackoff(Duration d) { this.initialBackoff = d; return this; }
        public Builder maxBackoff(Duration d) { this.maxBackoff = d; return this; }
        public Builder multiplier(double m) { this.multiplier = m; return this; }
        public Builder jitterType(JitterType j) { this.jitterType = j; return this; }
        public Builder maxConcurrentRetries(int n) { this.maxConcurrentRetries = n; return this; }

        public RetryPolicy build() { return new RetryPolicy(this); }
    }

    // Fail-fast validation: throw IllegalArgumentException for out-of-range values
    // instead of silently clamping, which can mask configuration errors.
    private static int requireInRange(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                name + " must be in range [" + min + ", " + max + "], got: " + value);
        }
        return value;
    }

    private static double requireInRange(double value, double min, double max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                name + " must be in range [" + min + ", " + max + "], got: " + value);
        }
        return value;
    }

    private static Duration requireDurationInRange(Duration value, Duration min, Duration max, String name) {
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException(
                name + " must be in range [" + min + ", " + max + "], got: " + value);
        }
        return value;
    }
}
```

#### 5.2.2 Retry Safety Table

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/retry/OperationSafety.java` (new file)

```java
package io.kubemq.sdk.retry;

/**
 * Classifies operations by retry safety per GS REQ-ERR-3.
 */
public enum OperationSafety {
    /** Safe to retry on any retryable error. */
    SAFE,
    /** NOT safe to retry on ambiguous failures (DEADLINE_EXCEEDED).
     *  Only retry on errors that prove server did NOT receive. */
    UNSAFE_ON_AMBIGUOUS;

    /**
     * Returns whether this operation type is safe to retry for the given error.
     */
    public boolean canRetry(io.kubemq.sdk.exception.KubeMQException ex) {
        if (this == SAFE) {
            return ex.isRetryable();
        }
        // UNSAFE_ON_AMBIGUOUS: only retry UNAVAILABLE before stream established.
        // DEADLINE_EXCEEDED -> NOT retryable for unsafe operations.
        if (ex.getCode() == io.kubemq.sdk.exception.ErrorCode.DEADLINE_EXCEEDED) {
            return false;
        }
        return ex.isRetryable();
    }
}
```

**Operation-to-safety mapping (to be used by RetryExecutor):**

| Operation | Safety | Rationale |
|-----------|--------|-----------|
| `sendEventsMessage` | SAFE | Fire-and-forget, duplicates acceptable |
| `sendEventsStoreMessage` | SAFE | Server-side sequence dedup |
| `sendQueuesMessage` | UNSAFE_ON_AMBIGUOUS | Queue message may have been processed |
| `sendCommandRequest` | UNSAFE_ON_AMBIGUOUS | RPC may have been executed |
| `sendQueryRequest` | UNSAFE_ON_AMBIGUOUS | RPC may have been executed |
| `subscribeToEvents` | SAFE | Idempotent re-subscribe |
| `subscribeToEventsStore` | SAFE | Idempotent re-subscribe |
| `subscribeToCommands` | SAFE | Idempotent re-subscribe |
| `subscribeToQueries` | SAFE | Idempotent re-subscribe |
| `createChannel` / `deleteChannel` / `listChannels` | SAFE | Idempotent admin operations |

#### 5.2.3 RetryExecutor

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/retry/RetryExecutor.java` (new file)

```java
package io.kubemq.sdk.retry;

import io.kubemq.sdk.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

/**
 * Executes operations with retry according to the configured RetryPolicy.
 * Thread-safe. One instance per client.
 */
public class RetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryExecutor.class);

    private final RetryPolicy policy;
    private final Semaphore concurrencyLimiter; // REQ-ERR-7
    private final java.util.function.Supplier<Boolean> connectionReadySupplier; // REQ-CONN-1 AC-10

    /**
     * @param policy the retry policy
     * @param connectionReadySupplier returns true when the connection is READY.
     *        When non-null, the executor checks connection state before each retry.
     *        If the connection is not READY and WaitForReady is true, the retry
     *        waits via ensureReady(). If WaitForReady is false, the retry is
     *        abandoned with ConnectionNotReadyException.
     *        May be null during unit tests or when connection state is not available.
     */
    public RetryExecutor(RetryPolicy policy,
                         java.util.function.Supplier<Boolean> connectionReadySupplier) {
        this.policy = policy;
        this.connectionReadySupplier = connectionReadySupplier;
        this.concurrencyLimiter = policy.getMaxConcurrentRetries() > 0
            ? new Semaphore(policy.getMaxConcurrentRetries())
            : null; // 0 = unlimited
    }

    /** Convenience constructor for when connection state is not tracked. */
    public RetryExecutor(RetryPolicy policy) {
        this(policy, null);
    }

    /**
     * Executes the given operation with retry.
     *
     * @param operation the callable to execute
     * @param operationName name for logging/error messages
     * @param channel the target channel (for error context)
     * @param safety the retry safety classification for this operation
     * @param <T> return type
     * @return the result of the operation
     * @throws KubeMQException if all retries exhausted or non-retryable error
     */
    public <T> T execute(Callable<T> operation, String operationName,
                         String channel, OperationSafety safety) throws KubeMQException {
        if (!policy.isEnabled()) {
            return executeOnce(operation, operationName, channel);
        }

        KubeMQException lastError = null;
        Instant startTime = Instant.now();
        int maxAttempts = policy.getMaxRetries() + 1; // initial + retries

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                return executeOnce(operation, operationName, channel);
            } catch (KubeMQException ex) {
                lastError = ex;

                // Non-retryable: return immediately
                if (!safety.canRetry(ex)) {
                    log.debug("[{}] Non-retryable error on attempt {}: {}",
                              operationName, attempt + 1, ex.getCode());
                    throw ex;
                }

                // UNKNOWN: max 1 retry regardless of policy
                if (ex.getCode() == ErrorCode.UNKNOWN_ERROR && attempt >= 1) {
                    log.debug("[{}] UNKNOWN error retried once, giving up", operationName);
                    throw ex;
                }

                // Last attempt: don't sleep
                if (attempt == maxAttempts - 1) {
                    break;
                }

                // Throttle check (REQ-ERR-7)
                // Design note: The semaphore limits concurrent retry *attempts* (backoff + operation).
                // It is held from acquire through the backoff sleep AND released in finally,
                // meaning the actual retry operation execution runs un-semaphored. This is intentional:
                // the semaphore prevents thundering-herd backoff storms, not concurrent operations.
                if (concurrencyLimiter != null && !concurrencyLimiter.tryAcquire()) {
                    throw RetryThrottledException.builder()
                        .message("Retry throttled: concurrent retry limit reached ("
                                 + policy.getMaxConcurrentRetries() + ")")
                        .operation(operationName)
                        .channel(channel)
                        .cause(ex)
                        .build();
                }

                try {
                    // REQ-CONN-1 AC-10: Check connection state before retrying.
                    // If the connection is RECONNECTING, the retry should wait
                    // (via ensureReady) rather than waste the retry budget.
                    // The caller (KubeMQClient) passes ensureReady as the supplier;
                    // if it throws, the retry is abandoned.
                    if (connectionReadySupplier != null && !connectionReadySupplier.get()) {
                        log.debug("[{}] Connection not ready, abandoning retry", operationName);
                        throw ex; // Propagate last error; caller's ensureReady will handle
                    }

                    Duration backoff = policy.computeBackoff(attempt);
                    // Extended backoff for throttling
                    if (ErrorClassifier.shouldUseExtendedBackoff(ex)) {
                        backoff = backoff.multipliedBy(2);
                    }

                    log.debug("[{}] Retry attempt {}/{} after {}ms (error: {})",
                              operationName, attempt + 1, policy.getMaxRetries(),
                              backoff.toMillis(), ex.getCode());

                    Thread.sleep(backoff.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw OperationCancelledException.builder()
                        .code(ErrorCode.CANCELLED_BY_CLIENT)
                        .message("Retry interrupted")
                        .operation(operationName)
                        .channel(channel)
                        .cause(ie)
                        .build();
                } finally {
                    if (concurrencyLimiter != null) {
                        concurrencyLimiter.release();
                    }
                }
            }
        }

        // All retries exhausted
        Duration totalDuration = Duration.between(startTime, Instant.now());
        throw KubeMQException.builder()
            .code(lastError.getCode())
            .category(lastError.getCategory())
            .retryable(false)
            .message(String.format(
                "%s failed on channel \"%s\": %s. Retries exhausted: %d/%d attempts over %s",
                operationName, channel, lastError.getMessage(),
                policy.getMaxRetries(), policy.getMaxRetries(),
                formatDuration(totalDuration)))
            .operation(operationName)
            .channel(channel)
            .cause(lastError)
            .build();
    }

    private <T> T executeOnce(Callable<T> operation, String operationName,
                              String channel) throws KubeMQException {
        try {
            return operation.call();
        } catch (KubeMQException ex) {
            throw ex;
        } catch (Exception ex) {
            throw KubeMQException.builder()
                .code(ErrorCode.UNKNOWN_ERROR)
                .category(ErrorCategory.FATAL)
                .retryable(false)
                .message(operationName + " failed: " + ex.getMessage())
                .operation(operationName)
                .channel(channel)
                .cause(ex)
                .build();
        }
    }

    private static String formatDuration(Duration d) {
        long millis = d.toMillis();
        if (millis < 1000) return millis + "ms";
        return String.format("%.1fs", millis / 1000.0);
    }
}
```

#### 5.2.4 Remove gRPC enableRetry()

**File to modify:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

**Line 276:** Remove `.enableRetry()` from TLS channel builder.
**Line 302:** Remove `.enableRetry()` from plaintext channel builder.

This prevents double-retry amplification between gRPC internal retry and SDK retry.

#### 5.2.5 Add RetryPolicy to Client Builder

**File to modify:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

Add field:
```java
private final RetryPolicy retryPolicy;
```

Add to constructor parameters. If null, use `RetryPolicy.DEFAULT`.

Each client subclass (`PubSubClient`, `CQClient`, `QueuesClient`) passes `RetryPolicy` through to the `RetryExecutor` and uses it for all operations.

#### 5.2.6 Independent Backoff Policies

Per GS requirement (derived from body text): operation retry backoff (this `RetryPolicy`) and connection reconnection backoff (Category 02, REQ-CONN-1) MUST be independent configurations. The `RetryPolicy` governs operation-level retry only. A separate `ReconnectPolicy` (specified in Category 02 spec) governs connection-level reconnection. Changing one does not affect the other.

### 5.3 Worst-Case Latency Documentation

With default policy (3 retries, 500ms initial, 2.0 multiplier, 5s operation timeout, no jitter):
- Attempt 1: 5s timeout
- Backoff 1: 500ms
- Attempt 2: 5s timeout
- Backoff 2: 1000ms
- Attempt 3: 5s timeout
- Backoff 3: 2000ms
- Attempt 4 (final): 5s timeout
- **Total worst-case: 23.5s**

### 5.4 Test Scenarios

| # | Test | Setup | Action | Assert |
|---|------|-------|--------|--------|
| T1 | Retry on transient error | Mock operation fails 2x then succeeds | Execute with default policy | Returns success; 3 total calls |
| T2 | No retry on non-retryable | Mock AuthenticationException | Execute | Throws immediately; 1 total call |
| T3 | maxRetries=0 disables retry | Policy with maxRetries=0 | Execute failing op | Throws after 1 call |
| T4 | Retry exhaustion message | Mock always fails transient | Execute | Message includes attempt count + duration |
| T5 | UNKNOWN max 1 retry | Mock UNKNOWN_ERROR | Execute with maxRetries=5 | Only 2 total calls |
| T6 | Queue send not retried on DEADLINE_EXCEEDED | Mock DEADLINE_EXCEEDED for queue | Execute with UNSAFE_ON_AMBIGUOUS | Throws after 1 call |
| T7 | Events publish retried on DEADLINE_EXCEEDED | Mock DEADLINE_EXCEEDED for events | Execute with SAFE | Retries up to max |
| T8 | Jitter adds randomness | Run computeBackoff 100 times | Collect results | Values distributed in expected range |
| T9 | Policy immutable | Build policy, attempt modification | No setters exist | Compile-time guarantee |
| T10 | gRPC enableRetry removed | Inspect channel builder config | Check gRPC channel | enableRetry not called |
| T11 | Extended backoff for throttling | Mock RESOURCE_EXHAUSTED | Execute | Backoff duration is doubled |
| T12 | DEBUG logging per attempt | Mock 2 failures then success | Execute | 2 DEBUG log entries for retries |

---

## 6. REQ-ERR-4: Per-Operation Timeouts

**Gap Status:** PARTIAL
**GS Reference:** 01-error-handling.md, REQ-ERR-4
**Assessment Evidence:** 4.4.1 (queue ops have timeout; commands/queries use message-level timeout; events/subscriptions have none)
**Effort:** M (1-2 days including tests)
**Depends On:** REQ-ERR-1 (for TimeoutException)

### 6.1 Current State

| Operation | Current Timeout | Source |
|-----------|----------------|--------|
| Queue send | `requestTimeoutSeconds` (default 30s) via `CompletableFuture.get(timeout)` | `QueueUpstreamHandler.java` line 191 |
| Queue receive/poll | `max(pollWaitTimeout + 5, requestTimeout)` via `CompletableFuture.get(timeout)` | `QueueDownstreamHandler.java` line 205-208 |
| Command/Query | Message-level `setTimeout()` (default 10s in proto) | `CommandMessage.java`, `QueryMessage.java` |
| Events publish | None | `PubSubClient.java` line 35 |
| Events store publish | None (blocks on CompletableFuture with cleanup timeout) | `EventStreamHelper.java` |
| Subscribe (initial) | None | `PubSubClient.java` line 201 |
| Ping | None (uses blocking stub without deadline) | `KubeMQClient.java` line 406 |

### 6.2 Target Design

#### 6.2.1 Default Timeout Constants

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/common/Defaults.java` (new file)

```java
package io.kubemq.sdk.common;

import java.time.Duration;

/**
 * Default timeout values per GS REQ-ERR-4.
 */
public final class Defaults {
    private Defaults() {}

    public static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration SUBSCRIBE_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration RPC_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration QUEUE_RECEIVE_SINGLE_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration QUEUE_RECEIVE_STREAMING_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10); // see Cat 02
}
```

#### 6.2.2 Method Signature Changes

Each public operation method gets an overload accepting `Duration timeout`. The existing no-timeout method delegates to the overloaded version with the default timeout.

**PubSubClient modifications:**

```java
// Existing (backward compatible):
public void sendEventsMessage(EventMessage message) {
    sendEventsMessage(message, Defaults.SEND_TIMEOUT);
}

// New overload:
public void sendEventsMessage(EventMessage message, Duration timeout) {
    // Apply timeout via gRPC deadline
    // ... uses blockingStub.withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS)
}
```

**Operations and their default timeouts:**

| Method | Class | Default Timeout | gRPC Deadline Mechanism |
|--------|-------|----------------|------------------------|
| `sendEventsMessage` | `PubSubClient` | 5s | Stream helper with timeout |
| `sendEventsStoreMessage` | `PubSubClient` | 5s | Stream helper with timeout |
| `sendCommandRequest` | `CQClient` | 10s | `blockingStub.withDeadlineAfter()` |
| `sendQueryRequest` | `CQClient` | 10s | `blockingStub.withDeadlineAfter()` |
| `subscribeToEvents` | `PubSubClient` | 10s (initial connection) | Async stub with initial deadline |
| `subscribeToEventsStore` | `PubSubClient` | 10s (initial connection) | Async stub with initial deadline |
| `subscribeToCommands` | `CQClient` | 10s (initial connection) | Async stub with initial deadline |
| `subscribeToQueries` | `CQClient` | 10s (initial connection) | Async stub with initial deadline |
| `sendQueuesMessage` | `QueuesClient` | 10s | Already has timeout; change default from 30s to 10s |
| `receiveQueuesMessages` | `QueuesClient` | 30s (streaming) | Already has timeout; keep 30s |
| `ping` | `KubeMQClient` | 5s | `blockingStub.withDeadlineAfter()` |
| `createChannel` / `deleteChannel` / `listChannels` | All clients | 10s | `blockingStub.withDeadlineAfter()` |

#### 6.2.3 Timeout Error Classification

When a gRPC `DEADLINE_EXCEEDED` is caught, it is mapped to `TimeoutException` (via GrpcErrorMapper, see REQ-ERR-6). `TimeoutException` has `isRetryable() = true` but subject to operation safety rules (REQ-ERR-3).

### 6.3 Test Scenarios

| # | Test | Setup | Action | Assert |
|---|------|-------|--------|--------|
| T1 | Default timeout applied | No timeout param | Call sendEventsMessage | gRPC deadline set to 5s |
| T2 | Custom timeout respected | Pass Duration.ofSeconds(2) | Call sendEventsMessage(msg, 2s) | gRPC deadline set to 2s |
| T3 | Timeout produces TimeoutException | Server delays > timeout | Call with 1s timeout | Throws TimeoutException |
| T4 | Timeout exception is retryable | Catch TimeoutException | Check isRetryable() | true |
| T5 | Ping has timeout | Server unavailable | Call ping() | Times out after 5s default |

---

## 7. REQ-ERR-5: Actionable Error Messages

**Gap Status:** PARTIAL
**GS Reference:** 01-error-handling.md, REQ-ERR-5
**Assessment Evidence:** 4.2.1 (no fix suggestions), 4.2.2 (limited context), 4.2.4 (no consistent format)
**Effort:** S (< 1 day, done alongside REQ-ERR-1 migration)
**Depends On:** REQ-ERR-1

### 7.1 Current State

Error messages describe what happened but do not suggest fixes. Examples:
- `"Request timed out after 60000ms"` (no suggestion)
- `"Failed to send event message"` (no channel, no cause detail)
- Queue errors include request ID but not channel name

### 7.2 Target Design

#### 7.2.1 ErrorMessageBuilder

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/exception/ErrorMessageBuilder.java` (new file)

```java
package io.kubemq.sdk.exception;

import java.time.Duration;

/**
 * Builds actionable error messages per GS REQ-ERR-5.
 * Format: {Operation} failed on channel "{Channel}": {cause}
 *   Suggestion: {how to fix}
 *   [Retries exhausted: {n}/{max} attempts over {duration}]
 */
public final class ErrorMessageBuilder {

    private ErrorMessageBuilder() {}

    /**
     * Builds an error message with operation context and suggestion.
     */
    public static String build(String operation, String channel, String cause,
                               ErrorCode code) {
        StringBuilder sb = new StringBuilder();
        sb.append(operation);
        if (channel != null) {
            sb.append(" failed on channel \"").append(channel).append("\"");
        } else {
            sb.append(" failed");
        }
        sb.append(": ").append(cause);
        String suggestion = getSuggestion(code);
        if (suggestion != null) {
            sb.append("\n  Suggestion: ").append(suggestion);
        }
        return sb.toString();
    }

    /**
     * Appends retry exhaustion context to a message.
     */
    public static String withRetryContext(String baseMessage, int attempts,
                                          int maxAttempts, Duration totalDuration) {
        return baseMessage + String.format(
            "\n  Retries exhausted: %d/%d attempts over %s",
            attempts, maxAttempts, formatDuration(totalDuration));
    }

    /**
     * Returns a suggestion for the given error code.
     */
    static String getSuggestion(ErrorCode code) {
        if (code == null) return null;
        switch (code) {
            case AUTHENTICATION_FAILED:
                return "Check your auth token configuration. Ensure the token is valid and not expired.";
            case AUTHORIZATION_DENIED:
                return "Check channel permissions for this client ID.";
            case CONNECTION_FAILED:
            case UNAVAILABLE:
                return "Check server connectivity and firewall rules. "
                     + "Verify the server address and port are correct.";
            case CONNECTION_TIMEOUT:
            case OPERATION_TIMEOUT:
            case DEADLINE_EXCEEDED:
                return "The operation timed out. Consider increasing the timeout "
                     + "or checking server load.";
            case INVALID_ARGUMENT:
            case FAILED_PRECONDITION:
                return "Check the request parameters. Refer to the API documentation.";
            case NOT_FOUND:
                return "The channel or queue does not exist. "
                     + "Create it first or check the channel name.";
            case RESOURCE_EXHAUSTED:
                return "The server is rate limiting requests. "
                     + "Reduce request rate or contact your administrator.";
            case BUFFER_FULL:
                return "The SDK send buffer is full. Wait for the connection "
                     + "to recover or increase the buffer size.";
            case SERVER_INTERNAL:
            case DATA_LOSS:
                return "An internal server error occurred. "
                     + "Check server logs for details.";
            case SERVER_UNIMPLEMENTED:
                return "This operation is not supported by the server. "
                     + "Check server version compatibility.";
            case STREAM_BROKEN:
                return "The stream was broken. The SDK will attempt to reconnect. "
                     + "Check unacknowledged messages.";
            default:
                return null;
        }
    }

    private static String formatDuration(Duration d) {
        long millis = d.toMillis();
        if (millis < 1000) return millis + "ms";
        return String.format("%.1fs", millis / 1000.0);
    }
}
```

#### 7.2.2 COMPLIANT Item: No Internal Details Exposed

**Status:** COMPLIANT (with verification caveat)

Assessment 4.2.3 confirms no stack traces or raw gRPC frames leak to users. Marked COMPLIANT based on available evidence. **Verification needed:** During REQ-ERR-1 throw site migration, audit each new error message to ensure no internal gRPC frame data, raw proto content, or stack trace information appears in user-facing messages. Per review R2 m-1, this is a forward-looking verification task.

### 7.3 Test Scenarios

| # | Test | Setup | Action | Assert |
|---|------|-------|--------|--------|
| T1 | Message includes operation | Build with operation="sendEventsMessage" | Get message | Contains "sendEventsMessage" |
| T2 | Message includes channel | Build with channel="orders.events" | Get message | Contains `"orders.events"` |
| T3 | Message includes suggestion | Build with code=AUTHENTICATION_FAILED | Get message | Contains "Check your auth token" |
| T4 | Retry context appended | Call withRetryContext | Get message | Contains "3/3 attempts over 12.4s" |
| T5 | No internal details | Build all error codes | Get messages | No raw gRPC status strings, no stack traces |

---

## 8. REQ-ERR-6: gRPC Error Mapping

**Gap Status:** MISSING
**GS Reference:** 01-error-handling.md, REQ-ERR-6
**Assessment Evidence:** 4.1.4 (status code never extracted), 4.1.5 (cause chain lost in some wrapping)
**Effort:** M (2-3 days including tests)
**Depends On:** REQ-ERR-1, REQ-ERR-2

### 8.1 Current State

- `StatusRuntimeException` is caught but the status code is never extracted or mapped
- `GRPCException` wraps messages as strings, discarding gRPC codes
- Some wrapping loses the cause chain (`new RuntimeException(e.getMessage())` instead of `new RuntimeException(e.getMessage(), e)`)
- No rich error detail extraction from `google.rpc.Status`
- No distinction between client-initiated and server-initiated CANCELLED

### 8.2 Target Design

#### 8.2.1 GrpcErrorMapper

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/exception/GrpcErrorMapper.java` (new file)

```java
package io.kubemq.sdk.exception;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
// NOTE: Do NOT import com.google.rpc.Status -- it collides with io.grpc.Status.
// Fully qualify as com.google.rpc.Status in extractRichDetails() instead.

/**
 * Maps gRPC StatusRuntimeException to SDK-typed KubeMQException.
 * Covers all 17 gRPC status codes (0-16).
 * Original gRPC error is always preserved in the cause chain.
 */
public final class GrpcErrorMapper {

    private GrpcErrorMapper() {}

    /**
     * Maps a gRPC StatusRuntimeException to a KubeMQException subtype.
     *
     * @param grpcError the gRPC exception to map
     * @param operation the operation that was being performed
     * @param channel the target channel (may be null)
     * @param requestId the request ID (may be null)
     * @param localContextCancelled true if the local context/token was cancelled
     *                              (used to distinguish client vs server CANCELLED)
     * @return a KubeMQException subtype with the original gRPC error in the cause chain
     */
    public static KubeMQException map(StatusRuntimeException grpcError,
                                       String operation, String channel,
                                       String requestId,
                                       boolean localContextCancelled) {
        Status status = grpcError.getStatus();
        Status.Code code = status.getCode();
        String description = status.getDescription() != null
            ? status.getDescription() : grpcError.getMessage();
        int grpcStatusCode = code.value();

        // Extract rich error details if present
        String richDetails = extractRichDetails(grpcError);
        if (richDetails != null) {
            description = description + " [details: " + richDetails + "]";
        }

        String message = ErrorMessageBuilder.build(operation, channel, description,
            toErrorCode(code, localContextCancelled));

        switch (code) {
            case OK:
                return null; // Should not happen

            case CANCELLED:
                if (localContextCancelled) {
                    return OperationCancelledException.builder()
                        .code(ErrorCode.CANCELLED_BY_CLIENT)
                        .message(message)
                        .operation(operation).channel(channel).requestId(requestId)
                        .cause(grpcError).statusCode(grpcStatusCode)
                        .category(ErrorCategory.CANCELLATION).retryable(false)
                        .build();
                } else {
                    // Server-initiated cancellation: treat as transient, retryable
                    return ConnectionException.builder()
                        .code(ErrorCode.CANCELLED_BY_SERVER)
                        .message(message)
                        .operation(operation).channel(channel).requestId(requestId)
                        .cause(grpcError).statusCode(grpcStatusCode)
                        .category(ErrorCategory.TRANSIENT).retryable(true)
                        .build();
                }

            case UNKNOWN:
                // Retryable, but max 1 retry (enforced by RetryExecutor)
                return ConnectionException.builder()
                    .code(ErrorCode.UNKNOWN_ERROR)
                    .message(message)
                    .operation(operation).channel(channel).requestId(requestId)
                    .cause(grpcError).statusCode(grpcStatusCode)
                    .category(ErrorCategory.TRANSIENT).retryable(true)
                    .build();

            case INVALID_ARGUMENT:
                return ValidationException.builder()
                    .code(ErrorCode.INVALID_ARGUMENT)
                    .message(message)
                    .operation(operation).channel(channel).requestId(requestId)
                    .cause(grpcError).statusCode(grpcStatusCode)
                    .build();

            case DEADLINE_EXCEEDED:
                return TimeoutException.builder()
                    .code(ErrorCode.DEADLINE_EXCEEDED)
                    .message(message)
                    .operation(operation).channel(channel).requestId(requestId)
                    .cause(grpcError).statusCode(grpcStatusCode)
                    .build();

            case NOT_FOUND:
                return KubeMQException.builder()
                    .code(ErrorCode.NOT_FOUND)
                    .message(message)
                    .operation(operation).channel(channel).requestId(requestId)
                    .cause(grpcError).statusCode(grpcStatusCode)
                    .category(ErrorCategory.NOT_FOUND).retryable(false)
                    .build();

            case ALREADY_EXISTS:
                return ValidationException.builder()
                    .code(ErrorCode.ALREADY_EXISTS)
                    .message(message)
                    .operation(operation).channel(channel).requestId(requestId)
                    .cause(grpcError).statusCode(grpcStatusCode)
                    .build();

            case PERMISSION_DENIED:
                return AuthorizationException.builder()
                    .message(message)
                    .operation(operation).channel(channel).requestId(requestId)
                    .cause(grpcError).statusCode(grpcStatusCode)
                    .build();

            case RESOURCE_EXHAUSTED:
                return ThrottlingException.builder()
                    .message(message)
                    .operation(operation).channel(channel).requestId(requestId)
                    .cause(grpcError).statusCode(grpcStatusCode)
                    .build();

            case FAILED_PRECONDITION:
                return ValidationException.builder()
                    .code(ErrorCode.FAILED_PRECONDITION)
                    .message(message)
                    .operation(operation).channel(channel).requestId(requestId)
                    .cause(grpcError).statusCode(grpcStatusCode)
                    .build();

            case ABORTED:
                return ConnectionException.builder()
                    .code(ErrorCode.ABORTED)
                    .message(message)
                    .operation(operation).channel(channel).requestId(requestId)
                    .cause(grpcError).statusCode(grpcStatusCode)
                    .category(ErrorCategory.TRANSIENT).retryable(true)
                    .build();

            case OUT_OF_RANGE:
                return ValidationException.builder()
                    .code(ErrorCode.OUT_OF_RANGE)
                    .message(message)
                    .operation(operation).channel(channel).requestId(requestId)
                    .cause(grpcError).statusCode(grpcStatusCode)
                    .build();

            case UNIMPLEMENTED:
                return ServerException.builder()
                    .code(ErrorCode.SERVER_UNIMPLEMENTED)
                    .message(message)
                    .operation(operation).channel(channel).requestId(requestId)
                    .cause(grpcError).statusCode(grpcStatusCode)
                    .build();

            case INTERNAL:
                // Fatal by default. Opt-in single retry not implemented (GS: user opt-in).
                return ServerException.builder()
                    .code(ErrorCode.SERVER_INTERNAL)
                    .message(message)
                    .operation(operation).channel(channel).requestId(requestId)
                    .cause(grpcError).statusCode(grpcStatusCode)
                    .build();

            case UNAVAILABLE:
                return ConnectionException.builder()
                    .code(ErrorCode.UNAVAILABLE)
                    .message(message)
                    .operation(operation).channel(channel).requestId(requestId)
                    .cause(grpcError).statusCode(grpcStatusCode)
                    .category(ErrorCategory.TRANSIENT).retryable(true)
                    .build();

            case DATA_LOSS:
                return ServerException.builder()
                    .code(ErrorCode.DATA_LOSS)
                    .message(message)
                    .operation(operation).channel(channel).requestId(requestId)
                    .cause(grpcError).statusCode(grpcStatusCode)
                    .build();

            case UNAUTHENTICATED:
                return AuthenticationException.builder()
                    .message(message)
                    .operation(operation).channel(channel).requestId(requestId)
                    .cause(grpcError).statusCode(grpcStatusCode)
                    .build();

            default:
                return KubeMQException.builder()
                    .code(ErrorCode.UNKNOWN_ERROR)
                    .message(message)
                    .operation(operation).channel(channel).requestId(requestId)
                    .cause(grpcError).statusCode(grpcStatusCode)
                    .category(ErrorCategory.FATAL).retryable(false)
                    .build();
        }
    }

    /**
     * Extracts rich error details from google.rpc.Status if present.
     * Uses {@code StatusProto.fromThrowable()} to extract the full google.rpc.Status proto,
     * then iterates over {@code getDetailsList()} to extract any {@code google.rpc.ErrorInfo},
     * {@code google.rpc.RetryInfo}, {@code google.rpc.DebugInfo}, etc. that the server
     * may have attached.
     *
     * @param grpcError the gRPC exception that may contain rich error details
     * @return a human-readable string of the details, or null if none present
     */
    private static String extractRichDetails(StatusRuntimeException grpcError) {
        try {
            com.google.rpc.Status rpcStatus = StatusProto.fromThrowable(grpcError);
            if (rpcStatus == null || rpcStatus.getDetailsCount() == 0) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            for (com.google.protobuf.Any detail : rpcStatus.getDetailsList()) {
                // Attempt to unpack known detail types
                if (detail.is(com.google.rpc.ErrorInfo.class)) {
                    com.google.rpc.ErrorInfo errorInfo = detail.unpack(com.google.rpc.ErrorInfo.class);
                    sb.append("ErrorInfo{reason=").append(errorInfo.getReason())
                      .append(", domain=").append(errorInfo.getDomain())
                      .append(", metadata=").append(errorInfo.getMetadataMap()).append("} ");
                } else if (detail.is(com.google.rpc.RetryInfo.class)) {
                    com.google.rpc.RetryInfo retryInfo = detail.unpack(com.google.rpc.RetryInfo.class);
                    sb.append("RetryInfo{retryDelay=").append(retryInfo.getRetryDelay()).append("} ");
                } else if (detail.is(com.google.rpc.DebugInfo.class)) {
                    com.google.rpc.DebugInfo debugInfo = detail.unpack(com.google.rpc.DebugInfo.class);
                    sb.append("DebugInfo{detail=").append(debugInfo.getDetail()).append("} ");
                } else {
                    // Unknown detail type -- include type URL for debugging
                    sb.append("Unknown{typeUrl=").append(detail.getTypeUrl()).append("} ");
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            // Rich details not available or unpacking failed; this is normal.
            return null;
        }
    }

    private static ErrorCode toErrorCode(Status.Code code, boolean localContextCancelled) {
        switch (code) {
            case CANCELLED: return localContextCancelled
                ? ErrorCode.CANCELLED_BY_CLIENT : ErrorCode.CANCELLED_BY_SERVER;
            case UNKNOWN: return ErrorCode.UNKNOWN_ERROR;
            case INVALID_ARGUMENT: return ErrorCode.INVALID_ARGUMENT;
            case DEADLINE_EXCEEDED: return ErrorCode.DEADLINE_EXCEEDED;
            case NOT_FOUND: return ErrorCode.NOT_FOUND;
            case ALREADY_EXISTS: return ErrorCode.ALREADY_EXISTS;
            case PERMISSION_DENIED: return ErrorCode.AUTHORIZATION_DENIED;
            case RESOURCE_EXHAUSTED: return ErrorCode.RESOURCE_EXHAUSTED;
            case FAILED_PRECONDITION: return ErrorCode.FAILED_PRECONDITION;
            case ABORTED: return ErrorCode.ABORTED;
            case OUT_OF_RANGE: return ErrorCode.OUT_OF_RANGE;
            case UNIMPLEMENTED: return ErrorCode.SERVER_UNIMPLEMENTED;
            case INTERNAL: return ErrorCode.SERVER_INTERNAL;
            case UNAVAILABLE: return ErrorCode.UNAVAILABLE;
            case DATA_LOSS: return ErrorCode.DATA_LOSS;
            case UNAUTHENTICATED: return ErrorCode.AUTHENTICATION_FAILED;
            default: return ErrorCode.UNKNOWN_ERROR;
        }
    }
}
```

**Note:** The `ErrorCode` enum in section 3.2.2 needs `ABORTED` and `OUT_OF_RANGE` added.

#### 8.2.2 ClientInterceptor for Centralized Mapping (Alternative Approach)

Per review R1 recommendation #5, a `ClientInterceptor` provides centralized mapping. However, since not all gRPC calls currently go through interceptors (some use blocking stubs directly), the initial implementation uses `GrpcErrorMapper` as a utility called at each catch site. A future refactoring to interceptor-based mapping is recommended when the layered architecture (REQ-CQ-1) is implemented.

**Initial approach:** Wrap all `catch (StatusRuntimeException e)` blocks with `GrpcErrorMapper.map(...)`.

**Future approach (after REQ-CQ-1):** Implement `ErrorMappingInterceptor` as a `ClientInterceptor` in the interceptor chain.

#### 8.2.3 Future Enhancement: Retry-After Metadata

The `GrpcErrorMapper` architecture is designed to accommodate future `Retry-After` header support for `RESOURCE_EXHAUSTED` responses. The `ThrottlingException` can be extended with a `retryAfter` duration field when server support is available. No implementation needed now, but the field should be reserved:

```java
// In ThrottlingException (future):
private final Duration retryAfter; // null until server supports it
```

### 8.3 Test Scenarios

| # | Test | Setup | Action | Assert |
|---|------|-------|--------|--------|
| T1 | All 17 codes mapped | Create StatusRuntimeException for each code | Map each | Returns correct subtype |
| T2 | UNAVAILABLE -> ConnectionException, retryable | Map UNAVAILABLE | Check type + retryable | ConnectionException, true |
| T3 | UNAUTHENTICATED -> AuthenticationException, not retryable | Map UNAUTHENTICATED | Check | AuthenticationException, false |
| T4 | CANCELLED client-initiated | Map with localContextCancelled=true | Check | OperationCancelledException, not retryable |
| T5 | CANCELLED server-initiated | Map with localContextCancelled=false | Check | ConnectionException, retryable |
| T6 | Original gRPC error preserved | Map any code | getCause() | Returns original StatusRuntimeException |
| T7 | Status code preserved | Map UNAVAILABLE (code 14) | getStatusCode() | Returns 14 |
| T8 | Rich error details extracted | Create error with google.rpc.Status | Map | Message includes details |
| T9 | Error message includes operation + channel | Map with operation + channel | getMessage() | Contains both |

---

## 9. REQ-ERR-7: Retry Throttling

**Gap Status:** NOT_ASSESSED (treat as MISSING)
**Verification Needed:** Confirm no concurrent retry limiting exists (evidence: no semaphore or rate limiter in current code).
**GS Reference:** 01-error-handling.md, REQ-ERR-7
**Effort:** S (< 1 day, integrated into RetryExecutor)
**Depends On:** REQ-ERR-3

### 9.1 Current State

No concurrent retry limiting exists in the SDK. Confirmed by code search: no `Semaphore`, `RateLimiter`, or similar constructs in the retry path.

### 9.2 Target Design

Retry throttling is integrated into `RetryExecutor` (section 5.2.3) and `RetryPolicy` (section 5.2.1):

- `RetryPolicy.maxConcurrentRetries` field (default: 10, range: 0-100)
- `RetryExecutor` uses `java.util.concurrent.Semaphore` with `tryAcquire()` before each retry
- When limit reached, immediately throws `RetryThrottledException` (subclass of `KubeMQException` with `code=RETRY_THROTTLED`, `category=THROTTLING`, `retryable=false`)
- Permit released in `finally` block

### 9.3 Test Scenarios

| # | Test | Setup | Action | Assert |
|---|------|-------|--------|--------|
| T1 | Under limit: retry proceeds | maxConcurrentRetries=10, 5 concurrent retries | All proceed | All complete normally |
| T2 | At limit: new retry throttled | maxConcurrentRetries=2, 2 in-flight, new attempt | tryAcquire | Throws RetryThrottledException |
| T3 | Permits released on completion | Fill semaphore, complete one | New retry | Proceeds |
| T4 | maxConcurrentRetries=0 means unlimited | Set to 0 | 100 concurrent retries | All proceed |
| T5 | ThrottleException has indicator | Catch RetryThrottledException | Check code | RETRY_THROTTLED |

---

## 10. REQ-ERR-8: Streaming Error Handling

**Gap Status:** PARTIAL
**GS Reference:** 01-error-handling.md, REQ-ERR-8
**Assessment Evidence:** 4.3.1 (queue streams no auto-reconnect), 3.2.6 (subscriptions auto-reconnect), 4.4.3 (queue batch individual ack/reject)
**Effort:** L (3-5 days including tests)
**Depends On:** REQ-ERR-1, REQ-ERR-3; also REQ-CONN-2 (Category 02)

### 10.1 Current State

| Stream Type | Current Behavior | GS Requirement | Gap |
|-------------|-----------------|----------------|-----|
| Event subscriptions | Auto-reconnect via `reconnect()` in `EventsSubscription.java` | Auto-reconnect with backoff | PARTIAL (no jitter, retries all errors) |
| EventStore subscriptions | Same pattern as Events | Auto-reconnect with backoff | PARTIAL (same issues) |
| Command/Query subscriptions | Same pattern | Auto-reconnect with backoff | PARTIAL |
| Queue upstream (send) | `closeStreamWithError()` completes all pending futures with error, no reconnect (`QueueUpstreamHandler.java` line 120-132) | Stream reconnect with backoff | MISSING |
| Queue downstream (receive) | Same as upstream -- `closeStreamWithError()`, no reconnect (`QueueDownstreamHandler.java` line 125-140) | Stream reconnect with backoff | MISSING |

Key issues:
- Queue streams have NO auto-reconnect -- stream errors complete all pending with generic error
- `closeStreamWithError` in `QueueUpstreamHandler` does not track which message IDs were in-flight
- No `StreamBrokenException` type (defined in REQ-ERR-1)
- Stream errors and connection errors are not distinguished

### 10.2 Target Design

#### 10.2.1 Stream Reconnection for Queue Handlers

**Files to modify:**
- `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueUpstreamHandler.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueDownstreamHandler.java`

**Changes to `QueueUpstreamHandler`:**

1. Replace `closeStreamWithError()` with `handleStreamError()`:
   - Collect in-flight message IDs from `pendingResponses.keySet()`
   - Complete each pending future with `StreamBrokenException` including the list of unacknowledged IDs
   - Schedule stream reconnection using the client's `RetryPolicy` backoff

2. Add stream reconnection method:
   ```java
   private void reconnectStream() {
       // Use RetryPolicy backoff (same policy as operation retry, per GS)
       // Attempt to re-establish the gRPC stream
       // On success: isConnected.set(true), create new requestsObserver
       // On failure: schedule next attempt with backoff
   }
   ```

3. Track in-flight messages:
   - `pendingResponses` already tracks request IDs -> change values from `CompletableFuture<QueueSendResult>` to include the original message ID for reporting

**Changes to `QueueDownstreamHandler`:**
- Same pattern as upstream: reconnect on stream error, report unacknowledged message IDs

#### 10.2.2 Subscription Reconnection Updates

**Files to modify:**
- `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsSubscription.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsStoreSubscription.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/cq/CommandsSubscription.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/cq/QueriesSubscription.java`

**Changes:**

1. **Use RetryPolicy backoff instead of hardcoded backoff:**
   Replace `Math.min(base * 2^(attempt-1), 60000)` with `RetryPolicy.computeBackoff(attempt)` (which includes jitter).

2. **Check error classification before retrying:**
   Replace `if (t instanceof StatusRuntimeException)` (line 143 in `EventsSubscription.java`) with:
   ```java
   KubeMQException mapped = GrpcErrorMapper.map((StatusRuntimeException) t, ...);
   if (mapped.isRetryable()) {
       reconnect(pubSubClient);
   } else {
       raiseOnError(mapped);  // Error callback with typed exception
   }
   ```

3. **Pass typed error to error callback:**
   Change `Consumer<String> onErrorCallback` to `Consumer<KubeMQException> onErrorCallback` (see REQ-ERR-9 for full design).

#### 10.2.3 Stream State vs Connection State

Per GS: "A broken stream does NOT change connection state from READY unless the underlying connection is also broken."

The current code conflates stream errors with connection errors. After implementation:
- Stream error (`onError` in `StreamObserver`) -> reconnect the stream only
- Connection error (detected by `KubeMQClient.handleStateChange()`) -> triggers connection reconnection (Category 02 scope)

This separation requires coordination with REQ-CONN-2 (connection state machine).

### 10.3 Test Scenarios

| # | Test | Setup | Action | Assert |
|---|------|-------|--------|--------|
| T1 | Queue upstream stream breaks, reconnects | Mock stream onError | Wait for reconnect | Stream re-established, pending futures completed with StreamBrokenException |
| T2 | StreamBrokenException includes message IDs | 3 in-flight messages, stream breaks | Check exception | unacknowledgedMessageIds has 3 entries |
| T3 | Queue downstream stream breaks, reconnects | Mock stream onError | Wait for reconnect | Stream re-established |
| T4 | Subscription uses jitter on reconnect | Force reconnect | Check delay values | Delay varies between runs (jitter) |
| T5 | Non-retryable error stops subscription reconnect | Mock UNAUTHENTICATED | onError fires | No reconnect attempt; error callback invoked |
| T6 | Per-message error does not kill stream | Queue poll with one bad message | Process messages | Stream continues after bad message |
| T7 | Stream reconnect uses RetryPolicy backoff | Configure custom RetryPolicy | Force stream error | Backoff matches policy |

---

## 11. REQ-ERR-9: Async Error Propagation

**Gap Status:** PARTIAL
**GS Reference:** 01-error-handling.md, REQ-ERR-9
**Assessment Evidence:** 8.5.2 (onErrorCallback exists), 4.2.3 (visibility timer exceptions caught/logged)
**Effort:** M (1-2 days including tests)
**Depends On:** REQ-ERR-1

### 11.1 Current State

- `onErrorCallback` exists on all 4 subscription types as `Consumer<String>` -- it receives a string message, not a typed exception
- `QueueDownstreamHandler` has no error callback for async operations
- No distinction between transport errors and handler (user code) errors
- If `onReceiveEventCallback` throws, it propagates up and may kill the subscription
- If no `onErrorCallback` is registered, errors are logged but not consistently at ERROR level

### 11.2 Target Design

#### 11.2.1 Change Error Callback Type

**All subscription classes:** Change `Consumer<String> onErrorCallback` to `Consumer<KubeMQException> onErrorCallback`.

**Files to modify:**
- `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsSubscription.java` line 58
- `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsStoreSubscription.java` line 79
- `kubemq-java/src/main/java/io/kubemq/sdk/cq/CommandsSubscription.java` line 40
- `kubemq-java/src/main/java/io/kubemq/sdk/cq/QueriesSubscription.java` line 40

**Breaking change:** Yes. See section 13 for migration.

#### 11.2.2 TransportException and HandlerException

These are already defined in the hierarchy (section 3.2.4).

- `TransportException`: wraps gRPC stream errors (onError from StreamObserver). Category: varies (mapped from gRPC code).
- `HandlerException`: wraps exceptions thrown by user callback code. Category: FATAL. `isRetryable() = false`.

#### 11.2.3 Handler Error Isolation

In each subscription's `StreamObserver.onNext()`, wrap the user callback invocation:

```java
@Override
public void onNext(Kubemq.EventReceive messageReceive) {
    try {
        raiseOnReceiveMessage(EventMessageReceived.decode(messageReceive));
    } catch (Exception userException) {
        // User handler error: wrap as HandlerException, invoke error callback
        HandlerException handlerError = HandlerException.builder()
            .message("User handler threw exception: " + userException.getMessage())
            .operation("onReceiveEvent")
            .channel(channel)
            .cause(userException)
            .build();
        raiseOnError(handlerError);
        // DO NOT terminate the subscription -- continue processing
    }
}
```

#### 11.2.4 Default Error Logging

If no `onErrorCallback` is registered, log at ERROR level:

```java
public void raiseOnError(KubeMQException error) {
    if (onErrorCallback != null) {
        onErrorCallback.accept(error);
    } else {
        log.error("Unhandled async error in subscription [channel={}]: {}",
                  channel, error.getMessage(), error);
    }
}
```

#### 11.2.5 Queue Downstream Error Callback

Add `Consumer<KubeMQException> onErrorCallback` to `QueuesPollRequest` (or to `QueuesClient` at client level) so queue downstream errors can be reported to user code.

### 11.3 Test Scenarios

| # | Test | Setup | Action | Assert |
|---|------|-------|--------|--------|
| T1 | Transport error invokes callback with TransportException | Subscription + mock stream error | onError fires | Callback receives TransportException |
| T2 | Handler error invokes callback with HandlerException | User callback throws | Process message | Callback receives HandlerException |
| T3 | Handler error does not kill subscription | User callback throws | Continue sending messages | Subscription continues processing |
| T4 | No callback: error logged at ERROR | No onErrorCallback set | Stream error | ERROR log entry present |
| T5 | Transport and Handler distinguishable | Catch both types | instanceof check | Can distinguish via type |
| T6 | Queue downstream error callback | Queue receive + stream error | onError fires | Error callback invoked |

---

## 12. Cross-Category Dependencies

### 12.1 What This Spec Depends On

| Dependency | Category | Impact | Notes |
|-----------|----------|--------|-------|
| None for Phase 1-2 | -- | -- | Error hierarchy and classification are self-contained |
| REQ-CONN-2 (State Machine) | Cat 02 | REQ-ERR-8 stream vs connection state separation | Phase 4 only |

### 12.2 What Depends On This Spec

| Dependent | Category | Dependency | Notes |
|-----------|----------|------------|-------|
| REQ-CONN-1 (Reconnection) | Cat 02 | REQ-ERR-1 (error types), REQ-ERR-2 (classification) | Reconnection needs to classify errors |
| REQ-CONN-2 (State Machine) | Cat 02 | REQ-ERR-1 (ConnectionException) | State transitions triggered by error types |
| REQ-OBS-1 (Tracing) | Cat 05 | REQ-ERR-1 (error events on spans) | Error events recorded as OTel span events |
| REQ-OBS-3 (Metrics) | Cat 05 | REQ-ERR-2 (error.type attribute values) | Error classification maps to OTel error.type |
| REQ-TEST-1 (Unit Tests) | Cat 04 | REQ-ERR-1 (error types in test assertions) | Tests assert specific exception types |

---

## 13. Breaking Changes

### 13.1 Error Callback Type Change (REQ-ERR-9)

**What:** `Consumer<String> onErrorCallback` changes to `Consumer<KubeMQException> onErrorCallback` on all subscription classes.

**Impact:** Any user code that registers an error callback on subscriptions.

**Migration:**
```java
// Before (v2.1.x):
.onErrorCallback(errorMsg -> System.err.println("Error: " + errorMsg))

// After (v2.2.0):
.onErrorCallback(error -> System.err.println("Error: " + error.getMessage()))
```

**Timeline:** Introduce in v2.2.0. The change is small and mechanical. Provide a `@Deprecated` overload accepting `Consumer<String>` that wraps the string in a `KubeMQException` for one minor version transition period.

### 13.2 Exception Type Changes (REQ-ERR-1)

**What:** Methods that previously threw `RuntimeException` now throw `KubeMQException` (a subtype of `RuntimeException`).

**Impact:** LOW. `KubeMQException extends RuntimeException`, so existing `catch (RuntimeException e)` blocks still work. Code catching the specific old types (`GRPCException`, `CreateChannelException`, `DeleteChannelException`, `ListChannelsException`) will continue to work during the deprecation period since they extend `KubeMQException` which extends `RuntimeException`.

**Migration:** Update catch blocks to use `KubeMQException` and its subtypes for richer error information.

### 13.3 gRPC enableRetry Removal (REQ-ERR-3)

**What:** `.enableRetry()` removed from gRPC channel construction.

**Impact:** LOW. gRPC-level retry was not documented as a feature. SDK-level retry replaces it with better control.

**Timeline:** v2.2.0. No deprecation period needed.

---

## 14. Open Questions

### OQ-1: GS Internal Inconsistency -- INTERNAL Error Opt-in Retry

GS states: "INTERNAL: Fatal by default. Optional: allow a single retry (configurable, default: no retry). This is a user opt-in, not a default behavior change."

**Question:** Should this be implemented now or deferred?

**Recommendation:** Defer to a future minor version. The mapper marks INTERNAL as non-retryable by default. Adding opt-in later is non-breaking (add a boolean to RetryPolicy).

### OQ-2: Queue Send Timeout Error Message

GS states: For Queue Send on DEADLINE_EXCEEDED, return error with `IsRetryable=false` and message: "Request may have been processed by the server. Check before retrying."

**Question:** Should this exact message text be used, or should it go through ErrorMessageBuilder?

**Recommendation:** Use ErrorMessageBuilder but ensure the suggestion includes this specific text for DEADLINE_EXCEEDED on queue send operations.

### OQ-3: PartialFailureException Immediate Use

GS Future Enhancements says: "A PartialFailureError type SHOULD be added to the error hierarchy now (even if unused)."

**Recommendation:** Added to hierarchy (section 3.2.4) with no call sites. Will be wired when server supports per-message batch status.

### OQ-4: IdempotencyKey Field in Message Types

GS Future Enhancements says: "SDKs should design their message types with room for this field."

**Recommendation:** This is a message type change (Category 08/09 scope), not error handling. Note here for cross-reference. When message types are updated, add `String idempotencyKey` as an optional field.

---

## 15. Migration Guide

### For SDK Users (v2.1.x to v2.2.0)

#### Error Handling

**Before:**
```java
try {
    client.sendEventsMessage(message);
} catch (RuntimeException e) {
    System.err.println("Failed: " + e.getMessage());
}
```

**After:**
```java
try {
    client.sendEventsMessage(message);
} catch (TimeoutException e) {
    // Retry with longer timeout
    System.err.println("Timed out: " + e.getMessage());
} catch (AuthenticationException e) {
    // Don't retry, fix credentials
    System.err.println("Auth failed: " + e.getMessage());
} catch (KubeMQException e) {
    // Generic handler
    if (e.isRetryable()) {
        // SDK already retried; this is after exhaustion
        System.err.println("Failed after retries: " + e.getMessage());
    } else {
        System.err.println("Permanent error: " + e.getMessage());
    }
}
```

#### Subscription Error Callbacks

**Before:**
```java
EventsSubscription.builder()
    .channel("my-channel")
    .onReceiveEventCallback(event -> process(event))
    .onErrorCallback(errorMsg -> log.error("Error: {}", errorMsg))
    .build();
```

**After:**
```java
EventsSubscription.builder()
    .channel("my-channel")
    .onReceiveEventCallback(event -> process(event))
    .onErrorCallback(error -> {
        if (error instanceof TransportException) {
            log.error("Transport error: {}", error.getMessage());
        } else if (error instanceof HandlerException) {
            log.error("Handler error: {}", error.getMessage(), error.getCause());
        }
    })
    .build();
```

#### Retry Policy Configuration

**New in v2.2.0:**
```java
PubSubClient client = PubSubClient.builder()
    .address("localhost:50000")
    .clientId("my-client")
    .retryPolicy(RetryPolicy.builder()
        .maxRetries(5)
        .initialBackoff(Duration.ofMillis(200))
        .maxBackoff(Duration.ofSeconds(60))
        .jitterType(RetryPolicy.JitterType.FULL)
        .build())
    .build();

// Disable retries entirely:
PubSubClient noRetryClient = PubSubClient.builder()
    .address("localhost:50000")
    .clientId("my-client")
    .retryPolicy(RetryPolicy.DISABLED)
    .build();
```

#### Per-Operation Timeouts

**New in v2.2.0:**
```java
// Use default timeout (5s for send):
client.sendEventsMessage(message);

// Use custom timeout:
client.sendEventsMessage(message, Duration.ofSeconds(15));
```

---

## Appendix A: File Inventory

### New Files to Create

| File | Package | Purpose |
|------|---------|---------|
| `exception/KubeMQException.java` | `io.kubemq.sdk.exception` | Base exception class |
| `exception/ErrorCode.java` | `io.kubemq.sdk.exception` | Error code enum |
| `exception/ErrorCategory.java` | `io.kubemq.sdk.exception` | Error category enum |
| `exception/ErrorClassifier.java` | `io.kubemq.sdk.exception` | Classification utility |
| `exception/ErrorMessageBuilder.java` | `io.kubemq.sdk.exception` | Message formatting |
| `exception/GrpcErrorMapper.java` | `io.kubemq.sdk.exception` | gRPC to SDK mapping |
| `exception/ConnectionException.java` | `io.kubemq.sdk.exception` | Connection errors |
| `exception/AuthenticationException.java` | `io.kubemq.sdk.exception` | Auth errors |
| `exception/AuthorizationException.java` | `io.kubemq.sdk.exception` | Permission errors |
| `exception/TimeoutException.java` | `io.kubemq.sdk.exception` | Timeout errors |
| `exception/ValidationException.java` | `io.kubemq.sdk.exception` | Input validation errors |
| `exception/ServerException.java` | `io.kubemq.sdk.exception` | Server-side errors |
| `exception/ThrottlingException.java` | `io.kubemq.sdk.exception` | Rate limiting errors |
| `exception/OperationCancelledException.java` | `io.kubemq.sdk.exception` | Cancellation errors |
| `exception/BackpressureException.java` | `io.kubemq.sdk.exception` | Buffer full errors |
| `exception/StreamBrokenException.java` | `io.kubemq.sdk.exception` | Stream break errors |
| `exception/TransportException.java` | `io.kubemq.sdk.exception` | Transport-layer errors |
| `exception/HandlerException.java` | `io.kubemq.sdk.exception` | User handler errors |
| `exception/RetryThrottledException.java` | `io.kubemq.sdk.exception` | Retry throttle errors |
| `exception/PartialFailureException.java` | `io.kubemq.sdk.exception` | Partial batch failure (future) |
| `retry/RetryPolicy.java` | `io.kubemq.sdk.retry` | Immutable retry config |
| `retry/RetryExecutor.java` | `io.kubemq.sdk.retry` | Retry loop execution |
| `retry/OperationSafety.java` | `io.kubemq.sdk.retry` | Operation safety classification |
| `common/Defaults.java` | `io.kubemq.sdk.common` | Default timeout constants |

### Existing Files to Modify

| File | Changes |
|------|---------|
| `client/KubeMQClient.java` | Remove `enableRetry()` (lines 276, 302). Add `RetryPolicy` field. Add `RetryExecutor` field. Change ping() catch block. |
| `pubsub/PubSubClient.java` | Replace all `throw new RuntimeException(e)` with `GrpcErrorMapper` calls. Add timeout overloads. |
| `pubsub/EventsSubscription.java` | Change `Consumer<String>` to `Consumer<KubeMQException>`. Wrap user callback in try-catch. Use RetryPolicy for backoff. Check error classification before retry. |
| `pubsub/EventsStoreSubscription.java` | Same changes as EventsSubscription. |
| `pubsub/EventStreamHelper.java` | Replace RuntimeException with typed exceptions. |
| `cq/CQClient.java` | Add timeout overloads. Wrap gRPC calls with GrpcErrorMapper. |
| `cq/CommandsSubscription.java` | Same changes as EventsSubscription. |
| `cq/QueriesSubscription.java` | Same changes as EventsSubscription. |
| `queues/QueuesClient.java` | Add timeout overloads. |
| `queues/QueueUpstreamHandler.java` | Add stream reconnection. Replace RuntimeException. Track in-flight IDs. |
| `queues/QueueDownstreamHandler.java` | Add stream reconnection. Replace RuntimeException. Track in-flight IDs. |
| `common/KubeMQUtils.java` | Replace RuntimeException with GrpcErrorMapper calls. |
| `exception/GRPCException.java` | Deprecate, extend KubeMQException. |
| `exception/CreateChannelException.java` | Deprecate, extend KubeMQException. |
| `exception/DeleteChannelException.java` | Deprecate, extend KubeMQException. |
| `exception/ListChannelsException.java` | Deprecate, extend KubeMQException. |

All file paths are relative to `kubemq-java/src/main/java/io/kubemq/sdk/`.
