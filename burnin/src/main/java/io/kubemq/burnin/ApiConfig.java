package io.kubemq.burnin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.security.SecureRandom;
import java.util.*;

// ─── v2 API request models ───────────────────────────────────────────────────

class ApiPatternThresholds {
    Double maxLossPct;
    Double maxP99LatencyMs;
    Double maxP999LatencyMs;
    Double maxDuplicationPct;
    Double maxErrorRatePct;
    Double minThroughputPct;
    Double maxMemoryGrowthFactor;
    Double maxDowntimePct;
}

class ApiPatternConfig {
    Boolean enabled;
    Integer channels;
    Integer producersPerChannel;
    Integer consumersPerChannel;
    Boolean consumerGroup;
    Integer sendersPerChannel;
    Integer respondersPerChannel;
    Integer rate;
    ApiPatternThresholds thresholds;
}

class ApiQueueConfig {
    Integer pollMaxMessages;
    Integer pollWaitTimeoutSeconds;
    Boolean autoAck;
    Integer maxDepth;
    Integer visibilitySeconds;
}

class ApiRpcConfig {
    Integer timeoutMs;
}

class ApiMessageConfig {
    String sizeMode;
    Integer sizeBytes;
    String sizeDistribution;
    Integer reorderWindow;
}

class ApiGlobalThresholds {
    Double maxLossPct;
    Double maxEventsLossPct;
    Double maxDuplicationPct;
    Double maxErrorRatePct;
    Double maxMemoryGrowthFactor;
    Double maxDowntimePct;
    Double minThroughputPct;
    Double maxP99LatencyMs;
    Double maxP999LatencyMs;
    String maxDuration;
}

class ApiForcedDisconnectConfig {
    String interval;
    String duration;
}

class ApiShutdownConfig {
    Integer drainTimeoutSeconds;
    Boolean cleanupChannels;
}

class ApiMetricsConfig {
    String reportInterval;
}

class ApiWarmupConfig {
    Integer maxParallelChannels;
    Integer timeoutPerChannelMs;
    String warmupDuration;
}

class ApiBrokerOverride {
    String address;
}

class ApiRunConfig {
    ApiBrokerOverride broker;
    String mode;
    String duration;
    String runId;
    Integer startingTimeoutSeconds;
    Map<String, ApiPatternConfig> patterns;
    ApiWarmupConfig warmup;
    ApiQueueConfig queue;
    ApiRpcConfig rpc;
    ApiMessageConfig message;
    ApiGlobalThresholds thresholds;
    ApiForcedDisconnectConfig forcedDisconnect;
    ApiShutdownConfig shutdown;
    ApiMetricsConfig metrics;
}

class PatternThreshold {
    double maxLossPct;
    double maxP99LatencyMs = 1000;
    double maxP999LatencyMs = 5000;
}

public final class ApiConfig {

    private ApiConfig() {}

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    static final Map<String, Integer> DEFAULT_RATES = new LinkedHashMap<>();
    static final Map<String, Double> DEFAULT_LOSS_PCT = new LinkedHashMap<>();

    static {
        DEFAULT_RATES.put("events", 100);
        DEFAULT_RATES.put("events_store", 100);
        DEFAULT_RATES.put("queue_stream", 50);
        DEFAULT_RATES.put("queue_simple", 50);
        DEFAULT_RATES.put("commands", 100);
        DEFAULT_RATES.put("queries", 100);

        DEFAULT_LOSS_PCT.put("events", 5.0);
        DEFAULT_LOSS_PCT.put("events_store", 0.0);
        DEFAULT_LOSS_PCT.put("queue_stream", 0.0);
        DEFAULT_LOSS_PCT.put("queue_simple", 0.0);
    }

