// BaseWorker: shared state, counters, 2-phase shutdown, dual tracking.
// All recording methods are thread-safe for multi-producer/consumer concurrency.
// v2: Added channelIndex, patternLatencyAccum for dual-write latency recording.

package io.kubemq.burnin;

import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.cq.CQClient;

import java.io.Closeable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Abstract base class for all 6 messaging pattern workers.
 * Provides 2-phase AtomicBoolean shutdown, all counters, tracking, rate limiting,
 * latency measurement, backpressure support, and per-worker stat tracking.
 * v2: Supports channelIndex for multi-channel naming and pattern-level latency dual-write.
 */
public abstract class BaseWorker implements Closeable {

    private static final Logger logger = Logger.getLogger(BaseWorker.class.getName());

    private final String pattern;
    private final BurninConfig config;
    private final String channelName;
    private final int channelIndex; // 1-based channel index (0001-1000)

    // 2-phase shutdown: producer stops first, consumer drains, then stops.
    protected final AtomicBoolean producerStop = new AtomicBoolean(false);
    protected final AtomicBoolean consumerStop = new AtomicBoolean(false);

    // Tracking
    private final Tracker tracker;
    private final LatencyAccumulator latencyAccum = new LatencyAccumulator();
    private final LatencyAccumulator rpcLatencyAccum = new LatencyAccumulator();
    private final PeakRateTracker peakRate = new PeakRateTracker();
    private final SlidingRateTracker slidingRate = new SlidingRateTracker();
    private final TimestampStore tsStore = new TimestampStore();

    // Pattern-level shared latency accumulator (thread-safe, shared across all channels in a PatternGroup)
    private volatile LatencyAccumulator patternLatencyAccum;
    private volatile LatencyAccumulator patternRpcLatencyAccum;

    // Rate limiting
    private final RateLimiter limiter;
    private final SizeDistribution sizeDist;

    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong received = new AtomicLong();
    private final AtomicLong corrupted = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final AtomicLong reconnections = new AtomicLong();
    private final AtomicLong rpcSuccess = new AtomicLong();
    private final AtomicLong rpcTimeout = new AtomicLong();
    private final AtomicLong rpcError = new AtomicLong();
    private final AtomicLong bytesSent = new AtomicLong();
    private final AtomicLong bytesReceived = new AtomicLong();

    // Downtime tracking
    private long downtimeStartMs; // 0 means not in downtime
    private double downtimeTotal;
    private final Object downtimeLock = new Object();

    // Reconnection duplicate cooldown
    private volatile boolean recentlyReconnected;
    private final AtomicInteger reconnDupCooldown = new AtomicInteger();

    // Backpressure warning
    private volatile boolean backpressureLogged;

    // Per-consumer message counts for group balance
    private final ConcurrentHashMap<String, AtomicLong> consumerCounts = new ConcurrentHashMap<>();

    // --- Per-worker stat tracking (M3-M6) ---

    static class ProducerStat {
        final String id;
        final AtomicLong sent = new AtomicLong();
        final AtomicLong errors = new AtomicLong();
        final SlidingRateTracker rate = new SlidingRateTracker();
        final LatencyAccumulator latency = new LatencyAccumulator();
        ProducerStat(String id) { this.id = id; }
    }

    static class ConsumerStat {
        final String id;
        final AtomicLong received = new AtomicLong();
        final AtomicLong corrupted = new AtomicLong();
        final AtomicLong errors = new AtomicLong();
        final LatencyAccumulator latency = new LatencyAccumulator();
        ConsumerStat(String id) { this.id = id; }
    }

    static class SenderStat {
        final String id;
        final AtomicLong sent = new AtomicLong();
        final AtomicLong responsesSuccess = new AtomicLong();
        final AtomicLong responsesTimeout = new AtomicLong();
        final AtomicLong responsesError = new AtomicLong();
        final AtomicLong errors = new AtomicLong();
        final SlidingRateTracker rate = new SlidingRateTracker();
        final LatencyAccumulator latency = new LatencyAccumulator();
        SenderStat(String id) { this.id = id; }
    }

