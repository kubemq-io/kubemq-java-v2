// Report generation: 10 verdict checks + memory_trend advisory, verdict, console + JSON output.
// Timestamps as "YYYY-MM-DD HH:MM:SS UTC".

package io.kubemq.burnin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

// ─── Verdict constants ─────────────────────────────────────────────────────────

final class VerdictResult {
    static final String PASSED = "PASSED";
    static final String PASSED_WITH_WARNINGS = "PASSED_WITH_WARNINGS";
    static final String FAILED = "FAILED";
    private VerdictResult() {}
}

// ─── Check result ──────────────────────────────────────────────────────────────

class CheckResult {
    @SerializedName("passed")
    boolean passed;

    @SerializedName("threshold")
    String threshold = "";

    @SerializedName("actual")
    String actual = "";

    @SerializedName("advisory")
    boolean advisory;
}

// ─── Verdict ───────────────────────────────────────────────────────────────────

class Verdict {
    @SerializedName("result")
    String result = VerdictResult.PASSED;

    @SerializedName("passed")
    boolean passed = true;

    @SerializedName("warnings")
    java.util.List<String> warnings = new java.util.ArrayList<>();

    @SerializedName("checks")
    Map<String, CheckResult> checks = new LinkedHashMap<>();
}

// ─── Pattern summary ───────────────────────────────────────────────────────────

class PatternSummary {
    String status = "unknown";
    long sent;
    long received;
    long lost;
    long duplicated;
    long corrupted;
    long outOfOrder;
    double lossPct;
    long errors;
    long reconnections;
    double downtimeSeconds;
    double latencyP50Ms;
    double latencyP95Ms;
    double latencyP99Ms;
    double latencyP999Ms;
    double avgThroughputMsgsSec;
    double peakThroughputMsgsSec;
    double slidingRateMsgsSec;
    double targetRate;
    long bytesSent;
    long bytesReceived;
    long unconfirmed;

    long responsesSuccess;
    long responsesTimeout;
    long responsesError;
    double rpcP50Ms;
    double rpcP95Ms;
    double rpcP99Ms;
    double rpcP999Ms;
    double avgThroughputRpcSec;

    java.util.List<java.util.Map<String, Object>> producers;
    java.util.List<java.util.Map<String, Object>> consumers;
    java.util.List<java.util.Map<String, Object>> senders;
    java.util.List<java.util.Map<String, Object>> responders;
    boolean consumerGroup;
    int numConsumers;

    // v2 multi-channel fields
    int channels = 1;
    int producersPerChannel = 1;
    int consumersPerChannel = 1;
    int sendersPerChannel = 1;
    int respondersPerChannel = 1;

    /**
     * Per-channel data for fail-on-any-channel verdict checks.
     * Each entry represents one channel's stats (sent, received, lost, duplicated, corrupted).
     */
    transient java.util.List<ChannelData> channelDataList;
}

/**
 * Per-channel statistics used for fail-on-any-channel verdict checks.
 */
class ChannelData {
    final int channelIndex;
    final long sent;
    final long received;
    final long lost;
    final long duplicated;
    final long corrupted;
    final long errors;
    final double lossPct;
    final int consumersPerChannel;

    ChannelData(int channelIndex, long sent, long received, long lost,
                long duplicated, long corrupted, long errors, int consumersPerChannel) {
        this.channelIndex = channelIndex;
        this.sent = sent;
        this.received = received;
        this.lost = lost;
        this.duplicated = duplicated;
        this.corrupted = corrupted;
        this.errors = errors;
        this.lossPct = sent > 0 ? (double) lost / sent * 100.0 : 0;
        this.consumersPerChannel = consumersPerChannel;
    }
}

// ─── Resource summary ──────────────────────────────────────────────────────────

class ResourceSummary {
    double peakRssMb;
    double baselineRssMb;
    double memoryGrowthFactor = 1.0;
    int peakWorkers;
}

// ─── Burnin summary ────────────────────────────────────────────────────────────

