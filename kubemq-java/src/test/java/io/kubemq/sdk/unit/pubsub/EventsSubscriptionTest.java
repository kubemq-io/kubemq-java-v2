package io.kubemq.sdk.unit.pubsub;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.pubsub.EventMessageReceived;
import io.kubemq.sdk.pubsub.EventsSubscription;
import io.kubemq.sdk.pubsub.PubSubClient;
import kubemq.Kubemq;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for EventsSubscription validation and StreamObserver callbacks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventsSubscriptionTest {

    @Mock
    private PubSubClient mockClient;

    @Nested
    class ValidationTests {

        @Test
        void validate_withValidSubscription_passes() {
            EventsSubscription subscription = EventsSubscription.builder()
                    .channel("test-channel")
                    .onReceiveEventCallback(event -> {})
                    .build();

            assertDoesNotThrow(subscription::validate);
        }

        @Test
        void validate_withGroupSet_passes() {
            EventsSubscription subscription = EventsSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .onReceiveEventCallback(event -> {})
                    .build();

            assertDoesNotThrow(subscription::validate);
        }

        @Test
        void validate_withNullChannel_throws() {
            EventsSubscription subscription = EventsSubscription.builder()
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
            EventsSubscription subscription = EventsSubscription.builder()
                    .channel("")
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
            EventsSubscription subscription = EventsSubscription.builder()
                    .channel("test-channel")
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    subscription::validate
            );
            assertTrue(ex.getMessage().contains("Callback") || ex.getMessage().contains("callback"));
        }

        @Test
        void validate_withErrorCallbackOnly_throws() {
            EventsSubscription subscription = EventsSubscription.builder()
                    .channel("test-channel")
                    .onErrorCallback(error -> {})
                    .build();

            assertThrows(IllegalArgumentException.class, subscription::validate);
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toString_includesChannelAndGroup() {
            EventsSubscription subscription = EventsSubscription.builder()
                    .channel("my-channel")
                    .group("my-group")
                    .onReceiveEventCallback(event -> {})
                    .build();

            String str = subscription.toString();

            assertTrue(str.contains("my-channel"));
            assertTrue(str.contains("my-group"));
        }
    }

    @Nested
    class StreamObserverTests {

        @Test
        void onNext_receivesEvent_invokesCallback() throws InterruptedException {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<EventMessageReceived> receivedMessage = new AtomicReference<>();

            EventsSubscription subscription = EventsSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
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
                    .setEventID("event-123")
                    .setChannel("test-channel")
                    .setMetadata("test-metadata")
                    .setBody(ByteString.copyFromUtf8("test-body"))
                    .putTags("key1", "value1")
                    .build();

            observer.onNext(event);

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNotNull(receivedMessage.get());
            assertEquals("event-123", receivedMessage.get().getId());
            assertEquals("test-channel", receivedMessage.get().getChannel());
            assertEquals("test-metadata", receivedMessage.get().getMetadata());
        }

        @Test
        void onError_receivesError_invokesErrorCallback() throws InterruptedException {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> errorMessage = new AtomicReference<>();

            EventsSubscription subscription = EventsSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .onReceiveEventCallback(msg -> {})
                    .onErrorCallback(error -> {
                        errorMessage.set(error);
                        latch.countDown();
                    })
                    .build();

            subscription.encode("test-client", mockClient);
            StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

            // Simulate error
            observer.onError(new RuntimeException("Event error"));

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNotNull(errorMessage.get());
            assertTrue(errorMessage.get().contains("Event error"));
        }

        @Test
        void onCompleted_streamCompletes_doesNotThrow() {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            EventsSubscription subscription = EventsSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .onReceiveEventCallback(msg -> {})
                    .onErrorCallback(error -> {})
                    .build();

            subscription.encode("test-client", mockClient);
            StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

            // Simulate stream completion - should not throw
            assertDoesNotThrow(() -> observer.onCompleted());
        }

        @Test
        void onError_withoutErrorCallback_doesNotThrow() {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            EventsSubscription subscription = EventsSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
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

            EventsSubscription subscription = EventsSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .onReceiveEventCallback(msg -> {})
                    .build();

            Kubemq.Subscribe subscribeRequest = subscription.encode("test-client", mockClient);

            assertNotNull(subscribeRequest);
            assertEquals("test-channel", subscribeRequest.getChannel());
            assertEquals("test-group", subscribeRequest.getGroup());
            assertEquals("test-client", subscribeRequest.getClientID());
            assertEquals(Kubemq.Subscribe.SubscribeType.Events, subscribeRequest.getSubscribeTypeData());
        }
    }
}
