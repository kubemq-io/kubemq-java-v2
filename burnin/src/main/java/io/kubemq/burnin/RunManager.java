package io.kubemq.burnin;

import com.google.gson.JsonSyntaxException;
import io.kubemq.sdk.client.ReconnectionConfig;
import io.kubemq.sdk.cq.CQChannel;
import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.pubsub.PubSubChannel;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.QueuesChannel;
import io.kubemq.sdk.queues.QueuesClient;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class RunManager {

    private final BurninConfig startupConfig;
    private final AtomicReference<RunState> state = new AtomicReference<>(RunState.IDLE);
    private final Instant bootTime = Instant.now();

    private volatile Engine engine;
    private volatile BurninConfig activeConfig;
    private volatile ApiRunConfig activeApiConfig;
    private volatile Map<String, PatternThreshold> patternThresholds;
    private volatile String runId;
    private volatile String startedAt;
    private volatile String endedAt;
    private volatile String errorMessage;
    private volatile BurninSummary lastReport;
    private volatile CountDownLatch stopLatch;
    private volatile Thread engineThread;
    private volatile ScheduledFuture<?> startingTimeoutFuture;
    private final ScheduledExecutorService timeoutScheduler;
    private volatile boolean statusDeprecationWarned;
    private volatile boolean summaryDeprecationWarned;

    public RunManager(BurninConfig startupConfig) {
        this.startupConfig = startupConfig;
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "starting-timeout");
            t.setDaemon(true);
            return t;
        });
        Metrics.preInitialize();
    }

    public RunState getState() {
        return state.get();
    }

    public String getRunId() {
        return runId;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public String getEndedAt() {
        return endedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public BurninConfig getStartupConfig() {
        return startupConfig;
    }

    public BurninConfig getActiveConfig() {
        return activeConfig;
    }

    public Instant getBootTime() {
        return bootTime;
    }

    public BurninSummary getLastReport() {
        return lastReport;
    }

    public boolean isStatusDeprecationWarned() {
        boolean was = statusDeprecationWarned;
        statusDeprecationWarned = true;
        return was;
    }

    public boolean isSummaryDeprecationWarned() {
        boolean was = summaryDeprecationWarned;
        summaryDeprecationWarned = true;
        return was;
    }

    // --- Run Start ---

    public static class StartResult {
        public final int httpStatus;
        public final Map<String, Object> body;

        StartResult(int httpStatus, Map<String, Object> body) {
            this.httpStatus = httpStatus;
            this.body = body;
        }
    }

    public StartResult startRun(String jsonBody) {
        RunState current = state.get();
        if (!current.canStart()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Run already active");
            body.put("run_id", runId);
            body.put("state", current.getValue());
            if (startedAt != null) body.put("started_at", startedAt);
            return new StartResult(409, body);
        }

        // v1 format detection -- dual-layer
        List<String> v1Errors = ApiConfig.detectV1Format(jsonBody);
        if (!v1Errors.isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", v1Errors.get(0));
            body.put("errors", v1Errors);
            return new StartResult(400, body);
        }

        ApiRunConfig apiConfig;
        try {
            apiConfig = ApiConfig.parse(jsonBody);
        } catch (JsonSyntaxException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Invalid JSON: " + e.getMessage());
            return new StartResult(400, body);
        }

        List<String> errors = ApiConfig.validate(apiConfig);
        if (!errors.isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Configuration validation failed");
            body.put("errors", errors);
            return new StartResult(400, body);
        }

        BurninConfig runConfig = ApiConfig.translate(apiConfig, startupConfig);
        Map<String, PatternThreshold> thresholds = ApiConfig.resolvePatternThresholds(apiConfig);
        String newRunId = runConfig.getRunId();

        if (!state.compareAndSet(current, RunState.STARTING)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Run already active");
            body.put("state", state.get().getValue());
            return new StartResult(409, body);
        }

        this.lastReport = null;
        this.errorMessage = null;
        this.endedAt = null;
        this.runId = newRunId;
        this.startedAt = Instant.now().toString();
        this.activeConfig = runConfig;
        this.activeApiConfig = apiConfig;
        this.patternThresholds = thresholds;

        launchEngine(runConfig);

        int enabledCount = runConfig.getEnabledPatterns().size();
        int totalChannels = 0;
        for (PatternConfig pc : runConfig.getPatterns().values()) {
            if (pc.isEnabled()) totalChannels += pc.getChannels();
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "starting");
        body.put("run_id", newRunId);
        body.put("message", "run starting with " + totalChannels + " channels across " + enabledCount + " patterns");
        return new StartResult(202, body);
    }

    private void launchEngine(BurninConfig cfg) {
        Engine eng = new Engine(cfg);
        this.engine = eng;
        CountDownLatch latch = new CountDownLatch(1);
        this.stopLatch = latch;

        eng.setOnReady(() -> {
            cancelStartingTimeout();
            state.compareAndSet(RunState.STARTING, RunState.RUNNING);
        });

        int timeoutSec = cfg.getStartingTimeoutSeconds();
        startingTimeoutFuture = timeoutScheduler.schedule(() -> {
            if (state.compareAndSet(RunState.STARTING, RunState.ERROR)) {
                errorMessage = "Starting timeout exceeded (" + timeoutSec + "s)";
                System.err.println("starting timeout exceeded, transitioning to error");
                latch.countDown();
                finishRun(true);
            }
        }, timeoutSec, TimeUnit.SECONDS);

        Thread t = new Thread(() -> {
            try {
                eng.run(latch);
            } catch (Exception ex) {
                if (state.get() == RunState.STARTING) {
                    errorMessage = "Startup failed: " + ex.getMessage();
                    state.set(RunState.ERROR);
                    cancelStartingTimeout();
                }
                System.err.println("engine run failed: " + ex.getMessage());
            }

            if (state.get() == RunState.RUNNING) {
                state.set(RunState.STOPPING);
                finishRun(false);
            } else if (state.get() == RunState.STARTING) {
                state.set(RunState.ERROR);
                cancelStartingTimeout();
                if (errorMessage == null) errorMessage = "Engine exited during starting";
                finishRun(true);
            }
        }, "burnin-engine");
        t.setDaemon(true);
        t.start();
        this.engineThread = t;
    }

    private void cancelStartingTimeout() {
        ScheduledFuture<?> f = startingTimeoutFuture;
        if (f != null) {
            f.cancel(false);
            startingTimeoutFuture = null;
        }
    }

    private void finishRun(boolean isError) {
        endedAt = Instant.now().toString();
        Engine eng = this.engine;
        if (eng != null) {
            boolean passed = eng.shutdown();
            BurninSummary summary = eng.getLastSummary();
            if (summary != null) {
                if (isError && errorMessage != null) {
                    summary.startupError = errorMessage;
                }
                summary.endedAt = endedAt;
                summary.allPatternsEnabled = activeConfig.getEnabledPatterns().size() == AllPatterns.NAMES.length;
                Verdict verdict = Report.generateVerdict(summary, activeConfig.getThresholds(),
                        activeConfig.getMode(), activeConfig.getEnabledPatterns(), patternThresholds);
                summary.verdict = verdict;
            }
            this.lastReport = summary;
            eng.close();

            String reportFile = activeConfig.getOutput().getReportFile();
            if (reportFile != null && !reportFile.isEmpty() && summary != null) {
                Report.writeJsonReport(summary, reportFile);
            }
            if (summary != null) Report.printConsoleReport(summary);
        }

        if (!isError) {
            state.set(RunState.STOPPED);
        }
    }

    // --- Run Stop ---

    public StartResult stopRun() {
        RunState current = state.get();

        if (current == RunState.STOPPING) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Run is already stopping");
            body.put("run_id", runId);
            body.put("state", "stopping");
            return new StartResult(409, body);
        }

        if (!current.canStop()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "No active run to stop");
            body.put("state", current.getValue());
            return new StartResult(409, body);
        }

        if (!state.compareAndSet(current, RunState.STOPPING)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "State changed concurrently");
            body.put("state", state.get().getValue());
            return new StartResult(409, body);
        }

        cancelStartingTimeout();

        CountDownLatch latch = stopLatch;
        if (latch != null) {
            latch.countDown();
        }

        Thread finisher = new Thread(() -> finishRun(false), "burnin-stop");
        finisher.setDaemon(true);
        finisher.start();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("run_id", runId);
        body.put("state", "stopping");
        body.put("message", "Graceful shutdown initiated");
        return new StartResult(202, body);
    }

    // --- Run Info ---

    public Map<String, Object> buildRunResponse() {
        RunState s = state.get();
        Map<String, Object> resp = new LinkedHashMap<>();

        if (s == RunState.IDLE) {
            resp.put("run_id", null);
            resp.put("state", "idle");
            return resp;
        }

        resp.put("run_id", runId);
        resp.put("state", s.getValue());

        if (s == RunState.ERROR && errorMessage != null) {
            resp.put("error", errorMessage);
        }

        if (activeConfig != null) {
            resp.put("mode", activeConfig.getMode());
            resp.put("broker_address", activeConfig.getBroker().getAddress());
        }

        if (startedAt != null) resp.put("started_at", startedAt);
        if (endedAt != null) resp.put("ended_at", endedAt);

        Engine eng = this.engine;
        if (eng != null) {
            BurninSummary summary = eng.getLastSummary();
            if (summary != null) {
                resp.put("elapsed_seconds", summary.durationSeconds);
                double totalDur = Config.durationSec(activeConfig.getDuration());
                double remaining = Math.max(0, totalDur - summary.durationSeconds);
                resp.put("remaining_seconds", s == RunState.STOPPED ? 0 : remaining);
                resp.put("duration", activeConfig.getDuration());
                resp.put("warmup_active", summary.warmupActive);

                Map<String, Object> patterns = new LinkedHashMap<>();
                for (String p : AllPatterns.NAMES) {
                    if (!activeConfig.isPatternEnabled(p)) {
                        Map<String, Object> disabled = new LinkedHashMap<>();
                        disabled.put("enabled", false);
                        patterns.put(p, disabled);
                        continue;
                    }
                    PatternSummary ps = summary.patterns.get(p);
                    if (ps != null) {
                        patterns.put(p, buildPatternResponse(p, ps, true));
                    }
                }
                resp.put("patterns", patterns);

                Map<String, Object> resources = new LinkedHashMap<>();
                if (s == RunState.STOPPED || s == RunState.ERROR) {
                    resources.put("peak_rss_mb", summary.resources.peakRssMb);
                } else {
                    resources.put("rss_mb", summary.resources.peakRssMb);
                }
                resources.put("baseline_rss_mb", summary.resources.baselineRssMb);
                resources.put("memory_growth_factor", summary.resources.memoryGrowthFactor);
                if (s == RunState.STOPPED || s == RunState.ERROR) {
                    resources.put("peak_workers", summary.resources.peakWorkers);
                } else {
                    resources.put("active_workers", summary.resources.peakWorkers);
                }
                resp.put("resources", resources);
            }
        }

        return resp;
    }

    public Map<String, Object> buildStatusResponse() {
        RunState s = state.get();
        Map<String, Object> resp = new LinkedHashMap<>();

        if (s == RunState.IDLE) {
            resp.put("run_id", null);
            resp.put("state", "idle");
            return resp;
        }

        resp.put("run_id", runId);
        resp.put("state", s.getValue());

        if (s == RunState.ERROR) {
            if (errorMessage != null) resp.put("error", errorMessage);
            return resp;
        }

        if (startedAt != null) resp.put("started_at", startedAt);

        Engine eng = this.engine;
        if (eng != null) {
            BurninSummary summary = eng.getLastSummary();
            if (summary != null) {
                resp.put("elapsed_seconds", summary.durationSeconds);
                double totalDur = Config.durationSec(activeConfig.getDuration());
                double remaining = Math.max(0, totalDur - summary.durationSeconds);
                resp.put("remaining_seconds", s == RunState.STOPPED ? 0 : remaining);
                resp.put("warmup_active", summary.warmupActive);

                Map<String, Long> totals = new LinkedHashMap<>();
                long tSent = 0, tRecv = 0, tLost = 0, tDup = 0, tCorr = 0, tOoo = 0, tErr = 0, tReconn = 0;
                Map<String, Object> patternStates = new LinkedHashMap<>();
                for (String p : AllPatterns.NAMES) {
                    if (!activeConfig.isPatternEnabled(p)) continue;
                    PatternSummary ps = summary.patterns.get(p);
                    if (ps != null) {
                        tSent += ps.sent;
                        if ("commands".equals(p) || "queries".equals(p)) {
                            tRecv += ps.responsesSuccess;
                            tLost += ps.responsesTimeout + ps.responsesError;
                        } else {
                            tRecv += ps.received;
                            tLost += ps.lost;
                        }
                        tDup += ps.duplicated;
                        tCorr += ps.corrupted;
                        tOoo += ps.outOfOrder;
                        tErr += ps.errors;
                        tReconn += ps.reconnections;
                        Map<String, Object> stateObj = new LinkedHashMap<>();
                        stateObj.put("state", ps.status);
                        stateObj.put("channels", ps.channels);
                        patternStates.put(p, stateObj);
                    }
                }
                totals.put("sent", tSent);
                totals.put("received", tRecv);
                totals.put("lost", tLost);
                totals.put("duplicated", tDup);
                totals.put("corrupted", tCorr);
                totals.put("out_of_order", tOoo);
                totals.put("errors", tErr);
                totals.put("reconnections", tReconn);
                resp.put("totals", totals);
                resp.put("pattern_states", patternStates);

                // v2: Add channels count per pattern in status response
                Map<String, Object> patternChannels = new LinkedHashMap<>();
                for (String p : AllPatterns.NAMES) {
                    if (!activeConfig.isPatternEnabled(p)) continue;
                    PatternSummary ps = summary.patterns.get(p);
                    if (ps != null) {
                        Map<String, Object> pInfo = new LinkedHashMap<>();
                        pInfo.put("channels", ps.channels);
                        pInfo.put("state", ps.status);
                        pInfo.put("sent", ps.sent);
                        boolean isRpc = "commands".equals(p) || "queries".equals(p);
                        if (isRpc) {
                            pInfo.put("responses_success", ps.responsesSuccess);
                        } else {
                            pInfo.put("received", ps.received);
                            pInfo.put("lost", ps.lost);
                        }
                        patternChannels.put(p, pInfo);
                    }
                }
                resp.put("patterns", patternChannels);
            }
        }

        return resp;
    }

    public Map<String, Object> buildConfigResponse() {
        RunState s = state.get();
        if (s == RunState.IDLE || activeConfig == null) {
            return null;
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("run_id", runId);
        resp.put("state", s.getValue());

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("version", "2");
        config.put("mode", activeConfig.getMode());
        config.put("duration", activeConfig.getDuration());
        config.put("run_id", runId);
        config.put("warmup_duration", activeConfig.getWarmupDuration());
        config.put("starting_timeout_seconds", activeConfig.getStartingTimeoutSeconds());

        Map<String, Object> broker = new LinkedHashMap<>();
        broker.put("address", activeConfig.getBroker().getAddress());
        broker.put("client_id_prefix", activeConfig.getBroker().getClientIdPrefix());
        config.put("broker", broker);

        Map<String, Object> patterns = new LinkedHashMap<>();
        for (String p : AllPatterns.NAMES) {
            PatternConfig pc = activeConfig.getPatternConfig(p);
            Map<String, Object> pcMap = new LinkedHashMap<>();
            pcMap.put("enabled", pc != null && pc.isEnabled());

            if (pc != null && pc.isEnabled()) {
                pcMap.put("channels", pc.getChannels());
                pcMap.put("rate", pc.getRate());

                boolean isRpc = "commands".equals(p) || "queries".equals(p);
                if (isRpc) {
                    pcMap.put("senders_per_channel", pc.getSendersPerChannel());
                    pcMap.put("responders_per_channel", pc.getRespondersPerChannel());
                } else {
                    pcMap.put("producers_per_channel", pc.getProducersPerChannel());
                    pcMap.put("consumers_per_channel", pc.getConsumersPerChannel());
                    if ("events".equals(p) || "events_store".equals(p)) {
                        pcMap.put("consumer_group", pc.isConsumerGroup());
                    }
                }

                PatternThreshold pt = patternThresholds != null ? patternThresholds.get(p) : null;
                if (pt != null) {
                    Map<String, Object> thr = new LinkedHashMap<>();
                    thr.put("max_loss_pct", pt.maxLossPct);
                    thr.put("max_p99_latency_ms", pt.maxP99LatencyMs);
                    thr.put("max_p999_latency_ms", pt.maxP999LatencyMs);
                    pcMap.put("thresholds", thr);
                }
            }
            patterns.put(p, pcMap);
        }
        config.put("patterns", patterns);

        resp.put("config", config);
        return resp;
    }

    // --- Cleanup ---

    public Map<String, Object> runCleanup() {
        RunState s = state.get();
        if (!s.canCleanup()) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("message", "Cannot cleanup while a run is active");
            resp.put("state", s.getValue());
            if (runId != null) resp.put("run_id", runId);
            return Collections.singletonMap("_status", resp);
        }

        List<String> deleted = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        String prefix = "java_burnin_";

        try {
            ReconnectionConfig rc = ReconnectionConfig.builder()
                    .maxReconnectAttempts(1)
                    .reconnectJitterEnabled(true)
                    .build();
            String addr = startupConfig.getBroker().getAddress();
            String cid = startupConfig.getBroker().getClientIdPrefix() + "-cleanup";

            PubSubClient psc = PubSubClient.builder().address(addr).clientId(cid + "-ps").reconnectionConfig(rc).build();
            QueuesClient qc = QueuesClient.builder().address(addr).clientId(cid + "-q").reconnectionConfig(rc).build();
            CQClient cc = CQClient.builder().address(addr).clientId(cid + "-cq").reconnectionConfig(rc).build();

            try {
                psc.ping();
                cleanChannelList(psc, qc, cc, prefix, deleted, failed);
            } catch (Exception e) {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("deleted_channels", deleted);
                resp.put("failed_channels", failed);
                resp.put("message", "Could not connect to broker: " + e.getMessage());
                return resp;
            } finally {
                try { psc.close(); } catch (Exception e) { /* best effort */ }
                try { qc.close(); } catch (Exception e) { /* best effort */ }
                try { cc.close(); } catch (Exception e) { /* best effort */ }
            }
        } catch (Exception e) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("deleted_channels", deleted);
            resp.put("failed_channels", failed);
            resp.put("message", "Could not connect to broker: " + e.getMessage());
            return resp;
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("deleted_channels", deleted);
        resp.put("failed_channels", failed);
        resp.put("message", "Deleted " + deleted.size() + " channels");
        return resp;
    }

    private void cleanChannelList(PubSubClient psc, QueuesClient qc, CQClient cc,
                                  String prefix, List<String> deleted, List<String> failed) {
        cleanType(() -> psc.listEventsChannels(prefix), PubSubChannel::getName,
                n -> psc.deleteEventsChannel(n), deleted, failed);
        cleanType(() -> psc.listEventsStoreChannels(prefix), PubSubChannel::getName,
                n -> psc.deleteEventsStoreChannel(n), deleted, failed);
        cleanType(() -> qc.listQueuesChannels(prefix), QueuesChannel::getName,
                n -> qc.deleteQueuesChannel(n), deleted, failed);
        cleanType(() -> cc.listCommandsChannels(prefix), CQChannel::getName,
                n -> cc.deleteCommandsChannel(n), deleted, failed);
        cleanType(() -> cc.listQueriesChannels(prefix), CQChannel::getName,
                n -> cc.deleteQueriesChannel(n), deleted, failed);
    }

    private <T> void cleanType(Engine.ChannelLister<T> lister, Engine.ChannelNameExtractor<T> namer,
                                Engine.ChannelDeleter deleter, List<String> deleted, List<String> failed) {
        try {
            List<T> channels = lister.list();
            if (channels == null) return;
            for (T ch : channels) {
                String name = namer.getName(ch);
                try {
                    deleter.delete(name);
                    deleted.add(name);
                } catch (Exception e) {
                    failed.add(name);
                }
            }
        } catch (Exception e) {
            // channel type may not exist
        }
    }

    // --- Broker Status ---

    public Map<String, Object> brokerStatus() {
        String addr = startupConfig.getBroker().getAddress();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("address", addr);

        try {
            ReconnectionConfig rc = ReconnectionConfig.builder()
                    .maxReconnectAttempts(1)
                    .reconnectJitterEnabled(true)
                    .build();
            PubSubClient client = PubSubClient.builder()
                    .address(addr)
                    .clientId("burnin-java-ping-" + System.currentTimeMillis())
                    .reconnectionConfig(rc)
                    .build();
            try {
                long start = System.nanoTime();
                var pingResult = client.ping();
                double latencyMs = (System.nanoTime() - start) / 1_000_000.0;
                resp.put("connected", true);
                resp.put("ping_latency_ms", Math.round(latencyMs * 100.0) / 100.0);
                resp.put("last_ping_at", Instant.now().toString());
                resp.put("server_version", "unavailable (SDK ping does not expose version)");
            } catch (Exception e) {
                resp.put("connected", false);
                resp.put("error", e.getMessage());
            } finally {
                try { client.close(); } catch (Exception e) { /* best effort */ }
            }
        } catch (Exception e) {
            resp.put("connected", false);
            resp.put("error", e.getMessage());
        }

        return resp;
    }

    // --- SIGTERM handling ---

    public int handleShutdown() {
        RunState s = state.get();
        if (s == RunState.IDLE) return 0;

        if (s.canStop()) {
            cancelStartingTimeout();
            state.set(RunState.STOPPING);
            CountDownLatch latch = stopLatch;
            if (latch != null) latch.countDown();
            finishRun(false);
        }

        BurninSummary report = lastReport;
        if (report != null && report.verdict != null) {
            if (VerdictResult.FAILED.equals(report.verdict.result)) return 1;
            return 0;
        }
        return 0;
    }

    public void shutdown() {
        timeoutScheduler.shutdownNow();
    }

    // --- Helpers ---

    private Map<String, Object> buildPatternResponse(String pattern, PatternSummary ps, boolean live) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", true);
        m.put("state", ps.status);
        m.put("channels", ps.channels);
        m.put("sent", ps.sent);

        boolean isRpc = "commands".equals(pattern) || "queries".equals(pattern);

        if (isRpc) {
            m.put("senders_per_channel", ps.sendersPerChannel);
            m.put("responders_per_channel", ps.respondersPerChannel);
            m.put("responses_success", ps.responsesSuccess);
            m.put("responses_timeout", ps.responsesTimeout);
            m.put("responses_error", ps.responsesError);
        } else {
            m.put("producers_per_channel", ps.producersPerChannel);
            m.put("consumers_per_channel", ps.consumersPerChannel);
            m.put("received", ps.received);
            m.put("lost", ps.lost);
            m.put("duplicated", ps.duplicated);
            m.put("corrupted", ps.corrupted);
            m.put("out_of_order", ps.outOfOrder);
            if ("events_store".equals(pattern)) {
                m.put("unconfirmed", ps.unconfirmed);
            }
            m.put("loss_pct", ps.lossPct);
            if ("events".equals(pattern) || "events_store".equals(pattern)) {
                m.put("consumer_group", ps.consumerGroup);
            }
        }

        m.put("errors", ps.errors);
        m.put("reconnections", ps.reconnections);
        m.put("target_rate", (int) ps.targetRate);

        if (live) {
            m.put("actual_rate", Math.round(ps.slidingRateMsgsSec * 10.0) / 10.0);
        } else {
            m.put("avg_rate", Math.round(ps.avgThroughputMsgsSec * 10.0) / 10.0);
        }
        m.put("peak_rate", Math.round(ps.peakThroughputMsgsSec * 10.0) / 10.0);
        m.put("bytes_sent", ps.bytesSent);
        m.put("bytes_received", ps.bytesReceived);

        Map<String, Object> latency = new LinkedHashMap<>();
        if (isRpc) {
            latency.put("p50_ms", ps.rpcP50Ms);
            latency.put("p95_ms", ps.rpcP95Ms);
            latency.put("p99_ms", ps.rpcP99Ms);
            latency.put("p999_ms", ps.rpcP999Ms);
        } else {
            latency.put("p50_ms", ps.latencyP50Ms);
            latency.put("p95_ms", ps.latencyP95Ms);
            latency.put("p99_ms", ps.latencyP99Ms);
            latency.put("p999_ms", ps.latencyP999Ms);
        }
        m.put("latency", latency);

        // Per-worker breakdowns
        if (isRpc) {
            if (ps.senders != null && !ps.senders.isEmpty()) {
                if (!live) {
                    for (Map<String, Object> s : ps.senders) {
                        Object ar = s.remove("actual_rate");
                        if (ar != null) s.put("avg_rate", ar);
                    }
                }
                m.put("senders", ps.senders);
            }
            if (ps.responders != null && !ps.responders.isEmpty()) {
                m.put("responders", ps.responders);
            }
        } else {
            if (ps.producers != null && !ps.producers.isEmpty()) {
                if (!live) {
                    for (Map<String, Object> p : ps.producers) {
                        Object ar = p.remove("actual_rate");
                        if (ar != null) p.put("avg_rate", ar);
                    }
                }
                m.put("producers", ps.producers);
            }
            if (ps.consumers != null && !ps.consumers.isEmpty()) {
                m.put("consumers", ps.consumers);
            }
        }

        return m;
    }
}
