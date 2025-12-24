package io.kubemq.sdk.unit.cq;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.cq.QueriesSubscription;
import io.kubemq.sdk.cq.QueryMessageReceived;
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
 * Unit tests for QueriesSubscription validation and StreamObserver callbacks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueriesSubscriptionTest {

    @Mock
    private CQClient mockClient;

    @Nested
    class ValidationTests {

        @Test
        void validate_withValidSubscription_passes() {
            QueriesSubscription subscription = QueriesSubscription.builder()
                    .channel("test-channel")
                    .onReceiveQueryCallback(qry -> {})
                    .build();

            assertDoesNotThrow(subscription::validate);
        }

        @Test
        void validate_withGroupSet_passes() {
            QueriesSubscription subscription = QueriesSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .onReceiveQueryCallback(qry -> {})
                    .build();

            assertDoesNotThrow(subscription::validate);
        }

        @Test
        void validate_withErrorCallback_passes() {
            QueriesSubscription subscription = QueriesSubscription.builder()
                    .channel("test-channel")
                    .onReceiveQueryCallback(qry -> {})
                    .onErrorCallback(error -> {})
                    .build();

            assertDoesNotThrow(subscription::validate);
        }

        @Test
        void validate_withNullChannel_throws() {
            QueriesSubscription subscription = QueriesSubscription.builder()
                    .onReceiveQueryCallback(qry -> {})
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    subscription::validate
            );
            assertTrue(ex.getMessage().contains("channel"));
        }

        @Test
        void validate_withEmptyChannel_throws() {
            QueriesSubscription subscription = QueriesSubscription.builder()
                    .channel("")
                    .onReceiveQueryCallback(qry -> {})
                    .build();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    subscription::validate
            );
            assertTrue(ex.getMessage().contains("channel"));
        }

        @Test
        void validate_withNullCallback_throws() {
            QueriesSubscription subscription = QueriesSubscription.builder()
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
            QueriesSubscription subscription = QueriesSubscription.builder()
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
            QueriesSubscription subscription = QueriesSubscription.builder()
                    .channel("my-channel")
                    .group("my-group")
                    .onReceiveQueryCallback(qry -> {})
                    .build();

            String str = subscription.toString();

            assertTrue(str.contains("my-channel"));
            assertTrue(str.contains("my-group"));
        }
    }

    @Nested
    class StreamObserverTests {

        @Test
        void onNext_receivesQuery_invokesCallback() throws InterruptedException {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<QueryMessageReceived> receivedMessage = new AtomicReference<>();

            QueriesSubscription subscription = QueriesSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .onReceiveQueryCallback(msg -> {
                        receivedMessage.set(msg);
                        latch.countDown();
                    })
                    .build();

            // Encode to create the observer
            Kubemq.Subscribe subscribeRequest = subscription.encode("test-client", mockClient);
            StreamObserver<Kubemq.Request> observer = subscription.getObserver();

            // Simulate receiving a query
            Kubemq.Request request = Kubemq.Request.newBuilder()
                    .setRequestID("req-456")
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
            assertEquals("req-456", receivedMessage.get().getId());
            assertEquals("test-channel", receivedMessage.get().getChannel());
            assertEquals("test-metadata", receivedMessage.get().getMetadata());
        }

        @Test
        void onError_receivesError_invokesErrorCallback() throws InterruptedException {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> errorMessage = new AtomicReference<>();

            QueriesSubscription subscription = QueriesSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .onReceiveQueryCallback(msg -> {})
                    .onErrorCallback(error -> {
                        errorMessage.set(error);
                        latch.countDown();
                    })
                    .build();

            subscription.encode("test-client", mockClient);
            StreamObserver<Kubemq.Request> observer = subscription.getObserver();

            // Simulate error
            observer.onError(new RuntimeException("Query error"));

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNotNull(errorMessage.get());
            assertTrue(errorMessage.get().contains("Query error"));
        }

        @Test
        void onCompleted_streamCompletes_doesNotThrow() {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            QueriesSubscription subscription = QueriesSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .onReceiveQueryCallback(msg -> {})
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

            QueriesSubscription subscription = QueriesSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .onReceiveQueryCallback(msg -> {})
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

            QueriesSubscription subscription = QueriesSubscription.builder()
                    .channel("test-channel")
                    .group("test-group")
                    .onReceiveQueryCallback(msg -> {})
                    .build();

            Kubemq.Subscribe subscribeRequest = subscription.encode("test-client", mockClient);

            assertNotNull(subscribeRequest);
            assertEquals("test-channel", subscribeRequest.getChannel());
            assertEquals("test-group", subscribeRequest.getGroup());
            assertEquals("test-client", subscribeRequest.getClientID());
            assertEquals(Kubemq.Subscribe.SubscribeType.Queries, subscribeRequest.getSubscribeTypeData());
        }

        @Test
        void encode_nullGroup_usesEmptyString() {
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getReconnectIntervalInMillis()).thenReturn(1000L);

            QueriesSubscription subscription = QueriesSubscription.builder()
                    .channel("test-channel")
                    .group(null)
                    .onReceiveQueryCallback(msg -> {})
                    .build();

            Kubemq.Subscribe subscribeRequest = subscription.encode("test-client", mockClient);

            assertNotNull(subscribeRequest);
            assertEquals("", subscribeRequest.getGroup());
        }
    }
}
