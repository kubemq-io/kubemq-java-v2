# Phase 1 → Phase 2 Type Verification

**Generated:** 2026-03-10  
**Purpose:** Verify that types referenced in Phase 2 specs (04-testing, 05-observability, 08-api-completeness, 09-api-design-dx) match the actual names and locations created by Phase 1 of the gap implementation.

---

## 1. Types Created in Phase 1

### 1.1 Client Package (`io.kubemq.sdk.client`)

| Fully Qualified Name | Type | Public API |
|---------------------|------|------------|
| `io.kubemq.sdk.client.BufferOverflowPolicy` | enum | `ERROR`, `BLOCK` |
| `io.kubemq.sdk.client.BufferedMessage` | interface | `estimatedSizeBytes()`, `grpcRequest()`, `messageType()`; nested `MessageType` enum |
| `io.kubemq.sdk.client.ConnectionState` | enum | `IDLE`, `CONNECTING`, `READY`, `RECONNECTING`, `CLOSED` |
| `io.kubemq.sdk.client.ConnectionStateListener` | interface | `onConnected()`, `onDisconnected()`, `onReconnecting(int)`, `onReconnected()`, `onClosed()` (all default no-op) |
| `io.kubemq.sdk.client.ConnectionStateMachine` | class | `ConnectionStateMachine()`, `getState()`, `addListener()`, `removeListener()`, `transitionTo()`, `setCurrentReconnectAttempt(int)`, `shutdown()` |
| `io.kubemq.sdk.client.MessageBuffer` | class | `MessageBuffer(long, BufferOverflowPolicy)`, `setOnBufferDrainCallback()`, `add()`, `flush()`, `discardAll()`, `size()`, `currentSizeBytes()` |
| `io.kubemq.sdk.client.ReconnectionConfig` | class | `@Builder` with defaults; getters via Lombok |
| `io.kubemq.sdk.client.ReconnectionManager` | class | `ReconnectionManager(ReconnectionConfig, ConnectionStateMachine)`, `startReconnection()`, `cancel()`, `shutdown()`, `computeDelay()`, `getAttemptCount()` |

### 1.2 Common Package (`io.kubemq.sdk.common`)

| Fully Qualified Name | Type | Public API |
|---------------------|------|------------|
| `io.kubemq.sdk.common.Defaults` | class | `SEND_TIMEOUT`, `SUBSCRIBE_TIMEOUT`, `RPC_TIMEOUT`, `QUEUE_RECEIVE_SINGLE_TIMEOUT`, `QUEUE_RECEIVE_STREAMING_TIMEOUT`, `CONNECTION_TIMEOUT` (static Duration constants) |
| `io.kubemq.sdk.common.Internal` | annotation | `@Internal` (runtime, targets TYPE, METHOD) |
| `io.kubemq.sdk.common.SubscriptionReconnectHandler` | class | `SubscriptionReconnectHandler(ScheduledExecutorService, long, String, String)`, `scheduleReconnect()`, `resetAttempts()` — marked `@Internal` |

### 1.3 Exception Package (`io.kubemq.sdk.exception`)

| Fully Qualified Name | Type | Public API |
|---------------------|------|------------|
| `io.kubemq.sdk.exception.KubeMQException` | class | `newBuilder()`, `getCode()`, `getOperation()`, `getChannel()`, `isRetryable()`, `getRequestId()`, `getMessageId()`, `getStatusCode()`, `getTimestamp()`, `getServerAddress()`, `getCategory()`; nested `Builder<T>`, `DefaultBuilder` |
| `io.kubemq.sdk.exception.ValidationException` | class | `builder()`, extends KubeMQException |
| `io.kubemq.sdk.exception.KubeMQTimeoutException` | class | `builder()`, extends KubeMQException |
| `io.kubemq.sdk.exception.ClientClosedException` | class | `create()`, `builder()`, extends KubeMQException |
| `io.kubemq.sdk.exception.ConnectionException` | class | `builder()`, extends KubeMQException |
| `io.kubemq.sdk.exception.AuthenticationException` | class | `builder()`, extends KubeMQException |
| `io.kubemq.sdk.exception.AuthorizationException` | class | `builder()`, extends KubeMQException |
| `io.kubemq.sdk.exception.BackpressureException` | class | `builder()`, `bufferFull(long, long, long)` static factory |
| `io.kubemq.sdk.exception.ErrorCategory` | enum | `TRANSIENT`, `TIMEOUT`, `THROTTLING`, `AUTHENTICATION`, `AUTHORIZATION`, `VALIDATION`, `NOT_FOUND`, `FATAL`, `CANCELLATION`, `BACKPRESSURE`; `isDefaultRetryable()` |
| `io.kubemq.sdk.exception.ErrorCode` | enum | 22 values (CONNECTION_FAILED, CONNECTION_TIMEOUT, …, PARTIAL_FAILURE) — **no** `FEATURE_NOT_IMPLEMENTED` |
| `io.kubemq.sdk.exception.ErrorClassifier` | class | `shouldRetry()`, `shouldUseExtendedBackoff()`, `toOtelErrorType()` (all static) |
| `io.kubemq.sdk.exception.ErrorMessageBuilder` | class | `build()`, `withRetryContext()`, `getSuggestion()` (static) |
| `io.kubemq.sdk.exception.GrpcErrorMapper` | class | `map(StatusRuntimeException, String, String, String, boolean)` — 5 args |

