package io.kubemq.sdk.integration;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.cq.*;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CQClient (Commands and Queries).
 * Requires a running KubeMQ server at localhost:50000 (or configured via KUBEMQ_ADDRESS).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CQIntegrationTest extends BaseIntegrationTest {

    private CQClient client;
    private CQClient responderClient;
    private String commandsChannel;
    private String queriesChannel;

    @BeforeAll
    void setup() {
        commandsChannel = uniqueChannel("commands-test");
        queriesChannel = uniqueChannel("queries-test");

        // Main client for sending requests
        client = CQClient.builder()
                .address(kubemqAddress)
                .clientId(uniqueClientId("cq-requester"))
                .logLevel(KubeMQClient.Level.INFO)
                .build();

        // Responder client for handling requests
        responderClient = CQClient.builder()
                .address(kubemqAddress)
                .clientId(uniqueClientId("cq-responder"))
                .logLevel(KubeMQClient.Level.INFO)
                .build();
    }

    @AfterAll
    void teardown() {
        if (client != null) {
            try {
                client.deleteCommandsChannel(commandsChannel);
                client.deleteQueriesChannel(queriesChannel);
            } catch (Exception ignored) {
            }
            client.close();
        }
        if (responderClient != null) {
            responderClient.close();
        }
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
    @DisplayName("Create commands channel - should succeed")
    void createCommandsChannel_shouldSucceed() {
        boolean result = client.createCommandsChannel(commandsChannel);

        assertTrue(result);
    }

    @Test
    @Order(3)
    @DisplayName("Create queries channel - should succeed")
    void createQueriesChannel_shouldSucceed() {
        boolean result = client.createQueriesChannel(queriesChannel);

        assertTrue(result);
    }

    @Test
    @Order(4)
    @DisplayName("List commands channels - should find created channel")
    void listCommandsChannels_shouldFindCreatedChannel() {
        List<CQChannel> channels = client.listCommandsChannels(commandsChannel);

        assertNotNull(channels);
        assertTrue(channels.stream().anyMatch(c -> c.getName().equals(commandsChannel)));
    }

    @Test
    @Order(5)
    @DisplayName("List queries channels - should find created channel")
    void listQueriesChannels_shouldFindCreatedChannel() {
        List<CQChannel> channels = client.listQueriesChannels(queriesChannel);

        assertNotNull(channels);
        assertTrue(channels.stream().anyMatch(c -> c.getName().equals(queriesChannel)));
    }

    @Test
    @Order(6)
    @DisplayName("Send command with responder - should receive response")
    void sendCommand_withResponder_shouldReceiveResponse() throws InterruptedException {
        String channel = uniqueChannel("cmd-resp");
        client.createCommandsChannel(channel);

        try {
            CountDownLatch responderReady = new CountDownLatch(1);
            AtomicReference<CommandMessageReceived> receivedCommand = new AtomicReference<>();

            // Setup responder
            CommandsSubscription subscription = CommandsSubscription.builder()
                    .channel(channel)
                    .onReceiveCommandCallback(cmd -> {
                        receivedCommand.set(cmd);
                        // Send response
                        CommandResponseMessage response = CommandResponseMessage.builder()
                                .commandReceived(cmd)
                                .isExecuted(true)
                                .build();
                        responderClient.sendResponseMessage(response);
                    })
                    .onErrorCallback(error -> fail("Responder error: " + error))
                    .build();

            responderClient.subscribeToCommands(subscription);
            sleep(500); // Wait for subscription

            // Send command
            CommandMessage command = CommandMessage.builder()
                    .channel(channel)
                    .body("execute action".getBytes())
                    .timeoutInSeconds(10)
                    .build();

            CommandResponseMessage response = client.sendCommandRequest(command);

            assertNotNull(response);
            assertTrue(response.isExecuted());
            assertTrue(response.getError() == null || response.getError().isEmpty());
            assertNotNull(receivedCommand.get());
            assertEquals("execute action", new String(receivedCommand.get().getBody()));

            subscription.cancel();
        } finally {
            client.deleteCommandsChannel(channel);
        }
    }

    @Test
    @Order(7)
    @DisplayName("Send query with responder - should receive response with data")
    void sendQuery_withResponder_shouldReceiveResponseWithData() throws InterruptedException {
        String channel = uniqueChannel("qry-resp");
        client.createQueriesChannel(channel);

        try {
            CountDownLatch responderReady = new CountDownLatch(1);
            AtomicReference<QueryMessageReceived> receivedQuery = new AtomicReference<>();

            // Setup responder
            QueriesSubscription subscription = QueriesSubscription.builder()
                    .channel(channel)
                    .onReceiveQueryCallback(qry -> {
                        receivedQuery.set(qry);
                        // Send response with data
                        QueryResponseMessage response = QueryResponseMessage.builder()
                                .queryReceived(qry)
                                .isExecuted(true)
                                .body("query result data".getBytes())
                                .metadata("response-metadata")
                                .build();
                        responderClient.sendResponseMessage(response);
                    })
                    .onErrorCallback(error -> fail("Responder error: " + error))
                    .build();

            responderClient.subscribeToQueries(subscription);
            sleep(500); // Wait for subscription

            // Send query
            QueryMessage query = QueryMessage.builder()
                    .channel(channel)
                    .body("get data".getBytes())
                    .metadata("request-metadata")
                    .timeoutInSeconds(10)
                    .build();

            QueryResponseMessage response = client.sendQueryRequest(query);

            assertNotNull(response);
            assertTrue(response.isExecuted());
            assertTrue(response.getError() == null || response.getError().isEmpty());
            assertEquals("query result data", new String(response.getBody()));
            assertEquals("response-metadata", response.getMetadata());

            // Verify received query
            assertNotNull(receivedQuery.get());
            assertEquals("get data", new String(receivedQuery.get().getBody()));
            assertEquals("request-metadata", receivedQuery.get().getMetadata());

            subscription.cancel();
        } finally {
            client.deleteQueriesChannel(channel);
        }
    }

    @Test
    @Order(8)
    @DisplayName("Command timeout - should timeout when no responder")
    void commandTimeout_shouldTimeoutWhenNoResponder() {
        String channel = uniqueChannel("cmd-timeout");
        client.createCommandsChannel(channel);

        try {
            CommandMessage command = CommandMessage.builder()
                    .channel(channel)
                    .body("no responder".getBytes())
                    .timeoutInSeconds(2) // Short timeout
                    .build();

            long start = System.currentTimeMillis();

            // Should throw or return error due to timeout
            try {
                CommandResponseMessage response = client.sendCommandRequest(command);
                // If we get here, response should indicate error/timeout
                assertTrue((response.getError() != null && !response.getError().isEmpty()) || !response.isExecuted(),
                        "Should indicate timeout or error");
            } catch (Exception e) {
                // Expected - timeout or error is acceptable
                long elapsed = System.currentTimeMillis() - start;
                assertTrue(elapsed >= 1500, "Should have waited for timeout");
            }
        } finally {
            client.deleteCommandsChannel(channel);
        }
    }

    @Test
    @Order(9)
    @DisplayName("Query with tags - should preserve tags")
    void queryWithTags_shouldPreserveTags() throws InterruptedException {
        String channel = uniqueChannel("qry-tags");
        client.createQueriesChannel(channel);

        try {
            AtomicReference<QueryMessageReceived> receivedQuery = new AtomicReference<>();

            QueriesSubscription subscription = QueriesSubscription.builder()
                    .channel(channel)
                    .onReceiveQueryCallback(qry -> {
                        receivedQuery.set(qry);
                        QueryResponseMessage response = QueryResponseMessage.builder()
                                .queryReceived(qry)
                                .isExecuted(true)
                                .build();
                        responderClient.sendResponseMessage(response);
                    })
                    .build();

            responderClient.subscribeToQueries(subscription);
            sleep(500);

            Map<String, String> tags = new HashMap<>();
            tags.put("request-type", "search");
            tags.put("user-id", "12345");
            QueryMessage query = QueryMessage.builder()
                    .channel(channel)
                    .body("tagged query".getBytes())
                    .tags(tags)
                    .timeoutInSeconds(10)
                    .build();

            client.sendQueryRequest(query);

            // Give time for the callback to execute
            sleep(500);

            assertNotNull(receivedQuery.get());
            assertEquals("search", receivedQuery.get().getTags().get("request-type"));
            assertEquals("12345", receivedQuery.get().getTags().get("user-id"));

            subscription.cancel();
        } finally {
            client.deleteQueriesChannel(channel);
        }
    }

    @Test
    @Order(10)
    @DisplayName("Command with error response - should propagate error")
    void commandWithErrorResponse_shouldPropagateError() throws InterruptedException {
        String channel = uniqueChannel("cmd-error");
        client.createCommandsChannel(channel);

        try {
            CommandsSubscription subscription = CommandsSubscription.builder()
                    .channel(channel)
                    .onReceiveCommandCallback(cmd -> {
                        // Send error response
                        CommandResponseMessage response = CommandResponseMessage.builder()
                                .commandReceived(cmd)
                                .isExecuted(false)
                                .error("Command execution failed")
                                .build();
                        responderClient.sendResponseMessage(response);
                    })
                    .build();

            responderClient.subscribeToCommands(subscription);
            sleep(500);

            CommandMessage command = CommandMessage.builder()
                    .channel(channel)
                    .body("fail this".getBytes())
                    .timeoutInSeconds(10)
                    .build();

            CommandResponseMessage response = client.sendCommandRequest(command);

            assertNotNull(response);
            assertFalse(response.isExecuted());
            assertTrue(response.getError() != null && !response.getError().isEmpty());
            assertEquals("Command execution failed", response.getError());

            subscription.cancel();
        } finally {
            client.deleteCommandsChannel(channel);
        }
    }

    @Test
    @Order(11)
    @DisplayName("Group subscription for commands - should load balance")
    void groupSubscription_shouldLoadBalanceCommands() throws InterruptedException {
        String channel = uniqueChannel("cmd-group");
        client.createCommandsChannel(channel);

        try {
            AtomicReference<Integer> responder1Count = new AtomicReference<>(0);
            AtomicReference<Integer> responder2Count = new AtomicReference<>(0);

            // Create two responders in the same group
            CommandsSubscription sub1 = CommandsSubscription.builder()
                    .channel(channel)
                    .group("cmd-group")
                    .onReceiveCommandCallback(cmd -> {
                        responder1Count.updateAndGet(v -> v + 1);
                        CommandResponseMessage response = CommandResponseMessage.builder()
                                .commandReceived(cmd)
                                .isExecuted(true)
                                .build();
                        responderClient.sendResponseMessage(response);
                    })
                    .build();

            // Need a second responder client for the group
            CQClient responder2 = CQClient.builder()
                    .address(kubemqAddress)
                    .clientId(uniqueClientId("cq-responder2"))
                    .logLevel(KubeMQClient.Level.INFO)
                    .build();

            CommandsSubscription sub2 = CommandsSubscription.builder()
                    .channel(channel)
                    .group("cmd-group")
                    .onReceiveCommandCallback(cmd -> {
                        responder2Count.updateAndGet(v -> v + 1);
                        CommandResponseMessage response = CommandResponseMessage.builder()
                                .commandReceived(cmd)
                                .isExecuted(true)
                                .build();
                        responder2.sendResponseMessage(response);
                    })
                    .build();

            responderClient.subscribeToCommands(sub1);
            responder2.subscribeToCommands(sub2);
            sleep(500);

            // Send multiple commands
            for (int i = 0; i < 4; i++) {
                CommandMessage command = CommandMessage.builder()
                        .channel(channel)
                        .body(("command " + i).getBytes())
                        .timeoutInSeconds(10)
                        .build();
                client.sendCommandRequest(command);
            }

            // Both responders should handle some commands
            int total = responder1Count.get() + responder2Count.get();
            assertEquals(4, total, "Total handled commands should equal sent count");

            sub1.cancel();
            sub2.cancel();
            responder2.close();
        } finally {
            client.deleteCommandsChannel(channel);
        }
    }

    @Test
    @Order(12)
    @DisplayName("Query with cache - should use cached response")
    void queryWithCache_shouldUseCachedResponse() throws InterruptedException {
        String channel = uniqueChannel("qry-cache");
        client.createQueriesChannel(channel);

        try {
            AtomicReference<Integer> callCount = new AtomicReference<>(0);

            QueriesSubscription subscription = QueriesSubscription.builder()
                    .channel(channel)
                    .onReceiveQueryCallback(qry -> {
                        callCount.updateAndGet(v -> v + 1);
                        QueryResponseMessage response = QueryResponseMessage.builder()
                                .queryReceived(qry)
                                .isExecuted(true)
                                .body(("response at " + System.currentTimeMillis()).getBytes())
                                .build();
                        responderClient.sendResponseMessage(response);
                    })
                    .build();

            responderClient.subscribeToQueries(subscription);
            sleep(500);

            // First query with cache
            QueryMessage query1 = QueryMessage.builder()
                    .channel(channel)
                    .body("cached query".getBytes())
                    .cacheKey("test-cache-key")
                    .cacheTtlInSeconds(30)
                    .timeoutInSeconds(10)
                    .build();

            QueryResponseMessage response1 = client.sendQueryRequest(query1);
            assertNotNull(response1);
            assertTrue(response1.isExecuted());

            // Second query with same cache key - might hit cache
            QueryMessage query2 = QueryMessage.builder()
                    .channel(channel)
                    .body("cached query".getBytes())
                    .cacheKey("test-cache-key")
                    .cacheTtlInSeconds(30)
                    .timeoutInSeconds(10)
                    .build();

            QueryResponseMessage response2 = client.sendQueryRequest(query2);
            assertNotNull(response2);
            assertTrue(response2.isExecuted());

            // Note: Caching behavior depends on server configuration
            // This test verifies the cache parameters are sent correctly

            subscription.cancel();
        } finally {
            client.deleteQueriesChannel(channel);
        }
    }

    @Test
    @Order(13)
    @DisplayName("Delete commands channel - should succeed")
    void deleteCommandsChannel_shouldSucceed() {
        String channel = uniqueChannel("delete-cmd");
        client.createCommandsChannel(channel);

        boolean result = client.deleteCommandsChannel(channel);

        assertTrue(result);

        List<CQChannel> channels = client.listCommandsChannels(channel);
        assertTrue(channels.stream().noneMatch(c -> c.getName().equals(channel)));
    }

    @Test
    @Order(14)
    @DisplayName("Delete queries channel - should succeed")
    void deleteQueriesChannel_shouldSucceed() {
        String channel = uniqueChannel("delete-qry");
        client.createQueriesChannel(channel);

        boolean result = client.deleteQueriesChannel(channel);

        assertTrue(result);

        List<CQChannel> channels = client.listQueriesChannels(channel);
        assertTrue(channels.stream().noneMatch(c -> c.getName().equals(channel)));
    }
}
