package io.kubemq.example.cq;

import io.kubemq.sdk.cq.*;
import io.kubemq.sdk.common.ServerInfo;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Query With Data Response Example
 *
 * This example demonstrates returning complex data in KubeMQ Query responses.
 * Unlike Commands (which just confirm execution), Queries return data to the sender.
 *
 * Query Response Components:
 * - body: The actual response data (byte array)
 * - metadata: Additional context about the response
 * - tags: Key-value pairs for response attributes
 * - isExecuted: Whether the query was successfully processed
 * - error: Error message if query failed
 *
 * Use Cases:
 * - Request/Reply pattern with data return
 * - Database lookups and data retrieval
 * - Aggregation and computation requests
 * - Status and health check queries
 * - Service discovery and capability queries
 *
 * Data Formats:
 * - JSON: Most common, human-readable
 * - Protocol Buffers: Efficient binary format
 * - Plain text: Simple strings
 * - Custom binary: Domain-specific encoding
 *
 * @see io.kubemq.sdk.cq.QueryMessage
 * @see io.kubemq.sdk.cq.QueryResponseMessage
 */
public class QueryWithDataResponseExample {

    private final CQClient cqClient;
    private final String channelName = "query-data-channel";
    private final String address = "localhost:50000";
    private final String clientId = "query-data-client";

    /**
     * Initializes the CQClient.
     */
    public QueryWithDataResponseExample() {
        cqClient = CQClient.builder()
                .address(address)
                .clientId(clientId)
                .build();

        ServerInfo pingResult = cqClient.ping();
        System.out.println("Connected to: " + pingResult.getHost() + " v" + pingResult.getVersion());

        cqClient.createQueriesChannel(channelName);
    }

    /**
     * Demonstrates query with JSON response.
     */
    public void jsonResponseExample() {
        System.out.println("\n=== JSON Response Example ===\n");

        // Subscriber that returns JSON data
        Consumer<QueryMessageReceived> jsonHandler = query -> {
            String request = new String(query.getBody());
            System.out.println("  Query service received: " + request);

            // Parse request and build JSON response
            String jsonResponse;
            if (request.contains("user")) {
                jsonResponse = "{\"userId\": 12345, \"name\": \"John Doe\", " +
                        "\"email\": \"john@example.com\", \"role\": \"admin\", \"active\": true}";
            } else if (request.contains("stats")) {
                jsonResponse = "{\"totalUsers\": 1234, \"activeToday\": 456, " +
                        "\"averageSessionTime\": 3600, \"peakConcurrent\": 89}";
            } else {
                jsonResponse = "{\"message\": \"Unknown query type\"}";
            }

            Map<String, String> responseTags = new HashMap<>();
            responseTags.put("contentType", "application/json");
            responseTags.put("encoding", "UTF-8");

            QueryResponseMessage response = QueryResponseMessage.builder()
                    .queryReceived(query)
                    .isExecuted(true)
                    .body(jsonResponse.getBytes(StandardCharsets.UTF_8))
                    .metadata("JSON response")
                    .tags(responseTags)
                    .build();

            cqClient.sendResponseMessage(response);
            System.out.println("  Sent JSON response");
        };

        QueriesSubscription subscription = QueriesSubscription.builder()
                .channel(channelName)
                .onReceiveQueryCallback(jsonHandler)
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        cqClient.subscribeToQueries(subscription);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Send query for user data
        System.out.println("Sending query for user data...\n");

        QueryMessage userQuery = QueryMessage.builder()
                .channel(channelName)
                .body("get user profile".getBytes())
                .timeoutInSeconds(10)
                .build();

        QueryResponseMessage userResponse = cqClient.sendQueryRequest(userQuery);
        System.out.println("User Response:");
        System.out.println("  Content-Type: " + userResponse.getTags().get("contentType"));
        System.out.println("  Data: " + new String(userResponse.getBody()));

        // Send query for stats
        System.out.println("\nSending query for stats...\n");

        QueryMessage statsQuery = QueryMessage.builder()
                .channel(channelName)
                .body("get stats".getBytes())
                .timeoutInSeconds(10)
                .build();

        QueryResponseMessage statsResponse = cqClient.sendQueryRequest(statsQuery);
        System.out.println("Stats Response:");
        System.out.println("  Data: " + new String(statsResponse.getBody()));
        System.out.println();

        subscription.cancel();
    }

