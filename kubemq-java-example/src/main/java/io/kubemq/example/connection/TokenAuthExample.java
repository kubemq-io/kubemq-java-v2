package io.kubemq.example.connection;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueuesClient;

/**
 * Token Authentication Example
 *
 * Demonstrates connecting to a KubeMQ server using JWT tokens or API keys.
 */
public class TokenAuthExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-connection-token-auth-client";
    private static final String AUTH_TOKEN = "your-jwt-token-or-api-key";

    public void connectWithAuthToken() {
        System.out.println("=== Connecting with Authentication Token ===\n");

        try {
            QueuesClient client = QueuesClient.builder()
                    .address(ADDRESS)
                    .clientId(CLIENT_ID)
                    .authToken(AUTH_TOKEN)
                    .logLevel(KubeMQClient.Level.INFO)
                    .build();

            ServerInfo serverInfo = client.ping();
            System.out.println("Successfully authenticated and connected!");
            System.out.println("Server Info: " + serverInfo);

            client.close();
            System.out.println("Connection closed.\n");

        } catch (Exception e) {
            System.err.println("Authentication failed: " + e.getMessage());
        }
    }

    public void connectWithEnvToken() {
        System.out.println("=== Connecting with Token from Environment ===\n");

        String token = System.getenv("KUBEMQ_AUTH_TOKEN");

        if (token == null || token.isEmpty()) {
            System.out.println("KUBEMQ_AUTH_TOKEN environment variable not set.");
            System.out.println("  export KUBEMQ_AUTH_TOKEN=your-token-here\n");
            return;
        }

        try (QueuesClient client = QueuesClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID)
                .authToken(token)
                .build()) {

            ServerInfo serverInfo = client.ping();
            System.out.println("Connected with environment token!");
            System.out.println("Server: " + serverInfo.getHost() + " v" + serverInfo.getVersion());

        } catch (Exception e) {
            System.err.println("Failed to connect: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        TokenAuthExample example = new TokenAuthExample();
        example.connectWithEnvToken();
        example.connectWithAuthToken();
        System.out.println("Token auth examples completed.");
    }
}