    static class ResponderStat {
        final String id;
        final AtomicLong responded = new AtomicLong();
        final AtomicLong errors = new AtomicLong();
        ResponderStat(String id) { this.id = id; }
    }

    private final ConcurrentHashMap<String, ProducerStat> producerStats = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConsumerStat> consumerStats = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SenderStat> senderStats = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ResponderStat> responderStats = new ConcurrentHashMap<>();

    /**
     * v2 constructor with channelIndex for multi-channel naming.
     */
    protected BaseWorker(String pattern, BurninConfig config, String channelName, double rate, int channelIndex) {
        this.pattern = pattern;
        this.config = config;
        this.channelName = channelName;
        this.channelIndex = channelIndex;
        this.tracker = new Tracker(config.getMessage().getReorderWindow());
        this.limiter = new RateLimiter(rate);
        this.sizeDist = "distribution".equals(config.getMessage().getSizeMode())
                ? new SizeDistribution(config.getMessage().getSizeDistribution())
                : null;
    }

    /**
     * Legacy constructor for single-channel (channelIndex defaults to 1).
     */
    protected BaseWorker(String pattern, BurninConfig config, String channelName, double rate) {
        this(pattern, config, channelName, rate, 1);
    }

    // --- Getters ---

    public String getPattern() { return pattern; }
    public BurninConfig getConfig() { return config; }
    public String getChannelName() { return channelName; }
    public int getChannelIndex() { return channelIndex; }
    public Tracker getTracker() { return tracker; }
    public LatencyAccumulator getLatencyAccum() { return latencyAccum; }
    public LatencyAccumulator getRpcLatencyAccum() { return rpcLatencyAccum; }
    public PeakRateTracker getPeakRate() { return peakRate; }
    public SlidingRateTracker getSlidingRate() { return slidingRate; }
    public TimestampStore getTsStore() { return tsStore; }
    public ConcurrentHashMap<String, AtomicLong> getConsumerCounts() { return consumerCounts; }
    public ConcurrentHashMap<String, ProducerStat> getProducerStats() { return producerStats; }
    public ConcurrentHashMap<String, ConsumerStat> getConsumerStats() { return consumerStats; }
    public ConcurrentHashMap<String, SenderStat> getSenderStats() { return senderStats; }
    public ConcurrentHashMap<String, ResponderStat> getResponderStats() { return responderStats; }

    public long getSent() { return sent.get(); }
    public long getReceived() { return received.get(); }
    public long getCorrupted() { return corrupted.get(); }
    public long getErrors() { return errors.get(); }
    public long getReconnections() { return reconnections.get(); }
    public long getRpcSuccess() { return rpcSuccess.get(); }
    public long getRpcTimeout() { return rpcTimeout.get(); }
    public long getRpcError() { return rpcError.get(); }
    public long getBytesSent() { return bytesSent.get(); }
    public long getBytesReceived() { return bytesReceived.get(); }

    /**
     * Set the pattern-level shared latency accumulators for dual-write.
     */
    public void setPatternLatencyAccum(LatencyAccumulator accum) {
        this.patternLatencyAccum = accum;
    }

    public void setPatternRpcLatencyAccum(LatencyAccumulator accum) {
        this.patternRpcLatencyAccum = accum;
    }

    /**
     * Get the next message size based on configuration.
     */
    public int messageSize() {
        return sizeDist != null ? sizeDist.selectSize() : config.getMessage().getSizeBytes();
    }

    /**
     * Check if backpressure should be applied. Returns true if producer should pause.
     * Logs a warning once when entering backpressure state.
     */
    public boolean backpressureCheck() {
        long lag = sent.get() - received.get();
        boolean active = lag > config.getQueue().getMaxDepth();
        if (active && !backpressureLogged) {
            System.err.println("WARNING: " + pattern + " producer paused -- consumer lag "
                    + lag + " exceeds max_depth " + config.getQueue().getMaxDepth());
            backpressureLogged = true;
        }
        if (!active) {
            backpressureLogged = false;
        }
        return active;
    }

    /**
     * Wait for rate limiter. Returns false if interrupted/stopped.
     */
    public boolean waitForRate() {
        return limiter.waitForRate(producerStop);
    }

