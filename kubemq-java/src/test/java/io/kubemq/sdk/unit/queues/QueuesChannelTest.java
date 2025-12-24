package io.kubemq.sdk.unit.queues;

import io.kubemq.sdk.queues.QueuesChannel;
import io.kubemq.sdk.queues.QueuesStats;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueuesChannel POJO.
 */
class QueuesChannelTest {

    @Nested
    class BuilderTests {

        @Test
        void builder_withAllFields_createsChannel() {
            QueuesStats incoming = QueuesStats.builder().messages(100).volume(1000).build();
            QueuesStats outgoing = QueuesStats.builder().messages(80).volume(800).build();

            QueuesChannel channel = QueuesChannel.builder()
                    .name("orders-queue")
                    .type("queues")
                    .lastActivity(System.currentTimeMillis())
                    .isActive(true)
                    .incoming(incoming)
                    .outgoing(outgoing)
                    .build();

            assertEquals("orders-queue", channel.getName());
            assertEquals("queues", channel.getType());
            assertTrue(channel.getLastActivity() > 0);
            assertTrue(channel.getIsActive());
            assertNotNull(channel.getIncoming());
            assertNotNull(channel.getOutgoing());
        }

        @Test
        void builder_withMinimalFields_createsChannel() {
            QueuesChannel channel = QueuesChannel.builder()
                    .name("minimal-queue")
                    .build();

            assertEquals("minimal-queue", channel.getName());
            assertNull(channel.getType());
            assertEquals(0, channel.getLastActivity());
            assertFalse(channel.getIsActive());
            assertNull(channel.getIncoming());
            assertNull(channel.getOutgoing());
        }

        @Test
        void builder_withStats_setsStats() {
            QueuesStats stats = QueuesStats.builder()
                    .messages(50)
                    .volume(2500)
                    .waiting(5)
                    .expired(2)
                    .delayed(1)
                    .build();

            QueuesChannel channel = QueuesChannel.builder()
                    .name("stats-queue")
                    .incoming(stats)
                    .build();

            assertEquals(50, channel.getIncoming().getMessages());
            assertEquals(2500, channel.getIncoming().getVolume());
        }
    }

    @Nested
    class GetterSetterTests {

        @Test
        void setName_updatesName() {
            QueuesChannel channel = new QueuesChannel();
            channel.setName("updated-name");
            assertEquals("updated-name", channel.getName());
        }

        @Test
        void setType_updatesType() {
            QueuesChannel channel = new QueuesChannel();
            channel.setType("queues");
            assertEquals("queues", channel.getType());
        }

        @Test
        void setLastActivity_updatesLastActivity() {
            QueuesChannel channel = new QueuesChannel();
            channel.setLastActivity(1234567890L);
            assertEquals(1234567890L, channel.getLastActivity());
        }

        @Test
        void setActive_updatesActive() {
            QueuesChannel channel = new QueuesChannel();
            channel.setActive(true);
            assertTrue(channel.getIsActive());
        }

        @Test
        void setIncoming_updatesIncoming() {
            QueuesChannel channel = new QueuesChannel();
            QueuesStats stats = QueuesStats.builder().messages(10).build();
            channel.setIncoming(stats);
            assertEquals(10, channel.getIncoming().getMessages());
        }

        @Test
        void setOutgoing_updatesOutgoing() {
            QueuesChannel channel = new QueuesChannel();
            QueuesStats stats = QueuesStats.builder().messages(20).build();
            channel.setOutgoing(stats);
            assertEquals(20, channel.getOutgoing().getMessages());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toString_includesAllFields() {
            QueuesStats incoming = QueuesStats.builder().messages(100).build();
            QueuesStats outgoing = QueuesStats.builder().messages(80).build();

            QueuesChannel channel = QueuesChannel.builder()
                    .name("test-queue")
                    .type("queues")
                    .lastActivity(1234567890L)
                    .isActive(true)
                    .incoming(incoming)
                    .outgoing(outgoing)
                    .build();

            String str = channel.toString();

            assertTrue(str.contains("test-queue"));
            assertTrue(str.contains("queues"));
            assertTrue(str.contains("1234567890"));
            assertTrue(str.contains("is_active=true"));
        }

        @Test
        void toString_withNullStats_handlesGracefully() {
            QueuesChannel channel = QueuesChannel.builder()
                    .name("no-stats")
                    .type("queues")
                    .build();

            String str = channel.toString();

            assertNotNull(str);
            assertTrue(str.contains("no-stats"));
        }
    }

    @Nested
    class NoArgsConstructorTests {

        @Test
        void noArgsConstructor_createsInstanceWithDefaults() {
            QueuesChannel channel = new QueuesChannel();

            assertNull(channel.getName());
            assertNull(channel.getType());
            assertEquals(0, channel.getLastActivity());
            assertFalse(channel.getIsActive());
            assertNull(channel.getIncoming());
            assertNull(channel.getOutgoing());
        }
    }
}
