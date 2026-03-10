package io.kubemq.sdk.benchmark;

import io.kubemq.sdk.queues.QueueMessage;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.queues.QueuesPollRequest;
import io.kubemq.sdk.queues.QueuesPollResponse;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Measures queue send + receive + ack roundtrip latency for a single 1KB message.
 * Each iteration sends one message, polls it, and auto-acks.
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class QueueRoundtripBenchmark {

    private QueuesClient client;
    private static final String CHANNEL = "bench-queue-roundtrip";

    @Setup(Level.Trial)
    public void setup() {
        client = QueuesClient.builder()
                .address(BenchmarkConfig.getAddress())
                .clientId("bench-queue-rt")
                .build();
        client.createQueuesChannel(CHANNEL);
    }

    @Benchmark
    public QueuesPollResponse roundtrip() {
        QueueMessage msg = QueueMessage.builder()
                .channel(CHANNEL)
                .body(new byte[1024])
                .build();
        client.sendQueuesMessage(msg);

        QueuesPollRequest poll = QueuesPollRequest.builder()
                .channel(CHANNEL)
                .pollMaxMessages(1)
                .pollWaitTimeoutInSeconds(5)
                .autoAckMessages(true)
                .build();
        return client.receiveQueuesMessages(poll);
    }

    @TearDown(Level.Trial)
    public void teardown() {
        try {
            client.deleteQueuesChannel(CHANNEL);
        } finally {
            client.close();
        }
    }
}
