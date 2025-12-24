package io.kubemq.sdk.unit.pubsub;

import io.kubemq.sdk.pubsub.EventStoreMessage;
import kubemq.Kubemq;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EventStoreMessage validation and encoding.
 */
class EventStoreMessageTest {

    @Nested
    class ValidationTests {

        @Test
        void validate_withValidMessage_passes() {
            EventStoreMessage msg = EventStoreMessage.builder()
                    .channel("test-channel")
                    .body("test body".getBytes())
                    .build();

            assertDoesNotThrow(msg::validate);
        }

        @Test
        void validate_withMetadataOnly_passes() {
            EventStoreMessage msg = EventStoreMessage.builder()
                    .channel("test-channel")
                    .metadata("test metadata")
                    .build();

            assertDoesNotThrow(msg::validate);
        }

        @Test
        void validate_withTagsOnly_passes() {
            Map<String, String> tags = new HashMap<>();
            tags.put("key", "value");

            EventStoreMessage msg = EventStoreMessage.builder()
                    .channel("test-channel")
                    .tags(tags)
                    .build();

            assertDoesNotThrow(msg::validate);
        }

        @Test
        void validate_withNullChannel_throws() {
            EventStoreMessage msg = EventStoreMessage.builder()
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
            EventStoreMessage msg = EventStoreMessage.builder()
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
            EventStoreMessage msg = EventStoreMessage.builder()
                    .channel("test-channel")
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    msg::validate
            );
            assertTrue(ex.getMessage().contains("metadata, body, or tags"));
        }
    }

    @Nested
    class EncodingTests {

        @Test
        void encode_setsClientId() {
            EventStoreMessage msg = EventStoreMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .build();

            Kubemq.Event proto = msg.encode("my-client");

            assertEquals("my-client", proto.getClientID());
        }

        @Test
        void encode_setsChannel() {
            EventStoreMessage msg = EventStoreMessage.builder()
                    .channel("events-store")
                    .body("test".getBytes())
                    .build();

            Kubemq.Event proto = msg.encode("client");

            assertEquals("events-store", proto.getChannel());
        }

        @Test
        void encode_setsStoreTrue() {
            EventStoreMessage msg = EventStoreMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .build();

            Kubemq.Event proto = msg.encode("client");

            assertTrue(proto.getStore());
        }

        @Test
        void encode_generatesIdWhenNull() {
            EventStoreMessage msg = EventStoreMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .build();

            Kubemq.Event proto = msg.encode("client");

            assertNotNull(proto.getEventID());
            assertFalse(proto.getEventID().isEmpty());
        }

        @Test
        void encode_usesProvidedId() {
            EventStoreMessage msg = EventStoreMessage.builder()
                    .id("custom-id-123")
                    .channel("test-channel")
                    .body("test".getBytes())
                    .build();

            Kubemq.Event proto = msg.encode("client");

            assertEquals("custom-id-123", proto.getEventID());
        }

        @Test
        void encode_setsBody() {
            byte[] body = "hello world".getBytes();
            EventStoreMessage msg = EventStoreMessage.builder()
                    .channel("test-channel")
                    .body(body)
                    .build();

            Kubemq.Event proto = msg.encode("client");

            assertArrayEquals(body, proto.getBody().toByteArray());
        }

        @Test
        void encode_setsMetadata() {
            EventStoreMessage msg = EventStoreMessage.builder()
                    .channel("test-channel")
                    .metadata("my-metadata")
                    .body("test".getBytes())
                    .build();

            Kubemq.Event proto = msg.encode("client");

            assertEquals("my-metadata", proto.getMetadata());
        }

        @Test
        void encode_setsTags() {
            Map<String, String> tags = new HashMap<>();
            tags.put("key1", "value1");
            tags.put("key2", "value2");

            EventStoreMessage msg = EventStoreMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .tags(tags)
                    .build();

            Kubemq.Event proto = msg.encode("client");

            assertEquals("value1", proto.getTagsMap().get("key1"));
            assertEquals("value2", proto.getTagsMap().get("key2"));
        }

        @Test
        void encode_addsClientIdTag() {
            EventStoreMessage msg = EventStoreMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .build();

            Kubemq.Event proto = msg.encode("my-client");

            assertEquals("my-client", proto.getTagsMap().get("x-kubemq-client-id"));
        }

        @Test
        void encode_handlesNullMetadata() {
            EventStoreMessage msg = EventStoreMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .build();

            Kubemq.Event proto = msg.encode("client");

            assertEquals("", proto.getMetadata());
        }

        @Test
        void encode_handlesNullTags() {
            EventStoreMessage msg = EventStoreMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .tags(null)
                    .build();

            Kubemq.Event proto = msg.encode("client");

            // Should add the client ID tag
            assertEquals("client", proto.getTagsMap().get("x-kubemq-client-id"));
        }
    }

    @Nested
    class DifferenceFromEventMessageTests {

        @Test
        void encode_storeFlag_differentiatesFromEventMessage() {
            // EventStoreMessage should have Store=true
            EventStoreMessage storeMsg = EventStoreMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .build();

            Kubemq.Event proto = storeMsg.encode("client");

            assertTrue(proto.getStore(), "EventStoreMessage should have Store=true");
        }
    }
}
