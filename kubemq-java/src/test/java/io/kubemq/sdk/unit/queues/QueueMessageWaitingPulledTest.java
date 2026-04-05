package io.kubemq.sdk.unit.queues;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.queues.QueueMessageWaitingPulled;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import kubemq.Kubemq;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for QueueMessageWaitingPulled POJO and decode method. */
class QueueMessageWaitingPulledTest {

  @Nested
  class BuilderTests {

    @Test
    void builder_withAllFields_createsMessage() {
      Map<String, String> tags = new HashMap<>();
      tags.put("key1", "value1");
      Instant now = Instant.now();

      QueueMessageWaitingPulled msg =
          QueueMessageWaitingPulled.builder()
              .id("msg-123")
              .channel("orders-queue")
              .metadata("test-metadata")
              .body("test-body".getBytes())
              .fromClientId("sender-client")
              .tags(tags)
              .timestamp(now)
              .sequence(100L)
              .receiveCount(1)
              .isReRouted(false)
              .reRouteFromQueue("")
              .expiredAt(now.plusSeconds(3600))
              .delayedTo(now.plusSeconds(60))
              .receiverClientId("receiver-client")
              .build();

      assertEquals("msg-123", msg.getId());
      assertEquals("orders-queue", msg.getChannel());
      assertEquals("test-metadata", msg.getMetadata());
      assertArrayEquals("test-body".getBytes(), msg.getBody());
      assertEquals("sender-client", msg.getFromClientId());
      assertEquals("value1", msg.getTags().get("key1"));
      assertEquals(now, msg.getTimestamp());
      assertEquals(100L, msg.getSequence());
      assertEquals(1, msg.getReceiveCount());
      assertFalse(msg.isReRouted());
      assertEquals("", msg.getReRouteFromQueue());
      assertEquals("receiver-client", msg.getReceiverClientId());
    }

    @Test
    void builder_withMinimalFields_createsMessage() {
      QueueMessageWaitingPulled msg =
          QueueMessageWaitingPulled.builder().id("minimal-msg").channel("test-queue").build();

      assertEquals("minimal-msg", msg.getId());
      assertEquals("test-queue", msg.getChannel());
      assertNotNull(msg.getTags());
      assertTrue(msg.getTags().isEmpty());
    }

    @Test
    void builder_defaultTags_initializesToEmptyMap() {
      QueueMessageWaitingPulled msg = QueueMessageWaitingPulled.builder().id("test").build();

      assertNotNull(msg.getTags());
      assertTrue(msg.getTags().isEmpty());
    }
  }

  @Nested
  class DecodeTests {

    @Test
    void decode_withAllFields_decodesCorrectly() {
      Map<String, String> tags = new HashMap<>();
      tags.put("decoded-key", "decoded-value");

      long timestampNanos = System.currentTimeMillis() * 1_000_000L;
      long expirationNanos = (System.currentTimeMillis() + 3600000) * 1_000_000L;
      long delayNanos = (System.currentTimeMillis() + 60000) * 1_000_000L;

      Kubemq.QueueMessage protoMsg =
          Kubemq.QueueMessage.newBuilder()
              .setMessageID("decoded-msg")
              .setChannel("decoded-queue")
              .setMetadata("decoded-metadata")
              .setBody(com.google.protobuf.ByteString.copyFrom("decoded-body".getBytes()))
              .setClientID("sender")
              .putAllTags(tags)
              .setAttributes(
                  Kubemq.QueueMessageAttributes.newBuilder()
                      .setTimestamp(timestampNanos)
                      .setSequence(200L)
                      .setReceiveCount(2)
                      .setReRouted(true)
                      .setReRoutedFromQueue("original-queue")
                      .setExpirationAt(expirationNanos)
                      .setDelayedTo(delayNanos)
                      .build())
              .build();

      QueueMessageWaitingPulled decoded = QueueMessageWaitingPulled.decode(protoMsg, "receiver");

      assertEquals("decoded-msg", decoded.getId());
      assertEquals("decoded-queue", decoded.getChannel());
      assertEquals("decoded-metadata", decoded.getMetadata());
      assertArrayEquals("decoded-body".getBytes(), decoded.getBody());
      assertEquals("sender", decoded.getFromClientId());
      assertEquals("decoded-value", decoded.getTags().get("decoded-key"));
      assertNotNull(decoded.getTimestamp());
      assertEquals(200L, decoded.getSequence());
      assertEquals(2, decoded.getReceiveCount());
      assertTrue(decoded.isReRouted());
      assertEquals("original-queue", decoded.getReRouteFromQueue());
      assertEquals("receiver", decoded.getReceiverClientId());
    }

