package io.kubemq.example.commands;

import io.kubemq.sdk.cq.*;
import java.util.concurrent.CountDownLatch;

public class HandleCommandExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-commands-handle-command-client";
    private static final String CHANNEL = "java-commands.handle-command";

    public static void main(String[] args) throws InterruptedException {
        CQClient client = CQClient.builder().address(ADDRESS).clientId(CLIENT_ID).build();
        client.ping();
        client.createCommandsChannel(CHANNEL);

        CommandsSubscription sub = CommandsSubscription.builder()
                .channel(CHANNEL)
                .onReceiveCommandCallback(cmd -> {
                    System.out.println("Handling command: " + new String(cmd.getBody()));
                    boolean success = processCommand(cmd);

                    CommandResponseMessage response = CommandResponseMessage.builder()
                            .commandReceived(cmd).isExecuted(success)
                            .error(success ? "" : "Processing failed").build();
                    client.sendResponseMessage(response);
                    System.out.println("Response sent: executed=" + success);
                })
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        client.subscribeToCommands(sub);
        System.out.println("Command handler listening on: " + CHANNEL);
        Thread.sleep(300);

        CommandResponseMessage resp = client.sendCommandRequest(CommandMessage.builder()
                .channel(CHANNEL).body("Process order #123".getBytes()).timeoutInSeconds(10).build());
        System.out.println("Result: " + resp.isExecuted());

        sub.cancel();
        client.deleteCommandsChannel(CHANNEL);
        client.close();
    }

    private static boolean processCommand(CommandMessageReceived cmd) {
        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return true;
    }
}
