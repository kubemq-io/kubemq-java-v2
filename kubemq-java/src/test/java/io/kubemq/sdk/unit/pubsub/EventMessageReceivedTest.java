package io.kubemq.sdk.unit.pubsub;

import com.google.protobuf.ByteString;
import io.kubemq.sdk.pubsub.EventMessageReceived;
import kubemq.Kubemq;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EventMessageReceived decoding.
 */
class EventMessageReceivedTest {

    @Nested
    class DecodeTests {

        @Test
        void decode_setsId() {
            Kubemq.EventReceive proto = Kubemq.EventReceive.newBuilder()
                    .setEventID("event-123")
                    .setChannel("test-channel")
                    .build();

            EventMessageReceived received = EventMessageReceived.decode(proto);

            assertEquals("event-123", received.getId());
        }

        @Test
        void decode_setsChannel() {
            Kubemq.EventReceive proto = Kubemq.EventReceive.newBuilder()
                    .setEventID("event-123")
                    .setChannel("events-channel")
                    .build();

            EventMessageReceived received = EventMessageReceived.decode(proto);

            assertEquals("events-channel", received.getChannel());
        }

        @Test
        void decode_setsMetadata() {
            Kubemq.EventReceive proto = Kubemq.EventReceive.newBuilder()
                    .setEventID("event-123")
                    .setChannel("test-channel")
                    .setMetadata("event-metadata")
                    .build();

            EventMessageReceived received = EventMessageReceived.decode(proto);

            assertEquals("event-metadata", received.getMetadata());
        }

        @Test
        void decode_setsBody() {
            byte[] body = "event body content".getBytes();
            Kubemq.EventReceive proto = Kubemq.EventReceive.newBuilder()
                    .setEventID("event-123")
                    .setChannel("test-channel")
                    .setBody(ByteString.copyFrom(body))
                    .build();

            EventMessageReceived received = EventMessageReceived.decode(proto);

            assertArrayEquals(body, received.getBody());
        }

        @Test
        void decode_setsTags() {
            Kubemq.EventReceive proto = Kubemq.EventReceive.newBuilder()
                    .setEventID("event-123")
                    .setChannel("test-channel")
                    .putTags("key1", "value1")
                    .putTags("key2", "value2")
                    .build();

            EventMessageReceived received = EventMessageReceived.decode(proto);

            assertEquals("value1", received.getTags().get("key1"));
            assertEquals("value2", received.getTags().get("key2"));
        }

        @Test
        void decode_setsFromClientId_fromTag() {
            Kubemq.EventReceive proto = Kubemq.EventReceive.newBuilder()
                    .setEventID("event-123")
                    .setChannel("test-channel")
                    .putTags("x-kubemq-client-id", "sender-client")
                    .build();

            EventMessageReceived received = EventMessageReceived.decode(proto);

            assertEquals("sender-client", received.getFromClientId());
        }

        @Test
        void decode_setsEmptyFromClientId_whenTagMissing() {
            Kubemq.EventReceive proto = Kubemq.EventReceive.newBuilder()
                    .setEventID("event-123")
                    .setChannel("test-channel")
                    .build();

            EventMessageReceived received = EventMessageReceived.decode(proto);

            assertEquals("", received.getFromClientId());
        }

        @Test
        void decode_handlesEmptyBody() {
            Kubemq.EventReceive proto = Kubemq.EventReceive.newBuilder()
                    .setEventID("event-123")
                    .setChannel("test-channel")
                    .build();

            EventMessageReceived received = EventMessageReceived.decode(proto);

            assertNotNull(received.getBody());
            assertEquals(0, received.getBody().length);
        }

        @Test
        void decode_handlesEmptyMetadata() {
            Kubemq.EventReceive proto = Kubemq.EventReceive.newBuilder()
                    .setEventID("event-123")
                    .setChannel("test-channel")
                    .build();

            EventMessageReceived received = EventMessageReceived.decode(proto);

            assertEquals("", received.getMetadata());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toString_includesAllFields() {
            Kubemq.EventReceive proto = Kubemq.EventReceive.newBuilder()
                    .setEventID("event-123")
                    .setChannel("my-channel")
                    .setMetadata("my-metadata")
                    .setBody(ByteString.copyFrom("body".getBytes()))
                    .putTags("x-kubemq-client-id", "client-1")
                    .build();

            EventMessageReceived received = EventMessageReceived.decode(proto);
            String str = received.toString();

            assertTrue(str.contains("event-123"));
            assertTrue(str.contains("my-channel"));
            assertTrue(str.contains("my-metadata"));
        }
    }
}
