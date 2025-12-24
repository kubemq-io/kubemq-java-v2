package io.kubemq.sdk.unit.queues;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.queues.QueueDownstreamHandler;
import io.kubemq.sdk.queues.QueuesPollRequest;
import io.kubemq.sdk.queues.QueuesPollResponse;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QueueDownstreamHandler - StreamObserver callbacks and message handling.
 */
@ExtendWith(MockitoExtension.class)
class QueueDownstreamHandlerTest {

    @Mock
    private KubeMQClient mockClient;

    @Mock
    private kubemqGrpc.kubemqStub mockAsyncStub;

    @Mock
    private StreamObserver<Kubemq.QueuesDownstreamRequest> mockRequestObserver;

    private QueueDownstreamHandler handler;

    @BeforeEach
    void setup() {
        handler = new QueueDownstreamHandler(mockClient);
    }

    @Nested
    @DisplayName("Connect Tests")
    class ConnectTests {

        @Test
        @DisplayName("Q-01: connect creates stream observer")
        void connect_createsStreamObserver() {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
            when(mockAsyncStub.queuesDownstream(any())).thenReturn(mockRequestObserver);

            handler.connect();

            verify(mockAsyncStub).queuesDownstream(any());
        }

        @Test
        @DisplayName("Q-02: connect is idempotent - only connects once")
        void connect_isIdempotent() {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
            when(mockAsyncStub.queuesDownstream(any())).thenReturn(mockRequestObserver);

            handler.connect();
            handler.connect();
            handler.connect();

            verify(mockAsyncStub, times(1)).queuesDownstream(any());
        }

        @Test
        @DisplayName("Q-03: connect handles exception gracefully")
        void connect_handlesExceptionGracefully() {
            when(mockClient.getAsyncClient()).thenThrow(new RuntimeException("Connection failed"));

            // Should not throw, but log error
            assertDoesNotThrow(() -> handler.connect());
        }
    }

    @Nested
    @DisplayName("ReceiveQueuesMessages Tests")
    class ReceiveQueuesMessagesTests {

        @Test
        @DisplayName("Q-04: receiveQueuesMessages sends request and receives response")
        void receiveQueuesMessages_sendsRequestAndReceivesResponse() throws Exception {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
            when(mockClient.getClientId()).thenReturn("test-client");

            // Capture the response observer to simulate response
            ArgumentCaptor<StreamObserver<Kubemq.QueuesDownstreamResponse>> observerCaptor =
                    ArgumentCaptor.forClass(StreamObserver.class);
            when(mockAsyncStub.queuesDownstream(observerCaptor.capture())).thenReturn(mockRequestObserver);

            QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                    .channel("test-queue")
                    .pollMaxMessages(10)
                    .pollWaitTimeoutInSeconds(5)
                    .build();

            // Run receive in a separate thread since it blocks
            CompletableFuture<QueuesPollResponse> resultFuture = CompletableFuture.supplyAsync(() ->
                    handler.receiveQueuesMessages(pollRequest)
            );

            // Wait for connection
            Thread.sleep(200);

            // Capture the request that was sent
            ArgumentCaptor<Kubemq.QueuesDownstreamRequest> requestCaptor =
                    ArgumentCaptor.forClass(Kubemq.QueuesDownstreamRequest.class);
            verify(mockRequestObserver, timeout(2000)).onNext(requestCaptor.capture());

            // Simulate response
            StreamObserver<Kubemq.QueuesDownstreamResponse> capturedObserver = observerCaptor.getValue();
            String refRequestId = requestCaptor.getValue().getRequestID();

            Kubemq.QueuesDownstreamResponse response = Kubemq.QueuesDownstreamResponse.newBuilder()
                    .setRefRequestId(refRequestId)
                    .setTransactionId("txn-123")
                    .setTransactionComplete(true)
                    .setIsError(false)
                    .addMessages(Kubemq.QueueMessage.newBuilder()
                            .setMessageID("msg-1")
                            .setChannel("test-queue")
                            .setBody(ByteString.copyFromUtf8("test-body"))
                            .build())
                    .build();

            capturedObserver.onNext(response);

            QueuesPollResponse pollResponse = resultFuture.get(5, TimeUnit.SECONDS);

            assertNotNull(pollResponse);
            assertFalse(pollResponse.isError());
            assertEquals("txn-123", pollResponse.getTransactionId());
            assertEquals(1, pollResponse.getMessages().size());
        }

