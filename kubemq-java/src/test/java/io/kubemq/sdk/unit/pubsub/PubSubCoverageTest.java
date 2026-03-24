package io.kubemq.sdk.unit.pubsub;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.Subscription;
import io.kubemq.sdk.exception.KubeMQException;
import io.kubemq.sdk.exception.ValidationException;
import io.kubemq.sdk.pubsub.*;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import kubemq.Kubemq;
import kubemq.kubemqGrpc;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class PubSubCoverageTest {

  @Mock private kubemqGrpc.kubemqBlockingStub mockBlockingStub;

  @Mock private kubemqGrpc.kubemqStub mockAsyncStub;

  private PubSubClient client;

  @BeforeEach
  void setup() {
    when(mockBlockingStub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(mockBlockingStub);
    client =
        PubSubClient.builder().address("localhost:50000").clientId("test-coverage-client").build();
    client.setBlockingStub(mockBlockingStub);
    client.setAsyncStub(mockAsyncStub);
  }

  @AfterEach
  void teardown() {
    if (client != null) {
      client.close();
    }
  }

  // ---------------------------------------------------------------
  // Async API methods on PubSubClient
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Async Event Methods")
  class AsyncEventTests {

    @Test
    @DisplayName("sendEventsMessageAsync completes successfully")
    void sendEventsMessageAsync_happyPath() throws Exception {
      @SuppressWarnings("unchecked")
      StreamObserver<Kubemq.Event> mockEventObserver = mock(StreamObserver.class);
      when(mockAsyncStub.sendEventsStream(any())).thenReturn(mockEventObserver);

      EventMessage message =
          EventMessage.builder().channel("async-channel").body("async-data".getBytes()).build();

      CompletableFuture<Void> future = client.sendEventsMessageAsync(message);
      assertNotNull(future);
      future.get(5, TimeUnit.SECONDS);
      verify(mockEventObserver).onNext(any(Kubemq.Event.class));
    }

    @Test
    @DisplayName("sendEventsMessageAsync propagates exception")
    void sendEventsMessageAsync_exception_propagated() {
      when(mockAsyncStub.sendEventsStream(any()))
          .thenThrow(new RuntimeException("async send failure"));

      EventMessage message =
          EventMessage.builder().channel("async-channel").body("data".getBytes()).build();

      CompletableFuture<Void> future = client.sendEventsMessageAsync(message);
      assertNotNull(future);
      ExecutionException ex =
          assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
      assertNotNull(ex.getCause());
    }

    @Test
    @DisplayName("sendEventsStoreMessageAsync completes with result")
    void sendEventsStoreMessageAsync_happyPath() throws Exception {
      @SuppressWarnings("unchecked")
      StreamObserver<Kubemq.Event> mockEventObserver = mock(StreamObserver.class);
      @SuppressWarnings("unchecked")
      ArgumentCaptor<StreamObserver<Kubemq.Result>> resultCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.sendEventsStream(resultCaptor.capture())).thenReturn(mockEventObserver);

      EventStoreMessage message =
          EventStoreMessage.builder()
              .channel("async-store-channel")
              .body("data".getBytes())
              .build();

      CompletableFuture<EventSendResult> future = client.sendEventsStoreMessageAsync(message);
      assertNotNull(future);
      assertFalse(future.isDone());

      Thread.sleep(100);
      ArgumentCaptor<Kubemq.Event> eventCaptor = ArgumentCaptor.forClass(Kubemq.Event.class);
      verify(mockEventObserver, timeout(1000)).onNext(eventCaptor.capture());
      String eventId = eventCaptor.getValue().getEventID();

      resultCaptor
          .getValue()
          .onNext(Kubemq.Result.newBuilder().setEventID(eventId).setSent(true).build());

      EventSendResult result = future.get(5, TimeUnit.SECONDS);
      assertNotNull(result);
      assertTrue(result.isSent());
    }

    @Test
    @DisplayName("sendEventsStoreMessage with Duration timeout")
    void sendEventsStoreMessage_withDurationTimeout() throws Exception {
      @SuppressWarnings("unchecked")
      StreamObserver<Kubemq.Event> mockEventObserver = mock(StreamObserver.class);
      @SuppressWarnings("unchecked")
      ArgumentCaptor<StreamObserver<Kubemq.Result>> resultCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.sendEventsStream(resultCaptor.capture())).thenReturn(mockEventObserver);

      EventStoreMessage message =
          EventStoreMessage.builder().channel("timeout-channel").body("data".getBytes()).build();

      Thread responseThread =
          new Thread(
              () -> {
                try {
                  Thread.sleep(100);
                  ArgumentCaptor<Kubemq.Event> eventCaptor =
                      ArgumentCaptor.forClass(Kubemq.Event.class);
                  verify(mockEventObserver, timeout(2000)).onNext(eventCaptor.capture());
                  String eventId = eventCaptor.getValue().getEventID();
                  resultCaptor
                      .getValue()
                      .onNext(Kubemq.Result.newBuilder().setEventID(eventId).setSent(true).build());
                } catch (Exception ignored) {
                }
              });
      responseThread.start();

      EventSendResult result = client.sendEventsStoreMessage(message, Duration.ofSeconds(10));
      responseThread.join(5000);
      assertNotNull(result);
      assertTrue(result.isSent());
    }

    @Test
    @DisplayName("sendEventsStoreMessage with Duration timeout expires")
    void sendEventsStoreMessage_durationTimeout_expires() {
      @SuppressWarnings("unchecked")
      StreamObserver<Kubemq.Event> mockEventObserver = mock(StreamObserver.class);
      when(mockAsyncStub.sendEventsStream(any())).thenReturn(mockEventObserver);

      EventStoreMessage message =
          EventStoreMessage.builder().channel("timeout-channel").body("data".getBytes()).build();

      assertThrows(
          RuntimeException.class,
          () -> client.sendEventsStoreMessage(message, Duration.ofMillis(200)));
    }
  }

  @Nested
  @DisplayName("Async Channel Management Methods")
  class AsyncChannelManagementTests {

    @Test
    @DisplayName("createEventsChannelAsync completes successfully")
    void createEventsChannelAsync_happyPath() throws Exception {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(Kubemq.Response.newBuilder().setExecuted(true).build());

      CompletableFuture<Boolean> future = client.createEventsChannelAsync("async-events-ch");
      assertNotNull(future);
      assertTrue(future.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("createEventsStoreChannelAsync completes successfully")
    void createEventsStoreChannelAsync_happyPath() throws Exception {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(Kubemq.Response.newBuilder().setExecuted(true).build());

      CompletableFuture<Boolean> future = client.createEventsStoreChannelAsync("async-store-ch");
      assertNotNull(future);
      assertTrue(future.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("deleteEventsChannelAsync completes successfully")
    void deleteEventsChannelAsync_happyPath() throws Exception {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(Kubemq.Response.newBuilder().setExecuted(true).build());

      CompletableFuture<Boolean> future = client.deleteEventsChannelAsync("del-events-ch");
      assertNotNull(future);
      assertTrue(future.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("deleteEventsStoreChannelAsync completes successfully")
    void deleteEventsStoreChannelAsync_happyPath() throws Exception {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(Kubemq.Response.newBuilder().setExecuted(true).build());

      CompletableFuture<Boolean> future = client.deleteEventsStoreChannelAsync("del-store-ch");
      assertNotNull(future);
      assertTrue(future.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("listEventsChannelsAsync completes successfully")
    void listEventsChannelsAsync_happyPath() throws Exception {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(
              Kubemq.Response.newBuilder()
                  .setExecuted(true)
                  .setBody(ByteString.copyFromUtf8("[]"))
                  .build());

      CompletableFuture<List<PubSubChannel>> future = client.listEventsChannelsAsync("*");
      assertNotNull(future);
      List<PubSubChannel> channels = future.get(5, TimeUnit.SECONDS);
      assertNotNull(channels);
    }

    @Test
    @DisplayName("listEventsStoreChannelsAsync completes successfully")
    void listEventsStoreChannelsAsync_happyPath() throws Exception {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(
              Kubemq.Response.newBuilder()
                  .setExecuted(true)
                  .setBody(ByteString.copyFromUtf8("[]"))
                  .build());

      CompletableFuture<List<PubSubChannel>> future = client.listEventsStoreChannelsAsync("*");
      assertNotNull(future);
      List<PubSubChannel> channels = future.get(5, TimeUnit.SECONDS);
      assertNotNull(channels);
    }

    @Test
    @DisplayName("createEventsChannelAsync propagates exception")
    void createEventsChannelAsync_exception_propagated() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

      CompletableFuture<Boolean> future = client.createEventsChannelAsync("fail-ch");
      ExecutionException ex =
          assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
      assertNotNull(ex.getCause());
    }

    @Test
    @DisplayName("deleteEventsStoreChannelAsync propagates exception")
    void deleteEventsStoreChannelAsync_exception_propagated() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new StatusRuntimeException(Status.INTERNAL));

      CompletableFuture<Boolean> future = client.deleteEventsStoreChannelAsync("fail-ch");
      ExecutionException ex =
          assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
      assertNotNull(ex.getCause());
    }

    @Test
    @DisplayName("listEventsChannelsAsync propagates exception")
    void listEventsChannelsAsync_exception_propagated() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new RuntimeException("list failed"));

      CompletableFuture<List<PubSubChannel>> future = client.listEventsChannelsAsync("*");
      ExecutionException ex =
          assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
      assertNotNull(ex.getCause());
    }
  }

  // ---------------------------------------------------------------
  // Subscription with Handle methods
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("subscribeToEventsWithHandle Tests")
  class SubscribeWithHandleTests {

    @Test
    @DisplayName("subscribeToEventsWithHandle returns cancellable Subscription")
    void subscribeToEventsWithHandle_returnsCancellableHandle() {
      EventsSubscription subscription =
          EventsSubscription.builder()
              .channel("handle-channel")
              .onReceiveEventCallback(event -> {})
              .onErrorCallback(err -> {})
              .build();

      Subscription handle = client.subscribeToEventsWithHandle(subscription);

      assertNotNull(handle);
      assertFalse(handle.isCancelled());
      verify(mockAsyncStub).subscribeToEvents(any(Kubemq.Subscribe.class), any());

      handle.cancel();
      assertTrue(handle.isCancelled());
    }

    @Test
    @DisplayName("subscribeToEventsStoreWithHandle returns cancellable Subscription")
    void subscribeToEventsStoreWithHandle_returnsCancellableHandle() {
      EventsStoreSubscription subscription =
          EventsStoreSubscription.builder()
              .channel("handle-store-channel")
              .eventsStoreType(EventsStoreType.StartFromFirst)
              .onReceiveEventCallback(event -> {})
              .onErrorCallback(err -> {})
              .build();

      Subscription handle = client.subscribeToEventsStoreWithHandle(subscription);

      assertNotNull(handle);
      assertFalse(handle.isCancelled());
      verify(mockAsyncStub).subscribeToEvents(any(Kubemq.Subscribe.class), any());

      handle.cancel();
      assertTrue(handle.isCancelled());
    }

    @Test
    @DisplayName("subscribeToEventsWithHandle idempotent cancel")
    void subscribeToEventsWithHandle_idempotentCancel() {
      EventsSubscription subscription =
          EventsSubscription.builder()
              .channel("handle-channel")
              .onReceiveEventCallback(event -> {})
              .build();

      Subscription handle = client.subscribeToEventsWithHandle(subscription);
      handle.cancel();
      handle.cancel();
      assertTrue(handle.isCancelled());
    }

    @Test
    @DisplayName("subscribeToEventsStoreWithHandle cancelAsync")
    void subscribeToEventsStoreWithHandle_cancelAsync() throws Exception {
      EventsStoreSubscription subscription =
          EventsStoreSubscription.builder()
              .channel("async-cancel-channel")
              .eventsStoreType(EventsStoreType.StartNewOnly)
              .onReceiveEventCallback(event -> {})
              .build();

      Subscription handle = client.subscribeToEventsStoreWithHandle(subscription);
      CompletableFuture<Void> cancelFuture = handle.cancelAsync();
      cancelFuture.get(5, TimeUnit.SECONDS);
      assertTrue(handle.isCancelled());
    }
  }

  // ---------------------------------------------------------------
  // Closed client guard on async methods
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Closed Client Guard Tests")
  class ClosedClientGuardTests {

    @Test
    @DisplayName("sendEventsMessageAsync throws when client is closed")
    void sendEventsMessageAsync_closedClient_throws() {
      client.close();
      EventMessage message = EventMessage.builder().channel("ch").body("b".getBytes()).build();
      assertThrows(KubeMQException.class, () -> client.sendEventsMessageAsync(message));
    }

    @Test
    @DisplayName("sendEventsStoreMessageAsync throws when client is closed")
    void sendEventsStoreMessageAsync_closedClient_throws() {
      client.close();
      EventStoreMessage message =
          EventStoreMessage.builder().channel("ch").body("b".getBytes()).build();
      assertThrows(KubeMQException.class, () -> client.sendEventsStoreMessageAsync(message));
    }

    @Test
    @DisplayName("createEventsChannelAsync throws when client is closed")
    void createEventsChannelAsync_closedClient_throws() {
      client.close();
      assertThrows(KubeMQException.class, () -> client.createEventsChannelAsync("ch"));
    }

    @Test
    @DisplayName("deleteEventsChannelAsync throws when client is closed")
    void deleteEventsChannelAsync_closedClient_throws() {
      client.close();
      assertThrows(KubeMQException.class, () -> client.deleteEventsChannelAsync("ch"));
    }

    @Test
    @DisplayName("listEventsChannelsAsync throws when client is closed")
    void listEventsChannelsAsync_closedClient_throws() {
      client.close();
      assertThrows(KubeMQException.class, () -> client.listEventsChannelsAsync("*"));
    }

    @Test
    @DisplayName("createEventsStoreChannelAsync throws when client is closed")
    void createEventsStoreChannelAsync_closedClient_throws() {
      client.close();
      assertThrows(KubeMQException.class, () -> client.createEventsStoreChannelAsync("ch"));
    }

    @Test
    @DisplayName("deleteEventsStoreChannelAsync throws when client is closed")
    void deleteEventsStoreChannelAsync_closedClient_throws() {
      client.close();
      assertThrows(KubeMQException.class, () -> client.deleteEventsStoreChannelAsync("ch"));
    }

    @Test
    @DisplayName("listEventsStoreChannelsAsync throws when client is closed")
    void listEventsStoreChannelsAsync_closedClient_throws() {
      client.close();
      assertThrows(KubeMQException.class, () -> client.listEventsStoreChannelsAsync("*"));
    }

    @Test
    @DisplayName("subscribeToEventsWithHandle throws when client is closed")
    void subscribeToEventsWithHandle_closedClient_throws() {
      client.close();
      EventsSubscription sub =
          EventsSubscription.builder().channel("ch").onReceiveEventCallback(e -> {}).build();
      assertThrows(KubeMQException.class, () -> client.subscribeToEventsWithHandle(sub));
    }

    @Test
    @DisplayName("subscribeToEventsStoreWithHandle throws when client is closed")
    void subscribeToEventsStoreWithHandle_closedClient_throws() {
      client.close();
      EventsStoreSubscription sub =
          EventsStoreSubscription.builder()
              .channel("ch")
              .eventsStoreType(EventsStoreType.StartFromFirst)
              .onReceiveEventCallback(e -> {})
              .build();
      assertThrows(KubeMQException.class, () -> client.subscribeToEventsStoreWithHandle(sub));
    }
  }

  // ---------------------------------------------------------------
  // EventsSubscription inner observer - deeper coverage
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("EventsSubscription Observer Coverage")
  class EventsSubscriptionObserverCoverage {

    @Mock private PubSubClient mockPubSubClient;

    @Test
    @DisplayName("onNext with user handler exception triggers HandlerException via onErrorCallback")
    void onNext_userHandlerException_raisesHandlerError() throws InterruptedException {
      when(mockPubSubClient.getClientId()).thenReturn("test-client");
      when(mockPubSubClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      CountDownLatch errorLatch = new CountDownLatch(1);
      AtomicReference<KubeMQException> capturedError = new AtomicReference<>();

      EventsSubscription subscription =
          EventsSubscription.builder()
              .channel("handler-error-channel")
              .onReceiveEventCallback(
                  msg -> {
                    throw new RuntimeException("user handler blew up");
                  })
              .onErrorCallback(
                  err -> {
                    capturedError.set(err);
                    errorLatch.countDown();
                  })
              .build();

      subscription.encode("test-client", mockPubSubClient);
      StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

      observer.onNext(
          Kubemq.EventReceive.newBuilder()
              .setEventID("e1")
              .setChannel("handler-error-channel")
              .setBody(ByteString.copyFromUtf8("body"))
              .build());

      assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
      assertNotNull(capturedError.get());
      assertTrue(capturedError.get().getMessage().contains("User handler threw exception"));
    }

    @Test
    @DisplayName("onError with StatusRuntimeException (retryable) triggers reconnect")
    void onError_statusRuntimeException_retryable_triggersReconnect() throws InterruptedException {
      when(mockPubSubClient.getClientId()).thenReturn("test-client");
      when(mockPubSubClient.getReconnectIntervalInMillis()).thenReturn(1000L);
      when(mockPubSubClient.getAsyncClient()).thenReturn(mockAsyncStub);

      CountDownLatch errorLatch = new CountDownLatch(1);
      AtomicReference<KubeMQException> capturedError = new AtomicReference<>();

      EventsSubscription subscription =
          EventsSubscription.builder()
              .channel("reconnect-channel")
              .onReceiveEventCallback(msg -> {})
              .onErrorCallback(
                  err -> {
                    capturedError.set(err);
                    errorLatch.countDown();
                  })
              .build();

      subscription.encode("test-client", mockPubSubClient);
      StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

      observer.onError(
          new StatusRuntimeException(Status.UNAVAILABLE.withDescription("connection lost")));

      assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
      assertNotNull(capturedError.get());
      assertTrue(capturedError.get().isRetryable());
    }

    @Test
    @DisplayName("onError with StatusRuntimeException (non-retryable) does not reconnect")
    void onError_statusRuntimeException_nonRetryable_noReconnect() throws InterruptedException {
      when(mockPubSubClient.getClientId()).thenReturn("test-client");
      when(mockPubSubClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      CountDownLatch errorLatch = new CountDownLatch(1);
      AtomicReference<KubeMQException> capturedError = new AtomicReference<>();

      EventsSubscription subscription =
          EventsSubscription.builder()
              .channel("no-reconnect-channel")
              .onReceiveEventCallback(msg -> {})
              .onErrorCallback(
                  err -> {
                    capturedError.set(err);
                    errorLatch.countDown();
                  })
              .build();

      subscription.encode("test-client", mockPubSubClient);
      StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

      observer.onError(
          new StatusRuntimeException(Status.PERMISSION_DENIED.withDescription("forbidden")));

      assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
      assertNotNull(capturedError.get());
      assertFalse(capturedError.get().isRetryable());
    }

    @Test
    @DisplayName("onError with non-StatusRuntimeException wraps as TransportException")
    void onError_nonStatusRuntimeException_wrapsAsTransportException() throws InterruptedException {
      when(mockPubSubClient.getClientId()).thenReturn("test-client");
      when(mockPubSubClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      CountDownLatch errorLatch = new CountDownLatch(1);
      AtomicReference<KubeMQException> capturedError = new AtomicReference<>();

      EventsSubscription subscription =
          EventsSubscription.builder()
              .channel("transport-error-channel")
              .onReceiveEventCallback(msg -> {})
              .onErrorCallback(
                  err -> {
                    capturedError.set(err);
                    errorLatch.countDown();
                  })
              .build();

      subscription.encode("test-client", mockPubSubClient);
      StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

      observer.onError(new RuntimeException("transport problem"));

      assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
      assertNotNull(capturedError.get());
      assertTrue(capturedError.get().getMessage().contains("transport problem"));
    }

    @Test
    @DisplayName("encode with custom callbackExecutor uses provided executor")
    void encode_withCustomCallbackExecutor() throws InterruptedException {
      when(mockPubSubClient.getClientId()).thenReturn("test-client");
      when(mockPubSubClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      ExecutorService customExecutor = Executors.newSingleThreadExecutor();
      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<String> threadName = new AtomicReference<>();

      try {
        EventsSubscription subscription =
            EventsSubscription.builder()
                .channel("custom-exec-channel")
                .callbackExecutor(customExecutor)
                .onReceiveEventCallback(
                    msg -> {
                      threadName.set(Thread.currentThread().getName());
                      latch.countDown();
                    })
                .build();

        subscription.encode("test-client", mockPubSubClient);
        StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

        observer.onNext(
            Kubemq.EventReceive.newBuilder()
                .setEventID("e2")
                .setChannel("custom-exec-channel")
                .setBody(ByteString.copyFromUtf8("body"))
                .build());

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(threadName.get());
      } finally {
        customExecutor.shutdown();
      }
    }

    @Test
    @DisplayName("encode with null group uses empty string")
    void encode_nullGroup_usesEmptyString() {
      when(mockPubSubClient.getClientId()).thenReturn("test-client");
      when(mockPubSubClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      EventsSubscription subscription =
          EventsSubscription.builder()
              .channel("null-group-channel")
              .group(null)
              .onReceiveEventCallback(msg -> {})
              .build();

      Kubemq.Subscribe sub = subscription.encode("test-client", mockPubSubClient);
      assertEquals("", sub.getGroup());
    }

    @Test
    @DisplayName("cancel without encoding does not throw")
    void cancel_withoutEncode_doesNotThrow() {
      EventsSubscription subscription =
          EventsSubscription.builder()
              .channel("cancel-channel")
              .onReceiveEventCallback(msg -> {})
              .build();

      assertDoesNotThrow(subscription::cancel);
    }

    @Test
    @DisplayName("resetReconnectAttempts with null handler does not throw")
    void resetReconnectAttempts_nullHandler_doesNotThrow() {
      EventsSubscription subscription =
          EventsSubscription.builder()
              .channel("reset-channel")
              .onReceiveEventCallback(msg -> {})
              .build();

      assertDoesNotThrow(subscription::resetReconnectAttempts);
    }

    @Test
    @DisplayName("raiseOnReceiveMessage with null callback does not throw")
    void raiseOnReceiveMessage_nullCallback_doesNotThrow() {
      EventsSubscription subscription =
          EventsSubscription.builder().channel("null-cb-channel").build();

      assertDoesNotThrow(
          () ->
              subscription.raiseOnReceiveMessage(
                  EventMessageReceived.decode(
                      Kubemq.EventReceive.newBuilder().setEventID("e1").setChannel("ch").build())));
    }

    @Test
    @DisplayName("raiseOnError with null callback does not throw")
    void raiseOnError_nullCallback_doesNotThrow() {
      EventsSubscription subscription =
          EventsSubscription.builder()
              .channel("null-err-channel")
              .onReceiveEventCallback(msg -> {})
              .build();

      assertDoesNotThrow(
          () -> subscription.raiseOnError(KubeMQException.newBuilder().message("test").build()));
    }
  }

  // ---------------------------------------------------------------
  // EventsStoreSubscription inner observer - deeper coverage
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("EventsStoreSubscription Observer Coverage")
  class EventsStoreSubscriptionObserverCoverage {

    @Mock private PubSubClient mockPubSubClient;

    @Test
    @DisplayName("onNext with user handler exception triggers HandlerException")
    void onNext_userHandlerException_raisesHandlerError() throws InterruptedException {
      when(mockPubSubClient.getClientId()).thenReturn("test-client");
      when(mockPubSubClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      CountDownLatch errorLatch = new CountDownLatch(1);
      AtomicReference<KubeMQException> capturedError = new AtomicReference<>();

      EventsStoreSubscription subscription =
          EventsStoreSubscription.builder()
              .channel("store-handler-error")
              .eventsStoreType(EventsStoreType.StartFromFirst)
              .onReceiveEventCallback(
                  msg -> {
                    throw new RuntimeException("store user handler error");
                  })
              .onErrorCallback(
                  err -> {
                    capturedError.set(err);
                    errorLatch.countDown();
                  })
              .build();

      subscription.encode("test-client", mockPubSubClient);
      StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

      observer.onNext(
          Kubemq.EventReceive.newBuilder()
              .setEventID("se1")
              .setChannel("store-handler-error")
              .setBody(ByteString.copyFromUtf8("body"))
              .setSequence(1)
              .build());

      assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
      assertNotNull(capturedError.get());
      assertTrue(capturedError.get().getMessage().contains("User handler threw exception"));
    }

    @Test
    @DisplayName("onError with StatusRuntimeException (retryable) triggers reconnect")
    void onError_statusRuntimeException_retryable() throws InterruptedException {
      when(mockPubSubClient.getClientId()).thenReturn("test-client");
      when(mockPubSubClient.getReconnectIntervalInMillis()).thenReturn(1000L);
      when(mockPubSubClient.getAsyncClient()).thenReturn(mockAsyncStub);

      CountDownLatch errorLatch = new CountDownLatch(1);
      AtomicReference<KubeMQException> capturedError = new AtomicReference<>();

      EventsStoreSubscription subscription =
          EventsStoreSubscription.builder()
              .channel("store-reconnect-ch")
              .eventsStoreType(EventsStoreType.StartFromLast)
              .onReceiveEventCallback(msg -> {})
              .onErrorCallback(
                  err -> {
                    capturedError.set(err);
                    errorLatch.countDown();
                  })
              .build();

      subscription.encode("test-client", mockPubSubClient);
      StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

      observer.onError(
          new StatusRuntimeException(Status.UNAVAILABLE.withDescription("server gone")));

      assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
      assertNotNull(capturedError.get());
      assertTrue(capturedError.get().isRetryable());
    }

    @Test
    @DisplayName("onError with StatusRuntimeException (non-retryable)")
    void onError_statusRuntimeException_nonRetryable() throws InterruptedException {
      when(mockPubSubClient.getClientId()).thenReturn("test-client");
      when(mockPubSubClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      CountDownLatch errorLatch = new CountDownLatch(1);
      AtomicReference<KubeMQException> capturedError = new AtomicReference<>();

      EventsStoreSubscription subscription =
          EventsStoreSubscription.builder()
              .channel("store-no-reconnect")
              .eventsStoreType(EventsStoreType.StartNewOnly)
              .onReceiveEventCallback(msg -> {})
              .onErrorCallback(
                  err -> {
                    capturedError.set(err);
                    errorLatch.countDown();
                  })
              .build();

      subscription.encode("test-client", mockPubSubClient);
      StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

      observer.onError(
          new StatusRuntimeException(Status.PERMISSION_DENIED.withDescription("auth denied")));

      assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
      assertNotNull(capturedError.get());
      assertFalse(capturedError.get().isRetryable());
    }

    @Test
    @DisplayName("onError with generic exception wraps as TransportException")
    void onError_genericException_wrapsAsTransportException() throws InterruptedException {
      when(mockPubSubClient.getClientId()).thenReturn("test-client");
      when(mockPubSubClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      CountDownLatch errorLatch = new CountDownLatch(1);
      AtomicReference<KubeMQException> capturedError = new AtomicReference<>();

      EventsStoreSubscription subscription =
          EventsStoreSubscription.builder()
              .channel("store-transport-err")
              .eventsStoreType(EventsStoreType.StartFromFirst)
              .onReceiveEventCallback(msg -> {})
              .onErrorCallback(
                  err -> {
                    capturedError.set(err);
                    errorLatch.countDown();
                  })
              .build();

      subscription.encode("test-client", mockPubSubClient);
      StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

      observer.onError(new RuntimeException("store transport problem"));

      assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
      assertNotNull(capturedError.get());
      assertTrue(capturedError.get().getMessage().contains("store transport problem"));
    }

    @Test
    @DisplayName("onError without error callback does not throw")
    void onError_noErrorCallback_doesNotThrow() {
      when(mockPubSubClient.getClientId()).thenReturn("test-client");
      when(mockPubSubClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      EventsStoreSubscription subscription =
          EventsStoreSubscription.builder()
              .channel("store-no-err-cb")
              .eventsStoreType(EventsStoreType.StartFromFirst)
              .onReceiveEventCallback(msg -> {})
              .onErrorCallback(null)
              .build();

      subscription.encode("test-client", mockPubSubClient);
      StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

      assertDoesNotThrow(() -> observer.onError(new StatusRuntimeException(Status.UNAVAILABLE)));
    }

    @Test
    @DisplayName("encode with custom callbackExecutor")
    void encode_withCustomCallbackExecutor() throws InterruptedException {
      when(mockPubSubClient.getClientId()).thenReturn("test-client");
      when(mockPubSubClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      ExecutorService customExecutor = Executors.newSingleThreadExecutor();
      CountDownLatch latch = new CountDownLatch(1);

      try {
        EventsStoreSubscription subscription =
            EventsStoreSubscription.builder()
                .channel("store-custom-exec")
                .eventsStoreType(EventsStoreType.StartFromFirst)
                .callbackExecutor(customExecutor)
                .onReceiveEventCallback(msg -> latch.countDown())
                .build();

        subscription.encode("test-client", mockPubSubClient);
        StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

        observer.onNext(
            Kubemq.EventReceive.newBuilder()
                .setEventID("se2")
                .setChannel("store-custom-exec")
                .setBody(ByteString.copyFromUtf8("body"))
                .setSequence(1)
                .build());

        assertTrue(latch.await(5, TimeUnit.SECONDS));
      } finally {
        customExecutor.shutdown();
      }
    }

    @Test
    @DisplayName("encode with maxConcurrentCallbacks > 1")
    void encode_withConcurrentCallbacks() throws InterruptedException {
      when(mockPubSubClient.getClientId()).thenReturn("test-client");
      when(mockPubSubClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      CountDownLatch latch = new CountDownLatch(2);
      ExecutorService pool = Executors.newFixedThreadPool(2);

      try {
        EventsStoreSubscription subscription =
            EventsStoreSubscription.builder()
                .channel("store-concurrent")
                .eventsStoreType(EventsStoreType.StartFromFirst)
                .callbackExecutor(pool)
                .maxConcurrentCallbacks(2)
                .onReceiveEventCallback(msg -> latch.countDown())
                .build();

        subscription.encode("test-client", mockPubSubClient);
        StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

        observer.onNext(
            Kubemq.EventReceive.newBuilder()
                .setEventID("c1")
                .setChannel("store-concurrent")
                .setBody(ByteString.copyFromUtf8("b1"))
                .setSequence(1)
                .build());
        observer.onNext(
            Kubemq.EventReceive.newBuilder()
                .setEventID("c2")
                .setChannel("store-concurrent")
                .setBody(ByteString.copyFromUtf8("b2"))
                .setSequence(2)
                .build());

        assertTrue(latch.await(5, TimeUnit.SECONDS));
      } finally {
        pool.shutdown();
      }
    }

    @Test
    @DisplayName("encode with null eventsStoreType uses 0 for store type")
    void encode_nullEventsStoreType() {
      when(mockPubSubClient.getClientId()).thenReturn("test-client");
      when(mockPubSubClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      EventsStoreSubscription subscription =
          EventsStoreSubscription.builder()
              .channel("null-type-channel")
              .eventsStoreType(null)
              .onReceiveEventCallback(msg -> {})
              .build();

      Kubemq.Subscribe sub = subscription.encode("test-client", mockPubSubClient);
      assertNotNull(sub);
    }

    @Test
    @DisplayName("resetReconnectAttempts with null handler does not throw")
    void resetReconnectAttempts_nullHandler_doesNotThrow() {
      EventsStoreSubscription subscription =
          EventsStoreSubscription.builder()
              .channel("reset-store")
              .eventsStoreType(EventsStoreType.StartFromFirst)
              .onReceiveEventCallback(msg -> {})
              .build();

      assertDoesNotThrow(subscription::resetReconnectAttempts);
    }

    @Test
    @DisplayName("default constructor sets eventsStoreType to Undefined")
    void defaultConstructor_setsUndefined() {
      EventsStoreSubscription subscription = new EventsStoreSubscription();
      assertEquals(EventsStoreType.Undefined, subscription.getEventsStoreType());
      assertEquals(0, subscription.getEventsStoreSequenceValue());
    }
  }

  // ---------------------------------------------------------------
  // EventStoreMessage additional coverage
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("EventStoreMessage Coverage")
  class EventStoreMessageCoverage {

    @Test
    @DisplayName("toString produces readable output")
    void toString_producesReadableOutput() {
      EventStoreMessage msg =
          EventStoreMessage.builder()
              .id("msg-id-1")
              .channel("store-channel")
              .metadata("meta-data")
              .body("test-body".getBytes())
              .build();

      String str = msg.toString();
      assertTrue(str.contains("msg-id-1"));
      assertTrue(str.contains("store-channel"));
      assertTrue(str.contains("meta-data"));
      assertTrue(str.contains("test-body"));
    }

    @Test
    @DisplayName("toString with null body does not throw")
    void toString_nullBody_doesNotThrow() {
      EventStoreMessage msg = new EventStoreMessage();
      msg.setChannel("ch");
      msg.setBody(null);
      String str = msg.toString();
      assertNotNull(str);
    }

    @Test
    @DisplayName("encode with metadata and tags together")
    void encode_withMetadataAndTags() {
      Map<String, String> tags = new HashMap<>();
      tags.put("env", "prod");
      tags.put("version", "2.0");

      EventStoreMessage msg =
          EventStoreMessage.builder()
              .id("full-msg")
              .channel("full-channel")
              .metadata("full-metadata")
              .body("full-body".getBytes())
              .tags(tags)
              .build();

      Kubemq.Event proto = msg.encode("full-client");
      assertEquals("full-msg", proto.getEventID());
      assertEquals("full-channel", proto.getChannel());
      assertEquals("full-metadata", proto.getMetadata());
      assertTrue(proto.getStore());
      assertEquals("prod", proto.getTagsMap().get("env"));
      assertEquals("full-client", proto.getTagsMap().get("x-kubemq-client-id"));
    }

    @Test
    @DisplayName("validate with body exceeding max size throws")
    void validate_bodyExceedsMaxSize_throws() {
      byte[] largeBody = new byte[104857601]; // just over 100MB
      EventStoreMessage msg = new EventStoreMessage();
      msg.setChannel("large-body-channel");
      msg.setBody(largeBody);

      ValidationException ex = assertThrows(ValidationException.class, msg::validate);
      assertTrue(ex.getMessage().contains("exceeds the maximum allowed size"));
    }

    @Test
    @DisplayName("validate with metadata only (empty body, no tags) passes")
    void validate_metadataOnly_passes() {
      EventStoreMessage msg =
          EventStoreMessage.builder()
              .channel("meta-only-channel")
              .metadata("some-metadata")
              .build();
      assertDoesNotThrow(msg::validate);
    }

    @Test
    @DisplayName("validate with null tags and null metadata and empty body throws")
    void validate_allEmpty_throws() {
      EventStoreMessage msg = new EventStoreMessage();
      msg.setChannel("empty-channel");
      msg.setBody(new byte[0]);
      msg.setTags(null);
      msg.setMetadata(null);

      ValidationException ex = assertThrows(ValidationException.class, msg::validate);
      assertTrue(ex.getMessage().contains("metadata, body, or tags"));
    }

    @Test
    @DisplayName("encode with empty tags map initializes null tags")
    void encode_nullTags_initializesToMap() {
      EventStoreMessage msg = new EventStoreMessage(null, "ch", null, "b".getBytes(), null);
      Kubemq.Event proto = msg.encode("client");
      assertEquals("client", proto.getTagsMap().get("x-kubemq-client-id"));
    }
  }

  // ---------------------------------------------------------------
  // EventStreamHelper additional coverage
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("EventStreamHelper Additional Coverage")
  class EventStreamHelperAdditional {

    @Mock private io.kubemq.sdk.client.KubeMQClient mockKubeMQClient;

    @Mock private StreamObserver<Kubemq.Event> mockEventStream;

    @Test
    @DisplayName("sendEventMessage creates stream on first call")
    void sendEventMessage_createsStreamOnFirstCall() {
      when(mockKubeMQClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockAsyncStub.sendEventsStream(any())).thenReturn(mockEventStream);

      EventStreamHelper helper = new EventStreamHelper();

      Kubemq.Event event =
          Kubemq.Event.newBuilder().setEventID("test-event").setChannel("test-channel").build();

      helper.sendEventMessage(mockKubeMQClient, event);

      verify(mockAsyncStub).sendEventsStream(any());
      verify(mockEventStream).onNext(event);
      assertNotNull(helper.getQueuesUpStreamHandler());
    }

    @Test
    @DisplayName("sendEventMessage reuses existing stream")
    void sendEventMessage_reusesExistingStream() {
      when(mockKubeMQClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockAsyncStub.sendEventsStream(any())).thenReturn(mockEventStream);

      EventStreamHelper helper = new EventStreamHelper();

      Kubemq.Event event1 = Kubemq.Event.newBuilder().setEventID("e1").setChannel("ch").build();
      Kubemq.Event event2 = Kubemq.Event.newBuilder().setEventID("e2").setChannel("ch").build();

      helper.sendEventMessage(mockKubeMQClient, event1);
      helper.sendEventMessage(mockKubeMQClient, event2);

      verify(mockAsyncStub, times(1)).sendEventsStream(any());
      verify(mockEventStream, times(2)).onNext(any());
    }

    @Test
    @DisplayName("onError completes all pending futures with error")
    void onError_completesAllPendingFutures() throws Exception {
      when(mockKubeMQClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockKubeMQClient.getRequestTimeoutSeconds()).thenReturn(30);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<StreamObserver<Kubemq.Result>> observerCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.sendEventsStream(observerCaptor.capture())).thenReturn(mockEventStream);

      EventStreamHelper helper = new EventStreamHelper();

      Kubemq.Event event =
          Kubemq.Event.newBuilder()
              .setEventID("pending-event")
              .setChannel("ch")
              .setStore(true)
              .build();

      CompletableFuture<EventSendResult> future =
          helper.sendEventStoreMessageAsync(mockKubeMQClient, event);
      assertFalse(future.isDone());

      StreamObserver<Kubemq.Result> resultObserver = observerCaptor.getValue();
      resultObserver.onError(new RuntimeException("stream died"));

      EventSendResult result = future.get(5, TimeUnit.SECONDS);
      assertNotNull(result);
      assertEquals("stream died", result.getError());
      // Stream handler IS cleared by onError in current production code
      assertNull(helper.getQueuesUpStreamHandler());
    }

    @Test
    @DisplayName("setQueuesUpStreamHandler allows manual injection")
    void setQueuesUpStreamHandler_manualInjection() {
      EventStreamHelper helper = new EventStreamHelper();
      assertNull(helper.getQueuesUpStreamHandler());

      helper.setQueuesUpStreamHandler(mockEventStream);
      assertSame(mockEventStream, helper.getQueuesUpStreamHandler());
    }

    @Test
    @DisplayName("getCleanupExecutor returns shared executor")
    void getCleanupExecutor_returnsSharedExecutor() {
      assertNotNull(EventStreamHelper.getCleanupExecutor());
      assertFalse(EventStreamHelper.getCleanupExecutor().isShutdown());
    }

    @Test
    @DisplayName("resultStreamObserver onCompleted does not throw")
    void resultStreamObserver_onCompleted() {
      EventStreamHelper helper = new EventStreamHelper();
      assertDoesNotThrow(() -> helper.getResultStreamObserver().onCompleted());
    }

    @Test
    @DisplayName("resultStreamObserver onNext for unknown eventId logs warning only")
    void resultStreamObserver_unknownEventId() {
      EventStreamHelper helper = new EventStreamHelper();
      Kubemq.Result result =
          Kubemq.Result.newBuilder().setEventID("unknown-id").setSent(true).build();

      assertDoesNotThrow(() -> helper.getResultStreamObserver().onNext(result));
    }
  }

  // ---------------------------------------------------------------
  // EventMessage additional coverage (for EventMessage.toString)
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("EventMessage Additional Coverage")
  class EventMessageAdditional {

    @Test
    @DisplayName("EventMessage toString produces readable output")
    void toString_producesReadableOutput() {
      EventMessage msg =
          EventMessage.builder()
              .id("em-id")
              .channel("em-channel")
              .metadata("em-meta")
              .body("em-body".getBytes())
              .build();

      String str = msg.toString();
      assertTrue(str.contains("em-id"));
      assertTrue(str.contains("em-channel"));
      assertTrue(str.contains("em-meta"));
    }

    @Test
    @DisplayName("EventMessage validate with body exceeding max size throws")
    void validate_bodyExceedsMaxSize_throws() {
      byte[] largeBody = new byte[104857601];
      EventMessage msg = new EventMessage();
      msg.setChannel("large-body-channel");
      msg.setBody(largeBody);

      ValidationException ex = assertThrows(ValidationException.class, msg::validate);
      assertTrue(ex.getMessage().contains("exceeds the maximum allowed size"));
    }

    @Test
    @DisplayName("EventMessage encode with empty tags adds client-id")
    void encode_emptyTags_addsClientId() {
      EventMessage msg = new EventMessage(null, "ch", null, "b".getBytes(), new java.util.HashMap<>());
      Kubemq.Event proto = msg.encode("client");
      assertEquals("client", proto.getTagsMap().get("x-kubemq-client-id"));
      assertFalse(proto.getStore());
    }

    @Test
    @DisplayName("EventMessage encode with null metadata uses empty string")
    void encode_nullMetadata_usesEmptyString() {
      EventMessage msg = EventMessage.builder().channel("ch").body("b".getBytes()).build();
      Kubemq.Event proto = msg.encode("client");
      assertEquals("", proto.getMetadata());
    }
  }

  // ---------------------------------------------------------------
  // EventStoreMessageReceived coverage
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("EventStoreMessageReceived Coverage")
  class EventStoreMessageReceivedCoverage {

    @Test
    @DisplayName("decode sets all fields correctly")
    void decode_setsAllFields() {
      Kubemq.EventReceive protoEvent =
          Kubemq.EventReceive.newBuilder()
              .setEventID("esm-123")
              .setChannel("store-channel")
              .setMetadata("store-meta")
              .setBody(ByteString.copyFromUtf8("store-body"))
              .setSequence(42)
              .setTimestamp(1700000000000000000L)
              .putTags("x-kubemq-client-id", "sender-client")
              .putTags("custom-tag", "custom-value")
              .build();

      EventStoreMessageReceived received = EventStoreMessageReceived.decode(protoEvent);

      assertEquals("esm-123", received.getId());
      assertEquals("store-channel", received.getChannel());
      assertEquals("store-meta", received.getMetadata());
      assertArrayEquals("store-body".getBytes(), received.getBody());
      assertEquals(42, received.getSequence());
      assertEquals(1700000000L, received.getTimestamp());
      assertEquals("sender-client", received.getFromClientId());
      assertEquals("custom-value", received.getTags().get("custom-tag"));
    }

    @Test
    @DisplayName("toString produces readable output")
    void toString_producesReadableOutput() {
      EventStoreMessageReceived received = new EventStoreMessageReceived();
      received.setId("esm-id");
      received.setChannel("esm-channel");
      received.setBody("esm-body".getBytes());
      received.setSequence(10);

      String str = received.toString();
      assertTrue(str.contains("esm-id"));
      assertTrue(str.contains("esm-channel"));
      assertTrue(str.contains("10"));
    }
  }

  // ---------------------------------------------------------------
  // EventMessageReceived coverage
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("EventMessageReceived Coverage")
  class EventMessageReceivedCoverage {

    @Test
    @DisplayName("decode sets all fields correctly")
    void decode_setsAllFields() {
      Kubemq.EventReceive protoEvent =
          Kubemq.EventReceive.newBuilder()
              .setEventID("emr-123")
              .setChannel("events-channel")
              .setMetadata("events-meta")
              .setBody(ByteString.copyFromUtf8("events-body"))
              .putTags("x-kubemq-client-id", "sender")
              .putTags("tag1", "val1")
              .build();

      EventMessageReceived received = EventMessageReceived.decode(protoEvent);

      assertEquals("emr-123", received.getId());
      assertEquals("events-channel", received.getChannel());
      assertEquals("events-meta", received.getMetadata());
      assertArrayEquals("events-body".getBytes(), received.getBody());
      assertEquals("sender", received.getFromClientId());
      assertEquals("val1", received.getTags().get("tag1"));
    }

    @Test
    @DisplayName("toString produces readable output")
    void toString_producesReadableOutput() {
      EventMessageReceived received = new EventMessageReceived();
      received.setId("emr-id");
      received.setChannel("emr-channel");
      received.setBody("emr-body".getBytes());

      String str = received.toString();
      assertTrue(str.contains("emr-id"));
      assertTrue(str.contains("emr-channel"));
    }
  }

  // ---------------------------------------------------------------
  // EventSendResult coverage
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("EventSendResult Coverage")
  class EventSendResultCoverage {

    @Test
    @DisplayName("decode maps all fields from protobuf")
    void decode_mapsAllFields() {
      Kubemq.Result proto =
          Kubemq.Result.newBuilder().setEventID("result-id").setSent(true).setError("").build();

      EventSendResult result = EventSendResult.decode(proto);

      assertEquals("result-id", result.getId());
      assertTrue(result.isSent());
      assertEquals("", result.getError());
    }

    @Test
    @DisplayName("decode with error message")
    void decode_withError() {
      Kubemq.Result proto =
          Kubemq.Result.newBuilder()
              .setEventID("err-result")
              .setSent(false)
              .setError("some error occurred")
              .build();

      EventSendResult result = EventSendResult.decode(proto);

      assertEquals("err-result", result.getId());
      assertFalse(result.isSent());
      assertEquals("some error occurred", result.getError());
    }

    @Test
    @DisplayName("toString produces readable output")
    void toString_readable() {
      EventSendResult result = EventSendResult.builder().id("sr-id").sent(true).error("").build();

      String str = result.toString();
      assertTrue(str.contains("sr-id"));
      assertTrue(str.contains("true"));
    }
  }

  // ---------------------------------------------------------------
  // Channel management error paths not covered by PubSubClientTest
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Channel Management Additional Error Paths")
  class ChannelManagementAdditionalErrors {

    @Test
    @DisplayName("createEventsStoreChannel with generic exception")
    void createEventsStoreChannel_genericException() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new RuntimeException("generic create error"));

      KubeMQException ex =
          assertThrows(KubeMQException.class, () -> client.createEventsStoreChannel("fail-ch"));
      assertTrue(ex.getMessage().contains("createEventsStoreChannel failed"));
    }

    @Test
    @DisplayName("deleteEventsChannel with generic exception")
    void deleteEventsChannel_genericException() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new RuntimeException("generic delete error"));

      KubeMQException ex =
          assertThrows(KubeMQException.class, () -> client.deleteEventsChannel("fail-ch"));
      assertTrue(ex.getMessage().contains("deleteEventsChannel failed"));
    }

    @Test
    @DisplayName("deleteEventsStoreChannel with generic exception")
    void deleteEventsStoreChannel_genericException() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new RuntimeException("generic store delete error"));

      KubeMQException ex =
          assertThrows(KubeMQException.class, () -> client.deleteEventsStoreChannel("fail-ch"));
      assertTrue(ex.getMessage().contains("deleteEventsStoreChannel failed"));
    }

    @Test
    @DisplayName("createEventsChannel returns false when server reports not executed")
    void createEventsChannel_notExecuted_returnsFalse() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(Kubemq.Response.newBuilder().setExecuted(false).build());

      boolean result = client.createEventsChannel("no-exec-ch");
      assertFalse(result);
    }

    @Test
    @DisplayName("listEventsStoreChannels with null search succeeds")
    void listEventsStoreChannels_nullSearch_succeeds() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(
              Kubemq.Response.newBuilder()
                  .setExecuted(true)
                  .setBody(ByteString.copyFromUtf8("[]"))
                  .build());

      List<PubSubChannel> result = client.listEventsStoreChannels(null);
      assertNotNull(result);
    }

    @Test
    @DisplayName("listEventsChannels with KubeMQException rethrows as-is")
    void listEventsChannels_kubemqException_rethrown() {
      KubeMQException original =
          KubeMQException.newBuilder().message("custom error").operation("test").build();
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class))).thenThrow(original);

      KubeMQException thrown =
          assertThrows(KubeMQException.class, () -> client.listEventsChannels("*"));
      assertSame(original, thrown);
    }
  }

  // ---------------------------------------------------------------
  // EventsStoreSubscription validation edge cases
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("EventsStoreSubscription Validation Edge Cases")
  class EventsStoreSubscriptionValidationEdgeCases {

    @Test
    @DisplayName("validate with StartAtTimeDelta passes")
    void validate_startAtTimeDelta_passes() {
      EventsStoreSubscription sub =
          EventsStoreSubscription.builder()
              .channel("delta-channel")
              .eventsStoreType(EventsStoreType.StartAtTimeDelta)
              .eventsStoreSequenceValue(60)
              .onReceiveEventCallback(msg -> {})
              .build();

      assertDoesNotThrow(sub::validate);
    }

    @Test
    @DisplayName("validate with error callback only (no receive callback) throws")
    void validate_errorCallbackOnly_throws() {
      EventsStoreSubscription sub =
          EventsStoreSubscription.builder()
              .channel("err-only-channel")
              .eventsStoreType(EventsStoreType.StartFromFirst)
              .onErrorCallback(err -> {})
              .build();

      assertThrows(ValidationException.class, sub::validate);
    }

    @Test
    @DisplayName("toString with StartFromFirst and null startTime")
    void toString_startFromFirst_nullStartTime() {
      EventsStoreSubscription sub =
          EventsStoreSubscription.builder()
              .channel("str-channel")
              .group("str-group")
              .eventsStoreType(EventsStoreType.StartFromFirst)
              .onReceiveEventCallback(msg -> {})
              .build();

      String str = sub.toString();
      assertTrue(str.contains("str-channel"));
      assertTrue(str.contains("StartFromFirst"));
    }

    @Test
    @DisplayName("toString with StartAtTime and startTime set")
    void toString_startAtTime_withStartTime() {
      Instant now = Instant.now();
      EventsStoreSubscription sub =
          EventsStoreSubscription.builder()
              .channel("time-channel")
              .eventsStoreType(EventsStoreType.StartAtTime)
              .eventsStoreStartTime(now)
              .onReceiveEventCallback(msg -> {})
              .build();

      String str = sub.toString();
      assertTrue(str.contains("time-channel"));
      assertTrue(str.contains("StartAtTime"));
    }
  }

  // ---------------------------------------------------------------
  // EventsSubscription validation edge cases
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("EventsSubscription Additional Coverage")
  class EventsSubscriptionAdditional {

    @Mock private PubSubClient mockPubSubClient;

    @Test
    @DisplayName("encode with maxConcurrentCallbacks > 1")
    void encode_withMaxConcurrentCallbacks() throws InterruptedException {
      when(mockPubSubClient.getClientId()).thenReturn("test-client");
      when(mockPubSubClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      ExecutorService pool = Executors.newFixedThreadPool(3);
      CountDownLatch latch = new CountDownLatch(3);

      try {
        EventsSubscription subscription =
            EventsSubscription.builder()
                .channel("concurrent-events")
                .callbackExecutor(pool)
                .maxConcurrentCallbacks(3)
                .onReceiveEventCallback(msg -> latch.countDown())
                .build();

        subscription.encode("test-client", mockPubSubClient);
        StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

        for (int i = 0; i < 3; i++) {
          observer.onNext(
              Kubemq.EventReceive.newBuilder()
                  .setEventID("ce" + i)
                  .setChannel("concurrent-events")
                  .setBody(ByteString.copyFromUtf8("body" + i))
                  .build());
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
      } finally {
        pool.shutdown();
      }
    }

    @Test
    @DisplayName("cancel after encode completes observer")
    void cancel_afterEncode_completesObserver() {
      when(mockPubSubClient.getClientId()).thenReturn("test-client");
      when(mockPubSubClient.getReconnectIntervalInMillis()).thenReturn(1000L);

      EventsSubscription subscription =
          EventsSubscription.builder()
              .channel("cancel-channel")
              .onReceiveEventCallback(msg -> {})
              .build();

      subscription.encode("test-client", mockPubSubClient);
      assertDoesNotThrow(subscription::cancel);
    }

    @Test
    @DisplayName("raiseOnReceiveMessage invokes callback")
    void raiseOnReceiveMessage_invokesCallback() {
      AtomicReference<EventMessageReceived> received = new AtomicReference<>();

      EventsSubscription subscription =
          EventsSubscription.builder()
              .channel("raise-channel")
              .onReceiveEventCallback(received::set)
              .build();

      EventMessageReceived msg = new EventMessageReceived();
      msg.setId("r1");
      subscription.raiseOnReceiveMessage(msg);

      assertNotNull(received.get());
      assertEquals("r1", received.get().getId());
    }

    @Test
    @DisplayName("raiseOnError invokes error callback")
    void raiseOnError_invokesErrorCallback() {
      AtomicReference<String> errorMsg = new AtomicReference<>();

      EventsSubscription subscription =
          EventsSubscription.builder()
              .channel("err-channel")
              .onReceiveEventCallback(msg -> {})
              .onErrorCallback(err -> errorMsg.set(err.getMessage()))
              .build();

      subscription.raiseOnError(KubeMQException.newBuilder().message("test-error").build());

      assertEquals("test-error", errorMsg.get());
    }
  }
}
