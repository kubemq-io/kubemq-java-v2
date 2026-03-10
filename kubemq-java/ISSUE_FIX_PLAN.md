# KubeMQ Java SDK - Issue Fix Plan

**Created**: December 24, 2024
**Status**: Approved with Minor Additions
**Estimated Effort**: 6-8 days
**Review Date**: December 24, 2024

---

## Executive Summary

This plan addresses all critical and high-priority issues identified in the Production Readiness Review and confirmed by the production readiness test suite. The fixes are organized by priority and include specific implementation details.

---

## Review Findings - Required Additions

The following items were identified during review and must be incorporated:

1. **InterruptedException Handling** - Add proper interrupt handling in all timeout catches
2. **Executor Shutdown** - Add reference counting and shutdown hooks for static executors
3. **EventStreamHelper Cleanup** - Apply same cleanup pattern as QueueUpstreamHandler
4. **Thread Safety Annotations** - Consider adding `@ThreadSafe` annotations

---

## Phase 1: Critical Issues (Days 1-2)

### CRITICAL-1: EventStreamHelper Thread Safety

**File**: `src/main/java/io/kubemq/sdk/pubsub/EventStreamHelper.java`

**Problem**: Single `CompletableFuture<EventSendResult> futureResponse` is shared across all concurrent calls to `sendEventStoreMessage()`, causing response mixup between threads.

**Solution**: Implement per-request future pattern (like QueueUpstreamHandler)

**Implementation**:
```java
// Before (line 17-18):
private CompletableFuture<EventSendResult> futureResponse;

// After:
private final Map<String, CompletableFuture<EventSendResult>> pendingResponses = new ConcurrentHashMap<>();

// In sendEventStoreMessage():
String requestId = UUID.randomUUID().toString();
CompletableFuture<EventSendResult> responseFuture = new CompletableFuture<>();
pendingResponses.put(requestId, responseFuture);

// Set requestId on the event before sending
Kubemq.Event eventWithId = event.toBuilder().setEventID(requestId).build();

// In onNext callback:
String eventId = result.getEventID();
CompletableFuture<EventSendResult> future = pendingResponses.remove(eventId);
if (future != null) {
    future.complete(EventSendResult.decode(result));
}
```

**Additional: Cleanup mechanism** (from review):
```java
// Add cleanup for orphaned requests (same pattern as QueueUpstreamHandler)
private final Map<String, Long> requestTimestamps = new ConcurrentHashMap<>();
private static final long REQUEST_TIMEOUT_MS = 60000;

private static final ScheduledExecutorService cleanupExecutor =
    Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "kubemq-event-cleanup");
        t.setDaemon(true);
        return t;
    });

// Start cleanup in constructor/connect:
cleanupExecutor.scheduleAtFixedRate(() -> {
    long now = System.currentTimeMillis();
    pendingResponses.entrySet().removeIf(entry -> {
        Long timestamp = requestTimestamps.get(entry.getKey());
        if (timestamp != null && (now - timestamp) > REQUEST_TIMEOUT_MS) {
            entry.getValue().complete(createTimeoutResult());
            requestTimestamps.remove(entry.getKey());
            log.warn("Cleaned up stale event request: {}", entry.getKey());
            return true;
        }
        return false;
    });
}, 30, 30, TimeUnit.SECONDS);
```

**Files to modify**:
- `EventStreamHelper.java` - Main fix + cleanup
- `EventStreamHelperTest.java` - Update tests

**Verification**: Run `EventStreamHelperConcurrencyTest` - all tests should pass

---

### CRITICAL-2: Visibility Timer Exception in Background Thread

**File**: `src/main/java/io/kubemq/sdk/queues/QueueMessageReceived.java`

**Problem**: `onVisibilityExpired()` throws `IllegalStateException` from Timer thread, which is swallowed silently.

**Solution**: Replace exception with logging; never throw from Timer threads

**Implementation**:
```java
// Before (lines 172-177):
private void onVisibilityExpired() {
    timerExpired = true;
    visibilityTimer = null;
    reject();
    throw new IllegalStateException("Message visibility expired");
}

// After:
private void onVisibilityExpired() {
    timerExpired = true;
    visibilityTimer = null;
    try {
        reject();
        log.warn("Message visibility expired, auto-rejected message: {}", id);
    } catch (Exception e) {
        log.error("Failed to auto-reject message {} on visibility expiry", id, e);
    }
    // Do NOT throw - we're in a Timer thread
}
```

