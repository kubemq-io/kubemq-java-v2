package io.kubemq.sdk.unit.queues;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.exception.KubeMQException;
import io.kubemq.sdk.queues.QueueDownstreamHandler;
import io.kubemq.sdk.queues.QueuesPollRequest;
import io.kubemq.sdk.queues.QueuesPollResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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

@ExtendWith(MockitoExtension.class)
class QueueDownstreamHandlerCoverageTest {

  @Mock private KubeMQClient mockClient;

  @Mock private kubemqGrpc.kubemqStub mockAsyncStub;

  @Mock private StreamObserver<Kubemq.QueuesDownstreamRequest> mockRequestObserver;

  private QueueDownstreamHandler handler;

  @BeforeEach
  void setup() {
    handler = new QueueDownstreamHandler(mockClient);
  }

  private QueuesPollRequest createValidRequest() {
    return QueuesPollRequest.builder()
        .channel("test-queue")
        .pollMaxMessages(10)
        .pollWaitTimeoutInSeconds(1)
        .build();
  }

  @Nested
  @DisplayName("receiveQueuesMessages Timeout Path")
  class ReceiveTimeoutTests {

    @Test
    @DisplayName("receiveQueuesMessages returns error response on timeout")
    void receiveQueuesMessages_timeout_returnsErrorResponse() {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");
      when(mockClient.getRequestTimeoutSeconds()).thenReturn(1);
      when(mockAsyncStub.queuesDownstream(any())).thenReturn(mockRequestObserver);

      QueuesPollResponse result = handler.receiveQueuesMessages(createValidRequest());

      assertNotNull(result);
      assertTrue(result.isError());
      assertTrue(result.getError().contains("timed out"));
    }
  }

  @Nested
  @DisplayName("receiveQueuesMessages Generic Exception Path")
  class ReceiveGenericExceptionTests {

    @Test
    @DisplayName("receiveQueuesMessagesAsync completes exceptionally when sendRequest throws")
    void receiveQueuesMessagesAsync_sendRequestThrows_completesExceptionally() {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");

      when(mockAsyncStub.queuesDownstream(any())).thenReturn(mockRequestObserver);
      doThrow(new RuntimeException("Stream broken")).when(mockRequestObserver).onNext(any());

      CompletableFuture<QueuesPollResponse> future =
          handler.receiveQueuesMessagesAsync(createValidRequest());

      assertTrue(future.isCompletedExceptionally());
    }

    @Test
    @DisplayName("receiveQueuesMessages wraps generic ExecutionException in KubeMQException")
    void receiveQueuesMessages_genericException_wrapsInKubeMQException() {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");
      when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

      when(mockAsyncStub.queuesDownstream(any())).thenReturn(mockRequestObserver);
      doThrow(new RuntimeException("Unexpected error")).when(mockRequestObserver).onNext(any());

      KubeMQException thrown =
          assertThrows(
              KubeMQException.class, () -> handler.receiveQueuesMessages(createValidRequest()));
      assertTrue(thrown.getMessage().contains("Failed to get response"));
    }
  }

  @Nested
  @DisplayName("receiveQueuesMessagesAsync Success Path")
  class ReceiveAsyncSuccessTests {

    @Test
    @DisplayName("receiveQueuesMessagesAsync returns CompletableFuture")
    void receiveQueuesMessagesAsync_returnsCompletableFuture() {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");
      when(mockAsyncStub.queuesDownstream(any())).thenReturn(mockRequestObserver);

      CompletableFuture<QueuesPollResponse> future =
          handler.receiveQueuesMessagesAsync(createValidRequest());

      assertNotNull(future);
      assertFalse(future.isDone());
    }

    @Test
    @DisplayName("receiveQueuesMessagesAsync resolves with response when server responds")
    void receiveQueuesMessagesAsync_resolvesWithResponse() throws Exception {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");

      @SuppressWarnings("unchecked")
      ArgumentCaptor<StreamObserver<Kubemq.QueuesDownstreamResponse>> observerCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.queuesDownstream(observerCaptor.capture()))
          .thenReturn(mockRequestObserver);

      CompletableFuture<QueuesPollResponse> future =
          handler.receiveQueuesMessagesAsync(createValidRequest());

      Thread.sleep(200);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Kubemq.QueuesDownstreamRequest> requestCaptor =
          ArgumentCaptor.forClass(Kubemq.QueuesDownstreamRequest.class);
      verify(mockRequestObserver, timeout(2000)).onNext(requestCaptor.capture());

      StreamObserver<Kubemq.QueuesDownstreamResponse> capturedObserver = observerCaptor.getValue();
      String refRequestId = requestCaptor.getValue().getRequestID();

      Kubemq.QueuesDownstreamResponse response =
          Kubemq.QueuesDownstreamResponse.newBuilder()
              .setRefRequestId(refRequestId)
              .setTransactionId("txn-async")
              .setTransactionComplete(true)
              .setIsError(false)
              .build();

      capturedObserver.onNext(response);

      QueuesPollResponse pollResponse = future.get(5, TimeUnit.SECONDS);
      assertNotNull(pollResponse);
      assertFalse(pollResponse.isError());
      assertEquals("txn-async", pollResponse.getTransactionId());
    }
  }
}
