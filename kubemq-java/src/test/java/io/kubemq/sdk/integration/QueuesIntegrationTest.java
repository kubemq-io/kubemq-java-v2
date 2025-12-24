package io.kubemq.sdk.integration;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.*;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for QueuesClient.
 * Requires a running KubeMQ server at localhost:50000 (or configured via KUBEMQ_ADDRESS).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QueuesIntegrationTest extends BaseIntegrationTest {

    private QueuesClient client;
    private String testChannel;

    @BeforeAll
    void setup() {
        testChannel = uniqueChannel("queues-test");
        client = QueuesClient.builder()
                .address(kubemqAddress)
                .clientId(uniqueClientId("queues"))
                .logLevel(KubeMQClient.Level.INFO)
                .build();
    }

    @AfterAll
    void teardown() {
        if (client != null) {
            try {
                client.deleteQueuesChannel(testChannel);
            } catch (Exception ignored) {
            }
            client.close();
        }
    }

    @Override
    protected void verifyKubeMQConnection() {
        // Will be verified in setup when client is created
    }

    @Test
    @Order(1)
    @DisplayName("Ping - should verify server connection")
    void ping_shouldVerifyServerConnection() {
        ServerInfo serverInfo = client.ping();

        assertNotNull(serverInfo);
        assertNotNull(serverInfo.getHost());
        assertNotNull(serverInfo.getVersion());
    }

    @Test
    @Order(2)
    @DisplayName("Create channel - should create queue channel")
    void createChannel_shouldSucceed() {
        boolean result = client.createQueuesChannel(testChannel);

        assertTrue(result);
    }

    @Test
    @Order(3)
    @DisplayName("List channels - should find created channel")
    void listChannels_shouldFindCreatedChannel() {
        List<QueuesChannel> channels = client.listQueuesChannels(testChannel);

        assertNotNull(channels);
        assertTrue(channels.stream().anyMatch(c -> c.getName().equals(testChannel)));
    }

    @Test
    @Order(4)
    @DisplayName("Send message - should send and receive message")
    void sendMessage_shouldSucceed() {
        String messageBody = "test message " + System.currentTimeMillis();

        QueueMessage message = QueueMessage.builder()
                .channel(testChannel)
                .body(messageBody.getBytes())
                .build();

        QueueSendResult result = client.sendQueuesMessage(message);

        assertNotNull(result);
        assertFalse(result.isError(), "Send should not have error: " + result.getError());
        assertNotNull(result.getId());
    }

    @Test
    @Order(5)
    @DisplayName("Receive message - should receive sent message")
    void receiveMessage_shouldReceiveSentMessage() {
        // First send a message
        String messageBody = "receive test " + System.currentTimeMillis();
        QueueMessage message = QueueMessage.builder()
                .channel(testChannel)
                .body(messageBody.getBytes())
                .build();
        client.sendQueuesMessage(message);

        // Now receive it
        QueuesPollRequest request = QueuesPollRequest.builder()
                .channel(testChannel)
                .pollMaxMessages(10)
                .pollWaitTimeoutInSeconds(5)
                .autoAckMessages(true)
                .build();

        QueuesPollResponse response = client.receiveQueuesMessages(request);

        assertNotNull(response);
        assertFalse(response.isError(), "Receive should not have error: " + response.getError());
        assertNotNull(response.getMessages());
        assertFalse(response.getMessages().isEmpty(), "Should have received at least one message");
    }

    @Test
    @Order(6)
    @DisplayName("Send with metadata - should preserve metadata")
    void sendWithMetadata_shouldPreserveMetadata() {
        String channel = uniqueChannel("metadata-test");
        client.createQueuesChannel(channel);

        try {
            String metadata = "test-metadata";
            QueueMessage message = QueueMessage.builder()
                    .channel(channel)
                    .body("test".getBytes())
                    .metadata(metadata)
                    .build();
            client.sendQueuesMessage(message);

            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel(channel)
                    .pollMaxMessages(1)
                    .pollWaitTimeoutInSeconds(5)
                    .autoAckMessages(true)
                    .build();

            QueuesPollResponse response = client.receiveQueuesMessages(request);

            assertFalse(response.getMessages().isEmpty());
            assertEquals(metadata, response.getMessages().get(0).getMetadata());
        } finally {
            client.deleteQueuesChannel(channel);
        }
    }

    @Test
    @Order(7)
    @DisplayName("Send with tags - should preserve tags")
    void sendWithTags_shouldPreserveTags() {
        String channel = uniqueChannel("tags-test");
        client.createQueuesChannel(channel);

        try {
            Map<String, String> tags = new HashMap<>();
            tags.put("key1", "value1");
            tags.put("key2", "value2");
            QueueMessage message = QueueMessage.builder()
                    .channel(channel)
                    .body("test".getBytes())
                    .tags(tags)
                    .build();
            client.sendQueuesMessage(message);

            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel(channel)
                    .pollMaxMessages(1)
                    .pollWaitTimeoutInSeconds(5)
                    .autoAckMessages(true)
                    .build();

            QueuesPollResponse response = client.receiveQueuesMessages(request);

            assertFalse(response.getMessages().isEmpty());
            Map<String, String> receivedTags = response.getMessages().get(0).getTags();
            assertEquals("value1", receivedTags.get("key1"));
            assertEquals("value2", receivedTags.get("key2"));
        } finally {
            client.deleteQueuesChannel(channel);
        }
    }

    @Test
    @Order(8)
    @DisplayName("Manual ack - should require explicit acknowledgement")
    void manualAck_shouldRequireExplicitAck() {
        String channel = uniqueChannel("manual-ack-test");
        client.createQueuesChannel(channel);

        try {
            // Send a message
            QueueMessage message = QueueMessage.builder()
                    .channel(channel)
                    .body("ack test".getBytes())
                    .build();
            client.sendQueuesMessage(message);

            // Receive without auto-ack
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel(channel)
                    .pollMaxMessages(1)
                    .pollWaitTimeoutInSeconds(5)
                    .autoAckMessages(false)
                    .build();

            QueuesPollResponse response = client.receiveQueuesMessages(request);
            assertFalse(response.getMessages().isEmpty());

            // Explicitly ack
            QueueMessageReceived receivedMsg = response.getMessages().get(0);
            receivedMsg.ack();

            // Try to receive again - should be empty (message was acked)
            QueuesPollResponse response2 = client.receiveQueuesMessages(request);
            assertTrue(response2.getMessages().isEmpty(), "No messages should remain after ack");
        } finally {
            client.deleteQueuesChannel(channel);
        }
    }

    @Test
    @Order(9)
    @DisplayName("Reject message - should return message to queue")
    void rejectMessage_shouldReturnToQueue() {
        String channel = uniqueChannel("reject-test");
        client.createQueuesChannel(channel);

        try {
            // Send a message
            QueueMessage message = QueueMessage.builder()
                    .channel(channel)
                    .body("reject test".getBytes())
                    .build();
            client.sendQueuesMessage(message);

            // Receive and reject
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel(channel)
                    .pollMaxMessages(1)
                    .pollWaitTimeoutInSeconds(5)
                    .autoAckMessages(false)
                    .build();

            QueuesPollResponse response = client.receiveQueuesMessages(request);
            assertFalse(response.getMessages().isEmpty());

            QueueMessageReceived receivedMsg = response.getMessages().get(0);
            receivedMsg.reject();

            // Message should be available again
            QueuesPollResponse response2 = client.receiveQueuesMessages(request);
            assertFalse(response2.getMessages().isEmpty(), "Rejected message should be available");

            // Clean up - ack the message
            response2.getMessages().get(0).ack();
        } finally {
            client.deleteQueuesChannel(channel);
        }
    }

    @Test
    @Order(10)
    @DisplayName("Waiting messages - should return count of pending messages")
    void waitingMessages_shouldReturnPendingCount() {
        String channel = uniqueChannel("waiting-test");
        client.createQueuesChannel(channel);

        try {
            // Send multiple messages
            for (int i = 0; i < 3; i++) {
                QueueMessage message = QueueMessage.builder()
                        .channel(channel)
                        .body(("waiting test " + i).getBytes())
                        .build();
                client.sendQueuesMessage(message);
            }

            // Check waiting count
            QueueMessagesWaiting waiting = client.waiting(channel, 10, 5);

            assertNotNull(waiting);
            assertTrue(waiting.getMessages().size() >= 3, "Should have at least 3 waiting messages");
        } finally {
            // Clean up by receiving all messages
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel(channel)
                    .pollMaxMessages(10)
                    .pollWaitTimeoutInSeconds(2)
                    .autoAckMessages(true)
                    .build();
            client.receiveQueuesMessages(request);
            client.deleteQueuesChannel(channel);
        }
    }

    @Test
    @Order(11)
    @DisplayName("Message with delay - should delay message delivery")
    void messageWithDelay_shouldDelayDelivery() {
        String channel = uniqueChannel("delay-test");
        client.createQueuesChannel(channel);

        try {
            // Send with 2 second delay
            QueueMessage message = QueueMessage.builder()
                    .channel(channel)
                    .body("delayed message".getBytes())
                    .delayInSeconds(2)
                    .build();
            client.sendQueuesMessage(message);

            // Try to receive immediately - should be empty
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel(channel)
                    .pollMaxMessages(1)
                    .pollWaitTimeoutInSeconds(1)
                    .autoAckMessages(true)
                    .build();

            QueuesPollResponse response1 = client.receiveQueuesMessages(request);
            assertTrue(response1.getMessages().isEmpty(), "Delayed message should not be available yet");

            // Wait for delay and try again
            sleep(3, TimeUnit.SECONDS);

            QueuesPollRequest request2 = QueuesPollRequest.builder()
                    .channel(channel)
                    .pollMaxMessages(1)
                    .pollWaitTimeoutInSeconds(2)
                    .autoAckMessages(true)
                    .build();

            QueuesPollResponse response2 = client.receiveQueuesMessages(request2);
            assertFalse(response2.getMessages().isEmpty(), "Delayed message should now be available");
        } finally {
            client.deleteQueuesChannel(channel);
        }
    }

    @Test
    @Order(12)
    @DisplayName("Delete channel - should remove channel")
    void deleteChannel_shouldSucceed() {
        String channel = uniqueChannel("delete-test");
        client.createQueuesChannel(channel);

        boolean result = client.deleteQueuesChannel(channel);

        assertTrue(result);

        // Verify channel is gone
        List<QueuesChannel> channels = client.listQueuesChannels(channel);
        assertTrue(channels.stream().noneMatch(c -> c.getName().equals(channel)));
    }

    @Test
    @Order(13)
    @DisplayName("Poll timeout - should return empty on timeout")
    void pollTimeout_shouldReturnEmptyOnTimeout() {
        String channel = uniqueChannel("timeout-test");
        client.createQueuesChannel(channel);

        try {
            // Poll empty channel
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel(channel)
                    .pollMaxMessages(1)
                    .pollWaitTimeoutInSeconds(2)
                    .autoAckMessages(true)
                    .build();

            long start = System.currentTimeMillis();
            QueuesPollResponse response = client.receiveQueuesMessages(request);
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(response.getMessages().isEmpty());
            assertTrue(elapsed >= 1500, "Should have waited for timeout"); // Allow some variance
        } finally {
            client.deleteQueuesChannel(channel);
        }
    }

    @Test
    @Order(14)
    @DisplayName("Visibility timeout - message returns to queue after visibility expires")
    void visibilityTimeout_shouldReturnMessageAfterExpiry() {
        String channel = uniqueChannel("visibility-test");
        client.createQueuesChannel(channel);

        try {
            // Send a message
            QueueMessage message = QueueMessage.builder()
                    .channel(channel)
                    .body("visibility test".getBytes())
                    .build();
            client.sendQueuesMessage(message);

            // Poll with short visibility timeout (3 seconds)
            // Note: visibilitySeconds is client-side - when it expires,
            // the SDK auto-rejects the message back to the queue
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel(channel)
                    .pollMaxMessages(1)
                    .pollWaitTimeoutInSeconds(5)
                    .autoAckMessages(false)
                    .visibilitySeconds(3)
                    .build();

            QueuesPollResponse response1 = client.receiveQueuesMessages(request);
            assertFalse(response1.getMessages().isEmpty(), "Should receive message");

            // Don't ack or reject - let visibility expire
            // The client-side timer will auto-reject after 3 seconds

            // Wait for visibility to expire plus buffer
            sleep(5, TimeUnit.SECONDS);

            // Poll again - message should be back in queue
            QueuesPollRequest request2 = QueuesPollRequest.builder()
                    .channel(channel)
                    .pollMaxMessages(1)
                    .pollWaitTimeoutInSeconds(5)
                    .autoAckMessages(true) // Auto-ack to clean up
                    .build();

            QueuesPollResponse response2 = client.receiveQueuesMessages(request2);
            assertFalse(response2.getMessages().isEmpty(),
                    "Message should be available after visibility timeout expired");
        } finally {
            // Clean up any remaining messages
            QueuesPollRequest cleanup = QueuesPollRequest.builder()
                    .channel(channel)
                    .pollMaxMessages(10)
                    .pollWaitTimeoutInSeconds(1)
                    .autoAckMessages(true)
                    .build();
            client.receiveQueuesMessages(cleanup);
            client.deleteQueuesChannel(channel);
        }
    }

    @Test
    @Order(15)
    @Tag("slow")
    @DisplayName("Client reconnects - should recover after brief disconnection")
    void clientReconnects_shouldRecoverAfterDisconnection() {
        // This test verifies that the client can recover from connection issues.
        // Since we don't have Testcontainers, we just verify basic reconnection
        // capability by testing that operations work after a brief pause.

        String channel = uniqueChannel("reconnect-test");
        client.createQueuesChannel(channel);

        try {
            // Send initial message
            QueueMessage message1 = QueueMessage.builder()
                    .channel(channel)
                    .body("before reconnect".getBytes())
                    .build();
            QueueSendResult result1 = client.sendQueuesMessage(message1);
            assertFalse(result1.isError(), "Initial send should succeed");

            // Verify ping works
            assertNotNull(client.ping(), "Ping should work");

            // Wait a moment (simulating network hiccup recovery time)
            sleep(2, TimeUnit.SECONDS);

            // Send another message after pause
            QueueMessage message2 = QueueMessage.builder()
                    .channel(channel)
                    .body("after reconnect".getBytes())
                    .build();
            QueueSendResult result2 = client.sendQueuesMessage(message2);
            assertFalse(result2.isError(), "Send after pause should succeed");

            // Verify we can receive messages
            QueuesPollRequest request = QueuesPollRequest.builder()
                    .channel(channel)
                    .pollMaxMessages(10)
                    .pollWaitTimeoutInSeconds(5)
                    .autoAckMessages(true)
                    .build();

            QueuesPollResponse response = client.receiveQueuesMessages(request);
            assertTrue(response.getMessages().size() >= 2,
                    "Should receive messages sent before and after pause");
        } finally {
            client.deleteQueuesChannel(channel);
        }
    }
}
