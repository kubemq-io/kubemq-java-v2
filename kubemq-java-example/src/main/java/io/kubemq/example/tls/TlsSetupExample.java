package io.kubemq.example.tls;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueuesClient;

/**
 * TLS Setup Example
 *
 * Demonstrates establishing a secure TLS connection to KubeMQ server.
 */
public class TlsSetupExample {

    private static final String ADDRESS = "localhost:50001";
    private static final String CLIENT_ID = "java-tls-tls-setup-client";
    private static final String CA_CERT_FILE = "/path/to/ca.pem";

    public void connectWithServerTLS() {
        System.out.println("=== Server-Side TLS Connection ===\n");

        try {
            QueuesClient client = QueuesClient.builder()
                    .address(ADDRESS)
                    .clientId(CLIENT_ID)
                    .tls(true)
                    .caCertFile(CA_CERT_FILE)
                    .logLevel(KubeMQClient.Level.INFO)
                    .build();

            ServerInfo serverInfo = client.ping();
            System.out.println("Successfully connected with server-side TLS!");
            System.out.println("Server Info: " + serverInfo);
            client.close();

        } catch (Exception e) {
            System.err.println("TLS connection failed: " + e.getMessage());
        }
    }

    public void connectWithTLSValidation() {
        System.out.println("=== TLS Validation ===\n");

        try {
            System.out.println("Testing incomplete TLS configuration...");
            QueuesClient client = QueuesClient.builder()
                    .address(ADDRESS)
                    .clientId(CLIENT_ID)
                    .tls(true)
                    .tlsCertFile("/path/to/client.pem")
                    .build();
            client.close();
        } catch (IllegalArgumentException e) {
            System.out.println("Caught validation error: " + e.getMessage());
            System.out.println("Cert and key must be provided together.\n");
        }
    }

    public static void main(String[] args) {
        TlsSetupExample example = new TlsSetupExample();
        example.connectWithTLSValidation();
        // Uncomment when TLS server is available:
        // example.connectWithServerTLS();
        System.out.println("TLS setup examples completed.");
    }
}