    public static ApiRunConfig parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ApiRunConfig();
        }
        return GSON.fromJson(json, ApiRunConfig.class);
    }

    /**
     * Detect v1 config format in the raw JSON string.
     * Returns list of error messages if v1 format detected; empty list if OK.
     */
    public static List<String> detectV1Format(String json) {
        if (json == null || json.trim().isEmpty()) return Collections.emptyList();
        List<String> errors = new ArrayList<>();

        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            // Layer 1: top-level keys
            if (root.has("concurrency") || root.has("rates")) {
                errors.add("v1 config format not supported. Update to v2 patterns format.");
                return errors;
            }

            // Layer 2: old field names in patterns block
            if (root.has("patterns")) {
                JsonElement patternsEl = root.get("patterns");
                if (patternsEl.isJsonObject()) {
                    JsonObject patternsObj = patternsEl.getAsJsonObject();
                    String[] v1Fields = {"producers", "consumers", "senders", "responders"};
                    for (Map.Entry<String, JsonElement> entry : patternsObj.entrySet()) {
                        String patternName = entry.getKey();
                        JsonElement pcEl = entry.getValue();
                        if (pcEl.isJsonObject()) {
                            JsonObject pcObj = pcEl.getAsJsonObject();
                            for (String field : v1Fields) {
                                if (pcObj.has(field)) {
                                    errors.add("detected v1 field: patterns." + patternName + "." + field + " -- use " + field + "_per_channel");
                                }
                            }
                        }
                    }
                    if (!errors.isEmpty()) {
                        errors.add(0, "v1 config format not supported. Update to v2 patterns format.");
                    }
                }
            }
        } catch (Exception e) {
            // Not valid JSON, will be caught by parse()
        }

        return errors;
    }

    public static List<String> validate(ApiRunConfig api) {
        List<String> errors = new ArrayList<>();

        if (api.mode != null && !"soak".equals(api.mode) && !"benchmark".equals(api.mode)) {
            errors.add("mode must be 'soak' or 'benchmark', got '" + api.mode + "'");
        }

        if (api.duration != null && !api.duration.isEmpty() && !"0".equals(api.duration)) {
            if (!api.duration.matches("\\d+[smhd]")) {
                errors.add("duration must match \\d+(s|m|h|d) or be '0', got '" + api.duration + "'");
            }
        }

        if (api.startingTimeoutSeconds != null && api.startingTimeoutSeconds <= 0) {
            errors.add("starting_timeout_seconds must be > 0, got " + api.startingTimeoutSeconds);
        }

        String mode = api.mode != null ? api.mode : "soak";
        boolean anyEnabled = false;
        boolean hasPatterns = api.patterns != null && !api.patterns.isEmpty();

        if (hasPatterns) {
            for (Map.Entry<String, ApiPatternConfig> entry : api.patterns.entrySet()) {
                String name = entry.getKey();
                ApiPatternConfig pc = entry.getValue();
                if (pc == null) continue;

                boolean enabled = pc.enabled == null || pc.enabled;
                if (enabled) {
                    anyEnabled = true;

                    // channels validation
                    if (pc.channels != null && (pc.channels < 1 || pc.channels > 1000))
                        errors.add("patterns." + name + ".channels: must be 1-1000, got " + pc.channels);

                    if ("soak".equals(mode) && pc.rate != null && pc.rate < 0) {
                        errors.add("patterns." + name + ".rate: must be >= 0, got " + pc.rate);
                    }

                    if (isRpcPattern(name)) {
                        if (pc.sendersPerChannel != null && pc.sendersPerChannel < 1)
                            errors.add("patterns." + name + ".senders_per_channel: must be >= 1, got " + pc.sendersPerChannel);
                        if (pc.respondersPerChannel != null && pc.respondersPerChannel < 1)
                            errors.add("patterns." + name + ".responders_per_channel: must be >= 1, got " + pc.respondersPerChannel);
                    } else {
                        if (pc.producersPerChannel != null && pc.producersPerChannel < 1)
                            errors.add("patterns." + name + ".producers_per_channel: must be >= 1, got " + pc.producersPerChannel);
                        if (pc.consumersPerChannel != null && pc.consumersPerChannel < 1)
                            errors.add("patterns." + name + ".consumers_per_channel: must be >= 1, got " + pc.consumersPerChannel);
                    }

                    if (pc.thresholds != null) {
                        if (pc.thresholds.maxLossPct != null && (pc.thresholds.maxLossPct < 0 || pc.thresholds.maxLossPct > 100))
                            errors.add("patterns." + name + ".thresholds.max_loss_pct: must be 0-100, got " + pc.thresholds.maxLossPct);
                        if (pc.thresholds.maxP99LatencyMs != null && pc.thresholds.maxP99LatencyMs <= 0)
                            errors.add("patterns." + name + ".thresholds.max_p99_latency_ms: must be > 0, got " + pc.thresholds.maxP99LatencyMs);
                        if (pc.thresholds.maxP999LatencyMs != null && pc.thresholds.maxP999LatencyMs <= 0)
                            errors.add("patterns." + name + ".thresholds.max_p999_latency_ms: must be > 0, got " + pc.thresholds.maxP999LatencyMs);
                    }
                }
            }

            if (!anyEnabled) {
                errors.add("no patterns enabled");
            }
        } else {
            anyEnabled = true;
        }

        if (api.message != null) {
            if (api.message.sizeBytes != null && api.message.sizeBytes < 64)
                errors.add("message.size_bytes: must be >= 64, got " + api.message.sizeBytes);
            if (api.message.reorderWindow != null && api.message.reorderWindow < 100)
                errors.add("message.reorder_window: must be >= 100, got " + api.message.reorderWindow);
        }

        if (api.thresholds != null) {
            if (api.thresholds.maxDuplicationPct != null && (api.thresholds.maxDuplicationPct < 0 || api.thresholds.maxDuplicationPct > 100))
                errors.add("thresholds.max_duplication_pct: must be 0-100, got " + api.thresholds.maxDuplicationPct);
            if (api.thresholds.maxErrorRatePct != null && (api.thresholds.maxErrorRatePct < 0 || api.thresholds.maxErrorRatePct > 100))
                errors.add("thresholds.max_error_rate_pct: must be 0-100, got " + api.thresholds.maxErrorRatePct);
            if (api.thresholds.maxDowntimePct != null && (api.thresholds.maxDowntimePct < 0 || api.thresholds.maxDowntimePct > 100))
                errors.add("thresholds.max_downtime_pct: must be 0-100, got " + api.thresholds.maxDowntimePct);
            if (api.thresholds.minThroughputPct != null && (api.thresholds.minThroughputPct < 0 || api.thresholds.minThroughputPct > 100))
                errors.add("thresholds.min_throughput_pct: must be 0-100, got " + api.thresholds.minThroughputPct);
            if (api.thresholds.maxMemoryGrowthFactor != null && api.thresholds.maxMemoryGrowthFactor < 1.0)
                errors.add("thresholds.max_memory_growth_factor: must be >= 1.0, got " + api.thresholds.maxMemoryGrowthFactor);
        }

        if (api.shutdown != null) {
            if (api.shutdown.drainTimeoutSeconds != null && api.shutdown.drainTimeoutSeconds <= 0)
                errors.add("shutdown.drain_timeout_seconds: must be > 0, got " + api.shutdown.drainTimeoutSeconds);
        }

        return errors;
    }

    /**
     * Translate v2 API config into BurninConfig.
     */
    public static BurninConfig translate(ApiRunConfig api, BurninConfig startup) {
        BurninConfig cfg = new BurninConfig();

        // Broker: default from startup, allow API override
        BrokerConfig broker = new BrokerConfig();
        broker.setAddress(
            api.broker != null && api.broker.address != null && !api.broker.address.isEmpty()
                ? api.broker.address : startup.getBroker().getAddress());
        broker.setClientIdPrefix(startup.getBroker().getClientIdPrefix());
        cfg.setBroker(broker);
        cfg.setRecovery(startup.getRecovery());
        cfg.setOutput(startup.getOutput());
        cfg.setLogging(startup.getLogging());
        cfg.setCors(startup.getCors());

        MetricsConfig metrics = new MetricsConfig();
        metrics.setPort(startup.getMetrics().getPort());
        metrics.setReportInterval(api.metrics != null && api.metrics.reportInterval != null
                ? api.metrics.reportInterval : "30s");
        cfg.setMetrics(metrics);

        cfg.setMode(api.mode != null ? api.mode : "soak");
        cfg.setDuration(api.duration != null && !api.duration.isEmpty() ? api.duration : "1h");

        String runId = api.runId;
        if (runId == null || runId.isEmpty()) {
            byte[] bytes = new byte[4];
            new SecureRandom().nextBytes(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format("%02x", b & 0xff));
            runId = hex.toString();
        }
        cfg.setRunId(runId);

        cfg.setStartingTimeoutSeconds(
                api.startingTimeoutSeconds != null ? api.startingTimeoutSeconds : 60);

        // Warmup config
        WarmupConfig warmup = new WarmupConfig();
        if (api.warmup != null) {
            if (api.warmup.maxParallelChannels != null) warmup.setMaxParallelChannels(api.warmup.maxParallelChannels);
            if (api.warmup.timeoutPerChannelMs != null) warmup.setTimeoutPerChannelMs(api.warmup.timeoutPerChannelMs);
            if (api.warmup.warmupDuration != null) warmup.setWarmupDuration(api.warmup.warmupDuration);
        }
        if (warmup.getWarmupDuration() == null || warmup.getWarmupDuration().isEmpty()) {
            warmup.setWarmupDuration("benchmark".equals(cfg.getMode()) ? "60s" : "0s");
        }
        cfg.setWarmup(warmup);

        // Translate v2 patterns
        Map<String, PatternConfig> patterns = new LinkedHashMap<>();
        for (String pattern : AllPatterns.NAMES) {
            ApiPatternConfig apc = api.patterns != null ? api.patterns.get(pattern) : null;
            PatternConfig pc = new PatternConfig();

            int defaultRate = DEFAULT_RATES.getOrDefault(pattern, 100);
            pc.setRate(apc != null && apc.rate != null ? apc.rate : defaultRate);

            if (apc != null) {
                if (apc.enabled != null) pc.setEnabled(apc.enabled);
                if (apc.channels != null) pc.setChannels(apc.channels);

                if (isRpcPattern(pattern)) {
                    if (apc.sendersPerChannel != null) pc.setSendersPerChannel(apc.sendersPerChannel);
                    if (apc.respondersPerChannel != null) pc.setRespondersPerChannel(apc.respondersPerChannel);
                } else {
                    if (apc.producersPerChannel != null) pc.setProducersPerChannel(apc.producersPerChannel);
                    if (apc.consumersPerChannel != null) pc.setConsumersPerChannel(apc.consumersPerChannel);
                    if (apc.consumerGroup != null) pc.setConsumerGroup(apc.consumerGroup);
                }

                // Per-pattern thresholds
                if (apc.thresholds != null) {
                    ThresholdsConfig thr = new ThresholdsConfig();
                    // Start from global defaults, then override with per-pattern
                    thr.setMaxLossPct(DEFAULT_LOSS_PCT.getOrDefault(pattern, 0.0));

                    if (apc.thresholds.maxLossPct != null) thr.setMaxLossPct(apc.thresholds.maxLossPct);
                    if (apc.thresholds.maxP99LatencyMs != null) thr.setMaxP99LatencyMs(apc.thresholds.maxP99LatencyMs);
                    if (apc.thresholds.maxP999LatencyMs != null) thr.setMaxP999LatencyMs(apc.thresholds.maxP999LatencyMs);
                    if (apc.thresholds.maxDuplicationPct != null) thr.setMaxDuplicationPct(apc.thresholds.maxDuplicationPct);
                    if (apc.thresholds.maxErrorRatePct != null) thr.setMaxErrorRatePct(apc.thresholds.maxErrorRatePct);
                    if (apc.thresholds.minThroughputPct != null) thr.setMinThroughputPct(apc.thresholds.minThroughputPct);
                    if (apc.thresholds.maxMemoryGrowthFactor != null) thr.setMaxMemoryGrowthFactor(apc.thresholds.maxMemoryGrowthFactor);
                    if (apc.thresholds.maxDowntimePct != null) thr.setMaxDowntimePct(apc.thresholds.maxDowntimePct);
                    pc.setThresholds(thr);
                }
            }

            patterns.put(pattern, pc);
        }
        cfg.setPatterns(patterns);

        QueueConfig queue = new QueueConfig();
        if (api.queue != null) {
            if (api.queue.pollMaxMessages != null) queue.setPollMaxMessages(api.queue.pollMaxMessages);
            if (api.queue.pollWaitTimeoutSeconds != null) queue.setPollWaitTimeoutSeconds(api.queue.pollWaitTimeoutSeconds);
            if (api.queue.autoAck != null) queue.setAutoAck(api.queue.autoAck);
            if (api.queue.maxDepth != null) queue.setMaxDepth(api.queue.maxDepth);
            if (api.queue.visibilitySeconds != null) queue.setVisibilitySeconds(api.queue.visibilitySeconds);
        }
        if (api.queue == null || api.queue.visibilitySeconds == null) {
            queue.setVisibilitySeconds(startup.getQueue().getVisibilitySeconds());
        }
        cfg.setQueue(queue);

        RpcConfig rpc = new RpcConfig();
        if (api.rpc != null && api.rpc.timeoutMs != null) rpc.setTimeoutMs(api.rpc.timeoutMs);
        cfg.setRpc(rpc);

        MessageConfig message = new MessageConfig();
        if (api.message != null) {
            if (api.message.sizeMode != null) message.setSizeMode(api.message.sizeMode);
            if (api.message.sizeBytes != null) message.setSizeBytes(api.message.sizeBytes);
            if (api.message.sizeDistribution != null) message.setSizeDistribution(api.message.sizeDistribution);
            if (api.message.reorderWindow != null) message.setReorderWindow(api.message.reorderWindow);
        }
        cfg.setMessage(message);

        ThresholdsConfig thr = new ThresholdsConfig();
        if (api.thresholds != null) {
            if (api.thresholds.maxLossPct != null) thr.setMaxLossPct(api.thresholds.maxLossPct);
            if (api.thresholds.maxEventsLossPct != null) thr.setMaxEventsLossPct(api.thresholds.maxEventsLossPct);
            if (api.thresholds.maxDuplicationPct != null) thr.setMaxDuplicationPct(api.thresholds.maxDuplicationPct);
            if (api.thresholds.maxErrorRatePct != null) thr.setMaxErrorRatePct(api.thresholds.maxErrorRatePct);
            if (api.thresholds.maxMemoryGrowthFactor != null) thr.setMaxMemoryGrowthFactor(api.thresholds.maxMemoryGrowthFactor);
            if (api.thresholds.maxDowntimePct != null) thr.setMaxDowntimePct(api.thresholds.maxDowntimePct);
            if (api.thresholds.minThroughputPct != null) thr.setMinThroughputPct(api.thresholds.minThroughputPct);
            if (api.thresholds.maxP99LatencyMs != null) thr.setMaxP99LatencyMs(api.thresholds.maxP99LatencyMs);
            if (api.thresholds.maxP999LatencyMs != null) thr.setMaxP999LatencyMs(api.thresholds.maxP999LatencyMs);
            if (api.thresholds.maxDuration != null) thr.setMaxDuration(api.thresholds.maxDuration);
        }
        cfg.setThresholds(thr);

        ForcedDisconnectConfig fd = new ForcedDisconnectConfig();
        if (api.forcedDisconnect != null) {
            if (api.forcedDisconnect.interval != null) fd.setInterval(api.forcedDisconnect.interval);
            if (api.forcedDisconnect.duration != null) fd.setDuration(api.forcedDisconnect.duration);
        }
        cfg.setForcedDisconnect(fd);

        ShutdownConfig shutdown = new ShutdownConfig();
        if (api.shutdown != null) {
            if (api.shutdown.drainTimeoutSeconds != null) shutdown.setDrainTimeoutSeconds(api.shutdown.drainTimeoutSeconds);
            if (api.shutdown.cleanupChannels != null) shutdown.setCleanupChannels(api.shutdown.cleanupChannels);
        }
        cfg.setShutdown(shutdown);

        return cfg;
    }

    public static Map<String, PatternThreshold> resolvePatternThresholds(ApiRunConfig api) {
        Map<String, PatternThreshold> result = new LinkedHashMap<>();
        for (String pattern : AllPatterns.NAMES) {
            PatternThreshold pt = new PatternThreshold();
            pt.maxLossPct = DEFAULT_LOSS_PCT.getOrDefault(pattern, 0.0);
            if (api != null && api.patterns != null) {
                ApiPatternConfig pc = api.patterns.get(pattern);
                if (pc != null && pc.thresholds != null) {
                    if (pc.thresholds.maxLossPct != null) pt.maxLossPct = pc.thresholds.maxLossPct;
                    if (pc.thresholds.maxP99LatencyMs != null) pt.maxP99LatencyMs = pc.thresholds.maxP99LatencyMs;
                    if (pc.thresholds.maxP999LatencyMs != null) pt.maxP999LatencyMs = pc.thresholds.maxP999LatencyMs;
                }
            }
            result.put(pattern, pt);
        }
        return result;
    }

    private static boolean isRpcPattern(String name) {
        return "commands".equals(name) || "queries".equals(name);
    }
}
