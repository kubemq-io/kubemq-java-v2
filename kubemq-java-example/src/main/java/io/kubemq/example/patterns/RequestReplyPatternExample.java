package io.kubemq.example.patterns;

import io.kubemq.sdk.cq.*;
import io.kubemq.sdk.common.ServerInfo;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Request-Reply Pattern Example
 *
 * This example demonstrates implementing the Request-Reply (Request-Response)
 * pattern using KubeMQ CQ (Commands/Queries) and other messaging patterns.
 *
 * Request-Reply Pattern:
 * - Client sends request and waits for response
 * - Server processes request and sends back response
 * - Synchronous from client perspective
 * - Common in microservices architectures
 *
 * Implementation Options in KubeMQ:
 * 1. Commands: For action execution (success/failure response)
 * 2. Queries: For data retrieval (response with data)
 * 3. Correlation with PubSub: Async request-reply with correlation IDs
 *
 * Use Cases:
 * - API gateway to microservices
 * - RPC-style communication
 * - Service-to-service calls
 * - Database proxy patterns
 * - Aggregation services
 *
 * Best Practices:
 * - Set appropriate timeouts
 * - Handle timeout gracefully
 * - Use correlation IDs for async patterns
 * - Implement circuit breakers
 * - Log request/response pairs
 *
 * @see io.kubemq.sdk.cq.QueryMessage
 * @see io.kubemq.sdk.cq.CommandMessage
 */
public class RequestReplyPatternExample {

    private final CQClient cqClient;
    private final String address = "localhost:50000";
    private final String clientId = "request-reply-client";

    /**
     * Initializes the CQClient.
     */
    public RequestReplyPatternExample() {
        cqClient = CQClient.builder()
                .address(address)
                .clientId(clientId)
                .build();

        ServerInfo pingResult = cqClient.ping();
        System.out.println("Connected to: " + pingResult.getHost() + " v" + pingResult.getVersion());
    }

    /**
     * Demonstrates basic request-reply with queries.
     */
    public void basicRequestReplyExample() {
        System.out.println("\n=== Basic Request-Reply Pattern ===\n");

        String channel = "user-service";
        cqClient.createQueriesChannel(channel);

        // Service (responder)
        Consumer<QueryMessageReceived> userService = request -> {
            String requestBody = new String(request.getBody());
            System.out.println("  [Service] Received request: " + requestBody);

            // Parse request and process
            String response;
            Map<String, String> tags = new HashMap<>();

            if (requestBody.contains("getUser")) {
                response = "{\"userId\": 123, \"name\": \"John Doe\", \"email\": \"john@example.com\"}";
                tags.put("status", "200");
            } else if (requestBody.contains("listUsers")) {
                response = "[{\"id\": 1, \"name\": \"User 1\"}, {\"id\": 2, \"name\": \"User 2\"}]";
                tags.put("status", "200");
                tags.put("count", "2");
            } else {
                response = "{\"error\": \"Unknown operation\"}";
                tags.put("status", "400");
            }

            System.out.println("  [Service] Sending response: " + response.substring(0, Math.min(50, response.length())) + "...");

            cqClient.sendResponseMessage(QueryResponseMessage.builder()
                    .queryReceived(request)
                    .isExecuted(true)
                    .body(response.getBytes(StandardCharsets.UTF_8))
                    .tags(tags)
                    .build());
        };

        QueriesSubscription subscription = QueriesSubscription.builder()
                .channel(channel)
                .onReceiveQueryCallback(userService)
                .onErrorCallback(err -> System.err.println("Service error: " + err))
                .build();

        cqClient.subscribeToQueries(subscription);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Client (requester)
        System.out.println("\nClient making requests:\n");

        // Request 1: Get user
        System.out.println("Request: getUser(123)");
        QueryResponseMessage response1 = cqClient.sendQueryRequest(
                QueryMessage.builder()
                        .channel(channel)
                        .body("getUser:123".getBytes())
                        .timeoutInSeconds(10)
                        .build()
        );
        System.out.println("Response: " + new String(response1.getBody()));
        System.out.println("Status: " + response1.getTags().get("status") + "\n");

        // Request 2: List users
        System.out.println("Request: listUsers()");
        QueryResponseMessage response2 = cqClient.sendQueryRequest(
                QueryMessage.builder()
                        .channel(channel)
                        .body("listUsers".getBytes())
                        .timeoutInSeconds(10)
                        .build()
        );
        System.out.println("Response: " + new String(response2.getBody()));
        System.out.println("Count: " + response2.getTags().get("count") + "\n");

        subscription.cancel();
        cqClient.deleteQueriesChannel(channel);
    }