class BurninSummary {
    String sdk = "java";
    String version = "";
    String mode = "";
    String brokerAddress = "";
    String startedAt = "";
    String endedAt = "";
    double durationSeconds;
    String status = "running";
    boolean allPatternsEnabled = true;
    boolean warmupActive;
    boolean memoryBaselineAdvisory;
    String startupError;
    Map<String, PatternSummary> patterns = new LinkedHashMap<>();
    ResourceSummary resources = new ResourceSummary();
    Verdict verdict;
}

// ─── Report generator ──────────────────────────────────────────────────────────

/**
 * Report generator: 10 verdict checks + memory_trend advisory.
 * Console output with TOTALS, P999, RESOURCES rows.
 * JSON report output via Gson.
 */
public final class Report {

    private Report() {}

    private static final Gson GSON_PRETTY = new GsonBuilder()
            .setPrettyPrinting()
            .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    public static Verdict generateVerdict(BurninSummary summary, ThresholdsConfig thresholds, String mode) {
        return generateVerdict(summary, thresholds, mode, null, null);
    }

    public static Verdict generateVerdict(BurninSummary summary, ThresholdsConfig thresholds, String mode,
                                          java.util.Set<String> enabledPatterns,
                                          Map<String, PatternThreshold> perPatternThresholds) {
        Map<String, CheckResult> checks = new LinkedHashMap<>();
        boolean allPassed = true;
        boolean anyAdvisoryFailed = false;
        java.util.List<String> warnings = new java.util.ArrayList<>();

        if (summary.startupError != null && !summary.startupError.isEmpty()) {
            CheckResult startupCheck = new CheckResult();
            startupCheck.passed = false;
            startupCheck.threshold = "startup succeeds";
            startupCheck.actual = summary.startupError;
            checks.put("startup", startupCheck);
            allPassed = false;

            Verdict v = new Verdict();
            v.result = VerdictResult.FAILED;
            v.passed = false;
            v.warnings = warnings;
            v.checks = checks;
            return v;
        }

        Map<String, PatternSummary> patterns = summary.patterns;
        double durationSecs = summary.durationSeconds;

        if (enabledPatterns == null) {
            enabledPatterns = new java.util.LinkedHashSet<>(java.util.Arrays.asList(AllPatterns.NAMES));
        }

        boolean allEnabled = enabledPatterns.size() == AllPatterns.NAMES.length;
        if (!allEnabled) {
            warnings.add("Not all patterns enabled — not valid for production certification");
        }

        String[] pubsubQueue = {"events", "events_store", "queue_stream", "queue_simple"};

        // v2: Per-channel verdict checks (fail-on-any-channel)
        for (String p : pubsubQueue) {
            if (!enabledPatterns.contains(p)) continue;
            PatternSummary ps = patterns.get(p);
            if (ps == null) continue;

            double maxLoss = thresholds.getMaxLossPct();
            if ("events".equals(p)) maxLoss = thresholds.getMaxEventsLossPct();
            if (perPatternThresholds != null && perPatternThresholds.containsKey(p)) {
                maxLoss = perPatternThresholds.get(p).maxLossPct;
            }

            // Per-channel message loss check: fail if ANY channel exceeds threshold
            if (ps.channelDataList != null && ps.channelDataList.size() > 1) {
                double worstLossPct = 0;
                int worstChannelIdx = 0;
                long worstLost = 0;
                long worstSent = 0;
                boolean anyChannelFailed = false;
                for (ChannelData cd : ps.channelDataList) {
                    if (cd.lossPct > worstLossPct) {
                        worstLossPct = cd.lossPct;
                        worstChannelIdx = cd.channelIndex;
                        worstLost = cd.lost;
                        worstSent = cd.sent;
                    }
                    if (cd.lossPct > maxLoss) {
                        anyChannelFailed = true;
                    }
                }
                String actualStr = anyChannelFailed
                        ? String.format("ch_%04d: %.1f%% (%d/%d)", worstChannelIdx, worstLossPct, worstLost, worstSent)
                        : String.format("%.5f%%", ps.lossPct);
                CheckResult cr = check(!anyChannelFailed,
                        String.format("%.1f%%", maxLoss), actualStr);
                checks.put("message_loss:" + p, cr);
                if (!cr.passed) allPassed = false;
            } else {
                // Single channel: aggregate check (backward compatible)
                CheckResult cr = check(ps.lossPct <= maxLoss,
                        String.format("%.1f%%", maxLoss), String.format("%.5f%%", ps.lossPct));
                checks.put("message_loss:" + p, cr);
                if (!cr.passed) allPassed = false;
            }
        }

        for (String p : pubsubQueue) {
            if (!enabledPatterns.contains(p)) continue;
            PatternSummary ps = patterns.get(p);
            if (ps == null) continue;

            boolean isEventPattern = "events".equals(p) || "events_store".equals(p);

            if (isEventPattern && !ps.consumerGroup && ps.consumersPerChannel > 1) {
                // Broadcast mode: arithmetic-only per-channel check
                // channel.received == channel.sent * consumers_per_channel
                if (ps.channelDataList != null && ps.channelDataList.size() > 1) {
                    boolean broadcastOk = true;
                    int worstChannelIdx = 0;
                    long worstExpected = 0;
                    long worstActual = 0;
                    for (ChannelData cd : ps.channelDataList) {
                        long expectedForChannel = cd.sent * cd.consumersPerChannel;
                        if (cd.received != expectedForChannel) {
                            broadcastOk = false;
                            if (worstExpected == 0 || (expectedForChannel - cd.received) > (worstExpected - worstActual)) {
                                worstChannelIdx = cd.channelIndex;
                                worstExpected = expectedForChannel;
                                worstActual = cd.received;
                            }
                        }
                    }
                    String actualStr = broadcastOk
                            ? String.valueOf(ps.received)
                            : String.format("ch_%04d: %d/%d", worstChannelIdx, worstActual, worstExpected);
                    CheckResult cr = check(broadcastOk,
                            ps.sent + "\u00d7" + ps.consumersPerChannel,
                            actualStr);
                    checks.put("broadcast:" + p, cr);
                    if (!cr.passed) allPassed = false;
                } else {
                    // Single channel: arithmetic check
                    long expectedTotal = ps.sent * ps.consumersPerChannel;
                    boolean broadcastOk = ps.received == expectedTotal;
                    CheckResult cr = check(broadcastOk,
                            ps.sent + "\u00d7" + ps.consumersPerChannel,
                            String.valueOf(ps.received));
                    checks.put("broadcast:" + p, cr);
                    if (!cr.passed) allPassed = false;
                }
            } else if (isEventPattern && ps.consumerGroup) {
                // Consumer group mode: per-channel strict 0% duplication
                if (ps.channelDataList != null && ps.channelDataList.size() > 1) {
                    boolean anyDuplication = false;
                    int worstChannelIdx = 0;
                    double worstDupPct = 0;
                    for (ChannelData cd : ps.channelDataList) {
                        double cdDupPct = cd.sent > 0 ? (double) cd.duplicated / cd.sent * 100.0 : 0;
                        if (cd.duplicated > 0) {
                            anyDuplication = true;
                            if (cdDupPct > worstDupPct) {
                                worstDupPct = cdDupPct;
                                worstChannelIdx = cd.channelIndex;
                            }
                        }
                    }
                    String actualStr = anyDuplication
                            ? String.format("ch_%04d: %.4f%%", worstChannelIdx, worstDupPct)
                            : "0.0000%";
                    CheckResult cr = check(!anyDuplication, "0.0%", actualStr);
                    checks.put("duplication:" + p, cr);
                    if (!cr.passed) allPassed = false;
                } else {
                    double dupPct = ps.sent > 0 ? (double) ps.duplicated / ps.sent * 100.0 : 0;
                    CheckResult cr = check(dupPct == 0,
                            "0.0%", String.format("%.4f%%", dupPct));
                    checks.put("duplication:" + p, cr);
                    if (!cr.passed) allPassed = false;
                }
            } else {
                // Queue patterns or single-consumer events: per-channel threshold check
                if (ps.channelDataList != null && ps.channelDataList.size() > 1) {
                    boolean anyExceeded = false;
                    int worstChannelIdx = 0;
                    double worstDupPct = 0;
                    for (ChannelData cd : ps.channelDataList) {
                        double cdDupPct = cd.sent > 0 ? (double) cd.duplicated / cd.sent * 100.0 : 0;
                        if (cdDupPct > worstDupPct) {
                            worstDupPct = cdDupPct;
                            worstChannelIdx = cd.channelIndex;
                        }
                        if (cdDupPct > thresholds.getMaxDuplicationPct()) {
                            anyExceeded = true;
                        }
                    }
                    String actualStr = anyExceeded
                            ? String.format("ch_%04d: %.4f%%", worstChannelIdx, worstDupPct)
                            : String.format("%.4f%%", ps.sent > 0 ? (double) ps.duplicated / ps.sent * 100.0 : 0);
                    CheckResult cr = check(!anyExceeded,
                            String.format("%.1f%%", thresholds.getMaxDuplicationPct()), actualStr);
                    checks.put("duplication:" + p, cr);
                    if (!cr.passed) allPassed = false;
                } else {
                    double dupPct = ps.sent > 0 ? (double) ps.duplicated / ps.sent * 100.0 : 0;
                    CheckResult cr = check(dupPct <= thresholds.getMaxDuplicationPct(),
                            String.format("%.1f%%", thresholds.getMaxDuplicationPct()),
                            String.format("%.4f%%", dupPct));
                    checks.put("duplication:" + p, cr);
                    if (!cr.passed) allPassed = false;
                }
            }
        }

        // Corruption: per-channel check -- fail if ANY channel has corruption
        long totalCorrupted = 0;
        boolean anyChannelCorrupted = false;
        int worstCorruptedChannelIdx = 0;
        long worstCorruptedCount = 0;
        String worstCorruptedPattern = "";
        for (Map.Entry<String, PatternSummary> psEntry : patterns.entrySet()) {
            PatternSummary ps = psEntry.getValue();
            totalCorrupted += ps.corrupted;
            if (ps.channelDataList != null) {
                for (ChannelData cd : ps.channelDataList) {
                    if (cd.corrupted > 0) {
                        anyChannelCorrupted = true;
                        if (cd.corrupted > worstCorruptedCount) {
                            worstCorruptedCount = cd.corrupted;
                            worstCorruptedChannelIdx = cd.channelIndex;
                            worstCorruptedPattern = psEntry.getKey();
                        }
                    }
                }
            }
        }
        String corruptActual = anyChannelCorrupted
                ? String.format("%s ch_%04d: %d", worstCorruptedPattern, worstCorruptedChannelIdx, worstCorruptedCount)
                : String.valueOf(totalCorrupted);
        CheckResult corrCheck = check(totalCorrupted == 0, "0", corruptActual);
        checks.put("corruption", corrCheck);
        if (!corrCheck.passed) allPassed = false;

        for (String p : AllPatterns.NAMES) {
            if (!enabledPatterns.contains(p)) continue;
            PatternSummary ps = patterns.get(p);
            if (ps == null) continue;

            double p99Threshold = thresholds.getMaxP99LatencyMs();
            double p999Threshold = thresholds.getMaxP999LatencyMs();
            if (perPatternThresholds != null && perPatternThresholds.containsKey(p)) {
                p99Threshold = perPatternThresholds.get(p).maxP99LatencyMs;
                p999Threshold = perPatternThresholds.get(p).maxP999LatencyMs;
            }

            boolean isRpc = "commands".equals(p) || "queries".equals(p);
            double actualP99 = isRpc ? ps.rpcP99Ms : ps.latencyP99Ms;
            double actualP999 = isRpc ? ps.rpcP999Ms : ps.latencyP999Ms;

            CheckResult c99 = check(actualP99 <= p99Threshold,
                    String.format("%.0fms", p99Threshold), String.format("%.1fms", actualP99));
            checks.put("p99_latency:" + p, c99);
            if (!c99.passed) allPassed = false;

            CheckResult c999 = check(actualP999 <= p999Threshold,
                    String.format("%.0fms", p999Threshold), String.format("%.1fms", actualP999));
            checks.put("p999_latency:" + p, c999);
            if (!c999.passed) allPassed = false;
        }

        if ("soak".equals(mode) && durationSecs > 0) {
            double minTp = 100;
            for (String p : AllPatterns.NAMES) {
                if (!enabledPatterns.contains(p)) continue;
                PatternSummary ps = patterns.get(p);
                if (ps != null && ps.targetRate > 0) {
                    minTp = Math.min(minTp, ps.avgThroughputMsgsSec / ps.targetRate * 100.0);
                }
            }
            CheckResult tpCheck = check(minTp >= thresholds.getMinThroughputPct(),
                    String.format("%.0f%%", thresholds.getMinThroughputPct()),
                    String.format("%.0f%%", minTp));
            checks.put("throughput", tpCheck);
            if (!tpCheck.passed) allPassed = false;
        }

        for (String p : AllPatterns.NAMES) {
            if (!enabledPatterns.contains(p)) continue;
            PatternSummary ps = patterns.get(p);
            if (ps == null) continue;

            boolean isRpc = "commands".equals(p) || "queries".equals(p);
            long denominator = isRpc ? (ps.sent + ps.responsesSuccess) : (ps.sent + ps.received);
            double errPct = denominator > 0 ? (double) ps.errors / denominator * 100.0 : 0;

            CheckResult cr = check(errPct <= thresholds.getMaxErrorRatePct(),
                    String.format("%.1f%%", thresholds.getMaxErrorRatePct()),
                    String.format("%.4f%%", errPct));
            checks.put("error_rate:" + p, cr);
            if (!cr.passed) allPassed = false;
        }

        double growth = summary.resources.memoryGrowthFactor;
        boolean memAdvisory = summary.memoryBaselineAdvisory;
        CheckResult memCheck = check(growth <= thresholds.getMaxMemoryGrowthFactor(),
                String.format("%.1fx", thresholds.getMaxMemoryGrowthFactor()),
                String.format("%.2fx", growth));
        if (memAdvisory) {
            memCheck.advisory = true;
        }
        checks.put("memory_stability", memCheck);
        if (!memCheck.passed) {
            if (memAdvisory) {
                anyAdvisoryFailed = true;
                warnings.add("Memory stability check advisory: run < 5 minutes, baseline may be unreliable");
            } else {
                allPassed = false;
            }
        }

        double memTrendThreshold = 1.0 + (thresholds.getMaxMemoryGrowthFactor() - 1.0) * 0.5;
        boolean trendPassed = growth <= memTrendThreshold;
        CheckResult trendCheck = new CheckResult();
        trendCheck.passed = trendPassed;
        trendCheck.threshold = String.format("%.1fx", memTrendThreshold);
        trendCheck.actual = String.format("%.2fx", growth);
        trendCheck.advisory = true;
        checks.put("memory_trend", trendCheck);
        if (!trendPassed) {
            anyAdvisoryFailed = true;
            warnings.add(String.format("Memory growth trend: %.2fx (advisory threshold: %.1fx)", growth, memTrendThreshold));
        }

        double maxDowntime = 0;
        if (durationSecs > 0) {
            for (PatternSummary ps : patterns.values()) {
                maxDowntime = Math.max(maxDowntime, ps.downtimeSeconds / durationSecs * 100.0);
            }
        }
        CheckResult downCheck = check(maxDowntime <= thresholds.getMaxDowntimePct(),
                String.format("%.1f%%", thresholds.getMaxDowntimePct()),
                String.format("%.4f%%", maxDowntime));
        checks.put("downtime", downCheck);
        if (!downCheck.passed) allPassed = false;

        String result;
        if (!allPassed) {
            result = VerdictResult.FAILED;
        } else if (anyAdvisoryFailed || !allEnabled) {
            result = VerdictResult.PASSED_WITH_WARNINGS;
        } else {
            result = VerdictResult.PASSED;
        }

        Verdict verdict = new Verdict();
        verdict.result = result;
        verdict.passed = allPassed;
        verdict.warnings = warnings;
        verdict.checks = checks;
        return verdict;
    }