    /**
     * Demonstrates query with structured data using tags.
     */
    public void structuredResponseExample() {
        System.out.println("=== Structured Response with Tags ===\n");

        String structChannel = channelName + "-struct";
        cqClient.createQueriesChannel(structChannel);

        // Subscriber that returns structured data via tags
        Consumer<QueryMessageReceived> structHandler = query -> {
            String productId = new String(query.getBody());
            System.out.println("  Looking up product: " + productId);

            // Use tags for structured fields
            Map<String, String> productData = new HashMap<>();
            productData.put("productId", productId);
            productData.put("name", "Premium Widget");
            productData.put("price", "99.99");
            productData.put("currency", "USD");
            productData.put("inStock", "true");
            productData.put("quantity", "150");
            productData.put("category", "Electronics");

            QueryResponseMessage response = QueryResponseMessage.builder()
                    .queryReceived(query)
                    .isExecuted(true)
                    .body("Product found".getBytes())
                    .metadata("Product lookup successful")
                    .tags(productData)
                    .build();

            cqClient.sendResponseMessage(response);
        };

        QueriesSubscription subscription = QueriesSubscription.builder()
                .channel(structChannel)
                .onReceiveQueryCallback(structHandler)
                .onErrorCallback(err -> {})
                .build();

        cqClient.subscribeToQueries(subscription);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Query for product
        System.out.println("Querying product information...\n");

        QueryMessage productQuery = QueryMessage.builder()
                .channel(structChannel)
                .body("PROD-12345".getBytes())
                .timeoutInSeconds(10)
                .build();

        QueryResponseMessage response = cqClient.sendQueryRequest(productQuery);

        System.out.println("Product Response (via tags):");
        Map<String, String> tags = response.getTags();
        System.out.println("  Product ID: " + tags.get("productId"));
        System.out.println("  Name: " + tags.get("name"));
        System.out.println("  Price: " + tags.get("price") + " " + tags.get("currency"));
        System.out.println("  In Stock: " + tags.get("inStock") + " (Qty: " + tags.get("quantity") + ")");
        System.out.println("  Category: " + tags.get("category"));
        System.out.println();

        subscription.cancel();
        cqClient.deleteQueriesChannel(structChannel);
    }

    /**
     * Demonstrates query with list/array data.
     */
    public void listDataResponseExample() {
        System.out.println("=== List Data Response ===\n");

        String listChannel = channelName + "-list";
        cqClient.createQueriesChannel(listChannel);

        // Subscriber that returns list data
        Consumer<QueryMessageReceived> listHandler = query -> {
            String category = new String(query.getBody());
            System.out.println("  Fetching items for category: " + category);

            // Build JSON array response
            String jsonList = "[" +
                    "{\"id\": 1, \"name\": \"Item A\", \"price\": 10.00}, " +
                    "{\"id\": 2, \"name\": \"Item B\", \"price\": 20.00}, " +
                    "{\"id\": 3, \"name\": \"Item C\", \"price\": 30.00}, " +
                    "{\"id\": 4, \"name\": \"Item D\", \"price\": 40.00}, " +
                    "{\"id\": 5, \"name\": \"Item E\", \"price\": 50.00}" +
                    "]";

            Map<String, String> tags = new HashMap<>();
            tags.put("contentType", "application/json");
            tags.put("itemCount", "5");
            tags.put("category", category);

            QueryResponseMessage response = QueryResponseMessage.builder()
                    .queryReceived(query)
                    .isExecuted(true)
                    .body(jsonList.getBytes(StandardCharsets.UTF_8))
                    .metadata("List query result")
                    .tags(tags)
                    .build();

            cqClient.sendResponseMessage(response);
        };

        QueriesSubscription subscription = QueriesSubscription.builder()
                .channel(listChannel)
                .onReceiveQueryCallback(listHandler)
                .onErrorCallback(err -> {})
                .build();

        cqClient.subscribeToQueries(subscription);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Query for list
        System.out.println("Querying list of items...\n");

        QueryMessage listQuery = QueryMessage.builder()
                .channel(listChannel)
                .body("Electronics".getBytes())
                .timeoutInSeconds(10)
                .build();

        QueryResponseMessage response = cqClient.sendQueryRequest(listQuery);

        System.out.println("List Response:");
        System.out.println("  Category: " + response.getTags().get("category"));
        System.out.println("  Item Count: " + response.getTags().get("itemCount"));
        System.out.println("  Data: " + new String(response.getBody()));
        System.out.println();

        subscription.cancel();
        cqClient.deleteQueriesChannel(listChannel);
    }