        @Test
        @DisplayName("Q-05: receiveQueuesMessages handles error response")
        void receiveQueuesMessages_handlesErrorResponse() throws Exception {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
            when(mockClient.getClientId()).thenReturn("test-client");

            ArgumentCaptor<StreamObserver<Kubemq.QueuesDownstreamResponse>> observerCaptor =
                    ArgumentCaptor.forClass(StreamObserver.class);
            when(mockAsyncStub.queuesDownstream(observerCaptor.capture())).thenReturn(mockRequestObserver);

            QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                    .channel("test-queue")
                    .pollMaxMessages(10)
                    .pollWaitTimeoutInSeconds(5)
                    .build();

            CompletableFuture<QueuesPollResponse> resultFuture = CompletableFuture.supplyAsync(() ->
                    handler.receiveQueuesMessages(pollRequest)
            );

            Thread.sleep(200);

            ArgumentCaptor<Kubemq.QueuesDownstreamRequest> requestCaptor =
                    ArgumentCaptor.forClass(Kubemq.QueuesDownstreamRequest.class);
            verify(mockRequestObserver, timeout(2000)).onNext(requestCaptor.capture());

            StreamObserver<Kubemq.QueuesDownstreamResponse> capturedObserver = observerCaptor.getValue();
            String refRequestId = requestCaptor.getValue().getRequestID();

            Kubemq.QueuesDownstreamResponse response = Kubemq.QueuesDownstreamResponse.newBuilder()
                    .setRefRequestId(refRequestId)
                    .setIsError(true)
                    .setError("Queue not found")
                    .build();

            capturedObserver.onNext(response);

            QueuesPollResponse pollResponse = resultFuture.get(5, TimeUnit.SECONDS);

            assertNotNull(pollResponse);
            assertTrue(pollResponse.isError());
            assertEquals("Queue not found", pollResponse.getError());
        }
    }

    @Nested
    @DisplayName("StreamObserver Callback Tests")
    class StreamObserverCallbackTests {

        @Test
        @DisplayName("Q-06: onError closes stream and completes pending futures with error")
        void onError_closesStreamAndCompletesPendingFuturesWithError() throws Exception {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
            when(mockClient.getClientId()).thenReturn("test-client");

            ArgumentCaptor<StreamObserver<Kubemq.QueuesDownstreamResponse>> observerCaptor =
                    ArgumentCaptor.forClass(StreamObserver.class);
            when(mockAsyncStub.queuesDownstream(observerCaptor.capture())).thenReturn(mockRequestObserver);

            QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                    .channel("test-queue")
                    .pollMaxMessages(10)
                    .pollWaitTimeoutInSeconds(5)
                    .build();

            CompletableFuture<QueuesPollResponse> resultFuture = CompletableFuture.supplyAsync(() ->
                    handler.receiveQueuesMessages(pollRequest)
            );

            Thread.sleep(200);
            verify(mockRequestObserver, timeout(2000)).onNext(any());

            // Simulate error
            StreamObserver<Kubemq.QueuesDownstreamResponse> capturedObserver = observerCaptor.getValue();
            capturedObserver.onError(new RuntimeException("Connection lost"));

            QueuesPollResponse pollResponse = resultFuture.get(5, TimeUnit.SECONDS);

            assertNotNull(pollResponse);
            assertTrue(pollResponse.isError());
            assertTrue(pollResponse.getError().contains("Connection lost"));
        }

        @Test
        @DisplayName("Q-07: onCompleted closes stream and completes pending futures")
        void onCompleted_closesStreamAndCompletesPendingFutures() throws Exception {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
            when(mockClient.getClientId()).thenReturn("test-client");

            ArgumentCaptor<StreamObserver<Kubemq.QueuesDownstreamResponse>> observerCaptor =
                    ArgumentCaptor.forClass(StreamObserver.class);
            when(mockAsyncStub.queuesDownstream(observerCaptor.capture())).thenReturn(mockRequestObserver);

            QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                    .channel("test-queue")
                    .pollMaxMessages(10)
                    .pollWaitTimeoutInSeconds(5)
                    .build();

            CompletableFuture<QueuesPollResponse> resultFuture = CompletableFuture.supplyAsync(() ->
                    handler.receiveQueuesMessages(pollRequest)
            );

            Thread.sleep(200);
            verify(mockRequestObserver, timeout(2000)).onNext(any());

            // Simulate completion
            StreamObserver<Kubemq.QueuesDownstreamResponse> capturedObserver = observerCaptor.getValue();
            capturedObserver.onCompleted();

            QueuesPollResponse pollResponse = resultFuture.get(5, TimeUnit.SECONDS);

            assertNotNull(pollResponse);
            assertTrue(pollResponse.isError());
            assertTrue(pollResponse.getError().contains("Stream completed"));
        }

        @Test
        @DisplayName("Q-08: onNext with unknown request ID does not throw")
        void onNext_unknownRequestId_doesNotThrow() throws Exception {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);

            ArgumentCaptor<StreamObserver<Kubemq.QueuesDownstreamResponse>> observerCaptor =
                    ArgumentCaptor.forClass(StreamObserver.class);
            when(mockAsyncStub.queuesDownstream(observerCaptor.capture())).thenReturn(mockRequestObserver);

            handler.connect();

