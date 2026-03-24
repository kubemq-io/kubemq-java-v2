// Configuration with YAML loading (SnakeYAML), v2 patterns format,
// and validation (unknown keys warn, collect all errors, range checks).
// v1 config format (concurrency/rates/env vars) is rejected.

package io.kubemq.burnin;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.*;

// ─── Nested config section POJOs ───────────────────────────────────────────────

class BrokerConfig {
    private String address = "localhost:50000";
    private String clientIdPrefix = "burnin-java";

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getClientIdPrefix() { return clientIdPrefix; }
    public void setClientIdPrefix(String clientIdPrefix) { this.clientIdPrefix = clientIdPrefix; }
}

/**
 * Per-pattern configuration (v2). Each pattern has its own channels, producers/consumers, rate, etc.
 */
class PatternConfig {
    private boolean enabled = true;
    private int channels = 1;
    private int producersPerChannel = 1;
    private int consumersPerChannel = 1;
    private boolean consumerGroup = false;
    private int sendersPerChannel = 1;
    private int respondersPerChannel = 1;
    private int rate = 100;
    private ThresholdsConfig thresholds; // optional per-pattern overrides

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public int getChannels() { return channels; }
    public void setChannels(int v) { this.channels = v; }
    public int getProducersPerChannel() { return producersPerChannel; }
    public void setProducersPerChannel(int v) { this.producersPerChannel = v; }
    public int getConsumersPerChannel() { return consumersPerChannel; }
    public void setConsumersPerChannel(int v) { this.consumersPerChannel = v; }
    public boolean isConsumerGroup() { return consumerGroup; }
    public void setConsumerGroup(boolean v) { this.consumerGroup = v; }
    public int getSendersPerChannel() { return sendersPerChannel; }
    public void setSendersPerChannel(int v) { this.sendersPerChannel = v; }
    public int getRespondersPerChannel() { return respondersPerChannel; }
    public void setRespondersPerChannel(int v) { this.respondersPerChannel = v; }
    public int getRate() { return rate; }
    public void setRate(int v) { this.rate = v; }
    public ThresholdsConfig getThresholds() { return thresholds; }
    public void setThresholds(ThresholdsConfig v) { this.thresholds = v; }
}

/**
 * Warmup configuration for multi-channel parallel warmup.
 */
class WarmupConfig {
    private int maxParallelChannels = 10;
    private int timeoutPerChannelMs = 5000;
    private String warmupDuration = "";

    public int getMaxParallelChannels() { return maxParallelChannels; }
    public void setMaxParallelChannels(int v) { this.maxParallelChannels = v; }
    public int getTimeoutPerChannelMs() { return timeoutPerChannelMs; }
    public void setTimeoutPerChannelMs(int v) { this.timeoutPerChannelMs = v; }
    public String getWarmupDuration() { return warmupDuration; }
    public void setWarmupDuration(String v) { this.warmupDuration = v; }
}

class QueueConfig {
    private int pollMaxMessages = 10;
    private int pollWaitTimeoutSeconds = 5;
    private int visibilitySeconds = 30;
    private boolean autoAck = false;
    private int maxDepth = 1_000_000;

    public int getPollMaxMessages() { return pollMaxMessages; }
    public void setPollMaxMessages(int v) { this.pollMaxMessages = v; }
    public int getPollWaitTimeoutSeconds() { return pollWaitTimeoutSeconds; }
    public void setPollWaitTimeoutSeconds(int v) { this.pollWaitTimeoutSeconds = v; }
    public int getVisibilitySeconds() { return visibilitySeconds; }
    public void setVisibilitySeconds(int v) { this.visibilitySeconds = v; }
    public boolean isAutoAck() { return autoAck; }
    public void setAutoAck(boolean v) { this.autoAck = v; }
    public int getMaxDepth() { return maxDepth; }
    public void setMaxDepth(int v) { this.maxDepth = v; }
}

class RpcConfig {
    private int timeoutMs = 5000;

    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int v) { this.timeoutMs = v; }
}

class MessageConfig {
    private String sizeMode = "fixed";
    private int sizeBytes = 1024;
    private String sizeDistribution = "256:80,4096:15,65536:5";
    private int reorderWindow = 10_000;

    public String getSizeMode() { return sizeMode; }
    public void setSizeMode(String v) { this.sizeMode = v; }
    public int getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(int v) { this.sizeBytes = v; }
    public String getSizeDistribution() { return sizeDistribution; }
    public void setSizeDistribution(String v) { this.sizeDistribution = v; }
    public int getReorderWindow() { return reorderWindow; }
    public void setReorderWindow(int v) { this.reorderWindow = v; }
}

class MetricsConfig {
    private int port = 8888;
    private String reportInterval = "30s";

    public int getPort() { return port; }
    public void setPort(int v) { this.port = v; }
    public String getReportInterval() { return reportInterval; }
    public void setReportInterval(String v) { this.reportInterval = v; }
}

class LoggingConfig {
    private String format = "text";
    private String level = "info";

    public String getFormat() { return format; }
    public void setFormat(String v) { this.format = v; }
    public String getLevel() { return level; }
    public void setLevel(String v) { this.level = v; }
}