    @Test
    void decode_withEmptyTags_createsEmptyMap() {
      Kubemq.QueueMessage protoMsg =
          Kubemq.QueueMessage.newBuilder()
              .setMessageID("empty-tags")
              .setChannel("test")
              .setAttributes(Kubemq.QueueMessageAttributes.newBuilder().build())
              .build();

      QueueMessageWaitingPulled decoded = QueueMessageWaitingPulled.decode(protoMsg, "receiver");

      assertNotNull(decoded.getTags());
      assertTrue(decoded.getTags().isEmpty());
    }
  }

  @Nested
  class TimestampConversionTests {

    /**
     * Verifies that nanosecond timestamps from the KubeMQ server are correctly converted to Instant
     * values representing reasonable dates (not year 58173). This is a regression test for a bug
     * where division by 1_000_000 (milliseconds) was used instead of 1_000_000_000 (nanoseconds).
     */
    @Test
    void decode_timestampsFromNanoseconds_produceCorrectInstants() {
      // 2025-01-15 12:00:00 UTC in epoch seconds
      long epochSeconds = 1736942400L;
      // Server sends nanoseconds
      long timestampNanos = epochSeconds * 1_000_000_000L;
      long expirationNanos = (epochSeconds + 3600) * 1_000_000_000L; // +1 hour
      long delayNanos = (epochSeconds + 60) * 1_000_000_000L; // +1 minute

      Kubemq.QueueMessage protoMsg =
          Kubemq.QueueMessage.newBuilder()
              .setMessageID("ts-test")
              .setChannel("ts-queue")
              .setAttributes(
                  Kubemq.QueueMessageAttributes.newBuilder()
                      .setTimestamp(timestampNanos)
                      .setExpirationAt(expirationNanos)
                      .setDelayedTo(delayNanos)
                      .build())
              .build();

      QueueMessageWaitingPulled decoded = QueueMessageWaitingPulled.decode(protoMsg, "receiver");

      // Timestamp should be 2025-01-15 12:00:00 UTC
      assertEquals(Instant.ofEpochSecond(epochSeconds), decoded.getTimestamp());
      // ExpirationAt should be 2025-01-15 13:00:00 UTC (1 hour later)
      assertEquals(Instant.ofEpochSecond(epochSeconds + 3600), decoded.getExpiredAt());
      // DelayedTo should be 2025-01-15 12:01:00 UTC (1 minute later)
      assertEquals(Instant.ofEpochSecond(epochSeconds + 60), decoded.getDelayedTo());

      // All timestamps should be within a reasonable year range (2020-2030)
      int year = decoded.getTimestamp().atZone(java.time.ZoneOffset.UTC).getYear();
      assertTrue(
          year >= 2020 && year <= 2030,
          "Timestamp year should be between 2020 and 2030, but was " + year);

      int expYear = decoded.getExpiredAt().atZone(java.time.ZoneOffset.UTC).getYear();
      assertTrue(
          expYear >= 2020 && expYear <= 2030,
          "ExpirationAt year should be between 2020 and 2030, but was " + expYear);

      int delayYear = decoded.getDelayedTo().atZone(java.time.ZoneOffset.UTC).getYear();
      assertTrue(
          delayYear >= 2020 && delayYear <= 2030,
          "DelayedTo year should be between 2020 and 2030, but was " + delayYear);
    }

    @Test
    void decode_withCurrentTimeNanoseconds_producesCurrentYearTimestamps() {
      long nowSeconds = Instant.now().getEpochSecond();
      long nowNanos = nowSeconds * 1_000_000_000L;
      long expirationNanos = (nowSeconds + 7200) * 1_000_000_000L; // +2 hours
      long delayNanos = (nowSeconds + 300) * 1_000_000_000L; // +5 minutes

      Kubemq.QueueMessage protoMsg =
          Kubemq.QueueMessage.newBuilder()
              .setMessageID("current-ts-test")
              .setChannel("ts-queue")
              .setAttributes(
                  Kubemq.QueueMessageAttributes.newBuilder()
                      .setTimestamp(nowNanos)
                      .setExpirationAt(expirationNanos)
                      .setDelayedTo(delayNanos)
                      .build())
              .build();

      QueueMessageWaitingPulled decoded = QueueMessageWaitingPulled.decode(protoMsg, "receiver");

      // Verify the decoded timestamp matches the original epoch second
      assertEquals(nowSeconds, decoded.getTimestamp().getEpochSecond());
      assertEquals(nowSeconds + 7200, decoded.getExpiredAt().getEpochSecond());
      assertEquals(nowSeconds + 300, decoded.getDelayedTo().getEpochSecond());
    }

