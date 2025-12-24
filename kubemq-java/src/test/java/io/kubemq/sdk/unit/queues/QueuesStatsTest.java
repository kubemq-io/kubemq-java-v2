package io.kubemq.sdk.unit.queues;

import io.kubemq.sdk.queues.QueuesStats;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueuesStats POJO.
 */
class QueuesStatsTest {

    @Nested
    class BuilderTests {

        @Test
        void builder_withAllFields_createsStats() {
            QueuesStats stats = QueuesStats.builder()
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
            QueuesStats stats = QueuesStats.builder()
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
            QueuesStats stats = QueuesStats.builder().build();

            assertEquals(0, stats.getMessages());
            assertEquals(0, stats.getVolume());
            assertEquals(0, stats.getWaiting());
        }
    }

    @Nested
    class GetterSetterTests {

        @Test
        void setMessages_updatesMessages() {
            QueuesStats stats = new QueuesStats();
            stats.setMessages(50);
            assertEquals(50, stats.getMessages());
        }

        @Test
        void setVolume_updatesVolume() {
            QueuesStats stats = new QueuesStats();
            stats.setVolume(2500);
            assertEquals(2500, stats.getVolume());
        }

        @Test
        void setWaiting_updatesWaiting() {
            QueuesStats stats = new QueuesStats();
            stats.setWaiting(5);
            assertEquals(5, stats.getWaiting());
        }

        @Test
        void setExpired_updatesExpired() {
            QueuesStats stats = new QueuesStats();
            stats.setExpired(2);
            assertEquals(2, stats.getExpired());
        }

        @Test
        void setDelayed_updatesDelayed() {
            QueuesStats stats = new QueuesStats();
            stats.setDelayed(1);
            assertEquals(1, stats.getDelayed());
        }

        @Test
        void setResponses_updatesResponses() {
            QueuesStats stats = new QueuesStats();
            stats.setResponses(40);
            assertEquals(40, stats.getResponses());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toString_includesAllFields() {
            QueuesStats stats = QueuesStats.builder()
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
            QueuesStats stats = QueuesStats.builder().build();

            String str = stats.toString();

            assertTrue(str.contains("messages=0"));
            assertTrue(str.contains("volume=0"));
        }
    }

    @Nested
    class NoArgsConstructorTests {

        @Test
        void noArgsConstructor_createsInstanceWithDefaults() {
            QueuesStats stats = new QueuesStats();

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
            QueuesStats stats = new QueuesStats(100, 5000, 10, 5, 3, 80);

            assertEquals(100, stats.getMessages());
            assertEquals(5000, stats.getVolume());
            assertEquals(10, stats.getWaiting());
            assertEquals(5, stats.getExpired());
            assertEquals(3, stats.getDelayed());
            assertEquals(80, stats.getResponses());
        }
    }
}