    /**
     * Demonstrates query with error response.
     */
    public void errorResponseExample() {
        System.out.println("=== Error Response Handling ===\n");

        String errorChannel = channelName + "-error";
        cqClient.createQueriesChannel(errorChannel);

        // Subscriber that may return errors
        Consumer<QueryMessageReceived> errorHandler = query -> {
            String request = new String(query.getBody());
            System.out.println("  Processing request: " + request);

            QueryResponseMessage response;

            if (request.equals("valid")) {
                response = QueryResponseMessage.builder()
                        .queryReceived(query)
                        .isExecuted(true)
                        .body("Success!".getBytes())
                        .build();
                System.out.println("  Returning success response");
            } else if (request.equals("not-found")) {
                Map<String, String> errorTags = new HashMap<>();
                errorTags.put("errorCode", "404");
                errorTags.put("errorType", "NotFound");

                response = QueryResponseMessage.builder()
                        .queryReceived(query)
                        .isExecuted(false)
                        .error("Resource not found")
                        .tags(errorTags)
                        .build();
                System.out.println("  Returning not-found error");
            } else {
                Map<String, String> errorTags = new HashMap<>();
                errorTags.put("errorCode", "400");
                errorTags.put("errorType", "BadRequest");

                response = QueryResponseMessage.builder()
                        .queryReceived(query)
                        .isExecuted(false)
                        .error("Invalid request format")
                        .tags(errorTags)
                        .build();
                System.out.println("  Returning bad-request error");
            }

            cqClient.sendResponseMessage(response);
        };

        QueriesSubscription subscription = QueriesSubscription.builder()
                .channel(errorChannel)
                .onReceiveQueryCallback(errorHandler)
                .onErrorCallback(err -> {})
                .build();

        cqClient.subscribeToQueries(subscription);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Test different scenarios
        String[] requests = {"valid", "not-found", "invalid"};

        for (String request : requests) {
            System.out.println("\nSending request: '" + request + "'");

            QueryMessage query = QueryMessage.builder()
                    .channel(errorChannel)
                    .body(request.getBytes())
                    .timeoutInSeconds(10)
                    .build();

            QueryResponseMessage response = cqClient.sendQueryRequest(query);

            System.out.println("Response:");
            System.out.println("  Executed: " + response.isExecuted());
            if (response.isExecuted()) {
                System.out.println("  Data: " + new String(response.getBody()));
            } else {
                System.out.println("  Error: " + response.getError());
                Map<String, String> tags = response.getTags();
                if (tags != null && !tags.isEmpty()) {
                    System.out.println("  Error Code: " + tags.get("errorCode"));
                    System.out.println("  Error Type: " + tags.get("errorType"));
                }
            }
        }
        System.out.println();

        subscription.cancel();
        cqClient.deleteQueriesChannel(errorChannel);
    }

    /**
     * Demonstrates aggregation query pattern.
     */
    public void aggregationQueryExample() {
        System.out.println("=== Aggregation Query Pattern ===\n");

        String aggChannel = channelName + "-agg";
        cqClient.createQueriesChannel(aggChannel);

        // Subscriber that performs aggregation
        Consumer<QueryMessageReceived> aggHandler = query -> {
            Map<String, String> requestTags = query.getTags();
            String operation = requestTags.getOrDefault("operation", "sum");
            System.out.println("  Performing aggregation: " + operation);

            // Simulate aggregation result
            String result;
            Map<String, String> responseTags = new HashMap<>();
            responseTags.put("operation", operation);

            switch (operation) {
                case "sum":
                    result = "{\"result\": 15000, \"count\": 100}";
                    break;
                case "avg":
                    result = "{\"result\": 150.0, \"count\": 100}";
                    break;
                case "max":
                    result = "{\"result\": 999, \"count\": 100}";
                    break;
                case "min":
                    result = "{\"result\": 1, \"count\": 100}";
                    break;
                default:
                    result = "{\"error\": \"Unknown operation\"}";
            }

            QueryResponseMessage response = QueryResponseMessage.builder()
                    .queryReceived(query)
                    .isExecuted(true)
                    .body(result.getBytes())
                    .tags(responseTags)
                    .build();

            cqClient.sendResponseMessage(response);
        };

        QueriesSubscription subscription = QueriesSubscription.builder()
                .channel(aggChannel)
                .onReceiveQueryCallback(aggHandler)
                .onErrorCallback(err -> {})
                .build();

        cqClient.subscribeToQueries(subscription);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Test different aggregations
        String[] operations = {"sum", "avg", "max", "min"};

        System.out.println("Performing aggregation queries:\n");

        for (String op : operations) {
            Map<String, String> tags = new HashMap<>();
            tags.put("operation", op);

            QueryMessage query = QueryMessage.builder()
                    .channel(aggChannel)
                    .body("aggregate sales".getBytes())
                    .tags(tags)
                    .timeoutInSeconds(10)
                    .build();

            QueryResponseMessage response = cqClient.sendQueryRequest(query);

            System.out.println("  " + op.toUpperCase() + ": " + new String(response.getBody()));
        }
        System.out.println();

        subscription.cancel();
        cqClient.deleteQueriesChannel(aggChannel);
    }

