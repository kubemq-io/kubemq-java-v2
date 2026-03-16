package io.kubemq.sdk.unit.queues;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.queues.QueueMessageReceived;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import kubemq.Kubemq;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for QueueMessageReceived POJO and decode method. Note: Transaction operations (ack,
 * reject, requeue) are tested via integration tests as they require active gRPC streams.
 */
class QueueMessageReceivedTest {

  @Nested
  class BuilderTests {

    @Test
    void builder_withAllFields_createsMessage() {
      Map<String, String> tags = new HashMap<>();
      tags.put("key1", "value1");
      Instant now = Instant.now();

      QueueMessageReceived msg =
          QueueMessageReceived.builder()
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
              .transactionId("txn-456")
              .receiverClientId("receiver-client")
              .visibilitySeconds(30)
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
      assertEquals("txn-456", msg.getTransactionId());
      assertEquals("receiver-client", msg.getReceiverClientId());
      assertEquals(30, msg.getVisibilitySeconds());
    }

    @Test
    void builder_withMinimalFields_createsMessage() {
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("minimal-msg").channel("test-queue").build();

      assertEquals("minimal-msg", msg.getId());
      assertEquals("test-queue", msg.getChannel());
      assertNotNull(msg.getTags());
      assertTrue(msg.getTags().isEmpty());
    }

    @Test
    void builder_defaultTags_initializesToEmptyMap() {
      QueueMessageReceived msg = QueueMessageReceived.builder().id("test").build();

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

      QueueMessageReceived decoded =
          new QueueMessageReceived().decode(protoMsg, "txn-123", false, "receiver", 0, false, null);

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
      assertEquals("txn-123", decoded.getTransactionId());
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

      QueueMessageReceived decoded =
          new QueueMessageReceived().decode(protoMsg, "txn-1", false, "receiver", 0, false, null);

      assertNotNull(decoded.getTags());
      assertTrue(decoded.getTags().isEmpty());
    }

    @Test
    void decode_withAutoAcked_setsAutoAckedFlag() {
      Kubemq.QueueMessage protoMsg =
          Kubemq.QueueMessage.newBuilder()
              .setMessageID("auto-ack-msg")
              .setChannel("test")
              .setAttributes(Kubemq.QueueMessageAttributes.newBuilder().build())
              .build();

      QueueMessageReceived decoded =
          new QueueMessageReceived().decode(protoMsg, "txn-1", false, "receiver", 0, true, null);

      assertTrue(decoded.isAutoAcked());
    }

    @Test
    void decode_returnsThis() {
      Kubemq.QueueMessage protoMsg =
          Kubemq.QueueMessage.newBuilder()
              .setMessageID("self-return")
              .setChannel("test")
              .setAttributes(Kubemq.QueueMessageAttributes.newBuilder().build())
              .build();

      QueueMessageReceived msg = new QueueMessageReceived();
      QueueMessageReceived decoded =
          msg.decode(protoMsg, "txn-1", false, "receiver", 0, false, null);

      assertSame(msg, decoded);
    }
  }

  @Nested
  class TimestampConversionTests {

