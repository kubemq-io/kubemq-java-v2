package io.kubemq.example.cq;

import io.kubemq.sdk.cq.*;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.exception.GRPCException;

import java.util.List;

public class ListExample {

    private static CQClient cqClient;

    private static String commandChannel = "my_commands_channel";
    private static String queryChannel = "my_queries_channel";
    private final String address = "localhost:50000";
    private final String clientId = "kueMQClientId";

    public ListExample() {
   // Create a CQClient
        cqClient = CQClient.builder()
                 .address(address)
                .clientId(clientId)
                .build();

        // Ping to test Connection is successful
        ServerInfo pingResult = cqClient.ping();
        System.out.println("Ping Response: " + pingResult.toString());
    }


    private void listCommandsChannels(String channelSearch) {
         System.out.println("\nExecuting listCommandsChannels...");
        List<CQChannel> channels = cqClient.listCommandsChannels(channelSearch);
        System.out.println("Command Channels: " + channels);
    }

    private void listQueriesChannels(String channelSearch) {
         System.out.println("\nExecuting listQueriesChannels...");
        List<CQChannel> channels = cqClient.listQueriesChannels(channelSearch);
        System.out.println("Query Channels: " + channels);
    }


    public static void main(String[] args) {
        ListExample cqClientExample = new ListExample();
        try {

            cqClientExample.listCommandsChannels("my_commands_");
            cqClientExample.listQueriesChannels("my_queries_");

        } catch (GRPCException e) {
            System.err.println("gRPC error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
