# Category 03: Auth & Security -- Java SDK Gap Close Specification

**Version:** 1.0
**Date:** 2026-03-09
**Status:** Draft
**Golden Standard:** `clients/golden-standard/03-auth-security.md`
**Gap Research:** `clients/gap-research/java-gap-research.md` (lines 473-625)
**Assessment:** `clients/assesments/JAVA_ASSESSMENT_REPORT.md` (Category 5, lines 348-375)
**GS Reviews:** `clients/golden-standard/reviews/03-auth-security-decisions.md`
**Review Rounds:** `clients/gap-research/java-review-r1.md` (C-5, M-8, m-6), `clients/gap-research/java-review-r2.md`

---

## Scope

This specification covers all 6 requirements in GS Category 03 (Auth & Security):

| REQ | Title | Gap Status | Priority | Effort | Section |
|-----|-------|-----------|----------|--------|---------|
| REQ-AUTH-1 | Token Authentication | PARTIAL | P0 | S | 3.1 |
| REQ-AUTH-2 | TLS Encryption | PARTIAL | P0 | M | 3.2 |
| REQ-AUTH-3 | Mutual TLS (mTLS) | PARTIAL | P0 | M | 3.3 |
| REQ-AUTH-4 | Credential Provider Interface | NOT_ASSESSED | P0 | L | 3.4 |
| REQ-AUTH-5 | Security Best Practices | PARTIAL | P1 | S | 3.5 |
| REQ-AUTH-6 | TLS Credentials During Reconnection | NOT_ASSESSED | P0 | M | 3.6 |

**Implementation order:** REQ-AUTH-1 -> REQ-AUTH-2 -> REQ-AUTH-3 -> REQ-AUTH-5 -> REQ-AUTH-4 -> REQ-AUTH-6

---

## Cross-Category Dependencies

### Inbound (this spec depends on)

| Dependency | From Spec | Reason | Blocking? |
|-----------|-----------|--------|-----------|
| REQ-ERR-1 (typed error hierarchy) | 01-error-handling-spec.md | `AuthenticationException`, `ConfigurationException`, `TransientException` types needed for error classification in AUTH-1, AUTH-2, AUTH-3, AUTH-4 | Yes -- implement error hierarchy first or define stub exception classes |
| REQ-CONN-1 (reconnection manager) | 02-connection-transport-spec.md | AUTH-6 requires hook into reconnection lifecycle to reload certs | Yes for AUTH-6 only |
| REQ-CONN-2 (connection state machine) | 02-connection-transport-spec.md | AUTH-4 credential provider must be invoked during CONNECTING and RECONNECTING states | Yes for AUTH-4 only |

### Outbound (other specs depend on this)

| Dependency | To Spec | Reason |
|-----------|---------|--------|
| REQ-OBS-1 | 05-observability-spec.md | OTel spans must not include auth tokens (REQ-AUTH-5) |
| REQ-CQ-7 | 07-code-quality-spec.md | Credential exclusion from toString(), error messages |
| REQ-TEST-1 | 04-testing-spec.md | Auth failure integration tests |

### Stub Strategy for Blocked Dependencies

If REQ-ERR-1 is not yet implemented, create minimal stub exception classes in `io.kubemq.sdk.exception/` that **already extend `KubeMQException`** (using a minimal `KubeMQException` stub). This ensures callers write `catch (AuthenticationException e)` against the correct parent class from the start, avoiding a breaking change when REQ-ERR-1 is fully implemented.

```java
// Minimal KubeMQException stub (if REQ-ERR-1 not yet implemented)
package io.kubemq.sdk.exception;
public class KubeMQException extends RuntimeException {
    public KubeMQException(String message) { super(message); }
    public KubeMQException(String message, Throwable cause) { super(message, cause); }
}

// Stub AuthenticationException -- extends KubeMQException from the start
package io.kubemq.sdk.exception;
public class AuthenticationException extends KubeMQException {
    public AuthenticationException(String message) { super(message); }
    public AuthenticationException(String message, Throwable cause) { super(message, cause); }
}

// Stub ConfigurationException -- extends KubeMQException from the start
package io.kubemq.sdk.exception;
public class ConfigurationException extends KubeMQException {
    public ConfigurationException(String message) { super(message); }
    public ConfigurationException(String message, Throwable cause) { super(message, cause); }
}

// Stub ConnectionException -- extends KubeMQException from the start.
// Used by CredentialManager.refreshToken() for retryable credential failures
// and by classifyTlsException() for network errors during TLS handshake.
package io.kubemq.sdk.exception;
public class ConnectionException extends KubeMQException {
    public ConnectionException(String message) { super(message); }
    public ConnectionException(String message, Throwable cause) { super(message, cause); }
}
```

**Important:** The stub `AuthenticationException` has a DIFFERENT signature from the final class in 01-error-handling-spec.md (which uses a builder pattern with `ErrorCode`, `ErrorCategory`, etc.). When REQ-ERR-1 is implemented, the stub constructors will be replaced by the builder pattern. Callers using `catch (AuthenticationException e)` will not break since the class name and parent hierarchy are preserved. However, callers using `new AuthenticationException(message)` will need to migrate to the builder -- add `@Deprecated` to the stub constructors to signal this.

These stubs allow AUTH-1 through AUTH-5 to proceed without waiting for the full error hierarchy.

---

## 3.1 REQ-AUTH-1: Token Authentication

**Gap Status:** PARTIAL
**Priority:** P0
**Effort:** S (< 1 day)

### Current State

- **COMPLIANT:** Static token via `authToken` builder parameter (line 42 of `KubeMQClient.java`)
- **COMPLIANT:** Token sent as gRPC metadata "authorization" header via `MetadataInterceptor` (lines 431-449 of `KubeMQClient.java`)
- **COMPLIANT:** Token never logged (assessment 5.2.2)
- **MISSING:** Token cannot be updated without recreating client (assessment 5.1.2)
- **MISSING:** No `AuthenticationException` type for missing/rejected tokens

### Acceptance Criteria Checklist

| # | Criterion | Status | Action |
|---|-----------|--------|--------|
| 1 | Static token via client options/builder | COMPLIANT | None |
| 2 | Token sent as gRPC metadata on every request | COMPLIANT | None |
| 3 | Token updatable without recreating client | MISSING | Implement |
| 4 | Missing token produces clear AuthenticationError | MISSING | Implement |
| 5 | Token never logged (even at DEBUG) | COMPLIANT | None |

### Implementation

#### 3.1.1 Mutable Token via AtomicReference

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

**Current code (line 42):**
```java
private  String authToken;
```

**Change to:**
```java
private final AtomicReference<String> authTokenRef = new AtomicReference<>();
```

**Required import:**
```java
import java.util.concurrent.atomic.AtomicReference;
```

**Add public method:**
```java
/**
 * Updates the authentication token used for all subsequent requests.
 * Thread-safe: can be called from any thread without synchronization.
 * The new token takes effect on the next gRPC call.
 *
 * @param token the new authentication token, or null to clear authentication
 */
public void setAuthToken(String token) {
    this.authTokenRef.set(token);
    log.debug("Auth token updated, token_present: {}", token != null && !token.isEmpty());
}
```

**Backward compatibility note:** The existing `getAuthToken()` method (generated by Lombok `@Getter`) must be preserved. Replace `@Getter` on the `authToken` field with a manual getter:

```java
/**
 * Returns the current authentication token.
 *
 * @return the current auth token, or null if not set
 */
public String getAuthToken() {
    return authTokenRef.get();
}
```

#### 3.1.2 Update MetadataInterceptor to Read from AtomicReference

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

Replace the current `MetadataInterceptor` (lines 431-449) with a dynamic version that reads the token on each call:

```java
/**
 * gRPC client interceptor that injects authentication metadata on every call.
 * Reads the token from the client's AtomicReference, so token updates
 * are reflected immediately without re-creating the interceptor.
 */
private class AuthInterceptor implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String token = authTokenRef.get();
                if (token != null && !token.isEmpty()) {
                    Metadata.Key<String> key = Metadata.Key.of("authorization",
                            Metadata.ASCII_STRING_MARSHALLER);
                    headers.put(key, token);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
```

**Changes to `initChannel()` (lines 261-325):**

Remove the conditional metadata setup (lines 263-267) and the conditional interceptor application (lines 311-319). The `AuthInterceptor` is always applied:

```java
private void initChannel() {
    log.debug("Constructing channel to KubeMQ on {}", address);

    // ... channel construction (TLS or plaintext) stays the same ...

    // Always apply AuthInterceptor -- it checks token presence on each call
    ClientInterceptor authInterceptor = new AuthInterceptor();
    Channel channel = ClientInterceptors.intercept(managedChannel, authInterceptor);
    this.blockingStub = kubemqGrpc.newBlockingStub(channel);
    this.asyncStub = kubemqGrpc.newStub(channel);

    addChannelStateListener();
    log.debug("Client initialized for KubeMQ address: {}, token_present: {}",
              address, authTokenRef.get() != null && !authTokenRef.get().isEmpty());
}
```

Remove the `metadata` field entirely (line 71). The `Metadata` instance is no longer stored as state.

#### 3.1.3 AuthenticationException on UNAUTHENTICATED

When the gRPC server returns `Status.UNAUTHENTICATED`, throw an `AuthenticationException` with an actionable message.

**New file:** `kubemq-java/src/main/java/io/kubemq/sdk/exception/AuthenticationException.java`