    /**
     * Record a successful send. Call only after the SDK confirms success.
     */
    public void recordSend(String producerId, long seq, int byteCount) {
        sent.incrementAndGet();
        bytesSent.addAndGet(byteCount);
        tsStore.store(producerId, seq);
        peakRate.record();
        slidingRate.record();
        Metrics.incSent(pattern, producerId, byteCount);

        ProducerStat ps = producerStats.computeIfAbsent(producerId, ProducerStat::new);
        ps.sent.incrementAndGet();
        ps.rate.record();
    }

    /**
     * Record a received message with CRC verification, sequence tracking, and latency measurement.
     * Dual-writes latency to both per-channel AND pattern-level accumulators.
     */
    public void recordReceive(String consumerId, byte[] body, String crcTag,
                              String producerId, long seq) {
        received.incrementAndGet();
        bytesReceived.addAndGet(body.length);
        consumerCounts.computeIfAbsent(consumerId, k -> new AtomicLong()).incrementAndGet();
        Metrics.incReceived(pattern, consumerId, body.length);

        ConsumerStat cs = consumerStats.computeIfAbsent(consumerId, ConsumerStat::new);
        cs.received.incrementAndGet();

        // CRC verification
        if (!Payload.verifyCrc(body, crcTag)) {
            corrupted.incrementAndGet();
            cs.corrupted.incrementAndGet();
            Metrics.incCorrupted(pattern);
            return;
        }

        // Sequence tracking
        RecordResult result = tracker.record(producerId, seq);
        if (result.isDuplicate) {
            Metrics.incDuplicated(pattern);
            if (recentlyReconnected) {
                Metrics.incReconnDuplicates(pattern);
                if (reconnDupCooldown.incrementAndGet() >= 100) {
                    recentlyReconnected = false;
                    reconnDupCooldown.set(0);
                }
            }
            return;
        }
        if (result.isOutOfOrder) {
            Metrics.incOutOfOrder(pattern);
        }

        // Latency measurement -- dual-write to per-channel AND pattern-level accumulators
        Long sendTime = tsStore.loadAndDelete(producerId, seq);
        if (sendTime != null) {
            double latency = TimestampStore.elapsedSeconds(sendTime);
            latencyAccum.record(latency);
            // Pattern-level dual-write
            LatencyAccumulator patternAccum = this.patternLatencyAccum;
            if (patternAccum != null) {
                patternAccum.record(latency);
            }
            cs.latency.record(latency);
            ProducerStat ps = producerStats.get(producerId);
            if (ps != null) ps.latency.record(latency);
            Metrics.observeLatency(pattern, latency);
        }
    }

    /**
     * Record an error with the given type.
     */
    public void recordError(String errorType) {
        errors.incrementAndGet();
        Metrics.incError(pattern, errorType);
    }

    /**
     * Increment the reconnection counter with duplicate cooldown tracking.
     */
    public void incReconnection() {
        reconnections.incrementAndGet();
        recentlyReconnected = true;
        reconnDupCooldown.set(0);
        Metrics.incReconnections(pattern);
    }

    public void incRpcSuccess(String senderId) {
        rpcSuccess.incrementAndGet();
        Metrics.incRpcResponse(pattern, "success");
        SenderStat ss = senderStats.computeIfAbsent(senderId, SenderStat::new);
        ss.responsesSuccess.incrementAndGet();
    }

    public void incRpcTimeout(String senderId) {
        rpcTimeout.incrementAndGet();
        Metrics.incRpcResponse(pattern, "timeout");
        SenderStat ss = senderStats.computeIfAbsent(senderId, SenderStat::new);
        ss.responsesTimeout.incrementAndGet();
    }

    public void incRpcError(String senderId) {
        rpcError.incrementAndGet();
        Metrics.incRpcResponse(pattern, "error");
        SenderStat ss = senderStats.computeIfAbsent(senderId, SenderStat::new);
        ss.responsesError.incrementAndGet();
    }