    /**
     * Demonstrates paged query results.
     */
    public void pagedResultsExample() {
        System.out.println("=== Paged Query Results ===\n");

        String pageChannel = channelName + "-paged";
        cqClient.createQueriesChannel(pageChannel);

        // Subscriber that returns paged results
        Consumer<QueryMessageReceived> pageHandler = query -> {
            Map<String, String> requestTags = query.getTags();
            int page = Integer.parseInt(requestTags.getOrDefault("page", "1"));
            int pageSize = Integer.parseInt(requestTags.getOrDefault("pageSize", "10"));

            System.out.println("  Fetching page " + page + " (size: " + pageSize + ")");

            // Calculate pagination
            int totalItems = 47;
            int totalPages = (int) Math.ceil((double) totalItems / pageSize);
            int startItem = (page - 1) * pageSize + 1;
            int endItem = Math.min(page * pageSize, totalItems);

            String pageData = String.format(
                "{\"items\": [\"Item %d\", \"Item %d\", \"Item %d\"], " +
                "\"page\": %d, \"pageSize\": %d, \"totalItems\": %d, " +
                "\"totalPages\": %d, \"hasNext\": %b, \"hasPrevious\": %b}",
                startItem, startItem + 1, startItem + 2,
                page, pageSize, totalItems, totalPages,
                page < totalPages, page > 1
            );

            Map<String, String> responseTags = new HashMap<>();
            responseTags.put("page", String.valueOf(page));
            responseTags.put("totalPages", String.valueOf(totalPages));
            responseTags.put("totalItems", String.valueOf(totalItems));

            QueryResponseMessage response = QueryResponseMessage.builder()
                    .queryReceived(query)
                    .isExecuted(true)
                    .body(pageData.getBytes())
                    .tags(responseTags)
                    .build();

            cqClient.sendResponseMessage(response);
        };

        QueriesSubscription subscription = QueriesSubscription.builder()
                .channel(pageChannel)
                .onReceiveQueryCallback(pageHandler)
                .onErrorCallback(err -> {})
                .build();

        cqClient.subscribeToQueries(subscription);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Query multiple pages
        System.out.println("Fetching paged results:\n");

        for (int page = 1; page <= 3; page++) {
            Map<String, String> tags = new HashMap<>();
            tags.put("page", String.valueOf(page));
            tags.put("pageSize", "10");

            QueryMessage query = QueryMessage.builder()
                    .channel(pageChannel)
                    .body("list items".getBytes())
                    .tags(tags)
                    .timeoutInSeconds(10)
                    .build();

            QueryResponseMessage response = cqClient.sendQueryRequest(query);
            Map<String, String> respTags = response.getTags();

            System.out.println("  Page " + respTags.get("page") + " of " + respTags.get("totalPages"));
            System.out.println("  " + new String(response.getBody()));
            System.out.println();
        }

        subscription.cancel();
        cqClient.deleteQueriesChannel(pageChannel);
    }

    /**
     * Cleans up resources.
     */
    public void cleanup() {
        try {
            cqClient.deleteQueriesChannel(channelName);
            System.out.println("Cleaned up channel: " + channelName);
        } catch (Exception e) {
            // Ignore
        }
        cqClient.close();
    }

    /**
     * Main method demonstrating query data response patterns.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║          Query Data Response Examples                        ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        QueryWithDataResponseExample example = new QueryWithDataResponseExample();

        try {
            // JSON response
            example.jsonResponseExample();

            // Structured response with tags
            example.structuredResponseExample();

            // List data response
            example.listDataResponseExample();

            // Error response handling
            example.errorResponseExample();

            // Aggregation queries
            example.aggregationQueryExample();

            // Paged results
            example.pagedResultsExample();

        } finally {
            example.cleanup();
        }

        System.out.println("Query data response examples completed.");
    }
}