    /**
     * Demonstrates RPC-style request-reply.
     */
    public void rpcStyleExample() {
        System.out.println("=== RPC-Style Request-Reply ===\n");

        String channel = "calculator-service";
        cqClient.createQueriesChannel(channel);

        // Calculator service
        Consumer<QueryMessageReceived> calculator = request -> {
            Map<String, String> reqTags = request.getTags();
            String operation = reqTags.getOrDefault("operation", "unknown");
            double a = Double.parseDouble(reqTags.getOrDefault("a", "0"));
            double b = Double.parseDouble(reqTags.getOrDefault("b", "0"));

            double result;
            String error = null;

            switch (operation) {
                case "add":
                    result = a + b;
                    break;
                case "subtract":
                    result = a - b;
                    break;
                case "multiply":
                    result = a * b;
                    break;
                case "divide":
                    if (b == 0) {
                        result = 0;
                        error = "Division by zero";
                    } else {
                        result = a / b;
                    }
                    break;
                default:
                    result = 0;
                    error = "Unknown operation: " + operation;
            }

            Map<String, String> respTags = new HashMap<>();
            respTags.put("operation", operation);

            if (error != null) {
                cqClient.sendResponseMessage(QueryResponseMessage.builder()
                        .queryReceived(request)
                        .isExecuted(false)
                        .error(error)
                        .tags(respTags)
                        .build());
            } else {
                cqClient.sendResponseMessage(QueryResponseMessage.builder()
                        .queryReceived(request)
                        .isExecuted(true)
                        .body(String.valueOf(result).getBytes())
                        .tags(respTags)
                        .build());
            }
        };

        QueriesSubscription subscription = QueriesSubscription.builder()
                .channel(channel)
                .onReceiveQueryCallback(calculator)
                .onErrorCallback(err -> {})
                .build();

        cqClient.subscribeToQueries(subscription);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Make RPC-style calls
        System.out.println("Making calculator RPC calls:\n");

        String[][] calculations = {
            {"add", "10", "5"},
            {"subtract", "10", "3"},
            {"multiply", "6", "7"},
            {"divide", "100", "4"},
            {"divide", "10", "0"}  // Error case
        };

        for (String[] calc : calculations) {
            Map<String, String> tags = new HashMap<>();
            tags.put("operation", calc[0]);
            tags.put("a", calc[1]);
            tags.put("b", calc[2]);

            QueryResponseMessage response = cqClient.sendQueryRequest(
                    QueryMessage.builder()
                            .channel(channel)
                            .body("calculate".getBytes())
                            .tags(tags)
                            .timeoutInSeconds(5)
                            .build()
            );

            if (response.isExecuted()) {
                System.out.println("  " + calc[0] + "(" + calc[1] + ", " + calc[2] + ") = " +
                        new String(response.getBody()));
            } else {
                System.out.println("  " + calc[0] + "(" + calc[1] + ", " + calc[2] + ") ERROR: " +
                        response.getError());
            }
        }
        System.out.println();

        subscription.cancel();
        cqClient.deleteQueriesChannel(channel);
    }

