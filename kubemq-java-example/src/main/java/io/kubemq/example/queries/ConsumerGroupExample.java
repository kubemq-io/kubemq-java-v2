package io.kubemq.example.queries;

import io.kubemq.sdk.cq.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ConsumerGroupExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queries-consumer-group-client";
    private static final String CHANNEL = "java-queries.consumer-group";

    public static void main(String[] args) throws InterruptedException {
        // Create a client connected to the KubeMQ server
        CQClient client = CQClient.builder().address(ADDRESS).clientId(CLIENT_ID).build();
        client.ping();
        // Create the queries channel
        client.createQueriesChannel(CHANNEL);

        // Subscribe multiple handlers in a consumer group (load-balanced)
        String group = "query-handlers";
        AtomicInteger[] counts = new AtomicInteger[3];
        QueriesSubscription[] subs = new QueriesSubscription[3];

        for (int i = 0; i < 3; i++) {
            final int id = i + 1;
            counts[i] = new AtomicInteger(0);
            final AtomicInteger counter = counts[i];
            Map<String, String> respTags = new HashMap<>();
            respTags.put("handler", String.valueOf(id));

            subs[i] = QueriesSubscription.builder()
                    .channel(CHANNEL).group(group)
                    .onReceiveQueryCallback(q -> {
                        counter.incrementAndGet();
                        client.sendResponseMessage(QueryResponseMessage.builder()
                                .queryReceived(q).isExecuted(true)
                                .body(("Handler " + id).getBytes()).tags(respTags).build());
                    })
                    .onErrorCallback(err -> {}).build();
            // Subscribe each handler to the consumer group
            client.subscribeToQueries(subs[i]);
        }
        Thread.sleep(500);

        // Send queries to the group (distributed across handlers)
        System.out.println("Sending 9 queries to group '" + group + "'...\n");
        for (int i = 1; i <= 9; i++) {
            try {
                QueryResponseMessage r = client.sendQueryRequest(QueryMessage.builder()
                        .channel(CHANNEL).body(("Query #" + i).getBytes()).timeoutInSeconds(10).build());
                System.out.println("  Query #" + i + " -> " + r.getTags().get("handler"));
            } catch (Exception e) { /* handle */ }
        }

        System.out.println("\nDistribution:");
        for (int i = 0; i < 3; i++) { System.out.println("  Handler " + (i + 1) + ": " + counts[i].get()); }

        // Clean up resources
        for (QueriesSubscription s : subs) { s.cancel(); }
        client.deleteQueriesChannel(CHANNEL);
        client.close();
    }
}
