package io.kubemq.example.cq;

import io.kubemq.sdk.cq.*;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.exception.GRPCException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class QueriesExample {

    private static KubeMQClient kubeMQClient;
    private static CQClient cqClient;

    private static String queryChannel = "my_queries_channel";
    private final String address = "localhost:50000";
    private final String clientId = "kueMQClientId";

    public QueriesExample() {
        // Setup KubeMQ client
        kubeMQClient = KubeMQClient.builder()
                .address(address)
                .clientId(clientId)
                .build();

        // Ping to test Connection is successful
        ServerInfo pingResult = kubeMQClient.ping();
        System.out.println("Ping Response: " + pingResult.toString());

        // Create a CQClient
        cqClient = CQClient.builder()
                .kubeMQClient(kubeMQClient)
                .build();
    }
    

    private void subscribeToQueries(String channel) {
         System.out.println("Executing subscribeToQueries...");
        // Consumer for handling received events
        Consumer<QueryMessageReceived> onReceiveQueryCallback = receivedQuery -> {
            System.out.println("Received Query: " + receivedQuery);
             // Reply this message 
           QueryResponseMessage response= QueryResponseMessage.builder()
                    .queryReceived(receivedQuery)
                     .isExecuted(true)
                     .body("hello kubemq, I'm replying to you!".getBytes())
                     .build();
                    
            cqClient.sendResponseMessage(response);
            
        };

        // Consumer for handling errors
        Consumer<String> onErrorCallback = errorMessage -> {
            System.err.println("Error in Query Subscription: " + errorMessage);
        };
        QueriesSubscription queriesSubscription = QueriesSubscription.builder()
                .channel(channel)
                .onReceiveQueryCallback(onReceiveQueryCallback)
                .onErrorCallback(onErrorCallback)
                .build();

        cqClient.subscribeToQueries(queriesSubscription);
    }
    
      private void sendQueryRequest(String channel) {
        System.out.println("Executing sendQueryRequest...");
        Map<String, String> tags = new HashMap<>();
        tags.put("tag1", "query Message example");
        tags.put("tag2", "query1");
        QueryMessage queryMessage = QueryMessage.builder()
                .channel(channel)
                .body("Test Query".getBytes())
                .metadata("Metadata put some description")
                .tags(tags)
                .timeoutInSeconds(20)
                .build();

            QueryResponseMessage response = cqClient.sendQueryRequest(queryMessage);
            System.out.println("Query Response: " + response);
       
    }


    public static void main(String[] args) {
        QueriesExample cqClientExample = new QueriesExample();
        try {
            cqClientExample.subscribeToQueries(queryChannel);
            cqClientExample.sendQueryRequest(queryChannel);

        } catch (GRPCException e) {
            System.err.println("gRPC error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
