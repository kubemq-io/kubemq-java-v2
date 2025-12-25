package io.kubemq.example.cq;

import io.kubemq.sdk.cq.*;
import io.kubemq.sdk.common.ServerInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Group Subscription Queries Example
 *
 * This example demonstrates using subscription groups for load-balanced
 * query processing in KubeMQ CQ (Commands/Queries) pattern.
 *
 * Group Subscription for Queries:
 * - Distributes query load across multiple responders
 * - Each query is handled by exactly ONE group member
 * - Responses include data, so responder selection matters
 * - Great for scaling read-heavy workloads
 *
 * Key Differences from Commands:
 * - Commands: Execute action, return success/failure
 * - Queries: Request data, return response body
 * - Both support groups for load balancing
 *
 * Use Cases:
 * - Scalable data services
 * - Read replicas for database queries
 * - Cached data lookup services
 * - API gateway query routing
 * - Search service scaling
 *
 * Best Practices:
 * - All group members should return consistent data
 * - Use groups for stateless query handlers
 * - Consider data locality for performance
 * - Monitor query distribution across members
 *
 * @see io.kubemq.sdk.cq.QueriesSubscription#getGroup()
 */
public class GroupSubscriptionQueriesExample {

    private final CQClient cqClient;
    private final String channelName = "group-queries-channel";
    private final String address = "localhost:50000";
    private final String clientId = "group-query-client";

    /**
     * Initializes the CQClient.
     */
    public GroupSubscriptionQueriesExample() {
        cqClient = CQClient.builder()
                .address(address)
                .clientId(clientId)
                .build();

        ServerInfo pingResult = cqClient.ping();
        System.out.println("Connected to: " + pingResult.getHost() + " v" + pingResult.getVersion());

        cqClient.createQueriesChannel(channelName);
    }

