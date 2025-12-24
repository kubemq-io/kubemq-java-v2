package io.kubemq.sdk.unit.pubsub;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.pubsub.EventStoreMessageReceived;
import io.kubemq.sdk.pubsub.EventsStoreSubscription;
import io.kubemq.sdk.pubsub.EventsStoreType;
import io.kubemq.sdk.pubsub.PubSubClient;
import kubemq.Kubemq;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for EventsStoreSubscription validation and StreamObserver callbacks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventsStoreSubscriptionTest {

    @Mock
    private PubSubClient mockClient;

    @Nested
    class ValidationTests {

        @Test
        void validate_withValidSubscription_passes() {
            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .eventsStoreType(EventsStoreType.StartFromFirst)
                    .onReceiveEventCallback(event -> {})
                    .build();

            assertDoesNotThrow(subscription::validate);
        }

        @Test
        void validate_withStartNewOnly_passes() {
            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .eventsStoreType(EventsStoreType.StartNewOnly)
                    .onReceiveEventCallback(event -> {})
                    .build();

            assertDoesNotThrow(subscription::validate);
        }

        @Test
        void validate_withStartFromLast_passes() {
            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .eventsStoreType(EventsStoreType.StartFromLast)
                    .onReceiveEventCallback(event -> {})
                    .build();

            assertDoesNotThrow(subscription::validate);
        }

        @Test
        void validate_withStartAtSequence_andValidSequence_passes() {
            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .eventsStoreType(EventsStoreType.StartAtSequence)
                    .eventsStoreSequenceValue(100)
                    .onReceiveEventCallback(event -> {})
                    .build();

            assertDoesNotThrow(subscription::validate);
        }

        @Test
        void validate_withStartAtTime_andValidTime_passes() {
            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .eventsStoreType(EventsStoreType.StartAtTime)
                    .eventsStoreStartTime(Instant.now().minusSeconds(3600))
                    .onReceiveEventCallback(event -> {})
                    .build();

            assertDoesNotThrow(subscription::validate);
        }

        @Test
        void validate_withGroupSet_passes() {
            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .eventsStoreType(EventsStoreType.StartFromFirst)
                    .onReceiveEventCallback(event -> {})
                    .build();

            assertDoesNotThrow(subscription::validate);
        }

        @Test
        void validate_withNullChannel_throws() {
            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .eventsStoreType(EventsStoreType.StartFromFirst)
                    .onReceiveEventCallback(event -> {})
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    subscription::validate
            );
            assertTrue(ex.getMessage().contains("channel"));
        }

        @Test
        void validate_withEmptyChannel_throws() {
            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("")
                    .eventsStoreType(EventsStoreType.StartFromFirst)
                    .onReceiveEventCallback(event -> {})
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    subscription::validate
            );
            assertTrue(ex.getMessage().contains("channel"));
        }

        @Test
        void validate_withNullCallback_throws() {
            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .eventsStoreType(EventsStoreType.StartFromFirst)
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    subscription::validate
            );
            assertTrue(ex.getMessage().contains("Callback") || ex.getMessage().contains("callback"));
        }

        @Test
        void validate_withNullEventsStoreType_throws() {
            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .eventsStoreType(null)
                    .onReceiveEventCallback(event -> {})
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    subscription::validate
            );
            assertTrue(ex.getMessage().contains("events store type"));
        }

        @Test
        void validate_withUndefinedEventsStoreType_throws() {
            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .eventsStoreType(EventsStoreType.Undefined)
                    .onReceiveEventCallback(event -> {})
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    subscription::validate
            );
            assertTrue(ex.getMessage().contains("events store type"));
        }

        @Test
        void validate_withStartAtSequence_andZeroSequence_throws() {
            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .eventsStoreType(EventsStoreType.StartAtSequence)
                    .eventsStoreSequenceValue(0)
                    .onReceiveEventCallback(event -> {})
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    subscription::validate
            );
            assertTrue(ex.getMessage().contains("sequence"));
        }

        @Test
        void validate_withStartAtTime_andNullTime_throws() {
            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .eventsStoreType(EventsStoreType.StartAtTime)
                    .eventsStoreStartTime(null)
                    .onReceiveEventCallback(event -> {})
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    subscription::validate
            );
            assertTrue(ex.getMessage().contains("start time"));
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toString_includesAllFields() {
            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("my-channel")
                    .group("my-group")
                    .eventsStoreType(EventsStoreType.StartAtSequence)
                    .eventsStoreSequenceValue(42)
                    .onReceiveEventCallback(event -> {})
                    .build();

            String str = subscription.toString();

            assertTrue(str.contains("my-channel"));
            assertTrue(str.contains("my-group"));
            assertTrue(str.contains("StartAtSequence"));
            assertTrue(str.contains("42"));
        }
    }

    @Nested
    class StreamObserverTests {

        @Test
        void onNext_receivesEvent_invokesCallback() throws InterruptedException {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<EventStoreMessageReceived> receivedMessage = new AtomicReference<>();

            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .eventsStoreType(EventsStoreType.StartFromFirst)
                    .onReceiveEventCallback(msg -> {
                        receivedMessage.set(msg);
                        latch.countDown();
                    })
                    .build();

            // Encode to create the observer
            Kubemq.Subscribe subscribeRequest = subscription.encode("test-client", mockClient);
            StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

            // Simulate receiving an event
            Kubemq.EventReceive event = Kubemq.EventReceive.newBuilder()
                    .setEventID("store-event-123")
                    .setChannel("test-channel")
                    .setMetadata("test-metadata")
                    .setBody(ByteString.copyFromUtf8("test-body"))
                    .setSequence(100)
                    .putTags("key1", "value1")
                    .build();

            observer.onNext(event);

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNotNull(receivedMessage.get());
            assertEquals("store-event-123", receivedMessage.get().getId());
            assertEquals("test-channel", receivedMessage.get().getChannel());
            assertEquals("test-metadata", receivedMessage.get().getMetadata());
            assertEquals(100, receivedMessage.get().getSequence());
        }

        @Test
        void onError_receivesError_invokesErrorCallback() throws InterruptedException {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> errorMessage = new AtomicReference<>();

            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .eventsStoreType(EventsStoreType.StartFromFirst)
                    .onReceiveEventCallback(msg -> {})
                    .onErrorCallback(error -> {
                        errorMessage.set(error);
                        latch.countDown();
                    })
                    .build();

            subscription.encode("test-client", mockClient);
            StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

            // Simulate error (use a regular exception to avoid reconnect logic)
            observer.onError(new RuntimeException("Event store error"));

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNotNull(errorMessage.get());
            assertTrue(errorMessage.get().contains("Event store error"));
        }

        @Test
        void onCompleted_streamCompletes_doesNotThrow() {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .eventsStoreType(EventsStoreType.StartFromFirst)
                    .onReceiveEventCallback(msg -> {})
                    .build();

            subscription.encode("test-client", mockClient);
            StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

            // Simulate stream completion
            assertDoesNotThrow(() -> observer.onCompleted());
        }

        @Test
        void onError_withoutErrorCallback_doesNotThrow() {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .eventsStoreType(EventsStoreType.StartFromFirst)
                    .onReceiveEventCallback(msg -> {})
                    .onErrorCallback(null)
                    .build();

            subscription.encode("test-client", mockClient);
            StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

            // Should not throw even without error callback
            assertDoesNotThrow(() -> observer.onError(new RuntimeException("Test error")));
        }
    }

    @Nested
    class EncodeTests {

        @Test
        void encode_createsValidSubscribeRequest() {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .eventsStoreType(EventsStoreType.StartFromFirst)
                    .onReceiveEventCallback(msg -> {})
                    .build();

            Kubemq.Subscribe subscribeRequest = subscription.encode("test-client", mockClient);

            assertNotNull(subscribeRequest);
            assertEquals("test-channel", subscribeRequest.getChannel());
            assertEquals("test-group", subscribeRequest.getGroup());
            assertEquals("test-client", subscribeRequest.getClientID());
            assertEquals(Kubemq.Subscribe.SubscribeType.EventsStore, subscribeRequest.getSubscribeTypeData());
        }

        @Test
        void encode_withStartAtSequence_setsSequenceValue() {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .eventsStoreType(EventsStoreType.StartAtSequence)
                    .eventsStoreSequenceValue(500)
                    .onReceiveEventCallback(msg -> {})
                    .build();

            Kubemq.Subscribe subscribeRequest = subscription.encode("test-client", mockClient);

            assertNotNull(subscribeRequest);
            assertEquals(500, subscribeRequest.getEventsStoreTypeValue());
        }

        @Test
        void encode_withStartAtTime_setsTimeValue() {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            Instant startTime = Instant.ofEpochSecond(1700000000L);
            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .eventsStoreType(EventsStoreType.StartAtTime)
                    .eventsStoreStartTime(startTime)
                    .onReceiveEventCallback(msg -> {})
                    .build();

            Kubemq.Subscribe subscribeRequest = subscription.encode("test-client", mockClient);

            assertNotNull(subscribeRequest);
            assertEquals(1700000000, subscribeRequest.getEventsStoreTypeValue());
        }

        @Test
        void encode_nullGroup_usesEmptyString() {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .group(null)
                    .eventsStoreType(EventsStoreType.StartFromFirst)
                    .onReceiveEventCallback(msg -> {})
                    .build();

            Kubemq.Subscribe subscribeRequest = subscription.encode("test-client", mockClient);

            assertNotNull(subscribeRequest);
            assertEquals("", subscribeRequest.getGroup());
        }
    }

    @Nested
    class CancelTests {

        @Test
        void cancel_withObserver_callsOnCompleted() {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .eventsStoreType(EventsStoreType.StartFromFirst)
                    .onReceiveEventCallback(msg -> {})
                    .build();

            subscription.encode("test-client", mockClient);

            // Cancel should not throw
            assertDoesNotThrow(() -> subscription.cancel());
        }

        @Test
        void cancel_withoutObserver_doesNotThrow() {
            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .eventsStoreType(EventsStoreType.StartFromFirst)
                    .onReceiveEventCallback(msg -> {})
                    .build();

            // Cancel without encoding (no observer) should not throw
            assertDoesNotThrow(() -> subscription.cancel());
        }
    }

    @Nested
    class CallbackTests {

        @Test
        void raiseOnReceiveMessage_withCallback_invokesCallback() {
            AtomicBoolean callbackInvoked = new AtomicBoolean(false);

            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .eventsStoreType(EventsStoreType.StartFromFirst)
                    .onReceiveEventCallback(msg -> callbackInvoked.set(true))
                    .build();

            EventStoreMessageReceived msg = new EventStoreMessageReceived();
            msg.setId("msg-1");
            msg.setChannel("test-channel");

            subscription.raiseOnReceiveMessage(msg);

            assertTrue(callbackInvoked.get());
        }

        @Test
        void raiseOnReceiveMessage_withNullCallback_doesNotThrow() {
            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .eventsStoreType(EventsStoreType.StartFromFirst)
                    .build();

            EventStoreMessageReceived msg = new EventStoreMessageReceived();
            msg.setId("msg-1");
            msg.setChannel("test-channel");

            assertDoesNotThrow(() -> subscription.raiseOnReceiveMessage(msg));
        }

        @Test
        void raiseOnError_withCallback_invokesCallback() {
            AtomicReference<String> receivedError = new AtomicReference<>();

            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .eventsStoreType(EventsStoreType.StartFromFirst)
                    .onReceiveEventCallback(msg -> {})
                    .onErrorCallback(error -> receivedError.set(error))
                    .build();

            subscription.raiseOnError("Test error message");

            assertEquals("Test error message", receivedError.get());
        }

        @Test
        void raiseOnError_withNullCallback_doesNotThrow() {
            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel("test-channel")
                    .eventsStoreType(EventsStoreType.StartFromFirst)
                    .onReceiveEventCallback(msg -> {})
                    .onErrorCallback(null)
                    .build();

            assertDoesNotThrow(() -> subscription.raiseOnError("Test error"));
        }
    }
}
