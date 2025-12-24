package io.kubemq.sdk.unit.cq;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.cq.CommandMessageReceived;
import io.kubemq.sdk.cq.CommandsSubscription;
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
 * Unit tests for CommandsSubscription validation and StreamObserver callbacks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommandsSubscriptionTest {

    @Mock
    private CQClient mockClient;

    @Nested
    class ValidationTests {

        @Test
        void validate_withValidSubscription_passes() {
            CommandsSubscription subscription = CommandsSubscription.builder()
                    .channel("test-channel")
                    .onReceiveCommandCallback(cmd -> {})
                    .build();

            assertDoesNotThrow(subscription::validate);
        }

        @Test
        void validate_withGroupSet_passes() {
            CommandsSubscription subscription = CommandsSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .onReceiveCommandCallback(cmd -> {})
                    .build();

            assertDoesNotThrow(subscription::validate);
        }

        @Test
        void validate_withErrorCallback_passes() {
            CommandsSubscription subscription = CommandsSubscription.builder()
                    .channel("test-channel")
                    .onReceiveCommandCallback(cmd -> {})
                    .onErrorCallback(error -> {})
                    .build();

            assertDoesNotThrow(subscription::validate);
        }

        @Test
        void validate_withNullChannel_throws() {
            CommandsSubscription subscription = CommandsSubscription.builder()
                    .onReceiveCommandCallback(cmd -> {})
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    subscription::validate
            );
            assertTrue(ex.getMessage().contains("channel"));
        }

        @Test
        void validate_withEmptyChannel_throws() {
            CommandsSubscription subscription = CommandsSubscription.builder()
                    .channel("")
                    .onReceiveCommandCallback(cmd -> {})
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    subscription::validate
            );
            assertTrue(ex.getMessage().contains("channel"));
        }

        @Test
        void validate_withNullCallback_throws() {
            CommandsSubscription subscription = CommandsSubscription.builder()
                    .channel("test-channel")
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    subscription::validate
            );
            assertTrue(ex.getMessage().contains("callback"));
        }

        @Test
        void validate_withErrorCallbackOnly_throws() {
            CommandsSubscription subscription = CommandsSubscription.builder()
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
            CommandsSubscription subscription = CommandsSubscription.builder()
                    .channel("my-channel")
                    .group("my-group")
                    .onReceiveCommandCallback(cmd -> {})
                    .build();

            String str = subscription.toString();

            assertTrue(str.contains("my-channel"));
            assertTrue(str.contains("my-group"));
        }
    }

    @Nested
    class StreamObserverTests {

        @Test
        void onNext_receivesCommand_invokesCallback() throws InterruptedException {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<CommandMessageReceived> receivedMessage = new AtomicReference<>();

            CommandsSubscription subscription = CommandsSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .onReceiveCommandCallback(msg -> {
                        receivedMessage.set(msg);
                        latch.countDown();
                    })
                    .build();

            // Encode to create the observer
            Kubemq.Subscribe subscribeRequest = subscription.encode("test-client", mockClient);
            StreamObserver<Kubemq.Request> observer = subscription.getObserver();

            // Simulate receiving a command
            Kubemq.Request request = Kubemq.Request.newBuilder()
                    .setRequestID("req-123")
                    .setChannel("test-channel")
                    .setClientID("sender-client")
                    .setMetadata("test-metadata")
                    .setBody(ByteString.copyFromUtf8("test-body"))
                    .setReplyChannel("reply-channel")
                    .putTags("key1", "value1")
                    .build();

            observer.onNext(request);

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNotNull(receivedMessage.get());
            assertEquals("req-123", receivedMessage.get().getId());
            assertEquals("test-channel", receivedMessage.get().getChannel());
            assertEquals("test-metadata", receivedMessage.get().getMetadata());
        }

        @Test
        void onError_receivesError_invokesErrorCallback() throws InterruptedException {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> errorMessage = new AtomicReference<>();

            CommandsSubscription subscription = CommandsSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .onReceiveCommandCallback(msg -> {})
                    .onErrorCallback(error -> {
                        errorMessage.set(error);
                        latch.countDown();
                    })
                    .build();

            subscription.encode("test-client", mockClient);
            StreamObserver<Kubemq.Request> observer = subscription.getObserver();

            // Simulate error
            observer.onError(new RuntimeException("Test error"));

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNotNull(errorMessage.get());
            assertTrue(errorMessage.get().contains("Test error"));
        }

        @Test
        void onCompleted_streamCompletes_doesNotThrow() {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            CommandsSubscription subscription = CommandsSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .onReceiveCommandCallback(msg -> {})
                    .onErrorCallback(error -> {})
                    .build();

            subscription.encode("test-client", mockClient);
            StreamObserver<Kubemq.Request> observer = subscription.getObserver();

            // Simulate stream completion - should not throw
            assertDoesNotThrow(() -> observer.onCompleted());
        }

        @Test
        void onError_withoutErrorCallback_doesNotThrow() {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            CommandsSubscription subscription = CommandsSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .onReceiveCommandCallback(msg -> {})
                    .onErrorCallback(null)
                    .build();

            subscription.encode("test-client", mockClient);
            StreamObserver<Kubemq.Request> observer = subscription.getObserver();

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

            CommandsSubscription subscription = CommandsSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .onReceiveCommandCallback(msg -> {})
                    .build();

            Kubemq.Subscribe subscribeRequest = subscription.encode("test-client", mockClient);

            assertNotNull(subscribeRequest);
            assertEquals("test-channel", subscribeRequest.getChannel());
            assertEquals("test-group", subscribeRequest.getGroup());
            assertEquals("test-client", subscribeRequest.getClientID());
            assertEquals(Kubemq.Subscribe.SubscribeType.Commands, subscribeRequest.getSubscribeTypeData());
        }

        @Test
        void encode_nullGroup_usesEmptyString() {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            CommandsSubscription subscription = CommandsSubscription.builder()
                    .channel("test-channel")
                    .group(null)
                    .onReceiveCommandCallback(msg -> {})
                    .build();

            Kubemq.Subscribe subscribeRequest = subscription.encode("test-client", mockClient);

            assertNotNull(subscribeRequest);
            assertEquals("", subscribeRequest.getGroup());
        }
    }
}
