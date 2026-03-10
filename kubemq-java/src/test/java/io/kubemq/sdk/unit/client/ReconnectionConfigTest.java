package io.kubemq.sdk.unit.client;

import io.kubemq.sdk.client.BufferOverflowPolicy;
import io.kubemq.sdk.client.ReconnectionConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReconnectionConfigTest {

    @Test
    void defaults_areCorrect() {
        ReconnectionConfig config = ReconnectionConfig.builder().build();

        assertEquals(-1, config.getMaxReconnectAttempts());
        assertEquals(500, config.getInitialReconnectDelayMs());
        assertEquals(30_000, config.getMaxReconnectDelayMs());
        assertEquals(2.0, config.getReconnectBackoffMultiplier());
        assertTrue(config.isReconnectJitterEnabled());
        assertEquals(8 * 1024 * 1024, config.getReconnectBufferSizeBytes());
        assertEquals(BufferOverflowPolicy.ERROR, config.getBufferOverflowPolicy());
    }

    @Test
    void builder_customValues_areRespected() {
        ReconnectionConfig config = ReconnectionConfig.builder()
            .maxReconnectAttempts(5)
            .initialReconnectDelayMs(1000)
            .maxReconnectDelayMs(60_000)
            .reconnectBackoffMultiplier(1.5)
            .reconnectJitterEnabled(false)
            .reconnectBufferSizeBytes(16 * 1024 * 1024)
            .bufferOverflowPolicy(BufferOverflowPolicy.BLOCK)
            .build();

        assertEquals(5, config.getMaxReconnectAttempts());
        assertEquals(1000, config.getInitialReconnectDelayMs());
        assertEquals(60_000, config.getMaxReconnectDelayMs());
        assertEquals(1.5, config.getReconnectBackoffMultiplier());
        assertFalse(config.isReconnectJitterEnabled());
        assertEquals(16 * 1024 * 1024, config.getReconnectBufferSizeBytes());
        assertEquals(BufferOverflowPolicy.BLOCK, config.getBufferOverflowPolicy());
    }
}
