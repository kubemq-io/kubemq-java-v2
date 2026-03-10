package io.kubemq.sdk.benchmark;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.PubSubClient;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Measures time from client creation to first successful ping.
 * Uses {@link Mode#SingleShotTime} to capture cold-start latency
 * without JVM warm-up bias.
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Measurement(iterations = 20)
@Fork(1)
public class ConnectionSetupBenchmark {

    @Benchmark
    public ServerInfo connectAndPing() {
        try (PubSubClient client = PubSubClient.builder()
                .address(BenchmarkConfig.getAddress())
                .clientId("bench-connect")
                .build()) {
            return client.ping();
        }
    }
}
