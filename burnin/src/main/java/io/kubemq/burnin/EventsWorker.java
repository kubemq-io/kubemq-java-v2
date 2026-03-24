// Events worker: fire-and-forget pub/sub via PubSubClient.
// publishEvent(EventMessage), subscribeToEvents(EventsSubscription).
// Cancel via subscription.cancel(). Consumer group support.
// v2: Multi-channel support with channelIndex-based naming.

package io.kubemq.burnin;

import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.pubsub.EventMessage;
import io.kubemq.sdk.pubsub.EventMessageReceived;
import io.kubemq.sdk.pubsub.EventsSubscription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Events pattern worker: fire-and-forget publish/subscribe.
 * v2: Constructed per-channel by PatternGroup.
 */
public final class EventsWorker extends BaseWorker {

    private static final Logger logger = Logger.getLogger(EventsWorker.class.getName());
    private static final String SDK = "java";
    private static final String PATTERN_NAME = "events";

    private final List<Thread> consumerThreads = new ArrayList<>();
    private final List<Thread> producerThreads = new ArrayList<>();
    private final List<EventsSubscription> activeSubscriptions = new ArrayList<>();
    private PubSubClient pubSubClient;
    private final String runId;
    private final int producersPerChannel;
    private final int consumersPerChannel;
    private final boolean useConsumerGroup;
    private final String consumerGroupName;

    /**
     * v2 constructor: channel name and index provided by PatternGroup.
     */
    public EventsWorker(BurninConfig config, String runId, String channelName, int channelIndex,
                         int producersPerChannel, int consumersPerChannel, boolean consumerGroup, int rate) {
        super(PATTERN_NAME, config, channelName, rate, channelIndex);
        this.runId = runId;
        this.producersPerChannel = producersPerChannel;
        this.consumersPerChannel = consumersPerChannel;
        this.useConsumerGroup = consumerGroup;
        this.consumerGroupName = consumerGroup
                ? SDK + "_burnin_" + runId + "_" + PATTERN_NAME + "_" + String.format("%04d", channelIndex) + "_group"
                : "";
    }

    @Override
    public void startConsumers(PubSubClient pubSubClient, QueuesClient queuesClient, CQClient cqClient) {
        this.pubSubClient = pubSubClient;

        for (int i = 0; i < consumersPerChannel; i++) {
            String consumerId = "c-" + PATTERN_NAME + "-" + String.format("%04d", getChannelIndex()) + "-" + String.format("%03d", i);
            final String cid = consumerId;

            Thread t = new Thread(() -> runConsumer(cid, consumerGroupName, pubSubClient),
                    "events-consumer-" + getChannelIndex() + "-" + i);
            t.setDaemon(true);
            consumerThreads.add(t);
            t.start();
        }
    }

    private void runConsumer(String consumerId, String group, PubSubClient client) {
        try {
            EventsSubscription subscription = EventsSubscription.builder()
                    .channel(getChannelName())
                    .group(group)
                    .onReceiveEventCallback(evt -> handleEvent(consumerId, evt))
                    .onErrorCallback(err -> {
                        System.err.println("events subscription error: " + err.getMessage());
                        recordError("subscription_error");
                        incReconnection();
                    })
                    .build();

            synchronized (activeSubscriptions) {
                activeSubscriptions.add(subscription);
            }

            client.subscribeToEvents(subscription);

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
                System.err.println("events consumer error: " + ex.getMessage());
                recordError("subscription_error");
                incReconnection();
            }
        }
    }

    private void handleEvent(String consumerId, EventMessageReceived evt) {
        if (consumerStop.get()) return;

        Map<String, String> tags = evt.getTags();
        if (tags != null && "true".equals(tags.get("warmup"))) {
            return;
        }

        try {
            MessagePayload decoded = Payload.decode(evt.getBody());
            String crcTag = tags != null ? tags.getOrDefault("content_hash", "") : "";
            recordReceive(consumerId, evt.getBody(), crcTag, decoded.producerId, decoded.sequence);
        } catch (Exception e) {
            recordError("decode_failure");
        }
    }

    @Override
    public void startProducers(PubSubClient pubSubClient, QueuesClient queuesClient, CQClient cqClient) {
        for (int i = 0; i < producersPerChannel; i++) {
            String producerId = "p-" + PATTERN_NAME + "-" + String.format("%04d", getChannelIndex()) + "-" + String.format("%03d", i);
            Thread t = new Thread(() -> runProducer(producerId, pubSubClient),
                    "events-producer-" + getChannelIndex() + "-" + i);
            t.setDaemon(true);
            producerThreads.add(t);
            t.start();
        }
    }

    private void runProducer(String producerId, PubSubClient client) {
        long seq = 0;

        while (!producerStop.get()) {
            if (!waitForRate()) break;
            if (backpressureCheck()) {
                sleepQuietly(100);
                continue;
            }

            seq++;
            int size = messageSize();
            EncodedPayload encoded = Payload.encode(SDK, PATTERN_NAME, producerId, seq, size);

            try {
                Map<String, String> tags = new HashMap<>();
                tags.put("content_hash", encoded.crcHex);

                EventMessage message = EventMessage.builder()
                        .channel(getChannelName())
                        .body(encoded.body)
                        .tags(tags)
                        .build();

                client.publishEvent(message);
                recordSend(producerId, seq, encoded.body.length);
            } catch (Exception e) {
                if (producerStop.get()) break;
                recordError("send_failure");
            }
        }
    }

    @Override
    public void stopConsumers() {
        super.stopConsumers();
        synchronized (activeSubscriptions) {
            for (EventsSubscription sub : activeSubscriptions) {
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

    private static void sleepQuietly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
