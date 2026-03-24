package io.kubemq.burnin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpServer {

    private final com.sun.net.httpserver.HttpServer server;
    private final RunManager runManager;
    private final String corsOrigins;

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .serializeNulls()
            .create();

    public HttpServer(int port, RunManager runManager, String corsOrigins) throws IOException {
        this.runManager = runManager;
        this.corsOrigins = corsOrigins != null ? corsOrigins : "*";
        this.server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        server.createContext("/health", wrap(this::handleHealth));
        server.createContext("/ready", wrap(this::handleReady));
        server.createContext("/metrics", wrap(this::handleMetrics));
        server.createContext("/info", wrap(this::handleInfo));
        server.createContext("/broker/status", wrap(this::handleBrokerStatus));
        server.createContext("/run/start", wrap(this::handleRunStart));
        server.createContext("/run/stop", wrap(this::handleRunStop));
        server.createContext("/run/status", wrap(this::handleRunStatus));
        server.createContext("/run/config", wrap(this::handleRunConfig));
        server.createContext("/run/report", wrap(this::handleRunReport));
        server.createContext("/run", wrap(this::handleRun));
        server.createContext("/cleanup", wrap(this::handleCleanup));
        server.createContext("/status", wrap(this::handleLegacyStatus));
        server.createContext("/summary", wrap(this::handleLegacySummary));
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(1);
    }

    private HttpHandler wrap(Handler handler) {
        return exchange -> {
            try {
                addCorsHeaders(exchange);
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                handler.handle(exchange);
            } catch (Exception e) {
                try {
                    Map<String, Object> err = new LinkedHashMap<>();
                    err.put("message", "Internal server error: " + e.getMessage());
                    sendJson(exchange, 500, GSON.toJson(err));
                } catch (Exception ignored) {}
            }
        };
    }

    @FunctionalInterface
    interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", corsOrigins);
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    // --- Endpoints ---

    private void handleHealth(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, "{\"status\":\"alive\"}");
    }

    private void handleReady(HttpExchange exchange) throws IOException {
        RunState state = runManager.getState();
        Map<String, Object> resp = new LinkedHashMap<>();
        if (state.isReady()) {
            resp.put("status", "ready");
            resp.put("state", state.getValue());
            sendJson(exchange, 200, GSON.toJson(resp));
        } else {
            resp.put("status", "not_ready");
            resp.put("state", state.getValue());
            sendJson(exchange, 503, GSON.toJson(resp));
        }
    }

    private void handleMetrics(HttpExchange exchange) throws IOException {
        String scraped = Metrics.scrape();
        sendText(exchange, 200, scraped, "text/plain; version=0.0.4; charset=utf-8");
    }

    private void handleInfo(HttpExchange exchange) throws IOException {
        BurninConfig cfg = runManager.getStartupConfig();
        Runtime rt = Runtime.getRuntime();
        Instant bootTime = runManager.getBootTime();
        double uptimeSec = Duration.between(bootTime, Instant.now()).toMillis() / 1000.0;

        String sdkVersion = cfg.getOutput().getSdkVersion();
        if (sdkVersion == null || sdkVersion.isEmpty()) sdkVersion = "unknown";

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("sdk", "java");
        info.put("sdk_version", sdkVersion);
        info.put("burnin_version", "2.0.0");
        info.put("burnin_spec_version", "2");
        info.put("os", System.getProperty("os.name", "unknown").toLowerCase());
        info.put("arch", System.getProperty("os.arch", "unknown"));
        info.put("runtime", "java" + System.getProperty("java.version", "unknown"));
        info.put("cpus", rt.availableProcessors());

        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        long totalMem = rt.maxMemory();
        info.put("memory_total_mb", (int) (totalMem / (1024 * 1024)));
        info.put("pid", ProcessHandle.current().pid());
        info.put("uptime_seconds", Math.round(uptimeSec * 10.0) / 10.0);
        info.put("started_at", bootTime.toString());
        info.put("state", runManager.getState().getValue());
        info.put("broker_address", cfg.getBroker().getAddress());

        sendJson(exchange, 200, GSON.toJson(info));
    }

    private void handleBrokerStatus(HttpExchange exchange) throws IOException {
        Map<String, Object> resp = runManager.brokerStatus();
        sendJson(exchange, 200, GSON.toJson(resp));
    }

    private void handleRunStart(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("message", "Method not allowed");
            sendJson(exchange, 405, GSON.toJson(err));
            return;
        }

        String body = readBody(exchange);
        RunManager.StartResult result = runManager.startRun(body);
        sendJson(exchange, result.httpStatus, GSON.toJson(result.body));
    }

    private void handleRunStop(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("message", "Method not allowed");
            sendJson(exchange, 405, GSON.toJson(err));
            return;
        }

        RunManager.StartResult result = runManager.stopRun();
        sendJson(exchange, result.httpStatus, GSON.toJson(result.body));
    }

    private void handleRun(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/run/start") || path.equals("/run/stop") ||
            path.equals("/run/status") || path.equals("/run/config") ||
            path.equals("/run/report")) {
            return;
        }

        Map<String, Object> resp = runManager.buildRunResponse();
        sendJson(exchange, 200, GSON.toJson(resp));
    }

    private void handleRunStatus(HttpExchange exchange) throws IOException {
        Map<String, Object> resp = runManager.buildStatusResponse();
        sendJson(exchange, 200, GSON.toJson(resp));
    }

    private void handleRunConfig(HttpExchange exchange) throws IOException {
        Map<String, Object> resp = runManager.buildConfigResponse();
        if (resp == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("message", "No run configuration available");
            sendJson(exchange, 404, GSON.toJson(err));
        } else {
            sendJson(exchange, 200, GSON.toJson(resp));
        }
    }

    private void handleRunReport(HttpExchange exchange) throws IOException {
        BurninSummary report = runManager.getLastReport();
        if (report == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("message", "No completed run report available");
            sendJson(exchange, 404, GSON.toJson(err));
        } else {
            sendJson(exchange, 200, GSON.toJson(report));
        }
    }

    private void handleCleanup(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("message", "Method not allowed");
            sendJson(exchange, 405, GSON.toJson(err));
            return;
        }

        RunState state = runManager.getState();
        if (!state.canCleanup()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("message", "Cannot cleanup while a run is active");
            err.put("state", state.getValue());
            if (runManager.getRunId() != null) err.put("run_id", runManager.getRunId());
            sendJson(exchange, 409, GSON.toJson(err));
            return;
        }

        Map<String, Object> resp = runManager.runCleanup();
        if (resp.containsKey("_status")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) resp.get("_status");
            sendJson(exchange, 409, GSON.toJson(body));
        } else {
            sendJson(exchange, 200, GSON.toJson(resp));
        }
    }

    private void handleLegacyStatus(HttpExchange exchange) throws IOException {
        if (!runManager.isStatusDeprecationWarned()) {
            System.err.println("WARNING: /status is deprecated, use /run/status");
        }
        handleRunStatus(exchange);
    }

    private void handleLegacySummary(HttpExchange exchange) throws IOException {
        if (!runManager.isSummaryDeprecationWarned()) {
            System.err.println("WARNING: /summary is deprecated, use /run/report");
        }
        handleRunReport(exchange);
    }

    // --- Utilities ---

    private static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendText(HttpExchange exchange, int statusCode, String text, String contentType) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            byte[] buf = is.readAllBytes();
            return new String(buf, StandardCharsets.UTF_8);
        }
    }
}
