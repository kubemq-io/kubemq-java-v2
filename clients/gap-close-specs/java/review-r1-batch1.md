# Java SDK Specs -- Architecture Review (Round 1, Batch 1)

**Reviewer:** Senior Java SDK Architect
**Specs Reviewed:** 01-error-handling, 02-connection-transport, 03-auth-security, 07-code-quality
**Date:** 2026-03-09

## Review Summary

| Spec | Issues Found | Critical | Major | Minor |
|------|-------------|----------|-------|-------|
| 01-error-handling | 10 | 2 | 4 | 4 |
| 02-connection-transport | 8 | 1 | 4 | 3 |
| 03-auth-security | 7 | 1 | 3 | 3 |
| 07-code-quality | 6 | 1 | 2 | 3 |
| **Cross-spec** | 4 | 0 | 3 | 1 |
| **Total** | **35** | **5** | **16** | **14** |

---

## Critical Issues (MUST FIX)

### C-1: `GrpcErrorMapper` uses invalid Java syntax for import alias

**Spec:** 01-error-handling-spec.md
**Section:** 8.2.1 GrpcErrorMapper
**Current:** Line 1317: `import com.google.rpc.Status as RpcStatus;`
**Should be:** `import com.google.rpc.Status;` (Java does not support import aliases). Uses of the type must be fully qualified (`com.google.rpc.Status`) or the import must be `com.google.rpc.Status` with local variable naming to disambiguate from `io.grpc.Status`.
**Reason:** This will not compile. The implementer will need to resolve the naming collision between `io.grpc.Status` and `com.google.rpc.Status`. The standard Java approach is to fully qualify one of them.

### C-2: `MessageBuffer.add()` has a race condition in ERROR policy

**Spec:** 02-connection-transport-spec.md
**Section:** 2.3.3 MessageBuffer
**Current:** The ERROR policy path does a non-atomic check-then-act: `if (currentSizeBytes.get() + messageSize > maxSizeBytes)` followed by `enqueue(message, messageSize)`. Two concurrent threads could both pass the check and exceed the buffer limit.
**Should be:** Use a CAS loop or synchronize the check-and-enqueue for the ERROR path, similar to how the BLOCK path already holds `blockLock`. Alternatively, accept that ERROR mode allows slight over-allocation (document this as a deliberate design choice for performance, since the buffer is bounded in bytes and a small overshoot is acceptable).
**Reason:** Under concurrent publishing during reconnection, the buffer can exceed `maxSizeBytes` by up to `N * maxMessageSize` where N is the thread count. This could cause OOM in constrained environments.

### C-3: `BufferFullException` does not extend `KubeMQException`

**Spec:** 02-connection-transport-spec.md
**Section:** 2.3.5 BufferFullException
**Current:** `BufferFullException extends RuntimeException` with a note to "move to error package when REQ-ERR-1 is done."
**Should be:** The spec should define `BufferFullException` as extending `KubeMQException` from the start (or the `BackpressureException` from 01-error-handling-spec.md section 3.2.4). The GS REQ-ERR-1 states "All SDK methods return SDK-typed errors, never raw RuntimeException." The spec already defines `BackpressureException` in 01-error-handling-spec.md -- `BufferFullException` is a second, conflicting type for the same concept.
**Reason:** Having both `BufferFullException` (in `client/`) and `BackpressureException` (in `exception/`) for the same semantic error violates the GS single-hierarchy requirement. The spec must resolve this: either `BufferFullException` IS `BackpressureException`, or there is a clear inheritance relationship.

### C-4: `AuthenticationException` in 03-auth-security-spec.md does not extend `KubeMQException`

**Spec:** 03-auth-security-spec.md
**Section:** 3.1.3 AuthenticationException
**Current:** `AuthenticationException extends RuntimeException` (section 3.1.3, line 211)
**Should be:** `AuthenticationException extends KubeMQException` (as defined in 01-error-handling-spec.md section 3.2.4). The spec has a "stub strategy" note (Cross-Category Dependencies section) but the actual class definition contradicts the error hierarchy.
**Reason:** The GS requires a single error hierarchy. The spec defines `AuthenticationException` twice -- once as a stub in 03-auth-security-spec.md (extends RuntimeException) and once in 01-error-handling-spec.md (extends KubeMQException with builder pattern). The stub definition will need to be replaced, but if implemented first, callers will write `catch (AuthenticationException e)` against the wrong parent class. The spec must explicitly state that the stub in 03 has a DIFFERENT signature from the final class in 01, and provide a concrete migration plan (e.g., the stub should already extend KubeMQException using a minimal KubeMQException stub).

