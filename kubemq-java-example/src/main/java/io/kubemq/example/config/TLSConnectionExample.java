package io.kubemq.example.config;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueuesClient;

/**
 * TLS Connection Example
 *
 * This example demonstrates how to establish a secure TLS/mTLS connection to KubeMQ server.
 *
 * TLS Configuration Options:
 * 1. Server-side TLS only (verify server certificate)
 *    - Set tls=true
 *    - Provide caCertFile for server certificate verification
 *
 * 2. Mutual TLS (mTLS) - both client and server authenticate
 *    - Set tls=true
 *    - Provide caCertFile for server certificate verification
 *    - Provide tlsCertFile (client certificate) and tlsKeyFile (client private key)
 *
 * Certificate Requirements:
 * - Certificates must be in PEM format
 * - The CA certificate must be the one that signed the server's certificate
 * - For mTLS, client cert must be signed by a CA trusted by the server
 *
 * Prerequisites:
 * - KubeMQ server configured with TLS enabled
 * - Valid certificate files accessible from the application
 *
 * @see <a href="https://docs.kubemq.io/getting-started/security">KubeMQ Security Documentation</a>
 */
public class TLSConnectionExample {

    // Server address - use the TLS-enabled endpoint
    private static final String ADDRESS = "localhost:50001"; // TLS port
    private static final String CLIENT_ID = "tls-client";

    // Certificate paths - adjust these to your certificate locations
    private static final String CA_CERT_FILE = "/path/to/ca.pem";
    private static final String CLIENT_CERT_FILE = "/path/to/client.pem";
    private static final String CLIENT_KEY_FILE = "/path/to/client.key";

    /**
     * Demonstrates server-side TLS connection.
     * The client verifies the server's certificate but doesn't present its own.
     */
    public void connectWithServerTLS() {
        System.out.println("=== Server-Side TLS Connection ===\n");

        try {
            // Create client with TLS enabled and CA certificate for server verification
            QueuesClient client = QueuesClient.builder()
                    .address(ADDRESS)
                    .clientId(CLIENT_ID + "-server-tls")
                    .tls(true)                          // Enable TLS
                    .caCertFile(CA_CERT_FILE)           // CA cert to verify server
                    .logLevel(KubeMQClient.Level.INFO)
                    .build();

            // Test the connection
            ServerInfo serverInfo = client.ping();
            System.out.println("Successfully connected with server-side TLS!");
            System.out.println("Server Info: " + serverInfo);

            // Always close the client when done
            client.close();
            System.out.println("Connection closed.\n");

        } catch (Exception e) {
            System.err.println("Failed to connect with server-side TLS: " + e.getMessage());
            System.err.println("Make sure:");
            System.err.println("  1. KubeMQ server is running with TLS enabled");
            System.err.println("  2. CA certificate path is correct");
            System.err.println("  3. CA certificate matches the server's certificate");
        }
    }

    /**
     * Demonstrates mutual TLS (mTLS) connection.
     * Both client and server authenticate each other with certificates.
     */
    public void connectWithMutualTLS() {
        System.out.println("=== Mutual TLS (mTLS) Connection ===\n");

        try {
            // Create client with mutual TLS - both client and server authenticate
            QueuesClient client = QueuesClient.builder()
                    .address(ADDRESS)
                    .clientId(CLIENT_ID + "-mtls")
                    .tls(true)                          // Enable TLS
                    .caCertFile(CA_CERT_FILE)           // CA cert to verify server
                    .tlsCertFile(CLIENT_CERT_FILE)      // Client certificate
                    .tlsKeyFile(CLIENT_KEY_FILE)        // Client private key
                    .logLevel(KubeMQClient.Level.INFO)
                    .build();

            // Test the connection
            ServerInfo serverInfo = client.ping();
            System.out.println("Successfully connected with mutual TLS!");
            System.out.println("Server Info: " + serverInfo);

            // Always close the client when done
            client.close();
            System.out.println("Connection closed.\n");

        } catch (Exception e) {
            System.err.println("Failed to connect with mutual TLS: " + e.getMessage());
            System.err.println("Make sure:");
            System.err.println("  1. KubeMQ server is running with mTLS enabled");
            System.err.println("  2. All certificate paths are correct");
            System.err.println("  3. Client certificate is signed by a CA trusted by the server");
            System.err.println("  4. Private key matches the client certificate");
        }
    }

    /**
     * Demonstrates TLS connection with validation error handling.
     * Shows how to diagnose common TLS configuration issues.
     */
    public void connectWithTLSValidation() {
        System.out.println("=== TLS Connection with Validation ===\n");

        // Example 1: Missing key file when cert is provided (should fail validation)
        try {
            System.out.println("Testing incomplete mTLS configuration...");
            QueuesClient client = QueuesClient.builder()
                    .address(ADDRESS)
                    .clientId(CLIENT_ID)
                    .tls(true)
                    .tlsCertFile(CLIENT_CERT_FILE)  // Cert without key - should fail
                    // Missing: .tlsKeyFile(CLIENT_KEY_FILE)
                    .build();
            client.close();
        } catch (IllegalArgumentException e) {
            System.out.println("Caught expected validation error: " + e.getMessage());
            System.out.println("This is correct behavior - cert and key must be provided together.\n");
        }

        // Example 2: Non-existent certificate file
        try {
            System.out.println("Testing with non-existent certificate file...");
            QueuesClient client = QueuesClient.builder()
                    .address(ADDRESS)
                    .clientId(CLIENT_ID)
                    .tls(true)
                    .caCertFile("/nonexistent/path/ca.pem")
                    .build();
            client.close();
        } catch (IllegalArgumentException e) {
            System.out.println("Caught expected validation error: " + e.getMessage());
            System.out.println("This is correct behavior - certificate files must exist.\n");
        }
    }

    /**
     * Main method demonstrating all TLS connection options.
     *
     * Note: These examples require a TLS-enabled KubeMQ server and valid certificates.
     * For local development without TLS, use the standard non-TLS examples instead.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           KubeMQ TLS Connection Examples                     ║");
        System.out.println("║                                                              ║");
        System.out.println("║  This example requires:                                      ║");
        System.out.println("║  - TLS-enabled KubeMQ server                                 ║");
        System.out.println("║  - Valid certificate files                                   ║");
        System.out.println("║                                                              ║");
        System.out.println("║  Update the certificate paths before running!                ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        TLSConnectionExample example = new TLSConnectionExample();

        // Demonstrate validation (this will work without a TLS server)
        example.connectWithTLSValidation();

        // Uncomment these when you have a TLS-enabled server:
        // example.connectWithServerTLS();
        // example.connectWithMutualTLS();

        System.out.println("TLS connection examples completed.");
    }
}
