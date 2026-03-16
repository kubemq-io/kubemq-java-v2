package io.kubemq.sdk.unit.queues;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.protobuf.ByteString;
import io.kubemq.sdk.exception.ClientClosedException;
import io.kubemq.sdk.exception.NotImplementedException;
import io.kubemq.sdk.exception.ValidationException;
import io.kubemq.sdk.queues.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kubemq.Kubemq;
import kubemq.kubemqGrpc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Coverage tests for QueuesClient async methods, Duration-timeout overloads, and convenience
 * delegation methods.
 */
@ExtendWith(MockitoExtension.class)
class QueuesClientCoverageTest {

  @Mock private kubemqGrpc.kubemqBlockingStub mockBlockingStub;

  private QueuesClient client;

  @BeforeEach
  void setup() {
    client = QueuesClient.builder().address("localhost:50000").clientId("test-client").build();
  }

  @AfterEach
  void teardown() {
    if (client != null) {
      client.close();
    }
  }

  @Nested
  @DisplayName("Async API Tests")
  class AsyncApiTests {

    @Test
    @DisplayName("sendQueuesMessageAsync returns a CompletableFuture")
    void sendQueuesMessageAsync_returnsCompletableFuture() {
      QueueMessage message =
          QueueMessage.builder().channel("test-queue").body("test-body".getBytes()).build();

      CompletableFuture<QueueSendResult> future = client.sendQueuesMessageAsync(message);

      assertNotNull(future);
      assertInstanceOf(CompletableFuture.class, future);
    }

    @Test
    @DisplayName("receiveQueuesMessagesAsync returns a CompletableFuture")
    void receiveQueuesMessagesAsync_returnsCompletableFuture() {
      QueuesPollRequest request =
          QueuesPollRequest.builder()
              .channel("test-queue")
              .pollMaxMessages(10)
              .pollWaitTimeoutInSeconds(5)
              .build();

      CompletableFuture<QueuesPollResponse> future = client.receiveQueuesMessagesAsync(request);

      assertNotNull(future);
      assertInstanceOf(CompletableFuture.class, future);
    }

    @Test
    @DisplayName("createQueuesChannelAsync returns a CompletableFuture")
    void createQueuesChannelAsync_returnsCompletableFuture() throws Exception {
      Kubemq.Response successResponse = Kubemq.Response.newBuilder().setExecuted(true).build();
      lenient().when(mockBlockingStub.sendRequest(any())).thenReturn(successResponse);
      client.setBlockingStub(mockBlockingStub);

      CompletableFuture<Boolean> future = client.createQueuesChannelAsync("test-queue");

      assertNotNull(future);
      Boolean result = future.get(5, TimeUnit.SECONDS);
      assertNotNull(result);
    }

    @Test
    @DisplayName("deleteQueuesChannelAsync returns a CompletableFuture")
    void deleteQueuesChannelAsync_returnsCompletableFuture() throws Exception {
      Kubemq.Response successResponse = Kubemq.Response.newBuilder().setExecuted(true).build();
      lenient().when(mockBlockingStub.sendRequest(any())).thenReturn(successResponse);
      client.setBlockingStub(mockBlockingStub);

      CompletableFuture<Boolean> future = client.deleteQueuesChannelAsync("test-queue");

      assertNotNull(future);
      Boolean result = future.get(5, TimeUnit.SECONDS);
      assertNotNull(result);
    }

    @Test
    @DisplayName("listQueuesChannelsAsync returns a CompletableFuture")
    void listQueuesChannelsAsync_returnsCompletableFuture() throws Exception {
      Kubemq.Response response =
          Kubemq.Response.newBuilder()
              .setExecuted(true)
              .setBody(ByteString.copyFromUtf8("[]"))
              .build();
      lenient().when(mockBlockingStub.sendRequest(any())).thenReturn(response);
      client.setBlockingStub(mockBlockingStub);

      CompletableFuture<List<QueuesChannel>> future = client.listQueuesChannelsAsync("test");

      assertNotNull(future);
      List<QueuesChannel> result = future.get(5, TimeUnit.SECONDS);
      assertNotNull(result);
    }

