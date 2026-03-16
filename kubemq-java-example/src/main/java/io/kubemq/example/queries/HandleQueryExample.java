package io.kubemq.example.queries;

import io.kubemq.sdk.cq.*;

public class HandleQueryExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queries-handle-query-client";
    private static final String CHANNEL = "java-queries.handle-query";

    public static void main(String[] args) throws InterruptedException {
        // Create a client connected to the KubeMQ server
        CQClient client = CQClient.builder().address(ADDRESS).clientId(CLIENT_ID).build();
        client.ping();
        // Create the queries channel
        client.createQueriesChannel(CHANNEL);

        // Subscribe to handle incoming queries
        QueriesSubscription sub = QueriesSubscription.builder()
                .channel(CHANNEL)
                .onReceiveQueryCallback(query -> {
                    String request = new String(query.getBody());
                    System.out.println("Handling query: " + request);
                    String responseData = "{\"users\": [{\"id\": 1, \"name\": \"Alice\"}]}";

                    // Send response back to the query sender
                    client.sendResponseMessage(QueryResponseMessage.builder()
                            .queryReceived(query).isExecuted(true)
                            .body(responseData.getBytes()).build());
                })
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();
        // Start the query handler subscription
        client.subscribeToQueries(sub);
        System.out.println("Query handler listening on: " + CHANNEL);
        Thread.sleep(300);

        // Send a query and wait for the response
        QueryResponseMessage resp = client.sendQueryRequest(QueryMessage.builder()
                .channel(CHANNEL).body("listUsers".getBytes()).timeoutInSeconds(10).build());
        System.out.println("Response: " + new String(resp.getBody()));

        // Clean up resources
        sub.cancel();
        client.deleteQueriesChannel(CHANNEL);
        client.close();
    }
}
