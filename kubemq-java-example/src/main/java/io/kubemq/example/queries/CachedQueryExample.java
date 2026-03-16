package io.kubemq.example.queries;

import io.kubemq.sdk.cq.*;

public class CachedQueryExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queries-cached-query-client";
    private static final String CHANNEL = "java-queries.cached-query";

    public static void main(String[] args) throws InterruptedException {
        // Create a client connected to the KubeMQ server
        CQClient client = CQClient.builder().address(ADDRESS).clientId(CLIENT_ID).build();
        client.ping();
        // Create the queries channel
        client.createQueriesChannel(CHANNEL);

        // Subscribe to handle queries (responses may be cached)
        QueriesSubscription sub = QueriesSubscription.builder()
                .channel(CHANNEL)
                .onReceiveQueryCallback(query -> {
                    System.out.println("Handler invoked for: " + query.getId());
                    client.sendResponseMessage(QueryResponseMessage.builder()
                            .queryReceived(query).isExecuted(true)
                            .body("cached-result".getBytes()).build());
                })
                .onErrorCallback(err -> System.err.println("Error: " + err.getMessage()))
                .build();
        // Start the query handler subscription
        client.subscribeToQueries(sub);
        Thread.sleep(1000);

        // Build query with cache key and TTL
        QueryMessage q = QueryMessage.builder()
                .channel(CHANNEL).body("get-price".getBytes()).metadata("product=xyz")
                .timeoutInSeconds(10).cacheKey("price:xyz").cacheTtlInSeconds(60).build();

        // First request hits the handler; second may be served from cache
        QueryResponseMessage r1 = client.sendQueryRequest(q);
        System.out.println("Response 1: " + new String(r1.getBody()));

        QueryResponseMessage r2 = client.sendQueryRequest(q);
        System.out.println("Response 2: " + new String(r2.getBody()) + " (may be cached)");

        // Clean up resources
        sub.cancel();
        client.deleteQueriesChannel(CHANNEL);
        client.close();
    }
}