class ForcedDisconnectConfig {
    private String interval = "0";
    private String duration = "5s";

    public String getInterval() { return interval; }
    public void setInterval(String v) { this.interval = v; }
    public String getDuration() { return duration; }
    public void setDuration(String v) { this.duration = v; }
}

class RecoveryConfig {
    private String reconnectInterval = "1s";
    private String reconnectMaxInterval = "30s";
    private double reconnectMultiplier = 2.0;

    public String getReconnectInterval() { return reconnectInterval; }
    public void setReconnectInterval(String v) { this.reconnectInterval = v; }
    public String getReconnectMaxInterval() { return reconnectMaxInterval; }
    public void setReconnectMaxInterval(String v) { this.reconnectMaxInterval = v; }
    public double getReconnectMultiplier() { return reconnectMultiplier; }
    public void setReconnectMultiplier(double v) { this.reconnectMultiplier = v; }
}

class ShutdownConfig {
    private int drainTimeoutSeconds = 10;
    private boolean cleanupChannels = true;

    public int getDrainTimeoutSeconds() { return drainTimeoutSeconds; }
    public void setDrainTimeoutSeconds(int v) { this.drainTimeoutSeconds = v; }
    public boolean isCleanupChannels() { return cleanupChannels; }
    public void setCleanupChannels(boolean v) { this.cleanupChannels = v; }
}

class CorsConfig {
    private String origins = "*";

    public String getOrigins() { return origins; }
    public void setOrigins(String v) { this.origins = v; }
}

class OutputConfig {
    private String reportFile = "";
    private String sdkVersion = "";

    public String getReportFile() { return reportFile; }
    public void setReportFile(String v) { this.reportFile = v; }
    public String getSdkVersion() { return sdkVersion; }
    public void setSdkVersion(String v) { this.sdkVersion = v; }
}

class ThresholdsConfig {
    private double maxLossPct = 0.0;
    private double maxEventsLossPct = 5.0;
    private double maxDuplicationPct = 0.1;
    private double maxP99LatencyMs = 1000;
    private double maxP999LatencyMs = 5000;
    private double minThroughputPct = 90;
    private double maxErrorRatePct = 1.0;
    private double maxMemoryGrowthFactor = 2.0;
    private double maxDowntimePct = 10;
    private String maxDuration = "168h";

    public double getMaxLossPct() { return maxLossPct; }
    public void setMaxLossPct(double v) { this.maxLossPct = v; }
    public double getMaxEventsLossPct() { return maxEventsLossPct; }
    public void setMaxEventsLossPct(double v) { this.maxEventsLossPct = v; }
    public double getMaxDuplicationPct() { return maxDuplicationPct; }
    public void setMaxDuplicationPct(double v) { this.maxDuplicationPct = v; }
    public double getMaxP99LatencyMs() { return maxP99LatencyMs; }
    public void setMaxP99LatencyMs(double v) { this.maxP99LatencyMs = v; }
    public double getMaxP999LatencyMs() { return maxP999LatencyMs; }
    public void setMaxP999LatencyMs(double v) { this.maxP999LatencyMs = v; }
    public double getMinThroughputPct() { return minThroughputPct; }
    public void setMinThroughputPct(double v) { this.minThroughputPct = v; }
    public double getMaxErrorRatePct() { return maxErrorRatePct; }
    public void setMaxErrorRatePct(double v) { this.maxErrorRatePct = v; }
    public double getMaxMemoryGrowthFactor() { return maxMemoryGrowthFactor; }
    public void setMaxMemoryGrowthFactor(double v) { this.maxMemoryGrowthFactor = v; }
    public double getMaxDowntimePct() { return maxDowntimePct; }
    public void setMaxDowntimePct(double v) { this.maxDowntimePct = v; }
    public String getMaxDuration() { return maxDuration; }
    public void setMaxDuration(String v) { this.maxDuration = v; }
}

// ─── Top-level config (v2) ─────────────────────────────────────────────────────

class BurninConfig {
    private String version = "2";
    private BrokerConfig broker = new BrokerConfig();
    private String mode = "soak";
    private String duration = "1h";
    private String runId = "";
    private int startingTimeoutSeconds = 60;
    private Map<String, PatternConfig> patterns = new LinkedHashMap<>();
    private WarmupConfig warmup = new WarmupConfig();
    private QueueConfig queue = new QueueConfig();
    private RpcConfig rpc = new RpcConfig();
    private MessageConfig message = new MessageConfig();
    private MetricsConfig metrics = new MetricsConfig();
    private LoggingConfig logging = new LoggingConfig();
    private ForcedDisconnectConfig forcedDisconnect = new ForcedDisconnectConfig();
    private RecoveryConfig recovery = new RecoveryConfig();
    private ShutdownConfig shutdown = new ShutdownConfig();
    private OutputConfig output = new OutputConfig();
    private ThresholdsConfig thresholds = new ThresholdsConfig();
    private CorsConfig cors = new CorsConfig();