**Additional improvement** - Use shared ScheduledExecutorService instead of Timer per message:
```java
// Add to class level:
private static final ScheduledExecutorService visibilityExecutor =
    Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "kubemq-visibility-timer");
        t.setDaemon(true);
        return t;
    });

private ScheduledFuture<?> visibilityFuture;

// Replace Timer usage with:
private void startVisibilityTimer() {
    if (visibilitySeconds > 0 && !isAutoAcked) {
        visibilityFuture = visibilityExecutor.schedule(
            this::onVisibilityExpired,
            visibilitySeconds,
            TimeUnit.SECONDS
        );
    }
}

private void cancelVisibilityTimer() {
    if (visibilityFuture != null) {
        visibilityFuture.cancel(false);
        visibilityFuture = null;
    }
}
```

**Files to modify**:
- `QueueMessageReceived.java` - Main fix

**Verification**: Run `VisibilityTimerExceptionTest` - all tests should pass

---

### CRITICAL-3: Infinite Reconnection Recursion

**Files**:
- `src/main/java/io/kubemq/sdk/pubsub/EventsSubscription.java`
- `src/main/java/io/kubemq/sdk/pubsub/EventsStoreSubscription.java`
- `src/main/java/io/kubemq/sdk/cq/CommandsSubscription.java`
- `src/main/java/io/kubemq/sdk/cq/QueriesSubscription.java`

**Problem**: `reconnect()` calls itself recursively with no exit condition, causing `StackOverflowError`.

**Solution**: Use ScheduledExecutorService with max retry limit and exponential backoff

**Implementation** (apply to all subscription classes):
```java
// Add fields:
private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
private static final int MAX_RECONNECT_ATTEMPTS = 10;
private static final ScheduledExecutorService reconnectExecutor =
    Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "kubemq-reconnect");
        t.setDaemon(true);
        return t;
    });

// Replace reconnect method:
private void reconnect(PubSubClient pubSubClient) {
    int attempt = reconnectAttempts.incrementAndGet();

    if (attempt > MAX_RECONNECT_ATTEMPTS) {
        log.error("Max reconnection attempts ({}) reached for channel: {}",
                  MAX_RECONNECT_ATTEMPTS, channel);
        raiseOnError("Max reconnection attempts reached after " + attempt + " tries");
        return;
    }

    // Exponential backoff: base * 2^attempt, capped at 60 seconds
    long delay = Math.min(
        pubSubClient.getReconnectIntervalInMillis() * (1L << (attempt - 1)),
        60000L
    );

    log.info("Scheduling reconnection attempt {} for channel {} in {}ms",
             attempt, channel, delay);

    reconnectExecutor.schedule(() -> {
        try {
            pubSubClient.getAsyncClient().subscribeToEvents(
                this.encode(pubSubClient.getClientId(), pubSubClient),
                this.getObserver()
            );
            reconnectAttempts.set(0); // Reset on success
            log.info("Successfully reconnected to channel {} after {} attempts",
                     channel, attempt);
        } catch (Exception e) {
            log.error("Reconnection attempt {} failed for channel {}", attempt, channel, e);
            reconnect(pubSubClient); // This is now safe - not recursive, scheduled
        }
    }, delay, TimeUnit.MILLISECONDS);
}

// Add method to reset attempts (call on successful message receive):
public void resetReconnectAttempts() {
    reconnectAttempts.set(0);
}
```

**Files to modify**:
- `EventsSubscription.java`
- `EventsStoreSubscription.java`
- `CommandsSubscription.java`
- `QueriesSubscription.java`

**Verification**: Run `ReconnectionRecursionTest` - all tests should pass

---

## Phase 2: High Priority Issues (Days 3-4)

### HIGH-1: Add Timeouts to Synchronous Methods

**Files**:
- `src/main/java/io/kubemq/sdk/queues/QueueUpstreamHandler.java`
- `src/main/java/io/kubemq/sdk/queues/QueueDownstreamHandler.java`
- `src/main/java/io/kubemq/sdk/pubsub/EventStreamHelper.java`

**Problem**: Synchronous methods use `.get()` without timeout, blocking indefinitely.

**Solution**: Add configurable timeout with sensible default

**Implementation**:

1. Add timeout configuration to `KubeMQClient`:
```java
// In KubeMQClient.java, add field:
@Builder.Default
private int requestTimeoutSeconds = 30;

public int getRequestTimeoutSeconds() {
    return requestTimeoutSeconds;
}
```

2. Update handlers to use timeout:
```java
// In QueueUpstreamHandler.sendQueuesMessage():
// Before:
return sendQueuesMessageAsync(queueMessage).get();

// After:
try {
    return sendQueuesMessageAsync(queueMessage)
        .get(kubeMQClient.getRequestTimeoutSeconds(), TimeUnit.SECONDS);
} catch (TimeoutException e) {
    QueueSendResult result = new QueueSendResult();
    result.setError("Request timed out after " + kubeMQClient.getRequestTimeoutSeconds() + " seconds");
    result.setIsError(true);
    return result;
}

// In QueueDownstreamHandler.receiveQueuesMessages():
// Before:
return responseFuture.get();

// After:
int timeout = Math.max(
    request.getPollWaitTimeoutInSeconds() + 5, // Add buffer
    kubeMQClient.getRequestTimeoutSeconds()
);
try {
    return responseFuture.get(timeout, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    QueuesPollResponse response = new QueuesPollResponse();
    response.setError("Request timed out after " + timeout + " seconds");
    response.setIsError(true);
    return response;
} catch (InterruptedException e) {
    Thread.currentThread().interrupt(); // Preserve interrupt status (from review)
    QueuesPollResponse response = new QueuesPollResponse();
    response.setError("Request interrupted");
    response.setIsError(true);
    return response;
}
```

**Files to modify**:
- `KubeMQClient.java` - Add timeout configuration
- `QueueUpstreamHandler.java` - Add timeout to sync method
- `QueueDownstreamHandler.java` - Add timeout to sync method
- `EventStreamHelper.java` - Add timeout to sync method

**Verification**: Run `HandlerTimeoutAndCleanupTest` timeout tests - should pass

---

### HIGH-2: Pending Response Cleanup

**Files**:
- `src/main/java/io/kubemq/sdk/queues/QueueUpstreamHandler.java`
- `src/main/java/io/kubemq/sdk/queues/QueueDownstreamHandler.java`

**Problem**: Orphaned requests in `pendingResponses` map are never cleaned up, causing memory leak.

**Solution**: Add scheduled cleanup task for stale entries

**Implementation**:
```java
// Add to QueueUpstreamHandler:
private static final long REQUEST_TIMEOUT_MS = 60000; // 60 seconds
private final Map<String, Long> requestTimestamps = new ConcurrentHashMap<>();

private static final ScheduledExecutorService cleanupExecutor =
    Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "kubemq-cleanup");
        t.setDaemon(true);
        return t;
    });

// Start cleanup task in constructor or connect():
private void startCleanupTask() {
    cleanupExecutor.scheduleAtFixedRate(() -> {
        long now = System.currentTimeMillis();
        pendingResponses.entrySet().removeIf(entry -> {
            Long timestamp = requestTimestamps.get(entry.getKey());
            if (timestamp != null && (now - timestamp) > REQUEST_TIMEOUT_MS) {
                // Complete the future with timeout error
                entry.getValue().complete(createTimeoutResult());
                requestTimestamps.remove(entry.getKey());
                log.warn("Cleaned up stale pending request: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }, 30, 30, TimeUnit.SECONDS);
}

// When adding to pendingResponses:
pendingResponses.put(requestId, responseFuture);
requestTimestamps.put(requestId, System.currentTimeMillis());

// When removing from pendingResponses:
pendingResponses.remove(requestId);
requestTimestamps.remove(requestId);
```

**For QueueDownstreamHandler** - also make the two maps atomic:
```java
// Replace two separate maps with a single wrapper class:
private static class PendingRequest {
    final CompletableFuture<QueuesPollResponse> future;
    final QueuesPollRequest request;
    final long timestamp;

    PendingRequest(CompletableFuture<QueuesPollResponse> future,
                   QueuesPollRequest request) {
        this.future = future;
        this.request = request;
        this.timestamp = System.currentTimeMillis();
    }
}

private final Map<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
```

**Files to modify**:
- `QueueUpstreamHandler.java`
- `QueueDownstreamHandler.java`

**Verification**: Run `HandlerTimeoutAndCleanupTest` cleanup tests - should pass

---

### HIGH-3: SDK-Specific Logger