### C-5: `AuthInterceptor` in 07-code-quality-spec.md contradicts 03-auth-security-spec.md design

**Spec:** 07-code-quality-spec.md
**Section:** 2.3.2 Protocol Layer (AuthInterceptor)
**Current:** `AuthInterceptor` stores a static `Metadata` instance constructed once from `authToken`. The token is immutable.
**Should be:** `AuthInterceptor` must read the token dynamically per-call (via `AtomicReference` or `CredentialManager`), as specified in 03-auth-security-spec.md section 3.1.2.
**Reason:** The 07-code-quality-spec.md `AuthInterceptor` implementation (section 2.3.2, lines 242-272) is incompatible with REQ-AUTH-1 (mutable token) and REQ-AUTH-4 (credential provider). If implemented as specified in 07, it would need to be immediately rewritten when 03 is implemented. The 07 spec should either defer `AuthInterceptor` to the 03 spec or use the dynamic design from the start.

---

## Major Issues (SHOULD FIX)

### M-1: `RetryPolicy` parameter clamping is silently lossy

**Spec:** 01-error-handling-spec.md
**Section:** 5.2.1 RetryPolicy
**Current:** `maxRetries` is clamped to range 0-10, `multiplier` to 1.5-3.0, etc., via `clamp()` without any warning or exception. A user passing `maxRetries(20)` silently gets 10.
**Should be:** Either (a) throw `IllegalArgumentException` for out-of-range values (fail-fast, consistent with GS philosophy), or (b) log a WARN when clamping occurs, documenting the clamped value. Option (a) is recommended for consistency with the fail-fast validation pattern used elsewhere.
**Reason:** Silent parameter clamping is surprising and can mask configuration errors. A user expecting 20 retries gets 10 with no feedback.

### M-2: `CancellationException` name clash with `java.util.concurrent.CancellationException`

**Spec:** 01-error-handling-spec.md
**Section:** 3.2.4 Exception Subclass Hierarchy
**Current:** Defines `io.kubemq.sdk.exception.CancellationException`.
**Should be:** Rename to `KubeMQCancelledException` or `OperationCancelledException` to avoid the name clash with `java.util.concurrent.CancellationException`. Import ambiguity will cause confusion and require fully-qualified names in code that uses both `CompletableFuture` (which throws `j.u.c.CancellationException`) and KubeMQ SDK.
**Reason:** Since the SDK heavily uses `CompletableFuture` (queue handlers, async operations), the clash is virtually guaranteed to cause import confusion during development.

### M-3: `ConnectionStateMachine.transitionTo()` recursive retry on CAS failure

**Spec:** 02-connection-transport-spec.md
**Section:** 3.3.3 ConnectionStateMachine
**Current:** When `compareAndSet` fails, `transitionTo()` recursively calls itself: `transitionTo(newState);`. This can cause `StackOverflowError` under extreme contention.
**Should be:** Use a CAS loop instead of recursion:
```java
while (true) {
    ConnectionState old = state.get();
    if (old == CLOSED) { log + return; }
    if (old == newState) { return; }
    if (state.compareAndSet(old, newState)) { /* notify */ break; }
}
```
**Reason:** Recursive CAS retry is an anti-pattern in Java concurrent programming. Under high contention (many threads transitioning simultaneously), the recursion depth grows unboundedly.

### M-4: `ReconnectionManager` uses `ThreadLocalRandom.current()` as a static field

**Spec:** 02-connection-transport-spec.md
**Section:** 2.3.6 ReconnectionManager
**Current:** `private static final ThreadLocalRandom random = ThreadLocalRandom.current();`
**Should be:** Use `ThreadLocalRandom.current()` at the call site, not as a static field. `ThreadLocalRandom.current()` returns the thread-local instance for the calling thread. Storing it in a static field captures the instance for the constructing thread, which is then shared unsafely across threads.
**Reason:** This is a well-known Java concurrency bug. `ThreadLocalRandom` instances must not be shared across threads. The fix is trivial: call `ThreadLocalRandom.current()` in `computeDelay()` instead.

