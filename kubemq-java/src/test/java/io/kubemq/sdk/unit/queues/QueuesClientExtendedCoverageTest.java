package io.kubemq.sdk.unit.queues;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.exception.NotImplementedException;
import io.kubemq.sdk.exception.ValidationException;
import io.kubemq.sdk.queues.*;
import java.util.List;
import org.junit.jupiter.api.*;

/**
 * Extended coverage tests for QueuesClient: - sendQueueMessage convenience method validation -
 * sendQueueMessage(QueueMessage) validation - sendQueuesMessages batch validation - purgeQueue
 * NotImplementedException - receiveQueueMessages delegation
 */
@DisplayName("QueuesClient extended coverage")
class QueuesClientExtendedCoverageTest {

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
  @DisplayName("sendQueueMessage(String, byte[]) convenience method")
  class SendQueueMessageConvenienceTests {

    @Test
    @DisplayName("null body via convenience method fails validation without metadata")
    void sendWithNullBodyNoMetadata_failsValidation() {
      assertThrows(ValidationException.class, () -> client.sendQueueMessage("test-queue", null));
    }

    @Test
    @DisplayName("null channel fails validation")
    void sendWithNullChannel_failsValidation() {
      assertThrows(
          ValidationException.class, () -> client.sendQueueMessage(null, "hello".getBytes()));
    }

    @Test
    @DisplayName("empty channel fails validation")
    void sendWithEmptyChannel_failsValidation() {
      assertThrows(
          ValidationException.class, () -> client.sendQueueMessage("", "hello".getBytes()));
    }
  }

  @Nested
  @DisplayName("sendQueueMessage(QueueMessage) validation")
  class SendQueueMessageValidationTests {

    @Test
    @DisplayName("message with null channel fails validation")
    void nullChannel_failsValidation() {
      QueueMessage msg = QueueMessage.builder().body("data".getBytes()).build();

      assertThrows(ValidationException.class, () -> client.sendQueueMessage(msg));
    }

    @Test
    @DisplayName("message with empty body and no metadata fails validation")
    void emptyBodyNoMetadata_failsValidation() {
      QueueMessage msg =
          QueueMessage.builder().channel("test-queue").body(new byte[0]).build();

      assertThrows(ValidationException.class, () -> client.sendQueueMessage(msg));
    }

    @Test
    @DisplayName("valid message with metadata passes validation (may fail on connection)")
    void validMessageWithMetadata_passesValidation() {
      QueueMessage msg =
          QueueMessage.builder()
              .channel("test-queue")
              .body(new byte[0])
              .metadata("meta")
              .build();

      // Validation passes but will fail on actual send due to no connection
      // The important thing is that no ValidationException is thrown
      try {
        client.sendQueueMessage(msg);
      } catch (ValidationException e) {
        fail("Should not throw ValidationException for valid message: " + e.getMessage());
      } catch (Exception e) {
        // Expected: connection-related errors are acceptable
      }
    }
  }

  @Nested
  @DisplayName("sendQueuesMessages batch validation")
  class SendQueuesMessagesBatchValidationTests {

    @Test
    @DisplayName("null list throws ValidationException")
    void nullList_throwsValidationException() {
      assertThrows(ValidationException.class, () -> client.sendQueuesMessages(null));
    }

    @Test
    @DisplayName("empty list throws ValidationException")
    void emptyList_throwsValidationException() {
      assertThrows(ValidationException.class, () -> client.sendQueuesMessages(List.of()));
    }

    @Test
    @DisplayName("list with invalid message throws ValidationException")
    void invalidMessage_throwsValidationException() {
      QueueMessage invalidMsg = QueueMessage.builder().body("data".getBytes()).build();

      assertThrows(
          ValidationException.class, () -> client.sendQueuesMessages(List.of(invalidMsg)));
    }
  }

  @Nested
  @DisplayName("purgeQueue tests")
  class PurgeQueueTests {

    @Test
    @DisplayName("purgeQueue throws NotImplementedException")
    void purgeQueue_throwsNotImplementedException() {
      // purgeQueue is not implemented in this SDK version; it always throws NotImplementedException
      NotImplementedException ex =
          assertThrows(NotImplementedException.class, () -> client.purgeQueue("test-queue"));
      assertTrue(ex.getMessage().contains("not implemented"));
    }
  }

  @Nested
  @DisplayName("receiveQueueMessages delegation")
  class ReceiveQueueMessagesDelegationTests {

    @Test
    @DisplayName("receiveQueueMessages validates request same as receiveQueuesMessages")
    void receiveQueueMessages_validatesLikeReceiveQueuesMessages() {
      // Both methods should validate the request the same way
      QueuesPollRequest invalidRequest =
          QueuesPollRequest.builder()
              .channel(null)
              .pollMaxMessages(10)
              .pollWaitTimeoutInSeconds(5)
              .build();

      assertThrows(ValidationException.class, () -> client.receiveQueueMessages(invalidRequest));
    }
  }
}
