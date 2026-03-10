# WU-3 Output: Spec 03 (Auth & Security)

**Status:** COMPLETED
**Build:** PASS
**Tests:** 1104 passed, 0 failed (55 new tests added)
**Previous baseline:** 1049 tests

## REQs Implemented

| REQ | Title | Status | Notes |
|-----|-------|--------|-------|
| REQ-AUTH-1 | Token Authentication | DONE | AtomicReference<String> for mutable token, AuthInterceptor replaces MetadataInterceptor, wrapGrpcException with actionable hints |
| REQ-AUTH-2 | TLS Encryption | DONE | Address-aware TLS defaulting (localhost=false, remote=true), PEM bytes API, insecureSkipVerify with WARNING, TLS 1.2 minimum, serverNameOverride, classifyTlsException |
| REQ-AUTH-3 | Mutual TLS (mTLS) | DONE | PEM bytes validation, cannot mix file+PEM, cert/key must be together. Shared with AUTH-2 |
| REQ-AUTH-4 | Credential Provider Interface | DONE | New io.kubemq.sdk.auth package: CredentialProvider, TokenResult, CredentialException, StaticTokenProvider, CredentialManager with caching, serialized refresh, proactive refresh |
| REQ-AUTH-5 | Security Best Practices | DONE | toString() excludes credentials, shows token_present=true/false, token_present logging in setAuthToken and initChannel |
| REQ-AUTH-6 | TLS Credentials During Reconnection | DONE | createSslContext() factory method re-reads certs from files, reconnectWithCertReload() with synchronized lock |

## New Files Created

| File | Purpose |
|------|---------|
| `kubemq-java/src/main/java/io/kubemq/sdk/exception/ConfigurationException.java` | TLS/config error exception (non-retryable, FAILED_PRECONDITION) |
| `kubemq-java/src/main/java/io/kubemq/sdk/auth/CredentialProvider.java` | @FunctionalInterface for pluggable auth token retrieval |
| `kubemq-java/src/main/java/io/kubemq/sdk/auth/TokenResult.java` | Token + optional expiry hint from provider |
| `kubemq-java/src/main/java/io/kubemq/sdk/auth/CredentialException.java` | Checked exception with retryable flag |
| `kubemq-java/src/main/java/io/kubemq/sdk/auth/StaticTokenProvider.java` | Built-in provider for static tokens |
| `kubemq-java/src/main/java/io/kubemq/sdk/auth/CredentialManager.java` | Token caching, serialized refresh, proactive refresh scheduling |
| `kubemq-java/src/test/java/io/kubemq/sdk/unit/auth/AuthSecurityTest.java` | 55 unit tests covering all 6 REQ-AUTH requirements |

## Modified Files

| File | Changes |
|------|---------|
| `kubemq-java/src/main/java/io/kubemq/sdk/client/KubeMQClient.java` | AtomicReference<String> authTokenRef, Boolean tls (nullable for auto-detect), new fields (caCertPem, tlsCertPem, tlsKeyPem, serverNameOverride, insecureSkipVerify, credentialProvider, credentialManager), AuthInterceptor replaces MetadataInterceptor, removed metadata field, createSslContext(), classifyTlsException(), wrapGrpcException(), isLocalhostAddress(), reconnectWithCertReload(), toString() override, close() shuts down credential manager |
| `kubemq-java/src/main/java/io/kubemq/sdk/queues/QueuesClient.java` | Constructor: added caCertPem, tlsCertPem, tlsKeyPem, serverNameOverride, insecureSkipVerify, credentialProvider params; Boolean tls |
| `kubemq-java/src/main/java/io/kubemq/sdk/pubsub/PubSubClient.java` | Same constructor param additions as QueuesClient |
| `kubemq-java/src/main/java/io/kubemq/sdk/cq/CQClient.java` | Same constructor param additions as QueuesClient |
| `kubemq-java/src/test/java/io/kubemq/sdk/unit/client/KubeMQClientTest.java` | Removed getMetadata() assertions (field removed), added .tls(false) for remote addresses |
| `kubemq-java/src/test/java/io/kubemq/sdk/unit/client/KubeMQClientChannelTest.java` | MetadataInterceptorTests -> AuthInterceptorTests, removed getMetadata() assertions |
| `kubemq-java/src/test/java/io/kubemq/sdk/unit/client/ConnectionConfigTest.java` | Added .tls(false) for remote address test (TLS auto-detect) |

## Breaking Changes

| Change | Severity | Migration |
|--------|----------|-----------|
| `tls` field: `boolean` -> `Boolean` | Low | `isTls()` convenience method preserves backward compat. Existing `.tls(true)` / `.tls(false)` autobox correctly |
| Remote addresses default to TLS=true | Medium | Set `.tls(false)` explicitly for non-TLS remote servers |
| `MetadataInterceptor` renamed to `AuthInterceptor` | Low | Internal class; private visibility |
| `metadata` field removed from KubeMQClient | Low | Was internal state; `getMetadata()` no longer available |

## Test Summary

55 new tests in `AuthSecurityTest.java`:
- 7 TokenAuthenticationTests (REQ-AUTH-1)
- 16 TlsEncryptionTests (REQ-AUTH-2)
- 3 MutualTlsTests (REQ-AUTH-3)
- 16 CredentialProviderTests (REQ-AUTH-4)
- 4 SecurityBestPracticesTests (REQ-AUTH-5)
- 3 TlsReconnectionTests (REQ-AUTH-6)
- 2 ConfigurationExceptionTests
- 4 BuilderParameterTests

## Build Fix Notes

- 2 existing tests fixed: `ConnectionConfigTest.customAddress_isRespected` and `KubeMQClientTest.getAddress_returnsConfiguredAddress` — added `.tls(false)` because TLS auto-detection now defaults to true for non-localhost addresses
- Methods `isLocalhostAddress`, `wrapGrpcException`, `classifyTlsException`, `createSslContext` changed from `protected`/package-private to `public` for testability from external test packages
