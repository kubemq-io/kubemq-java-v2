package io.kubemq.example.config;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.cq.CQClient;

/**
 * Client Configuration Example
 *
 * This example demonstrates all available configuration options for KubeMQ clients.
 * Understanding these options helps optimize performance, reliability, and debugging.
 *
 * Configuration Categories:
 *
 * 1. CONNECTION SETTINGS
 *    - address: KubeMQ server address (host:port)
 *    - clientId: Unique identifier for this client instance
 *
 * 2. SECURITY SETTINGS
 *    - authToken: JWT or API key for authentication
 *    - tls: Enable/disable TLS encryption
 *    - tlsCertFile, tlsKeyFile, caCertFile: Certificate files for TLS/mTLS
 *
 * 3. CONNECTION MANAGEMENT
 *    - keepAlive: Enable gRPC keepalive mechanism
 *    - pingIntervalInSeconds: Interval between keepalive pings
 *    - pingTimeoutInSeconds: Timeout for keepalive ping response
 *    - reconnectIntervalSeconds: Base interval for reconnection attempts
 *
 * 4. MESSAGE SIZE
 *    - maxReceiveSize: Maximum message size in bytes (default: 100MB)
 *
 * 5. LOGGING
 *    - logLevel: SDK logging verbosity (TRACE, DEBUG, INFO, WARN, ERROR, OFF)
 *
 * @see io.kubemq.sdk.client.KubeMQClient
 */
public class ClientConfigurationExample {

    private static final String ADDRESS = "localhost:50000";

