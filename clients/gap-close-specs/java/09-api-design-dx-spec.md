# Implementation Specification: Category 09 -- API Design & Developer Experience

**SDK:** KubeMQ Java v2
**Category:** 09 -- API Design & DX
**GS Source:** `clients/golden-standard/09-api-design-dx.md`
**Gap Research:** `clients/gap-research/java-gap-research.md` (lines 1313-1408)
**Current Score:** 3.63 / 5.0 | **Target:** 4.0+
**Priority:** P2-P3 (Tier 2)
**Total Estimated Effort:** 8-12 days

---

## Table of Contents

1. [Summary of REQ Items](#1-summary-of-req-items)
2. [Implementation Order](#2-implementation-order)
3. [REQ-DX-1: Language-Idiomatic Configuration](#3-req-dx-1-language-idiomatic-configuration)
4. [REQ-DX-2: Minimal Code Happy Path](#4-req-dx-2-minimal-code-happy-path)
5. [REQ-DX-3: Consistent Verbs Across SDKs](#5-req-dx-3-consistent-verbs-across-sdks)
6. [REQ-DX-4: Fail-Fast Validation](#6-req-dx-4-fail-fast-validation)
7. [REQ-DX-5: Message Builder/Factory](#7-req-dx-5-message-builderfactory)
8. [Cross-Category Dependencies](#8-cross-category-dependencies)
9. [Breaking Changes](#9-breaking-changes)
10. [Migration Guide](#10-migration-guide)
11. [Test Plan](#11-test-plan)
12. [Open Questions](#12-open-questions)

---

## 1. Summary of REQ Items

| REQ | Status | Gap | Effort | Impl Order |
|-----|--------|-----|--------|------------|
| REQ-DX-1 | PARTIAL | Address validation, default-value Javadoc, optional validateOnBuild | S-M (1-2 days) | 1 |
| REQ-DX-2 | PARTIAL | Convenience publish methods, default address/clientId | S-M (1-2 days) | 2 |
| REQ-DX-3 | PARTIAL | Verb alignment (alias methods + deprecation cycle) | M (2-3 days) | 3 |
| REQ-DX-4 | PARTIAL | ValidationException integration, address/clientId validation | S-M (1-2 days) | 1 (co-implemented with DX-1) |
| REQ-DX-5 | PARTIAL | Build-time validation, message immutability (phased) | M (2-3 days non-breaking phase; breaking phase deferred to v3.0) | 4 |

---

## 2. Implementation Order

The order reflects dependency chains and risk management. Items within the same phase can be parallelized.

**Phase 1 -- Validation & Configuration (days 1-3):**
1. REQ-DX-4 (fail-fast validation) -- depends on REQ-ERR-1 (ValidationException from 01-error-handling-spec.md)
2. REQ-DX-1 (idiomatic configuration) -- co-implemented with DX-4 (address validation is shared)

**Phase 2 -- Convenience & Happy Path (days 3-5):**
3. REQ-DX-2 (minimal code happy path) -- depends on DX-1 (default address)
4. REQ-DX-5 (message builder, non-breaking phase) -- build-time validation, custom builders

**Phase 3 -- Verb Alignment (days 5-8):**
5. REQ-DX-3 (consistent verbs) -- depends on DX-2 (convenience methods are new verb-aligned names)

**Deferred to v3.0:**
6. REQ-DX-5 (message immutability -- breaking phase) -- requires MAJOR version bump

---

## 3. REQ-DX-1: Language-Idiomatic Configuration

**Gap Status:** PARTIAL
**GS Reference:** 09-api-design-dx.md, REQ-DX-1
**Assessment Evidence:** 2.1.2 (score 4, builder pattern), 2.1.3 (score 2, error handling), 2.1.7 (score 3, null handling)
**Effort:** S-M (1-2 days including tests)

### 3.1 Current State

The SDK uses Lombok `@Builder` on all three client classes (`PubSubClient`, `QueuesClient`, `CQClient`), all extending `KubeMQClient`. Current constructor validation in `KubeMQClient`:

```java
// kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java lines 93-95
if (address == null || clientId == null) {
    throw new IllegalArgumentException("Address and clientId are required");
}
```

Gaps:
- `address` null check exists but no format validation (no `host:port` check)
- `clientId` null check exists but empty string passes validation
- Default values exist (`maxReceiveSize=100MB`, `reconnectIntervalSeconds=1`) but are not documented in Javadoc
- No optional eager connectivity validation at build time

### 3.2 Changes Required

#### 3.2.1 Address Format Validation

Add address format validation in the `KubeMQClient` constructor, after the existing null check.

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

```java
/**
 * Validates address format. Accepts "host:port" where host is non-empty
 * and port is a valid integer 1-65535.
 *
 * @param address the address to validate
 * @throws ValidationException if address format is invalid
 */
private static void validateAddress(String address) {
    if (address == null || address.trim().isEmpty()) {
        throw new ValidationException(
            ErrorCode.INVALID_ARGUMENT,
            "Address is required and cannot be empty.",
            "ClientBuilder.build()",
            null, // no channel context
            null  // no requestId context
        );
    }

    String trimmed = address.trim();
    int lastColon = trimmed.lastIndexOf(':');
    if (lastColon <= 0 || lastColon == trimmed.length() - 1) {
        throw new ValidationException(
            ErrorCode.INVALID_ARGUMENT,
            "Address must be in 'host:port' format. Got: '" + trimmed + "'. "
                + "Example: 'localhost:50000'",
            "ClientBuilder.build()",
            null,
            null
        );
    }

    String portStr = trimmed.substring(lastColon + 1);
    try {
        int port = Integer.parseInt(portStr);
        if (port < 1 || port > 65535) {
            throw new ValidationException(
                ErrorCode.INVALID_ARGUMENT,
                "Port must be between 1 and 65535. Got: " + port,
                "ClientBuilder.build()",
                null,
                null
            );
        }
    } catch (NumberFormatException e) {
        throw new ValidationException(
            ErrorCode.INVALID_ARGUMENT,
            "Port must be a valid integer. Got: '" + portStr + "'",
            "ClientBuilder.build()",
            null,
            null
        );
    }
}
```

**Integration point:** Replace the existing null check block (lines 93-95) with:

```java
// Validate address format
validateAddress(address);
// Validate clientId
validateClientId(clientId);
```

Note: `ValidationException` is defined in 01-error-handling-spec.md (Section 3.2, class `io.kubemq.sdk.exception.ValidationException`). This spec depends on that class being implemented first.

**Backward compatibility:** Currently throws `IllegalArgumentException` for null address/clientId. After this change, throws `ValidationException` (which extends `KubeMQException` extends `RuntimeException`). Any caller catching `IllegalArgumentException` will break. To maintain compatibility during v2.x:

```java
// During v2.x transition: ValidationException also IS-A RuntimeException
// via KubeMQException extends RuntimeException.
// Callers catching RuntimeException or IllegalArgumentException:
//   - RuntimeException catch still works (ValidationException IS-A RuntimeException)
//   - IllegalArgumentException catch BREAKS (ValidationException is NOT an IllegalArgumentException)
```

**Migration path (Review R1 M-11):** This is a *narrowly* breaking change. `ValidationException extends KubeMQException extends RuntimeException`, NOT `extends IllegalArgumentException`. Any caller specifically catching `IllegalArgumentException` from builder construction will need to catch `ValidationException` instead.

**Justification and mitigation:**
1. The GS golden standard requires `ValidationException` for all validation errors.
2. The 01-error-handling-spec already defines the migration path for this change.
3. Callers catching the broader `RuntimeException` are unaffected.
4. Java single inheritance prevents `ValidationException` from extending both `KubeMQException` and `IllegalArgumentException`.

**Required documentation in migration guide (06-documentation-spec REQ-DOC-7):**

```java
// BEFORE (v2.1.x):
try {
    client = PubSubClient.builder().address("bad").build();
} catch (IllegalArgumentException e) {
    handleValidation(e);
}

// AFTER (v2.2.x):
try {
    client = PubSubClient.builder().address("bad").build();
} catch (ValidationException e) {
    handleValidation(e); // ValidationException extends KubeMQException extends RuntimeException
}
// Or catch RuntimeException if you don't need specific handling
```

This before/after code must be included in the migration guide and CHANGELOG.

#### 3.2.2 ClientId Validation

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

```java
/**
 * Validates clientId is non-null and non-empty.
 *
 * @param clientId the client ID to validate
 * @throws ValidationException if clientId is null or empty
 */
private static void validateClientId(String clientId) {
    if (clientId == null || clientId.trim().isEmpty()) {
        throw new ValidationException(
            ErrorCode.INVALID_ARGUMENT,
            "ClientId is required and cannot be empty. "
                + "Provide a unique identifier for this client instance, "
                + "or use the default (auto-generated UUID) by omitting clientId.",
            "ClientBuilder.build()",
            null,
            null
        );
    }
}
```

Note: Once REQ-DX-2 adds a default clientId, this validation fires only if an explicit empty string is passed. The default UUID path bypasses this check.

#### 3.2.3 Default Value Javadoc

Add `@Builder.Default` Javadoc comments to all builder fields in `KubeMQClient`. This is a documentation-only change.

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

Update field declarations to include default value documentation:

```java
/**
 * The address of the KubeMQ server in 'host:port' format.
 * Default: {@code "localhost:50000"} (see REQ-DX-2).
 */
private String address;

/**
 * The client ID used for identifying this client instance.
 * Default: auto-generated UUID (see REQ-DX-2).
 */
private String clientId;

/**
 * The authorization token for secure communication.
 * Default: {@code null} (no authentication).
 */
private String authToken;

/**
 * Indicates if TLS (Transport Layer Security) is enabled.
 * Default: {@code false}.
 */
private boolean tls;

/**
 * The maximum size of inbound messages in bytes.
 * Default: {@code 104857600} (100 MB).
 */
private int maxReceiveSize;

/**
 * The interval between reconnection attempts in seconds.
 * Default: {@code 1} second.
 */
private int reconnectIntervalSeconds;

/**
 * The logging level for the SDK.
 * Default: {@code Level.INFO}.
 */
private Level logLevel;

/**
 * The timeout for synchronous request operations in seconds.
 * Default: {@code 30} seconds.
 */
private int requestTimeoutSeconds;
```

#### 3.2.4 Optional Eager Connection Validation

Add a `validateOnBuild(boolean)` option that pings the server during `build()`. Default: `false` for backward compatibility.

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

Add field:

```java
/**
 * When {@code true}, the builder validates connectivity to the KubeMQ server
 * during {@code build()} by sending a ping request. If the server is unreachable,
 * construction fails immediately with a {@link ConnectionException}.
 * Default: {@code false}.
 *
 * <p><strong>Warning (Review R1 m-13):</strong> This option performs a network call
 * during {@code build()}, which violates the typical builder pattern convention that
 * {@code build()} is a pure construction method. Do not enable this in dependency
 * injection frameworks that call {@code build()} during wiring, where network
 * availability is not guaranteed. The error message clearly indicates this was an
 * optional validation step.</p>
 */
private boolean validateOnBuild;
```

Add to constructor, after `initChannel()`:

```java
if (validateOnBuild) {
    try {
        this.ping();
    } catch (Exception e) {
        // Clean up the channel we just created
        if (managedChannel != null) {
            managedChannel.shutdownNow();
        }
        throw new ConnectionException(
            ErrorCode.CONNECTION_FAILED,
            "Failed to connect to KubeMQ server at '" + address + "' during build validation. "
                + "Ensure the server is running and accessible. "
                + "To skip this check, remove .validateOnBuild(true) from the builder.",
            "ClientBuilder.build()",
            null,
            null
        );
    }
}
```

This must be added to all three client builder constructors (`PubSubClient`, `QueuesClient`, `CQClient`) -- they delegate to `KubeMQClient` super constructor, so adding it once in `KubeMQClient` suffices.

**Note:** `ConnectionException` is defined in 01-error-handling-spec.md.

---

## 4. REQ-DX-2: Minimal Code Happy Path

**Gap Status:** PARTIAL
**GS Reference:** 09-api-design-dx.md, REQ-DX-2
**Assessment Evidence:** 2.2.1 (score 4, publish ~4 lines), 2.2.2 (score 4, sensible defaults)
**Effort:** S-M (1-2 days including tests)

### 4.1 Current State

Current happy path for publishing an event:

```java
PubSubClient client = PubSubClient.builder()
    .address("localhost:50000")
    .clientId("test")
    .build();
client.sendEventsMessage(EventMessage.builder()
    .channel("ch")
    .body("hello".getBytes())
    .build());
```

This is ~4 lines (excluding imports). The GS target is <=3 lines. Gaps:
- `address` is required (no default to `"localhost:50000"`)
- `clientId` is required (no default to auto-generated UUID)
- No convenience methods for common publish/send patterns

### 4.2 Changes Required

#### 4.2.1 Default Address and ClientId

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

Modify the constructor to apply defaults before validation:

```java
public KubeMQClient(String address, String clientId, /* ... other params ... */) {
    // Apply defaults for local development
    // Review R1 M-12: Check KUBEMQ_ADDRESS env var before falling back to localhost
    String envAddress = System.getenv("KUBEMQ_ADDRESS");
    String effectiveAddress;
    if (address != null && !address.trim().isEmpty()) {
        effectiveAddress = address;
    } else if (envAddress != null && !envAddress.trim().isEmpty()) {
        effectiveAddress = envAddress;
    } else {
        effectiveAddress = "localhost:50000";
    }

    String effectiveClientId = (clientId == null || clientId.trim().isEmpty())
        ? "kubemq-client-" + UUID.randomUUID().toString().substring(0, 8)
        : clientId;

    // Validate (now validates effective values, not raw inputs)
    validateAddress(effectiveAddress);
    // clientId is always valid at this point (either user-provided or UUID-generated)

    this.address = effectiveAddress;
    this.clientId = effectiveClientId;

    // Review R1 M-12: Warn when using default address to prevent silent production misconfiguration
    if (address == null || address.trim().isEmpty()) {
        // Use logger if available, or System.err as last resort during construction
        if (envAddress != null && !envAddress.trim().isEmpty()) {
            // Log at DEBUG: env var usage is intentional
            // logger.debug("Using address from KUBEMQ_ADDRESS environment variable",
            //     "address", effectiveAddress);
        } else {
            // Log at WARN: default localhost may be unintentional in production
            // logger.warn("Using default address localhost:50000. "
            //     + "Set address explicitly or use KUBEMQ_ADDRESS environment variable "
            //     + "for production deployments.",
            //     "address", effectiveAddress);
        }
    }
    // ... rest of constructor
}
```

**Backward compatibility:** Fully backward compatible. Users who currently pass `address` and `clientId` see no change. Users who previously got `IllegalArgumentException` for null address now get a working default.

> **Review R1 M-12:** The default address resolution order is:
> 1. Explicit `address` parameter (highest priority)
> 2. `KUBEMQ_ADDRESS` environment variable
> 3. `"localhost:50000"` (lowest priority, with WARN log)
>
> This protects production deployments: if a user forgets to set the address, the WARN log
> alerts them rather than silently connecting to localhost. The env var fallback supports
> containerized deployments where address is injected via environment.

**Impact on DX-1 validation:** The `validateClientId()` method from Section 3.2.2 is not called when the default is applied. It is only called when the user explicitly passes an empty string but not null. Clarification: `null` triggers default; empty string `""` triggers default; whitespace-only `"  "` triggers default. Only non-blank explicit values bypass default-and-validate.

#### 4.2.2 Convenience Publish/Send Methods on PubSubClient

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/PubSubClient.java`

Add convenience methods:

```java
/**
 * Publishes an event message to the specified channel.
 * This is a convenience method equivalent to:
 * <pre>{@code
 * sendEventsMessage(EventMessage.builder()
 *     .channel(channel)
 *     .body(body)
 *     .build());
 * }</pre>
 *
 * @param channel the target channel name (must not be null or empty)
 * @param body    the message body
 * @throws ValidationException if channel is null/empty or body is null
 */
public void publishEvent(String channel, byte[] body) {
    sendEventsMessage(EventMessage.builder()
        .channel(channel)
        .body(body != null ? body : new byte[0])
        .build());
}

/**
 * Publishes an event message with string body to the specified channel.
 *
 * @param channel the target channel name
 * @param body    the message body as a string (encoded as UTF-8)
 * @throws ValidationException if channel is null/empty
 */
public void publishEvent(String channel, String body) {
    publishEvent(channel, body != null ? body.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0]);
}

/**
 * Publishes an event store message to the specified channel.
 *
 * @param channel the target channel name
 * @param body    the message body
 * @return the result of sending the event store message
 * @throws ValidationException if channel is null/empty
 */
public EventSendResult publishEventStore(String channel, byte[] body) {
    return sendEventsStoreMessage(EventStoreMessage.builder()
        .channel(channel)
        .body(body != null ? body : new byte[0])
        .build());
}
```

Note: `publishEvent` is the GS verb-aligned name (REQ-DX-3). Adding it as the convenience method name means the new shortest path already uses the correct verb. The existing `sendEventsMessage()` remains as-is.

> **Review R1 m-11 -- Method naming clarification:** There are two distinct `publishEvent` signatures:
> 1. `publishEvent(String channel, byte[] body)` -- convenience shorthand (DX-2), takes channel + body directly
> 2. `publishEvent(EventMessage message)` -- verb-aligned alias (DX-3), 1:1 alias for `sendEventsMessage(EventMessage)`
>
> Both are added. They are overloaded methods (same name, different parameters). The relationship
> to the deprecated method is: `sendEventsMessage(EventMessage)` is deprecated in favor of
> `publishEvent(EventMessage)`. The convenience method `publishEvent(String, byte[])` is new
> with no deprecated predecessor.

#### 4.2.3 Convenience Send Methods on QueuesClient

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueuesClient.java`

```java
/**
 * Sends a message to the specified queue channel.
 * This is a convenience method equivalent to:
 * <pre>{@code
 * sendQueuesMessage(QueueMessage.builder()
 *     .channel(channel)
 *     .body(body)
 *     .build());
 * }</pre>
 *
 * @param channel the target queue channel name
 * @param body    the message body
 * @return the result of the send operation
 * @throws ValidationException if channel is null/empty
 */
public QueueSendResult sendQueueMessage(String channel, byte[] body) {
    return sendQueuesMessage(QueueMessage.builder()
        .channel(channel)
        .body(body != null ? body : new byte[0])
        .build());
}
```

Note: `sendQueueMessage` (singular) is the GS verb-aligned name. The existing `sendQueuesMessage` (plural) remains.

#### 4.2.4 Convenience Send Methods on CQClient

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/cq/CQClient.java`

```java
/**
 * Sends a command to the specified channel.
 *
 * @param channel          the target channel name
 * @param body             the command body
 * @param timeoutInSeconds the timeout for the command in seconds
 * @return the command response
 * @throws ValidationException if channel is null/empty or timeout <= 0
 */
public CommandResponseMessage sendCommand(String channel, byte[] body, int timeoutInSeconds) {
    return sendCommandRequest(CommandMessage.builder()
        .channel(channel)
        .body(body != null ? body : new byte[0])
        .timeoutInSeconds(timeoutInSeconds)
        .build());
}

/**
 * Sends a query to the specified channel.
 *
 * @param channel          the target channel name
 * @param body             the query body
 * @param timeoutInSeconds the timeout for the query in seconds
 * @return the query response
 * @throws ValidationException if channel is null/empty or timeout <= 0
 */
public QueryResponseMessage sendQuery(String channel, byte[] body, int timeoutInSeconds) {
    return sendQueryRequest(QueryMessage.builder()
        .channel(channel)
        .body(body != null ? body : new byte[0])
        .timeoutInSeconds(timeoutInSeconds)
        .build());
}
```

#### 4.2.5 Target Happy Path After Changes

After implementing DX-1 and DX-2, the minimal publish path becomes:

```java
// 2 lines (excluding imports and close)
PubSubClient client = PubSubClient.builder().build(); // defaults to localhost:50000
client.publishEvent("my-channel", "hello".getBytes());
```

Queue send:

```java
QueuesClient client = QueuesClient.builder().build();
client.sendQueueMessage("my-queue", "hello".getBytes());
```

Command:

```java
CQClient client = CQClient.builder().build();
client.sendCommand("my-channel", "payload".getBytes(), 10);
```

All achieve <=3 lines. Subscribe/receive with ack remains <=10 lines (already compliant per assessment 2.2.1).

---

## 5. REQ-DX-3: Consistent Verbs Across SDKs

**Gap Status:** PARTIAL
**GS Reference:** 09-api-design-dx.md, REQ-DX-3
**Assessment Evidence:** 2.4.3 (score 3, method names longer than convention)
**Effort:** M (2-3 days including tests and deprecation annotations)

### 5.1 Current State

Current method names vs GS verb table:

| Operation | GS Verb (Java) | Current SDK Method | Status |
|-----------|----------------|-------------------|--------|
| Publish event | `publishEvent()` | `sendEventsMessage(EventMessage)` | MISALIGNED |
| Publish event store | `publishEventStore()` | `sendEventsStoreMessage(EventStoreMessage)` | MISALIGNED |
| Subscribe to events | `subscribeToEvents()` | `subscribeToEvents(EventsSubscription)` | ALIGNED |
| Subscribe to events store | `subscribeToEventsStore()` | `subscribeToEventsStore(EventsStoreSubscription)` | ALIGNED |
| Send to queue | `sendQueueMessage()` | `sendQueuesMessage(QueueMessage)` | MISALIGNED (plural) |
| Receive from queue | `receiveQueueMessages()` | `receiveQueuesMessages(QueuesPollRequest)` | MISALIGNED (plural) |
| Send command | `sendCommand()` | `sendCommandRequest(CommandMessage)` | MISALIGNED (extra "Request") |
| Send query | `sendQuery()` | `sendQueryRequest(QueryMessage)` | MISALIGNED (extra "Request") |
| Ack message | `ack()` | `ack()` on `QueueMessageReceived` | ALIGNED |
| Reject message | `reject()` | `reject()` on `QueueMessageReceived` | ALIGNED |

### 5.2 Changes Required

#### 5.2.1 Strategy: Alias Methods with Deprecation

Add new methods with GS-aligned names. Deprecate old names. Remove old names in v3.0.

**Deprecation policy:** Per REQ-COMPAT-2 (from GS 12-compatibility-lifecycle.md), deprecated APIs must have a minimum of 2 minor versions notice before removal. The `@Deprecated` annotation with `forRemoval = true` (Java 9+) and `since` attribute communicates this clearly.

#### 5.2.2 PubSubClient Verb Aliases

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/PubSubClient.java`

```java
// --- New verb-aligned methods (delegate to existing) ---

/**
 * Publishes an event message.
 * This is the preferred method name per cross-SDK verb alignment.
 *
 * @param message the event message to publish
 * @throws ValidationException if the message is invalid
 * @see #sendEventsMessage(EventMessage)
 */
public void publishEvent(EventMessage message) {
    sendEventsMessage(message);
}

/**
 * Publishes an event store message.
 * This is the preferred method name per cross-SDK verb alignment.
 *
 * @param message the event store message to publish
 * @return the result of publishing the message
 * @throws ValidationException if the message is invalid
 * @see #sendEventsStoreMessage(EventStoreMessage)
 */
public EventSendResult publishEventStore(EventStoreMessage message) {
    return sendEventsStoreMessage(message);
}

// --- Deprecate old methods ---

/**
 * Sends an event message.
 *
 * @param message the event message to be sent
 * @throws RuntimeException if sending the message fails
 * @deprecated Use {@link #publishEvent(EventMessage)} instead. This method
 *             will be removed in v3.0.
 */
@Deprecated(since = "2.2.0", forRemoval = true)
public void sendEventsMessage(EventMessage message) {
    // ... existing implementation unchanged ...
}

/**
 * Sends an event store message.
 *
 * @param message the event store message to be sent
 * @return the result of sending the event store message
 * @throws RuntimeException if sending the message fails
 * @deprecated Use {@link #publishEventStore(EventStoreMessage)} instead.
 *             This method will be removed in v3.0.
 */
@Deprecated(since = "2.2.0", forRemoval = true)
public EventSendResult sendEventsStoreMessage(EventStoreMessage message) {
    // ... existing implementation unchanged ...
}
```

**Implementation note:** The new methods delegate to the old implementation. The `@Deprecated` annotation is on the old public method signatures. Internally the new method calls the old one. When the old methods are removed in v3.0, the implementation moves to the new method name.

#### 5.2.3 QueuesClient Verb Aliases

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueuesClient.java`

```java
// --- New verb-aligned methods ---

/**
 * Sends a message to a queue channel.
 * This is the preferred method name per cross-SDK verb alignment.
 *
 * @param queueMessage the message to send
 * @return the result of the send operation
 * @see #sendQueuesMessage(QueueMessage)
 */
public QueueSendResult sendQueueMessage(QueueMessage queueMessage) {
    return sendQueuesMessage(queueMessage);
}

/**
 * Receives messages from a queue channel.
 * This is the preferred method name per cross-SDK verb alignment.
 *
 * @param queuesPollRequest the poll request configuration
 * @return the poll response containing received messages
 * @see #receiveQueuesMessages(QueuesPollRequest)
 */
public QueuesPollResponse receiveQueueMessages(QueuesPollRequest queuesPollRequest) {
    return receiveQueuesMessages(queuesPollRequest);
}

// --- Deprecate old methods ---

/**
 * @deprecated Use {@link #sendQueueMessage(QueueMessage)} instead.
 *             Will be removed in v3.0.
 */
@Deprecated(since = "2.2.0", forRemoval = true)
public QueueSendResult sendQueuesMessage(QueueMessage queueMessage) {
    // ... existing implementation unchanged ...
}

/**
 * @deprecated Use {@link #receiveQueueMessages(QueuesPollRequest)} instead.
 *             Will be removed in v3.0.
 */
@Deprecated(since = "2.2.0", forRemoval = true)
public QueuesPollResponse receiveQueuesMessages(QueuesPollRequest queuesPollRequest) {
    // ... existing implementation unchanged ...
}
```

#### 5.2.4 CQClient Verb Aliases

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/cq/CQClient.java`

```java
// --- New verb-aligned methods ---

/**
 * Sends a command to the KubeMQ server.
 * This is the preferred method name per cross-SDK verb alignment.
 *
 * @param message the command message to send
 * @return the command response message
 * @see #sendCommandRequest(CommandMessage)
 */
public CommandResponseMessage sendCommand(CommandMessage message) {
    return sendCommandRequest(message);
}

/**
 * Sends a query to the KubeMQ server.
 * This is the preferred method name per cross-SDK verb alignment.
 *
 * @param message the query message to send
 * @return the query response message
 * @see #sendQueryRequest(QueryMessage)
 */
public QueryResponseMessage sendQuery(QueryMessage message) {
    return sendQueryRequest(message);
}

// --- Deprecate old methods ---

/**
 * @deprecated Use {@link #sendCommand(CommandMessage)} instead.
 *             Will be removed in v3.0.
 */
@Deprecated(since = "2.2.0", forRemoval = true)
public CommandResponseMessage sendCommandRequest(CommandMessage message) {
    // ... existing implementation unchanged ...
}

/**
 * @deprecated Use {@link #sendQuery(QueryMessage)} instead.
 *             Will be removed in v3.0.
 */
@Deprecated(since = "2.2.0", forRemoval = true)
public QueryResponseMessage sendQueryRequest(QueryMessage message) {
    // ... existing implementation unchanged ...
}
```

#### 5.2.5 No-Change Methods (Already Aligned)

The following methods already match the GS verb table and require NO changes:

| Method | Class | GS Match |
|--------|-------|----------|
| `subscribeToEvents(EventsSubscription)` | `PubSubClient` | `subscribeToEvents()` |
| `subscribeToEventsStore(EventsStoreSubscription)` | `PubSubClient` | `subscribeToEventsStore()` |
| `subscribeToCommands(CommandsSubscription)` | `CQClient` | `subscribeToCommands()` |
| `subscribeToQueries(QueriesSubscription)` | `CQClient` | `subscribeToQueries()` |
| `ack()` | `QueueMessageReceived` | `ack()` |
| `reject()` | `QueueMessageReceived` | `reject()` |

#### 5.2.6 Complete Verb Mapping Summary

| GS Verb | New Method (v2.2+) | Deprecated Method | Removal |
|---------|--------------------|--------------------|---------|
| `publishEvent` | `publishEvent(EventMessage)` | `sendEventsMessage(EventMessage)` | v3.0 |
| `publishEvent` | `publishEvent(String, byte[])` | -- (new convenience) | -- |
| `publishEvent` | `publishEvent(String, String)` | -- (new convenience) | -- |
| `publishEventStore` | `publishEventStore(EventStoreMessage)` | `sendEventsStoreMessage(EventStoreMessage)` | v3.0 |
| `publishEventStore` | `publishEventStore(String, byte[])` | -- (new convenience) | -- |
| `sendQueueMessage` | `sendQueueMessage(QueueMessage)` | `sendQueuesMessage(QueueMessage)` | v3.0 |
| `sendQueueMessage` | `sendQueueMessage(String, byte[])` | -- (new convenience) | -- |
| `receiveQueueMessages` | `receiveQueueMessages(QueuesPollRequest)` | `receiveQueuesMessages(QueuesPollRequest)` | v3.0 |
| `sendCommand` | `sendCommand(CommandMessage)` | `sendCommandRequest(CommandMessage)` | v3.0 |
| `sendCommand` | `sendCommand(String, byte[], int)` | -- (new convenience) | -- |
| `sendQuery` | `sendQuery(QueryMessage)` | `sendQueryRequest(QueryMessage)` | v3.0 |
| `sendQuery` | `sendQuery(String, byte[], int)` | -- (new convenience) | -- |

---

## 6. REQ-DX-4: Fail-Fast Validation

**Gap Status:** PARTIAL
**GS Reference:** 09-api-design-dx.md, REQ-DX-4
**Assessment Evidence:** 5.2.4 (score 3, input validation), 2.1.3 (score 2, error handling)
**Effort:** S-M (1-2 days, co-implemented with DX-1)

### 6.1 Current State

Current validation exists but has gaps:

| Validation | Location | Current Behavior | Gap |
|-----------|----------|-----------------|-----|
| Channel non-empty | `EventMessage.validate()`, `QueueMessage.validate()`, etc. | `IllegalArgumentException` | Should be `ValidationException` |
| Body size <= 100MB | All message `validate()` methods | `IllegalArgumentException` | Should be `ValidationException` |
| Timeout > 0 | `CommandMessage.validate()`, `QueryMessage.validate()` | `IllegalArgumentException` | Should be `ValidationException` |
| Address format | `KubeMQClient` constructor | Null check only | No host:port validation |
| ClientId non-empty | `KubeMQClient` constructor | Null check only | Empty string passes |
| Channel name format | Not implemented | -- | No special char / length validation |

### 6.2 Changes Required

#### 6.2.1 Replace IllegalArgumentException with ValidationException

All `validate()` methods on message classes must throw `ValidationException` instead of `IllegalArgumentException`.

**Affected files:**
- `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventMessage.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventStoreMessage.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueMessage.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/cq/CommandMessage.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/cq/QueryMessage.java`
- `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueuesPollRequest.java` (if it has validate())
- `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsSubscription.java` (validate())
- `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsStoreSubscription.java` (validate())
- `kubemq-java/src/main/java/io/kubemq/sdk/cq/CommandsSubscription.java` (validate())
- `kubemq-java/src/main/java/io/kubemq/sdk/cq/QueriesSubscription.java` (validate())

**Pattern for each validate() method:**

Before:
```java
if (channel == null || channel.isEmpty()) {
    throw new IllegalArgumentException("Event message must have a channel.");
}
```

After:
```java
if (channel == null || channel.isEmpty()) {
    throw new ValidationException(
        ErrorCode.INVALID_ARGUMENT,
        "Event message must have a channel. Provide a non-empty channel name.",
        "publishEvent",
        null,
        null
    );
}
```

The `operation` parameter should match the GS verb name for the calling context. Since `validate()` is called from multiple places, use a generic operation name like `"EventMessage.validate"` or pass the operation as a parameter. Recommended: keep the operation generic in `validate()` since the caller will wrap with context.

**Backward compatibility:** `ValidationException extends KubeMQException extends RuntimeException`. Callers catching `RuntimeException` are unaffected. Callers specifically catching `IllegalArgumentException` from validation will break. This is the same migration pattern as Section 3.2.1, covered in the 01-error-handling-spec migration guide.

#### 6.2.2 Address Validation (Cross-Reference)

Address format validation is specified in Section 3.2.1 (REQ-DX-1). No duplication needed here.

#### 6.2.3 ClientId Validation (Cross-Reference)

ClientId validation is specified in Section 3.2.2 (REQ-DX-1). No duplication needed here.

#### 6.2.4 Channel Name Format Validation (New)

Add basic channel name format validation to all message `validate()` methods. The GS says "Channel names (non-empty, valid characters)" but does not define exactly which characters are valid. KubeMQ server accepts alphanumeric characters, dots, dashes, underscores, and forward slashes.

**Add a shared utility method:**

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/common/KubeMQUtils.java`

```java
/**
 * Validates a channel name. Channel names must:
 * <ul>
 *   <li>Be non-null and non-empty</li>
 *   <li>Be at most 256 characters long</li>
 *   <li>Contain only alphanumeric characters, dots, dashes, underscores,
 *       forward slashes, and colons</li>
 * </ul>
 *
 * @param channel   the channel name to validate
 * @param operation the operation context for error reporting
 * @throws ValidationException if channel name is invalid
 */
public static void validateChannelName(String channel, String operation) {
    if (channel == null || channel.trim().isEmpty()) {
        throw new ValidationException(
            ErrorCode.INVALID_ARGUMENT,
            "Channel name is required and cannot be empty.",
            operation,
            null,
            null
        );
    }
    if (channel.length() > 256) {
        throw new ValidationException(
            ErrorCode.INVALID_ARGUMENT,
            "Channel name must be at most 256 characters. Got: " + channel.length(),
            operation,
            channel,
            null
        );
    }
    // NOTE: This regex is for publish-side validation. Subscription channel patterns
    // may include wildcards (e.g., "events.*"). If validateChannelName() is called
    // from subscription validate() methods, use validateChannelPattern() instead,
    // which adds '*' to the allowed characters.
    if (!channel.matches("^[a-zA-Z0-9._\\-/:]+$")) {
        throw new ValidationException(
            ErrorCode.INVALID_ARGUMENT,
            "Channel name contains invalid characters. "
                + "Allowed: alphanumeric, dots, dashes, underscores, forward slashes, colons. "
                + "Got: '" + channel + "'",
            operation,
            channel,
            null
        );
    }
}
```

**Integration:** Replace channel validation in each `validate()` method:

Before:
```java
if (channel == null || channel.isEmpty()) {
    throw new IllegalArgumentException("Event message must have a channel.");
}
```

After:
```java
KubeMQUtils.validateChannelName(channel, "EventMessage.validate");
```

#### 6.2.5 Validation Happens Before Network Call (Already Compliant)

Current implementation calls `validate()` before `encode()` and before any gRPC call. This is already compliant. No changes needed for this acceptance criterion.

Verification by source:
- `PubSubClient.sendEventsMessage()` line 38: `message.validate()` before `message.encode()`
- `QueuesClient.sendQueuesMessage()` line 68: `queueMessage.validate()` before `this.queueUpstreamHandler.sendQueuesMessage()`
- `CQClient.sendCommandRequest()` line 33: `message.validate()` before `message.encode()`

#### 6.2.6 Validation Error Classification (Non-Retryable)

`ValidationException` is defined in 01-error-handling-spec.md with `isRetryable() = false`. This satisfies the GS criterion "Validation errors are classified as non-retryable." No additional work needed in this spec beyond using `ValidationException`.

---

## 7. REQ-DX-5: Message Builder/Factory

**Gap Status:** PARTIAL
**GS Reference:** 09-api-design-dx.md, REQ-DX-5
**Assessment Evidence:** 2.3.1 (score 4, separate message types with builders)
**Effort:** M (2-3 days for non-breaking phase)

### 7.1 Current State

| Criterion | Status | Evidence |
|-----------|--------|----------|
| Builder/factory methods | COMPLIANT | Lombok `@Builder` on all message types |
| Required fields enforced at build time | PARTIAL | Channel validated in `validate()` (before send) but NOT in `build()` |
| Optional fields have sensible defaults | COMPLIANT | Empty byte array body, empty HashMap tags |
| Messages immutable after construction | **NOT COMPLIANT** | `@Data` generates setters; `@Getter`+`@Setter` on EventMessage/EventStoreMessage |

Current Lombok annotations on message classes:

| Class | Annotations | Mutable? |
|-------|------------|----------|
| `EventMessage` | `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor` | YES (setters) |
| `EventStoreMessage` | `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor` | YES (setters) |
| `QueueMessage` | `@Data @Builder @AllArgsConstructor` | YES (@Data generates setters) |
| `CommandMessage` | `@Data @Builder` | YES (@Data generates setters) |
| `QueryMessage` | `@Data @Builder @NoArgsConstructor @AllArgsConstructor` | YES (@Data generates setters) |
| `QueueMessageReceived` | `@Data @Builder @NoArgsConstructor @AllArgsConstructor` | YES (and MUST remain mutable for `decode()` and internal state) |

### 7.2 Changes Required

#### 7.2.1 Build-Time Validation (Non-Breaking)

Replace Lombok's default `build()` with a custom builder that validates required fields.

**Pattern for each message class:** Override the Lombok-generated `build()` method using the manual builder customization pattern.

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventMessage.java`

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventMessage {

    private String id;
    private String channel;
    private String metadata;
    @Builder.Default
    private byte[] body = new byte[0];
    @Builder.Default
    private Map<String, String> tags = new HashMap<>();

    // Custom builder with build-time validation
    public static class EventMessageBuilder {
        /**
         * Builds the EventMessage, validating required fields.
         *
         * @return the validated EventMessage
         * @throws ValidationException if channel is null/empty
         */
        public EventMessage build() {
            EventMessage message = new EventMessage(id, channel, metadata, body, tags);
            // Validate at build time (not just at send time)
            if (message.channel != null) {
                // Only validate channel format if provided at build time.
                // Channel can be set later via setter (until immutability in v3.0).
                // Full validation (including channel-required check) happens in validate().
                KubeMQUtils.validateChannelName(message.channel, "EventMessage.build");
            }
            return message;
        }
    }

    // validate() remains for pre-send validation (channel-required check)
    public void validate() {
        KubeMQUtils.validateChannelName(channel, "EventMessage.validate");
        // ... rest of validation unchanged, but using ValidationException ...
    }
}
```

**Design decision:** Build-time validation is LENIENT -- it validates field *format* when present but does not require all fields to be present. This is because:
1. Setters still exist in v2.x (not yet removed), so users may build then set fields
2. The `validate()` method enforces required fields before send
3. Full required-field enforcement at build time would break existing code that builds then sets

**Apply the same pattern to:**
- `EventStoreMessage` -- custom `EventStoreMessageBuilder.build()`
- `QueueMessage` -- custom `QueueMessageBuilder.build()`
- `CommandMessage` -- custom `CommandMessageBuilder.build()`
- `QueryMessage` -- custom `QueryMessageBuilder.build()`

For `CommandMessage` and `QueryMessage`, also validate `timeoutInSeconds` at build time if provided:

```java
public static class CommandMessageBuilder {
    public CommandMessage build() {
        CommandMessage message = new CommandMessage(id, channel, metadata, body, tags, timeoutInSeconds);
        if (message.channel != null) {
            KubeMQUtils.validateChannelName(message.channel, "CommandMessage.build");
        }
        // Timeout validated at build time only if explicitly set (non-zero)
        // Zero is the default and means "not set yet" -- validate() will catch this
        return message;
    }
}
```

#### 7.2.2 Message Immutability -- Phased Approach

**BREAKING CHANGE WARNING:** Removing setters from message classes is a breaking change that violates SemVer for a minor release. Per REQ-PKG-2, this MUST be deferred to v3.0.

**Phase 1 (v2.2.0, this release):**
- Deprecate setters on outbound message types
- Add `@Deprecated(since = "2.2.0", forRemoval = true)` to setter methods
- Document in CHANGELOG that setters will be removed in v3.0

For Lombok-annotated classes, deprecating individual setters requires switching from `@Setter` (class-level) to field-level or manual setter methods. This is complex with Lombok.

**Practical approach for v2.2.0:** Do NOT deprecate setters yet. Instead:
1. Add Javadoc to message classes stating: "Message objects should be treated as immutable after construction. Setter methods are provided for backward compatibility and will be removed in v3.0."
2. Ensure all examples and documentation use the builder pattern exclusively
3. Add `@apiNote` tags to setters (requires delombok for Javadoc)

**Phase 2 (v3.0.0, future release):**

Replace Lombok annotations to remove setters:

| Class | v2.x Annotation | v3.0 Annotation |
|-------|----------------|-----------------|
| `EventMessage` | `@Getter @Setter @Builder` | `@Getter @Builder` (remove `@Setter`) |
| `EventStoreMessage` | `@Getter @Setter @Builder` | `@Getter @Builder` (remove `@Setter`) |
| `QueueMessage` | `@Data @Builder` | `@Getter @Builder` (replace `@Data`) |
| `CommandMessage` | `@Data @Builder` | `@Getter @Builder` (replace `@Data`) |
| `QueryMessage` | `@Data @Builder` | `@Getter @Builder` (replace `@Data`) |

Also in v3.0: add `@Builder(toBuilder = true)` to allow creating modified copies without setters:

```java
// v3.0 pattern for modifying messages:
EventMessage modified = original.toBuilder().channel("new-channel").build();
```

**Excluded from immutability:** `QueueMessageReceived` MUST remain mutable because:
- `decode()` method mutates fields after construction
- `ack()`, `reject()`, `reQueue()` mutate internal state (`messageCompleted`, `isTransactionCompleted`)
- `extendVisibilityTimer()`, `resetVisibilityTimer()` mutate `visibilitySeconds`

This is acceptable because `QueueMessageReceived` is an *inbound* message created by the SDK, not an *outbound* message created by the user. The GS says "Messages are immutable after construction (where language supports it)" -- `QueueMessageReceived` construction is SDK-internal and state mutation is by design.

---

## 8. Cross-Category Dependencies

| This Spec | Depends On | Reason |
|-----------|-----------|--------|
| REQ-DX-1 (address validation) | **REQ-ERR-1** (01-error-handling-spec) | `ValidationException`, `ConnectionException` classes |
| REQ-DX-4 (fail-fast validation) | **REQ-ERR-1** (01-error-handling-spec) | `ValidationException` class |
| REQ-DX-4 (fail-fast validation) | **REQ-ERR-2** (01-error-handling-spec) | Error classification (non-retryable) |
| REQ-DX-3 (verb alignment) | **REQ-COMPAT-2** (12-compatibility-lifecycle) | Deprecation policy (2 minor versions) |
| REQ-DX-5 (immutability phase 2) | **REQ-PKG-2** (11-packaging) | SemVer: breaking changes only in major |
| REQ-DX-2 (convenience methods) | REQ-DX-1 (this spec) | Default address must exist first |
| REQ-DX-3 (verb aliases) | REQ-DX-2 (this spec) | Convenience methods use new verb names |

**Dependency on 01-error-handling-spec:** This spec CANNOT be implemented before Phase 1 of 01-error-handling-spec (error hierarchy). Specifically, `ValidationException` and `ErrorCode` must exist before the validation changes in REQ-DX-1 and REQ-DX-4 can be implemented. If error handling is not yet implemented, use `IllegalArgumentException` as a temporary measure and convert to `ValidationException` once available.

**Dependency on 08-api-completeness-spec (Review R1 X-4):** The verb-aligned alias methods in REQ-DX-3 (e.g., `subscribeEvents()`) must be added AFTER 08-spec changes the return types of `subscribeToEvents()` etc. from `void` to subscription objects. The DX-3 aliases must also return subscription objects. Implementation order: 08-spec changes return types first, then 09-spec adds aliases.

---

## 9. Breaking Changes

### 9.1 Changes That Are Breaking (Deferred to v3.0)

| Change | Impact | v2.x Mitigation | v3.0 Action |
|--------|--------|-----------------|-------------|
| Remove message setters (immutability) | User code calling `.setChannel()`, `.setBody()`, etc. on message objects breaks | Document intent; add Javadoc warning | Replace `@Data`/`@Setter` with `@Getter` only |
| Remove deprecated method names | User code calling `sendEventsMessage()`, `sendCommandRequest()`, etc. breaks | `@Deprecated(forRemoval=true)` with 2+ minor version notice | Remove old method names |

### 9.2 Changes That Are Narrowly Breaking (Acceptable in v2.2.0)

| Change | Impact | Justification |
|--------|--------|--------------|
| `IllegalArgumentException` -> `ValidationException` in validation | Callers catching `IllegalArgumentException` specifically | `ValidationException extends RuntimeException` still. Catching `RuntimeException` works. Very unlikely that callers catch `IllegalArgumentException` specifically from SDK validation. |
| Default address/clientId (null no longer throws) | Callers relying on null-address throwing for defensive programming | Extremely unlikely pattern. Default values are purely additive. |

### 9.3 Changes That Are Non-Breaking

| Change | Reason |
|--------|--------|
| New convenience methods (`publishEvent(String, byte[])`, etc.) | Additive API only |
| New verb-aligned method aliases (`publishEvent(EventMessage)`, etc.) | Additive API only |
| `validateOnBuild(boolean)` builder option | Default `false`, opt-in only |
| Build-time format validation on channel names | Only validates format when channel is provided; does not require channel at build time |
| Default value Javadoc | Documentation only |

---

## 10. Migration Guide

### 10.1 v2.1.x to v2.2.0 Migration

**What changes:**

1. **New default address and clientId.** If you relied on `address` being required and throwing on null, your code still works (you pass a non-null address). If you previously handled the exception for testing, you may need to adjust.

2. **Validation exceptions.** Validation errors now throw `ValidationException` instead of `IllegalArgumentException`. If you catch `IllegalArgumentException` from SDK validation methods, change to:
   ```java
   // Before
   try {
       client.sendEventsMessage(msg);
   } catch (IllegalArgumentException e) {
       // handle validation error
   }

   // After
   try {
       client.publishEvent(msg); // or sendEventsMessage (deprecated)
   } catch (ValidationException e) {
       System.out.println(e.getCode());       // ErrorCode.INVALID_ARGUMENT
       System.out.println(e.isRetryable());    // false
       System.out.println(e.getOperation());   // "EventMessage.validate"
   }
   ```

3. **Deprecated method names.** The following methods are deprecated. They continue to work but will be removed in v3.0:
   - `sendEventsMessage()` -> use `publishEvent()`
   - `sendEventsStoreMessage()` -> use `publishEventStore()`
   - `sendQueuesMessage()` -> use `sendQueueMessage()` (singular)
   - `receiveQueuesMessages()` -> use `receiveQueueMessages()` (singular)
   - `sendCommandRequest()` -> use `sendCommand()`
   - `sendQueryRequest()` -> use `sendQuery()`

4. **New convenience methods.** Shorter publish/send paths available:
   ```java
   // Before
   client.sendEventsMessage(EventMessage.builder().channel("ch").body("hi".getBytes()).build());

   // After (shortest path)
   client.publishEvent("ch", "hi".getBytes());
   ```

### 10.2 v2.x to v3.0 Migration (Future)

1. **Deprecated methods removed.** Replace all deprecated method calls with new verb-aligned names.
2. **Message setters removed.** Replace `.setChannel("ch")` with builder pattern:
   ```java
   // Before (v2.x)
   EventMessage msg = EventMessage.builder().build();
   msg.setChannel("ch");
   msg.setBody("hi".getBytes());

   // After (v3.0)
   EventMessage msg = EventMessage.builder().channel("ch").body("hi".getBytes()).build();
   // Or, to modify an existing message:
   EventMessage modified = msg.toBuilder().channel("new-ch").build();
   ```

---

## 11. Test Plan

### 11.1 REQ-DX-1 Tests

**File:** `kubemq-java/src/test/java/io/kubemq/sdk/client/KubeMQClientConfigurationTest.java` (new)

| Test | Input | Expected |
|------|-------|----------|
| `testDefaultAddress` | `PubSubClient.builder().build()` (no address) | Client created with address `"localhost:50000"` |
| `testDefaultClientId` | `PubSubClient.builder().build()` (no clientId) | Client created with non-null, non-empty clientId starting with `"kubemq-client-"` |
| `testValidAddress` | `.address("myhost:50000")` | No exception |
| `testInvalidAddressNoPort` | `.address("myhost")` | `ValidationException` with `ErrorCode.INVALID_ARGUMENT` |
| `testInvalidAddressEmptyHost` | `.address(":50000")` | `ValidationException` |
| `testInvalidAddressBadPort` | `.address("myhost:99999")` | `ValidationException` |
| `testInvalidAddressNonNumericPort` | `.address("myhost:abc")` | `ValidationException` |
| `testEmptyClientId` | `.clientId("")` | Uses default UUID (not a validation error) |
| `testExplicitClientId` | `.clientId("my-client")` | Client created with clientId `"my-client"` |
| `testValidateOnBuildSuccess` | `.validateOnBuild(true)` with live server | No exception, server pinged |
| `testValidateOnBuildFailure` | `.validateOnBuild(true)` with bad address | `ConnectionException` |
| `testDefaultValueDocumentation` | Reflection: check `@Builder.Default` fields | Each has Javadoc with default value |

### 11.2 REQ-DX-2 Tests

**File:** `kubemq-java/src/test/java/io/kubemq/sdk/pubsub/PubSubClientConvenienceTest.java` (new)

| Test | Method | Verification |
|------|--------|-------------|
| `testPublishEventConvenience` | `publishEvent("ch", "hello".getBytes())` | No exception; delegates to `sendEventsMessage()` |
| `testPublishEventStringBody` | `publishEvent("ch", "hello")` | UTF-8 encoding |
| `testPublishEventNullBody` | `publishEvent("ch", (byte[])null)` | Empty body, no NPE |
| `testPublishEventStoreConvenience` | `publishEventStore("ch", body)` | Returns `EventSendResult` |
| `testSendQueueMessageConvenience` | `sendQueueMessage("ch", body)` | Returns `QueueSendResult` |
| `testSendCommandConvenience` | `sendCommand("ch", body, 10)` | Returns `CommandResponseMessage` |
| `testSendQueryConvenience` | `sendQuery("ch", body, 10)` | Returns `QueryResponseMessage` |
| `testMinimalHappyPathLines` | Count actual lines for event publish | <= 3 lines after import/client |

### 11.3 REQ-DX-3 Tests

**File:** `kubemq-java/src/test/java/io/kubemq/sdk/VerbAlignmentTest.java` (new)

| Test | Verification |
|------|-------------|
| `testPublishEventDelegatesToSendEventsMessage` | `publishEvent(msg)` produces same result as `sendEventsMessage(msg)` |
| `testSendQueueMessageDelegatesToSendQueuesMessage` | Same delegation check |
| `testSendCommandDelegatesToSendCommandRequest` | Same delegation check |
| `testSendQueryDelegatesToSendQueryRequest` | Same delegation check |
| `testReceiveQueueMessagesDelegatesToReceiveQueuesMessages` | Same delegation check |
| `testDeprecatedMethodsStillWork` | Old method names still callable, produce correct results |
| `testDeprecatedAnnotationPresent` | Reflection: verify `@Deprecated(forRemoval=true)` on old methods |

### 11.4 REQ-DX-4 Tests

**File:** `kubemq-java/src/test/java/io/kubemq/sdk/validation/FailFastValidationTest.java` (new)

| Test | Input | Expected |
|------|-------|----------|
| `testEventMessageNullChannel` | `EventMessage.builder().body(body).build()` then `validate()` | `ValidationException` |
| `testEventMessageEmptyChannel` | `.channel("")` | `ValidationException` |
| `testEventMessageInvalidChannelChars` | `.channel("ch@#$%")` | `ValidationException` |
| `testEventMessageChannelTooLong` | 257-char channel | `ValidationException` |
| `testEventMessageValidChannel` | `.channel("my.channel-name/sub")` | No exception |
| `testQueueMessageValidation` | Same pattern | `ValidationException` |
| `testCommandMessageZeroTimeout` | `.timeoutInSeconds(0)` | `ValidationException` |
| `testValidationIsNonRetryable` | Catch `ValidationException` | `isRetryable() == false` |
| `testValidationBeforeNetworkCall` | Mock gRPC stub, call with invalid msg | `ValidationException` thrown, gRPC stub never called |
| `testAddressValidationAtConstruction` | Bad address format | `ValidationException` at `build()`, not at first operation |

### 11.5 REQ-DX-5 Tests

**File:** `kubemq-java/src/test/java/io/kubemq/sdk/messages/MessageBuilderTest.java` (new)

| Test | Verification |
|------|-------------|
| `testBuildTimeChannelFormatValidation` | `EventMessage.builder().channel("bad@channel").build()` throws `ValidationException` |
| `testBuildWithoutChannelAllowed` | `EventMessage.builder().body(body).build()` succeeds (channel set later or at validate-time) |
| `testBuildDefaultBody` | `EventMessage.builder().channel("ch").build()` has `body = new byte[0]` |
| `testBuildDefaultTags` | Built message has `tags = empty HashMap` |
| `testSettersStillWorkInV2` | `msg.setChannel("ch")` compiles and works |
| `testQueueMessageReceivedRemainsFullyMutable` | Setters work on `QueueMessageReceived` (excluded from immutability) |
| `testToBuilderPattern` | (v3.0 prep) If `@Builder(toBuilder=true)`, test `msg.toBuilder().channel("x").build()` |

---

## 12. Open Questions

| # | Question | Impact | Recommended Resolution |
|---|----------|--------|----------------------|
| 1 | Should `publishEvent(String, byte[])` convenience methods also accept `Map<String,String> tags`? | API surface growth | No -- keep convenience methods minimal (2-3 params). Users needing tags use the full builder. |
| 2 | Should channel name regex include Unicode characters? | International users | Start with ASCII-only (`[a-zA-Z0-9._\\-/:]`). KubeMQ server behavior with Unicode channels is not documented. Add Unicode support if server supports it. |
| 3 | Should the default clientId prefix be configurable? | Multi-instance disambiguation | No -- keep it simple. The prefix `"kubemq-client-"` is sufficient. Users who need specific clientIds set them explicitly. |
| 4 | Should `validateOnBuild(true)` retry the ping? | Flaky networks at startup | No -- single attempt with the builder's requestTimeout. Users on flaky networks should not use `validateOnBuild`. |
| 5 | Should deprecated methods log a warning when called? | Migration nudging | No -- `@Deprecated` annotation is sufficient. Runtime warnings are noisy and annoying. IDE warnings are the primary mechanism. |
| 6 | When should the error hierarchy (01 spec) be implemented relative to this spec? | Blocking dependency | Error hierarchy Phase 1 (REQ-ERR-1, REQ-ERR-2) MUST be done before this spec's Phase 1. If timeline pressure exists, this spec's Phase 1 can use `IllegalArgumentException` temporarily and convert to `ValidationException` when available. |
