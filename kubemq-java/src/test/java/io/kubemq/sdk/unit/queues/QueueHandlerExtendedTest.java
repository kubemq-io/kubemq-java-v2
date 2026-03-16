package io.kubemq.sdk.unit.queues;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.exception.KubeMQException;
import io.kubemq.sdk.queues.*;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.mockito.junit.jupiter.MockitoSettings;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class QueueHandlerExtendedTest {

  @Mock private KubeMQClient mockClient;

  @Mock private kubemqGrpc.kubemqStub mockAsyncStub;

  @Mock private StreamObserver<Kubemq.QueuesUpstreamRequest> mockUpstreamRequestObserver;

  @Mock private StreamObserver<Kubemq.QueuesDownstreamRequest> mockDownstreamRequestObserver;

  private QueueUpstreamHandler upstreamHandler;
  private QueueDownstreamHandler downstreamHandler;

  @BeforeEach
  void setup() {
    upstreamHandler = new QueueUpstreamHandler(mockClient);
    downstreamHandler = new QueueDownstreamHandler(mockClient);
  }

  private QueueMessage createValidMessage() {
    return QueueMessage.builder().channel("test-queue").body("test-body".getBytes()).build();
  }

  /**
   * Uses reflection to call the package-private setRequestSender method on QueueMessageReceived.
   */
  private static void setRequestSenderViaReflection(
      QueueMessageReceived msg, RequestSender sender) {
    try {
      Method method =
          QueueMessageReceived.class.getDeclaredMethod("setRequestSender", RequestSender.class);
      method.setAccessible(true);
      method.invoke(msg, sender);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set requestSender via reflection", e);
    }
  }

  private QueuesPollRequest createValidPollRequest() {
    return QueuesPollRequest.builder()
        .channel("test-queue")
        .pollMaxMessages(10)
        .pollWaitTimeoutInSeconds(1)
        .build();
  }

  // =========================================================================
  // QueueUpstreamHandler tests
  // =========================================================================

  @Nested
  @DisplayName("Upstream: connect() success and double-check locking")
  class UpstreamConnectTests {

    @Test
    @DisplayName("connect() establishes stream on first call")
    void connect_firstCall_establishesStream() {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockAsyncStub.queuesUpstream(any())).thenReturn(mockUpstreamRequestObserver);

      upstreamHandler.connect();

      verify(mockAsyncStub).queuesUpstream(any());
    }

    @Test
    @DisplayName("connect() is idempotent - second call is no-op")
    void connect_secondCall_isNoOp() {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockAsyncStub.queuesUpstream(any())).thenReturn(mockUpstreamRequestObserver);

      upstreamHandler.connect();
      upstreamHandler.connect();

      verify(mockAsyncStub, times(1)).queuesUpstream(any());
    }

    @Test
    @DisplayName("connect() handles exception and remains disconnected")
    void connect_exception_remainsDisconnected() {
      when(mockClient.getAsyncClient())
          .thenThrow(new RuntimeException("Connection refused"))
          .thenReturn(mockAsyncStub);
      when(mockAsyncStub.queuesUpstream(any())).thenReturn(mockUpstreamRequestObserver);

      upstreamHandler.connect();
      upstreamHandler.connect();
      verify(mockAsyncStub, times(1)).queuesUpstream(any());
    }
  }

  @Nested
  @DisplayName("Upstream: response observer onNext - single message path")
  class UpstreamOnNextSingleTests {

    @Test
    @DisplayName("onNext with successful single result completes future")
    void onNext_singleResult_completesFuture() throws Exception {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");
      when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<StreamObserver<Kubemq.QueuesUpstreamResponse>> observerCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.queuesUpstream(observerCaptor.capture()))
          .thenReturn(mockUpstreamRequestObserver);

      CompletableFuture<QueueSendResult> future =
          upstreamHandler.sendQueuesMessageAsync(createValidMessage());

      Thread.sleep(200);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Kubemq.QueuesUpstreamRequest> requestCaptor =
          ArgumentCaptor.forClass(Kubemq.QueuesUpstreamRequest.class);
      verify(mockUpstreamRequestObserver, timeout(2000)).onNext(requestCaptor.capture());

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
              .build();

      capturedObserver.onNext(response);

      QueueSendResult result = future.get(5, TimeUnit.SECONDS);
      assertNotNull(result);
      assertFalse(result.isError());
    }

    @Test
    @DisplayName("onNext with error response completes future with error result")
    void onNext_errorResponse_completesFutureWithError() throws Exception {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");
      when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<StreamObserver<Kubemq.QueuesUpstreamResponse>> observerCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.queuesUpstream(observerCaptor.capture()))
          .thenReturn(mockUpstreamRequestObserver);

      CompletableFuture<QueueSendResult> future =
          upstreamHandler.sendQueuesMessageAsync(createValidMessage());

      Thread.sleep(200);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Kubemq.QueuesUpstreamRequest> requestCaptor =
          ArgumentCaptor.forClass(Kubemq.QueuesUpstreamRequest.class);
      verify(mockUpstreamRequestObserver, timeout(2000)).onNext(requestCaptor.capture());

      StreamObserver<Kubemq.QueuesUpstreamResponse> capturedObserver = observerCaptor.getValue();
      String refRequestId = requestCaptor.getValue().getRequestID();

      Kubemq.QueuesUpstreamResponse errorResponse =
          Kubemq.QueuesUpstreamResponse.newBuilder()
              .setRefRequestID(refRequestId)
              .setIsError(true)
              .setError("Server error: queue full")
              .build();

      capturedObserver.onNext(errorResponse);

      QueueSendResult result = future.get(5, TimeUnit.SECONDS);
      assertNotNull(result);
      assertTrue(result.isError());
      assertEquals("Server error: queue full", result.getError());
    }

    @Test
    @DisplayName("onNext with zero results completes future with 'no results' error")
    void onNext_zeroResults_noResultsError() throws Exception {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");
      when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<StreamObserver<Kubemq.QueuesUpstreamResponse>> observerCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.queuesUpstream(observerCaptor.capture()))
          .thenReturn(mockUpstreamRequestObserver);

      CompletableFuture<QueueSendResult> future =
          upstreamHandler.sendQueuesMessageAsync(createValidMessage());

      Thread.sleep(200);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Kubemq.QueuesUpstreamRequest> requestCaptor =
          ArgumentCaptor.forClass(Kubemq.QueuesUpstreamRequest.class);
      verify(mockUpstreamRequestObserver, timeout(2000)).onNext(requestCaptor.capture());

      StreamObserver<Kubemq.QueuesUpstreamResponse> capturedObserver = observerCaptor.getValue();
      String refRequestId = requestCaptor.getValue().getRequestID();

      Kubemq.QueuesUpstreamResponse emptyResponse =
          Kubemq.QueuesUpstreamResponse.newBuilder()
              .setRefRequestID(refRequestId)
              .setIsError(false)
              .build();

      capturedObserver.onNext(emptyResponse);

      QueueSendResult result = future.get(5, TimeUnit.SECONDS);
      assertNotNull(result);
      assertTrue(result.isError());
      assertEquals("no results", result.getError());
    }
  }

  @Nested
  @DisplayName("Upstream: response observer onNext - batch path")
  class UpstreamOnNextBatchTests {

    @Test
    @DisplayName("onNext with batch error response completes batch future with error")
    void onNext_batchErrorResponse_completesBatchFuture() throws Exception {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");
      when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<StreamObserver<Kubemq.QueuesUpstreamResponse>> observerCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.queuesUpstream(observerCaptor.capture()))
          .thenReturn(mockUpstreamRequestObserver);

      List<QueueMessage> messages = List.of(createValidMessage(), createValidMessage());

      CompletableFuture<List<QueueSendResult>> resultFuture =
          CompletableFuture.supplyAsync(() -> upstreamHandler.sendQueuesMessages(messages));

      Thread.sleep(300);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Kubemq.QueuesUpstreamRequest> requestCaptor =
          ArgumentCaptor.forClass(Kubemq.QueuesUpstreamRequest.class);
      verify(mockUpstreamRequestObserver, timeout(2000)).onNext(requestCaptor.capture());

      StreamObserver<Kubemq.QueuesUpstreamResponse> capturedObserver = observerCaptor.getValue();
      String refRequestId = requestCaptor.getValue().getRequestID();

      Kubemq.QueuesUpstreamResponse errorResponse =
          Kubemq.QueuesUpstreamResponse.newBuilder()
              .setRefRequestID(refRequestId)
              .setIsError(true)
              .setError("Batch processing failed")
              .build();

      capturedObserver.onNext(errorResponse);

      List<QueueSendResult> results = resultFuture.get(5, TimeUnit.SECONDS);
      assertNotNull(results);
      assertEquals(1, results.size());
      assertTrue(results.get(0).isError());
      assertEquals("Batch processing failed", results.get(0).getError());
    }
  }

  @Nested
  @DisplayName("Upstream: response observer onError and onCompleted")
  class UpstreamOnErrorOnCompletedTests {

    @Test
    @DisplayName("onError completes pending single futures with error")
    void onError_completesPendingSingleFutures() throws Exception {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");
      when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<StreamObserver<Kubemq.QueuesUpstreamResponse>> observerCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.queuesUpstream(observerCaptor.capture()))
          .thenReturn(mockUpstreamRequestObserver);

      CompletableFuture<QueueSendResult> future =
          upstreamHandler.sendQueuesMessageAsync(createValidMessage());

      Thread.sleep(200);
      verify(mockUpstreamRequestObserver, timeout(2000)).onNext(any());

      StreamObserver<Kubemq.QueuesUpstreamResponse> capturedObserver = observerCaptor.getValue();
      capturedObserver.onError(new RuntimeException("Connection lost"));

      QueueSendResult result = future.get(5, TimeUnit.SECONDS);
      assertNotNull(result);
      assertTrue(result.isError());
      assertTrue(result.getError().contains("Connection lost"));
    }

    @Test
    @DisplayName("onCompleted completes pending single futures with error")
    void onCompleted_completesPendingSingleFutures() throws Exception {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");
      when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<StreamObserver<Kubemq.QueuesUpstreamResponse>> observerCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.queuesUpstream(observerCaptor.capture()))
          .thenReturn(mockUpstreamRequestObserver);

      CompletableFuture<QueueSendResult> future =
          upstreamHandler.sendQueuesMessageAsync(createValidMessage());

      Thread.sleep(200);
      verify(mockUpstreamRequestObserver, timeout(2000)).onNext(any());

      StreamObserver<Kubemq.QueuesUpstreamResponse> capturedObserver = observerCaptor.getValue();
      capturedObserver.onCompleted();

      QueueSendResult result = future.get(5, TimeUnit.SECONDS);
      assertNotNull(result);
      assertTrue(result.isError());
      assertTrue(result.getError().contains("Stream completed"));
    }

    @Test
    @DisplayName("onCompleted completes pending batch futures with error")
    void onCompleted_completesPendingBatchFutures() throws Exception {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");
      when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<StreamObserver<Kubemq.QueuesUpstreamResponse>> observerCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.queuesUpstream(observerCaptor.capture()))
          .thenReturn(mockUpstreamRequestObserver);

      List<QueueMessage> messages = List.of(createValidMessage());

      CompletableFuture<List<QueueSendResult>> resultFuture =
          CompletableFuture.supplyAsync(() -> upstreamHandler.sendQueuesMessages(messages));

      Thread.sleep(300);
      verify(mockUpstreamRequestObserver, timeout(2000)).onNext(any());

      StreamObserver<Kubemq.QueuesUpstreamResponse> capturedObserver = observerCaptor.getValue();
      capturedObserver.onCompleted();

      List<QueueSendResult> results = resultFuture.get(5, TimeUnit.SECONDS);
      assertNotNull(results);
      assertTrue(results.get(0).isError());
      assertTrue(results.get(0).getError().contains("Stream completed"));
    }
  }

  @Nested
  @DisplayName("Upstream: sendQueuesMessage synchronous exception paths")
  class UpstreamSendSyncTests {

    @Test
    @DisplayName("sendQueuesMessage returns error on InterruptedException")
    void sendQueuesMessage_interrupted_returnsError() throws Exception {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");
      when(mockClient.getRequestTimeoutSeconds()).thenReturn(60);
      when(mockAsyncStub.queuesUpstream(any())).thenReturn(mockUpstreamRequestObserver);

      Thread testThread = Thread.currentThread();
      Thread interrupter =
          new Thread(
              () -> {
                try {
                  Thread.sleep(200);
                } catch (InterruptedException ignored) {
                }
                testThread.interrupt();
              });
      interrupter.start();

      QueueSendResult result = upstreamHandler.sendQueuesMessage(createValidMessage());

      Thread.interrupted(); // clear flag
      assertNotNull(result);
      assertTrue(result.isError());
      assertTrue(result.getError().contains("interrupted"));
    }
  }

  @Nested
  @DisplayName("Upstream: sendQueuesMessages batch exception paths")
  class UpstreamBatchExceptionTests {

    @Test
    @DisplayName("sendQueuesMessages returns interrupted error for each message")
    void sendQueuesMessages_interrupted_returnsErrorForEachMessage() throws Exception {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");
      when(mockClient.getRequestTimeoutSeconds()).thenReturn(60);
      when(mockAsyncStub.queuesUpstream(any())).thenReturn(mockUpstreamRequestObserver);

      List<QueueMessage> messages = List.of(createValidMessage(), createValidMessage());

      Thread testThread = Thread.currentThread();
      Thread interrupter =
          new Thread(
              () -> {
                try {
                  Thread.sleep(200);
                } catch (InterruptedException ignored) {
                }
                testThread.interrupt();
              });
      interrupter.start();

      List<QueueSendResult> results = upstreamHandler.sendQueuesMessages(messages);

      Thread.interrupted(); // clear flag
      assertNotNull(results);
      assertEquals(2, results.size());
      for (QueueSendResult r : results) {
        assertTrue(r.isError());
        assertTrue(r.getError().contains("interrupted"));
      }
    }

    @Test
    @DisplayName("sendQueuesMessages throws KubeMQException on generic failure")
    void sendQueuesMessages_genericException_throwsKubeMQException() {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");
      when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

      when(mockAsyncStub.queuesUpstream(any())).thenReturn(mockUpstreamRequestObserver);
      doThrow(new RuntimeException("Unexpected failure"))
          .when(mockUpstreamRequestObserver)
          .onNext(any());

      List<QueueMessage> messages = List.of(createValidMessage());

      KubeMQException thrown =
          assertThrows(KubeMQException.class, () -> upstreamHandler.sendQueuesMessages(messages));
      assertTrue(thrown.getMessage().contains("Failed to send batch"));
    }
  }

  // =========================================================================
  // QueueDownstreamHandler tests
  // =========================================================================

  @Nested
  @DisplayName("Downstream: connect() success and failure")
  class DownstreamConnectTests {

    @Test
    @DisplayName("connect() establishes stream on first call")
    void connect_firstCall_establishesStream() {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockAsyncStub.queuesDownstream(any())).thenReturn(mockDownstreamRequestObserver);

      downstreamHandler.connect();

      verify(mockAsyncStub).queuesDownstream(any());
    }

    @Test
    @DisplayName("connect() is idempotent")
    void connect_secondCall_isNoOp() {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockAsyncStub.queuesDownstream(any())).thenReturn(mockDownstreamRequestObserver);

      downstreamHandler.connect();
      downstreamHandler.connect();

      verify(mockAsyncStub, times(1)).queuesDownstream(any());
    }

    @Test
    @DisplayName("connect() handles exception and remains disconnected")
    void connect_exception_remainsDisconnected() {
      when(mockClient.getAsyncClient())
          .thenThrow(new RuntimeException("Connection refused"))
          .thenReturn(mockAsyncStub);
      when(mockAsyncStub.queuesDownstream(any())).thenReturn(mockDownstreamRequestObserver);

      downstreamHandler.connect();
      downstreamHandler.connect();
      verify(mockAsyncStub, times(1)).queuesDownstream(any());
    }
  }

  @Nested
  @DisplayName("Downstream: response observer onNext with transaction response")
  class DownstreamOnNextTests {

    @Test
    @DisplayName("onNext with messages decodes QueueMessageReceived objects")
    void onNext_withMessages_decodesMessages() throws Exception {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");

      @SuppressWarnings("unchecked")
      ArgumentCaptor<StreamObserver<Kubemq.QueuesDownstreamResponse>> observerCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.queuesDownstream(observerCaptor.capture()))
          .thenReturn(mockDownstreamRequestObserver);

      CompletableFuture<QueuesPollResponse> future =
          downstreamHandler.receiveQueuesMessagesAsync(createValidPollRequest());

      Thread.sleep(200);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Kubemq.QueuesDownstreamRequest> requestCaptor =
          ArgumentCaptor.forClass(Kubemq.QueuesDownstreamRequest.class);
      verify(mockDownstreamRequestObserver, timeout(2000)).onNext(requestCaptor.capture());

      StreamObserver<Kubemq.QueuesDownstreamResponse> capturedObserver = observerCaptor.getValue();
      String refRequestId = requestCaptor.getValue().getRequestID();

      Kubemq.QueueMessage pbMessage =
          Kubemq.QueueMessage.newBuilder()
              .setMessageID("msg-1")
              .setChannel("test-queue")
              .setMetadata("meta-1")
              .setBody(ByteString.copyFromUtf8("hello"))
              .setClientID("sender-client")
              .setAttributes(
                  Kubemq.QueueMessageAttributes.newBuilder()
                      .setTimestamp(System.currentTimeMillis() * 1_000_000L)
                      .setSequence(42)
                      .setReceiveCount(1)
                      .build())
              .build();

      Kubemq.QueuesDownstreamResponse response =
          Kubemq.QueuesDownstreamResponse.newBuilder()
              .setRefRequestId(refRequestId)
              .setTransactionId("txn-1")
              .setTransactionComplete(false)
              .setIsError(false)
              .addMessages(pbMessage)
              .build();

      capturedObserver.onNext(response);

      QueuesPollResponse pollResponse = future.get(5, TimeUnit.SECONDS);
      assertNotNull(pollResponse);
      assertFalse(pollResponse.isError());
      assertEquals("txn-1", pollResponse.getTransactionId());
      assertEquals(1, pollResponse.getMessages().size());
      assertEquals("msg-1", pollResponse.getMessages().get(0).getId());
      assertEquals("test-queue", pollResponse.getMessages().get(0).getChannel());
      assertEquals("meta-1", pollResponse.getMessages().get(0).getMetadata());
    }

    @Test
    @DisplayName("onNext with error response completes future with error")
    void onNext_errorResponse_completesFuture() throws Exception {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");

      @SuppressWarnings("unchecked")
      ArgumentCaptor<StreamObserver<Kubemq.QueuesDownstreamResponse>> observerCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.queuesDownstream(observerCaptor.capture()))
          .thenReturn(mockDownstreamRequestObserver);

      CompletableFuture<QueuesPollResponse> future =
          downstreamHandler.receiveQueuesMessagesAsync(createValidPollRequest());

      Thread.sleep(200);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Kubemq.QueuesDownstreamRequest> requestCaptor =
          ArgumentCaptor.forClass(Kubemq.QueuesDownstreamRequest.class);
      verify(mockDownstreamRequestObserver, timeout(2000)).onNext(requestCaptor.capture());

      StreamObserver<Kubemq.QueuesDownstreamResponse> capturedObserver = observerCaptor.getValue();
      String refRequestId = requestCaptor.getValue().getRequestID();

      Kubemq.QueuesDownstreamResponse errorResponse =
          Kubemq.QueuesDownstreamResponse.newBuilder()
              .setRefRequestId(refRequestId)
              .setTransactionId("txn-err")
              .setIsError(true)
              .setError("Queue not found")
              .build();

      capturedObserver.onNext(errorResponse);

      QueuesPollResponse pollResponse = future.get(5, TimeUnit.SECONDS);
      assertNotNull(pollResponse);
      assertTrue(pollResponse.isError());
      assertEquals("Queue not found", pollResponse.getError());
    }

    @Test
    @DisplayName("onNext with unmatched requestId is silently ignored")
    void onNext_unmatchedRequestId_ignoredSilently() throws Exception {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");

      @SuppressWarnings("unchecked")
      ArgumentCaptor<StreamObserver<Kubemq.QueuesDownstreamResponse>> observerCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.queuesDownstream(observerCaptor.capture()))
          .thenReturn(mockDownstreamRequestObserver);

      CompletableFuture<QueuesPollResponse> future =
          downstreamHandler.receiveQueuesMessagesAsync(createValidPollRequest());

      Thread.sleep(200);
      verify(mockDownstreamRequestObserver, timeout(2000)).onNext(any());

      StreamObserver<Kubemq.QueuesDownstreamResponse> capturedObserver = observerCaptor.getValue();

      Kubemq.QueuesDownstreamResponse unmatchedResponse =
          Kubemq.QueuesDownstreamResponse.newBuilder()
              .setRefRequestId("non-existent-id")
              .setTransactionId("txn-x")
              .setIsError(false)
              .build();

      assertDoesNotThrow(() -> capturedObserver.onNext(unmatchedResponse));
      assertFalse(future.isDone());
    }
  }

  @Nested
  @DisplayName("Downstream: response observer onError and onCompleted")
  class DownstreamOnErrorOnCompletedTests {

    @Test
    @DisplayName("onError completes pending futures with error response")
    void onError_completesPendingFutures() throws Exception {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");

      @SuppressWarnings("unchecked")
      ArgumentCaptor<StreamObserver<Kubemq.QueuesDownstreamResponse>> observerCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.queuesDownstream(observerCaptor.capture()))
          .thenReturn(mockDownstreamRequestObserver);

      CompletableFuture<QueuesPollResponse> future =
          downstreamHandler.receiveQueuesMessagesAsync(createValidPollRequest());

      Thread.sleep(200);
      verify(mockDownstreamRequestObserver, timeout(2000)).onNext(any());

      StreamObserver<Kubemq.QueuesDownstreamResponse> capturedObserver = observerCaptor.getValue();
      capturedObserver.onError(new RuntimeException("Transport error"));

      QueuesPollResponse pollResponse = future.get(5, TimeUnit.SECONDS);
      assertNotNull(pollResponse);
      assertTrue(pollResponse.isError());
      assertTrue(pollResponse.getError().contains("Transport error"));
    }

    @Test
    @DisplayName("onCompleted completes pending futures with error response")
    void onCompleted_completesPendingFutures() throws Exception {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");

      @SuppressWarnings("unchecked")
      ArgumentCaptor<StreamObserver<Kubemq.QueuesDownstreamResponse>> observerCaptor =
          ArgumentCaptor.forClass(StreamObserver.class);
      when(mockAsyncStub.queuesDownstream(observerCaptor.capture()))
          .thenReturn(mockDownstreamRequestObserver);

      CompletableFuture<QueuesPollResponse> future =
          downstreamHandler.receiveQueuesMessagesAsync(createValidPollRequest());

      Thread.sleep(200);
      verify(mockDownstreamRequestObserver, timeout(2000)).onNext(any());

      StreamObserver<Kubemq.QueuesDownstreamResponse> capturedObserver = observerCaptor.getValue();
      capturedObserver.onCompleted();

      QueuesPollResponse pollResponse = future.get(5, TimeUnit.SECONDS);
      assertNotNull(pollResponse);
      assertTrue(pollResponse.isError());
      assertTrue(pollResponse.getError().contains("Stream completed"));
    }
  }

  @Nested
  @DisplayName("Downstream: receiveQueuesMessages synchronous exception paths")
  class DownstreamReceiveSyncTests {

    @Test
    @DisplayName("receiveQueuesMessages returns error on InterruptedException")
    void receiveQueuesMessages_interrupted_returnsError() throws Exception {
      when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
      when(mockClient.getClientId()).thenReturn("test-client");
      when(mockClient.getRequestTimeoutSeconds()).thenReturn(60);
      when(mockAsyncStub.queuesDownstream(any())).thenReturn(mockDownstreamRequestObserver);

      Thread testThread = Thread.currentThread();
      Thread interrupter =
          new Thread(
              () -> {
                try {
                  Thread.sleep(200);
                } catch (InterruptedException ignored) {
                }
                testThread.interrupt();
              });
      interrupter.start();

      QueuesPollResponse result = downstreamHandler.receiveQueuesMessages(createValidPollRequest());

      Thread.interrupted(); // clear flag
      assertNotNull(result);
      assertTrue(result.isError());
      assertTrue(result.getError().contains("interrupted"));
    }
  }

  // =========================================================================
  // QueueMessage tests
  // =========================================================================

  @Nested
  @DisplayName("QueueMessage: encode and toString")
  class QueueMessageTests {

    @Test
    @DisplayName("encode() with all fields set produces correct protobuf")
    void encode_allFields_correctProtobuf() {
      Map<String, String> tags = new HashMap<>();
      tags.put("env", "test");
      tags.put("priority", "high");

      QueueMessage msg =
          QueueMessage.builder()
              .id("custom-id")
              .channel("orders")
              .metadata("order-meta")
              .body("order-data".getBytes())
              .tags(tags)
              .delayInSeconds(10)
              .expirationInSeconds(300)
              .attemptsBeforeDeadLetterQueue(3)
              .deadLetterQueue("orders-dlq")
              .build();

      Kubemq.QueuesUpstreamRequest encoded = msg.encode("client-1");

      assertEquals(1, encoded.getMessagesCount());
      Kubemq.QueueMessage pbMsg = encoded.getMessages(0);
      assertEquals("custom-id", pbMsg.getMessageID());
      assertEquals("client-1", pbMsg.getClientID());
      assertEquals("orders", pbMsg.getChannel());
      assertEquals("order-meta", pbMsg.getMetadata());
      assertEquals("order-data", pbMsg.getBody().toStringUtf8());
      assertEquals("test", pbMsg.getTagsMap().get("env"));
      assertEquals("high", pbMsg.getTagsMap().get("priority"));
      assertEquals(10, pbMsg.getPolicy().getDelaySeconds());
      assertEquals(300, pbMsg.getPolicy().getExpirationSeconds());
      assertEquals(3, pbMsg.getPolicy().getMaxReceiveCount());
      assertEquals("orders-dlq", pbMsg.getPolicy().getMaxReceiveQueue());
    }

    @Test
    @DisplayName("encode() with null metadata and null deadLetterQueue uses defaults")
    void encode_nullDefaults_usesEmptyStrings() {
      QueueMessage msg = QueueMessage.builder().channel("test-ch").body("data".getBytes()).build();

      Kubemq.QueuesUpstreamRequest encoded = msg.encode("client-1");
      Kubemq.QueueMessage pbMsg = encoded.getMessages(0);

      assertEquals("", pbMsg.getMetadata());
      assertEquals("", pbMsg.getPolicy().getMaxReceiveQueue());
      assertNotNull(pbMsg.getMessageID());
      assertFalse(pbMsg.getMessageID().isEmpty());
    }

    @Test
    @DisplayName("encode() with null id generates a UUID")
    void encode_nullId_generatesUUID() {
      QueueMessage msg = QueueMessage.builder().channel("ch").body("b".getBytes()).build();

      Kubemq.QueuesUpstreamRequest encoded = msg.encode("c");
      String messageId = encoded.getMessages(0).getMessageID();
      assertNotNull(messageId);
      assertFalse(messageId.isEmpty());
    }

    @Test
    @DisplayName("encodeMessage() with empty tags produces message with no tags")
    void encodeMessage_emptyTags_noTags() {
      QueueMessage msg =
          QueueMessage.builder()
              .channel("ch")
              .body("data".getBytes())
              .tags(new HashMap<>())
              .build();

      Kubemq.QueueMessage pbMsg = msg.encodeMessage("client-1");
      assertTrue(pbMsg.getTagsMap().isEmpty());
    }

    @Test
    @DisplayName("toString() includes all fields")
    void toString_includesAllFields() {
      QueueMessage msg =
          QueueMessage.builder()
              .id("id-1")
              .channel("ch-1")
              .metadata("meta")
              .body("body".getBytes())
              .delayInSeconds(5)
              .expirationInSeconds(60)
              .attemptsBeforeDeadLetterQueue(2)
              .deadLetterQueue("dlq")
              .build();

      String str = msg.toString();
      assertTrue(str.contains("id-1"));
      assertTrue(str.contains("ch-1"));
      assertTrue(str.contains("meta"));
      assertTrue(str.contains("body"));
      assertTrue(str.contains("5"));
      assertTrue(str.contains("60"));
      assertTrue(str.contains("2"));
      assertTrue(str.contains("dlq"));
    }

    @Test
    @DisplayName("decode() from protobuf reconstructs all fields")
    void decode_fromProtobuf_reconstructsAllFields() {
      Kubemq.QueueMessage pbMsg =
          Kubemq.QueueMessage.newBuilder()
              .setMessageID("decoded-id")
              .setChannel("decoded-ch")
              .setMetadata("decoded-meta")
              .setBody(ByteString.copyFromUtf8("decoded-body"))
              .putTags("key1", "val1")
              .setPolicy(
                  Kubemq.QueueMessagePolicy.newBuilder()
                      .setDelaySeconds(15)
                      .setExpirationSeconds(120)
                      .setMaxReceiveCount(5)
                      .setMaxReceiveQueue("decoded-dlq")
                      .build())
              .build();

      QueueMessage decoded = QueueMessage.decode(pbMsg);

      assertEquals("decoded-id", decoded.getId());
      assertEquals("decoded-ch", decoded.getChannel());
      assertEquals("decoded-meta", decoded.getMetadata());
      assertEquals("decoded-body", new String(decoded.getBody()));
      assertEquals("val1", decoded.getTags().get("key1"));
      assertEquals(15, decoded.getDelayInSeconds());
      assertEquals(120, decoded.getExpirationInSeconds());
      assertEquals(5, decoded.getAttemptsBeforeDeadLetterQueue());
      assertEquals("decoded-dlq", decoded.getDeadLetterQueue());
    }
  }

  // =========================================================================
  // QueueMessageReceived tests
  // =========================================================================

  @Nested
  @DisplayName("QueueMessageReceived: decode, ack, reject, reQueue, toString")
  class QueueMessageReceivedTests {

    private QueueMessageReceived createDecodedMessage(boolean autoAcked, int visibilitySeconds) {
      Kubemq.QueueMessage pbMsg =
          Kubemq.QueueMessage.newBuilder()
              .setMessageID("rcv-msg-1")
              .setChannel("rcv-channel")
              .setMetadata("rcv-meta")
              .setBody(ByteString.copyFromUtf8("rcv-body"))
              .setClientID("sender-1")
              .putTags("tagA", "valA")
              .setAttributes(
                  Kubemq.QueueMessageAttributes.newBuilder()
                      .setTimestamp(1000000000L * 1_000_000_000L)
                      .setSequence(99)
                      .setReceiveCount(2)
                      .setReRouted(true)
                      .setReRoutedFromQueue("original-queue")
                      .setExpirationAt(2000000L * 1_000_000_000L)
                      .setDelayedTo(1500000L * 1_000_000_000L)
                      .build())
              .build();

      QueuesPollResponse pollResponse =
          QueuesPollResponse.builder().transactionId("txn-rcv").build();

      return new QueueMessageReceived()
          .decode(
              pbMsg,
              "txn-rcv",
              false,
              "receiver-client",
              visibilitySeconds,
              autoAcked,
              pollResponse);
    }

    @Test
    @DisplayName("decode() populates all fields correctly")
    void decode_populatesAllFields() {
      QueueMessageReceived msg = createDecodedMessage(false, 0);

      assertEquals("rcv-msg-1", msg.getId());
      assertEquals("rcv-channel", msg.getChannel());
      assertEquals("rcv-meta", msg.getMetadata());
      assertEquals("rcv-body", new String(msg.getBody()));
      assertEquals("sender-1", msg.getFromClientId());
      assertEquals("valA", msg.getTags().get("tagA"));
      assertEquals(99, msg.getSequence());
      assertEquals(2, msg.getReceiveCount());
      assertTrue(msg.isReRouted());
      assertEquals("original-queue", msg.getReRouteFromQueue());
      assertEquals("txn-rcv", msg.getTransactionId());
      assertEquals("receiver-client", msg.getReceiverClientId());
      assertFalse(msg.isTransactionCompleted());
      assertNotNull(msg.getTimestamp());
      assertNotNull(msg.getExpiredAt());
      assertNotNull(msg.getDelayedTo());
    }

    @Test
    @DisplayName("ack() sends AckRange request via requestSender")
    void ack_sendsAckRangeRequest() {
      QueueMessageReceived msg = createDecodedMessage(false, 0);

      RequestSenderCaptor captor = new RequestSenderCaptor();
      setRequestSenderViaReflection(msg, captor);

      msg.ack();

      assertNotNull(captor.lastRequest);
      assertEquals(
          Kubemq.QueuesDownstreamRequestType.AckRange, captor.lastRequest.getRequestTypeData());
      assertEquals("txn-rcv", captor.lastRequest.getRefTransactionId());
      assertTrue(captor.lastRequest.getSequenceRangeList().contains(99L));
    }

    @Test
    @DisplayName("reject() sends NAckRange request via requestSender")
    void reject_sendsNAckRangeRequest() {
      QueueMessageReceived msg = createDecodedMessage(false, 0);

      RequestSenderCaptor captor = new RequestSenderCaptor();
      setRequestSenderViaReflection(msg, captor);

      msg.reject();

      assertNotNull(captor.lastRequest);
      assertEquals(
          Kubemq.QueuesDownstreamRequestType.NAckRange, captor.lastRequest.getRequestTypeData());
    }

    @Test
    @DisplayName("reQueue() sends ReQueueRange request with channel")
    void reQueue_sendsReQueueRangeRequest() {
      QueueMessageReceived msg = createDecodedMessage(false, 0);

      RequestSenderCaptor captor = new RequestSenderCaptor();
      setRequestSenderViaReflection(msg, captor);

      msg.reQueue("new-queue");

      assertNotNull(captor.lastRequest);
      assertEquals(
          Kubemq.QueuesDownstreamRequestType.ReQueueRange, captor.lastRequest.getRequestTypeData());
      assertEquals("new-queue", captor.lastRequest.getReQueueChannel());
    }

    @Test
    @DisplayName("reQueue() with null channel throws IllegalArgumentException")
    void reQueue_nullChannel_throwsException() {
      QueueMessageReceived msg = createDecodedMessage(false, 0);
      assertThrows(IllegalArgumentException.class, () -> msg.reQueue(null));
    }

    @Test
    @DisplayName("reQueue() with empty channel throws IllegalArgumentException")
    void reQueue_emptyChannel_throwsException() {
      QueueMessageReceived msg = createDecodedMessage(false, 0);
      assertThrows(IllegalArgumentException.class, () -> msg.reQueue(""));
    }

    @Test
    @DisplayName("ack() on auto-acked message throws IllegalStateException")
    void ack_autoAcked_throwsException() {
      QueueMessageReceived msg = createDecodedMessage(true, 0);
      assertThrows(IllegalStateException.class, msg::ack);
    }

    @Test
    @DisplayName("ack() after transaction completed throws IllegalStateException")
    void ack_afterCompleted_throwsException() {
      QueueMessageReceived msg = createDecodedMessage(false, 0);
      RequestSenderCaptor captor = new RequestSenderCaptor();
      setRequestSenderViaReflection(msg, captor);
      msg.ack();

      assertThrows(IllegalStateException.class, msg::ack);
    }

    @Test
    @DisplayName("ack() without requestSender throws IllegalStateException")
    void ack_noRequestSender_throwsException() {
      Kubemq.QueueMessage pbMsg =
          Kubemq.QueueMessage.newBuilder()
              .setMessageID("no-sender")
              .setChannel("ch")
              .setBody(ByteString.copyFromUtf8("data"))
              .setAttributes(Kubemq.QueueMessageAttributes.newBuilder().build())
              .build();

      QueueMessageReceived msg =
          new QueueMessageReceived().decode(pbMsg, "txn", false, "client", 0, false, null);

      assertThrows(IllegalStateException.class, msg::ack);
    }

    @Test
    @DisplayName("markTransactionCompleted sets completed state and cancels timer")
    void markTransactionCompleted_setsState() {
      QueueMessageReceived msg = createDecodedMessage(false, 0);
      msg.markTransactionCompleted();
      assertTrue(msg.isTransactionCompleted());
    }

    @Test
    @DisplayName("extendVisibilityTimer with zero throws IllegalArgumentException")
    void extendVisibilityTimer_zero_throwsException() {
      QueueMessageReceived msg = createDecodedMessage(false, 30);
      assertThrows(IllegalArgumentException.class, () -> msg.extendVisibilityTimer(0));
    }

    @Test
    @DisplayName("extendVisibilityTimer with negative throws IllegalArgumentException")
    void extendVisibilityTimer_negative_throwsException() {
      QueueMessageReceived msg = createDecodedMessage(false, 30);
      assertThrows(IllegalArgumentException.class, () -> msg.extendVisibilityTimer(-1));
    }

    @Test
    @DisplayName("extendVisibilityTimer on active timer succeeds")
    void extendVisibilityTimer_activeTimer_succeeds() {
      QueueMessageReceived msg = createDecodedMessage(false, 60);
      assertDoesNotThrow(() -> msg.extendVisibilityTimer(30));
      msg.markTransactionCompleted();
    }

    @Test
    @DisplayName("resetVisibilityTimer with zero throws IllegalArgumentException")
    void resetVisibilityTimer_zero_throwsException() {
      QueueMessageReceived msg = createDecodedMessage(false, 30);
      assertThrows(IllegalArgumentException.class, () -> msg.resetVisibilityTimer(0));
    }

    @Test
    @DisplayName("resetVisibilityTimer on active timer succeeds")
    void resetVisibilityTimer_activeTimer_succeeds() {
      QueueMessageReceived msg = createDecodedMessage(false, 60);
      assertDoesNotThrow(() -> msg.resetVisibilityTimer(45));
      msg.markTransactionCompleted();
    }

    @Test
    @DisplayName("extendVisibilityTimer without timer throws IllegalStateException")
    void extendVisibilityTimer_noTimer_throwsException() {
      QueueMessageReceived msg = createDecodedMessage(false, 0);
      assertThrows(IllegalStateException.class, () -> msg.extendVisibilityTimer(10));
    }

    @Test
    @DisplayName("resetVisibilityTimer without timer throws IllegalStateException")
    void resetVisibilityTimer_noTimer_throwsException() {
      QueueMessageReceived msg = createDecodedMessage(false, 0);
      assertThrows(IllegalStateException.class, () -> msg.resetVisibilityTimer(10));
    }

    @Test
    @DisplayName("extendVisibilityTimer after completed throws IllegalStateException")
    void extendVisibilityTimer_completed_throwsException() {
      QueueMessageReceived msg = createDecodedMessage(false, 30);
      msg.markTransactionCompleted();
      assertThrows(IllegalStateException.class, () -> msg.extendVisibilityTimer(10));
    }

    @Test
    @DisplayName("resetVisibilityTimer after completed throws IllegalStateException")
    void resetVisibilityTimer_completed_throwsException() {
      QueueMessageReceived msg = createDecodedMessage(false, 30);
      msg.markTransactionCompleted();
      assertThrows(IllegalStateException.class, () -> msg.resetVisibilityTimer(10));
    }
  }

  private static class RequestSenderCaptor implements io.kubemq.sdk.queues.RequestSender {
    Kubemq.QueuesDownstreamRequest lastRequest;

    @Override
    public void send(Kubemq.QueuesDownstreamRequest request) {
      this.lastRequest = request;
    }
  }
}
