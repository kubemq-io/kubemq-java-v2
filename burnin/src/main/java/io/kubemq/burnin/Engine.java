// Engine: full orchestrator for PatternGroups, warmup, periodic tasks, 2-phase shutdown.
// v2: Uses PatternGroup (multi-channel) instead of single workers per pattern.
// Creates 3 clients (PubSubClient, QueuesClient, CQClient) with ReconnectionConfig.

package io.kubemq.burnin;

import io.kubemq.sdk.client.ReconnectionConfig;
import io.kubemq.sdk.pubsub.*;
import io.kubemq.sdk.queues.*;
import io.kubemq.sdk.cq.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Closeable;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * All 6 messaging patterns used in the burn-in test.
 */
final class AllPatterns {
    static final String[] NAMES = {
            "events", "events_store", "queue_stream", "queue_simple", "commands", "queries"
    };
    private AllPatterns() {}
}

/**
 * Full burn-in test orchestrator (v2). Manages three KubeMQ clients (PubSub, Queues, CQ),
 * PatternGroups with multi-channel support, warmup, periodic reporting, and 2-phase shutdown.
 */
public final class Engine implements Closeable, ClientRecreator {

    private static final String CHANNEL_PREFIX = "java_burnin_";
    private static final int WARMUP_COUNT = 3;

    private final BurninConfig cfg;
    // Shared clients for channel management, ping, warmup
    private PubSubClient pubSubClient;
    private QueuesClient queuesClient;
    private CQClient cqClient;
    // Per-pattern clients for independent gRPC streams (eliminates stream contention)
    private final Map<String, PubSubClient> perPatternPubSub = new ConcurrentHashMap<>();
    private final Map<String, QueuesClient> perPatternQueues = new ConcurrentHashMap<>();
    private final Map<String, CQClient> perPatternCQ = new ConcurrentHashMap<>();
    private final Map<String, PatternGroup> patternGroups = new LinkedHashMap<>();
    private HttpServer httpServer;
    private long uptimeStartMs;
    private String startedAt = "";
    private String endedAt = "";
    private long testStartMs;
    private long producersStoppedMs;
    private final Map<String, String> patternStatus = new ConcurrentHashMap<>();
    private double baselineRss;
    private double peakRss;
    private int peakWorkers;
    private int memoryBaselineDelaySec;
    private boolean memoryBaselineAdvisory;
    private ScheduledExecutorService scheduler;

    private static final Gson GSON = new GsonBuilder().create();

    private volatile Runnable onReady;
    private volatile BurninSummary lastSummary;
    private volatile boolean warmupActive;

    /** Snapshot of all pattern counters at producer-stop time (T2). */
    private volatile Map<String, PatternSnapshotData> producerStopSnapshot;

    public Engine(BurninConfig config) {
        this.cfg = config;
    }

    public void setOnReady(Runnable callback) {
        this.onReady = callback;
    }

    public BurninSummary getLastSummary() {
        return lastSummary != null ? lastSummary : buildSummary("running");
    }

    public Map<String, String> getPatternStatus() {
        return new HashMap<>(patternStatus);
    }

    /**
     * Get all PatternGroups for status/config responses.
     */
    public Map<String, PatternGroup> getPatternGroups() {
        return Collections.unmodifiableMap(patternGroups);
    }

    /**
     * Run the burn-in test. Blocks until duration expires or latch is triggered.
     */
    public void run(CountDownLatch shutdownLatch) throws Exception {
        uptimeStartMs = System.currentTimeMillis();
        startedAt = Instant.now().toString();

        // Step 0: Clear metrics registry from any previous run
        Metrics.clearRunMetrics();

        // Step 1: Create clients with reconnect config
        createClients();
        System.out.println("clients created, pinging broker at " + cfg.getBroker().getAddress());
        pubSubClient.ping();
        System.out.println("broker ping ok");

        // Step 2: Skip stale channel cleanup (channels auto-create on subscribe/send)
        System.out.println("skipping stale channel cleanup at startup (channels auto-create on subscribe/send)");

        // Benchmark mode: auto-set all rates to 0 (unlimited)
        if ("benchmark".equals(cfg.getMode())) {
            for (PatternConfig pc : cfg.getPatterns().values()) {
                pc.setRate(0);
            }
        }

        // Step 3: Create PatternGroups (N workers per pattern)
        createPatternGroups();

        // Set target rates for enabled patterns
        for (Map.Entry<String, PatternGroup> entry : patternGroups.entrySet()) {
            String pattern = entry.getKey();
            PatternConfig pc = cfg.getPatternConfig(pattern);
            int totalTargetRate = pc.getRate() * pc.getChannels();
            Metrics.setTargetRate(pattern, totalTargetRate);
            patternStatus.put(pattern, "starting");
        }

        // Step 5: Start ALL consumers/responders across ALL patterns
        // Each pattern gets its own dedicated client for independent gRPC streams
        for (PatternGroup pg : patternGroups.values()) {
            String pattern = pg.getPattern();
            PubSubClient ps = perPatternPubSub.getOrDefault(pattern, pubSubClient);
            QueuesClient qs = perPatternQueues.getOrDefault(pattern, queuesClient);
            CQClient cq = perPatternCQ.getOrDefault(pattern, cqClient);
            pg.startConsumers(ps, qs, cq);
            System.out.println("consumers started: " + pattern + " (" + pg.getChannelCount() + " channels)");
        }

        // Brief delay to allow subscriptions to establish
        sleepQuietly(1000);

        // Step 6: Run warmup on EVERY channel (parallel with concurrency limit)
        runMultiChannelWarmup();

        // Step 7: Start ALL producers/senders across ALL patterns
        for (PatternGroup pg : patternGroups.values()) {
            String pattern = pg.getPattern();
            PubSubClient ps = perPatternPubSub.getOrDefault(pattern, pubSubClient);
            QueuesClient qs = perPatternQueues.getOrDefault(pattern, queuesClient);
            CQClient cq = perPatternCQ.getOrDefault(pattern, cqClient);
            pg.startProducers(ps, qs, cq);
            System.out.println("producers started: " + pattern + " (" + pg.getChannelCount() + " channels)");
        }

        // Step 8: Print banner
        printBanner();

        // Signal ready to RunManager
        testStartMs = System.currentTimeMillis();
        for (String p : patternGroups.keySet()) {
            patternStatus.put(p, "running");
        }
        Runnable ready = onReady;
        if (ready != null) ready.run();

        // Warmup period wait + reset (metrics-exclusion window)
        double warmupSec = Config.warmupDurationSec(cfg);
        if (warmupSec > 0) {
            warmupActive = true;
            Metrics.setWarmupActive(1);
            System.out.println("warmup period: " + warmupSec + "s");
            safeDelay((long) (warmupSec * 1000), shutdownLatch);
            if (shutdownLatch.getCount() == 0) return;
            for (PatternGroup pg : patternGroups.values()) {
                pg.resetAfterWarmup();
            }
            testStartMs = System.currentTimeMillis();
            warmupActive = false;
            Metrics.setWarmupActive(0);
            System.out.println("warmup complete, counters reset");
        }

        // Step 9: Start periodic tasks
        startPeriodicTasks();

        // Disconnect manager
        double disconnectInterval = Config.forcedDisconnectIntervalSec(cfg);
        double disconnectDuration = Config.forcedDisconnectDurationSec(cfg);
        DisconnectManager dm = null;
        if (disconnectInterval > 0) {
            dm = new DisconnectManager(disconnectInterval, disconnectDuration, this);
            dm.start();
            System.out.println("disconnect manager enabled");
        }

        // Wait for duration or signal
        double dur = Config.durationSec(cfg.getDuration());
        double maxDur = Config.maxDurationSec(cfg);
        if (dur <= 0 || dur > maxDur) {
            dur = maxDur;
        }
        System.out.println("running for " + (long) dur + "s (or until stopped)");
        safeDelay((long) (dur * 1000), shutdownLatch);

        if (dm != null) dm.stop();
    }

