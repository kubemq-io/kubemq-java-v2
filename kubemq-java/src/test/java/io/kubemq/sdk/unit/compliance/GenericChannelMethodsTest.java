package io.kubemq.sdk.unit.compliance;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.exception.ValidationException;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.QueuesClient;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for generic channel management methods (JV-02): createChannel, deleteChannel,
 * listChannels on all 3 client classes.
 */
@ExtendWith(MockitoExtension.class)
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class GenericChannelMethodsTest {

  private PubSubClient pubSubClient;
  private CQClient cqClient;
  private QueuesClient queuesClient;

  @BeforeEach
  void setup() {
    pubSubClient =
        PubSubClient.builder().address("localhost:50000").clientId("test-pubsub-generic").build();
    cqClient = CQClient.builder().address("localhost:50000").clientId("test-cq-generic").build();
    queuesClient =
        QueuesClient.builder().address("localhost:50000").clientId("test-queues-generic").build();
  }

  @AfterEach
  void teardown() {
    if (pubSubClient != null) pubSubClient.close();
    if (cqClient != null) cqClient.close();
    if (queuesClient != null) queuesClient.close();
  }

  @Nested
  @DisplayName("PubSubClient generic channel methods")
  class PubSubClientGenericTests {

    @Test
    @DisplayName("createChannel rejects null type")
    void createChannel_nullType_throwsValidationException() {
      assertThrows(ValidationException.class, () -> pubSubClient.createChannel("test-ch", null));
    }

    @Test
    @DisplayName("createChannel rejects invalid type")
    void createChannel_invalidType_throwsValidationException() {
      assertThrows(
          ValidationException.class, () -> pubSubClient.createChannel("test-ch", "invalid"));
    }

    @Test
    @DisplayName("deleteChannel rejects null type")
    void deleteChannel_nullType_throwsValidationException() {
      assertThrows(ValidationException.class, () -> pubSubClient.deleteChannel("test-ch", null));
    }

    @Test
    @DisplayName("deleteChannel rejects invalid type")
    void deleteChannel_invalidType_throwsValidationException() {
      assertThrows(ValidationException.class, () -> pubSubClient.deleteChannel("test-ch", "bad"));
    }

    @Test
    @DisplayName("listChannels rejects null type")
    void listChannels_nullType_throwsValidationException() {
      assertThrows(ValidationException.class, () -> pubSubClient.listChannels(null, ""));
    }

    @Test
    @DisplayName("listChannels rejects invalid type")
    void listChannels_invalidType_throwsValidationException() {
      assertThrows(ValidationException.class, () -> pubSubClient.listChannels("unknown", ""));
    }

    @Test
    @DisplayName("createChannel rejects after close")
    void createChannel_closedClient_throwsException() {
      pubSubClient.close();
      assertThrows(Exception.class, () -> pubSubClient.createChannel("ch", "events"));
      pubSubClient = null;
    }
  }

  @Nested
  @DisplayName("CQClient generic channel methods")
  class CQClientGenericTests {

    @Test
    @DisplayName("createChannel rejects null type")
    void createChannel_nullType_throwsValidationException() {
      assertThrows(ValidationException.class, () -> cqClient.createChannel("test-ch", null));
    }

    @Test
    @DisplayName("createChannel rejects invalid type")
    void createChannel_invalidType_throwsValidationException() {
      assertThrows(ValidationException.class, () -> cqClient.createChannel("test-ch", "wrong"));
    }

    @Test
    @DisplayName("deleteChannel rejects null type")
    void deleteChannel_nullType_throwsValidationException() {
      assertThrows(ValidationException.class, () -> cqClient.deleteChannel("test-ch", null));
    }

    @Test
    @DisplayName("listChannels rejects invalid type")
    void listChannels_invalidType_throwsValidationException() {
      assertThrows(ValidationException.class, () -> cqClient.listChannels("x", ""));
    }

    @Test
    @DisplayName("createChannel rejects after close")
    void createChannel_closedClient_throwsException() {
      cqClient.close();
      assertThrows(Exception.class, () -> cqClient.createChannel("ch", "commands"));
      cqClient = null;
    }
  }

  @Nested
  @DisplayName("QueuesClient generic channel methods")
  class QueuesClientGenericTests {

    @Test
    @DisplayName("createChannel rejects null type")
    void createChannel_nullType_throwsValidationException() {
      assertThrows(ValidationException.class, () -> queuesClient.createChannel("test-ch", null));
    }

    @Test
    @DisplayName("createChannel rejects invalid type")
    void createChannel_invalidType_throwsValidationException() {
      assertThrows(ValidationException.class, () -> queuesClient.createChannel("test-ch", "nope"));
    }

    @Test
    @DisplayName("deleteChannel rejects invalid type")
    void deleteChannel_invalidType_throwsValidationException() {
      assertThrows(ValidationException.class, () -> queuesClient.deleteChannel("test-ch", "fake"));
    }

    @Test
    @DisplayName("listChannels rejects null type")
    void listChannels_nullType_throwsValidationException() {
      assertThrows(ValidationException.class, () -> queuesClient.listChannels(null, ""));
    }

    @Test
    @DisplayName("createChannel rejects after close")
    void createChannel_closedClient_throwsException() {
      queuesClient.close();
      assertThrows(Exception.class, () -> queuesClient.createChannel("ch", "queues"));
      queuesClient = null;
    }
  }
}
