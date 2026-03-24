// All 26 Prometheus metrics + helper functions.
// Metric names and labels match spec Section 7.1 exactly.
// Uses Micrometer PrometheusMeterRegistry.

package io.kubemq.burnin;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central metrics registry with all 26 Prometheus metrics and helper methods.
 * SDK label = "java" for the Java implementation.
 */
public final class Metrics {

    private Metrics() {}

    private static final String SDK = "java";

    private static final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    // --- SLO bucket definitions ---

    private static final double[] LATENCY_SLO = {
        0.0005, 0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5
    };

    private static final double[] RPC_SLO = {
        0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5
    };

    // --- Gauge backing values (AtomicLong for thread-safety) ---

    private static final ConcurrentHashMap<String, AtomicLong> gaugeValues = new ConcurrentHashMap<>();

    static {
        // Register JVM metrics (process metrics NOT disabled).
        new JvmMemoryMetrics().bindTo(registry);
        new JvmGcMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new ClassLoaderMetrics().bindTo(registry);
    }

    /**
     * Get the Prometheus registry for scraping.
     */
    public static PrometheusMeterRegistry getRegistry() {
        return registry;
    }

    public static String scrape() {
        return registry.scrape();
    }

    public static void preInitialize() {
        for (String pattern : AllPatterns.NAMES) {
            setTargetRate(pattern, 0);
            setActualRate(pattern, 0);
            setConsumerLag(pattern, 0);
            setActiveConnections(pattern, 0);
            setGroupBalance(pattern, 1.0);
        }
        setUptime(0);
        setWarmupActive(0);
        setActiveWorkers(0);
    }

    /**
     * Clear all non-JVM meters from the registry between runs to prevent
     * unbounded accumulation of counter/histogram objects across test iterations.
     * JVM metrics (memory, GC, threads) are re-bound after clearing.
     */
    public static void clearRunMetrics() {
        registry.clear();
        gaugeValues.clear();
        // Re-bind JVM metrics after clearing
        new JvmMemoryMetrics().bindTo(registry);
        new JvmGcMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new ClassLoaderMetrics().bindTo(registry);
    }

    // ── Counter helpers (15 counters) ─────────────────────────────────────

    private static Counter counter(String name, String help, String... tagKVs) {
        return Counter.builder(name).description(help).tags(tagKVs).register(registry);
    }

    public static void incSent(String pattern, String producerId, int bytes) {
        counter("burnin_messages_sent_total", "Total messages sent",
                "sdk", SDK, "pattern", pattern, "producer_id", producerId).increment();
        if (bytes > 0) {
            counter("burnin_bytes_sent_total", "Bytes sent",
                    "sdk", SDK, "pattern", pattern).increment(bytes);
        }
    }

    public static void incSent(String pattern, String producerId) {
        incSent(pattern, producerId, 0);
    }

    public static void incReceived(String pattern, String consumerId, int bytes) {
        counter("burnin_messages_received_total", "Total messages received",
                "sdk", SDK, "pattern", pattern, "consumer_id", consumerId).increment();
        if (bytes > 0) {
            counter("burnin_bytes_received_total", "Bytes received",
                    "sdk", SDK, "pattern", pattern).increment(bytes);
        }
    }

    public static void incReceived(String pattern, String consumerId) {
        incReceived(pattern, consumerId, 0);
    }

    public static void incLost(String pattern, long count) {
        counter("burnin_messages_lost_total", "Confirmed lost messages",
                "sdk", SDK, "pattern", pattern).increment(count);
    }

    public static void incLost(String pattern) {
        incLost(pattern, 1);
    }

    public static void incDuplicated(String pattern) {
        counter("burnin_messages_duplicated_total", "Duplicate messages",
                "sdk", SDK, "pattern", pattern).increment();
    }

    public static void incCorrupted(String pattern) {
        counter("burnin_messages_corrupted_total", "Corrupted messages",
                "sdk", SDK, "pattern", pattern).increment();
    }

    public static void incOutOfOrder(String pattern) {
        counter("burnin_messages_out_of_order_total", "Out-of-order messages",
                "sdk", SDK, "pattern", pattern).increment();
    }

    public static void incUnconfirmed(String pattern) {
        counter("burnin_messages_unconfirmed_total", "Unconfirmed messages",
                "sdk", SDK, "pattern", pattern).increment();
    }

    public static void incReconnDuplicates(String pattern) {
        counter("burnin_reconnection_duplicates_total", "Post-reconnection duplicates",
                "sdk", SDK, "pattern", pattern).increment();
    }