```java
package io.kubemq.sdk.exception;

/**
 * Thrown when authentication fails -- either the server returned UNAUTHENTICATED
 * or no token was provided when the server requires one.
 *
 * <p>This exception is non-retryable. To resolve, provide or refresh the auth token
 * via {@code client.setAuthToken()} or configure a {@code CredentialProvider}.
 */
public class AuthenticationException extends KubeMQException {

    @Deprecated // Use builder pattern when REQ-ERR-1 is fully implemented
    public AuthenticationException(String message) {
        super(message);
    }

    @Deprecated // Use builder pattern when REQ-ERR-1 is fully implemented
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**Where to catch:** In every location that currently catches `StatusRuntimeException` and re-throws as `RuntimeException`. For example, in `PubSubClient.sendEventsMessage()` (line 43), `CQClient.sendCommandRequest()` (line 35), and `QueuesClient` methods. A centralized approach is to add detection in a response interceptor (see 3.2 for interceptor chain enhancement), but for AUTH-1 alone, add a utility method:

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

```java
/**
 * Checks a StatusRuntimeException for UNAUTHENTICATED status and wraps it
 * as an AuthenticationException with an actionable message.
 *
 * @param e the gRPC exception to check
 * @return an AuthenticationException if the status is UNAUTHENTICATED, otherwise the original exception wrapped in RuntimeException
 */
protected RuntimeException wrapGrpcException(StatusRuntimeException e) {
    if (e.getStatus().getCode() == Status.Code.UNAUTHENTICATED) {
        String token = authTokenRef.get();
        String hint = (token == null || token.isEmpty())
            ? "Server requires authentication. Set auth token via builder.authToken() or provide a CredentialProvider."
            : "Authentication token was rejected by the server. Verify the token is valid and not expired.";
        return new AuthenticationException(hint, e);
    }
    // Other status codes will be handled by REQ-ERR-6 (gRPC status mapping)
    return new RuntimeException(e);
}
```

#### 3.1.4 Constructor Update

Update the constructor (line 90-121) to initialize `authTokenRef`:

```java
// In constructor body, replace:
//   this.authToken = authToken;
// with:
this.authTokenRef.set(authToken);
```

### Breaking Changes

- **MetadataInterceptor renamed to AuthInterceptor:** If any user code references `MetadataInterceptor` via reflection (unlikely -- it's a public inner class), this is a breaking change. Since the class is marked `public` but is an internal implementation detail, document this in CHANGELOG.
- **`metadata` field removed:** The `getMetadata()` getter (from `@Getter`) will no longer exist. This is a breaking change if any user code accesses it directly. Mitigate by keeping a deprecated getter that returns null or by converting to package-private first.

### Tests

| # | Test | Type | Description |
|---|------|------|-------------|
| 1 | `testTokenUpdateReflectedInNextCall` | Unit | Set token A, verify interceptor sends A. Call `setAuthToken(B)`, verify next call sends B. |
| 2 | `testTokenUpdateThreadSafe` | Unit | 10 threads simultaneously calling `setAuthToken()` and sending requests. No exceptions, no mixed tokens. |
| 3 | `testNullTokenSkipsHeader` | Unit | Set token to null. Verify "authorization" header is not set in gRPC metadata. |
| 4 | `testUnauthenticatedThrowsAuthenticationException` | Unit | Mock gRPC stub to return `UNAUTHENTICATED`. Verify `AuthenticationException` is thrown with actionable message. |
| 5 | `testUnauthenticatedWithNoTokenGivesSetTokenHint` | Unit | No token set, UNAUTHENTICATED response. Message says "Set auth token via builder.authToken()". |
| 6 | `testUnauthenticatedWithTokenGivesVerifyHint` | Unit | Token set, UNAUTHENTICATED response. Message says "Verify the token is valid". |
| 7 | `testTokenNotLoggedAtAnyLevel` | Unit | Set TRACE level. Call setAuthToken. Verify log output contains "token_present: true" but never the token value. |

---

## 3.2 REQ-AUTH-2: TLS Encryption

**Gap Status:** PARTIAL
**Priority:** P0
**Effort:** M (2-3 days)

### Current State

- **COMPLIANT:** TLS enabled with `.tls(true)` (line 270 of `KubeMQClient.java`)
- **PARTIAL:** Custom CA via file path only, no PEM bytes (line 280)
- **MISSING:** Address-aware TLS defaulting (assessment 5.2.1 -- plaintext for all addresses)
- **MISSING:** Server name override
- **MISSING:** `InsecureSkipVerify` as a separately named option
- **MISSING:** WARNING log for disabled cert verification
- **MISSING:** TLS 1.2 minimum enforcement (assessment 3.3.4)
- **NOT_ASSESSED:** System CA bundle usage (likely works via JDK defaults but not verified)
- **MISSING:** TLS handshake failure classification

### Acceptance Criteria Checklist

| # | Criterion | Status | Action |
|---|-----------|--------|--------|
| 1 | TLS default based on address (false for localhost, true for remote) | MISSING | Implement |
| 2 | TLS enabled with single option | COMPLIANT | None |
| 3 | Custom CA certificates (file path or PEM bytes) | PARTIAL | Add PEM bytes |
| 4 | Server name override supported | MISSING | Implement |
| 5 | InsecureSkipVerify as separately named option | MISSING | Implement |
| 6 | SDK logs WARNING for disabled cert verification | MISSING | Implement |
| 7 | TLS 1.2 minimum enforced | MISSING | Implement |
| 8 | System CA bundle used by default | NOT_ASSESSED | Verify/implement |
| 9 | TLS handshake failures classified | MISSING | Implement |

### Implementation

#### 3.2.1 New Builder Fields

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

Add new fields after the existing TLS fields (after line 46):

```java
private boolean tlsAutoDetect;       // true if tls was not explicitly set
private byte[] caCertPem;            // CA certificate as PEM bytes (alternative to caCertFile)
private byte[] tlsCertPem;           // Client certificate as PEM bytes
private byte[] tlsKeyPem;            // Client private key as PEM bytes
private String serverNameOverride;   // Override for TLS server name verification
private boolean insecureSkipVerify;  // Disable certificate verification (dev only)
```

#### 3.2.2 Address-Aware TLS Defaulting

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

Add a static utility method:

```java
/**
 * Determines whether the given address is a localhost address.
 * Localhost addresses: "localhost", "127.0.0.1", "::1", "[::1]".
 * All other addresses (including Kubernetes DNS names like
 * "kubemq.default.svc.cluster.local") are considered remote.
 *
 * @param address the target address (host:port format)
 * @return true if the address is localhost
 */
static boolean isLocalhostAddress(String address) {
    if (address == null) {
        return false;
    }
    // Strip port if present
    String host = address;
    int lastColon = address.lastIndexOf(':');
    if (lastColon > 0 && !address.endsWith("]")) {
        // IPv4 or hostname:port
        host = address.substring(0, lastColon);
    } else if (address.startsWith("[")) {
        // IPv6 [::1]:port format
        int bracket = address.indexOf(']');
        if (bracket > 0) {
            host = address.substring(0, bracket + 1);
        }
    }
    return "localhost".equalsIgnoreCase(host)
        || "127.0.0.1".equals(host)
        || "::1".equals(host)
        || "[::1]".equals(host);
}
```

**In the constructor,** after validating address and clientId, apply TLS defaulting:

```java
// Address-aware TLS default: false for localhost, true for remote
if (tlsAutoDetect) {
    this.tls = !isLocalhostAddress(address);
    if (this.tls) {
        log.info("TLS enabled by default for remote address: {}", address);
    }
}
```

**Builder integration:** Each subclass builder (`PubSubClient.builder()`, `CQClient.builder()`, `QueuesClient.builder()`) must pass a flag indicating whether `.tls()` was explicitly called. With Lombok `@Builder`, this requires a custom builder that tracks explicit calls. The simplest approach: change the `tls` field type to `Boolean` (nullable), and treat `null` as "auto-detect":

```java
// In constructor:
if (tls == null) {
    this.tls = !isLocalhostAddress(address);
    this.tlsAutoDetect = true;
    if (this.tls) {
        log.info("TLS enabled by default for remote address: {}", address);
    }
} else {
    this.tls = tls;
    this.tlsAutoDetect = false;
}
```

**Breaking change:** Change `tls` from `boolean` to `Boolean`. Existing code using `.tls(true)` or `.tls(false)` is unaffected because Java autoboxes. Code that reads `isTls()` (primitive return) needs to become `getTls()` (object return). To minimize breakage, keep a convenience method:

```java
public boolean isTls() {
    return Boolean.TRUE.equals(this.tls);
}
```

#### 3.2.3 PEM Bytes API

Add overloaded builder parameters for PEM bytes. In the constructor, validate that file paths and PEM bytes are not both provided for the same credential:

```java
// In validateTlsConfiguration():
if (caCertFile != null && !caCertFile.isEmpty() && caCertPem != null && caCertPem.length > 0) {
    throw new IllegalArgumentException(
        "Cannot specify both caCertFile and caCertPem. Use one or the other.");
}
if (tlsCertFile != null && !tlsCertFile.isEmpty() && tlsCertPem != null && tlsCertPem.length > 0) {
    throw new IllegalArgumentException(
        "Cannot specify both tlsCertFile and tlsCertPem. Use one or the other.");
}
if (tlsKeyFile != null && !tlsKeyFile.isEmpty() && tlsKeyPem != null && tlsKeyPem.length > 0) {
    throw new IllegalArgumentException(
        "Cannot specify both tlsKeyFile and tlsKeyPem. Use one or the other.");
}
```

**In `initChannel()` TLS block,** update `SslContextBuilder` to accept PEM bytes:

```java
SslContextBuilder sslContextBuilder = SslContextBuilder.forClient();

