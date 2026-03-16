package io.kubemq.example.tls;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueuesClient;

/**
 * Mutual TLS Setup Example
 *
 * Demonstrates establishing a mutual TLS (mTLS) connection to KubeMQ server
 * where both client and server authenticate each other with certificates.
 */
public class MtlsSetupExample {

    private static final String ADDRESS = "localhost:50001";
    private static final String CLIENT_ID = "java-tls-mtls-setup-client";
    private static final String CA_CERT_FILE = "/path/to/ca.pem";
    private static final String CLIENT_CERT_FILE = "/path/to/client.pem";
    private static final String CLIENT_KEY_FILE = "/path/to/client.key";

    public void connectWithMutualTLS() {
        System.out.println("=== Mutual TLS (mTLS) Connection ===\n");

        // Create a client with mTLS (client cert + key + CA cert)
        try (QueuesClient client = QueuesClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID)
                .tls(true)
                .caCertFile(CA_CERT_FILE)
                .tlsCertFile(CLIENT_CERT_FILE)
                .tlsKeyFile(CLIENT_KEY_FILE)
                .logLevel(KubeMQClient.Level.INFO)
                .build()) {

            // Verify mTLS connection
            ServerInfo serverInfo = client.ping();
            System.out.println("Connected with mTLS. Server: " + serverInfo);

        } catch (Exception e) {
            System.err.println("mTLS connection failed: " + e.getMessage());
        }
    }

    public void connectWithMutualTLSFromPemBytes() {
        System.out.println("=== Mutual TLS from PEM bytes ===\n");

        // Load certs from PEM bytes instead of files
        byte[] caCertPem = "-----BEGIN CERTIFICATE-----\n... CA cert ...\n-----END CERTIFICATE-----".getBytes();
        byte[] clientCertPem = "-----BEGIN CERTIFICATE-----\n... client cert ...\n-----END CERTIFICATE-----".getBytes();
        byte[] clientKeyPem = "-----BEGIN PRIVATE KEY-----\n... client key ...\n-----END PRIVATE KEY-----".getBytes();

        try (QueuesClient client = QueuesClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID + "-pem")
                .tls(true)
                .caCertPem(caCertPem)
                .tlsCertPem(clientCertPem)
                .tlsKeyPem(clientKeyPem)
                .build()) {

            ServerInfo serverInfo = client.ping();
            System.out.println("Connected with mTLS (PEM bytes). Server: " + serverInfo);

        } catch (Exception e) {
            System.err.println("mTLS PEM connection failed: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        MtlsSetupExample example = new MtlsSetupExample();
        example.connectWithMutualTLS();
        example.connectWithMutualTLSFromPemBytes();
    }
}
