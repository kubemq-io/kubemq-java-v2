package io.kubemq.sdk.unit.common;

import io.kubemq.sdk.common.ServerInfo;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ServerInfo POJO.
 */
class ServerInfoTest {

    @Nested
    class BuilderTests {

        @Test
        void builder_withAllFields_createsServerInfo() {
            ServerInfo info = ServerInfo.builder()
                    .host("kubemq-server-1")
                    .version("2.3.0")
                    .serverStartTime(1703462400L)
                    .serverUpTimeSeconds(3600L)
                    .build();

            assertEquals("kubemq-server-1", info.getHost());
            assertEquals("2.3.0", info.getVersion());
            assertEquals(1703462400L, info.getServerStartTime());
            assertEquals(3600L, info.getServerUpTimeSeconds());
        }

        @Test
        void builder_withMinimalFields_createsServerInfo() {
            ServerInfo info = ServerInfo.builder()
                    .host("localhost")
                    .build();

            assertEquals("localhost", info.getHost());
            assertNull(info.getVersion());
            assertEquals(0, info.getServerStartTime());
            assertEquals(0, info.getServerUpTimeSeconds());
        }

        @Test
        void builder_withZeroValues_createsServerInfo() {
            ServerInfo info = ServerInfo.builder()
                    .host("test-host")
                    .version("1.0.0")
                    .serverStartTime(0L)
                    .serverUpTimeSeconds(0L)
                    .build();

            assertEquals(0, info.getServerStartTime());
            assertEquals(0, info.getServerUpTimeSeconds());
        }

        @Test
        void builder_withLargeUptime_createsServerInfo() {
            // Server running for 1 year
            long oneYear = 365L * 24L * 60L * 60L;

            ServerInfo info = ServerInfo.builder()
                    .host("long-running-server")
                    .version("2.0.0")
                    .serverUpTimeSeconds(oneYear)
                    .build();

            assertEquals(oneYear, info.getServerUpTimeSeconds());
        }
    }

    @Nested
    class GetterSetterTests {

        @Test
        void setHost_updatesHost() {
            ServerInfo info = ServerInfo.builder().build();
            info.setHost("new-host");
            assertEquals("new-host", info.getHost());
        }

        @Test
        void setVersion_updatesVersion() {
            ServerInfo info = ServerInfo.builder().build();
            info.setVersion("3.0.0");
            assertEquals("3.0.0", info.getVersion());
        }

        @Test
        void setServerStartTime_updatesServerStartTime() {
            ServerInfo info = ServerInfo.builder().build();
            info.setServerStartTime(1234567890L);
            assertEquals(1234567890L, info.getServerStartTime());
        }

        @Test
        void setServerUpTimeSeconds_updatesServerUpTimeSeconds() {
            ServerInfo info = ServerInfo.builder().build();
            info.setServerUpTimeSeconds(7200L);
            assertEquals(7200L, info.getServerUpTimeSeconds());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toString_includesAllFields() {
            ServerInfo info = ServerInfo.builder()
                    .host("tostring-host")
                    .version("1.2.3")
                    .serverStartTime(1000L)
                    .serverUpTimeSeconds(500L)
                    .build();

            String str = info.toString();

            assertTrue(str.contains("tostring-host"));
            assertTrue(str.contains("1.2.3"));
            assertTrue(str.contains("1000"));
            assertTrue(str.contains("500"));
        }

        @Test
        void toString_withNullHost_handlesGracefully() {
            ServerInfo info = ServerInfo.builder()
                    .version("1.0.0")
                    .build();

            String str = info.toString();

            assertNotNull(str);
            assertTrue(str.contains("null") || str.contains("host"));
        }

        @Test
        void toString_withNullVersion_handlesGracefully() {
            ServerInfo info = ServerInfo.builder()
                    .host("test-host")
                    .build();

            String str = info.toString();

            assertNotNull(str);
            assertTrue(str.contains("test-host"));
        }
    }

    @Nested
    class VersionParsingTests {

        @Test
        void version_withSemanticVersioning_storesCorrectly() {
            ServerInfo info = ServerInfo.builder()
                    .host("test")
                    .version("2.3.4-beta.1")
                    .build();

            assertEquals("2.3.4-beta.1", info.getVersion());
        }

        @Test
        void version_withMajorVersionOnly_storesCorrectly() {
            ServerInfo info = ServerInfo.builder()
                    .host("test")
                    .version("3")
                    .build();

            assertEquals("3", info.getVersion());
        }

        @Test
        void version_withSpecialCharacters_storesCorrectly() {
            ServerInfo info = ServerInfo.builder()
                    .host("test")
                    .version("v2.0.0-rc1+build.123")
                    .build();

            assertEquals("v2.0.0-rc1+build.123", info.getVersion());
        }
    }

    @Nested
    class HostFormatTests {

        @Test
        void host_withPort_storesCorrectly() {
            ServerInfo info = ServerInfo.builder()
                    .host("192.168.1.100:50000")
                    .version("1.0.0")
                    .build();

            assertEquals("192.168.1.100:50000", info.getHost());
        }

        @Test
        void host_withHostname_storesCorrectly() {
            ServerInfo info = ServerInfo.builder()
                    .host("kubemq.production.cluster.local")
                    .version("1.0.0")
                    .build();

            assertEquals("kubemq.production.cluster.local", info.getHost());
        }

        @Test
        void host_withIPv6_storesCorrectly() {
            ServerInfo info = ServerInfo.builder()
                    .host("[::1]:50000")
                    .version("1.0.0")
                    .build();

            assertEquals("[::1]:50000", info.getHost());
        }
    }
}