    /**
     * Demonstrates basic configuration with essential settings.
     * This is the minimum configuration needed to connect to KubeMQ.
     */
    public void basicConfiguration() {
        System.out.println("=== Basic Configuration ===\n");

        try (QueuesClient client = QueuesClient.builder()
                .address(ADDRESS)          // Required: server address
                .clientId("basic-client")  // Required: unique client ID
                .build()) {

            ServerInfo info = client.ping();
            System.out.println("Connected with basic config:");
            System.out.println("  Server: " + info.getHost());
            System.out.println("  Version: " + info.getVersion());
            System.out.println("  Uptime: " + info.getServerUpTimeSeconds() + " seconds\n");

        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    /**
     * Demonstrates keep-alive configuration for long-running connections.
     * Keep-alive prevents idle connections from being dropped by firewalls
     * or load balancers.
     */
    public void keepAliveConfiguration() {
        System.out.println("=== Keep-Alive Configuration ===\n");

        try (QueuesClient client = QueuesClient.builder()
                .address(ADDRESS)
                .clientId("keepalive-client")

                // Enable keep-alive - sends periodic pings to keep connection alive
                .keepAlive(true)

                // Send a ping every 30 seconds when connection is idle
                .pingIntervalInSeconds(30)

                // Wait up to 10 seconds for ping response before considering dead
                .pingTimeoutInSeconds(10)

                .build()) {

            ServerInfo info = client.ping();
            System.out.println("Connected with keep-alive enabled:");
            System.out.println("  Ping Interval: 30 seconds");
            System.out.println("  Ping Timeout: 10 seconds");
            System.out.println("  Server: " + info.getHost() + "\n");

            System.out.println("Keep-alive is useful when:");
            System.out.println("  - Connection goes through NAT/firewall");
            System.out.println("  - Using cloud load balancers with idle timeouts");
            System.out.println("  - Application has periods of inactivity\n");

        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    /**
     * Demonstrates reconnection configuration for fault tolerance.
     * The SDK automatically attempts to reconnect when connection is lost.
     */
    public void reconnectionConfiguration() {
        System.out.println("=== Reconnection Configuration ===\n");

        try (PubSubClient client = PubSubClient.builder()
                .address(ADDRESS)
                .clientId("reconnect-client")

                // Base interval for reconnection attempts (uses exponential backoff)
                // Actual delay: base * 2^(attempt-1), capped at 60 seconds
                .reconnectIntervalSeconds(1)  // Start with 1 second, then 2, 4, 8...

                .build()) {

            ServerInfo info = client.ping();
            System.out.println("Connected with reconnection configured:");
            System.out.println("  Base Reconnect Interval: 1 second");
            System.out.println("  Backoff: Exponential (1s, 2s, 4s, 8s, ... up to 60s)");
            System.out.println("  Max Attempts: 10 per subscription");
            System.out.println("  Server: " + info.getHost() + "\n");

            System.out.println("Reconnection behavior:");
            System.out.println("  - Automatic reconnection on connection loss");
            System.out.println("  - Exponential backoff prevents server overload");
            System.out.println("  - Error callback notified on each failure");
            System.out.println("  - Attempt counter resets on successful reconnect\n");

        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    /**
     * Demonstrates message size configuration for large payloads.
     * Default is 100MB, but can be adjusted based on your needs.
     */
    public void messageSizeConfiguration() {
        System.out.println("=== Message Size Configuration ===\n");

        // Example: Allow receiving messages up to 200MB
        int maxSize = 200 * 1024 * 1024; // 200 MB in bytes

        try (QueuesClient client = QueuesClient.builder()
                .address(ADDRESS)
                .clientId("large-message-client")

                // Maximum size of messages that can be received
                .maxReceiveSize(maxSize)

                .build()) {

            ServerInfo info = client.ping();
            System.out.println("Connected with custom message size:");
            System.out.println("  Max Receive Size: " + (maxSize / 1024 / 1024) + " MB");
            System.out.println("  Server: " + info.getHost() + "\n");

            System.out.println("Message size considerations:");
            System.out.println("  - Default is 100MB (104857600 bytes)");
            System.out.println("  - Increase for large file transfers");
            System.out.println("  - Server may have its own limits");
            System.out.println("  - Larger sizes consume more memory\n");

        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    /**
     * Demonstrates logging configuration for debugging and monitoring.
     * Different log levels provide varying amounts of detail.
     */
    public void loggingConfiguration() {
        System.out.println("=== Logging Configuration ===\n");

        System.out.println("Available log levels:");
        System.out.println("  TRACE - Most detailed, shows all internal operations");
        System.out.println("  DEBUG - Detailed information for debugging");
        System.out.println("  INFO  - General operational information (default)");
        System.out.println("  WARN  - Warning messages for potential issues");
        System.out.println("  ERROR - Only error messages");
        System.out.println("  OFF   - Disable all SDK logging\n");

        // Example with DEBUG level for troubleshooting
        System.out.println("Creating client with DEBUG logging...");
        try (QueuesClient debugClient = QueuesClient.builder()
                .address(ADDRESS)
                .clientId("debug-client")
                .logLevel(KubeMQClient.Level.DEBUG)
                .build()) {

            debugClient.ping();
            System.out.println("(Check console for DEBUG-level log messages)\n");

        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
        }

        // Example with minimal logging for production
        System.out.println("Creating client with WARN logging (production)...");
        try (QueuesClient prodClient = QueuesClient.builder()
                .address(ADDRESS)
                .clientId("prod-client")
                .logLevel(KubeMQClient.Level.WARN)
                .build()) {

            prodClient.ping();
            System.out.println("(Only warnings and errors will be logged)\n");

        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    /**
     * Demonstrates a production-ready configuration combining multiple settings.
     * This represents a well-configured client for real-world use.
     */
    public void productionConfiguration() {
        System.out.println("=== Production-Ready Configuration ===\n");

        try (CQClient client = CQClient.builder()
                // Connection
                .address(ADDRESS)
                .clientId("prod-service-" + System.currentTimeMillis())

                // Security (enable in production)
                // .authToken(System.getenv("KUBEMQ_TOKEN"))
                // .tls(true)
                // .caCertFile("/etc/kubemq/ca.pem")

                // Reliability
                .keepAlive(true)
                .pingIntervalInSeconds(30)
                .pingTimeoutInSeconds(10)
                .reconnectIntervalSeconds(1)

                // Performance
                .maxReceiveSize(100 * 1024 * 1024)  // 100MB

                // Logging (less verbose in production)
                .logLevel(KubeMQClient.Level.WARN)

                .build()) {

            ServerInfo info = client.ping();
            System.out.println("Production configuration summary:");
            System.out.println("┌─────────────────────────────────────────────┐");
            System.out.println("│ Setting                 │ Value             │");
            System.out.println("├─────────────────────────────────────────────┤");
            System.out.println("│ Keep-Alive              │ Enabled           │");
            System.out.println("│ Ping Interval           │ 30 seconds        │");
            System.out.println("│ Ping Timeout            │ 10 seconds        │");
            System.out.println("│ Reconnect Interval      │ 1 second (base)   │");
            System.out.println("│ Max Message Size        │ 100 MB            │");
            System.out.println("│ Log Level               │ WARN              │");
            System.out.println("│ TLS                     │ Recommended       │");
            System.out.println("│ Authentication          │ Recommended       │");
            System.out.println("└─────────────────────────────────────────────┘");
            System.out.println("\nConnected to: " + info.getHost() + " v" + info.getVersion() + "\n");

        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    /**
     * Demonstrates configuration validation and error handling.
     * Shows common configuration mistakes and how they're handled.
     */
    public void configurationValidation() {
        System.out.println("=== Configuration Validation ===\n");

        // Test 1: Missing required address
        System.out.println("Test 1: Missing address...");
        try {
            QueuesClient.builder()
                    .clientId("test")
                    // Missing: .address(ADDRESS)
                    .build();
        } catch (Exception e) {
            System.out.println("  Caught: " + e.getClass().getSimpleName());
            System.out.println("  Message: " + e.getMessage() + "\n");
        }

        // Test 2: Missing required clientId
        System.out.println("Test 2: Missing clientId...");
        try {
            QueuesClient.builder()
                    .address(ADDRESS)
                    // Missing: .clientId("test")
                    .build();
        } catch (Exception e) {
            System.out.println("  Caught: " + e.getClass().getSimpleName());
            System.out.println("  Message: " + e.getMessage() + "\n");
        }

        System.out.println("Configuration validation ensures:");
        System.out.println("  - Required parameters are provided");
        System.out.println("  - TLS settings are consistent");
        System.out.println("  - Certificate files exist (when specified)");
        System.out.println("  - Numeric values are within valid ranges\n");
    }

    /**
     * Main method demonstrating all configuration options.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           KubeMQ Client Configuration Examples               ║");
        System.out.println("║                                                              ║");
        System.out.println("║  Demonstrates all available configuration options            ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        ClientConfigurationExample example = new ClientConfigurationExample();

        example.basicConfiguration();
        example.keepAliveConfiguration();
        example.reconnectionConfiguration();
        example.messageSizeConfiguration();
        example.loggingConfiguration();
        example.productionConfiguration();
        example.configurationValidation();

        System.out.println("Configuration examples completed.");
    }
}
