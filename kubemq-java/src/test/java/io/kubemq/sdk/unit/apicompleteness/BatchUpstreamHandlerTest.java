package io.kubemq.sdk.unit.apicompleteness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.queues.QueueMessage;
import io.kubemq.sdk.queues.QueueSendResult;
import io.kubemq.sdk.queues.QueueUpstreamHandler;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kubemq.Kubemq;
import kubemq.kubemqGrpc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Tests for batch send on QueueUpstreamHandler (REQ-API-1 Gap B). */
@ExtendWith(MockitoExtension.class)
class BatchUpstreamHandlerTest {

  @Mock private KubeMQClient mockClient;

  @Mock private kubemqGrpc.kubemqStub mockAsyncStub;

  @Mock private StreamObserver<Kubemq.QueuesUpstreamRequest> mockRequestObserver;

  private QueueUpstreamHandler handler;

  @BeforeEach
  void setup() {
    handler = new QueueUpstreamHandler(mockClient);
  }

  @Test
  @DisplayName("API-15: sendQueuesMessages encodes all messages into single request")
  void sendQueuesMessages_encodesAllInOneRequest() throws Exception {
    when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
    when(mockClient.getClientId()).thenReturn("test-client");
    when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

    ArgumentCaptor<StreamObserver<Kubemq.QueuesUpstreamResponse>> observerCaptor =
        ArgumentCaptor.forClass(StreamObserver.class);
    when(mockAsyncStub.queuesUpstream(observerCaptor.capture())).thenReturn(mockRequestObserver);

    QueueMessage msg1 = QueueMessage.builder().channel("queue-1").body("body-1".getBytes()).build();
    QueueMessage msg2 = QueueMessage.builder().channel("queue-2").body("body-2".getBytes()).build();

    CompletableFuture<List<QueueSendResult>> resultFuture =
        CompletableFuture.supplyAsync(() -> handler.sendQueuesMessages(Arrays.asList(msg1, msg2)));

    Thread.sleep(300);

    ArgumentCaptor<Kubemq.QueuesUpstreamRequest> requestCaptor =
        ArgumentCaptor.forClass(Kubemq.QueuesUpstreamRequest.class);
    verify(mockRequestObserver, timeout(2000)).onNext(requestCaptor.capture());

    Kubemq.QueuesUpstreamRequest capturedRequest = requestCaptor.getValue();
    assertEquals(2, capturedRequest.getMessagesCount());

    StreamObserver<Kubemq.QueuesUpstreamResponse> capturedObserver = observerCaptor.getValue();
    String refRequestId = capturedRequest.getRequestID();

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
    assertEquals("msg-1", results.get(0).getId());
    assertEquals("msg-2", results.get(1).getId());
  }

  @Test
  @DisplayName("API-16: sendQueuesMessages handles error response")
  void sendQueuesMessages_handlesErrorResponse() throws Exception {
    when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
    when(mockClient.getClientId()).thenReturn("test-client");
    when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

    ArgumentCaptor<StreamObserver<Kubemq.QueuesUpstreamResponse>> observerCaptor =
        ArgumentCaptor.forClass(StreamObserver.class);
    when(mockAsyncStub.queuesUpstream(observerCaptor.capture())).thenReturn(mockRequestObserver);

    QueueMessage msg1 = QueueMessage.builder().channel("queue-1").body("body-1".getBytes()).build();

    CompletableFuture<List<QueueSendResult>> resultFuture =
        CompletableFuture.supplyAsync(() -> handler.sendQueuesMessages(List.of(msg1)));

    Thread.sleep(300);

    ArgumentCaptor<Kubemq.QueuesUpstreamRequest> requestCaptor =
        ArgumentCaptor.forClass(Kubemq.QueuesUpstreamRequest.class);
    verify(mockRequestObserver, timeout(2000)).onNext(requestCaptor.capture());

    StreamObserver<Kubemq.QueuesUpstreamResponse> capturedObserver = observerCaptor.getValue();
    String refRequestId = requestCaptor.getValue().getRequestID();

    Kubemq.QueuesUpstreamResponse response =
        Kubemq.QueuesUpstreamResponse.newBuilder()
            .setRefRequestID(refRequestId)
            .setIsError(true)
            .setError("Queue not found")
            .build();

    capturedObserver.onNext(response);

    List<QueueSendResult> results = resultFuture.get(5, TimeUnit.SECONDS);

    assertNotNull(results);
    assertEquals(1, results.size());
    assertTrue(results.get(0).isError());
    assertEquals("Queue not found", results.get(0).getError());
  }

  @Test
  @DisplayName("API-17: sendQueuesMessages completes on stream error")
  void sendQueuesMessages_completesOnStreamError() throws Exception {
    when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
    when(mockClient.getClientId()).thenReturn("test-client");
    when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

    ArgumentCaptor<StreamObserver<Kubemq.QueuesUpstreamResponse>> observerCaptor =
        ArgumentCaptor.forClass(StreamObserver.class);
    when(mockAsyncStub.queuesUpstream(observerCaptor.capture())).thenReturn(mockRequestObserver);

    QueueMessage msg1 = QueueMessage.builder().channel("queue-1").body("body-1".getBytes()).build();

    CompletableFuture<List<QueueSendResult>> resultFuture =
        CompletableFuture.supplyAsync(() -> handler.sendQueuesMessages(List.of(msg1)));

    Thread.sleep(300);
    verify(mockRequestObserver, timeout(2000)).onNext(any());

    StreamObserver<Kubemq.QueuesUpstreamResponse> capturedObserver = observerCaptor.getValue();
    capturedObserver.onError(new RuntimeException("Connection lost"));

    List<QueueSendResult> results = resultFuture.get(5, TimeUnit.SECONDS);

    assertNotNull(results);
    assertFalse(results.isEmpty());
    assertTrue(results.get(0).isError());
    assertTrue(results.get(0).getError().contains("Connection lost"));
  }
}
