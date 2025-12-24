package io.kubemq.sdk.unit.client;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.QueuesClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KubeMQClient builder, configuration, and validation.
 * These tests use concrete implementations (CQClient, PubSubClient, QueuesClient)
 * to test the common KubeMQClient functionality.
 *
 * Note: Tests that require actual gRPC connections are in integration tests.
 */
class KubeMQClientTest {

    @Nested
    class BuilderValidationTests {

        @Test
        void builder_withMinimalConfig_createsClient() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("test-client")
                    .build();

            assertNotNull(client);
            assertEquals("localhost:50000", client.getAddress());
            assertEquals("test-client", client.getClientId());
            client.close();
        }

        @Test
        void builder_withNullAddress_throwsException() {
            assertThrows(IllegalArgumentException.class, () -> {
                CQClient.builder()
                        .address(null)
                        .clientId("test-client")
                        .build();
            });
        }

        @Test
        void builder_withNullClientId_throwsException() {
            assertThrows(IllegalArgumentException.class, () -> {
                CQClient.builder()
                        .address("localhost:50000")
                        .clientId(null)
                        .build();
            });
        }

        @Test
        void builder_withAllOptions_createsClient() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("full-config-client")
                    .authToken("my-auth-token")
                    .tls(false)
                    .maxReceiveSize(1024 * 1024 * 50) // 50MB
                    .reconnectIntervalSeconds(5)
                    .keepAlive(true)
                    .pingIntervalInSeconds(30)
                    .pingTimeoutInSeconds(10)
                    .logLevel(KubeMQClient.Level.DEBUG)
                    .build();

