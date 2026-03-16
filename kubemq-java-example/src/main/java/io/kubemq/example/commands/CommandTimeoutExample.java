package io.kubemq.example.commands;

import io.kubemq.sdk.cq.*;

public class CommandTimeoutExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-commands-command-timeout-client";
    private static final String CHANNEL = "java-commands.command-timeout";

    public static void main(String[] args) throws InterruptedException {
        // Create a client connected to the KubeMQ server
        CQClient client = CQClient.builder().address(ADDRESS).clientId(CLIENT_ID).build();
        client.ping();
        // Create the commands channel
        client.createCommandsChannel(CHANNEL);

        // Subscribe to handle commands (handler intentionally delays 3s)
        CommandsSubscription sub = CommandsSubscription.builder()
                .channel(CHANNEL)
                .onReceiveCommandCallback(cmd -> {
                    try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    client.sendResponseMessage(CommandResponseMessage.builder()
                            .commandReceived(cmd).isExecuted(true).build());
                })
                .onErrorCallback(err -> {})
                .build();
        // Start the command handler subscription
        client.subscribeToCommands(sub);
        Thread.sleep(300);

        // Send command with short timeout (expect timeout)
        System.out.println("Sending command with 1s timeout (handler takes 3s)...");
        try {
            client.sendCommandRequest(CommandMessage.builder()
                    .channel(CHANNEL).body("Slow command".getBytes()).timeoutInSeconds(1).build());
        } catch (Exception e) {
            System.out.println("Timeout (expected): " + e.getMessage());
        }

        // Wait for handler to finish processing the first command before sending the second
        Thread.sleep(4000);

        // Send command with sufficient timeout (expect success)
        System.out.println("\nSending command with 5s timeout (handler takes 3s)...");
        try {
            CommandResponseMessage resp = client.sendCommandRequest(CommandMessage.builder()
                    .channel(CHANNEL).body("Normal command".getBytes()).timeoutInSeconds(5).build());
            System.out.println("Success: " + resp.isExecuted());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }

        // Clean up resources
        sub.cancel();
        client.deleteCommandsChannel(CHANNEL);
        client.close();
    }
}