// CA certificate
if (caCertPem != null && caCertPem.length > 0) {
    sslContextBuilder.trustManager(new ByteArrayInputStream(caCertPem));
} else if (caCertFile != null && !caCertFile.isEmpty()) {
    sslContextBuilder.trustManager(new File(caCertFile));
}
// else: system CA bundle is used by default (JDK TrustManagerFactory default)

// Client cert + key for mTLS (see 3.3)
if (tlsCertPem != null && tlsCertPem.length > 0 && tlsKeyPem != null && tlsKeyPem.length > 0) {
    sslContextBuilder.keyManager(
        new ByteArrayInputStream(tlsCertPem),
        new ByteArrayInputStream(tlsKeyPem));
} else if (tlsCertFile != null && tlsKeyFile != null) {
    sslContextBuilder.keyManager(new File(tlsCertFile), new File(tlsKeyFile));
}
```

**Required import:**
```java
import java.io.ByteArrayInputStream;
```

#### 3.2.4 Server Name Override

**In the TLS block of `initChannel()`:**

```java
if (serverNameOverride != null && !serverNameOverride.isEmpty()) {
    ncb = ncb.overrideAuthority(serverNameOverride);
}
```

`NettyChannelBuilder.overrideAuthority()` sets the TLS SNI and hostname verification target.

#### 3.2.5 InsecureSkipVerify

**In the TLS block of `initChannel()`,** before `sslContextBuilder.build()`:

```java
if (insecureSkipVerify) {
    log.warn("certificate verification is disabled -- this is insecure and should only be used in development");
    sslContextBuilder.trustManager(
        io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE);
}
```

The warning MUST contain the exact string "certificate verification is disabled" per GS.

**Per-connection warning:** Since gRPC manages the underlying connection lifecycle (including reconnects), and we log this during `initChannel()` which is called once at construction, we must also log on reconnection. When REQ-AUTH-6 adds cert reload on reconnection (section 3.6), the warning will be logged there too. For now, the construction-time warning satisfies the acceptance criterion because `initChannel()` is the only connection establishment point.

#### 3.2.6 TLS 1.2 Minimum

**In the TLS block of `initChannel()`:**

```java
sslContextBuilder.protocols("TLSv1.3", "TLSv1.2");
```

This restricts the SSL context to TLS 1.2 and 1.3 only. TLS 1.0 and 1.1 are excluded.

#### 3.2.7 System CA Bundle Default

When TLS is enabled and no custom CA is provided (`caCertFile` is null and `caCertPem` is null), do NOT call `sslContextBuilder.trustManager(...)`. The Netty `SslContextBuilder` defaults to the JDK `TrustManagerFactory`, which uses the system CA bundle (`$JAVA_HOME/lib/security/cacerts`).

This is already the current behavior when `caCertFile` is null (the `if` block on line 279-281 is skipped). **No code change needed** -- just verify with a test.

#### 3.2.8 TLS Handshake Failure Classification

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

Replace the current catch block in `initChannel()` TLS section (lines 294-297):

```java
} catch (SSLException e) {
    throw classifyTlsException(e);
}
```

Add the classification method:

```java
/**
 * Classifies TLS/SSL exceptions into the appropriate SDK exception type.
 *
 * <ul>
 *   <li>Certificate validation failures (expired, untrusted, hostname mismatch)
 *       -> AuthenticationException (non-retryable)</li>
 *   <li>Network errors during handshake -> TransientException (retryable)</li>
 *   <li>TLS version/cipher negotiation failures -> ConfigurationException (non-retryable)</li>
 * </ul>
 *
 * @param e the SSL exception to classify
 * @return the classified RuntimeException
 */
protected RuntimeException classifyTlsException(SSLException e) {
    String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
    Throwable cause = e.getCause();

    // Certificate validation failures
    if (e instanceof javax.net.ssl.SSLHandshakeException) {
        if (message.contains("certificate") || message.contains("cert")
                || message.contains("trust") || message.contains("expired")
                || message.contains("hostname") || message.contains("peer not authenticated")) {
            return new AuthenticationException(
                "TLS handshake failed due to certificate validation: " + e.getMessage(), e);
        }
        // Negotiation failures
        if (message.contains("protocol") || message.contains("cipher")
                || message.contains("no common") || message.contains("version")) {
            return new ConfigurationException(
                "TLS handshake failed due to protocol/cipher negotiation: " + e.getMessage()
                + ". Verify TLS version compatibility (minimum TLS 1.2).", e);
        }
    }

    // Network errors -- retryable (transient)
    if (cause instanceof java.io.IOException
            || message.contains("connection reset") || message.contains("broken pipe")) {
        // Use ConnectionException stub (added to stub strategy above) for transient errors.
        // When REQ-ERR-1 is fully implemented, this becomes:
        //   ConnectionException.builder().code(ErrorCode.CONNECTION_FAILED).retryable(true)...
        return new ConnectionException("TLS handshake failed due to network error: " + e.getMessage(), e);
    }

    // Default: treat as authentication error (most common TLS failures are cert-related)
    return new AuthenticationException("TLS handshake failed: " + e.getMessage(), e);
}
```

**New file:** `kubemq-java/src/main/java/io/kubemq/sdk/exception/ConfigurationException.java`

```java
package io.kubemq.sdk.exception;

/**
 * Thrown when the SDK detects a configuration error that cannot be resolved
 * at runtime (e.g., TLS version mismatch, invalid option combinations).
 * Non-retryable.
 */
public class ConfigurationException extends KubeMQException {

    @Deprecated // Use builder pattern when REQ-ERR-1 is fully implemented
    public ConfigurationException(String message) {
        super(message);
    }