    /**
     * Demonstrates basic group subscription for queries.
     */
    public void basicGroupQueryExample() {
        System.out.println("\n=== Basic Query Group Subscription ===\n");

        String groupName = "query-handlers";
        int numResponders = 3;
        int numQueries = 9;

        AtomicInteger[] responderCounts = new AtomicInteger[numResponders];
        CountDownLatch latch = new CountDownLatch(numQueries);

        // Create multiple responders in the same group
        QueriesSubscription[] subscriptions = new QueriesSubscription[numResponders];

        for (int i = 0; i < numResponders; i++) {
            final int responderId = i + 1;
            responderCounts[i] = new AtomicInteger(0);
            final AtomicInteger counter = responderCounts[i];

            Consumer<QueryMessageReceived> handler = query -> {
                counter.incrementAndGet();
                String request = new String(query.getBody());
                System.out.println("  Responder " + responderId + " handling: " + request);

                // Return response with responder info
                String responseData = "{\"handler\": " + responderId +
                        ", \"query\": \"" + request + "\", \"result\": \"OK\"}";

                Map<String, String> tags = new HashMap<>();
                tags.put("handlerId", String.valueOf(responderId));

                QueryResponseMessage response = QueryResponseMessage.builder()
                        .queryReceived(query)
                        .isExecuted(true)
                        .body(responseData.getBytes())
                        .tags(tags)
                        .build();

                cqClient.sendResponseMessage(response);
                latch.countDown();
            };

            subscriptions[i] = QueriesSubscription.builder()
                    .channel(channelName)
                    .group(groupName)  // Same group for all responders
                    .onReceiveQueryCallback(handler)
                    .onErrorCallback(err -> System.err.println("Responder " + responderId + " error: " + err))
                    .build();

            cqClient.subscribeToQueries(subscriptions[i]);
        }

        System.out.println("Created " + numResponders + " responders in group: " + groupName + "\n");

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Send queries
        System.out.println("Sending " + numQueries + " queries (load balanced across responders)...\n");

        for (int i = 1; i <= numQueries; i++) {
            QueryMessage query = QueryMessage.builder()
                    .channel(channelName)
                    .body(("Query #" + i).getBytes())
                    .timeoutInSeconds(10)
                    .build();

            try {
                QueryResponseMessage response = cqClient.sendQueryRequest(query);
                System.out.println("  Query #" + i + " -> Handler " +
                        response.getTags().get("handlerId"));
            } catch (Exception e) {
                System.err.println("  Query #" + i + " failed: " + e.getMessage());
            }
        }

        try {
            latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Show distribution
        System.out.println("\n─────────────────────────────────────");
        System.out.println("Query Distribution:");
        int total = 0;
        for (int i = 0; i < numResponders; i++) {
            int count = responderCounts[i].get();
            total += count;
            System.out.println("  Responder " + (i + 1) + ": " + count + " queries");
        }
        System.out.println("  Total: " + total + " (each query handled by exactly one responder)");
        System.out.println("─────────────────────────────────────\n");

        // Cancel subscriptions
        for (QueriesSubscription sub : subscriptions) {
            sub.cancel();
        }
    }

    /**
     * Demonstrates data service with replicas pattern.
     */
    public void dataServiceReplicasExample() {
        System.out.println("=== Data Service Replicas ===\n");

        String dataChannel = channelName + "-data";
        cqClient.createQueriesChannel(dataChannel);

        // Simulate multiple database read replicas
        String[] replicaNames = {"Primary", "Replica-1", "Replica-2"};
        AtomicInteger[] replicaLoads = new AtomicInteger[3];
        QueriesSubscription[] replicas = new QueriesSubscription[3];

        for (int i = 0; i < 3; i++) {
            final String replicaName = replicaNames[i];
            replicaLoads[i] = new AtomicInteger(0);
            final AtomicInteger load = replicaLoads[i];

            replicas[i] = QueriesSubscription.builder()
                    .channel(dataChannel)
                    .group("data-replicas")  // All replicas in same group
                    .onReceiveQueryCallback(query -> {
                        load.incrementAndGet();
                        String key = new String(query.getBody());
                        System.out.println("  " + replicaName + " looking up: " + key);

                        // All replicas return same data (consistent)
                        String data = "{\"key\": \"" + key + "\", \"value\": \"data-12345\", " +
                                "\"replica\": \"" + replicaName + "\"}";

                        Map<String, String> tags = new HashMap<>();
                        tags.put("replica", replicaName);

                        cqClient.sendResponseMessage(QueryResponseMessage.builder()
                                .queryReceived(query)
                                .isExecuted(true)
                                .body(data.getBytes())
                                .tags(tags)
                                .build());
                    })
                    .onErrorCallback(err -> {})
                    .build();

            cqClient.subscribeToQueries(replicas[i]);
        }

        System.out.println("Data service with 3 replicas ready\n");

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate read requests
        System.out.println("Sending 12 read requests...\n");

        for (int i = 1; i <= 12; i++) {
            QueryMessage query = QueryMessage.builder()
                    .channel(dataChannel)
                    .body(("user:" + i).getBytes())
                    .timeoutInSeconds(10)
                    .build();

            try {
                QueryResponseMessage response = cqClient.sendQueryRequest(query);
                System.out.println("    Served by: " + response.getTags().get("replica"));
            } catch (Exception e) {
                // Handle
            }
        }

        System.out.println("\nReplica Load:");
        for (int i = 0; i < 3; i++) {
            System.out.println("  " + replicaNames[i] + ": " + replicaLoads[i].get() + " reads");
        }
        System.out.println();

        for (QueriesSubscription replica : replicas) {
            replica.cancel();
        }
        cqClient.deleteQueriesChannel(dataChannel);
    }

    /**
     * Demonstrates specialized query handlers.
     */
    public void specializedHandlersExample() {
        System.out.println("=== Specialized Query Handlers ===\n");

        String specialChannel = channelName + "-special";
        cqClient.createQueriesChannel(specialChannel);

        // Different groups for different query types
        // Group 1: User queries
        AtomicInteger userQueries = new AtomicInteger(0);
        QueriesSubscription userHandler = QueriesSubscription.builder()
                .channel(specialChannel)
                .group("user-handlers")
                .onReceiveQueryCallback(query -> {
                    userQueries.incrementAndGet();
                    String request = new String(query.getBody());
                    System.out.println("  [User Handler] " + request);

                    cqClient.sendResponseMessage(QueryResponseMessage.builder()
                            .queryReceived(query)
                            .isExecuted(true)
                            .body("{\"type\": \"user\", \"data\": \"user info\"}".getBytes())
                            .build());
                })
                .onErrorCallback(err -> {})
                .build();

        // Group 2: Product queries
        AtomicInteger productQueries = new AtomicInteger(0);
        QueriesSubscription productHandler = QueriesSubscription.builder()
                .channel(specialChannel)
                .group("product-handlers")
                .onReceiveQueryCallback(query -> {
                    productQueries.incrementAndGet();
                    String request = new String(query.getBody());
                    System.out.println("  [Product Handler] " + request);

                    cqClient.sendResponseMessage(QueryResponseMessage.builder()
                            .queryReceived(query)
                            .isExecuted(true)
                            .body("{\"type\": \"product\", \"data\": \"product info\"}".getBytes())
                            .build());
                })
                .onErrorCallback(err -> {})
                .build();

        cqClient.subscribeToQueries(userHandler);
        cqClient.subscribeToQueries(productHandler);

        System.out.println("Two specialized handler groups:");
        System.out.println("  - user-handlers");
        System.out.println("  - product-handlers\n");

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Send queries - both groups receive (but one responds first)
        System.out.println("Sending queries (both groups receive)...\n");

        for (int i = 1; i <= 4; i++) {
            try {
                cqClient.sendQueryRequest(QueryMessage.builder()
                        .channel(specialChannel)
                        .body(("Query " + i).getBytes())
                        .timeoutInSeconds(5)
                        .build());
            } catch (Exception e) {
                // Handle
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\nQuery counts:");
        System.out.println("  User handlers: " + userQueries.get());
        System.out.println("  Product handlers: " + productQueries.get());
        System.out.println("\nNote: All groups receive all queries (each group picks one member).\n");

        userHandler.cancel();
        productHandler.cancel();
        cqClient.deleteQueriesChannel(specialChannel);
    }

    /**
     * Demonstrates caching layer with group pattern.
     */
    public void cachingLayerExample() {
        System.out.println("=== Caching Layer Pattern ===\n");

        String cacheChannel = channelName + "-cache";
        cqClient.createQueriesChannel(cacheChannel);

        // Simulate multiple cache nodes
        AtomicInteger[] cacheHits = new AtomicInteger[3];
        QueriesSubscription[] cacheNodes = new QueriesSubscription[3];
        Map<String, String>[] localCaches = new HashMap[3];

        for (int i = 0; i < 3; i++) {
            final int nodeId = i + 1;
            cacheHits[i] = new AtomicInteger(0);
            localCaches[i] = new HashMap<>();

            // Pre-populate each cache with some data
            localCaches[i].put("key1", "value1-node" + nodeId);
            localCaches[i].put("key2", "value2-node" + nodeId);
            localCaches[i].put("key3", "value3-node" + nodeId);

            final Map<String, String> cache = localCaches[i];
            final AtomicInteger hits = cacheHits[i];

            cacheNodes[i] = QueriesSubscription.builder()
                    .channel(cacheChannel)
                    .group("cache-cluster")
                    .onReceiveQueryCallback(query -> {
                        String key = new String(query.getBody());
                        String value = cache.get(key);

                        Map<String, String> tags = new HashMap<>();
                        tags.put("cacheNode", String.valueOf(nodeId));
                        tags.put("cacheHit", value != null ? "true" : "false");

                        if (value != null) {
                            hits.incrementAndGet();
                            cqClient.sendResponseMessage(QueryResponseMessage.builder()
                                    .queryReceived(query)
                                    .isExecuted(true)
                                    .body(value.getBytes())
                                    .tags(tags)
                                    .build());
                        } else {
                            cqClient.sendResponseMessage(QueryResponseMessage.builder()
                                    .queryReceived(query)
                                    .isExecuted(false)
                                    .error("Cache miss")
                                    .tags(tags)
                                    .build());
                        }
                    })
                    .onErrorCallback(err -> {})
                    .build();

            cqClient.subscribeToQueries(cacheNodes[i]);
        }

        System.out.println("Cache cluster with 3 nodes ready\n");

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Query cache
        System.out.println("Querying cache cluster:\n");

        String[] keys = {"key1", "key2", "key3", "key1", "key2", "key4"};

        for (String key : keys) {
            QueryMessage query = QueryMessage.builder()
                    .channel(cacheChannel)
                    .body(key.getBytes())
                    .timeoutInSeconds(10)
                    .build();

            try {
                QueryResponseMessage response = cqClient.sendQueryRequest(query);
                String node = response.getTags().get("cacheNode");
                String hit = response.getTags().get("cacheHit");

                if ("true".equals(hit)) {
                    System.out.println("  " + key + " -> HIT (node " + node + "): " +
                            new String(response.getBody()));
                } else {
                    System.out.println("  " + key + " -> MISS (node " + node + ")");
                }
            } catch (Exception e) {
                System.out.println("  " + key + " -> ERROR: " + e.getMessage());
            }
        }

        System.out.println("\nCache node statistics:");
        for (int i = 0; i < 3; i++) {
            System.out.println("  Node " + (i + 1) + ": " + cacheHits[i].get() + " hits");
        }
        System.out.println();

        for (QueriesSubscription node : cacheNodes) {
            node.cancel();
        }
        cqClient.deleteQueriesChannel(cacheChannel);
    }

    /**
     * Demonstrates query router pattern.
     */
    public void queryRouterExample() {
        System.out.println("=== Query Router Pattern ===\n");

        String routerChannel = channelName + "-router";
        cqClient.createQueriesChannel(routerChannel);

        // Create query handlers with different capabilities
        AtomicInteger fastCount = new AtomicInteger(0);
        AtomicInteger slowCount = new AtomicInteger(0);

        QueriesSubscription fastHandler = QueriesSubscription.builder()
                .channel(routerChannel)
                .group("query-routers")
                .onReceiveQueryCallback(query -> {
                    fastCount.incrementAndGet();
                    // Fast response
                    cqClient.sendResponseMessage(QueryResponseMessage.builder()
                            .queryReceived(query)
                            .isExecuted(true)
                            .body("Fast response".getBytes())
                            .metadata("fast-handler")
                            .build());
                })
                .onErrorCallback(err -> {})
                .build();

        QueriesSubscription slowHandler = QueriesSubscription.builder()
                .channel(routerChannel)
                .group("query-routers")
                .onReceiveQueryCallback(query -> {
                    slowCount.incrementAndGet();
                    // Slower response
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    cqClient.sendResponseMessage(QueryResponseMessage.builder()
                            .queryReceived(query)
                            .isExecuted(true)
                            .body("Slow response".getBytes())
                            .metadata("slow-handler")
                            .build());
                })
                .onErrorCallback(err -> {})
                .build();

        cqClient.subscribeToQueries(fastHandler);
        cqClient.subscribeToQueries(slowHandler);

        System.out.println("Query routers ready (fast and slow handlers)\n");

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Send queries
        System.out.println("Sending 10 queries...\n");

        for (int i = 1; i <= 10; i++) {
            try {
                QueryResponseMessage response = cqClient.sendQueryRequest(
                        QueryMessage.builder()
                                .channel(routerChannel)
                                .body(("Query " + i).getBytes())
                                .timeoutInSeconds(10)
                                .build());
                System.out.println("  Query " + i + " -> " + response.getMetadata());
            } catch (Exception e) {
                // Handle
            }
        }

        System.out.println("\nHandler distribution:");
        System.out.println("  Fast handler: " + fastCount.get() + " queries");
        System.out.println("  Slow handler: " + slowCount.get() + " queries");
        System.out.println();

        fastHandler.cancel();
        slowHandler.cancel();
        cqClient.deleteQueriesChannel(routerChannel);
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
     * Main method demonstrating group subscription for queries.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║         Group Subscription Queries Examples                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        GroupSubscriptionQueriesExample example = new GroupSubscriptionQueriesExample();

        try {
            // Basic group query
            example.basicGroupQueryExample();

            // Data service replicas
            example.dataServiceReplicasExample();

            // Specialized handlers
            example.specializedHandlersExample();

            // Caching layer
            example.cachingLayerExample();

            // Query router
            example.queryRouterExample();

        } finally {
            example.cleanup();
        }

        System.out.println("Group subscription queries examples completed.");
    }
}
