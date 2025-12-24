package io.kubemq.sdk.unit.queues;

import io.kubemq.sdk.queues.QueueMessage;
import kubemq.Kubemq;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueueMessage validation, encoding, and decoding.
 */
class QueueMessageTest {

    @Nested
    class ValidationTests {

        @Test
        void validate_withValidMessage_passes() {
            QueueMessage msg = QueueMessage.builder()
                    .channel("test-channel")
                    .body("test body".getBytes())
                    .build();

            assertDoesNotThrow(msg::validate);
        }

        @Test
        void validate_withMetadataOnly_passes() {
            QueueMessage msg = QueueMessage.builder()
                    .channel("test-channel")
                    .metadata("test metadata")
                    .build();

            assertDoesNotThrow(msg::validate);
        }

        @Test
        void validate_withTagsOnly_passes() {
            Map<String, String> tags = new HashMap<>();
            tags.put("key", "value");

            QueueMessage msg = QueueMessage.builder()
                    .channel("test-channel")
                    .tags(tags)
                    .build();

            assertDoesNotThrow(msg::validate);
        }

        @Test
        void validate_withNullChannel_throws() {
            QueueMessage msg = QueueMessage.builder()
                    .body("test".getBytes())
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    msg::validate
            );
            assertTrue(ex.getMessage().contains("channel"));
        }

