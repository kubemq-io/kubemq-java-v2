package io.kubemq.sdk.unit.client;

import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.cq.CommandMessage;
import io.kubemq.sdk.cq.CommandsSubscription;
import io.kubemq.sdk.exception.ClientClosedException;
import io.kubemq.sdk.pubsub.EventMessage;
import io.kubemq.sdk.pubsub.EventStoreMessage;
import io.kubemq.sdk.pubsub.EventsSubscription;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.QueueMessage;
import io.kubemq.sdk.queues.QueuesPollRequest;
import io.kubemq.sdk.queues.QueuesClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that every operation on a closed client throws ClientClosedException.
 * Validates ensureNotClosed() guard on all three client types per REQ-TEST-1 AC-7.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ClosedClientGuardTest {

    @Nested
    class PubSubClientTests {

        private PubSubClient createAndClose() {
            PubSubClient client = PubSubClient.builder()
                .address("localhost:50000")
                .clientId("closed-guard-pubsub")
                .build();
            client.close();
            return client;
        }

        @Test
        void sendEventsMessage_afterClose_throwsClientClosedException() {
            PubSubClient client = createAndClose();

            assertThrows(ClientClosedException.class, () ->
                client.sendEventsMessage(EventMessage.builder()
                    .channel("test-ch")
                    .body("data".getBytes())
                    .build()));
        }

        @Test
        void sendEventsStoreMessage_afterClose_throwsClientClosedException() {
            PubSubClient client = createAndClose();

            assertThrows(ClientClosedException.class, () ->
                client.sendEventsStoreMessage(EventStoreMessage.builder()
                    .channel("test-ch")
                    .body("data".getBytes())
                    .build()));
        }

        @Test
        void subscribeToEvents_afterClose_throwsClientClosedException() {
            PubSubClient client = createAndClose();

            assertThrows(ClientClosedException.class, () ->
                client.subscribeToEvents(EventsSubscription.builder()
                    .channel("test-ch")
                    .onReceiveEventCallback(event -> {})
                    .onErrorCallback(err -> {})
                    .build()));
        }

        @Test
        void createEventsChannel_afterClose_throwsClientClosedException() {
            PubSubClient client = createAndClose();

            assertThrows(ClientClosedException.class, () ->
                client.createEventsChannel("test-ch"));
        }

        @Test
        void deleteEventsChannel_afterClose_throwsClientClosedException() {
            PubSubClient client = createAndClose();

            assertThrows(ClientClosedException.class, () ->
                client.deleteEventsChannel("test-ch"));
        }

        @Test
        void listEventsChannels_afterClose_throwsClientClosedException() {
            PubSubClient client = createAndClose();

            assertThrows(ClientClosedException.class, () ->
                client.listEventsChannels("*"));
        }
    }

    @Nested
    class QueuesClientTests {

        private QueuesClient createAndClose() {
            QueuesClient client = QueuesClient.builder()
                .address("localhost:50000")
                .clientId("closed-guard-queues")
                .build();
            client.close();
            return client;
        }

        @Test
        void sendQueueMessage_afterClose_throwsClientClosedException() {
            QueuesClient client = createAndClose();

            assertThrows(ClientClosedException.class, () ->
                client.sendQueueMessage(QueueMessage.builder()
                    .channel("test-q")
                    .body("data".getBytes())
                    .build()));
        }

        @Test
        void createQueuesChannel_afterClose_throwsClientClosedException() {
            QueuesClient client = createAndClose();

            assertThrows(ClientClosedException.class, () ->
                client.createQueuesChannel("test-q"));
        }

        @Test
        void deleteQueuesChannel_afterClose_throwsClientClosedException() {
            QueuesClient client = createAndClose();

            assertThrows(ClientClosedException.class, () ->
                client.deleteQueuesChannel("test-q"));
        }

        @Test
        void listQueuesChannels_afterClose_throwsClientClosedException() {
            QueuesClient client = createAndClose();

            assertThrows(ClientClosedException.class, () ->
                client.listQueuesChannels("*"));
        }
    }

    @Nested
    class CQClientTests {

        private CQClient createAndClose() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("closed-guard-cq")
                .build();
            client.close();
            return client;
        }

        @Test
        void sendCommandRequest_afterClose_throwsClientClosedException() {
            CQClient client = createAndClose();

            assertThrows(ClientClosedException.class, () ->
                client.sendCommandRequest(CommandMessage.builder()
                    .channel("test-cmd")
                    .body("data".getBytes())
                    .timeoutInSeconds(5)
                    .build()));
        }

        @Test
        void createCommandsChannel_afterClose_throwsClientClosedException() {
            CQClient client = createAndClose();

            assertThrows(ClientClosedException.class, () ->
                client.createCommandsChannel("test-cmd"));
        }

        @Test
        void subscribeToCommands_afterClose_throwsClientClosedException() {
            CQClient client = createAndClose();

            assertThrows(ClientClosedException.class, () ->
                client.subscribeToCommands(CommandsSubscription.builder()
                    .channel("test-cmd")
                    .onReceiveCommandCallback(cmd -> {})
                    .onErrorCallback(err -> {})
                    .build()));
        }

        @Test
        void listCommandsChannels_afterClose_throwsClientClosedException() {
            CQClient client = createAndClose();

            assertThrows(ClientClosedException.class, () ->
                client.listCommandsChannels("*"));
        }
    }

    @Nested
    class IdempotentCloseTests {

        @Test
        void pubSubClient_close_calledTwice_doesNotThrow() {
            PubSubClient client = PubSubClient.builder()
                .address("localhost:50000")
                .clientId("idempotent-close-pubsub")
                .build();
            client.close();
            assertDoesNotThrow(client::close);
        }

        @Test
        void queuesClient_close_calledTwice_doesNotThrow() {
            QueuesClient client = QueuesClient.builder()
                .address("localhost:50000")
                .clientId("idempotent-close-queues")
                .build();
            client.close();
            assertDoesNotThrow(client::close);
        }

        @Test
        void cqClient_close_calledTwice_doesNotThrow() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("idempotent-close-cq")
                .build();
            client.close();
            assertDoesNotThrow(client::close);
        }

        @Test
        void close_setsIsClosedToTrue() {
            PubSubClient client = PubSubClient.builder()
                .address("localhost:50000")
                .clientId("is-closed-check")
                .build();
            assertFalse(client.isClosed());
            client.close();
            assertTrue(client.isClosed());
        }
    }

    @Nested
    class ClientClosedExceptionTest {

        @Test
        void clientClosedException_hasCorrectProperties() {
            ClientClosedException ex = ClientClosedException.create();

            assertFalse(ex.isRetryable());
            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("closed"));
        }
    }
}