### 1.4 Auth Package (`io.kubemq.sdk.auth`)

| Fully Qualified Name | Type | Public API |
|---------------------|------|------------|
| `io.kubemq.sdk.auth.CredentialProvider` | interface | `getToken() throws CredentialException` |
| `io.kubemq.sdk.auth.TokenResult` | class | `TokenResult(String)`, `TokenResult(String, Instant)`, `getToken()`, `getExpiresAt()` |
| `io.kubemq.sdk.auth.CredentialException` | class | extends Exception; `CredentialException(String)`, `CredentialException(String, boolean)`, `CredentialException(String, Throwable, boolean)`; `isRetryable()` |
| `io.kubemq.sdk.auth.StaticTokenProvider` | class | `StaticTokenProvider(String)`; implements CredentialProvider |
| `io.kubemq.sdk.auth.CredentialManager` | class | `CredentialManager(CredentialProvider, ScheduledExecutorService)`, `getToken()`, `invalidate()`, `shutdown()` |

### 1.5 Retry Package (`io.kubemq.sdk.retry`)

| Fully Qualified Name | Type | Public API |
|---------------------|------|------------|
| `io.kubemq.sdk.retry.RetryPolicy` | class | `builder()`, `DEFAULT`, `DISABLED`, `getMaxRetries()`, `getInitialBackoff()`, `getMaxBackoff()`, `getMultiplier()`, `getJitterType()`, `getMaxConcurrentRetries()`, `isEnabled()`, `computeBackoff(int)`, `worstCaseLatency(Duration)` |
| `io.kubemq.sdk.retry.RetryExecutor` | class | `RetryExecutor(RetryPolicy)`, `RetryExecutor(RetryPolicy, Supplier<Boolean>)`, `execute(Callable, String, String, OperationSafety)` |
| `io.kubemq.sdk.retry.OperationSafety` | enum | `SAFE`, `UNSAFE_ON_AMBIGUOUS`; `canRetry(KubeMQException)` |

### 1.6 Transport Package (`io.kubemq.sdk.transport`)

| Fully Qualified Name | Type | Visibility | Public API |
|---------------------|------|------------|------------|
| `io.kubemq.sdk.transport.Transport` | interface | **package-private** | `ping()`, `isReady()`, `close()` |
| `io.kubemq.sdk.transport.TransportConfig` | class | public | `@Builder`; all getters (address, tls, tokenSupplier, etc.) |
| `io.kubemq.sdk.transport.TransportFactory` | class | public, `@Internal` | `create(TransportConfig)` static |
| `io.kubemq.sdk.transport.TransportAuthInterceptor` | class | **package-private** | — (gRPC ClientInterceptor) |
| `io.kubemq.sdk.transport.GrpcTransport` | class | package-private | — |

### 1.7 Types NOT Created in Phase 1

- `io.kubemq.sdk.exception.NotImplementedException` — required by 08-api-completeness (REQ-API-3)
- `io.kubemq.sdk.exception.ErrorCode.FEATURE_NOT_IMPLEMENTED` — required by 08-api-completeness
- `io.kubemq.sdk.observability.KubeMQLogger` and related — required by 05-observability Phase 1 (REQ-OBS-5); **observability package not yet created**

---

## 2. Phase 2 Spec References Checked

### 2.1 04-testing-spec.md (Phase 1 scope: REQ-TEST-3, REQ-TEST-4, REQ-TEST-5; Phase 2: REQ-TEST-1, REQ-TEST-2)

