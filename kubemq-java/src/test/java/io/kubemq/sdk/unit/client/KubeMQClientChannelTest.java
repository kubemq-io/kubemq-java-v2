package io.kubemq.sdk.unit.client;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.cq.CQClient;
import kubemq.Kubemq;
import kubemq.kubemqGrpc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for KubeMQClient channel management, ping, and close operations.
 */
@ExtendWith(MockitoExtension.class)
class KubeMQClientChannelTest {

    @Mock
    private kubemqGrpc.kubemqBlockingStub mockBlockingStub;

    @Mock
    private kubemqGrpc.kubemqStub mockAsyncStub;

    @Mock
    private ManagedChannel mockManagedChannel;

    private CQClient client;

    @BeforeEach
    void setup() {
        // Create a real client first
        client = CQClient.builder()
                .address("localhost:50000")
                .clientId("test-client")
                .build();
    }

    @AfterEach
    void teardown() {
        if (client != null) {
            client.close();
        }
    }

    @Nested
    @DisplayName("Ping Error Tests")
    class PingErrorTests {

        @Test
        @DisplayName("C-08: Ping with server unavailable throws RuntimeException")
        void ping_serverUnavailable_throwsRuntimeException() {
            // Create client with mocked stub
            CQClient testClient = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("ping-error-test")
                    .build();

            // Replace the blocking stub with mock
            when(mockBlockingStub.ping(any())).thenThrow(
                    new StatusRuntimeException(Status.UNAVAILABLE.withDescription("Server unavailable"))
            );
            testClient.setBlockingStub(mockBlockingStub);

            // Should throw RuntimeException wrapping StatusRuntimeException
            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                testClient.ping();
            });

            assertTrue(exception.getCause() instanceof StatusRuntimeException);
            testClient.close();
        }

        @Test
        @DisplayName("C-08b: Ping with deadline exceeded throws RuntimeException")
        void ping_deadlineExceeded_throwsRuntimeException() {
            CQClient testClient = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("ping-timeout-test")
                    .build();

            when(mockBlockingStub.ping(any())).thenThrow(
                    new StatusRuntimeException(Status.DEADLINE_EXCEEDED.withDescription("Deadline exceeded"))
            );
            testClient.setBlockingStub(mockBlockingStub);

            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                testClient.ping();
            });

            assertTrue(exception.getCause() instanceof StatusRuntimeException);
            testClient.close();
        }

        @Test
        @DisplayName("C-08c: Ping with permission denied throws RuntimeException")
        void ping_permissionDenied_throwsRuntimeException() {
            CQClient testClient = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("ping-auth-test")
                    .build();

            when(mockBlockingStub.ping(any())).thenThrow(
                    new StatusRuntimeException(Status.PERMISSION_DENIED.withDescription("Permission denied"))
            );
            testClient.setBlockingStub(mockBlockingStub);

            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                testClient.ping();
            });

            assertTrue(exception.getCause() instanceof StatusRuntimeException);
            testClient.close();
        }

        @Test
        @DisplayName("Ping success returns ServerInfo")
        void ping_success_returnsServerInfo() {
            CQClient testClient = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("ping-success-test")
                    .build();

            Kubemq.PingResult mockPingResult = Kubemq.PingResult.newBuilder()
                    .setHost("test-host")
                    .setVersion("2.0.0")
                    .setServerStartTime(1234567890L)
                    .setServerUpTimeSeconds(3600L)
                    .build();

            when(mockBlockingStub.ping(any())).thenReturn(mockPingResult);
            testClient.setBlockingStub(mockBlockingStub);

            io.kubemq.sdk.common.ServerInfo serverInfo = testClient.ping();

            assertNotNull(serverInfo);
            assertEquals("test-host", serverInfo.getHost());
            assertEquals("2.0.0", serverInfo.getVersion());
            assertEquals(1234567890L, serverInfo.getServerStartTime());
            assertEquals(3600L, serverInfo.getServerUpTimeSeconds());
            testClient.close();
        }
    }

    @Nested
    @DisplayName("Close Tests")
    class CloseTests {

        @Test
        @DisplayName("C-07: Close handles normal shutdown")
        void close_normalShutdown_completesSuccessfully() {
            CQClient testClient = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("close-test")
                    .build();

            // Should not throw
            assertDoesNotThrow(() -> testClient.close());
        }

        @Test
        @DisplayName("Close with null channel does not throw")
        void close_nullChannel_doesNotThrow() {
            CQClient testClient = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("close-null-test")
                    .build();

            testClient.setManagedChannel(null);

            assertDoesNotThrow(() -> testClient.close());
        }

        @Test
        @DisplayName("Multiple close calls do not throw")
        void close_multipleCalls_doesNotThrow() {
            CQClient testClient = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("close-multiple-test")
                    .build();

            assertDoesNotThrow(() -> {
                testClient.close();
                testClient.close();
                testClient.close();
            });
        }
    }

    @Nested
    @DisplayName("Getter Tests")
    class GetterTests {

        @Test
        @DisplayName("getClient returns blocking stub")
        void getClient_returnsBlockingStub() {
            assertNotNull(client.getClient());
        }

        @Test
        @DisplayName("getAsyncClient returns async stub")
        void getAsyncClient_returnsAsyncStub() {
            assertNotNull(client.getAsyncClient());
        }

        @Test
        @DisplayName("getManagedChannel returns channel")
        void getManagedChannel_returnsChannel() {
            assertNotNull(client.getManagedChannel());
        }
    }

    @Nested
    @DisplayName("Metadata Interceptor Tests")
    class MetadataInterceptorTests {

        @Test
        @DisplayName("C-09: Client with auth token has metadata")
        void builder_withAuthToken_hasMetadata() {
            CQClient testClient = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("auth-test")
                    .authToken("test-token-123")
                    .build();

            assertNotNull(testClient.getMetadata());
            assertEquals("test-token-123", testClient.getAuthToken());
            testClient.close();
        }

        @Test
        @DisplayName("Client without auth token has null metadata")
        void builder_withoutAuthToken_hasNullMetadata() {
            CQClient testClient = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("no-auth-test")
                    .build();

            assertNull(testClient.getMetadata());
            testClient.close();
        }

        @Test
        @DisplayName("Client with empty auth token has null metadata")
        void builder_withEmptyAuthToken_hasNullMetadata() {
            CQClient testClient = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("empty-auth-test")
                    .authToken("")
                    .build();

            assertNull(testClient.getMetadata());
            testClient.close();
        }
    }

    @Nested
    @DisplayName("Reconnection Interval Tests")
    class ReconnectionIntervalTests {

        @Test
        @DisplayName("Reconnect interval is converted to milliseconds")
        void reconnectInterval_convertedToMillis() {
            CQClient testClient = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("reconnect-test")
                    .reconnectIntervalSeconds(5)
                    .build();

            assertEquals(5, testClient.getReconnectIntervalSeconds());
            assertEquals(5000L, testClient.getReconnectIntervalInMillis());
            testClient.close();
        }
    }
}