    public String getVersion() { return version; }
    public void setVersion(String v) { this.version = v; }
    public BrokerConfig getBroker() { return broker; }
    public void setBroker(BrokerConfig v) { this.broker = v; }
    public String getMode() { return mode; }
    public void setMode(String v) { this.mode = v; }
    public String getDuration() { return duration; }
    public void setDuration(String v) { this.duration = v; }
    public String getRunId() { return runId; }
    public void setRunId(String v) { this.runId = v; }
    public int getStartingTimeoutSeconds() { return startingTimeoutSeconds; }
    public void setStartingTimeoutSeconds(int v) { this.startingTimeoutSeconds = v; }
    public Map<String, PatternConfig> getPatterns() { return patterns; }
    public void setPatterns(Map<String, PatternConfig> v) { this.patterns = v; }
    public WarmupConfig getWarmup() { return warmup; }
    public void setWarmup(WarmupConfig v) { this.warmup = v; }
    public QueueConfig getQueue() { return queue; }
    public void setQueue(QueueConfig v) { this.queue = v; }
    public RpcConfig getRpc() { return rpc; }
    public void setRpc(RpcConfig v) { this.rpc = v; }
    public MessageConfig getMessage() { return message; }
    public void setMessage(MessageConfig v) { this.message = v; }
    public MetricsConfig getMetrics() { return metrics; }
    public void setMetrics(MetricsConfig v) { this.metrics = v; }
    public LoggingConfig getLogging() { return logging; }
    public void setLogging(LoggingConfig v) { this.logging = v; }
    public ForcedDisconnectConfig getForcedDisconnect() { return forcedDisconnect; }
    public void setForcedDisconnect(ForcedDisconnectConfig v) { this.forcedDisconnect = v; }
    public RecoveryConfig getRecovery() { return recovery; }
    public void setRecovery(RecoveryConfig v) { this.recovery = v; }
    public ShutdownConfig getShutdown() { return shutdown; }
    public void setShutdown(ShutdownConfig v) { this.shutdown = v; }
    public OutputConfig getOutput() { return output; }
    public void setOutput(OutputConfig v) { this.output = v; }
    public ThresholdsConfig getThresholds() { return thresholds; }
    public void setThresholds(ThresholdsConfig v) { this.thresholds = v; }
    public CorsConfig getCors() { return cors; }
    public void setCors(CorsConfig v) { this.cors = v; }

    /**
     * Get the warmup duration string (from WarmupConfig or mode default).
     */
    public String getWarmupDuration() {
        String wd = warmup.getWarmupDuration();
        if (wd != null && !wd.isEmpty()) return wd;
        return "benchmark".equals(mode) ? "60s" : "0s";
    }

    /**
     * Convenience: get the set of enabled pattern names.
     */
    public Set<String> getEnabledPatterns() {
        Set<String> enabled = new LinkedHashSet<>();
        for (String name : AllPatterns.NAMES) {
            PatternConfig pc = patterns.get(name);
            if (pc != null && pc.isEnabled()) {
                enabled.add(name);
            }
        }
        return enabled;
    }

    public boolean isPatternEnabled(String pattern) {
        PatternConfig pc = patterns.get(pattern);
        return pc != null && pc.isEnabled();
    }

    /**
     * Get PatternConfig for a pattern, or null if not configured.
     */
    public PatternConfig getPatternConfig(String pattern) {
        return patterns.get(pattern);
    }

    /**
     * Ensure all 6 patterns exist with defaults.
     */
    public void ensureAllPatterns() {
        for (String name : AllPatterns.NAMES) {
            if (!patterns.containsKey(name)) {
                PatternConfig pc = new PatternConfig();
                if ("queue_stream".equals(name)) {
                    pc.setRate(50);
                } else if ("commands".equals(name) || "queries".equals(name)) {
                    pc.setRate(100);
                }
                patterns.put(name, pc);
            }
        }
    }

    /**
     * Get the rate for a pattern.
     */
    public int getPatternRate(String pattern) {
        PatternConfig pc = patterns.get(pattern);
        return pc != null ? pc.getRate() : 0;
    }
}

// ─── ConfigLoadResult ──────────────────────────────────────────────────────────

class ConfigLoadResult {
    private final BurninConfig config;
    private final List<String> warnings;

    ConfigLoadResult(BurninConfig config, List<String> warnings) {
        this.config = config;
        this.warnings = warnings;
    }

    public BurninConfig getConfig() { return config; }
    public List<String> getWarnings() { return warnings; }
}

// ─── Config loader ─────────────────────────────────────────────────────────────

/**
 * Configuration loader with YAML parsing and validation.
 * v2 format only -- no environment variable overrides (config via YAML or API only).
 */
public final class Config {

    private Config() {}

    // ── Duration parsing ──────────────────────────────────────────────────

