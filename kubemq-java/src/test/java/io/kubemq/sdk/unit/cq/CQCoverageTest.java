package io.kubemq.sdk.unit.cq;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.Subscription;
import io.kubemq.sdk.cq.*;
import io.kubemq.sdk.exception.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import kubemq.Kubemq;
import kubemq.kubemqGrpc;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class CQCoverageTest {

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

  private Kubemq.Response successResponse() {
    return Kubemq.Response.newBuilder()
        .setClientID("server")
        .setRequestID("req-1")
        .setExecuted(true)
        .setTimestamp(Instant.now().getEpochSecond() * 1_000_000_000L)
        .build();
  }

  private Kubemq.Response querySuccessResponse() {
    return Kubemq.Response.newBuilder()
        .setClientID("server")
        .setRequestID("query-1")
        .setExecuted(true)
        .setMetadata("result-meta")
        .setBody(ByteString.copyFromUtf8("result-body"))
        .setTimestamp(Instant.now().getEpochSecond() * 1_000_000_000L)
        .build();
  }

  private CommandMessage validCommandMessage() {
    return CommandMessage.builder()
        .channel("cmd-channel")
        .body("data".getBytes())
        .timeoutInSeconds(10)
        .build();
  }

  private QueryMessage validQueryMessage() {
    return QueryMessage.builder()
        .channel("query-channel")
        .body("data".getBytes())
        .timeoutInSeconds(10)
        .build();
  }

  private CommandResponseMessage validCommandResponse() {
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

  private QueryResponseMessage validQueryResponse() {
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

  // ================================================================
  // CQClient: Async methods
  // ================================================================

  @Nested
  @DisplayName("sendCommandRequestAsync")
  class SendCommandRequestAsyncTests {

    @Test
    @DisplayName("Happy path returns decoded response")
    void happyPath() throws Exception {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(successResponse());

      CompletableFuture<CommandResponseMessage> future =
          client.sendCommandRequestAsync(validCommandMessage());
      CommandResponseMessage response = future.get(5, TimeUnit.SECONDS);

      assertNotNull(response);
      assertTrue(response.isExecuted());
    }

    @Test
    @DisplayName("Propagates exception through future")
    void exceptionPropagated() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

      CompletableFuture<CommandResponseMessage> future =
          client.sendCommandRequestAsync(validCommandMessage());

      ExecutionException ex =
          assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
      assertNotNull(ex.getCause());
    }

    @Test
    @DisplayName("Validation failure throws immediately")
    void validationFailure() {
      CommandMessage invalid =
          CommandMessage.builder().channel("cmd-channel").timeoutInSeconds(10).build();

      assertThrows(ValidationException.class, () -> client.sendCommandRequestAsync(invalid));
    }
  }

  @Nested
  @DisplayName("sendQueryRequestAsync")
  class SendQueryRequestAsyncTests {

    @Test
    @DisplayName("Happy path returns decoded response")
    void happyPath() throws Exception {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(querySuccessResponse());

      CompletableFuture<QueryResponseMessage> future =
          client.sendQueryRequestAsync(validQueryMessage());
      QueryResponseMessage response = future.get(5, TimeUnit.SECONDS);

      assertNotNull(response);
      assertTrue(response.isExecuted());
    }

    @Test
    @DisplayName("Propagates exception through future")
    void exceptionPropagated() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new StatusRuntimeException(Status.DEADLINE_EXCEEDED));

      CompletableFuture<QueryResponseMessage> future =
          client.sendQueryRequestAsync(validQueryMessage());

      ExecutionException ex =
          assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
      assertNotNull(ex.getCause());
    }

    @Test
    @DisplayName("Validation failure throws immediately")
    void validationFailure() {
      QueryMessage invalid =
          QueryMessage.builder().channel("query-channel").timeoutInSeconds(10).build();

      assertThrows(ValidationException.class, () -> client.sendQueryRequestAsync(invalid));
    }
  }

  @Nested
  @DisplayName("sendResponseMessageAsync (Command)")
  class SendCommandResponseAsyncTests {

    @Test
    @DisplayName("Happy path completes without error")
    void happyPath() throws Exception {
      when(mockBlockingStub.sendResponse(any(Kubemq.Response.class)))
          .thenReturn(Kubemq.Empty.newBuilder().build());

      CompletableFuture<Void> future = client.sendResponseMessageAsync(validCommandResponse());
      assertDoesNotThrow(() -> future.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Validation failure throws immediately for invalid response")
    void validationFailure() {
      CommandResponseMessage invalid = CommandResponseMessage.builder().isExecuted(true).build();

      assertThrows(IllegalArgumentException.class, () -> client.sendResponseMessageAsync(invalid));
    }
  }

  @Nested
  @DisplayName("sendResponseMessageAsync (Query)")
  class SendQueryResponseAsyncTests {

    @Test
    @DisplayName("Happy path completes without error")
    void happyPath() throws Exception {
      when(mockBlockingStub.sendResponse(any(Kubemq.Response.class)))
          .thenReturn(Kubemq.Empty.newBuilder().build());

      CompletableFuture<Void> future = client.sendResponseMessageAsync(validQueryResponse());
      assertDoesNotThrow(() -> future.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Validation failure throws immediately for invalid response")
    void validationFailure() {
      QueryResponseMessage invalid = QueryResponseMessage.builder().isExecuted(true).build();

      assertThrows(IllegalArgumentException.class, () -> client.sendResponseMessageAsync(invalid));
    }
  }

  // ================================================================
  // CQClient: Duration overloads
  // ================================================================

  @Nested
  @DisplayName("sendCommandRequest with Duration")
  class SendCommandDurationTests {

    @Test
    @DisplayName("Returns response within timeout")
    void happyPath() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenReturn(successResponse());

      CommandResponseMessage response =
          client.sendCommandRequest(validCommandMessage(), Duration.ofSeconds(5));

      assertNotNull(response);
      assertTrue(response.isExecuted());
    }

    @Test
    @DisplayName("Throws on timeout")
    void timeoutThrows() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenAnswer(
              inv -> {
                Thread.sleep(10_000);
                return successResponse();
              });

      assertThrows(
          KubeMQException.class,
          () -> client.sendCommandRequest(validCommandMessage(), Duration.ofMillis(100)));
    }
  }

  @Nested
  @DisplayName("sendQueryRequest with Duration")
  class SendQueryDurationTests {

    @Test
    @DisplayName("Returns response within timeout")
    void happyPath() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(querySuccessResponse());

      QueryResponseMessage response =
          client.sendQueryRequest(validQueryMessage(), Duration.ofSeconds(5));

      assertNotNull(response);
      assertTrue(response.isExecuted());
    }

    @Test
    @DisplayName("Throws on timeout")
    void timeoutThrows() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenAnswer(
              inv -> {
                Thread.sleep(10_000);
                return querySuccessResponse();
              });

      assertThrows(
          KubeMQException.class,
          () -> client.sendQueryRequest(validQueryMessage(), Duration.ofMillis(100)));
    }
  }

  // ================================================================
  // CQClient: Subscription handle methods
  // ================================================================

  @Nested
  @DisplayName("subscribeToCommandsWithHandle")
  class SubscribeCommandsWithHandleTests {

    @Test
    @DisplayName("Returns a Subscription handle")
    void returnsHandle() {
      CommandsSubscription sub =
          CommandsSubscription.builder()
              .channel("cmd-channel")
              .onReceiveCommandCallback(cmd -> {})
              .onErrorCallback(err -> {})
              .build();

      Subscription handle = client.subscribeToCommandsWithHandle(sub);

      assertNotNull(handle);
      assertFalse(handle.isCancelled());
      verify(mockAsyncStub).subscribeToRequests(any(Kubemq.Subscribe.class), any());
    }

    @Test
    @DisplayName("Handle.cancel() cancels subscription")
    void cancelWorks() {
      CommandsSubscription sub =
          CommandsSubscription.builder()
              .channel("cmd-channel")
              .onReceiveCommandCallback(cmd -> {})
              .onErrorCallback(err -> {})
              .build();

      Subscription handle = client.subscribeToCommandsWithHandle(sub);
      handle.cancel();

      assertTrue(handle.isCancelled());
    }

    @Test
    @DisplayName("Validation fails for invalid subscription")
    void validationFailure() {
      CommandsSubscription sub =
          CommandsSubscription.builder().onReceiveCommandCallback(cmd -> {}).build();

      assertThrows(ValidationException.class, () -> client.subscribeToCommandsWithHandle(sub));
    }
  }

  @Nested
  @DisplayName("subscribeToQueriesWithHandle")
  class SubscribeQueriesWithHandleTests {

    @Test
    @DisplayName("Returns a Subscription handle")
    void returnsHandle() {
      QueriesSubscription sub =
          QueriesSubscription.builder()
              .channel("query-channel")
              .onReceiveQueryCallback(q -> {})
              .onErrorCallback(err -> {})
              .build();

      Subscription handle = client.subscribeToQueriesWithHandle(sub);

      assertNotNull(handle);
      assertFalse(handle.isCancelled());
      verify(mockAsyncStub).subscribeToRequests(any(Kubemq.Subscribe.class), any());
    }

    @Test
    @DisplayName("Handle.cancel() cancels subscription")
    void cancelWorks() {
      QueriesSubscription sub =
          QueriesSubscription.builder()
              .channel("query-channel")
              .onReceiveQueryCallback(q -> {})
              .onErrorCallback(err -> {})
              .build();

      Subscription handle = client.subscribeToQueriesWithHandle(sub);
      handle.cancel();

      assertTrue(handle.isCancelled());
    }

    @Test
    @DisplayName("Validation fails for invalid subscription")
    void validationFailure() {
      QueriesSubscription sub =
          QueriesSubscription.builder().onReceiveQueryCallback(q -> {}).build();

      assertThrows(ValidationException.class, () -> client.subscribeToQueriesWithHandle(sub));
    }
  }

  // ================================================================
  // CQClient: Async channel management
  // ================================================================

  @Nested
  @DisplayName("Async channel management methods")
  class AsyncChannelManagementTests {

    @Test
    @DisplayName("createCommandsChannelAsync succeeds")
    void createCommandsAsync() throws Exception {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(Kubemq.Response.newBuilder().setExecuted(true).build());

      CompletableFuture<Boolean> future = client.createCommandsChannelAsync("cmd-ch");
      assertTrue(future.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("createQueriesChannelAsync succeeds")
    void createQueriesAsync() throws Exception {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(Kubemq.Response.newBuilder().setExecuted(true).build());

      CompletableFuture<Boolean> future = client.createQueriesChannelAsync("query-ch");
      assertTrue(future.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("deleteCommandsChannelAsync succeeds")
    void deleteCommandsAsync() throws Exception {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(Kubemq.Response.newBuilder().setExecuted(true).build());

      CompletableFuture<Boolean> future = client.deleteCommandsChannelAsync("cmd-ch");
      assertTrue(future.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("deleteQueriesChannelAsync succeeds")
    void deleteQueriesAsync() throws Exception {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(Kubemq.Response.newBuilder().setExecuted(true).build());

      CompletableFuture<Boolean> future = client.deleteQueriesChannelAsync("query-ch");
      assertTrue(future.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("listCommandsChannelsAsync succeeds")
    void listCommandsAsync() throws Exception {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(
              Kubemq.Response.newBuilder()
                  .setExecuted(true)
                  .setBody(ByteString.copyFromUtf8("[]"))
                  .build());

      CompletableFuture<java.util.List<CQChannel>> future = client.listCommandsChannelsAsync("*");
      assertNotNull(future.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("listQueriesChannelsAsync succeeds")
    void listQueriesAsync() throws Exception {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(
              Kubemq.Response.newBuilder()
                  .setExecuted(true)
                  .setBody(ByteString.copyFromUtf8("[]"))
                  .build());

      CompletableFuture<java.util.List<CQChannel>> future = client.listQueriesChannelsAsync("*");
      assertNotNull(future.get(5, TimeUnit.SECONDS));
    }
  }

  // ================================================================
  // CQClient: Closed client
  // ================================================================

  @Nested
  @DisplayName("Closed client operations")
  class ClosedClientTests {

    @Test
    @DisplayName("sendCommandRequest on closed client throws ClientClosedException")
    void sendCommandOnClosed() {
      client.close();
      assertThrows(
          ClientClosedException.class, () -> client.sendCommandRequest(validCommandMessage()));
    }

    @Test
    @DisplayName("sendQueryRequest on closed client throws ClientClosedException")
    void sendQueryOnClosed() {
      client.close();
      assertThrows(ClientClosedException.class, () -> client.sendQueryRequest(validQueryMessage()));
    }

    @Test
    @DisplayName("sendResponseMessage(Command) on closed client throws ClientClosedException")
    void sendCommandResponseOnClosed() {
      client.close();
      assertThrows(
          ClientClosedException.class, () -> client.sendResponseMessage(validCommandResponse()));
    }

    @Test
    @DisplayName("sendResponseMessage(Query) on closed client throws ClientClosedException")
    void sendQueryResponseOnClosed() {
      client.close();
      assertThrows(
          ClientClosedException.class, () -> client.sendResponseMessage(validQueryResponse()));
    }

    @Test
    @DisplayName("sendCommandRequestAsync on closed client throws ClientClosedException")
    void asyncCommandOnClosed() {
      client.close();
      assertThrows(
          ClientClosedException.class, () -> client.sendCommandRequestAsync(validCommandMessage()));
    }

    @Test
    @DisplayName("sendQueryRequestAsync on closed client throws ClientClosedException")
    void asyncQueryOnClosed() {
      client.close();
      assertThrows(
          ClientClosedException.class, () -> client.sendQueryRequestAsync(validQueryMessage()));
    }

    @Test
    @DisplayName("sendResponseMessageAsync(Command) on closed client throws ClientClosedException")
    void asyncCommandResponseOnClosed() {
      client.close();
      assertThrows(
          ClientClosedException.class,
          () -> client.sendResponseMessageAsync(validCommandResponse()));
    }

    @Test
    @DisplayName("sendResponseMessageAsync(Query) on closed client throws ClientClosedException")
    void asyncQueryResponseOnClosed() {
      client.close();
      assertThrows(
          ClientClosedException.class, () -> client.sendResponseMessageAsync(validQueryResponse()));
    }

    @Test
    @DisplayName("createCommandsChannel on closed client throws ClientClosedException")
    void createCmdChannelOnClosed() {
      client.close();
      assertThrows(ClientClosedException.class, () -> client.createCommandsChannel("ch"));
    }

    @Test
    @DisplayName("subscribeToCommands on closed client throws ClientClosedException")
    void subscribeCommandsOnClosed() {
      client.close();
      CommandsSubscription sub =
          CommandsSubscription.builder().channel("ch").onReceiveCommandCallback(cmd -> {}).build();
      assertThrows(ClientClosedException.class, () -> client.subscribeToCommands(sub));
    }

    @Test
    @DisplayName("subscribeToQueriesWithHandle on closed client throws ClientClosedException")
    void subscribeQueriesHandleOnClosed() {
      client.close();
      QueriesSubscription sub =
          QueriesSubscription.builder().channel("ch").onReceiveQueryCallback(q -> {}).build();
      assertThrows(ClientClosedException.class, () -> client.subscribeToQueriesWithHandle(sub));
    }
  }

  // ================================================================
  // CommandsSubscription: cancel, raiseOnReceiveMessage, raiseOnError
  // ================================================================

  @Nested
  @DisplayName("CommandsSubscription coverage")
  class CommandsSubscriptionCoverageTests {

    @Mock private CQClient mockCqClient;

    @Test
    @DisplayName("cancel with null observer does not throw")
    void cancelNullObserver() {
      CommandsSubscription sub =
          CommandsSubscription.builder().channel("ch").onReceiveCommandCallback(cmd -> {}).build();

      assertDoesNotThrow(sub::cancel);
    }

    @Test
    @DisplayName("cancel with observer calls onCompleted")
    void cancelWithObserver() {
      when(mockCqClient.getClientId()).thenReturn("test");
      when(mockCqClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      CommandsSubscription sub =
          CommandsSubscription.builder().channel("ch").onReceiveCommandCallback(cmd -> {}).build();

      sub.encode("test", mockCqClient);
      StreamObserver<Kubemq.Request> observer = sub.getObserver();
      assertNotNull(observer);

      assertDoesNotThrow(sub::cancel);
    }

    @Test
    @DisplayName("raiseOnReceiveMessage with null callback does not throw")
    void raiseOnReceiveNullCallback() {
      CommandsSubscription sub = CommandsSubscription.builder().channel("ch").build();

      CommandMessageReceived msg =
          CommandMessageReceived.builder().id("id").channel("ch").body("data".getBytes()).build();

      assertDoesNotThrow(() -> sub.raiseOnReceiveMessage(msg));
    }

    @Test
    @DisplayName("raiseOnReceiveMessage invokes callback")
    void raiseOnReceiveCallsCallback() {
      AtomicReference<CommandMessageReceived> ref = new AtomicReference<>();
      CommandsSubscription sub =
          CommandsSubscription.builder().channel("ch").onReceiveCommandCallback(ref::set).build();

      CommandMessageReceived msg =
          CommandMessageReceived.builder().id("id").channel("ch").body("data".getBytes()).build();

      sub.raiseOnReceiveMessage(msg);
      assertNotNull(ref.get());
      assertEquals("id", ref.get().getId());
    }

    @Test
    @DisplayName("raiseOnError with callback invokes callback")
    void raiseOnErrorCallsCallback() {
      AtomicReference<KubeMQException> ref = new AtomicReference<>();
      CommandsSubscription sub =
          CommandsSubscription.builder()
              .channel("ch")
              .onReceiveCommandCallback(cmd -> {})
              .onErrorCallback(ref::set)
              .build();

      KubeMQException error =
          KubeMQException.newBuilder().message("test error").operation("test").build();

      sub.raiseOnError(error);
      assertNotNull(ref.get());
      assertEquals("test error", ref.get().getMessage());
    }

    @Test
    @DisplayName("raiseOnError without callback does not throw (logs)")
    void raiseOnErrorNoCallback() {
      CommandsSubscription sub =
          CommandsSubscription.builder().channel("ch").onReceiveCommandCallback(cmd -> {}).build();

      KubeMQException error =
          KubeMQException.newBuilder().message("test error").operation("test").build();

      assertDoesNotThrow(() -> sub.raiseOnError(error));
    }

    @Test
    @DisplayName("onError with StatusRuntimeException maps via GrpcErrorMapper")
    void observerOnErrorStatusRuntime() throws InterruptedException {
      when(mockCqClient.getClientId()).thenReturn("test");
      when(mockCqClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<KubeMQException> errorRef = new AtomicReference<>();

      CommandsSubscription sub =
          CommandsSubscription.builder()
              .channel("ch")
              .onReceiveCommandCallback(cmd -> {})
              .onErrorCallback(
                  ex -> {
                    errorRef.set(ex);
                    latch.countDown();
                  })
              .build();

      sub.encode("test", mockCqClient);
      StreamObserver<Kubemq.Request> observer = sub.getObserver();

      observer.onError(
          new StatusRuntimeException(Status.PERMISSION_DENIED.withDescription("no access")));

      assertTrue(latch.await(5, TimeUnit.SECONDS));
      assertNotNull(errorRef.get());
      assertTrue(errorRef.get() instanceof AuthorizationException);
    }

    @Test
    @DisplayName("onError with StatusRuntimeException UNAVAILABLE triggers reconnect")
    void observerOnErrorUnavailableTriggersReconnect() throws InterruptedException {
      when(mockCqClient.getClientId()).thenReturn("test");
      when(mockCqClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      CountDownLatch latch = new CountDownLatch(1);

      CommandsSubscription sub =
          CommandsSubscription.builder()
              .channel("ch")
              .onReceiveCommandCallback(cmd -> {})
              .onErrorCallback(ex -> latch.countDown())
              .build();

      sub.encode("test", mockCqClient);
      StreamObserver<Kubemq.Request> observer = sub.getObserver();

      observer.onError(
          new StatusRuntimeException(Status.UNAVAILABLE.withDescription("connection lost")));

      assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("onNext when user callback throws invokes onError with HandlerException")
    void observerOnNextUserExceptionHandled() throws InterruptedException {
      when(mockCqClient.getClientId()).thenReturn("test");
      when(mockCqClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<KubeMQException> errorRef = new AtomicReference<>();

      CommandsSubscription sub =
          CommandsSubscription.builder()
              .channel("ch")
              .onReceiveCommandCallback(
                  cmd -> {
                    throw new RuntimeException("user callback error");
                  })
              .onErrorCallback(
                  ex -> {
                    errorRef.set(ex);
                    latch.countDown();
                  })
              .build();

      sub.encode("test", mockCqClient);
      StreamObserver<Kubemq.Request> observer = sub.getObserver();

      Kubemq.Request request =
          Kubemq.Request.newBuilder()
              .setRequestID("req-1")
              .setChannel("ch")
              .setClientID("sender")
              .setBody(ByteString.copyFromUtf8("body"))
              .setReplyChannel("reply")
              .build();

      observer.onNext(request);

      assertTrue(latch.await(5, TimeUnit.SECONDS));
      assertNotNull(errorRef.get());
      assertTrue(errorRef.get() instanceof HandlerException);
      assertTrue(errorRef.get().getMessage().contains("user callback error"));
    }

    @Test
    @DisplayName("resetReconnectAttempts with no handler does not throw")
    void resetReconnectNoHandler() {
      CommandsSubscription sub =
          CommandsSubscription.builder().channel("ch").onReceiveCommandCallback(cmd -> {}).build();

      assertDoesNotThrow(sub::resetReconnectAttempts);
    }

    @Test
    @DisplayName("encode with custom callbackExecutor")
    void encodeWithCustomExecutor() {
      when(mockCqClient.getClientId()).thenReturn("test");
      when(mockCqClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      CommandsSubscription sub =
          CommandsSubscription.builder()
              .channel("ch")
              .onReceiveCommandCallback(cmd -> {})
              .callbackExecutor(Runnable::run)
              .build();

      Kubemq.Subscribe result = sub.encode("test", mockCqClient);

      assertNotNull(result);
      assertEquals("ch", result.getChannel());
    }

    @Test
    @DisplayName("encode with maxConcurrentCallbacks")
    void encodeWithMaxConcurrent() {
      when(mockCqClient.getClientId()).thenReturn("test");
      when(mockCqClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      CommandsSubscription sub =
          CommandsSubscription.builder()
              .channel("ch")
              .onReceiveCommandCallback(cmd -> {})
              .maxConcurrentCallbacks(5)
              .build();

      Kubemq.Subscribe result = sub.encode("test", mockCqClient);

      assertNotNull(result);
    }

    @Test
    @DisplayName("getReconnectExecutor returns non-null")
    void reconnectExecutorNotNull() {
      assertNotNull(CommandsSubscription.getReconnectExecutor());
    }

    @Test
    @DisplayName("toString format")
    void toStringFormat() {
      CommandsSubscription sub =
          CommandsSubscription.builder()
              .channel("my-ch")
              .group("my-grp")
              .onReceiveCommandCallback(cmd -> {})
              .build();

      String str = sub.toString();
      assertTrue(str.contains("CommandsSubscription"));
      assertTrue(str.contains("my-ch"));
      assertTrue(str.contains("my-grp"));
    }
  }

  // ================================================================
  // QueriesSubscription: cancel, raiseOnReceiveMessage, raiseOnError
  // ================================================================

  @Nested
  @DisplayName("QueriesSubscription coverage")
  class QueriesSubscriptionCoverageTests {

    @Mock private CQClient mockCqClient;

    @Test
    @DisplayName("cancel with null observer does not throw")
    void cancelNullObserver() {
      QueriesSubscription sub =
          QueriesSubscription.builder().channel("ch").onReceiveQueryCallback(q -> {}).build();

      assertDoesNotThrow(sub::cancel);
    }

    @Test
    @DisplayName("cancel with observer calls onCompleted")
    void cancelWithObserver() {
      when(mockCqClient.getClientId()).thenReturn("test");
      when(mockCqClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      QueriesSubscription sub =
          QueriesSubscription.builder().channel("ch").onReceiveQueryCallback(q -> {}).build();

      sub.encode("test", mockCqClient);
      assertNotNull(sub.getObserver());

      assertDoesNotThrow(sub::cancel);
    }

    @Test
    @DisplayName("raiseOnReceiveMessage with null callback does not throw")
    void raiseOnReceiveNullCallback() {
      QueriesSubscription sub = QueriesSubscription.builder().channel("ch").build();

      QueryMessageReceived msg = new QueryMessageReceived();
      msg.setId("id");
      msg.setChannel("ch");
      msg.setBody("data".getBytes());

      assertDoesNotThrow(() -> sub.raiseOnReceiveMessage(msg));
    }

    @Test
    @DisplayName("raiseOnReceiveMessage invokes callback")
    void raiseOnReceiveCallsCallback() {
      AtomicReference<QueryMessageReceived> ref = new AtomicReference<>();
      QueriesSubscription sub =
          QueriesSubscription.builder().channel("ch").onReceiveQueryCallback(ref::set).build();

      QueryMessageReceived msg = new QueryMessageReceived();
      msg.setId("id");
      msg.setChannel("ch");
      msg.setBody("data".getBytes());

      sub.raiseOnReceiveMessage(msg);
      assertNotNull(ref.get());
      assertEquals("id", ref.get().getId());
    }

    @Test
    @DisplayName("raiseOnError with callback invokes callback")
    void raiseOnErrorCallsCallback() {
      AtomicReference<KubeMQException> ref = new AtomicReference<>();
      QueriesSubscription sub =
          QueriesSubscription.builder()
              .channel("ch")
              .onReceiveQueryCallback(q -> {})
              .onErrorCallback(ref::set)
              .build();

      KubeMQException error =
          KubeMQException.newBuilder().message("test error").operation("test").build();

      sub.raiseOnError(error);
      assertNotNull(ref.get());
    }

    @Test
    @DisplayName("raiseOnError without callback does not throw (logs)")
    void raiseOnErrorNoCallback() {
      QueriesSubscription sub =
          QueriesSubscription.builder().channel("ch").onReceiveQueryCallback(q -> {}).build();

      KubeMQException error =
          KubeMQException.newBuilder().message("test error").operation("test").build();

      assertDoesNotThrow(() -> sub.raiseOnError(error));
    }

    @Test
    @DisplayName("onError with StatusRuntimeException maps via GrpcErrorMapper")
    void observerOnErrorStatusRuntime() throws InterruptedException {
      when(mockCqClient.getClientId()).thenReturn("test");
      when(mockCqClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<KubeMQException> errorRef = new AtomicReference<>();

      QueriesSubscription sub =
          QueriesSubscription.builder()
              .channel("ch")
              .onReceiveQueryCallback(q -> {})
              .onErrorCallback(
                  ex -> {
                    errorRef.set(ex);
                    latch.countDown();
                  })
              .build();

      sub.encode("test", mockCqClient);
      sub.getObserver()
          .onError(
              new StatusRuntimeException(Status.PERMISSION_DENIED.withDescription("no access")));

      assertTrue(latch.await(5, TimeUnit.SECONDS));
      assertNotNull(errorRef.get());
      assertTrue(errorRef.get() instanceof AuthorizationException);
    }

    @Test
    @DisplayName("onError with StatusRuntimeException UNAVAILABLE triggers reconnect")
    void observerOnErrorUnavailableReconnect() throws InterruptedException {
      when(mockCqClient.getClientId()).thenReturn("test");
      when(mockCqClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      CountDownLatch latch = new CountDownLatch(1);

      QueriesSubscription sub =
          QueriesSubscription.builder()
              .channel("ch")
              .onReceiveQueryCallback(q -> {})
              .onErrorCallback(ex -> latch.countDown())
              .build();

      sub.encode("test", mockCqClient);
      sub.getObserver()
          .onError(
              new StatusRuntimeException(Status.UNAVAILABLE.withDescription("connection lost")));

      assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("onNext when user callback throws invokes onError with HandlerException")
    void observerOnNextUserExceptionHandled() throws InterruptedException {
      when(mockCqClient.getClientId()).thenReturn("test");
      when(mockCqClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<KubeMQException> errorRef = new AtomicReference<>();

      QueriesSubscription sub =
          QueriesSubscription.builder()
              .channel("ch")
              .onReceiveQueryCallback(
                  q -> {
                    throw new RuntimeException("user query handler error");
                  })
              .onErrorCallback(
                  ex -> {
                    errorRef.set(ex);
                    latch.countDown();
                  })
              .build();

      sub.encode("test", mockCqClient);

      Kubemq.Request request =
          Kubemq.Request.newBuilder()
              .setRequestID("req-1")
              .setChannel("ch")
              .setClientID("sender")
              .setBody(ByteString.copyFromUtf8("body"))
              .setReplyChannel("reply")
              .build();

      sub.getObserver().onNext(request);

      assertTrue(latch.await(5, TimeUnit.SECONDS));
      assertNotNull(errorRef.get());
      assertTrue(errorRef.get() instanceof HandlerException);
      assertTrue(errorRef.get().getMessage().contains("user query handler error"));
    }

    @Test
    @DisplayName("resetReconnectAttempts with no handler does not throw")
    void resetReconnectNoHandler() {
      QueriesSubscription sub =
          QueriesSubscription.builder().channel("ch").onReceiveQueryCallback(q -> {}).build();

      assertDoesNotThrow(sub::resetReconnectAttempts);
    }

    @Test
    @DisplayName("encode with custom callbackExecutor")
    void encodeWithCustomExecutor() {
      when(mockCqClient.getClientId()).thenReturn("test");
      when(mockCqClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      QueriesSubscription sub =
          QueriesSubscription.builder()
              .channel("ch")
              .onReceiveQueryCallback(q -> {})
              .callbackExecutor(Runnable::run)
              .build();

      Kubemq.Subscribe result = sub.encode("test", mockCqClient);

      assertNotNull(result);
      assertEquals("ch", result.getChannel());
    }

    @Test
    @DisplayName("encode with maxConcurrentCallbacks")
    void encodeWithMaxConcurrent() {
      when(mockCqClient.getClientId()).thenReturn("test");
      when(mockCqClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      QueriesSubscription sub =
          QueriesSubscription.builder()
              .channel("ch")
              .onReceiveQueryCallback(q -> {})
              .maxConcurrentCallbacks(5)
              .build();

      Kubemq.Subscribe result = sub.encode("test", mockCqClient);

      assertNotNull(result);
    }

    @Test
    @DisplayName("getReconnectExecutor returns non-null")
    void reconnectExecutorNotNull() {
      assertNotNull(QueriesSubscription.getReconnectExecutor());
    }

    @Test
    @DisplayName("toString format")
    void toStringFormat() {
      QueriesSubscription sub =
          QueriesSubscription.builder()
              .channel("my-ch")
              .group("my-grp")
              .onReceiveQueryCallback(q -> {})
              .build();

      String str = sub.toString();
      assertTrue(str.contains("QueriesSubscription"));
      assertTrue(str.contains("my-ch"));
      assertTrue(str.contains("my-grp"));
    }
  }

  // ================================================================
  // CommandMessage: toString, encode edge cases, validate chain
  // ================================================================

  @Nested
  @DisplayName("CommandMessage coverage")
  class CommandMessageCoverageTests {

    @Test
    @DisplayName("toString contains all fields")
    void toStringContainsFields() {
      Map<String, String> tags = new HashMap<>();
      tags.put("k", "v");

      CommandMessage msg =
          CommandMessage.builder()
              .id("msg-1")
              .channel("ch")
              .metadata("meta")
              .body("body".getBytes())
              .tags(tags)
              .timeoutInSeconds(30)
              .build();

      String str = msg.toString();
      assertTrue(str.contains("msg-1"));
      assertTrue(str.contains("ch"));
      assertTrue(str.contains("meta"));
      assertTrue(str.contains("body"));
      assertTrue(str.contains("30"));
    }

    @Test
    @DisplayName("toString with null id and metadata")
    void toStringNulls() {
      CommandMessage msg =
          CommandMessage.builder()
              .channel("ch")
              .body("body".getBytes())
              .timeoutInSeconds(10)
              .build();

      String str = msg.toString();
      assertNotNull(str);
      assertTrue(str.contains("CommandMessage"));
    }

    @Test
    @DisplayName("validate returns this for chaining")
    void validateReturnsThis() {
      CommandMessage msg =
          CommandMessage.builder()
              .channel("ch")
              .body("data".getBytes())
              .timeoutInSeconds(10)
              .build();

      CommandMessage result = msg.validate();
      assertSame(msg, result);
    }

    @Test
    @DisplayName("encode with null metadata uses empty string")
    void encodeNullMetadata() {
      CommandMessage msg =
          CommandMessage.builder()
              .channel("ch")
              .body("data".getBytes())
              .timeoutInSeconds(10)
              .build();

      Kubemq.Request proto = msg.encode("client");
      assertEquals("", proto.getMetadata());
    }

    @Test
    @DisplayName("encode with non-null id uses that id")
    void encodeWithId() {
      CommandMessage msg =
          CommandMessage.builder()
              .id("my-id")
              .channel("ch")
              .body("data".getBytes())
              .timeoutInSeconds(10)
              .build();

      Kubemq.Request proto = msg.encode("client");
      assertEquals("my-id", proto.getRequestID());
    }

    @Test
    @DisplayName("encode with null id generates UUID")
    void encodeNullId() {
      CommandMessage msg =
          CommandMessage.builder()
              .channel("ch")
              .body("data".getBytes())
              .timeoutInSeconds(10)
              .build();

      Kubemq.Request proto = msg.encode("client");
      assertNotNull(proto.getRequestID());
      assertFalse(proto.getRequestID().isEmpty());
    }

    @Test
    @DisplayName("constructor normalizes null body to empty array")
    void constructorNullBody() {
      CommandMessage msg = new CommandMessage(null, "ch", null, null, null, 10);
      assertNotNull(msg.getBody());
      assertEquals(0, msg.getBody().length);
    }

    @Test
    @DisplayName("constructor normalizes null tags to empty map")
    void constructorNullTags() {
      CommandMessage msg = new CommandMessage(null, "ch", null, "data".getBytes(), null, 10);
      assertNotNull(msg.getTags());
      assertTrue(msg.getTags().isEmpty());
    }

    @Test
    @DisplayName("encode with null tags initializes and adds client-id")
    void encodeNullTags() {
      CommandMessage msg =
          new CommandMessage("id", "ch", "meta", "data".getBytes(), null, 10);
      Kubemq.Request proto = msg.encode("client");
      assertEquals("client", proto.getTagsMap().get("x-kubemq-client-id"));
    }

    @Test
    @DisplayName("validate with null metadata, null body, null tags throws")
    void validateAllNull() {
      CommandMessage msg =
          new CommandMessage(null, "ch", null, new byte[0], new HashMap<>(), 10);
      assertThrows(ValidationException.class, msg::validate);
    }

    @Test
    @DisplayName("validate with only metadata passes")
    void validateMetadataOnly() {
      CommandMessage msg =
          new CommandMessage(null, "ch", "meta", new byte[0], new HashMap<>(), 10);
      assertDoesNotThrow(msg::validate);
    }

    @Test
    @DisplayName("validate with only tags passes")
    void validateTagsOnly() {
      Map<String, String> tags = new HashMap<>();
      tags.put("k", "v");
      CommandMessage msg = new CommandMessage(null, "ch", null, new byte[0], tags, 10);
      assertDoesNotThrow(msg::validate);
    }

    @Test
    @DisplayName("builder with invalid channel format at build time")
    void builderInvalidChannel() {
      assertThrows(
          Exception.class,
          () ->
              CommandMessage.builder()
                  .channel("invalid channel name!@#")
                  .body("data".getBytes())
                  .timeoutInSeconds(10)
                  .build());
    }
  }

  // ================================================================
  // QueryMessage: toString, encode edge cases
  // ================================================================

  @Nested
  @DisplayName("QueryMessage coverage")
  class QueryMessageCoverageTests {

    @Test
    @DisplayName("toString contains all fields including cache")
    void toStringContainsFields() {
      Map<String, String> tags = new HashMap<>();
      tags.put("k", "v");

      QueryMessage msg =
          QueryMessage.builder()
              .id("msg-1")
              .channel("ch")
              .metadata("meta")
              .body("body".getBytes())
              .tags(tags)
              .timeoutInSeconds(30)
              .cacheKey("cache-key")
              .cacheTtlInSeconds(300)
              .build();

      String str = msg.toString();
      assertTrue(str.contains("msg-1"));
      assertTrue(str.contains("ch"));
      assertTrue(str.contains("meta"));
      assertTrue(str.contains("cache-key"));
      assertTrue(str.contains("300"));
    }

    @Test
    @DisplayName("toString with null fields")
    void toStringNulls() {
      QueryMessage msg =
          QueryMessage.builder().channel("ch").body("body".getBytes()).timeoutInSeconds(10).build();

      String str = msg.toString();
      assertNotNull(str);
      assertTrue(str.contains("QueryMessage"));
    }

    @Test
    @DisplayName("encode with null cacheKey uses empty string")
    void encodeNullCacheKey() {
      QueryMessage msg =
          QueryMessage.builder().channel("ch").body("data".getBytes()).timeoutInSeconds(10).build();

      Kubemq.Request proto = msg.encode("client");
      assertEquals("", proto.getCacheKey());
    }

    @Test
    @DisplayName("encode with non-null cacheKey")
    void encodeWithCacheKey() {
      QueryMessage msg =
          QueryMessage.builder()
              .channel("ch")
              .body("data".getBytes())
              .timeoutInSeconds(10)
              .cacheKey("my-key")
              .cacheTtlInSeconds(60)
              .build();

      Kubemq.Request proto = msg.encode("client");
      assertEquals("my-key", proto.getCacheKey());
      assertEquals(60000, proto.getCacheTTL());
    }

    @Test
    @DisplayName("encode with null id generates UUID")
    void encodeNullId() {
      QueryMessage msg =
          QueryMessage.builder().channel("ch").body("data".getBytes()).timeoutInSeconds(10).build();

      Kubemq.Request proto = msg.encode("client");
      assertNotNull(proto.getRequestID());
      assertFalse(proto.getRequestID().isEmpty());
    }

    @Test
    @DisplayName("encode with non-null id")
    void encodeWithId() {
      QueryMessage msg =
          QueryMessage.builder()
              .id("my-id")
              .channel("ch")
              .body("data".getBytes())
              .timeoutInSeconds(10)
              .build();

      Kubemq.Request proto = msg.encode("client");
      assertEquals("my-id", proto.getRequestID());
    }

    @Test
    @DisplayName("encode with empty tags adds client-id")
    void encodeEmptyTags() {
      QueryMessage msg =
          new QueryMessage("id", "ch", "meta", "data".getBytes(), new HashMap<>(), 10, null, 0);
      Kubemq.Request proto = msg.encode("client");
      assertEquals("client", proto.getTagsMap().get("x-kubemq-client-id"));
    }

    @Test
    @DisplayName("encode with null metadata uses empty string")
    void encodeNullMetadata() {
      QueryMessage msg =
          QueryMessage.builder().channel("ch").body("data".getBytes()).timeoutInSeconds(10).build();

      Kubemq.Request proto = msg.encode("client");
      assertEquals("", proto.getMetadata());
    }

    @Test
    @DisplayName("validate with null metadata, null body, null tags throws")
    void validateAllNull() {
      QueryMessage msg =
          new QueryMessage(null, "ch", null, new byte[0], new HashMap<>(), 10, null, 0);
      assertThrows(ValidationException.class, msg::validate);
    }

    @Test
    @DisplayName("validate with only metadata passes")
    void validateMetadataOnly() {
      QueryMessage msg =
          new QueryMessage(null, "ch", "meta", new byte[0], new HashMap<>(), 10, null, 0);
      assertDoesNotThrow(msg::validate);
    }

    @Test
    @DisplayName("validate with only tags passes")
    void validateTagsOnly() {
      Map<String, String> tags = new HashMap<>();
      tags.put("k", "v");
      QueryMessage msg = new QueryMessage(null, "ch", null, new byte[0], tags, 10, null, 0);
      assertDoesNotThrow(msg::validate);
    }

    @Test
    @DisplayName("builder with invalid channel format at build time")
    void builderInvalidChannel() {
      assertThrows(
          Exception.class,
          () ->
              QueryMessage.builder()
                  .channel("invalid channel!@#")
                  .body("data".getBytes())
                  .timeoutInSeconds(10)
                  .build());
    }

    @Test
    @DisplayName("no-arg constructor creates instance with defaults")
    void noArgConstructor() {
      QueryMessage msg = new QueryMessage();
      assertNotNull(msg);
      assertNull(msg.getChannel());
    }
  }
}
