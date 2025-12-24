package io.kubemq.sdk.unit.cq;

import io.kubemq.sdk.cq.CQStats;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CQStats POJO.
 */
class CQStatsTest {

    @Nested
    class BuilderTests {

        @Test
        void builder_withAllFields_createsStats() {
            CQStats stats = CQStats.builder()
                    .messages(100)
                    .volume(5000)
                    .responses(80)
                    .waiting(10)
                    .expired(5)
                    .delayed(3)
                    .build();

            assertEquals(100, stats.getMessages());
            assertEquals(5000, stats.getVolume());
            assertEquals(80, stats.getResponses());
            assertEquals(10, stats.getWaiting());
            assertEquals(5, stats.getExpired());
            assertEquals(3, stats.getDelayed());
        }

        @Test
        void builder_withZeroValues_createsStats() {
            CQStats stats = CQStats.builder()
                    .messages(0)
                    .volume(0)
                    .responses(0)
                    .waiting(0)
                    .expired(0)
                    .delayed(0)
                    .build();

            assertEquals(0, stats.getMessages());
            assertEquals(0, stats.getVolume());
            assertEquals(0, stats.getResponses());
            assertEquals(0, stats.getWaiting());
            assertEquals(0, stats.getExpired());
            assertEquals(0, stats.getDelayed());
        }

        @Test
        void builder_withDefaultValues_hasZeros() {
            CQStats stats = CQStats.builder().build();

            assertEquals(0, stats.getMessages());
            assertEquals(0, stats.getVolume());
            assertEquals(0, stats.getResponses());
        }
    }

    @Nested
    class GetterSetterTests {

        @Test
        void setMessages_updatesMessages() {
            CQStats stats = new CQStats();
            stats.setMessages(50);
            assertEquals(50, stats.getMessages());
        }

        @Test
        void setVolume_updatesVolume() {
            CQStats stats = new CQStats();
            stats.setVolume(2500);
            assertEquals(2500, stats.getVolume());
        }

        @Test
        void setResponses_updatesResponses() {
            CQStats stats = new CQStats();
            stats.setResponses(40);
            assertEquals(40, stats.getResponses());
        }

        @Test
        void setWaiting_updatesWaiting() {
            CQStats stats = new CQStats();
            stats.setWaiting(5);
            assertEquals(5, stats.getWaiting());
        }

        @Test
        void setExpired_updatesExpired() {
            CQStats stats = new CQStats();
            stats.setExpired(2);
            assertEquals(2, stats.getExpired());
        }

        @Test
        void setDelayed_updatesDelayed() {
            CQStats stats = new CQStats();
            stats.setDelayed(1);
            assertEquals(1, stats.getDelayed());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toString_includesAllFields() {
            CQStats stats = CQStats.builder()
                    .messages(100)
                    .volume(5000)
                    .responses(80)
                    .build();

            String str = stats.toString();

            assertTrue(str.contains("100"));
            assertTrue(str.contains("5000"));
            assertTrue(str.contains("80"));
        }

        @Test
        void toString_withZeroValues_showsZeros() {
            CQStats stats = CQStats.builder().build();

            String str = stats.toString();

            assertTrue(str.contains("messages=0"));
            assertTrue(str.contains("volume=0"));
            assertTrue(str.contains("responses=0"));
        }
    }

    @Nested
    class NoArgsConstructorTests {

        @Test
        void noArgsConstructor_createsInstanceWithDefaults() {
            CQStats stats = new CQStats();

            assertEquals(0, stats.getMessages());
            assertEquals(0, stats.getVolume());
            assertEquals(0, stats.getResponses());
            assertEquals(0, stats.getWaiting());
            assertEquals(0, stats.getExpired());
            assertEquals(0, stats.getDelayed());
        }
    }

    @Nested
    class AllArgsConstructorTests {

        @Test
        void allArgsConstructor_setsAllFields() {
            CQStats stats = new CQStats(100, 5000, 80, 10, 5, 3);

            assertEquals(100, stats.getMessages());
            assertEquals(5000, stats.getVolume());
            assertEquals(80, stats.getResponses());
            assertEquals(10, stats.getWaiting());
            assertEquals(5, stats.getExpired());
            assertEquals(3, stats.getDelayed());
        }
    }
}
