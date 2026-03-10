# Implementation Specification: Category 08 -- API Completeness & Feature Parity

**SDK:** KubeMQ Java v2
**Category:** 08 -- API Completeness & Feature Parity
**GS Source:** `clients/golden-standard/08-api-completeness.md`
**Gap Research:** `clients/gap-research/java-gap-research.md` (lines 1216-1310)
**Current Score:** 4.54 / 5.0 | **Target:** 4.0+
**Priority:** P3 (Tier 2, already above target)
**Total Estimated Effort:** 3-5 days

---

## Table of Contents

1. [Summary of REQ Items](#1-summary-of-req-items)
2. [Implementation Order](#2-implementation-order)
3. [REQ-API-1: Core Feature Coverage](#3-req-api-1-core-feature-coverage)
4. [REQ-API-2: Feature Matrix Document](#4-req-api-2-feature-matrix-document)
5. [REQ-API-3: No Silent Feature Gaps](#5-req-api-3-no-silent-feature-gaps)
6. [Cross-Category Dependencies](#6-cross-category-dependencies)
7. [Breaking Changes](#7-breaking-changes)
8. [Test Plan](#8-test-plan)
9. [File Manifest](#9-file-manifest)

---

## 1. Summary of REQ Items

| REQ | Status | Gap | Effort | Impl Order |
|-----|--------|-----|--------|------------|
| REQ-API-1 | MOSTLY COMPLIANT (40/44 complete) | Unsubscribe handle return type (Events, Events Store); batch queue send | M (2-3 days) | 1 |
| REQ-API-2 | MISSING | Feature matrix document | S (< 1 day) | 3 |
| REQ-API-3 | MISSING | NotImplementedException stubs for missing features; purge queue | S (< 1 day) | 2 |

---

## 2. Implementation Order

**Phase 1 -- Subscription Handle & Batch Send (days 1-3):**
1. REQ-API-1 Gap A: Change `subscribeToEvents()` and `subscribeToEventsStore()` return type from `void` to the subscription object (already has `cancel()`)
2. REQ-API-1 Gap B: Add `sendQueuesMessages(List<QueueMessage>)` batch method
3. REQ-API-3: Add `FEATURE_NOT_IMPLEMENTED` to ErrorCode enum; add stub methods that throw `NotImplementedException`

**Phase 2 -- Documentation (day 4):**
4. REQ-API-2: Create feature matrix document

---

## 3. REQ-API-1: Core Feature Coverage

**Gap Status:** MOSTLY COMPLIANT (score 4.54)
**GS Reference:** 08-api-completeness.md, REQ-API-1
**Assessment Evidence:** 40 of 44 features score 2 (Complete), 3 score 1 (Partial), 1 scores 0 (Missing)

### 3.1 Compliant Features (No Changes Required)

The following features are fully implemented and require no modifications:

**Client Management:** Ping, Channel List, Server Info, Channel Create, Channel Delete -- all verified in `KubeMQClient.ping()`, `PubSubClient.listEventsChannels()`, `PubSubClient.createEventsChannel()`, `PubSubClient.deleteEventsChannel()`, and equivalent methods on `QueuesClient` and `CQClient`.

**Events:** Publish (`PubSubClient.sendEventsMessage()`), Subscribe with callback (`PubSubClient.subscribeToEvents()`), Wildcard subscribe (server-side), Group subscribe (`EventsSubscription.group`).

**Events Store:** Publish (`PubSubClient.sendEventsStoreMessage()`), all six subscribe start positions (`EventsStoreType.StartNewOnly`, `StartFromFirst`, `StartFromLast`, `StartAtSequence`, `StartAtTime`, `StartAtTimeDelta`).

**Queues (Stream):** Stream upstream (`QueueUpstreamHandler`), Stream downstream (`QueueDownstreamHandler`), Visibility timeout (`QueuesPollRequest.visibilitySeconds`), Ack/Reject/Requeue (`QueueMessageReceived.ack()`, `.reject()`, `.reQueue()`), DLQ (`QueueMessage.attemptsBeforeDeadLetterQueue`, `.deadLetterQueue`), Delayed messages (`QueueMessage.delayInSeconds`), Message expiration (`QueueMessage.expirationInSeconds`).

**Queues (Simple):** Send single (`QueuesClient.sendQueuesMessage()`), Receive/Pull (`QueuesClient.receiveQueuesMessages()`), Peek (`QueuesClient.waiting()`).

**RPC Commands:** Send (`CQClient.sendCommandRequest()`), Subscribe (`CQClient.subscribeToCommands()`), Group subscribe (`CommandsSubscription.group`), Send response (`CQClient.sendResponseMessage(CommandResponseMessage)`).

**RPC Queries:** Send (`CQClient.sendQueryRequest()`), Subscribe (`CQClient.subscribeToQueries()`), Group subscribe (`QueriesSubscription.group`), Send response (`CQClient.sendResponseMessage(QueryResponseMessage)`), Cache-enabled (`QueryMessage.cacheKey`, `.cacheTtlInSeconds`).

### 3.2 Gap A: Per-Subscription Unsubscribe Handle

**Current State:** `PubSubClient.subscribeToEvents()` and `subscribeToEventsStore()` return `void`. The subscription objects (`EventsSubscription`, `EventsStoreSubscription`) already have a `cancel()` method that calls `observer.onCompleted()`. However, because the subscribe methods return `void`, the caller has no type-safe handle returned from the subscribe call itself.

Similarly, `CQClient.subscribeToCommands()` and `subscribeToQueries()` return `void`, though `CommandsSubscription` and `QueriesSubscription` also have `cancel()` methods.

**Important finding:** The `cancel()` method already exists on all four subscription classes:
- `EventsSubscription.cancel()` (line 92-97)
- `EventsStoreSubscription.cancel()` (line 124-129)
- `CommandsSubscription.cancel()` (line 63-68)
- `QueriesSubscription.cancel()` (line 63-68)

The gap is that the assessment scored this as "Partial" because the `subscribeToEvents()` method returns `void` rather than returning the subscription handle, making the cancel pattern less discoverable. The subscription object is passed as a parameter, so callers already hold a reference and can call `cancel()`. This is a discoverability issue, not a functional gap.

**Design Decision:** Change return types to return the subscription object passed in, enabling fluent usage patterns.

#### 3.2.1 Changes to PubSubClient

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/PubSubClient.java`

Change `subscribeToEvents()`:

```java
// BEFORE (line 201-212):
public void subscribeToEvents(EventsSubscription subscription) {
    try {
        log.debug("Subscribing to events");
        subscription.validate();
        kubemq.Kubemq.Subscribe subscribe = subscription.encode(this.getClientId(), this);
        this.getAsyncClient().subscribeToEvents(subscribe, subscription.getObserver());
    } catch (Exception e) {
        log.error("Failed to subscribe to events", e);
        throw new RuntimeException(e);
    }
}

// AFTER:
/**
 * Subscribes to events and returns the subscription handle for lifecycle control.
 *
 * @param subscription the subscription configuration with callbacks
 * @return the same subscription object, now active, with {@link EventsSubscription#cancel()} available
 * @throws RuntimeException if subscribing fails
 */
public EventsSubscription subscribeToEvents(EventsSubscription subscription) {
    try {
        log.debug("Subscribing to events");
        subscription.validate();
        kubemq.Kubemq.Subscribe subscribe = subscription.encode(this.getClientId(), this);
        this.getAsyncClient().subscribeToEvents(subscribe, subscription.getObserver());
        return subscription;
    } catch (Exception e) {
        log.error("Failed to subscribe to events", e);
        throw new RuntimeException(e);
    }
}
```

Change `subscribeToEventsStore()`:

```java
// BEFORE (line 220-231):
public void subscribeToEventsStore(EventsStoreSubscription subscription) { ... }

// AFTER:
/**
 * Subscribes to events store and returns the subscription handle for lifecycle control.
 *
 * @param subscription the subscription configuration with callbacks and start position
 * @return the same subscription object, now active, with {@link EventsStoreSubscription#cancel()} available
 * @throws RuntimeException if subscribing fails
 */
public EventsStoreSubscription subscribeToEventsStore(EventsStoreSubscription subscription) {
    try {
        log.debug("Subscribing to events store");
        subscription.validate();
        kubemq.Kubemq.Subscribe subscribe = subscription.encode(this.getClientId(), this);
        this.getAsyncClient().subscribeToEvents(subscribe, subscription.getObserver());
        return subscription;
    } catch (Exception e) {
        log.error("Failed to subscribe to events store", e);
        throw new RuntimeException(e);
    }
}
```

#### 3.2.2 Changes to CQClient

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/cq/CQClient.java`

```java
// BEFORE (line 142-146):
public void subscribeToCommands(CommandsSubscription commandsSubscription) { ... }

// AFTER:
/**
 * Subscribes to commands and returns the subscription handle for lifecycle control.
 *
 * @param commandsSubscription the subscription configuration
 * @return the same subscription object, now active, with {@link CommandsSubscription#cancel()} available
 */
public CommandsSubscription subscribeToCommands(CommandsSubscription commandsSubscription) {
    commandsSubscription.validate();
    Kubemq.Subscribe subscribe = commandsSubscription.encode(this.getClientId(), this);
    this.getAsyncClient().subscribeToRequests(subscribe, commandsSubscription.getObserver());
    return commandsSubscription;
}
```

```java
// BEFORE (line 153-157):
public void subscribeToQueries(QueriesSubscription queriesSubscription) { ... }

// AFTER:
/**
 * Subscribes to queries and returns the subscription handle for lifecycle control.
 *
 * @param queriesSubscription the subscription configuration
 * @return the same subscription object, now active, with {@link QueriesSubscription#cancel()} available
 */
public QueriesSubscription subscribeToQueries(QueriesSubscription queriesSubscription) {
    queriesSubscription.validate();
    Kubemq.Subscribe subscribe = queriesSubscription.encode(this.getClientId(), this);
    this.getAsyncClient().subscribeToRequests(subscribe, queriesSubscription.getObserver());
    return queriesSubscription;
}
```

#### 3.2.3 Breaking Change Assessment

Changing return type from `void` to `EventsSubscription` (or equivalent) is **source-compatible** in all practical scenarios:
- Callers that ignore the return value (current behavior) continue to work unchanged.
- Callers that assign the return value get the new handle.
- Reflective callers looking for `void` return type will break, but this is not a realistic usage pattern.

**Verdict:** Non-breaking for all practical purposes. No deprecation cycle required.

#### 3.2.4 Usage Pattern After Change

```java
// Before: caller must keep their own reference
EventsSubscription sub = EventsSubscription.builder()
    .channel("my-channel")
    .onReceiveEventCallback(event -> process(event))
    .build();
client.subscribeToEvents(sub);
// ... later ...
sub.cancel();

// After: fluent pattern also works
EventsSubscription sub = client.subscribeToEvents(
    EventsSubscription.builder()
        .channel("my-channel")
        .onReceiveEventCallback(event -> process(event))
        .build()
);
// ... later ...
sub.cancel();
```

### 3.3 Gap B: Queue Batch Send

**Current State:** `QueuesClient` has `sendQueuesMessage(QueueMessage)` for single messages. There is no `sendQueuesMessages(List<QueueMessage>)` batch method. Users must loop individually, incurring one gRPC round-trip per message.

The protobuf already supports batching: `QueuesUpstreamRequest` has a `repeated QueueMessage messages` field (see `QueueMessage.encode()` which creates a `QueuesUpstreamRequest` with `addMessages()`). The server processes all messages in a single request.

**Note:** The `QueueUpstreamHandler` already uses `QueuesUpstreamRequest` with `addMessages()` for single messages. Batch simply means adding multiple messages to the same request.

#### 3.3.1 New Method on QueuesClient

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueuesClient.java`

Add after `sendQueuesMessage()` (line 70):

```java
/**
 * Sends a batch of messages to queues in a single gRPC call.
 * All messages are validated before sending. If any message fails validation,
 * no messages are sent and an IllegalArgumentException is thrown.
 *
 * @param queueMessages the list of messages to send
 * @return list of results, one per message, in the same order as input
 * @throws IllegalArgumentException if the list is null/empty or any message fails validation
 */
public List<QueueSendResult> sendQueuesMessages(List<QueueMessage> queueMessages) {
    if (queueMessages == null || queueMessages.isEmpty()) {
        throw new IllegalArgumentException("Queue messages list cannot be null or empty.");
    }
    // Validate all before sending (fail-fast)
    for (QueueMessage msg : queueMessages) {
        msg.validate();
    }
    return this.queueUpstreamHandler.sendQueuesMessages(queueMessages);
}
```

#### 3.3.2 New Method on QueueUpstreamHandler

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueUpstreamHandler.java`

Add batch async method after `sendQueuesMessageAsync()` (line 183):

```java
/**
 * Sends multiple messages in a single QueuesUpstreamRequest.
 * Uses the same stream as single-message sends.
 */
public List<QueueSendResult> sendQueuesMessages(List<QueueMessage> queueMessages) {
    String requestId = generateRequestId();
    CompletableFuture<List<QueueSendResult>> responseFuture = new CompletableFuture<>();

    pendingBatchResponses.put(requestId, responseFuture);
    requestTimestamps.put(requestId, System.currentTimeMillis());

    try {
        Kubemq.QueuesUpstreamRequest.Builder requestBuilder =
            Kubemq.QueuesUpstreamRequest.newBuilder()
                .setRequestID(requestId);

        for (QueueMessage msg : queueMessages) {
            requestBuilder.addMessages(msg.encodeMessage(kubeMQClient.getClientId()));
        }

        sendRequest(requestBuilder.build());

        return responseFuture.get(kubeMQClient.getRequestTimeoutSeconds(), TimeUnit.SECONDS);

    } catch (TimeoutException e) {
        pendingBatchResponses.remove(requestId);
        requestTimestamps.remove(requestId);
        log.error("Timeout waiting for batch send response");
        // Return error results for all messages
        return queueMessages.stream()
            .map(msg -> {
                QueueSendResult r = new QueueSendResult();
                r.setError("Batch request timed out after "
                    + kubeMQClient.getRequestTimeoutSeconds() + " seconds");
                r.setIsError(true);
                return r;
            })
            .collect(Collectors.toList());
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        pendingBatchResponses.remove(requestId);
        requestTimestamps.remove(requestId);
        log.error("Interrupted waiting for batch send response");
        return queueMessages.stream()
            .map(msg -> {
                QueueSendResult r = new QueueSendResult();
                r.setError("Batch request interrupted");
                r.setIsError(true);
                return r;
            })
            .collect(Collectors.toList());
    } catch (Exception e) {
        pendingBatchResponses.remove(requestId);
        requestTimestamps.remove(requestId);
        log.error("Error sending batch queue messages: ", e);
        throw new RuntimeException("Failed to send batch", e);
    }
}
```

#### 3.3.3 Response Handler Changes

The existing `responsesObserver.onNext()` in `QueueUpstreamHandler` currently extracts only `getResults(0)` for single-message responses. For batch, the response contains `getResultsList()` with one result per message.

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueUpstreamHandler.java`

Modify the `onNext` handler in the `responsesObserver` to handle batch responses:

```java
// Add field alongside existing pendingResponses:
// Review R1 M-9: requestId values are globally unique across both maps (guaranteed by
// UUID generation in generateRequestId()). Each response is routed to exactly one map.
// requestTimestamps.remove() must be called in BOTH the single and batch onNext paths.
private final Map<String, CompletableFuture<List<QueueSendResult>>> pendingBatchResponses =
    new ConcurrentHashMap<>();

// In responsesObserver.onNext(), after the existing single-message handling:
@Override
public void onNext(Kubemq.QueuesUpstreamResponse receivedResponse) {
    String refRequestID = receivedResponse.getRefRequestID();

    // Check single-message pending first (existing logic)
    CompletableFuture<QueueSendResult> singleFuture =
            pendingResponses.remove(refRequestID);
    requestTimestamps.remove(refRequestID);

    if (singleFuture != null) {
        // ... existing single-message handling unchanged ...
        return;
    }

    // Check batch pending
    CompletableFuture<List<QueueSendResult>> batchFuture =
            pendingBatchResponses.remove(refRequestID);
    // Review R1 M-9: Clean up timestamp for batch responses (was missing)
    requestTimestamps.remove(refRequestID);

    if (batchFuture != null) {
        if (receivedResponse.getIsError()) {
            List<QueueSendResult> errorResults = new ArrayList<>();
            QueueSendResult err = QueueSendResult.builder()
                    .id(refRequestID)
                    .isError(true)
                    .error(receivedResponse.getError())
                    .build();
            errorResults.add(err);
            batchFuture.complete(errorResults);
            return;
        }

        List<QueueSendResult> results = new ArrayList<>();
        for (Kubemq.SendQueueMessageResult result : receivedResponse.getResultsList()) {
            results.add(new QueueSendResult().decode(result));
        }
        batchFuture.complete(results);
    }
}
```

Also update `closeStreamWithError()` to complete batch futures:

```java
private void closeStreamWithError(String message) {
    isConnected.set(false);
    // Existing single-message cleanup
    pendingResponses.forEach((id, future) -> {
        future.complete(QueueSendResult.builder().error(message).isError(true).build());
    });
    pendingResponses.clear();
    // Batch cleanup
    pendingBatchResponses.forEach((id, future) -> {
        future.complete(Collections.singletonList(
            QueueSendResult.builder().error(message).isError(true).build()
        ));
    });
    pendingBatchResponses.clear();
    requestTimestamps.clear();
}
```

And update `startCleanupTask()` to handle batch timeouts in the same sweep:

```java
// Inside the cleanup lambda, after existing single-message cleanup:
pendingBatchResponses.entrySet().removeIf(entry -> {
    Long timestamp = requestTimestamps.get(entry.getKey());
    if (timestamp != null && (now - timestamp) > REQUEST_TIMEOUT_MS) {
        entry.getValue().complete(Collections.singletonList(
            QueueSendResult.builder()
                .error("Batch request timed out after " + REQUEST_TIMEOUT_MS + "ms")
                .isError(true)
                .build()));
        requestTimestamps.remove(entry.getKey());
        log.warn("Cleaned up stale pending batch request: {}", entry.getKey());
        return true;
    }
    return false;
});
```

#### 3.3.4 Imports Required

Add to `QueueUpstreamHandler.java`:
```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
```

Add to `QueuesClient.java`:
```java
import java.util.List;  // already present
```

---

## 4. REQ-API-2: Feature Matrix Document

**Gap Status:** MISSING
**GS Reference:** 08-api-completeness.md, REQ-API-2
**Assessment Evidence:** No feature matrix document exists
**Effort:** S (< 1 day)

### 4.1 Current State

No feature matrix document exists in the `clients/` directory. The assessment report provides feature coverage data but not in the standardized matrix format.

### 4.2 Implementation

Create file: `clients/feature-matrix.md`

This is a documentation-only deliverable. The matrix must follow the GS format:

```markdown
# KubeMQ SDK Feature Matrix

Last updated: <date>
SDK versions: Java v2.1.1, Go <version>, C# <version>, Python <version>, JS/TS <version>

## Client Management

| Feature | Go | Java | C# | Python | JS/TS | Tier |
|---------|-----|------|-----|--------|-------|------|
| Ping | | ✅ | | | | Core |
| Channel List | | ✅ | | | | Core |
| Server Info | | ✅ | | | | Extended |
| Channel Create | | ✅ | | | | Extended |
| Channel Delete | | ✅ | | | | Extended |

## Events (Pub/Sub)

| Feature | Go | Java | C# | Python | JS/TS | Tier |
|---------|-----|------|-----|--------|-------|------|
| Publish | | ✅ | | | | Core |
| Subscribe with callback | | ✅ | | | | Core |
| Wildcard subscribe | | ✅ | | | | Core |
| Group subscribe | | ✅ | | | | Core |
| Unsubscribe | | ✅ | | | | Core |

## Events Store (Persistent Pub/Sub)

| Feature | Go | Java | C# | Python | JS/TS | Tier |
|---------|-----|------|-----|--------|-------|------|
| Publish | | ✅ | | | | Core |
| Subscribe from beginning | | ✅ | | | | Core |
| Subscribe from sequence | | ✅ | | | | Core |
| Subscribe from timestamp | | ✅ | | | | Core |
| Subscribe from time delta | | ✅ | | | | Core |
| Subscribe from last | | ✅ | | | | Core |
| Subscribe new only | | ✅ | | | | Core |
| Unsubscribe | | ✅ | | | | Core |

## Queues (Stream -- Primary API)

| Feature | Go | Java | C# | Python | JS/TS | Tier |
|---------|-----|------|-----|--------|-------|------|
| Stream upstream | | ✅ | | | | Core |
| Stream downstream | | ✅ | | | | Core |
| Visibility timeout | | ✅ | | | | Core |
| Ack | | ✅ | | | | Core |
| Reject | | ✅ | | | | Core |
| Requeue | | ✅ | | | | Core |
| DLQ | | ✅ | | | | Core |
| Delayed messages | | ✅ | | | | Core |
| Message expiration | | ✅ | | | | Core |

## Queues (Simple -- Secondary API)

| Feature | Go | Java | C# | Python | JS/TS | Tier |
|---------|-----|------|-----|--------|-------|------|
| Send single | | ✅ | | | | Extended |
| Send batch | | ✅ | | | | Extended |
| Receive (single pull) | | ✅ | | | | Extended |
| Peek | | ✅ | | | | Extended |
| Purge queue | | ❌ (NI) | | | | Extended |

## RPC -- Commands

| Feature | Go | Java | C# | Python | JS/TS | Tier |
|---------|-----|------|-----|--------|-------|------|
| Send command | | ✅ | | | | Core |
| Subscribe to commands | | ✅ | | | | Core |
| Group subscribe | | ✅ | | | | Core |
| Send response | | ✅ | | | | Core |

## RPC -- Queries

| Feature | Go | Java | C# | Python | JS/TS | Tier |
|---------|-----|------|-----|--------|-------|------|
| Send query | | ✅ | | | | Core |
| Subscribe to queries | | ✅ | | | | Core |
| Group subscribe | | ✅ | | | | Core |
| Send response | | ✅ | | | | Core |
| Cache-enabled queries | | ✅ | | | | Core |

## Legend

- ✅ Compliant -- fully implemented and tested
- ⚠️ Partial -- implemented with limitations (see notes)
- ❌ Missing -- not implemented
- ❌ (NI) -- not implemented; stub throws NotImplementedException
- Empty -- not yet assessed
```

### 4.3 Acceptance Criteria

| Criterion | How Met |
|-----------|---------|
| Feature matrix document exists and is current | Created at `clients/feature-matrix.md` |
| Matrix reviewed/updated with each release | Add to release checklist (documented in matrix header) |
| Features categorized as Core/Extended | Tier column in every table |
| Gaps documented with rationale | "❌ (NI)" notation with `NotImplementedException` reference |

### 4.4 Process Integration

Add a step to the release checklist (once one exists per Category 12 -- Compatibility & Lifecycle):

> Before each minor/major release, review `clients/feature-matrix.md` and update all SDK columns to reflect current status.

---

## 5. REQ-API-3: No Silent Feature Gaps

**Gap Status:** MISSING
**GS Reference:** 08-api-completeness.md, REQ-API-3
**Assessment Evidence:** `purge queue` scores 0 (Missing, criterion 1.3.12). No `NotImplementedException` for missing features.
**Effort:** S (< 1 day)
**Dependency:** REQ-ERR-1 (Category 01 error hierarchy) for `ErrorCode` enum extension

### 5.1 Current State

The SDK has one confirmed missing feature: **purge queue** (assessment 1.3.12, score 0). This feature is silently absent -- no method exists, no error is thrown, no documentation explains the gap. This violates REQ-API-3 which requires missing features to return `ErrNotImplemented` rather than being silently absent.

### 5.2 ErrorCode Extension

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/exception/ErrorCode.java` (from REQ-ERR-1 spec)

Add to the `ErrorCode` enum:

```java
    // Feature gaps
    FEATURE_NOT_IMPLEMENTED
```

This must be added alongside the other error codes defined in the Category 01 spec. If Category 01 has not yet been implemented, add `FEATURE_NOT_IMPLEMENTED` as a standalone constant that can later be migrated into the enum.

### 5.3 NotImplementedException Class

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/exception/NotImplementedException.java` (new file)

If Category 01 error hierarchy (REQ-ERR-1) is already implemented, `NotImplementedException` extends `KubeMQException`:

```java
package io.kubemq.sdk.exception;

/**
 * Thrown when an SDK method is called for a feature that is recognized by the
 * KubeMQ server but not yet implemented in this SDK version.
 *
 * <p>This exception signals an intentional gap, not a bug. The feature is documented
 * in the feature matrix ({@code clients/feature-matrix.md}) with rationale and
 * tracking information.</p>
 *
 * <p>This is never retryable -- the feature is structurally absent, not transiently
 * unavailable.</p>
 */
public class NotImplementedException extends KubeMQException {

    private NotImplementedException(Builder builder) {
        super(builder);
    }

    public static class Builder extends KubeMQException.Builder<Builder> {
        public Builder() {
            code(ErrorCode.FEATURE_NOT_IMPLEMENTED);
            category(ErrorCategory.FATAL);
            retryable(false);
        }

        @Override
        public NotImplementedException build() {
            return new NotImplementedException(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
```

**Fallback (if Category 01 not yet implemented):**

```java
package io.kubemq.sdk.exception;

/**
 * Thrown when an SDK method is called for a feature that is recognized by the
 * KubeMQ server but not yet implemented in this SDK version.
 */
public class NotImplementedException extends UnsupportedOperationException {

    private final String feature;
    private final String trackingInfo;

    public NotImplementedException(String feature, String trackingInfo) {
        super(String.format(
            "Feature '%s' is not implemented in this SDK version. %s",
            feature, trackingInfo != null ? trackingInfo : "See feature-matrix.md for details."
        ));
        this.feature = feature;
        this.trackingInfo = trackingInfo;
    }

    public String getFeature() { return feature; }
    public String getTrackingInfo() { return trackingInfo; }
}
```

### 5.4 Stub Method: purgeQueue

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueuesClient.java`

Add after `pull()` method:

```java
/**
 * Purges all messages from a queue channel.
 *
 * <p><strong>Not yet implemented.</strong> The KubeMQ server supports this operation
 * ({@code AckAllQueueMessages}), but this SDK version does not expose it.
 * See {@code clients/feature-matrix.md} for status.</p>
 *
 * @param channel the queue channel to purge
 * @throws NotImplementedException always
 */
public void purgeQueue(String channel) {
    throw NotImplementedException.builder()
        .message("purgeQueue is not implemented in this SDK version. "
            + "Server supports AckAllQueueMessages. "
            + "See clients/feature-matrix.md for tracking.")
        .operation("purgeQueue")
        .channel(channel)
        .build();
}
```

**Fallback (if Category 01 not yet implemented):**

```java
public void purgeQueue(String channel) {
    throw new NotImplementedException(
        "purgeQueue",
        "Server supports AckAllQueueMessages. Tracking: feature-matrix.md"
    );
}
```

### 5.5 Acceptance Criteria

| Criterion | How Met |
|-----------|---------|
| Missing features documented in matrix | `purgeQueue` listed as "❌ (NI)" in feature matrix |
| Return `ErrNotImplemented` error | `purgeQueue()` throws `NotImplementedException` with `FEATURE_NOT_IMPLEMENTED` code |
| Tracking issue for implementation | Reference in exception message and feature matrix |

### 5.6 Future Feature Gaps

If additional server features are identified as missing from the SDK in the future, follow this pattern:
1. Add a stub method that throws `NotImplementedException`
2. Update the feature matrix with "❌ (NI)" status
3. Include tracking information in the exception message

---

## 6. Cross-Category Dependencies

| This Spec Item | Depends On | Nature | How Resolved |
|----------------|-----------|--------|-------------|
| REQ-API-3 `NotImplementedException` | REQ-ERR-1 (Cat 01, error hierarchy) | `NotImplementedException` extends `KubeMQException` | Fallback class provided if Cat 01 not yet done; migrate when ERR-1 lands |
| REQ-API-3 `ErrorCode.FEATURE_NOT_IMPLEMENTED` | REQ-ERR-1 (Cat 01, ErrorCode enum) | New enum value | Add to enum when it exists; use string constant as interim |
| REQ-API-1 subscribe return types | REQ-ERR-8 (Cat 01, streaming errors) | Subscription cancel interacts with streaming error handling | Independent -- cancel() already exists; ERR-8 will enhance reconnect behavior |
| REQ-API-2 feature matrix | REQ-COMPAT-* (Cat 12, lifecycle) | Matrix reviewed with each release | Release checklist dependency; matrix can be created independently |

---

## 7. Breaking Changes

| Change | Breaking? | Mitigation |
|--------|-----------|-----------|
| `subscribeToEvents()` returns `EventsSubscription` instead of `void` | Source compatible; **binary incompatible** (Review R1 M-10) | Recompilation required; documented in CHANGELOG and migration guide |
| `subscribeToEventsStore()` returns `EventsStoreSubscription` instead of `void` | Source compatible; **binary incompatible** | Same as above |
| `subscribeToCommands()` returns `CommandsSubscription` instead of `void` | Source compatible; **binary incompatible** | Same as above |
| `subscribeToQueries()` returns `QueriesSubscription` instead of `void` | Source compatible; **binary incompatible** | Same as above |
| New `sendQueuesMessages(List)` method | No -- additive | New method, no existing signatures affected |
| New `purgeQueue()` method that throws | No -- additive | New method; throws on call, so no silent behavior change |

**Binary compatibility note (Review R1 M-10):** Changing return type from `void` to a reference type changes the method descriptor at the bytecode level (e.g., `()V` becomes `()Lio/kubemq/sdk/pubsub/EventsSubscription;`). This is a **binary-incompatible** change per the Java Language Specification -- code compiled against the old SDK will get `NoSuchMethodError` at runtime if it uses the new SDK jar without recompilation.

**Accepted as minor-release binary break with explicit justification:**
1. The SDK is at v2.x with no documented binary compatibility guarantee.
2. The practical impact is minimal: users must recompile (standard practice for Maven dependency updates).
3. All callers that ignore the return value (the only existing usage pattern) are source-compatible.
4. The change is required by the Golden Standard for unsubscribe handle support.

**Required mitigation:**
- Include this change prominently in the CHANGELOG with a "Binary Compatibility" section.
- Document in the migration guide (06-documentation-spec REQ-DOC-7) with before/after code.
- Add a note in the release description: "Recompilation required when upgrading from 2.1.x."

**Alternative considered but rejected:** Adding a separate `subscribeToEventsReturning()` method would preserve binary compatibility but creates API confusion with two methods for the same operation. Given the SDK's maturity level, the clean approach (single method with return type) is preferred.

---

## 8. Test Plan

### 8.1 Unit Tests

| Test | File | Validates |
|------|------|-----------|
| `subscribeToEvents_returnsSubscription` | `PubSubClientTest.java` | Return value is the same subscription object passed in |
| `subscribeToEvents_returnedSubscription_cancel` | `PubSubClientTest.java` | Returned subscription's `cancel()` calls `observer.onCompleted()` |
| `subscribeToEventsStore_returnsSubscription` | `PubSubClientTest.java` | Same pattern for events store |
| `subscribeToCommands_returnsSubscription` | `CQClientTest.java` | Return value for commands |
| `subscribeToQueries_returnsSubscription` | `CQClientTest.java` | Return value for queries |
| `sendQueuesMessages_validatesAll` | `QueuesClientTest.java` | All messages validated before any are sent |
| `sendQueuesMessages_nullList_throws` | `QueuesClientTest.java` | Null list throws `IllegalArgumentException` |
| `sendQueuesMessages_emptyList_throws` | `QueuesClientTest.java` | Empty list throws `IllegalArgumentException` |
| `sendQueuesMessages_encodesAllInOneRequest` | `QueueUpstreamHandlerTest.java` | All messages encoded into single `QueuesUpstreamRequest` |
| `sendQueuesMessages_returnsResultPerMessage` | `QueueUpstreamHandlerTest.java` | Result list size matches input list size |
| `purgeQueue_throwsNotImplemented` | `QueuesClientTest.java` | Throws `NotImplementedException` with correct code and message |
| `purgeQueue_includesChannelInError` | `QueuesClientTest.java` | Exception contains channel name |

### 8.2 Integration Tests (Require Live Server)

| Test | Validates |
|------|-----------|
| `subscribeAndCancel_stopsReceiving` | After `cancel()`, no more messages received on callback |
| `batchSend_multipleMessages_allDelivered` | Send 10 messages via `sendQueuesMessages()`, receive all 10 |
| `batchSend_mixedChannels_correctRouting` | Batch with messages to different channels, each arrives at correct channel |

---

## 9. File Manifest

| File | Package | Change Type | Description |
|------|---------|------------|-------------|
| `pubsub/PubSubClient.java` | `io.kubemq.sdk.pubsub` | MODIFY | Return subscription from `subscribeToEvents()`, `subscribeToEventsStore()` |
| `cq/CQClient.java` | `io.kubemq.sdk.cq` | MODIFY | Return subscription from `subscribeToCommands()`, `subscribeToQueries()` |
| `queues/QueuesClient.java` | `io.kubemq.sdk.queues` | MODIFY | Add `sendQueuesMessages(List)`, `purgeQueue(String)` |
| `queues/QueueUpstreamHandler.java` | `io.kubemq.sdk.queues` | MODIFY | Add `sendQueuesMessages(List)` with batch request encoding, add `pendingBatchResponses` map, update `onNext` and `closeStreamWithError` |
| `exception/NotImplementedException.java` | `io.kubemq.sdk.exception` | NEW | Exception for unimplemented features |
| `exception/ErrorCode.java` | `io.kubemq.sdk.exception` | MODIFY | Add `FEATURE_NOT_IMPLEMENTED` (when Cat 01 lands) |
| `clients/feature-matrix.md` | (docs) | NEW | Cross-SDK feature matrix |