    @Deprecated // Use builder pattern when REQ-ERR-1 is fully implemented
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**Note:** `ConfigurationException` maps to `ValidationException` with `ErrorCode.FAILED_PRECONDITION` in the 01-error-handling-spec.md hierarchy. When REQ-ERR-1 is fully implemented, consider whether to keep `ConfigurationException` as a distinct subclass of `KubeMQException` (recommended for clarity) or map all configuration errors to `ValidationException`. If kept as a distinct class, add it to the exception hierarchy table in 01-error-handling-spec.md.

#### 3.2.9 Updated initChannel() -- Full TLS Block

Here is the complete TLS branch of `initChannel()` with all changes integrated:

```java
if (isTls()) {
    try {
        NettyChannelBuilder ncb = NettyChannelBuilder.forTarget(address)
                .negotiationType(NegotiationType.TLS)
                .maxInboundMessageSize(maxReceiveSize);
                // NOTE: .enableRetry() intentionally removed per REQ-ERR-3 (01-error-handling-spec.md
                // section 5.2.4) to prevent double-retry amplification. SDK-level retry is used instead.

        // Server name override for mismatched certificates
        if (serverNameOverride != null && !serverNameOverride.isEmpty()) {
            ncb = ncb.overrideAuthority(serverNameOverride);
        }

        SslContextBuilder sslContextBuilder = SslContextBuilder.forClient();

        // TLS 1.2 minimum (HTTP/2 requires it)
        sslContextBuilder.protocols("TLSv1.3", "TLSv1.2");

        // InsecureSkipVerify
        if (insecureSkipVerify) {
            log.warn("certificate verification is disabled -- "
                     + "this is insecure and should only be used in development");
            sslContextBuilder.trustManager(
                io.grpc.netty.shaded.io.netty.handler.ssl.util
                    .InsecureTrustManagerFactory.INSTANCE);
        } else {
            // CA certificate (file or PEM bytes; system CA used if neither provided)
            if (caCertPem != null && caCertPem.length > 0) {
                sslContextBuilder.trustManager(new ByteArrayInputStream(caCertPem));
            } else if (caCertFile != null && !caCertFile.isEmpty()) {
                sslContextBuilder.trustManager(new File(caCertFile));
            }
            // else: JDK default TrustManagerFactory uses system CA bundle
        }

        // Client cert + key for mTLS
        if (tlsCertPem != null && tlsCertPem.length > 0
                && tlsKeyPem != null && tlsKeyPem.length > 0) {
            sslContextBuilder.keyManager(
                new ByteArrayInputStream(tlsCertPem),
                new ByteArrayInputStream(tlsKeyPem));
        } else if (tlsCertFile != null && !tlsCertFile.isEmpty()
                && tlsKeyFile != null && !tlsKeyFile.isEmpty()) {
            sslContextBuilder.keyManager(new File(tlsCertFile), new File(tlsKeyFile));
        }

        SslContext sslContext = sslContextBuilder.build();
        ncb = ncb.sslContext(sslContext);

        if (keepAlive != null) {
            ncb = ncb.keepAliveTime(
                        pingIntervalInSeconds == 0 ? 60 : pingIntervalInSeconds, TimeUnit.SECONDS)
                    .keepAliveTimeout(
                        pingTimeoutInSeconds == 0 ? 30 : pingTimeoutInSeconds, TimeUnit.SECONDS)
                    .keepAliveWithoutCalls(keepAlive);
        }

        managedChannel = ncb.build();
    } catch (SSLException e) {
        throw classifyTlsException(e);
    }
}
```

### GS Internal Inconsistency

**TLS default behavior:** The GS says "default: false for localhost, true for remote" in REQ-AUTH-2. However, it also says "TLS can be enabled with a single option (e.g., `WithTLS(true)`)" which implies the user must opt in. The address-aware default makes the `WithTLS` option a way to override the default, not the only way to enable TLS. This is intentional per the GS but may surprise users migrating from v2.1.x where `tls` defaults to `false` for all addresses.

**Migration note:** Document in CHANGELOG that remote addresses now default to TLS. Users connecting to KubeMQ servers without TLS on remote addresses must explicitly set `.tls(false)`.

### Tests

| # | Test | Type | Description |
|---|------|------|-------------|
| 1 | `testLocalhostAddressDetection` | Unit | Parameterized: "localhost:50000" -> true, "127.0.0.1:50000" -> true, "::1" -> true, "[::1]:50000" -> true, "kubemq.default.svc.cluster.local:50000" -> false, "10.0.0.1:50000" -> false |
| 2 | `testTlsDefaultsToFalseForLocalhost` | Unit | Builder with address "localhost:50000", no `.tls()` call. Assert `isTls()` == false. |
| 3 | `testTlsDefaultsToTrueForRemote` | Unit | Builder with address "kubemq.prod:50000", no `.tls()` call. Assert `isTls()` == true. |
| 4 | `testExplicitTlsFalseOverridesDefault` | Unit | Builder with address "kubemq.prod:50000", `.tls(false)`. Assert `isTls()` == false. |
| 5 | `testInsecureSkipVerifyLogsWarning` | Unit | Enable `insecureSkipVerify`. Capture log output. Assert contains "certificate verification is disabled". |
| 6 | `testInsecureSkipVerifyWarningOnEveryConnection` | Unit | Track that the warning is logged during `initChannel()`. |
| 7 | `testPemBytesCA` | Unit | Provide `caCertPem` with valid PEM bytes. Verify `SslContextBuilder.trustManager(InputStream)` is called. |
| 8 | `testBothFileAndPemThrows` | Unit | Provide both `caCertFile` and `caCertPem`. Assert `IllegalArgumentException`. |
| 9 | `testServerNameOverride` | Unit | Set `serverNameOverride`. Verify `overrideAuthority()` called on channel builder. |
| 10 | `testTlsHandshakeFailureClassification` | Unit | Mock various `SSLException` subtypes. Verify correct exception type returned by `classifyTlsException()`. |
| 11 | `testTls12MinimumEnforced` | Unit | Verify `protocols("TLSv1.3", "TLSv1.2")` is set on SslContext. |
| 12 | `testSystemCaBundleUsedByDefault` | Integration | Enable TLS without custom CA against a server with a public CA cert. Verify connection succeeds. |

---

## 3.3 REQ-AUTH-3: Mutual TLS (mTLS)

**Gap Status:** PARTIAL
**Priority:** P0
**Effort:** M (1-2 days) -- shared work with REQ-AUTH-2

### Current State

- **COMPLIANT:** mTLS configurable with 3 parameters: `tlsCertFile`, `tlsKeyFile`, `caCertFile` (line 282-284 of `KubeMQClient.java`)
- **PARTIAL:** Only file paths supported, no PEM bytes
- **PARTIAL:** File existence validated (lines 236-251), cert content errors are generic
- **MISSING:** Certificate errors not classified as `AuthenticationException`
- **COMPLIANT:** mTLS documented with example at `TLSConnectionExample.java`
- **MISSING:** TLS credentials not reloaded on reconnection (assessment 3.3.4) -- covered by REQ-AUTH-6
- **MISSING:** No env var cert loading example

### Acceptance Criteria Checklist

| # | Criterion | Status | Action |
|---|-----------|--------|--------|
| 1 | mTLS configurable with 3 parameters | COMPLIANT | None |
| 2 | Both file paths and PEM bytes accepted | PARTIAL | PEM bytes added in 3.2.3 |
| 3 | Invalid certs produce clear error at connection time | PARTIAL | Improve via 3.2.8 |
| 4 | Certificate errors classified as AuthenticationError | MISSING | Covered by 3.2.8 |
| 5 | mTLS documented with examples | COMPLIANT | Extend |
| 6 | TLS credentials reloaded on reconnection | MISSING | Covered by REQ-AUTH-6 (3.6) |
| 7 | Changed cert files used on reconnection | MISSING | Covered by REQ-AUTH-6 (3.6) |
| 8 | Documentation: env var cert loading example | MISSING | Implement |

### Implementation

Most mTLS work is shared with REQ-AUTH-2 (PEM bytes in 3.2.3, error classification in 3.2.8). Incremental work:

#### 3.3.1 PEM Bytes Validation for mTLS

In `validateTlsConfiguration()`, add validation for PEM bytes:

```java
// Validate mutual TLS -- cert and key must be provided together (PEM bytes)
boolean hasCertPem = tlsCertPem != null && tlsCertPem.length > 0;
boolean hasKeyPem = tlsKeyPem != null && tlsKeyPem.length > 0;

if (hasCertPem != hasKeyPem) {
    throw new IllegalArgumentException(
        "When using mutual TLS, both tlsCertPem and tlsKeyPem must be provided together. "
        + "Got tlsCertPem=" + (hasCertPem ? "set" : "null")
        + ", tlsKeyPem=" + (hasKeyPem ? "set" : "null")
    );
}

// Cannot mix file and PEM for client cert/key
if ((hasCert && hasCertPem) || (hasKey && hasKeyPem)) {
    throw new IllegalArgumentException(
        "Cannot mix file paths and PEM bytes for client certificate/key. "
        + "Use either tlsCertFile+tlsKeyFile or tlsCertPem+tlsKeyPem.");
}
```

#### 3.3.2 Environment Variable Certificate Loading Example

**New file:** `kubemq-java-example/src/main/java/io/kubemq/example/config/EnvVarCertExample.java`

```java
package io.kubemq.example.config;

import io.kubemq.sdk.queues.QueuesClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Demonstrates loading TLS certificates from environment variables.
 *
 * This pattern is common in container deployments where certificates
 * are injected as environment variables rather than mounted as files.
 *
 * Environment variables expected:
 *   KUBEMQ_CA_CERT     - PEM-encoded CA certificate
 *   KUBEMQ_CLIENT_CERT - PEM-encoded client certificate (for mTLS)
 *   KUBEMQ_CLIENT_KEY  - PEM-encoded client private key (for mTLS)
 *
 * Values can be either raw PEM or base64-encoded PEM.
 */
public class EnvVarCertExample {

    public static void main(String[] args) {
        // Load certificates from environment variables
        byte[] caCert = loadPemFromEnv("KUBEMQ_CA_CERT");
        byte[] clientCert = loadPemFromEnv("KUBEMQ_CLIENT_CERT");
        byte[] clientKey = loadPemFromEnv("KUBEMQ_CLIENT_KEY");

        if (caCert == null) {
            System.err.println("KUBEMQ_CA_CERT environment variable not set");
            return;
        }

        // Build client with PEM bytes from environment
        QueuesClient.QueuesClientBuilder builder = QueuesClient.builder()
                .address(System.getenv().getOrDefault("KUBEMQ_ADDRESS", "kubemq:50000"))
                .clientId("env-cert-client")
                .tls(true)
                .caCertPem(caCert);

        // Add mTLS if client cert/key are provided
        if (clientCert != null && clientKey != null) {
            builder.tlsCertPem(clientCert).tlsKeyPem(clientKey);
        }

        try (QueuesClient client = builder.build()) {
            System.out.println("Connected: " + client.ping());
        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    /**
     * Loads a PEM certificate from an environment variable.
     * Handles both raw PEM and base64-encoded PEM values.
     */
    private static byte[] loadPemFromEnv(String envVar) {
        String value = System.getenv(envVar);
        if (value == null || value.isEmpty()) {
            return null;
        }
        // If it starts with "-----BEGIN", it's raw PEM
        if (value.startsWith("-----BEGIN")) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
        // Otherwise, try base64 decode
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
    }
}
```

### Tests

| # | Test | Type | Description |
|---|------|------|-------------|
| 1 | `testMtlsWithPemBytes` | Unit | Provide `tlsCertPem` + `tlsKeyPem` + `caCertPem`. Verify SslContextBuilder receives ByteArrayInputStream for all three. |
| 2 | `testMtlsPemBytesRequireBoth` | Unit | Provide `tlsCertPem` without `tlsKeyPem`. Assert `IllegalArgumentException`. |
| 3 | `testCannotMixFileAndPem` | Unit | Provide `tlsCertFile` and `tlsCertPem` together. Assert `IllegalArgumentException`. |
| 4 | `testMtlsCertErrorClassifiedAsAuthenticationException` | Unit | Provide invalid cert PEM bytes. Assert `AuthenticationException` thrown during construction. |

---

## 3.4 REQ-AUTH-4: Credential Provider Interface

**Gap Status:** NOT_ASSESSED (confirmed MISSING)
**Priority:** P0
**Effort:** L (3-5 days)

### Current State

No credential provider interface exists. Token is set once in the constructor and cannot be refreshed (assessment 5.1.2). No OIDC support (assessment 5.1.3).

### Acceptance Criteria Checklist

| # | Criterion | Status | Action |
|---|-----------|--------|--------|
| 1 | `CredentialProvider` interface with `getToken()` | MISSING | Implement |
| 2 | Static token as built-in provider | MISSING | Implement |
| 3 | Custom providers supported (Vault, OIDC, etc.) | MISSING | Implement |
| 4 | Reactive refresh on `UNAUTHENTICATED` | MISSING | Implement |
| 5 | Proactive refresh when `expiresAt` provided (RECOMMENDED) | MISSING | Implement |
| 6 | Provider calls serialized (at most one outstanding) | MISSING | Implement |
| 7 | Provider invoked during CONNECTING and RECONNECTING | MISSING | Implement |
| 8 | Provider errors classified | MISSING | Implement |
| 9 | OIDC provider example documented | MISSING | Implement |

### Implementation

#### 3.4.1 Core Interfaces

**New file:** `kubemq-java/src/main/java/io/kubemq/sdk/auth/TokenResult.java`

```java
package io.kubemq.sdk.auth;

import java.time.Instant;

/**
 * Result returned by a {@link CredentialProvider} containing the token
 * and an optional expiry hint for proactive refresh scheduling.
 */
public final class TokenResult {

    private final String token;
    private final Instant expiresAt;

    /**
     * Creates a TokenResult with no expiry hint.
     * The token will only be refreshed reactively (on UNAUTHENTICATED response).
     *
     * @param token the authentication token (must not be null or empty)
     */
    public TokenResult(String token) {
        this(token, null);
    }

    /**
     * Creates a TokenResult with an expiry hint.
     * When provided, the SDK will proactively refresh the token before expiry.
     *
     * @param token     the authentication token (must not be null or empty)
     * @param expiresAt when the token expires (null means no expiry hint)
     */
    public TokenResult(String token, Instant expiresAt) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Token must not be null or empty");
        }
        this.token = token;
        this.expiresAt = expiresAt;
    }

    public String getToken() {
        return token;
    }

    /**
     * Returns when the token expires, or null if no expiry hint was provided.
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    @Override
    public String toString() {
        // SECURITY: never include the token value
        return "TokenResult{token_present=true, expiresAt=" + expiresAt + "}";
    }
}
```

**New file:** `kubemq-java/src/main/java/io/kubemq/sdk/auth/CredentialException.java`

```java
package io.kubemq.sdk.auth;

/**
 * Thrown by a {@link CredentialProvider} when token retrieval fails.
 *
 * <p>Implementers should set {@link #isRetryable()} to indicate whether
 * the SDK should retry (e.g., transient infrastructure failure) or not
 * (e.g., invalid/expired credentials).
 */
public class CredentialException extends Exception {

    private final boolean retryable;

    /**
     * Creates a non-retryable credential exception.
     */
    public CredentialException(String message) {
        this(message, false);
    }

    public CredentialException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public CredentialException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    /**
     * Whether this failure is transient and the SDK should retry.
     *
     * @return true if the provider failure is transient (e.g., network timeout,
     *         credential store temporarily unavailable)
     */
    public boolean isRetryable() {
        return retryable;
    }
}
```

**New file:** `kubemq-java/src/main/java/io/kubemq/sdk/auth/CredentialProvider.java`

```java
package io.kubemq.sdk.auth;

/**
 * Pluggable interface for providing authentication tokens.
 *
 * <p>Implementations may retrieve tokens from any source: static configuration,
 * environment variables, Vault, OIDC providers, cloud IAM, Kubernetes service
 * account tokens, etc.
 *
 * <h3>Threading guarantee</h3>
 * The SDK serializes calls to {@code getToken()} -- at most one call is
 * outstanding at a time. Implementations do NOT need to be thread-safe.
 * The SDK caches the returned token and only re-invokes the provider when:
 * <ul>
 *   <li>No cached token exists (first call)</li>
 *   <li>The cached token is invalidated by a server {@code UNAUTHENTICATED} response</li>
 *   <li>Proactive refresh determines the token is approaching expiry (when
 *       {@link TokenResult#getExpiresAt()} is provided)</li>
 * </ul>
 *
 * <h3>Error handling</h3>
 * Throw {@link CredentialException} with {@code retryable=true} for transient
 * failures (Vault unavailable, network timeout). Throw with {@code retryable=false}
 * for permanent failures (invalid credentials, revoked access).
 *
 * @see StaticTokenProvider
 * @see TokenResult
 */
@FunctionalInterface
public interface CredentialProvider {

    /**
     * Retrieves an authentication token.
     *
     * @return the token result containing the token and optional expiry hint
     * @throws CredentialException if token retrieval fails
     */
    TokenResult getToken() throws CredentialException;
}
```

#### 3.4.2 Built-in Static Token Provider

**New file:** `kubemq-java/src/main/java/io/kubemq/sdk/auth/StaticTokenProvider.java`

```java
package io.kubemq.sdk.auth;

/**
 * A {@link CredentialProvider} that always returns the same token.
 * Used for static API keys and long-lived tokens that do not expire.
 */
public final class StaticTokenProvider implements CredentialProvider {

    private final TokenResult tokenResult;

    /**
     * Creates a static token provider.
     *
     * @param token the static authentication token
     */
    public StaticTokenProvider(String token) {
        this.tokenResult = new TokenResult(token);
    }

    @Override
    public TokenResult getToken() {
        return tokenResult;
    }

    @Override
    public String toString() {
        return "StaticTokenProvider{token_present=true}";
    }
}
```

#### 3.4.3 Credential Manager

**New file:** `kubemq-java/src/main/java/io/kubemq/sdk/auth/CredentialManager.java`

```java
package io.kubemq.sdk.auth;

import io.kubemq.sdk.exception.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages credential lifecycle: caching, serialized retrieval,
 * reactive invalidation, and proactive refresh scheduling.
 *
 * <p>Thread-safe. All public methods can be called from any thread.
 */
public class CredentialManager {

    private static final Logger log = LoggerFactory.getLogger(CredentialManager.class);

    /**
     * Proactive refresh occurs when the token is within this fraction
     * of its total lifetime from expiry. E.g., 0.1 means refresh at 90%
     * of lifetime elapsed.
     */
    private static final double REFRESH_BUFFER_FRACTION = 0.1;
    private static final Duration MIN_REFRESH_BUFFER = Duration.ofSeconds(30);

    private final CredentialProvider provider;
    private final ScheduledExecutorService scheduler;
    private final ReentrantLock refreshLock = new ReentrantLock();
    private final AtomicReference<TokenResult> cachedToken = new AtomicReference<>();
    private volatile ScheduledFuture<?> proactiveRefreshFuture;
    private volatile Instant tokenFetchedAt;

    /**
     * @param provider  the credential provider to delegate to
     * @param scheduler executor for scheduling proactive refresh (may be null
     *                  to disable proactive refresh)
     */
    public CredentialManager(CredentialProvider provider, ScheduledExecutorService scheduler) {
        if (provider == null) {
            throw new IllegalArgumentException("CredentialProvider must not be null");
        }
        this.provider = provider;
        this.scheduler = scheduler;
    }

    /**
     * Returns the current token, fetching from the provider if no cached
     * token exists.
     *
     * @return the current authentication token (never null)
     * @throws AuthenticationException if the provider returns a non-retryable error
     * @throws RuntimeException if the provider returns a retryable error
     *         (will be TransientException once REQ-ERR-1 is implemented)
     */
    public String getToken() {
        TokenResult cached = cachedToken.get();
        if (cached != null) {
            return cached.getToken();
        }
        return refreshToken();
    }

    /**
     * Invalidates the cached token. Called on UNAUTHENTICATED response
     * for reactive refresh.
     */
    public void invalidate() {
        log.debug("Token invalidated (reactive refresh triggered)");
        cachedToken.set(null);
        cancelProactiveRefresh();
    }

    /**
     * Fetches a new token from the provider, serializing concurrent calls.
     */
    private String refreshToken() {
        refreshLock.lock();
        try {
            // Double-check: another thread may have refreshed while we waited
            TokenResult cached = cachedToken.get();
            if (cached != null) {
                return cached.getToken();
            }

            log.debug("Fetching token from credential provider");
            TokenResult result = provider.getToken();
            cachedToken.set(result);
            tokenFetchedAt = Instant.now();
            log.debug("Token fetched, token_present: true, expiresAt: {}",
                       result.getExpiresAt());

            // Schedule proactive refresh if expiry hint is available
            scheduleProactiveRefresh(result);

            return result.getToken();
        } catch (CredentialException e) {
            if (e.isRetryable()) {
                // Transient infrastructure failure -- retryable.
                // Wrap as ConnectionException (from error hierarchy) instead of bare RuntimeException.
                log.warn("Credential provider returned retryable error: {}", e.getMessage());
                throw new ConnectionException(
                    "Transient credential provider failure: " + e.getMessage(), e);
            } else {
                // Permanent credential failure -- non-retryable
                log.error("Credential provider returned non-retryable error: {}", e.getMessage());
                throw new AuthenticationException(
                    "Credential provider error: " + e.getMessage(), e);
            }
        } finally {
            refreshLock.unlock();
        }
    }

    /**
     * Schedules proactive refresh before the token expires.
     */
    private void scheduleProactiveRefresh(TokenResult result) {
        if (scheduler == null || result.getExpiresAt() == null) {
            return;
        }

        cancelProactiveRefresh();

        Duration lifetime = Duration.between(tokenFetchedAt, result.getExpiresAt());
        if (lifetime.isNegative() || lifetime.isZero()) {
            log.warn("Token expiresAt is in the past; skipping proactive refresh");
            return;
        }

        // Refresh at (lifetime - buffer) or (lifetime * 0.9), whichever is smaller
        Duration buffer = Duration.ofMillis(
            (long) (lifetime.toMillis() * REFRESH_BUFFER_FRACTION));
        if (buffer.compareTo(MIN_REFRESH_BUFFER) < 0) {
            buffer = MIN_REFRESH_BUFFER;
        }
        Duration delay = lifetime.minus(buffer);
        if (delay.isNegative()) {
            delay = Duration.ZERO;
        }

        log.debug("Scheduling proactive token refresh in {} seconds", delay.getSeconds());
        proactiveRefreshFuture = scheduler.schedule(() -> {
            log.debug("Proactive token refresh triggered");
            cachedToken.set(null);
            try {
                refreshToken();
            } catch (Exception e) {
                log.warn("Proactive token refresh failed: {}", e.getMessage());
                // Token is invalidated; next request will trigger reactive refresh
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void cancelProactiveRefresh() {
        ScheduledFuture<?> future = proactiveRefreshFuture;
        if (future != null && !future.isDone()) {
            future.cancel(false);
        }
    }

    /**
     * Shuts down the proactive refresh scheduler.
     * Call during client close.
     */
    public void shutdown() {
        cancelProactiveRefresh();
    }
}
```

#### 3.4.4 Integration with KubeMQClient

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

Add a new field:

```java
private CredentialManager credentialManager;
```

Add builder parameter:

```java
private CredentialProvider credentialProvider;
```

**In the constructor,** after existing initialization:

```java
// Credential provider setup
if (credentialProvider != null) {
    ScheduledExecutorService refreshScheduler =
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kubemq-credential-refresh");
            t.setDaemon(true);
            return t;
        });
    this.credentialManager = new CredentialManager(credentialProvider, refreshScheduler);
} else if (authTokenRef.get() != null && !authTokenRef.get().isEmpty()) {
    // Wrap static token as a provider for uniform handling
    this.credentialManager = new CredentialManager(
        new StaticTokenProvider(authTokenRef.get()), null);
}
```

**Validation:** Cannot specify both `authToken` and `credentialProvider`:

```java
if (credentialProvider != null && authToken != null && !authToken.isEmpty()) {
    throw new IllegalArgumentException(
        "Cannot specify both authToken and credentialProvider. Use one or the other.");
}
```

**Update AuthInterceptor** to use `CredentialManager`:

```java
@Override
public void start(Listener<RespT> responseListener, Metadata headers) {
    String token = null;
    if (credentialManager != null) {
        token = credentialManager.getToken();
    } else {
        token = authTokenRef.get();
    }
    if (token != null && !token.isEmpty()) {
        Metadata.Key<String> key = Metadata.Key.of("authorization",
                Metadata.ASCII_STRING_MARSHALLER);
        headers.put(key, token);
    }
    super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(
            responseListener) {
        @Override
        public void onClose(Status status, Metadata trailers) {
            // Reactive refresh: invalidate token on UNAUTHENTICATED
            if (status.getCode() == Status.Code.UNAUTHENTICATED
                    && credentialManager != null) {
                credentialManager.invalidate();
            }
            super.onClose(status, trailers);
        }
    }, headers);
}
```

**In `close()`** method:

```java
@Override
public void close() {
    if (credentialManager != null) {
        credentialManager.shutdown();
    }
    // ... existing close logic ...
}
```

#### 3.4.5 Builder API

Each subclass builder must add `credentialProvider` parameter. Example for `QueuesClient`:

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueuesClient.java`

```java
@Builder
public QueuesClient(String address, String clientId, String authToken,
                    Boolean tls, String tlsCertFile, String tlsKeyFile, String caCertFile,
                    byte[] caCertPem, byte[] tlsCertPem, byte[] tlsKeyPem,
                    String serverNameOverride, boolean insecureSkipVerify,
                    CredentialProvider credentialProvider,
                    int maxReceiveSize, int reconnectIntervalSeconds,
                    Boolean keepAlive, int pingIntervalInSeconds,
                    int pingTimeoutInSeconds, Level logLevel) {
    super(address, clientId, authToken, tls, tlsCertFile, tlsKeyFile, caCertFile,
          caCertPem, tlsCertPem, tlsKeyPem, serverNameOverride, insecureSkipVerify,
          credentialProvider,
          maxReceiveSize, reconnectIntervalSeconds, keepAlive,
          pingIntervalInSeconds, pingTimeoutInSeconds, logLevel);
    // ...
}
```

Same pattern for `PubSubClient` and `CQClient`.

#### 3.4.6 OIDC Provider Example

**New file:** `kubemq-java-example/src/main/java/io/kubemq/example/auth/OidcCredentialProviderExample.java`

```java
package io.kubemq.example.auth;

import io.kubemq.sdk.auth.CredentialException;
import io.kubemq.sdk.auth.CredentialProvider;
import io.kubemq.sdk.auth.TokenResult;
import io.kubemq.sdk.queues.QueuesClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

/**
 * Example OIDC credential provider using the CredentialProvider interface.
 *
 * This demonstrates how to integrate with an OIDC/OAuth2 identity provider
 * to obtain and refresh JWT tokens for KubeMQ authentication.
 *
 * Adapt the token endpoint URL, client credentials, and grant type
 * to match your identity provider (Auth0, Keycloak, Azure AD, etc.).
 */
public class OidcCredentialProviderExample implements CredentialProvider {

