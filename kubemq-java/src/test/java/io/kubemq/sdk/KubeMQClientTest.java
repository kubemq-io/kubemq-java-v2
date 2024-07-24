package io.kubemq.sdk;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import kubemq.Kubemq;
import kubemq.kubemqGrpc;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KubeMQClientTest {

    @Mock
    private kubemqGrpc.kubemqBlockingStub blockingStubMock;

    @Mock
    private ManagedChannel managedChannelMock;

    @InjectMocks
    private KubeMQClient kubeMQClient =new KubeMQClient("localhost", "testClient", "authToken", false, null, null, 0, false, 0, 0, KubeMQClient.Level.INFO);

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        kubeMQClient =new KubeMQClient("localhost", "testClient", "authToken", false, null, null, 0, false, 0, 0, KubeMQClient.Level.INFO);

        kubeMQClient.setBlockingStub(blockingStubMock);
        kubeMQClient.setManagedChannel(managedChannelMock);
    }

    @Test
    @Order(1)
    public void testPing_Success() {
        log.info("Starting test: testPing_Success "+blockingStubMock+" "+managedChannelMock);
        Kubemq.PingResult pingResultMock = Kubemq.PingResult.newBuilder().setHost("localhost").build();
        when(blockingStubMock.ping(null)).thenReturn(pingResultMock);

        ServerInfo result = kubeMQClient.ping();

        assertEquals(pingResultMock.getHost(), result.getHost());
        verify(blockingStubMock, times(1)).ping(null);
        log.info("Finished test: testPing_Success");
    }

    @Test
    @Order(2)
    public void testPing_Failure() {
        log.info("Starting test: testPing_Failure");
        when(blockingStubMock.ping(null)).thenThrow(new StatusRuntimeException(io.grpc.Status.UNAVAILABLE));

        Exception exception = assertThrows(RuntimeException.class, () -> kubeMQClient.ping());

        assertInstanceOf(StatusRuntimeException.class, exception.getCause());
        verify(blockingStubMock, times(1)).ping(null);
        log.info("Finished test: testPing_Failure");
    }

    @Test
    @Order(4)
    public void testGetClientId() {
        log.info("Starting test: testGetClientId");
        String clientId = kubeMQClient.getClientId();
        assertNotNull(clientId);
        assertEquals("testClient", clientId);
        log.info("Finished test: testGetClientId");
    }

    @Test
    @Order(5)
    public void testGetClient() {
        log.info("Starting test: testGetClient");
        assertNotNull(kubeMQClient.getClient());
        log.info("Finished test: testGetClient");
    }

    @Test
    @Order(6)
    public void testGetAsyncClient() {
        log.info("Starting test: testGetAsyncClient");
        assertNotNull(kubeMQClient.getAsyncClient());
        log.info("Finished test: testGetAsyncClient");
    }

    @Test
    @Order(10)
    public void testClose_ChannelNull() throws InterruptedException {
        log.info("Starting test: testClose_ChannelNull");
        kubeMQClient.setManagedChannel(null);
        kubeMQClient.close();
        verify(managedChannelMock, times(0)).shutdown();
        verify(managedChannelMock, times(0)).awaitTermination(anyLong(), any(TimeUnit.class));
        log.info("Finished test: testClose_ChannelNull");
    }
}
