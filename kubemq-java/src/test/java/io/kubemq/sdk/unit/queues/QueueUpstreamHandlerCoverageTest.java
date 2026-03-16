package io.kubemq.sdk.unit.queues;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.exception.KubeMQException;
import io.kubemq.sdk.queues.QueueMessage;
import io.kubemq.sdk.queues.QueueSendResult;
import io.kubemq.sdk.queues.QueueUpstreamHandler;
import java.util.List;
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
class QueueUpstreamHandlerCoverageTest {

  @Mock private KubeMQClient mockClient;

  @Mock private kubemqGrpc.kubemqStub mockAsyncStub;

  @Mock private StreamObserver<Kubemq.QueuesUpstreamRequest> mockRequestObserver;

  private QueueUpstreamHandler handler;

  @BeforeEach
  void setup() {
    handler = new QueueUpstreamHandler(mockClient);
  }

  private QueueMessage createValidMessage() {
    return QueueMessage.builder().channel("test-queue").body("test-body".getBytes()).build();
  }

  @Nested
  @DisplayName("sendQueuesMessage Timeout Path")
  class SendQueuesMessageTimeoutTests {

    @Test
    @DisplayName("sendQueuesMessage returns error result on timeout")
    void sendQueuesMessage_timeout_returnsErrorResult() {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");
      when(mockClient.getRequestTimeoutSeconds()).thenReturn(1);
      when(mockAsyncStub.queuesUpstream(any())).thenReturn(mockRequestObserver);

      QueueSendResult result = handler.sendQueuesMessage(createValidMessage());

      assertNotNull(result);
      assertTrue(result.isError());
      assertTrue(result.getError().contains("timed out"));
    }
  }

  @Nested
  @DisplayName("sendQueuesMessage Generic Exception Path")
  class SendQueuesMessageGenericExceptionTests {

    @Test
    @DisplayName("sendQueuesMessageAsync completes exceptionally when sendRequest throws")
    void sendQueuesMessageAsync_sendRequestThrows_completesExceptionally() {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");

      when(mockAsyncStub.queuesUpstream(any())).thenReturn(mockRequestObserver);
      doThrow(new RuntimeException("Stream broken")).when(mockRequestObserver).onNext(any());

      CompletableFuture<QueueSendResult> future =
          handler.sendQueuesMessageAsync(createValidMessage());

      assertTrue(future.isCompletedExceptionally());
    }
  }

  @Nested
  @DisplayName("sendQueuesMessage wraps execution exception")
  class SendQueuesMessageGenericExceptionWrappingTests {

    @Test
    @DisplayName("sendQueuesMessage wraps generic ExecutionException in KubeMQException")
    void sendQueuesMessage_genericExecutionException_wrapsInKubeMQException() {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");
      when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

      when(mockAsyncStub.queuesUpstream(any())).thenReturn(mockRequestObserver);
      doThrow(new RuntimeException("Unexpected error")).when(mockRequestObserver).onNext(any());

      KubeMQException thrown =
          assertThrows(
              KubeMQException.class, () -> handler.sendQueuesMessage(createValidMessage()));
      assertTrue(thrown.getMessage().contains("Failed to get response"));
    }
  }

  @Nested
  @DisplayName("sendQueuesMessages Batch Error Paths")
  class BatchSendErrorTests {

    @Test
    @DisplayName("sendQueuesMessages returns timeout errors for each message")
    void sendQueuesMessages_timeout_returnsErrorForEachMessage() {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");
      when(mockClient.getRequestTimeoutSeconds()).thenReturn(1);
      when(mockAsyncStub.queuesUpstream(any())).thenReturn(mockRequestObserver);

      List<QueueMessage> messages = List.of(createValidMessage(), createValidMessage());

      List<QueueSendResult> results = handler.sendQueuesMessages(messages);

      assertNotNull(results);
      assertEquals(2, results.size());
      for (QueueSendResult result : results) {
        assertTrue(result.isError());
        assertTrue(result.getError().contains("timed out"));
      }
    }

    @Test
    @DisplayName("sendQueuesMessages batch success flow with response")
    void sendQueuesMessages_successFlow() throws Exception {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");
      when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<StreamObserver<Kubemq.QueuesUpstreamResponse>> observerCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.queuesUpstream(observerCaptor.capture())).thenReturn(mockRequestObserver);

      List<QueueMessage> messages = List.of(createValidMessage(), createValidMessage());

      CompletableFuture<List<QueueSendResult>> resultFuture =
          CompletableFuture.supplyAsync(() -> handler.sendQueuesMessages(messages));

      Thread.sleep(300);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Kubemq.QueuesUpstreamRequest> requestCaptor =
          ArgumentCaptor.forClass(Kubemq.QueuesUpstreamRequest.class);
      verify(mockRequestObserver, timeout(2000)).onNext(requestCaptor.capture());

      StreamObserver<Kubemq.QueuesUpstreamResponse> capturedObserver = observerCaptor.getValue();
      String refRequestId = requestCaptor.getValue().getRequestID();

      Kubemq.QueuesUpstreamResponse response =
          Kubemq.QueuesUpstreamResponse.newBuilder()
              .setRefRequestID(refRequestId)
              .setIsError(false)
              .addResults(
                  Kubemq.SendQueueMessageResult.newBuilder()
                      .setMessageID("msg-1")
                      .setIsError(false)
                      .build())
              .addResults(
                  Kubemq.SendQueueMessageResult.newBuilder()
                      .setMessageID("msg-2")
                      .setIsError(false)
                      .build())
              .build();

      capturedObserver.onNext(response);

      List<QueueSendResult> results = resultFuture.get(5, TimeUnit.SECONDS);
      assertNotNull(results);
      assertEquals(2, results.size());
    }

    @Test
    @DisplayName("onError completes pending batch futures with error")
    void onError_completesPendingBatchFutures() throws Exception {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");
      when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<StreamObserver<Kubemq.QueuesUpstreamResponse>> observerCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.queuesUpstream(observerCaptor.capture())).thenReturn(mockRequestObserver);

      List<QueueMessage> messages = List.of(createValidMessage());

      CompletableFuture<List<QueueSendResult>> resultFuture =
          CompletableFuture.supplyAsync(() -> handler.sendQueuesMessages(messages));

      Thread.sleep(300);
      verify(mockRequestObserver, timeout(2000)).onNext(any());

      StreamObserver<Kubemq.QueuesUpstreamResponse> capturedObserver = observerCaptor.getValue();
      capturedObserver.onError(new RuntimeException("Stream error"));

      List<QueueSendResult> results = resultFuture.get(5, TimeUnit.SECONDS);
      assertNotNull(results);
      assertTrue(results.get(0).isError());
    }
  }
}
