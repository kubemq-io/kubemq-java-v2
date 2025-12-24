package io.kubemq.sdk.unit.cq;

import io.kubemq.sdk.cq.CommandMessage;
import kubemq.Kubemq;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CommandMessage validation and encoding.
 */
class CommandMessageTest {

    @Nested
    class ValidationTests {

        @Test
        void validate_withValidMessage_passes() {
            CommandMessage msg = CommandMessage.builder()
                    .channel("test-channel")
                    .body("test body".getBytes())
                    .timeoutInSeconds(30)
                    .build();

            assertDoesNotThrow(msg::validate);
        }

        @Test
        void validate_withMetadataOnly_passes() {
            CommandMessage msg = CommandMessage.builder()
                    .channel("test-channel")
                    .metadata("test metadata")
                    .timeoutInSeconds(30)
                    .build();

            assertDoesNotThrow(msg::validate);
        }

        @Test
        void validate_withTagsOnly_passes() {
            Map<String, String> tags = new HashMap<>();
            tags.put("key", "value");

            CommandMessage msg = CommandMessage.builder()
                    .channel("test-channel")
                    .tags(tags)
                    .timeoutInSeconds(30)
                    .build();

            assertDoesNotThrow(msg::validate);
        }

        @Test
        void validate_withNullChannel_throws() {
            CommandMessage msg = CommandMessage.builder()
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    msg::validate
            );
            assertTrue(ex.getMessage().contains("channel"));
        }

        @Test
        void validate_withEmptyChannel_throws() {
            CommandMessage msg = CommandMessage.builder()
                    .channel("")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    msg::validate
            );
            assertTrue(ex.getMessage().contains("channel"));
        }

        @Test
        void validate_withNoContent_throws() {
            CommandMessage msg = CommandMessage.builder()
                    .channel("test-channel")
                    .timeoutInSeconds(30)
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    msg::validate
            );
            assertTrue(ex.getMessage().contains("metadata, body, or tags"));
        }

        @Test
        void validate_withZeroTimeout_throws() {
            CommandMessage msg = CommandMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .timeoutInSeconds(0)
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    msg::validate
            );
            assertTrue(ex.getMessage().contains("timeout"));
        }

        @Test
        void validate_withNegativeTimeout_throws() {
            CommandMessage msg = CommandMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .timeoutInSeconds(-1)
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    msg::validate
            );
            assertTrue(ex.getMessage().contains("timeout"));
        }
    }

    @Nested
    class EncodingTests {

        @Test
        void encode_setsClientId() {
            CommandMessage msg = CommandMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .build();

            Kubemq.Request proto = msg.encode("my-client");

            assertEquals("my-client", proto.getClientID());
        }

        @Test
        void encode_setsChannel() {
            CommandMessage msg = CommandMessage.builder()
                    .channel("commands")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .build();

            Kubemq.Request proto = msg.encode("client");

            assertEquals("commands", proto.getChannel());
        }

        @Test
        void encode_setsRequestTypeToCommand() {
            CommandMessage msg = CommandMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .build();

            Kubemq.Request proto = msg.encode("client");

            assertEquals(Kubemq.Request.RequestType.Command, proto.getRequestTypeData());
        }

        @Test
        void encode_convertsTimeoutToMilliseconds() {
            CommandMessage msg = CommandMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .build();

            Kubemq.Request proto = msg.encode("client");

            assertEquals(30000, proto.getTimeout());
        }

        @Test
        void encode_generatesIdWhenNull() {
            CommandMessage msg = CommandMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .build();

            Kubemq.Request proto = msg.encode("client");

            assertNotNull(proto.getRequestID());
            assertFalse(proto.getRequestID().isEmpty());
        }

        @Test
        void encode_usesProvidedId() {
            CommandMessage msg = CommandMessage.builder()
                    .id("custom-id-123")
                    .channel("test-channel")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .build();

            Kubemq.Request proto = msg.encode("client");

            assertEquals("custom-id-123", proto.getRequestID());
        }

        @Test
        void encode_setsBody() {
            byte[] body = "hello world".getBytes();
            CommandMessage msg = CommandMessage.builder()
                    .channel("test-channel")
                    .body(body)
                    .timeoutInSeconds(30)
                    .build();

            Kubemq.Request proto = msg.encode("client");

            assertArrayEquals(body, proto.getBody().toByteArray());
        }

        @Test
        void encode_setsMetadata() {
            CommandMessage msg = CommandMessage.builder()
                    .channel("test-channel")
                    .metadata("my-metadata")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .build();

            Kubemq.Request proto = msg.encode("client");

            assertEquals("my-metadata", proto.getMetadata());
        }

        @Test
        void encode_setsTags() {
            Map<String, String> tags = new HashMap<>();
            tags.put("key1", "value1");
            tags.put("key2", "value2");

            CommandMessage msg = CommandMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .tags(tags)
                    .timeoutInSeconds(30)
                    .build();

            Kubemq.Request proto = msg.encode("client");

            assertEquals("value1", proto.getTagsMap().get("key1"));
            assertEquals("value2", proto.getTagsMap().get("key2"));
        }

        @Test
        void encode_addsClientIdTag() {
            CommandMessage msg = CommandMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .build();

            Kubemq.Request proto = msg.encode("my-client");

            assertEquals("my-client", proto.getTagsMap().get("x-kubemq-client-id"));
        }

        @Test
        void encode_handlesNullMetadata() {
            CommandMessage msg = CommandMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .build();

            Kubemq.Request proto = msg.encode("client");

            assertEquals("", proto.getMetadata());
        }
    }
}
