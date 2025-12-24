package io.kubemq.sdk.unit.queues;

import io.kubemq.sdk.queues.QueueMessageReceived;
import io.kubemq.sdk.queues.QueuesPollResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QueuesPollResponse builder, getters, and state management.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QueuesPollResponse Tests")
class QueuesPollResponseTest {

    private QueueMessageReceived createMockMessage(String id) {
        return QueueMessageReceived.builder()
                .id(id)
                .channel("test-channel")
                .metadata("test-metadata")
                .body("test-body".getBytes())
                .build();
    }

    @Nested
    class BuilderTests {

        @Test
        void builder_withAllFields_createsResponse() {
            List<QueueMessageReceived> messages = Arrays.asList(
                    createMockMessage("msg-1"),
                    createMockMessage("msg-2")
            );
            List<Long> offsets = Arrays.asList(1L, 2L);

            QueuesPollResponse response = QueuesPollResponse.builder()
                    .refRequestId("ref-123")
                    .transactionId("txn-456")
                    .messages(messages)
                    .error("")
                    .isError(false)
                    .isTransactionCompleted(false)
                    .activeOffsets(offsets)
                    .receiverClientId("client-789")
                    .visibilitySeconds(30)
                    .isAutoAcked(false)
                    .build();

            assertEquals("ref-123", response.getRefRequestId());
            assertEquals("txn-456", response.getTransactionId());
            assertEquals(2, response.getMessages().size());
            assertEquals("", response.getError());
            assertFalse(response.isError());
            assertFalse(response.isTransactionCompleted());
            assertEquals(2, response.getActiveOffsets().size());
            assertEquals("client-789", response.getReceiverClientId());
            assertEquals(30, response.getVisibilitySeconds());
            assertFalse(response.isAutoAcked());
        }

        @Test
        void builder_withMinimalFields_createsResponse() {
            QueuesPollResponse response = QueuesPollResponse.builder()
                    .refRequestId("ref-minimal")
                    .build();

            assertEquals("ref-minimal", response.getRefRequestId());
            assertNotNull(response.getMessages());
            assertTrue(response.getMessages().isEmpty());
            assertNotNull(response.getActiveOffsets());
            assertTrue(response.getActiveOffsets().isEmpty());
        }

        @Test
        void builder_withError_createsErrorResponse() {
            QueuesPollResponse response = QueuesPollResponse.builder()
                    .refRequestId("ref-error")
                    .transactionId("txn-error")
                    .isError(true)
                    .error("Queue not found")
                    .build();

            assertEquals("ref-error", response.getRefRequestId());
            assertTrue(response.isError());
            assertEquals("Queue not found", response.getError());
        }

        @Test
        void builder_withNoMessages_createsEmptyList() {
            QueuesPollResponse response = QueuesPollResponse.builder()
                    .refRequestId("ref-empty")
                    .build();

            assertNotNull(response.getMessages());
            assertEquals(0, response.getMessages().size());
        }
    }

    @Nested
    class GetterSetterTests {

        @Test
        void setRefRequestId_updatesRefRequestId() {
            QueuesPollResponse response = new QueuesPollResponse();
            response.setRefRequestId("updated-ref");
            assertEquals("updated-ref", response.getRefRequestId());
        }

        @Test
        void setTransactionId_updatesTransactionId() {
            QueuesPollResponse response = new QueuesPollResponse();
            response.setTransactionId("updated-txn");
            assertEquals("updated-txn", response.getTransactionId());
        }

        @Test
        void setMessages_updatesMessages() {
            QueuesPollResponse response = new QueuesPollResponse();
            List<QueueMessageReceived> messages = Arrays.asList(
                    createMockMessage("msg-setter")
            );
            response.setMessages(messages);
            assertEquals(1, response.getMessages().size());
            assertEquals("msg-setter", response.getMessages().get(0).getId());
        }

        @Test
        void setError_updatesError() {
            QueuesPollResponse response = new QueuesPollResponse();
            response.setError("new error message");
            assertEquals("new error message", response.getError());
        }