**File**: `src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

**Problem**: `setLogLevel()` modifies ROOT logger, affecting entire application.

**Solution**: Only modify SDK-specific logger

**Implementation**:
```java
// Before (lines 231-234):
private void setLogLevel() {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger("ROOT");
    rootLogger.setLevel(ch.qos.logback.classic.Level.valueOf(logLevel.name()));
}

// After:
private void setLogLevel() {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    // Only modify SDK logger, not ROOT
    ch.qos.logback.classic.Logger sdkLogger = loggerContext.getLogger("io.kubemq.sdk");
    sdkLogger.setLevel(ch.qos.logback.classic.Level.valueOf(logLevel.name()));
    log.debug("Set SDK log level to: {}", logLevel);
}
```

**Files to modify**:
- `KubeMQClient.java`

**Verification**: Run `LoggerAndTLSValidationTest` logger tests - should pass

---

### HIGH-4: TLS Validation

**File**: `src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

**Problem**: TLS validation code is commented out (lines 79-81), allowing invalid configurations.

**Solution**: Implement proper TLS validation

**Implementation**:
```java
// Uncomment and improve validation (around line 79):
private void validateTlsConfiguration() {
    if (!tls) {
        return; // TLS not enabled, no validation needed
    }

    // Validate mutual TLS configuration
    boolean hasCert = tlsCertFile != null && !tlsCertFile.isEmpty();
    boolean hasKey = tlsKeyFile != null && !tlsKeyFile.isEmpty();

    if (hasCert != hasKey) {
        throw new IllegalArgumentException(
            "When using mutual TLS, both tlsCertFile and tlsKeyFile must be provided together. " +
            "Got tlsCertFile=" + (hasCert ? "set" : "null") +
            ", tlsKeyFile=" + (hasKey ? "set" : "null")
        );
    }

    // Validate CA cert for server verification
    if (caCertFile != null && !caCertFile.isEmpty()) {
        File caFile = new File(caCertFile);
        if (!caFile.exists()) {
            throw new IllegalArgumentException(
                "CA certificate file does not exist: " + caCertFile
            );
        }
    }

    // Validate client cert files if provided
    if (hasCert) {
        File certFile = new File(tlsCertFile);
        File keyFile = new File(tlsKeyFile);

        if (!certFile.exists()) {
            throw new IllegalArgumentException(
                "Client certificate file does not exist: " + tlsCertFile
            );
        }
        if (!keyFile.exists()) {
            throw new IllegalArgumentException(
                "Client key file does not exist: " + tlsKeyFile
            );
        }
    }

    log.debug("TLS configuration validated: caCert={}, clientCert={}, clientKey={}",
              caCertFile != null, hasCert, hasKey);
}

// Call in constructor after field initialization:
validateTlsConfiguration();
```

**Files to modify**:
- `KubeMQClient.java`

**Verification**: Run `LoggerAndTLSValidationTest` TLS tests - should pass

---

## Phase 3: Medium Priority Issues (Days 5-6)

### MEDIUM-1: Configurable Shutdown Timeout

**File**: `KubeMQClient.java`

```java
// Add field:
@Builder.Default
private int shutdownTimeoutSeconds = 10;

// Update close():
@Override
public void close() {
    if (managedChannel != null) {
        try {
            managedChannel.shutdown()
                .awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.warn("Channel shutdown interrupted, forcing shutdown");
            managedChannel.shutdownNow();
        }
    }
}

// Add forceClose():
public void forceClose() {
    if (managedChannel != null) {
        managedChannel.shutdownNow();
    }
}
```

### MEDIUM-2: Connection State API

**File**: `KubeMQClient.java`

```java
// Add methods:
public ConnectivityState getConnectionState() {
    if (managedChannel == null) {
        return ConnectivityState.SHUTDOWN;
    }
    return managedChannel.getState(false);
}

public boolean isConnected() {
    return getConnectionState() == ConnectivityState.READY;
}

public boolean waitForConnection(long timeout, TimeUnit unit) throws InterruptedException {
    if (managedChannel == null) {
        return false;
    }
    long deadline = System.nanoTime() + unit.toNanos(timeout);
    while (System.nanoTime() < deadline) {
        ConnectivityState state = managedChannel.getState(true);
        if (state == ConnectivityState.READY) {
            return true;
        }
        if (state == ConnectivityState.SHUTDOWN) {
            return false;
        }
        Thread.sleep(100);
    }
    return false;
}
```

