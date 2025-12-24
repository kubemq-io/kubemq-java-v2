package io.kubemq.sdk.unit.pubsub;

import io.kubemq.sdk.pubsub.EventSendResult;
import kubemq.Kubemq;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EventSendResult.
 */
class EventSendResultTest {

    @Nested
    class BuilderTests {

        @Test
        void builder_withAllFields_createsResult() {
            EventSendResult result = EventSendResult.builder()
                    .id("event-123")
                    .sent(true)
                    .error("")
                    .build();

            assertEquals("event-123", result.getId());
            assertTrue(result.isSent());
            assertEquals("", result.getError());
        }

        @Test
        void builder_withError_createsResultWithError() {
            EventSendResult result = EventSendResult.builder()
                    .id("event-456")
                    .sent(false)
                    .error("Connection failed")
                    .build();

            assertEquals("event-456", result.getId());
            assertFalse(result.isSent());
            assertEquals("Connection failed", result.getError());
        }
    }

    @Nested
    class DecodeTests {

        @Test
        void decode_withSuccessResult_decodesCorrectly() {
            Kubemq.Result protoResult = Kubemq.Result.newBuilder()
                    .setEventID("decoded-event-1")
                    .setSent(true)
                    .setError("")
                    .build();

            EventSendResult result = EventSendResult.decode(protoResult);

            assertEquals("decoded-event-1", result.getId());
            assertTrue(result.isSent());
            assertEquals("", result.getError());
        }

        @Test
        void decode_withErrorResult_decodesCorrectly() {
            Kubemq.Result protoResult = Kubemq.Result.newBuilder()
                    .setEventID("decoded-event-2")
                    .setSent(false)
                    .setError("Channel not found")
                    .build();

            EventSendResult result = EventSendResult.decode(protoResult);

            assertEquals("decoded-event-2", result.getId());
            assertFalse(result.isSent());
            assertEquals("Channel not found", result.getError());
        }

        @Test
        void decode_withEmptyEventId_handlesGracefully() {
            Kubemq.Result protoResult = Kubemq.Result.newBuilder()
                    .setEventID("")
                    .setSent(true)
                    .setError("")
                    .build();

            EventSendResult result = EventSendResult.decode(protoResult);

            assertEquals("", result.getId());
            assertTrue(result.isSent());
        }
    }

    @Nested
    class GetterSetterTests {

        @Test
        void setId_updatesId() {
            EventSendResult result = new EventSendResult();
            result.setId("new-id");
            assertEquals("new-id", result.getId());
        }

        @Test
        void setSent_updatesSent() {
            EventSendResult result = new EventSendResult();
            result.setSent(true);
            assertTrue(result.isSent());
        }

        @Test
        void setError_updatesError() {
            EventSendResult result = new EventSendResult();
            result.setError("test error");
            assertEquals("test error", result.getError());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toString_includesAllFields() {
            EventSendResult result = EventSendResult.builder()
                    .id("event-789")
                    .sent(true)
                    .error("none")
                    .build();

            String str = result.toString();

            assertTrue(str.contains("event-789"));
            assertTrue(str.contains("true"));
            assertTrue(str.contains("none"));
        }

        @Test
        void toString_withNullError_handlesGracefully() {
            EventSendResult result = EventSendResult.builder()
                    .id("event-000")
                    .sent(false)
                    .error(null)
                    .build();

            String str = result.toString();

            assertTrue(str.contains("event-000"));
            assertTrue(str.contains("null") || str.contains("error=null"));
        }
    }
}
