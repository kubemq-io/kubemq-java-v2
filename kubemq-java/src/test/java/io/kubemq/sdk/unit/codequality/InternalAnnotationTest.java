package io.kubemq.sdk.unit.codequality;

import io.kubemq.sdk.common.ChannelDecoder;
import io.kubemq.sdk.common.Internal;
import io.kubemq.sdk.common.KubeMQUtils;
import io.kubemq.sdk.pubsub.EventStreamHelper;
import io.kubemq.sdk.queues.QueueDownstreamHandler;
import io.kubemq.sdk.queues.QueueUpstreamHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that internal SDK classes are annotated with {@link Internal} (REQ-CQ-2).
 */
class InternalAnnotationTest {

    @Test
    void queueUpstreamHandlerIsInternal() {
        assertTrue(QueueUpstreamHandler.class.isAnnotationPresent(Internal.class),
                "QueueUpstreamHandler should be @Internal");
    }

    @Test
    void queueDownstreamHandlerIsInternal() {
        assertTrue(QueueDownstreamHandler.class.isAnnotationPresent(Internal.class),
                "QueueDownstreamHandler should be @Internal");
    }

    @Test
    void eventStreamHelperIsInternal() {
        assertTrue(EventStreamHelper.class.isAnnotationPresent(Internal.class),
                "EventStreamHelper should be @Internal");
    }

    @Test
    void channelDecoderIsInternal() {
        assertTrue(ChannelDecoder.class.isAnnotationPresent(Internal.class),
                "ChannelDecoder should be @Internal");
    }

    @Test
    @SuppressWarnings("deprecation")
    void kubeMQUtilsIsDeprecatedAndInternal() {
        assertTrue(KubeMQUtils.class.isAnnotationPresent(Internal.class),
                "KubeMQUtils should be @Internal");
        assertTrue(KubeMQUtils.class.isAnnotationPresent(Deprecated.class),
                "KubeMQUtils should be @Deprecated");
    }

    @Test
    void internalAnnotationIsRetainedAtRuntime() {
        assertEquals(java.lang.annotation.RetentionPolicy.RUNTIME,
                Internal.class.getAnnotation(java.lang.annotation.Retention.class).value(),
                "@Internal should have RUNTIME retention for reflection-based checks");
    }
}