            assertNotNull(client);
            assertEquals("localhost:50000", client.getAddress());
            assertEquals("full-config-client", client.getClientId());
            assertEquals("my-auth-token", client.getAuthToken());
            assertFalse(client.isTls());
            assertEquals(1024 * 1024 * 50, client.getMaxReceiveSize());
            assertEquals(5, client.getReconnectIntervalSeconds());
            assertTrue(client.getKeepAlive());
            assertEquals(30, client.getPingIntervalInSeconds());
            assertEquals(10, client.getPingTimeoutInSeconds());
            assertEquals(KubeMQClient.Level.DEBUG, client.getLogLevel());
            client.close();
        }

        @Test
        void builder_withDefaultMaxReceiveSize_setsDefault() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("test-client")
                    .maxReceiveSize(0)
                    .build();

            // Default is 100MB when 0 or negative
            assertEquals(1024 * 1024 * 100, client.getMaxReceiveSize());
            client.close();
        }

        @Test
        void builder_withNegativeMaxReceiveSize_setsDefault() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("test-client")
                    .maxReceiveSize(-1)
                    .build();

            // Default is 100MB when 0 or negative
            assertEquals(1024 * 1024 * 100, client.getMaxReceiveSize());
            client.close();
        }

        @Test
        void builder_withDefaultReconnectInterval_setsDefault() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("test-client")
                    .reconnectIntervalSeconds(0)
                    .build();

            // Default is 1 second when 0 or negative
            assertEquals(1, client.getReconnectIntervalSeconds());
            client.close();
        }

        @Test
        void builder_withNegativeReconnectInterval_setsDefault() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("test-client")
                    .reconnectIntervalSeconds(-5)
                    .build();

            // Default is 1 second when 0 or negative
            assertEquals(1, client.getReconnectIntervalSeconds());
            client.close();
        }
    }

    @Nested
    class LogLevelTests {

        @Test
        void allLogLevels_exist() {
            assertEquals(6, KubeMQClient.Level.values().length);
            assertNotNull(KubeMQClient.Level.TRACE);
            assertNotNull(KubeMQClient.Level.DEBUG);
            assertNotNull(KubeMQClient.Level.INFO);
            assertNotNull(KubeMQClient.Level.WARN);
            assertNotNull(KubeMQClient.Level.ERROR);
            assertNotNull(KubeMQClient.Level.OFF);
        }

        @Test
        void valueOf_validLevels_returnsCorrectEnum() {
            assertEquals(KubeMQClient.Level.TRACE, KubeMQClient.Level.valueOf("TRACE"));
            assertEquals(KubeMQClient.Level.DEBUG, KubeMQClient.Level.valueOf("DEBUG"));
            assertEquals(KubeMQClient.Level.INFO, KubeMQClient.Level.valueOf("INFO"));
            assertEquals(KubeMQClient.Level.WARN, KubeMQClient.Level.valueOf("WARN"));
            assertEquals(KubeMQClient.Level.ERROR, KubeMQClient.Level.valueOf("ERROR"));
            assertEquals(KubeMQClient.Level.OFF, KubeMQClient.Level.valueOf("OFF"));
        }

        @Test
        void builder_withNullLogLevel_defaultsToInfo() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("test-client")
                    .logLevel(null)
                    .build();

            assertEquals(KubeMQClient.Level.INFO, client.getLogLevel());
            client.close();
        }

        @Test
        void builder_withTraceLevel_setsTraceLevel() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("test-client")
                    .logLevel(KubeMQClient.Level.TRACE)
                    .build();

            assertEquals(KubeMQClient.Level.TRACE, client.getLogLevel());
            client.close();
        }
    }

    @Nested
    class TLSConfigurationTests {

        @Test
        void builder_withTlsDisabled_clearsFlag() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("non-tls-client")
                    .tls(false)
                    .build();

            assertFalse(client.isTls());
            client.close();
        }

        // Note: TLS-enabled tests require actual certificates and gRPC ALPN setup,
        // so they are covered in integration tests rather than unit tests.
    }

    @Nested
    class CloseTests {

        @Test
        void close_multipleTimess_doesNotThrow() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("close-test-client")
                    .build();

            // Should not throw even when called multiple times
            assertDoesNotThrow(() -> {
                client.close();
                client.close();
                client.close();
            });
        }
    }

    @Nested
    class GetterTests {

        @Test
        void getAddress_returnsConfiguredAddress() {
            CQClient client = CQClient.builder()
                    .address("custom-host:12345")
                    .clientId("test")
                    .build();

            assertEquals("custom-host:12345", client.getAddress());
            client.close();
        }

        @Test
        void getClientId_returnsConfiguredId() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("my-unique-client-id")
                    .build();

            assertEquals("my-unique-client-id", client.getClientId());
            client.close();
        }

        @Test
        void getClient_returnsBlockingStub() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("test")
                    .build();

            assertNotNull(client.getClient());
            client.close();
        }

        @Test
        void getAsyncClient_returnsAsyncStub() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("test")
                    .build();

            assertNotNull(client.getAsyncClient());
            client.close();
        }
    }

    @Nested
    class DifferentClientTypesTests {

        @Test
        void pubSubClient_builderWorks() {
            PubSubClient client = PubSubClient.builder()
                    .address("localhost:50000")
                    .clientId("pubsub-test")
                    .build();

            assertNotNull(client);
            assertEquals("localhost:50000", client.getAddress());
            assertEquals("pubsub-test", client.getClientId());
            client.close();
        }

        @Test
        void queuesClient_builderWorks() {
            QueuesClient client = QueuesClient.builder()
                    .address("localhost:50000")
                    .clientId("queues-test")
                    .build();

            assertNotNull(client);
            assertEquals("localhost:50000", client.getAddress());
            assertEquals("queues-test", client.getClientId());
            client.close();
        }
    }

    @Nested
    class KeepAliveConfigurationTests {

        @Test
        void builder_withKeepAliveEnabled_setsKeepAlive() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("keepalive-client")
                    .keepAlive(true)
                    .pingIntervalInSeconds(60)
                    .pingTimeoutInSeconds(30)
                    .build();

            assertTrue(client.getKeepAlive());
            assertEquals(60, client.getPingIntervalInSeconds());
            assertEquals(30, client.getPingTimeoutInSeconds());
            client.close();
        }

        @Test
        void builder_withKeepAliveDisabled_setsKeepAliveFalse() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("no-keepalive-client")
                    .keepAlive(false)
                    .build();

            assertFalse(client.getKeepAlive());
            client.close();
        }

        @Test
        void builder_withNullKeepAlive_allowsNull() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("null-keepalive-client")
                    .keepAlive(null)
                    .build();

            assertNull(client.getKeepAlive());
            client.close();
        }
    }

    @Nested
    class AuthTokenTests {

        @Test
        void builder_withAuthToken_setsToken() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("auth-client")
                    .authToken("secret-token-123")
                    .build();

            assertEquals("secret-token-123", client.getAuthToken());
            assertNotNull(client.getMetadata());
            client.close();
        }

        @Test
        void builder_withoutAuthToken_hasNullMetadata() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("no-auth-client")
                    .build();

            assertNull(client.getAuthToken());
            assertNull(client.getMetadata());
            client.close();
        }

        @Test
        void builder_withEmptyAuthToken_hasNullMetadata() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("empty-auth-client")
                    .authToken("")
                    .build();

            assertEquals("", client.getAuthToken());
            assertNull(client.getMetadata());
            client.close();
        }
    }
}