    private final String tokenEndpoint;
    private final String clientId;
    private final String clientSecret;
    private final HttpClient httpClient;

    public OidcCredentialProviderExample(String tokenEndpoint,
                                         String clientId,
                                         String clientSecret) {
        this.tokenEndpoint = tokenEndpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public TokenResult getToken() throws CredentialException {
        try {
            // Client credentials grant
            String body = "grant_type=client_credentials"
                + "&client_id=" + clientId
                + "&client_secret=" + clientSecret
                + "&audience=kubemq";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                boolean retryable = response.statusCode() >= 500;
                throw new CredentialException(
                    "OIDC token request failed: HTTP " + response.statusCode(),
                    retryable);
            }

            // Parse JSON response (simplified -- use Jackson in production)
            String responseBody = response.body();
            String token = extractJsonField(responseBody, "access_token");
            String expiresIn = extractJsonField(responseBody, "expires_in");

            Instant expiresAt = null;
            if (expiresIn != null) {
                expiresAt = Instant.now().plusSeconds(Long.parseLong(expiresIn));
            }

            return new TokenResult(token, expiresAt);

        } catch (CredentialException e) {
            throw e;
        } catch (java.net.ConnectException e) {
            throw new CredentialException(
                "Cannot reach OIDC provider: " + e.getMessage(), e, true);
        } catch (Exception e) {
            throw new CredentialException(
                "OIDC token retrieval failed: " + e.getMessage(), e, false);
        }
    }

