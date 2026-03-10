# KubeMQ Java SDK - Code Review & Refactoring Report

## Overview

This document details the problems found in the `SubscriberService` and `PublisherService` implementations, explains the root causes, and describes the refactoring applied to align both classes with the KubeMQ Java SDK capabilities.

---

## Table of Contents

1. [SubscriberService - Problems Found](#1-subscriberservice---problems-found)
2. [PublisherService - Problems Found](#2-publisherservice---problems-found)
3. [Refactoring: SubscriberService](#3-refactoring-subscriberservice)
4. [Refactoring: PublisherService](#4-refactoring-publisherservice)
5. [Server-Side DLQ: How It Works](#5-server-side-dlq-how-it-works)
6. [Configuration Changes Required](#6-configuration-changes-required)
7. [Verification](#7-verification)

---

## 1. SubscriberService - Problems Found

### 1.1 CRITICAL: New TCP Connection on Every Poll Cycle

**Problem:** The `initializeClient()` method creates a **brand new `QueuesClient`** (which internally creates a new gRPC `ManagedChannel` = new TCP connection) every 10 consecutive poll errors:

```java
// ORIGINAL CODE - PROBLEMATIC
private int pollAndProcessMessages(int loop) throws InterruptedException {
    QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);
    if (response.isError()) {
        loop++;
        if (loop > 10) {
            initializeClient();  // Creates entirely new client + TCP connection!
            return 0;
        }
    }
}
```

**Root Cause:** The SDK's `QueueDownstreamHandler` already handles stream reconnection internally. When the gRPC bidirectional stream closes (which is normal after each poll transaction), the handler's `sendRequest()` method detects `isConnected == false` and calls `connect()` to re-establish the stream on the **existing** TCP channel. Creating a new `QueuesClient` bypasses this mechanism and opens redundant TCP connections.

**Impact:**
- New TCP connection to KubeMQ server every ~10 polls
- The old `QueuesClient` was never closed (`queuesClient.close()` was not called before replacement), leaking gRPC channels and associated resources
- KubeMQ server logs showed excessive connection churn

### 1.2 CRITICAL: Missing keepAlive Configuration

**Problem:** The `QueuesClient` was built without keepAlive:

```java
// ORIGINAL CODE - MISSING KEEPALIVE
queuesClient = QueuesClient.builder()
        .address(kubeMQConfig.getServer().getAddress())
        .clientId(kubeMQConfig.getQueue().getClientId())
        .reconnectIntervalSeconds(5)
        .build();  // No keepAlive, no ping intervals
```

**Root Cause:** In the SDK's `KubeMQClient.initChannel()`, keepAlive is only configured when `keepAlive != null`. Since the builder defaults `keepAlive` to `null` (it's `Boolean`, not `boolean`), no keepAlive pings are sent on the gRPC channel.

**Impact:** Without keepAlive, intermediate network infrastructure (load balancers, firewalls, NAT gateways) can silently kill idle TCP connections. The client would not detect this until the next poll attempt fails, causing unnecessary errors and reconnections.

### 1.3 HIGH: Unnecessary Client-Side DLQ Logic

**Problem:** The subscriber manually tracked retry counts and routed messages to DLQ:

```java
// ORIGINAL CODE - MANUAL DLQ (UNNECESSARY)
if (receiveCount > retryConfig.getMaxAttempts()) {
    message.reQueue(kubeMQConfig.getQueue().getDlqChannel());
    return;
}
```

**Root Cause:** KubeMQ supports server-side DLQ out of the box. When a message is sent with `attemptsBeforeDeadLetterQueue` and `deadLetterQueue` configured, the **server** automatically routes the message to the DLQ after the configured number of rejections. The subscriber only needs to `ack()` or `reject()` - the server handles the rest.

**Impact:**
- Redundant logic that duplicates server functionality
- Required the subscriber to know the DLQ channel name (coupling between publisher and subscriber)
- Required `RetryConfig` dependency with `maxAttempts` and `dlqChannel` settings
- Risk of inconsistency if publisher and subscriber DLQ settings differ

### 1.4 MEDIUM: QueuesPollRequest Rebuilt on Every Poll

**Problem:** A new `QueuesPollRequest` object was created on every poll iteration:

```java
// ORIGINAL CODE - REBUILT EVERY ITERATION
private int pollAndProcessMessages(int loop) throws InterruptedException {
    QueuesPollRequest pollRequest = QueuesPollRequest.builder()
            .channel(kubeMQConfig.getQueue().getChannel())
            .pollMaxMessages(kubeMQConfig.getPoll().getMaxMessages())
            .pollWaitTimeoutInSeconds(kubeMQConfig.getPoll().getWaitTimeoutSeconds())
            .autoAckMessages(false)
            .visibilitySeconds(kubeMQConfig.getPoll().getVisibilitySeconds())
            .build();
    // ...
}
```

**Impact:** Unnecessary object allocation on every poll. The poll request parameters never change, so it should be built once and reused.

### 1.5 MEDIUM: Unnecessary Sleep After Empty Poll Response

**Problem:** After receiving an empty poll response (no messages), an additional sleep was added:

```java
// ORIGINAL CODE - UNNECESSARY SLEEP
if (response.getMessages() == null || response.getMessages().isEmpty()) {
    sleep(retryConfig.getPollDelayMs());  // Extra sleep on top of long-poll
    loop = 0;
    return loop;
}
```

**Root Cause:** The `pollWaitTimeoutInSeconds` parameter already implements server-side long-polling. The SDK blocks for that duration waiting for messages. If `pollWaitTimeoutInSeconds` is 30 seconds and `pollDelayMs` is 1000ms, the consumer unnecessarily waits 31 seconds between polls when the queue is empty.

**Impact:** Added latency to message consumption. When a message arrives right after the long-poll timeout, the consumer waits an extra `pollDelayMs` before the next poll.

### 1.6 MEDIUM: Commented-Out Exception in initializeClient()

**Problem:** The fail-fast exception was commented out:

```java
// ORIGINAL CODE - SILENTLY CONTINUES ON FAILURE
} catch (Exception e) {
    log.error("Failed to connect to KubeMQ", e);
    Thread.sleep(5_000);
    // throw new RuntimeException("Cannot start without KubeMQ connection", e);
}
```

**Impact:** If the KubeMQ server is unreachable at startup, the service silently continues with a potentially null or broken `queuesClient`. The worker thread starts anyway and immediately begins hitting errors, entering the error loop counter cycle.

### 1.7 LOW: Worker Thread Not Set as Daemon

**Problem:** The worker thread was created as a non-daemon thread:

```java
// ORIGINAL CODE - NON-DAEMON THREAD
workerThread = new Thread(this::messageLoop, "KubeMQ-Subscriber");
workerThread.start();
```

**Impact:** If the Spring context fails to shut down cleanly (e.g., `@PreDestroy` is not called due to a crash), this non-daemon thread prevents the JVM from exiting.

### 1.8 LOW: @PostConstruct Declaring throws InterruptedException

**Problem:** The `start()` method declared `throws InterruptedException`, which is unusual for Spring lifecycle methods:

```java
// ORIGINAL CODE
@PostConstruct
public void start() throws InterruptedException {
```

**Impact:** If `Thread.sleep(5_000)` inside `initializeClient()` was interrupted during startup, it propagated as a `BeanCreationException`, killing the application in an unclear way.

---

## 2. PublisherService - Problems Found

### 2.1 CRITICAL: No Server-Side DLQ Configuration

**Problem:** Messages were sent without DLQ configuration:

```java
// ORIGINAL CODE - NO DLQ CONFIG
QueueMessage message = QueueMessage.builder()
        .id(uid)
        .channel(queueName)
        .body(payload.getBytes(StandardCharsets.UTF_8))
        .build();  // Missing attemptsBeforeDeadLetterQueue and deadLetterQueue
```

**Root Cause:** KubeMQ's server-side DLQ is configured at the **message level** by the publisher. Without these fields, the server has no DLQ routing rules, and the subscriber was forced to implement client-side DLQ logic (which introduced the issues described in section 1.3).

**Impact:**
- Server-side DLQ was completely inactive
- All DLQ responsibility fell on the subscriber (fragile, error-prone)
- Messages that could never be processed (poison pills) would cycle indefinitely between the queue and the subscriber

---

## 3. Refactoring: SubscriberService

### What Was Removed

| Removed Item | Reason |
|---|---|
| `RetryConfig` dependency | DLQ is now server-side; subscriber no longer needs retry/DLQ config |
| `initializeClient()` method | SDK handles stream reconnection internally via `QueueDownstreamHandler.sendRequest()` |
| Error loop counter (`int loop`) | No longer needed without `initializeClient()` |
| Manual DLQ logic (`receiveCount` check, `reQueue`) | Server handles DLQ routing automatically |
| `sleep()` after empty poll response | `pollWaitTimeoutInSeconds` already provides long-polling |
| `throws InterruptedException` on `@PostConstruct` | Startup now fails fast with a clear exception |

### What Was Changed

| Change | Before | After |
|---|---|---|
| Client configuration | No keepAlive | `keepAlive(true)`, `pingIntervalInSeconds(30)`, `pingTimeoutInSeconds(10)` |
| Poll request | Rebuilt on every poll | Built once in `start()`, stored as field |
| Worker thread | Non-daemon | `setDaemon(true)` |
| Startup failure | Swallowed exception, continued with broken client | `ping()` throws if unreachable, preventing startup |
| Message loop | Error counter + sleep + reinitialize | Simple poll-process loop; errors logged and continued |
| `processMessage()` | Check `receiveCount`, manual `reQueue` to DLQ | Just `ack()` on success, `reject()` on failure |

### Key Architectural Change

The subscriber is now **stateless with respect to retry/DLQ logic**. It only has two responsibilities:
1. **Poll** for messages
2. **Process** each message: `ack()` if successful, `reject()` if not

The KubeMQ server tracks retry counts and automatically routes messages to the DLQ after the configured number of failed delivery attempts (set by the publisher).

---

## 4. Refactoring: PublisherService

### What Was Added

| Added Item | Purpose |
|---|---|
| `maxRetries` field | Configurable max delivery attempts before DLQ routing (default: 3) |
| `dlqChannel` field | DLQ channel name (default: `dlq-payment`) |
| `@Value("${kubemq.canal.payment.maxRetries:3}")` | Spring config binding with default |
| `@Value("${kubemq.canal.payment.dlq:dlq-payment}")` | Spring config binding with default |
| `.attemptsBeforeDeadLetterQueue(maxRetries)` on `QueueMessage` | Tells KubeMQ server to route to DLQ after N rejections |
| `.deadLetterQueue(dlqChannel)` on `QueueMessage` | Tells KubeMQ server which channel to use as DLQ |

---

## 5. Server-Side DLQ: How It Works

### Flow Diagram

```
Publisher                     KubeMQ Server                    Subscriber
   |                              |                               |
   |-- send message ------------->|                               |
   |   (maxRetries=3,            |                               |
   |    dlq="dlq-payment")       |                               |
   |                              |                               |
   |                              |-- deliver message ----------->|
   |                              |                  receiveCount=1|
   |                              |<-- reject() ------------------|
   |                              |                               |
   |                              |-- deliver message ----------->|
   |                              |                  receiveCount=2|
   |                              |<-- reject() ------------------|
   |                              |                               |
   |                              |-- deliver message ----------->|
   |                              |                  receiveCount=3|
   |                              |<-- reject() ------------------|
   |                              |                               |
   |                              |== 3 rejections reached ======>|
   |                              |== auto-route to DLQ =========>|
   |                              |                               |
   |                              | (message now in "dlq-payment")|
```

### Key Points

1. **Configuration is on the publisher side.** The publisher sets `attemptsBeforeDeadLetterQueue` and `deadLetterQueue` when sending each message.
2. **The server tracks rejections.** Each time the subscriber calls `reject()`, the server increments the `receiveCount`.
3. **Automatic routing.** When `receiveCount` reaches `attemptsBeforeDeadLetterQueue`, the server automatically moves the message to the DLQ channel.
4. **The subscriber just acks or rejects.** No need for the subscriber to check retry counts or manually route to DLQ.
5. **DLQ messages are marked.** Messages in the DLQ have `isReRouted() == true` and `reRouteFromQueue` set to the original channel name.

---

## 6. Configuration Changes Required

### application.properties - New Properties

Add the following to your `application.properties` (or equivalent YAML):

```properties
# DLQ configuration for the publisher
# Maximum delivery attempts before the message is routed to DLQ
kubemq.canal.payment.maxRetries=3

# Dead letter queue channel name
kubemq.canal.payment.dlq=dlq-payment
```

### application.properties - Properties That Can Be Removed

The subscriber no longer needs retry/DLQ configuration. The following properties (if they exist in your `RetryConfig`) can be removed from the subscriber's configuration:

```properties
# REMOVE THESE (no longer needed on subscriber side)
# retry.maxAttempts=...
# retry.pollDelayMs=...
# kubemq.queue.dlqChannel=...
```

### RetryConfig Class

The `RetryConfig` class is no longer injected into `SubscriberService`. If it is not used elsewhere, it can be removed entirely. If it is used by other services, no changes are needed - it is simply no longer a dependency of `SubscriberService`.

---

## 7. Verification

### Integration Test

A new integration test (`DlqIntegrationTest.java`) was written and executed against a live KubeMQ server at `localhost:50000`. It validates the complete server-side DLQ flow:

| Test | What It Validates | Result |
|---|---|---|
| `serverSideDlq_shouldRouteAfterMaxRejections` | Send with DLQ config, reject N times, verify message appears in DLQ channel with `isReRouted == true` | PASSED |
| `serverSideDlq_shouldNotRouteToDlqIfAcked` | Send with DLQ config, reject once, ack on second attempt, verify DLQ is empty | PASSED |
| `serverSideDlq_shouldIncrementReceiveCount` | Verify `receiveCount` increments on each rejection | PASSED |

### How to Run the Test

```bash
cd kubemq-java
JAVA_HOME=/path/to/jdk21 mvn test -Dtest=DlqIntegrationTest
```

Requires a running KubeMQ server at `localhost:50000` (configurable via `KUBEMQ_ADDRESS` environment variable).