    // ── Console Report ────────────────────────────────────────────────────

    private static final Map<String, String> CHECK_LABELS = new LinkedHashMap<>();
    static {
        CHECK_LABELS.put("message_loss_persistent", "Message loss (persistent):");
        CHECK_LABELS.put("message_loss_events", "Message loss (events):");
        CHECK_LABELS.put("duplication", "Duplication:");
        CHECK_LABELS.put("corruption", "Corruption:");
        CHECK_LABELS.put("p99_latency", "P99 latency:");
        CHECK_LABELS.put("p999_latency", "P999 latency:");
        CHECK_LABELS.put("throughput", "Throughput:");
        CHECK_LABELS.put("error_rate", "Error rate:");
        CHECK_LABELS.put("memory_stability", "Memory stability:");
        CHECK_LABELS.put("downtime", "Downtime:");
        CHECK_LABELS.put("memory_trend", "Memory trend:");
    }

    /**
     * Print the full console report with TOTALS row, RESOURCES, and P999 column.
     * Timestamps formatted as "YYYY-MM-DD HH:MM:SS UTC".
     */
    public static void printConsoleReport(BurninSummary summary) {
        Verdict v = summary.verdict;
        double dur = summary.durationSeconds;
        int h = (int) (dur / 3600);
        int m = (int) ((dur % 3600) / 60);
        int s = (int) (dur % 60);
        String durStr = h > 0 ? h + "h " + m + "m " + s + "s" : m + "m " + s + "s";

        StringBuilder sb = new StringBuilder();
        String sep67 = repeat('=', 67);
        String dash67 = repeat('-', 67);

        sb.append('\n');
        sb.append(sep67).append('\n');
        sb.append("  KUBEMQ BURN-IN TEST REPORT -- Java SDK v").append(summary.version).append('\n');
        sb.append(sep67).append('\n');
        sb.append("  Mode:     ").append(summary.mode).append('\n');
        sb.append("  Broker:   ").append(summary.brokerAddress).append('\n');
        sb.append("  Duration: ").append(durStr).append('\n');
        sb.append("  Started:  ").append(formatTimestamp(summary.startedAt)).append('\n');
        sb.append("  Ended:    ").append(formatTimestamp(summary.endedAt)).append('\n');
        sb.append(dash67).append('\n');

        // Header with P999 column.
        sb.append("  ").append(padRight("PATTERN", 16));
        sb.append(padLeft("SENT", 10)).append(padLeft("RECV", 10));
        sb.append(padLeft("LOST", 6)).append(padLeft("DUP", 6)).append(padLeft("ERR", 6));
        sb.append(padLeft("P99(ms)", 9)).append(padLeft("P999(ms)", 10)).append('\n');

        long tSent = 0, tRecv = 0, tLost = 0, tDup = 0, tErr = 0;
        for (Map.Entry<String, PatternSummary> entry : summary.patterns.entrySet()) {
            String name = entry.getKey();
            PatternSummary ps = entry.getValue();
            tSent += ps.sent;
            tRecv += ps.received;
            tLost += ps.lost;
            tDup += ps.duplicated;
            tErr += ps.errors;

            double p99 = ("commands".equals(name) || "queries".equals(name)) ? ps.rpcP99Ms : ps.latencyP99Ms;
            double p999 = ("commands".equals(name) || "queries".equals(name)) ? ps.rpcP999Ms : ps.latencyP999Ms;

            // v2: show channel count per pattern
            String displayName = ps.channels > 1 ? name + " (" + ps.channels + "ch)" : name;
            sb.append("  ").append(padRight(displayName, 16));
            sb.append(padLeft(String.valueOf(ps.sent), 10));
            sb.append(padLeft(String.valueOf(ps.received), 10));
            sb.append(padLeft(String.valueOf(ps.lost), 6));
            sb.append(padLeft(String.valueOf(ps.duplicated), 6));
            sb.append(padLeft(String.valueOf(ps.errors), 6));
            sb.append(padLeft(String.format("%.1f", p99), 9));
            sb.append(padLeft(String.format("%.1f", p999), 10));
            sb.append('\n');
        }

        sb.append(dash67).append('\n');
        sb.append("  ").append(padRight("TOTALS", 16));
        sb.append(padLeft(String.valueOf(tSent), 10));
        sb.append(padLeft(String.valueOf(tRecv), 10));
        sb.append(padLeft(String.valueOf(tLost), 6));
        sb.append(padLeft(String.valueOf(tDup), 6));
        sb.append(padLeft(String.valueOf(tErr), 6));
        sb.append('\n');

        ResourceSummary res = summary.resources;
        sb.append(String.format("  RESOURCES       RSS: %.0fMB -> %.0fMB (%.2fx)  Workers: %d%n",
                res.baselineRssMb, res.peakRssMb, res.memoryGrowthFactor, res.peakWorkers));

        sb.append(dash67).append('\n');
        sb.append("  VERDICT: ").append(v.result).append('\n');

        for (Map.Entry<String, CheckResult> entry : v.checks.entrySet()) {
            String name = entry.getKey();
            CheckResult cr = entry.getValue();
            String mk = cr.passed ? "+" : "!";
            String adv = cr.advisory ? " (advisory)" : "";
            String label = CHECK_LABELS.getOrDefault(name, name + ":");
            sb.append("    ").append(mk).append(' ');
            sb.append(padRight(label, 30));
            sb.append(padRight(cr.actual, 12));
            sb.append("(threshold: ").append(cr.threshold).append(')');
            sb.append(adv).append('\n');
        }

        sb.append(sep67).append('\n');
        System.out.println(sb.toString());
    }

