package io.kubemq.example.commands;

import io.kubemq.sdk.cq.*;

public class CommandTimeoutExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-commands-command-timeout-client";
    private static final String CHANNEL = "java-commands.command-timeout";

    public static void main(String[] args) throws InterruptedException {
        CQClient client = CQClient.builder().address(ADDRESS).clientId(CLIENT_ID).build();
        client.ping();
        client.createCommandsChannel(CHANNEL);

        CommandsSubscription sub = CommandsSubscription.builder()
                .channel(CHANNEL)
                .onReceiveCommandCallback(cmd -> {
                    try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    client.sendResponseMessage(CommandResponseMessage.builder()
                            .commandReceived(cmd).isExecuted(true).build());
                })
                .onErrorCallback(err -> {})
                .build();
        client.subscribeToCommands(sub);
        Thread.sleep(300);

        System.out.println("Sending command with 1s timeout (handler takes 3s)...");
        try {
            client.sendCommandRequest(CommandMessage.builder()
                    .channel(CHANNEL).body("Slow command".getBytes()).timeoutInSeconds(1).build());
        } catch (Exception e) {
            System.out.println("Timeout (expected): " + e.getMessage());
        }

        // Wait for handler to finish processing the first command before sending the second
        Thread.sleep(4000);

        System.out.println("\nSending command with 5s timeout (handler takes 3s)...");
        try {
            CommandResponseMessage resp = client.sendCommandRequest(CommandMessage.builder()
                    .channel(CHANNEL).body("Normal command".getBytes()).timeoutInSeconds(5).build());
            System.out.println("Success: " + resp.isExecuted());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }

        sub.cancel();
        client.deleteCommandsChannel(CHANNEL);
        client.close();
    }
}