    /**
     * Parse a duration string like "30s", "5m", "2h", "1d" to seconds.
     * Returns 0 for null/empty/zero.
     */
    public static double parseDuration(String s) {
        if (s == null || s.trim().isEmpty() || s.equals("0")) return 0;
        s = s.trim();

        String[][] units = { {"d", "86400"}, {"h", "3600"}, {"m", "60"}, {"s", "1"} };
        for (String[] unit : units) {
            if (s.endsWith(unit[0])) {
                try {
                    double n = Double.parseDouble(s.substring(0, s.length() - unit[0].length()));
                    return n * Double.parseDouble(unit[1]);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }

        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static double durationSec(String s) { return parseDuration(s); }
    public static double reportIntervalSec(BurninConfig cfg) { return parseDuration(cfg.getMetrics().getReportInterval()); }
    public static double warmupDurationSec(BurninConfig cfg) { return parseDuration(cfg.getWarmupDuration()); }
    public static double forcedDisconnectIntervalSec(BurninConfig cfg) { return parseDuration(cfg.getForcedDisconnect().getInterval()); }
    public static double forcedDisconnectDurationSec(BurninConfig cfg) { return parseDuration(cfg.getForcedDisconnect().getDuration()); }
    public static double reconnectIntervalMs(BurninConfig cfg) { return parseDuration(cfg.getRecovery().getReconnectInterval()) * 1000; }
    public static double reconnectMaxIntervalMs(BurninConfig cfg) { return parseDuration(cfg.getRecovery().getReconnectMaxInterval()) * 1000; }
    public static double maxDurationSec(BurninConfig cfg) { return parseDuration(cfg.getThresholds().getMaxDuration()); }

    // ── Known top-level YAML keys (v2) ────────────────────────────────────

    private static final Set<String> KNOWN_TOP_KEYS = new HashSet<>(Arrays.asList(
        "version", "broker", "mode", "duration", "run_id",
        "patterns", "warmup", "queue", "rpc", "message", "metrics",
        "logging", "forced_disconnect", "recovery", "shutdown", "output", "thresholds",
        "cors", "starting_timeout_seconds"
    ));

    // ── v1 format detection keys ──────────────────────────────────────────

    private static final Set<String> V1_TOP_KEYS = new HashSet<>(Arrays.asList(
        "concurrency", "rates", "warmup_duration"
    ));

    private static final Set<String> V1_PATTERN_FIELDS = new HashSet<>(Arrays.asList(
        "producers", "consumers", "senders", "responders"
    ));

    // ── Config discovery ──────────────────────────────────────────────────

    /**
     * Discover config file path.
     * Priority: BURNIN_CONFIG_FILE env > --config CLI > ./burnin-config.yaml > /etc/burnin/config.yaml
     */
    public static String findConfigFile(String cliPath) {
        String envPath = System.getenv("BURNIN_CONFIG_FILE");
        if (envPath != null && !envPath.isEmpty()) return envPath;
        if (cliPath != null && !cliPath.isEmpty()) return cliPath;

        String[] candidates = {"./burnin-config.yaml", "/etc/burnin/config.yaml"};
        for (String candidate : candidates) {
            if (new File(candidate).exists()) return candidate;
        }
        return "";
    }

    // ── Load config ───────────────────────────────────────────────────────

    /**
     * Load configuration from YAML file. No env var overrides (v2: YAML or API only).
     */
    @SuppressWarnings("unchecked")
    public static ConfigLoadResult loadConfig(String cliPath) {
        String configPath = findConfigFile(cliPath);
        BurninConfig cfg = new BurninConfig();
        List<String> warnings = new ArrayList<>();

        if (!configPath.isEmpty() && new File(configPath).exists()) {
            Yaml yaml = new Yaml();
            try (InputStream is = new FileInputStream(configPath)) {
                Map<String, Object> rawMap = yaml.load(is);
                if (rawMap != null) {
                    // Detect v1 format in YAML
                    for (String key : rawMap.keySet()) {
                        if (V1_TOP_KEYS.contains(key)) {
                            warnings.add("v1 config format detected ('" + key + "' key). Update to v2 patterns format.");
                        }
                        if (!KNOWN_TOP_KEYS.contains(key) && !V1_TOP_KEYS.contains(key)) {
                            warnings.add("unknown config key '" + key + "' -- ignored");
                        }
                    }
                    // Map raw YAML to config object.
                    mapYamlToConfig(rawMap, cfg);
                }
            } catch (Exception ex) {
                warnings.add("YAML parse error: " + ex.getMessage());
            }
        }

        // Ensure all 6 patterns exist with defaults.
        cfg.ensureAllPatterns();

        // Auto-generate runId if empty.
        if (cfg.getRunId() == null || cfg.getRunId().isEmpty()) {
            byte[] bytes = new byte[4];
            new SecureRandom().nextBytes(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b & 0xff));
            }
            cfg.setRunId(hex.toString());
        }

        return new ConfigLoadResult(cfg, warnings);
    }

    // ── Validation ────────────────────────────────────────────────────────

    /**
     * Validate config and return a list of errors/warnings.
     */
    public static List<String> validateConfig(BurninConfig cfg) {
        List<String> errors = new ArrayList<>();

        if (cfg.getBroker().getAddress() == null || cfg.getBroker().getAddress().trim().isEmpty())
            errors.add("broker.address is required");

        if (!"soak".equals(cfg.getMode()) && !"benchmark".equals(cfg.getMode()))
            errors.add("mode must be 'soak' or 'benchmark', got '" + cfg.getMode() + "'");

        if ("soak".equals(cfg.getMode()) && durationSec(cfg.getDuration()) <= 0)
            errors.add("duration must be > 0 for soak mode");

        if (!"fixed".equals(cfg.getMessage().getSizeMode()) && !"distribution".equals(cfg.getMessage().getSizeMode()))
            errors.add("message.size_mode must be 'fixed' or 'distribution'");

        if (cfg.getMessage().getSizeBytes() < 64)
            errors.add("message.size_bytes: must be >= 64, got " + cfg.getMessage().getSizeBytes());

        if (cfg.getMetrics().getPort() <= 0 || cfg.getMetrics().getPort() > 65535)
            errors.add("api.port: must be 1-65535, got " + cfg.getMetrics().getPort());

        if (cfg.getRpc().getTimeoutMs() <= 0)
            errors.add("rpc.timeout_ms must be > 0");

        if (cfg.getShutdown().getDrainTimeoutSeconds() <= 0)
            errors.add("shutdown.drain_timeout_seconds: must be > 0, got " + cfg.getShutdown().getDrainTimeoutSeconds());

        // Pattern-level validation
        boolean anyEnabled = false;
        int totalWorkers = 0;
        int totalAggregateRate = 0;
        Map<String, Integer> clientTypeRates = new LinkedHashMap<>();
        clientTypeRates.put("pubSubClient", 0);
        clientTypeRates.put("queuesClient", 0);
        clientTypeRates.put("cqClient", 0);

        for (Map.Entry<String, PatternConfig> entry : cfg.getPatterns().entrySet()) {
            String name = entry.getKey();
            PatternConfig pc = entry.getValue();
            if (!pc.isEnabled()) continue;
            anyEnabled = true;

            // channels validation
            if (pc.getChannels() < 1 || pc.getChannels() > 1000)
                errors.add(name + ".channels: must be 1-1000, got " + pc.getChannels());

            boolean isRpc = "commands".equals(name) || "queries".equals(name);

            if (isRpc) {
                if (pc.getSendersPerChannel() < 1)
                    errors.add(name + ".senders_per_channel: must be >= 1, got " + pc.getSendersPerChannel());
                if (pc.getSendersPerChannel() > 100)
                    errors.add("WARNING: " + name + ".senders_per_channel: " + pc.getSendersPerChannel() + " exceeds recommended max 100");
                if (pc.getRespondersPerChannel() < 1)
                    errors.add(name + ".responders_per_channel: must be >= 1, got " + pc.getRespondersPerChannel());
                if (pc.getRespondersPerChannel() > 100)
                    errors.add("WARNING: " + name + ".responders_per_channel: " + pc.getRespondersPerChannel() + " exceeds recommended max 100");
                totalWorkers += pc.getChannels() * (pc.getSendersPerChannel() + pc.getRespondersPerChannel());
            } else {
                if (pc.getProducersPerChannel() < 1)
                    errors.add(name + ".producers_per_channel: must be >= 1, got " + pc.getProducersPerChannel());
                if (pc.getProducersPerChannel() > 100)
                    errors.add("WARNING: " + name + ".producers_per_channel: " + pc.getProducersPerChannel() + " exceeds recommended max 100");
                if (pc.getConsumersPerChannel() < 1)
                    errors.add(name + ".consumers_per_channel: must be >= 1, got " + pc.getConsumersPerChannel());
                if (pc.getConsumersPerChannel() > 100)
                    errors.add("WARNING: " + name + ".consumers_per_channel: " + pc.getConsumersPerChannel() + " exceeds recommended max 100");
                totalWorkers += pc.getChannels() * (pc.getProducersPerChannel() + pc.getConsumersPerChannel());
            }

            if ("soak".equals(cfg.getMode()) && pc.getRate() < 0)
                errors.add(name + ".rate: must be >= 0, got " + pc.getRate());

            int channelRate = pc.getChannels() * pc.getRate();
            totalAggregateRate += channelRate;

            // Client type rate aggregation
            if ("events".equals(name) || "events_store".equals(name)) {
                clientTypeRates.merge("pubSubClient", channelRate, Integer::sum);
            } else if ("queue_stream".equals(name) || "queue_simple".equals(name)) {
                clientTypeRates.merge("queuesClient", channelRate, Integer::sum);
            } else if ("commands".equals(name) || "queries".equals(name)) {
                clientTypeRates.merge("cqClient", channelRate, Integer::sum);
            }

            // Per-pattern threshold validation
            ThresholdsConfig pt = pc.getThresholds();
            if (pt != null) {
                if (pt.getMaxLossPct() < 0 || pt.getMaxLossPct() > 100)
                    errors.add(name + ".thresholds.max_loss_pct: must be 0-100, got " + pt.getMaxLossPct());
                if (pt.getMaxP99LatencyMs() <= 0)
                    errors.add(name + ".thresholds.max_p99_latency_ms: must be > 0, got " + pt.getMaxP99LatencyMs());
                if (pt.getMaxP999LatencyMs() <= 0)
                    errors.add(name + ".thresholds.max_p999_latency_ms: must be > 0, got " + pt.getMaxP999LatencyMs());
            }
        }

        if (!anyEnabled) {
            errors.add("at least one pattern must be enabled");
        }

        // Resource guard warnings
        if (totalWorkers > 500)
            errors.add("WARNING: high worker count: " + totalWorkers + " -- may impact system resources");

        // Memory estimate: totalWorkers * (reorderWindow * 8 / 1024 / 1024 + 0.5)
        double estMemoryMb = totalWorkers * (cfg.getMessage().getReorderWindow() * 8.0 / 1024.0 / 1024.0 + 0.5);
        if (estMemoryMb > 4096)
            errors.add("WARNING: memory warning: estimated " + String.format("%.1f", estMemoryMb / 1024) + "GB overhead for " + totalWorkers + " workers -- ensure sufficient system memory");

        for (Map.Entry<String, Integer> cte : clientTypeRates.entrySet()) {
            if (cte.getValue() > 50000)
                errors.add("WARNING: high aggregate rate " + cte.getValue() + " msgs/s through single gRPC connection " + cte.getKey() + " -- may cause transport bottleneck");
        }

        // Global threshold validation
        ThresholdsConfig t = cfg.getThresholds();
        if (t.getMaxLossPct() < 0 || t.getMaxLossPct() > 100) errors.add("thresholds.max_loss_pct must be 0-100");
        if (t.getMaxEventsLossPct() < 0 || t.getMaxEventsLossPct() > 100) errors.add("thresholds.max_events_loss_pct must be 0-100");
        if (t.getMaxDuplicationPct() < 0 || t.getMaxDuplicationPct() > 100) errors.add("thresholds.max_duplication_pct must be 0-100");
        if (t.getMaxErrorRatePct() < 0 || t.getMaxErrorRatePct() > 100) errors.add("thresholds.max_error_rate_pct must be 0-100");
        if (t.getMaxDowntimePct() < 0 || t.getMaxDowntimePct() > 100) errors.add("thresholds.max_downtime_pct must be 0-100");
        if (t.getMinThroughputPct() <= 0 || t.getMinThroughputPct() > 100) errors.add("thresholds.min_throughput_pct must be 0-100");
        if (t.getMaxMemoryGrowthFactor() < 1.0) errors.add("thresholds.max_memory_growth_factor must be >= 1.0");
        if (t.getMaxP99LatencyMs() <= 0) errors.add("thresholds.max_p99_latency_ms must be > 0");
        if (t.getMaxP999LatencyMs() <= 0) errors.add("thresholds.max_p999_latency_ms must be > 0");

        return errors;
    }

    // ── v1 format detection ──────────────────────────────────────────────

    /**
     * Detect v1 config format in a raw JSON map (from API request).
     * Returns list of error messages if v1 format detected; empty list if OK.
     */
    @SuppressWarnings("unchecked")
    public static List<String> detectV1Format(Map<String, Object> rawMap) {
        List<String> errors = new ArrayList<>();

        // Layer 1: top-level keys
        if (rawMap.containsKey("concurrency") || rawMap.containsKey("rates")) {
            errors.add("v1 config format not supported. Update to v2 patterns format.");
            return errors;
        }

        // Layer 2: old field names in patterns block
        Object patternsObj = rawMap.get("patterns");
        if (patternsObj instanceof Map) {
            Map<String, Object> patterns = (Map<String, Object>) patternsObj;
            for (Map.Entry<String, Object> entry : patterns.entrySet()) {
                String patternName = entry.getKey();
                Object pcObj = entry.getValue();
                if (pcObj instanceof Map) {
                    Map<String, Object> pc = (Map<String, Object>) pcObj;
                    for (String field : V1_PATTERN_FIELDS) {
                        if (pc.containsKey(field)) {
                            errors.add("detected v1 field: patterns." + patternName + "." + field + " -- use " + field + "_per_channel");
                        }
                    }
                }
            }
            if (!errors.isEmpty()) {
                errors.add(0, "v1 config format not supported. Update to v2 patterns format.");
            }
        }

        return errors;
    }

    // ── Private: YAML mapping ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static void mapYamlToConfig(Map<String, Object> raw, BurninConfig cfg) {
        if (raw.containsKey("version")) cfg.setVersion(toStr(raw.get("version")));
        if (raw.containsKey("mode")) cfg.setMode(toStr(raw.get("mode")));
        if (raw.containsKey("duration")) cfg.setDuration(toStr(raw.get("duration")));
        if (raw.containsKey("run_id")) cfg.setRunId(toStr(raw.get("run_id")));

        Object brokerObj = raw.get("broker");
        if (brokerObj instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) brokerObj;
            if (m.containsKey("address")) cfg.getBroker().setAddress(toStr(m.get("address")));
            if (m.containsKey("client_id_prefix")) cfg.getBroker().setClientIdPrefix(toStr(m.get("client_id_prefix")));
        }