### MEDIUM-3: Consistent Exception Hierarchy

**Create new exceptions**:
```java
// KubeMQException.java (base)
public class KubeMQException extends RuntimeException {
    public KubeMQException(String message) { super(message); }
    public KubeMQException(String message, Throwable cause) { super(message, cause); }
}

// TimeoutException.java
public class KubeMQTimeoutException extends KubeMQException {
    private final long timeoutMs;
    public KubeMQTimeoutException(String message, long timeoutMs) {
        super(message);
        this.timeoutMs = timeoutMs;
    }
}

// ValidationException.java
public class ValidationException extends KubeMQException {
    public ValidationException(String message) { super(message); }
}

// Update GRPCException to extend KubeMQException
public class GRPCException extends KubeMQException { ... }
```

### MEDIUM-4: Batch Send API

**File**: `QueuesClient.java`

```java
public List<QueueSendResult> sendQueuesMessages(List<QueueMessage> messages) {
    if (messages == null || messages.isEmpty()) {
        return Collections.emptyList();
    }

    // Validate all messages first
    for (QueueMessage message : messages) {
        message.validate();
    }

    List<CompletableFuture<QueueSendResult>> futures = new ArrayList<>();
    for (QueueMessage message : messages) {
        futures.add(queueUpstreamHandler.sendQueuesMessageAsync(message));
    }

    List<QueueSendResult> results = new ArrayList<>();
    for (CompletableFuture<QueueSendResult> future : futures) {
        try {
            results.add(future.get(requestTimeoutSeconds, TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            QueueSendResult timeoutResult = new QueueSendResult();
            timeoutResult.setError("Batch send timed out");
            timeoutResult.setIsError(true);
            results.add(timeoutResult);
        } catch (Exception e) {
            QueueSendResult errorResult = new QueueSendResult();
            errorResult.setError(e.getMessage());
            errorResult.setIsError(true);
            results.add(errorResult);
        }
    }
    return results;
}
```

---

## Phase 4: Low Priority Issues (Days 7-8)

### LOW-1: Fix Log Level in CommandsSubscription

**File**: `CommandsSubscription.java` line 48

```java
// Before:
log.error("Subscription Cancelled");

// After:
log.info("Subscription cancelled for channel: {}", channel);
```

### LOW-2: Channel Name Validation

**Add to message validation methods**:
```java
private static final Pattern CHANNEL_NAME_PATTERN =
    Pattern.compile("^[a-zA-Z0-9_\\-\\.]+$");

private void validateChannelName(String channel) {
    if (channel == null || channel.isEmpty()) {
        throw new IllegalArgumentException("Channel name is required");
    }
    if (channel.length() > 256) {
        throw new IllegalArgumentException("Channel name too long (max 256 chars)");
    }
    if (!CHANNEL_NAME_PATTERN.matcher(channel).matches()) {
        throw new IllegalArgumentException(
            "Channel name contains invalid characters. " +
            "Only alphanumeric, underscore, hyphen, and dot are allowed."
        );
    }
}
```

### LOW-3: Zero Timeout Validation

**Files**: `CommandMessage.java`, `QueryMessage.java`

```java
// In validate():
if (timeoutInSeconds <= 0) {
    throw new IllegalArgumentException(
        "Timeout must be greater than 0 seconds, got: " + timeoutInSeconds
    );
}
```

---

## Cross-Cutting Concern: Executor Shutdown (from review)

All static `ScheduledExecutorService` instances must be properly shut down. Add to `KubeMQClient`:

```java
// Reference counting for shared executors
private static final AtomicInteger clientCount = new AtomicInteger(0);

// Static shutdown hook (registered once)
private static volatile boolean shutdownHookRegistered = false;
private static final Object shutdownHookLock = new Object();

private void registerShutdownHook() {
    if (!shutdownHookRegistered) {
        synchronized (shutdownHookLock) {
            if (!shutdownHookRegistered) {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    log.info("Shutting down KubeMQ SDK executors...");
                    shutdownAllExecutors();
                }, "kubemq-shutdown"));
                shutdownHookRegistered = true;
            }
        }
    }
}

private static void shutdownAllExecutors() {
    // Shutdown all static executors used by the SDK
    shutdownExecutor(QueueUpstreamHandler.getCleanupExecutor());
    shutdownExecutor(QueueDownstreamHandler.getCleanupExecutor());
    shutdownExecutor(EventStreamHelper.getCleanupExecutor());
    shutdownExecutor(QueueMessageReceived.getVisibilityExecutor());
    shutdownExecutor(EventsSubscription.getReconnectExecutor());
    // ... other subscription classes
}

private static void shutdownExecutor(ExecutorService executor) {
    if (executor != null && !executor.isShutdown()) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

// In constructor:
clientCount.incrementAndGet();
registerShutdownHook();

// In close():
if (clientCount.decrementAndGet() == 0) {
    log.debug("Last client closed, executors will be cleaned up on JVM shutdown");
}
```

