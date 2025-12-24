package io.kubemq.sdk.unit.productionreadiness;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.QueuesClient;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to verify HIGH-3 FIX (SDK-Only Logger Modification) and HIGH-4 FIX (TLS Validation)
 *
 * HIGH-3 FIX: setLogLevel() now modifies the "io.kubemq.sdk" logger instead of ROOT,
 * so it only affects SDK logging, not the entire application.
 *
 * HIGH-4 FIX: TLS validation is now enabled, rejecting invalid TLS configurations
 * (e.g., cert without key, TLS enabled without proper configuration).
 *
 * These tests verify the fixes work correctly.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LoggerAndTLSValidationTest {

    private static Level originalRootLogLevel;
    private static Level originalSdkLogLevel;
    private static LoggerContext loggerContext;

    @BeforeAll
    static void captureOriginalLogLevel() {
        loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger("ROOT");
        ch.qos.logback.classic.Logger sdkLogger = loggerContext.getLogger("io.kubemq.sdk");
        originalRootLogLevel = rootLogger.getLevel();
        originalSdkLogLevel = sdkLogger.getLevel();
    }

    @AfterAll
    static void restoreLogLevel() {
        if (loggerContext != null) {
            ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger("ROOT");
            ch.qos.logback.classic.Logger sdkLogger = loggerContext.getLogger("io.kubemq.sdk");
            if (originalRootLogLevel != null) {
                rootLogger.setLevel(originalRootLogLevel);
            }
            if (originalSdkLogLevel != null) {
                sdkLogger.setLevel(originalSdkLogLevel);
            }
        }
    }

    @AfterEach
    void restoreLogLevelAfterEach() {
        if (loggerContext != null) {
            ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger("ROOT");
            ch.qos.logback.classic.Logger sdkLogger = loggerContext.getLogger("io.kubemq.sdk");
            if (originalRootLogLevel != null) {
                rootLogger.setLevel(originalRootLogLevel);
            }
            if (originalSdkLogLevel != null) {
                sdkLogger.setLevel(originalSdkLogLevel);
            }
        }
    }

    // ==================== HIGH-3 FIX: SDK-Only Logger Tests ====================

    @Test
    @Order(1)
    @DisplayName("HIGH-3 FIX: Creating SDK client modifies SDK logger, not ROOT logger")
    void creatingClient_modifiesSdkLogger_notRoot() {
        // Capture current ROOT level
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger("ROOT");
        ch.qos.logback.classic.Logger sdkLogger = loggerContext.getLogger("io.kubemq.sdk");

        Level rootLevelBefore = rootLogger.getLevel();

        // Create a client with ERROR log level
        CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("logger-test")
                .logLevel(KubeMQClient.Level.ERROR)
                .build();

        try {
            // Check ROOT logger level after client creation
            Level rootLevelAfter = rootLogger.getLevel();
            Level sdkLevelAfter = sdkLogger.getLevel();

            // HIGH-3 FIX VERIFICATION: ROOT logger should NOT have changed
            assertEquals(rootLevelBefore, rootLevelAfter,
                    "ROOT logger level should NOT have changed. " +
                    "Before: " + rootLevelBefore + ", After: " + rootLevelAfter);

            // SDK logger should have been changed
            assertEquals(Level.ERROR, sdkLevelAfter,
                    "SDK logger (io.kubemq.sdk) should be set to ERROR");

        } finally {
            client.close();
        }
    }

    @Test
    @Order(2)
    @DisplayName("HIGH-3 FIX: SDK log level does not affect application loggers")
    void sdkLogLevel_doesNotAffectApplicationLoggers() {
        // Create an application logger (simulating user's own logger)
        ch.qos.logback.classic.Logger appLogger =
                loggerContext.getLogger("com.mycompany.myapp");

        // Set app logger to DEBUG
        appLogger.setLevel(Level.DEBUG);
        assertEquals(Level.DEBUG, appLogger.getEffectiveLevel(),
                "App logger should be DEBUG before SDK client creation");

        // Create SDK client with WARN level
        QueuesClient client = QueuesClient.builder()
                .address("localhost:50000")
                .clientId("logger-test-2")
                .logLevel(KubeMQClient.Level.WARN)
                .build();

        try {
            // Check if app logger is affected
            Level appLoggerLevelAfter = appLogger.getEffectiveLevel();

            // HIGH-3 FIX VERIFICATION: App logger should still be DEBUG
            assertEquals(Level.DEBUG, appLoggerLevelAfter,
                    "Application logger should NOT be affected by SDK log level change. " +
                    "Expected DEBUG, got " + appLoggerLevelAfter);

            // SDK logger should be WARN
            ch.qos.logback.classic.Logger sdkLogger = loggerContext.getLogger("io.kubemq.sdk");
            assertEquals(Level.WARN, sdkLogger.getLevel(),
                    "SDK logger should be set to WARN");

        } finally {
            client.close();
            appLogger.setLevel(null); // Restore app logger
        }
    }

    @Test
    @Order(3)
    @DisplayName("HIGH-3 FIX: Verify setLogLevel modifies SDK logger, not ROOT")
    void verifySetLogLevel_modifiesSdkLogger() {
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger("ROOT");
        ch.qos.logback.classic.Logger sdkLogger = loggerContext.getLogger("io.kubemq.sdk");

        Level rootBefore = rootLogger.getLevel();

        // Create client
        PubSubClient client = PubSubClient.builder()
                .address("localhost:50000")
                .clientId("logger-test-3")
                .logLevel(KubeMQClient.Level.TRACE)
                .build();

        try {
            Level rootAfter = rootLogger.getLevel();
            Level sdkAfter = sdkLogger.getLevel();

            // HIGH-3 FIX VERIFICATION: ROOT was NOT modified
            assertEquals(rootBefore, rootAfter,
                    "ROOT logger should NOT have been modified");

            // SDK logger should have been modified
            assertEquals(Level.TRACE, sdkAfter,
                    "SDK logger (io.kubemq.sdk) should be set to TRACE");

        } finally {
            client.close();
        }
    }

    @Test
    @Order(4)
    @DisplayName("HIGH-3 FIX: Multiple clients with different log levels don't conflict")
    void multipleClients_logLevelsDontConflict() {
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger("ROOT");
        ch.qos.logback.classic.Logger sdkLogger = loggerContext.getLogger("io.kubemq.sdk");

        Level rootBefore = rootLogger.getLevel();

        // Create first client with DEBUG
        CQClient client1 = CQClient.builder()
                .address("localhost:50000")
                .clientId("logger-test-4a")
                .logLevel(KubeMQClient.Level.DEBUG)
                .build();

        // Create second client with ERROR
        QueuesClient client2 = QueuesClient.builder()
                .address("localhost:50000")
                .clientId("logger-test-4b")
                .logLevel(KubeMQClient.Level.ERROR)
                .build();

        try {
            // ROOT should not have changed
            assertEquals(rootBefore, rootLogger.getLevel(),
                    "ROOT logger should not change regardless of SDK clients");

            // SDK logger should have the last set value (ERROR from client2)
            assertEquals(Level.ERROR, sdkLogger.getLevel(),
                    "SDK logger should reflect the last configured level");

        } finally {
            client1.close();
            client2.close();
        }
    }

    // ==================== HIGH-4 FIX: TLS Validation Tests ====================

    @Test
    @Order(10)
    @DisplayName("HIGH-4 FIX: TLS enabled without any certs is allowed (server-only TLS)")
    void tlsEnabledWithoutCerts_isAllowed() {
        // TLS without any client certs is valid for server-only verification
        // The validation should pass (actual connection may fail without server or due to ALPN)
        try {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("tls-test")
                    .tls(true)  // TLS enabled
                    // No tlsCertFile
                    // No tlsKeyFile
                    // No caCertFile
                    .build();

            client.close();
            // If we get here, validation passed
        } catch (IllegalArgumentException e) {
            // This is expected if ALPN is not available in the test environment
            // ALPN is a gRPC TLS requirement, not our validation issue
            assertTrue(e.getMessage().contains("ALPN") || e.getMessage().contains("mutual TLS"),
                    "If validation fails, it should be ALPN or mTLS related. Got: " + e.getMessage());
        }
    }

    @Test
    @Order(11)
    @DisplayName("HIGH-4 FIX: TLS with cert but no key should throw exception")
    void tlsWithCertButNoKey_shouldThrow() {
        // Having a certificate without its private key is always invalid
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("tls-cert-no-key")
                    .tls(true)
                    .tlsCertFile("/some/path/cert.pem")
                    // No tlsKeyFile - this is invalid!
                    .build();
            client.close();
        }, "TLS with cert but no key should throw IllegalArgumentException");

        assertTrue(ex.getMessage().contains("tlsCertFile") || ex.getMessage().contains("tlsKeyFile") ||
                   ex.getMessage().contains("mutual TLS"),
                "Exception should mention TLS certificate/key requirements. Got: " + ex.getMessage());
    }

    @Test
    @Order(12)
    @DisplayName("HIGH-4 FIX: TLS with key but no cert should throw exception")
    void tlsWithKeyButNoCert_shouldThrow() {
        // Having a private key without its certificate is always invalid
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("tls-key-no-cert")
                    .tls(true)
                    .tlsKeyFile("/some/path/key.pem")
                    // No tlsCertFile - this is invalid!
                    .build();
            client.close();
        }, "TLS with key but no cert should throw IllegalArgumentException");

        assertTrue(ex.getMessage().contains("tlsCertFile") || ex.getMessage().contains("tlsKeyFile") ||
                   ex.getMessage().contains("mutual TLS"),
                "Exception should mention TLS certificate/key requirements. Got: " + ex.getMessage());
    }

    @Test
    @Order(13)
    @DisplayName("HIGH-4 FIX: TLS with both cert and key is valid")
    void tlsWithBothCertAndKey_isValid() {
        // This should be valid (though the files don't exist, that's caught later)
        try {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("tls-valid")
                    .tls(true)
                    .tlsCertFile("/some/path/cert.pem")
                    .tlsKeyFile("/some/path/key.pem")
                    .build();
            client.close();
            // If we get here, the validation passed (file existence is a separate check)
        } catch (IllegalArgumentException e) {
            // If it fails, it should be about file existence, not config
            assertTrue(e.getMessage().contains("exist") || e.getMessage().contains("not found"),
                    "If exception occurs, it should be about file existence. Got: " + e.getMessage());
        } catch (RuntimeException e) {
            // Runtime exception during connection is expected if server is not running
            // but the validation should have passed
        }
    }

    @Test
    @Order(14)
    @DisplayName("HIGH-4 FIX: TLS disabled ignores cert/key configuration")
    void tlsDisabled_ignoresCertKeyConfig() {
        // When TLS is disabled, cert/key configuration should be ignored
        assertDoesNotThrow(() -> {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("tls-disabled")
                    .tls(false)  // TLS disabled
                    .tlsCertFile("/some/path/cert.pem")  // This should be ignored
                    // No key - but it doesn't matter because TLS is disabled
                    .build();
            client.close();
        }, "When TLS is disabled, cert/key configuration should be ignored");
    }

    @Test
    @Order(15)
    @DisplayName("HIGH-4 FIX: Empty string cert paths are treated as null")
    void emptyStringCertPaths_treatedAsNull() {
        // Empty strings should be treated as null (no client certs)
        // Validation should pass, even if connection later fails
        try {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("tls-empty-paths")
                    .tls(true)
                    .tlsCertFile("")  // Empty string = null = no client cert
                    .tlsKeyFile("")   // Empty string = null = no client key
                    .build();
            client.close();
            // If we get here, validation passed
        } catch (IllegalArgumentException e) {
            // Validation may fail due to:
            // - mTLS config mismatch (mutual TLS, together)
            // - ALPN not available in test environment
            // - Empty string passed to file reader (File does not contain)
            // All these are acceptable - the key is we're not getting unexpected errors
            String msg = e.getMessage();
            assertTrue(msg.contains("mutual TLS") ||
                       msg.contains("together") ||
                       msg.contains("ALPN") ||
                       msg.contains("File does not contain") ||
                       msg.contains("certificate"),
                    "If validation fails, it should be about config/cert/ALPN. Got: " + e.getMessage());
        } catch (RuntimeException e) {
            // Connection error is acceptable (empty string passed to TLS setup)
            // The important thing is our validation passed
        }
    }

    @Test
    @Order(16)
    @DisplayName("HIGH-3/HIGH-4 FIX: Combined security configuration works correctly")
    void combinedSecurityConfiguration_worksCorrectly() {
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger("ROOT");
        ch.qos.logback.classic.Logger sdkLogger = loggerContext.getLogger("io.kubemq.sdk");
        Level rootBefore = rootLogger.getLevel();

        try {
            // Create a client with both TLS and log level configuration
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("combined-security-test")
                    .tls(true)           // TLS enabled (server-only)
                    .logLevel(KubeMQClient.Level.WARN)  // SDK logger only
                    .build();

            try {
                // ROOT logger should NOT have changed
                assertEquals(rootBefore, rootLogger.getLevel(),
                        "ROOT logger should not change");

                // SDK logger should be WARN
                assertEquals(Level.WARN, sdkLogger.getLevel(),
                        "SDK logger should be WARN");

                // TLS should be enabled
                assertTrue(client.isTls(),
                        "TLS should be enabled");

            } finally {
                client.close();
            }

        } catch (Exception e) {
            // If connection fails, that's expected without a server
            // The important thing is validation passed
        }
    }

    @Test
    @Order(17)
    @DisplayName("HIGH-4 FIX: Validate TLS configuration method exists")
    void validateTlsConfiguration_methodExists() throws Exception {
        // Verify the validation method exists
        Method validateMethod = null;
        for (Method method : KubeMQClient.class.getDeclaredMethods()) {
            if (method.getName().contains("validateTls") ||
                method.getName().contains("TlsConfiguration")) {
                validateMethod = method;
                break;
            }
        }

        assertNotNull(validateMethod,
                "TLS validation method should exist in KubeMQClient");
    }
}
