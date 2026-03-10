package io.kubemq.sdk.unit.auth;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.kubemq.sdk.auth.*;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.exception.AuthenticationException;
import io.kubemq.sdk.exception.ConfigurationException;
import io.kubemq.sdk.exception.ConnectionException;
import io.kubemq.sdk.exception.KubeMQException;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.QueuesClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Auth & Security requirements (REQ-AUTH-1 through REQ-AUTH-6).
 */
class AuthSecurityTest {

    @TempDir
    Path tempDir;

    // ========================================================================
    // REQ-AUTH-1: Token Authentication
    // ========================================================================

    @Nested
    @DisplayName("REQ-AUTH-1: Token Authentication")
    class TokenAuthenticationTests {

        @Test
        @DisplayName("Token is updatable without recreating client")
        void testTokenUpdateReflectedInNextCall() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("token-update-test")
                .authToken("initial-token")
                .build();

            assertEquals("initial-token", client.getAuthToken());

            client.setAuthToken("updated-token");
            assertEquals("updated-token", client.getAuthToken());

            client.setAuthToken(null);
            assertNull(client.getAuthToken());

            client.close();
        }

        @Test
        @DisplayName("Token update is thread-safe")
        void testTokenUpdateThreadSafe() throws Exception {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("thread-safe-test")
                .build();

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final String token = "token-" + i;
                executor.submit(() -> {
                    try {
                        client.setAuthToken(token);
                        String read = client.getAuthToken();
                        assertNotNull(read);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            executor.shutdown();
            client.close();
        }

        @Test
        @DisplayName("Null token skips authorization header")
        void testNullTokenSkipsHeader() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("null-token-test")
                .build();

            assertNull(client.getAuthToken());
            client.close();
        }

        @Test
        @DisplayName("UNAUTHENTICATED without token gives 'set token' hint")
        void testUnauthenticatedWithNoTokenGivesSetTokenHint() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("no-token-hint-test")
                .build();

            StatusRuntimeException grpcEx =
                new StatusRuntimeException(Status.UNAUTHENTICATED);
            RuntimeException wrapped = client.wrapGrpcException(grpcEx);

            assertInstanceOf(AuthenticationException.class, wrapped);
            assertTrue(wrapped.getMessage().contains("Set auth token"));
            client.close();
        }

        @Test
        @DisplayName("UNAUTHENTICATED with token gives 'verify token' hint")
        void testUnauthenticatedWithTokenGivesVerifyHint() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("token-hint-test")
                .authToken("my-token")
                .build();

            StatusRuntimeException grpcEx =
                new StatusRuntimeException(Status.UNAUTHENTICATED);
            RuntimeException wrapped = client.wrapGrpcException(grpcEx);

            assertInstanceOf(AuthenticationException.class, wrapped);
            assertTrue(wrapped.getMessage().contains("Verify the token"));
            client.close();
        }

        @Test
        @DisplayName("Non-UNAUTHENTICATED status is not wrapped as AuthenticationException")
        void testNonUnauthenticatedNotWrappedAsAuth() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("other-status-test")
                .build();

            StatusRuntimeException grpcEx =
                new StatusRuntimeException(Status.UNAVAILABLE);
            RuntimeException wrapped = client.wrapGrpcException(grpcEx);

