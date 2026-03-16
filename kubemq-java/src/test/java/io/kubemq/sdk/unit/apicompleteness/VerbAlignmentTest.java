package io.kubemq.sdk.unit.apicompleteness;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.cq.*;
import io.kubemq.sdk.pubsub.*;
import io.kubemq.sdk.queues.*;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for REQ-DX-3: Consistent verb alignment across SDKs. Verifies that verb-aligned alias
 * methods exist and old methods are deprecated.
 */
class VerbAlignmentTest {

  @Nested
  @DisplayName("PubSubClient Verb Alignment")
  class PubSubClientVerbTests {

    @Test
    @DisplayName("DX3-1: publishEvent(EventMessage) alias exists")
    void publishEvent_aliasExists() throws NoSuchMethodException {
      Method method = PubSubClient.class.getMethod("publishEvent", EventMessage.class);
      assertNotNull(method);
      assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("DX3-2: publishEventStore(EventStoreMessage) alias exists")
    void publishEventStore_aliasExists() throws NoSuchMethodException {
      Method method = PubSubClient.class.getMethod("publishEventStore", EventStoreMessage.class);
      assertNotNull(method);
      assertEquals(EventSendResult.class, method.getReturnType());
    }

    @Test
    @DisplayName("DX3-3: sendEventsMessage(EventMessage) is deprecated")
    void sendEventsMessage_isDeprecated() throws NoSuchMethodException {
      Method method = PubSubClient.class.getMethod("sendEventsMessage", EventMessage.class);
      Deprecated annotation = method.getAnnotation(Deprecated.class);
      assertNotNull(annotation, "sendEventsMessage should be @Deprecated");
      assertTrue(annotation.forRemoval(), "forRemoval should be true");
      assertEquals("2.2.0", annotation.since());
    }

    @Test
    @DisplayName("DX3-4: sendEventsStoreMessage(EventStoreMessage) is deprecated")
    void sendEventsStoreMessage_isDeprecated() throws NoSuchMethodException {
      Method method =
          PubSubClient.class.getMethod("sendEventsStoreMessage", EventStoreMessage.class);
      Deprecated annotation = method.getAnnotation(Deprecated.class);
      assertNotNull(annotation, "sendEventsStoreMessage should be @Deprecated");
      assertTrue(annotation.forRemoval(), "forRemoval should be true");
      assertEquals("2.2.0", annotation.since());
    }

    @Test
    @DisplayName("DX3-5: publishEvent(EventMessage) alias is NOT deprecated")
    void publishEvent_aliasNotDeprecated() throws NoSuchMethodException {
      Method method = PubSubClient.class.getMethod("publishEvent", EventMessage.class);
      assertNull(
          method.getAnnotation(Deprecated.class),
          "publishEvent(EventMessage) alias should NOT be deprecated");
    }

    @Test
    @DisplayName("DX3-6: publishEventStore(EventStoreMessage) alias is NOT deprecated")
    void publishEventStore_aliasNotDeprecated() throws NoSuchMethodException {
      Method method = PubSubClient.class.getMethod("publishEventStore", EventStoreMessage.class);
      assertNull(
          method.getAnnotation(Deprecated.class),
          "publishEventStore(EventStoreMessage) alias should NOT be deprecated");
    }

    @Test
    @DisplayName("DX3-7: convenience publishEvent(String, byte[]) still exists")
    void publishEvent_convenienceExists() throws NoSuchMethodException {
      Method method = PubSubClient.class.getMethod("publishEvent", String.class, byte[].class);
      assertNotNull(method);
      assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("DX3-8: convenience publishEvent(String, String) still exists")
    void publishEvent_stringConvenienceExists() throws NoSuchMethodException {
      Method method = PubSubClient.class.getMethod("publishEvent", String.class, String.class);
      assertNotNull(method);
      assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("DX3-9: convenience publishEventStore(String, byte[]) still exists")
    void publishEventStore_convenienceExists() throws NoSuchMethodException {
      Method method = PubSubClient.class.getMethod("publishEventStore", String.class, byte[].class);
      assertNotNull(method);
      assertEquals(EventSendResult.class, method.getReturnType());
    }

    @Test
    @DisplayName("DX3-10: subscribeToEvents is already aligned (not deprecated)")
    void subscribeToEvents_notDeprecated() throws NoSuchMethodException {
      Method method = PubSubClient.class.getMethod("subscribeToEvents", EventsSubscription.class);
      assertNull(
          method.getAnnotation(Deprecated.class),
          "subscribeToEvents is already verb-aligned and should NOT be deprecated");
    }

    @Test
    @DisplayName("DX3-11: subscribeToEventsStore is already aligned (not deprecated)")
    void subscribeToEventsStore_notDeprecated() throws NoSuchMethodException {
      Method method =
          PubSubClient.class.getMethod("subscribeToEventsStore", EventsStoreSubscription.class);
      assertNull(
          method.getAnnotation(Deprecated.class),
          "subscribeToEventsStore is already verb-aligned and should NOT be deprecated");
    }
  }

  @Nested
  @DisplayName("QueuesClient Verb Alignment")
  class QueuesClientVerbTests {

    @Test
    @DisplayName("DX3-12: sendQueueMessage(QueueMessage) alias exists")
    void sendQueueMessage_aliasExists() throws NoSuchMethodException {
      Method method = QueuesClient.class.getMethod("sendQueueMessage", QueueMessage.class);
      assertNotNull(method);
      assertEquals(QueueSendResult.class, method.getReturnType());
    }

    @Test
    @DisplayName("DX3-13: receiveQueueMessages(QueuesPollRequest) alias exists")
    void receiveQueueMessages_aliasExists() throws NoSuchMethodException {
      Method method = QueuesClient.class.getMethod("receiveQueueMessages", QueuesPollRequest.class);
      assertNotNull(method);
      assertEquals(QueuesPollResponse.class, method.getReturnType());
    }

    @Test
    @DisplayName("DX3-14: sendQueuesMessage(QueueMessage) is deprecated")
    void sendQueuesMessage_isDeprecated() throws NoSuchMethodException {
      Method method = QueuesClient.class.getMethod("sendQueuesMessage", QueueMessage.class);
      Deprecated annotation = method.getAnnotation(Deprecated.class);
      assertNotNull(annotation, "sendQueuesMessage should be @Deprecated");
      assertTrue(annotation.forRemoval(), "forRemoval should be true");
      assertEquals("2.2.0", annotation.since());
    }

    @Test
    @DisplayName("DX3-15: receiveQueuesMessages(QueuesPollRequest) is deprecated")
    void receiveQueuesMessages_isDeprecated() throws NoSuchMethodException {
      Method method =
          QueuesClient.class.getMethod("receiveQueuesMessages", QueuesPollRequest.class);
      Deprecated annotation = method.getAnnotation(Deprecated.class);
      assertNotNull(annotation, "receiveQueuesMessages should be @Deprecated");
      assertTrue(annotation.forRemoval(), "forRemoval should be true");
      assertEquals("2.2.0", annotation.since());
    }

    @Test
    @DisplayName("DX3-16: sendQueueMessage(QueueMessage) alias is NOT deprecated")
    void sendQueueMessage_aliasNotDeprecated() throws NoSuchMethodException {
      Method method = QueuesClient.class.getMethod("sendQueueMessage", QueueMessage.class);
      assertNull(
          method.getAnnotation(Deprecated.class),
          "sendQueueMessage(QueueMessage) alias should NOT be deprecated");
    }

    @Test
    @DisplayName("DX3-17: receiveQueueMessages(QueuesPollRequest) alias is NOT deprecated")
    void receiveQueueMessages_aliasNotDeprecated() throws NoSuchMethodException {
      Method method = QueuesClient.class.getMethod("receiveQueueMessages", QueuesPollRequest.class);
      assertNull(
          method.getAnnotation(Deprecated.class),
          "receiveQueueMessages(QueuesPollRequest) alias should NOT be deprecated");
    }

    @Test
    @DisplayName("DX3-18: convenience sendQueueMessage(String, byte[]) still exists")
    void sendQueueMessage_convenienceExists() throws NoSuchMethodException {
      Method method = QueuesClient.class.getMethod("sendQueueMessage", String.class, byte[].class);
      assertNotNull(method);
      assertEquals(QueueSendResult.class, method.getReturnType());
    }
  }

  @Nested
  @DisplayName("CQClient Verb Alignment")
  class CQClientVerbTests {

    @Test
    @DisplayName("DX3-19: sendCommand(CommandMessage) alias exists")
    void sendCommand_aliasExists() throws NoSuchMethodException {
      Method method = CQClient.class.getMethod("sendCommand", CommandMessage.class);
      assertNotNull(method);
      assertEquals(CommandResponseMessage.class, method.getReturnType());
    }

    @Test
    @DisplayName("DX3-20: sendQuery(QueryMessage) alias exists")
    void sendQuery_aliasExists() throws NoSuchMethodException {
      Method method = CQClient.class.getMethod("sendQuery", QueryMessage.class);
      assertNotNull(method);
      assertEquals(QueryResponseMessage.class, method.getReturnType());
    }

    @Test
    @DisplayName("DX3-21: sendCommandRequest(CommandMessage) is deprecated")
    void sendCommandRequest_isDeprecated() throws NoSuchMethodException {
      Method method = CQClient.class.getMethod("sendCommandRequest", CommandMessage.class);
      Deprecated annotation = method.getAnnotation(Deprecated.class);
      assertNotNull(annotation, "sendCommandRequest should be @Deprecated");
      assertTrue(annotation.forRemoval(), "forRemoval should be true");
      assertEquals("2.2.0", annotation.since());
    }

    @Test
    @DisplayName("DX3-22: sendQueryRequest(QueryMessage) is deprecated")
    void sendQueryRequest_isDeprecated() throws NoSuchMethodException {
      Method method = CQClient.class.getMethod("sendQueryRequest", QueryMessage.class);
      Deprecated annotation = method.getAnnotation(Deprecated.class);
      assertNotNull(annotation, "sendQueryRequest should be @Deprecated");
      assertTrue(annotation.forRemoval(), "forRemoval should be true");
      assertEquals("2.2.0", annotation.since());
    }

    @Test
    @DisplayName("DX3-23: sendCommand(CommandMessage) alias is NOT deprecated")
    void sendCommand_aliasNotDeprecated() throws NoSuchMethodException {
      Method method = CQClient.class.getMethod("sendCommand", CommandMessage.class);
      assertNull(
          method.getAnnotation(Deprecated.class),
          "sendCommand(CommandMessage) alias should NOT be deprecated");
    }

    @Test
    @DisplayName("DX3-24: sendQuery(QueryMessage) alias is NOT deprecated")
    void sendQuery_aliasNotDeprecated() throws NoSuchMethodException {
      Method method = CQClient.class.getMethod("sendQuery", QueryMessage.class);
      assertNull(
          method.getAnnotation(Deprecated.class),
          "sendQuery(QueryMessage) alias should NOT be deprecated");
    }

    @Test
    @DisplayName("DX3-25: convenience sendCommand(String, byte[], int) still exists")
    void sendCommand_convenienceExists() throws NoSuchMethodException {
      Method method =
          CQClient.class.getMethod("sendCommand", String.class, byte[].class, int.class);
      assertNotNull(method);
      assertEquals(CommandResponseMessage.class, method.getReturnType());
    }

    @Test
    @DisplayName("DX3-26: convenience sendQuery(String, byte[], int) still exists")
    void sendQuery_convenienceExists() throws NoSuchMethodException {
      Method method = CQClient.class.getMethod("sendQuery", String.class, byte[].class, int.class);
      assertNotNull(method);
      assertEquals(QueryResponseMessage.class, method.getReturnType());
    }

    @Test
    @DisplayName("DX3-27: subscribeToCommands is already aligned (not deprecated)")
    void subscribeToCommands_notDeprecated() throws NoSuchMethodException {
      Method method = CQClient.class.getMethod("subscribeToCommands", CommandsSubscription.class);
      assertNull(
          method.getAnnotation(Deprecated.class),
          "subscribeToCommands is already verb-aligned and should NOT be deprecated");
    }

    @Test
    @DisplayName("DX3-28: subscribeToQueries is already aligned (not deprecated)")
    void subscribeToQueries_notDeprecated() throws NoSuchMethodException {
      Method method = CQClient.class.getMethod("subscribeToQueries", QueriesSubscription.class);
      assertNull(
          method.getAnnotation(Deprecated.class),
          "subscribeToQueries is already verb-aligned and should NOT be deprecated");
    }
  }

  @Nested
  @DisplayName("Complete Verb Mapping Coverage")
  class CompleteMappingTests {

    @Test
    @DisplayName("DX3-29: all 6 deprecated methods exist and are deprecated")
    void allDeprecatedMethodsExist() {
      assertDoesNotThrow(
          () -> {
            assertDeprecated(PubSubClient.class, "sendEventsMessage", EventMessage.class);
            assertDeprecated(PubSubClient.class, "sendEventsStoreMessage", EventStoreMessage.class);
            assertDeprecated(QueuesClient.class, "sendQueuesMessage", QueueMessage.class);
            assertDeprecated(QueuesClient.class, "receiveQueuesMessages", QueuesPollRequest.class);
            assertDeprecated(CQClient.class, "sendCommandRequest", CommandMessage.class);
            assertDeprecated(CQClient.class, "sendQueryRequest", QueryMessage.class);
          });
    }

    @Test
    @DisplayName("DX3-30: all 6 verb-aligned alias methods exist and are NOT deprecated")
    void allAliasMethodsExist() {
      assertDoesNotThrow(
          () -> {
            assertNotDeprecated(PubSubClient.class, "publishEvent", EventMessage.class);
            assertNotDeprecated(PubSubClient.class, "publishEventStore", EventStoreMessage.class);
            assertNotDeprecated(QueuesClient.class, "sendQueueMessage", QueueMessage.class);
            assertNotDeprecated(
                QueuesClient.class, "receiveQueueMessages", QueuesPollRequest.class);
            assertNotDeprecated(CQClient.class, "sendCommand", CommandMessage.class);
            assertNotDeprecated(CQClient.class, "sendQuery", QueryMessage.class);
          });
    }

    private void assertDeprecated(Class<?> clazz, String methodName, Class<?>... paramTypes)
        throws NoSuchMethodException {
      Method method = clazz.getMethod(methodName, paramTypes);
      Deprecated annotation = method.getAnnotation(Deprecated.class);
      assertNotNull(
          annotation, clazz.getSimpleName() + "." + methodName + " should be @Deprecated");
      assertTrue(
          annotation.forRemoval(),
          clazz.getSimpleName() + "." + methodName + " should have forRemoval=true");
    }

    private void assertNotDeprecated(Class<?> clazz, String methodName, Class<?>... paramTypes)
        throws NoSuchMethodException {
      Method method = clazz.getMethod(methodName, paramTypes);
      assertNull(
          method.getAnnotation(Deprecated.class),
          clazz.getSimpleName() + "." + methodName + " should NOT be @Deprecated");
    }
  }
}