        // Parse v2 patterns block
        Object patternsObj = raw.get("patterns");
        if (patternsObj instanceof Map) {
            Map<String, Object> patternsMap = (Map<String, Object>) patternsObj;
            for (Map.Entry<String, Object> entry : patternsMap.entrySet()) {
                String patternName = entry.getKey();
                Object pcObj = entry.getValue();
                if (pcObj instanceof Map) {
                    Map<String, Object> pcMap = (Map<String, Object>) pcObj;
                    PatternConfig pc = new PatternConfig();

                    if (pcMap.containsKey("enabled")) pc.setEnabled(toBool(pcMap.get("enabled")));
                    if (pcMap.containsKey("channels")) pc.setChannels(toInt(pcMap.get("channels")));
                    if (pcMap.containsKey("producers_per_channel")) pc.setProducersPerChannel(toInt(pcMap.get("producers_per_channel")));
                    if (pcMap.containsKey("consumers_per_channel")) pc.setConsumersPerChannel(toInt(pcMap.get("consumers_per_channel")));
                    if (pcMap.containsKey("consumer_group")) pc.setConsumerGroup(toBool(pcMap.get("consumer_group")));
                    if (pcMap.containsKey("senders_per_channel")) pc.setSendersPerChannel(toInt(pcMap.get("senders_per_channel")));
                    if (pcMap.containsKey("responders_per_channel")) pc.setRespondersPerChannel(toInt(pcMap.get("responders_per_channel")));
                    if (pcMap.containsKey("rate")) pc.setRate(toInt(pcMap.get("rate")));

                    // Per-pattern threshold overrides
                    Object thrObj = pcMap.get("thresholds");
                    if (thrObj instanceof Map) {
                        Map<String, Object> thrMap = (Map<String, Object>) thrObj;
                        ThresholdsConfig thr = new ThresholdsConfig();
                        if (thrMap.containsKey("max_loss_pct")) thr.setMaxLossPct(toDouble(thrMap.get("max_loss_pct")));
                        if (thrMap.containsKey("max_p99_latency_ms")) thr.setMaxP99LatencyMs(toDouble(thrMap.get("max_p99_latency_ms")));
                        if (thrMap.containsKey("max_p999_latency_ms")) thr.setMaxP999LatencyMs(toDouble(thrMap.get("max_p999_latency_ms")));
                        if (thrMap.containsKey("max_duplication_pct")) thr.setMaxDuplicationPct(toDouble(thrMap.get("max_duplication_pct")));
                        if (thrMap.containsKey("max_error_rate_pct")) thr.setMaxErrorRatePct(toDouble(thrMap.get("max_error_rate_pct")));
                        if (thrMap.containsKey("min_throughput_pct")) thr.setMinThroughputPct(toDouble(thrMap.get("min_throughput_pct")));
                        if (thrMap.containsKey("max_memory_growth_factor")) thr.setMaxMemoryGrowthFactor(toDouble(thrMap.get("max_memory_growth_factor")));
                        if (thrMap.containsKey("max_downtime_pct")) thr.setMaxDowntimePct(toDouble(thrMap.get("max_downtime_pct")));
                        pc.setThresholds(thr);
                    }

                    // Set default rate for queue_stream
                    if ("queue_stream".equals(patternName) && !pcMap.containsKey("rate")) {
                        pc.setRate(50);
                    }

                    cfg.getPatterns().put(patternName, pc);
                }
            }
        }