Each handler class should expose its executor via a static getter:
```java
// In QueueUpstreamHandler, QueueDownstreamHandler, etc:
static ScheduledExecutorService getCleanupExecutor() {
    return cleanupExecutor;
}
```

---

## Testing Strategy

After each phase, run the corresponding test suite:

```bash
# Phase 1 - Critical
mvn test -Dtest="**/productionreadiness/EventStreamHelperConcurrencyTest"
mvn test -Dtest="**/productionreadiness/VisibilityTimerExceptionTest"
mvn test -Dtest="**/productionreadiness/ReconnectionRecursionTest"

# Phase 2 - High Priority
mvn test -Dtest="**/productionreadiness/HandlerTimeoutAndCleanupTest"
mvn test -Dtest="**/productionreadiness/LoggerAndTLSValidationTest"

# Full regression
mvn test

# Integration tests (requires running KubeMQ server)
mvn verify -Pintegration-tests
```

---

## Implementation Order

1. **Day 1**: CRITICAL-1 (EventStreamHelper), CRITICAL-2 (Timer exception)
2. **Day 2**: CRITICAL-3 (Reconnection recursion) - all 4 subscription classes
3. **Day 3**: HIGH-1 (Timeouts), HIGH-2 (Cleanup) - handlers
4. **Day 4**: HIGH-3 (Logger), HIGH-4 (TLS validation)
5. **Day 5**: MEDIUM-1 (Shutdown), MEDIUM-2 (Connection state)
6. **Day 6**: MEDIUM-3 (Exceptions), MEDIUM-4 (Batch API)
7. **Day 7-8**: LOW priority items, documentation, final testing

---

## Rollback Plan

Each phase should be committed separately with clear commit messages:

```
fix(critical): EventStreamHelper thread safety - use per-request futures
fix(critical): Remove exception throw from visibility timer thread
fix(critical): Add max retries and exponential backoff to reconnection
fix(high): Add configurable timeouts to synchronous methods
fix(high): Add cleanup mechanism for pending responses
fix(high): Change logger scope from ROOT to io.kubemq.sdk
fix(high): Implement TLS configuration validation
```

If issues arise, revert the specific commit without affecting other fixes.

---

## Success Criteria

All production readiness tests pass:
```
Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
```

All existing unit tests continue to pass.

Integration tests pass against a live KubeMQ server.

---

---

## Documentation Updates Required (from review)

Update README.md and Javadoc with:

1. **New Configuration Options**:
   - `requestTimeoutSeconds` - Timeout for synchronous operations (default: 30)
   - `shutdownTimeoutSeconds` - Timeout for graceful shutdown (default: 10)

2. **New Connection State API**:
   ```java
   // Check if connected
   boolean connected = client.isConnected();

   // Get detailed state
   ConnectivityState state = client.getConnectionState();

   // Wait for connection with timeout
   boolean ready = client.waitForConnection(10, TimeUnit.SECONDS);
   ```

3. **Batch Send API**:
   ```java
   List<QueueMessage> messages = Arrays.asList(msg1, msg2, msg3);
   List<QueueSendResult> results = client.sendQueuesMessages(messages);
   ```

4. **TLS Configuration Requirements**:
   - When `tls=true`, at least `caCertFile` should be provided for server verification
   - For mutual TLS, both `tlsCertFile` and `tlsKeyFile` must be provided together
   - File paths are validated for existence at client creation time

5. **Thread Safety Guarantees**:
   - Document which classes are thread-safe
   - Note that SDK uses daemon threads for background tasks

---

**Document End**

*Created by Claude - December 24, 2024*
*Reviewed and approved with additions - December 24, 2024*