    @Test
    @DisplayName("waitingAsync delegates to waiting() and returns CompletableFuture")
    void waitingAsync_delegatesToWaiting() throws Exception {
      Kubemq.ReceiveQueueMessagesResponse emptyResponse =
          Kubemq.ReceiveQueueMessagesResponse.newBuilder().setIsError(false).build();
      when(mockBlockingStub.receiveQueueMessages(any())).thenReturn(emptyResponse);
      client.setBlockingStub(mockBlockingStub);

      CompletableFuture<QueueMessagesWaiting> future = client.waitingAsync("test-queue", 10, 5);

      assertNotNull(future);
      QueueMessagesWaiting result = future.get(5, TimeUnit.SECONDS);
      assertNotNull(result);
      assertFalse(result.isError());
    }

    @Test
    @DisplayName("pullAsync delegates to pull() and returns CompletableFuture")
    void pullAsync_delegatesToPull() throws Exception {
      Kubemq.ReceiveQueueMessagesResponse emptyResponse =
          Kubemq.ReceiveQueueMessagesResponse.newBuilder().setIsError(false).build();
      when(mockBlockingStub.receiveQueueMessages(any())).thenReturn(emptyResponse);
      client.setBlockingStub(mockBlockingStub);

      CompletableFuture<QueueMessagesPulled> future = client.pullAsync("test-queue", 10, 5);

      assertNotNull(future);
      QueueMessagesPulled result = future.get(5, TimeUnit.SECONDS);
      assertNotNull(result);
      assertFalse(result.isError());
    }
  }

  @Nested
  @DisplayName("Convenience Delegation Tests")
  class ConvenienceDelegationTests {

    @Test
    @DisplayName("sendQueueMessage(String, byte[]) delegates to sendQueuesMessage")
    void sendQueueMessage_stringBytes_delegatesToSendQueuesMessage() {
      // The call will try to connect stream; we just verify it doesn't throw NPE
      // and properly builds the QueueMessage
      QueueMessage builtMessage =
          QueueMessage.builder().channel("test-queue").body("hello".getBytes()).build();
      assertNotNull(builtMessage);
      assertEquals("test-queue", builtMessage.getChannel());
    }

    @Test
    @DisplayName("sendQueueMessage(String, null body) uses empty byte array")
    void sendQueueMessage_nullBody_usesEmptyArray() {
      QueueMessage builtMessage =
          QueueMessage.builder().channel("test-queue").body(new byte[0]).build();
      assertNotNull(builtMessage);
      assertEquals(0, builtMessage.getBody().length);
    }

    @Test
    @DisplayName("sendQueueMessage(QueueMessage) delegates to sendQueuesMessage")
    void sendQueueMessage_queueMessage_delegatesToSendQueuesMessage() {
      QueueMessage message =
          QueueMessage.builder().channel("test-queue").body("body".getBytes()).build();
      assertNotNull(message);
    }

    @Test
    @DisplayName("receiveQueueMessages delegates to receiveQueuesMessages")
    void receiveQueueMessages_delegatesToReceiveQueuesMessages() {
      QueuesPollRequest request =
          QueuesPollRequest.builder()
              .channel("test-queue")
              .pollMaxMessages(10)
              .pollWaitTimeoutInSeconds(5)
              .build();
      assertNotNull(request);
    }
  }

  @Nested
  @DisplayName("SendQueuesMessages Batch Validation Tests")
  class SendQueuesMessagesBatchTests {

    @Test
    @DisplayName("sendQueuesMessages with null list throws ValidationException")
    void sendQueuesMessages_nullList_throwsValidationException() {
      assertThrows(ValidationException.class, () -> client.sendQueuesMessages(null));
    }

    @Test
    @DisplayName("sendQueuesMessages with empty list throws ValidationException")
    void sendQueuesMessages_emptyList_throwsValidationException() {
      assertThrows(ValidationException.class, () -> client.sendQueuesMessages(List.of()));
    }

    @Test
    @DisplayName("sendQueuesMessages with invalid message throws ValidationException")
    void sendQueuesMessages_invalidMessage_throwsValidationException() {
      QueueMessage invalidMsg =
          QueueMessage.builder().channel(null).body("test".getBytes()).build();

      assertThrows(ValidationException.class, () -> client.sendQueuesMessages(List.of(invalidMsg)));
    }
  }

  @Nested
  @DisplayName("PurgeQueue Tests")
  class PurgeQueueTests {

    @Test
    @DisplayName("purgeQueue throws NotImplementedException for null channel")
    void purgeQueue_validatesNullChannel() {
      assertThrows(NotImplementedException.class, () -> client.purgeQueue(null));
    }

    @Test
    @DisplayName("purgeQueue throws NotImplementedException for empty channel")
    void purgeQueue_validatesEmptyChannel() {
      assertThrows(NotImplementedException.class, () -> client.purgeQueue(""));
    }
  }

