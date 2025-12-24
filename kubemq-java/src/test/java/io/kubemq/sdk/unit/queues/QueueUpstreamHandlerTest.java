package io.kubemq.sdk.unit.queues;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.queues.QueueMessage;
import io.kubemq.sdk.queues.QueueSendResult;
import io.kubemq.sdk.queues.QueueUpstreamHandler;
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
 * Unit tests for QueueUpstreamHandler - StreamObserver callbacks and message sending.
 */
@ExtendWith(MockitoExtension.class)
class QueueUpstreamHandlerTest {

    @Mock
    private KubeMQClient mockClient;

    @Mock
    private kubemqGrpc.kubemqStub mockAsyncStub;

    @Mock
    private StreamObserver<Kubemq.QueuesUpstreamRequest> mockRequestObserver;

    private QueueUpstreamHandler handler;

    @BeforeEach
    void setup() {
        handler = new QueueUpstreamHandler(mockClient);
    }

    @Nested
    @DisplayName("Connect Tests")
    class ConnectTests {

        @Test
        @DisplayName("Q-11: connect creates stream observer")
        void connect_createsStreamObserver() {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
            when(mockAsyncStub.queuesUpstream(any())).thenReturn(mockRequestObserver);

            handler.connect();

            verify(mockAsyncStub).queuesUpstream(any());
        }

        @Test
        @DisplayName("Q-12: connect is idempotent - only connects once")
        void connect_isIdempotent() {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
            when(mockAsyncStub.queuesUpstream(any())).thenReturn(mockRequestObserver);

            handler.connect();
            handler.connect();
            handler.connect();

            verify(mockAsyncStub, times(1)).queuesUpstream(any());
        }

        @Test
        @DisplayName("Q-13: connect handles exception gracefully")
        void connect_handlesExceptionGracefully() {
            when(mockClient.getAsyncClient()).thenThrow(new RuntimeException("Connection failed"));

            // Should not throw, but log error
            assertDoesNotThrow(() -> handler.connect());
        }
    }

    @Nested
    @DisplayName("SendQueuesMessage Tests")
    class SendQueuesMessageTests {

        @Test
        @DisplayName("Q-14: sendQueuesMessage sends message and receives result")
        void sendQueuesMessage_sendsMessageAndReceivesResult() throws Exception {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

            ArgumentCaptor<StreamObserver<Kubemq.QueuesUpstreamResponse>> observerCaptor =
                    ArgumentCaptor.forClass(StreamObserver.class);
            when(mockAsyncStub.queuesUpstream(observerCaptor.capture())).thenReturn(mockRequestObserver);

            QueueMessage message = QueueMessage.builder()
                    .channel("test-queue")
                    .body("test-body".getBytes())
                    .build();

            CompletableFuture<QueueSendResult> resultFuture = CompletableFuture.supplyAsync(() ->
                    handler.sendQueuesMessage(message)
            );

            Thread.sleep(200);

            ArgumentCaptor<Kubemq.QueuesUpstreamRequest> requestCaptor =
                    ArgumentCaptor.forClass(Kubemq.QueuesUpstreamRequest.class);
            verify(mockRequestObserver, timeout(2000)).onNext(requestCaptor.capture());

            StreamObserver<Kubemq.QueuesUpstreamResponse> capturedObserver = observerCaptor.getValue();
            String refRequestId = requestCaptor.getValue().getRequestID();

            Kubemq.QueuesUpstreamResponse response = Kubemq.QueuesUpstreamResponse.newBuilder()
                    .setRefRequestID(refRequestId)
                    .setIsError(false)
                    .addResults(Kubemq.SendQueueMessageResult.newBuilder()
                            .setMessageID("msg-123")
                            .setSentAt(System.currentTimeMillis() * 1_000_000)
                            .setIsError(false)
                            .build())
                    .build();

            capturedObserver.onNext(response);

            QueueSendResult sendResult = resultFuture.get(5, TimeUnit.SECONDS);

            assertNotNull(sendResult);
            assertFalse(sendResult.isError());
            assertEquals("msg-123", sendResult.getId());
        }

