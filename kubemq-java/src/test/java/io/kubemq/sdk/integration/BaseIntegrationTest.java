package io.kubemq.sdk.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Base class for integration tests.
 *
 * Configuration options (via environment variables or system properties):
 * - KUBEMQ_ADDRESS: KubeMQ server address (default: localhost:50000)
 * - KUBEMQ_AUTH_TOKEN: Optional authentication token
 *
 * For CI/CD with Testcontainers, override getKubeMQAddress() in subclass.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {

    protected static final String DEFAULT_ADDRESS = "localhost:50000";
    protected static final int DEFAULT_TIMEOUT_SECONDS = 10;

    protected String kubemqAddress;
    protected String authToken;

    @BeforeAll
    void setupBase() {
        kubemqAddress = getConfigValue("KUBEMQ_ADDRESS", DEFAULT_ADDRESS);
        authToken = getConfigValue("KUBEMQ_AUTH_TOKEN", null);

        // Verify connection before running tests
        verifyKubeMQConnection();
    }

    /**
     * Gets a configuration value from system property or environment variable.
     */
    protected String getConfigValue(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null) {
            value = System.getenv(key);
        }
        return value != null ? value : defaultValue;
    }

    /**
     * Generates a unique channel name for test isolation.
     */
    protected String uniqueChannel(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Generates a unique client ID for test isolation.
     */
    protected String uniqueClientId(String prefix) {
        return prefix + "-client-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Verifies KubeMQ server is reachable before running tests.
     * Override in subclass for custom verification.
     */
    protected void verifyKubeMQConnection() {
        // Subclasses will verify by pinging
    }

    /**
     * Sleeps for the specified duration.
     */
    protected void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sleeps for the specified duration with unit.
     */
    protected void sleep(long duration, TimeUnit unit) {
        sleep(unit.toMillis(duration));
    }
}
