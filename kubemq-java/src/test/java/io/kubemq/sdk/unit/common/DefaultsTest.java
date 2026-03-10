package io.kubemq.sdk.unit.common;

import io.kubemq.sdk.common.Defaults;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for REQ-ERR-4: Per-operation default timeouts.
 */
class DefaultsTest {

    @Test
    void sendTimeout_is5Seconds() {
        assertEquals(Duration.ofSeconds(5), Defaults.SEND_TIMEOUT);
    }

    @Test
    void subscribeTimeout_is10Seconds() {
        assertEquals(Duration.ofSeconds(10), Defaults.SUBSCRIBE_TIMEOUT);
    }

    @Test
    void rpcTimeout_is10Seconds() {
        assertEquals(Duration.ofSeconds(10), Defaults.RPC_TIMEOUT);
    }

    @Test
    void queueReceiveSingleTimeout_is10Seconds() {
        assertEquals(Duration.ofSeconds(10), Defaults.QUEUE_RECEIVE_SINGLE_TIMEOUT);
    }

    @Test
    void queueReceiveStreamingTimeout_is30Seconds() {
        assertEquals(Duration.ofSeconds(30), Defaults.QUEUE_RECEIVE_STREAMING_TIMEOUT);
    }

    @Test
    void connectionTimeout_is10Seconds() {
        assertEquals(Duration.ofSeconds(10), Defaults.CONNECTION_TIMEOUT);
    }

    @Test
    void allTimeouts_arePositive() {
        assertTrue(Defaults.SEND_TIMEOUT.toMillis() > 0);
        assertTrue(Defaults.SUBSCRIBE_TIMEOUT.toMillis() > 0);
        assertTrue(Defaults.RPC_TIMEOUT.toMillis() > 0);
        assertTrue(Defaults.QUEUE_RECEIVE_SINGLE_TIMEOUT.toMillis() > 0);
        assertTrue(Defaults.QUEUE_RECEIVE_STREAMING_TIMEOUT.toMillis() > 0);
        assertTrue(Defaults.CONNECTION_TIMEOUT.toMillis() > 0);
    }
}
