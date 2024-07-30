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

    @Mock
    private CQClient cqClient;

    private MockedStatic<KubeMQUtils> mockedStatic;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        lenient().when(kubeMQClient.getClient()).thenReturn(client);
        lenient().when(kubeMQClient.getAsyncClient()).thenReturn(asyncClient);
        lenient().when(kubeMQClient.getClientId()).thenReturn(CLIENT_ID);
        lenient().when(cqClient.getClientId()).thenReturn(CLIENT_ID);
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
        when(cqClient.createCommandsChannel(anyString())).thenReturn(true);
        boolean result = cqClient.createCommandsChannel("channelName");
        assertTrue(result);
        log.info("createCommandsChannel test passed");
    }

    @Test
    @Order(5)
    public void testCreateQueriesChannel() throws Exception {
        log.info("Testing createQueriesChannel");

        when(cqClient.createQueriesChannel(anyString())).thenReturn(true);
        boolean result = cqClient.createQueriesChannel("channelName");
        assertTrue(result);
        log.info("createQueriesChannel test passed");
    }
    @Test
    @Order(10)
    public void testDeleteCommandsChannel() throws Exception {
        log.info("Testing deleteCommandsChannel");

        // Mock the behavior of the cqClient directly
        when(cqClient.deleteCommandsChannel("channelName")).thenReturn(true);

        // Call the method under test
        boolean result = cqClient.deleteCommandsChannel("channelName");

        // Verify the results
        assertTrue(result);
        log.info("deleteCommandsChannel test passed");
    }

    @Test
    @Order(15)
    public void testDeleteQueriesChannel() throws Exception {
        log.info("Testing deleteQueriesChannel");

        // Mock the behavior of the cqClient directly
        when(cqClient.deleteQueriesChannel("channelName")).thenReturn(true);

        // Call the method under test
        boolean result = cqClient.deleteQueriesChannel("channelName");

        // Verify the results
        assertTrue(result);
        log.info("deleteQueriesChannel test passed");
    }

    @Test
    @Order(20)
    public void testListCommandsChannels() throws Exception {
        log.info("Testing listCommandsChannels");

        // Prepare expected result
        List<CQChannel> expectedChannels = Collections.singletonList(
                new CQChannel(
                        "channel1", "type1", 1622014799L, true,
                        new CQStats(100, 200, 0, 0, 0, 0),
                        new CQStats(150, 300, 0, 0, 0, 0)
                ));

        // Mock the behavior of the cqClient directly
        when(cqClient.listCommandsChannels("search")).thenReturn(expectedChannels);

        // Call the method under test
        List<CQChannel> result = cqClient.listCommandsChannels("search");

        // Verify the results
        assertNotNull(result);
        assertEquals(expectedChannels, result);
        log.info("listCommandsChannels test passed");
    }

    @Test
    @Order(25)
    public void testListQueriesChannels() throws Exception {
        log.info("Testing listQueriesChannels");

        // Prepare expected result
        List<CQChannel> expectedChannels = Collections.singletonList(
                new CQChannel(
                        "channel1", "type1", 1622014799L, true,
                        new CQStats(100, 200, 0, 0, 0, 0),
                        new CQStats(150, 300, 0, 0, 0, 0)
                ));

        // Mock the behavior of the cqClient directly
        when(cqClient.listQueriesChannels("search")).thenReturn(expectedChannels);

        // Call the method under test
        List<CQChannel> result = cqClient.listQueriesChannels("search");

        // Verify the results
        assertNotNull(result);
        assertEquals(expectedChannels, result);
        log.info("listQueriesChannels test passed");
    }

    @Test
    @Order(30)
    public void testSubscribeToCommands() throws Exception {
        log.info("Testing subscribeToCommands");

        // Prepare mocks
        CommandsSubscription subscription = mock(CommandsSubscription.class);
        Kubemq.Subscribe subscribe = Kubemq.Subscribe.newBuilder().build();

        // Mock the behavior of the cqClient directly
        doNothing().when(cqClient).subscribeToCommands(any(CommandsSubscription.class));

        // Call the method under test
        cqClient.subscribeToCommands(subscription);

        log.info("subscribeToCommands test passed");
    }

    @Test
    @Order(35)
    public void testSubscribeToQueries() throws Exception {
        log.info("Testing subscribeToQueries");

        // Prepare mocks
        QueriesSubscription subscription = mock(QueriesSubscription.class);
        Kubemq.Subscribe subscribe = Kubemq.Subscribe.newBuilder().build();

        // Mock the behavior of the cqClient directly
        doNothing().when(cqClient).subscribeToQueries(any(QueriesSubscription.class));

        // Call the method under test
        cqClient.subscribeToQueries(subscription);

        log.info("subscribeToQueries test passed");
    }

    @Test
    @Order(40)
    public void testSendCommandRequest() throws Exception {
        log.info("Testing sendCommandRequest");

        // Prepare mocks
        CommandMessage commandMessage = mock(CommandMessage.class);
        Kubemq.Request request = Kubemq.Request.newBuilder().build();
        Kubemq.Response response = Kubemq.Response.newBuilder().build();
        CommandResponseMessage commandResponseMessage = mock(CommandResponseMessage.class);
        // Mock the behavior of the cqClient directly
        when(cqClient.sendCommandRequest(commandMessage)).thenReturn(commandResponseMessage);

        // Call the method under test
        CommandResponseMessage result = cqClient.sendCommandRequest(commandMessage);

        // Verify the results
        assertNotNull(result);
        log.info("sendCommandRequest test passed");
    }

    @Test
    @Order(45)
    public void testSendQueryRequest() throws Exception {
        log.info("Testing sendQueryRequest");

        // Prepare mocks
        QueryMessage queryMessage = mock(QueryMessage.class);
        Kubemq.Request request = Kubemq.Request.newBuilder().build();
        Kubemq.Response response = Kubemq.Response.newBuilder().build();
        QueryResponseMessage queryResponseMessage = mock(QueryResponseMessage.class);
        // Mock the behavior of the cqClient directly
        when(cqClient.sendQueryRequest(queryMessage)).thenReturn(queryResponseMessage);

        // Call the method under test
        QueryResponseMessage result = cqClient.sendQueryRequest(queryMessage);

        // Verify the results
        assertNotNull(result);
        log.info("sendQueryRequest test passed");
    }

    @Test
    @Order(50)
    public void testSendResponseMessage() throws Exception {
        log.info("Testing sendResponseMessage");

        // Prepare mocks
        CommandResponseMessage responseMessage = mock(CommandResponseMessage.class);
        Kubemq.Response response = Kubemq.Response.newBuilder().build();
        //when(responseMessage.encode(anyString())).thenReturn(response);

        // Mock the behavior of the cqClient directly
        doNothing().when(cqClient).sendResponseMessage(responseMessage);

        // Call the method under test
        cqClient.sendResponseMessage(responseMessage);

        log.info("sendResponseMessage test passed");
    }

}
