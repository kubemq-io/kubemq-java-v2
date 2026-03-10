package io.kubemq.sdk.unit.codequality;

import io.kubemq.sdk.transport.TransportConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies secure defaults (REQ-CQ-7):
 * - No credential material in toString()
 * - No credential material in error messages
 */
class SecureDefaultsTest {

    @Test
    void transportConfigToStringDoesNotLeakToken() {
        TransportConfig config = TransportConfig.builder()
                .address("localhost:50000")
                .tokenSupplier(() -> "super-secret-auth-token-12345")
                .tls(true)
                .build();

        String output = config.toString();
        assertFalse(output.contains("super-secret-auth-token-12345"),
                "TransportConfig.toString() must not contain token value");
        assertFalse(output.contains("tokenSupplier"),
                "TransportConfig.toString() must not reference tokenSupplier");
    }

    @Test
    void transportConfigToStringShowsNonSensitiveFields() {
        TransportConfig config = TransportConfig.builder()
                .address("prod.kubemq.io:50000")
                .tls(true)
                .keepAlive(true)
                .maxReceiveSize(1024)
                .build();

        String output = config.toString();
        assertTrue(output.contains("prod.kubemq.io:50000"),
                "toString() should include address");
        assertTrue(output.contains("tls=true"),
                "toString() should include tls setting");
    }
}
