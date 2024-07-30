package io.kubemq.example.cq;

import io.kubemq.sdk.cq.*;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.exception.GRPCException;


public class CreateExample {

    private static KubeMQClient kubeMQClient;
    private static CQClient cqClient;

    private static String commandChannel = "my_commands_channel";
    private static String queryChannel = "my_queries_channel";
    private final String address = "localhost:50000";
    private final String clientId = "kueMQClientId";

    public CreateExample() {
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

    private void createCommandsChannel(String channel) {
          System.out.println("Executing createCommandsChannel...");
        boolean result = cqClient.createCommandsChannel(channel);
        System.out.println("Commands channel created: " + result);
    }

    private void createQueriesChannel(String channel) {
        System.out.println("\nExecuting createQueriesChannel...");
        boolean result = cqClient.createQueriesChannel(channel);
        System.out.println("Queries channel created: " + result);
    }


    public static void main(String[] args) {
        CreateExample cqClientExample = new CreateExample();
        try {

            cqClientExample.createCommandsChannel(commandChannel);
            cqClientExample.createQueriesChannel(queryChannel);

        } catch (GRPCException e) {
            System.err.println("gRPC error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
