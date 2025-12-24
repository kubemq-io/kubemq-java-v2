package io.kubemq.sdk.unit.cq;

import com.google.protobuf.ByteString;
import io.kubemq.sdk.cq.QueryMessageReceived;
import kubemq.Kubemq;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueryMessageReceived decoding.
 */
class QueryMessageReceivedTest {

    @Nested
    class DecodeTests {

        @Test
        void decode_setsId() {
            Kubemq.Request proto = Kubemq.Request.newBuilder()
                    .setRequestID("qry-123")
                    .setChannel("test-channel")
                    .build();

            QueryMessageReceived received = QueryMessageReceived.decode(proto);

            assertEquals("qry-123", received.getId());
        }

        @Test
        void decode_setsChannel() {
            Kubemq.Request proto = Kubemq.Request.newBuilder()
                    .setRequestID("qry-123")
                    .setChannel("queries-channel")
                    .build();

            QueryMessageReceived received = QueryMessageReceived.decode(proto);

            assertEquals("queries-channel", received.getChannel());
        }

        @Test
        void decode_setsMetadata() {
            Kubemq.Request proto = Kubemq.Request.newBuilder()
                    .setRequestID("qry-123")
                    .setChannel("test-channel")
                    .setMetadata("query-metadata")
                    .build();

            QueryMessageReceived received = QueryMessageReceived.decode(proto);

            assertEquals("query-metadata", received.getMetadata());
        }

        @Test
        void decode_setsBody() {
            byte[] body = "query payload".getBytes();
            Kubemq.Request proto = Kubemq.Request.newBuilder()
                    .setRequestID("qry-123")
                    .setChannel("test-channel")
                    .setBody(ByteString.copyFrom(body))
                    .build();

            QueryMessageReceived received = QueryMessageReceived.decode(proto);

            assertArrayEquals(body, received.getBody());
        }

        @Test
        void decode_setsFromClientId() {
            Kubemq.Request proto = Kubemq.Request.newBuilder()
                    .setRequestID("qry-123")
                    .setChannel("test-channel")
                    .setClientID("sender-client")
                    .build();

            QueryMessageReceived received = QueryMessageReceived.decode(proto);

            assertEquals("sender-client", received.getFromClientId());
        }

        @Test
        void decode_setsReplyChannel() {
            Kubemq.Request proto = Kubemq.Request.newBuilder()
                    .setRequestID("qry-123")
                    .setChannel("test-channel")
                    .setReplyChannel("reply-channel-456")
                    .build();

            QueryMessageReceived received = QueryMessageReceived.decode(proto);

            assertEquals("reply-channel-456", received.getReplyChannel());
        }

        @Test
        void decode_setsTags() {
            Kubemq.Request proto = Kubemq.Request.newBuilder()
                    .setRequestID("qry-123")
                    .setChannel("test-channel")
                    .putTags("key1", "value1")
                    .putTags("key2", "value2")
                    .build();

            QueryMessageReceived received = QueryMessageReceived.decode(proto);

            assertEquals("value1", received.getTags().get("key1"));
            assertEquals("value2", received.getTags().get("key2"));
        }

        @Test
        void decode_setsTimestamp() {
            Kubemq.Request proto = Kubemq.Request.newBuilder()
                    .setRequestID("qry-123")
                    .setChannel("test-channel")
                    .build();

            QueryMessageReceived received = QueryMessageReceived.decode(proto);

            assertNotNull(received.getTimestamp());
        }

        @Test
        void decode_handlesEmptyBody() {
            Kubemq.Request proto = Kubemq.Request.newBuilder()
                    .setRequestID("qry-123")
                    .setChannel("test-channel")
                    .build();

            QueryMessageReceived received = QueryMessageReceived.decode(proto);

            assertNotNull(received.getBody());
            assertEquals(0, received.getBody().length);
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toString_includesAllFields() {
            Kubemq.Request proto = Kubemq.Request.newBuilder()
                    .setRequestID("qry-123")
                    .setChannel("my-channel")
                    .setMetadata("my-metadata")
                    .setReplyChannel("reply-456")
                    .setClientID("client-1")
                    .build();

            QueryMessageReceived received = QueryMessageReceived.decode(proto);
            String str = received.toString();

            assertTrue(str.contains("qry-123"));
            assertTrue(str.contains("my-channel"));
            assertTrue(str.contains("reply-456"));
        }
    }
}
