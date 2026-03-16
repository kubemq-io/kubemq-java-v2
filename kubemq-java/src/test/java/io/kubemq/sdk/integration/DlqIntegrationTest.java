package io.kubemq.sdk.integration;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.queues.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/**
 * Integration tests for server-side Dead Letter Queue (DLQ) functionality. Requires a running
 * KubeMQ server at localhost:50000 (or configured via KUBEMQ_ADDRESS).
 *
 * <p>Validates that messages sent with attemptsBeforeDeadLetterQueue and deadLetterQueue are
 * automatically routed to the DLQ by the server after N rejections.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DlqIntegrationTest extends BaseIntegrationTest {

  private QueuesClient client;
  private String testChannel;
  private String dlqChannel;

  @BeforeAll
  void setup() {
    testChannel = uniqueChannel("dlq-test");
    dlqChannel = "dlq-" + testChannel;
    client =
        QueuesClient.builder()
            .address(kubemqAddress)
            .clientId(uniqueClientId("dlq"))
            .logLevel(KubeMQClient.Level.INFO)
            .build();

    // Verify connection
    assertNotNull(client.ping(), "KubeMQ server should be reachable");
  }

  @AfterAll
  void teardown() {
    if (client != null) {
      try {
        client.deleteQueuesChannel(testChannel);
      } catch (Exception ignored) {
      }
      try {
        client.deleteQueuesChannel(dlqChannel);
      } catch (Exception ignored) {
      }
      client.close();
    }
  }

  @Test
  @Order(1)
  @DisplayName("Server-side DLQ - message routes to DLQ after N rejections")
  void serverSideDlq_shouldRouteAfterMaxRejections() {
    String channel = uniqueChannel("dlq-route");
    String dlq = "dlq-" + channel;
    int maxAttempts = 3;

    try {
      // Send a message with DLQ config
      QueueMessage message =
          QueueMessage.builder()
              .channel(channel)
              .body("dlq test message".getBytes())
              .metadata("test-metadata")
              .attemptsBeforeDeadLetterQueue(maxAttempts)
              .deadLetterQueue(dlq)
              .build();

      QueueSendResult sendResult = client.sendQueuesMessage(message);
      assertFalse(sendResult.isError(), "Send should succeed: " + sendResult.getError());

      // Reject the message maxAttempts times
      QueuesPollRequest pollRequest =
          QueuesPollRequest.builder()
              .channel(channel)
              .pollMaxMessages(1)
              .pollWaitTimeoutInSeconds(5)
              .autoAckMessages(false)
              .build();

      for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        QueuesPollResponse response = client.receiveQueuesMessages(pollRequest);
        assertFalse(response.isError(), "Poll should not error on attempt " + attempt);

        if (response.getMessages().isEmpty()) {
          // Message may have already been routed to DLQ
          break;
        }

        QueueMessageReceived received = response.getMessages().get(0);
        received.reject();
        sleep(500);
      }

      // Wait for server to process the DLQ routing
      sleep(2, TimeUnit.SECONDS);

      // Verify message is no longer in the main queue
      QueuesPollRequest mainPoll =
          QueuesPollRequest.builder()
              .channel(channel)
              .pollMaxMessages(1)
              .pollWaitTimeoutInSeconds(2)
              .autoAckMessages(true)
              .build();

      QueuesPollResponse mainResponse = client.receiveQueuesMessages(mainPoll);
      assertTrue(
          mainResponse.getMessages().isEmpty(),
          "Message should no longer be in main queue after max rejections");

      // Verify message arrived in DLQ
      QueuesPollRequest dlqPoll =
          QueuesPollRequest.builder()
              .channel(dlq)
              .pollMaxMessages(1)
              .pollWaitTimeoutInSeconds(5)
              .autoAckMessages(true)
              .build();

      QueuesPollResponse dlqResponse = client.receiveQueuesMessages(dlqPoll);
      assertFalse(dlqResponse.isError(), "DLQ poll should not error: " + dlqResponse.getError());
      assertFalse(dlqResponse.getMessages().isEmpty(), "Message should be in DLQ");

      QueueMessageReceived dlqMessage = dlqResponse.getMessages().get(0);
      assertEquals("dlq test message", new String(dlqMessage.getBody()));
      assertTrue(dlqMessage.isReRouted(), "Message should be marked as re-routed");

    } finally {
      try {
        client.deleteQueuesChannel(channel);
      } catch (Exception ignored) {
      }
      try {
        client.deleteQueuesChannel(dlq);
      } catch (Exception ignored) {
      }
    }
  }

  @Test
  @Order(2)
  @DisplayName("Server-side DLQ - message acked before max attempts stays in main queue")
  void serverSideDlq_shouldNotRouteToDlqIfAcked() {
    String channel = uniqueChannel("dlq-ack");
    String dlq = "dlq-" + channel;
    int maxAttempts = 3;

    try {
      // Send a message with DLQ config
      QueueMessage message =
          QueueMessage.builder()
              .channel(channel)
              .body("ack before dlq".getBytes())
              .attemptsBeforeDeadLetterQueue(maxAttempts)
              .deadLetterQueue(dlq)
              .build();

      QueueSendResult sendResult = client.sendQueuesMessage(message);
      assertFalse(sendResult.isError(), "Send should succeed");

      // Reject once, then ack on second attempt
      QueuesPollRequest pollRequest =
          QueuesPollRequest.builder()
              .channel(channel)
              .pollMaxMessages(1)
              .pollWaitTimeoutInSeconds(5)
              .autoAckMessages(false)
              .build();

      // First attempt: reject
      QueuesPollResponse response1 = client.receiveQueuesMessages(pollRequest);
      assertFalse(response1.getMessages().isEmpty());
      response1.getMessages().get(0).reject();
      sleep(500);

      // Second attempt: ack
      QueuesPollResponse response2 = client.receiveQueuesMessages(pollRequest);
      assertFalse(response2.getMessages().isEmpty());
      response2.getMessages().get(0).ack();

      // Verify DLQ is empty
      sleep(1, TimeUnit.SECONDS);
      QueuesPollRequest dlqPoll =
          QueuesPollRequest.builder()
              .channel(dlq)
              .pollMaxMessages(1)
              .pollWaitTimeoutInSeconds(2)
              .autoAckMessages(true)
              .build();

      QueuesPollResponse dlqResponse = client.receiveQueuesMessages(dlqPoll);
      assertTrue(
          dlqResponse.getMessages().isEmpty(),
          "DLQ should be empty since message was acked before max attempts");

    } finally {
      try {
        client.deleteQueuesChannel(channel);
      } catch (Exception ignored) {
      }
      try {
        client.deleteQueuesChannel(dlq);
      } catch (Exception ignored) {
      }
    }
  }

  @Test
  @Order(3)
  @DisplayName("Server-side DLQ - receive count increments on each rejection")
  void serverSideDlq_shouldIncrementReceiveCount() {
    String channel = uniqueChannel("dlq-count");
    String dlq = "dlq-" + channel;
    int maxAttempts = 5;

    try {
      QueueMessage message =
          QueueMessage.builder()
              .channel(channel)
              .body("count test".getBytes())
              .attemptsBeforeDeadLetterQueue(maxAttempts)
              .deadLetterQueue(dlq)
              .build();

      client.sendQueuesMessage(message);

      QueuesPollRequest pollRequest =
          QueuesPollRequest.builder()
              .channel(channel)
              .pollMaxMessages(1)
              .pollWaitTimeoutInSeconds(5)
              .autoAckMessages(false)
              .build();

      // Reject twice and verify receive count increments
      for (int expected = 1; expected <= 2; expected++) {
        QueuesPollResponse response = client.receiveQueuesMessages(pollRequest);
        assertFalse(response.getMessages().isEmpty());

        QueueMessageReceived received = response.getMessages().get(0);
        assertEquals(expected, received.getReceiveCount(), "Receive count should be " + expected);
        received.reject();
        sleep(500);
      }

      // Ack on third attempt to clean up
      QueuesPollResponse response = client.receiveQueuesMessages(pollRequest);
      assertFalse(response.getMessages().isEmpty());
      assertEquals(3, response.getMessages().get(0).getReceiveCount());
      response.getMessages().get(0).ack();

    } finally {
      try {
        client.deleteQueuesChannel(channel);
      } catch (Exception ignored) {
      }
      try {
        client.deleteQueuesChannel(dlq);
      } catch (Exception ignored) {
      }
    }
  }
}