    /**
     * Demonstrates request-reply with timeout handling.
     */
    public void timeoutHandlingExample() {
        System.out.println("=== Request-Reply with Timeout Handling ===\n");

        String channel = "slow-service";
        cqClient.createQueriesChannel(channel);

        // Slow service
        Consumer<QueryMessageReceived> slowService = request -> {
            Map<String, String> tags = request.getTags();
            int delay = Integer.parseInt(tags.getOrDefault("processingTime", "0"));

            System.out.println("  [Service] Processing (will take " + delay + "ms)...");

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            cqClient.sendResponseMessage(QueryResponseMessage.builder()
                    .queryReceived(request)
                    .isExecuted(true)
                    .body("Completed".getBytes())
                    .build());

            System.out.println("  [Service] Response sent");
        };

        QueriesSubscription subscription = QueriesSubscription.builder()
                .channel(channel)
                .onReceiveQueryCallback(slowService)
                .onErrorCallback(err -> {})
                .build();

        cqClient.subscribeToQueries(subscription);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Test different scenarios
        int[][] scenarios = {
            {500, 2},   // 500ms processing, 2s timeout -> success
            {3000, 1}   // 3000ms processing, 1s timeout -> timeout
        };

        for (int[] scenario : scenarios) {
            int processingMs = scenario[0];
            int timeoutSec = scenario[1];

            System.out.println("\nRequest: " + processingMs + "ms processing, " + timeoutSec + "s timeout");

            Map<String, String> tags = new HashMap<>();
            tags.put("processingTime", String.valueOf(processingMs));

            long start = System.currentTimeMillis();
            try {
                QueryResponseMessage response = cqClient.sendQueryRequest(
                        QueryMessage.builder()
                                .channel(channel)
                                .body("request".getBytes())
                                .tags(tags)
                                .timeoutInSeconds(timeoutSec)
                                .build()
                );
                long elapsed = System.currentTimeMillis() - start;
                System.out.println("  SUCCESS after " + elapsed + "ms: " + new String(response.getBody()));

            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                System.out.println("  TIMEOUT after " + elapsed + "ms: " + e.getMessage());
                System.out.println("  (Implement retry or fallback here)");
            }
        }
        System.out.println();

        // Give slow service time to finish
        try {
            Thread.sleep(2500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        subscription.cancel();
        cqClient.deleteQueriesChannel(channel);
    }

    /**
     * Demonstrates service proxy pattern.
     */
    public void serviceProxyExample() {
        System.out.println("=== Service Proxy Pattern ===\n");

        String backendChannel = "backend-service";
        cqClient.createQueriesChannel(backendChannel);

        // Backend service
        Consumer<QueryMessageReceived> backendService = request -> {
            String operation = new String(request.getBody());
            System.out.println("  [Backend] Processing: " + operation);

            cqClient.sendResponseMessage(QueryResponseMessage.builder()
                    .queryReceived(request)
                    .isExecuted(true)
                    .body(("Backend response for: " + operation).getBytes())
                    .build());
        };

        QueriesSubscription backendSub = QueriesSubscription.builder()
                .channel(backendChannel)
                .onReceiveQueryCallback(backendService)
                .onErrorCallback(err -> {})
                .build();

        cqClient.subscribeToQueries(backendSub);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Proxy client that adds logging, metrics, etc.
        System.out.println("Creating service proxy with logging and metrics...\n");

        ServiceProxy proxy = new ServiceProxy(cqClient, backendChannel);

        // Make calls through proxy
        String[] operations = {"getConfig", "updateSettings", "healthCheck"};

        for (String op : operations) {
            System.out.println("Client: Calling " + op);
            try {
                String response = proxy.call(op);
                System.out.println("  Response: " + response);
            } catch (Exception e) {
                System.out.println("  Error: " + e.getMessage());
            }
        }

        System.out.println("\nProxy statistics:");
        System.out.println("  Total calls: " + proxy.getTotalCalls());
        System.out.println("  Successful: " + proxy.getSuccessfulCalls());
        System.out.println("  Avg latency: " + proxy.getAverageLatency() + "ms\n");

        backendSub.cancel();
        cqClient.deleteQueriesChannel(backendChannel);
    }

    /**
     * Simple service proxy implementation.
     */
    private class ServiceProxy {
        private final CQClient client;
        private final String channel;
        private int totalCalls = 0;
        private int successfulCalls = 0;
        private long totalLatency = 0;

        public ServiceProxy(CQClient client, String channel) {
            this.client = client;
            this.channel = channel;
        }

        public String call(String operation) throws Exception {
            totalCalls++;
            long start = System.currentTimeMillis();

            try {
                // Pre-call logging
                System.out.println("  [Proxy] Request: " + operation);

                QueryResponseMessage response = client.sendQueryRequest(
                        QueryMessage.builder()
                                .channel(channel)
                                .body(operation.getBytes())
                                .timeoutInSeconds(10)
                                .build()
                );

                long latency = System.currentTimeMillis() - start;
                totalLatency += latency;

                // Post-call logging
                System.out.println("  [Proxy] Response received in " + latency + "ms");

                if (response.isExecuted()) {
                    successfulCalls++;
                    return new String(response.getBody());
                } else {
                    throw new RuntimeException(response.getError());
                }
            } catch (Exception e) {
                System.out.println("  [Proxy] Error: " + e.getMessage());
                throw e;
            }
        }

        public int getTotalCalls() { return totalCalls; }
        public int getSuccessfulCalls() { return successfulCalls; }
        public long getAverageLatency() {
            return successfulCalls > 0 ? totalLatency / successfulCalls : 0;
        }
    }

    /**
     * Demonstrates scatter-gather pattern.
     */
    public void scatterGatherExample() {
        System.out.println("=== Scatter-Gather Pattern ===\n");

        System.out.println("Scatter-Gather: Send request to multiple services,");
        System.out.println("aggregate responses.\n");

        // Create multiple services
        String[] services = {"pricing-service", "inventory-service", "shipping-service"};

        for (String service : services) {
            cqClient.createQueriesChannel(service);

            final String svcName = service;
            Consumer<QueryMessageReceived> handler = request -> {
                String productId = new String(request.getBody());

                // Each service returns its specific data
                String response;
                switch (svcName) {
                    case "pricing-service":
                        response = "{\"price\": 99.99, \"currency\": \"USD\"}";
                        break;
                    case "inventory-service":
                        response = "{\"inStock\": true, \"quantity\": 50}";
                        break;
                    case "shipping-service":
                        response = "{\"deliveryDays\": 3, \"cost\": 5.99}";
                        break;
                    default:
                        response = "{}";
                }

                cqClient.sendResponseMessage(QueryResponseMessage.builder()
                        .queryReceived(request)
                        .isExecuted(true)
                        .body(response.getBytes())
                        .metadata(svcName)
                        .build());
            };

            QueriesSubscription sub = QueriesSubscription.builder()
                    .channel(service)
                    .onReceiveQueryCallback(handler)
                    .onErrorCallback(err -> {})
                    .build();

            cqClient.subscribeToQueries(sub);
        }

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Scatter: Send requests to all services in parallel
        System.out.println("Scattering request to all services...\n");
        String productId = "PROD-12345";

        ExecutorService executor = Executors.newFixedThreadPool(services.length);
        Map<String, Future<String>> futures = new HashMap<>();

        for (String service : services) {
            futures.put(service, executor.submit(() -> {
                QueryResponseMessage response = cqClient.sendQueryRequest(
                        QueryMessage.builder()
                                .channel(service)
                                .body(productId.getBytes())
                                .timeoutInSeconds(5)
                                .build()
                );
                return new String(response.getBody());
            }));
        }

        // Gather: Collect all responses
        System.out.println("Gathering responses:\n");
        StringBuilder aggregated = new StringBuilder();
        aggregated.append("{\"productId\": \"").append(productId).append("\", ");

        for (String service : services) {
            try {
                String response = futures.get(service).get(5, TimeUnit.SECONDS);
                System.out.println("  " + service + ": " + response);
                aggregated.append("\"").append(service).append("\": ").append(response).append(", ");
            } catch (Exception e) {
                System.out.println("  " + service + ": ERROR - " + e.getMessage());
            }
        }

        aggregated.setLength(aggregated.length() - 2);  // Remove trailing comma
        aggregated.append("}");

        System.out.println("\nAggregated response:");
        System.out.println("  " + aggregated.toString() + "\n");

        executor.shutdown();

        // Cleanup
        for (String service : services) {
            cqClient.deleteQueriesChannel(service);
        }
    }

    /**
     * Cleans up resources.
     */
    public void cleanup() {
        cqClient.close();
        System.out.println("Cleaned up resources.\n");
    }

    /**
     * Main method demonstrating request-reply patterns.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║          Request-Reply Pattern Examples                      ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        RequestReplyPatternExample example = new RequestReplyPatternExample();

        try {
            // Basic request-reply
            example.basicRequestReplyExample();

            // RPC-style
            example.rpcStyleExample();

            // Timeout handling
            example.timeoutHandlingExample();

            // Service proxy
            example.serviceProxyExample();

            // Scatter-gather
            example.scatterGatherExample();

        } finally {
            example.cleanup();
        }

        System.out.println("Request-reply pattern examples completed.");
    }
}
