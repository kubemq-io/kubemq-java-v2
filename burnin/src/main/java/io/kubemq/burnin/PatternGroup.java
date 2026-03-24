// PatternGroup: holds N BaseWorkers (one per channel) for a single pattern.
// Owns shared thread-safe LatencyAccumulator for pattern-level latency.
// Provides aggregation methods for report and verdict.

package io.kubemq.burnin;

import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.cq.CQClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Coordinator for N ChannelWorkers (BaseWorker instances) within a single pattern.
 * Each channel has its own independent tracker, rate limiter, and per-channel latency.
 * The PatternGroup owns a shared pattern-level LatencyAccumulator that receives
 * dual-writes from all channels concurrently (thread-safe via synchronized in LatencyAccumulator).
 */
public final class PatternGroup {

    private static final String SDK = "java";

    private final String pattern;
    private final PatternConfig patternConfig;
    private final BurninConfig config;
    private final String runId;
    private final List<BaseWorker> workers;
    private final LatencyAccumulator patternLatencyAccum = new LatencyAccumulator();
    private final LatencyAccumulator patternRpcLatencyAccum = new LatencyAccumulator();

    /**
     * Create a PatternGroup with N workers based on PatternConfig.channels.
     * Channel naming: java_burnin_{runId}_{pattern}_{4-digit-index}
     */
    public PatternGroup(String pattern, PatternConfig patternConfig, BurninConfig config, String runId) {
        this.pattern = pattern;
        this.patternConfig = patternConfig;
        this.config = config;
        this.runId = runId;

        int nChannels = patternConfig.getChannels();
        List<BaseWorker> workerList = new ArrayList<>(nChannels);

        for (int i = 1; i <= nChannels; i++) {
            String channelName = SDK + "_burnin_" + runId + "_" + pattern + "_" + String.format("%04d", i);
            int rate = patternConfig.getRate();
            BaseWorker worker = createWorker(pattern, config, runId, channelName, i, patternConfig, rate);
            // Set shared pattern-level latency accumulators for dual-write
            worker.setPatternLatencyAccum(patternLatencyAccum);
            worker.setPatternRpcLatencyAccum(patternRpcLatencyAccum);
            workerList.add(worker);
        }

        this.workers = Collections.unmodifiableList(workerList);
    }

    private static BaseWorker createWorker(String pattern, BurninConfig config, String runId,
                                            String channelName, int channelIndex,
                                            PatternConfig pc, int rate) {
        switch (pattern) {
            case "events":
                return new EventsWorker(config, runId, channelName, channelIndex,
                        pc.getProducersPerChannel(), pc.getConsumersPerChannel(),
                        pc.isConsumerGroup(), rate);
            case "events_store":
                return new EventsStoreWorker(config, runId, channelName, channelIndex,
                        pc.getProducersPerChannel(), pc.getConsumersPerChannel(),
                        pc.isConsumerGroup(), rate);
            case "queue_stream":
                return new QueueStreamWorker(config, runId, channelName, channelIndex,
                        pc.getProducersPerChannel(), pc.getConsumersPerChannel(), rate);
            case "queue_simple":
                return new QueueSimpleWorker(config, runId, channelName, channelIndex,
                        pc.getProducersPerChannel(), pc.getConsumersPerChannel(), rate);
            case "commands":
                return new CommandsWorker(config, runId, channelName, channelIndex,
                        pc.getSendersPerChannel(), pc.getRespondersPerChannel(), rate);
            case "queries":
                return new QueriesWorker(config, runId, channelName, channelIndex,
                        pc.getSendersPerChannel(), pc.getRespondersPerChannel(), rate);
            default:
                throw new IllegalArgumentException("Unknown pattern: " + pattern);
        }
    }

    // --- Getters ---

    public String getPattern() { return pattern; }
    public PatternConfig getPatternConfig() { return patternConfig; }
    public List<BaseWorker> getWorkers() { return workers; }
    public int getChannelCount() { return workers.size(); }
    public LatencyAccumulator getPatternLatencyAccum() { return patternLatencyAccum; }
    public LatencyAccumulator getPatternRpcLatencyAccum() { return patternRpcLatencyAccum; }

    /**
     * Get all channel names for this pattern.
     */
    public List<String> getChannelNames() {
        List<String> names = new ArrayList<>(workers.size());
        for (BaseWorker w : workers) {
            names.add(w.getChannelName());
        }
        return names;
    }

    // --- Lifecycle ---

