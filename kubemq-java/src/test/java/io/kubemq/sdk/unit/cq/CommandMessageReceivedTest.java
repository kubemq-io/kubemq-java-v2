package io.kubemq.sdk.unit.cq;

import com.google.protobuf.ByteString;
import io.kubemq.sdk.cq.CommandMessageReceived;
import kubemq.Kubemq;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CommandMessageReceived decoding.
 */
class CommandMessageReceivedTest {

    @Nested
    class DecodeTests {

        @Test
        void decode_setsId() {
            Kubemq.Request proto = Kubemq.Request.newBuilder()
                    .setRequestID("cmd-123")
                    .setChannel("test-channel")
                    .build();

            CommandMessageReceived received = CommandMessageReceived.decode(proto);

            assertEquals("cmd-123", received.getId());
        }

        @Test
        void decode_setsChannel() {
            Kubemq.Request proto = Kubemq.Request.newBuilder()
                    .setRequestID("cmd-123")
                    .setChannel("commands-channel")
                    .build();

            CommandMessageReceived received = CommandMessageReceived.decode(proto);

            assertEquals("commands-channel", received.getChannel());
        }

        @Test
        void decode_setsMetadata() {
            Kubemq.Request proto = Kubemq.Request.newBuilder()
                    .setRequestID("cmd-123")
                    .setChannel("test-channel")
                    .setMetadata("command-metadata")
                    .build();

            CommandMessageReceived received = CommandMessageReceived.decode(proto);

            assertEquals("command-metadata", received.getMetadata());
        }

        @Test
        void decode_setsBody() {
            byte[] body = "command payload".getBytes();
            Kubemq.Request proto = Kubemq.Request.newBuilder()
                    .setRequestID("cmd-123")
                    .setChannel("test-channel")
                    .setBody(ByteString.copyFrom(body))
                    .build();

            CommandMessageReceived received = CommandMessageReceived.decode(proto);

            assertArrayEquals(body, received.getBody());
        }

        @Test
        void decode_setsFromClientId() {
            Kubemq.Request proto = Kubemq.Request.newBuilder()
                    .setRequestID("cmd-123")
                    .setChannel("test-channel")
                    .setClientID("sender-client")
                    .build();

            CommandMessageReceived received = CommandMessageReceived.decode(proto);

            assertEquals("sender-client", received.getFromClientId());
        }

        @Test
        void decode_setsReplyChannel() {
            Kubemq.Request proto = Kubemq.Request.newBuilder()
                    .setRequestID("cmd-123")
                    .setChannel("test-channel")
                    .setReplyChannel("reply-channel-123")
                    .build();

            CommandMessageReceived received = CommandMessageReceived.decode(proto);

            assertEquals("reply-channel-123", received.getReplyChannel());
        }

        @Test
        void decode_setsTimestamp() {
            Kubemq.Request proto = Kubemq.Request.newBuilder()
                    .setRequestID("cmd-123")
                    .setChannel("test-channel")
                    .build();

            CommandMessageReceived received = CommandMessageReceived.decode(proto);

            assertNotNull(received.getTimestamp());
        }

        @Test
        void decode_handlesEmptyBody() {
            Kubemq.Request proto = Kubemq.Request.newBuilder()
                    .setRequestID("cmd-123")
                    .setChannel("test-channel")
                    .build();

            CommandMessageReceived received = CommandMessageReceived.decode(proto);

            assertNotNull(received.getBody());
            assertEquals(0, received.getBody().length);
        }

        @Test
        void decode_handlesEmptyMetadata() {
            Kubemq.Request proto = Kubemq.Request.newBuilder()
                    .setRequestID("cmd-123")
                    .setChannel("test-channel")
                    .build();

            CommandMessageReceived received = CommandMessageReceived.decode(proto);

            assertEquals("", received.getMetadata());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toString_includesAllFields() {
            Kubemq.Request proto = Kubemq.Request.newBuilder()
                    .setRequestID("cmd-123")
                    .setChannel("my-channel")
                    .setMetadata("my-metadata")
                    .setReplyChannel("reply-123")
                    .setClientID("client-1")
                    .build();

            CommandMessageReceived received = CommandMessageReceived.decode(proto);
            String str = received.toString();

            assertTrue(str.contains("cmd-123"));
            assertTrue(str.contains("my-channel"));
            assertTrue(str.contains("reply-123"));
        }
    }
}
