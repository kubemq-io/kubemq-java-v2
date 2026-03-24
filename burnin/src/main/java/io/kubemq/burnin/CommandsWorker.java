// Commands worker: RPC request/response via CQClient.
// sendCommand(CommandMessage), subscribeToCommands(CommandsSubscription).
// Responder checks warmup FIRST, then sendResponseMessage(CommandResponseMessage).
// v2: Multi-channel support with channelIndex-based naming.

package io.kubemq.burnin;

import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.cq.CommandMessage;
import io.kubemq.sdk.cq.CommandMessageReceived;
import io.kubemq.sdk.cq.CommandResponseMessage;
import io.kubemq.sdk.cq.CommandsSubscription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Commands pattern worker: synchronous RPC with timeout.
 * v2: Constructed per-channel by PatternGroup.
 */
public final class CommandsWorker extends BaseWorker {

    private static final Logger logger = Logger.getLogger(CommandsWorker.class.getName());
    private static final String SDK = "java";
    private static final String PATTERN_NAME = "commands";
    private static final int MAX_IN_FLIGHT = 1000;
    private final Semaphore inFlight = new Semaphore(MAX_IN_FLIGHT);

    private final List<Thread> responderThreads = new ArrayList<>();
    private final List<Thread> senderThreads = new ArrayList<>();
    private final List<CommandsSubscription> activeSubscriptions = new ArrayList<>();
    private CQClient cqClient;
    private final int sendersPerChannel;
    private final int respondersPerChannel;

    public CommandsWorker(BurninConfig config, String runId, String channelName, int channelIndex,
                           int sendersPerChannel, int respondersPerChannel, int rate) {
        super(PATTERN_NAME, config, channelName, rate, channelIndex);
        this.sendersPerChannel = sendersPerChannel;
        this.respondersPerChannel = respondersPerChannel;
    }

    @Override
    public void startConsumers(PubSubClient pubSubClient, QueuesClient queuesClient, CQClient cqClient) {
        this.cqClient = cqClient;

        for (int i = 0; i < respondersPerChannel; i++) {
            String responderId = "r-" + PATTERN_NAME + "-" + String.format("%04d", getChannelIndex()) + "-" + String.format("%03d", i);
            final String rid = responderId;
            Thread t = new Thread(() -> runResponder(rid, cqClient),
                    "commands-responder-" + getChannelIndex() + "-" + i);
            t.setDaemon(true);
            responderThreads.add(t);
            t.start();
        }
    }

    private void runResponder(String responderId, CQClient client) {
        try {
            CommandsSubscription subscription = CommandsSubscription.builder()
                    .channel(getChannelName())
                    .onReceiveCommandCallback(cmd -> handleCommand(responderId, client, cmd))
                    .onErrorCallback(err -> {
                        System.err.println("commands subscription error: " + err.getMessage());
                        recordError("subscription_error");
                        incReconnection();
                    })
                    .build();

            synchronized (activeSubscriptions) {
                activeSubscriptions.add(subscription);
            }

            client.subscribeToCommands(subscription);

            while (!consumerStop.get()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception ex) {
            if (!consumerStop.get()) {
                System.err.println("commands responder error: " + ex.getMessage());
                recordError("subscription_error");
                incReconnection();
            }
        }
    }

    private void handleCommand(String responderId, CQClient client, CommandMessageReceived cmd) {
        if (consumerStop.get()) return;

        Map<String, String> tags = cmd.getTags();
        boolean isWarmup = tags != null && "true".equals(tags.get("warmup"));

        try {
            CommandResponseMessage response = CommandResponseMessage.builder()
                    .commandReceived(cmd)
                    .isExecuted(true)
                    .build();

            client.sendResponseMessage(response);
            if (!isWarmup) {
                recordRespond(responderId);
            }
        } catch (Exception ex) {
            if (!isWarmup) {
                recordResponderError(responderId);
            }
        }
    }

    @Override
    public void startProducers(PubSubClient pubSubClient, QueuesClient queuesClient, CQClient cqClient) {
        for (int i = 0; i < sendersPerChannel; i++) {
            String senderId = "s-" + PATTERN_NAME + "-" + String.format("%04d", getChannelIndex()) + "-" + String.format("%03d", i);
            Thread t = new Thread(() -> runSender(senderId, cqClient),
                    "commands-sender-" + getChannelIndex() + "-" + i);
            t.setDaemon(true);
            senderThreads.add(t);
            t.start();
        }
    }

    private void runSender(String senderId, CQClient client) {
        long seq = 0;
        int timeoutSeconds = Math.max(1, getConfig().getRpc().getTimeoutMs() / 1000);

        while (!producerStop.get()) {
            if (!waitForRate()) break;

            // Acquire in-flight permit FIRST to avoid sequence gaps on timeout
            try {
                if (!inFlight.tryAcquire(100, TimeUnit.MILLISECONDS)) continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            seq++;
            int size = messageSize();
            EncodedPayload encoded = Payload.encode(SDK, PATTERN_NAME, senderId, seq, size);
            final long thisSeq = seq;

            try {
                Map<String, String> tags = new HashMap<>();
                tags.put("content_hash", encoded.crcHex);

                final long t0 = System.nanoTime();

                CommandMessage command = CommandMessage.builder()
                        .channel(getChannelName())
                        .body(encoded.body)
                        .timeoutInSeconds(timeoutSeconds)
                        .tags(tags)
                        .build();

                // Async send: fire without blocking, handle result in callback
                client.sendCommandRequestAsync(command)
                        .whenComplete((resp, ex) -> {
                            inFlight.release();
                            double rpcDuration = (System.nanoTime() - t0) / 1_000_000_000.0;
                            recordRpcLatency(senderId, rpcDuration);

                            if (ex != null) {
                                String msg = ex.getMessage() != null ? ex.getMessage() : "";
                                if (msg.toLowerCase().contains("timeout")) {
                                    incRpcTimeout(senderId);
                                } else {
                                    incRpcError(senderId);
                                }
                                recordSenderError(senderId, "send_failure");
                                return;
                            }

                            if (resp.getError() != null && !resp.getError().isEmpty()) {
                                if (resp.getError().toLowerCase().contains("timeout")) {
                                    incRpcTimeout(senderId);
                                } else {
                                    incRpcError(senderId);
                                }
                            } else {
                                incRpcSuccess(senderId);
                                recordRpcSend(senderId, thisSeq, encoded.body.length);
                                // Balance the tsStore immediately for commands
                                getTsStore().loadAndDelete(senderId, thisSeq);
                            }
                        });
            } catch (Exception ex) {
                if (producerStop.get()) break;
                String msg = ex.getMessage() != null ? ex.getMessage() : "";
                if (msg.toLowerCase().contains("timeout")) {
                    incRpcTimeout(senderId);
                } else {
                    incRpcError(senderId);
                }
                recordSenderError(senderId, "send_failure");
            }
        }
    }

    @Override
    public void stopConsumers() {
        super.stopConsumers();
        synchronized (activeSubscriptions) {
            for (CommandsSubscription sub : activeSubscriptions) {
                try {
                    sub.cancel();
                } catch (Exception e) {
                    // best effort
                }
            }
            activeSubscriptions.clear();
        }
    }

    @Override
    public void close() {
        stopConsumers();
        super.close();
    }
}