    public void recordRpcSend(String senderId, long seq, int byteCount) {
        sent.incrementAndGet();
        bytesSent.addAndGet(byteCount);
        tsStore.store(senderId, seq);
        peakRate.record();
        slidingRate.record();
        Metrics.incSent(pattern, senderId, byteCount);

        SenderStat ss = senderStats.computeIfAbsent(senderId, SenderStat::new);
        ss.sent.incrementAndGet();
        ss.rate.record();
    }

    public void recordRpcLatency(String senderId, double durationSec) {
        rpcLatencyAccum.record(durationSec);
        // Pattern-level dual-write for RPC latency
        LatencyAccumulator patternRpc = this.patternRpcLatencyAccum;
        if (patternRpc != null) {
            patternRpc.record(durationSec);
        }
        SenderStat ss = senderStats.computeIfAbsent(senderId, SenderStat::new);
        ss.latency.record(durationSec);
    }

    public void recordRespond(String responderId) {
        ResponderStat rs = responderStats.computeIfAbsent(responderId, ResponderStat::new);
        rs.responded.incrementAndGet();
    }

    public void recordResponderError(String responderId) {
        errors.incrementAndGet();
        Metrics.incError(pattern, "response_send_failure");
        ResponderStat rs = responderStats.computeIfAbsent(responderId, ResponderStat::new);
        rs.errors.incrementAndGet();
    }

    public void recordSenderError(String senderId, String errorType) {
        errors.incrementAndGet();
        Metrics.incError(pattern, errorType);
        SenderStat ss = senderStats.computeIfAbsent(senderId, SenderStat::new);
        ss.errors.incrementAndGet();
    }

    /**
     * Mark the start of a downtime period.
     */
    public void startDowntime() {
        synchronized (downtimeLock) {
            if (downtimeStartMs == 0) {
                downtimeStartMs = System.currentTimeMillis();
            }
        }
    }

    /**
     * Mark the end of a downtime period, accumulating elapsed time.
     */
    public void stopDowntime() {
        synchronized (downtimeLock) {
            if (downtimeStartMs != 0) {
                downtimeTotal += (System.currentTimeMillis() - downtimeStartMs) / 1000.0;
                downtimeStartMs = 0;
            }
        }
    }

    /**
     * Get total downtime in seconds (including any currently active downtime).
     */
    public double getDowntimeSeconds() {
        synchronized (downtimeLock) {
            double t = downtimeTotal;
            if (downtimeStartMs != 0) {
                t += (System.currentTimeMillis() - downtimeStartMs) / 1000.0;
            }
            return t;
        }
    }

    // --- Lifecycle ---

    /**
     * Start all consumer tasks/subscriptions. Called before producers.
     */
    public abstract void startConsumers(PubSubClient pubSubClient, QueuesClient queuesClient, CQClient cqClient);

    /**
     * Start all producer tasks. Called after consumers are running.
     */
    public abstract void startProducers(PubSubClient pubSubClient, QueuesClient queuesClient, CQClient cqClient);

    /**
     * Stop all producers (phase 1 of shutdown).
     */
    public void stopProducers() {
        producerStop.set(true);
    }

    /**
     * Stop all consumers (phase 2 of shutdown).
     */
    public void stopConsumers() {
        consumerStop.set(true);
    }

    /**
     * Reset all counters and tracking after warmup period.
     */
    public void resetAfterWarmup() {
        sent.set(0);
        received.set(0);
        corrupted.set(0);
        errors.set(0);
        reconnections.set(0);
        rpcSuccess.set(0);
        rpcTimeout.set(0);
        rpcError.set(0);
        bytesSent.set(0);
        bytesReceived.set(0);

        synchronized (downtimeLock) {
            downtimeTotal = 0;
            downtimeStartMs = 0;
        }

        tracker.reset();
        latencyAccum.reset();
        rpcLatencyAccum.reset();
        peakRate.reset();
        slidingRate.reset();
        tsStore.purge(0);
        consumerCounts.clear();
        producerStats.clear();
        consumerStats.clear();
        senderStats.clear();
        responderStats.clear();
    }

    @Override
    public void close() {
        // RateLimiter has no resources to close; just mark stopped.
        producerStop.set(true);
        consumerStop.set(true);
    }
}