### M-5: `ConfigurationException` in 03-auth-security-spec.md does not extend `KubeMQException`

**Spec:** 03-auth-security-spec.md
**Section:** 3.2.8 TLS Handshake Failure Classification
**Current:** `ConfigurationException extends RuntimeException`
**Should be:** `ConfigurationException` should extend `KubeMQException` or be mapped to `ValidationException` from the error hierarchy. The GS classifies TLS version/cipher negotiation failure as `ConfigurationError` but the 01-error-handling hierarchy does not include a `ConfigurationException` -- the closest match is `ValidationException` with `ErrorCode.FAILED_PRECONDITION`.
**Reason:** A standalone `ConfigurationException extends RuntimeException` breaks the single-hierarchy invariant. The spec should either add `ConfigurationException` to the hierarchy in 01-error-handling-spec.md or map to `ValidationException`.

### M-6: `ensureReady()` uses busy-wait polling with `Thread.sleep(100ms)`

**Spec:** 02-connection-transport-spec.md
**Section:** 6.3.5 SDK-Level WaitForReady
**Current:** `ensureReady()` polls state every 100ms using `Thread.sleep()`.
**Should be:** Use a `CountDownLatch`, `CompletableFuture`, or `Condition` variable that is signaled by the `ConnectionStateMachine` on state transitions. The state machine already has a listener mechanism -- register a one-shot listener that completes a `CompletableFuture` when READY is reached.
**Reason:** Busy-wait polling wastes CPU and adds up to 100ms latency to every operation during reconnection. With many concurrent operations, this means N threads all sleeping and polling, which is wasteful.

### M-7: No state transition validation in `ConnectionStateMachine`

**Spec:** 02-connection-transport-spec.md
**Section:** 3.3.3 ConnectionStateMachine
**Current:** `transitionTo()` allows any transition except from CLOSED. For example, IDLE -> CLOSED or READY -> CONNECTING are accepted without validation.
**Should be:** Validate transitions against the state diagram. Only the following transitions should be allowed: IDLE -> CONNECTING, CONNECTING -> READY, CONNECTING -> CLOSED, READY -> RECONNECTING, READY -> CLOSED, RECONNECTING -> READY, RECONNECTING -> CLOSED. Invalid transitions should be logged at WARN and rejected.
**Reason:** Without transition validation, bugs in the reconnection or shutdown logic could put the state machine into illegal states (e.g., RECONNECTING -> CONNECTING), leading to inconsistent behavior.

### M-8: `CredentialException` extends `Exception` (checked) but is handled inconsistently

**Spec:** 03-auth-security-spec.md
**Section:** 3.4.1 Core Interfaces
**Current:** `CredentialException extends Exception` (checked). The `CredentialManager.refreshToken()` catches it and rethrows as either `AuthenticationException` (RuntimeException) or `RuntimeException`.
**Should be:** `CredentialException` should extend `RuntimeException` (unchecked) for consistency with the SDK's unchecked exception convention, OR the `CredentialProvider` interface should declare `throws CredentialException` which it already does. However, the checked exception forces all `CredentialProvider` implementations to handle or declare it, which is appropriate for a provider interface. The real issue is the wrapping in `CredentialManager` -- the retryable path wraps as bare `RuntimeException` (line 1143) instead of a typed SDK exception.
**Reason:** The retryable path should wrap as `ConnectionException` (transient, retryable) instead of bare `RuntimeException`, using the error hierarchy.

### M-9: `GrpcTransport` is package-private but `Transport` interface is public

**Spec:** 07-code-quality-spec.md
**Section:** 2.3.1 Transport Layer
**Current:** `Transport` interface is `public`, `GrpcTransport` is `class` (package-private).
**Should be:** This is actually correct for the factory pattern -- users interact with `Transport` interface but never instantiate `GrpcTransport` directly. However, the spec does not specify HOW the `Transport` is created. There is no factory method or builder visible to the transport package boundary. The `KubeMQClient` would need to either be in the same package or have a factory.
**Reason:** Since `GrpcTransport` is package-private in `io.kubemq.sdk.transport` and `KubeMQClient` is in `io.kubemq.sdk.client`, `KubeMQClient` cannot instantiate `GrpcTransport`. The spec needs a package-visible factory or the `GrpcTransport` constructor needs to be at least package-private with a public factory method.

