package io.kubemq.example.commands;

import io.kubemq.sdk.cq.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ConsumerGroupExample {
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-commands-consumer-group-client";
    private static final String CHANNEL = "java-commands.consumer-group";

    public static void main(String[] args) throws InterruptedException {
        CQClient client = CQClient.builder().address(ADDRESS).clientId(CLIENT_ID).build();
        client.ping();
        client.createCommandsChannel(CHANNEL);

        String group = "command-handlers";
        int numWorkers = 3;
        AtomicInteger[] counts = new AtomicInteger[numWorkers];
        CommandsSubscription[] subs = new CommandsSubscription[numWorkers];

        for (int i = 0; i < numWorkers; i++) {
            final int id = i + 1;
            counts[i] = new AtomicInteger(0);
            final AtomicInteger counter = counts[i];

            subs[i] = CommandsSubscription.builder()
                    .channel(CHANNEL).group(group)
                    .onReceiveCommandCallback(cmd -> {
                        counter.incrementAndGet();
                        client.sendResponseMessage(CommandResponseMessage.builder()
                                .commandReceived(cmd).isExecuted(true).build());
                    })
                    .onErrorCallback(err -> {}).build();
            client.subscribeToCommands(subs[i]);
        }
        Thread.sleep(500);

        System.out.println("Sending 9 commands to group '" + group + "'...\n");
        for (int i = 1; i <= 9; i++) {
            try { client.sendCommandRequest(CommandMessage.builder()
                    .channel(CHANNEL).body(("Cmd #" + i).getBytes()).timeoutInSeconds(10).build()); }
            catch (Exception e) { /* handle */ }
        }

        System.out.println("Distribution:");
        for (int i = 0; i < numWorkers; i++) {
            System.out.println("  Worker " + (i + 1) + ": " + counts[i].get());
        }

        for (CommandsSubscription s : subs) { s.cancel(); }
        client.deleteCommandsChannel(CHANNEL);
        client.close();
    }
}