            // Simulate response with unknown request ID
            StreamObserver<Kubemq.QueuesDownstreamResponse> capturedObserver = observerCaptor.getValue();
            Kubemq.QueuesDownstreamResponse response = Kubemq.QueuesDownstreamResponse.newBuilder()
                    .setRefRequestId("unknown-id")
                    .setTransactionComplete(true)
                    .build();

            // Should not throw
            assertDoesNotThrow(() -> capturedObserver.onNext(response));
        }
    }

    @Nested
    @DisplayName("Send Request Tests")
    class SendRequestTests {

        @Test
        @DisplayName("Q-09: sendRequest auto-connects if not connected")
        void sendRequest_autoConnectsIfNotConnected() throws Exception {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
            when(mockClient.getClientId()).thenReturn("test-client");

            ArgumentCaptor<StreamObserver<Kubemq.QueuesDownstreamResponse>> observerCaptor =
                    ArgumentCaptor.forClass(StreamObserver.class);
            when(mockAsyncStub.queuesDownstream(observerCaptor.capture())).thenReturn(mockRequestObserver);

            QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                    .channel("test-queue")
                    .pollMaxMessages(10)
                    .pollWaitTimeoutInSeconds(5)
                    .build();

            // Start receive which triggers connect
            CompletableFuture<QueuesPollResponse> resultFuture = CompletableFuture.supplyAsync(() ->
                    handler.receiveQueuesMessages(pollRequest)
            );

            Thread.sleep(200);

            // Verify connection was made
            verify(mockAsyncStub).queuesDownstream(any());

            // Complete the test by responding
            ArgumentCaptor<Kubemq.QueuesDownstreamRequest> requestCaptor =
                    ArgumentCaptor.forClass(Kubemq.QueuesDownstreamRequest.class);
            verify(mockRequestObserver, timeout(2000)).onNext(requestCaptor.capture());

            StreamObserver<Kubemq.QueuesDownstreamResponse> capturedObserver = observerCaptor.getValue();
            String refRequestId = requestCaptor.getValue().getRequestID();

            Kubemq.QueuesDownstreamResponse response = Kubemq.QueuesDownstreamResponse.newBuilder()
                    .setRefRequestId(refRequestId)
                    .setTransactionComplete(true)
                    .build();

            capturedObserver.onNext(response);
            resultFuture.get(5, TimeUnit.SECONDS);
        }
    }

    @Nested
    @DisplayName("Message Decoding Tests")
    class MessageDecodingTests {

        @Test
        @DisplayName("Q-10: response with multiple messages decodes all")
        void response_withMultipleMessages_decodesAll() throws Exception {
            when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
            when(mockClient.getClientId()).thenReturn("test-client");

            ArgumentCaptor<StreamObserver<Kubemq.QueuesDownstreamResponse>> observerCaptor =
                    ArgumentCaptor.forClass(StreamObserver.class);
            when(mockAsyncStub.queuesDownstream(observerCaptor.capture())).thenReturn(mockRequestObserver);

            QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                    .channel("test-queue")
                    .pollMaxMessages(10)
                    .pollWaitTimeoutInSeconds(5)
                    .build();

            CompletableFuture<QueuesPollResponse> resultFuture = CompletableFuture.supplyAsync(() ->
                    handler.receiveQueuesMessages(pollRequest)
            );

            Thread.sleep(200);

            ArgumentCaptor<Kubemq.QueuesDownstreamRequest> requestCaptor =
                    ArgumentCaptor.forClass(Kubemq.QueuesDownstreamRequest.class);
            verify(mockRequestObserver, timeout(2000)).onNext(requestCaptor.capture());

            StreamObserver<Kubemq.QueuesDownstreamResponse> capturedObserver = observerCaptor.getValue();
            String refRequestId = requestCaptor.getValue().getRequestID();

            // Response with multiple messages
            Kubemq.QueuesDownstreamResponse response = Kubemq.QueuesDownstreamResponse.newBuilder()
                    .setRefRequestId(refRequestId)
                    .setTransactionId("txn-123")
                    .setTransactionComplete(true)
                    .addMessages(Kubemq.QueueMessage.newBuilder()
                            .setMessageID("msg-1")
                            .setBody(ByteString.copyFromUtf8("body-1"))
                            .build())
                    .addMessages(Kubemq.QueueMessage.newBuilder()
                            .setMessageID("msg-2")
                            .setBody(ByteString.copyFromUtf8("body-2"))
                            .build())
                    .addMessages(Kubemq.QueueMessage.newBuilder()
                            .setMessageID("msg-3")
                            .setBody(ByteString.copyFromUtf8("body-3"))
                            .build())
                    .build();

            capturedObserver.onNext(response);

            QueuesPollResponse pollResponse = resultFuture.get(5, TimeUnit.SECONDS);

            assertNotNull(pollResponse);
            assertEquals(3, pollResponse.getMessages().size());
        }
    }
}