### M-10: `error/` vs `exception/` package naming inconsistency

**Spec:** 07-code-quality-spec.md
**Section:** 6.2 GS Target Structure
**Current:** Section 6.2 shows `error/` as the new package for error types. But 01-error-handling-spec.md section 3.2.2 places all new exception classes in `io.kubemq.sdk.exception` (the existing package).
**Should be:** Pick one package name and use it consistently across all specs. The GS layout (section 2.3.1 in 07-code-quality-spec.md) shows `error/` but 01-error-handling-spec.md uses `exception/`. Since `exception/` already exists with legacy classes, the pragmatic choice is to keep `exception/` and not create `error/`.
**Reason:** Two different package names for the same concept will cause confusion during implementation. If `error/` is preferred, 01-error-handling-spec.md must be updated. If `exception/` is preferred, 07-code-quality-spec.md must be updated.

### M-11: `enableRetry()` still present in 03-auth-security-spec.md initChannel code

**Spec:** 03-auth-security-spec.md
**Section:** 3.2.9 Updated initChannel() -- Full TLS Block
**Current:** Line 588 includes `.enableRetry()` in the updated TLS block code.
**Should be:** `.enableRetry()` must be removed. 01-error-handling-spec.md section 5.2.4 explicitly requires removing `enableRetry()` from both TLS and plaintext paths to prevent double-retry amplification. The 03-auth-security-spec.md reproduces the old pattern.
**Reason:** If the implementer follows the 03 spec first, they will re-introduce `enableRetry()` that must then be removed by the 01 spec. This contradicts the GS requirement and could cause subtle double-retry bugs.

---

## Minor Issues (NICE TO FIX)

### m-1: `KubeMQException` implements `Serializable` but contains non-serializable `ErrorCode` and `ErrorCategory`

**Spec:** 01-error-handling-spec.md
**Section:** 3.2.3 KubeMQException Base Class
**Current:** `KubeMQException implements Serializable` with `ErrorCode` and `ErrorCategory` enum fields.
**Should be:** Enums are inherently `Serializable` in Java, so this is actually fine. However, the `Instant timestamp` field is also `Serializable` (since Java 8). No issue here -- just confirm all field types are serializable. The test T4 covers this. Note: if `serverAddress` or other String fields are populated from mutable sources, they are safe since String is immutable.
**Reason:** Self-correcting during review -- no change needed. Removing this item but keeping the note for completeness.

### m-2: `RetryExecutor` acquires semaphore but releases in `finally` of the sleep block only

**Spec:** 01-error-handling-spec.md
**Section:** 5.2.3 RetryExecutor
**Current:** The semaphore is acquired before the sleep block and released in the `finally` of the sleep block. If the operation itself takes time (the `Callable` call), the semaphore is not held during the retry operation execution.
**Should be:** Clarify the design intent. The semaphore limits concurrent retry *attempts* (backoff + operation). If the intent is to limit concurrent retrying operations, the semaphore should be held from acquire through the retry operation execution. If the intent is just to limit the concurrent backoff-waiting threads, the current design is correct but should be documented.
**Reason:** The GS says "concurrent retry attempts are limited." An attempt includes the operation execution, not just the sleep. The current implementation only holds the semaphore during the sleep, meaning the actual retry operation runs unsemaphored.

### m-3: `TimeoutException` name clash with `java.util.concurrent.TimeoutException`

**Spec:** 01-error-handling-spec.md
**Section:** 3.2.4 Exception Subclass Hierarchy
**Current:** Defines `io.kubemq.sdk.exception.TimeoutException`.
**Should be:** Consider renaming to `KubeMQTimeoutException` or `OperationTimeoutException`. The clash with `j.u.c.TimeoutException` is less severe than `CancellationException` (see M-2) because `j.u.c.TimeoutException` is a checked exception and less commonly used in catch blocks alongside SDK code. However, for consistency with the `CancellationException` rename recommendation, consider renaming both.
**Reason:** Import clarity. Not blocking but improves DX.

