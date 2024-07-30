package io.kubemq.example.cq;

import io.kubemq.sdk.cq.*;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.exception.GRPCException;

public class DeleteExample {

    private static KubeMQClient kubeMQClient;
    private static CQClient cqClient;

    private static String commandChannel = "my_commands_channel";
    private static String queryChannel = "my_queries_channel";
    private final String address = "localhost:50000";
    private final String clientId = "kueMQClientId";

    public DeleteExample() {
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

   private void deleteCommandsChannel(String channel) {
        System.out.println("Executing deleteCommandsChannel...");
        boolean result = cqClient.deleteCommandsChannel(channel);
        System.out.println("Commands channel deleted: " + result);
    }

    /**
     * Deletes a queries channel.
     *
     * @param channel the name of the channel to delete
     */
    private void deleteQueriesChannel(String channel) {
        System.out.println("Executing deleteQueriesChannel...");
        boolean result = cqClient.deleteQueriesChannel(channel);
        System.out.println("Queries channel deleted: " + result);
    }
   

    public static void main(String[] args) {
        DeleteExample cqClientExample = new DeleteExample();
        try {
           
            cqClientExample.deleteCommandsChannel(commandChannel);
            cqClientExample.deleteQueriesChannel(queryChannel);

        } catch (GRPCException e) {
            System.err.println("gRPC error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
