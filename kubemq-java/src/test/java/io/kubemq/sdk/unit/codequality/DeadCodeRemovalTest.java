package io.kubemq.sdk.unit.codequality;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies dead code removal (REQ-CQ-6).
 */
class DeadCodeRemovalTest {

    @Test
    void queueDownStreamProcessorIsRemoved() {
        assertThrows(ClassNotFoundException.class, () ->
            Class.forName("io.kubemq.sdk.queues.QueueDownStreamProcessor"),
            "QueueDownStreamProcessor should have been deleted (dead code)");
    }
}
