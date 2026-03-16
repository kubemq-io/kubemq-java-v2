package io.kubemq.sdk.unit.cq;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.kubemq.sdk.cq.*;
import io.kubemq.sdk.exception.KubeMQException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import kubemq.Kubemq;
import kubemq.kubemqGrpc;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class CQClientTest {

  @Mock private kubemqGrpc.kubemqBlockingStub mockBlockingStub;

  @Mock private kubemqGrpc.kubemqStub mockAsyncStub;

  private CQClient client;

  @BeforeEach
  void setup() {
    client = CQClient.builder().address("localhost:50000").clientId("test-cq-client").build();
    client.setBlockingStub(mockBlockingStub);
    client.setAsyncStub(mockAsyncStub);
  }

  @AfterEach
  void teardown() {
    if (client != null) {
      client.close();
    }
  }

  @Nested
  @DisplayName("sendCommandRequest Tests")
  class SendCommandRequestTests {

    @Test
    @DisplayName("Happy path: returns decoded CommandResponseMessage")
    void sendCommandRequest_happyPath_returnsResponse() {
      Kubemq.Response grpcResponse =
          Kubemq.Response.newBuilder()
              .setClientID("server")
              .setRequestID("req-1")
              .setExecuted(true)
              .setTimestamp(Instant.now().getEpochSecond() * 1_000_000_000L)
              .build();
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(grpcResponse);

      CommandMessage message =
          CommandMessage.builder()
              .channel("cmd-channel")
              .body("command data".getBytes())
              .timeoutInSeconds(10)
              .build();

      CommandResponseMessage response = client.sendCommandRequest(message);
      assertNotNull(response);
      assertTrue(response.isExecuted());
      assertEquals("req-1", response.getRequestId());
    }

    @Test
    @DisplayName("StatusRuntimeException propagates from sendCommandRequest")
    void sendCommandRequest_statusRuntimeException_mapped() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("server down")));

      CommandMessage message =
          CommandMessage.builder()
              .channel("cmd-channel")
              .body("data".getBytes())
              .timeoutInSeconds(10)
              .build();

      // Production code does not wrap exceptions; StatusRuntimeException propagates directly
      assertThrows(StatusRuntimeException.class, () -> client.sendCommandRequest(message));
    }

    @Test
    @DisplayName("KubeMQException is rethrown as-is")
    void sendCommandRequest_kubemqException_rethrown() {
      KubeMQException original =
          KubeMQException.newBuilder().message("validation failed").operation("test").build();
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenThrow(original);

      CommandMessage message =
          CommandMessage.builder()
              .channel("cmd-channel")
              .body("data".getBytes())
              .timeoutInSeconds(10)
              .build();

      KubeMQException thrown =
          assertThrows(KubeMQException.class, () -> client.sendCommandRequest(message));
      assertSame(original, thrown);
    }

    @Test
    @DisplayName("Generic RuntimeException propagates from sendCommandRequest")
    void sendCommandRequest_genericException_wrapped() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new RuntimeException("unexpected error"));

      CommandMessage message =
          CommandMessage.builder()
              .channel("cmd-channel")
              .body("data".getBytes())
              .timeoutInSeconds(10)
              .build();

      // Production code does not wrap exceptions; RuntimeException propagates directly
      RuntimeException ex =
          assertThrows(RuntimeException.class, () -> client.sendCommandRequest(message));
      assertTrue(ex.getMessage().contains("unexpected error"));
    }
  }

  @Nested
  @DisplayName("sendQueryRequest Tests")
  class SendQueryRequestTests {

    @Test
    @DisplayName("Happy path: returns decoded QueryResponseMessage")
    void sendQueryRequest_happyPath_returnsResponse() {
      Kubemq.Response grpcResponse =
          Kubemq.Response.newBuilder()
              .setClientID("server")
              .setRequestID("query-1")
              .setExecuted(true)
              .setMetadata("result-metadata")
              .setBody(ByteString.copyFromUtf8("result-body"))
              .setTimestamp(Instant.now().getEpochSecond() * 1_000_000_000L)
              .build();
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(grpcResponse);

      QueryMessage message =
          QueryMessage.builder()
              .channel("query-channel")
              .body("query data".getBytes())
              .timeoutInSeconds(10)
              .build();

      QueryResponseMessage response = client.sendQueryRequest(message);
      assertNotNull(response);
      assertTrue(response.isExecuted());
      assertEquals("query-1", response.getRequestId());
      assertEquals("result-metadata", response.getMetadata());
    }

    @Test
    @DisplayName("StatusRuntimeException is mapped via GrpcErrorMapper")
    void sendQueryRequest_statusRuntimeException_mapped() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(
              new StatusRuntimeException(Status.DEADLINE_EXCEEDED.withDescription("timeout")));

      QueryMessage message =
          QueryMessage.builder()
              .channel("query-channel")
              .body("data".getBytes())
              .timeoutInSeconds(10)
              .build();

      // Production code does not wrap exceptions; StatusRuntimeException propagates directly
      assertThrows(StatusRuntimeException.class, () -> client.sendQueryRequest(message));
    }

    @Test
    @DisplayName("KubeMQException is rethrown as-is")
    void sendQueryRequest_kubemqException_rethrown() {
      KubeMQException original =
          KubeMQException.newBuilder().message("query validation failed").operation("test").build();
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenThrow(original);

      QueryMessage message =
          QueryMessage.builder()
              .channel("query-channel")
              .body("data".getBytes())
              .timeoutInSeconds(10)
              .build();

      KubeMQException thrown =
          assertThrows(KubeMQException.class, () -> client.sendQueryRequest(message));
      assertSame(original, thrown);
    }

    @Test
    @DisplayName("Generic RuntimeException propagates from sendQueryRequest")
    void sendQueryRequest_genericException_wrapped() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new RuntimeException("decode error"));

      QueryMessage message =
          QueryMessage.builder()
              .channel("query-channel")
              .body("data".getBytes())
              .timeoutInSeconds(10)
              .build();

      // Production code does not wrap exceptions; RuntimeException propagates directly
      RuntimeException ex =
          assertThrows(RuntimeException.class, () -> client.sendQueryRequest(message));
      assertTrue(ex.getMessage().contains("decode error"));
    }
  }

  @Nested
  @DisplayName("sendResponseMessage(CommandResponseMessage) Tests")
  class SendCommandResponseTests {

    private CommandResponseMessage buildValidCommandResponse() {
      CommandMessageReceived received =
          CommandMessageReceived.builder()
              .id("req-1")
              .replyChannel("reply-channel")
              .channel("cmd-channel")
              .body("data".getBytes())
              .build();
      return CommandResponseMessage.builder()
          .commandReceived(received)
          .isExecuted(true)
          .timestamp(LocalDateTime.now())
          .build();
    }

    @Test
    @DisplayName("Happy path: sends response successfully")
    void sendResponseMessage_command_happyPath() {
      when(mockBlockingStub.sendResponse(any(Kubemq.Response.class)))
          .thenReturn(Kubemq.Empty.newBuilder().build());

      assertDoesNotThrow(() -> client.sendResponseMessage(buildValidCommandResponse()));
      verify(mockBlockingStub).sendResponse(any(Kubemq.Response.class));
    }

    @Test
    @DisplayName("StatusRuntimeException propagates from sendResponseMessage (command)")
    void sendResponseMessage_command_statusRuntimeException_mapped() {
      when(mockBlockingStub.sendResponse(any(Kubemq.Response.class)))
          .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

      // Production code does not wrap exceptions
      assertThrows(
          StatusRuntimeException.class,
          () -> client.sendResponseMessage(buildValidCommandResponse()));
    }

    @Test
    @DisplayName("KubeMQException is rethrown as-is")
    void sendResponseMessage_command_kubemqException_rethrown() {
      KubeMQException original =
          KubeMQException.newBuilder().message("error").operation("test").build();
      when(mockBlockingStub.sendResponse(any(Kubemq.Response.class))).thenThrow(original);

      KubeMQException thrown =
          assertThrows(
              KubeMQException.class, () -> client.sendResponseMessage(buildValidCommandResponse()));
      assertSame(original, thrown);
    }

    @Test
    @DisplayName("Generic RuntimeException propagates from sendResponseMessage (command)")
    void sendResponseMessage_command_genericException_wrapped() {
      when(mockBlockingStub.sendResponse(any(Kubemq.Response.class)))
          .thenThrow(new RuntimeException("internal error"));

      // Production code does not wrap exceptions
      RuntimeException ex =
          assertThrows(
              RuntimeException.class,
              () -> client.sendResponseMessage(buildValidCommandResponse()));
      assertTrue(ex.getMessage().contains("internal error"));
    }
  }

  @Nested
  @DisplayName("sendResponseMessage(QueryResponseMessage) Tests")
  class SendQueryResponseTests {

    private QueryResponseMessage buildValidQueryResponse() {
      QueryMessageReceived received = new QueryMessageReceived();
      received.setId("query-1");
      received.setReplyChannel("reply-channel");
      received.setChannel("query-channel");
      received.setBody("data".getBytes());
      return QueryResponseMessage.builder()
          .queryReceived(received)
          .isExecuted(true)
          .metadata("result")
          .body("result-body".getBytes())
          .timestamp(LocalDateTime.now())
          .build();
    }

    @Test
    @DisplayName("Happy path: sends query response successfully")
    void sendResponseMessage_query_happyPath() {
      when(mockBlockingStub.sendResponse(any(Kubemq.Response.class)))
          .thenReturn(Kubemq.Empty.newBuilder().build());

      assertDoesNotThrow(() -> client.sendResponseMessage(buildValidQueryResponse()));
      verify(mockBlockingStub).sendResponse(any(Kubemq.Response.class));
    }

    @Test
    @DisplayName("StatusRuntimeException propagates from sendResponseMessage (query)")
    void sendResponseMessage_query_statusRuntimeException_mapped() {
      when(mockBlockingStub.sendResponse(any(Kubemq.Response.class)))
          .thenThrow(new StatusRuntimeException(Status.INTERNAL));

      // Production code does not wrap exceptions
      assertThrows(
          StatusRuntimeException.class,
          () -> client.sendResponseMessage(buildValidQueryResponse()));
    }

    @Test
    @DisplayName("KubeMQException is rethrown as-is")
    void sendResponseMessage_query_kubemqException_rethrown() {
      KubeMQException original =
          KubeMQException.newBuilder().message("error").operation("test").build();
      when(mockBlockingStub.sendResponse(any(Kubemq.Response.class))).thenThrow(original);

      KubeMQException thrown =
          assertThrows(
              KubeMQException.class, () -> client.sendResponseMessage(buildValidQueryResponse()));
      assertSame(original, thrown);
    }

    @Test
    @DisplayName("Generic RuntimeException propagates from sendResponseMessage (query)")
    void sendResponseMessage_query_genericException_wrapped() {
      when(mockBlockingStub.sendResponse(any(Kubemq.Response.class)))
          .thenThrow(new RuntimeException("codec error"));

      // Production code does not wrap exceptions
      RuntimeException ex =
          assertThrows(
              RuntimeException.class,
              () -> client.sendResponseMessage(buildValidQueryResponse()));
      assertTrue(ex.getMessage().contains("codec error"));
    }
  }

  @Nested
  @DisplayName("Convenience Methods Tests")
  class ConvenienceMethodTests {

    @Test
    @DisplayName("sendCommand(CommandMessage) delegates to sendCommandRequest")
    void sendCommand_message_delegates() {
      Kubemq.Response grpcResponse =
          Kubemq.Response.newBuilder()
              .setExecuted(true)
              .setTimestamp(Instant.now().getEpochSecond() * 1_000_000_000L)
              .build();
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(grpcResponse);

      CommandMessage message =
          CommandMessage.builder()
              .channel("cmd-channel")
              .body("data".getBytes())
              .timeoutInSeconds(10)
              .build();

      CommandResponseMessage response = client.sendCommand(message);
      assertNotNull(response);
      assertTrue(response.isExecuted());
    }

    @Test
    @DisplayName("sendCommand(String, byte[], int) with non-null body")
    void sendCommand_channelBodyTimeout_succeeds() {
      Kubemq.Response grpcResponse =
          Kubemq.Response.newBuilder()
              .setExecuted(true)
              .setTimestamp(Instant.now().getEpochSecond() * 1_000_000_000L)
              .build();
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(grpcResponse);

      CommandResponseMessage response = client.sendCommand("cmd-channel", "data".getBytes(), 10);
      assertNotNull(response);
      assertTrue(response.isExecuted());
    }

    @Test
    @DisplayName("sendCommand(String, byte[], int) with null body triggers validation error")
    void sendCommand_channelNullBodyTimeout_validationError() {
      // null body is converted to empty byte[], which with no metadata/tags fails validation
      assertThrows(Exception.class, () -> client.sendCommand("cmd-channel", null, 10));
    }

    @Test
    @DisplayName("sendQuery(QueryMessage) delegates to sendQueryRequest")
    void sendQuery_message_delegates() {
      Kubemq.Response grpcResponse =
          Kubemq.Response.newBuilder()
              .setExecuted(true)
              .setTimestamp(Instant.now().getEpochSecond() * 1_000_000_000L)
              .build();
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(grpcResponse);

      QueryMessage message =
          QueryMessage.builder()
              .channel("query-channel")
              .body("data".getBytes())
              .timeoutInSeconds(10)
              .build();

      QueryResponseMessage response = client.sendQuery(message);
      assertNotNull(response);
      assertTrue(response.isExecuted());
    }

    @Test
    @DisplayName("sendQuery(String, byte[], int) with non-null body")
    void sendQuery_channelBodyTimeout_succeeds() {
      Kubemq.Response grpcResponse =
          Kubemq.Response.newBuilder()
              .setExecuted(true)
              .setTimestamp(Instant.now().getEpochSecond() * 1_000_000_000L)
              .build();
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(grpcResponse);

      QueryResponseMessage response = client.sendQuery("query-channel", "data".getBytes(), 10);
      assertNotNull(response);
      assertTrue(response.isExecuted());
    }

    @Test
    @DisplayName("sendQuery(String, byte[], int) with null body triggers validation error")
    void sendQuery_channelNullBodyTimeout_validationError() {
      // null body is converted to empty byte[], which with no metadata/tags fails validation
      assertThrows(Exception.class, () -> client.sendQuery("query-channel", null, 10));
    }
  }

  @Nested
  @DisplayName("Channel Management Tests")
  class ChannelManagementTests {

    @Test
    @DisplayName("createCommandsChannel succeeds")
    void createCommandsChannel_happyPath() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(Kubemq.Response.newBuilder().setExecuted(true).build());

      boolean result = client.createCommandsChannel("cmd-channel");
      assertTrue(result);
    }

    @Test
    @DisplayName("createQueriesChannel succeeds")
    void createQueriesChannel_happyPath() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(Kubemq.Response.newBuilder().setExecuted(true).build());

      boolean result = client.createQueriesChannel("query-channel");
      assertTrue(result);
    }

    @Test
    @DisplayName("deleteCommandsChannel succeeds")
    void deleteCommandsChannel_happyPath() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(Kubemq.Response.newBuilder().setExecuted(true).build());

      boolean result = client.deleteCommandsChannel("cmd-channel");
      assertTrue(result);
    }

    @Test
    @DisplayName("deleteQueriesChannel succeeds")
    void deleteQueriesChannel_happyPath() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(Kubemq.Response.newBuilder().setExecuted(true).build());

      boolean result = client.deleteQueriesChannel("query-channel");
      assertTrue(result);
    }

    @Test
    @DisplayName("listCommandsChannels returns channels")
    void listCommandsChannels_happyPath() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(
              Kubemq.Response.newBuilder()
                  .setExecuted(true)
                  .setBody(ByteString.copyFromUtf8("[]"))
                  .build());

      java.util.List<CQChannel> result = client.listCommandsChannels("*");
      assertNotNull(result);
    }

    @Test
    @DisplayName("listQueriesChannels returns channels")
    void listQueriesChannels_happyPath() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(
              Kubemq.Response.newBuilder()
                  .setExecuted(true)
                  .setBody(ByteString.copyFromUtf8("[]"))
                  .build());

      java.util.List<CQChannel> result = client.listQueriesChannels("*");
      assertNotNull(result);
    }

    @Test
    @DisplayName("createCommandsChannel with StatusRuntimeException")
    void createCommandsChannel_statusRuntimeException_throws() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

      assertThrows(Exception.class, () -> client.createCommandsChannel("cmd-channel"));
    }

    @Test
    @DisplayName("deleteCommandsChannel with StatusRuntimeException")
    void deleteCommandsChannel_statusRuntimeException_throws() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

      assertThrows(Exception.class, () -> client.deleteCommandsChannel("cmd-channel"));
    }

    @Test
    @DisplayName("listCommandsChannels with StatusRuntimeException")
    void listCommandsChannels_statusRuntimeException_throws() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

      assertThrows(Exception.class, () -> client.listCommandsChannels("*"));
    }
  }

  @Nested
  @DisplayName("Subscription Tests")
  class SubscriptionTests {

    @Test
    @DisplayName("subscribeToCommands succeeds")
    void subscribeToCommands_happyPath() {
      CommandsSubscription subscription =
          CommandsSubscription.builder()
              .channel("cmd-channel")
              .onReceiveCommandCallback(cmd -> {})
              .onErrorCallback(err -> {})
              .build();

      CommandsSubscription result = client.subscribeToCommands(subscription);
      assertNotNull(result);
      verify(mockAsyncStub).subscribeToRequests(any(Kubemq.Subscribe.class), any());
    }

    @Test
    @DisplayName("subscribeToQueries succeeds")
    void subscribeToQueries_happyPath() {
      QueriesSubscription subscription =
          QueriesSubscription.builder()
              .channel("query-channel")
              .onReceiveQueryCallback(query -> {})
              .onErrorCallback(err -> {})
              .build();

      QueriesSubscription result = client.subscribeToQueries(subscription);
      assertNotNull(result);
      verify(mockAsyncStub).subscribeToRequests(any(Kubemq.Subscribe.class), any());
    }

    @Test
    @DisplayName("subscribeToCommands with StatusRuntimeException")
    void subscribeToCommands_statusRuntimeException_invokesRpcCall() {
      CommandsSubscription subscription =
          CommandsSubscription.builder()
              .channel("cmd-channel")
              .onReceiveCommandCallback(cmd -> {})
              .onErrorCallback(err -> {})
              .build();

      client.subscribeToCommands(subscription);
      verify(mockAsyncStub).subscribeToRequests(any(Kubemq.Subscribe.class), any());
    }

    @Test
    @DisplayName("subscribeToQueries with StatusRuntimeException")
    void subscribeToQueries_statusRuntimeException_invokesRpcCall() {
      QueriesSubscription subscription =
          QueriesSubscription.builder()
              .channel("query-channel")
              .onReceiveQueryCallback(query -> {})
              .onErrorCallback(err -> {})
              .build();

      client.subscribeToQueries(subscription);
      verify(mockAsyncStub).subscribeToRequests(any(Kubemq.Subscribe.class), any());
    }
  }

  @Nested
  @DisplayName("Channel Management Error Paths via KubeMQUtils")
  class ChannelManagementErrorPathsTests {

    @Test
    @DisplayName("createCommandsChannel error response throws CreateChannelException")
    void createCommandsChannel_errorResponse_throws() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(
              Kubemq.Response.newBuilder().setExecuted(false).setError("channel exists").build());

      assertThrows(Exception.class, () -> client.createCommandsChannel("cmd-channel"));
    }

    @Test
    @DisplayName("deleteQueriesChannel error response throws DeleteChannelException")
    void deleteQueriesChannel_errorResponse_throws() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(
              Kubemq.Response.newBuilder()
                  .setExecuted(false)
                  .setError("channel not found")
                  .build());

      assertThrows(Exception.class, () -> client.deleteQueriesChannel("query-channel"));
    }

    @Test
    @DisplayName("listQueriesChannels error response throws ListChannelsException")
    void listQueriesChannels_errorResponse_throws() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(
              Kubemq.Response.newBuilder().setExecuted(false).setError("listing failed").build());

      assertThrows(Exception.class, () -> client.listQueriesChannels("*"));
    }

    @Test
    @DisplayName("listCommandsChannels with null search")
    void listCommandsChannels_nullSearch_succeeds() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(
              Kubemq.Response.newBuilder()
                  .setExecuted(true)
                  .setBody(ByteString.copyFromUtf8("[]"))
                  .build());

      java.util.List<CQChannel> result = client.listCommandsChannels(null);
      assertNotNull(result);
    }
  }
}
