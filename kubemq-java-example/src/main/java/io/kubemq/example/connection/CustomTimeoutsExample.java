package io.kubemq.example.connection;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.cq.CQClient;

/**
 * Custom Timeouts Example
 *
 * Demonstrates configuring custom timeouts for KubeMQ clients including
 * connection timeouts, keep-alive intervals, and reconnection intervals.
 */
public class CustomTimeoutsExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-connection-custom-timeouts-client";

    public void connectionTimeoutExample() {
        System.out.println("=== Connection Timeout Configuration ===\n");

        // Create a client with custom connection timeout
        try (QueuesClient client = QueuesClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID + "-conn-timeout")
                .connectionTimeoutSeconds(10)
                .build()) {

            ServerInfo info = client.ping();
            System.out.println("Connected with 10s connection timeout.");
            System.out.println("Server: " + info.getHost() + "\n");

        } catch (Exception e) {
            System.err.println("Connection timeout: " + e.getMessage());
        }
    }

    public void keepAliveTimeoutsExample() {
        System.out.println("=== Keep-Alive Timeout Configuration ===\n");

        // Create a client with custom keep-alive intervals
        try (PubSubClient client = PubSubClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID + "-keepalive")
                .keepAlive(true)
                .pingIntervalInSeconds(15)
                .pingTimeoutInSeconds(5)
                .build()) {

            ServerInfo info = client.ping();
            System.out.println("Connected with custom keep-alive:");
            System.out.println("  Ping Interval: 15 seconds");
            System.out.println("  Ping Timeout: 5 seconds");
            System.out.println("  Server: " + info.getHost() + "\n");

        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    public void reconnectionTimeoutExample() {
        System.out.println("=== Reconnection Interval Configuration ===\n");

        // Create a client with custom reconnection interval
        try (CQClient client = CQClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID + "-reconnect")
                .reconnectIntervalSeconds(2)
                .build()) {

            ServerInfo info = client.ping();
            System.out.println("Connected with 2s base reconnect interval.");
            System.out.println("  Backoff: 2s, 4s, 8s, 16s, ... up to 60s");
            System.out.println("  Server: " + info.getHost() + "\n");

        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    public void shutdownTimeoutExample() {
        System.out.println("=== Shutdown Timeout Configuration ===\n");

        // Create a client with custom shutdown timeout
        try (QueuesClient client = QueuesClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID + "-shutdown")
                .shutdownTimeoutSeconds(5)
                .build()) {

            ServerInfo info = client.ping();
            System.out.println("Connected with 5s shutdown timeout.");
            System.out.println("Server: " + info.getHost() + "\n");

        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        CustomTimeoutsExample example = new CustomTimeoutsExample();
        example.connectionTimeoutExample();
        example.keepAliveTimeoutsExample();
        example.reconnectionTimeoutExample();
        example.shutdownTimeoutExample();
        System.out.println("Custom timeouts examples completed.");
    }
}
