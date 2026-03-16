package io.kubemq.example.commands;

import io.kubemq.sdk.cq.*;
import io.kubemq.sdk.common.ServerInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SendCommandExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-commands-send-command-client";
    private static final String CHANNEL = "java-commands.send-command";

    public static void main(String[] args) throws InterruptedException {
        // Create a client connected to the KubeMQ server
        CQClient client = CQClient.builder().address(ADDRESS).clientId(CLIENT_ID).build();
        ServerInfo info = client.ping();
        System.out.println("Connected to: " + info.getHost());
        // Create the commands channel
        client.createCommandsChannel(CHANNEL);

        CountDownLatch latch = new CountDownLatch(1);

        // Subscribe to handle incoming commands (handler sends response)
        CommandsSubscription sub = CommandsSubscription.builder()
                .channel(CHANNEL)
                .onReceiveCommandCallback(cmd -> {
                    System.out.println("  Handler received: " + new String(cmd.getBody()));
                    client.sendResponseMessage(CommandResponseMessage.builder()
                            .commandReceived(cmd).isExecuted(true).build());
                    latch.countDown();
                })
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        // Start the command handler subscription
        client.subscribeToCommands(sub);
        Thread.sleep(300);

        Map<String, String> tags = new HashMap<>();
        tags.put("action", "restart-service");

        CommandMessage command = CommandMessage.builder()
                .channel(CHANNEL).body("Restart the worker service".getBytes())
                .metadata("Command metadata").tags(tags).timeoutInSeconds(10).build();

        // Send a command and wait for the response
        CommandResponseMessage response = client.sendCommandRequest(command);
        System.out.println("Command executed: " + response.isExecuted());

        latch.await(5, TimeUnit.SECONDS);
        // Clean up resources
        sub.cancel();
        client.deleteCommandsChannel(CHANNEL);
        client.close();
    }
}

// Expected output:
// Connected to: <host>
//   Handler received: Restart the worker service
// Command executed: true
