package io.kubemq.sdk.unit.transport;

import io.kubemq.sdk.transport.TransportConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TransportConfig} focusing on secure defaults (REQ-CQ-7).
 */
class TransportConfigTest {

    @Test
    void toStringExcludesTokenSupplier() {
        TransportConfig config = TransportConfig.builder()
                .address("localhost:50000")
                .tokenSupplier(() -> "secret-token-value")
                .tls(false)
                .build();

        String str = config.toString();
        assertFalse(str.contains("secret-token-value"),
                "toString() must not contain auth token value");
        assertFalse(str.contains("tokenSupplier"),
                "toString() must not reference tokenSupplier field");
        assertTrue(str.contains("localhost:50000"),
                "toString() should contain the address");
    }

    @Test
    void builderDefaults() {
        TransportConfig config = TransportConfig.builder()
                .address("myserver:50000")
                .build();

        assertEquals("myserver:50000", config.getAddress());
        assertFalse(config.isTls());
        assertNull(config.getTokenSupplier());
        assertNull(config.getTlsCertFile());
        assertNull(config.getCaCertFile());
        assertFalse(config.isInsecureSkipVerify());
    }

    @Test
    void builderWithTls() {
        TransportConfig config = TransportConfig.builder()
                .address("remote:50000")
                .tls(true)
                .caCertFile("/path/to/ca.pem")
                .keepAlive(true)
                .keepAliveTimeSeconds(15)
                .keepAliveTimeoutSeconds(10)
                .build();

        assertTrue(config.isTls());
        assertEquals("/path/to/ca.pem", config.getCaCertFile());
        assertTrue(config.isKeepAlive());
        assertEquals(15, config.getKeepAliveTimeSeconds());
        assertEquals(10, config.getKeepAliveTimeoutSeconds());
    }

    @Test
    void tokenSupplierIsCallable() {
        String expectedToken = "my-test-token";
        TransportConfig config = TransportConfig.builder()
                .address("localhost:50000")
                .tokenSupplier(() -> expectedToken)
                .build();

        assertNotNull(config.getTokenSupplier());
        assertEquals(expectedToken, config.getTokenSupplier().get());
    }
}
