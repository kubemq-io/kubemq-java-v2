package io.kubemq.sdk.benchmark;

import io.kubemq.sdk.pubsub.EventSendResult;
import io.kubemq.sdk.pubsub.EventStoreMessage;
import io.kubemq.sdk.pubsub.PubSubClient;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Measures p50/p99 publish latency using EventStore messages.
 * EventStore publish returns an ack ({@link EventSendResult}), so
 * end-to-end latency is meaningful (unlike fire-and-forget events).
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class PublishLatencyBenchmark {

    private PubSubClient client;
    private EventStoreMessage message;

    @Setup(Level.Trial)
    public void setup() {
        client = PubSubClient.builder()
                .address(BenchmarkConfig.getAddress())
                .clientId("bench-latency")
                .build();

        byte[] payload = new byte[1024];
        message = EventStoreMessage.builder()
                .channel("bench-latency")
                .body(payload)
                .build();
    }

    @Benchmark
    public EventSendResult publishEventStore() {
        return client.sendEventsStoreMessage(message);
    }

    @TearDown(Level.Trial)
    public void teardown() {
        client.close();
    }
}