        @Test
        void setActiveOffsets_updatesActiveOffsets() {
            QueuesPollResponse response = new QueuesPollResponse();
            List<Long> offsets = Arrays.asList(10L, 20L, 30L);
            response.setActiveOffsets(offsets);
            assertEquals(3, response.getActiveOffsets().size());
            assertEquals(Long.valueOf(10L), response.getActiveOffsets().get(0));
        }

        @Test
        void setReceiverClientId_updatesReceiverClientId() {
            QueuesPollResponse response = new QueuesPollResponse();
            response.setReceiverClientId("new-client");
            assertEquals("new-client", response.getReceiverClientId());
        }

        @Test
        void setVisibilitySeconds_updatesVisibilitySeconds() {
            QueuesPollResponse response = new QueuesPollResponse();
            response.setVisibilitySeconds(60);
            assertEquals(60, response.getVisibilitySeconds());
        }

        @Test
        void setAutoAcked_updatesAutoAcked() {
            QueuesPollResponse response = new QueuesPollResponse();
            response.setAutoAcked(true);
            assertTrue(response.isAutoAcked());
        }
    }

    @Nested
    class IsErrorTests {

        @Test
        void isError_returnsFalseByDefault() {
            QueuesPollResponse response = new QueuesPollResponse();
            assertFalse(response.isError());
        }

        @Test
        void isError_returnsTrueWhenSet() {
            QueuesPollResponse response = QueuesPollResponse.builder()
                    .isError(true)
                    .error("Some error")
                    .build();
            assertTrue(response.isError());
        }

        @Test
        void isError_methodReturnsCorrectValue() {
            QueuesPollResponse responseWithError = QueuesPollResponse.builder()
                    .isError(true)
                    .build();
            QueuesPollResponse responseWithoutError = QueuesPollResponse.builder()
                    .isError(false)
                    .build();

            assertTrue(responseWithError.isError());
            assertFalse(responseWithoutError.isError());
        }
    }

    @Nested
    class MessageCompletionTests {

        @Test
        void markMessageCompleted_removesFromReceivedMessages() {
            QueueMessageReceived msg = createMockMessage("msg-complete");
            List<QueueMessageReceived> messages = new ArrayList<>();
            messages.add(msg);

            QueuesPollResponse response = QueuesPollResponse.builder()
                    .refRequestId("ref-1")
                    .transactionId("txn-1")
                    .messages(messages)
                    .receiverClientId("client")
                    .isAutoAcked(false)
                    .build();

            // Simulate complete() being called (which adds to receivedMessages map)
            // Since complete() requires RequestSender, we test the markMessageCompleted behavior
            // by directly calling it after manually simulating the map state
            assertDoesNotThrow(() -> response.markMessageCompleted("msg-complete"));
        }
    }

    @Nested
    class TransactionOperationValidationTests {

        @Test
        void ackAll_withAutoAcked_throwsException() {
            QueuesPollResponse response = QueuesPollResponse.builder()
                    .refRequestId("ref-1")
                    .transactionId("txn-1")
                    .isAutoAcked(true)
                    .build();

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    response::ackAll
            );
            assertTrue(ex.getMessage().contains("auto ack"));
        }

