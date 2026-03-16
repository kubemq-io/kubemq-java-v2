package io.kubemq.sdk.unit.common;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.exception.ValidationException;
import io.kubemq.sdk.pubsub.EventMessage;
import io.kubemq.sdk.pubsub.EventStoreMessage;
import io.kubemq.sdk.queues.QueueMessage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Message validation edge cases per REQ-TEST-1 AC-8 (oversized) and AC-9 (null/empty). */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class MessageValidationEdgeCaseTest {

  @Nested
  class OversizedMessageTests {

    @Test
    void eventMessage_exceedsMaxSize_throwsValidationException() {
      byte[] oversizedBody = new byte[100 * 1024 * 1024 + 1];
      EventMessage msg = EventMessage.builder().channel("test-channel").body(oversizedBody).build();

      ValidationException thrown = assertThrows(ValidationException.class, msg::validate);
      assertTrue(
          thrown.getMessage().contains("100 MB") || thrown.getMessage().contains("body"),
          "Error message should reference size limit, got: " + thrown.getMessage());
    }

    @Test
    void queueMessage_exceedsMaxSize_throwsValidationException() {
      byte[] oversizedBody = new byte[100 * 1024 * 1024 + 1];
      QueueMessage msg = QueueMessage.builder().channel("test-channel").body(oversizedBody).build();

      ValidationException thrown = assertThrows(ValidationException.class, msg::validate);
      assertTrue(
          thrown.getMessage().contains("100 MB") || thrown.getMessage().contains("body"),
          "Error message should reference size limit, got: " + thrown.getMessage());
    }

    @Test
    void eventMessage_exactlyMaxSize_doesNotThrow() {
      byte[] maxBody = new byte[100 * 1024 * 1024];
      EventMessage msg = EventMessage.builder().channel("test-channel").body(maxBody).build();

      assertDoesNotThrow(msg::validate);
    }
  }

  @Nested
  class EmptyAndNullPayloadTests {

    @Test
    void eventMessage_emptyBody_withMetadata_succeeds() {
      EventMessage msg =
          EventMessage.builder()
              .channel("test-channel")
              .body(new byte[0])
              .metadata("some-metadata")
              .build();

      assertDoesNotThrow(msg::validate);
    }

    @Test
    void eventMessage_emptyBody_withTags_succeeds() {
      Map<String, String> tags = new HashMap<>();
      tags.put("key", "value");

      EventMessage msg =
          EventMessage.builder().channel("test-channel").body(new byte[0]).tags(tags).build();

      assertDoesNotThrow(msg::validate);
    }

    @Test
    void eventMessage_emptyBodyAndNoMetadataOrTags_throwsValidation() {
      EventMessage msg = EventMessage.builder().channel("test-channel").body(new byte[0]).build();

      assertThrows(ValidationException.class, msg::validate);
    }

    @Test
    void queueMessage_emptyBody_withMetadata_succeeds() {
      QueueMessage msg =
          QueueMessage.builder()
              .channel("test-channel")
              .body(new byte[0])
              .metadata("some-metadata")
              .build();

      assertDoesNotThrow(msg::validate);
    }

    @Test
    void queueMessage_emptyBody_withTags_succeeds() {
      Map<String, String> tags = new HashMap<>();
      tags.put("key", "value");

      QueueMessage msg =
          QueueMessage.builder().channel("test-channel").body(new byte[0]).tags(tags).build();

      assertDoesNotThrow(msg::validate);
    }

    @Test
    void queueMessage_emptyBodyAndNoMetadataOrTags_throwsValidation() {
      QueueMessage msg = QueueMessage.builder().channel("test-channel").body(new byte[0]).build();

      assertThrows(ValidationException.class, msg::validate);
    }
  }

  @Nested
  class ChannelValidationTests {

    @Test
    void eventMessage_nullChannel_throwsValidation() {
      EventMessage msg = EventMessage.builder().channel(null).body("data".getBytes()).build();

      assertThrows(ValidationException.class, msg::validate);
    }

    @Test
    void eventMessage_emptyChannel_throwsValidation() {
      EventMessage msg = EventMessage.builder().channel("").body("data".getBytes()).build();

      assertThrows(ValidationException.class, msg::validate);
    }

    @Test
    void queueMessage_nullChannel_throwsValidation() {
      QueueMessage msg = QueueMessage.builder().channel(null).body("data".getBytes()).build();

      assertThrows(ValidationException.class, msg::validate);
    }

    @Test
    void queueMessage_emptyChannel_throwsValidation() {
      QueueMessage msg = QueueMessage.builder().channel("").body("data".getBytes()).build();

      assertThrows(ValidationException.class, msg::validate);
    }
  }

  @Nested
  class EventStoreMessageTests {

    @Test
    void eventStoreMessage_exceedsMaxSize_throwsValidation() {
      byte[] oversizedBody = new byte[100 * 1024 * 1024 + 1];
      EventStoreMessage msg =
          EventStoreMessage.builder().channel("test-channel").body(oversizedBody).build();

      assertThrows(ValidationException.class, msg::validate);
    }

    @Test
    void eventStoreMessage_validMessage_succeeds() {
      EventStoreMessage msg =
          EventStoreMessage.builder().channel("test-channel").body("data".getBytes()).build();

      assertDoesNotThrow(msg::validate);
    }
  }
}