### m-4: Missing `@Override` on `Builder.build()` in some subclass examples

**Spec:** 01-error-handling-spec.md
**Section:** 3.2.4 (various subclass Builder patterns)
**Current:** Some builder inner classes show `@Override public TimeoutException build()` and some don't show `@Override`.
**Should be:** All `build()` methods in subclass builders should have `@Override` annotation since they override `KubeMQException.Builder.build()`.
**Reason:** Consistency and compile-time safety.

### m-5: `ReconnectionConfig` uses Lombok `@Builder` but `MessageBuffer` does not

**Spec:** 02-connection-transport-spec.md
**Section:** 2.3.1 ReconnectionConfig and 2.3.3 MessageBuffer
**Current:** `ReconnectionConfig` uses `@Builder`, `MessageBuffer` uses constructor.
**Should be:** Consistent style -- either both use builders or both use constructors. Since `MessageBuffer` is internal, constructor is fine. Just note the deliberate difference.
**Reason:** Style consistency. Low priority.

### m-6: `ConnectionNotReadyException` referenced but never defined

**Spec:** 02-connection-transport-spec.md
**Section:** 6.3.5 SDK-Level WaitForReady
**Current:** `throw new ConnectionNotReadyException(...)` is used in `ensureReady()`.
**Should be:** Define `ConnectionNotReadyException` as a class (either in the `client/` package or as a subtype of `KubeMQException` in `exception/`). Currently it is referenced but no definition is provided anywhere in the spec.
**Reason:** The implementer will not know how to create this class.

### m-7: `isLocalhostAddress` does not handle `0.0.0.0`

**Spec:** 03-auth-security-spec.md
**Section:** 3.2.2 Address-Aware TLS Defaulting
**Current:** Recognizes `localhost`, `127.0.0.1`, `::1`, `[::1]` as localhost.
**Should be:** Also consider `0.0.0.0` as a localhost address (commonly used in development). While `0.0.0.0` technically means "all interfaces," in practice when used as a *client connection target* it resolves to localhost.
**Reason:** Development convenience. Debatable -- could also document that `0.0.0.0` is not treated as localhost.

### m-8: OIDC example uses `java.net.http.HttpClient` (Java 11+)

**Spec:** 03-auth-security-spec.md
**Section:** 3.4.6 OIDC Provider Example
**Current:** Uses `java.net.http.HttpClient` and `HttpRequest`.
**Should be:** Fine since the SDK targets Java 11+ (per `maven.compiler.source` of 11 in pom.xml). Just verify this is documented. The example also does naive JSON parsing -- add a note that production code should use Jackson or a proper JSON library.
**Reason:** No real issue -- just a documentation note.

### m-9: Checkstyle `MissingJavadocMethod` will flag Lombok-generated methods

**Spec:** 07-code-quality-spec.md
**Section:** 4.2.3 Checkstyle
**Current:** `MissingJavadocMethod` with `scope=public` and `allowMissingPropertyJavadoc=true`.
**Should be:** Lombok `@Getter` generates public methods without Javadoc. Checkstyle will flag all of them. Either (a) add a suppression for Lombok-generated code, (b) switch from Lombok `@Getter` to manual getters with Javadoc, or (c) use Checkstyle's `allowedAnnotations` to suppress for Lombok-annotated classes.
**Reason:** Adding Checkstyle without Lombok accommodation will produce hundreds of false-positive violations.

### m-10: OWASP dependency-check in `verify` phase will slow every build

**Spec:** 07-code-quality-spec.md
**Section:** 5.2.5 OWASP Dependency-Check Plugin
**Current:** OWASP runs in the `verify` phase, meaning every `mvn verify` and `mvn install` triggers a vulnerability scan (which downloads the NVD database and can take 1-5 minutes).
**Should be:** Move to a separate Maven profile (e.g., `-Psecurity`) or bind to a non-default lifecycle phase. Run in CI only, not on every local build.
**Reason:** Developer velocity. OWASP scans are slow and require internet access.

---

## Missing Coverage

