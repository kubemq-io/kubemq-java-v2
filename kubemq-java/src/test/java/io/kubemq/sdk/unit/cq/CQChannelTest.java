package io.kubemq.sdk.unit.cq;

import io.kubemq.sdk.cq.CQChannel;
import io.kubemq.sdk.cq.CQStats;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CQChannel POJO.
 */
class CQChannelTest {

    @Nested
    class BuilderTests {

        @Test
        void builder_withAllFields_createsChannel() {
            CQStats incoming = CQStats.builder().messages(100).volume(1000).responses(50).build();
            CQStats outgoing = CQStats.builder().messages(80).volume(800).responses(40).build();

            CQChannel channel = CQChannel.builder()
                    .name("commands-channel")
                    .type("commands")
                    .lastActivity(System.currentTimeMillis())
                    .isActive(true)
                    .incoming(incoming)
                    .outgoing(outgoing)
                    .build();

            assertEquals("commands-channel", channel.getName());
            assertEquals("commands", channel.getType());
            assertTrue(channel.getLastActivity() > 0);
            assertTrue(channel.getIsActive());
            assertNotNull(channel.getIncoming());
            assertNotNull(channel.getOutgoing());
        }

        @Test
        void builder_withMinimalFields_createsChannel() {
            CQChannel channel = CQChannel.builder()
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
        void builder_withInactiveChannel_createsInactiveChannel() {
            CQChannel channel = CQChannel.builder()
                    .name("inactive-channel")
                    .type("queries")
                    .isActive(false)
                    .build();

            assertFalse(channel.getIsActive());
        }
    }

    @Nested
    class GetterSetterTests {

        @Test
        void setName_updatesName() {
            CQChannel channel = new CQChannel();
            channel.setName("updated-name");
            assertEquals("updated-name", channel.getName());
        }

        @Test
        void setType_updatesType() {
            CQChannel channel = new CQChannel();
            channel.setType("queries");
            assertEquals("queries", channel.getType());
        }

        @Test
        void setLastActivity_updatesLastActivity() {
            CQChannel channel = new CQChannel();
            channel.setLastActivity(1234567890L);
            assertEquals(1234567890L, channel.getLastActivity());
        }

        @Test
        void setActive_updatesActive() {
            CQChannel channel = new CQChannel();
            channel.setActive(true);
            assertTrue(channel.getIsActive());
        }

        @Test
        void setIncoming_updatesIncoming() {
            CQChannel channel = new CQChannel();
            CQStats stats = CQStats.builder().messages(10).build();
            channel.setIncoming(stats);
            assertEquals(10, channel.getIncoming().getMessages());
        }

        @Test
        void setOutgoing_updatesOutgoing() {
            CQChannel channel = new CQChannel();
            CQStats stats = CQStats.builder().messages(20).build();
            channel.setOutgoing(stats);
            assertEquals(20, channel.getOutgoing().getMessages());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toString_includesAllFields() {
            CQStats incoming = CQStats.builder().messages(100).volume(1000).responses(50).build();
            CQStats outgoing = CQStats.builder().messages(80).volume(800).responses(40).build();

            CQChannel channel = CQChannel.builder()
                    .name("test-channel")
                    .type("commands")
                    .lastActivity(1234567890L)
                    .isActive(true)
                    .incoming(incoming)
                    .outgoing(outgoing)
                    .build();

            String str = channel.toString();

            assertTrue(str.contains("test-channel"));
            assertTrue(str.contains("commands"));
            assertTrue(str.contains("1234567890"));
            assertTrue(str.contains("is_active=true"));
        }

        @Test
        void toString_withNullStats_handlesGracefully() {
            CQChannel channel = CQChannel.builder()
                    .name("no-stats")
                    .type("queries")
                    .build();

            String str = channel.toString();

            assertNotNull(str);
            assertTrue(str.contains("no-stats"));
        }
    }
}
