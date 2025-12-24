package io.kubemq.sdk.unit.queues;

import io.kubemq.sdk.queues.QueueMessagesWaiting;
import io.kubemq.sdk.queues.QueueMessageWaitingPulled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueueMessagesWaiting.
 */
class QueueMessagesWaitingTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("builder creates instance with defaults")
        void builder_createsInstanceWithDefaults() {
            QueueMessagesWaiting waiting = QueueMessagesWaiting.builder().build();

            assertNotNull(waiting);
            assertFalse(waiting.isError());
            assertNull(waiting.getError());
            assertNotNull(waiting.getMessages());
            assertTrue(waiting.getMessages().isEmpty());
        }

        @Test
        @DisplayName("builder with error sets error fields")
        void builder_withError_setsErrorFields() {
            QueueMessagesWaiting waiting = QueueMessagesWaiting.builder()
                    .isError(true)
                    .error("Queue not found")
                    .build();

            assertTrue(waiting.isError());
            assertEquals("Queue not found", waiting.getError());
        }

        @Test
        @DisplayName("builder with messages sets messages")
        void builder_withMessages_setsMessages() {
            List<QueueMessageWaitingPulled> messages = new ArrayList<>();
            messages.add(QueueMessageWaitingPulled.builder()
                    .id("msg-1")
                    .channel("test-queue")
                    .build());

            QueueMessagesWaiting waiting = QueueMessagesWaiting.builder()
                    .messages(messages)
                    .build();

            assertNotNull(waiting.getMessages());
            assertEquals(1, waiting.getMessages().size());
            assertEquals("msg-1", waiting.getMessages().get(0).getId());
        }
    }

    @Nested
    @DisplayName("Getter/Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("builder with isError sets the flag")
        void builder_withIsError_setsTheFlag() {
            QueueMessagesWaiting waiting = QueueMessagesWaiting.builder()
                    .isError(true)
                    .build();
            assertTrue(waiting.isError());
        }

        @Test
        @DisplayName("builder with error message sets the message")
        void builder_withError_setsTheMessage() {
            QueueMessagesWaiting waiting = QueueMessagesWaiting.builder()
                    .error("New error")
                    .build();
            assertEquals("New error", waiting.getError());
        }

        @Test
        @DisplayName("messages list is modifiable")
        void messagesList_isModifiable() {
            QueueMessagesWaiting waiting = QueueMessagesWaiting.builder().build();

            waiting.getMessages().add(QueueMessageWaitingPulled.builder()
                    .id("added-msg")
                    .build());

            assertEquals(1, waiting.getMessages().size());
            assertEquals("added-msg", waiting.getMessages().get(0).getId());
        }
    }

    @Nested
    @DisplayName("Multiple Messages Tests")
    class MultipleMessagesTests {

        @Test
        @DisplayName("can hold multiple messages")
        void canHoldMultipleMessages() {
            QueueMessagesWaiting waiting = QueueMessagesWaiting.builder().build();

            for (int i = 0; i < 10; i++) {
                waiting.getMessages().add(QueueMessageWaitingPulled.builder()
                        .id("msg-" + i)
                        .build());
            }

            assertEquals(10, waiting.getMessages().size());
        }
    }
}
