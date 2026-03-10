package io.kubemq.sdk.benchmark;

/**
 * Shared configuration for JMH benchmarks.
 * Server address can be overridden via system property:
 * {@code -Dkubemq.address=myhost:50000}
 */
public final class BenchmarkConfig {

    private BenchmarkConfig() {
    }

    public static String getAddress() {
        return System.getProperty("kubemq.address", "localhost:50000");
    }
}