    public void startConsumers(PubSubClient pubSubClient, QueuesClient queuesClient, CQClient cqClient) {
        for (BaseWorker w : workers) {
            w.startConsumers(pubSubClient, queuesClient, cqClient);
        }
    }

    public void startProducers(PubSubClient pubSubClient, QueuesClient queuesClient, CQClient cqClient) {
        for (BaseWorker w : workers) {
            w.startProducers(pubSubClient, queuesClient, cqClient);
        }
    }

    public void stopProducers() {
        for (BaseWorker w : workers) {
            w.stopProducers();
        }
    }

    public void stopConsumers() {
        for (BaseWorker w : workers) {
            w.stopConsumers();
        }
    }

    public void resetAfterWarmup() {
        for (BaseWorker w : workers) {
            w.resetAfterWarmup();
        }
        patternLatencyAccum.reset();
        patternRpcLatencyAccum.reset();
    }

    public void close() {
        for (BaseWorker w : workers) {
            try { w.close(); } catch (Exception e) { /* best effort */ }
        }
    }

    // --- Aggregation methods ---

    public long totalSent() {
        long total = 0;
        for (BaseWorker w : workers) total += w.getSent();
        return total;
    }

    public long totalReceived() {
        long total = 0;
        for (BaseWorker w : workers) total += w.getReceived();
        return total;
    }

    public long totalLost() {
        long total = 0;
        for (BaseWorker w : workers) total += w.getTracker().totalLost();
        return total;
    }

    public long totalDuplicated() {
        long total = 0;
        for (BaseWorker w : workers) total += w.getTracker().totalDuplicates();
        return total;
    }

    public long totalOutOfOrder() {
        long total = 0;
        for (BaseWorker w : workers) total += w.getTracker().totalOutOfOrder();
        return total;
    }

    public long totalCorrupted() {
        long total = 0;
        for (BaseWorker w : workers) total += w.getCorrupted();
        return total;
    }

    public long totalErrors() {
        long total = 0;
        for (BaseWorker w : workers) total += w.getErrors();
        return total;
    }

    public long totalBytesSent() {
        long total = 0;
        for (BaseWorker w : workers) total += w.getBytesSent();
        return total;
    }

    public long totalBytesReceived() {
        long total = 0;
        for (BaseWorker w : workers) total += w.getBytesReceived();
        return total;
    }

    /**
     * Total reconnections: connection-level, not channel-level.
     * Since all channels share a gRPC client, we take max (they should all be roughly equal).
     */
    public long totalReconnections() {
        long max = 0;
        for (BaseWorker w : workers) max = Math.max(max, w.getReconnections());
        return max;
    }

    /**
     * Max downtime across channels (shared connection => equal, but take max to be safe).
     */
    public double maxDowntimeSeconds() {
        double max = 0;
        for (BaseWorker w : workers) max = Math.max(max, w.getDowntimeSeconds());
        return max;
    }

    public long totalRpcSuccess() {
        long total = 0;
        for (BaseWorker w : workers) total += w.getRpcSuccess();
        return total;
    }

    public long totalRpcTimeout() {
        long total = 0;
        for (BaseWorker w : workers) total += w.getRpcTimeout();
        return total;
    }

    public long totalRpcError() {
        long total = 0;
        for (BaseWorker w : workers) total += w.getRpcError();
        return total;
    }

    public double maxPeakRate() {
        double max = 0;
        for (BaseWorker w : workers) max = Math.max(max, w.getPeakRate().getPeak());
        return max;
    }

    public double aggregateSlidingRate() {
        double total = 0;
        for (BaseWorker w : workers) total += w.getSlidingRate().getRate();
        return total;
    }

    /**
     * Total unconfirmed (events_store only).
     */
    public long totalUnconfirmed() {
        long total = 0;
        for (BaseWorker w : workers) {
            if (w instanceof EventsStoreWorker) {
                total += ((EventsStoreWorker) w).getUnconfirmed();
            }
        }
        return total;
    }

    // --- Downtime/reconnection propagation for forced disconnects ---

    public void startDowntime() {
        for (BaseWorker w : workers) w.startDowntime();
    }

    public void stopDowntime() {
        for (BaseWorker w : workers) w.stopDowntime();
    }

    /**
     * Increment reconnection counter once for the pattern (connection-level).
     * Only increments first worker to avoid N-times counting since all workers share the connection.
     */
    public void incReconnection() {
        if (!workers.isEmpty()) {
            // Increment all workers since they each track their own reconnection state for dup cooldown
            for (BaseWorker w : workers) {
                w.incReconnection();
            }
        }
    }
}
