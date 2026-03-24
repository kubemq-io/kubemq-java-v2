// Queue Simple worker: sendQueueMessage for send, receiveQueueMessages for receive.
// autoAckMessages=true for auto-consumed messages.
// v2: Multi-channel support with channelIndex-based naming.

package io.kubemq.burnin;

import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.queues.QueueMessage;
import io.kubemq.sdk.queues.QueueSendResult;
import io.kubemq.sdk.queues.QueuesPollRequest;
import io.kubemq.sdk.queues.QueuesPollResponse;
import io.kubemq.sdk.queues.QueueMessageReceived;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Queue Simple pattern worker: unary send/receive with auto-ack.
 * v2: Constructed per-channel by PatternGroup.
 */
public final class QueueSimpleWorker extends BaseWorker {

    private static final Logger logger = Logger.getLogger(QueueSimpleWorker.class.getName());
    private static final String SDK = "java";
    private static final String PATTERN_NAME = "queue_simple";
    private static final int MAX_IN_FLIGHT = 1000;
    private final Semaphore inFlight = new Semaphore(MAX_IN_FLIGHT);

    private final List<Thread> consumerThreads = new ArrayList<>();
    private final List<Thread> producerThreads = new ArrayList<>();
    private final int producersPerChannel;
    private final int consumersPerChannel;

    public QueueSimpleWorker(BurninConfig config, String runId, String channelName, int channelIndex,
                              int producersPerChannel, int consumersPerChannel, int rate) {
        super(PATTERN_NAME, config, channelName, rate, channelIndex);
        this.producersPerChannel = producersPerChannel;
        this.consumersPerChannel = consumersPerChannel;
    }

    @Override
    public void startConsumers(PubSubClient pubSubClient, QueuesClient queuesClient, CQClient cqClient) {
        for (int i = 0; i < consumersPerChannel; i++) {
            String consumerId = "c-" + PATTERN_NAME + "-" + String.format("%04d", getChannelIndex()) + "-" + String.format("%03d", i);
            Thread t = new Thread(() -> runConsumer(consumerId, queuesClient),
                    "queue-simple-consumer-" + getChannelIndex() + "-" + i);
            t.setDaemon(true);
            consumerThreads.add(t);
            t.start();
        }
    }

    private void runConsumer(String consumerId, QueuesClient client) {
        int maxItems = getConfig().getQueue().getPollMaxMessages();
        int waitTimeoutSec = getConfig().getQueue().getPollWaitTimeoutSeconds();

        while (!consumerStop.get()) {
            try {
                QueuesPollRequest request = QueuesPollRequest.builder()
                        .channel(getChannelName())
                        .pollMaxMessages(maxItems)
                        .pollWaitTimeoutInSeconds(waitTimeoutSec)
                        .autoAckMessages(true)
                        .build();

                QueuesPollResponse response = client.receiveQueueMessages(request);

                if (response.isError()) {
                    String errMsg = response.getError() != null ? response.getError() : "unknown";
                    if (!errMsg.toLowerCase().contains("timeout")) {
                        recordError("receive_failure");
                    }
                    continue;
                }

                List<QueueMessageReceived> messages = response.getMessages();
                if (messages == null || messages.isEmpty()) {
                    continue;
                }

                for (QueueMessageReceived msg : messages) {
                    Map<String, String> tags = msg.getTags();
                    if (tags != null && "true".equals(tags.get("warmup"))) {
                        continue;
                    }

                    try {
                        MessagePayload decoded = Payload.decode(msg.getBody());
                        String crcTag = tags != null ? tags.getOrDefault("content_hash", "") : "";
                        recordReceive(consumerId, msg.getBody(), crcTag,
                                decoded.producerId, decoded.sequence);
                    } catch (Exception e) {
                        recordError("decode_failure");
                    }
                }
            } catch (Exception ex) {
                if (consumerStop.get()) break;
                String msg = ex.getMessage() != null ? ex.getMessage() : "";
                if (!msg.toLowerCase().contains("timeout")) {
                    recordError("receive_failure");
                }
                sleepQuietly(1000);
            }
        }
    }

    @Override
    public void startProducers(PubSubClient pubSubClient, QueuesClient queuesClient, CQClient cqClient) {
        for (int i = 0; i < producersPerChannel; i++) {
            String producerId = "p-" + PATTERN_NAME + "-" + String.format("%04d", getChannelIndex()) + "-" + String.format("%03d", i);
            Thread t = new Thread(() -> runProducer(producerId, queuesClient),
                    "queue-simple-producer-" + getChannelIndex() + "-" + i);
            t.setDaemon(true);
            producerThreads.add(t);
            t.start();
        }
    }

    private void runProducer(String producerId, QueuesClient client) {
        long seq = 0;

        while (!producerStop.get()) {
            if (!waitForRate()) break;
            if (backpressureCheck()) {
                sleepQuietly(100);
                continue;
            }

            // Acquire in-flight permit FIRST to avoid sequence gaps on timeout
            try {
                if (!inFlight.tryAcquire(100, TimeUnit.MILLISECONDS)) continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            seq++;
            int size = messageSize();
            EncodedPayload encoded = Payload.encode(SDK, PATTERN_NAME, producerId, seq, size);
            final long thisSeq = seq;

            try {
                Map<String, String> tags = new HashMap<>();
                tags.put("content_hash", encoded.crcHex);

                QueueMessage message = QueueMessage.builder()
                        .channel(getChannelName())
                        .body(encoded.body)
                        .tags(tags)
                        .build();

                // Async send: fire without blocking, handle result in callback
                client.sendQueuesMessageAsync(message)
                        .whenComplete((result, ex) -> {
                            inFlight.release();
                            if (ex != null) {
                                recordError("send_failure");
                                return;
                            }
                            if (result.isError()) {
                                recordError("send_failure");
                            } else {
                                recordSend(producerId, thisSeq, encoded.body.length);
                            }
                        });
            } catch (Exception e) {
                if (producerStop.get()) break;
                recordError("send_failure");
            }
        }
    }

    @Override
    public void close() {
        super.close();
    }

    private static void sleepQuietly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