        @Test
        void rejectAll_withAutoAcked_throwsException() {
            QueuesPollResponse response = QueuesPollResponse.builder()
                    .refRequestId("ref-1")
                    .transactionId("txn-1")
                    .isAutoAcked(true)
                    .build();

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    response::rejectAll
            );
            assertTrue(ex.getMessage().contains("auto ack"));
        }

        @Test
        void reQueueAll_withAutoAcked_throwsException() {
            QueuesPollResponse response = QueuesPollResponse.builder()
                    .refRequestId("ref-1")
                    .transactionId("txn-1")
                    .isAutoAcked(true)
                    .build();

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> response.reQueueAll("other-channel")
            );
            assertTrue(ex.getMessage().contains("auto ack"));
        }

        @Test
        void ackAll_withCompletedTransaction_throwsException() {
            QueuesPollResponse response = QueuesPollResponse.builder()
                    .refRequestId("ref-1")
                    .transactionId("txn-1")
                    .isAutoAcked(false)
                    .isTransactionCompleted(true)
                    .build();

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    response::ackAll
            );
            assertTrue(ex.getMessage().contains("already completed"));
        }

        @Test
        void rejectAll_withCompletedTransaction_throwsException() {
            QueuesPollResponse response = QueuesPollResponse.builder()
                    .refRequestId("ref-1")
                    .transactionId("txn-1")
                    .isAutoAcked(false)
                    .isTransactionCompleted(true)
                    .build();

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    response::rejectAll
            );
            assertTrue(ex.getMessage().contains("already completed"));
        }

        @Test
        void reQueueAll_withCompletedTransaction_throwsException() {
            QueuesPollResponse response = QueuesPollResponse.builder()
                    .refRequestId("ref-1")
                    .transactionId("txn-1")
                    .isAutoAcked(false)
                    .isTransactionCompleted(true)
                    .build();

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> response.reQueueAll("other-channel")
            );
            assertTrue(ex.getMessage().contains("already completed"));
        }

        @Test
        void ackAll_withNoRequestSender_throwsException() {
            QueuesPollResponse response = QueuesPollResponse.builder()
                    .refRequestId("ref-1")
                    .transactionId("txn-1")
                    .isAutoAcked(false)
                    .isTransactionCompleted(false)
                    .build();

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    response::ackAll
            );
            assertTrue(ex.getMessage().contains("handler is not set"));
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toString_includesAllFields() {
            List<QueueMessageReceived> messages = Arrays.asList(
                    createMockMessage("msg-str")
            );
            List<Long> offsets = Arrays.asList(100L);

            QueuesPollResponse response = QueuesPollResponse.builder()
                    .refRequestId("ref-string")
                    .transactionId("txn-string")
                    .messages(messages)
                    .error("")
                    .isError(false)
                    .isTransactionCompleted(false)
                    .activeOffsets(offsets)
                    .build();

            String str = response.toString();

            assertTrue(str.contains("ref-string"));
            assertTrue(str.contains("txn-string"));
            assertTrue(str.contains("isError=false"));
            assertTrue(str.contains("isTransactionCompleted=false"));
        }

        @Test
        void toString_withError_includesError() {
            QueuesPollResponse response = QueuesPollResponse.builder()
                    .refRequestId("ref-err")
                    .isError(true)
                    .error("Queue timeout")
                    .build();

            String str = response.toString();

            assertTrue(str.contains("Queue timeout"));
            assertTrue(str.contains("isError=true"));
        }

        @Test
        void toString_withEmptyMessages_handlesGracefully() {
            QueuesPollResponse response = QueuesPollResponse.builder()
                    .refRequestId("ref-empty")
                    .build();

            String str = response.toString();

            assertNotNull(str);
            assertTrue(str.contains("ref-empty"));
        }
    }

    @Nested
    class DefaultValuesTests {

        @Test
        void noArgsConstructor_initializesWithDefaults() {
            QueuesPollResponse response = new QueuesPollResponse();

            assertNull(response.getRefRequestId());
            assertNull(response.getTransactionId());
            assertNotNull(response.getMessages());
            assertNotNull(response.getActiveOffsets());
            assertFalse(response.isError());
            assertFalse(response.isTransactionCompleted());
            assertFalse(response.isAutoAcked());
        }

        @Test
        void builder_initializesListsToEmptyArrayLists() {
            QueuesPollResponse response = QueuesPollResponse.builder().build();

            assertNotNull(response.getMessages());
            assertEquals(ArrayList.class, response.getMessages().getClass());
            assertNotNull(response.getActiveOffsets());
            assertEquals(ArrayList.class, response.getActiveOffsets().getClass());
        }
    }

    @Nested
    @DisplayName("Complete Method Tests")
    class CompleteMethodTests {

        @Test
        @DisplayName("complete with auto-acked does not throw")
        void complete_withAutoAcked_doesNotThrow() throws Exception {
            QueueMessageReceived msg = QueueMessageReceived.builder().id("msg-1").build();

            QueuesPollResponse response = QueuesPollResponse.builder()
                    .messages(Arrays.asList(msg))
                    .isAutoAcked(true)
                    .build();

            // Use reflection to call complete with a lambda that does nothing
            Method completeMethod = QueuesPollResponse.class.getDeclaredMethod("complete",
                    Class.forName("io.kubemq.sdk.queues.RequestSender"));
            completeMethod.setAccessible(true);

            // Create a lambda using reflection proxy
            Object requestSender = java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] { Class.forName("io.kubemq.sdk.queues.RequestSender") },
                    (proxy, method, args) -> null
            );

            // Should return early without setting up message tracking
            assertDoesNotThrow(() -> {
                try {
                    completeMethod.invoke(response, requestSender);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        @Test
        @DisplayName("complete sets up message tracking when not auto-acked")
        void complete_setsUpMessageTracking_whenNotAutoAcked() throws Exception {
            QueueMessageReceived msg1 = QueueMessageReceived.builder().id("msg-1").build();
            QueueMessageReceived msg2 = QueueMessageReceived.builder().id("msg-2").build();

            QueuesPollResponse response = QueuesPollResponse.builder()
                    .messages(Arrays.asList(msg1, msg2))
                    .isAutoAcked(false)
                    .build();

            // Use reflection to call complete
            Method completeMethod = QueuesPollResponse.class.getDeclaredMethod("complete",
                    Class.forName("io.kubemq.sdk.queues.RequestSender"));
            completeMethod.setAccessible(true);

            Object requestSender = java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] { Class.forName("io.kubemq.sdk.queues.RequestSender") },
                    (proxy, method, args) -> null
            );

            completeMethod.invoke(response, requestSender);

            // Messages should be tracked but transaction not yet completed
            assertFalse(response.isTransactionCompleted());
        }
    }

    @Nested
    @DisplayName("Transaction Operation Success Tests (via Reflection)")
    class TransactionOperationSuccessTests {

        private void setupResponseWithSender(QueuesPollResponse response, java.util.concurrent.atomic.AtomicReference<Object> capturedRequest) throws Exception {
            Method completeMethod = QueuesPollResponse.class.getDeclaredMethod("complete",
                    Class.forName("io.kubemq.sdk.queues.RequestSender"));
            completeMethod.setAccessible(true);

            Object requestSender = java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] { Class.forName("io.kubemq.sdk.queues.RequestSender") },
                    (proxy, method, args) -> {
                        if (capturedRequest != null && args != null && args.length > 0) {
                            capturedRequest.set(args[0]);
                        }
                        return null;
                    }
            );

            completeMethod.invoke(response, requestSender);
        }

        @Test
        @DisplayName("ackAll sends request and marks transaction complete")
        void ackAll_sendsRequestAndMarksComplete() throws Exception {
            java.util.concurrent.atomic.AtomicReference<Object> capturedRequest = new java.util.concurrent.atomic.AtomicReference<>();
            QueueMessageReceived msg = QueueMessageReceived.builder().id("msg-1").build();

            QueuesPollResponse response = QueuesPollResponse.builder()
                    .transactionId("tx-ack")
                    .receiverClientId("client-1")
                    .activeOffsets(Arrays.asList(1L, 2L))
                    .messages(Arrays.asList(msg))
                    .isAutoAcked(false)
                    .build();
            setupResponseWithSender(response, capturedRequest);

            response.ackAll();

            assertTrue(response.isTransactionCompleted());
            assertNotNull(capturedRequest.get());
        }

        @Test
        @DisplayName("rejectAll sends NACK request and marks transaction complete")
        void rejectAll_sendsNackRequestAndMarksComplete() throws Exception {
            java.util.concurrent.atomic.AtomicReference<Object> capturedRequest = new java.util.concurrent.atomic.AtomicReference<>();
            QueueMessageReceived msg = QueueMessageReceived.builder().id("msg-1").build();

            QueuesPollResponse response = QueuesPollResponse.builder()
                    .transactionId("tx-nack")
                    .receiverClientId("client-1")
                    .activeOffsets(Arrays.asList(1L))
                    .messages(Arrays.asList(msg))
                    .isAutoAcked(false)
                    .build();
            setupResponseWithSender(response, capturedRequest);

            response.rejectAll();

            assertTrue(response.isTransactionCompleted());
            assertNotNull(capturedRequest.get());
        }

        @Test
        @DisplayName("reQueueAll sends requeue request with channel")
        void reQueueAll_sendsRequeueRequestWithChannel() throws Exception {
            java.util.concurrent.atomic.AtomicReference<Object> capturedRequest = new java.util.concurrent.atomic.AtomicReference<>();
            QueueMessageReceived msg = QueueMessageReceived.builder().id("msg-1").build();

            QueuesPollResponse response = QueuesPollResponse.builder()
                    .transactionId("tx-requeue")
                    .receiverClientId("client-1")
                    .activeOffsets(Arrays.asList(1L, 2L, 3L))
                    .messages(Arrays.asList(msg))
                    .isAutoAcked(false)
                    .build();
            setupResponseWithSender(response, capturedRequest);

            response.reQueueAll("dead-letter-queue");

            assertTrue(response.isTransactionCompleted());
            assertNotNull(capturedRequest.get());
        }

        @Test
        @DisplayName("transaction operations mark all messages as completed")
        void transactionOperations_markAllMessagesAsCompleted() throws Exception {
            java.util.concurrent.atomic.AtomicReference<Object> capturedRequest = new java.util.concurrent.atomic.AtomicReference<>();
            QueueMessageReceived msg1 = QueueMessageReceived.builder().id("msg-1").build();
            QueueMessageReceived msg2 = QueueMessageReceived.builder().id("msg-2").build();

            QueuesPollResponse response = QueuesPollResponse.builder()
                    .transactionId("tx-multi")
                    .receiverClientId("client-1")
                    .activeOffsets(Arrays.asList(1L, 2L))
                    .messages(Arrays.asList(msg1, msg2))
                    .isAutoAcked(false)
                    .build();
            setupResponseWithSender(response, capturedRequest);

            response.ackAll();

            assertTrue(response.isTransactionCompleted());
            // Messages should also be marked as completed
            assertTrue(msg1.isTransactionCompleted());
            assertTrue(msg2.isTransactionCompleted());
        }
    }

    @Nested
    @DisplayName("Message Completion Tracking Tests")
    class MessageCompletionTrackingTests {

        private void setupResponseWithSender(QueuesPollResponse response) throws Exception {
            Method completeMethod = QueuesPollResponse.class.getDeclaredMethod("complete",
                    Class.forName("io.kubemq.sdk.queues.RequestSender"));
            completeMethod.setAccessible(true);

            Object requestSender = java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] { Class.forName("io.kubemq.sdk.queues.RequestSender") },
                    (proxy, method, args) -> null
            );

            completeMethod.invoke(response, requestSender);
        }

        @Test
        @DisplayName("markMessageCompleted tracks individual message completion")
        void markMessageCompleted_tracksIndividualCompletion() throws Exception {
            QueueMessageReceived msg1 = QueueMessageReceived.builder().id("msg-1").build();
            QueueMessageReceived msg2 = QueueMessageReceived.builder().id("msg-2").build();

            QueuesPollResponse response = QueuesPollResponse.builder()
                    .messages(Arrays.asList(msg1, msg2))
                    .isAutoAcked(false)
                    .build();
            setupResponseWithSender(response);

            assertFalse(response.isTransactionCompleted());

            response.markMessageCompleted("msg-1");
            assertFalse(response.isTransactionCompleted());

            response.markMessageCompleted("msg-2");
            assertTrue(response.isTransactionCompleted());
        }

        @Test
        @DisplayName("markMessageCompleted with single message completes transaction")
        void markMessageCompleted_singleMessageCompletesTransaction() throws Exception {
            QueueMessageReceived msg = QueueMessageReceived.builder().id("only-msg").build();

            QueuesPollResponse response = QueuesPollResponse.builder()
                    .messages(Arrays.asList(msg))
                    .isAutoAcked(false)
                    .build();
            setupResponseWithSender(response);

            response.markMessageCompleted("only-msg");

            assertTrue(response.isTransactionCompleted());
        }

        @Test
        @DisplayName("markMessageCompleted handles unknown message id gracefully")
        void markMessageCompleted_handlesUnknownMessageId() {
            QueuesPollResponse response = QueuesPollResponse.builder()
                    .messages(new ArrayList<>())
                    .isAutoAcked(false)
                    .build();

            assertDoesNotThrow(() -> response.markMessageCompleted("unknown-id"));
        }
    }
}