    /**
     * Verifies that nanosecond timestamps from the KubeMQ server are correctly
     * converted to Instant values representing reasonable dates (not year 58173).
     * This is a regression test for a bug where division by 1_000_000 (milliseconds)
     * was used instead of 1_000_000_000 (nanoseconds).
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

      QueueMessageReceived decoded =
          new QueueMessageReceived().decode(protoMsg, "txn-ts", false, "receiver", 0, false, null);

      // Timestamp should be 2025-01-15 12:00:00 UTC
      assertEquals(Instant.ofEpochSecond(epochSeconds), decoded.getTimestamp());
      // ExpirationAt should be 2025-01-15 13:00:00 UTC (1 hour later)
      assertEquals(Instant.ofEpochSecond(epochSeconds + 3600), decoded.getExpiredAt());
      // DelayedTo should be 2025-01-15 12:01:00 UTC (1 minute later)
      assertEquals(Instant.ofEpochSecond(epochSeconds + 60), decoded.getDelayedTo());

      // All timestamps should be within a reasonable year range (2020-2030)
      int year = decoded.getTimestamp().atZone(java.time.ZoneOffset.UTC).getYear();
      assertTrue(year >= 2020 && year <= 2030,
          "Timestamp year should be between 2020 and 2030, but was " + year);

      int expYear = decoded.getExpiredAt().atZone(java.time.ZoneOffset.UTC).getYear();
      assertTrue(expYear >= 2020 && expYear <= 2030,
          "ExpirationAt year should be between 2020 and 2030, but was " + expYear);

      int delayYear = decoded.getDelayedTo().atZone(java.time.ZoneOffset.UTC).getYear();
      assertTrue(delayYear >= 2020 && delayYear <= 2030,
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

      QueueMessageReceived decoded =
          new QueueMessageReceived().decode(protoMsg, "txn-ts2", false, "receiver", 0, false, null);

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

      QueueMessageReceived decoded =
          new QueueMessageReceived().decode(protoMsg, "txn-ts3", false, "receiver", 0, false, null);

      // Zero nanoseconds / 1_000_000_000 = 0 epoch seconds = epoch
      assertEquals(Instant.ofEpochSecond(0), decoded.getExpiredAt());
      assertEquals(Instant.ofEpochSecond(0), decoded.getDelayedTo());
    }
  }

  @Nested
  class TransactionOperationValidationTests {

    @Test
    void ack_withAutoAcked_throwsException() {
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("msg-1").isAutoAcked(true).build();

      IllegalStateException ex = assertThrows(IllegalStateException.class, msg::ack);
      assertTrue(ex.getMessage().contains("Auto-acked"));
    }

    @Test
    void reject_withAutoAcked_throwsException() {
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("msg-1").isAutoAcked(true).build();

      IllegalStateException ex = assertThrows(IllegalStateException.class, msg::reject);
      assertTrue(ex.getMessage().contains("Auto-acked"));
    }

    @Test
    void reQueue_withAutoAcked_throwsException() {
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("msg-1").isAutoAcked(true).build();

      IllegalStateException ex =
          assertThrows(IllegalStateException.class, () -> msg.reQueue("other-queue"));
      assertTrue(ex.getMessage().contains("Auto-acked"));
    }

    @Test
    void reQueue_withNullChannel_throwsException() {
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("msg-1").isAutoAcked(false).build();

      IllegalArgumentException ex =
          assertThrows(IllegalArgumentException.class, () -> msg.reQueue(null));
      assertTrue(ex.getMessage().contains("Re-queue channel"));
    }

    @Test
    void reQueue_withEmptyChannel_throwsException() {
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("msg-1").isAutoAcked(false).build();

      IllegalArgumentException ex =
          assertThrows(IllegalArgumentException.class, () -> msg.reQueue(""));
      assertTrue(ex.getMessage().contains("Re-queue channel"));
    }
  }

  @Nested
  class VisibilityTimerTests {

    @Test
    void extendVisibilityTimer_withZeroSeconds_throwsException() {
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("msg-1").visibilitySeconds(30).build();

      IllegalArgumentException ex =
          assertThrows(IllegalArgumentException.class, () -> msg.extendVisibilityTimer(0));
      assertTrue(ex.getMessage().contains("must be greater than 0"));
    }

    @Test
    void extendVisibilityTimer_withNegativeSeconds_throwsException() {
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("msg-1").visibilitySeconds(30).build();

      IllegalArgumentException ex =
          assertThrows(IllegalArgumentException.class, () -> msg.extendVisibilityTimer(-10));
      assertTrue(ex.getMessage().contains("must be greater than 0"));
    }

    @Test
    void extendVisibilityTimer_withNoActiveTimer_throwsException() {
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("msg-1").visibilitySeconds(0).build();

      IllegalStateException ex =
          assertThrows(IllegalStateException.class, () -> msg.extendVisibilityTimer(10));
      assertTrue(ex.getMessage().contains("timer not active"));
    }

    @Test
    void resetVisibilityTimer_withZeroSeconds_throwsException() {
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("msg-1").visibilitySeconds(30).build();

      IllegalArgumentException ex =
          assertThrows(IllegalArgumentException.class, () -> msg.resetVisibilityTimer(0));
      assertTrue(ex.getMessage().contains("must be greater than 0"));
    }

    @Test
    void resetVisibilityTimer_withNoActiveTimer_throwsException() {
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("msg-1").visibilitySeconds(0).build();

      IllegalStateException ex =
          assertThrows(IllegalStateException.class, () -> msg.resetVisibilityTimer(60));
      assertTrue(ex.getMessage().contains("timer not active"));
    }
  }

  @Nested
  class GetterSetterTests {

    @Test
    void setId_updatesId() {
      QueueMessageReceived msg = new QueueMessageReceived();
      msg.setId("new-id");
      assertEquals("new-id", msg.getId());
    }

    @Test
    void setChannel_updatesChannel() {
      QueueMessageReceived msg = new QueueMessageReceived();
      msg.setChannel("new-channel");
      assertEquals("new-channel", msg.getChannel());
    }

    @Test
    void setMetadata_updatesMetadata() {
      QueueMessageReceived msg = new QueueMessageReceived();
      msg.setMetadata("new-metadata");
      assertEquals("new-metadata", msg.getMetadata());
    }

    @Test
    void setBody_updatesBody() {
      QueueMessageReceived msg = new QueueMessageReceived();
      byte[] body = "new-body".getBytes();
      msg.setBody(body);
      assertArrayEquals(body, msg.getBody());
    }

    @Test
    void setSequence_updatesSequence() {
      QueueMessageReceived msg = new QueueMessageReceived();
      msg.setSequence(500L);
      assertEquals(500L, msg.getSequence());
    }

    @Test
    void setReceiveCount_updatesReceiveCount() {
      QueueMessageReceived msg = new QueueMessageReceived();
      msg.setReceiveCount(3);
      assertEquals(3, msg.getReceiveCount());
    }

    @Test
    void setReRouted_updatesReRouted() {
      QueueMessageReceived msg = new QueueMessageReceived();
      msg.setReRouted(true);
      assertTrue(msg.isReRouted());
    }

    @Test
    void setTransactionId_updatesTransactionId() {
      QueueMessageReceived msg = new QueueMessageReceived();
      msg.setTransactionId("txn-new");
      assertEquals("txn-new", msg.getTransactionId());
    }

    @Test
    void setReceiverClientId_updatesReceiverClientId() {
      QueueMessageReceived msg = new QueueMessageReceived();
      msg.setReceiverClientId("new-receiver");
      assertEquals("new-receiver", msg.getReceiverClientId());
    }

    @Test
    void setVisibilitySeconds_updatesVisibilitySeconds() {
      QueueMessageReceived msg = new QueueMessageReceived();
      msg.setVisibilitySeconds(60);
      assertEquals(60, msg.getVisibilitySeconds());
    }
  }

  @Nested
  class MarkTransactionCompletedTests {

    @Test
    void markTransactionCompleted_setsFlags() {
      QueueMessageReceived msg = QueueMessageReceived.builder().id("msg-1").build();

      msg.markTransactionCompleted();

      assertTrue(msg.isTransactionCompleted());
    }
  }

  @Nested
  class NoArgsConstructorTests {

    @Test
    void noArgsConstructor_createsInstanceWithDefaults() {
      QueueMessageReceived msg = new QueueMessageReceived();

      assertNull(msg.getId());
      assertNull(msg.getChannel());
      assertNull(msg.getMetadata());
      assertNull(msg.getBody());
      assertEquals(0, msg.getSequence());
      assertEquals(0, msg.getReceiveCount());
      assertFalse(msg.isReRouted());
      assertFalse(msg.isTransactionCompleted());
      assertFalse(msg.isAutoAcked());
    }
  }

  @Nested
  class TransactionStateTests {

    @Test
    void ack_withTransactionAlreadyCompleted_throwsException() {
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("msg-1").isAutoAcked(false).build();
      msg.markTransactionCompleted();

      IllegalStateException ex = assertThrows(IllegalStateException.class, msg::ack);
      assertTrue(ex.getMessage().contains("already completed"));
    }

    @Test
    void reject_withTransactionAlreadyCompleted_throwsException() {
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("msg-1").isAutoAcked(false).build();
      msg.markTransactionCompleted();

      IllegalStateException ex = assertThrows(IllegalStateException.class, msg::reject);
      assertTrue(ex.getMessage().contains("already completed"));
    }

    @Test
    void reQueue_withTransactionAlreadyCompleted_throwsException() {
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("msg-1").isAutoAcked(false).build();
      msg.markTransactionCompleted();

      IllegalStateException ex =
          assertThrows(IllegalStateException.class, () -> msg.reQueue("other-queue"));
      assertTrue(ex.getMessage().contains("already completed"));
    }

    @Test
    void ack_withoutRequestSender_throwsException() {
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("msg-1").isAutoAcked(false).build();

      IllegalStateException ex = assertThrows(IllegalStateException.class, msg::ack);
      assertTrue(ex.getMessage().contains("Response handler not set"));
    }

    @Test
    void reject_withoutRequestSender_throwsException() {
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("msg-1").isAutoAcked(false).build();

      IllegalStateException ex = assertThrows(IllegalStateException.class, msg::reject);
      assertTrue(ex.getMessage().contains("Response handler not set"));
    }

    @Test
    void reQueue_withoutRequestSender_throwsException() {
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("msg-1").isAutoAcked(false).build();

      IllegalStateException ex =
          assertThrows(IllegalStateException.class, () -> msg.reQueue("other-queue"));
      assertTrue(ex.getMessage().contains("Response handler not set"));
    }
  }

  @Nested
  class ExtendedVisibilityTimerTests {

    @Test
    void extendVisibilityTimer_withTimerExpired_throwsException() {
      // Create message with visibility timer that we can manipulate
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("msg-1").visibilitySeconds(30).build();

      // Manually set the timer expired flag using reflection
      try {
        java.lang.reflect.Field timerExpiredField =
            QueueMessageReceived.class.getDeclaredField("timerExpired");
        timerExpiredField.setAccessible(true);
        timerExpiredField.set(msg, true);

        // Use mock ScheduledFuture since we changed from Timer to ScheduledFuture
        java.lang.reflect.Field timerField =
            QueueMessageReceived.class.getDeclaredField("visibilityFuture");
        timerField.setAccessible(true);
        java.util.concurrent.ScheduledFuture<?> mockFuture =
            org.mockito.Mockito.mock(java.util.concurrent.ScheduledFuture.class);
        timerField.set(msg, mockFuture);
      } catch (Exception e) {
        fail("Could not set up test: " + e.getMessage());
      }

      IllegalStateException ex =
          assertThrows(IllegalStateException.class, () -> msg.extendVisibilityTimer(10));
      assertTrue(ex.getMessage().contains("has expired"));
    }

    @Test
    void extendVisibilityTimer_withMessageCompleted_throwsException() {
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("msg-1").visibilitySeconds(30).build();

      // Set up timer and mark completed
      try {
        // Use mock ScheduledFuture since we changed from Timer to ScheduledFuture
        java.lang.reflect.Field timerField =
            QueueMessageReceived.class.getDeclaredField("visibilityFuture");
        timerField.setAccessible(true);
        java.util.concurrent.ScheduledFuture<?> mockFuture =
            org.mockito.Mockito.mock(java.util.concurrent.ScheduledFuture.class);
        timerField.set(msg, mockFuture);

        java.lang.reflect.Field completedField =
            QueueMessageReceived.class.getDeclaredField("messageCompleted");
        completedField.setAccessible(true);
        completedField.set(msg, true);
      } catch (Exception e) {
        fail("Could not set up test: " + e.getMessage());
      }

      IllegalStateException ex =
          assertThrows(IllegalStateException.class, () -> msg.extendVisibilityTimer(10));
      assertTrue(ex.getMessage().contains("already completed"));
    }

    @Test
    void resetVisibilityTimer_withTimerExpired_throwsException() {
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("msg-1").visibilitySeconds(30).build();

      try {
        java.lang.reflect.Field timerExpiredField =
            QueueMessageReceived.class.getDeclaredField("timerExpired");
        timerExpiredField.setAccessible(true);
        timerExpiredField.set(msg, true);

        // Use mock ScheduledFuture since we changed from Timer to ScheduledFuture
        java.lang.reflect.Field timerField =
            QueueMessageReceived.class.getDeclaredField("visibilityFuture");
        timerField.setAccessible(true);
        java.util.concurrent.ScheduledFuture<?> mockFuture =
            org.mockito.Mockito.mock(java.util.concurrent.ScheduledFuture.class);
        timerField.set(msg, mockFuture);
      } catch (Exception e) {
        fail("Could not set up test: " + e.getMessage());
      }

      IllegalStateException ex =
          assertThrows(IllegalStateException.class, () -> msg.resetVisibilityTimer(60));
      assertTrue(ex.getMessage().contains("has expired"));
    }

    @Test
    void resetVisibilityTimer_withMessageCompleted_throwsException() {
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("msg-1").visibilitySeconds(30).build();

      try {
        // Use mock ScheduledFuture since we changed from Timer to ScheduledFuture
        java.lang.reflect.Field timerField =
            QueueMessageReceived.class.getDeclaredField("visibilityFuture");
        timerField.setAccessible(true);
        java.util.concurrent.ScheduledFuture<?> mockFuture =
            org.mockito.Mockito.mock(java.util.concurrent.ScheduledFuture.class);
        timerField.set(msg, mockFuture);

        java.lang.reflect.Field completedField =
            QueueMessageReceived.class.getDeclaredField("messageCompleted");
        completedField.setAccessible(true);
        completedField.set(msg, true);
      } catch (Exception e) {
        fail("Could not set up test: " + e.getMessage());
      }

      IllegalStateException ex =
          assertThrows(IllegalStateException.class, () -> msg.resetVisibilityTimer(60));
      assertTrue(ex.getMessage().contains("already completed"));
    }

    @Test
    void resetVisibilityTimer_withNegativeSeconds_throwsException() {
      QueueMessageReceived msg =
          QueueMessageReceived.builder().id("msg-1").visibilitySeconds(30).build();

      IllegalArgumentException ex =
          assertThrows(IllegalArgumentException.class, () -> msg.resetVisibilityTimer(-10));
      assertTrue(ex.getMessage().contains("must be greater than 0"));
    }
  }
}
