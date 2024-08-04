package io.kubemq.sdk.cq;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.KubeMQUtils;
import kubemq.Kubemq;
import kubemq.kubemqGrpc;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
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
    @Order(2)
    public void testCreateCommandsChannelValidation() throws Exception {
        log.info("Testing createCommandsChannel validation");
        when(cqClient.createCommandsChannel(anyString())).thenThrow(new IllegalArgumentException("Invalid channel name"));
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            cqClient.createCommandsChannel("");
        });
        assertEquals("Invalid channel name", exception.getMessage());
        log.info("createCommandsChannel validation test passed");
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
    @Order(6)
    public void testCreateQueriesChannelValidation() throws Exception {
        log.info("Testing createQueriesChannel validation");
        when(cqClient.createQueriesChannel(anyString())).thenThrow(new IllegalArgumentException("Invalid channel name"));
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            cqClient.createQueriesChannel("");
        });
        assertEquals("Invalid channel name", exception.getMessage());
        log.info("createQueriesChannel validation test passed");
    }

    @Test
    @Order(10)
    public void testDeleteCommandsChannel() throws Exception {
        log.info("Testing deleteCommandsChannel");
        when(cqClient.deleteCommandsChannel("channelName")).thenReturn(true);
        boolean result = cqClient.deleteCommandsChannel("channelName");
        assertTrue(result);
        log.info("deleteCommandsChannel test passed");
    }

    @Test
    @Order(11)
    public void testDeleteCommandsChannelValidation() throws Exception {
        log.info("Testing deleteCommandsChannel validation");
        when(cqClient.deleteCommandsChannel(anyString())).thenThrow(new IllegalArgumentException("Invalid channel name"));
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            cqClient.deleteCommandsChannel("");
        });
        assertEquals("Invalid channel name", exception.getMessage());
        log.info("deleteCommandsChannel validation test passed");
    }

    @Test
    @Order(15)
    public void testDeleteQueriesChannel() throws Exception {
        log.info("Testing deleteQueriesChannel");
        when(cqClient.deleteQueriesChannel("channelName")).thenReturn(true);
        boolean result = cqClient.deleteQueriesChannel("channelName");
        assertTrue(result);
        log.info("deleteQueriesChannel test passed");
    }

    @Test
    @Order(16)
    public void testDeleteQueriesChannelValidation() throws Exception {
        log.info("Testing deleteQueriesChannel validation");
        when(cqClient.deleteQueriesChannel(anyString())).thenThrow(new IllegalArgumentException("Invalid channel name"));
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            cqClient.deleteQueriesChannel("");
        });
        assertEquals("Invalid channel name", exception.getMessage());
        log.info("deleteQueriesChannel validation test passed");
    }

    @Test
    @Order(20)
    public void testListCommandsChannels() throws Exception {
        log.info("Testing listCommandsChannels");
        List<CQChannel> expectedChannels = Collections.singletonList(
                new CQChannel(
                        "channel1", "type1", 1622014799L, true,
                        new CQStats(100, 200, 0, 0, 0, 0),
                        new CQStats(150, 300, 0, 0, 0, 0)
                ));
        when(cqClient.listCommandsChannels("search")).thenReturn(expectedChannels);
        List<CQChannel> result = cqClient.listCommandsChannels("search");
        assertNotNull(result);
        assertEquals(expectedChannels, result);
        log.info("listCommandsChannels test passed");
    }

    @Test
    @Order(21)
    public void testListCommandsChannelsValidation() throws Exception {
        log.info("Testing listCommandsChannels validation");
        when(cqClient.listCommandsChannels(anyString())).thenThrow(new IllegalArgumentException("Invalid search pattern"));
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            cqClient.listCommandsChannels("");
        });
        assertEquals("Invalid search pattern", exception.getMessage());
        log.info("listCommandsChannels validation test passed");
    }

    @Test
    @Order(25)
    public void testListQueriesChannels() throws Exception {
        log.info("Testing listQueriesChannels");
        List<CQChannel> expectedChannels = Collections.singletonList(
                new CQChannel(
                        "channel1", "type1", 1622014799L, true,
                        new CQStats(100, 200, 0, 0, 0, 0),
                        new CQStats(150, 300, 0, 0, 0, 0)
                ));
        when(cqClient.listQueriesChannels("search")).thenReturn(expectedChannels);
        List<CQChannel> result = cqClient.listQueriesChannels("search");
        assertNotNull(result);
        assertEquals(expectedChannels, result);
        log.info("listQueriesChannels test passed");
    }

    @Test
    @Order(26)
    public void testListQueriesChannelsValidation() throws Exception {
        log.info("Testing listQueriesChannels validation");
        when(cqClient.listQueriesChannels(anyString())).thenThrow(new IllegalArgumentException("Invalid search pattern"));
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            cqClient.listQueriesChannels("");
        });
        assertEquals("Invalid search pattern", exception.getMessage());
        log.info("listQueriesChannels validation test passed");
    }

    @Test
    @Order(30)
    public void testSubscribeToCommands() throws Exception {
        log.info("Testing subscribeToCommands");
        CommandsSubscription subscription = mock(CommandsSubscription.class);
        Kubemq.Subscribe subscribe = Kubemq.Subscribe.newBuilder().build();
        doNothing().when(cqClient).subscribeToCommands(any(CommandsSubscription.class));
        cqClient.subscribeToCommands(subscription);
        log.info("subscribeToCommands test passed");
    }

    @Test
    @Order(31)
    public void testSubscribeToCommandsValidation() throws Exception {
        log.info("Testing subscribeToCommands validation");
        CommandsSubscription subscription = mock(CommandsSubscription.class);
        lenient().doThrow(new IllegalArgumentException("Invalid subscription")).when(subscription).validate();
        doThrow(new RuntimeException("Invalid subscription."))
                .when(cqClient).subscribeToCommands(subscription);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () ->  cqClient.subscribeToCommands(subscription));
        assertEquals("Invalid subscription.", exception.getMessage());
        log.info("subscribeToCommands validation test passed");
    }

    @Test
    @Order(35)
    public void testSubscribeToQueries() throws Exception {
        log.info("Testing subscribeToQueries");
        QueriesSubscription subscription = mock(QueriesSubscription.class);
        Kubemq.Subscribe subscribe = Kubemq.Subscribe.newBuilder().build();
        doNothing().when(cqClient).subscribeToQueries(any(QueriesSubscription.class));
        cqClient.subscribeToQueries(subscription);
        log.info("subscribeToQueries test passed");
    }

    @Test
    @Order(36)
    public void testSubscribeToQueriesValidation() throws Exception {
        log.info("Testing subscribeToQueries validation");
        QueriesSubscription subscription = mock(QueriesSubscription.class);
        lenient().doThrow(new IllegalArgumentException("Invalid subscription")).when(subscription).validate();
        doThrow(new RuntimeException("Invalid subscription."))
                .when(cqClient).subscribeToQueries(subscription);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () ->  cqClient.subscribeToQueries(subscription));
        assertEquals("Invalid subscription.", exception.getMessage());
        log.info("subscribeToQueries validation test passed");
    }

    @Test
    @Order(40)
    public void testSendQueryRequest() throws Exception {
        log.info("Testing sendQueryRequest");
        QueryMessage message = mock(QueryMessage.class);
        QueryResponseMessage expected = QueryResponseMessage.builder().isExecuted(true).build();
        Kubemq.Request query = Kubemq.Request.newBuilder().build();
        lenient().when(message.encode(CLIENT_ID)).thenReturn(query);
        when(cqClient.sendQueryRequest(message)).thenReturn(expected);
        QueryResponseMessage actual = cqClient.sendQueryRequest(message);
        assertNotNull(actual);
        assertEquals(actual.isExecuted(),expected.isExecuted());
        log.info("sendQueryRequest test passed");
    }

    @Test
    @Order(41)
    public void testSendQueryRequestValidation() throws Exception {
        log.info("Testing sendQueryRequest validation");
        QueryMessage message = mock(QueryMessage.class);
        lenient().when(message.encode(CLIENT_ID)).thenThrow(new IllegalArgumentException("Invalid query message"));
        doThrow(new RuntimeException("Invalid query message."))
                .when(cqClient).sendQueryRequest(message);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () ->  cqClient.sendQueryRequest(message));
        assertEquals("Invalid query message.", exception.getMessage());
        log.info("sendQueryRequest validation test passed");
    }

    @Test
    @Order(45)
    public void testSendCommandRequest() throws Exception {
        log.info("Testing sendCommandRequest");
        CommandMessage message = mock(CommandMessage.class);
        CommandResponseMessage expected = CommandResponseMessage.builder().isExecuted(true).build();
        Kubemq.Request command = Kubemq.Request.newBuilder().build();
        lenient().when(message.encode(CLIENT_ID)).thenReturn(command);
        when(cqClient.sendCommandRequest(message)).thenReturn(expected);
        CommandResponseMessage result = cqClient.sendCommandRequest(message);
        assertNotNull(result);
        assertEquals(expected.isExecuted(), result.isExecuted());
        log.info("sendCommandRequest test passed");
    }

    @Test
    @Order(46)
    public void testSendCommandRequestValidation() throws Exception {
        log.info("Testing sendCommandRequest validation");
        CommandMessage message = mock(CommandMessage.class);
        lenient().when(message.encode(CLIENT_ID)).thenThrow(new IllegalArgumentException("Invalid command message"));
        doThrow(new RuntimeException("Invalid command message."))
                .when(cqClient).sendCommandRequest(message);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () ->  cqClient.sendCommandRequest(message));
        assertEquals("Invalid command message.", exception.getMessage());
        log.info("sendCommandRequest validation test passed");
    }

    @Test
    @Order(50)
    public void testSendResponseMessage() throws Exception {
        log.info("Testing sendResponseMessage");
        CommandResponseMessage message = mock(CommandResponseMessage.class);
        Kubemq.Response response = Kubemq.Response.newBuilder().build();
        lenient().when(message.encode(CLIENT_ID)).thenReturn(response);
        doNothing().when(cqClient).sendResponseMessage(message);
        cqClient.sendResponseMessage(message);
        log.info("sendResponseMessage test passed");
    }

    @Test
    @Order(51)
    public void testSendResponseMessageValidation() throws Exception {
        log.info("Testing sendResponseMessage validation");
        CommandResponseMessage message = mock(CommandResponseMessage.class);
        lenient().when(message.encode(CLIENT_ID)).thenThrow(new IllegalArgumentException("Invalid response message"));
        doThrow(new RuntimeException("Invalid response message."))
                .when(cqClient).sendResponseMessage(message);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () ->  cqClient.sendResponseMessage(message));
        assertEquals("Invalid response message.", exception.getMessage());
        log.info("sendResponseMessage validation test passed");
    }
}