    /**
     * Execute 2-phase shutdown with drain period.
     */
    private static final int CLEANUP_TIMEOUT_SECONDS = 30;

    public boolean shutdown() {
        endedAt = Instant.now().toString();

        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        System.out.println("initiating 2-phase shutdown");

        int drainSec = cfg.getShutdown().getDrainTimeoutSeconds();

        // Phase 1: stop ALL producers across ALL patterns
        for (PatternGroup pg : patternGroups.values()) {
            patternStatus.put(pg.getPattern(), "draining");
        }

        // Snapshot all counters BEFORE stopping producers so the final report
        // reflects a clean measurement window and excludes drain-phase events.
        producerStopSnapshot = capturePatternSnapshots();
        System.out.println("producer-stop snapshot captured");

        producersStoppedMs = System.currentTimeMillis();
        for (PatternGroup pg : patternGroups.values()) {
            pg.stopProducers();
        }

        System.out.println("producers stopped, draining for " + drainSec + "s");
        sleepQuietly(drainSec * 1000L);

        // Phase 2: stop ALL consumers across ALL patterns
        for (PatternGroup pg : patternGroups.values()) {
            pg.stopConsumers();
            patternStatus.put(pg.getPattern(), "stopped");
        }
        System.out.println("consumers stopped");

        if (baselineRss == 0) {
            baselineRss = getRssMb();
        }

        // Build summary and verdict BEFORE cleanup so the report is available
        // even if channel cleanup hangs or times out (matches Go SDK approach).
        BurninSummary summary = buildSummary("completed");
        this.lastSummary = summary;
        Verdict verdict = Report.generateVerdict(summary, cfg.getThresholds(), cfg.getMode(),
                cfg.getEnabledPatterns(), resolvePatternThresholds());
        summary.verdict = verdict;

        if (cfg.getShutdown().isCleanupChannels()) {
            ExecutorService cleanupExec = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "burnin-cleanup");
                t.setDaemon(true);
                return t;
            });
            Future<?> cleanupFuture = cleanupExec.submit(this::cleanStaleChannels);
            try {
                cleanupFuture.get(CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                cleanupFuture.cancel(true);
                System.err.println("channel cleanup timed out after " + CLEANUP_TIMEOUT_SECONDS + "s");
            } catch (Exception e) {
                System.err.println("channel cleanup error: " + e.getMessage());
            } finally {
                cleanupExec.shutdownNow();
            }
        }

        closeClientsQuietly();

        return verdict.passed;
    }

    // --- ClientRecreator interface ---

    @Override
    public void closeClient() {
        for (PatternGroup pg : patternGroups.values()) {
            patternStatus.put(pg.getPattern(), "recovering");
            pg.startDowntime();
            Metrics.setActiveConnections(pg.getPattern(), 0);
        }
        closeClientsQuietly();
    }

    @Override
    public void recreateClient() {
        try {
            // Close old clients to release gRPC channels, thread pools, and executors
            closeClientQuietly(pubSubClient);
            closeClientQuietly(queuesClient);
            closeClientQuietly(cqClient);

            createClients();
            for (PatternGroup pg : patternGroups.values()) {
                patternStatus.put(pg.getPattern(), "running");
                pg.stopDowntime();
                pg.incReconnection();
            }
        } catch (Exception e) {
            System.err.println("failed to recreate clients: " + e.getMessage());
        }
    }

    /**
     * Get the per-pattern PubSubClient (for warmup to use the right client).
     */
    PubSubClient getPubSubClientForPattern(String pattern) {
        return perPatternPubSub.getOrDefault(pattern, pubSubClient);
    }

    CQClient getCQClientForPattern(String pattern) {
        return perPatternCQ.getOrDefault(pattern, cqClient);
    }

    private void closeClientQuietly(AutoCloseable client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                // best effort
            }
        }
    }

    // --- Private: Client Creation ---

    private void createClients() throws Exception {
        long initDelayMs = (long) (Config.parseDuration(cfg.getRecovery().getReconnectInterval()) * 1000);
        long maxDelayMs = (long) (Config.parseDuration(cfg.getRecovery().getReconnectMaxInterval()) * 1000);
        double mult = cfg.getRecovery().getReconnectMultiplier();
        ReconnectionConfig reconnectConfig = ReconnectionConfig.builder()
                .maxReconnectAttempts(-1)
                .initialReconnectDelayMs(Math.max(100, Math.min(initDelayMs, 5000)))
                .maxReconnectDelayMs(Math.max(1000, Math.min(maxDelayMs, 120000)))
                .reconnectBackoffMultiplier(Math.max(1.5, Math.min(mult, 3.0)))
                .reconnectJitterEnabled(true)
                .build();

        String clientId = cfg.getBroker().getClientIdPrefix() + "-" + cfg.getRunId();

        // Shared clients for channel management, ping, and warmup
        pubSubClient = PubSubClient.builder()
                .address(cfg.getBroker().getAddress())
                .clientId(clientId + "-pubsub")
                .reconnectionConfig(reconnectConfig)
                .build();

        queuesClient = QueuesClient.builder()
                .address(cfg.getBroker().getAddress())
                .clientId(clientId + "-queues")
                .reconnectionConfig(reconnectConfig)
                .build();

        cqClient = CQClient.builder()
                .address(cfg.getBroker().getAddress())
                .clientId(clientId + "-cq")
                .reconnectionConfig(reconnectConfig)
                .build();

        // Per-pattern clients: each pattern gets its own client with an independent
        // gRPC stream, eliminating lock contention between patterns that share a
        // client type (e.g., events vs events_store on PubSubClient).
        closePerPatternClients();
        for (String pattern : AllPatterns.NAMES) {
            PatternConfig pc = cfg.getPatternConfig(pattern);
            if (pc == null || !pc.isEnabled()) continue;

            switch (pattern) {
                case "events":
                case "events_store":
                    perPatternPubSub.put(pattern, PubSubClient.builder()
                            .address(cfg.getBroker().getAddress())
                            .clientId(clientId + "-" + pattern)
                            .reconnectionConfig(reconnectConfig)
                            .build());
                    break;
                case "queue_stream":
                case "queue_simple":
                    perPatternQueues.put(pattern, QueuesClient.builder()
                            .address(cfg.getBroker().getAddress())
                            .clientId(clientId + "-" + pattern)
                            .reconnectionConfig(reconnectConfig)
                            .build());
                    break;
                case "commands":
                case "queries":
                    perPatternCQ.put(pattern, CQClient.builder()
                            .address(cfg.getBroker().getAddress())
                            .clientId(clientId + "-" + pattern)
                            .reconnectionConfig(reconnectConfig)
                            .build());
                    break;
            }
        }
    }

    private void closePerPatternClients() {
        perPatternPubSub.values().forEach(c -> closeClientQuietly(c));
        perPatternPubSub.clear();
        perPatternQueues.values().forEach(c -> closeClientQuietly(c));
        perPatternQueues.clear();
        perPatternCQ.values().forEach(c -> closeClientQuietly(c));
        perPatternCQ.clear();
    }

    private void createPatternGroups() {
        String rid = cfg.getRunId();
        for (String pattern : AllPatterns.NAMES) {
            PatternConfig pc = cfg.getPatternConfig(pattern);
            if (pc != null && pc.isEnabled()) {
                PatternGroup pg = new PatternGroup(pattern, pc, cfg, rid);
                patternGroups.put(pattern, pg);
            }
        }
    }

    // --- Private: Channels ---

    private void cleanStaleChannels() {
        System.out.println("cleaning stale channels with prefix '" + CHANNEL_PREFIX + "'");
        int count = 0;

        count += cleanChannelType(
                () -> pubSubClient.listEventsChannels(CHANNEL_PREFIX),
                PubSubChannel::getName,
                name -> pubSubClient.deleteEventsChannel(name));
        count += cleanChannelType(
                () -> pubSubClient.listEventsStoreChannels(CHANNEL_PREFIX),
                PubSubChannel::getName,
                name -> pubSubClient.deleteEventsStoreChannel(name));
        count += cleanChannelType(
                () -> queuesClient.listQueuesChannels(CHANNEL_PREFIX),
                QueuesChannel::getName,
                name -> queuesClient.deleteQueuesChannel(name));
        count += cleanChannelType(
                () -> cqClient.listCommandsChannels(CHANNEL_PREFIX),
                CQChannel::getName,
                name -> cqClient.deleteCommandsChannel(name));
        count += cleanChannelType(
                () -> cqClient.listQueriesChannels(CHANNEL_PREFIX),
                CQChannel::getName,
                name -> cqClient.deleteQueriesChannel(name));

        System.out.println("cleaned " + count + " stale channels");
    }

    static <T> int cleanChannelType(ChannelLister<T> lister, ChannelNameExtractor<T> nameExtractor, ChannelDeleter deleter) {
        int deleted = 0;
        try {
            List<T> channels = lister.list();
            if (channels == null) return 0;
            for (T ch : channels) {
                try {
                    deleter.delete(nameExtractor.getName(ch));
                    deleted++;
                } catch (Exception e) {
                    // best effort
                }
            }
        } catch (Exception e) {
            // channel type may not exist
        }
        return deleted;
    }

    @FunctionalInterface
    interface ChannelLister<T> {
        List<T> list() throws Exception;
    }

    @FunctionalInterface
    interface ChannelNameExtractor<T> {
        String getName(T channel);
    }

    @FunctionalInterface
    interface ChannelDeleter {
        void delete(String name) throws Exception;
    }

    // --- Private: Multi-Channel Warmup ---

    private void runMultiChannelWarmup() {
        System.out.println("running multi-channel warmup verification");
        int maxParallel = cfg.getWarmup().getMaxParallelChannels();
        int timeoutMs = cfg.getWarmup().getTimeoutPerChannelMs();
        int maxRetries = 3;

        ExecutorService warmupPool = Executors.newFixedThreadPool(
                Math.min(maxParallel, Runtime.getRuntime().availableProcessors() * 2),
                r -> {
                    Thread t = new Thread(r, "warmup-pool");
                    t.setDaemon(true);
                    return t;
                });

        Semaphore semaphore = new Semaphore(maxParallel);
        List<Future<Boolean>> futures = new ArrayList<>();
        List<String> failedChannels = new ArrayList<>();

        for (PatternGroup pg : patternGroups.values()) {
            for (BaseWorker w : pg.getWorkers()) {
                futures.add(warmupPool.submit(() -> {
                    semaphore.acquire();
                    try {
                        return warmupChannel(w.getPattern(), w.getChannelName(), timeoutMs, maxRetries);
                    } finally {
                        semaphore.release();
                    }
                }));
            }
        }

        // Collect results
        for (int i = 0; i < futures.size(); i++) {
            try {
                Boolean result = futures.get(i).get(timeoutMs * (maxRetries + 1L), TimeUnit.MILLISECONDS);
                if (result == null || !result) {
                    failedChannels.add("channel-" + i);
                }
            } catch (Exception e) {
                failedChannels.add("channel-" + i + " (timeout/error)");
            }
        }

        warmupPool.shutdownNow();

        if (!failedChannels.isEmpty()) {
            System.err.println("WARNING: warmup failed for " + failedChannels.size() + " channels");
            // Fail-fast: if any channel fails warmup after all retries
            throw new RuntimeException("Warmup failed for " + failedChannels.size() + " channels. Run aborting.");
        }

        System.out.println("multi-channel warmup verification complete");
    }

    private boolean warmupChannel(String pattern, String channelName, int timeoutMs, int maxRetries) {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                boolean ok = doWarmupChannel(pattern, channelName, timeoutMs);
                if (ok) return true;
            } catch (Exception e) {
                System.err.println("warmup " + pattern + " " + channelName + " attempt " + (attempt + 1) + " error: " + e.getMessage());
            }
        }
        return false;
    }

    private boolean doWarmupChannel(String pattern, String channelName, int timeoutMs) throws Exception {
        switch (pattern) {
            case "events":
                return warmupEventsChannel(channelName, timeoutMs);
            case "events_store":
                return warmupEventsStoreChannel(channelName, timeoutMs);
            case "queue_stream":
            case "queue_simple":
                // Skip warmup sends for queue patterns — queue channels
                // auto-create on first send/poll, and warmup messages leave
                // unacked items that cause false duplicates.
                System.out.println("skipping warmup for queue pattern " + pattern + " channel " + channelName);
                return true;
            case "commands":
                return warmupCommandsChannel(channelName, timeoutMs);
            case "queries":
                return warmupQueriesChannel(channelName, timeoutMs);
            default:
                return true;
        }
    }

    private boolean warmupEventsChannel(String channelName, int timeoutMs) {
        AtomicInteger count = new AtomicInteger(0);
        try {
            EventsSubscription subscription = EventsSubscription.builder()
                    .channel(channelName)
                    .group("")
                    .onReceiveEventCallback(evt -> {
                        Map<String, String> tags = evt.getTags();
                        if (tags != null && "true".equals(tags.get("warmup"))) {
                            count.incrementAndGet();
                        }
                    })
                    .onErrorCallback(err -> {})
                    .build();
            pubSubClient.subscribeToEvents(subscription);
            sleepQuietly(500);

            for (int i = 0; i < WARMUP_COUNT; i++) {
                Map<String, String> tags = new HashMap<>();
                tags.put("warmup", "true");
                tags.put("content_hash", "00000000");
                EventMessage msg = EventMessage.builder()
                        .channel(channelName)
                        .body(("warmup-" + i).getBytes(StandardCharsets.UTF_8))
                        .tags(tags)
                        .build();
                pubSubClient.publishEvent(msg);
                sleepQuietly(100);
            }

            long deadline = System.currentTimeMillis() + timeoutMs;
            while (count.get() < 1 && System.currentTimeMillis() < deadline) {
                sleepQuietly(100);
            }
            subscription.cancel();
            return count.get() >= 1;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean warmupEventsStoreChannel(String channelName, int timeoutMs) {
        AtomicInteger count = new AtomicInteger(0);
        try {
            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel(channelName)
                    .group("")
                    .eventsStoreType(EventsStoreType.StartNewOnly)
                    .onReceiveEventCallback(evt -> {
                        Map<String, String> tags = evt.getTags();
                        if (tags != null && "true".equals(tags.get("warmup"))) {
                            count.incrementAndGet();
                        }
                    })
                    .onErrorCallback(err -> {})
                    .build();
            pubSubClient.subscribeToEventsStore(subscription);
            sleepQuietly(500);

            for (int i = 0; i < WARMUP_COUNT; i++) {
                Map<String, String> tags = new HashMap<>();
                tags.put("warmup", "true");
                tags.put("content_hash", "00000000");
                EventStoreMessage msg = EventStoreMessage.builder()
                        .channel(channelName)
                        .body(("warmup-" + i).getBytes(StandardCharsets.UTF_8))
                        .tags(tags)
                        .build();
                pubSubClient.publishEventStore(msg);
                sleepQuietly(100);
            }

            long deadline = System.currentTimeMillis() + timeoutMs;
            while (count.get() < 1 && System.currentTimeMillis() < deadline) {
                sleepQuietly(100);
            }
            subscription.cancel();
            return count.get() >= 1;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean warmupQueueChannel(String channelName, int timeoutMs) {
        int sent = 0;
        try {
            for (int i = 0; i < WARMUP_COUNT; i++) {
                Map<String, String> tags = new HashMap<>();
                tags.put("warmup", "true");
                tags.put("content_hash", "00000000");
                QueueMessage msg = QueueMessage.builder()
                        .channel(channelName)
                        .body(("warmup-" + i).getBytes(StandardCharsets.UTF_8))
                        .tags(tags)
                        .build();
                queuesClient.sendQueueMessage(msg);
                sent++;
                sleepQuietly(100);
            }
        } catch (Exception e) {
            // partial sends are OK
        }
        return sent >= 1;
    }

    private boolean warmupCommandsChannel(String channelName, int timeoutMs) {
        AtomicInteger responded = new AtomicInteger(0);
        int timeoutSeconds = Math.max(1, timeoutMs / 1000);
        try {
            CommandsSubscription subscription = CommandsSubscription.builder()
                    .channel(channelName)
                    .onReceiveCommandCallback(cmd -> {
                        try {
                            CommandResponseMessage response = CommandResponseMessage.builder()
                                    .commandReceived(cmd)
                                    .isExecuted(true)
                                    .build();
                            cqClient.sendResponseMessage(response);
                            responded.incrementAndGet();
                        } catch (Exception e) { /* best effort */ }
                    })
                    .onErrorCallback(err -> {})
                    .build();
            cqClient.subscribeToCommands(subscription);
            sleepQuietly(500);

            int success = 0;
            for (int i = 0; i < WARMUP_COUNT; i++) {
                try {
                    Map<String, String> tags = new HashMap<>();
                    tags.put("warmup", "true");
                    tags.put("content_hash", "00000000");
                    CommandMessage cmd = CommandMessage.builder()
                            .channel(channelName)
                            .body(("warmup-" + i).getBytes(StandardCharsets.UTF_8))
                            .timeoutInSeconds(timeoutSeconds)
                            .tags(tags)
                            .build();
                    cqClient.sendCommand(cmd);
                    success++;
                } catch (Exception e) { /* best effort */ }
                sleepQuietly(100);
            }

            sleepQuietly(500);
            subscription.cancel();
            return success >= 1;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean warmupQueriesChannel(String channelName, int timeoutMs) {
        AtomicInteger responded = new AtomicInteger(0);
        int timeoutSeconds = Math.max(1, timeoutMs / 1000);
        try {
            QueriesSubscription subscription = QueriesSubscription.builder()
                    .channel(channelName)
                    .onReceiveQueryCallback(query -> {
                        try {
                            QueryResponseMessage response = QueryResponseMessage.builder()
                                    .queryReceived(query)
                                    .isExecuted(true)
                                    .body(query.getBody())
                                    .build();
                            cqClient.sendResponseMessage(response);
                            responded.incrementAndGet();
                        } catch (Exception e) { /* best effort */ }
                    })
                    .onErrorCallback(err -> {})
                    .build();
            cqClient.subscribeToQueries(subscription);
            sleepQuietly(500);

            int success = 0;
            for (int i = 0; i < WARMUP_COUNT; i++) {
                try {
                    Map<String, String> tags = new HashMap<>();
                    tags.put("warmup", "true");
                    tags.put("content_hash", "00000000");
                    QueryMessage query = QueryMessage.builder()
                            .channel(channelName)
                            .body(("warmup-" + i).getBytes(StandardCharsets.UTF_8))
                            .timeoutInSeconds(timeoutSeconds)
                            .tags(tags)
                            .build();
                    cqClient.sendQuery(query);
                    success++;
                } catch (Exception e) { /* best effort */ }
                sleepQuietly(100);
            }

            sleepQuietly(500);
            subscription.cancel();
            return success >= 1;
        } catch (Exception e) {
            return false;
        }
    }

    // --- Private: Periodic Tasks ---

    private void startPeriodicTasks() {
        scheduler = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "burnin-scheduler");
            t.setDaemon(true);
            return t;
        });

        long reportIntervalMs = (long) (Config.reportIntervalSec(cfg) * 1000);

        // Periodic report
        scheduler.scheduleAtFixedRate(
                this::periodicReport, reportIntervalMs, reportIntervalMs, TimeUnit.MILLISECONDS);

        // Peak rate + sliding rate advance (every 1s)
        scheduler.scheduleAtFixedRate(() -> {
            for (PatternGroup pg : patternGroups.values()) {
                for (BaseWorker w : pg.getWorkers()) {
                    w.getPeakRate().advance();
                    w.getSlidingRate().advance();
                    for (BaseWorker.ProducerStat ps : w.getProducerStats().values()) {
                        ps.rate.advance();
                    }
                    for (BaseWorker.SenderStat ss : w.getSenderStats().values()) {
                        ss.rate.advance();
                    }
                }
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);

        // Uptime + active workers (every 1s)
        scheduler.scheduleAtFixedRate(() -> {
            double uptimeSec = (System.currentTimeMillis() - uptimeStartMs) / 1000.0;
            Metrics.setUptime(uptimeSec);
            int threads = Thread.activeCount();
            Metrics.setActiveWorkers(threads);
            if (threads > peakWorkers) peakWorkers = threads;
        }, 1000, 1000, TimeUnit.MILLISECONDS);

        // Memory baseline delay
        double runDurationSec = Config.durationSec(cfg.getDuration());
        if (runDurationSec < 60) {
            memoryBaselineDelaySec = 0;
            memoryBaselineAdvisory = true;
            baselineRss = getRssMb();
        } else if (runDurationSec < 300) {
            memoryBaselineDelaySec = 60;
            memoryBaselineAdvisory = true;
        } else {
            memoryBaselineDelaySec = 300;
            memoryBaselineAdvisory = false;
        }

        // Memory tracking (every 10s)
        scheduler.scheduleAtFixedRate(() -> {
            double rss = getRssMb();
            if (rss > peakRss) peakRss = rss;
            if (baselineRss == 0 && testStartMs > 0) {
                double testElapsed = (System.currentTimeMillis() - testStartMs) / 1000.0;
                if (testElapsed >= memoryBaselineDelaySec) {
                    baselineRss = rss;
                    System.out.println("memory baseline set: " + String.format("%.1f", rss) + " MB");
                }
            }
        }, 10000, 10000, TimeUnit.MILLISECONDS);

        // Timestamp store purge (every 60s)
        scheduler.scheduleAtFixedRate(() -> {
            for (PatternGroup pg : patternGroups.values()) {
                for (BaseWorker w : pg.getWorkers()) {
                    w.getTsStore().purge(60_000_000_000L);
                }
            }
        }, 60000, 60000, TimeUnit.MILLISECONDS);

        peakRss = getRssMb();
    }

    private void periodicReport() {
        double elapsed = testStartMs > 0
                ? (System.currentTimeMillis() - testStartMs) / 1000.0
                : (System.currentTimeMillis() - uptimeStartMs) / 1000.0;
        double rss = getRssMb();

        for (PatternGroup pg : patternGroups.values()) {
            for (BaseWorker w : pg.getWorkers()) {
                Map<String, Long> gaps = w.getTracker().detectGaps();
                for (Map.Entry<String, Long> entry : gaps.entrySet()) {
                    Metrics.incLost(pg.getPattern(), entry.getValue());
                }
            }

            Metrics.setConsumerLag(pg.getPattern(), Math.max(0, pg.totalSent() - pg.totalReceived()));
            if (elapsed > 0) {
                Metrics.setActualRate(pg.getPattern(), pg.totalSent() / elapsed);
            }
        }

        if ("json".equals(cfg.getLogging().getFormat())) {
            Map<String, Object> patternsObj = new LinkedHashMap<>();
            for (PatternGroup pg : patternGroups.values()) {
                long rate = elapsed > 0 ? (long) (pg.totalSent() / elapsed) : 0;
                Map<String, Object> pd = new LinkedHashMap<>();
                pd.put("sent", pg.totalSent());
                pd.put("recv", pg.totalReceived());
                pd.put("lost", pg.totalLost());
                pd.put("dup", pg.totalDuplicated());
                pd.put("err", pg.totalErrors());
                pd.put("channels", pg.getChannelCount());
                pd.put("p99_ms", pg.getPatternLatencyAccum().percentileMs(99));
                pd.put("rate", rate);
                patternsObj.put(pg.getPattern(), pd);
            }

            Map<String, Object> logEntry = new LinkedHashMap<>();
            logEntry.put("ts", Instant.now().toString());
            logEntry.put("level", "info");
            logEntry.put("msg", "periodic_status");
            logEntry.put("uptime_s", (long) elapsed);
            logEntry.put("mode", cfg.getMode());
            logEntry.put("rss_mb", (long) rss);
            logEntry.put("patterns", patternsObj);

            System.out.println(GSON.toJson(logEntry));
        } else {
            String ts = Instant.now().toString().replace("T", " ").replaceAll("\\.\\d+Z", " UTC");
            String uptimeStr = formatDuration(elapsed);
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(ts).append("] BURN-IN STATUS | uptime=").append(uptimeStr)
                    .append(" mode=").append(cfg.getMode())
                    .append(" rss=").append(String.format("%.0f", rss)).append("MB\n");

            for (PatternGroup pg : patternGroups.values()) {
                String rate = elapsed > 0 ? String.format("%.0f", pg.totalSent() / elapsed) : "0";
                String chLabel = pg.getChannelCount() > 1 ? " (" + pg.getChannelCount() + "ch)" : "";
                boolean isRpc = "commands".equals(pg.getPattern()) || "queries".equals(pg.getPattern());

                if (isRpc) {
                    sb.append(String.format("  %-14s sent=%-8d resp=%-8d tout=%-4d err=%-4d p99=%.1fms rate=%s/s%s%n",
                            pg.getPattern(), pg.totalSent(), pg.totalRpcSuccess(),
                            pg.totalRpcTimeout(), pg.totalErrors(),
                            pg.getPatternRpcLatencyAccum().percentileMs(99), rate, chLabel));
                } else {
                    sb.append(String.format("  %-14s sent=%-8d recv=%-8d lost=%-4d dup=%-4d err=%-4d p99=%.1fms rate=%s/s%s%n",
                            pg.getPattern(), pg.totalSent(), pg.totalReceived(),
                            pg.totalLost(), pg.totalDuplicated(),
                            pg.totalErrors(), pg.getPatternLatencyAccum().percentileMs(99), rate, chLabel));
                }
            }

            System.out.print(sb.toString());
        }
    }

    // --- Private: Snapshot at producer-stop time (T2) ---

    /**
     * Point-in-time snapshot of all PatternGroup counters.
     */
    static final class PatternSnapshotData {
        long sent, received, lost, duplicated, corrupted, outOfOrder;
        long errors, reconnections, bytesSent, bytesReceived;
        long rpcSuccess, rpcTimeout, rpcError, unconfirmed;
        double peakRate, downtimeSeconds;
        double latencyP50Ms, latencyP95Ms, latencyP99Ms, latencyP999Ms;
        double rpcLatencyP50Ms, rpcLatencyP95Ms, rpcLatencyP99Ms, rpcLatencyP999Ms;
        // Per-channel data for verdict checks
        List<ChannelData> channelDataList = new ArrayList<>();
        // Per-worker stats (serializable copies)
        List<Map<String, Object>> senders = new ArrayList<>();
        List<Map<String, Object>> responders = new ArrayList<>();
        List<Map<String, Object>> producers = new ArrayList<>();
        List<Map<String, Object>> consumers = new ArrayList<>();
    }

    private Map<String, PatternSnapshotData> capturePatternSnapshots() {
        Map<String, PatternSnapshotData> snapshots = new LinkedHashMap<>();
        for (Map.Entry<String, PatternGroup> entry : patternGroups.entrySet()) {
            String pattern = entry.getKey();
            PatternGroup pg = entry.getValue();
            PatternConfig pc = cfg.getPatternConfig(pattern);
            boolean isRpc = "commands".equals(pattern) || "queries".equals(pattern);

            PatternSnapshotData snap = new PatternSnapshotData();
            snap.sent = pg.totalSent();
            snap.received = pg.totalReceived();
            snap.lost = pg.totalLost();
            snap.duplicated = pg.totalDuplicated();
            snap.corrupted = pg.totalCorrupted();
            snap.outOfOrder = pg.totalOutOfOrder();
            snap.errors = pg.totalErrors();
            snap.reconnections = pg.totalReconnections();
            snap.bytesSent = pg.totalBytesSent();
            snap.bytesReceived = pg.totalBytesReceived();
            snap.rpcSuccess = pg.totalRpcSuccess();
            snap.rpcTimeout = pg.totalRpcTimeout();
            snap.rpcError = pg.totalRpcError();
            snap.unconfirmed = pg.totalUnconfirmed();
            snap.peakRate = pg.maxPeakRate();
            snap.downtimeSeconds = pg.maxDowntimeSeconds();
            snap.latencyP50Ms = pg.getPatternLatencyAccum().percentileMs(50);
            snap.latencyP95Ms = pg.getPatternLatencyAccum().percentileMs(95);
            snap.latencyP99Ms = pg.getPatternLatencyAccum().percentileMs(99);
            snap.latencyP999Ms = pg.getPatternLatencyAccum().percentileMs(99.9);
            snap.rpcLatencyP50Ms = pg.getPatternRpcLatencyAccum().percentileMs(50);
            snap.rpcLatencyP95Ms = pg.getPatternRpcLatencyAccum().percentileMs(95);
            snap.rpcLatencyP99Ms = pg.getPatternRpcLatencyAccum().percentileMs(99);
            snap.rpcLatencyP999Ms = pg.getPatternRpcLatencyAccum().percentileMs(99.9);

            // Per-channel data
            for (BaseWorker w : pg.getWorkers()) {
                long chSent = w.getSent();
                long chReceived = w.getReceived();
                long chLost = w.getTracker().totalLost();
                long chDuplicated = w.getTracker().totalDuplicates();
                long chCorrupted = w.getCorrupted();
                long chErrors = w.getErrors();
                int chConsumersPerChannel = isRpc ? 0 : pc.getConsumersPerChannel();
                snap.channelDataList.add(new ChannelData(w.getChannelIndex(), chSent, chReceived,
                        chLost, chDuplicated, chCorrupted, chErrors, chConsumersPerChannel));
            }

            // Per-worker producer/consumer stats
            if (isRpc) {
                for (BaseWorker w : pg.getWorkers()) {
                    for (BaseWorker.SenderStat ss : w.getSenderStats().values()) {
                        Map<String, Object> sm = new LinkedHashMap<>();
                        sm.put("id", ss.id);
                        sm.put("sent", ss.sent.get());
                        sm.put("responses_success", ss.responsesSuccess.get());
                        sm.put("responses_timeout", ss.responsesTimeout.get());
                        sm.put("responses_error", ss.responsesError.get());
                        sm.put("errors", ss.errors.get());
                        sm.put("actual_rate", Math.round(ss.rate.getRate() * 10.0) / 10.0);
                        Map<String, Object> lat = new LinkedHashMap<>();
                        lat.put("p50_ms", ss.latency.percentileMs(50));
                        lat.put("p95_ms", ss.latency.percentileMs(95));
                        lat.put("p99_ms", ss.latency.percentileMs(99));
                        lat.put("p999_ms", ss.latency.percentileMs(99.9));
                        sm.put("latency", lat);
                        snap.senders.add(sm);
                    }
                    for (BaseWorker.ResponderStat rs : w.getResponderStats().values()) {
                        Map<String, Object> rm = new LinkedHashMap<>();
                        rm.put("id", rs.id);
                        rm.put("responded", rs.responded.get());
                        rm.put("errors", rs.errors.get());
                        snap.responders.add(rm);
                    }
                }
            } else {
                for (BaseWorker w : pg.getWorkers()) {
                    for (BaseWorker.ProducerStat pstat : w.getProducerStats().values()) {
                        Map<String, Object> pm = new LinkedHashMap<>();
                        pm.put("id", pstat.id);
                        pm.put("sent", pstat.sent.get());
                        pm.put("errors", pstat.errors.get());
                        pm.put("actual_rate", Math.round(pstat.rate.getRate() * 10.0) / 10.0);
                        Map<String, Object> lat = new LinkedHashMap<>();
                        lat.put("p50_ms", pstat.latency.percentileMs(50));
                        lat.put("p95_ms", pstat.latency.percentileMs(95));
                        lat.put("p99_ms", pstat.latency.percentileMs(99));
                        lat.put("p999_ms", pstat.latency.percentileMs(99.9));
                        pm.put("latency", lat);
                        snap.producers.add(pm);
                    }
                    for (BaseWorker.ConsumerStat cstat : w.getConsumerStats().values()) {
                        Map<String, Object> cm = new LinkedHashMap<>();
                        cm.put("id", cstat.id);
                        cm.put("received", cstat.received.get());
                        cm.put("corrupted", cstat.corrupted.get());
                        cm.put("errors", cstat.errors.get());
                        Map<String, Object> lat = new LinkedHashMap<>();
                        lat.put("p50_ms", cstat.latency.percentileMs(50));
                        lat.put("p95_ms", cstat.latency.percentileMs(95));
                        lat.put("p99_ms", cstat.latency.percentileMs(99));
                        lat.put("p999_ms", cstat.latency.percentileMs(99.9));
                        cm.put("latency", lat);
                        snap.consumers.add(cm);
                    }
                }
            }

            snapshots.put(pattern, snap);
        }
        return snapshots;
    }

    // --- Private: Summary ---

    private BurninSummary buildSummary(String status) {
        long endMs = producersStoppedMs > 0 ? producersStoppedMs : System.currentTimeMillis();
        double elapsed = testStartMs > 0
                ? (endMs - testStartMs) / 1000.0
                : (endMs - uptimeStartMs) / 1000.0;

        Map<String, PatternSummary> patterns = new LinkedHashMap<>();

        for (Map.Entry<String, PatternGroup> entry : patternGroups.entrySet()) {
            String pattern = entry.getKey();
            PatternGroup pg = entry.getValue();
            PatternConfig pc = cfg.getPatternConfig(pattern);
            boolean isRpc = "commands".equals(pattern) || "queries".equals(pattern);

            // Use snapshot values if available (taken at producer-stop time T2),
            // otherwise fall back to live values.
            PatternSnapshotData snap = producerStopSnapshot != null
                    ? producerStopSnapshot.get(pattern) : null;

            long wSent, wReceived, lost, duplicated, corrupted, outOfOrder;
            long errors, reconnections, bytesSent, bytesReceived;
            long rpcSuccess, rpcTimeout, rpcError, unconfirmed;
            double peakRateVal, downtimeSeconds;
            double latP50, latP95, latP99, latP999;
            double rpcLatP50, rpcLatP95, rpcLatP99, rpcLatP999;

            if (snap != null) {
                wSent = snap.sent; wReceived = snap.received; lost = snap.lost;
                duplicated = snap.duplicated; corrupted = snap.corrupted; outOfOrder = snap.outOfOrder;
                errors = snap.errors; reconnections = snap.reconnections;
                bytesSent = snap.bytesSent; bytesReceived = snap.bytesReceived;
                rpcSuccess = snap.rpcSuccess; rpcTimeout = snap.rpcTimeout; rpcError = snap.rpcError;
                unconfirmed = snap.unconfirmed; peakRateVal = snap.peakRate;
                downtimeSeconds = snap.downtimeSeconds;
                latP50 = snap.latencyP50Ms; latP95 = snap.latencyP95Ms;
                latP99 = snap.latencyP99Ms; latP999 = snap.latencyP999Ms;
                rpcLatP50 = snap.rpcLatencyP50Ms; rpcLatP95 = snap.rpcLatencyP95Ms;
                rpcLatP99 = snap.rpcLatencyP99Ms; rpcLatP999 = snap.rpcLatencyP999Ms;
            } else {
                wSent = pg.totalSent(); wReceived = pg.totalReceived(); lost = pg.totalLost();
                duplicated = pg.totalDuplicated(); corrupted = pg.totalCorrupted();
                outOfOrder = pg.totalOutOfOrder();
                errors = pg.totalErrors(); reconnections = pg.totalReconnections();
                bytesSent = pg.totalBytesSent(); bytesReceived = pg.totalBytesReceived();
                rpcSuccess = pg.totalRpcSuccess(); rpcTimeout = pg.totalRpcTimeout();
                rpcError = pg.totalRpcError(); unconfirmed = pg.totalUnconfirmed();
                peakRateVal = pg.maxPeakRate(); downtimeSeconds = pg.maxDowntimeSeconds();
                latP50 = pg.getPatternLatencyAccum().percentileMs(50);
                latP95 = pg.getPatternLatencyAccum().percentileMs(95);
                latP99 = pg.getPatternLatencyAccum().percentileMs(99);
                latP999 = pg.getPatternLatencyAccum().percentileMs(99.9);
                rpcLatP50 = pg.getPatternRpcLatencyAccum().percentileMs(50);
                rpcLatP95 = pg.getPatternRpcLatencyAccum().percentileMs(95);
                rpcLatP99 = pg.getPatternRpcLatencyAccum().percentileMs(99);
                rpcLatP999 = pg.getPatternRpcLatencyAccum().percentileMs(99.9);
            }

            double lossPct = wSent > 0 ? (double) lost / wSent * 100 : 0;
            double avgTp = elapsed > 0 ? wSent / elapsed : 0;
            int totalTargetRate = pc.getRate() * pc.getChannels();

            PatternSummary ps = new PatternSummary();
            ps.status = patternStatus.getOrDefault(pattern, "unknown");
            ps.sent = wSent;
            ps.received = wReceived;
            ps.lost = lost;
            ps.duplicated = duplicated;
            ps.corrupted = corrupted;
            ps.outOfOrder = outOfOrder;
            ps.lossPct = lossPct;
            ps.errors = errors;
            ps.reconnections = reconnections;
            ps.downtimeSeconds = downtimeSeconds;

            // Latency from snapshot or live
            ps.latencyP50Ms = latP50;
            ps.latencyP95Ms = latP95;
            ps.latencyP99Ms = latP99;
            ps.latencyP999Ms = latP999;

            ps.avgThroughputMsgsSec = avgTp;
            ps.peakThroughputMsgsSec = peakRateVal;
            ps.slidingRateMsgsSec = snap != null ? 0 : pg.aggregateSlidingRate();
            ps.targetRate = totalTargetRate;
            ps.bytesSent = bytesSent;
            ps.bytesReceived = bytesReceived;

            // v2 multi-channel fields
            ps.channels = pc.getChannels();
            if (isRpc) {
                ps.sendersPerChannel = pc.getSendersPerChannel();
                ps.respondersPerChannel = pc.getRespondersPerChannel();
            } else {
                ps.producersPerChannel = pc.getProducersPerChannel();
                ps.consumersPerChannel = pc.getConsumersPerChannel();
            }
            ps.consumerGroup = pc.isConsumerGroup();
            ps.numConsumers = isRpc ? 0 : pc.getConsumersPerChannel();

            // Per-channel data from snapshot or live
            if (snap != null && snap.channelDataList != null) {
                ps.channelDataList = snap.channelDataList;
            } else {
                List<ChannelData> channelDataList = new ArrayList<>();
                for (BaseWorker w : pg.getWorkers()) {
                    long chSent = w.getSent();
                    long chReceived = w.getReceived();
                    long chLost = w.getTracker().totalLost();
                    long chDuplicated = w.getTracker().totalDuplicates();
                    long chCorrupted = w.getCorrupted();
                    long chErrors = w.getErrors();
                    int chConsumersPerChannel = isRpc ? 0 : pc.getConsumersPerChannel();
                    channelDataList.add(new ChannelData(w.getChannelIndex(), chSent, chReceived,
                            chLost, chDuplicated, chCorrupted, chErrors, chConsumersPerChannel));
                }
                ps.channelDataList = channelDataList;
            }

            if (isRpc) {
                ps.responsesSuccess = rpcSuccess;
                ps.responsesTimeout = rpcTimeout;
                ps.responsesError = rpcError;
                ps.rpcP50Ms = rpcLatP50;
                ps.rpcP95Ms = rpcLatP95;
                ps.rpcP99Ms = rpcLatP99;
                ps.rpcP999Ms = rpcLatP999;
                if (elapsed > 0) {
                    ps.avgThroughputRpcSec = rpcSuccess / elapsed;
                }

                // Per-sender/responder from snapshot or live
                if (snap != null) {
                    ps.senders = snap.senders;
                    ps.responders = snap.responders;
                } else {
                    List<Map<String, Object>> senderList = new ArrayList<>();
                    for (BaseWorker w : pg.getWorkers()) {
                        for (BaseWorker.SenderStat ss : w.getSenderStats().values()) {
                            Map<String, Object> sm = new LinkedHashMap<>();
                            sm.put("id", ss.id);
                            sm.put("sent", ss.sent.get());
                            sm.put("responses_success", ss.responsesSuccess.get());
                            sm.put("responses_timeout", ss.responsesTimeout.get());
                            sm.put("responses_error", ss.responsesError.get());
                            sm.put("errors", ss.errors.get());
                            sm.put("actual_rate", Math.round(ss.rate.getRate() * 10.0) / 10.0);
                            Map<String, Object> lat = new LinkedHashMap<>();
                            lat.put("p50_ms", ss.latency.percentileMs(50));
                            lat.put("p95_ms", ss.latency.percentileMs(95));
                            lat.put("p99_ms", ss.latency.percentileMs(99));
                            lat.put("p999_ms", ss.latency.percentileMs(99.9));
                            sm.put("latency", lat);
                            senderList.add(sm);
                        }
                    }
                    ps.senders = senderList;

                    List<Map<String, Object>> responderList = new ArrayList<>();
                    for (BaseWorker w : pg.getWorkers()) {
                        for (BaseWorker.ResponderStat rs : w.getResponderStats().values()) {
                            Map<String, Object> rm = new LinkedHashMap<>();
                            rm.put("id", rs.id);
                            rm.put("responded", rs.responded.get());
                            rm.put("errors", rs.errors.get());
                            responderList.add(rm);
                        }
                    }
                    ps.responders = responderList;
                }
            } else {
                // Per-producer/consumer from snapshot or live
                if (snap != null) {
                    ps.producers = snap.producers;
                    ps.consumers = snap.consumers;
                } else {
                    List<Map<String, Object>> producerList = new ArrayList<>();
                    for (BaseWorker w : pg.getWorkers()) {
                        for (BaseWorker.ProducerStat pstat : w.getProducerStats().values()) {
                            Map<String, Object> pm = new LinkedHashMap<>();
                            pm.put("id", pstat.id);
                            pm.put("sent", pstat.sent.get());
                            pm.put("errors", pstat.errors.get());
                            pm.put("actual_rate", Math.round(pstat.rate.getRate() * 10.0) / 10.0);
                            Map<String, Object> lat = new LinkedHashMap<>();
                            lat.put("p50_ms", pstat.latency.percentileMs(50));
                            lat.put("p95_ms", pstat.latency.percentileMs(95));
                            lat.put("p99_ms", pstat.latency.percentileMs(99));
                            lat.put("p999_ms", pstat.latency.percentileMs(99.9));
                            pm.put("latency", lat);
                            producerList.add(pm);
                        }
                    }
                    ps.producers = producerList;

                    List<Map<String, Object>> consumerList = new ArrayList<>();
                    for (BaseWorker w : pg.getWorkers()) {
                        for (BaseWorker.ConsumerStat cstat : w.getConsumerStats().values()) {
                            Map<String, Object> cm = new LinkedHashMap<>();
                            cm.put("id", cstat.id);
                            cm.put("received", cstat.received.get());
                            cm.put("corrupted", cstat.corrupted.get());
                            cm.put("errors", cstat.errors.get());
                            Map<String, Object> lat = new LinkedHashMap<>();
                            lat.put("p50_ms", cstat.latency.percentileMs(50));
                            lat.put("p95_ms", cstat.latency.percentileMs(95));
                            lat.put("p99_ms", cstat.latency.percentileMs(99));
                            lat.put("p999_ms", cstat.latency.percentileMs(99.9));
                            cm.put("latency", lat);
                            consumerList.add(cm);
                        }
                    }
                    ps.consumers = consumerList;
                }
            }

            if ("events_store".equals(pattern)) {
                ps.unconfirmed = unconfirmed;
            }

            patterns.put(pattern, ps);
        }

        double baseline = baselineRss > 0 ? baselineRss : Math.max(peakRss, 1);
        double peak = peakRss > 0 ? peakRss : getRssMb();
        double growth = baseline > 0 ? peak / baseline : 1;

        String version = cfg.getOutput().getSdkVersion();
        if (version == null || version.isEmpty()) version = "unknown";

        BurninSummary summary = new BurninSummary();
        summary.sdk = "java";
        summary.version = version;
        summary.mode = cfg.getMode();
        summary.brokerAddress = cfg.getBroker().getAddress();
        summary.startedAt = startedAt;
        summary.endedAt = endedAt;
        summary.durationSeconds = elapsed;
        summary.status = status;
        summary.warmupActive = warmupActive;
        summary.memoryBaselineAdvisory = memoryBaselineAdvisory;
        summary.allPatternsEnabled = cfg.getEnabledPatterns().size() == AllPatterns.NAMES.length;
        summary.patterns = patterns;

        ResourceSummary resources = new ResourceSummary();
        resources.peakRssMb = peak;
        resources.baselineRssMb = baseline;
        resources.memoryGrowthFactor = growth;
        resources.peakWorkers = peakWorkers;
        summary.resources = resources;

        summary.verdict = Report.generateVerdict(summary, cfg.getThresholds(), cfg.getMode(),
                cfg.getEnabledPatterns(), resolvePatternThresholds());
        return summary;
    }

    /**
     * Resolve per-pattern thresholds merging pattern-level overrides with global defaults.
     */
    private Map<String, PatternThreshold> resolvePatternThresholds() {
        Map<String, PatternThreshold> result = new LinkedHashMap<>();
        for (String pattern : AllPatterns.NAMES) {
            PatternThreshold pt = new PatternThreshold();
            pt.maxLossPct = ApiConfig.DEFAULT_LOSS_PCT.getOrDefault(pattern, 0.0);

            PatternConfig pc = cfg.getPatternConfig(pattern);
            if (pc != null && pc.getThresholds() != null) {
                ThresholdsConfig thr = pc.getThresholds();
                pt.maxLossPct = thr.getMaxLossPct();
                pt.maxP99LatencyMs = thr.getMaxP99LatencyMs();
                pt.maxP999LatencyMs = thr.getMaxP999LatencyMs();
            }

            result.put(pattern, pt);
        }
        return result;
    }

    // --- Private: Banner ---

    private void printBanner() {
        String sep = repeat('=', 67);
        System.out.println(sep);
        System.out.println("  KUBEMQ BURN-IN TEST -- Java SDK v2");
        System.out.println(sep);
        System.out.println("  Mode:     " + cfg.getMode());
        System.out.println("  Broker:   " + cfg.getBroker().getAddress());
        System.out.println("  Duration: " + cfg.getDuration());
        System.out.println("  Run ID:   " + cfg.getRunId());

        StringBuilder patternsInfo = new StringBuilder("  Patterns: ");
        for (Map.Entry<String, PatternGroup> e : patternGroups.entrySet()) {
            patternsInfo.append(e.getKey()).append("(").append(e.getValue().getChannelCount()).append("ch) ");
        }
        System.out.println(patternsInfo.toString().trim());
        System.out.println(sep);
        System.out.println();
    }

    // --- Private: Utilities ---

    private static double getRssMb() {
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memBean.getHeapMemoryUsage().getUsed();
        long nonHeapUsed = memBean.getNonHeapMemoryUsage().getUsed();
        return (heapUsed + nonHeapUsed) / (1024.0 * 1024.0);
    }

    private static String formatDuration(double secs) {
        int s = (int) secs;
        if (s >= 3600) return (s / 3600) + "h" + (s % 3600 / 60) + "m" + (s % 60) + "s";
        if (s >= 60) return (s / 60) + "m" + (s % 60) + "s";
        return s + "s";
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) sb.append(c);
        return sb.toString();
    }

    private static void safeDelay(long ms, CountDownLatch latch) {
        try {
            latch.await(ms, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepQuietly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void closeClientsQuietly() {
        try { if (pubSubClient != null) pubSubClient.close(); } catch (Exception e) { /* best effort */ }
        try { if (queuesClient != null) queuesClient.close(); } catch (Exception e) { /* best effort */ }
        try { if (cqClient != null) cqClient.close(); } catch (Exception e) { /* best effort */ }
        closePerPatternClients();
    }

    @Override
    public void close() {
        for (PatternGroup pg : patternGroups.values()) {
            pg.close();
        }
        closeClientsQuietly();
    }
}

/**
 * Standalone cleanup-only mode: create clients, clean stale channels, close.
 */
class CleanupRunner {
    static void run(BurninConfig cfg) throws Exception {
        ReconnectionConfig reconnectConfig = ReconnectionConfig.builder()
                .maxReconnectAttempts(-1)
                .reconnectJitterEnabled(true)
                .build();

        String clientId = cfg.getBroker().getClientIdPrefix() + "-" + cfg.getRunId();

        PubSubClient pubSubClient = PubSubClient.builder()
                .address(cfg.getBroker().getAddress())
                .clientId(clientId + "-cleanup-pubsub")
                .reconnectionConfig(reconnectConfig)
                .build();

        QueuesClient queuesClient = QueuesClient.builder()
                .address(cfg.getBroker().getAddress())
                .clientId(clientId + "-cleanup-queues")
                .reconnectionConfig(reconnectConfig)
                .build();

        CQClient cqClient = CQClient.builder()
                .address(cfg.getBroker().getAddress())
                .clientId(clientId + "-cleanup-cq")
                .reconnectionConfig(reconnectConfig)
                .build();

        try {
            pubSubClient.ping();
            System.out.println("cleaning stale channels");

            String prefix = "java_burnin_";
            int count = 0;

            count += Engine.cleanChannelType(
                    () -> pubSubClient.listEventsChannels(prefix),
                    PubSubChannel::getName,
                    n -> pubSubClient.deleteEventsChannel(n));
            count += Engine.cleanChannelType(
                    () -> pubSubClient.listEventsStoreChannels(prefix),
                    PubSubChannel::getName,
                    n -> pubSubClient.deleteEventsStoreChannel(n));
            count += Engine.cleanChannelType(
                    () -> queuesClient.listQueuesChannels(prefix),
                    QueuesChannel::getName,
                    n -> queuesClient.deleteQueuesChannel(n));
            count += Engine.cleanChannelType(
                    () -> cqClient.listCommandsChannels(prefix),
                    CQChannel::getName,
                    n -> cqClient.deleteCommandsChannel(n));
            count += Engine.cleanChannelType(
                    () -> cqClient.listQueriesChannels(prefix),
                    CQChannel::getName,
                    n -> cqClient.deleteQueriesChannel(n));

            System.out.println("cleaned " + count + " stale channels");
        } finally {
            try { pubSubClient.close(); } catch (Exception e) { /* best effort */ }
            try { queuesClient.close(); } catch (Exception e) { /* best effort */ }
            try { cqClient.close(); } catch (Exception e) { /* best effort */ }
        }
    }
}
