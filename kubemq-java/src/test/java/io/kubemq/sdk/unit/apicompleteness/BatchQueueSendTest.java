package io.kubemq.sdk.unit.apicompleteness;

import io.kubemq.sdk.exception.ValidationException;
import io.kubemq.sdk.queues.QueueMessage;
import io.kubemq.sdk.queues.QueuesClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for batch queue send validation (REQ-API-1 Gap B).
 */
@ExtendWith(MockitoExtension.class)
class BatchQueueSendTest {

    private QueuesClient client;

    @BeforeEach
    void setup() {
        client = QueuesClient.builder()
                .address("localhost:50000")
                .clientId("test-client")
                .build();
    }

    @AfterEach
    void teardown() {
        if (client != null) client.close();
    }

    @Test
    @DisplayName("API-5: sendQueuesMessages with null list throws ValidationException")
    void sendQueuesMessages_nullList_throwsValidationException() {
        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> client.sendQueuesMessages(null)
        );
        assertTrue(ex.getMessage().contains("null or empty"));
    }

    @Test
    @DisplayName("API-6: sendQueuesMessages with empty list throws ValidationException")
    void sendQueuesMessages_emptyList_throwsValidationException() {
        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> client.sendQueuesMessages(new ArrayList<>())
        );
        assertTrue(ex.getMessage().contains("null or empty"));
    }

    @Test
    @DisplayName("API-7: sendQueuesMessages validates all messages before sending")
    void sendQueuesMessages_invalidMessage_throwsValidationException() {
        QueueMessage valid = QueueMessage.builder()
                .channel("queue-1")
                .body("body".getBytes())
                .build();
        QueueMessage invalid = QueueMessage.builder()
                .channel(null)
                .body("body".getBytes())
                .build();

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> client.sendQueuesMessages(Arrays.asList(valid, invalid))
        );
        assertTrue(ex.getMessage().contains("channel") || ex.getMessage().contains("Channel"));
    }

    @Test
    @DisplayName("API-8: sendQueuesMessages validates message with empty body/metadata/tags")
    void sendQueuesMessages_emptyBodyAndMetadata_throwsValidationException() {
        QueueMessage emptyMsg = QueueMessage.builder()
                .channel("queue-1")
                .build();

        assertThrows(
                ValidationException.class,
                () -> client.sendQueuesMessages(List.of(emptyMsg))
        );
    }
}
