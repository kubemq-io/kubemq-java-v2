package io.kubemq.sdk.unit.client;

import io.kubemq.sdk.client.ConnectionState;
import io.kubemq.sdk.client.ConnectionStateListener;
import io.kubemq.sdk.client.ReconnectionConfig;
import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.exception.ClientClosedException;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.pubsub.PubSubClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionConfigTest {

    @Nested
    class DefaultAddressTests {

        @Test
        void nullAddress_usesDefault() {
            CQClient client = CQClient.builder()
                .address(null)
                .clientId("test")
                .build();
            assertEquals("localhost:50000", client.getAddress());
            client.close();
        }

        @Test
        void emptyAddress_usesDefault() {
            CQClient client = CQClient.builder()
                .address("")
                .clientId("test")
                .build();
            assertEquals("localhost:50000", client.getAddress());
            client.close();
        }

        @Test
        void customAddress_isRespected() {
            CQClient client = CQClient.builder()
                .address("myserver:9090")
                .clientId("test")
                .tls(false)
                .build();
            assertEquals("myserver:9090", client.getAddress());
            client.close();
        }
    }

    @Nested
    class FailFastValidationTests {

        @Test
        void nullClientId_getsDefaultId() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId(null)
                    .build();
            assertNotNull(client.getClientId());
            assertTrue(client.getClientId().startsWith("kubemq-client-"));
            client.close();
        }

        @Test
        void emptyClientId_getsDefaultId() {
            CQClient client = CQClient.builder()
                    .address("localhost:50000")
                    .clientId("")
                    .build();
            assertNotNull(client.getClientId());
            assertTrue(client.getClientId().startsWith("kubemq-client-"));
            client.close();
        }

        @Test
        void negativePingInterval_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () ->
                CQClient.builder()
                    .address("localhost:50000")
                    .clientId("test")
                    .pingIntervalInSeconds(-1)
                    .build()
            );
        }

        @Test
        void negativePingTimeout_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () ->
                CQClient.builder()
                    .address("localhost:50000")
                    .clientId("test")
                    .pingTimeoutInSeconds(-1)
                    .build()
            );
        }
    }

    @Nested
    class ConnectionStateTests {

        @Test
        void getConnectionState_returnsReady() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("test")
                .build();
            assertEquals(ConnectionState.READY, client.getConnectionState());
            client.close();
        }

        @Test
        void afterClose_stateIsClosed() throws InterruptedException {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("test")
                .build();
            client.close();
            Thread.sleep(100);
            assertEquals(ConnectionState.CLOSED, client.getConnectionState());
        }
    }

    @Nested
    class GracefulShutdownTests {

        @Test
        void close_isIdempotent() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("test")
                .build();
            client.close();
            assertDoesNotThrow(client::close);
        }

        @Test
        void operationAfterClose_throwsClientClosedException() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("test")
                .build();
            client.close();
            assertThrows(ClientClosedException.class, () ->
                client.sendCommandRequest(null)
            );
        }

        @Test
        void isClosed_returnsTrueAfterClose() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("test")
                .build();
            assertFalse(client.isClosed());
            client.close();
            assertTrue(client.isClosed());
        }
    }

    @Nested
    class ConnectionStateListenerTests {

        @Test
        void listener_receivesOnConnected() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean connected = new AtomicBoolean(false);

            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("test")
                .connectionStateListener(new ConnectionStateListener() {
                    @Override
                    public void onConnected() {
                        connected.set(true);
                        latch.countDown();
                    }
                })
                .build();

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertTrue(connected.get());
            client.close();
        }

        @Test
        void listener_receivesOnClosed() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean closed = new AtomicBoolean(false);

            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("test")
                .connectionStateListener(new ConnectionStateListener() {
                    @Override
                    public void onClosed() {
                        closed.set(true);
                        latch.countDown();
                    }
                })
                .build();

            client.close();
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertTrue(closed.get());
        }

        @Test
        void addConnectionStateListener_afterConstruction() throws InterruptedException {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("test")
                .build();

            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean closed = new AtomicBoolean(false);

            client.addConnectionStateListener(new ConnectionStateListener() {
                @Override
                public void onClosed() {
                    closed.set(true);
                    latch.countDown();
                }
            });

            client.close();
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertTrue(closed.get());
        }
    }

    @Nested
    class ReconnectionConfigTests {

        @Test
        void customReconnectionConfig_isAccepted() {
            ReconnectionConfig config = ReconnectionConfig.builder()
                .maxReconnectAttempts(3)
                .initialReconnectDelayMs(1000)
                .build();

            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("test")
                .reconnectionConfig(config)
                .build();

            assertNotNull(client);
            client.close();
        }

        @Test
        void defaultReconnectionConfig_isCreated() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("test")
                .build();

            assertNotNull(client.getReconnectionConfig());
            assertEquals(-1, client.getReconnectionConfig().getMaxReconnectAttempts());
            client.close();
        }
    }

    @Nested
    class WaitForReadyTests {

        @Test
        void defaultWaitForReady_isTrue() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("test")
                .build();
            assertTrue(client.isWaitForReady());
            client.close();
        }

        @Test
        void waitForReady_canBeDisabled() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("test")
                .waitForReady(false)
                .build();
            assertFalse(client.isWaitForReady());
            client.close();
        }
    }

    @Nested
    class ShutdownTimeoutTests {

        @Test
        void defaultShutdownTimeout_isFive() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("test")
                .build();
            assertEquals(5, client.getShutdownTimeoutSeconds());
            client.close();
        }

        @Test
        void customShutdownTimeout_isRespected() {
            CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("test")
                .shutdownTimeoutSeconds(10)
                .build();
            assertEquals(10, client.getShutdownTimeoutSeconds());
            client.close();
        }
    }

    @Nested
    class AllClientTypesTests {

        @Test
        void pubSubClient_supportsNewParameters() {
            PubSubClient client = PubSubClient.builder()
                .address("localhost:50000")
                .clientId("test")
                .reconnectionConfig(ReconnectionConfig.builder().build())
                .shutdownTimeoutSeconds(10)
                .waitForReady(true)
                .build();
            assertNotNull(client);
            assertEquals(ConnectionState.READY, client.getConnectionState());
            client.close();
        }

        @Test
        void queuesClient_supportsNewParameters() {
            QueuesClient client = QueuesClient.builder()
                .address("localhost:50000")
                .clientId("test")
                .reconnectionConfig(ReconnectionConfig.builder().build())
                .shutdownTimeoutSeconds(10)
                .build();
            assertNotNull(client);
            assertEquals(ConnectionState.READY, client.getConnectionState());
            client.close();
        }
    }
}