        // Warmup config
        Object warmupObj = raw.get("warmup");
        if (warmupObj instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) warmupObj;
            if (m.containsKey("max_parallel_channels")) cfg.getWarmup().setMaxParallelChannels(toInt(m.get("max_parallel_channels")));
            if (m.containsKey("timeout_per_channel_ms")) cfg.getWarmup().setTimeoutPerChannelMs(toInt(m.get("timeout_per_channel_ms")));
            if (m.containsKey("warmup_duration")) cfg.getWarmup().setWarmupDuration(toStr(m.get("warmup_duration")));
        }

        Object queueObj = raw.get("queue");
        if (queueObj instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) queueObj;
            if (m.containsKey("poll_max_messages")) cfg.getQueue().setPollMaxMessages(toInt(m.get("poll_max_messages")));
            if (m.containsKey("poll_wait_timeout_seconds")) cfg.getQueue().setPollWaitTimeoutSeconds(toInt(m.get("poll_wait_timeout_seconds")));
            if (m.containsKey("visibility_seconds")) cfg.getQueue().setVisibilitySeconds(toInt(m.get("visibility_seconds")));
            if (m.containsKey("auto_ack")) cfg.getQueue().setAutoAck(toBool(m.get("auto_ack")));
            if (m.containsKey("max_depth")) cfg.getQueue().setMaxDepth(toInt(m.get("max_depth")));
        }

        Object rpcObj = raw.get("rpc");
        if (rpcObj instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) rpcObj;
            if (m.containsKey("timeout_ms")) cfg.getRpc().setTimeoutMs(toInt(m.get("timeout_ms")));
        }

        Object msgObj = raw.get("message");
        if (msgObj instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) msgObj;
            if (m.containsKey("size_mode")) cfg.getMessage().setSizeMode(toStr(m.get("size_mode")));
            if (m.containsKey("size_bytes")) cfg.getMessage().setSizeBytes(toInt(m.get("size_bytes")));
            if (m.containsKey("size_distribution")) cfg.getMessage().setSizeDistribution(toStr(m.get("size_distribution")));
            if (m.containsKey("reorder_window")) cfg.getMessage().setReorderWindow(toInt(m.get("reorder_window")));
        }

        Object metObj = raw.get("metrics");
        if (metObj instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) metObj;
            if (m.containsKey("port")) cfg.getMetrics().setPort(toInt(m.get("port")));
            if (m.containsKey("report_interval")) cfg.getMetrics().setReportInterval(toStr(m.get("report_interval")));
        }

        Object logObj = raw.get("logging");
        if (logObj instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) logObj;
            if (m.containsKey("format")) cfg.getLogging().setFormat(toStr(m.get("format")));
            if (m.containsKey("level")) cfg.getLogging().setLevel(toStr(m.get("level")));
        }

        Object fdObj = raw.get("forced_disconnect");
        if (fdObj instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) fdObj;
            if (m.containsKey("interval")) cfg.getForcedDisconnect().setInterval(toStr(m.get("interval")));
            if (m.containsKey("duration")) cfg.getForcedDisconnect().setDuration(toStr(m.get("duration")));
        }

        Object recObj = raw.get("recovery");
        if (recObj instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) recObj;
            if (m.containsKey("reconnect_interval")) cfg.getRecovery().setReconnectInterval(toStr(m.get("reconnect_interval")));
            if (m.containsKey("reconnect_max_interval")) cfg.getRecovery().setReconnectMaxInterval(toStr(m.get("reconnect_max_interval")));
            if (m.containsKey("reconnect_multiplier")) cfg.getRecovery().setReconnectMultiplier(toDouble(m.get("reconnect_multiplier")));
        }

        Object sdObj = raw.get("shutdown");
        if (sdObj instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) sdObj;
            if (m.containsKey("drain_timeout_seconds")) cfg.getShutdown().setDrainTimeoutSeconds(toInt(m.get("drain_timeout_seconds")));
            if (m.containsKey("cleanup_channels")) cfg.getShutdown().setCleanupChannels(toBool(m.get("cleanup_channels")));
        }

        Object outObj = raw.get("output");
        if (outObj instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) outObj;
            if (m.containsKey("report_file")) cfg.getOutput().setReportFile(toStr(m.get("report_file")));
            if (m.containsKey("sdk_version")) cfg.getOutput().setSdkVersion(toStr(m.get("sdk_version")));
        }

        if (raw.containsKey("starting_timeout_seconds"))
            cfg.setStartingTimeoutSeconds(toInt(raw.get("starting_timeout_seconds")));

        Object corsObj = raw.get("cors");
        if (corsObj instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) corsObj;
            if (m.containsKey("origins")) cfg.getCors().setOrigins(toStr(m.get("origins")));
        }

        Object thrObj = raw.get("thresholds");
        if (thrObj instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) thrObj;
            if (m.containsKey("max_loss_pct")) cfg.getThresholds().setMaxLossPct(toDouble(m.get("max_loss_pct")));
            if (m.containsKey("max_events_loss_pct")) cfg.getThresholds().setMaxEventsLossPct(toDouble(m.get("max_events_loss_pct")));
            if (m.containsKey("max_duplication_pct")) cfg.getThresholds().setMaxDuplicationPct(toDouble(m.get("max_duplication_pct")));
            if (m.containsKey("max_p99_latency_ms")) cfg.getThresholds().setMaxP99LatencyMs(toDouble(m.get("max_p99_latency_ms")));
            if (m.containsKey("max_p999_latency_ms")) cfg.getThresholds().setMaxP999LatencyMs(toDouble(m.get("max_p999_latency_ms")));
            if (m.containsKey("min_throughput_pct")) cfg.getThresholds().setMinThroughputPct(toDouble(m.get("min_throughput_pct")));
            if (m.containsKey("max_error_rate_pct")) cfg.getThresholds().setMaxErrorRatePct(toDouble(m.get("max_error_rate_pct")));
            if (m.containsKey("max_memory_growth_factor")) cfg.getThresholds().setMaxMemoryGrowthFactor(toDouble(m.get("max_memory_growth_factor")));
            if (m.containsKey("max_downtime_pct")) cfg.getThresholds().setMaxDowntimePct(toDouble(m.get("max_downtime_pct")));
            if (m.containsKey("max_duration")) cfg.getThresholds().setMaxDuration(toStr(m.get("max_duration")));
        }
    }

    // ── Private: helper type conversions ──────────────────────────────────

    static String toStr(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    static int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o == null) return 0;
        try { return Integer.parseInt(String.valueOf(o)); } catch (NumberFormatException e) { return 0; }
    }

    static double toDouble(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o == null) return 0.0;
        try { return Double.parseDouble(String.valueOf(o)); } catch (NumberFormatException e) { return 0.0; }
    }

    static boolean toBool(Object o) {
        if (o instanceof Boolean) return (Boolean) o;
        if (o == null) return false;
        String s = String.valueOf(o).toLowerCase();
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }

    /**
     * Convert snake_case to PascalCase (e.g., "client_id_prefix" -> "ClientIdPrefix").
     */
    static String snakeToPascal(String snake) {
        StringBuilder sb = new StringBuilder(snake.length());
        boolean capitalize = true;
        for (int i = 0; i < snake.length(); i++) {
            char c = snake.charAt(i);
            if (c == '_') {
                capitalize = true;
                continue;
            }
            sb.append(capitalize ? Character.toUpperCase(c) : c);
            capitalize = false;
        }
        return sb.toString();
    }
}