    // ── JSON report ───────────────────────────────────────────────────────

    /**
     * Write the summary as a formatted JSON file.
     */
    public static void writeJsonReport(BurninSummary summary, String path) {
        String json = GSON_PRETTY.toJson(summary);
        try (FileWriter writer = new FileWriter(path)) {
            writer.write(json);
        } catch (IOException e) {
            System.err.println("Failed to write report: " + e.getMessage());
            return;
        }
        System.out.println("report written to " + path);
    }

    // ── Timestamp formatting ──────────────────────────────────────────────

    /**
     * Format an ISO 8601 timestamp as "YYYY-MM-DD HH:MM:SS UTC".
     */
    public static String formatTimestamp(String iso) {
        if (iso == null || iso.isEmpty()) return "N/A";
        String formatted = iso.replace("T", " ").replace("Z", "");
        int dotIdx = formatted.lastIndexOf('.');
        if (dotIdx >= 0) {
            formatted = formatted.substring(0, dotIdx);
        }
        return formatted + " UTC";
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private static CheckResult check(boolean passed, String threshold, String actual) {
        CheckResult cr = new CheckResult();
        cr.passed = passed;
        cr.threshold = threshold;
        cr.actual = actual;
        return cr;
    }

    private static String padRight(String s, int width) {
        if (s.length() >= width) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    private static String padLeft(String s, int width) {
        if (s.length() >= width) return s;
        StringBuilder sb = new StringBuilder();
        while (sb.length() + s.length() < width) sb.append(' ');
        sb.append(s);
        return sb.toString();
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) sb.append(c);
        return sb.toString();
    }
}
