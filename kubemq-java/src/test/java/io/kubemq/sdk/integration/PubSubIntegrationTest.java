package io.kubemq.sdk.integration;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PubSubClient.
 * Requires a running KubeMQ server at localhost:50000 (or configured via KUBEMQ_ADDRESS).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PubSubIntegrationTest extends BaseIntegrationTest {

    private PubSubClient client;
    private String eventsChannel;
    private String eventsStoreChannel;

    @BeforeAll
    void setup() {
        eventsChannel = uniqueChannel("events-test");
        eventsStoreChannel = uniqueChannel("events-store-test");
        client = PubSubClient.builder()
                .address(kubemqAddress)
                .clientId(uniqueClientId("pubsub"))
                .logLevel(KubeMQClient.Level.INFO)
                .build();
    }

    @AfterAll
    void teardown() {
        if (client != null) {
            try {
                client.deleteEventsChannel(eventsChannel);
                client.deleteEventsStoreChannel(eventsStoreChannel);
            } catch (Exception ignored) {
            }
            client.close();
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
    @DisplayName("Create events channel - should succeed")
    void createEventsChannel_shouldSucceed() {
        boolean result = client.createEventsChannel(eventsChannel);

        assertTrue(result);
    }

    @Test
    @Order(3)
    @DisplayName("Create events store channel - should succeed")
    void createEventsStoreChannel_shouldSucceed() {
        boolean result = client.createEventsStoreChannel(eventsStoreChannel);

        assertTrue(result);
    }

    @Test
    @Order(4)
    @DisplayName("List events channels - should find created channel")
    void listEventsChannels_shouldFindCreatedChannel() {
        List<PubSubChannel> channels = client.listEventsChannels(eventsChannel);

        assertNotNull(channels);
        assertTrue(channels.stream().anyMatch(c -> c.getName().equals(eventsChannel)));
    }

    @Test
    @Order(5)
    @DisplayName("List events store channels - should find created channel")
    void listEventsStoreChannels_shouldFindCreatedChannel() {
        List<PubSubChannel> channels = client.listEventsStoreChannels(eventsStoreChannel);

        assertNotNull(channels);
        assertTrue(channels.stream().anyMatch(c -> c.getName().equals(eventsStoreChannel)));
    }

    @Test
    @Order(6)
    @DisplayName("Send and receive event - should deliver message")
    void sendAndReceiveEvent_shouldDeliverMessage() throws InterruptedException {
        String channel = uniqueChannel("event-delivery");
        client.createEventsChannel(channel);

        try {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<EventMessageReceived> receivedEvent = new AtomicReference<>();
            AtomicReference<String> errorMessage = new AtomicReference<>();

            // Subscribe first
            EventsSubscription subscription = EventsSubscription.builder()
                    .channel(channel)
                    .onReceiveEventCallback(event -> {
                        receivedEvent.set(event);
                        latch.countDown();
                    })
                    .onErrorCallback(errorMessage::set)
                    .build();

            client.subscribeToEvents(subscription);

            // Give subscription time to establish
            sleep(500);

            // Send event
            String body = "test event " + System.currentTimeMillis();
            EventMessage message = EventMessage.builder()
                    .channel(channel)
                    .body(body.getBytes())
                    .metadata("test-metadata")
                    .build();

            client.sendEventsMessage(message);

            // Wait for message
            boolean received = latch.await(5, TimeUnit.SECONDS);

            assertTrue(received, "Should have received the event");
            assertNotNull(receivedEvent.get());
            assertEquals(body, new String(receivedEvent.get().getBody()));
            assertEquals("test-metadata", receivedEvent.get().getMetadata());
            assertNull(errorMessage.get(), "Should not have errors");

            subscription.cancel();
        } finally {
            client.deleteEventsChannel(channel);
        }
    }

    @Test
    @Order(7)
    @DisplayName("Send event store message - should return result")
    void sendEventStoreMessage_shouldReturnResult() {
        String channel = uniqueChannel("store-send");
        client.createEventsStoreChannel(channel);

        try {
            EventStoreMessage message = EventStoreMessage.builder()
                    .channel(channel)
                    .body("store test".getBytes())
                    .build();

            EventSendResult result = client.sendEventsStoreMessage(message);

            assertNotNull(result);
            assertNotNull(result.getId());
            assertTrue(result.getError() == null || result.getError().isEmpty(), "Send should not have error: " + result.getError());
            assertTrue(result.isSent());
        } finally {
            client.deleteEventsStoreChannel(channel);
        }
    }

    @Test
    @Order(8)
    @DisplayName("Events store subscription - should receive stored messages")
    void eventsStoreSubscription_shouldReceiveStoredMessages() throws InterruptedException {
        String channel = uniqueChannel("store-sub");
        client.createEventsStoreChannel(channel);

        try {
            // First send some messages to store
            for (int i = 0; i < 3; i++) {
                EventStoreMessage message = EventStoreMessage.builder()
                        .channel(channel)
                        .body(("stored message " + i).getBytes())
                        .build();
                client.sendEventsStoreMessage(message);
            }

            // Give time for messages to be stored
            sleep(500);

            // Now subscribe from beginning
            CountDownLatch latch = new CountDownLatch(3);
            AtomicReference<String> errorMessage = new AtomicReference<>();

            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel(channel)
                    .eventsStoreType(EventsStoreType.StartFromFirst)
                    .onReceiveEventCallback(event -> {
                        latch.countDown();
                    })
                    .onErrorCallback(errorMessage::set)
                    .build();

            client.subscribeToEventsStore(subscription);

            // Wait for messages
            boolean received = latch.await(5, TimeUnit.SECONDS);

            assertTrue(received, "Should have received stored events");
            assertNull(errorMessage.get(), "Should not have errors");

            subscription.cancel();
        } finally {
            client.deleteEventsStoreChannel(channel);
        }
    }

    @Test
    @Order(9)
    @DisplayName("Event with tags - should preserve tags")
    void eventWithTags_shouldPreserveTags() throws InterruptedException {
        String channel = uniqueChannel("event-tags");
        client.createEventsChannel(channel);

        try {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<EventMessageReceived> receivedEvent = new AtomicReference<>();

            EventsSubscription subscription = EventsSubscription.builder()
                    .channel(channel)
                    .onReceiveEventCallback(event -> {
                        receivedEvent.set(event);
                        latch.countDown();
                    })
                    .build();

            client.subscribeToEvents(subscription);
            sleep(500);

            Map<String, String> tags = new HashMap<>();
            tags.put("env", "test");
            tags.put("priority", "high");
            EventMessage message = EventMessage.builder()
                    .channel(channel)
                    .body("tagged event".getBytes())
                    .tags(tags)
                    .build();

            client.sendEventsMessage(message);

            boolean received = latch.await(5, TimeUnit.SECONDS);

            assertTrue(received);
            assertNotNull(receivedEvent.get());
            assertEquals("test", receivedEvent.get().getTags().get("env"));
            assertEquals("high", receivedEvent.get().getTags().get("priority"));

            subscription.cancel();
        } finally {
            client.deleteEventsChannel(channel);
        }
    }

    @Test
    @Order(10)
    @DisplayName("Group subscription - should load balance between subscribers")
    void groupSubscription_shouldLoadBalance() throws InterruptedException {
        String channel = uniqueChannel("group-test");
        client.createEventsChannel(channel);

        try {
            CountDownLatch latch = new CountDownLatch(5);
            AtomicReference<Integer> subscriber1Count = new AtomicReference<>(0);
            AtomicReference<Integer> subscriber2Count = new AtomicReference<>(0);

            // Create two subscribers in the same group
            EventsSubscription sub1 = EventsSubscription.builder()
                    .channel(channel)
                    .group("test-group")
                    .onReceiveEventCallback(event -> {
                        subscriber1Count.updateAndGet(v -> v + 1);
                        latch.countDown();
                    })
                    .build();

            EventsSubscription sub2 = EventsSubscription.builder()
                    .channel(channel)
                    .group("test-group")
                    .onReceiveEventCallback(event -> {
                        subscriber2Count.updateAndGet(v -> v + 1);
                        latch.countDown();
                    })
                    .build();

            client.subscribeToEvents(sub1);
            client.subscribeToEvents(sub2);
            sleep(500);

            // Send multiple messages
            for (int i = 0; i < 5; i++) {
                EventMessage message = EventMessage.builder()
                        .channel(channel)
                        .body(("message " + i).getBytes())
                        .build();
                client.sendEventsMessage(message);
                sleep(50); // Small delay between sends
            }

            boolean received = latch.await(10, TimeUnit.SECONDS);

            assertTrue(received, "All messages should be received");
            // With load balancing, both subscribers should get some messages
            int total = subscriber1Count.get() + subscriber2Count.get();
            assertEquals(5, total, "Total messages should equal sent count");

            sub1.cancel();
            sub2.cancel();
        } finally {
            client.deleteEventsChannel(channel);
        }
    }

    @Test
    @Order(11)
    @DisplayName("Events store from sequence - should start from specific sequence")
    void eventsStoreFromSequence_shouldStartFromSequence() throws InterruptedException {
        String channel = uniqueChannel("store-seq");
        client.createEventsStoreChannel(channel);

        try {
            // Send 5 messages
            for (int i = 0; i < 5; i++) {
                EventStoreMessage message = EventStoreMessage.builder()
                        .channel(channel)
                        .body(("seq message " + i).getBytes())
                        .build();
                client.sendEventsStoreMessage(message);
            }

            sleep(500);

            // Subscribe from sequence 3 (should get messages 3, 4, 5)
            CountDownLatch latch = new CountDownLatch(3);
            AtomicReference<Integer> receivedCount = new AtomicReference<>(0);

            EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                    .channel(channel)
                    .eventsStoreType(EventsStoreType.StartAtSequence)
                    .eventsStoreSequenceValue(3)
                    .onReceiveEventCallback(event -> {
                        receivedCount.updateAndGet(v -> v + 1);
                        latch.countDown();
                    })
                    .build();

            client.subscribeToEventsStore(subscription);

            boolean received = latch.await(5, TimeUnit.SECONDS);

            assertTrue(received, "Should receive messages from sequence 3 onwards");
            assertTrue(receivedCount.get() >= 3, "Should receive at least 3 messages");

            subscription.cancel();
        } finally {
            client.deleteEventsStoreChannel(channel);
        }
    }

    @Test
    @Order(12)
    @DisplayName("Delete events channel - should succeed")
    void deleteEventsChannel_shouldSucceed() {
        String channel = uniqueChannel("delete-events");
        client.createEventsChannel(channel);

        boolean result = client.deleteEventsChannel(channel);

        assertTrue(result);

        // Verify channel is gone
        List<PubSubChannel> channels = client.listEventsChannels(channel);
        assertTrue(channels.stream().noneMatch(c -> c.getName().equals(channel)));
    }

    @Test
    @Order(13)
    @DisplayName("Delete events store channel - should succeed")
    void deleteEventsStoreChannel_shouldSucceed() {
        String channel = uniqueChannel("delete-store");
        client.createEventsStoreChannel(channel);

        boolean result = client.deleteEventsStoreChannel(channel);

        assertTrue(result);

        // Verify channel is gone
        List<PubSubChannel> channels = client.listEventsStoreChannels(channel);
        assertTrue(channels.stream().noneMatch(c -> c.getName().equals(channel)));
    }

    @Test
    @Order(14)
    @DisplayName("Fire and forget event - should not block")
    void fireAndForgetEvent_shouldNotBlock() {
        String channel = uniqueChannel("fire-forget");
        client.createEventsChannel(channel);

        try {
            long start = System.currentTimeMillis();

            // Send multiple events without waiting for confirmation
            for (int i = 0; i < 10; i++) {
                EventMessage message = EventMessage.builder()
                        .channel(channel)
                        .body(("fire and forget " + i).getBytes())
                        .build();
                client.sendEventsMessage(message);
            }

            long elapsed = System.currentTimeMillis() - start;

            // Fire and forget should be very fast
            assertTrue(elapsed < 1000, "Fire and forget should complete quickly");
        } finally {
            client.deleteEventsChannel(channel);
        }
    }
}
