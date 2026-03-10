# Gap-Close Specification: Category 05 -- Observability

**SDK:** KubeMQ Java v2 (`kubemq-java-v2`)
**Version Under Spec:** 2.1.1
**Golden Standard:** `clients/golden-standard/05-observability.md`
**Gap Research:** `clients/gap-research/java-gap-research.md` (lines 756-884)
**Assessment:** `clients/assesments/JAVA_ASSESSMENT_REPORT.md` (Category 7, lines 408-445)
**Current Score:** 1.86 / 5.0 | **Target:** 4.0+ | **Gap:** +2.14 | **Priority:** P0
**Date:** 2026-03-09

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Implementation Order](#2-implementation-order)
3. [REQ-OBS-5: Structured Logging Hooks](#3-req-obs-5-structured-logging-hooks)
4. [REQ-OBS-1: OpenTelemetry Trace Instrumentation](#4-req-obs-1-opentelemetry-trace-instrumentation)
5. [REQ-OBS-2: W3C Trace Context Propagation](#5-req-obs-2-w3c-trace-context-propagation)
6. [REQ-OBS-3: OpenTelemetry Metrics](#6-req-obs-3-opentelemetry-metrics)
7. [REQ-OBS-4: Near-Zero Cost When Not Configured](#7-req-obs-4-near-zero-cost-when-not-configured)
8. [Cross-Category Dependencies](#8-cross-category-dependencies)
9. [Test Plan](#9-test-plan)
10. [Breaking Changes](#10-breaking-changes)
11. [Migration Guide](#11-migration-guide)
12. [Open Questions](#12-open-questions)

---

## 1. Executive Summary

| REQ | Status | Effort | Breaking? | Summary |
|-----|--------|--------|-----------|---------|
| REQ-OBS-5 | PARTIAL | M (1-3d) | Minor | Replace Logback hard-dependency with SDK logger interface; SLF4J adapter as default when on classpath; trace correlation |
| REQ-OBS-1 | MISSING | XL (8-10d) | No | OTel span creation for all messaging operations, retry events, batch patterns, instrumentation scope |
| REQ-OBS-2 | MISSING | L (3-5d) | No | W3C Trace Context injection/extraction via message tags; RPC round-trip; requeue/DLQ preservation |
| REQ-OBS-3 | MISSING | L (3-5d) | No | 7 OTel metrics with attributes, histogram buckets, cardinality management, error.type mapping |
| REQ-OBS-4 | MISSING | M (covered by OBS-1/2/3) | No | `provided` scope dependency, injectable providers, `isRecording()` guards, documentation |

**Total Estimated Effort:** 18-25 days (revised upward per Review R1 C-1 proxy pattern)

**Implementation dependency chain:**
```
REQ-OBS-5 (no deps)
    |
    v
REQ-OBS-1 (depends on REQ-CQ-1, REQ-ERR-3) --> REQ-OBS-2 (depends on OBS-1)
    |                                                |
    v                                                v
REQ-OBS-3 (depends on OBS-1, REQ-CONN-2, REQ-ERR-2)
    |
    v
REQ-OBS-4 (architectural constraint applied throughout OBS-1/2/3)
```

---

## 1.1 Prerequisites (Review R1 X-1)

> This spec assumes REQ-ERR-1 (error hierarchy), REQ-ERR-2 (error classification), and
> REQ-ERR-3 (retry policy) from `01-error-handling-spec.md` are implemented. Types referenced:
> `ErrorCategory`, `ErrorClassifier.toOtelErrorType()`, `KubeMQException`.
> If these types do not yet exist when implementation begins, use the fallback stubs
> documented in each section. Remove fallback code once error handling is in place.

---

## 2. Implementation Order

**Phase 1 -- Logger Foundation (days 1-3):**
1. REQ-OBS-5 (structured logging hooks) -- no cross-category dependencies; enables all other categories to use the new logger

**Phase 2 -- OTel Tracing (days 4-13):**
2. REQ-OBS-1 (trace instrumentation) -- depends on REQ-CQ-1 (protocol layer), REQ-ERR-3 (retry events)
3. REQ-OBS-2 (trace context propagation) -- depends on OBS-1

**Phase 3 -- OTel Metrics (days 11-18):**
4. REQ-OBS-3 (metrics) -- depends on OBS-1 (shared OTel setup), REQ-CONN-2 (connection state for connection metrics), REQ-ERR-2 (error categories for error.type)

**Phase 4 -- Verification & Polish (days 17-23):**
5. REQ-OBS-4 (near-zero cost) -- architectural constraint verified across all OTel code; documentation and examples

> **Note:** REQ-OBS-4 is not a separate implementation item -- it is an architectural constraint applied throughout the OBS-1/2/3 implementations. Phase 4 is for verification, benchmarking, and documentation.

---

## 3. REQ-OBS-5: Structured Logging Hooks

**Gap Status:** PARTIAL | **Priority:** P0 | **Effort:** M (1-3 days)

### 3.1 Current State

**Source files examined:**

| File | Path | Evidence |
|------|------|----------|
| KubeMQClient | `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java` | Lines 3, 15-16: imports `ch.qos.logback.classic.LoggerContext`, `org.slf4j.LoggerFactory`; Line 30: `@Slf4j`; Lines 391-395: `setLogLevel()` casts `LoggerFactory.getILoggerFactory()` to Logback `LoggerContext` |
| PubSubClient | `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/PubSubClient.java` | Line 17: `@Slf4j` -- uses `log.debug(...)`, `log.error(...)` |
| CQClient | `kubemq-java/src/main/java/io/kubemq/sdk/cq/CQClient.java` | Line 15: `@Slf4j` |
| QueuesClient | `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueuesClient.java` | Line 17: `@Slf4j` |
| pom.xml | `kubemq-java/pom.xml` | Line 85-88: `ch.qos.logback:logback-classic:1.4.12` as compile-scope dependency |

**What exists:**
- SLF4J used via Lombok `@Slf4j` on 15 classes (parameterized logging `log.debug("message {}", param)`)
- Configurable log level via `Level` enum on client builder (TRACE, DEBUG, INFO, WARN, ERROR, OFF)
- Logback as a runtime dependency (compile scope in pom.xml)
- Sensitive data (tokens) excluded from logs (Assessment 7.1.5)

**What is missing or broken:**
1. **Logback hard-dependency** -- `setLogLevel()` (KubeMQClient line 391) casts `LoggerFactory.getILoggerFactory()` to `ch.qos.logback.classic.LoggerContext`, forcing Logback as the implementation
2. **No SDK-defined logger interface** -- direct coupling to SLF4J/Logback
3. **No structured key-value fields** -- log statements use `{}` placeholders, not key-value pairs (Assessment 7.1.1)
4. **No MDC context** -- no trace_id, span_id, client_id, channel in log entries (Assessment 7.1.6)
5. **No user-injectable logger** -- users cannot replace the logging implementation
6. **No no-op default** -- Logback is a required runtime dependency
7. **Per-message log levels not verified** -- individual publish/receive log levels need audit

### 3.2 Acceptance Criteria Mapping

| # | GS Acceptance Criterion | Current Status | Implementation Required |
|---|------------------------|----------------|------------------------|
| AC-1 | Logger interface defined with structured key-value fields | MISSING | Create `KubeMQLogger` interface |
| AC-2 | Default logger is no-op | MISSING | SLF4J adapter as default when SLF4J on classpath; `NoOpLogger` when not |
| AC-3 | User can inject preferred logger | MISSING | Add `logger(KubeMQLogger)` to client builder |
| AC-4 | Log entries include trace_id/span_id when OTel active | MISSING | Integrate with OTel context (Phase 2 dependency) |
| AC-5 | Sensitive data never logged | COMPLIANT | Already excludes tokens (Assessment 7.1.5) |
| AC-6 | Log levels appropriate | PARTIAL | Audit all log statements for level correctness |
| AC-7 | Per-message logging at DEBUG/TRACE only | NOT_ASSESSED | Audit and fix any INFO-level per-message logging |

### 3.3 Specification

#### 3.3.1 `KubeMQLogger` Interface

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/observability/KubeMQLogger.java` (NEW)

```java
package io.kubemq.sdk.observability;

/**
 * SDK-defined logger interface with structured key-value fields.
 * <p>
 * Users implement this interface to integrate their preferred logging framework.
 * The {@code keysAndValues} parameter accepts alternating key-value pairs:
 * {@code logger.info("Connected", "address", "localhost:50000", "clientId", "my-client")}
 * <p>
 * Odd-length varargs: the last value is logged with key "MISSING_VALUE".
 */
public interface KubeMQLogger {

    /**
     * Log at TRACE level. Used for per-message detail (individual publish/receive events).
     */
    void trace(String msg, Object... keysAndValues);

    /**
     * Log at DEBUG level. Used for retry attempts, keepalive pings, state transitions,
     * individual publish/receive summaries.
     */
    void debug(String msg, Object... keysAndValues);

    /**
     * Log at INFO level. Used for connection established, reconnection, subscription created,
     * graceful shutdown.
     */
    void info(String msg, Object... keysAndValues);

    /**
     * Log at WARN level. Used for insecure configuration, buffer near capacity,
     * deprecated API usage, cardinality threshold exceeded.
     */
    void warn(String msg, Object... keysAndValues);

    /**
     * Log at ERROR level. Used for connection failed (after retries exhausted),
     * auth failure, unrecoverable error.
     */
    void error(String msg, Object... keysAndValues);

    /**
     * Log at ERROR level with an associated exception.
     * The {@code cause} is passed to the underlying logger so that stack traces
     * are preserved (e.g., SLF4J's {@code error(String, Throwable)} method).
     * <p>
     * Review R1 M-5: Without this overload, exceptions passed as varargs values
     * are toString()'d and the stack trace is lost in the SLF4J output.
     */
    void error(String msg, Throwable cause, Object... keysAndValues);

    /**
     * Returns true if the given level is enabled. Used to guard expensive computation.
     */
    boolean isEnabled(LogLevel level);

    /**
     * Log levels for the {@link #isEnabled(LogLevel)} check.
     */
    enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR
    }
}
```

#### 3.3.2 `NoOpLogger` Implementation

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/observability/NoOpLogger.java` (NEW)

```java
package io.kubemq.sdk.observability;

/**
 * Default logger when no SLF4J is on classpath and no user logger is configured.
 * All methods are no-ops. Completely inlined by JIT.
 */
public final class NoOpLogger implements KubeMQLogger {

    public static final NoOpLogger INSTANCE = new NoOpLogger();

    private NoOpLogger() {}

    @Override public void trace(String msg, Object... keysAndValues) {}
    @Override public void debug(String msg, Object... keysAndValues) {}
    @Override public void info(String msg, Object... keysAndValues) {}
    @Override public void warn(String msg, Object... keysAndValues) {}
    @Override public void error(String msg, Object... keysAndValues) {}
    @Override public void error(String msg, Throwable cause, Object... keysAndValues) {}
    @Override public boolean isEnabled(LogLevel level) { return false; }
}
```

#### 3.3.3 `Slf4jLoggerAdapter` Implementation

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/observability/Slf4jLoggerAdapter.java` (NEW)

This adapter maintains backward compatibility for existing users who rely on SLF4J. It is the default when SLF4J is detected on the classpath (per review R1 M-12).

```java
package io.kubemq.sdk.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * SLF4J adapter for KubeMQLogger. Default when SLF4J is on the classpath.
 * <p>
 * Converts structured key-value pairs to SLF4J parameterized messages.
 * When OpenTelemetry context is active, populates MDC with trace_id and span_id
 * (requires opentelemetry-logback-mdc or equivalent OTel-SLF4J bridge on classpath).
 * <p>
 * Compatible with SLF4J 1.7.x and 2.x.
 */
public final class Slf4jLoggerAdapter implements KubeMQLogger {

    private final Logger delegate;

    public Slf4jLoggerAdapter(String loggerName) {
        this.delegate = LoggerFactory.getLogger(loggerName);
    }

    public Slf4jLoggerAdapter(Class<?> clazz) {
        this.delegate = LoggerFactory.getLogger(clazz);
    }

    @Override
    public void trace(String msg, Object... keysAndValues) {
        if (delegate.isTraceEnabled()) {
            delegate.trace(formatMessage(msg, keysAndValues));
        }
    }

    @Override
    public void debug(String msg, Object... keysAndValues) {
        if (delegate.isDebugEnabled()) {
            delegate.debug(formatMessage(msg, keysAndValues));
        }
    }

    @Override
    public void info(String msg, Object... keysAndValues) {
        if (delegate.isInfoEnabled()) {
            delegate.info(formatMessage(msg, keysAndValues));
        }
    }

    @Override
    public void warn(String msg, Object... keysAndValues) {
        if (delegate.isWarnEnabled()) {
            delegate.warn(formatMessage(msg, keysAndValues));
        }
    }

    @Override
    public void error(String msg, Object... keysAndValues) {
        if (delegate.isErrorEnabled()) {
            delegate.error(formatMessage(msg, keysAndValues));
        }
    }

    @Override
    public void error(String msg, Throwable cause, Object... keysAndValues) {
        if (delegate.isErrorEnabled()) {
            delegate.error(formatMessage(msg, keysAndValues), cause);
        }
    }

    @Override
    public boolean isEnabled(LogLevel level) {
        switch (level) {
            case TRACE: return delegate.isTraceEnabled();
            case DEBUG: return delegate.isDebugEnabled();
            case INFO:  return delegate.isInfoEnabled();
            case WARN:  return delegate.isWarnEnabled();
            case ERROR: return delegate.isErrorEnabled();
            default:    return false;
        }
    }

    /**
     * Convert structured key-value pairs to a human-readable log message.
     * Format: "msg [key1=value1, key2=value2]"
     */
    static String formatMessage(String msg, Object... keysAndValues) {
        if (keysAndValues == null || keysAndValues.length == 0) {
            return msg;
        }
        StringBuilder sb = new StringBuilder(msg).append(" [");
        for (int i = 0; i < keysAndValues.length; i += 2) {
            if (i > 0) sb.append(", ");
            String key = String.valueOf(keysAndValues[i]);
            Object value = (i + 1 < keysAndValues.length)
                    ? keysAndValues[i + 1]
                    : "MISSING_VALUE";
            sb.append(key).append('=').append(value);
        }
        sb.append(']');
        return sb.toString();
    }
}
```

#### 3.3.4 `KubeMQLoggerFactory` (SDK-internal)

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/observability/KubeMQLoggerFactory.java` (NEW)

```java
package io.kubemq.sdk.observability;

/**
 * Factory that resolves the default KubeMQLogger.
 * <p>
 * Resolution order:
 * 1. User-provided logger via client builder (always wins)
 * 2. SLF4J adapter if SLF4J is on the classpath (backward-compatible default)
 * 3. NoOpLogger (silent default when no logging framework is present)
 */
public final class KubeMQLoggerFactory {

    private static final boolean SLF4J_AVAILABLE;

    static {
        boolean available;
        try {
            Class.forName("org.slf4j.Logger", false, KubeMQLoggerFactory.class.getClassLoader());
            available = true;
        } catch (ClassNotFoundException e) {
            available = false;
        }
        SLF4J_AVAILABLE = available;
    }

    private KubeMQLoggerFactory() {}

    /**
     * Returns the default logger for the given name.
     * Returns Slf4jLoggerAdapter if SLF4J is on classpath, NoOpLogger otherwise.
     */
    public static KubeMQLogger getLogger(String name) {
        if (SLF4J_AVAILABLE) {
            return new Slf4jLoggerAdapter(name);
        }
        return NoOpLogger.INSTANCE;
    }

    /**
     * Returns the default logger for the given class.
     */
    public static KubeMQLogger getLogger(Class<?> clazz) {
        if (SLF4J_AVAILABLE) {
            return new Slf4jLoggerAdapter(clazz);
        }
        return NoOpLogger.INSTANCE;
    }

    /**
     * Returns true if SLF4J is available on the classpath.
     */
    public static boolean isSlf4jAvailable() {
        return SLF4J_AVAILABLE;
    }
}
```

#### 3.3.5 OTel Trace Correlation in Log Entries

When OTel context is active, the logger must include `trace_id` and `span_id` in log entries. Two mechanisms:

**Mechanism A (SLF4J MDC -- preferred for SLF4J users):**
The `Slf4jLoggerAdapter` detects OpenTelemetry context and populates SLF4J MDC before each log call. Users can use the `opentelemetry-logback-mdc-1.0` or `opentelemetry-log4j-context-data-2.17-autoconfigure` dependency to get automatic MDC population. This approach requires no SDK changes beyond documenting the recommended OTel-SLF4J bridge dependency.

**Mechanism B (explicit fields -- for custom KubeMQLogger implementations):**
The SDK adds a `LogContextProvider` that extracts trace context and provides it as additional key-value pairs to the logger:

```java
package io.kubemq.sdk.observability;

/**
 * Extracts OTel trace context for log correlation.
 * Returns empty arrays when OTel is not configured (no-op).
 */
final class LogContextProvider {

    private static final boolean OTEL_AVAILABLE;

    static {
        boolean available;
        try {
            Class.forName("io.opentelemetry.api.trace.Span",
                          false, LogContextProvider.class.getClassLoader());
            available = true;
        } catch (ClassNotFoundException e) {
            available = false;
        }
        OTEL_AVAILABLE = available;
    }

    private LogContextProvider() {}

    /**
     * Returns additional key-value pairs for trace correlation.
     * Returns ["trace_id", "<hex>", "span_id", "<hex>"] when OTel is active,
     * or empty array when not.
     */
    static Object[] getTraceContext() {
        if (!OTEL_AVAILABLE) {
            return EMPTY;
        }
        return getTraceContextFromOTel();
    }

    private static final Object[] EMPTY = new Object[0];

    // Separated to avoid class loading of OTel API when not available.
    // NOTE: On HotSpot JVM, this method's OTel type references are only resolved
    // when the method is actually called (after the OTEL_AVAILABLE guard).
    // On GraalVM native-image, all referenced types may be resolved eagerly --
    // if native-image support is needed, move this method to a separate class.
    private static Object[] getTraceContextFromOTel() {
        io.opentelemetry.api.trace.SpanContext ctx =
            io.opentelemetry.api.trace.Span.current().getSpanContext();
        if (ctx.isValid()) {
            return new Object[]{
                "trace_id", ctx.getTraceId(),
                "span_id", ctx.getSpanId()
            };
        }
        return EMPTY;
    }
}
```

The SDK-internal logging helper merges trace context into every log call using `LogHelper.merge()`:

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/observability/LogHelper.java` (NEW)

```java
package io.kubemq.sdk.observability;

/**
 * Utility for merging structured key-value arrays for logging.
 * Review R1 M-6: Java varargs cannot splice Object[] inline; this utility
 * concatenates multiple key-value arrays into a single flat array.
 */
public final class LogHelper {

    private LogHelper() {}

    /**
     * Merges multiple key-value arrays into one flat array.
     * Example: merge(new Object[]{"a", 1}, new Object[]{"b", 2}) => ["a", 1, "b", 2]
     */
    public static Object[] merge(Object[]... arrays) {
        int totalLength = 0;
        for (Object[] arr : arrays) {
            if (arr != null) totalLength += arr.length;
        }
        if (totalLength == 0) return new Object[0];

        Object[] result = new Object[totalLength];
        int pos = 0;
        for (Object[] arr : arrays) {
            if (arr != null && arr.length > 0) {
                System.arraycopy(arr, 0, result, pos, arr.length);
                pos += arr.length;
            }
        }
        return result;
    }
}
```

Usage at call sites:

```java
// Inside SDK classes, replace `log.debug(...)` with:
logger.debug("Ping successful",
    LogHelper.merge(
        new Object[]{"address", address},
        LogContextProvider.getTraceContext()
    ));
```

> **Alternative approach:** Instead of requiring `LogHelper.merge()` at every call site, each `KubeMQLogger` implementation could internally call `LogContextProvider.getTraceContext()` and append trace context to every log entry. This centralizes the merge logic but requires logger implementations to be trace-context-aware. The `LogHelper.merge()` approach is simpler and keeps logger implementations pure.

#### 3.3.6 Client Builder Changes

Modify `KubeMQClient` builder to accept an optional `KubeMQLogger`:

```java
// In KubeMQClient (or in the builder if migrated per 07-code-quality-spec.md):
private KubeMQLogger logger;

// Builder method:
public B logger(KubeMQLogger logger) {
    this.logger = logger;
    return self();
}

// In init (replaces setLogLevel()):
private void initLogger() {
    if (this.logger == null) {
        this.logger = io.kubemq.sdk.observability.KubeMQLoggerFactory.getLogger("io.kubemq.sdk");
    }
}
```

#### 3.3.7 Logback Removal

1. Change `logback-classic` from compile scope to `test` scope in `pom.xml`
2. Change `slf4j-api` to `provided` scope (users bring their own SLF4J binding)
3. Remove the `setLogLevel()` method that casts to `LoggerContext` (KubeMQClient lines 391-395)
4. Remove `import ch.qos.logback.classic.LoggerContext` (KubeMQClient line 3)
5. The `Level` enum (KubeMQClient line 423) is replaced by `KubeMQLogger.LogLevel`
6. Retain the `logLevel` builder parameter for backward compatibility -- it controls the SLF4J adapter's effective level when the `Slf4jLoggerAdapter` is used

#### 3.3.8 Log Level Audit

Per GS REQ-OBS-5, per-message logging must be DEBUG or TRACE, never INFO. The following log statements must be audited and corrected:

| Level | Appropriate Events |
|-------|--------------------|
| TRACE | Individual message body/payload details, raw gRPC frame content |
| DEBUG | Retry attempts, keepalive pings, state transitions, individual publish/receive events, per-message operation summaries |
| INFO | Connection established, reconnection successful, subscription created, graceful shutdown started/completed |
| WARN | Insecure configuration (skip_verify, plaintext to remote), buffer near capacity, deprecated API usage, cardinality threshold exceeded |
| ERROR | Connection failed (after retries exhausted), authentication failure, unrecoverable error |

**Migration approach:** Replace all `@Slf4j` + `log.xxx(...)` calls across 15 classes with `this.logger.xxx(...)` calls using the `KubeMQLogger` interface. Each class receives a `KubeMQLogger` instance via constructor injection from the client.

#### 3.3.9 Files Changed / Created

| File | Action | Description |
|------|--------|-------------|
| `kubemq-java/src/main/java/io/kubemq/sdk/observability/KubeMQLogger.java` | NEW | Logger interface |
| `kubemq-java/src/main/java/io/kubemq/sdk/observability/NoOpLogger.java` | NEW | No-op implementation |
| `kubemq-java/src/main/java/io/kubemq/sdk/observability/Slf4jLoggerAdapter.java` | NEW | SLF4J bridge |
| `kubemq-java/src/main/java/io/kubemq/sdk/observability/KubeMQLoggerFactory.java` | NEW | Auto-detection factory |
| `kubemq-java/src/main/java/io/kubemq/sdk/observability/LogContextProvider.java` | NEW | OTel trace correlation |
| `kubemq-java/src/main/java/io/kubemq/sdk/observability/LogHelper.java` | NEW | Varargs merge utility (Review R1 M-6) |
| `kubemq-java/src/main/java/io/kubemq/sdk/observability/Tracing.java` | NEW | Tracing interface (Review R1 C-1, M-7) |
| `kubemq-java/src/main/java/io/kubemq/sdk/observability/NoOpTracing.java` | NEW | No-op tracing (Review R1 C-1, M-7) |
| `kubemq-java/src/main/java/io/kubemq/sdk/observability/Metrics.java` | NEW | Metrics interface (Review R1 C-1, M-7) |
| `kubemq-java/src/main/java/io/kubemq/sdk/observability/NoOpMetrics.java` | NEW | No-op metrics (Review R1 C-1, M-7) |
| `kubemq-java/src/main/java/io/kubemq/sdk/observability/TracingFactory.java` | NEW | Lazy-loads KubeMQTracing or NoOpTracing (Review R1 C-1) |
| `kubemq-java/src/main/java/io/kubemq/sdk/observability/MetricsFactory.java` | NEW | Lazy-loads KubeMQMetrics or NoOpMetrics (Review R1 C-1) |
| `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java` | MODIFY | Remove Logback import/cast, add logger field, replace @Slf4j |
| `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/PubSubClient.java` | MODIFY | Replace @Slf4j with KubeMQLogger |
| `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventStreamHelper.java` | MODIFY | Replace @Slf4j with KubeMQLogger |
| `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsSubscription.java` | MODIFY | Replace @Slf4j with KubeMQLogger |
| `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsStoreSubscription.java` | MODIFY | Replace @Slf4j with KubeMQLogger |
| `kubemq-java/src/main/java/io/kubemq/sdk/cq/CQClient.java` | MODIFY | Replace @Slf4j with KubeMQLogger |
| `kubemq-java/src/main/java/io/kubemq/sdk/cq/CommandsSubscription.java` | MODIFY | Replace @Slf4j with KubeMQLogger |
| `kubemq-java/src/main/java/io/kubemq/sdk/cq/QueriesSubscription.java` | MODIFY | Replace @Slf4j with KubeMQLogger |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueuesClient.java` | MODIFY | Replace @Slf4j with KubeMQLogger |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueUpstreamHandler.java` | MODIFY | Replace @Slf4j with KubeMQLogger |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueDownstreamHandler.java` | MODIFY | Replace @Slf4j with KubeMQLogger |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueuesPollRequest.java` | MODIFY | Replace @Slf4j with KubeMQLogger |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueuesPollResponse.java` | MODIFY | Replace @Slf4j with KubeMQLogger |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueMessageWaitingPulled.java` | MODIFY | Replace @Slf4j with KubeMQLogger |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueMessageReceived.java` | MODIFY | Replace @Slf4j with KubeMQLogger |
| `kubemq-java/src/main/java/io/kubemq/sdk/common/KubeMQUtils.java` | MODIFY | Replace @Slf4j with KubeMQLogger |
| `kubemq-java/pom.xml` | MODIFY | logback-classic to test scope; slf4j-api to provided scope |

---

## 4. REQ-OBS-1: OpenTelemetry Trace Instrumentation

**Gap Status:** MISSING | **Priority:** P0 | **Effort:** XL (8-10 days)

### 4.1 Current State

**Assessment Evidence:** 7.3.1-7.3.3: "No W3C Trace Context propagation. No span creation. No OpenTelemetry dependency or integration."

The SDK has zero OTel integration:
- No `opentelemetry-api` dependency in `pom.xml`
- No span creation anywhere in the codebase
- No instrumentation scope configuration
- No OTel attribute constants
- No trace context carrier implementation

### 4.2 Acceptance Criteria Mapping

| # | GS Acceptance Criterion | Current Status | Implementation Required |
|---|------------------------|----------------|------------------------|
| AC-1 | Spans created for all messaging operations | MISSING | Instrument all 6 operation types |
| AC-2 | All required attributes set on every span | MISSING | Implement `KubeMQSemconv` constants, set on every span |
| AC-3 | Failed operations set span status to ERROR | MISSING | Add error status in catch blocks |
| AC-4 | Batch operations set `messaging.batch.message_count` | MISSING | Add to queue receive batch spans |
| AC-5 | Span names follow `{operation} {channel}` format | MISSING | Implement naming convention |
| AC-6 | Retry attempts recorded as span events | MISSING | Add retry events per REQ-ERR-3 integration |
| AC-7 | Batch consume: receive/process span pattern with links | MISSING | Implement batch trace pattern |
| AC-8 | Instrumentation scope name = `io.kubemq.sdk`; version = SDK version | MISSING | Configure Tracer with correct scope |

### 4.3 Specification

#### 4.3.1 Maven Dependency

Add to `kubemq-java/pom.xml`:

```xml
<!-- OpenTelemetry API: provided scope = compile-time only, users bring their own SDK -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <version>1.40.0</version>
    <scope>provided</scope>
</dependency>

<!-- BOM for consistent OTel versions (import in dependencyManagement) -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-bom</artifactId>
    <version>1.40.0</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

**Minimum supported OTel API version:** 1.32.0 (first stable metrics API release). Document this in README.

#### 4.3.2 `KubeMQSemconv` -- Semantic Convention Constants

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/observability/KubeMQSemconv.java` (NEW)

All OTel attribute names defined in one file per GS requirement.

> **Review R1 m-4:** This class imports `io.opentelemetry.api.common.AttributeKey` at class level.
> If loaded when OTel is not on the classpath, it will throw `NoClassDefFoundError`. Per the
> proxy pattern from C-1, `KubeMQSemconv` must ONLY be loaded within OTel-available code paths
> (inside `KubeMQTracing` and `KubeMQMetrics`), never from main client classes or the `Tracing`/`Metrics` interfaces.

```java
package io.kubemq.sdk.observability;

import io.opentelemetry.api.common.AttributeKey;

/**
 * OpenTelemetry semantic convention constants for KubeMQ SDK.
 * Based on OTel messaging semconv v1.27.0.
 * <p>
 * All attribute keys are defined here as the single source of truth.
 */
public final class KubeMQSemconv {

    private KubeMQSemconv() {}

    // ---- System identifier ----
    public static final String MESSAGING_SYSTEM_VALUE = "kubemq";

    // ---- Required span attributes (messaging semconv) ----
    public static final AttributeKey<String> MESSAGING_SYSTEM =
            AttributeKey.stringKey("messaging.system");
    public static final AttributeKey<String> MESSAGING_OPERATION_NAME =
            AttributeKey.stringKey("messaging.operation.name");
    public static final AttributeKey<String> MESSAGING_OPERATION_TYPE =
            AttributeKey.stringKey("messaging.operation.type");
    public static final AttributeKey<String> MESSAGING_DESTINATION_NAME =
            AttributeKey.stringKey("messaging.destination.name");
    public static final AttributeKey<String> MESSAGING_MESSAGE_ID =
            AttributeKey.stringKey("messaging.message.id");
    public static final AttributeKey<String> MESSAGING_CLIENT_ID =
            AttributeKey.stringKey("messaging.client.id");
    public static final AttributeKey<String> MESSAGING_CONSUMER_GROUP_NAME =
            AttributeKey.stringKey("messaging.consumer.group.name");
    public static final AttributeKey<String> SERVER_ADDRESS =
            AttributeKey.stringKey("server.address");
    public static final AttributeKey<Long> SERVER_PORT =
            AttributeKey.longKey("server.port");
    public static final AttributeKey<String> ERROR_TYPE =
            AttributeKey.stringKey("error.type");

    // ---- Recommended span attributes ----
    public static final AttributeKey<Long> MESSAGING_MESSAGE_BODY_SIZE =
            AttributeKey.longKey("messaging.message.body.size");

    // ---- Batch attributes ----
    public static final AttributeKey<Long> MESSAGING_BATCH_MESSAGE_COUNT =
            AttributeKey.longKey("messaging.batch.message_count");

    // ---- Retry span event attributes ----
    public static final String RETRY_EVENT_NAME = "retry";
    public static final AttributeKey<Long> RETRY_ATTEMPT =
            AttributeKey.longKey("retry.attempt");
    public static final AttributeKey<Double> RETRY_DELAY_SECONDS =
            AttributeKey.doubleKey("retry.delay_seconds");
    // error.type reused from above for retry event error attribution

    // ---- DLQ span event ----
    public static final String DLQ_EVENT_NAME = "message.dead_lettered";

    // ---- Instrumentation scope ----
    public static final String INSTRUMENTATION_SCOPE_NAME = "io.kubemq.sdk";
    // Version loaded at runtime from pom.xml / manifest

    // ---- Operation name constants ----
    public static final String OP_PUBLISH = "publish";
    public static final String OP_PROCESS = "process";
    public static final String OP_RECEIVE = "receive";
    public static final String OP_SETTLE = "settle";
    public static final String OP_SEND = "send";
}
```

#### 4.3.3 Span Configuration Table

| Operation | Span Kind | Span Name | `messaging.operation.name` | `messaging.operation.type` |
|-----------|-----------|-----------|---------------------------|---------------------------|
| Publish Event / Event Store | PRODUCER | `publish {channel}` | `publish` | `publish` |
| Publish Queue Message | PRODUCER | `publish {channel}` | `publish` | `publish` |
| Subscribe callback (Events, Events Store) | CONSUMER | `process {channel}` | `process` | `process` |
| Queue Receive (poll-based) | CONSUMER | `receive {channel}` | `receive` | `receive` |
| Queue Ack / Reject / Requeue | CONSUMER | `settle {channel}` | `settle` | `settle` |
| Command send | CLIENT | `send {channel}` | `send` | `send` |
| Query send | CLIENT | `send {channel}` | `send` | `send` |
| Command response processing | SERVER | `process {channel}` | `process` | `process` |
| Query response processing | SERVER | `process {channel}` | `process` | `process` |

#### 4.3.4a `Tracing` Interface and `NoOpTracing` (Review R1 M-7, C-1)

Client code references only the `Tracing` interface, never the OTel-importing `KubeMQTracing` directly. This avoids `NoClassDefFoundError` when OTel is absent (see Section 7.3.2).

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/observability/Tracing.java` (NEW)

```java
package io.kubemq.sdk.observability;

/**
 * Abstraction for SDK tracing operations. Client code uses this interface
 * exclusively; the OTel-importing implementation (KubeMQTracing) is loaded
 * only when OTel is confirmed available on the classpath.
 */
public interface Tracing {

    /** Start a span. Returns an opaque handle that must be ended via endSpan(). */
    Object startSpan(Object spanKind, String operationName, String channel,
                     String messageId, Object parentContext);

    /** Start a linked consumer span. */
    Object startLinkedConsumerSpan(String operationName, String channel,
                                   String messageId, Object producerContext);

    /** Start a batch receive span. */
    Object startBatchReceiveSpan(String channel, int messageCount,
                                  java.util.List<?> producerContexts);

    /** Record a retry event on a span. */
    void recordRetryEvent(Object span, int attempt, double delaySeconds, String errorType);

    /** Record a DLQ event on a span. */
    void recordDlqEvent(Object span);

    /** Set error status on a span. */
    void setError(Object span, Throwable error, String errorTypeValue);

    /** Set body size attribute. */
    void setBodySize(Object span, byte[] body);

    /** Set consumer group attribute. */
    void setConsumerGroup(Object span, String groupName);

    /** End a span. */
    void endSpan(Object span);

    /** Make span current and return a scope that must be closed. */
    AutoCloseable makeCurrent(Object span);

    /** Inject trace context into a tags map. */
    void injectContext(Object currentContext, java.util.Map<String, String> tags);

    /** Extract trace context from a tags map. Returns an opaque context. */
    Object extractContext(java.util.Map<String, String> tags);
}
```

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/observability/NoOpTracing.java` (NEW)

```java
package io.kubemq.sdk.observability;

/**
 * No-op tracing implementation. Used when OTel API is not on the classpath.
 * All methods are no-ops; completely inlined by JIT.
 */
public final class NoOpTracing implements Tracing {

    public static final NoOpTracing INSTANCE = new NoOpTracing();
    private static final Object NOOP_SPAN = new Object();
    private static final AutoCloseable NOOP_SCOPE = () -> {};

    private NoOpTracing() {}

    @Override public Object startSpan(Object spanKind, String op, String ch,
                                       String mid, Object ctx) { return NOOP_SPAN; }
    @Override public Object startLinkedConsumerSpan(String op, String ch,
                                                     String mid, Object ctx) { return NOOP_SPAN; }
    @Override public Object startBatchReceiveSpan(String ch, int count,
                                                    java.util.List<?> ctxs) { return NOOP_SPAN; }
    @Override public void recordRetryEvent(Object span, int attempt,
                                            double delay, String err) {}
    @Override public void recordDlqEvent(Object span) {}
    @Override public void setError(Object span, Throwable err, String errType) {}
    @Override public void setBodySize(Object span, byte[] body) {}
    @Override public void setConsumerGroup(Object span, String group) {}
    @Override public void endSpan(Object span) {}
    @Override public AutoCloseable makeCurrent(Object span) { return NOOP_SCOPE; }
    @Override public void injectContext(Object ctx, java.util.Map<String, String> tags) {}
    @Override public Object extractContext(java.util.Map<String, String> tags) { return null; }
}
```

Similarly, define `Metrics` interface and `NoOpMetrics`:

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/observability/Metrics.java` (NEW)

```java
package io.kubemq.sdk.observability;

/**
 * Abstraction for SDK metrics operations. Client code uses this interface
 * exclusively.
 */
public interface Metrics {
    void recordOperationDuration(double durationSeconds, String operationName,
                                  String channel, String errorType);
    void recordSentMessage(String operationName, String channel);
    void recordConsumedMessage(String operationName, String channel);
    void recordConnectionOpened();
    void recordConnectionClosed();
    void recordReconnectionAttempt();
    void recordRetryAttempt(String operationName, String errorType);
    void recordRetryExhausted(String operationName, String errorType);
}
```

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/observability/NoOpMetrics.java` (NEW)

```java
package io.kubemq.sdk.observability;

public final class NoOpMetrics implements Metrics {
    public static final NoOpMetrics INSTANCE = new NoOpMetrics();
    private NoOpMetrics() {}

    @Override public void recordOperationDuration(double d, String op, String ch, String err) {}
    @Override public void recordSentMessage(String op, String ch) {}
    @Override public void recordConsumedMessage(String op, String ch) {}
    @Override public void recordConnectionOpened() {}
    @Override public void recordConnectionClosed() {}
    @Override public void recordReconnectionAttempt() {}
    @Override public void recordRetryAttempt(String op, String err) {}
    @Override public void recordRetryExhausted(String op, String err) {}
}
```

> **Note:** `KubeMQTracing` implements `Tracing` and `KubeMQMetrics` implements `Metrics`.
> The `Object` parameter types in `Tracing` are unwrapped to OTel-specific types inside `KubeMQTracing`.
> Client code only interacts with `Tracing` and `Metrics` interfaces.

#### 4.3.4 `KubeMQTracing` Class

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/observability/KubeMQTracing.java` (NEW)

This class encapsulates all span creation logic. It is instantiated once per client.
It implements the `Tracing` interface (Section 4.3.4a) and is loaded only when OTel is available.

```java
package io.kubemq.sdk.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;

/**
 * Centralized tracing instrumentation for the KubeMQ SDK.
 * <p>
 * When no OTel SDK is registered, all operations use no-op implementations
 * with near-zero overhead (REQ-OBS-4).
 */
public final class KubeMQTracing implements Tracing {

    private final Tracer tracer;
    private final String clientId;
    private final String serverAddress;
    private final int serverPort;

    /**
     * @param tracerProvider If null, falls back to GlobalOpenTelemetry.getTracerProvider()
     * @param sdkVersion     SDK version for instrumentation scope
     * @param clientId       Client ID for messaging.client.id attribute
     * @param serverAddress  Server hostname
     * @param serverPort     Server port
     */
    public KubeMQTracing(TracerProvider tracerProvider,
                         String sdkVersion,
                         String clientId,
                         String serverAddress,
                         int serverPort) {
        TracerProvider provider = (tracerProvider != null)
                ? tracerProvider
                : GlobalOpenTelemetry.getTracerProvider();
        this.tracer = provider.get(
                KubeMQSemconv.INSTRUMENTATION_SCOPE_NAME,
                sdkVersion);
        this.clientId = clientId;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    /**
     * Start a span for a messaging operation.
     *
     * @param spanKind      SpanKind (PRODUCER, CONSUMER, CLIENT, SERVER)
     * @param operationName One of OP_PUBLISH, OP_PROCESS, OP_RECEIVE, OP_SETTLE, OP_SEND
     * @param channel       Destination channel name
     * @param messageId     Message ID (may be null)
     * @param parentContext Parent context (may be null for root spans, or extracted context for linked spans)
     * @return Started Span (caller must end it)
     */
    public Span startSpan(SpanKind spanKind,
                          String operationName,
                          String channel,
                          String messageId,
                          Context parentContext) {
        String spanName = operationName + " " + channel;

        SpanBuilder builder = tracer.spanBuilder(spanName)
                .setSpanKind(spanKind)
                .setAttribute(KubeMQSemconv.MESSAGING_SYSTEM,
                              KubeMQSemconv.MESSAGING_SYSTEM_VALUE)
                .setAttribute(KubeMQSemconv.MESSAGING_OPERATION_NAME, operationName)
                .setAttribute(KubeMQSemconv.MESSAGING_OPERATION_TYPE, operationName)
                .setAttribute(KubeMQSemconv.MESSAGING_DESTINATION_NAME, channel)
                .setAttribute(KubeMQSemconv.MESSAGING_CLIENT_ID, clientId)
                .setAttribute(KubeMQSemconv.SERVER_ADDRESS, serverAddress)
                .setAttribute(KubeMQSemconv.SERVER_PORT, (long) serverPort);

        if (messageId != null) {
            builder.setAttribute(KubeMQSemconv.MESSAGING_MESSAGE_ID, messageId);
        }

        if (parentContext != null) {
            builder.setParent(parentContext);
        }

        return builder.startSpan();
    }

    /**
     * Start a consumer span linked (not parented) to a producer span context.
     * Used for pub/sub consume correlation per GS: "Use span links (not parent-child)."
     */
    public Span startLinkedConsumerSpan(String operationName,
                                        String channel,
                                        String messageId,
                                        Context producerContext) {
        String spanName = operationName + " " + channel;

        SpanBuilder builder = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.CONSUMER)
                .setAttribute(KubeMQSemconv.MESSAGING_SYSTEM,
                              KubeMQSemconv.MESSAGING_SYSTEM_VALUE)
                .setAttribute(KubeMQSemconv.MESSAGING_OPERATION_NAME, operationName)
                .setAttribute(KubeMQSemconv.MESSAGING_OPERATION_TYPE, operationName)
                .setAttribute(KubeMQSemconv.MESSAGING_DESTINATION_NAME, channel)
                .setAttribute(KubeMQSemconv.MESSAGING_CLIENT_ID, clientId)
                .setAttribute(KubeMQSemconv.SERVER_ADDRESS, serverAddress)
                .setAttribute(KubeMQSemconv.SERVER_PORT, (long) serverPort);

        if (messageId != null) {
            builder.setAttribute(KubeMQSemconv.MESSAGING_MESSAGE_ID, messageId);
        }

        // Link to producer span rather than parent
        if (producerContext != null) {
            Span producerSpan = Span.fromContext(producerContext);
            if (producerSpan.getSpanContext().isValid()) {
                builder.addLink(producerSpan.getSpanContext());
            }
        }

        return builder.startSpan();
    }

    /**
     * Start a batch receive span with message count and links to producer spans.
     *
     * @param channel        Channel name
     * @param messageCount   Number of messages in the batch
     * @param producerContexts Producer contexts for linking (capped at 128 per GS)
     */
    public Span startBatchReceiveSpan(String channel,
                                       int messageCount,
                                       java.util.List<Context> producerContexts) {
        String spanName = KubeMQSemconv.OP_RECEIVE + " " + channel;

        SpanBuilder builder = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.CONSUMER)
                .setAttribute(KubeMQSemconv.MESSAGING_SYSTEM,
                              KubeMQSemconv.MESSAGING_SYSTEM_VALUE)
                .setAttribute(KubeMQSemconv.MESSAGING_OPERATION_NAME,
                              KubeMQSemconv.OP_RECEIVE)
                .setAttribute(KubeMQSemconv.MESSAGING_OPERATION_TYPE,
                              KubeMQSemconv.OP_RECEIVE)
                .setAttribute(KubeMQSemconv.MESSAGING_DESTINATION_NAME, channel)
                .setAttribute(KubeMQSemconv.MESSAGING_CLIENT_ID, clientId)
                .setAttribute(KubeMQSemconv.SERVER_ADDRESS, serverAddress)
                .setAttribute(KubeMQSemconv.SERVER_PORT, (long) serverPort)
                .setAttribute(KubeMQSemconv.MESSAGING_BATCH_MESSAGE_COUNT,
                              (long) messageCount);

        // Link to producer spans (cap at 128 per GS)
        if (producerContexts != null) {
            int limit = Math.min(producerContexts.size(), 128);
            for (int i = 0; i < limit; i++) {
                Span producerSpan = Span.fromContext(producerContexts.get(i));
                if (producerSpan.getSpanContext().isValid()) {
                    builder.addLink(producerSpan.getSpanContext());
                }
            }
        }

        return builder.startSpan();
    }

    /**
     * Record a retry attempt as a span event.
     *
     * @param span          Active span
     * @param attempt       Current retry attempt number (1-based)
     * @param delaySeconds  Delay before this attempt in seconds
     * @param errorType     The error.type string that triggered the retry
     *                      (from ErrorClassifier.toOtelErrorType(), see 01-error-handling-spec.md)
     */
    public void recordRetryEvent(Span span, int attempt,
                                  double delaySeconds, String errorType) {
        if (span.isRecording()) {
            span.addEvent(KubeMQSemconv.RETRY_EVENT_NAME, Attributes.of(
                    KubeMQSemconv.RETRY_ATTEMPT, (long) attempt,
                    KubeMQSemconv.RETRY_DELAY_SECONDS, delaySeconds,
                    KubeMQSemconv.ERROR_TYPE, errorType
            ));
        }
    }

    /**
     * Record a DLQ transition as a span event.
     */
    public void recordDlqEvent(Span span) {
        if (span.isRecording()) {
            span.addEvent(KubeMQSemconv.DLQ_EVENT_NAME);
        }
    }

    /**
     * Set error status on a span for a failed operation.
     *
     * @param span  The active span
     * @param error The exception that caused the failure
     * @param errorTypeValue The error.type attribute value
     *                       (from ErrorClassifier.toOtelErrorType())
     */
    public void setError(Span span, Throwable error, String errorTypeValue) {
        if (span.isRecording()) {
            span.setStatus(StatusCode.ERROR, error.getMessage());
            span.setAttribute(KubeMQSemconv.ERROR_TYPE, errorTypeValue);
            span.recordException(error);
        }
    }

    /**
     * Set recommended body size attribute, guarded by isRecording().
     */
    public void setBodySize(Span span, byte[] body) {
        if (span.isRecording() && body != null) {
            span.setAttribute(KubeMQSemconv.MESSAGING_MESSAGE_BODY_SIZE,
                              (long) body.length);
        }
    }

    /**
     * Set consumer group attribute for Events Store subscriptions.
     */
    public void setConsumerGroup(Span span, String groupName) {
        if (span.isRecording() && groupName != null && !groupName.isEmpty()) {
            span.setAttribute(KubeMQSemconv.MESSAGING_CONSUMER_GROUP_NAME,
                              groupName);
        }
    }

    /** Returns the Tracer for advanced use cases. */
    public Tracer getTracer() {
        return tracer;
    }
}
```

#### 4.3.5 Instrumentation Points

Each operation in the SDK is instrumented as follows. The actual code lives in the protocol/interceptor layer (per 07-code-quality-spec.md REQ-CQ-1). This table maps operations to source files that need modification:

| Operation | Source File | Method | Span Kind | Notes |
|-----------|------------|--------|-----------|-------|
| Publish Event | `PubSubClient.java` `sendEventsMessage()` | `startSpan(PRODUCER, "publish", ...)` | PRODUCER | Inject trace context into tags before gRPC call |
| Publish Event (stream) | `EventStreamHelper.java` `sendMessage()` | `startSpan(PRODUCER, "publish", ...)` | PRODUCER | Per-message span within stream |
| Publish Event Store | `PubSubClient.java` `sendEventStoreMessage()` | `startSpan(PRODUCER, "publish", ...)` | PRODUCER | Inject trace context into tags |
| Subscribe Events callback | `EventsSubscription.java` `onNext()` | `startLinkedConsumerSpan("process", ...)` | CONSUMER | Extract context from message tags; link to producer |
| Subscribe Events Store callback | `EventsStoreSubscription.java` `onNext()` | `startLinkedConsumerSpan("process", ...)` | CONSUMER | Extract context; link to producer; set consumer group |
| Queue Send | `QueuesClient.java` `sendQueuesMessage()` | `startSpan(PRODUCER, "publish", ...)` | PRODUCER | Inject trace context into tags |
| Queue Receive (poll) | `QueuesPollResponse.java` | `startBatchReceiveSpan(...)` then per-message `startLinkedConsumerSpan("process", ...)` | CONSUMER | Batch pattern |
| Queue Ack | `QueueMessageReceived.java` `ack()` | `startSpan(CONSUMER, "settle", ...)` | CONSUMER | Child of process span |
| Queue Reject | `QueueMessageReceived.java` `reject()` | `startSpan(CONSUMER, "settle", ...)` | CONSUMER | Child of process span |
| Queue Requeue | `QueueMessageReceived.java` `reQueue()` | `startSpan(CONSUMER, "settle", ...)` | CONSUMER | Preserve trace context in tags |
| Command Send | `CQClient.java` `sendCommandRequest()` | `startSpan(CLIENT, "send", ...)` | CLIENT | Inject trace context |
| Query Send | `CQClient.java` `sendQueryRequest()` | `startSpan(CLIENT, "send", ...)` | CLIENT | Inject trace context |
| Command Response | `CommandsSubscription.java` `onNext()` | `startLinkedConsumerSpan("process", ...)` + SERVER kind | SERVER | Extract context; link to sender |
| Query Response | `QueriesSubscription.java` `onNext()` | `startLinkedConsumerSpan("process", ...)` + SERVER kind | SERVER | Extract context; link to sender |
| Queue Upstream (stream) | `QueueUpstreamHandler.java` `send()` | `startSpan(PRODUCER, "publish", ...)` | PRODUCER | Per-message span within stream |
| Queue Downstream (stream) | `QueueDownstreamHandler.java` `onNext()` | Per-message `startLinkedConsumerSpan("process", ...)` | CONSUMER | Stream-level receive span, per-message process spans |

#### 4.3.6 Span Lifecycle Pattern

Every instrumented operation follows this pattern:

```java
// Pseudocode for a publish operation
Span span = tracing.startSpan(SpanKind.PRODUCER, OP_PUBLISH, channel, messageId, null);
try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
    // Inject trace context into message tags (REQ-OBS-2)
    propagator.inject(Context.current(), message.getTags(), KubeMQTagsCarrier.SETTER);

    // Set body size (guarded by isRecording)
    tracing.setBodySize(span, message.getBody());

    // Execute the actual operation
    result = transport.publish(message);

} catch (KubeMQException e) {
    tracing.setError(span, e, ErrorClassifier.toOtelErrorType(e));
    throw e;
} finally {
    span.end();
}
```

#### 4.3.7 Retry Event Integration

The retry executor (from 01-error-handling-spec.md REQ-ERR-3) calls `KubeMQTracing.recordRetryEvent()` on each retry attempt:

```java
// Inside RetryExecutor (see 01-error-handling-spec.md section 6.3)
Span currentSpan = Span.current();
tracing.recordRetryEvent(
    currentSpan,
    attempt,                                    // 1-based attempt number
    actualDelayMs / 1000.0,                     // delay in seconds
    ErrorClassifier.toOtelErrorType(lastError)   // error.type from 01-spec
);
```

#### 4.3.8 SDK Version Resolution

The instrumentation scope version must match the SDK version. Load from Maven artifact metadata:

```java
package io.kubemq.sdk.observability;

import java.io.InputStream;
import java.util.Properties;

final class SdkVersion {
    private static final String VERSION;

    static {
        String v = "unknown";
        try (InputStream is = SdkVersion.class.getResourceAsStream(
                "/META-INF/maven/io.kubemq/kubemq-java-sdk/pom.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                v = props.getProperty("version", "unknown");
            }
        } catch (Exception ignored) {}
        VERSION = v;
    }

    static String get() { return VERSION; }

    private SdkVersion() {}
}
```

#### 4.3.9 Files Created / Changed

| File | Action | Description |
|------|--------|-------------|
| `kubemq-java/src/main/java/io/kubemq/sdk/observability/KubeMQSemconv.java` | NEW | Semantic convention constants |
| `kubemq-java/src/main/java/io/kubemq/sdk/observability/KubeMQTracing.java` | NEW | Centralized span creation |
| `kubemq-java/src/main/java/io/kubemq/sdk/observability/SdkVersion.java` | NEW | Version resolution |
| `kubemq-java/pom.xml` | MODIFY | Add opentelemetry-api (provided), opentelemetry-bom |
| All client/handler/subscription classes (15 files) | MODIFY | Add span creation at each instrumentation point |

---

## 5. REQ-OBS-2: W3C Trace Context Propagation

**Gap Status:** MISSING | **Priority:** P0 | **Effort:** L (3-5 days)

### 5.1 Current State

**Assessment Evidence:** 7.3.1: "No W3C Trace Context propagation. Tags map could carry trace headers but no built-in support."

All KubeMQ message types have a `Map<String, String> tags` field that can carry `traceparent` and `tracestate` headers. No injection or extraction code exists.

### 5.2 Acceptance Criteria Mapping

| # | GS Acceptance Criterion | Current Status | Implementation Required |
|---|------------------------|----------------|------------------------|
| AC-1 | `traceparent`/`tracestate` injected into published messages | MISSING | Add inject at all publish points |
| AC-2 | Consumers extract context and create linked spans | MISSING | Add extract at all consume points |
| AC-3 | Trace context survives round-trip | MISSING | End-to-end integration test |
| AC-4 | Batch publishes inject per-message context | MISSING | Per-message injection in queue send batch |
| AC-5 | Missing context handled gracefully | MISSING | Graceful fallback (no error) |
| AC-6 | Trace context preserved through requeue/DLQ | MISSING | Preserve tags on requeue/DLQ |

### 5.3 Specification

#### 5.3.1 `KubeMQTagsCarrier` -- TextMap Carrier

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/observability/KubeMQTagsCarrier.java` (NEW)

```java
package io.kubemq.sdk.observability;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.Map;

/**
 * OTel TextMap carrier over KubeMQ message tags (Map&lt;String, String&gt;).
 * <p>
 * Injects/extracts W3C Trace Context (traceparent, tracestate) into message tags.
 */
public final class KubeMQTagsCarrier {

    private KubeMQTagsCarrier() {}

    /**
     * Getter for extracting trace context from message tags.
     */
    public static final TextMapGetter<Map<String, String>> GETTER =
            new TextMapGetter<Map<String, String>>() {
                @Override
                public Iterable<String> keys(Map<String, String> carrier) {
                    return carrier.keySet();
                }

                @Override
                public String get(Map<String, String> carrier, String key) {
                    return carrier != null ? carrier.get(key) : null;
                }
            };

    /**
     * Setter for injecting trace context into message tags.
     */
    public static final TextMapSetter<Map<String, String>> SETTER =
            (carrier, key, value) -> {
                if (carrier != null) {
                    carrier.put(key, value);
                }
            };
}
```

#### 5.3.2 Injection (Producer Side)

At every publish/send operation, inject trace context into the message's tags before sending:

```java
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;

// Inside the span scope (after startSpan, before gRPC call):
TextMapPropagator propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();

// Ensure tags map is mutable
Map<String, String> tags = message.getTags();
if (tags == null) {
    tags = new HashMap<>();
    message.setTags(tags);
}

propagator.inject(Context.current(), tags, KubeMQTagsCarrier.SETTER);
```

This injects `traceparent` and `tracestate` (if configured) as entries in the tags map.

#### 5.3.3 Extraction (Consumer Side)

At every consume/receive callback, extract trace context from the message's tags:

```java
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;

// Extract producer's trace context from message tags
Map<String, String> tags = receivedMessage.getTags();
Context extractedContext = Context.root(); // default if no context present
if (tags != null && !tags.isEmpty()) {
    TextMapPropagator propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
    extractedContext = propagator.extract(Context.current(), tags, KubeMQTagsCarrier.GETTER);
}

// Create consumer span linked (not parented) to producer
Span processSpan = tracing.startLinkedConsumerSpan(
    KubeMQSemconv.OP_PROCESS, channel, messageId, extractedContext);
```

**Graceful handling of missing context (AC-5):** When tags have no `traceparent`, `propagator.extract()` returns the current context unchanged. The consumer span is created without a link. No error or warning is emitted.

#### 5.3.4 Queue Stream Downstream Trace Shape

Per GS REQ-OBS-2, queue stream downstream follows this pattern:

```
1. Stream-level span: receive {channel} (SpanKind.CONSUMER)
   - messaging.batch.message_count set on completion

2. Per-message spans: process {channel} (SpanKind.CONSUMER)
   - Linked (not parented) to original producer span
   - Extract trace context independently from each message's tags
   - Never inherit trace context from the stream span

3. Settle spans: settle {channel} (SpanKind.CONSUMER)
   - Child of the corresponding process span
```

Implementation in `QueueDownstreamHandler`:

```java
// Stream opened -- create stream-level receive span
Span receiveSpan = tracing.startSpan(SpanKind.CONSUMER, OP_RECEIVE, channel, null, null);

// On each message received:
void onMessage(QueueMessageReceived msg) {
    // Extract trace context from THIS message's tags (independent extraction)
    Context producerCtx = extractContext(msg.getTags());

    // Create process span linked to producer (not child of receive span)
    Span processSpan = tracing.startLinkedConsumerSpan(
        OP_PROCESS, channel, msg.getId(), producerCtx);

    // Store process span on the message for settle operations
    msg.setProcessSpan(processSpan);

    // Deliver to user callback
    callback.onMessage(msg);
}

// On ack/reject/requeue:
void settle(QueueMessageReceived msg, String settleType) {
    Span processSpan = msg.getProcessSpan();
    Context parentCtx = processSpan != null ? Context.current().with(processSpan) : null;
    Span settleSpan = tracing.startSpan(SpanKind.CONSUMER, OP_SETTLE, channel, msg.getId(), parentCtx);
    try {
        // Send settle to server
    } finally {
        settleSpan.end();
    }
}
```

#### 5.3.5 RPC Round-Trip Trace Shape (Commands/Queries)

Per GS: `sender.request -> [broker] -> responder.process -> [broker] -> sender.response`

**Sender side (CQClient):**
```java
// 1. Create CLIENT span
Span sendSpan = tracing.startSpan(SpanKind.CLIENT, OP_SEND, channel, requestId, null);
try (Scope scope = sendSpan.makeCurrent()) {
    // 2. Inject trace context into command/query tags
    propagator.inject(Context.current(), request.getTags(), KubeMQTagsCarrier.SETTER);

    // 3. Send and wait for response
    Response response = transport.sendRequest(request);

    // 4. Response arrives (trace context from responder preserved in response tags)
} finally {
    sendSpan.end();
}
```

**Responder side (CommandsSubscription / QueriesSubscription):**
```java
// 1. Extract sender's trace context
Context senderCtx = extractContext(receivedCommand.getTags());

// 2. Create SERVER span linked to sender
Span processSpan = tracing.startSpan(SpanKind.SERVER, OP_PROCESS, channel,
    receivedCommand.getId(), null);
// Add link to sender span
SpanContext senderSpanCtx = Span.fromContext(senderCtx).getSpanContext();
if (senderSpanCtx.isValid()) {
    // Note: link must be added at span creation time via SpanBuilder
    // so processSpan creation needs the link upfront
}

// 3. User processes and creates response
// 4. Inject responder's trace context into response (if response has tags)
```

#### 5.3.6 Requeue and DLQ Trace Context Preservation

When a message is requeued or moved to DLQ:
1. Preserve the original `traceparent`/`tracestate` in the tags -- do not overwrite
2. Record a span event `message.dead_lettered` (for DLQ transitions) on the current settle span
3. The next consumer of the requeued/DLQ'd message will extract the original producer's trace context

```java
// In QueueMessageReceived.reQueue():
// Tags already contain traceparent/tracestate from the original producer
// Do NOT re-inject -- preserve the original context
Span settleSpan = tracing.startSpan(SpanKind.CONSUMER, OP_SETTLE, channel, messageId, processContext);
// ... send requeue to server ...
settleSpan.end();

// In DLQ transition (when reject with maxReceiveCount exceeded):
tracing.recordDlqEvent(settleSpan);
// Tags preserved in DLQ message for downstream consumer
```

#### 5.3.7 Files Created / Changed

| File | Action | Description |
|------|--------|-------------|
| `kubemq-java/src/main/java/io/kubemq/sdk/observability/KubeMQTagsCarrier.java` | NEW | TextMap carrier over message tags |
| `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/PubSubClient.java` | MODIFY | Add inject on publish |
| `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventStreamHelper.java` | MODIFY | Add inject on stream publish |
| `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsSubscription.java` | MODIFY | Add extract on consume |
| `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/EventsStoreSubscription.java` | MODIFY | Add extract on consume |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueuesClient.java` | MODIFY | Add inject on queue send |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueUpstreamHandler.java` | MODIFY | Add inject on stream send |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueDownstreamHandler.java` | MODIFY | Add extract on stream receive |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueueMessageReceived.java` | MODIFY | Preserve context on requeue/DLQ; settle spans |
| `kubemq-java/src/main/java/io/kubemq/sdk/cq/CQClient.java` | MODIFY | Add inject on command/query send |
| `kubemq-java/src/main/java/io/kubemq/sdk/cq/CommandsSubscription.java` | MODIFY | Add extract on command receive |
| `kubemq-java/src/main/java/io/kubemq/sdk/cq/QueriesSubscription.java` | MODIFY | Add extract on query receive |

---

## 6. REQ-OBS-3: OpenTelemetry Metrics

**Gap Status:** MISSING | **Priority:** P0 | **Effort:** L (3-5 days)

### 6.1 Current State

**Assessment Evidence:** 7.2.1-7.2.3: "No metrics infrastructure. No hooks, callbacks, or interfaces. No Prometheus or OpenTelemetry integration."

Zero metrics exist in the codebase.

### 6.2 Acceptance Criteria Mapping

| # | GS Acceptance Criterion | Current Status | Implementation Required |
|---|------------------------|----------------|------------------------|
| AC-1 | All 7 required metrics emitted | MISSING | Implement all metrics |
| AC-2 | Correct instrument types | MISSING | Histogram, Counter, UpDownCounter |
| AC-3 | Metric names follow OTel conventions | MISSING | Use standard names |
| AC-4 | Required attributes on metrics | MISSING | system, operation.name, destination.name, error.type |
| AC-5 | Duration histograms use specified buckets | MISSING | Configure explicit boundaries |
| AC-6 | Cardinality management implemented | MISSING | Threshold, allowlist, WARN log |
| AC-7 | `error.type` value mapping from REQ-ERR-2 categories | MISSING | Integration with `ErrorClassifier.toOtelErrorType()` (01-spec) |
| AC-8 | Meter instrumentation scope = `io.kubemq.sdk`; version = SDK version | MISSING | Configure Meter with correct scope |

### 6.3 Specification

#### 6.3.1 Required Metrics Table

| Metric Name | Instrument | Unit | Java Type | Description |
|-------------|-----------|------|-----------|-------------|
| `messaging.client.operation.duration` | Histogram | seconds | `DoubleHistogram` | Duration of each messaging operation |
| `messaging.client.sent.messages` | Counter | `{message}` | `LongCounter` | Total messages sent (publish) |
| `messaging.client.consumed.messages` | Counter | `{message}` | `LongCounter` | Total messages consumed (process/receive) |
| `messaging.client.connection.count` | UpDownCounter | `{connection}` | `LongUpDownCounter` | Active connections (increments on connect, decrements on disconnect) |
| `messaging.client.reconnections` | Counter | `{attempt}` | `LongCounter` | Reconnection attempts (each entry to RECONNECTING state per REQ-CONN-2) |
| `kubemq.client.retry.attempts` | Counter | `{attempt}` | `LongCounter` | Retry attempts (per REQ-ERR-3) |
| `kubemq.client.retry.exhausted` | Counter | `{attempt}` | `LongCounter` | Retries exhausted (all attempts failed) |

#### 6.3.2 Required Metric Attributes

| Attribute | Applied To | Value Source |
|-----------|-----------|-------------|
| `messaging.system` | All metrics | Constant `"kubemq"` |
| `messaging.operation.name` | `operation.duration`, `sent.messages`, `consumed.messages`, `retry.attempts`, `retry.exhausted` | Operation name from span (publish, process, receive, settle, send) |
| `messaging.destination.name` | All metrics (when below cardinality threshold) | Channel name |
| `error.type` | `operation.duration` (on failure), `retry.attempts`, `retry.exhausted` | From `ErrorClassifier.toOtelErrorType()` |

#### 6.3.3 `error.type` Value Mapping

Cross-reference with `ErrorCategory` from `01-error-handling-spec.md` section 4.2.1:

| `ErrorCategory` (from 01-spec) | `error.type` OTel Attribute Value |
|-------------------------------|----------------------------------|
| `TRANSIENT` | `transient` |
| `TIMEOUT` | `timeout` |
| `THROTTLING` | `throttling` |
| `AUTHENTICATION` | `authentication` |
| `AUTHORIZATION` | `authorization` |
| `VALIDATION` | `validation` |
| `NOT_FOUND` | `not_found` |
| `FATAL` | `fatal` |
| `CANCELLATION` | `cancellation` |
| `BACKPRESSURE` | `backpressure` |

The mapping is implemented by `ErrorClassifier.toOtelErrorType(KubeMQException ex)` (see 01-error-handling-spec.md section 4.2.3), which returns `ex.getCategory().name().toLowerCase()`.

#### 6.3.4 Histogram Bucket Boundaries

Per GS: `[0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1, 2.5, 5, 7.5, 10, 30, 60]` seconds

These are tuned for messaging latency distributions -- from sub-millisecond local operations to 60-second timeouts.

#### 6.3.5 `KubeMQMetrics` Class

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/observability/KubeMQMetrics.java` (NEW)

```java
package io.kubemq.sdk.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.*;

import java.util.List;

/**
 * Centralized metrics instrumentation for the KubeMQ SDK.
 * <p>
 * When no OTel SDK is registered, all instruments are no-ops with near-zero overhead.
 */
public final class KubeMQMetrics {

    private final DoubleHistogram operationDuration;
    private final LongCounter sentMessages;
    private final LongCounter consumedMessages;
    private final LongUpDownCounter connectionCount;
    private final LongCounter reconnections;
    private final LongCounter retryAttempts;
    private final LongCounter retryExhausted;

    private final CardinalityManager cardinalityManager;

    // Review R1 m-5: List.of() requires Java 9+. SDK minimum is Java 11, so this is intentional.
    // If Java 8 support is ever needed, change to Arrays.asList() or Collections.unmodifiableList().
    private static final List<Double> HISTOGRAM_BOUNDARIES = List.of(
            0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.075, 0.1,
            0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0, 30.0, 60.0
    );

    /**
     * @param meterProvider  If null, falls back to GlobalOpenTelemetry.getMeterProvider()
     * @param sdkVersion     SDK version for instrumentation scope
     * @param cardinalityConfig Cardinality management configuration
     */
    public KubeMQMetrics(MeterProvider meterProvider,
                         String sdkVersion,
                         CardinalityConfig cardinalityConfig) {
        MeterProvider provider = (meterProvider != null)
                ? meterProvider
                : GlobalOpenTelemetry.getMeterProvider();

        Meter meter = provider.get(
                KubeMQSemconv.INSTRUMENTATION_SCOPE_NAME,
                sdkVersion);

        this.operationDuration = meter.histogramBuilder("messaging.client.operation.duration")
                .setUnit("s")
                .setDescription("Duration of each messaging operation")
                .setExplicitBucketBoundariesAdvice(HISTOGRAM_BOUNDARIES)
                .build();

        this.sentMessages = meter.counterBuilder("messaging.client.sent.messages")
                .setUnit("{message}")
                .setDescription("Total messages sent")
                .build();

        this.consumedMessages = meter.counterBuilder("messaging.client.consumed.messages")
                .setUnit("{message}")
                .setDescription("Total messages consumed")
                .build();

        this.connectionCount = meter.upDownCounterBuilder("messaging.client.connection.count")
                .setUnit("{connection}")
                .setDescription("Active connections")
                .build();

        this.reconnections = meter.counterBuilder("messaging.client.reconnections")
                .setUnit("{attempt}")
                .setDescription("Reconnection attempts")
                .build();

        this.retryAttempts = meter.counterBuilder("kubemq.client.retry.attempts")
                .setUnit("{attempt}")
                .setDescription("Retry attempts")
                .build();

        this.retryExhausted = meter.counterBuilder("kubemq.client.retry.exhausted")
                .setUnit("{attempt}")
                .setDescription("Retries exhausted")
                .build();

        this.cardinalityManager = new CardinalityManager(
                cardinalityConfig != null ? cardinalityConfig : CardinalityConfig.defaults());
    }

    // ---- Recording methods ----

    /**
     * Record operation duration.
     * @param durationSeconds Duration in seconds
     * @param operationName   Operation name (publish, process, receive, settle, send)
     * @param channel         Channel name
     * @param errorType       Error type string (null if success)
     */
    public void recordOperationDuration(double durationSeconds,
                                         String operationName,
                                         String channel,
                                         String errorType) {
        AttributesBuilder attrs = Attributes.builder()
                .put(KubeMQSemconv.MESSAGING_SYSTEM, KubeMQSemconv.MESSAGING_SYSTEM_VALUE)
                .put(KubeMQSemconv.MESSAGING_OPERATION_NAME, operationName);

        addChannelAttribute(attrs, channel);

        if (errorType != null) {
            attrs.put(KubeMQSemconv.ERROR_TYPE, errorType);
        }

        operationDuration.record(durationSeconds, attrs.build());
    }

    /**
     * Increment sent messages counter.
     */
    public void recordSentMessage(String operationName, String channel) {
        AttributesBuilder attrs = Attributes.builder()
                .put(KubeMQSemconv.MESSAGING_SYSTEM, KubeMQSemconv.MESSAGING_SYSTEM_VALUE)
                .put(KubeMQSemconv.MESSAGING_OPERATION_NAME, operationName);
        addChannelAttribute(attrs, channel);
        sentMessages.add(1, attrs.build());
    }

    /**
     * Increment consumed messages counter.
     */
    public void recordConsumedMessage(String operationName, String channel) {
        AttributesBuilder attrs = Attributes.builder()
                .put(KubeMQSemconv.MESSAGING_SYSTEM, KubeMQSemconv.MESSAGING_SYSTEM_VALUE)
                .put(KubeMQSemconv.MESSAGING_OPERATION_NAME, operationName);
        addChannelAttribute(attrs, channel);
        consumedMessages.add(1, attrs.build());
    }

    /**
     * Increment connection count (call on CONNECTED state).
     */
    public void recordConnectionOpened() {
        connectionCount.add(1, Attributes.of(
                KubeMQSemconv.MESSAGING_SYSTEM, KubeMQSemconv.MESSAGING_SYSTEM_VALUE));
    }

    /**
     * Decrement connection count (call on CLOSED/DISCONNECTED state).
     */
    public void recordConnectionClosed() {
        connectionCount.add(-1, Attributes.of(
                KubeMQSemconv.MESSAGING_SYSTEM, KubeMQSemconv.MESSAGING_SYSTEM_VALUE));
    }

    /**
     * Increment reconnection attempts counter.
     * Called on each entry to RECONNECTING state (per REQ-CONN-2 from 02-spec).
     */
    public void recordReconnectionAttempt() {
        reconnections.add(1, Attributes.of(
                KubeMQSemconv.MESSAGING_SYSTEM, KubeMQSemconv.MESSAGING_SYSTEM_VALUE));
    }

    /**
     * Increment retry attempts counter.
     * @param operationName Operation being retried
     * @param errorType     Error type that triggered the retry
     */
    public void recordRetryAttempt(String operationName, String errorType) {
        retryAttempts.add(1, Attributes.of(
                KubeMQSemconv.MESSAGING_SYSTEM, KubeMQSemconv.MESSAGING_SYSTEM_VALUE,
                KubeMQSemconv.MESSAGING_OPERATION_NAME, operationName,
                KubeMQSemconv.ERROR_TYPE, errorType));
    }

    /**
     * Increment retry exhausted counter.
     * @param operationName Operation that exhausted retries
     * @param errorType     Final error type
     */
    public void recordRetryExhausted(String operationName, String errorType) {
        retryExhausted.add(1, Attributes.of(
                KubeMQSemconv.MESSAGING_SYSTEM, KubeMQSemconv.MESSAGING_SYSTEM_VALUE,
                KubeMQSemconv.MESSAGING_OPERATION_NAME, operationName,
                KubeMQSemconv.ERROR_TYPE, errorType));
    }

    // ---- Cardinality management ----

    private void addChannelAttribute(AttributesBuilder attrs, String channel) {
        if (channel != null && cardinalityManager.shouldIncludeChannel(channel)) {
            attrs.put(KubeMQSemconv.MESSAGING_DESTINATION_NAME, channel);
        }
    }
}
```

#### 6.3.6 Cardinality Management

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/observability/CardinalityConfig.java` (NEW)

```java
package io.kubemq.sdk.observability;

import java.util.Collections;
import java.util.Set;

/**
 * Configuration for metric cardinality management.
 */
public final class CardinalityConfig {

    private final int maxChannelCardinality;
    private final Set<String> channelAllowlist;

    public CardinalityConfig(int maxChannelCardinality, Set<String> channelAllowlist) {
        this.maxChannelCardinality = maxChannelCardinality;
        this.channelAllowlist = channelAllowlist != null
                ? Collections.unmodifiableSet(channelAllowlist)
                : Collections.emptySet();
    }

    public static CardinalityConfig defaults() {
        return new CardinalityConfig(100, Collections.emptySet());
    }

    public int getMaxChannelCardinality() { return maxChannelCardinality; }
    public Set<String> getChannelAllowlist() { return channelAllowlist; }
}
```

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/observability/CardinalityManager.java` (NEW)

```java
package io.kubemq.sdk.observability;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages metric cardinality for messaging.destination.name attribute.
 * <p>
 * When unique channel count exceeds the configured threshold:
 * 1. Emits a WARN log (once)
 * 2. Omits messaging.destination.name from new metric series
 * 3. Channels in the allowlist are always included regardless of threshold
 * <p>
 * Users can additionally use OTel Metric Views for server-side cardinality control.
 */
final class CardinalityManager {

    private final CardinalityConfig config;
    private final ConcurrentHashMap.KeySetView<String, Boolean> observedChannels =
            ConcurrentHashMap.newKeySet();
    private final AtomicBoolean thresholdWarningEmitted = new AtomicBoolean(false);

    // Logger injected or resolved via KubeMQLoggerFactory
    private final KubeMQLogger logger;

    CardinalityManager(CardinalityConfig config) {
        this.config = config;
        this.logger = io.kubemq.sdk.observability.KubeMQLoggerFactory.getLogger(
                "io.kubemq.sdk.observability.CardinalityManager");
    }

    /**
     * Returns true if the channel should be included as a metric attribute.
     */
    boolean shouldIncludeChannel(String channel) {
        // Allowlist always passes
        if (config.getChannelAllowlist().contains(channel)) {
            return true;
        }

        // Already known channel
        if (observedChannels.contains(channel)) {
            return true;
        }

        // Check threshold
        if (observedChannels.size() >= config.getMaxChannelCardinality()) {
            if (thresholdWarningEmitted.compareAndSet(false, true)) {
                logger.warn("Metric cardinality threshold exceeded",
                        "threshold", config.getMaxChannelCardinality(),
                        "action", "omitting messaging.destination.name for new channels",
                        "hint", "Configure channelAllowlist or increase maxChannelCardinality");
            }
            return false;
        }

        // Register new channel
        observedChannels.add(channel);
        return true;
    }
}
```

#### 6.3.7 Integration with Connection State Machine

The `messaging.client.connection.count` and `messaging.client.reconnections` metrics integrate with the connection state machine from `02-connection-transport-spec.md` REQ-CONN-2:

```java
// In the state machine callback (ConnectionStateListener from 02-spec):
void onStateChange(ConnectionState oldState, ConnectionState newState) {
    switch (newState) {
        case CONNECTED:
            metrics.recordConnectionOpened();
            break;
        case RECONNECTING:
            metrics.recordReconnectionAttempt();
            break;
        case CLOSED:
            metrics.recordConnectionClosed();
            break;
    }
}
```

#### 6.3.8 Integration with Retry Executor

The retry metrics integrate with the retry executor from `01-error-handling-spec.md` REQ-ERR-3:

```java
// In RetryExecutor (01-spec section 6.3), on each retry attempt:
metrics.recordRetryAttempt(operationName, ErrorClassifier.toOtelErrorType(lastError));

// When retries exhausted:
metrics.recordRetryExhausted(operationName, ErrorClassifier.toOtelErrorType(lastError));
```

#### 6.3.9 Client Builder Integration

Add to `KubeMQClient` builder:

```java
// Optional MeterProvider injection
private MeterProvider meterProvider;

public B meterProvider(MeterProvider meterProvider) {
    this.meterProvider = meterProvider;
    return self();
}

// Optional cardinality configuration
private CardinalityConfig cardinalityConfig;

public B cardinalityConfig(CardinalityConfig cardinalityConfig) {
    this.cardinalityConfig = cardinalityConfig;
    return self();
}
```

#### 6.3.10 Files Created / Changed

| File | Action | Description |
|------|--------|-------------|
| `kubemq-java/src/main/java/io/kubemq/sdk/observability/KubeMQMetrics.java` | NEW | Centralized metrics |
| `kubemq-java/src/main/java/io/kubemq/sdk/observability/CardinalityConfig.java` | NEW | Cardinality config |
| `kubemq-java/src/main/java/io/kubemq/sdk/observability/CardinalityManager.java` | NEW | Cardinality threshold logic |
| `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java` | MODIFY | Add meterProvider and cardinalityConfig to builder |
| All client/handler classes | MODIFY | Add metric recording at operation points |

---

## 7. REQ-OBS-4: Near-Zero Cost When Not Configured

**Gap Status:** MISSING | **Priority:** P0 | **Effort:** M (covered by OBS-1/2/3 architecture)

### 7.1 Current State

No OTel integration exists. This requirement defines the architectural constraints that OBS-1/2/3 must follow.

### 7.2 Acceptance Criteria Mapping

| # | GS Acceptance Criterion | Current Status | Implementation Required |
|---|------------------------|----------------|------------------------|
| AC-1 | OTel API is only observability dependency | MISSING | `provided` scope in pom.xml |
| AC-2 | No-op provider when OTel SDK not registered | MISSING | Architectural pattern |
| AC-3 | TracerProvider/MeterProvider injectable via options | MISSING | Builder methods |
| AC-4 | Guard expensive computation with `span.isRecording()` | MISSING | Applied throughout OBS-1 code |
| AC-5 | OTel documented with complete setup example | MISSING | README + example module |
| AC-6 | Less than 1% latency overhead with no-op at p99 | MISSING | Verification benchmark |

### 7.3 Specification

#### 7.3.1 Dependency Scope

The `opentelemetry-api` dependency is `provided` scope in Maven:

```xml
<scope>provided</scope>
```

This means:
- Available at compile time for type references
- NOT included in the SDK's transitive dependencies
- Users who want OTel must add their own OTel SDK dependency
- When no OTel SDK is on the classpath, `GlobalOpenTelemetry.getTracerProvider()` returns `TracerProvider.noop()` and `GlobalOpenTelemetry.getMeterProvider()` returns `MeterProvider.noop()`
- All Tracer/Meter instruments returned by no-op providers are no-ops themselves

#### 7.3.2 OTel API Availability Detection

Because `opentelemetry-api` is `provided` scope, it may not be on the classpath at runtime. The SDK must detect this and avoid ClassNotFoundException:

```java
package io.kubemq.sdk.observability;

/**
 * Detects whether OpenTelemetry API is available at runtime.
 */
final class OTelAvailability {

    private static final boolean AVAILABLE;

    static {
        boolean available;
        try {
            Class.forName("io.opentelemetry.api.trace.Tracer",
                          false, OTelAvailability.class.getClassLoader());
            available = true;
        } catch (ClassNotFoundException e) {
            available = false;
        }
        AVAILABLE = available;
    }

    static boolean isAvailable() { return AVAILABLE; }

    private OTelAvailability() {}
}
```

When OTel API is not available:
- `KubeMQTracing` is replaced with a `NoOpTracing` implementation that creates no spans
- `KubeMQMetrics` is replaced with a `NoOpMetrics` implementation that records nothing
- `KubeMQTagsCarrier` inject/extract operations are skipped

> **IMPORTANT -- Proxy/Lazy-Loading Pattern (Review R1 C-1):**
> Because `opentelemetry-api` is `provided` scope, all classes that directly import OTel types
> (`KubeMQTracing`, `KubeMQMetrics`, `KubeMQSemconv`, `KubeMQTagsCarrier`) will throw
> `NoClassDefFoundError` if loaded by the JVM when OTel is not on the classpath. This is a
> Java-specific concern that does not exist in Go.
>
> **Solution:** Client code must NEVER directly reference these classes. Instead, use a proxy
> pattern with lazy loading:
>
> 1. Define interfaces `Tracing` and `Metrics` in the `observability` package with no OTel imports.
> 2. `KubeMQTracing` implements `Tracing`; `NoOpTracing` implements `Tracing` (no OTel imports).
> 3. `KubeMQMetrics` implements `Metrics`; `NoOpMetrics` implements `Metrics` (no OTel imports).
> 4. At client construction time, check `OTelAvailability.isAvailable()`:
>    - If true: load `KubeMQTracing` and `KubeMQMetrics` via reflection or a factory method
>      inside a try-catch for `NoClassDefFoundError`.
>    - If false: use `NoOpTracing` and `NoOpMetrics` directly.
> 5. All client code references only the `Tracing` and `Metrics` interfaces, never the OTel-importing classes.
> 6. `KubeMQSemconv` must ONLY be loaded within OTel-available code paths (inside `KubeMQTracing`/`KubeMQMetrics`),
>    never from client classes.
>
> **Factory pattern:**
> ```java
> // In observability package -- no OTel imports
> public final class TracingFactory {
>     public static Tracing create(boolean otelAvailable, /* params */) {
>         if (!otelAvailable) {
>             return NoOpTracing.INSTANCE;
>         }
>         try {
>             return (Tracing) Class.forName("io.kubemq.sdk.observability.KubeMQTracing")
>                 .getConstructor(/* param types */)
>                 .newInstance(/* params */);
>         } catch (Exception | NoClassDefFoundError e) {
>             return NoOpTracing.INSTANCE;
>         }
>     }
> }
> ```
>
> The same pattern applies to `MetricsFactory` for `KubeMQMetrics`/`NoOpMetrics`.
>
> This adds an estimated 2-3 days to the implementation effort.

#### 7.3.3 Injectable Providers

The client builder accepts optional `TracerProvider` and `MeterProvider`:

```java
// In KubeMQClient builder (or subclass builders):
private TracerProvider tracerProvider;   // null = use GlobalOpenTelemetry
private MeterProvider meterProvider;     // null = use GlobalOpenTelemetry

public B tracerProvider(TracerProvider tracerProvider) {
    this.tracerProvider = tracerProvider;
    return self();
}

public B meterProvider(MeterProvider meterProvider) {
    this.meterProvider = meterProvider;
    return self();
}
```

Fallback chain:
1. User-provided `TracerProvider` / `MeterProvider` (highest priority)
2. `GlobalOpenTelemetry.getTracerProvider()` / `GlobalOpenTelemetry.getMeterProvider()` (auto-configured)
3. No-op (when no OTel SDK is registered -- this is the default behavior of GlobalOpenTelemetry)

#### 7.3.4 `isRecording()` Guards

All expensive attribute computation must be guarded by `span.isRecording()`:

```java
// CORRECT: guarded -- zero cost with no-op
if (span.isRecording()) {
    span.setAttribute(MESSAGING_MESSAGE_BODY_SIZE, (long) body.length);
}

// INCORRECT: unguarded -- allocates Attributes even with no-op
span.setAttribute(MESSAGING_MESSAGE_BODY_SIZE, (long) body.length);
```

The `KubeMQTracing` helper methods (section 4.3.4) include `isRecording()` guards internally, so callers using the helper are safe by default.

Operations that must be guarded:
- `message.getBody().length` (body size computation)
- `String.valueOf(...)` for attribute values computed from complex objects
- `Attributes.of(...)` for retry events with multiple attributes
- Any tag serialization or inspection for logging purposes

#### 7.3.5 Documentation Requirements

1. **README section:** "OpenTelemetry Integration" with:
   - Minimum supported OTel API version (1.32.0)
   - Maven dependency snippet for adding OTel SDK
   - Note that OTel API major version bumps are treated as SDK breaking changes
2. **Example in examples module:** Complete setup showing:
   - Adding `opentelemetry-sdk`, `opentelemetry-exporter-otlp` dependencies
   - Configuring `SdkTracerProvider` and `SdkMeterProvider`
   - Injecting via client builder or via `GlobalOpenTelemetry.set()`
   - Viewing traces in Jaeger / metrics in Grafana
3. **Per-channel trace filtering:** Document that users can configure a custom OTel Sampler on their TracerProvider to filter traces per channel
4. **OTel Metric Views:** Document how users can use OTel Metric Views for additional cardinality control beyond the SDK's built-in `CardinalityConfig`

#### 7.3.6 Performance Verification

Create a micro-benchmark (can be timing-based per 01-review-r1 M-13):
- Publish 10,000 messages with no-op OTel (no SDK registered)
- Publish 10,000 messages without OTel dependency on classpath
- Compare p99 latency: must be less than 1% difference

#### 7.3.7 Files Created / Changed

| File | Action | Description |
|------|--------|-------------|
| `kubemq-java/src/main/java/io/kubemq/sdk/observability/OTelAvailability.java` | NEW | Runtime detection |
| `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java` | MODIFY | Add tracerProvider/meterProvider builder params |
| `kubemq-java-example/src/main/java/.../OTelExample.java` | NEW | Complete setup example |

---

## 8. Cross-Category Dependencies

### 8.1 Dependencies FROM This Category

| This REQ | Depends On | From Spec | Why |
|----------|-----------|-----------|-----|
| REQ-OBS-1 | REQ-CQ-1 (layered architecture) | 07-code-quality-spec.md | Protocol layer / interceptor chain is where tracing instrumentation lives |
| REQ-OBS-1 | REQ-ERR-3 (retry policy) | 01-error-handling-spec.md | Retry events recorded as span events |
| REQ-OBS-2 | REQ-OBS-1 | This spec | Spans must exist before context can be injected/extracted |
| REQ-OBS-3 | REQ-OBS-1 | This spec | Shared OTel setup (same `provided` dependency, same initialization pattern) |
| REQ-OBS-3 | REQ-CONN-2 (connection state machine) | 02-connection-transport-spec.md | Connection state callbacks drive connection count and reconnection metrics |
| REQ-OBS-3 | REQ-ERR-2 (error classification) | 01-error-handling-spec.md | `ErrorCategory` enum and `ErrorClassifier.toOtelErrorType()` provide error.type values |
| REQ-OBS-5 | None | -- | Can proceed independently |

### 8.2 Dependencies ON This Category

| Other REQ | Depends On | Why |
|-----------|-----------|-----|
| REQ-CQ-4 (minimal deps) | REQ-OBS-5 | Must replace Logback with logger interface before removing logback-classic dependency |
| REQ-DOC-7 (examples) | REQ-OBS-1 | OTel setup example requires OTel integration to exist |

### 8.3 Shared Types

| Type | Defined In | Used By |
|------|-----------|---------|
| `ErrorCategory` | 01-error-handling-spec.md | REQ-OBS-3 (error.type metric attribute) |
| `ErrorClassifier.toOtelErrorType()` | 01-error-handling-spec.md | REQ-OBS-1 (retry span events), REQ-OBS-3 (metric attributes) |
| `ConnectionState` | 02-connection-transport-spec.md | REQ-OBS-3 (connection count metric) |
| `KubeMQLogger` | This spec (REQ-OBS-5) | 07-code-quality-spec.md (REQ-CQ-4, Logback removal) |

---

## 9. Test Plan

### 9.1 REQ-OBS-5 Tests

| # | Test | Type | Description |
|---|------|------|-------------|
| T1 | NoOpLogger does nothing | Unit | Verify all methods are no-ops, `isEnabled()` returns false |
| T2 | Slf4jLoggerAdapter delegates correctly | Unit | Mock SLF4J Logger, verify delegation for each level |
| T3 | Slf4jLoggerAdapter.formatMessage | Unit | Test key-value formatting, empty args, odd-length args |
| T4 | KubeMQLoggerFactory auto-detects SLF4J | Unit | With SLF4J on classpath returns Slf4jLoggerAdapter; mock ClassNotFoundException for NoOpLogger path |
| T5 | User-injected logger used | Integration | Build client with custom `KubeMQLogger`, verify it receives log calls |
| T6 | LogContextProvider with OTel active | Unit | Set up OTel context, verify trace_id/span_id returned |
| T7 | LogContextProvider without OTel | Unit | No OTel on classpath, verify empty array returned |
| T8 | Log level audit | Manual | Verify per-message logging is DEBUG/TRACE, not INFO |

### 9.2 REQ-OBS-1 Tests

| # | Test | Type | Description |
|---|------|------|-------------|
| T9 | Publish creates PRODUCER span | Unit | Use OTel test SDK (`SdkTracerProvider` with `InMemorySpanExporter`), verify span kind, name, attributes |
| T10 | Subscribe callback creates CONSUMER span | Unit | Verify linked span (not child) with correct attributes |
| T11 | Queue receive creates batch receive + process spans | Unit | Verify receive span has `message_count`, per-message process spans linked to producers |
| T12 | Queue settle creates CONSUMER span | Unit | Verify settle span is child of process span |
| T13 | Command send creates CLIENT span | Unit | Verify span kind CLIENT, attributes |
| T14 | Command response creates SERVER span | Unit | Verify span kind SERVER, linked to sender |
| T15 | Failed operation sets ERROR status | Unit | Trigger error, verify span status and error.type attribute |
| T16 | Retry events recorded on span | Unit | Trigger retries, verify span events with attempt, delay, error.type |
| T17 | Instrumentation scope correct | Unit | Verify tracer scope name = `io.kubemq.sdk`, version = SDK version |
| T18 | Span name format | Unit | Verify `{operation} {channel}` format for all operation types |

### 9.3 REQ-OBS-2 Tests

| # | Test | Type | Description |
|---|------|------|-------------|
| T19 | Inject adds traceparent to tags | Unit | Verify tags contain `traceparent` after publish |
| T20 | Extract recovers context from tags | Unit | Set traceparent in tags, extract, verify context has correct trace/span ID |
| T21 | Round-trip context survives | Integration | Publish with trace context, consume, verify linked span has correct trace ID |
| T22 | Missing context handled gracefully | Unit | Consume message with no traceparent, verify no error, span created without link |
| T23 | Batch publish per-message context | Unit | Publish batch, verify each message has its own traceparent |
| T24 | Requeue preserves context | Unit | Requeue message, verify traceparent unchanged in tags |
| T25 | DLQ preserves context | Unit | DLQ message, verify traceparent unchanged, DLQ event recorded |
| T26 | RPC round-trip trace shape | Integration | Send command with context, verify responder extracts and creates linked SERVER span |

### 9.4 REQ-OBS-3 Tests

| # | Test | Type | Description |
|---|------|------|-------------|
| T27 | operation.duration recorded | Unit | Use `InMemoryMetricReader`, publish message, verify histogram recorded with correct attributes |
| T28 | sent.messages incremented | Unit | Publish, verify counter = 1 |
| T29 | consumed.messages incremented | Unit | Consume, verify counter = 1 |
| T30 | connection.count up/down | Unit | Simulate connect/disconnect, verify UpDownCounter |
| T31 | reconnections incremented | Unit | Simulate state change to RECONNECTING, verify counter |
| T32 | retry.attempts incremented | Unit | Trigger retry, verify counter with operation.name and error.type |
| T33 | retry.exhausted incremented | Unit | Exhaust retries, verify counter |
| T34 | Histogram bucket boundaries | Unit | Verify explicit boundaries match GS specification |
| T35 | error.type mapping | Unit | For each ErrorCategory, verify `toOtelErrorType()` returns correct lowercase string |
| T36 | Cardinality threshold | Unit | Add 101 unique channels, verify 101st omits destination.name, WARN logged |
| T37 | Cardinality allowlist | Unit | Add channel to allowlist, verify it always included even above threshold |
| T38 | Meter scope correct | Unit | Verify meter scope name = `io.kubemq.sdk`, version = SDK version |

### 9.5 REQ-OBS-4 Tests

| # | Test | Type | Description |
|---|------|------|-------------|
| T39 | No-op overhead benchmark | Perf | Publish 10K messages with no-op OTel, verify < 1% overhead vs no OTel |
| T40 | Injectable TracerProvider used | Unit | Inject custom TracerProvider, verify spans use that provider |
| T41 | Injectable MeterProvider used | Unit | Inject custom MeterProvider, verify metrics use that provider |
| T42 | Fallback to GlobalOpenTelemetry | Unit | Don't inject, verify GlobalOpenTelemetry used |
| T43 | OTel API not on classpath | Unit | Simulate missing OTel API, verify no ClassNotFoundException, operations work normally |

---

## 10. Breaking Changes

| Change | Impact | Mitigation |
|--------|--------|-----------|
| Logback removal from compile scope | Users who rely on SDK transitively providing Logback will need to add it themselves | Document in migration guide; WARN in release notes |
| `KubeMQClient.Level` enum replaced by `KubeMQLogger.LogLevel` | Users who reference `KubeMQClient.Level` directly | Keep `KubeMQClient.Level` as `@Deprecated` alias for one minor version |
| `setLogLevel()` behavior change | Level setting via Logback cast no longer works | New logger interface provides level control via `isEnabled()` and SLF4J backend configuration |
| Tags map may now contain `traceparent`/`tracestate` keys | Users who iterate tags may see unexpected keys | Document that trace context keys are reserved; filter in user code if needed |

**SemVer assessment:** The Logback removal is a behavioral breaking change (transitive dependency removed). This should be released as part of a minor version with prominent release notes, or deferred to the next major version. Since `logback-classic` was never part of the SDK's public API contract (it was an implementation detail), a minor version bump is defensible per SemVer, but the release notes must call it out.

---

## 11. Migration Guide

### 11.1 For Users of SDK Logging

**Before (v2.1.x):**
```java
PubSubClient client = PubSubClient.builder()
    .address("localhost:50000")
    .clientId("my-client")
    .logLevel(KubeMQClient.Level.DEBUG)
    .build();
// Logback automatically provides logging output
```

**After (v2.2.x):**
```java
// Option A: Keep using SLF4J (add logback-classic to YOUR pom.xml)
PubSubClient client = PubSubClient.builder()
    .address("localhost:50000")
    .clientId("my-client")
    .logLevel(KubeMQClient.Level.DEBUG)  // still works with SLF4J adapter
    .build();

// Option B: Use custom logger
PubSubClient client = PubSubClient.builder()
    .address("localhost:50000")
    .clientId("my-client")
    .logger(new MyCustomLogger())  // any KubeMQLogger implementation
    .build();
```

### 11.2 For Users Adding OTel

```java
// 1. Add dependencies to your pom.xml:
// io.opentelemetry:opentelemetry-sdk
// io.opentelemetry:opentelemetry-exporter-otlp

// 2. Configure OTel SDK (before creating KubeMQ client):
SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
    .addSpanProcessor(BatchSpanProcessor.builder(
        OtlpGrpcSpanExporter.builder().build()
    ).build())
    .build();

SdkMeterProvider meterProvider = SdkMeterProvider.builder()
    .registerMetricReader(PeriodicMetricReader.builder(
        OtlpGrpcMetricExporter.builder().build()
    ).build())
    .build();

// 3. Option A: Inject into client builder
PubSubClient client = PubSubClient.builder()
    .address("localhost:50000")
    .clientId("my-client")
    .tracerProvider(tracerProvider)
    .meterProvider(meterProvider)
    .build();

// 3. Option B: Set globally (affects all clients)
OpenTelemetrySdk otel = OpenTelemetrySdk.builder()
    .setTracerProvider(tracerProvider)
    .setMeterProvider(meterProvider)
    .buildAndRegisterGlobal();
// KubeMQ clients will auto-detect via GlobalOpenTelemetry
```

---

## 12. Open Questions

| # | Question | Impact | Default If Not Resolved |
|---|----------|--------|------------------------|
| OQ-1 | Should `slf4j-api` move to `provided` scope or remain `compile`? Moving to `provided` is stricter (users must add SLF4J themselves) but matches the OTel pattern. Keeping `compile` maintains backward compatibility. | Dependency graph | Keep `compile` scope for SLF4J for backward compatibility; only `logback-classic` moves to `test` scope |
| OQ-2 | Should the SDK use the OTel `opentelemetry-api-incubator` for access to `setExplicitBucketBoundariesAdvice()`? This API graduated in OTel 1.38+ but was incubating before. | Histogram boundaries | Use stable API (1.31+ minimum). If `setExplicitBucketBoundariesAdvice()` is not available, document that users should configure boundaries via OTel Metric Views |
| OQ-3 | Should the `KubeMQLogger` interface include a `isTraceEnabled()` / `isDebugEnabled()` shortcut, or is `isEnabled(LogLevel)` sufficient? | API ergonomics | `isEnabled(LogLevel)` is sufficient; convenience methods can be added as default methods in a future minor version |
| OQ-4 | Should trace context injection be opt-out (inject by default) or opt-in? | Backward compatibility | Inject by default when OTel API is on classpath -- this is the standard behavior for instrumented SDKs |
| OQ-5 | For the `Slf4jLoggerAdapter`, should the `logLevel` builder parameter control SLF4J level programmatically (as it does today via Logback cast), or should it be advisory only? | Backward compatibility | Advisory only -- users configure log levels via their SLF4J backend (logback.xml, log4j2.xml). The Logback cast is removed. Document this change. |

---

## Appendix A: Complete New Package Structure

```
io.kubemq.sdk.observability/
    KubeMQLogger.java          -- Logger interface (REQ-OBS-5)
    NoOpLogger.java            -- No-op implementation (REQ-OBS-5)
    Slf4jLoggerAdapter.java    -- SLF4J bridge (REQ-OBS-5)
    KubeMQLoggerFactory.java         -- Auto-detection factory (REQ-OBS-5)
    LogContextProvider.java    -- OTel trace correlation for logs (REQ-OBS-5)
    KubeMQSemconv.java         -- Semantic convention constants (REQ-OBS-1)
    KubeMQTracing.java         -- Span creation (REQ-OBS-1)
    KubeMQTagsCarrier.java     -- W3C Trace Context carrier (REQ-OBS-2)
    KubeMQMetrics.java         -- Metrics instrumentation (REQ-OBS-3)
    CardinalityConfig.java     -- Cardinality configuration (REQ-OBS-3)
    CardinalityManager.java    -- Cardinality threshold logic (REQ-OBS-3)
    SdkVersion.java            -- Version resolution (REQ-OBS-1, OBS-3)
    OTelAvailability.java      -- Runtime detection (REQ-OBS-4)
```

## Appendix B: Full Attribute Reference

### Span Attributes (Required)

| Attribute Key | Type | Value | Set On |
|--------------|------|-------|--------|
| `messaging.system` | string | `"kubemq"` | All spans |
| `messaging.operation.name` | string | `publish`, `process`, `receive`, `settle`, `send` | All spans |
| `messaging.operation.type` | string | Same as operation.name | All spans |
| `messaging.destination.name` | string | Channel name | All spans |
| `messaging.message.id` | string | Message ID | All spans (when available) |
| `messaging.client.id` | string | Client ID | All spans |
| `messaging.consumer.group.name` | string | Group name | Events Store consumer spans |
| `server.address` | string | Server hostname | All spans |
| `server.port` | long | Server port | All spans |
| `error.type` | string | Error category (lowercase) | Failed spans |

### Span Attributes (Recommended)

| Attribute Key | Type | Value | Set On |
|--------------|------|-------|--------|
| `messaging.message.body.size` | long | Body size in bytes | All spans (guarded by isRecording) |

### Batch Attributes

| Attribute Key | Type | Value | Set On |
|--------------|------|-------|--------|
| `messaging.batch.message_count` | long | Message count | Batch receive spans |

### Retry Event Attributes

| Attribute Key | Type | Value | Set On |
|--------------|------|-------|--------|
| `retry.attempt` | long | 1-based attempt number | `retry` span events |
| `retry.delay_seconds` | double | Delay before attempt | `retry` span events |
| `error.type` | string | Error that triggered retry | `retry` span events |

### Metric Attributes

| Attribute Key | Applied To | Notes |
|--------------|-----------|-------|
| `messaging.system` | All metrics | Always `"kubemq"` |
| `messaging.operation.name` | duration, sent, consumed, retry | Operation identifier |
| `messaging.destination.name` | All metrics | Subject to cardinality management |
| `error.type` | duration (on failure), retry.attempts, retry.exhausted | From ErrorClassifier |
