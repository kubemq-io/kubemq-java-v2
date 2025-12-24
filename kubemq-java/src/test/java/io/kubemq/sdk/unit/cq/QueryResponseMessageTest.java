package io.kubemq.sdk.unit.cq;

import io.kubemq.sdk.cq.QueryMessageReceived;
import io.kubemq.sdk.cq.QueryResponseMessage;
import kubemq.Kubemq;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueryResponseMessage encode, decode, and validation.
 */
class QueryResponseMessageTest {

    private QueryMessageReceived createMockQueryReceived(String id, String replyChannel) {
        QueryMessageReceived query = new QueryMessageReceived();
        query.setId(id);
        query.setFromClientId("sender-client");
        query.setTimestamp(LocalDateTime.now());
        query.setChannel("queries-channel");
        query.setMetadata("test-metadata");
        query.setBody("test-body".getBytes());
        query.setReplyChannel(replyChannel);
        query.setTags(new HashMap<>());
        return query;
    }

    @Nested
    class ValidationTests {

        @Test
        void validate_withValidResponse_passes() {
            QueryResponseMessage response = QueryResponseMessage.builder()
                    .queryReceived(createMockQueryReceived("query-123", "reply-channel"))
                    .isExecuted(true)
                    .build();

            assertDoesNotThrow(response::validate);
        }

        @Test
        void validate_withoutQueryReceived_throws() {
            QueryResponseMessage response = QueryResponseMessage.builder()
                    .isExecuted(true)
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    response::validate
            );
            assertTrue(ex.getMessage().contains("query request"));
        }

