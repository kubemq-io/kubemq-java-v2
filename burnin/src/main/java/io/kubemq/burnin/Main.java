package io.kubemq.burnin;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Main {

    private Main() {}

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            System.err.println("uncaught exception in thread " + t.getName() + ": " + e.getMessage());
            e.printStackTrace(System.err);
        });

        int exitCode = run(args);
        System.exit(exitCode);
    }

    private static int run(String[] args) {
        String configPath = "";
        boolean validateConfig = false;
        boolean cleanupOnly = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--config":
                case "-c":
                    if (i + 1 < args.length) {
                        configPath = args[++i];
                    }
                    break;
                case "--validate-config":
                    validateConfig = true;
                    break;
                case "--cleanup-only":
                    cleanupOnly = true;
                    break;
                default:
                    break;
            }
        }

        ConfigLoadResult loadResult = Config.loadConfig(configPath);
        BurninConfig cfg = loadResult.getConfig();
        for (String w : loadResult.getWarnings()) {
            System.err.println("WARNING: " + w);
        }

        List<String> errors = Config.validateConfig(cfg);
        boolean hasErrors = false;
        for (String e : errors) {
            if (e.startsWith("WARNING")) {
                System.err.println(e);
            } else {
                System.err.println("config error: " + e);
                hasErrors = true;
            }
        }

        if (hasErrors) {
            return 2;
        }

        if (validateConfig) {
            System.out.println("config validation passed");
            System.out.println("mode=" + cfg.getMode() + " duration=" + cfg.getDuration()
                    + " broker=" + cfg.getBroker().getAddress() + " run_id=" + cfg.getRunId());
            return 0;
        }

        if (cleanupOnly) {
            System.out.println("running cleanup-only mode");
            try {
                CleanupRunner.run(cfg);
                System.out.println("cleanup complete");
            } catch (Exception ex) {
                System.err.println("cleanup failed: " + ex);
                return 1;
            }
            return 0;
        }

        RunManager runManager = new RunManager(cfg);

        HttpServer httpServer;
        try {
            httpServer = new HttpServer(cfg.getMetrics().getPort(), runManager, cfg.getCors().getOrigins());
            httpServer.start();
            System.out.println("HTTP server started on port " + cfg.getMetrics().getPort());
            System.out.println("app is idle, waiting for POST /run/start");
        } catch (Exception e) {
            System.err.println("failed to start HTTP server: " + e.getMessage());
            return 2;
        }

        CountDownLatch shutdownLatch = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("received shutdown signal");
            shutdownLatch.countDown();

            ScheduledExecutorService hardDeadline = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "hard-deadline");
                t.setDaemon(true);
                return t;
            });
            hardDeadline.schedule(() -> {
                System.err.println("HARD DEADLINE reached, forcing exit");
                Runtime.getRuntime().halt(2);
            }, 30, TimeUnit.SECONDS);
        }, "shutdown-hook"));

        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int exitCode = runManager.handleShutdown();

        try { httpServer.stop(); } catch (Exception e) { /* best effort */ }
        runManager.shutdown();

        System.out.println("burn-in app shutdown complete");
        return exitCode;
    }
}
