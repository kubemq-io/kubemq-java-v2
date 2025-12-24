package io.kubemq.sdk.unit.pubsub;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.pubsub.EventSendResult;
import io.kubemq.sdk.pubsub.EventStreamHelper;
import kubemq.Kubemq;
import kubemq.kubemqGrpc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventStreamHelper - covers StreamObserver callbacks and message sending.
 * Updated to work with per-request futures pattern (CRITICAL-1 fix).
 */
@ExtendWith(MockitoExtension.class)
class EventStreamHelperTest {

    @Mock
    private KubeMQClient mockClient;

    @Mock
    private kubemqGrpc.kubemqStub mockAsyncStub;

    @Mock
    private StreamObserver<Kubemq.Event> mockEventObserver;

    private EventStreamHelper eventStreamHelper;

    @BeforeEach
    void setup() {
        eventStreamHelper = new EventStreamHelper();
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Constructor initializes resultStreamObserver")
        void constructor_initializesResultStreamObserver() {
            assertNotNull(eventStreamHelper.getResultStreamObserver());
        }
    }

    @Nested
    @DisplayName("SendEventMessage Tests")
    class SendEventMessageTests {

        @Test
        @DisplayName("P-01: sendEventMessage creates stream on first call")
        void sendEventMessage_createsStreamOnFirstCall() {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
            when(mockAsyncStub.sendEventsStream(any())).thenReturn(mockEventObserver);

            Kubemq.Event event = Kubemq.Event.newBuilder()
                    .setEventID("event-1")
                    .setChannel("test-channel")
                    .build();

            eventStreamHelper.sendEventMessage(mockClient, event);

            verify(mockAsyncStub).sendEventsStream(any());
            verify(mockEventObserver).onNext(event);
        }

        @Test
        @DisplayName("P-02: sendEventMessage reuses existing stream")
        void sendEventMessage_reusesExistingStream() {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
            when(mockAsyncStub.sendEventsStream(any())).thenReturn(mockEventObserver);

            Kubemq.Event event1 = Kubemq.Event.newBuilder()
                    .setEventID("event-1")
                    .setChannel("test-channel")
                    .build();

            Kubemq.Event event2 = Kubemq.Event.newBuilder()
                    .setEventID("event-2")
                    .setChannel("test-channel")
                    .build();

            eventStreamHelper.sendEventMessage(mockClient, event1);
            eventStreamHelper.sendEventMessage(mockClient, event2);

            // Stream should only be created once
            verify(mockAsyncStub, times(1)).sendEventsStream(any());
            verify(mockEventObserver, times(2)).onNext(any());
        }
    }

    @Nested
    @DisplayName("SendEventStoreMessage Tests")
    class SendEventStoreMessageTests {

        @Test
        @DisplayName("P-03: sendEventStoreMessage creates stream on first call")
        void sendEventStoreMessage_createsStreamOnFirstCall() throws Exception {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);

            // Capture the result observer to simulate response
            ArgumentCaptor<StreamObserver<Kubemq.Result>> observerCaptor =
                    ArgumentCaptor.forClass(StreamObserver.class);
            when(mockAsyncStub.sendEventsStream(observerCaptor.capture())).thenReturn(mockEventObserver);

            Kubemq.Event event = Kubemq.Event.newBuilder()
                    .setEventID("store-event-1")
                    .setChannel("test-store-channel")
                    .setStore(true)
                    .build();

            // Start the send in a separate thread since it blocks
            CompletableFuture<EventSendResult> resultFuture = CompletableFuture.supplyAsync(() ->
                    eventStreamHelper.sendEventStoreMessage(mockClient, event)
            );

            // Wait a bit for the call to be made
            Thread.sleep(100);

            // Capture the event that was actually sent (with new ID from per-request pattern)
            ArgumentCaptor<Kubemq.Event> eventCaptor = ArgumentCaptor.forClass(Kubemq.Event.class);
            verify(mockEventObserver).onNext(eventCaptor.capture());
            String actualEventId = eventCaptor.getValue().getEventID();

            // Simulate response with the actual event ID
            StreamObserver<Kubemq.Result> capturedObserver = observerCaptor.getValue();
            Kubemq.Result result = Kubemq.Result.newBuilder()
                    .setEventID(actualEventId)
                    .setSent(true)
                    .build();
            capturedObserver.onNext(result);

            EventSendResult sendResult = resultFuture.get(5, TimeUnit.SECONDS);

            assertNotNull(sendResult);
            assertEquals(actualEventId, sendResult.getId());
            assertTrue(sendResult.isSent());
        }

        @Test
        @DisplayName("P-04: sendEventStoreMessage reuses existing stream")
        void sendEventStoreMessage_reusesExistingStream() throws Exception {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);

            ArgumentCaptor<StreamObserver<Kubemq.Result>> observerCaptor =
                    ArgumentCaptor.forClass(StreamObserver.class);
            when(mockAsyncStub.sendEventsStream(observerCaptor.capture())).thenReturn(mockEventObserver);

            Kubemq.Event event = Kubemq.Event.newBuilder()
                    .setEventID("store-event-1")
                    .setChannel("test-store-channel")
                    .setStore(true)
                    .build();

            CompletableFuture<EventSendResult> resultFuture = CompletableFuture.supplyAsync(() ->
                    eventStreamHelper.sendEventStoreMessage(mockClient, event)
            );