| Spec Reference | Spec Location | Phase 1 Actual | Match? |
|----------------|---------------|----------------|--------|
| `io.kubemq.sdk.error.ErrorCategory` | Lines 163–164, 200 | `io.kubemq.sdk.exception.ErrorCategory` | ❌ Package |
| `io.kubemq.sdk.error.GrpcErrorMapper` | Lines 164, 190 | `io.kubemq.sdk.exception.GrpcErrorMapper` | ❌ Package |
| `io.kubemq.sdk.error.KubeMQException` | Lines 164, 190 | `io.kubemq.sdk.exception.KubeMQException` | ❌ Package |
| `io.kubemq.sdk.error.ClientClosedException` | Lines 355, 449 | `io.kubemq.sdk.exception.ClientClosedException` | ❌ Package |
| `io.kubemq.sdk.error.RetryPolicy` | Lines 243, 256 | `io.kubemq.sdk.retry.RetryPolicy` | ❌ Package |
| `io.kubemq.sdk.error.AuthenticationException` | Line 959 | `io.kubemq.sdk.exception.AuthenticationException` | ❌ Package |
| `io.kubemq.sdk.error.TimeoutException` | Lines 1162, 1180, 1196, 1818 | `io.kubemq.sdk.exception.KubeMQTimeoutException` | ❌ Name + Package |
| `io.kubemq.sdk.client.ConnectionState` | Line 1023 | `io.kubemq.sdk.client.ConnectionState` | ✓ |
| Test package `io.kubemq.sdk.unit.error` | Lines 155–160, 238–241, 352 | Phase 1 tests are in `io.kubemq.sdk.unit.exception` | ❌ Package |
| `GrpcErrorMapper.map(…, operation, channel)` | Lines 190–195 | `GrpcErrorMapper.map(grpcError, operation, channel, requestId, localContextCancelled)` — 5 args | ❌ Signature |
| Transport interface (mock) | Lines 370–371, 378 | `Transport` is package-private in `io.kubemq.sdk.transport` | ⚠️ May need factory/mock strategy |

### 2.2 05-observability-spec.md (Phase 1 scope: REQ-OBS-5; Phases 2–3: REQ-OBS-1–4)

| Spec Reference | Spec Location | Phase 1 Actual | Match? |
|----------------|---------------|----------------|--------|
| `ErrorCategory` | Prerequisites, Section 4 | `io.kubemq.sdk.exception.ErrorCategory` | ✓ |
| `ErrorClassifier.toOtelErrorType()` | Prerequisites, Sections 4–5 | `io.kubemq.sdk.exception.ErrorClassifier.toOtelErrorType()` | ✓ |
| `KubeMQException` | Prerequisites | `io.kubemq.sdk.exception.KubeMQException` | ✓ |
| `io.kubemq.sdk.observability.KubeMQLogger` | Section 3.3.1, Files to Modify | **Not created** — observability package does not exist | N/A (Phase 2 work) |
| `io.kubemq.sdk.observability.NoOpLogger` | Section 3.3.2 | Not created | N/A |
| `io.kubemq.sdk.observability.Slf4jLoggerAdapter` | Section 3.3.3 | Not created | N/A |
| `io.kubemq.sdk.observability.KubeMQLoggerFactory` | Section 3.3.4 | Not created | N/A |

**Note:** REQ-OBS-5 (KubeMQLogger etc.) is Phase 1 of the *observability* spec, but was not part of gap-implementation Phase 1 (WU-1–WU-5). Those types are to be created when implementing 05-observability.

### 2.3 08-api-completeness-spec.md (REQ-API-1, REQ-API-2, REQ-API-3)

| Spec Reference | Spec Location | Phase 1 Actual | Match? |
|----------------|---------------|----------------|--------|
| `ValidationException` | Indirect (message validation) | `io.kubemq.sdk.exception.ValidationException` | ✓ |
| `ErrorCode` | Section 5.2 | `io.kubemq.sdk.exception.ErrorCode` — **no** `FEATURE_NOT_IMPLEMENTED` | ❌ Enum value |
| `NotImplementedException` | Section 5.3, Files to Modify | **Not created** | ❌ |
| `KubeMQException` | Section 5.3 (NotImplementedException extends) | `io.kubemq.sdk.exception.KubeMQException` | ✓ |
| `ErrorCategory` | Section 5.3 | `io.kubemq.sdk.exception.ErrorCategory` | ✓ |
| `TimeoutException` (line 300) | Section 4, batch send snippet | `java.util.concurrent.TimeoutException` ( Future.get() ) | ✓ (JDK, not SDK) |

### 2.4 09-api-design-dx-spec.md (Phase 1 scope: REQ-DX-1, REQ-DX-2, REQ-DX-4, REQ-DX-5)

| Spec Reference | Spec Location | Phase 1 Actual | Match? |
|----------------|---------------|----------------|--------|
| `io.kubemq.sdk.exception.ValidationException` | Section 3.2.1, 6.2.1 | `io.kubemq.sdk.exception.ValidationException` | ✓ |
| `ErrorCode.INVALID_ARGUMENT` | Throughout | Exists in `ErrorCode` enum | ✓ |
| `ConnectionException` | Section 6 cross-ref | `io.kubemq.sdk.exception.ConnectionException` | ✓ |

