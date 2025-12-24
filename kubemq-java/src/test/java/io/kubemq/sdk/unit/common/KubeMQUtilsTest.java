package io.kubemq.sdk.unit.common;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.KubeMQUtils;
import io.kubemq.sdk.cq.CQChannel;
import io.kubemq.sdk.exception.CreateChannelException;
import io.kubemq.sdk.exception.DeleteChannelException;
import io.kubemq.sdk.exception.GRPCException;
import io.kubemq.sdk.exception.ListChannelsException;
import io.kubemq.sdk.pubsub.PubSubChannel;
import io.kubemq.sdk.queues.QueuesChannel;
import kubemq.Kubemq;
import kubemq.kubemqGrpc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for KubeMQUtils utility class.
 * Tests cover all channel operations including create, delete, and list.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KubeMQUtilsTest {

    @Mock
    private KubeMQClient mockClient;

    @Mock
    private kubemqGrpc.kubemqBlockingStub mockBlockingStub;

    @BeforeEach
    void setup() {
        when(mockClient.getClient()).thenReturn(mockBlockingStub);
        when(mockClient.getClientId()).thenReturn("test-client");
    }

    @Nested
    @DisplayName("Create Channel Tests")
    class CreateChannelTests {

        @Test
        @DisplayName("Create channel success returns true")
        void createChannel_success_returnsTrue() {
            Kubemq.Response successResponse = Kubemq.Response.newBuilder()
                    .setExecuted(true)
                    .build();
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(successResponse);

            Boolean result = KubeMQUtils.createChannelRequest(mockClient, "test-client", "test-channel", "queues");

            assertTrue(result);
        }

        @Test
        @DisplayName("U-01: Create channel not executed throws CreateChannelException")
        void createChannel_notExecuted_throwsCreateChannelException() {
            Kubemq.Response failResponse = Kubemq.Response.newBuilder()
                    .setExecuted(false)
                    .setError("Channel creation failed")
                    .build();
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(failResponse);

            CreateChannelException exception = assertThrows(CreateChannelException.class, () -> {
                KubeMQUtils.createChannelRequest(mockClient, "test-client", "test-channel", "queues");
            });

            assertEquals("Channel creation failed", exception.getMessage());
        }

        @Test
        @DisplayName("U-02: Create channel gRPC error throws GRPCException")
        void createChannel_grpcError_throwsGRPCException() {
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

            assertThrows(GRPCException.class, () -> {
                KubeMQUtils.createChannelRequest(mockClient, "test-client", "test-channel", "queues");
            });
        }

        @Test
        @DisplayName("U-03: Create channel null response returns null")
        void createChannel_nullResponse_returnsNull() {
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(null);

            Boolean result = KubeMQUtils.createChannelRequest(mockClient, "test-client", "test-channel", "queues");

            assertNull(result);
        }

        @Test
        @DisplayName("Create channel for different types")
        void createChannel_differentTypes_works() {
            Kubemq.Response successResponse = Kubemq.Response.newBuilder()
                    .setExecuted(true)
                    .build();
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(successResponse);

            // Test different channel types
            assertTrue(KubeMQUtils.createChannelRequest(mockClient, "test-client", "test-channel", "events"));
            assertTrue(KubeMQUtils.createChannelRequest(mockClient, "test-client", "test-channel", "events_store"));
            assertTrue(KubeMQUtils.createChannelRequest(mockClient, "test-client", "test-channel", "commands"));
            assertTrue(KubeMQUtils.createChannelRequest(mockClient, "test-client", "test-channel", "queries"));
        }
    }

    @Nested
    @DisplayName("Delete Channel Tests")
    class DeleteChannelTests {

        @Test
        @DisplayName("Delete channel success returns true")
        void deleteChannel_success_returnsTrue() {
            Kubemq.Response successResponse = Kubemq.Response.newBuilder()
                    .setExecuted(true)
                    .build();
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(successResponse);

            Boolean result = KubeMQUtils.deleteChannelRequest(mockClient, "test-client", "test-channel", "queues");

            assertTrue(result);
        }

        @Test
        @DisplayName("U-04: Delete channel not executed throws DeleteChannelException")
        void deleteChannel_notExecuted_throwsDeleteChannelException() {
            Kubemq.Response failResponse = Kubemq.Response.newBuilder()
                    .setExecuted(false)
                    .setError("Channel deletion failed")
                    .build();
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(failResponse);

            DeleteChannelException exception = assertThrows(DeleteChannelException.class, () -> {
                KubeMQUtils.deleteChannelRequest(mockClient, "test-client", "test-channel", "queues");
            });

            assertEquals("Channel deletion failed", exception.getMessage());
        }

        @Test
        @DisplayName("U-05: Delete channel gRPC error throws GRPCException")
        void deleteChannel_grpcError_throwsGRPCException() {
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

            assertThrows(GRPCException.class, () -> {
                KubeMQUtils.deleteChannelRequest(mockClient, "test-client", "test-channel", "queues");
            });
        }

        @Test
        @DisplayName("U-06: Delete channel null response returns null")
        void deleteChannel_nullResponse_returnsNull() {
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(null);

            Boolean result = KubeMQUtils.deleteChannelRequest(mockClient, "test-client", "test-channel", "queues");

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("List Queues Channels Tests")
    class ListQueuesChannelsTests {

        @Test
        @DisplayName("List queues channels success returns channel list")
        void listQueuesChannels_success_returnsChannelList() {
            // Create a valid JSON response for queues channels
            String jsonResponse = "[{\"name\":\"queue1\",\"type\":\"queues\",\"lastActivity\":1234567890,\"isActive\":true,\"incoming\":{\"messages\":100,\"volume\":1024,\"waiting\":10},\"outgoing\":{\"messages\":90,\"volume\":900,\"waiting\":0}}]";

            Kubemq.Response successResponse = Kubemq.Response.newBuilder()
                    .setExecuted(true)
                    .setBody(ByteString.copyFromUtf8(jsonResponse))
                    .build();
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(successResponse);

            List<QueuesChannel> result = KubeMQUtils.listQueuesChannels(mockClient, "test-client", "");

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("queue1", result.get(0).getName());
        }

        @Test
        @DisplayName("U-07: List queues not executed throws ListChannelsException")
        void listQueuesChannels_notExecuted_throwsListChannelsException() {
            Kubemq.Response failResponse = Kubemq.Response.newBuilder()
                    .setExecuted(false)
                    .setError("Failed to list channels")
                    .build();
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(failResponse);

            ListChannelsException exception = assertThrows(ListChannelsException.class, () -> {
                KubeMQUtils.listQueuesChannels(mockClient, "test-client", "");
            });

            assertEquals("Failed to list channels", exception.getMessage());
        }

        @Test
        @DisplayName("U-08: List queues gRPC error throws GRPCException")
        void listQueuesChannels_grpcError_throwsGRPCException() {
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

            assertThrows(GRPCException.class, () -> {
                KubeMQUtils.listQueuesChannels(mockClient, "test-client", "");
            });
        }

        @Test
        @DisplayName("U-09: List queues IO error throws RuntimeException")
        void listQueuesChannels_invalidJson_throwsRuntimeException() {
            // Return invalid JSON to trigger IOException
            Kubemq.Response successResponse = Kubemq.Response.newBuilder()
                    .setExecuted(true)
                    .setBody(ByteString.copyFromUtf8("invalid json {{{"))
                    .build();
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(successResponse);

            assertThrows(RuntimeException.class, () -> {
                KubeMQUtils.listQueuesChannels(mockClient, "test-client", "");
            });
        }

        @Test
        @DisplayName("U-10: List queues null response returns null")
        void listQueuesChannels_nullResponse_returnsNull() {
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(null);

            List<QueuesChannel> result = KubeMQUtils.listQueuesChannels(mockClient, "test-client", "");

            assertNull(result);
        }

        @Test
        @DisplayName("List queues with search filter")
        void listQueuesChannels_withSearch_passesFilter() {
            String jsonResponse = "[]";
            Kubemq.Response successResponse = Kubemq.Response.newBuilder()
                    .setExecuted(true)
                    .setBody(ByteString.copyFromUtf8(jsonResponse))
                    .build();
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(successResponse);

            List<QueuesChannel> result = KubeMQUtils.listQueuesChannels(mockClient, "test-client", "test-filter");

            assertNotNull(result);
            assertEquals(0, result.size());
        }
    }

    @Nested
    @DisplayName("List PubSub Channels Tests")
    class ListPubSubChannelsTests {

        @Test
        @DisplayName("List PubSub channels success returns channel list")
        void listPubSubChannels_success_returnsChannelList() {
            String jsonResponse = "[{\"name\":\"events1\",\"type\":\"events\",\"lastActivity\":1234567890,\"isActive\":true,\"incoming\":{\"messages\":100,\"volume\":1024,\"waiting\":0},\"outgoing\":{\"messages\":100,\"volume\":1024,\"waiting\":0}}]";

            Kubemq.Response successResponse = Kubemq.Response.newBuilder()
                    .setExecuted(true)
                    .setBody(ByteString.copyFromUtf8(jsonResponse))
                    .build();
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(successResponse);

            List<PubSubChannel> result = KubeMQUtils.listPubSubChannels(mockClient, "test-client", "events", "");

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("events1", result.get(0).getName());
        }

        @Test
        @DisplayName("U-11: List PubSub not executed throws ListChannelsException")
        void listPubSubChannels_notExecuted_throwsListChannelsException() {
            Kubemq.Response failResponse = Kubemq.Response.newBuilder()
                    .setExecuted(false)
                    .setError("Failed to list PubSub channels")
                    .build();
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(failResponse);

            ListChannelsException exception = assertThrows(ListChannelsException.class, () -> {
                KubeMQUtils.listPubSubChannels(mockClient, "test-client", "events", "");
            });

            assertEquals("Failed to list PubSub channels", exception.getMessage());
        }

        @Test
        @DisplayName("U-12: List PubSub gRPC error throws GRPCException")
        void listPubSubChannels_grpcError_throwsGRPCException() {
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

            assertThrows(GRPCException.class, () -> {
                KubeMQUtils.listPubSubChannels(mockClient, "test-client", "events", "");
            });
        }

        @Test
        @DisplayName("U-13: List PubSub IO error throws RuntimeException")
        void listPubSubChannels_invalidJson_throwsRuntimeException() {
            Kubemq.Response successResponse = Kubemq.Response.newBuilder()
                    .setExecuted(true)
                    .setBody(ByteString.copyFromUtf8("invalid json"))
                    .build();
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(successResponse);

            assertThrows(RuntimeException.class, () -> {
                KubeMQUtils.listPubSubChannels(mockClient, "test-client", "events", "");
            });
        }

        @Test
        @DisplayName("List PubSub with null search uses empty string")
        void listPubSubChannels_nullSearch_usesEmptyString() {
            String jsonResponse = "[]";
            Kubemq.Response successResponse = Kubemq.Response.newBuilder()
                    .setExecuted(true)
                    .setBody(ByteString.copyFromUtf8(jsonResponse))
                    .build();
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(successResponse);

            List<PubSubChannel> result = KubeMQUtils.listPubSubChannels(mockClient, "test-client", "events", null);

            assertNotNull(result);
        }

        @Test
        @DisplayName("List PubSub null response returns null")
        void listPubSubChannels_nullResponse_returnsNull() {
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(null);

            List<PubSubChannel> result = KubeMQUtils.listPubSubChannels(mockClient, "test-client", "events", "");

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("List CQ Channels Tests")
    class ListCQChannelsTests {

        @Test
        @DisplayName("List CQ channels success returns channel list")
        void listCQChannels_success_returnsChannelList() {
            String jsonResponse = "[{\"name\":\"commands1\",\"type\":\"commands\",\"lastActivity\":1234567890,\"isActive\":true,\"incoming\":{\"messages\":100,\"volume\":1024,\"waiting\":0},\"outgoing\":{\"messages\":100,\"volume\":1024,\"waiting\":0}}]";

            Kubemq.Response successResponse = Kubemq.Response.newBuilder()
                    .setExecuted(true)
                    .setBody(ByteString.copyFromUtf8(jsonResponse))
                    .build();
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(successResponse);

            List<CQChannel> result = KubeMQUtils.listCQChannels(mockClient, "test-client", "commands", "");

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("commands1", result.get(0).getName());
        }

        @Test
        @DisplayName("U-14: List CQ not executed throws ListChannelsException")
        void listCQChannels_notExecuted_throwsListChannelsException() {
            Kubemq.Response failResponse = Kubemq.Response.newBuilder()
                    .setExecuted(false)
                    .setError("Failed to list CQ channels")
                    .build();
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(failResponse);

            ListChannelsException exception = assertThrows(ListChannelsException.class, () -> {
                KubeMQUtils.listCQChannels(mockClient, "test-client", "commands", "");
            });

            assertEquals("Failed to list CQ channels", exception.getMessage());
        }

        @Test
        @DisplayName("U-15: List CQ gRPC error throws GRPCException")
        void listCQChannels_grpcError_throwsGRPCException() {
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

            assertThrows(GRPCException.class, () -> {
                KubeMQUtils.listCQChannels(mockClient, "test-client", "commands", "");
            });
        }

        @Test
        @DisplayName("U-16: List CQ IO error throws RuntimeException")
        void listCQChannels_invalidJson_throwsRuntimeException() {
            Kubemq.Response successResponse = Kubemq.Response.newBuilder()
                    .setExecuted(true)
                    .setBody(ByteString.copyFromUtf8("invalid json"))
                    .build();
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(successResponse);

            assertThrows(RuntimeException.class, () -> {
                KubeMQUtils.listCQChannels(mockClient, "test-client", "commands", "");
            });
        }

        @Test
        @DisplayName("List CQ with null search uses empty string")
        void listCQChannels_nullSearch_usesEmptyString() {
            String jsonResponse = "[]";
            Kubemq.Response successResponse = Kubemq.Response.newBuilder()
                    .setExecuted(true)
                    .setBody(ByteString.copyFromUtf8(jsonResponse))
                    .build();
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(successResponse);

            List<CQChannel> result = KubeMQUtils.listCQChannels(mockClient, "test-client", "commands", null);

            assertNotNull(result);
        }

        @Test
        @DisplayName("List CQ null response returns null")
        void listCQChannels_nullResponse_returnsNull() {
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(null);

            List<CQChannel> result = KubeMQUtils.listCQChannels(mockClient, "test-client", "commands", "");

            assertNull(result);
        }

        @Test
        @DisplayName("List queries channels")
        void listQueriesChannels_success_returnsChannelList() {
            String jsonResponse = "[{\"name\":\"queries1\",\"type\":\"queries\",\"lastActivity\":1234567890,\"isActive\":true,\"incoming\":{\"messages\":50,\"volume\":512,\"waiting\":0},\"outgoing\":{\"messages\":50,\"volume\":512,\"waiting\":0}}]";

            Kubemq.Response successResponse = Kubemq.Response.newBuilder()
                    .setExecuted(true)
                    .setBody(ByteString.copyFromUtf8(jsonResponse))
                    .build();
            when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(successResponse);

            List<CQChannel> result = KubeMQUtils.listCQChannels(mockClient, "test-client", "queries", "");

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("queries1", result.get(0).getName());
        }
    }
}