        @Test
        void validate_withEmptyReplyChannel_throws() {
            QueryResponseMessage response = QueryResponseMessage.builder()
                    .queryReceived(createMockQueryReceived("query-123", ""))
                    .isExecuted(true)
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    response::validate
            );
            assertTrue(ex.getMessage().contains("reply channel"));
        }

        @Test
        void validate_returnsThis() {
            QueryResponseMessage response = QueryResponseMessage.builder()
                    .queryReceived(createMockQueryReceived("query-123", "reply-channel"))
                    .isExecuted(true)
                    .build();

            QueryResponseMessage validated = response.validate();

            assertSame(response, validated);
        }
    }

    @Nested
    class EncodeTests {

        @Test
        void encode_setsClientId() {
            QueryResponseMessage response = QueryResponseMessage.builder()
                    .queryReceived(createMockQueryReceived("query-123", "reply-channel"))
                    .isExecuted(true)
                    .timestamp(LocalDateTime.now())
                    .build();

            Kubemq.Response proto = response.encode("my-client");

            assertEquals("my-client", proto.getClientID());
        }

        @Test
        void encode_setsRequestId() {
            QueryResponseMessage response = QueryResponseMessage.builder()
                    .queryReceived(createMockQueryReceived("query-456", "reply-channel"))
                    .isExecuted(true)
                    .timestamp(LocalDateTime.now())
                    .build();

            Kubemq.Response proto = response.encode("client");

            assertEquals("query-456", proto.getRequestID());
        }

        @Test
        void encode_setsReplyChannel() {
            QueryResponseMessage response = QueryResponseMessage.builder()
                    .queryReceived(createMockQueryReceived("query-123", "my-reply-channel"))
                    .isExecuted(true)
                    .timestamp(LocalDateTime.now())
                    .build();

            Kubemq.Response proto = response.encode("client");

            assertEquals("my-reply-channel", proto.getReplyChannel());
        }

        @Test
        void encode_setsExecuted() {
            QueryResponseMessage response = QueryResponseMessage.builder()
                    .queryReceived(createMockQueryReceived("query-123", "reply-channel"))
                    .isExecuted(true)
                    .timestamp(LocalDateTime.now())
                    .build();

            Kubemq.Response proto = response.encode("client");

            assertTrue(proto.getExecuted());
        }

        @Test
        void encode_setsBody() {
            byte[] body = "response-body".getBytes();
            QueryResponseMessage response = QueryResponseMessage.builder()
                    .queryReceived(createMockQueryReceived("query-123", "reply-channel"))
                    .isExecuted(true)
                    .body(body)
                    .timestamp(LocalDateTime.now())
                    .build();

            Kubemq.Response proto = response.encode("client");

            assertArrayEquals(body, proto.getBody().toByteArray());
        }

        @Test
        void encode_setsMetadata() {
            QueryResponseMessage response = QueryResponseMessage.builder()
                    .queryReceived(createMockQueryReceived("query-123", "reply-channel"))
                    .isExecuted(true)
                    .metadata("response-metadata")
                    .timestamp(LocalDateTime.now())
                    .build();

            Kubemq.Response proto = response.encode("client");

            assertEquals("response-metadata", proto.getMetadata());
        }

        @Test
        void encode_withNullMetadata_usesEmptyString() {
            QueryResponseMessage response = QueryResponseMessage.builder()
                    .queryReceived(createMockQueryReceived("query-123", "reply-channel"))
                    .isExecuted(true)
                    .metadata(null)
                    .timestamp(LocalDateTime.now())
                    .build();

            Kubemq.Response proto = response.encode("client");

            assertEquals("", proto.getMetadata());
        }

        @Test
        void encode_setsTags() {
            Map<String, String> tags = new HashMap<>();
            tags.put("key1", "value1");
            tags.put("key2", "value2");

            QueryResponseMessage response = QueryResponseMessage.builder()
                    .queryReceived(createMockQueryReceived("query-123", "reply-channel"))
                    .isExecuted(true)
                    .tags(tags)
                    .timestamp(LocalDateTime.now())
                    .build();

            Kubemq.Response proto = response.encode("client");

            assertEquals("value1", proto.getTagsMap().get("key1"));
            assertEquals("value2", proto.getTagsMap().get("key2"));
        }

        @Test
        void encode_setsError() {
            QueryResponseMessage response = QueryResponseMessage.builder()
                    .queryReceived(createMockQueryReceived("query-123", "reply-channel"))
                    .isExecuted(false)
                    .error("Query execution failed")
                    .timestamp(LocalDateTime.now())
                    .build();

            Kubemq.Response proto = response.encode("client");

            assertEquals("Query execution failed", proto.getError());
        }

        @Test
        void encode_withNullError_usesEmptyString() {
            QueryResponseMessage response = QueryResponseMessage.builder()
                    .queryReceived(createMockQueryReceived("query-123", "reply-channel"))
                    .isExecuted(true)
                    .error(null)
                    .timestamp(LocalDateTime.now())
                    .build();

            Kubemq.Response proto = response.encode("client");

            assertEquals("", proto.getError());
        }

        @Test
        void encode_setsTimestamp() {
            LocalDateTime timestamp = LocalDateTime.of(2023, 12, 25, 10, 30, 0);
            QueryResponseMessage response = QueryResponseMessage.builder()
                    .queryReceived(createMockQueryReceived("query-123", "reply-channel"))
                    .isExecuted(true)
                    .timestamp(timestamp)
                    .build();

            Kubemq.Response proto = response.encode("client");

            assertTrue(proto.getTimestamp() > 0);
        }

        @Test
        void encode_withNullTimestamp_usesCurrentTime() {
            QueryResponseMessage response = QueryResponseMessage.builder()
                    .queryReceived(createMockQueryReceived("query-123", "reply-channel"))
                    .isExecuted(true)
                    .timestamp(null)
                    .build();

            Kubemq.Response proto = response.encode("client");

            assertTrue(proto.getTimestamp() > 0);
        }
    }

    @Nested
    class DecodeTests {

        @Test
        void decode_setsClientId() {
            Kubemq.Response proto = Kubemq.Response.newBuilder()
                    .setClientID("decoded-client")
                    .setRequestID("req-123")
                    .setExecuted(true)
                    .setError("")
                    .setMetadata("")
                    .setBody(com.google.protobuf.ByteString.EMPTY)
                    .setTimestamp(System.currentTimeMillis() * 1_000_000L)
                    .build();

            QueryResponseMessage response = new QueryResponseMessage().decode(proto);

            assertEquals("decoded-client", response.getClientId());
        }

        @Test
        void decode_setsRequestId() {
            Kubemq.Response proto = Kubemq.Response.newBuilder()
                    .setClientID("client")
                    .setRequestID("req-456")
                    .setExecuted(true)
                    .setError("")
                    .setMetadata("")
                    .setBody(com.google.protobuf.ByteString.EMPTY)
                    .setTimestamp(System.currentTimeMillis() * 1_000_000L)
                    .build();

            QueryResponseMessage response = new QueryResponseMessage().decode(proto);

            assertEquals("req-456", response.getRequestId());
        }

        @Test
        void decode_setsBody() {
            byte[] body = "decoded-body".getBytes();
            Kubemq.Response proto = Kubemq.Response.newBuilder()
                    .setClientID("client")
                    .setRequestID("req-123")
                    .setExecuted(true)
                    .setError("")
                    .setMetadata("")
                    .setBody(com.google.protobuf.ByteString.copyFrom(body))
                    .setTimestamp(System.currentTimeMillis() * 1_000_000L)
                    .build();

            QueryResponseMessage response = new QueryResponseMessage().decode(proto);

            assertArrayEquals(body, response.getBody());
        }

        @Test
        void decode_setsMetadata() {
            Kubemq.Response proto = Kubemq.Response.newBuilder()
                    .setClientID("client")
                    .setRequestID("req-123")
                    .setExecuted(true)
                    .setError("")
                    .setMetadata("decoded-metadata")
                    .setBody(com.google.protobuf.ByteString.EMPTY)
                    .setTimestamp(System.currentTimeMillis() * 1_000_000L)
                    .build();

            QueryResponseMessage response = new QueryResponseMessage().decode(proto);

            assertEquals("decoded-metadata", response.getMetadata());
        }

        @Test
        void decode_setsTags() {
            Map<String, String> tags = new HashMap<>();
            tags.put("decoded-key", "decoded-value");

            Kubemq.Response proto = Kubemq.Response.newBuilder()
                    .setClientID("client")
                    .setRequestID("req-123")
                    .setExecuted(true)
                    .setError("")
                    .setMetadata("")
                    .setBody(com.google.protobuf.ByteString.EMPTY)
                    .putAllTags(tags)
                    .setTimestamp(System.currentTimeMillis() * 1_000_000L)
                    .build();

            QueryResponseMessage response = new QueryResponseMessage().decode(proto);

            assertEquals("decoded-value", response.getTags().get("decoded-key"));
        }

        @Test
        void decode_setsError() {
            Kubemq.Response proto = Kubemq.Response.newBuilder()
                    .setClientID("client")
                    .setRequestID("req-123")
                    .setExecuted(false)
                    .setError("Decoded error")
                    .setMetadata("")
                    .setBody(com.google.protobuf.ByteString.EMPTY)
                    .setTimestamp(System.currentTimeMillis() * 1_000_000L)
                    .build();

            QueryResponseMessage response = new QueryResponseMessage().decode(proto);

            assertEquals("Decoded error", response.getError());
        }

        @Test
        void decode_setsTimestamp() {
            long timestampNanos = LocalDateTime.of(2023, 12, 25, 10, 0, 0)
                    .atZone(ZoneId.systemDefault()).toInstant().getEpochSecond() * 1_000_000_000L;

            Kubemq.Response proto = Kubemq.Response.newBuilder()
                    .setClientID("client")
                    .setRequestID("req-123")
                    .setExecuted(true)
                    .setError("")
                    .setMetadata("")
                    .setBody(com.google.protobuf.ByteString.EMPTY)
                    .setTimestamp(timestampNanos)
                    .build();

            QueryResponseMessage response = new QueryResponseMessage().decode(proto);

            assertNotNull(response.getTimestamp());
        }

        @Test
        void decode_returnsThis() {
            Kubemq.Response proto = Kubemq.Response.newBuilder()
                    .setClientID("client")
                    .setRequestID("req-123")
                    .setExecuted(true)
                    .setError("")
                    .setMetadata("")
                    .setBody(com.google.protobuf.ByteString.EMPTY)
                    .setTimestamp(System.currentTimeMillis() * 1_000_000L)
                    .build();

            QueryResponseMessage response = new QueryResponseMessage();
            QueryResponseMessage decoded = response.decode(proto);

            assertSame(response, decoded);
        }
    }

    @Nested
    class RoundTripTests {

        @Test
        void encodeAndDecode_preservesFields() {
            Map<String, String> tags = new HashMap<>();
            tags.put("key1", "value1");
            tags.put("key2", "value2");

            LocalDateTime timestamp = LocalDateTime.now();
            byte[] body = "roundtrip-body".getBytes();

            QueryResponseMessage original = QueryResponseMessage.builder()
                    .queryReceived(createMockQueryReceived("query-roundtrip", "reply-channel"))
                    .isExecuted(true)
                    .metadata("roundtrip-metadata")
                    .body(body)
                    .tags(tags)
                    .error("")
                    .timestamp(timestamp)
                    .build();

            Kubemq.Response proto = original.encode("test-client");
            QueryResponseMessage decoded = new QueryResponseMessage().decode(proto);

            assertEquals("test-client", decoded.getClientId());
            assertEquals("query-roundtrip", decoded.getRequestId());
            assertTrue(decoded.isExecuted());
            assertEquals("roundtrip-metadata", decoded.getMetadata());
            assertArrayEquals(body, decoded.getBody());
            assertEquals("value1", decoded.getTags().get("key1"));
            assertEquals("value2", decoded.getTags().get("key2"));
            assertEquals("", decoded.getError());
            assertNotNull(decoded.getTimestamp());
        }

        @Test
        void encodeAndDecode_withError_preservesError() {
            QueryResponseMessage original = QueryResponseMessage.builder()
                    .queryReceived(createMockQueryReceived("query-error", "reply-channel"))
                    .isExecuted(false)
                    .error("Query execution failed")
                    .timestamp(LocalDateTime.now())
                    .build();

            Kubemq.Response proto = original.encode("test-client");
            QueryResponseMessage decoded = new QueryResponseMessage().decode(proto);

            assertFalse(decoded.isExecuted());
            assertEquals("Query execution failed", decoded.getError());
        }

        @Test
        void encodeAndDecode_withEmptyBody_preservesEmptyBody() {
            QueryResponseMessage original = QueryResponseMessage.builder()
                    .queryReceived(createMockQueryReceived("query-empty", "reply-channel"))
                    .isExecuted(true)
                    .body(new byte[0])
                    .timestamp(LocalDateTime.now())
                    .build();

            Kubemq.Response proto = original.encode("test-client");
            QueryResponseMessage decoded = new QueryResponseMessage().decode(proto);

            assertEquals(0, decoded.getBody().length);
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toString_includesAllFields() {
            QueryResponseMessage response = QueryResponseMessage.builder()
                    .clientId("client-123")
                    .requestId("req-456")
                    .isExecuted(true)
                    .error("none")
                    .timestamp(LocalDateTime.of(2023, 12, 25, 10, 30, 0))
                    .build();

            String str = response.toString();

            assertTrue(str.contains("client-123"));
            assertTrue(str.contains("req-456"));
            assertTrue(str.contains("true"));
        }

        @Test
        void toString_withError_includesError() {
            QueryResponseMessage response = QueryResponseMessage.builder()
                    .clientId("client")
                    .requestId("req")
                    .isExecuted(false)
                    .error("query failed")
                    .build();

            String str = response.toString();

            assertTrue(str.contains("query failed"));
        }
    }
}
