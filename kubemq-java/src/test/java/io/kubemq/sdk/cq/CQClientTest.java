package io.kubemq.sdk.cq;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.KubeMQUtils;
import kubemq.Kubemq;
import kubemq.kubemqGrpc;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CQClientTest {

    private static final String CLIENT_ID = "TestsClientID";

    //private static final MockedStatic<KubeMQUtils> mockedStatic = mockStatic(KubeMQUtils.class);
    @Mock
    private kubemqGrpc.kubemqBlockingStub client;

    @Mock
    private kubemqGrpc.kubemqStub asyncClient;

    @Mock
    private KubeMQClient kubeMQClient;

    @InjectMocks
    private CQClient cqClient;

    private MockedStatic<KubeMQUtils> mockedStatic;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        lenient().when(kubeMQClient.getClient()).thenReturn(client);
        lenient().when(kubeMQClient.getAsyncClient()).thenReturn(asyncClient);
        lenient().when(kubeMQClient.getClientId()).thenReturn(CLIENT_ID);
        cqClient = CQClient.builder().kubeMQClient(kubeMQClient).build(); // Manually inject the initialized kubeMQClient
    }

    @AfterEach
    public void tearDown() {
        if (mockedStatic != null) {
            mockedStatic.close();
        }
    }

    @Test
    @Order(1)
    public void testCreateCommandsChannel() throws Exception {
        log.info("Testing createCommandsChannel");

        mockedStatic = mockStatic(KubeMQUtils.class);
        mockedStatic.when(() -> KubeMQUtils.createChannelRequest(any(), anyString(), anyString(), eq("commands")))
                .thenReturn(true);

        boolean result = cqClient.createCommandsChannel("channelName");

        assertTrue(result);
        mockedStatic.verify(() -> KubeMQUtils.createChannelRequest(any(), anyString(), anyString(), eq("commands")));
        log.info("createCommandsChannel test passed");
    }

    @Test
    @Order(5)
    public void testCreateQueriesChannel() throws Exception {
        log.info("Testing createQueriesChannel");

        mockedStatic = mockStatic(KubeMQUtils.class);
        mockedStatic.when(() -> KubeMQUtils.createChannelRequest(any(), anyString(), anyString(), eq("queries")))
                .thenReturn(true);

        boolean result = cqClient.createQueriesChannel("channelName");

        assertTrue(result);
        mockedStatic.verify(() -> KubeMQUtils.createChannelRequest(any(), anyString(), anyString(), eq("queries")));
        log.info("createQueriesChannel test passed");
    }

    @Test
    @Order(10)
    public void testDeleteCommandsChannel() throws Exception {
        log.info("Testing deleteCommandsChannel");

        mockedStatic = mockStatic(KubeMQUtils.class);
        mockedStatic.when(() -> KubeMQUtils.deleteChannelRequest(any(), anyString(), anyString(), eq("commands")))
                .thenReturn(true);

        boolean result = cqClient.deleteCommandsChannel("channelName");

        assertTrue(result);
        mockedStatic.verify(() -> KubeMQUtils.deleteChannelRequest(any(), anyString(), anyString(), eq("commands")));
        log.info("deleteCommandsChannel test passed");
    }

    @Test
    @Order(15)
    public void testDeleteQueriesChannel() throws Exception {
        log.info("Testing deleteQueriesChannel");

        mockedStatic = mockStatic(KubeMQUtils.class);
        mockedStatic.when(() -> KubeMQUtils.deleteChannelRequest(any(), anyString(), anyString(), eq("queries")))
                .thenReturn(true);

        boolean result = cqClient.deleteQueriesChannel("channelName");

        assertTrue(result);
        mockedStatic.verify(() -> KubeMQUtils.deleteChannelRequest(any(), anyString(), anyString(), eq("queries")));
        log.info("deleteQueriesChannel test passed");
    }


    @Test
    @Order(20)
    public void testListCommandsChannels() throws Exception {
        log.info("Testing listCommandsChannels");
        List<CQChannel> expectedChannels = Collections.singletonList(
                new CQChannel(
                        "channel1", "type1", 1622014799L, true,
                        new CQStats(100, 200, 0,0,0,0),
                        new CQStats(150, 300, 0,0,0,0)
                ));

        try (MockedStatic<KubeMQUtils> mockedStatic = mockStatic(KubeMQUtils.class)) {
            mockedStatic.when(() -> KubeMQUtils.listCQChannels(any(), anyString(), eq("commands"), anyString()))
                    .thenReturn(expectedChannels);

            List<CQChannel> result = cqClient.listCommandsChannels("search");

            assertNotNull(result);
            assertEquals(expectedChannels, result);
            mockedStatic.verify(() -> KubeMQUtils.listCQChannels(any(), anyString(), eq("commands"), anyString()));
            log.info("listCommandsChannels test passed");
        }
    }

    @Test
    @Order(25)
    public void testListQueriesChannels() throws Exception {
        log.info("Testing listQueriesChannels");
        List<CQChannel> expectedChannels = Collections.singletonList(
                new CQChannel(
                        "channel1", "type1", 1622014799L, true,
                        new CQStats(100, 200, 0,0,0,0),
                        new CQStats(150, 300, 0,0,0,0)
                ));

        try (MockedStatic<KubeMQUtils> mockedStatic = mockStatic(KubeMQUtils.class)) {
            mockedStatic.when(() -> KubeMQUtils.listCQChannels(any(), anyString(), eq("queries"), anyString()))
                    .thenReturn(expectedChannels);

            List<CQChannel> result = cqClient.listQueriesChannels("search");

            assertNotNull(result);
            assertEquals(expectedChannels, result);
            mockedStatic.verify(() -> KubeMQUtils.listCQChannels(any(), anyString(), eq("queries"), anyString()));
            log.info("listQueriesChannels test passed");
        }
    }

    @Test
    @Order(30)
    public void testSubscribeToCommands() throws Exception {
        log.info("Testing subscribeToCommands");
        CommandsSubscription subscription = mock(CommandsSubscription.class);
        Kubemq.Subscribe subscribe = Kubemq.Subscribe.newBuilder().build();

        when(subscription.encode(anyString())).thenReturn(subscribe);

        cqClient.subscribeToCommands(subscription);

        verify(asyncClient).subscribeToRequests(eq(subscribe), any(StreamObserver.class));
        log.info("subscribeToCommands test passed");
    }

    @Test
    @Order(35)
    public void testSubscribeToQueries() throws Exception {
        log.info("Testing subscribeToQueries");
        QueriesSubscription subscription = mock(QueriesSubscription.class);
        Kubemq.Subscribe subscribe = Kubemq.Subscribe.newBuilder().build();

        when(subscription.encode(anyString())).thenReturn(subscribe);

        cqClient.subscribeToQueries(subscription);

        verify(asyncClient).subscribeToRequests(eq(subscribe), any(StreamObserver.class));
        log.info("subscribeToQueries test passed");
    }

    @Test
    @Order(40)
    public void testSendCommandRequest() throws Exception {
        log.info("Testing sendCommandRequest");
        CommandMessage commandMessage = mock(CommandMessage.class);
        Kubemq.Request request = Kubemq.Request.newBuilder().build();
        Kubemq.Response response = Kubemq.Response.newBuilder().build();
        CommandResponseMessage commandResponseMessage = mock(CommandResponseMessage.class);

        when(commandMessage.encode(anyString())).thenReturn(request);
        when(client.sendRequest(request)).thenReturn(response);
        lenient().when(commandResponseMessage.decode(response)).thenReturn(commandResponseMessage);

        CommandResponseMessage result = cqClient.sendCommandRequest(commandMessage);

        assertNotNull(result);
        verify(commandMessage).validate();
        verify(client).sendRequest(request);
        log.info("sendCommandRequest test passed");
    }

    @Test
    @Order(45)
    public void testSendQueryRequest() throws Exception {
        log.info("Testing sendQueryRequest");
        QueryMessage queryMessage = mock(QueryMessage.class);
        Kubemq.Request request = Kubemq.Request.newBuilder().build();
        Kubemq.Response response = Kubemq.Response.newBuilder().build();
        QueryResponseMessage queryResponseMessage = mock(QueryResponseMessage.class);

        when(queryMessage.encode(anyString())).thenReturn(request);
        when(client.sendRequest(request)).thenReturn(response);
        lenient().when(queryResponseMessage.decode(response)).thenReturn(queryResponseMessage);

        QueryResponseMessage result = cqClient.sendQueryRequest(queryMessage);

        assertNotNull(result);
        verify(queryMessage).validate();
        verify(client).sendRequest(request);
        log.info("sendQueryRequest test passed");
    }

    @Test
    @Order(50)
    public void testSendResponseMessage() throws Exception {
        log.info("Testing sendResponseMessage");
        CommandResponseMessage responseMessage = mock(CommandResponseMessage.class);
        Kubemq.Response response = Kubemq.Response.newBuilder().build();

        when(responseMessage.encode(anyString())).thenReturn(response);

        cqClient.sendResponseMessage(responseMessage);

        verify(responseMessage).validate();
        verify(client).sendResponse(response);
        log.info("sendResponseMessage test passed");
    }
}
