package io.kubemq.sdk.unit.pubsub;

import io.kubemq.sdk.pubsub.PubSubStats;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PubSubStats POJO.
 */
class PubSubStatsTest {

    @Nested
    class BuilderTests {

        @Test
        void builder_withAllFields_createsStats() {
            PubSubStats stats = PubSubStats.builder()
                    .messages(100)
                    .volume(5000)
                    .waiting(10)
                    .expired(5)
                    .delayed(3)
                    .responses(80)
                    .build();

            assertEquals(100, stats.getMessages());
            assertEquals(5000, stats.getVolume());
            assertEquals(10, stats.getWaiting());
            assertEquals(5, stats.getExpired());
            assertEquals(3, stats.getDelayed());
            assertEquals(80, stats.getResponses());
        }

        @Test
        void builder_withZeroValues_createsStats() {
            PubSubStats stats = PubSubStats.builder()
                    .messages(0)
                    .volume(0)
                    .waiting(0)
                    .expired(0)
                    .delayed(0)
                    .responses(0)
                    .build();

            assertEquals(0, stats.getMessages());
            assertEquals(0, stats.getVolume());
            assertEquals(0, stats.getWaiting());
            assertEquals(0, stats.getExpired());
            assertEquals(0, stats.getDelayed());
            assertEquals(0, stats.getResponses());
        }

        @Test
        void builder_withDefaultValues_hasZeros() {
            PubSubStats stats = PubSubStats.builder().build();

            assertEquals(0, stats.getMessages());
            assertEquals(0, stats.getVolume());
            assertEquals(0, stats.getResponses());
        }
    }

    @Nested
    class GetterSetterTests {

        @Test
        void setMessages_updatesMessages() {
            PubSubStats stats = new PubSubStats();
            stats.setMessages(50);
            assertEquals(50, stats.getMessages());
        }

        @Test
        void setVolume_updatesVolume() {
            PubSubStats stats = new PubSubStats();
            stats.setVolume(2500);
            assertEquals(2500, stats.getVolume());
        }

        @Test
        void setWaiting_updatesWaiting() {
            PubSubStats stats = new PubSubStats();
            stats.setWaiting(5);
            assertEquals(5, stats.getWaiting());
        }

        @Test
        void setExpired_updatesExpired() {
            PubSubStats stats = new PubSubStats();
            stats.setExpired(2);
            assertEquals(2, stats.getExpired());
        }

        @Test
        void setDelayed_updatesDelayed() {
            PubSubStats stats = new PubSubStats();
            stats.setDelayed(1);
            assertEquals(1, stats.getDelayed());
        }

        @Test
        void setResponses_updatesResponses() {
            PubSubStats stats = new PubSubStats();
            stats.setResponses(40);
            assertEquals(40, stats.getResponses());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toString_includesAllFields() {
            PubSubStats stats = PubSubStats.builder()
                    .messages(100)
                    .volume(5000)
                    .waiting(10)
                    .expired(5)
                    .delayed(3)
                    .responses(80)
                    .build();

            String str = stats.toString();

            assertTrue(str.contains("messages=100"));
            assertTrue(str.contains("volume=5000"));
            assertTrue(str.contains("waiting=10"));
            assertTrue(str.contains("expired=5"));
            assertTrue(str.contains("delayed=3"));
            assertTrue(str.contains("responses=80"));
        }

        @Test
        void toString_withZeroValues_showsZeros() {
            PubSubStats stats = PubSubStats.builder().build();

            String str = stats.toString();

            assertTrue(str.contains("messages=0"));
            assertTrue(str.contains("volume=0"));
        }
    }

    @Nested
    class NoArgsConstructorTests {

        @Test
        void noArgsConstructor_createsInstanceWithDefaults() {
            PubSubStats stats = new PubSubStats();

            assertEquals(0, stats.getMessages());
            assertEquals(0, stats.getVolume());
            assertEquals(0, stats.getWaiting());
            assertEquals(0, stats.getExpired());
            assertEquals(0, stats.getDelayed());
            assertEquals(0, stats.getResponses());
        }
    }

    @Nested
    class AllArgsConstructorTests {

        @Test
        void allArgsConstructor_setsAllFields() {
            PubSubStats stats = new PubSubStats(100, 5000, 10, 5, 3, 80);

            assertEquals(100, stats.getMessages());
            assertEquals(5000, stats.getVolume());
            assertEquals(10, stats.getWaiting());
            assertEquals(5, stats.getExpired());
            assertEquals(3, stats.getDelayed());
            assertEquals(80, stats.getResponses());
        }
    }
}