        @Test
        @DisplayName("Q-15: sendQueuesMessage handles error response")
        void sendQueuesMessage_handlesErrorResponse() throws Exception {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

            ArgumentCaptor<StreamObserver<Kubemq.QueuesUpstreamResponse>> observerCaptor =
                    ArgumentCaptor.forClass(StreamObserver.class);
            when(mockAsyncStub.queuesUpstream(observerCaptor.capture())).thenReturn(mockRequestObserver);

            QueueMessage message = QueueMessage.builder()
                    .channel("test-queue")
                    .body("test-body".getBytes())
                    .build();

            CompletableFuture<QueueSendResult> resultFuture = CompletableFuture.supplyAsync(() ->
                    handler.sendQueuesMessage(message)
            );

            Thread.sleep(200);

            ArgumentCaptor<Kubemq.QueuesUpstreamRequest> requestCaptor =
                    ArgumentCaptor.forClass(Kubemq.QueuesUpstreamRequest.class);
            verify(mockRequestObserver, timeout(2000)).onNext(requestCaptor.capture());

            StreamObserver<Kubemq.QueuesUpstreamResponse> capturedObserver = observerCaptor.getValue();
            String refRequestId = requestCaptor.getValue().getRequestID();

            Kubemq.QueuesUpstreamResponse response = Kubemq.QueuesUpstreamResponse.newBuilder()
                    .setRefRequestID(refRequestId)
                    .setIsError(true)
                    .setError("Queue not found")
                    .build();

            capturedObserver.onNext(response);

            QueueSendResult sendResult = resultFuture.get(5, TimeUnit.SECONDS);

            assertNotNull(sendResult);
            assertTrue(sendResult.isError());
            assertEquals("Queue not found", sendResult.getError());
        }

        @Test
        @DisplayName("Q-16: sendQueuesMessage handles no results response")
        void sendQueuesMessage_handlesNoResultsResponse() throws Exception {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

            ArgumentCaptor<StreamObserver<Kubemq.QueuesUpstreamResponse>> observerCaptor =
                    ArgumentCaptor.forClass(StreamObserver.class);
            when(mockAsyncStub.queuesUpstream(observerCaptor.capture())).thenReturn(mockRequestObserver);

            QueueMessage message = QueueMessage.builder()
                    .channel("test-queue")
                    .body("test-body".getBytes())
                    .build();

            CompletableFuture<QueueSendResult> resultFuture = CompletableFuture.supplyAsync(() ->
                    handler.sendQueuesMessage(message)
            );

            Thread.sleep(200);

            ArgumentCaptor<Kubemq.QueuesUpstreamRequest> requestCaptor =
                    ArgumentCaptor.forClass(Kubemq.QueuesUpstreamRequest.class);
            verify(mockRequestObserver, timeout(2000)).onNext(requestCaptor.capture());

            StreamObserver<Kubemq.QueuesUpstreamResponse> capturedObserver = observerCaptor.getValue();
            String refRequestId = requestCaptor.getValue().getRequestID();

            // Response with no results
            Kubemq.QueuesUpstreamResponse response = Kubemq.QueuesUpstreamResponse.newBuilder()
                    .setRefRequestID(refRequestId)
                    .setIsError(false)
                    // No results added
                    .build();

            capturedObserver.onNext(response);

            QueueSendResult sendResult = resultFuture.get(5, TimeUnit.SECONDS);

            assertNotNull(sendResult);
            assertTrue(sendResult.isError());
            assertEquals("no results", sendResult.getError());
        }
    }

    @Nested
    @DisplayName("StreamObserver Callback Tests")
    class StreamObserverCallbackTests {

        @Test
        @DisplayName("Q-17: onError closes stream and completes pending futures with error")
        void onError_closesStreamAndCompletesPendingFuturesWithError() throws Exception {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

            ArgumentCaptor<StreamObserver<Kubemq.QueuesUpstreamResponse>> observerCaptor =
                    ArgumentCaptor.forClass(StreamObserver.class);
            when(mockAsyncStub.queuesUpstream(observerCaptor.capture())).thenReturn(mockRequestObserver);

            QueueMessage message = QueueMessage.builder()
                    .channel("test-queue")
                    .body("test-body".getBytes())
                    .build();

            CompletableFuture<QueueSendResult> resultFuture = CompletableFuture.supplyAsync(() ->
                    handler.sendQueuesMessage(message)
            );

            Thread.sleep(200);
            verify(mockRequestObserver, timeout(2000)).onNext(any());

            // Simulate error
            StreamObserver<Kubemq.QueuesUpstreamResponse> capturedObserver = observerCaptor.getValue();
            capturedObserver.onError(new RuntimeException("Connection lost"));

            QueueSendResult sendResult = resultFuture.get(5, TimeUnit.SECONDS);

            assertNotNull(sendResult);
            assertTrue(sendResult.isError());
            assertTrue(sendResult.getError().contains("Connection lost"));
        }