    public static void incError(String pattern, String errorType) {
        counter("burnin_errors_total", "Errors by type",
                "sdk", SDK, "pattern", pattern, "error_type", errorType).increment();
    }

    public static void incReconnections(String pattern) {
        counter("burnin_reconnections_total", "Reconnections",
                "sdk", SDK, "pattern", pattern).increment();
    }

    public static void incRpcResponse(String pattern, String status) {
        counter("burnin_rpc_responses_total", "RPC responses by status",
                "sdk", SDK, "pattern", pattern, "status", status).increment();
    }

    public static void addDowntime(String pattern, double seconds) {
        if (seconds > 0) {
            counter("burnin_downtime_seconds_total", "Downtime seconds",
                    "sdk", SDK, "pattern", pattern).increment(seconds);
        }
    }

    public static void incForcedDisconnects() {
        counter("burnin_forced_disconnects_total", "Forced disconnects",
                "sdk", SDK).increment();
    }

    // ── DistributionSummary (histogram) helpers (3 histograms) ────────────

    private static DistributionSummary distributionSummary(String name, String help,
                                                           double[] slo, String... tagKVs) {
        DistributionSummary.Builder builder = DistributionSummary.builder(name)
                .description(help)
                .tags(tagKVs)
                .serviceLevelObjectives(slo);
        // Note: publishPercentileHistogram(true) was removed to prevent
        // unbounded memory growth from HDR time-decay histograms per label set.
        // The explicit SLO buckets provide sufficient Prometheus histogram data.
        return builder.register(registry);
    }

    public static void observeLatency(String pattern, double seconds) {
        distributionSummary("burnin_message_latency_seconds", "E2E message latency",
                LATENCY_SLO, "sdk", SDK, "pattern", pattern).record(seconds);
    }

    public static void observeSendDuration(String pattern, double seconds) {
        distributionSummary("burnin_send_duration_seconds", "Send duration",
                LATENCY_SLO, "sdk", SDK, "pattern", pattern).record(seconds);
    }

    public static void observeRpcDuration(String pattern, double seconds) {
        distributionSummary("burnin_rpc_duration_seconds", "RPC round-trip",
                RPC_SLO, "sdk", SDK, "pattern", pattern).record(seconds);
    }

    // ── Gauge helpers (8 gauges) ──────────────────────────────────────────

    private static AtomicLong getOrCreateGaugeValue(String name, String help, String... tagKVs) {
        // Build a unique key for this gauge instance based on name and tags.
        StringBuilder keyBuilder = new StringBuilder(name);
        for (String tv : tagKVs) {
            keyBuilder.append('.').append(tv);
        }
        String key = keyBuilder.toString();

        return gaugeValues.computeIfAbsent(key, k -> {
            AtomicLong value = new AtomicLong(0);
            Gauge.builder(name, value, v -> Double.longBitsToDouble(v.get()))
                    .description(help)
                    .tags(tagKVs)
                    .register(registry);
            return value;
        });
    }

    private static void setGauge(String name, String help, double val, String... tagKVs) {
        AtomicLong holder = getOrCreateGaugeValue(name, help, tagKVs);
        holder.set(Double.doubleToLongBits(val));
    }

    public static void setActiveConnections(String pattern, double count) {
        setGauge("burnin_active_connections", "Active connections", count,
                "sdk", SDK, "pattern", pattern);
    }

    public static void setUptime(double seconds) {
        setGauge("burnin_uptime_seconds", "Uptime seconds", seconds,
                "sdk", SDK);
    }

    public static void setTargetRate(String pattern, double rate) {
        setGauge("burnin_target_rate", "Target rate", rate,
                "sdk", SDK, "pattern", pattern);
    }

    public static void setActualRate(String pattern, double rate) {
        setGauge("burnin_actual_rate", "Actual rate", rate,
                "sdk", SDK, "pattern", pattern);
    }

    public static void setConsumerLag(String pattern, double lag) {
        setGauge("burnin_consumer_lag_messages", "Consumer lag", lag,
                "sdk", SDK, "pattern", pattern);
    }

    public static void setGroupBalance(String pattern, double ratio) {
        setGauge("burnin_consumer_group_balance_ratio", "Group balance ratio", ratio,
                "sdk", SDK, "pattern", pattern);
    }

    public static void setWarmupActive(double val) {
        setGauge("burnin_warmup_active", "Warmup active", val,
                "sdk", SDK);
    }

    public static void setActiveWorkers(double count) {
        setGauge("burnin_active_workers", "Active workers", count,
                "sdk", SDK);
    }
}
