// Events Store worker: persistent pub/sub via PubSubClient.
// publishEventStore, check result.isSent(). subscribeToEventsStore with StartNewOnly.
// v2: Multi-channel support with channelIndex-based naming.

package io.kubemq.burnin;

import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.pubsub.EventStoreMessage;
import io.kubemq.sdk.pubsub.EventStoreMessageReceived;
import io.kubemq.sdk.pubsub.EventsStoreSubscription;
import io.kubemq.sdk.pubsub.EventsStoreType;
import io.kubemq.sdk.pubsub.EventSendResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Events Store pattern worker: persistent publish/subscribe with delivery confirmation.
 * v2: Constructed per-channel by PatternGroup.
 */
public final class EventsStoreWorker extends BaseWorker {

    private static final Logger logger = Logger.getLogger(EventsStoreWorker.class.getName());
    private static final String SDK = "java";
    private static final String PATTERN_NAME = "events_store";
    private static final int MAX_IN_FLIGHT = 1000;
    private final Semaphore inFlight = new Semaphore(MAX_IN_FLIGHT);

    private final List<Thread> consumerThreads = new ArrayList<>();
    private final List<Thread> producerThreads = new ArrayList<>();
    private final List<EventsStoreSubscription> activeSubscriptions = new ArrayList<>();
    private final AtomicLong unconfirmed = new AtomicLong();
    private final String runId;
    private final int producersPerChannel;
    private final int consumersPerChannel;
    private final boolean useConsumerGroup;
    private final String consumerGroupName;

    public long getUnconfirmed() { return unconfirmed.get(); }

    public EventsStoreWorker(BurninConfig config, String runId, String channelName, int channelIndex,
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
        for (int i = 0; i < consumersPerChannel; i++) {
            String consumerId = "c-" + PATTERN_NAME + "-" + String.format("%04d", getChannelIndex()) + "-" + String.format("%03d", i);
            final String cid = consumerId;

            Thread t = new Thread(() -> runConsumer(cid, consumerGroupName, pubSubClient),
                    "events-store-consumer-" + getChannelIndex() + "-" + i);
            t.setDaemon(true);
            consumerThreads.add(t);
            t.start();
        }
    }

    private void runConsumer(String consumerId, String group, PubSubClient client) {
        try {
            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel(getChannelName())
                    .group(group)
                    .eventsStoreType(EventsStoreType.StartNewOnly)
                    .onReceiveEventCallback(evt -> handleEvent(consumerId, evt))
                    .onErrorCallback(err -> {
                        System.err.println("events_store subscription error: " + err.getMessage());
                        recordError("subscription_error");
                        incReconnection();
                    })
                    .build();

            synchronized (activeSubscriptions) {
                activeSubscriptions.add(subscription);
            }

            client.subscribeToEventsStore(subscription);

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
                System.err.println("events_store consumer error: " + ex.getMessage());
                recordError("subscription_error");
                incReconnection();
            }
        }
    }

    private void handleEvent(String consumerId, EventStoreMessageReceived evt) {
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
                    "events-store-producer-" + getChannelIndex() + "-" + i);
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

                EventStoreMessage message = EventStoreMessage.builder()
                        .channel(getChannelName())
                        .body(encoded.body)
                        .tags(tags)
                        .build();

                // Async send: fire without blocking, handle result in callback.
                // This lets the producer thread continue to the next message immediately,
                // matching the Go SDK's async streaming pattern.
                client.sendEventsStoreMessageAsync(message)
                        .whenComplete((result, ex) -> {
                            inFlight.release();
                            if (ex != null) {
                                unconfirmed.incrementAndGet();
                                Metrics.incUnconfirmed(PATTERN_NAME);
                                recordError("send_failure");
                                return;
                            }
                            if (result.isSent()) {
                                recordSend(producerId, thisSeq, encoded.body.length);
                            } else {
                                unconfirmed.incrementAndGet();
                                Metrics.incUnconfirmed(PATTERN_NAME);
                                recordError("send_failure");
                            }
                        });
            } catch (Exception e) {
                if (producerStop.get()) break;
                unconfirmed.incrementAndGet();
                Metrics.incUnconfirmed(PATTERN_NAME);
                recordError("send_failure");
            }
        }
    }

    @Override
    public void stopConsumers() {
        super.stopConsumers();
        synchronized (activeSubscriptions) {
            for (EventsStoreSubscription sub : activeSubscriptions) {
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