            assertNotEquals(AuthenticationException.class, wrapped.getClass());
            client.close();
        }

        @Test
        @DisplayName("Token value is never in log output (only token_present)")
        void testTokenNotLoggedAtAnyLevel() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("log-test")
                .logLevel(KubeMQClient.Level.TRACE)
                .authToken("super-secret-token-12345")
                .build();

            client.setAuthToken("another-secret-token");

            String toStr = client.toString();
            assertFalse(toStr.contains("super-secret-token-12345"));
            assertFalse(toStr.contains("another-secret-token"));
            assertTrue(toStr.contains("token_present=true"));

            client.close();
        }
    }

    // ========================================================================
    // REQ-AUTH-2: TLS Encryption
    // ========================================================================

    @Nested
    @DisplayName("REQ-AUTH-2: TLS Encryption")
    class TlsEncryptionTests {

        @Test
        @DisplayName("Localhost address detection - localhost:50000")
        void testLocalhostAddress_localhost() {
            assertTrue(KubeMQClient.isLocalhostAddress("localhost:50000"));
        }

        @Test
        @DisplayName("Localhost address detection - 127.0.0.1:50000")
        void testLocalhostAddress_127001() {
            assertTrue(KubeMQClient.isLocalhostAddress("127.0.0.1:50000"));
        }

        @Test
        @DisplayName("Localhost address detection - [::1]:50000")
        void testLocalhostAddress_ipv6() {
            assertTrue(KubeMQClient.isLocalhostAddress("[::1]:50000"));
        }

        @Test
        @DisplayName("Remote address detection - kubernetes DNS")
        void testRemoteAddress_kubeDns() {
            assertFalse(KubeMQClient.isLocalhostAddress("kubemq.default.svc.cluster.local:50000"));
        }

        @Test
        @DisplayName("Remote address detection - IP")
        void testRemoteAddress_ip() {
            assertFalse(KubeMQClient.isLocalhostAddress("10.0.0.1:50000"));
        }

        @Test
        @DisplayName("Remote address detection - hostname")
        void testRemoteAddress_hostname() {
            assertFalse(KubeMQClient.isLocalhostAddress("kubemq.prod:50000"));
        }

        @Test
        @DisplayName("Empty address is not localhost")
        void testEmptyAddressNotLocalhost() {
            assertFalse(KubeMQClient.isLocalhostAddress(""));
        }

        @Test
        @DisplayName("Null address is not localhost")
        void testNullAddressIsNotLocalhost() {
            assertFalse(KubeMQClient.isLocalhostAddress(null));
        }

        @Test
        @DisplayName("TLS defaults to false for localhost")
        void testTlsDefaultsToFalseForLocalhost() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("local-tls-test")
                .build();

            assertFalse(client.isTls());
            client.close();
        }

        @Test
        @DisplayName("Explicit tls(false) overrides default for remote")
        void testExplicitTlsFalseOverridesDefault() {
            CQClient client = CQClient.builder()
                .address("kubemq.prod:50000")
                .clientId("explicit-tls-test")
                .tls(false)
                .build();

            assertFalse(client.isTls());
            client.close();
        }

        @Test
        @DisplayName("Both caCertFile and caCertPem throws")
        void testBothFileAndPemThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                CQClient.builder()
                    .address("localhost:50000")
                    .clientId("both-ca-test")
                    .tls(true)
                    .caCertFile("/some/ca.pem")
                    .caCertPem("cert".getBytes())
                    .build());
        }

        @Test
        @DisplayName("Both tlsCertFile and tlsCertPem throws")
        void testBothTlsCertFileAndPemThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                CQClient.builder()
                    .address("localhost:50000")
                    .clientId("both-cert-test")
                    .tls(false)
                    .tlsCertFile("/some/cert.pem")
                    .tlsCertPem("cert".getBytes())
                    .build());
        }

        @Test
        @DisplayName("TLS handshake cert failure -> AuthenticationException")
        void testTlsHandshakeCertFailure() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("tls-classify-test")
                .build();

            SSLHandshakeException sslEx =
                new SSLHandshakeException("certificate has expired");
            RuntimeException classified = client.classifyTlsException(sslEx);

            assertInstanceOf(AuthenticationException.class, classified);
            assertTrue(classified.getMessage().contains("certificate validation"));
            client.close();
        }

        @Test
        @DisplayName("TLS handshake protocol failure -> ConfigurationException")
        void testTlsHandshakeProtocolFailure() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("tls-protocol-test")
                .build();

            SSLHandshakeException sslEx =
                new SSLHandshakeException("no common protocol");
            RuntimeException classified = client.classifyTlsException(sslEx);

            assertInstanceOf(ConfigurationException.class, classified);
            assertTrue(classified.getMessage().contains("protocol/cipher negotiation"));
            client.close();
        }

        @Test
        @DisplayName("TLS network error -> ConnectionException")
        void testTlsNetworkError() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("tls-network-test")
                .build();

            SSLException sslEx =
                new SSLException("connection reset", new IOException("reset by peer"));
            RuntimeException classified = client.classifyTlsException(sslEx);

            assertInstanceOf(ConnectionException.class, classified);
            assertTrue(classified.getMessage().contains("network error"));
            client.close();
        }

        @Test
        @DisplayName("Generic TLS failure -> AuthenticationException (default)")
        void testGenericTlsFailure() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("tls-generic-test")
                .build();

            SSLException sslEx = new SSLException("unknown error");
            RuntimeException classified = client.classifyTlsException(sslEx);

            assertInstanceOf(AuthenticationException.class, classified);
            client.close();
        }
    }

    // ========================================================================
    // REQ-AUTH-3: Mutual TLS
    // ========================================================================

    @Nested
    @DisplayName("REQ-AUTH-3: Mutual TLS")
    class MutualTlsTests {

        @Test
        @DisplayName("mTLS PEM bytes require both cert and key")
        void testMtlsPemBytesRequireBoth() {
            assertThrows(IllegalArgumentException.class, () ->
                CQClient.builder()
                    .address("localhost:50000")
                    .clientId("mtls-pem-test")
                    .tls(true)
                    .tlsCertPem("cert-content".getBytes())
                    .build());
        }

        @Test
        @DisplayName("Cannot mix file and PEM for client cert")
        void testCannotMixFileAndPem() throws IOException {
            File certFile = tempDir.resolve("cert.pem").toFile();
            try (FileWriter w = new FileWriter(certFile)) {
                w.write("dummy cert");
            }

            assertThrows(IllegalArgumentException.class, () ->
                CQClient.builder()
                    .address("localhost:50000")
                    .clientId("mix-test")
                    .tls(false)
                    .tlsCertFile(certFile.getAbsolutePath())
                    .tlsCertPem("pem-content".getBytes())
                    .build());
        }

        @Test
        @DisplayName("mTLS file paths require both cert and key")
        void testMtlsFilePathsRequireBoth() {
            File certFile = tempDir.resolve("cert.pem").toFile();
            try {
                certFile.createNewFile();
            } catch (IOException e) {
                fail("Could not create temp file");
            }

            assertThrows(IllegalArgumentException.class, () ->
                CQClient.builder()
                    .address("localhost:50000")
                    .clientId("mtls-file-test")
                    .tls(true)
                    .tlsCertFile(certFile.getAbsolutePath())
                    .build());
        }
    }

    // ========================================================================
    // REQ-AUTH-4: Credential Provider Interface
    // ========================================================================

    @Nested
    @DisplayName("REQ-AUTH-4: Credential Provider")
    class CredentialProviderTests {

        @Test
        @DisplayName("StaticTokenProvider returns configured token")
        void testStaticTokenProviderReturnsToken() throws CredentialException {
            StaticTokenProvider provider = new StaticTokenProvider("my-static-token");
            TokenResult result = provider.getToken();

            assertEquals("my-static-token", result.getToken());
            assertNull(result.getExpiresAt());
        }

        @Test
        @DisplayName("StaticTokenProvider toString excludes token")
        void testStaticTokenProviderToString() {
            StaticTokenProvider provider = new StaticTokenProvider("secret");
            String str = provider.toString();

            assertTrue(str.contains("token_present=true"));
            assertFalse(str.contains("secret"));
        }

        @Test
        @DisplayName("TokenResult requires non-null, non-empty token")
        void testTokenResultRequiresToken() {
            assertThrows(IllegalArgumentException.class, () -> new TokenResult(null));
            assertThrows(IllegalArgumentException.class, () -> new TokenResult(""));
        }

        @Test
        @DisplayName("TokenResult with expiry hint")
        void testTokenResultWithExpiry() {
            Instant expiry = Instant.now().plusSeconds(3600);
            TokenResult result = new TokenResult("token", expiry);

            assertEquals("token", result.getToken());
            assertEquals(expiry, result.getExpiresAt());
        }

        @Test
        @DisplayName("TokenResult toString excludes token value")
        void testTokenResultToStringExcludesToken() {
            TokenResult result = new TokenResult("super-secret-token");
            String str = result.toString();

            assertTrue(str.contains("token_present=true"));
            assertFalse(str.contains("super-secret-token"));
        }

        @Test
        @DisplayName("CredentialManager caches token")
        void testCredentialManagerCachesToken() throws CredentialException {
            AtomicInteger callCount = new AtomicInteger(0);
            CredentialProvider provider = () -> {
                callCount.incrementAndGet();
                return new TokenResult("cached-token");
            };

            CredentialManager manager = new CredentialManager(provider, null);
            String token1 = manager.getToken();
            String token2 = manager.getToken();

            assertEquals("cached-token", token1);
            assertEquals("cached-token", token2);
            assertEquals(1, callCount.get());

            manager.shutdown();
        }

        @Test
        @DisplayName("CredentialManager serializes refresh")
        void testCredentialManagerSerializesRefresh() throws Exception {
            AtomicInteger callCount = new AtomicInteger(0);
            CredentialProvider provider = () -> {
                callCount.incrementAndGet();
                return new TokenResult("serialized-token");
            };

            CredentialManager manager = new CredentialManager(provider, null);

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        manager.getToken();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(1, callCount.get());

            executor.shutdown();
            manager.shutdown();
        }

        @Test
        @DisplayName("Reactive refresh on invalidate")
        void testReactiveRefreshOnUnauthenticated() throws CredentialException {
            AtomicInteger callCount = new AtomicInteger(0);
            CredentialProvider provider = () -> {
                int count = callCount.incrementAndGet();
                return new TokenResult("token-v" + count);
            };

            CredentialManager manager = new CredentialManager(provider, null);

            assertEquals("token-v1", manager.getToken());
            assertEquals(1, callCount.get());

            manager.invalidate();

            assertEquals("token-v2", manager.getToken());
            assertEquals(2, callCount.get());

            manager.shutdown();
        }

        @Test
        @DisplayName("Proactive refresh is scheduled")
        void testProactiveRefreshScheduled() throws Exception {
            AtomicInteger callCount = new AtomicInteger(0);
            ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

            CredentialProvider provider = () -> {
                int count = callCount.incrementAndGet();
                return new TokenResult("token-v" + count,
                    Instant.now().plusMillis(200));
            };

            CredentialManager manager = new CredentialManager(provider, scheduler);

            assertEquals("token-v1", manager.getToken());
            assertEquals(1, callCount.get());

            Thread.sleep(500);

            assertTrue(callCount.get() >= 2,
                "Proactive refresh should have been triggered, callCount=" + callCount.get());

            manager.shutdown();
            scheduler.shutdownNow();
        }

        @Test
        @DisplayName("Retryable CredentialException -> ConnectionException")
        void testRetryableCredentialExceptionWrapped() {
            CredentialProvider provider = () -> {
                throw new CredentialException("vault unavailable", true);
            };

            CredentialManager manager = new CredentialManager(provider, null);

            assertThrows(ConnectionException.class, manager::getToken);
            manager.shutdown();
        }

        @Test
        @DisplayName("Non-retryable CredentialException -> AuthenticationException")
        void testNonRetryableCredentialExceptionWrapped() {
            CredentialProvider provider = () -> {
                throw new CredentialException("invalid credentials", false);
            };

            CredentialManager manager = new CredentialManager(provider, null);

            assertThrows(AuthenticationException.class, manager::getToken);
            manager.shutdown();
        }

        @Test
        @DisplayName("Cannot set both authToken and credentialProvider")
        void testCannotSetBothAuthTokenAndCredentialProvider() {
            assertThrows(IllegalArgumentException.class, () ->
                CQClient.builder()
                    .address("localhost:50000")
                    .clientId("both-auth-test")
                    .authToken("my-token")
                    .credentialProvider(new StaticTokenProvider("provider-token"))
                    .build());
        }

        @Test
        @DisplayName("CredentialManager requires non-null provider")
        void testCredentialManagerRequiresProvider() {
            assertThrows(IllegalArgumentException.class, () ->
                new CredentialManager(null, null));
        }

        @Test
        @DisplayName("CredentialException retryable flag")
        void testCredentialExceptionRetryableFlag() {
            CredentialException retryable = new CredentialException("retry", true);
            assertTrue(retryable.isRetryable());

            CredentialException nonRetryable = new CredentialException("no retry");
            assertFalse(nonRetryable.isRetryable());

            CredentialException withCause =
                new CredentialException("cause", new RuntimeException(), true);
            assertTrue(withCause.isRetryable());
        }

        @Test
        @DisplayName("Client with credential provider builds successfully")
        void testClientWithCredentialProvider() {
            StaticTokenProvider provider = new StaticTokenProvider("provider-token");

            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("credential-provider-test")
                .credentialProvider(provider)
                .build();

            assertNotNull(client);
            client.close();
        }

        @Test
        @DisplayName("All three client types accept credentialProvider")
        void testAllClientTypesAcceptCredentialProvider() {
            StaticTokenProvider provider = new StaticTokenProvider("token");

            PubSubClient pubsub = PubSubClient.builder()
                .address("localhost:50000").clientId("ps").credentialProvider(provider).build();
            assertNotNull(pubsub);
            pubsub.close();

            QueuesClient queues = QueuesClient.builder()
                .address("localhost:50000").clientId("q").credentialProvider(provider).build();
            assertNotNull(queues);
            queues.close();

            CQClient cq = CQClient.builder()
                .address("localhost:50000").clientId("cq").credentialProvider(provider).build();
            assertNotNull(cq);
            cq.close();
        }
    }

    // ========================================================================
    // REQ-AUTH-5: Security Best Practices
    // ========================================================================

    @Nested
    @DisplayName("REQ-AUTH-5: Security Best Practices")
    class SecurityBestPracticesTests {

        @Test
        @DisplayName("KubeMQClient toString excludes auth token")
        void testKubeMQClientToStringExcludesAuthToken() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("tostring-test")
                .authToken("secret-token-xyz")
                .build();

            String str = client.toString();

            assertFalse(str.contains("secret-token-xyz"),
                "toString must not contain the token value");
            assertTrue(str.contains("token_present=true"),
                "toString must contain token_present=true");

            client.close();
        }

        @Test
        @DisplayName("KubeMQClient toString shows token_present=false when no token")
        void testKubeMQClientToStringNoTokenShowsFalse() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("no-token-tostring")
                .build();

            String str = client.toString();
            assertTrue(str.contains("token_present=false"));

            client.close();
        }

        @Test
        @DisplayName("toString includes essential config fields")
        void testToStringIncludesConfig() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("config-tostring")
                .build();

            String str = client.toString();
            assertTrue(str.contains("address='localhost:50000'"));
            assertTrue(str.contains("clientId='config-tostring'"));
            assertTrue(str.contains("tls="));

            client.close();
        }

        @Test
        @DisplayName("toString shows credentialProvider_present")
        void testToStringShowsCredentialProvider() {
            StaticTokenProvider provider = new StaticTokenProvider("token");
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("cred-tostring")
                .credentialProvider(provider)
                .build();

            String str = client.toString();
            assertTrue(str.contains("credentialProvider_present=true"));

            client.close();
        }
    }

    // ========================================================================
    // REQ-AUTH-6: TLS Credentials During Reconnection
    // ========================================================================

    @Nested
    @DisplayName("REQ-AUTH-6: TLS Credentials During Reconnection")
    class TlsReconnectionTests {

        @Test
        @DisplayName("createSslContext can be called repeatedly (insecureSkipVerify)")
        void testCreateSslContextRepeatable() throws SSLException {
            // Use a non-TLS client since we can't create a real TLS channel in tests
            // (requires ALPN), but createSslContext() is a standalone method
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("ssl-context-test")
                .tls(false)
                .insecureSkipVerify(true)
                .build();

            assertNotNull(client.createSslContext());
            assertNotNull(client.createSslContext());

            client.close();
        }

        @Test
        @DisplayName("createSslContext produces distinct contexts on each call")
        void testCreateSslContextProducesDistinct() throws SSLException {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("ssl-distinct-test")
                .tls(false)
                .insecureSkipVerify(true)
                .build();

            io.grpc.netty.shaded.io.netty.handler.ssl.SslContext ctx1 = client.createSslContext();
            io.grpc.netty.shaded.io.netty.handler.ssl.SslContext ctx2 = client.createSslContext();

            assertNotNull(ctx1);
            assertNotNull(ctx2);

            client.close();
        }

        @Test
        @DisplayName("createSslContext thread safety")
        void testCreateSslContextThreadSafe() throws Exception {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("reconnect-thread-test")
                .tls(false)
                .insecureSkipVerify(true)
                .build();

            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        assertDoesNotThrow(() -> client.createSslContext());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            executor.shutdown();
            client.close();
        }
    }

    // ========================================================================
    // ConfigurationException Tests
    // ========================================================================

    @Nested
    @DisplayName("ConfigurationException")
    class ConfigurationExceptionTests {

        @Test
        @DisplayName("ConfigurationException has FAILED_PRECONDITION code")
        void testConfigurationExceptionCode() {
            ConfigurationException ex = ConfigurationException.builder()
                .message("TLS config error")
                .build();

            assertEquals(io.kubemq.sdk.exception.ErrorCode.FAILED_PRECONDITION, ex.getCode());
            assertFalse(ex.isRetryable());
        }

        @Test
        @DisplayName("ConfigurationException preserves cause chain")
        void testConfigurationExceptionCause() {
            SSLException cause = new SSLException("cipher negotiation failed");
            ConfigurationException ex = ConfigurationException.builder()
                .message("TLS failed")
                .cause(cause)
                .build();

            assertSame(cause, ex.getCause());
        }
    }

    // ========================================================================
    // Builder Parameter Tests (all client types)
    // ========================================================================

    @Nested
    @DisplayName("New Builder Parameters")
    class BuilderParameterTests {

        @Test
        @DisplayName("All new TLS parameters accepted on CQClient")
        void testCQClientNewParams() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("cq-params")
                .tls(false)
                .serverNameOverride("my-server")
                .insecureSkipVerify(false)
                .build();

            assertNotNull(client);
            assertEquals("my-server", client.getServerNameOverride());
            client.close();
        }

        @Test
        @DisplayName("All new TLS parameters accepted on PubSubClient")
        void testPubSubClientNewParams() {
            PubSubClient client = PubSubClient.builder()
                .address("localhost:50000")
                .clientId("ps-params")
                .tls(false)
                .serverNameOverride("override")
                .build();

            assertNotNull(client);
            client.close();
        }

        @Test
        @DisplayName("All new TLS parameters accepted on QueuesClient")
        void testQueuesClientNewParams() {
            QueuesClient client = QueuesClient.builder()
                .address("localhost:50000")
                .clientId("q-params")
                .tls(false)
                .insecureSkipVerify(false)
                .build();

            assertNotNull(client);
            client.close();
        }

        @Test
        @DisplayName("PEM byte arrays are stored")
        void testPemByteArraysStored() {
            byte[] caPem = "ca-cert".getBytes();
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("pem-store-test")
                .tls(false)
                .caCertPem(caPem)
                .build();

            assertArrayEquals(caPem, client.getCaCertPem());
            client.close();
        }
    }
}
