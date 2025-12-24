package io.kubemq.sdk.unit.queues;

import io.kubemq.sdk.queues.QueueMessageReceived;
import io.kubemq.sdk.queues.QueueMessagesReceived;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueueMessagesReceived.
 */
@DisplayName("QueueMessagesReceived Tests")
class QueueMessagesReceivedTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("builder creates instance with all fields")
        void builder_createsInstanceWithAllFields() {
            List<QueueMessageReceived> messages = new ArrayList<>();
            messages.add(QueueMessageReceived.builder().id("msg-1").build());
            messages.add(QueueMessageReceived.builder().id("msg-2").build());

            QueueMessagesReceived response = QueueMessagesReceived.builder()
                    .requestID("req-123")
                    .messages(messages)
                    .messagesReceived(2)
                    .messagesExpired(1)
                    .isPeak(true)
                    .isError(false)
                    .error(null)
                    .build();

            assertEquals("req-123", response.getRequestID());
            assertEquals(2, response.getMessages().size());
            assertEquals(2, response.getMessagesReceived());
            assertEquals(1, response.getMessagesExpired());
            assertTrue(response.isPeak());
            assertFalse(response.isError());
            assertNull(response.getError());
        }

        @Test
        @DisplayName("builder with error creates error response")
        void builder_withError_createsErrorResponse() {
            QueueMessagesReceived response = QueueMessagesReceived.builder()
                    .requestID("req-456")
                    .isError(true)
                    .error("Connection timeout")
                    .build();

            assertEquals("req-456", response.getRequestID());
            assertTrue(response.isError());
            assertEquals("Connection timeout", response.getError());
        }

        @Test
        @DisplayName("builder with empty messages list")
        void builder_withEmptyMessagesList() {
            QueueMessagesReceived response = QueueMessagesReceived.builder()
                    .requestID("req-789")
                    .messages(new ArrayList<>())
                    .messagesReceived(0)
                    .build();

            assertEquals("req-789", response.getRequestID());
            assertTrue(response.getMessages().isEmpty());
            assertEquals(0, response.getMessagesReceived());
        }
    }

    @Nested
    @DisplayName("Getter Tests")
    class GetterTests {

        @Test
        @DisplayName("getMessages returns empty list when messages is null")
        void getMessages_returnsEmptyListWhenNull() {
            QueueMessagesReceived response = new QueueMessagesReceived();

            List<QueueMessageReceived> messages = response.getMessages();

            assertNotNull(messages);
            assertTrue(messages.isEmpty());
        }

        @Test
        @DisplayName("getMessages returns same list after initialization")
        void getMessages_returnsSameListAfterInitialization() {
            QueueMessagesReceived response = new QueueMessagesReceived();

            List<QueueMessageReceived> firstCall = response.getMessages();
            List<QueueMessageReceived> secondCall = response.getMessages();

            assertSame(firstCall, secondCall);
        }

        @Test
        @DisplayName("getMessages allows adding messages to list")
        void getMessages_allowsAddingMessagesToList() {
            QueueMessagesReceived response = new QueueMessagesReceived();
            QueueMessageReceived msg = QueueMessageReceived.builder().id("msg-1").build();

            response.getMessages().add(msg);

            assertEquals(1, response.getMessages().size());
            assertEquals("msg-1", response.getMessages().get(0).getId());
        }
    }

    @Nested
    @DisplayName("Setter Tests")
    class SetterTests {

        @Test
        @DisplayName("setters update all fields")
        void setters_updateAllFields() {
            QueueMessagesReceived response = new QueueMessagesReceived();
            List<QueueMessageReceived> messages = new ArrayList<>();
            messages.add(QueueMessageReceived.builder().id("msg-1").build());

            response.setRequestID("new-req-id");
            response.setMessages(messages);
            response.setMessagesReceived(1);
            response.setMessagesExpired(0);
            response.setPeak(false);
            // Note: setError(boolean) sets isError, setError(String) sets the error message

            assertEquals("new-req-id", response.getRequestID());
            assertEquals(1, response.getMessages().size());
            assertEquals(1, response.getMessagesReceived());
            assertEquals(0, response.getMessagesExpired());
            assertFalse(response.isPeak());
        }
    }

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @Test
        @DisplayName("toString contains key fields")
        void toString_containsKeyFields() {
            QueueMessagesReceived response = QueueMessagesReceived.builder()
                    .requestID("req-test")
                    .messagesReceived(5)
                    .messagesExpired(2)
                    .isPeak(true)
                    .isError(false)
                    .build();

            String str = response.toString();

            assertNotNull(str);
            assertTrue(str.contains("req-test") || str.contains("requestID"));
        }
    }

    @Nested
    @DisplayName("NoArgsConstructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("no-args constructor creates empty instance")
        void noArgsConstructor_createsEmptyInstance() {
            QueueMessagesReceived response = new QueueMessagesReceived();

            assertNull(response.getRequestID());
            assertNotNull(response.getMessages()); // getMessages initializes if null
            assertEquals(0, response.getMessagesReceived());
            assertEquals(0, response.getMessagesExpired());
            assertFalse(response.isPeak());
            assertFalse(response.isError());
            assertNull(response.getError());
        }
    }

    @Nested
    @DisplayName("AllArgsConstructor Tests")
    class AllArgsConstructorTests {

        @Test
        @DisplayName("all-args constructor sets all fields")
        void allArgsConstructor_setsAllFields() {
            List<QueueMessageReceived> messages = Arrays.asList(
                    QueueMessageReceived.builder().id("msg-1").build(),
                    QueueMessageReceived.builder().id("msg-2").build()
            );

            QueueMessagesReceived response = new QueueMessagesReceived(
                    "req-all-args",
                    messages,
                    2,
                    0,
                    false,
                    false,
                    null
            );

            assertEquals("req-all-args", response.getRequestID());
            assertEquals(2, response.getMessages().size());
            assertEquals(2, response.getMessagesReceived());
            assertEquals(0, response.getMessagesExpired());
            assertFalse(response.isPeak());
            assertFalse(response.isError());
            assertNull(response.getError());
        }
    }
}
