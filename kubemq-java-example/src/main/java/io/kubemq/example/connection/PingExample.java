package io.kubemq.example.connection;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueuesClient;

/**
 * Ping Example
 *
 * Demonstrates verifying connectivity to a KubeMQ server using ping.
 */
public class PingExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-connection-ping-client";

    public void pingServer() {
        System.out.println("=== Ping KubeMQ Server ===\n");

        // Create a client and verify connectivity with ping
        try (QueuesClient client = QueuesClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID)
                .build()) {

            ServerInfo serverInfo = client.ping();
            System.out.println("Ping successful!");
            System.out.println("  Host: " + serverInfo.getHost());
            System.out.println("  Version: " + serverInfo.getVersion());
            System.out.println("  Server Start Time: " + serverInfo.getServerStartTime());
            System.out.println("  Server Uptime (seconds): " + serverInfo.getServerUpTimeSeconds());

        } catch (Exception e) {
            System.err.println("Ping failed: " + e.getMessage());
        }
    }

    public void pingWithValidateOnBuild() {
        System.out.println("\n=== Ping via validateOnBuild ===\n");

        // Create a client that validates connectivity on build
        try (QueuesClient client = QueuesClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID + "-validate")
                .validateOnBuild(true)
                .build()) {

            System.out.println("Client created and connectivity validated on build.");

        } catch (Exception e) {
            System.err.println("Connectivity validation failed: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        PingExample example = new PingExample();
        example.pingServer();
        example.pingWithValidateOnBuild();
    }
}
