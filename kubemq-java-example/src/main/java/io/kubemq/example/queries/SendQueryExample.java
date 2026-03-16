package io.kubemq.example.queries;

import io.kubemq.sdk.cq.*;
import java.util.HashMap;
import java.util.Map;

public class SendQueryExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queries-send-query-client";
    private static final String CHANNEL = "java-queries.send-query";

    public static void main(String[] args) throws InterruptedException {
        CQClient client = CQClient.builder().address(ADDRESS).clientId(CLIENT_ID).build();
        client.ping();
        client.createQueriesChannel(CHANNEL);

        QueriesSubscription sub = QueriesSubscription.builder()
                .channel(CHANNEL)
                .onReceiveQueryCallback(query -> {
                    System.out.println("  Handler received: " + new String(query.getBody()));
                    client.sendResponseMessage(QueryResponseMessage.builder()
                            .queryReceived(query).isExecuted(true)
                            .body("{\"result\": \"data\"}".getBytes()).build());
                })
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();
        client.subscribeToQueries(sub);
        Thread.sleep(300);

        Map<String, String> tags = new HashMap<>();
        tags.put("type", "lookup");

        QueryMessage query = QueryMessage.builder()
                .channel(CHANNEL).body("Get user data".getBytes())
                .metadata("Query metadata").tags(tags).timeoutInSeconds(10).build();

        QueryResponseMessage response = client.sendQueryRequest(query);
        System.out.println("Query executed: " + response.isExecuted());
        System.out.println("Response body: " + new String(response.getBody()));

        sub.cancel();
        client.deleteQueriesChannel(CHANNEL);
        client.close();
    }
}
