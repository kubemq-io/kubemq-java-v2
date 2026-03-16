package io.kubemq.example.patterns;

import io.kubemq.sdk.cq.*;
import io.kubemq.sdk.common.ServerInfo;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Request-Reply Pattern Example
 *
 * Demonstrates implementing the Request-Reply pattern using KubeMQ Queries.
 */
public class RequestReplyExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-patterns-request-reply-client";
    private static final String CHANNEL = "java-patterns.request-reply";

    public static void main(String[] args) throws InterruptedException {
        // Create a client connected to the KubeMQ server
        CQClient client = CQClient.builder().address(ADDRESS).clientId(CLIENT_ID).build();
        ServerInfo info = client.ping();
        System.out.println("Connected to: " + info.getHost());
        // Create the queries channel for request-reply
        client.createQueriesChannel(CHANNEL);

        // Define the service handler (receives request, sends response)
        Consumer<QueryMessageReceived> service = request -> {
            String requestBody = new String(request.getBody());
            System.out.println("  [Service] Received: " + requestBody);

            String response = "{\"userId\": 123, \"name\": \"John Doe\"}";
            Map<String, String> tags = new HashMap<>();
            tags.put("status", "200");

            client.sendResponseMessage(QueryResponseMessage.builder()
                    .queryReceived(request).isExecuted(true)
                    .body(response.getBytes(StandardCharsets.UTF_8)).tags(tags).build());
        };

        QueriesSubscription sub = QueriesSubscription.builder()
                .channel(CHANNEL).onReceiveQueryCallback(service)
                .onErrorCallback(err -> System.err.println("Error: " + err)).build();

        // Subscribe to handle incoming requests
        client.subscribeToQueries(sub);
        Thread.sleep(300);

        // Client sends request and waits for reply
        System.out.println("\nClient sending request...");
        QueryResponseMessage response = client.sendQueryRequest(QueryMessage.builder()
                .channel(CHANNEL).body("getUser:123".getBytes()).timeoutInSeconds(10).build());
        System.out.println("Response: " + new String(response.getBody()));
        System.out.println("Status: " + response.getTags().get("status"));

        // Clean up resources
        sub.cancel();
        client.deleteQueriesChannel(CHANNEL);
        client.close();
    }
}
