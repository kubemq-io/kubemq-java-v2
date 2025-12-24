package io.kubemq.sdk.unit.queues;

import io.kubemq.sdk.queues.QueuesPollRequest;
import kubemq.Kubemq.QueuesDownstreamRequest;
import kubemq.Kubemq.QueuesDownstreamRequestType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueuesPollRequest validation and encoding.
 */
class QueuesPollRequestTest {

    @Nested
    class ValidationTests {

        @Test
        void validate_withValidRequest_passes() {
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel("test-channel")
                    .pollMaxMessages(10)
                    .pollWaitTimeoutInSeconds(30)
                    .build();

            assertDoesNotThrow(request::validate);
        }

        @Test
        void validate_withDefaultValues_passes() {
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel("test-channel")
                    .build();

            assertDoesNotThrow(request::validate);
            assertEquals(1, request.getPollMaxMessages());
            assertEquals(60, request.getPollWaitTimeoutInSeconds());
            assertFalse(request.isAutoAckMessages());
            assertEquals(0, request.getVisibilitySeconds());
        }

        @Test
        void validate_withNullChannel_throws() {
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .pollMaxMessages(10)
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    request::validate
            );
            assertTrue(ex.getMessage().contains("channel"));
        }

        @Test
        void validate_withEmptyChannel_throws() {
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel("")
                    .pollMaxMessages(10)
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    request::validate
            );
            assertTrue(ex.getMessage().contains("channel"));
        }

        @Test
        void validate_withZeroMaxMessages_throws() {
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel("test-channel")
                    .pollMaxMessages(0)
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    request::validate
            );
            assertTrue(ex.getMessage().contains("pollMaxMessages"));
        }

        @Test
        void validate_withNegativeMaxMessages_throws() {
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel("test-channel")
                    .pollMaxMessages(-1)
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    request::validate
            );
            assertTrue(ex.getMessage().contains("pollMaxMessages"));
        }

        @Test
        void validate_withZeroTimeout_throws() {
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel("test-channel")
                    .pollWaitTimeoutInSeconds(0)
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    request::validate
            );
            assertTrue(ex.getMessage().contains("pollWaitTimeoutInSeconds"));
        }

        @Test
        void validate_withNegativeTimeout_throws() {
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel("test-channel")
                    .pollWaitTimeoutInSeconds(-1)
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    request::validate
            );
            assertTrue(ex.getMessage().contains("pollWaitTimeoutInSeconds"));
        }

        @Test
        void validate_withNegativeVisibility_throws() {
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel("test-channel")
                    .visibilitySeconds(-1)
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    request::validate
            );
            assertTrue(ex.getMessage().contains("Visibility"));
        }

        @Test
        void validate_withAutoAckAndVisibility_throws() {
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel("test-channel")
                    .autoAckMessages(true)
                    .visibilitySeconds(30)
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    request::validate
            );
            assertTrue(ex.getMessage().contains("autoAckMessages") && ex.getMessage().contains("visibilitySeconds"));
        }

        @Test
        void validate_withAutoAckOnly_passes() {
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel("test-channel")
                    .autoAckMessages(true)
                    .build();

            assertDoesNotThrow(request::validate);
        }

        @Test
        void validate_withVisibilityOnly_passes() {
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel("test-channel")
                    .visibilitySeconds(30)
                    .build();

            assertDoesNotThrow(request::validate);
        }
    }

    @Nested
    class EncodingTests {

        @Test
        void encode_setsClientId() {
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel("test-channel")
                    .build();

            QueuesDownstreamRequest proto = request.encode("my-client");

            assertEquals("my-client", proto.getClientID());
        }

        @Test
        void encode_setsChannel() {
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel("orders")
                    .build();

            QueuesDownstreamRequest proto = request.encode("client");

            assertEquals("orders", proto.getChannel());
        }

        @Test
        void encode_setsMaxItems() {
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel("test-channel")
                    .pollMaxMessages(25)
                    .build();

            QueuesDownstreamRequest proto = request.encode("client");

            assertEquals(25, proto.getMaxItems());
        }

        @Test
        void encode_convertsTimeoutToMilliseconds() {
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel("test-channel")
                    .pollWaitTimeoutInSeconds(30)
                    .build();

            QueuesDownstreamRequest proto = request.encode("client");

            assertEquals(30000, proto.getWaitTimeout());
        }

        @Test
        void encode_setsAutoAck() {
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel("test-channel")
                    .autoAckMessages(true)
                    .build();

            QueuesDownstreamRequest proto = request.encode("client");

            assertTrue(proto.getAutoAck());
        }

        @Test
        void encode_setsRequestType() {
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel("test-channel")
                    .build();

            QueuesDownstreamRequest proto = request.encode("client");

            assertEquals(QueuesDownstreamRequestType.Get, proto.getRequestTypeData());
        }

        @Test
        void encode_generatesRequestId() {
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel("test-channel")
                    .build();

            QueuesDownstreamRequest proto = request.encode("client");

            assertNotNull(proto.getRequestID());
            assertFalse(proto.getRequestID().isEmpty());
        }

        @Test
        void encode_generatesUniqueRequestIds() {
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel("test-channel")
                    .build();

            QueuesDownstreamRequest proto1 = request.encode("client");
            QueuesDownstreamRequest proto2 = request.encode("client");

            assertNotEquals(proto1.getRequestID(), proto2.getRequestID());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toString_includesAllFields() {
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel("orders")
                    .pollMaxMessages(10)
                    .pollWaitTimeoutInSeconds(30)
                    .autoAckMessages(true)
                    .visibilitySeconds(60)
                    .build();

            String str = request.toString();

            assertTrue(str.contains("orders"));
            assertTrue(str.contains("10"));
            assertTrue(str.contains("30"));
            assertTrue(str.contains("true"));
            assertTrue(str.contains("60"));
        }
    }
}
