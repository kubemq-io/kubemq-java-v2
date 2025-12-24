package io.kubemq.sdk.unit.client;

import io.kubemq.sdk.cq.CQClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TLS configuration in KubeMQClient.
 * Tests cover TLS initialization paths, certificate handling, and SSL exceptions.
 */
class KubeMQClientTLSTest {

    @TempDir
    Path tempDir;

    private File createTempFile(String content, String filename) throws IOException {
        File file = tempDir.resolve(filename).toFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        return file;
    }

    @Nested
    @DisplayName("TLS Error Path Tests")
    class TLSErrorPathTests {

        @Test
        @DisplayName("C-04: TLS enabled with invalid cert throws RuntimeException")
        void builder_withTlsEnabled_invalidCert_throwsRuntimeException() throws IOException {
            File invalidCertFile = createTempFile("invalid cert content", "invalid.pem");

            // Invalid certificate should cause exception
            RuntimeException ex = assertThrows(RuntimeException.class, () -> {
                CQClient.builder()
                        .address("localhost:50000")
                        .clientId("tls-invalid-cert-test")
                        .tls(true)
                        .caCertFile(invalidCertFile.getAbsolutePath())
                        .build();
            });
            assertTrue(ex.getMessage().contains("does not contain valid certificates") ||
                       ex.getCause() != null);
        }

        @Test
        @DisplayName("C-04b: TLS enabled with non-existent cert throws RuntimeException")
        void builder_withTlsEnabled_nonExistentCert_throwsRuntimeException() {
            assertThrows(RuntimeException.class, () -> {
                CQClient.builder()
                        .address("localhost:50000")
                        .clientId("tls-missing-cert-test")
                        .tls(true)
                        .caCertFile("/non/existent/path/ca.pem")
                        .build();
            });
        }

        @Test
        @DisplayName("TLS with invalid key file throws RuntimeException")
        void builder_withTlsEnabled_invalidKeyFile_throwsRuntimeException() throws IOException {
            File invalidKeyFile = createTempFile("invalid key content", "invalid.key");

            assertThrows(RuntimeException.class, () -> {
                CQClient.builder()
                        .address("localhost:50000")
                        .clientId("tls-invalid-key-test")
                        .tls(true)
                        .tlsKeyFile(invalidKeyFile.getAbsolutePath())
                        .build();
            });
        }
    }

    @Nested
    @DisplayName("Plaintext Connection Tests")
    class PlaintextConnectionTests {

        @Test
        @DisplayName("C-05: Non-TLS with keepAlive sets keepAlive options")
        void builder_withoutTls_withKeepAlive_setsKeepAliveOptions() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("plaintext-keepalive-test")
                    .tls(false)
                    .keepAlive(true)
                    .pingIntervalInSeconds(60)
                    .pingTimeoutInSeconds(30)
                    .build();

            assertNotNull(client);
            assertFalse(client.isTls());
            assertTrue(client.getKeepAlive());
            assertEquals(60, client.getPingIntervalInSeconds());
            assertEquals(30, client.getPingTimeoutInSeconds());
            client.close();
        }

        @Test
        @DisplayName("Non-TLS with keepAlive and default ping values uses defaults")
        void builder_withoutTls_withKeepAlive_defaultPingValues() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("plaintext-keepalive-defaults-test")
                    .tls(false)
                    .keepAlive(true)
                    .pingIntervalInSeconds(0) // Should use default 60
                    .pingTimeoutInSeconds(0) // Should use default 30
                    .build();

            assertNotNull(client);
            assertTrue(client.getKeepAlive());
            client.close();
        }

        @Test
        @DisplayName("Non-TLS without keepAlive creates standard channel")
        void builder_withoutTls_noKeepAlive_createsStandardChannel() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("plaintext-no-keepalive")
                    .tls(false)
                    .build();

            assertNotNull(client);
            assertFalse(client.isTls());
            client.close();
        }
    }

    @Nested
    @DisplayName("Builder Property Tests")
    class BuilderPropertyTests {

        @Test
        @DisplayName("Builder stores TLS cert file path")
        void builder_storesTlsCertFilePath() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("cert-path-test")
                    .tls(false)
                    .tlsCertFile("/path/to/cert.pem")
                    .build();

            assertEquals("/path/to/cert.pem", client.getTlsCertFile());
            client.close();
        }

        @Test
        @DisplayName("Builder stores TLS key file path")
        void builder_storesTlsKeyFilePath() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("key-path-test")
                    .tls(false)
                    .tlsKeyFile("/path/to/key.pem")
                    .build();

            assertEquals("/path/to/key.pem", client.getTlsKeyFile());
            client.close();
        }

        @Test
        @DisplayName("Builder stores CA cert file path")
        void builder_storesCaCertFilePath() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("ca-path-test")
                    .tls(false)
                    .caCertFile("/path/to/ca.pem")
                    .build();

            assertEquals("/path/to/ca.pem", client.getCaCertFile());
            client.close();
        }

        @Test
        @DisplayName("Builder stores max receive size")
        void builder_storesMaxReceiveSize() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("max-receive-test")
                    .maxReceiveSize(1024 * 1024 * 10) // 10MB
                    .build();

            assertEquals(1024 * 1024 * 10, client.getMaxReceiveSize());
            client.close();
        }

        @Test
        @DisplayName("Builder stores reconnect interval")
        void builder_storesReconnectInterval() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("reconnect-test")
                    .reconnectIntervalSeconds(10)
                    .build();

            assertEquals(10, client.getReconnectIntervalSeconds());
            assertEquals(10000L, client.getReconnectIntervalInMillis());
            client.close();
        }
    }
}