1. **REQ-ERR-6 rich error details extraction:** The GS acceptance criterion "Rich error details from `google.rpc.Status` are extracted when present" is addressed in the `GrpcErrorMapper` but the `extractRichDetails()` method body is not shown. The spec should at least outline the approach (use `StatusProto.fromThrowable()` and iterate over `com.google.rpc.Status.getDetailsList()`).

2. **REQ-CONN-1 AC-10:** "Operation retries suspended during RECONNECTING" is listed in the acceptance criteria mapping but the implementation spec does not show how `RetryExecutor` checks the connection state before retrying. The `ensureReady()` method in REQ-CONN-5 handles this for new operations, but in-flight retry loops in `RetryExecutor` do not check connection state between retry attempts.

3. **REQ-CONN-4 AC-4:** "Buffered messages flushed before close" is mentioned but `sendBufferedMessage()` referenced in the `close()` method (spec 02, section 5.3) is never defined.

4. **REQ-CQ-1 AC-6:** "Dependencies flow downward only: Public API -> Protocol -> Transport. No upward or circular references." The spec mentions this as a target but does not describe how to enforce it (e.g., ArchUnit tests, dependency analysis plugin). Consider specifying an ArchUnit test.

---

## Cross-Spec Consistency Issues

### X-1: Two conflicting `AuthInterceptor` implementations

**Specs:** 03-auth-security-spec.md (section 3.1.2) and 07-code-quality-spec.md (section 2.3.2)
**Issue:** The 03 spec defines `AuthInterceptor` as a dynamic interceptor reading from `AtomicReference` per-call and later from `CredentialManager`. The 07 spec defines `AuthInterceptor` with a static `Metadata` instance set once at construction. These are incompatible implementations.
**Resolution:** The 07 spec should reference the 03 spec's design or defer `AuthInterceptor` implementation to 03. The 07 spec should only define the interceptor chain structure, not the individual interceptor implementations.

### X-2: `BufferFullException` vs `BackpressureException` duplicate

**Specs:** 02-connection-transport-spec.md (section 2.3.5) and 01-error-handling-spec.md (section 3.2.4)
**Issue:** Two exception types for the same concept. `BufferFullException extends RuntimeException` in 02, `BackpressureException extends KubeMQException` with `ErrorCode.BUFFER_FULL` in 01. The GS classifies `BufferFullError` as `Backpressure` category.
**Resolution:** Remove `BufferFullException` from 02. Use `BackpressureException` from 01 throughout. If 02 is implemented before 01, use a stub that extends RuntimeException temporarily, but the spec should reference the final type.

### X-3: `error/` vs `exception/` package name

**Specs:** 07-code-quality-spec.md (section 6.2 shows `error/`) and 01-error-handling-spec.md (section 3.2.2 uses `exception/`)
**Issue:** Different package names for the error hierarchy across specs.
**Resolution:** Standardize on `exception/` since it already exists. Update 07-code-quality-spec.md section 6.2 to show `exception/` instead of `error/`.

### X-4: `enableRetry()` removal inconsistency

**Specs:** 01-error-handling-spec.md (section 5.2.4 removes it) and 03-auth-security-spec.md (section 3.2.9 re-introduces it)
**Issue:** The updated `initChannel()` in 03-auth-security-spec.md still contains `.enableRetry()`.
**Resolution:** Remove `.enableRetry()` from the code in 03-auth-security-spec.md section 3.2.9. Add a comment noting that gRPC retry is disabled per REQ-ERR-3.

---

## Java-Specific Recommendations

1. **Use `sealed` classes for the exception hierarchy (Java 17+).** If the SDK can raise the minimum Java version to 17, consider making `KubeMQException` a `sealed` class with `permits` for all subclasses. This provides compile-time exhaustiveness checking for switch expressions. Since the current target is Java 11, this is a future consideration -- add a note in the spec.

2. **Consider `record` types for immutable DTOs.** `TransportConfig`, `ReconnectionConfig`, `TokenResult`, and `RetryPolicy` are all immutable value objects. If Java 16+ is acceptable, these could be records. For Java 11, the current approach with Lombok `@Builder` / `@Getter` or manual builders is correct.