        @Test
        void validate_withEmptyChannel_throws() {
            QueueMessage msg = QueueMessage.builder()
                    .channel("")
                    .body("test".getBytes())
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    msg::validate
            );
            assertTrue(ex.getMessage().contains("channel"));
        }

        @Test
        void validate_withNoContent_throws() {
            QueueMessage msg = QueueMessage.builder()
                    .channel("test-channel")
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    msg::validate
            );
            assertTrue(ex.getMessage().contains("metadata, body, or tags"));
        }

        @Test
        void validate_withNegativeDelay_throws() {
            QueueMessage msg = QueueMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .delayInSeconds(-1)
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    msg::validate
            );
            assertTrue(ex.getMessage().contains("delay"));
        }

        @Test
        void validate_withNegativeExpiration_throws() {
            QueueMessage msg = QueueMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .expirationInSeconds(-1)
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    msg::validate
            );
            assertTrue(ex.getMessage().contains("expiration"));
        }

        @Test
        void validate_withNegativeAttempts_throws() {
            QueueMessage msg = QueueMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .attemptsBeforeDeadLetterQueue(-1)
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    msg::validate
            );
            assertTrue(ex.getMessage().contains("attempts"));
        }

        @Test
        void validate_withAllOptionalFields_passes() {
            Map<String, String> tags = new HashMap<>();
            tags.put("key1", "value1");
            tags.put("key2", "value2");

            QueueMessage msg = QueueMessage.builder()
                    .id("msg-123")
                    .channel("test-channel")
                    .metadata("test metadata")
                    .body("test body".getBytes())
                    .tags(tags)
                    .delayInSeconds(10)
                    .expirationInSeconds(3600)
                    .attemptsBeforeDeadLetterQueue(3)
                    .deadLetterQueue("dlq-channel")
                    .build();

            assertDoesNotThrow(msg::validate);
        }
    }

    @Nested
    class EncodingTests {

        @Test
        void encodeMessage_setsClientId() {
            QueueMessage msg = QueueMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .build();

            Kubemq.QueueMessage proto = msg.encodeMessage("my-client");

            assertEquals("my-client", proto.getClientID());
        }

        @Test
        void encodeMessage_setsChannel() {
            QueueMessage msg = QueueMessage.builder()
                    .channel("orders")
                    .body("test".getBytes())
                    .build();

            Kubemq.QueueMessage proto = msg.encodeMessage("client");

            assertEquals("orders", proto.getChannel());
        }

        @Test
        void encodeMessage_generatesIdWhenNull() {
            QueueMessage msg = QueueMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .build();

            Kubemq.QueueMessage proto = msg.encodeMessage("client");

            assertNotNull(proto.getMessageID());
            assertFalse(proto.getMessageID().isEmpty());
        }

        @Test
        void encodeMessage_usesProvidedId() {
            QueueMessage msg = QueueMessage.builder()
                    .id("custom-id-123")
                    .channel("test-channel")
                    .body("test".getBytes())
                    .build();

            Kubemq.QueueMessage proto = msg.encodeMessage("client");

            assertEquals("custom-id-123", proto.getMessageID());
        }

        @Test
        void encodeMessage_setsBody() {
            byte[] body = "hello world".getBytes();
            QueueMessage msg = QueueMessage.builder()
                    .channel("test-channel")
                    .body(body)
                    .build();

            Kubemq.QueueMessage proto = msg.encodeMessage("client");

            assertArrayEquals(body, proto.getBody().toByteArray());
        }

        @Test
        void encodeMessage_setsMetadata() {
            QueueMessage msg = QueueMessage.builder()
                    .channel("test-channel")
                    .metadata("my-metadata")
                    .body("test".getBytes())
                    .build();

            Kubemq.QueueMessage proto = msg.encodeMessage("client");

            assertEquals("my-metadata", proto.getMetadata());
        }

        @Test
        void encodeMessage_setsTags() {
            Map<String, String> tags = new HashMap<>();
            tags.put("key1", "value1");
            tags.put("key2", "value2");

            QueueMessage msg = QueueMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .tags(tags)
                    .build();

            Kubemq.QueueMessage proto = msg.encodeMessage("client");

            assertEquals("value1", proto.getTagsMap().get("key1"));
            assertEquals("value2", proto.getTagsMap().get("key2"));
        }

        @Test
        void encodeMessage_setsPolicy() {
            QueueMessage msg = QueueMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .delayInSeconds(10)
                    .expirationInSeconds(3600)
                    .attemptsBeforeDeadLetterQueue(3)
                    .deadLetterQueue("dlq")
                    .build();

            Kubemq.QueueMessage proto = msg.encodeMessage("client");

            assertEquals(10, proto.getPolicy().getDelaySeconds());
            assertEquals(3600, proto.getPolicy().getExpirationSeconds());
            assertEquals(3, proto.getPolicy().getMaxReceiveCount());
            assertEquals("dlq", proto.getPolicy().getMaxReceiveQueue());
        }

        @Test
        void encode_wrapsMessageInUpstreamRequest() {
            QueueMessage msg = QueueMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .build();

            Kubemq.QueuesUpstreamRequest request = msg.encode("client");

            assertNotNull(request.getRequestID());
            assertEquals(1, request.getMessagesCount());
            assertEquals("test-channel", request.getMessages(0).getChannel());
        }
    }

    @Nested
    class DecodingTests {

        @Test
        void decode_mapsAllFields() {
            Map<String, String> tags = new HashMap<>();
            tags.put("key", "value");

            Kubemq.QueueMessage proto = Kubemq.QueueMessage.newBuilder()
                    .setMessageID("msg-123")
                    .setChannel("orders")
                    .setMetadata("meta")
                    .setBody(com.google.protobuf.ByteString.copyFrom("body".getBytes()))
                    .putAllTags(tags)
                    .setPolicy(Kubemq.QueueMessagePolicy.newBuilder()
                            .setDelaySeconds(10)
                            .setExpirationSeconds(3600)
                            .setMaxReceiveCount(3)
                            .setMaxReceiveQueue("dlq")
                            .build())
                    .build();

            QueueMessage decoded = QueueMessage.decode(proto);

            assertEquals("msg-123", decoded.getId());
            assertEquals("orders", decoded.getChannel());
            assertEquals("meta", decoded.getMetadata());
            assertArrayEquals("body".getBytes(), decoded.getBody());
            assertEquals("value", decoded.getTags().get("key"));
            assertEquals(10, decoded.getDelayInSeconds());
            assertEquals(3600, decoded.getExpirationInSeconds());
            assertEquals(3, decoded.getAttemptsBeforeDeadLetterQueue());
            assertEquals("dlq", decoded.getDeadLetterQueue());
        }

        @Test
        void decode_handlesEmptyFields() {
            Kubemq.QueueMessage proto = Kubemq.QueueMessage.newBuilder()
                    .setMessageID("msg-123")
                    .setChannel("test")
                    .build();

            QueueMessage decoded = QueueMessage.decode(proto);

            assertEquals("msg-123", decoded.getId());
            assertEquals("test", decoded.getChannel());
            assertEquals("", decoded.getMetadata());
            assertEquals(0, decoded.getBody().length);
            assertTrue(decoded.getTags().isEmpty());
        }
    }

    @Nested
    class RoundTripTests {

        @Test
        void encodeAndDecode_preservesAllFields() {
            Map<String, String> tags = new HashMap<>();
            tags.put("key1", "value1");
            tags.put("key2", "value2");

            QueueMessage original = QueueMessage.builder()
                    .id("msg-123")
                    .channel("orders")
                    .metadata("meta")
                    .body("payload".getBytes())
                    .tags(tags)
                    .delayInSeconds(10)
                    .expirationInSeconds(3600)
                    .attemptsBeforeDeadLetterQueue(3)
                    .deadLetterQueue("dlq")
                    .build();

            Kubemq.QueueMessage proto = original.encodeMessage("client");
            QueueMessage decoded = QueueMessage.decode(proto);

            assertEquals(original.getId(), decoded.getId());
            assertEquals(original.getChannel(), decoded.getChannel());
            assertEquals(original.getMetadata(), decoded.getMetadata());
            assertArrayEquals(original.getBody(), decoded.getBody());
            assertEquals(original.getTags(), decoded.getTags());
            assertEquals(original.getDelayInSeconds(), decoded.getDelayInSeconds());
            assertEquals(original.getExpirationInSeconds(), decoded.getExpirationInSeconds());
            assertEquals(original.getAttemptsBeforeDeadLetterQueue(), decoded.getAttemptsBeforeDeadLetterQueue());
            assertEquals(original.getDeadLetterQueue(), decoded.getDeadLetterQueue());
        }

        @Test
        void encodeAndDecode_withMinimalMessage() {
            QueueMessage original = QueueMessage.builder()
                    .channel("test")
                    .body("data".getBytes())
                    .build();

            Kubemq.QueueMessage proto = original.encodeMessage("client");
            QueueMessage decoded = QueueMessage.decode(proto);

            assertEquals(original.getChannel(), decoded.getChannel());
            assertArrayEquals(original.getBody(), decoded.getBody());
        }
    }
}
