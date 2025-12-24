package io.kubemq.sdk.unit.queues;

import io.kubemq.sdk.queues.QueueSendResult;
import kubemq.Kubemq.SendQueueMessageResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueueSendResult decode, getters/setters, and toString.
 */
class QueueSendResultTest {

    // Nanoseconds timestamp for testing (represents a valid epoch time)
    private static final long SENT_AT_NANOS = 1703462400L * 1_000_000_000L; // 2023-12-25 00:00:00 UTC
    private static final long EXPIRED_AT_NANOS = 1703548800L * 1_000_000_000L; // 2023-12-26 00:00:00 UTC
    private static final long DELAYED_TO_NANOS = 1703466000L * 1_000_000_000L; // 2023-12-25 01:00:00 UTC

    @Nested
    class DecodeTests {

        @Test
        void decode_withAllFields_decodesCorrectly() {
            SendQueueMessageResult protoResult = SendQueueMessageResult.newBuilder()
                    .setMessageID("msg-123")
                    .setSentAt(SENT_AT_NANOS)
                    .setExpirationAt(EXPIRED_AT_NANOS)
                    .setDelayedTo(DELAYED_TO_NANOS)
                    .setIsError(false)
                    .setError("")
                    .build();

            QueueSendResult result = new QueueSendResult().decode(protoResult);

            assertEquals("msg-123", result.getId());
            assertNotNull(result.getSentAt());
            assertNotNull(result.getExpiredAt());
            assertNotNull(result.getDelayedTo());
            assertFalse(result.isError());
            assertEquals("", result.getError());
        }

        @Test
        void decode_withError_decodesCorrectly() {
            SendQueueMessageResult protoResult = SendQueueMessageResult.newBuilder()
                    .setMessageID("msg-456")
                    .setSentAt(0)
                    .setExpirationAt(0)
                    .setDelayedTo(0)
                    .setIsError(true)
                    .setError("Queue not found")
                    .build();

            QueueSendResult result = new QueueSendResult().decode(protoResult);

            assertEquals("msg-456", result.getId());
            assertNull(result.getSentAt());
            assertNull(result.getExpiredAt());
            assertNull(result.getDelayedTo());
            assertTrue(result.isError());
            assertEquals("Queue not found", result.getError());
        }

        @Test
        void decode_withOnlySentAt_decodesCorrectly() {
            SendQueueMessageResult protoResult = SendQueueMessageResult.newBuilder()
                    .setMessageID("msg-789")
                    .setSentAt(SENT_AT_NANOS)
                    .setExpirationAt(0)
                    .setDelayedTo(0)
                    .setIsError(false)
                    .setError("")
                    .build();

            QueueSendResult result = new QueueSendResult().decode(protoResult);

            assertEquals("msg-789", result.getId());
            assertNotNull(result.getSentAt());
            assertNull(result.getExpiredAt());
            assertNull(result.getDelayedTo());
            assertFalse(result.isError());
        }

        @Test
        void decode_withNullMessageId_usesEmptyString() {
            SendQueueMessageResult protoResult = SendQueueMessageResult.newBuilder()
                    .setSentAt(SENT_AT_NANOS)
                    .setIsError(false)
                    .build();

            QueueSendResult result = new QueueSendResult().decode(protoResult);

            // Protobuf returns empty string for unset string fields
            assertEquals("", result.getId());
        }

        @Test
        void decode_withNullError_usesEmptyString() {
            SendQueueMessageResult protoResult = SendQueueMessageResult.newBuilder()
                    .setMessageID("msg-000")
                    .setIsError(true)
                    .build();

            QueueSendResult result = new QueueSendResult().decode(protoResult);

            // Protobuf returns empty string for unset string fields
            assertEquals("", result.getError());
        }
    }

    @Nested
    class BuilderTests {

        @Test
        void builder_withAllFields_createsResult() {
            LocalDateTime now = LocalDateTime.now();

            QueueSendResult result = QueueSendResult.builder()
                    .id("build-123")
                    .sentAt(now)
                    .expiredAt(now.plusHours(1))
                    .delayedTo(now.plusMinutes(10))
                    .isError(false)
                    .error("")
                    .build();

            assertEquals("build-123", result.getId());
            assertEquals(now, result.getSentAt());
            assertEquals(now.plusHours(1), result.getExpiredAt());
            assertEquals(now.plusMinutes(10), result.getDelayedTo());
            assertFalse(result.isError());
            assertEquals("", result.getError());
        }

        @Test
        void builder_withMinimalFields_createsResult() {
            QueueSendResult result = QueueSendResult.builder()
                    .id("minimal-123")
                    .isError(false)
                    .build();

            assertEquals("minimal-123", result.getId());
            assertFalse(result.isError());
            assertNull(result.getSentAt());
            assertNull(result.getExpiredAt());
            assertNull(result.getDelayedTo());
        }
    }

    @Nested
    class GetterSetterTests {

        @Test
        void setId_updatesId() {
            QueueSendResult result = new QueueSendResult();
            result.setId("updated-id");
            assertEquals("updated-id", result.getId());
        }

        @Test
        void setSentAt_updatesSentAt() {
            QueueSendResult result = new QueueSendResult();
            LocalDateTime now = LocalDateTime.now();
            result.setSentAt(now);
            assertEquals(now, result.getSentAt());
        }

        @Test
        void setExpiredAt_updatesExpiredAt() {
            QueueSendResult result = new QueueSendResult();
            LocalDateTime expiry = LocalDateTime.now().plusHours(2);
            result.setExpiredAt(expiry);
            assertEquals(expiry, result.getExpiredAt());
        }

        @Test
        void setDelayedTo_updatesDelayedTo() {
            QueueSendResult result = new QueueSendResult();
            LocalDateTime delay = LocalDateTime.now().plusMinutes(30);
            result.setDelayedTo(delay);
            assertEquals(delay, result.getDelayedTo());
        }

        @Test
        void setIsError_updatesIsError() {
            QueueSendResult result = new QueueSendResult();
            result.setIsError(true);
            assertTrue(result.isError());
        }

        @Test
        void setError_updatesError() {
            QueueSendResult result = new QueueSendResult();
            result.setError("test error message");
            assertEquals("test error message", result.getError());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toString_includesAllFields() {
            LocalDateTime now = LocalDateTime.of(2023, 12, 25, 10, 30, 0);

            QueueSendResult result = QueueSendResult.builder()
                    .id("tostring-123")
                    .sentAt(now)
                    .expiredAt(now.plusHours(1))
                    .delayedTo(now.plusMinutes(5))
                    .isError(false)
                    .error("")
                    .build();

            String str = result.toString();

            assertTrue(str.contains("tostring-123"));
            assertTrue(str.contains("2023-12-25"));
            assertTrue(str.contains("isError=false"));
        }

        @Test
        void toString_withError_includesErrorMessage() {
            QueueSendResult result = QueueSendResult.builder()
                    .id("error-123")
                    .isError(true)
                    .error("Something went wrong")
                    .build();

            String str = result.toString();

            assertTrue(str.contains("error-123"));
            assertTrue(str.contains("isError=true"));
            assertTrue(str.contains("Something went wrong"));
        }

        @Test
        void toString_withNullDates_handlesGracefully() {
            QueueSendResult result = QueueSendResult.builder()
                    .id("null-dates")
                    .isError(false)
                    .build();

            String str = result.toString();

            assertTrue(str.contains("null-dates"));
            // Should not throw exception
            assertNotNull(str);
        }
    }
}
