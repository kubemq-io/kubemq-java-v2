package io.kubemq.example.cq;

import io.kubemq.sdk.cq.*;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.exception.GRPCException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class CommandsExample {

    private static KubeMQClient kubeMQClient;
    private static CQClient cqClient;

    private static String commandChannel = "my_commands_channel";
    private final String address = "localhost:50000";
    private final String clientId = "kueMQClientId";

    public CommandsExample() {
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
    
    private void subscribeToCommands(String channel) {
         System.out.println("Executing subscribeToCommands...");
        // Consumer for handling received events
        Consumer<CommandMessageReceived> onReceiveCommandCallback = receivedCommand -> {
            System.out.println("Received CommandMessage: " + receivedCommand);
            // Reply this message 
           CommandResponseMessage response= CommandResponseMessage.builder().
                    commandReceived(receivedCommand)
                     .isExecuted(true)
                     .build();
                    
            cqClient.sendResponseMessage(response);
        };

        // Consumer for handling errors
        Consumer<String> onErrorCallback = errorMessage -> {
            System.err.println("Error in Command Subscription: " + errorMessage);
        };

        CommandsSubscription commandsSubscription = CommandsSubscription.builder()
                .channel(channel)
                .onReceiveCommandCallback(onReceiveCommandCallback)
                .onErrorCallback(onErrorCallback)
                .build();

        cqClient.subscribeToCommands(commandsSubscription);
    }

   private void sendCommandRequest(String channel) {
        System.out.println("Executing sendCommandRequest...");
        
        Map<String, String> tags = new HashMap<>();
        tags.put("tag1", "Command Message example");
        tags.put("tag2", "cq1");
        
        CommandMessage commandMessage = CommandMessage.builder()
                .channel(channel)
                .body("Test Command".getBytes())
                .metadata("Metadata add some extra information")
                .tags(tags)
                .timeoutInSeconds(20)
                .build();

            CommandResponseMessage response = cqClient.sendCommandRequest(commandMessage);
            System.out.println("Command Response: " + response);
       
    }


    public static void main(String[] args) {
        CommandsExample cqClientExample = new CommandsExample();
        try {

            cqClientExample.subscribeToCommands(commandChannel);
            cqClientExample.sendCommandRequest(commandChannel);


        } catch (GRPCException e) {
            System.err.println("gRPC error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}