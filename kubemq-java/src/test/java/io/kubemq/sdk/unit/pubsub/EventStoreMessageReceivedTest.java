package io.kubemq.sdk.unit.pubsub;

import com.google.protobuf.ByteString;
import io.kubemq.sdk.pubsub.EventStoreMessageReceived;
import kubemq.Kubemq;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EventStoreMessageReceived decoding.
 */
class EventStoreMessageReceivedTest {

    @Nested
    class DecodeTests {

        @Test
        void decode_setsId() {
            Kubemq.EventReceive proto = Kubemq.EventReceive.newBuilder()
                    .setEventID("store-event-123")
                    .setChannel("test-channel")
                    .build();

            EventStoreMessageReceived received = EventStoreMessageReceived.decode(proto);

            assertEquals("store-event-123", received.getId());
        }

        @Test
        void decode_setsChannel() {
            Kubemq.EventReceive proto = Kubemq.EventReceive.newBuilder()
                    .setEventID("event-123")
                    .setChannel("store-channel")
                    .build();

            EventStoreMessageReceived received = EventStoreMessageReceived.decode(proto);

            assertEquals("store-channel", received.getChannel());
        }

        @Test
        void decode_setsMetadata() {
            Kubemq.EventReceive proto = Kubemq.EventReceive.newBuilder()
                    .setEventID("event-123")
                    .setChannel("test-channel")
                    .setMetadata("store-metadata")
                    .build();

            EventStoreMessageReceived received = EventStoreMessageReceived.decode(proto);

            assertEquals("store-metadata", received.getMetadata());
        }

        @Test
        void decode_setsBody() {
            byte[] body = "stored event content".getBytes();
            Kubemq.EventReceive proto = Kubemq.EventReceive.newBuilder()
                    .setEventID("event-123")
                    .setChannel("test-channel")
                    .setBody(ByteString.copyFrom(body))
                    .build();

            EventStoreMessageReceived received = EventStoreMessageReceived.decode(proto);

            assertArrayEquals(body, received.getBody());
        }

        @Test
        void decode_setsSequence() {
            Kubemq.EventReceive proto = Kubemq.EventReceive.newBuilder()
                    .setEventID("event-123")
                    .setChannel("test-channel")
                    .setSequence(42)
                    .build();

            EventStoreMessageReceived received = EventStoreMessageReceived.decode(proto);

            assertEquals(42, received.getSequence());
        }

        @Test
        void decode_setsTimestamp() {
            long timestampNanos = 1700000000000000000L; // nanoseconds
            Kubemq.EventReceive proto = Kubemq.EventReceive.newBuilder()
                    .setEventID("event-123")
                    .setChannel("test-channel")
                    .setTimestamp(timestampNanos)
                    .build();

            EventStoreMessageReceived received = EventStoreMessageReceived.decode(proto);

            assertEquals(1700000000L, received.getTimestamp()); // converted to seconds
        }

        @Test
        void decode_setsTags() {
            Kubemq.EventReceive proto = Kubemq.EventReceive.newBuilder()
                    .setEventID("event-123")
                    .setChannel("test-channel")
                    .putTags("tag1", "value1")
                    .putTags("tag2", "value2")
                    .build();

            EventStoreMessageReceived received = EventStoreMessageReceived.decode(proto);

            assertEquals("value1", received.getTags().get("tag1"));
            assertEquals("value2", received.getTags().get("tag2"));
        }

        @Test
        void decode_setsFromClientId_fromTag() {
            Kubemq.EventReceive proto = Kubemq.EventReceive.newBuilder()
                    .setEventID("event-123")
                    .setChannel("test-channel")
                    .putTags("x-kubemq-client-id", "sender-client")
                    .build();

            EventStoreMessageReceived received = EventStoreMessageReceived.decode(proto);

            assertEquals("sender-client", received.getFromClientId());
        }

        @Test
        void decode_setsEmptyFromClientId_whenTagMissing() {
            Kubemq.EventReceive proto = Kubemq.EventReceive.newBuilder()
                    .setEventID("event-123")
                    .setChannel("test-channel")
                    .build();

            EventStoreMessageReceived received = EventStoreMessageReceived.decode(proto);

            assertEquals("", received.getFromClientId());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toString_includesSequence() {
            Kubemq.EventReceive proto = Kubemq.EventReceive.newBuilder()
                    .setEventID("event-123")
                    .setChannel("my-channel")
                    .setSequence(100)
                    .build();

            EventStoreMessageReceived received = EventStoreMessageReceived.decode(proto);
            String str = received.toString();

            assertTrue(str.contains("100"));
            assertTrue(str.contains("my-channel"));
        }
    }
}