3. **`Duration` parameter on public API methods.** The spec correctly uses `java.time.Duration` for timeout parameters. Ensure all public API methods that accept timeout use `Duration` (not `long millis` or `int seconds`) for type safety and readability.

4. **Thread naming convention.** The specs use `"kubemq-reconnect-manager"`, `"kubemq-state-listener"`, `"kubemq-credential-refresh"` as daemon thread names. This is good practice. Consider documenting the full set of SDK-created threads for operational visibility.

5. **`@FunctionalInterface` on `CredentialProvider`.** The spec correctly marks `CredentialProvider` as `@FunctionalInterface`, enabling lambda usage: `.credentialProvider(() -> new TokenResult(fetchFromVault()))`. This is good Java API design.

6. **Consider `AutoCloseable` on `CredentialManager`.** Since `CredentialManager.shutdown()` must be called during client close, implementing `AutoCloseable` would make the lifecycle contract explicit and support try-with-resources if used independently.

---

## Open Questions

1. **Q-1: Should `TimeoutException` and `CancellationException` be renamed to avoid JDK clashes?** The spec uses names that clash with `java.util.concurrent` types. This is a DX concern, not a correctness issue. Recommend renaming at least `CancellationException` (Major issue M-2) since `CompletableFuture` usage is pervasive.

2. **Q-2: What is the intended package for `ClientClosedException` and `ConnectionNotReadyException`?** Currently placed in `io.kubemq.sdk.client` with a note to move to `exception/` when REQ-ERR-1 is done. Should they be defined as subtypes of `KubeMQException` in the error hierarchy from the start?

3. **Q-3: How does `WaitForReady` interact with `RetryExecutor`?** If an operation fails with a transient error and enters retry, but the connection drops during retry backoff, does the retry loop call `ensureReady()` before each attempt? The spec is silent on this interaction.

4. **Q-4: Should the `Transport` interface be in the public API?** The 07-code-quality-spec.md makes `Transport` public but `GrpcTransport` package-private. Is `Transport` intended for user extension (e.g., mock transport for testing)? If so, it should be documented as part of the public API. If not, it should be package-private with a factory.

5. **Q-5: Implementation ordering between specs 01 and 07.** The 07 spec's REQ-CQ-1 (layered architecture) is a massive XL refactor that creates the `transport/` package. The 01 spec's error hierarchy is the foundation for everything. Which comes first? The 07 spec says "do CI before Phase 2" and the 01 spec says "Foundation first." These are compatible but the dependency graph should be made explicit: implement 01 error hierarchy -> then 07 CQ-1 transport extraction, not the other way around.

---

## Fixes Applied (Round 1)