    @Test
    void decode_withZeroExpirationAndDelay_producesEpochInstant() {
      long nowNanos = Instant.now().getEpochSecond() * 1_000_000_000L;

      Kubemq.QueueMessage protoMsg =
          Kubemq.QueueMessage.newBuilder()
              .setMessageID("zero-exp-test")
              .setChannel("ts-queue")
              .setAttributes(
                  Kubemq.QueueMessageAttributes.newBuilder()
                      .setTimestamp(nowNanos)
                      .setExpirationAt(0)
                      .setDelayedTo(0)
                      .build())
              .build();

      QueueMessageWaitingPulled decoded = QueueMessageWaitingPulled.decode(protoMsg, "receiver");

      // Zero nanoseconds / 1_000_000_000 = 0 epoch seconds = epoch
      assertEquals(Instant.ofEpochSecond(0), decoded.getExpiredAt());
      assertEquals(Instant.ofEpochSecond(0), decoded.getDelayedTo());
    }
  }

  @Nested
  class GetterSetterTests {

    @Test
    void setId_updatesId() {
      QueueMessageWaitingPulled msg = new QueueMessageWaitingPulled();
      msg.setId("new-id");
      assertEquals("new-id", msg.getId());
    }

    @Test
    void setChannel_updatesChannel() {
      QueueMessageWaitingPulled msg = new QueueMessageWaitingPulled();
      msg.setChannel("new-channel");
      assertEquals("new-channel", msg.getChannel());
    }

    @Test
    void setMetadata_updatesMetadata() {
      QueueMessageWaitingPulled msg = new QueueMessageWaitingPulled();
      msg.setMetadata("new-metadata");
      assertEquals("new-metadata", msg.getMetadata());
    }

    @Test
    void setBody_updatesBody() {
      QueueMessageWaitingPulled msg = new QueueMessageWaitingPulled();
      byte[] body = "new-body".getBytes();
      msg.setBody(body);
      assertArrayEquals(body, msg.getBody());
    }

    @Test
    void setSequence_updatesSequence() {
      QueueMessageWaitingPulled msg = new QueueMessageWaitingPulled();
      msg.setSequence(500L);
      assertEquals(500L, msg.getSequence());
    }

    @Test
    void setReceiveCount_updatesReceiveCount() {
      QueueMessageWaitingPulled msg = new QueueMessageWaitingPulled();
      msg.setReceiveCount(3);
      assertEquals(3, msg.getReceiveCount());
    }

    @Test
    void setReRouted_updatesReRouted() {
      QueueMessageWaitingPulled msg = new QueueMessageWaitingPulled();
      msg.setReRouted(true);
      assertTrue(msg.isReRouted());
    }

    @Test
    void setReceiverClientId_updatesReceiverClientId() {
      QueueMessageWaitingPulled msg = new QueueMessageWaitingPulled();
      msg.setReceiverClientId("new-receiver");
      assertEquals("new-receiver", msg.getReceiverClientId());
    }
  }

  @Nested
  class ToStringTests {

    @Test
    void toString_includesAllFields() {
      QueueMessageWaitingPulled msg =
          QueueMessageWaitingPulled.builder()
              .id("tostring-msg")
              .channel("tostring-queue")
              .metadata("meta")
              .body("body".getBytes())
              .fromClientId("sender")
              .sequence(100L)
              .receiveCount(1)
              .build();

      String str = msg.toString();

      assertTrue(str.contains("tostring-msg"));
      assertTrue(str.contains("tostring-queue"));
      assertTrue(str.contains("meta"));
      assertTrue(str.contains("100"));
    }

    @Test
    void toString_withNullBody_handlesGracefully() {
      QueueMessageWaitingPulled msg =
          QueueMessageWaitingPulled.builder().id("null-body").channel("test").body(null).build();

      // Should not throw NullPointerException
      assertThrows(NullPointerException.class, msg::toString);
    }
  }

  @Nested
  class NoArgsConstructorTests {

    @Test
    void noArgsConstructor_createsInstanceWithDefaults() {
      QueueMessageWaitingPulled msg = new QueueMessageWaitingPulled();

      assertNull(msg.getId());
      assertNull(msg.getChannel());
      assertNull(msg.getMetadata());
      assertNull(msg.getBody());
      assertEquals(0, msg.getSequence());
      assertEquals(0, msg.getReceiveCount());
      assertFalse(msg.isReRouted());
    }
  }
}
