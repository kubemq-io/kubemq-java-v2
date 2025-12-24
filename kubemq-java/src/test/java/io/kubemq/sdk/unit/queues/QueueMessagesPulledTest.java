package io.kubemq.sdk.unit.queues;

import io.kubemq.sdk.queues.QueueMessagesPulled;
import io.kubemq.sdk.queues.QueueMessageWaitingPulled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueueMessagesPulled.
 */
class QueueMessagesPulledTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("builder creates instance with defaults")
        void builder_createsInstanceWithDefaults() {
            QueueMessagesPulled pulled = QueueMessagesPulled.builder().build();

            assertNotNull(pulled);
            assertFalse(pulled.isError());
            assertNull(pulled.getError());
            assertNotNull(pulled.getMessages());
            assertTrue(pulled.getMessages().isEmpty());
        }

        @Test
        @DisplayName("builder with error sets error fields")
        void builder_withError_setsErrorFields() {
            QueueMessagesPulled pulled = QueueMessagesPulled.builder()
                    .isError(true)
                    .error("Queue not found")
                    .build();

            assertTrue(pulled.isError());
            assertEquals("Queue not found", pulled.getError());
        }

        @Test
        @DisplayName("builder with messages sets messages")
        void builder_withMessages_setsMessages() {
            List<QueueMessageWaitingPulled> messages = new ArrayList<>();
            messages.add(QueueMessageWaitingPulled.builder()
                    .id("msg-1")
                    .channel("test-queue")
                    .build());

            QueueMessagesPulled pulled = QueueMessagesPulled.builder()
                    .messages(messages)
                    .build();

            assertNotNull(pulled.getMessages());
            assertEquals(1, pulled.getMessages().size());
            assertEquals("msg-1", pulled.getMessages().get(0).getId());
        }
    }

    @Nested
    @DisplayName("Getter/Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("builder with isError sets the flag")
        void builder_withIsError_setsTheFlag() {
            QueueMessagesPulled pulled = QueueMessagesPulled.builder()
                    .isError(true)
                    .build();
            assertTrue(pulled.isError());
        }

        @Test
        @DisplayName("builder with error message sets the message")
        void builder_withError_setsTheMessage() {
            QueueMessagesPulled pulled = QueueMessagesPulled.builder()
                    .error("New error")
                    .build();
            assertEquals("New error", pulled.getError());
        }

        @Test
        @DisplayName("messages list is modifiable")
        void messagesList_isModifiable() {
            QueueMessagesPulled pulled = QueueMessagesPulled.builder().build();

            pulled.getMessages().add(QueueMessageWaitingPulled.builder()
                    .id("added-msg")
                    .build());

            assertEquals(1, pulled.getMessages().size());
            assertEquals("added-msg", pulled.getMessages().get(0).getId());
        }
    }

    @Nested
    @DisplayName("Multiple Messages Tests")
    class MultipleMessagesTests {

        @Test
        @DisplayName("can hold multiple messages")
        void canHoldMultipleMessages() {
            QueueMessagesPulled pulled = QueueMessagesPulled.builder().build();

            for (int i = 0; i < 10; i++) {
                pulled.getMessages().add(QueueMessageWaitingPulled.builder()
                        .id("msg-" + i)
                        .build());
            }

            assertEquals(10, pulled.getMessages().size());
        }
    }
}
