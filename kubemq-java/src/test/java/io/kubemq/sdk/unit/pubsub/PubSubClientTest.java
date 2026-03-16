package io.kubemq.sdk.unit.pubsub;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.exception.KubeMQException;
import io.kubemq.sdk.pubsub.*;
import java.util.concurrent.TimeUnit;
import kubemq.Kubemq;
import kubemq.kubemqGrpc;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class PubSubClientTest {

  @Mock private kubemqGrpc.kubemqBlockingStub mockBlockingStub;

  @Mock private kubemqGrpc.kubemqStub mockAsyncStub;

  private PubSubClient client;

  @BeforeEach
  void setup() {
    client =
        PubSubClient.builder().address("localhost:50000").clientId("test-pubsub-client").build();
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
  @DisplayName("sendEventsMessage Tests")
  class SendEventsMessageTests {

    @Test
    @DisplayName("Happy path: sendEventMessage succeeds")
    void sendEventsMessage_happyPath_succeeds() {
      @SuppressWarnings("unchecked")
      StreamObserver<Kubemq.Event> mockEventObserver = mock(StreamObserver.class);
      when(mockAsyncStub.sendEventsStream(any())).thenReturn(mockEventObserver);

      EventMessage message =
          EventMessage.builder().channel("test-channel").body("hello".getBytes()).build();

      assertDoesNotThrow(() -> client.sendEventsMessage(message));
      verify(mockEventObserver).onNext(any(Kubemq.Event.class));
    }

    @Test
    @DisplayName("StatusRuntimeException is mapped via GrpcErrorMapper")
    void sendEventsMessage_statusRuntimeException_mapped() {
      when(mockAsyncStub.sendEventsStream(any()))
          .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("server down")));

      EventMessage message =
          EventMessage.builder().channel("test-channel").body("hello".getBytes()).build();

      KubeMQException ex =
          assertThrows(KubeMQException.class, () -> client.sendEventsMessage(message));
      assertTrue(ex.isRetryable());
    }

    @Test
    @DisplayName("Generic exception is wrapped in KubeMQException")
    void sendEventsMessage_genericException_wrapped() {
      when(mockAsyncStub.sendEventsStream(any()))
          .thenThrow(new RuntimeException("unexpected error"));

      EventMessage message =
          EventMessage.builder().channel("test-channel").body("hello".getBytes()).build();

      KubeMQException ex =
          assertThrows(KubeMQException.class, () -> client.sendEventsMessage(message));
      assertTrue(ex.getMessage().contains("sendEventsMessage failed"));
      assertFalse(ex.isRetryable());
    }
  }

  @Nested
  @DisplayName("sendEventsStoreMessage Tests")
  class SendEventsStoreMessageTests {

    @Test
    @DisplayName("Happy path: sendEventsStoreMessage returns result")
    void sendEventsStoreMessage_happyPath_returnsResult() throws Exception {
      @SuppressWarnings("unchecked")
      StreamObserver<Kubemq.Event> mockEventObserver = mock(StreamObserver.class);
      @SuppressWarnings("unchecked")
      ArgumentCaptor<StreamObserver<Kubemq.Result>> resultObserverCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.sendEventsStream(resultObserverCaptor.capture()))
          .thenReturn(mockEventObserver);

      EventStoreMessage message =
          EventStoreMessage.builder()
              .channel("test-store-channel")
              .body("hello".getBytes())
              .build();

      Thread responseThread =
          new Thread(
              () -> {
                try {
                  Thread.sleep(200);
                  ArgumentCaptor<Kubemq.Event> eventCaptor =
                      ArgumentCaptor.forClass(Kubemq.Event.class);
                  verify(mockEventObserver, timeout(1000)).onNext(eventCaptor.capture());
                  String eventId = eventCaptor.getValue().getEventID();

                  StreamObserver<Kubemq.Result> resultObserver = resultObserverCaptor.getValue();
                  resultObserver.onNext(
                      Kubemq.Result.newBuilder().setEventID(eventId).setSent(true).build());
                } catch (Exception ignored) {
                }
              });
      responseThread.start();

      EventSendResult result = client.sendEventsStoreMessage(message);
      responseThread.join(5000);

      assertNotNull(result);
      assertTrue(result.isSent());
    }

    @Test
    @DisplayName("StatusRuntimeException is mapped via GrpcErrorMapper")
    void sendEventsStoreMessage_statusRuntimeException_mapped() {
      when(mockAsyncStub.sendEventsStream(any()))
          .thenThrow(
              new StatusRuntimeException(Status.PERMISSION_DENIED.withDescription("no access")));

      EventStoreMessage message =
          EventStoreMessage.builder()
              .channel("test-store-channel")
              .body("hello".getBytes())
              .build();

      KubeMQException ex =
          assertThrows(KubeMQException.class, () -> client.sendEventsStoreMessage(message));
      assertFalse(ex.isRetryable());
    }

    @Test
    @DisplayName("Generic exception is wrapped in KubeMQException")
    void sendEventsStoreMessage_genericException_wrapped() {
      when(mockAsyncStub.sendEventsStream(any())).thenThrow(new RuntimeException("boom"));

      EventStoreMessage message =
          EventStoreMessage.builder()
              .channel("test-store-channel")
              .body("hello".getBytes())
              .build();

      KubeMQException ex =
          assertThrows(KubeMQException.class, () -> client.sendEventsStoreMessage(message));
      assertTrue(
          ex.getMessage().contains("sendEventsStoreMessage failed")
              || ex.getMessage().contains("Failed to get response"));
    }
  }

  @Nested
  @DisplayName("Channel Management Tests")
  class ChannelManagementTests {

    @Test
    @DisplayName("createEventsChannel succeeds")
    void createEventsChannel_happyPath_returnsTrue() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(Kubemq.Response.newBuilder().setExecuted(true).build());

      boolean result = client.createEventsChannel("my-events-channel");
      assertTrue(result);
    }

    @Test
    @DisplayName("createEventsChannel with StatusRuntimeException")
    void createEventsChannel_statusRuntimeException_throws() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

      assertThrows(KubeMQException.class, () -> client.createEventsChannel("my-events-channel"));
    }

    @Test
    @DisplayName("createEventsChannel with generic exception")
    void createEventsChannel_genericException_throws() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new RuntimeException("generic error"));

      KubeMQException ex =
          assertThrows(
              KubeMQException.class, () -> client.createEventsChannel("my-events-channel"));
      assertTrue(ex.getMessage().contains("createEventsChannel failed"));
    }

    @Test
    @DisplayName("createEventsStoreChannel succeeds")
    void createEventsStoreChannel_happyPath_returnsTrue() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(Kubemq.Response.newBuilder().setExecuted(true).build());

      boolean result = client.createEventsStoreChannel("my-store-channel");
      assertTrue(result);
    }

    @Test
    @DisplayName("deleteEventsChannel succeeds")
    void deleteEventsChannel_happyPath_returnsTrue() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(Kubemq.Response.newBuilder().setExecuted(true).build());

      boolean result = client.deleteEventsChannel("my-events-channel");
      assertTrue(result);
    }

    @Test
    @DisplayName("deleteEventsChannel with StatusRuntimeException")
    void deleteEventsChannel_statusRuntimeException_throws() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new StatusRuntimeException(Status.INTERNAL));

      assertThrows(KubeMQException.class, () -> client.deleteEventsChannel("my-events-channel"));
    }

    @Test
    @DisplayName("deleteEventsStoreChannel succeeds")
    void deleteEventsStoreChannel_happyPath_returnsTrue() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(Kubemq.Response.newBuilder().setExecuted(true).build());

      boolean result = client.deleteEventsStoreChannel("my-store-channel");
      assertTrue(result);
    }

    @Test
    @DisplayName("listEventsChannels returns channels on success")
    void listEventsChannels_happyPath_returnsChannels() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(
              Kubemq.Response.newBuilder()
                  .setExecuted(true)
                  .setBody(com.google.protobuf.ByteString.copyFromUtf8("[]"))
                  .build());

      java.util.List<PubSubChannel> result = client.listEventsChannels("*");
      assertNotNull(result);
    }

    @Test
    @DisplayName("listEventsChannels with server error response")
    void listEventsChannels_serverError_throws() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(
              Kubemq.Response.newBuilder()
                  .setExecuted(false)
                  .setError("channel not found")
                  .build());

      assertThrows(KubeMQException.class, () -> client.listEventsChannels("*"));
    }

    @Test
    @DisplayName("listEventsChannels with StatusRuntimeException")
    void listEventsChannels_statusRuntimeException_throws() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

      assertThrows(KubeMQException.class, () -> client.listEventsChannels("*"));
    }

    @Test
    @DisplayName("listEventsChannels with generic exception")
    void listEventsChannels_genericException_throws() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new RuntimeException("decode failure"));

      KubeMQException ex =
          assertThrows(KubeMQException.class, () -> client.listEventsChannels("*"));
      assertTrue(ex.getMessage().contains("listEventsChannels failed"));
    }

    @Test
    @DisplayName("listEventsStoreChannels returns channels on success")
    void listEventsStoreChannels_happyPath_returnsChannels() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(
              Kubemq.Response.newBuilder()
                  .setExecuted(true)
                  .setBody(com.google.protobuf.ByteString.copyFromUtf8("[]"))
                  .build());

      java.util.List<PubSubChannel> result = client.listEventsStoreChannels("*");
      assertNotNull(result);
    }

    @Test
    @DisplayName("listEventsStoreChannels with server error response")
    void listEventsStoreChannels_serverError_throws() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(
              Kubemq.Response.newBuilder()
                  .setExecuted(false)
                  .setError("error from server")
                  .build());

      assertThrows(KubeMQException.class, () -> client.listEventsStoreChannels("*"));
    }

    @Test
    @DisplayName("listEventsChannels with null search")
    void listEventsChannels_nullSearch_succeeds() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(
              Kubemq.Response.newBuilder()
                  .setExecuted(true)
                  .setBody(com.google.protobuf.ByteString.copyFromUtf8("[]"))
                  .build());

      java.util.List<PubSubChannel> result = client.listEventsChannels(null);
      assertNotNull(result);
    }
  }

  @Nested
  @DisplayName("Convenience Methods Tests")
  class ConvenienceMethodTests {

    @Test
    @DisplayName("publishEvent(EventMessage) delegates to sendEventsMessage")
    void publishEvent_delegatesToSendEventsMessage() {
      @SuppressWarnings("unchecked")
      StreamObserver<Kubemq.Event> mockEventObserver = mock(StreamObserver.class);
      when(mockAsyncStub.sendEventsStream(any())).thenReturn(mockEventObserver);

      EventMessage message =
          EventMessage.builder().channel("test-channel").body("data".getBytes()).build();

      assertDoesNotThrow(() -> client.publishEvent(message));
      verify(mockEventObserver).onNext(any(Kubemq.Event.class));
    }

    @Test
    @DisplayName("publishEvent(String, byte[]) with non-null body")
    void publishEvent_channelAndBody_succeeds() {
      @SuppressWarnings("unchecked")
      StreamObserver<Kubemq.Event> mockEventObserver = mock(StreamObserver.class);
      when(mockAsyncStub.sendEventsStream(any())).thenReturn(mockEventObserver);

      assertDoesNotThrow(() -> client.publishEvent("test-channel", "data".getBytes()));
      verify(mockEventObserver).onNext(any(Kubemq.Event.class));
    }

    @Test
    @DisplayName("publishEvent(String, byte[]) with null body builds message with empty body")
    void publishEvent_channelAndNullBody_buildsWithEmptyBody() {
      assertThrows(KubeMQException.class, () -> client.publishEvent("test-channel", (byte[]) null));
    }

    @Test
    @DisplayName("publishEvent(String, String) with string body")
    void publishEvent_channelAndStringBody_succeeds() {
      @SuppressWarnings("unchecked")
      StreamObserver<Kubemq.Event> mockEventObserver = mock(StreamObserver.class);
      when(mockAsyncStub.sendEventsStream(any())).thenReturn(mockEventObserver);

      assertDoesNotThrow(() -> client.publishEvent("test-channel", "string body"));
      verify(mockEventObserver).onNext(any(Kubemq.Event.class));
    }

    @Test
    @DisplayName("publishEvent(String, String) with null string triggers validation error")
    void publishEvent_channelAndNullString_validationError() {
      // null string converts to empty byte[], which with no metadata/tags fails validation
      assertThrows(KubeMQException.class, () -> client.publishEvent("test-channel", (String) null));
    }

    @Test
    @DisplayName("publishEventStore(EventStoreMessage) delegates to sendEventsStoreMessage")
    void publishEventStore_delegatesToSendEventsStoreMessage() {
      @SuppressWarnings("unchecked")
      StreamObserver<Kubemq.Event> mockEventObserver = mock(StreamObserver.class);
      @SuppressWarnings("unchecked")
      ArgumentCaptor<StreamObserver<Kubemq.Result>> resultCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.sendEventsStream(resultCaptor.capture())).thenReturn(mockEventObserver);

      EventStoreMessage message =
          EventStoreMessage.builder().channel("test-store-channel").body("data".getBytes()).build();

      Thread responseThread =
          new Thread(
              () -> {
                try {
                  Thread.sleep(200);
                  ArgumentCaptor<Kubemq.Event> eventCaptor =
                      ArgumentCaptor.forClass(Kubemq.Event.class);
                  verify(mockEventObserver, timeout(1000)).onNext(eventCaptor.capture());
                  String eventId = eventCaptor.getValue().getEventID();
                  resultCaptor
                      .getValue()
                      .onNext(Kubemq.Result.newBuilder().setEventID(eventId).setSent(true).build());
                } catch (Exception ignored) {
                }
              });
      responseThread.start();

      EventSendResult result = client.publishEventStore(message);
      assertNotNull(result);
      assertTrue(result.isSent());
    }

    @Test
    @DisplayName("publishEventStore(String, byte[]) with non-null body")
    void publishEventStore_channelAndBody_succeeds() {
      @SuppressWarnings("unchecked")
      StreamObserver<Kubemq.Event> mockEventObserver = mock(StreamObserver.class);
      @SuppressWarnings("unchecked")
      ArgumentCaptor<StreamObserver<Kubemq.Result>> resultCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.sendEventsStream(resultCaptor.capture())).thenReturn(mockEventObserver);

      Thread responseThread =
          new Thread(
              () -> {
                try {
                  Thread.sleep(200);
                  ArgumentCaptor<Kubemq.Event> eventCaptor =
                      ArgumentCaptor.forClass(Kubemq.Event.class);
                  verify(mockEventObserver, timeout(1000)).onNext(eventCaptor.capture());
                  String eventId = eventCaptor.getValue().getEventID();
                  resultCaptor
                      .getValue()
                      .onNext(Kubemq.Result.newBuilder().setEventID(eventId).setSent(true).build());
                } catch (Exception ignored) {
                }
              });
      responseThread.start();

      EventSendResult result = client.publishEventStore("test-store-channel", "data".getBytes());
      assertNotNull(result);
      assertTrue(result.isSent());
    }

    @Test
    @DisplayName("publishEventStore(String, byte[]) with null body triggers validation error")
    void publishEventStore_channelAndNullBody_validationError() {
      // null body is converted to empty byte[], which with no metadata/tags fails validation
      assertThrows(
          KubeMQException.class, () -> client.publishEventStore("test-store-channel", null));
    }
  }

  @Nested
  @DisplayName("Subscription Tests")
  class SubscriptionTests {

    @Test
    @DisplayName("subscribeToEvents succeeds with valid subscription")
    void subscribeToEvents_happyPath_succeeds() {
      EventsSubscription subscription =
          EventsSubscription.builder()
              .channel("events-channel")
              .onReceiveEventCallback(event -> {})
              .onErrorCallback(err -> {})
              .build();

      EventsSubscription result = client.subscribeToEvents(subscription);
      assertNotNull(result);
      verify(mockAsyncStub).subscribeToEvents(any(Kubemq.Subscribe.class), any());
    }

    @Test
    @DisplayName("subscribeToEvents with StatusRuntimeException")
    void subscribeToEvents_statusRuntimeException_throws() {
      doThrow(new StatusRuntimeException(Status.UNAUTHENTICATED))
          .when(mockAsyncStub)
          .subscribeToEvents(any(Kubemq.Subscribe.class), any());

      EventsSubscription subscription =
          EventsSubscription.builder()
              .channel("events-channel")
              .onReceiveEventCallback(event -> {})
              .onErrorCallback(err -> {})
              .build();

      assertThrows(KubeMQException.class, () -> client.subscribeToEvents(subscription));
    }

    @Test
    @DisplayName("subscribeToEvents with generic exception")
    void subscribeToEvents_genericException_throws() {
      doThrow(new RuntimeException("stream error"))
          .when(mockAsyncStub)
          .subscribeToEvents(any(Kubemq.Subscribe.class), any());

      EventsSubscription subscription =
          EventsSubscription.builder()
              .channel("events-channel")
              .onReceiveEventCallback(event -> {})
              .onErrorCallback(err -> {})
              .build();

      KubeMQException ex =
          assertThrows(KubeMQException.class, () -> client.subscribeToEvents(subscription));
      assertTrue(ex.getMessage().contains("subscribeToEvents failed"));
    }

    @Test
    @DisplayName("subscribeToEventsStore succeeds with valid subscription")
    void subscribeToEventsStore_happyPath_succeeds() {
      EventsStoreSubscription subscription =
          EventsStoreSubscription.builder()
              .channel("store-channel")
              .eventsStoreType(EventsStoreType.StartFromFirst)
              .onReceiveEventCallback(event -> {})
              .onErrorCallback(err -> {})
              .build();

      EventsStoreSubscription result = client.subscribeToEventsStore(subscription);
      assertNotNull(result);
      verify(mockAsyncStub).subscribeToEvents(any(Kubemq.Subscribe.class), any());
    }

    @Test
    @DisplayName("subscribeToEventsStore with StatusRuntimeException")
    void subscribeToEventsStore_statusRuntimeException_throws() {
      doThrow(new StatusRuntimeException(Status.UNAVAILABLE))
          .when(mockAsyncStub)
          .subscribeToEvents(any(Kubemq.Subscribe.class), any());

      EventsStoreSubscription subscription =
          EventsStoreSubscription.builder()
              .channel("store-channel")
              .eventsStoreType(EventsStoreType.StartFromFirst)
              .onReceiveEventCallback(event -> {})
              .onErrorCallback(err -> {})
              .build();

      assertThrows(KubeMQException.class, () -> client.subscribeToEventsStore(subscription));
    }

    @Test
    @DisplayName("subscribeToEventsStore with generic exception")
    void subscribeToEventsStore_genericException_throws() {
      doThrow(new RuntimeException("unexpected"))
          .when(mockAsyncStub)
          .subscribeToEvents(any(Kubemq.Subscribe.class), any());

      EventsStoreSubscription subscription =
          EventsStoreSubscription.builder()
              .channel("store-channel")
              .eventsStoreType(EventsStoreType.StartFromFirst)
              .onReceiveEventCallback(event -> {})
              .onErrorCallback(err -> {})
              .build();

      KubeMQException ex =
          assertThrows(KubeMQException.class, () -> client.subscribeToEventsStore(subscription));
      assertTrue(ex.getMessage().contains("subscribeToEventsStore failed"));
    }
  }

  @Nested
  @DisplayName("KubeMQException re-throw Tests")
  class KubeMQExceptionRethrowTests {

    @Test
    @DisplayName("sendEventsMessage rethrows KubeMQException as-is")
    void sendEventsMessage_kubemqException_rethrown() {
      KubeMQException original =
          KubeMQException.newBuilder().message("test error").operation("test").build();
      when(mockAsyncStub.sendEventsStream(any())).thenThrow(original);

      KubeMQException thrown =
          assertThrows(
              KubeMQException.class,
              () ->
                  client.sendEventsMessage(
                      EventMessage.builder().channel("ch").body("b".getBytes()).build()));
      assertSame(original, thrown);
    }

    @Test
    @DisplayName("sendEventsStoreMessage wraps exception from EventStreamHelper")
    void sendEventsStoreMessage_kubemqException_wrapped() {
      when(mockAsyncStub.sendEventsStream(any()))
          .thenThrow(new RuntimeException("stream init failed"));

      KubeMQException thrown =
          assertThrows(
              KubeMQException.class,
              () ->
                  client.sendEventsStoreMessage(
                      EventStoreMessage.builder().channel("ch").body("b".getBytes()).build()));
      assertNotNull(thrown.getMessage());
    }
  }

  @Nested
  @DisplayName("Channel Management Error Verification")
  class ChannelManagementErrorVerificationTests {

    @Test
    @DisplayName("createEventsStoreChannel with StatusRuntimeException")
    void createEventsStoreChannel_statusRuntimeException_throws() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

      assertThrows(KubeMQException.class, () -> client.createEventsStoreChannel("my-channel"));
    }

    @Test
    @DisplayName("deleteEventsStoreChannel with StatusRuntimeException")
    void deleteEventsStoreChannel_statusRuntimeException_throws() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

      assertThrows(KubeMQException.class, () -> client.deleteEventsStoreChannel("my-channel"));
    }

    @Test
    @DisplayName("deleteEventsStoreChannel succeeds")
    void deleteEventsStoreChannel_happyPath_returnsTrue() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenReturn(Kubemq.Response.newBuilder().setExecuted(true).build());

      boolean result = client.deleteEventsStoreChannel("my-store-channel");
      assertTrue(result);
    }

    @Test
    @DisplayName("listEventsStoreChannels with StatusRuntimeException")
    void listEventsStoreChannels_statusRuntimeException_throws() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

      assertThrows(KubeMQException.class, () -> client.listEventsStoreChannels("*"));
    }

    @Test
    @DisplayName("listEventsStoreChannels with generic exception")
    void listEventsStoreChannels_genericException_throws() {
      when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
          .thenThrow(new RuntimeException("oops"));

      KubeMQException ex =
          assertThrows(KubeMQException.class, () -> client.listEventsStoreChannels("*"));
      assertTrue(ex.getMessage().contains("listEventsStoreChannels failed"));
    }
  }
}