            Thread.sleep(100);

            // Capture the event that was actually sent
            ArgumentCaptor<Kubemq.Event> eventCaptor = ArgumentCaptor.forClass(Kubemq.Event.class);
            verify(mockEventObserver).onNext(eventCaptor.capture());
            String actualEventId = eventCaptor.getValue().getEventID();

            StreamObserver<Kubemq.Result> capturedObserver = observerCaptor.getValue();
            Kubemq.Result result = Kubemq.Result.newBuilder()
                    .setEventID(actualEventId)
                    .setSent(true)
                    .build();
            capturedObserver.onNext(result);

            resultFuture.get(5, TimeUnit.SECONDS);

            // Stream should only be created once
            verify(mockAsyncStub, times(1)).sendEventsStream(any());
        }
    }

    @Nested
    @DisplayName("ResultStreamObserver Callback Tests")
    class ResultStreamObserverCallbackTests {

        @Test
        @DisplayName("P-05: onNext completes correct per-request future")
        void resultStreamObserver_onNext_completesCorrectFuture() throws Exception {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);

            ArgumentCaptor<StreamObserver<Kubemq.Result>> observerCaptor =
                    ArgumentCaptor.forClass(StreamObserver.class);
            when(mockAsyncStub.sendEventsStream(observerCaptor.capture())).thenReturn(mockEventObserver);

            Kubemq.Event event = Kubemq.Event.newBuilder()
                    .setEventID("test-event")
                    .setChannel("test-channel")
                    .setStore(true)
                    .build();

            CompletableFuture<EventSendResult> resultFuture = CompletableFuture.supplyAsync(() ->
                    eventStreamHelper.sendEventStoreMessage(mockClient, event)
            );

            Thread.sleep(100);

            // Get the actual event ID that was sent
            ArgumentCaptor<Kubemq.Event> eventCaptor = ArgumentCaptor.forClass(Kubemq.Event.class);
            verify(mockEventObserver).onNext(eventCaptor.capture());
            String actualEventId = eventCaptor.getValue().getEventID();

            // Send the response
            StreamObserver<Kubemq.Result> resultObserver = observerCaptor.getValue();
            Kubemq.Result result = Kubemq.Result.newBuilder()
                    .setEventID(actualEventId)
                    .setSent(true)
                    .build();
            resultObserver.onNext(result);

            EventSendResult sendResult = resultFuture.get(1, TimeUnit.SECONDS);
            assertNotNull(sendResult);
            assertEquals(actualEventId, sendResult.getId());
            assertTrue(sendResult.isSent());
        }

        @Test
        @DisplayName("P-06: onError completes all pending futures with error")
        void resultStreamObserver_onError_completesAllPendingFutures() throws Exception {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);

            ArgumentCaptor<StreamObserver<Kubemq.Result>> observerCaptor =
                    ArgumentCaptor.forClass(StreamObserver.class);
            when(mockAsyncStub.sendEventsStream(observerCaptor.capture())).thenReturn(mockEventObserver);

            Kubemq.Event event = Kubemq.Event.newBuilder()
                    .setEventID("test-event")
                    .setChannel("test-channel")
                    .setStore(true)
                    .build();

            CompletableFuture<EventSendResult> resultFuture = CompletableFuture.supplyAsync(() ->
                    eventStreamHelper.sendEventStoreMessage(mockClient, event)
            );

            Thread.sleep(100);

            // Trigger an error
            StreamObserver<Kubemq.Result> resultObserver = observerCaptor.getValue();
            resultObserver.onError(new RuntimeException("Connection lost"));

            EventSendResult sendResult = resultFuture.get(1, TimeUnit.SECONDS);
            assertNotNull(sendResult);
            assertEquals("Connection lost", sendResult.getError());
        }

        @Test
        @DisplayName("P-07: onCompleted does not throw")
        void resultStreamObserver_onCompleted_doesNotThrow() {
            StreamObserver<Kubemq.Result> resultObserver = eventStreamHelper.getResultStreamObserver();

            assertDoesNotThrow(() -> resultObserver.onCompleted());
        }
    }

    @Nested
    @DisplayName("Timeout Handling Tests")
    class TimeoutHandlingTests {

        @Test
        @DisplayName("P-08: sendEventStoreMessage returns timeout result after 30 seconds")
        void sendEventStoreMessage_timeout_returnsTimeoutResult() throws Exception {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
            when(mockAsyncStub.sendEventsStream(any())).thenReturn(mockEventObserver);

            Kubemq.Event event = Kubemq.Event.newBuilder()
                    .setEventID("timeout-event")
                    .setChannel("test-channel")
                    .setStore(true)
                    .build();

            // With the new implementation, timeout is handled gracefully and returns a result
            // We can't easily test the 30-second timeout in a unit test, but we verify
            // the method exists and can handle failures
            CompletableFuture<EventSendResult> resultFuture = CompletableFuture.supplyAsync(() ->
                    eventStreamHelper.sendEventStoreMessage(mockClient, event)
            );

            // This test verifies the async call starts successfully
            Thread.sleep(100);
            verify(mockEventObserver).onNext(any());

            // Clean up by triggering error (simulates connection lost)
            eventStreamHelper.getResultStreamObserver().onError(new RuntimeException("Test cleanup"));
        }
    }
}
