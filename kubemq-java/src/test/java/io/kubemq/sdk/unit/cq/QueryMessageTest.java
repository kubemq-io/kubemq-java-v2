package io.kubemq.sdk.unit.cq;

import io.kubemq.sdk.cq.QueryMessage;
import kubemq.Kubemq;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueryMessage validation and encoding.
 */
class QueryMessageTest {

    @Nested
    class ValidationTests {

        @Test
        void validate_withValidMessage_passes() {
            QueryMessage msg = QueryMessage.builder()
                    .channel("test-channel")
                    .body("test body".getBytes())
                    .timeoutInSeconds(30)
                    .build();

            assertDoesNotThrow(msg::validate);
        }

        @Test
        void validate_withMetadataOnly_passes() {
            QueryMessage msg = QueryMessage.builder()
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

            QueryMessage msg = QueryMessage.builder()
                    .channel("test-channel")
                    .tags(tags)
                    .timeoutInSeconds(30)
                    .build();

            assertDoesNotThrow(msg::validate);
        }

        @Test
        void validate_withNullChannel_throws() {
            QueryMessage msg = QueryMessage.builder()
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
            QueryMessage msg = QueryMessage.builder()
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
            QueryMessage msg = QueryMessage.builder()
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
            QueryMessage msg = QueryMessage.builder()
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
            QueryMessage msg = QueryMessage.builder()
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

        @Test
        void validate_withCacheSettings_passes() {
            QueryMessage msg = QueryMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .cacheKey("my-cache-key")
                    .cacheTtlInSeconds(300)
                    .build();

            assertDoesNotThrow(msg::validate);
        }
    }

    @Nested
    class EncodingTests {

        @Test
        void encode_setsClientId() {
            QueryMessage msg = QueryMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .build();

            Kubemq.Request proto = msg.encode("my-client");

            assertEquals("my-client", proto.getClientID());
        }

        @Test
        void encode_setsChannel() {
            QueryMessage msg = QueryMessage.builder()
                    .channel("queries")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .build();

            Kubemq.Request proto = msg.encode("client");

            assertEquals("queries", proto.getChannel());
        }

        @Test
        void encode_setsRequestTypeToQuery() {
            QueryMessage msg = QueryMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .build();

            Kubemq.Request proto = msg.encode("client");

            assertEquals(Kubemq.Request.RequestType.Query, proto.getRequestTypeData());
        }

        @Test
        void encode_convertsTimeoutToMilliseconds() {
            QueryMessage msg = QueryMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .build();

            Kubemq.Request proto = msg.encode("client");

            assertEquals(30000, proto.getTimeout());
        }

        @Test
        void encode_generatesIdWhenNull() {
            QueryMessage msg = QueryMessage.builder()
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
            QueryMessage msg = QueryMessage.builder()
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
            QueryMessage msg = QueryMessage.builder()
                    .channel("test-channel")
                    .body(body)
                    .timeoutInSeconds(30)
                    .build();

            Kubemq.Request proto = msg.encode("client");

            assertArrayEquals(body, proto.getBody().toByteArray());
        }

        @Test
        void encode_setsMetadata() {
            QueryMessage msg = QueryMessage.builder()
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

            QueryMessage msg = QueryMessage.builder()
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
            QueryMessage msg = QueryMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .build();

            Kubemq.Request proto = msg.encode("my-client");

            assertEquals("my-client", proto.getTagsMap().get("x-kubemq-client-id"));
        }

        @Test
        void encode_setsCacheKey() {
            QueryMessage msg = QueryMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .cacheKey("my-cache-key")
                    .build();

            Kubemq.Request proto = msg.encode("client");

            assertEquals("my-cache-key", proto.getCacheKey());
        }

        @Test
        void encode_setsCacheTtlInMilliseconds() {
            QueryMessage msg = QueryMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .cacheTtlInSeconds(300)
                    .build();

            Kubemq.Request proto = msg.encode("client");

            assertEquals(300000, proto.getCacheTTL());
        }

        @Test
        void encode_handlesNullCacheKey() {
            QueryMessage msg = QueryMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .build();

            Kubemq.Request proto = msg.encode("client");

            assertEquals("", proto.getCacheKey());
        }

        @Test
        void encode_handlesNullMetadata() {
            QueryMessage msg = QueryMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .build();

            Kubemq.Request proto = msg.encode("client");

            assertEquals("", proto.getMetadata());
        }
    }

    @Nested
    class DifferenceFromCommandMessageTests {

        @Test
        void encode_requestType_differentiatesFromCommandMessage() {
            // QueryMessage should have RequestType.Query
            QueryMessage msg = QueryMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .build();

            Kubemq.Request proto = msg.encode("client");

            assertEquals(Kubemq.Request.RequestType.Query, proto.getRequestTypeData());
        }

        @Test
        void encode_hasCacheFields() {
            // QueryMessage has cache fields that CommandMessage doesn't
            QueryMessage msg = QueryMessage.builder()
                    .channel("test-channel")
                    .body("test".getBytes())
                    .timeoutInSeconds(30)
                    .cacheKey("key")
                    .cacheTtlInSeconds(60)
                    .build();

            Kubemq.Request proto = msg.encode("client");

            assertEquals("key", proto.getCacheKey());
            assertEquals(60000, proto.getCacheTTL());
        }
    }
}
