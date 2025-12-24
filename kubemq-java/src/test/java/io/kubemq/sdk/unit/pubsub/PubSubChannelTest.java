package io.kubemq.sdk.unit.pubsub;

import io.kubemq.sdk.pubsub.PubSubChannel;
import io.kubemq.sdk.pubsub.PubSubStats;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PubSubChannel POJO.
 */
class PubSubChannelTest {

    @Nested
    class BuilderTests {

        @Test
        void builder_withAllFields_createsChannel() {
            PubSubStats incoming = PubSubStats.builder().messages(100).volume(1000).build();
            PubSubStats outgoing = PubSubStats.builder().messages(80).volume(800).build();

            PubSubChannel channel = PubSubChannel.builder()
                    .name("events-channel")
                    .type("events")
                    .lastActivity(System.currentTimeMillis())
                    .isActive(true)
                    .incoming(incoming)
                    .outgoing(outgoing)
                    .build();

            assertEquals("events-channel", channel.getName());
            assertEquals("events", channel.getType());
            assertTrue(channel.getLastActivity() > 0);
            assertTrue(channel.getIsActive());
            assertNotNull(channel.getIncoming());
            assertNotNull(channel.getOutgoing());
        }

        @Test
        void builder_withMinimalFields_createsChannel() {
            PubSubChannel channel = PubSubChannel.builder()
                    .name("minimal-channel")
                    .build();

            assertEquals("minimal-channel", channel.getName());
            assertNull(channel.getType());
            assertEquals(0, channel.getLastActivity());
            assertFalse(channel.getIsActive());
            assertNull(channel.getIncoming());
            assertNull(channel.getOutgoing());
        }

        @Test
        void builder_withStats_setsStats() {
            PubSubStats stats = PubSubStats.builder()
                    .messages(50)
                    .volume(2500)
                    .waiting(5)
                    .expired(2)
                    .delayed(1)
                    .responses(10)
                    .build();

            PubSubChannel channel = PubSubChannel.builder()
                    .name("stats-channel")
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
            PubSubChannel channel = new PubSubChannel();
            channel.setName("updated-name");
            assertEquals("updated-name", channel.getName());
        }

        @Test
        void setType_updatesType() {
            PubSubChannel channel = new PubSubChannel();
            channel.setType("events_store");
            assertEquals("events_store", channel.getType());
        }

        @Test
        void setLastActivity_updatesLastActivity() {
            PubSubChannel channel = new PubSubChannel();
            channel.setLastActivity(1234567890L);
            assertEquals(1234567890L, channel.getLastActivity());
        }

        @Test
        void setActive_updatesActive() {
            PubSubChannel channel = new PubSubChannel();
            channel.setActive(true);
            assertTrue(channel.getIsActive());
        }

        @Test
        void setIncoming_updatesIncoming() {
            PubSubChannel channel = new PubSubChannel();
            PubSubStats stats = PubSubStats.builder().messages(10).build();
            channel.setIncoming(stats);
            assertEquals(10, channel.getIncoming().getMessages());
        }

        @Test
        void setOutgoing_updatesOutgoing() {
            PubSubChannel channel = new PubSubChannel();
            PubSubStats stats = PubSubStats.builder().messages(20).build();
            channel.setOutgoing(stats);
            assertEquals(20, channel.getOutgoing().getMessages());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toString_includesAllFields() {
            PubSubStats incoming = PubSubStats.builder().messages(100).build();
            PubSubStats outgoing = PubSubStats.builder().messages(80).build();

            PubSubChannel channel = PubSubChannel.builder()
                    .name("test-channel")
                    .type("events")
                    .lastActivity(1234567890L)
                    .isActive(true)
                    .incoming(incoming)
                    .outgoing(outgoing)
                    .build();

            String str = channel.toString();

            assertTrue(str.contains("test-channel"));
            assertTrue(str.contains("events"));
            assertTrue(str.contains("1234567890"));
            assertTrue(str.contains("isActive=true"));
        }

        @Test
        void toString_withNullStats_handlesGracefully() {
            PubSubChannel channel = PubSubChannel.builder()
                    .name("no-stats")
                    .type("events_store")
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
            PubSubChannel channel = new PubSubChannel();

            assertNull(channel.getName());
            assertNull(channel.getType());
            assertEquals(0, channel.getLastActivity());
            assertFalse(channel.getIsActive());
            assertNull(channel.getIncoming());
            assertNull(channel.getOutgoing());
        }
    }
}