---

## 3. Mismatches Found

### 3.1 Package `io.kubemq.sdk.error` vs `io.kubemq.sdk.exception`

**Specs affected:** 04-testing-spec.md  
**Issue:** The testing spec consistently uses `io.kubemq.sdk.error` for error-handling types. Phase 1 created all such types in `io.kubemq.sdk.exception`.

**Recommendation:** Update 04-testing-spec.md to use `io.kubemq.sdk.exception` for:
- `ErrorCategory`
- `GrpcErrorMapper`
- `KubeMQException`
- `ClientClosedException`
- `RetryPolicy` (actually in `io.kubemq.sdk.retry`)
- `AuthenticationException`
- All other error-type imports.

Also change the test package from `io.kubemq.sdk.unit.error` to `io.kubemq.sdk.unit.exception` (or align with existing Phase 1 test layout).

### 3.2 TimeoutException vs KubeMQTimeoutException

**Spec:** 04-testing-spec.md (lines 1162, 1180, 1183, 1196, 1818)  
**Issue:** Spec references `io.kubemq.sdk.error.TimeoutException`. Phase 1 uses `KubeMQTimeoutException` to avoid conflict with `java.util.concurrent.TimeoutException`.

**Recommendation:** Replace all references to `TimeoutException` (SDK) with `KubeMQTimeoutException` and import `io.kubemq.sdk.exception.KubeMQTimeoutException`.

### 3.3 GrpcErrorMapper.map() signature

**Spec:** 04-testing-spec.md (lines 190–195)  
**Issue:** Spec shows a 3-argument call: `GrpcErrorMapper.map(grpcError, "testOperation", "testChannel")`.  
**Actual:** `GrpcErrorMapper.map(StatusRuntimeException grpcError, String operation, String channel, String requestId, boolean localContextCancelled)` — 5 arguments.

**Recommendation:** In tests, use `GrpcErrorMapper.map(grpcError, "testOperation", "testChannel", null, false)` (or equivalent for each scenario).

### 3.4 NotImplementedException and ErrorCode.FEATURE_NOT_IMPLEMENTED

**Spec:** 08-api-completeness-spec.md (REQ-API-3)  
**Issue:** Phase 1 did not create `NotImplementedException` or add `FEATURE_NOT_IMPLEMENTED` to `ErrorCode`.

**Recommendation:** Implement these when doing 08-api-completeness Phase 1, per the spec’s "Files to Modify" and fallback instructions.

### 3.5 Transport interface visibility

**Spec:** 04-testing-spec.md (lines 370–371, 378)  
**Issue:** Spec suggests using "Transport interface from 07-code-quality-spec" or InProcessChannelBuilder for closed-client tests. Phase 1’s `Transport` is package-private in `io.kubemq.sdk.transport`.

**Recommendation:** For unit tests, either:
- Use InProcessChannelBuilder (as spec fallback), or
- Introduce a test-only transport/mock via a package split or factory that exposes a mockable transport for tests.

---

## 4. Summary Table

| Mismatch | Spec | Severity | Action |
|----------|------|----------|--------|
| Package `error` → `exception` | 04-testing | High | Update all imports in 04-testing-spec |
| `TimeoutException` → `KubeMQTimeoutException` | 04-testing | High | Update test assertions and imports |
| `GrpcErrorMapper.map` 3 args → 5 args | 04-testing | Medium | Add `requestId`, `localContextCancelled` to test calls |
| Test package `unit.error` → `unit.exception` | 04-testing | Low | Align package with Phase 1 layout |
| `NotImplementedException` missing | 08-api-completeness | High | Add when implementing REQ-API-3 |
| `ErrorCode.FEATURE_NOT_IMPLEMENTED` missing | 08-api-completeness | High | Add when implementing REQ-API-3 |
| Transport package-private | 04-testing | Low | Use InProcessChannelBuilder or document mock strategy |

---

## 5. Types Verified as Correct

- `ConnectionState`, `ConnectionStateListener`, `ConnectionStateMachine` — names and packages match
- `ReconnectionManager`, `ReconnectionConfig` — names and packages match
- `TransportConfig`, `TransportFactory` — names and packages match (Transport and TransportAuthInterceptor are intentionally package-private)
- `ValidationException`, `ErrorCode`, `ErrorCategory`, `ErrorClassifier`, `KubeMQException`, `ConnectionException` — names and packages match
- `RetryPolicy` in `io.kubemq.sdk.retry` — correct package