        @Test
        @DisplayName("Q-18: onCompleted closes stream and completes pending futures")
        void onCompleted_closesStreamAndCompletesPendingFutures() throws Exception {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

            ArgumentCaptor<StreamObserver<Kubemq.QueuesUpstreamResponse>> observerCaptor =
                    ArgumentCaptor.forClass(StreamObserver.class);
            when(mockAsyncStub.queuesUpstream(observerCaptor.capture())).thenReturn(mockRequestObserver);

            QueueMessage message = QueueMessage.builder()
                    .channel("test-queue")
                    .body("test-body".getBytes())
                    .build();

            CompletableFuture<QueueSendResult> resultFuture = CompletableFuture.supplyAsync(() ->
                    handler.sendQueuesMessage(message)
            );

            Thread.sleep(200);
            verify(mockRequestObserver, timeout(2000)).onNext(any());

            // Simulate completion
            StreamObserver<Kubemq.QueuesUpstreamResponse> capturedObserver = observerCaptor.getValue();
            capturedObserver.onCompleted();

            QueueSendResult sendResult = resultFuture.get(5, TimeUnit.SECONDS);

            assertNotNull(sendResult);
            assertTrue(sendResult.isError());
            assertTrue(sendResult.getError().contains("Stream completed"));
        }

        @Test
        @DisplayName("Q-19: onNext with unknown request ID does not throw")
        void onNext_unknownRequestId_doesNotThrow() throws Exception {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);

            ArgumentCaptor<StreamObserver<Kubemq.QueuesUpstreamResponse>> observerCaptor =
                    ArgumentCaptor.forClass(StreamObserver.class);
            when(mockAsyncStub.queuesUpstream(observerCaptor.capture())).thenReturn(mockRequestObserver);

            handler.connect();

            // Simulate response with unknown request ID
            StreamObserver<Kubemq.QueuesUpstreamResponse> capturedObserver = observerCaptor.getValue();
            Kubemq.QueuesUpstreamResponse response = Kubemq.QueuesUpstreamResponse.newBuilder()
                    .setRefRequestID("unknown-id")
                    .setIsError(false)
                    .build();

            // Should not throw
            assertDoesNotThrow(() -> capturedObserver.onNext(response));
        }
    }

    @Nested
    @DisplayName("Auto-Connect Tests")
    class AutoConnectTests {

        @Test
        @DisplayName("Q-20: sendQueuesMessage auto-connects if not connected")
        void sendQueuesMessage_autoConnectsIfNotConnected() throws Exception {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
            when(mockClient.getClientId()).thenReturn("test-client");
            when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

            ArgumentCaptor<StreamObserver<Kubemq.QueuesUpstreamResponse>> observerCaptor =
                    ArgumentCaptor.forClass(StreamObserver.class);
            when(mockAsyncStub.queuesUpstream(observerCaptor.capture())).thenReturn(mockRequestObserver);

            QueueMessage message = QueueMessage.builder()
                    .channel("test-queue")
                    .body("test-body".getBytes())
                    .build();

            CompletableFuture<QueueSendResult> resultFuture = CompletableFuture.supplyAsync(() ->
                    handler.sendQueuesMessage(message)
            );

            Thread.sleep(200);

            // Verify connection was made
            verify(mockAsyncStub).queuesUpstream(any());

            // Complete the test
            ArgumentCaptor<Kubemq.QueuesUpstreamRequest> requestCaptor =
                    ArgumentCaptor.forClass(Kubemq.QueuesUpstreamRequest.class);
            verify(mockRequestObserver, timeout(2000)).onNext(requestCaptor.capture());

            StreamObserver<Kubemq.QueuesUpstreamResponse> capturedObserver = observerCaptor.getValue();
            String refRequestId = requestCaptor.getValue().getRequestID();

            Kubemq.QueuesUpstreamResponse response = Kubemq.QueuesUpstreamResponse.newBuilder()
                    .setRefRequestID(refRequestId)
                    .addResults(Kubemq.SendQueueMessageResult.newBuilder()
                            .setMessageID("msg-123")
                            .build())
                    .build();

            capturedObserver.onNext(response);
            resultFuture.get(5, TimeUnit.SECONDS);
        }
    }
}
