package io.kubemq.example.config;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueueMessage;
import io.kubemq.sdk.queues.QueueSendResult;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.cq.CQClient;

/**
 * Authentication Token Example
 *
 * This example demonstrates how to connect to a KubeMQ server that requires
 * authentication using JWT tokens or API keys.
 *
 * Authentication Flow:
 * 1. Obtain an authentication token (JWT or API key) from your identity provider
 * 2. Pass the token to the KubeMQ client using the authToken parameter
 * 3. The token is automatically included in all gRPC requests as metadata
 *
 * Token Types Supported:
 * - JWT (JSON Web Tokens) - typically from OAuth2/OIDC providers
 * - API Keys - static keys configured in KubeMQ
 * - Custom tokens - any string accepted by your KubeMQ auth configuration
 *
 * Security Best Practices:
 * - Never hardcode tokens in source code
 * - Use environment variables or secure vaults for token storage
 * - Rotate tokens regularly
 * - Use TLS when transmitting tokens over the network
 *
 * Prerequisites:
 * - KubeMQ server configured with authentication enabled
 * - Valid authentication token
 *
 * @see <a href="https://docs.kubemq.io/getting-started/security">KubeMQ Security Documentation</a>
 */
public class AuthTokenExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "auth-example-client";

    // In production, load this from environment variable or secure vault
    // Example: System.getenv("KUBEMQ_AUTH_TOKEN")
    private static final String AUTH_TOKEN = "your-jwt-token-or-api-key";

    /**
     * Demonstrates connecting with an authentication token.
     * The token is passed in the authToken parameter and automatically
     * included in all gRPC requests.
     */
    public void connectWithAuthToken() {
        System.out.println("=== Connecting with Authentication Token ===\n");

        try {
            // Create client with authentication token
            QueuesClient client = QueuesClient.builder()
                    .address(ADDRESS)
                    .clientId(CLIENT_ID)
                    .authToken(AUTH_TOKEN)  // Authentication token
                    .logLevel(KubeMQClient.Level.INFO)
                    .build();

            // Test the connection
            ServerInfo serverInfo = client.ping();
            System.out.println("Successfully authenticated and connected!");
            System.out.println("Server Info: " + serverInfo);

            client.close();
            System.out.println("Connection closed.\n");

        } catch (Exception e) {
            System.err.println("Authentication failed: " + e.getMessage());
            System.err.println("\nPossible causes:");
            System.err.println("  1. Invalid or expired token");
            System.err.println("  2. Token doesn't have required permissions");
            System.err.println("  3. Server authentication not configured correctly");
        }
    }

    /**
     * Demonstrates loading authentication token from environment variable.
     * This is the recommended approach for production environments.
     */
    public void connectWithEnvToken() {
        System.out.println("=== Connecting with Token from Environment ===\n");

        // Load token from environment variable
        String token = System.getenv("KUBEMQ_AUTH_TOKEN");

        if (token == null || token.isEmpty()) {
            System.out.println("KUBEMQ_AUTH_TOKEN environment variable not set.");
            System.out.println("To test this example, set the environment variable:");
            System.out.println("  export KUBEMQ_AUTH_TOKEN=your-token-here\n");
            return;
        }

        try {
            QueuesClient client = QueuesClient.builder()
                    .address(ADDRESS)
                    .clientId(CLIENT_ID)
                    .authToken(token)
                    .build();

            ServerInfo serverInfo = client.ping();
            System.out.println("Successfully connected with environment token!");
            System.out.println("Server: " + serverInfo.getHost() + " v" + serverInfo.getVersion());

            client.close();

        } catch (Exception e) {
            System.err.println("Failed to connect: " + e.getMessage());
        }
    }

    /**
     * Demonstrates authenticated operations across different client types.
     * Shows that the same token works for Queues, PubSub, and CQ clients.
     */
    public void authenticatedOperationsDemo() {
        System.out.println("=== Authenticated Operations Demo ===\n");

        String token = System.getenv("KUBEMQ_AUTH_TOKEN");
        if (token == null) {
            token = AUTH_TOKEN; // Fall back to configured token
        }

        // Demonstrate with QueuesClient
        System.out.println("1. Creating authenticated QueuesClient...");
        try (QueuesClient queuesClient = QueuesClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID + "-queues")
                .authToken(token)
                .build()) {

            ServerInfo info = queuesClient.ping();
            System.out.println("   QueuesClient connected: " + info.getHost());

            // Create a channel (requires auth)
            boolean created = queuesClient.createQueuesChannel("auth-test-queue");
            System.out.println("   Channel created: " + created);

            // Send a message (requires auth)
            QueueMessage message = QueueMessage.builder()
                    .channel("auth-test-queue")
                    .body("Authenticated message".getBytes())
                    .build();
            QueueSendResult result = queuesClient.sendQueuesMessage(message);
            System.out.println("   Message sent: " + result.getId());

            // Cleanup
            queuesClient.deleteQueuesChannel("auth-test-queue");
            System.out.println("   Channel deleted\n");

        } catch (Exception e) {
            System.err.println("   QueuesClient error: " + e.getMessage() + "\n");
        }

        // Demonstrate with PubSubClient
        System.out.println("2. Creating authenticated PubSubClient...");
        try (PubSubClient pubSubClient = PubSubClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID + "-pubsub")
                .authToken(token)
                .build()) {

            ServerInfo info = pubSubClient.ping();
            System.out.println("   PubSubClient connected: " + info.getHost());

            boolean created = pubSubClient.createEventsChannel("auth-test-events");
            System.out.println("   Events channel created: " + created);

            pubSubClient.deleteEventsChannel("auth-test-events");
            System.out.println("   Events channel deleted\n");

        } catch (Exception e) {
            System.err.println("   PubSubClient error: " + e.getMessage() + "\n");
        }

        // Demonstrate with CQClient
        System.out.println("3. Creating authenticated CQClient...");
        try (CQClient cqClient = CQClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID + "-cq")
                .authToken(token)
                .build()) {

            ServerInfo info = cqClient.ping();
            System.out.println("   CQClient connected: " + info.getHost());

            boolean created = cqClient.createCommandsChannel("auth-test-commands");
            System.out.println("   Commands channel created: " + created);

            cqClient.deleteCommandsChannel("auth-test-commands");
            System.out.println("   Commands channel deleted\n");

        } catch (Exception e) {
            System.err.println("   CQClient error: " + e.getMessage() + "\n");
        }
    }

    /**
     * Demonstrates token refresh pattern for long-running applications.
     * In production, you would implement proper token refresh logic.
     */
    public void tokenRefreshPattern() {
        System.out.println("=== Token Refresh Pattern ===\n");

        System.out.println("For long-running applications with expiring tokens:");
        System.out.println("1. Monitor token expiration time");
        System.out.println("2. Refresh token before it expires");
        System.out.println("3. Create a new client with the refreshed token");
        System.out.println("4. Gracefully transition from old to new client");
        System.out.println();

        // Example pseudo-code for token refresh
        System.out.println("Example implementation:");
        System.out.println("```java");
        System.out.println("// Token refresh service");
        System.out.println("ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);");
        System.out.println("scheduler.scheduleAtFixedRate(() -> {");
        System.out.println("    if (isTokenExpiringSoon()) {");
        System.out.println("        String newToken = refreshToken();");
        System.out.println("        recreateClientWithNewToken(newToken);");
        System.out.println("    }");
        System.out.println("}, 0, 5, TimeUnit.MINUTES);");
        System.out.println("```\n");
    }

    /**
     * Main method demonstrating authentication examples.
     *
     * Note: For these examples to work with actual authentication,
     * you need a KubeMQ server configured with authentication enabled.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           KubeMQ Authentication Token Examples               ║");
        System.out.println("║                                                              ║");
        System.out.println("║  Set KUBEMQ_AUTH_TOKEN environment variable for testing      ║");
        System.out.println("║  or configure a token in the AUTH_TOKEN constant.            ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        AuthTokenExample example = new AuthTokenExample();

        // Try connecting with environment token first
        example.connectWithEnvToken();

        // Demonstrate authenticated operations
        example.authenticatedOperationsDemo();

        // Show token refresh pattern
        example.tokenRefreshPattern();

        System.out.println("Authentication examples completed.");
    }
}