    private static String extractJsonField(String json, String field) {
        // Simplified JSON parsing for example purposes
        int idx = json.indexOf("\"" + field + "\"");
        if (idx < 0) return null;
        int colon = json.indexOf(":", idx);
        int start = json.indexOf("\"", colon + 1);
        if (start < 0) {
            // Numeric value
            start = colon + 1;
            while (start < json.length() && json.charAt(start) == ' ') start++;
            int end = start;
            while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
            return json.substring(start, end);
        }
        int end = json.indexOf("\"", start + 1);
        return json.substring(start + 1, end);
    }

    /**
     * Usage example.
     */
    public static void main(String[] args) {
        CredentialProvider oidcProvider = new OidcCredentialProviderExample(
            System.getenv().getOrDefault("OIDC_TOKEN_ENDPOINT",
                "https://auth.example.com/oauth/token"),
            System.getenv().getOrDefault("OIDC_CLIENT_ID", "kubemq-client"),
            System.getenv().getOrDefault("OIDC_CLIENT_SECRET", "secret")
        );

        try (QueuesClient client = QueuesClient.builder()
                .address("kubemq.prod:50000")
                .clientId("oidc-example")
                .credentialProvider(oidcProvider)
                .build()) {

            System.out.println("Connected with OIDC: " + client.ping());

        } catch (Exception e) {
            System.err.println("Failed: " + e.getMessage());
        }
    }
}
```

### Tests

| # | Test | Type | Description |
|---|------|------|-------------|
| 1 | `testStaticTokenProviderReturnsToken` | Unit | StaticTokenProvider returns the configured token. |
| 2 | `testCredentialManagerCachesToken` | Unit | Call `getToken()` twice. Provider's `getToken()` called only once. |
| 3 | `testCredentialManagerSerializesRefresh` | Unit | 10 threads call `getToken()` simultaneously on empty cache. Provider called exactly once. |
| 4 | `testReactiveRefreshOnUnauthenticated` | Unit | Cache token, call `invalidate()`, call `getToken()`. Provider called again. |
| 5 | `testProactiveRefreshScheduled` | Unit | Provider returns token with expiresAt = now + 100ms. Wait 120ms. Verify provider called again. |
| 6 | `testRetryableCredentialExceptionWrappedAsRuntimeException` | Unit | Provider throws `CredentialException(retryable=true)`. Verify `RuntimeException` thrown. |
| 7 | `testNonRetryableCredentialExceptionWrappedAsAuthenticationException` | Unit | Provider throws `CredentialException(retryable=false)`. Verify `AuthenticationException` thrown. |
| 8 | `testCannotSetBothAuthTokenAndCredentialProvider` | Unit | Builder with both `.authToken()` and `.credentialProvider()`. Assert `IllegalArgumentException`. |
| 9 | `testInterceptorUsesCredentialManager` | Unit | Configure CredentialProvider. Send gRPC request. Verify "authorization" header contains provider's token. |
| 10 | `testUnauthenticatedResponseInvalidatesCache` | Unit | Mock gRPC to return UNAUTHENTICATED. Verify `credentialManager.invalidate()` is called. |
| 11 | `testTokenResultToStringExcludesToken` | Unit | `new TokenResult("secret").toString()` contains "token_present=true", not "secret". |

---

## 3.5 REQ-AUTH-5: Security Best Practices

**Gap Status:** PARTIAL
**Priority:** P1
**Effort:** S (< 1 day)

### Current State

- **PARTIAL:** Auth token not logged (assessment 5.2.2), but `toString()` on config classes not audited
- **MISSING:** No `token_present: true/false` logging
- **COMPLIANT:** TLS cert files validated at construction (fail-fast) -- lines 209-255 of `KubeMQClient.java`
- **PARTIAL:** Auth token example exists, no comprehensive security guide
- **MISSING:** No InsecureSkipVerify warning (covered by REQ-AUTH-2 section 3.2.5)

### Acceptance Criteria Checklist

| # | Criterion | Status | Action |
|---|-----------|--------|--------|
| 1 | Credentials excluded from logs/errors/OTel/toString() | PARTIAL | Audit toString() |
| 2 | Log only token_present: true/false | MISSING | Implement |
| 3 | TLS cert files validated at construction (fail-fast) | COMPLIANT | None |
| 4 | Security configuration guide with examples | PARTIAL | Extended by AUTH-1/2/3/4 examples |
| 5 | InsecureSkipVerify emits warning on every connection | MISSING | Covered by 3.2.5 |

### Implementation

#### 3.5.1 toString() Audit

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

`KubeMQClient` uses Lombok `@Getter` and `@AllArgsConstructor` but does not have `@ToString`. However, the `@Getter` annotation means the `authToken` field is accessible. Since `KubeMQClient` does not override `toString()`, the default `Object.toString()` is used (class@hashcode), which is safe.

**Action:** Add an explicit `toString()` override that excludes credentials:

```java
@Override
public String toString() {
    return "KubeMQClient{" +
           "address='" + address + '\'' +
           ", clientId='" + clientId + '\'' +
           ", tls=" + isTls() +
           ", token_present=" + (authTokenRef.get() != null && !authTokenRef.get().isEmpty()) +
           ", credentialProvider_present=" + (credentialManager != null) +
           ", insecureSkipVerify=" + insecureSkipVerify +
           '}';
}
```

**Other classes to audit:**

| File | Has toString? | Contains credentials? | Action |
|------|-------------|---------------------|--------|
| `KubeMQClient.java` | No (Object default) | authToken in fields | Add safe toString() |
| `ServerInfo.java` | Yes (line 49) | No | None |
| `EventMessage.java` | Yes (line 93) | No | None |
| `QueueMessage.java` | Yes (line 164) | No | None |
| `QueueMessagesPulled.java` | Yes (`@ToString`) | No | None |
| `QueueMessagesWaiting.java` | Yes (`@ToString`) | No | None |
| `QueueMessagesReceived.java` | Yes (`@ToString`) | No | None |
| `UpstreamResponse.java` | Yes (`@ToString`) | No | None |

**Result:** Only `KubeMQClient` needs a toString() change. Message classes do not contain credentials.

#### 3.5.2 Token Presence Logging

Already implemented in sections 3.1.1 and 3.1.2:
- `setAuthToken()` logs `"Auth token updated, token_present: {}"`
- `initChannel()` logs `"Client initialized for KubeMQ address: {}, token_present: {}"`

#### 3.5.3 Error Message Credential Audit

When implementing the `AuthenticationException` (3.1.3) and `classifyTlsException()` (3.2.8), ensure:
- Error messages never include the token value
- Error messages never include certificate file contents
- Certificate file *paths* are acceptable in error messages (they are configuration, not secrets)

**Rule for all future error messages in this category:**
```java
// GOOD: includes path (configuration)
"Client certificate file does not exist: " + certFile

// BAD: would include token value
"Authentication failed with token: " + token

// GOOD: includes presence, not value
"Authentication failed, token_present: " + (token != null)
```

### Tests

| # | Test | Type | Description |
|---|------|------|-------------|
| 1 | `testKubeMQClientToStringExcludesAuthToken` | Unit | Set authToken. Call toString(). Assert result does not contain the token value. Assert contains "token_present=true". |
| 2 | `testKubeMQClientToStringNoTokenShowsFalse` | Unit | No authToken. toString() contains "token_present=false". |
| 3 | `testTokenNeverInLogOutput` | Unit | Set TRACE level. Perform operations. Capture all log output. Assert token value never appears. |
| 4 | `testCredentialManagerToStringExcludesToken` | Unit | `CredentialManager` with token cached. toString() does not contain token value. |

---

## 3.6 REQ-AUTH-6: TLS Credentials During Reconnection

**Gap Status:** NOT_ASSESSED (confirmed MISSING)
**Priority:** P0
**Effort:** M (1-2 days, assumes REQ-AUTH-2/3 complete)

### Current State

No certificate reload on reconnection. Assessment 3.3.4 confirms "No certificate rotation support." The current `SslContext` is created once in `initChannel()` (line 285 of `KubeMQClient.java`) and reused for all connections.

### Acceptance Criteria Checklist

| # | Criterion | Status | Action |
|---|-----------|--------|--------|
| 1 | Certificates reloaded from configured source on reconnection | MISSING | Implement |
| 2 | Source error treated as transient, retried per backoff | MISSING | Implement |
| 3 | TLS handshake failure classified per REQ-AUTH-2 rules | MISSING | Implement |
| 4 | Certificate reload logged at DEBUG | MISSING | Implement |
| 5 | Certificate reload errors logged at ERROR | MISSING | Implement |

### Dependencies

This requirement **depends on REQ-CONN-1** (reconnection manager). The current SDK uses gRPC's built-in reconnection (via `handleStateChange()` on lines 327-346 of `KubeMQClient.java`), which does NOT recreate the `SslContext`. Implementing cert reload requires intercepting the reconnection process.

### Implementation Strategy

There are two possible approaches:

**Option A: Custom SslContext factory with lazy reload**
Use Netty's `SslContext` recreation on each connection by implementing a custom `ChannelFactory` or hooking into `ManagedChannel`'s transport lifecycle. This is complex and tightly coupled to gRPC internals.

**Option B: Channel recreation on reconnection (recommended)**
When a connection drops, instead of relying on gRPC's built-in reconnection (which reuses the same `SslContext`), shut down the old channel and create a new one with fresh certificates. This is the approach used by the NATS Java client.

We recommend **Option B** because:
1. Netty's `SslContext` is immutable once built -- there is no way to update it
2. gRPC's `ManagedChannel` does not expose a hook to rebuild `SslContext` during reconnection
3. Channel recreation is straightforward and testable

#### 3.6.1 SslContext Factory Method

**File:** `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java`

Extract the SslContext construction from `initChannel()` into a separate method that can be called on every reconnection:

```java
/**
 * Creates a fresh SslContext by reading certificate files from disk.
 * Called on every connection/reconnection to pick up rotated certificates.
 *
 * @return a new SslContext configured with current certificates
 * @throws SSLException if the SSL context cannot be built
 */
protected SslContext createSslContext() throws SSLException {
    log.debug("Loading TLS certificates from configured source");

    SslContextBuilder sslContextBuilder = SslContextBuilder.forClient();

    // TLS version minimum
    sslContextBuilder.protocols("TLSv1.3", "TLSv1.2");

    // InsecureSkipVerify
    if (insecureSkipVerify) {
        log.warn("certificate verification is disabled -- "
                 + "this is insecure and should only be used in development");
        sslContextBuilder.trustManager(
            io.grpc.netty.shaded.io.netty.handler.ssl.util
                .InsecureTrustManagerFactory.INSTANCE);
    } else {
        // CA certificate -- re-read from disk on each call
        if (caCertPem != null && caCertPem.length > 0) {
            sslContextBuilder.trustManager(new ByteArrayInputStream(caCertPem));
        } else if (caCertFile != null && !caCertFile.isEmpty()) {
            sslContextBuilder.trustManager(new File(caCertFile));
        }
    }

    // Client certificate for mTLS -- re-read from disk on each call
    if (tlsCertPem != null && tlsCertPem.length > 0
            && tlsKeyPem != null && tlsKeyPem.length > 0) {
        sslContextBuilder.keyManager(
            new ByteArrayInputStream(tlsCertPem),
            new ByteArrayInputStream(tlsKeyPem));
    } else if (tlsCertFile != null && !tlsCertFile.isEmpty()
            && tlsKeyFile != null && !tlsKeyFile.isEmpty()) {
        // Re-reading files picks up rotated certificates
        sslContextBuilder.keyManager(new File(tlsCertFile), new File(tlsKeyFile));
    }

    log.debug("TLS certificates loaded successfully");
    return sslContextBuilder.build();
}
```

#### 3.6.2 Reconnection with Cert Reload

Update `handleStateChange()` to recreate the channel with fresh certificates:

```java
private void handleStateChange() {
    ConnectivityState state = managedChannel.getState(false);
    switch (state) {
        case TRANSIENT_FAILURE:
            log.debug("Channel is disconnected, reconnecting...");
            if (isTls()) {
                // Recreate channel with fresh certificates
                reconnectWithCertReload();
            } else {
                managedChannel.resetConnectBackoff();
                addChannelStateListener();
            }
            break;
        case SHUTDOWN:
            log.debug("Channel is shutdown.");
            break;
    }
}

/**
 * Recreates the gRPC channel with fresh TLS certificates.
 * Supports certificate rotation by re-reading files on each reconnection.
 */
private void reconnectWithCertReload() {
    try {
        SslContext sslContext = createSslContext();

        // Shut down old channel
        ManagedChannel oldChannel = managedChannel;
        if (oldChannel != null) {
            oldChannel.shutdown();
        }

        // Create new channel with fresh SslContext
        NettyChannelBuilder ncb = NettyChannelBuilder.forTarget(address)
                .negotiationType(NegotiationType.TLS)
                .maxInboundMessageSize(maxReceiveSize)
                // NOTE: .enableRetry() intentionally removed per REQ-ERR-3
                .sslContext(sslContext);

        if (serverNameOverride != null && !serverNameOverride.isEmpty()) {
            ncb = ncb.overrideAuthority(serverNameOverride);
        }

        if (keepAlive != null) {
            ncb = ncb.keepAliveTime(
                        pingIntervalInSeconds == 0 ? 60 : pingIntervalInSeconds,
                        TimeUnit.SECONDS)
                    .keepAliveTimeout(
                        pingTimeoutInSeconds == 0 ? 30 : pingTimeoutInSeconds,
                        TimeUnit.SECONDS)
                    .keepAliveWithoutCalls(keepAlive);
        }

        managedChannel = ncb.build();

        // Rebuild stubs
        ClientInterceptor authInterceptor = new AuthInterceptor();
        Channel channel = ClientInterceptors.intercept(managedChannel, authInterceptor);
        this.blockingStub = kubemqGrpc.newBlockingStub(channel);
        this.asyncStub = kubemqGrpc.newStub(channel);

        addChannelStateListener();

        log.info("Reconnected with fresh TLS certificates to {}", address);

    } catch (SSLException e) {
        RuntimeException classified = classifyTlsException(e);
        log.error("TLS certificate reload failed during reconnection: {}",
                  classified.getMessage());
        // Treat as transient -- schedule retry via gRPC backoff
        managedChannel.resetConnectBackoff();
        addChannelStateListener();
    } catch (java.io.IOException e) {
        // File read failure (missing, permission denied) -- transient, retry
        log.error("Failed to read certificate files during reconnection: {}",
                  e.getMessage());
        managedChannel.resetConnectBackoff();
        addChannelStateListener();
    }
}
```

**Note:** This implementation is a stepping stone. When REQ-CONN-1 (reconnection manager) is implemented, the cert reload logic will be integrated into the formal reconnection lifecycle rather than the gRPC state change listener. The `createSslContext()` method is designed to be callable from either the current state listener or a future reconnection manager.

#### 3.6.3 Thread Safety Consideration

The `reconnectWithCertReload()` method modifies `managedChannel`, `blockingStub`, and `asyncStub`. Concurrent calls from gRPC's callback threads could cause races. Add synchronization:

```java
private final Object reconnectLock = new Object();

private void reconnectWithCertReload() {
    synchronized (reconnectLock) {
        // ... reconnection logic from 3.6.2 ...
    }
}
```

In-flight gRPC calls on the old channel will fail with `UNAVAILABLE` and can be retried by the caller (or by the retry mechanism from REQ-ERR-3 when implemented).

### Tests

| # | Test | Type | Description |
|---|------|------|-------------|
| 1 | `testCreateSslContextRereadsCertFiles` | Unit | Write cert to temp file. Create SslContext. Overwrite cert with new file. Create SslContext again. Verify different context (different cert content). |
| 2 | `testCertReloadLoggedAtDebug` | Unit | Trigger cert reload. Verify DEBUG log contains "Loading TLS certificates". |
| 3 | `testCertReloadErrorLoggedAtError` | Unit | Delete cert file before reconnection. Verify ERROR log contains "Failed to read certificate files". |
| 4 | `testCertFileReadFailureTreatedAsTransient` | Unit | Remove cert file. Trigger reconnection. Verify channel continues reconnection backoff (not permanent failure). |
| 5 | `testTlsHandshakeFailureAfterReloadClassified` | Unit | Provide cert that will cause handshake failure. Verify `classifyTlsException()` returns correct exception type. |
| 6 | `testReconnectWithCertReloadThreadSafe` | Unit | 5 threads trigger `reconnectWithCertReload()` simultaneously. No exception, no deadlock. |

---

## GS Internal Inconsistencies

| # | Inconsistency | GS Location | Impact | Resolution |
|---|--------------|-------------|--------|------------|
| 1 | REQ-AUTH-2 says "TLS default: false for localhost, true for remote" but also "TLS can be enabled with a single option." These are compatible but the interplay is non-obvious. | 03-auth-security.md lines 37, 53 | Low -- the single option overrides the default | Implemented as: `Boolean tls` (null = auto-detect, true/false = explicit) |
| 2 | REQ-AUTH-3 acceptance criteria 6-7 overlap with REQ-AUTH-6 (TLS credentials during reconnection). AUTH-3 says "On reconnection, the SDK MUST reload TLS credentials" while AUTH-6 is dedicated to this topic. | 03-auth-security.md lines 80-81, 130-141 | Low -- same behavior specified in two places | Implementation is shared: `createSslContext()` serves both. Tests in section 3.6. |

---

## Future Enhancements

Items explicitly out of scope per GS review decisions but worth designing for:

| # | Enhancement | GS Decision Ref | Design Consideration |
|---|------------|-----------------|---------------------|
| 1 | PKCS#12 / JKS keystore support | A.5 REJECTED | The `SslContextBuilder` could accept a `KeyStore` object. If added later, add builder methods `trustStore(KeyStore)` and `keyStore(KeyStore, char[])`. No current code changes needed. |
| 2 | Certificate file watching (hot-reload) | B.3 REJECTED | The `createSslContext()` factory method (3.6.1) is already designed for repeated invocation. A `WatchService`-based file watcher could trigger channel recreation using the same code path. |
| 3 | Kubernetes service account token provider | C.3 REJECTED | Trivially implementable as a `CredentialProvider` that reads `/var/run/secrets/kubernetes.io/serviceaccount/token`. Include as a documentation example, not a built-in provider. |
| 4 | Custom cipher suite configuration | C.5 REJECTED | `SslContextBuilder.ciphers()` is available. If added, expose as `tlsCiphers(List<String>)` on the builder. |
| 5 | Passphrase-protected private keys | C.1 REJECTED | Users can decrypt keys before passing PEM bytes. If added later, add `tlsKeyPassword(char[])` builder parameter. |

---

## Implementation Sequence

### Phase 1: Foundation (S effort, ~1 day)
1. Create `AuthenticationException` and `ConfigurationException` in `io.kubemq.sdk.exception/`
2. Implement REQ-AUTH-1 (mutable token, `AuthInterceptor`, `wrapGrpcException`)
3. Write REQ-AUTH-1 unit tests

### Phase 2: TLS Enhancement (M effort, ~2-3 days)
4. Add new builder fields (PEM bytes, serverNameOverride, insecureSkipVerify)
5. Implement address-aware TLS defaulting
6. Update `initChannel()` TLS block (section 3.2.9)
7. Extract `createSslContext()` factory method
8. Implement `classifyTlsException()`
9. Write REQ-AUTH-2 and REQ-AUTH-3 unit tests
10. Create `EnvVarCertExample.java`

### Phase 3: Security Audit (S effort, ~0.5 day)
11. Add `toString()` override on `KubeMQClient`
12. Add `token_present` logging
13. Write REQ-AUTH-5 tests

### Phase 4: Credential Provider (L effort, ~3-5 days)
14. Create `io.kubemq.sdk.auth` package with `CredentialProvider`, `TokenResult`, `CredentialException`, `StaticTokenProvider`
15. Implement `CredentialManager`
16. Integrate with `KubeMQClient` and `AuthInterceptor`
17. Update all three client builders (QueuesClient, PubSubClient, CQClient)
18. Write REQ-AUTH-4 unit tests
19. Create `OidcCredentialProviderExample.java`

### Phase 5: Reconnection Cert Reload (M effort, ~1-2 days)
*Depends on: Phase 2 complete, ideally REQ-CONN-1 started*
20. Implement `reconnectWithCertReload()`
21. Add reconnect lock for thread safety
22. Write REQ-AUTH-6 unit tests

**Total estimated effort:** 8-12 days

---

## Files Modified (Summary)

### New Files

| File | Purpose |
|------|---------|
| `kubemq-java/src/main/java/io/kubemq/sdk/exception/AuthenticationException.java` | Auth failure exception |
| `kubemq-java/src/main/java/io/kubemq/sdk/exception/ConfigurationException.java` | Config error exception |
| `kubemq-java/src/main/java/io/kubemq/sdk/auth/CredentialProvider.java` | Pluggable auth interface |
| `kubemq-java/src/main/java/io/kubemq/sdk/auth/TokenResult.java` | Token + expiry hint |
| `kubemq-java/src/main/java/io/kubemq/sdk/auth/CredentialException.java` | Provider error with retryability |
| `kubemq-java/src/main/java/io/kubemq/sdk/auth/StaticTokenProvider.java` | Built-in static token provider |
| `kubemq-java/src/main/java/io/kubemq/sdk/auth/CredentialManager.java` | Token caching and refresh |
| `kubemq-java-example/src/main/java/io/kubemq/example/config/EnvVarCertExample.java` | Env var cert loading example |
| `kubemq-java-example/src/main/java/io/kubemq/example/auth/OidcCredentialProviderExample.java` | OIDC provider example |

### Modified Files

| File | Changes |
|------|---------|
| `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java` | Mutable token (AtomicReference), AuthInterceptor, new builder fields, SslContext factory, TLS defaults, InsecureSkipVerify, serverNameOverride, classifyTlsException, createSslContext, reconnectWithCertReload, toString override, CredentialManager integration |
| `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/PubSubClient.java` | Constructor: add new builder parameters |
| `kubemq-java/src/main/java/io/kubemq/sdk/cq/CQClient.java` | Constructor: add new builder parameters |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueuesClient.java` | Constructor: add new builder parameters |

### Breaking Changes Summary

| Change | Severity | Migration |
|--------|----------|-----------|
| `tls` field type: `boolean` -> `Boolean` | Low | `isTls()` convenience method preserves backward compat |
| Remote addresses now default to TLS | Medium | Set `.tls(false)` explicitly for non-TLS remote servers |
| `MetadataInterceptor` renamed to `AuthInterceptor` | Low | Internal class; unlikely to be referenced by user code |
| `metadata` field removed from `KubeMQClient` | Low | `getMetadata()` no longer available; was internal state |
