package io.kubemq.sdk.unit.pubsub;

import io.kubemq.sdk.pubsub.EventMessage;
import kubemq.Kubemq;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EventMessage validation and encoding.
 */
class EventMessageTest {

    @Nested
    class ValidationTests {

        @Test
        void validate_withValidMessage_passes() {
            EventMessage msg = EventMessage.builder()
                    .channel("test-channel")
                    .body("test body".getBytes())
                    .build();

            assertDoesNotThrow(msg::validate);
        }

        @Test
        void validate_withMetadataOnly_passes() {
            EventMessage msg = EventMessage.builder()
                    .channel("test-channel")
                    .metadata("test metadata")
                    .build();

            assertDoesNotThrow(msg::validate);
        }

        @Test
        void validate_withTagsOnly_passes() {
            Map<String, String> tags = new HashMap<>();
            tags.put("key", "value");

            EventMessage msg = EventMessage.builder()
                    .channel("test-channel")
                    .tags(tags)
                    .build();

            assertDoesNotThrow(msg::validate);
        }

        @Test
        void validate_withNullChannel_throws() {
            EventMessage msg = EventMessage.builder()
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
            EventMessage msg = EventMessage.builder()
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
            EventMessage msg = EventMessage.builder()
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
            EventMessage msg = EventMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .build();

            Kubemq.Event proto = msg.encode("my-client");

            assertEquals("my-client", proto.getClientID());
        }

        @Test
        void encode_setsChannel() {
            EventMessage msg = EventMessage.builder()
                    .channel("events")
                    .body("test".getBytes())
                    .build();

            Kubemq.Event proto = msg.encode("client");

            assertEquals("events", proto.getChannel());
        }

        @Test
        void encode_setsStoreFalse() {
            EventMessage msg = EventMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .build();

            Kubemq.Event proto = msg.encode("client");

            assertFalse(proto.getStore());
        }

        @Test
        void encode_generatesIdWhenNull() {
            EventMessage msg = EventMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .build();

            Kubemq.Event proto = msg.encode("client");

            assertNotNull(proto.getEventID());
            assertFalse(proto.getEventID().isEmpty());
        }

        @Test
        void encode_usesProvidedId() {
            EventMessage msg = EventMessage.builder()
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
            EventMessage msg = EventMessage.builder()
                    .channel("test-channel")
                    .body(body)
                    .build();

            Kubemq.Event proto = msg.encode("client");

            assertArrayEquals(body, proto.getBody().toByteArray());
        }

        @Test
        void encode_setsMetadata() {
            EventMessage msg = EventMessage.builder()
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

            EventMessage msg = EventMessage.builder()
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
            EventMessage msg = EventMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .build();

            Kubemq.Event proto = msg.encode("my-client");

            assertEquals("my-client", proto.getTagsMap().get("x-kubemq-client-id"));
        }

        @Test
        void encode_handlesNullMetadata() {
            EventMessage msg = EventMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .build();

            Kubemq.Event proto = msg.encode("client");

            assertEquals("", proto.getMetadata());
        }
    }

    @Nested
    class RoundTripTests {

        @Test
        void encodeAndDecode_preservesChannel() {
            EventMessage original = EventMessage.builder()
                    .id("event-123")
                    .channel("test-channel")
                    .body("test body".getBytes())
                    .build();

            // Encode
            Kubemq.Event encoded = original.encode("client-1");

            // Simulate received message (EventReceive has same structure)
            Kubemq.EventReceive received = Kubemq.EventReceive.newBuilder()
                    .setEventID(encoded.getEventID())
                    .setChannel(encoded.getChannel())
                    .setMetadata(encoded.getMetadata())
                    .setBody(encoded.getBody())
                    .putAllTags(encoded.getTagsMap())
                    .build();

            // Decode
            io.kubemq.sdk.pubsub.EventMessageReceived decoded =
                    io.kubemq.sdk.pubsub.EventMessageReceived.decode(received);

            // Verify round-trip
            assertEquals(original.getChannel(), decoded.getChannel());
        }

        @Test
        void encodeAndDecode_preservesBody() {
            byte[] body = "hello world event".getBytes();
            EventMessage original = EventMessage.builder()
                    .channel("test-channel")
                    .body(body)
                    .build();

            Kubemq.Event encoded = original.encode("client-1");

            Kubemq.EventReceive received = Kubemq.EventReceive.newBuilder()
                    .setEventID(encoded.getEventID())
                    .setChannel(encoded.getChannel())
                    .setBody(encoded.getBody())
                    .build();

            io.kubemq.sdk.pubsub.EventMessageReceived decoded =
                    io.kubemq.sdk.pubsub.EventMessageReceived.decode(received);

            assertArrayEquals(original.getBody(), decoded.getBody());
        }

        @Test
        void encodeAndDecode_preservesMetadata() {
            EventMessage original = EventMessage.builder()
                    .channel("test-channel")
                    .metadata("my-metadata")
                    .body("test".getBytes())
                    .build();

            Kubemq.Event encoded = original.encode("client-1");

            Kubemq.EventReceive received = Kubemq.EventReceive.newBuilder()
                    .setEventID(encoded.getEventID())
                    .setChannel(encoded.getChannel())
                    .setMetadata(encoded.getMetadata())
                    .setBody(encoded.getBody())
                    .build();

            io.kubemq.sdk.pubsub.EventMessageReceived decoded =
                    io.kubemq.sdk.pubsub.EventMessageReceived.decode(received);

            assertEquals(original.getMetadata(), decoded.getMetadata());
        }

        @Test
        void encodeAndDecode_preservesTags() {
            Map<String, String> tags = new HashMap<>();
            tags.put("env", "test");
            tags.put("version", "1.0");

            EventMessage original = EventMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .tags(tags)
                    .build();

            Kubemq.Event encoded = original.encode("client-1");

            Kubemq.EventReceive received = Kubemq.EventReceive.newBuilder()
                    .setEventID(encoded.getEventID())
                    .setChannel(encoded.getChannel())
                    .setBody(encoded.getBody())
                    .putAllTags(encoded.getTagsMap())
                    .build();

            io.kubemq.sdk.pubsub.EventMessageReceived decoded =
                    io.kubemq.sdk.pubsub.EventMessageReceived.decode(received);

            assertEquals("test", decoded.getTags().get("env"));
            assertEquals("1.0", decoded.getTags().get("version"));
        }

        @Test
        void encodeAndDecode_preservesId() {
            EventMessage original = EventMessage.builder()
                    .id("custom-event-id")
                    .channel("test-channel")
                    .body("test".getBytes())
                    .build();

            Kubemq.Event encoded = original.encode("client-1");

            Kubemq.EventReceive received = Kubemq.EventReceive.newBuilder()
                    .setEventID(encoded.getEventID())
                    .setChannel(encoded.getChannel())
                    .setBody(encoded.getBody())
                    .build();

            io.kubemq.sdk.pubsub.EventMessageReceived decoded =
                    io.kubemq.sdk.pubsub.EventMessageReceived.decode(received);

            assertEquals(original.getId(), decoded.getId());
        }
    }
}
