package io.kubemq.example.connection;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.cq.CQClient;

/**
 * Connect Example
 *
 * Demonstrates all available configuration options for KubeMQ clients.
 * Shows basic, keep-alive, reconnection, message size, logging,
 * and production-ready configurations.
 */
public class ConnectExample {

    // TODO: Replace with your KubeMQ server address
    private static final String ADDRESS = "localhost:50000";

    public void basicConfiguration() {
        System.out.println("=== Basic Configuration ===\n");

        try (QueuesClient client = QueuesClient.builder()
                .address(ADDRESS)
                .clientId("java-connection-connect-client")
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

    public void keepAliveConfiguration() {
        System.out.println("=== Keep-Alive Configuration ===\n");

        try (QueuesClient client = QueuesClient.builder()
                .address(ADDRESS)
                .clientId("java-connection-connect-keepalive-client")
                .keepAlive(true)
                .pingIntervalInSeconds(30)
                .pingTimeoutInSeconds(10)
                .build()) {

            ServerInfo info = client.ping();
            System.out.println("Connected with keep-alive enabled:");
            System.out.println("  Ping Interval: 30 seconds");
            System.out.println("  Ping Timeout: 10 seconds");
            System.out.println("  Server: " + info.getHost() + "\n");

        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    public void reconnectionConfiguration() {
        System.out.println("=== Reconnection Configuration ===\n");

        try (PubSubClient client = PubSubClient.builder()
                .address(ADDRESS)
                .clientId("java-connection-connect-reconnect-client")
                .reconnectIntervalSeconds(1)
                .build()) {

            ServerInfo info = client.ping();
            System.out.println("Connected with reconnection configured:");
            System.out.println("  Base Reconnect Interval: 1 second");
            System.out.println("  Server: " + info.getHost() + "\n");

        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    public void messageSizeConfiguration() {
        System.out.println("=== Message Size Configuration ===\n");

        int maxSize = 200 * 1024 * 1024;

        try (QueuesClient client = QueuesClient.builder()
                .address(ADDRESS)
                .clientId("java-connection-connect-largemsg-client")
                .maxReceiveSize(maxSize)
                .build()) {

            ServerInfo info = client.ping();
            System.out.println("Connected with custom message size:");
            System.out.println("  Max Receive Size: " + (maxSize / 1024 / 1024) + " MB");
            System.out.println("  Server: " + info.getHost() + "\n");

        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    public void loggingConfiguration() {
        System.out.println("=== Logging Configuration ===\n");

        try (QueuesClient debugClient = QueuesClient.builder()
                .address(ADDRESS)
                .clientId("java-connection-connect-debug-client")
                .logLevel(KubeMQClient.Level.DEBUG)
                .build()) {

            debugClient.ping();
            System.out.println("DEBUG logging client connected.\n");

        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    public void productionConfiguration() {
        System.out.println("=== Production-Ready Configuration ===\n");

        try (CQClient client = CQClient.builder()
                .address(ADDRESS)
                .clientId("java-connection-connect-prod-client")
                .keepAlive(true)
                .pingIntervalInSeconds(30)
                .pingTimeoutInSeconds(10)
                .reconnectIntervalSeconds(1)
                .maxReceiveSize(100 * 1024 * 1024)
                .logLevel(KubeMQClient.Level.WARN)
                .build()) {

            ServerInfo info = client.ping();
            System.out.println("Production configuration active.");
            System.out.println("Connected to: " + info.getHost() + " v" + info.getVersion() + "\n");

        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    public void configurationValidation() {
        System.out.println("=== Configuration Validation ===\n");

        System.out.println("Test 1: Missing address...");
        try {
            QueuesClient.builder()
                    .clientId("java-connection-connect-client")
                    .build();
        } catch (Exception e) {
            System.out.println("  Caught: " + e.getClass().getSimpleName());
            System.out.println("  Message: " + e.getMessage() + "\n");
        }

        System.out.println("Test 2: Missing clientId...");
        try {
            QueuesClient.builder()
                    .address(ADDRESS)
                    .build();
        } catch (Exception e) {
            System.out.println("  Caught: " + e.getClass().getSimpleName());
            System.out.println("  Message: " + e.getMessage() + "\n");
        }
    }

    public static void main(String[] args) {
        ConnectExample example = new ConnectExample();
        example.basicConfiguration();
        example.keepAliveConfiguration();
        example.reconnectionConfiguration();
        example.messageSizeConfiguration();
        example.loggingConfiguration();
        example.productionConfiguration();
        example.configurationValidation();
        System.out.println("Connect examples completed.");
    }
}