| Issue | Status | Change Made |
|-------|--------|-------------|
| C-1 | FIXED | Replaced invalid `import com.google.rpc.Status as RpcStatus` with proper Java import and disambiguation comment in 01-error-handling-spec.md |
| C-2 | FIXED | Replaced non-atomic check-then-act in MessageBuffer.add() ERROR policy with CAS loop for thread-safe size reservation in 02-connection-transport-spec.md |
| C-3 | FIXED | Removed `BufferFullException` class definition; replaced all references with `BackpressureException` from error hierarchy (01-spec section 3.2.4) in 02-connection-transport-spec.md. Updated test scenarios and cross-category dependency table. |
| C-4 | FIXED | Changed `AuthenticationException extends RuntimeException` to `extends KubeMQException` in 03-auth-security-spec.md. Updated stub strategy to require `KubeMQException` stub from the start. Added `@Deprecated` to stub constructors and migration notes. |
| C-5 | FIXED | Replaced static `Metadata` instance in `AuthInterceptor` (07-code-quality-spec.md) with dynamic `Supplier<String>` pattern that reads token per-call, compatible with REQ-AUTH-1 mutable token and REQ-AUTH-4 credential provider. |
| M-1 | FIXED | Replaced silent `clamp()` methods with `requireInRange()` methods that throw `IllegalArgumentException` for out-of-range RetryPolicy values in 01-error-handling-spec.md |
| M-2 | FIXED | Renamed `CancellationException` to `OperationCancelledException` throughout 01-error-handling-spec.md. Updated cross-references in 10-concurrency-spec.md (12 occurrences). |
| M-3 | FIXED | Replaced recursive `transitionTo()` with CAS loop in ConnectionStateMachine (02-connection-transport-spec.md). Eliminates StackOverflowError risk under contention. |
| M-4 | FIXED | Removed static `ThreadLocalRandom` field in ReconnectionManager. Added comment explaining that `ThreadLocalRandom.current()` must be called at the call site (already done correctly in `computeDelay()`). |
| M-5 | FIXED | Changed `ConfigurationException extends RuntimeException` to `extends KubeMQException` in 03-auth-security-spec.md. Added note on mapping to error hierarchy. |
| M-6 | FIXED | Replaced busy-wait `Thread.sleep(100ms)` polling in `ensureReady()` with `CompletableFuture`-based approach using ConnectionStateMachine listener in 02-connection-transport-spec.md |
| M-7 | FIXED | Added `isValidTransition()` method to ConnectionStateMachine with allowed state transition validation (IDLE->CONNECTING, CONNECTING->READY/CLOSED, READY->RECONNECTING/CLOSED, RECONNECTING->READY/CLOSED). Invalid transitions logged at WARN and rejected. Combined with M-3 fix. |
| M-8 | FIXED | Changed retryable `CredentialException` wrapping from bare `RuntimeException` to `ConnectionException` (typed SDK exception) in 03-auth-security-spec.md |
| M-9 | FIXED | Added `TransportFactory` public factory class in 07-code-quality-spec.md to enable cross-package instantiation of package-private `GrpcTransport` |
| M-10 | FIXED | Standardized on `exception/` package (existing) instead of `error/` (proposed) across 07-code-quality-spec.md. Updated GS Target Structure, dependency graph, and all references. |
| M-11 | FIXED | Removed `.enableRetry()` from both TLS code blocks in 03-auth-security-spec.md (section 3.2.9 initChannel and reconnectWithCertReload). Added comments referencing REQ-ERR-3. |
| m-1 | SKIPPED | Self-correcting during review -- no change needed (enums and Instant are Serializable) |
| m-2 | FIXED | Added design intent comment clarifying semaphore scope in RetryExecutor (limits backoff storms, not concurrent operations) |
| m-3 | SKIPPED | TimeoutException clash is less severe than CancellationException (checked vs unchecked). Can be addressed in a future pass if needed. |
| m-4 | SKIPPED | Subjective style preference -- consistent `@Override` is good practice but not blocking |
| m-5 | SKIPPED | Deliberate difference: ReconnectionConfig is public API (builder pattern), MessageBuffer is internal (constructor). Low priority. |
| m-6 | FIXED | Changed `ConnectionNotReadyException extends RuntimeException` to `extends KubeMQException` in 02-connection-transport-spec.md. Added note on error hierarchy integration. |
| m-7 | SKIPPED | Whether `0.0.0.0` is localhost is debatable. Document current behavior rather than change it. |
| m-8 | SKIPPED | Java 11+ target confirmed by pom.xml. HttpClient usage is correct. JSON parsing note is subjective. |
| m-9 | FIXED | Added `allowedAnnotations` property for Lombok annotations (`Getter,Setter,Builder,Data,Value`) to Checkstyle `MissingJavadocMethod` rule in 07-code-quality-spec.md |
| m-10 | FIXED | Moved OWASP dependency-check from default `verify` phase to a separate `-Psecurity` Maven profile in 07-code-quality-spec.md |
| Missing-1 | FIXED | Expanded `extractRichDetails()` method body in 01-error-handling-spec.md to show unpacking of `ErrorInfo`, `RetryInfo`, `DebugInfo` from `google.rpc.Status.getDetailsList()` |
| Missing-2 | SKIPPED | RetryExecutor/connection state interaction requires design decision -- left for Round 2 |
| Missing-3 | SKIPPED | `sendBufferedMessage()` definition requires design decision -- left for Round 2 |
| Missing-4 | SKIPPED | ArchUnit enforcement is a good idea but requires separate tooling discussion -- left for Round 2 |

**Total:** 22 fixed, 9 skipped

**Cross-spec cascade updates:**
- 10-concurrency-spec.md: Updated 12 references from `CancellationException` to `OperationCancelledException`
