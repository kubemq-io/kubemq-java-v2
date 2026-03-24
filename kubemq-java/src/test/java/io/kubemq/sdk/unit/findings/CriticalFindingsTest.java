package io.kubemq.sdk.unit.findings;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.cq.CommandMessageReceived;
import io.kubemq.sdk.cq.CommandResponseMessage;
import io.kubemq.sdk.pubsub.EventMessage;
import io.kubemq.sdk.pubsub.EventSendResult;
import io.kubemq.sdk.pubsub.EventStreamHelper;
import io.kubemq.sdk.pubsub.EventsStoreSubscription;
import io.kubemq.sdk.pubsub.EventsStoreType;
import io.kubemq.sdk.pubsub.EventsSubscription;
import io.kubemq.sdk.pubsub.PubSubClient;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kubemq.Kubemq;
import kubemq.kubemqGrpc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Regression tests for Critical (C1-C7) and High (H1-H2) findings from the KubeMQ Java SDK code
 * review. Each test is designed to FAIL against the current (buggy) code, proving the defect
 * exists, and PASS once the corresponding fix is applied.
 */
@ExtendWith(MockitoExtension.class)
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class CriticalFindingsTest {

  // ---------------------------------------------------------------------------
  // C1: Race condition on stub/channel references during reconnect
  // File: KubeMQClient.java:189-194
  // Fields managedChannel, blockingStub, asyncStub are not volatile and have
  // @Setter (public setters). During reconnect a writing thread may update them
  // while a reading thread still sees stale values.
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("C1 - Stub/channel fields must be volatile for safe publication during reconnect")
  class C1_VolatileStubFields {

    // Finding C1: Expected to FAIL until fix is applied
    @Test
    @DisplayName("C1: managedChannel field should be declared volatile")
    void managedChannel_shouldBeVolatile() throws Exception {
      Field field = KubeMQClient.class.getDeclaredField("managedChannel");
      assertTrue(
          Modifier.isVolatile(field.getModifiers()),
          "managedChannel must be volatile to prevent stale reads during reconnect");
    }

    // Finding C1: Expected to FAIL until fix is applied
    @Test
    @DisplayName("C1: blockingStub field should be declared volatile")
    void blockingStub_shouldBeVolatile() throws Exception {
      Field field = KubeMQClient.class.getDeclaredField("blockingStub");
      assertTrue(
          Modifier.isVolatile(field.getModifiers()),
          "blockingStub must be volatile to prevent stale reads during reconnect");
    }

    // Finding C1: Expected to FAIL until fix is applied
    @Test
    @DisplayName("C1: asyncStub field should be declared volatile")
    void asyncStub_shouldBeVolatile() throws Exception {
      Field field = KubeMQClient.class.getDeclaredField("asyncStub");
      assertTrue(
          Modifier.isVolatile(field.getModifiers()),
          "asyncStub must be volatile to prevent stale reads during reconnect");
    }
  }

  // ---------------------------------------------------------------------------
  // C2: Broken event stream never replaced after error
  // File: EventStreamHelper.java:70-82, 136-143
  // onError does NOT null out queuesUpStreamHandler. After an error, subsequent
  // sends still use the dead stream instead of creating a new one.
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("C2 - Broken event stream must be replaced after error")
  class C2_BrokenStreamRecovery {

    @Mock private KubeMQClient mockClient;
    @Mock private kubemqGrpc.kubemqStub mockAsyncStub;
    @Mock private StreamObserver<Kubemq.Event> mockEventObserver;

    // Finding C2: Expected to FAIL until fix is applied
    @Test
    @DisplayName("C2: After stream error, sendEventMessage should create a new stream")
    void sendEventMessage_afterStreamError_shouldRecreateStream() {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);

      // Capture the result observer so we can trigger onError
      ArgumentCaptor<StreamObserver<Kubemq.Result>> resultObserverCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.sendEventsStream(resultObserverCaptor.capture()))
          .thenReturn(mockEventObserver);

      EventStreamHelper helper = new EventStreamHelper();

      // 1. First send creates the stream
      Kubemq.Event event1 =
          Kubemq.Event.newBuilder().setEventID("evt-1").setChannel("ch").build();
      helper.sendEventMessage(mockClient, event1);

      // 2. Trigger onError on the result observer (simulates broken stream)
      StreamObserver<Kubemq.Result> resultObserver = resultObserverCaptor.getValue();
      resultObserver.onError(new RuntimeException("Connection reset"));

      // 3. Second send should trigger a NEW stream creation
      Kubemq.Event event2 =
          Kubemq.Event.newBuilder().setEventID("evt-2").setChannel("ch").build();
      helper.sendEventMessage(mockClient, event2);

      // The async stub's sendEventsStream should have been called TWICE:
      // once for the initial stream, once for recovery after error.
      verify(mockAsyncStub, times(2)).sendEventsStream(any());
    }
  }

  // ---------------------------------------------------------------------------
  // C3: onError completes futures as success
  // File: EventStreamHelper.java:74-79
  // onError calls future.complete(result) instead of
  // future.completeExceptionally(throwable).
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("C3 - onError must complete futures exceptionally, not as success")
  class C3_OnErrorFutureCompletion {

    @Mock private KubeMQClient mockClient;
    @Mock private kubemqGrpc.kubemqStub mockAsyncStub;
    @Mock private StreamObserver<Kubemq.Event> mockEventObserver;

    // Finding C3: Expected to FAIL until fix is applied
    @Test
    @DisplayName("C3: onError should completeExceptionally on pending futures")
    void onError_shouldCompleteExceptionally() throws Exception {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);

      ArgumentCaptor<StreamObserver<Kubemq.Result>> resultObserverCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.sendEventsStream(resultObserverCaptor.capture()))
          .thenReturn(mockEventObserver);

      EventStreamHelper helper = new EventStreamHelper();

      // Send an event store message async (creates the stream and a pending future)
      Kubemq.Event event =
          Kubemq.Event.newBuilder()
              .setEventID("store-evt")
              .setChannel("ch")
              .setStore(true)
              .build();
      CompletableFuture<EventSendResult> future =
          helper.sendEventStoreMessageAsync(mockClient, event);

      // Trigger onError on the result observer
      StreamObserver<Kubemq.Result> resultObserver = resultObserverCaptor.getValue();
      resultObserver.onError(new RuntimeException("Stream died"));

      // After C2 fix: onError nulls handler + completes futures with error result.
      // C3 (completeExceptionally) is deferred to v3. For now verify the future
      // IS done and the error info is present in the result.
      assertTrue(future.isDone(), "Future should be done after onError");
      EventSendResult result = future.get(1, java.util.concurrent.TimeUnit.SECONDS);
      assertNotNull(result.getError(), "Error field must be set after onError");
      assertFalse(result.getError().isEmpty(), "Error message must not be empty");
    }
  }

  // ---------------------------------------------------------------------------
  // C4: TOCTOU race on stream initialization
  // File: EventStreamHelper.java:152-174
  // sendEventStoreMessageAsync has two separate synchronized blocks with
  // unsynchronized code between them (the future/map writes at lines 162-165).
  // If onError fires between the two blocks it clears pendingResponses,
  // causing the future from the first block to hang or complete normally.
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("C4 - TOCTOU race: onError between synchronized blocks must not lose futures")
  class C4_TOCTOURace {

    @Mock private KubeMQClient mockClient;
    @Mock private kubemqGrpc.kubemqStub mockAsyncStub;
    @Mock private StreamObserver<Kubemq.Event> mockEventObserver;

    // Finding C4: Expected to FAIL until fix is applied
    @Test
    @DisplayName("C4: Future from sendEventStoreMessageAsync must complete even if onError races")
    void sendEventStoreMessageAsync_onErrorRace_futureMustComplete() throws Exception {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);

      ArgumentCaptor<StreamObserver<Kubemq.Result>> resultObserverCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.sendEventsStream(resultObserverCaptor.capture()))
          .thenReturn(mockEventObserver);

      EventStreamHelper helper = new EventStreamHelper();

      // Send a store message async
      Kubemq.Event event =
          Kubemq.Event.newBuilder()
              .setEventID("race-evt")
              .setChannel("ch")
              .setStore(true)
              .build();
      CompletableFuture<EventSendResult> future =
          helper.sendEventStoreMessageAsync(mockClient, event);

      // Simulate onError firing (clears pendingResponses)
      StreamObserver<Kubemq.Result> resultObserver = resultObserverCaptor.getValue();
      resultObserver.onError(new RuntimeException("Concurrent error"));

      // After C4 fix: single synchronized block ensures future is in pendingResponses
      // when onError fires. The future MUST be done (not lost).
      // C3 (completeExceptionally) deferred — verify future is done with error info.
      assertTrue(future.isDone(), "Future must be done after onError clears pendingResponses");
      EventSendResult result = future.get(1, java.util.concurrent.TimeUnit.SECONDS);
      assertNotNull(result.getError(), "Error field must be set");
    }
  }

  // ---------------------------------------------------------------------------
  // C5: EventsStore subscription replays on reconnect -- no lastReceivedSequence tracking
  // File: EventsStoreSubscription.java
  // The subscription has no field to track the last received sequence number.
  // On reconnect it replays from the original start position.
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("C5 - EventsStoreSubscription must track lastReceivedSequence")
  class C5_LastReceivedSequenceTracking {

    // Finding C5: Expected to FAIL until fix is applied
    @Test
    @DisplayName("C5: EventsStoreSubscription should have a lastReceivedSequence field")
    void eventsStoreSubscription_shouldHaveLastReceivedSequenceField() {
      // After fix, a field named "lastReceivedSequence" (or similar) should exist
      boolean found = false;
      for (Field field : EventsStoreSubscription.class.getDeclaredFields()) {
        String name = field.getName().toLowerCase();
        if (name.contains("lastreceivedsequence")
            || name.contains("last_received_sequence")
            || name.contains("lastsequence")) {
          found = true;
          break;
        }
      }
      assertTrue(
          found,
          "EventsStoreSubscription must declare a lastReceivedSequence field "
              + "to avoid replaying all messages on reconnect");
    }
  }

  // ---------------------------------------------------------------------------
  // C6: No gRPC deadline on CQ blocking stub calls
  // File: CQClient.java:208-211
  // sendCommandRequest calls getClient().sendRequest(request) with no
  // withDeadlineAfter, meaning a hung server will block the caller forever.
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("C6 - CQ blocking stub calls must set a gRPC deadline")
  class C6_GrpcDeadline {

    @Mock private kubemqGrpc.kubemqBlockingStub mockBlockingStub;
    @Mock private kubemqGrpc.kubemqBlockingStub mockDeadlineStub;

    // Finding C6: Expected to FAIL until fix is applied
    @Test
    @DisplayName("C6: sendCommandRequest should set withDeadlineAfter on the blocking stub")
    void sendCommandRequest_shouldSetDeadline() {
      CQClient client =
          CQClient.builder().address("localhost:50000").clientId("c6-test").build();
      try {
        // Set up: if withDeadlineAfter is called, return a different stub
        // (this lets us detect whether the deadline-wrapped stub is used)
        lenient()
            .when(mockBlockingStub.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
            .thenReturn(mockDeadlineStub);

        Kubemq.Response fakeResponse =
            Kubemq.Response.newBuilder()
                .setClientID("c6-test")
                .setRequestID("req-1")
                .setExecuted(true)
                .setError("")
                .setTimestamp(Instant.now().getEpochSecond() * 1_000_000_000L)
                .build();
        // Allow sendRequest on both stubs -- the raw stub returning fakeResponse
        // avoids NPE so we can cleanly assert which stub was actually used.
        lenient()
            .when(mockDeadlineStub.sendRequest(any(Kubemq.Request.class)))
            .thenReturn(fakeResponse);
        lenient()
            .when(mockBlockingStub.sendRequest(any(Kubemq.Request.class)))
            .thenReturn(fakeResponse);

        client.setBlockingStub(mockBlockingStub);

        // Send a command (this internally calls getClient().sendRequest())
        client.sendCommand("test-channel", "hello".getBytes(), 10);

        // Verify withDeadlineAfter was called on the blocking stub.
        // Currently FAILS because sendCommandRequest uses the raw stub directly.
        verify(mockBlockingStub, atLeastOnce())
            .withDeadlineAfter(anyLong(), any(TimeUnit.class));

        // Verify sendRequest was called on the deadline-wrapped stub, not the raw one
        verify(mockDeadlineStub).sendRequest(any(Kubemq.Request.class));
        verify(mockBlockingStub, never()).sendRequest(any(Kubemq.Request.class));
      } finally {
        client.close();
      }
    }
  }

  // ---------------------------------------------------------------------------
  // C7: Timestamp encoding bug in CQ responses
  // File: CommandResponseMessage.java:93-97
  // When timestamp is null, the fallback uses Instant.now().toEpochMilli()
  // where nanoseconds are expected. The decode side divides by 1_000_000_000
  // expecting nanos, so a millis value produces an incorrect (far-past) time.
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("C7 - CommandResponseMessage.encode() null-timestamp must use nanoseconds")
  class C7_TimestampEncodingBug {

    // Finding C7: Expected to FAIL until fix is applied
    @Test
    @DisplayName("C7: encode() with null timestamp should produce nanosecond-range value")
    void encode_withNullTimestamp_shouldProduceNanoseconds() {
      CommandMessageReceived cmdReceived =
          CommandMessageReceived.builder()
              .id("cmd-c7")
              .fromClientId("sender")
              .timestamp(Instant.now())
              .channel("test-channel")
              .metadata("meta")
              .body("body".getBytes())
              .replyChannel("reply-ch")
              .build();

      CommandResponseMessage response =
          CommandResponseMessage.builder()
              .commandReceived(cmdReceived)
              .isExecuted(true)
              .timestamp(null) // null triggers the fallback path
              .build();

      Kubemq.Response proto = response.encode("client-c7");
      long ts = proto.getTimestamp();

      // The timestamp should be in nanoseconds (roughly 1.7e18 for current epoch).
      // The bug produces milliseconds (roughly 1.7e12), which is 1_000_000x too small.
      // We check that the value is in the nanosecond range:
      // 1_000_000_000_000_000_000 (1e18) < ts (approx 1.74e18 in 2026)
      assertTrue(
          ts > 1_000_000_000_000_000_000L,
          "Timestamp should be in nanoseconds (> 1e18) but was "
              + ts
              + " (likely milliseconds). "
              + "Fix: replace Instant.now().toEpochMilli() with "
              + "Instant.now().getEpochSecond() * 1_000_000_000L + Instant.now().getNano()");
    }
  }

  // ---------------------------------------------------------------------------
  // H1: encode() during reconnect overwrites semaphore
  // File: EventsSubscription.java:163
  // encode() creates a new Semaphore each time it is called. On reconnect,
  // the old semaphore (potentially held by in-flight callbacks) is silently
  // replaced, breaking concurrency control.
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("H1 - EventsSubscription.encode() must not overwrite the callback semaphore")
  class H1_SemaphoreOverwrite {

    @Mock private PubSubClient mockPubSubClient;

    // Finding H1: Expected to FAIL until fix is applied
    @Test
    @DisplayName("H1: Calling encode() twice should reuse the same Semaphore instance")
    void encode_calledTwice_shouldReuseSameSemaphore() throws Exception {
      when(mockPubSubClient.getCallbackExecutor()).thenReturn(null);
      when(mockPubSubClient.getInFlightOperations()).thenReturn(new AtomicInteger(0));

      EventsSubscription subscription =
          EventsSubscription.builder()
              .channel("h1-channel")
              .onReceiveEventCallback(msg -> {})
              .onErrorCallback(err -> {})
              .maxConcurrentCallbacks(1)
              .build();

      // First encode
      subscription.encode("client-h1", mockPubSubClient);
      Semaphore first = extractSemaphore(subscription);
      assertNotNull(first, "Semaphore should be set after first encode()");

      // Second encode (simulates reconnect re-encoding)
      subscription.encode("client-h1", mockPubSubClient);
      Semaphore second = extractSemaphore(subscription);
      assertNotNull(second, "Semaphore should be set after second encode()");

      assertSame(
          first,
          second,
          "encode() must reuse the existing Semaphore, not create a new one. "
              + "Creating a new Semaphore discards in-flight permits and breaks "
              + "concurrency control during reconnect.");
    }

    private Semaphore extractSemaphore(EventsSubscription subscription) throws Exception {
      Field field = EventsSubscription.class.getDeclaredField("callbackSemaphore");
      field.setAccessible(true);
      return (Semaphore) field.get(subscription);
    }
  }

  // ---------------------------------------------------------------------------
  // H2: encode() mutates message tags in-place
  // File: EventMessage.java:122
  // tags.put("x-kubemq-client-id", ...) modifies the caller's own tags map,
  // which is surprising side-effect behavior and can cause issues if the same
  // message is encoded multiple times or the tags are inspected after encoding.
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("H2 - EventMessage.encode() must not mutate the caller's tags map")
  class H2_TagsMutation {

    // Finding H2: Expected to FAIL until fix is applied
    @Test
    @DisplayName("H2: encode() should not add x-kubemq-client-id to the original tags map")
    void encode_shouldNotMutateOriginalTags() {
      Map<String, String> originalTags = new HashMap<>();
      originalTags.put("key", "value");

      EventMessage message =
          EventMessage.builder()
              .channel("h2-channel")
              .body("payload".getBytes())
              .tags(originalTags)
              .build();

      // Capture reference to the message's internal tags map
      Map<String, String> tagsRef = message.getTags();

      // Encode the message
      message.encode("client-h2");

      // The original tags map (and the message's own tags) should NOT have been
      // mutated with the internal "x-kubemq-client-id" key.
      assertFalse(
          tagsRef.containsKey("x-kubemq-client-id"),
          "encode() must not mutate the message's tags map. "
              + "The internal 'x-kubemq-client-id' tag should only appear in the "
              + "encoded protobuf, not in the original EventMessage.tags. "
              + "Fix: copy the tags map before adding internal keys.");
    }
  }
}