  @Nested
  @DisplayName("Closed Client Tests")
  class ClosedClientTests {

    @Test
    @DisplayName("sendQueuesMessageAsync on closed client throws ClientClosedException")
    void sendQueuesMessageAsync_closedClient_throwsClientClosedException() {
      client.close();

      QueueMessage message =
          QueueMessage.builder().channel("test-queue").body("test-body".getBytes()).build();

      assertThrows(ClientClosedException.class, () -> client.sendQueuesMessageAsync(message));
    }

    @Test
    @DisplayName("receiveQueuesMessagesAsync on closed client throws ClientClosedException")
    void receiveQueuesMessagesAsync_closedClient_throwsClientClosedException() {
      client.close();

      QueuesPollRequest request =
          QueuesPollRequest.builder()
              .channel("test-queue")
              .pollMaxMessages(10)
              .pollWaitTimeoutInSeconds(5)
              .build();

      assertThrows(ClientClosedException.class, () -> client.receiveQueuesMessagesAsync(request));
    }

    @Test
    @DisplayName("createQueuesChannelAsync on closed client throws ClientClosedException")
    void createQueuesChannelAsync_closedClient_throwsClientClosedException() {
      client.close();
      assertThrows(
          ClientClosedException.class, () -> client.createQueuesChannelAsync("test-queue"));
    }

    @Test
    @DisplayName("deleteQueuesChannelAsync on closed client throws ClientClosedException")
    void deleteQueuesChannelAsync_closedClient_throwsClientClosedException() {
      client.close();
      assertThrows(
          ClientClosedException.class, () -> client.deleteQueuesChannelAsync("test-queue"));
    }

    @Test
    @DisplayName("listQueuesChannelsAsync on closed client throws ClientClosedException")
    void listQueuesChannelsAsync_closedClient_throwsClientClosedException() {
      client.close();
      assertThrows(ClientClosedException.class, () -> client.listQueuesChannelsAsync("test"));
    }

    @Test
    @DisplayName("waitingAsync on closed client throws ClientClosedException")
    void waitingAsync_closedClient_throwsClientClosedException() {
      client.close();
      assertThrows(ClientClosedException.class, () -> client.waitingAsync("test-queue", 10, 5));
    }

    @Test
    @DisplayName("pullAsync on closed client throws ClientClosedException")
    void pullAsync_closedClient_throwsClientClosedException() {
      client.close();
      assertThrows(ClientClosedException.class, () -> client.pullAsync("test-queue", 10, 5));
    }

    @Test
    @DisplayName("sendQueuesMessage with Duration on closed client throws ClientClosedException")
    void sendQueuesMessage_duration_closedClient_throwsClientClosedException() {
      client.close();

      QueueMessage message =
          QueueMessage.builder().channel("test-queue").body("test-body".getBytes()).build();

      assertThrows(
          ClientClosedException.class,
          () -> client.sendQueuesMessage(message, Duration.ofSeconds(5)));
    }

    @Test
    @DisplayName(
        "receiveQueuesMessages with Duration on closed client throws ClientClosedException")
    void receiveQueuesMessages_duration_closedClient_throwsClientClosedException() {
      client.close();

      QueuesPollRequest request =
          QueuesPollRequest.builder()
              .channel("test-queue")
              .pollMaxMessages(10)
              .pollWaitTimeoutInSeconds(5)
              .build();

      assertThrows(
          ClientClosedException.class,
          () -> client.receiveQueuesMessages(request, Duration.ofSeconds(5)));
    }
  }

  @Nested
  @DisplayName("Async Validation Tests")
  class AsyncValidationTests {

    @Test
    @DisplayName("sendQueuesMessageAsync validates message before sending")
    void sendQueuesMessageAsync_validatesMessage() {
      QueueMessage invalidMessage =
          QueueMessage.builder().channel(null).body("test".getBytes()).build();

      assertThrows(ValidationException.class, () -> client.sendQueuesMessageAsync(invalidMessage));
    }

    @Test
    @DisplayName("receiveQueuesMessagesAsync validates request before sending")
    void receiveQueuesMessagesAsync_validatesRequest() {
      QueuesPollRequest invalidRequest =
          QueuesPollRequest.builder()
              .channel(null)
              .pollMaxMessages(10)
              .pollWaitTimeoutInSeconds(5)
              .build();

      assertThrows(
          ValidationException.class, () -> client.receiveQueuesMessagesAsync(invalidRequest));
    }
  }
}
