package io.kubemq.sdk.unit.cq;

import io.kubemq.sdk.cq.CommandMessageReceived;
import io.kubemq.sdk.cq.CommandResponseMessage;
import kubemq.Kubemq;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CommandResponseMessage encode, decode, and validation.
 */
class CommandResponseMessageTest {

    private CommandMessageReceived createMockCommandReceived(String id, String replyChannel) {
        return CommandMessageReceived.builder()
                .id(id)
                .fromClientId("sender-client")
                .timestamp(Instant.now())
                .channel("commands-channel")
                .metadata("test-metadata")
                .body("test-body".getBytes())
                .replyChannel(replyChannel)
                .build();
    }

    @Nested
    class ValidationTests {

        @Test
        void validate_withValidResponse_passes() {
            CommandResponseMessage response = CommandResponseMessage.builder()
                    .commandReceived(createMockCommandReceived("cmd-123", "reply-channel"))
                    .isExecuted(true)
                    .build();

            assertDoesNotThrow(response::validate);
        }

        @Test
        void validate_withoutCommandReceived_throws() {
            CommandResponseMessage response = CommandResponseMessage.builder()
                    .isExecuted(true)
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    response::validate
            );
            assertTrue(ex.getMessage().contains("command request"));
        }

        @Test
        void validate_withNullReplyChannel_throws() {
            CommandResponseMessage response = CommandResponseMessage.builder()
                    .commandReceived(createMockCommandReceived("cmd-123", null))
                    .isExecuted(true)
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    response::validate
            );
            assertTrue(ex.getMessage().contains("reply channel"));
        }

        @Test
        void validate_withEmptyReplyChannel_throws() {
            CommandResponseMessage response = CommandResponseMessage.builder()
                    .commandReceived(createMockCommandReceived("cmd-123", ""))
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
            CommandResponseMessage response = CommandResponseMessage.builder()
                    .commandReceived(createMockCommandReceived("cmd-123", "reply-channel"))
                    .isExecuted(true)
                    .build();

            CommandResponseMessage validated = response.validate();

            assertSame(response, validated);
        }
    }

    @Nested
    class EncodeTests {

        @Test
        void encode_setsClientId() {
            CommandResponseMessage response = CommandResponseMessage.builder()
                    .commandReceived(createMockCommandReceived("cmd-123", "reply-channel"))
                    .isExecuted(true)
                    .timestamp(LocalDateTime.now())
                    .build();

            Kubemq.Response proto = response.encode("my-client");

            assertEquals("my-client", proto.getClientID());
        }

        @Test
        void encode_setsRequestId() {
            CommandResponseMessage response = CommandResponseMessage.builder()
                    .commandReceived(createMockCommandReceived("cmd-456", "reply-channel"))
                    .isExecuted(true)
                    .timestamp(LocalDateTime.now())
                    .build();

            Kubemq.Response proto = response.encode("client");

            assertEquals("cmd-456", proto.getRequestID());
        }

        @Test
        void encode_setsReplyChannel() {
            CommandResponseMessage response = CommandResponseMessage.builder()
                    .commandReceived(createMockCommandReceived("cmd-123", "my-reply-channel"))
                    .isExecuted(true)
                    .timestamp(LocalDateTime.now())
                    .build();

            Kubemq.Response proto = response.encode("client");

            assertEquals("my-reply-channel", proto.getReplyChannel());
        }

        @Test
        void encode_setsExecuted() {
            CommandResponseMessage response = CommandResponseMessage.builder()
                    .commandReceived(createMockCommandReceived("cmd-123", "reply-channel"))
                    .isExecuted(true)
                    .timestamp(LocalDateTime.now())
                    .build();

            Kubemq.Response proto = response.encode("client");

            assertTrue(proto.getExecuted());
        }

        @Test
        void encode_setsExecutedFalse() {
            CommandResponseMessage response = CommandResponseMessage.builder()
                    .commandReceived(createMockCommandReceived("cmd-123", "reply-channel"))
                    .isExecuted(false)
                    .error("Command failed")
                    .timestamp(LocalDateTime.now())
                    .build();

            Kubemq.Response proto = response.encode("client");

            assertFalse(proto.getExecuted());
        }

        @Test
        void encode_setsError() {
            CommandResponseMessage response = CommandResponseMessage.builder()
                    .commandReceived(createMockCommandReceived("cmd-123", "reply-channel"))
                    .isExecuted(false)
                    .error("Execution error occurred")
                    .timestamp(LocalDateTime.now())
                    .build();

            Kubemq.Response proto = response.encode("client");

            assertEquals("Execution error occurred", proto.getError());
        }

        @Test
        void encode_withNullError_usesEmptyString() {
            CommandResponseMessage response = CommandResponseMessage.builder()
                    .commandReceived(createMockCommandReceived("cmd-123", "reply-channel"))
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
            CommandResponseMessage response = CommandResponseMessage.builder()
                    .commandReceived(createMockCommandReceived("cmd-123", "reply-channel"))
                    .isExecuted(true)
                    .timestamp(timestamp)
                    .build();

            Kubemq.Response proto = response.encode("client");

            assertTrue(proto.getTimestamp() > 0);
        }

        @Test
        void encode_withNullTimestamp_usesCurrentTime() {
            CommandResponseMessage response = CommandResponseMessage.builder()
                    .commandReceived(createMockCommandReceived("cmd-123", "reply-channel"))
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
                    .setTimestamp(System.currentTimeMillis() * 1_000_000L)
                    .build();

            CommandResponseMessage response = new CommandResponseMessage().decode(proto);

            assertEquals("decoded-client", response.getClientId());
        }

        @Test
        void decode_setsRequestId() {
            Kubemq.Response proto = Kubemq.Response.newBuilder()
                    .setClientID("client")
                    .setRequestID("req-456")
                    .setExecuted(true)
                    .setError("")
                    .setTimestamp(System.currentTimeMillis() * 1_000_000L)
                    .build();

            CommandResponseMessage response = new CommandResponseMessage().decode(proto);

            assertEquals("req-456", response.getRequestId());
        }

        @Test
        void decode_setsExecuted() {
            Kubemq.Response proto = Kubemq.Response.newBuilder()
                    .setClientID("client")
                    .setRequestID("req-123")
                    .setExecuted(true)
                    .setError("")
                    .setTimestamp(System.currentTimeMillis() * 1_000_000L)
                    .build();

            CommandResponseMessage response = new CommandResponseMessage().decode(proto);

            assertTrue(response.isExecuted());
        }

        @Test
        void decode_setsError() {
            Kubemq.Response proto = Kubemq.Response.newBuilder()
                    .setClientID("client")
                    .setRequestID("req-123")
                    .setExecuted(false)
                    .setError("Something failed")
                    .setTimestamp(System.currentTimeMillis() * 1_000_000L)
                    .build();

            CommandResponseMessage response = new CommandResponseMessage().decode(proto);

            assertEquals("Something failed", response.getError());
        }

        @Test
        void decode_setsTimestamp() {
            long timestampNanos = LocalDateTime.of(2023, 12, 25, 10, 0, 0)
                    .toEpochSecond(ZoneOffset.UTC) * 1_000_000_000L;

            Kubemq.Response proto = Kubemq.Response.newBuilder()
                    .setClientID("client")
                    .setRequestID("req-123")
                    .setExecuted(true)
                    .setError("")
                    .setTimestamp(timestampNanos)
                    .build();

            CommandResponseMessage response = new CommandResponseMessage().decode(proto);

            assertNotNull(response.getTimestamp());
        }

        @Test
        void decode_returnsThis() {
            Kubemq.Response proto = Kubemq.Response.newBuilder()
                    .setClientID("client")
                    .setRequestID("req-123")
                    .setExecuted(true)
                    .setError("")
                    .setTimestamp(System.currentTimeMillis() * 1_000_000L)
                    .build();

            CommandResponseMessage response = new CommandResponseMessage();
            CommandResponseMessage decoded = response.decode(proto);

            assertSame(response, decoded);
        }
    }

    @Nested
    class RoundTripTests {

        @Test
        void encodeAndDecode_preservesFields() {
            LocalDateTime timestamp = LocalDateTime.now();
            CommandResponseMessage original = CommandResponseMessage.builder()
                    .commandReceived(createMockCommandReceived("cmd-roundtrip", "reply-channel"))
                    .isExecuted(true)
                    .error("")
                    .timestamp(timestamp)
                    .build();

            Kubemq.Response proto = original.encode("test-client");
            CommandResponseMessage decoded = new CommandResponseMessage().decode(proto);

            assertEquals("test-client", decoded.getClientId());
            assertEquals("cmd-roundtrip", decoded.getRequestId());
            assertTrue(decoded.isExecuted());
            assertEquals("", decoded.getError());
            assertNotNull(decoded.getTimestamp());
        }

        @Test
        void encodeAndDecode_withError_preservesError() {
            LocalDateTime timestamp = LocalDateTime.now();
            CommandResponseMessage original = CommandResponseMessage.builder()
                    .commandReceived(createMockCommandReceived("cmd-error", "reply-channel"))
                    .isExecuted(false)
                    .error("Execution failed")
                    .timestamp(timestamp)
                    .build();

            Kubemq.Response proto = original.encode("test-client");
            CommandResponseMessage decoded = new CommandResponseMessage().decode(proto);

            assertFalse(decoded.isExecuted());
            assertEquals("Execution failed", decoded.getError());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toString_includesAllFields() {
            CommandResponseMessage response = CommandResponseMessage.builder()
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
            CommandResponseMessage response = CommandResponseMessage.builder()
                    .clientId("client")
                    .requestId("req")
                    .isExecuted(false)
                    .error("failed to execute")
                    .build();

            String str = response.toString();

            assertTrue(str.contains("failed to execute"));
        }
    }

    @Nested
    class GetterSetterTests {

        @Test
        void setCommandReceived_updatesCommandReceived() {
            CommandResponseMessage response = new CommandResponseMessage();
            CommandMessageReceived cmd = createMockCommandReceived("cmd-1", "reply");
            response.setCommandReceived(cmd);
            assertEquals(cmd, response.getCommandReceived());
        }

        @Test
        void setClientId_updatesClientId() {
            CommandResponseMessage response = new CommandResponseMessage();
            response.setClientId("new-client");
            assertEquals("new-client", response.getClientId());
        }

        @Test
        void setRequestId_updatesRequestId() {
            CommandResponseMessage response = new CommandResponseMessage();
            response.setRequestId("new-request");
            assertEquals("new-request", response.getRequestId());
        }

        @Test
        void setExecuted_updatesExecuted() {
            CommandResponseMessage response = new CommandResponseMessage();
            response.setExecuted(true);
            assertTrue(response.isExecuted());
        }

        @Test
        void setTimestamp_updatesTimestamp() {
            CommandResponseMessage response = new CommandResponseMessage();
            LocalDateTime timestamp = LocalDateTime.now();
            response.setTimestamp(timestamp);
            assertEquals(timestamp, response.getTimestamp());
        }

        @Test
        void setError_updatesError() {
            CommandResponseMessage response = new CommandResponseMessage();
            response.setError("new error");
            assertEquals("new error", response.getError());
        }
    }
}
