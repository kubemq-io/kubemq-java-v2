package io.kubemq.sdk.benchmark;

import io.kubemq.sdk.pubsub.EventMessage;
import io.kubemq.sdk.pubsub.PubSubClient;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Measures fire-and-forget event publish throughput (messages/second).
 * Uses {@link PubSubClient#sendEventsMessage(EventMessage)} which is
 * a void fire-and-forget operation over the gRPC event stream.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class PublishThroughputBenchmark {

    private PubSubClient client;
    private EventMessage message;

    @Param({"1024"})
    private int payloadSize;

    @Setup(Level.Trial)
    public void setup() {
        client = PubSubClient.builder()
                .address(BenchmarkConfig.getAddress())
                .clientId("bench-throughput")
                .build();

        byte[] payload = new byte[payloadSize];
        message = EventMessage.builder()
                .channel("bench-throughput")
                .body(payload)
                .build();
    }

    @Benchmark
    public void publishEvent(Blackhole bh) {
        client.sendEventsMessage(message);
        bh.consume(true);
    }

    @TearDown(Level.Trial)
    public void teardown() {
        client.close();
    }
}
