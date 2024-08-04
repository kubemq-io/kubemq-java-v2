package io.kubemq.sdk.pubsub;

import ch.qos.logback.classic.Logger;
import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ChannelDecoder;
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
public class PubSubClientTest {

    private static final String CLIENT_ID = "TestsClientID";
    private MockedStatic<ChannelDecoder> mockedStatic;

    @Mock
    private kubemqGrpc.kubemqBlockingStub client;

    @Mock
    private kubemqGrpc.kubemqStub asyncClient;

    @Mock
    private KubeMQClient kubeMQClient;

    @Mock
    private PubSubClient pubSubClient;

    @Mock
    private Logger logger;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        lenient().when(kubeMQClient.getClient()).thenReturn(client);
        lenient().when(kubeMQClient.getAsyncClient()).thenReturn(asyncClient);
        lenient().when(kubeMQClient.getClientId()).thenReturn(CLIENT_ID);
        lenient().when(pubSubClient.getClientId()).thenReturn(CLIENT_ID);
        mockedStatic = mockStatic(ChannelDecoder.class);
    }

    @AfterEach
    public void tearDown() {
        if (mockedStatic != null) {
            mockedStatic.close();
        }
    }

    @Test
    @Order(1)
    public void testCreateEventsChannel() throws Exception {
        log.info("Testing createEventsChannel");
        Kubemq.Request request = Kubemq.Request.newBuilder().setClientID(CLIENT_ID).build();
        Kubemq.Response response = Kubemq.Response.newBuilder().setExecuted(true).build();

        lenient().when(client.sendRequest(any(Kubemq.Request.class))).thenReturn(response);
        when(pubSubClient.createEventsChannel(any(String.class))).thenReturn(true);

        boolean result = pubSubClient.createEventsChannel("channelName");

        assertTrue(result);
        log.info("createEventsChannel test passed");
    }

    @Test
    @Order(2)
    public void testCreateEventsChannelNullName() throws Exception {
        log.info("Testing createEventsChannelNullName");
        Kubemq.Request request = Kubemq.Request.newBuilder().setClientID(CLIENT_ID).build();
        Kubemq.Response response = Kubemq.Response.newBuilder().setExecuted(false).setError("Error").build();

        lenient().when(client.sendRequest(any(Kubemq.Request.class))).thenReturn(response);
        lenient().when(pubSubClient.createEventsChannel(any(String.class))).thenCallRealMethod();
            boolean result =  pubSubClient.createEventsChannel(null);
        assertFalse(result);
        log.info("createEventsChannelNullName test passed");
    }

    @Test
    @Order(5)
    public void testCreateEventsStoreChannel() throws Exception {
        log.info("Testing createEventsStoreChannel");
        Kubemq.Request request = Kubemq.Request.newBuilder().setClientID(CLIENT_ID).build();
        Kubemq.Response response = Kubemq.Response.newBuilder().setExecuted(true).build();

        lenient().when(client.sendRequest(any(Kubemq.Request.class))).thenReturn(response);
        when(pubSubClient.createEventsStoreChannel(any(String.class))).thenReturn(true);

        boolean result = pubSubClient.createEventsStoreChannel("channelName");

        assertTrue(result);
        log.info("createEventsStoreChannel test passed");
    }

    @Test
    @Order(6)
    public void testCreateEventsStoreChannelNullName() throws Exception {
        log.info("Testing createEventsStoreChannelNullName");
        Kubemq.Request request = Kubemq.Request.newBuilder().setClientID(CLIENT_ID).build();
        Kubemq.Response response = Kubemq.Response.newBuilder().setExecuted(false).setError("Error").build();

        lenient().when(client.sendRequest(any(Kubemq.Request.class))).thenReturn(response);
        lenient().when(pubSubClient.createEventsStoreChannel(any(String.class))).thenReturn(true);

        boolean result = pubSubClient.createEventsStoreChannel(null);

        assertFalse(result);
        log.info("createEventsStoreChannelNullName test passed");
    }

    @Test
    @Order(10)
    public void testListEventsChannels() throws Exception {
        log.info("Testing listEventsChannels");
        Kubemq.Request request = Kubemq.Request.newBuilder().setClientID(CLIENT_ID).build();
        Kubemq.Response response = Kubemq.Response.newBuilder().setExecuted(true).build();
        List<PubSubChannel> expectedChannels = Collections.singletonList(
                new PubSubChannel(
                        "channel1", "type1", 1622014799L, true,
                        new PubSubStats(100, 200, 0, 0, 0, 0),
                        new PubSubStats(150, 300, 0, 0, 0, 0)
                ));
        mockedStatic.when(() -> ChannelDecoder.decodePubSubChannelList(response.toByteArray())).thenReturn(expectedChannels);

        when(pubSubClient.listEventsChannels(any(String.class))).thenReturn(expectedChannels);

        List<PubSubChannel> result = pubSubClient.listEventsChannels("search");

        assertNotNull(result);
        assertEquals(expectedChannels.size(), result.size());
        log.info("listEventsChannels test passed");
    }

    @Test
    @Order(11)
    public void testListEventsChannelsSearchNUll() throws Exception {
        log.info("Testing listEventsChannelsSearchNUll");
        Kubemq.Request request = Kubemq.Request.newBuilder().setClientID(CLIENT_ID).build();
        Kubemq.Response response = Kubemq.Response.newBuilder().setExecuted(false).setError("Error").build();

        doThrow(new IllegalArgumentException("Invalid Channel Search String."))
                .when(pubSubClient).listEventsChannels(anyString());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () ->  pubSubClient.listEventsChannels("null"));

        assertEquals("Invalid Channel Search String.", exception.getMessage());
        log.info("listEventsChannelsSearchNUll test passed");
    }

    @Test
    @Order(15)
    public void testListEventsStoreChannels() throws Exception {
        log.info("Testing listEventsStoreChannels");
        Kubemq.Request request = Kubemq.Request.newBuilder().setClientID(CLIENT_ID).build();
        Kubemq.Response response = Kubemq.Response.newBuilder().setExecuted(true).build();
        List<PubSubChannel> expectedChannels = Collections.singletonList(
                new PubSubChannel(
                        "channel1", "type1", 1622014799L, true,
                        new PubSubStats(100, 200, 0, 0, 0, 0),
                        new PubSubStats(150, 300, 0, 0, 0, 0)
                ));

        mockedStatic.when(() -> ChannelDecoder.decodePubSubChannelList(response.toByteArray())).thenReturn(expectedChannels);

        //when(client.sendRequest(any(Kubemq.Request.class))).thenReturn(response);
        when(pubSubClient.listEventsStoreChannels(any(String.class))).thenReturn(expectedChannels);

        List<PubSubChannel> result = pubSubClient.listEventsStoreChannels("search");

        assertNotNull(result);
        assertEquals(expectedChannels.size(), result.size());
        log.info("listEventsStoreChannels test passed");
    }

    @Test
    @Order(16)
    public void testListEventsStoreChannelsNull() throws Exception {
        log.info("Testing listEventsStoreChannelsNegative");
        Kubemq.Request request = Kubemq.Request.newBuilder().setClientID(CLIENT_ID).build();
        Kubemq.Response response = Kubemq.Response.newBuilder().setExecuted(false).setError("Error").build();

        doThrow(new IllegalArgumentException("Invalid Channel Search String."))
                .when(pubSubClient).listEventsStoreChannels(anyString());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () ->  pubSubClient.listEventsStoreChannels("null"));

        assertEquals("Invalid Channel Search String.", exception.getMessage());
        log.info("listEventsStoreChannelsNull test passed");
    }

    @Test
    @Order(20)
    public void testSubscribeToEvents() throws Exception {
        log.info("Testing subscribeToEvents");
        EventsSubscription subscription = mock(EventsSubscription.class);
        Kubemq.Subscribe subscribe = Kubemq.Subscribe.newBuilder().build();

        lenient().doNothing().when(subscription).validate();
        lenient().when(subscription.encode(anyString(), any())).thenReturn(subscribe);
        lenient().doNothing().when(asyncClient).subscribeToEvents(any(Kubemq.Subscribe.class), any());
        pubSubClient.subscribeToEvents(subscription);
        log.info("subscribeToEvents test passed");
    }

    @Test
    @Order(21)
    public void testSubscribeToEventsNegative() throws Exception {
        log.info("Testing subscribeToEventsNegative");
        EventsSubscription subscription = mock(EventsSubscription.class);
        Kubemq.Subscribe subscribe = Kubemq.Subscribe.newBuilder().build();

        //doThrow(new RuntimeException("Validation error")).when(subscription).validate();
        doThrow(new RuntimeException("Validation error."))
                .when(pubSubClient).subscribeToEvents(subscription);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () ->  pubSubClient.subscribeToEvents(subscription));

        assertEquals("Validation error.", exception.getMessage());
        log.info("subscribeToEventsNegative test passed");
    }

    @Test
    @Order(25)
    public void testSubscribeToEventsStore() throws Exception {
        log.info("Testing subscribeToEventsStore");
        EventsStoreSubscription subscription = mock(EventsStoreSubscription.class);
        Kubemq.Subscribe subscribe = Kubemq.Subscribe.newBuilder().build();

        lenient().doNothing().when(subscription).validate();
        lenient().when(subscription.encode(anyString(), any())).thenReturn(subscribe);
        lenient().doNothing().when(asyncClient).subscribeToEvents(any(Kubemq.Subscribe.class), any());

        //when(pubSubClient.subscribeToEventsStore(any(EventsStoreSubscription.class))).thenCallRealMethod();

        pubSubClient.subscribeToEventsStore(subscription);
        log.info("subscribeToEventsStore test passed");
    }

    @Test
    @Order(26)
    public void testSubscribeToEventsStoreNegative() throws Exception {
        log.info("Testing subscribeToEventsStoreNegative");
        EventsStoreSubscription subscription = mock(EventsStoreSubscription.class);
        Kubemq.Subscribe subscribe = Kubemq.Subscribe.newBuilder().build();

        //doThrow(new RuntimeException("Validation error")).when(subscription).validate();
        doThrow(new RuntimeException("Validation error."))
                .when(pubSubClient).subscribeToEventsStore(subscription);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () ->  pubSubClient.subscribeToEventsStore(subscription));

        assertEquals("Validation error.", exception.getMessage());
        log.info("subscribeToEventsStoreNegative test passed");
    }

    @Test
    @Order(30)
    public void testSendEventsMessage() throws Exception {
        log.info("Testing sendEventsMessage");
        EventMessage message = mock(EventMessage.class);
        Kubemq.Event event = Kubemq.Event.newBuilder().build();

        //doNothing().when(message).validate();
        lenient().when(message.encode(anyString())).thenReturn(event);
        doNothing().when(pubSubClient).sendEventsMessage(any(EventMessage.class));

        pubSubClient.sendEventsMessage(message);
        log.info("sendEventsMessage test passed");
    }

    @Test
    @Order(31)
    public void testSendEventsMessageChannelEmpty() throws Exception {
        log.info("Testing SendEventsMessageChannelEmpty");
        EventMessage message = mock(EventMessage.class);

        //doThrow(new RuntimeException("Validation error")).when(message).validate();
        doThrow(new RuntimeException("Event message must have a channel."))
                .when(pubSubClient).sendEventsMessage(message);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () ->  pubSubClient.sendEventsMessage(message));

        assertEquals("Event message must have a channel.", exception.getMessage());
        log.info("SendEventsMessageChannelEmpty test passed");
    }

    @Test
    @Order(32)
    public void testSendEventsMessageBodyEmpty() throws Exception {
        log.info("Testing SendEventsMessageBodyEmpty");
        EventMessage message = mock(EventMessage.class);

        //doThrow(new RuntimeException("Validation error")).when(message).validate();
        doThrow(new RuntimeException("Event message must have at least one of the following: metadata, body, or tags."))
                .when(pubSubClient).sendEventsMessage(message);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () ->  pubSubClient.sendEventsMessage(message));

        assertEquals("Event message must have at least one of the following: metadata, body, or tags.", exception.getMessage());
        log.info("SendEventsMessageChannelEmpty test passed");
    }

    @Test
    @Order(35)
    public void testSendEventsStoreMessage() throws Exception {
        log.info("Testing sendEventsStoreMessage");
        EventStoreMessage message = mock(EventStoreMessage.class);
        StreamObserver<Kubemq.Event> eventStreamObserver = mock(StreamObserver.class);
        Kubemq.Event event = Kubemq.Event.newBuilder().build();
        EventSendResult sendResult = EventSendResult.builder().sent(true).build();

        //doNothing().when(message).validate();
        lenient().when(message.encode(anyString())).thenReturn(event);
       // when(asyncClient.sendEventsStream(any())).thenReturn(eventStreamObserver);
        when(pubSubClient.sendEventsStoreMessage(any(EventStoreMessage.class))).thenReturn(sendResult);

        EventSendResult actual = pubSubClient.sendEventsStoreMessage(message);
        assertNotNull(actual);
        assertEquals(actual.isSent(), sendResult.isSent());
        log.info("sendEventsStoreMessage test passed");
    }

    @Test
    @Order(36)
    public void testSendEventsStoreMessageNotSent() throws Exception {
        log.info("Testing SendEventsStoreMessageNotSent");
        EventStoreMessage message = mock(EventStoreMessage.class);
        StreamObserver<Kubemq.Event> eventStreamObserver = mock(StreamObserver.class);
        Kubemq.Event event = Kubemq.Event.newBuilder().build();
        EventSendResult sendResult = EventSendResult.builder().sent(false).build();

        lenient().when(message.encode(anyString())).thenReturn(event);
        when(pubSubClient.sendEventsStoreMessage(any(EventStoreMessage.class))).thenReturn(sendResult);

        EventSendResult actual = pubSubClient.sendEventsStoreMessage(message);
        assertNotNull(actual);
        assertEquals(actual.isSent(), sendResult.isSent());
        log.info("SendEventsStoreMessageNotSent test passed");
    }

    @Test
    @Order(37)
    public void testSendEventsStoreMessageChannelEmpty() throws Exception {
        log.info("Testing SendEventsStoreMessageChannelEmpty");
        EventStoreMessage message = mock(EventStoreMessage.class);

        //doThrow(new RuntimeException("Validation error")).when(message).validate();
        doThrow(new RuntimeException("Event Store message must have a channel."))
                .when(pubSubClient).sendEventsStoreMessage(message);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () ->  pubSubClient.sendEventsStoreMessage(message));

        assertEquals("Event Store message must have a channel.", exception.getMessage());
        log.info("SendEventsStoreMessageChannelEmpty test passed");
    }

    @Test
    @Order(38)
    public void testSendEventsStoreMessageEmptyBody() throws Exception {
        log.info("Testing SendEventsStoreMessageEmptyBody");
        EventStoreMessage message = mock(EventStoreMessage.class);

        //doThrow(new RuntimeException("Validation error")).when(message).validate();
        doThrow(new RuntimeException("Event Store message must have at least one of the following: metadata, body, or tags."))
                .when(pubSubClient).sendEventsStoreMessage(message);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () ->  pubSubClient.sendEventsStoreMessage(message));

        assertEquals("Event Store message must have at least one of the following: metadata, body, or tags.", exception.getMessage());
        log.info("SendEventsStoreMessageEmptyBody test passed");
    }

    @Test
    @Order(40)
    public void testDeleteEventsChannel() throws Exception {
        log.info("Testing deleteEventsChannel");
        Kubemq.Request request = Kubemq.Request.newBuilder().setClientID(CLIENT_ID).build();
        Kubemq.Response response = Kubemq.Response.newBuilder().setExecuted(true).build();

        //when(client.sendRequest(any(Kubemq.Request.class))).thenReturn(response);
        when(pubSubClient.deleteEventsChannel(any(String.class))).thenReturn(true);

        boolean result = pubSubClient.deleteEventsChannel("channelName");

        assertTrue(result);
        log.info("deleteEventsChannel test passed");
    }

    @Test
    @Order(41)
    public void testDeleteEventsChannelNotDeleted() throws Exception {
        log.info("Testing deleteEventsChannelNotDeleted");
        Kubemq.Request request = Kubemq.Request.newBuilder().setClientID(CLIENT_ID).build();
        Kubemq.Response response = Kubemq.Response.newBuilder().setExecuted(true).build();

        //when(client.sendRequest(any(Kubemq.Request.class))).thenReturn(response);
        when(pubSubClient.deleteEventsChannel(any(String.class))).thenReturn(false);
        boolean result = pubSubClient.deleteEventsChannel("channelName");
        assertFalse(result);
        log.info("deleteEventsChannelNotDeleted test passed");
    }

    @Test
    @Order(42)
    public void testDeleteEventsChannelNegative() throws Exception {
        log.info("Testing deleteEventsChannelNegative");
        Kubemq.Request request = Kubemq.Request.newBuilder().setClientID(CLIENT_ID).build();
        Kubemq.Response response = Kubemq.Response.newBuilder().setExecuted(false).setError("Error").build();

        lenient().when(client.sendRequest(any(Kubemq.Request.class))).thenReturn(response);
        //when(pubSubClient.deleteEventsChannel(any(String.class))).thenCallRealMethod();
        doThrow(new RuntimeException("Grpc Error."))
                .when(pubSubClient).deleteEventsChannel("Null");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () ->  pubSubClient.deleteEventsChannel("Null"));

        assertEquals("Grpc Error.", exception.getMessage());
        log.info("deleteEventsChannelNegative test passed");
    }

    @Test
    @Order(45)
    public void testDeleteEventsStoreChannel() throws Exception {
        log.info("Testing deleteEventsStoreChannel");
        Kubemq.Request request = Kubemq.Request.newBuilder().setClientID(CLIENT_ID).build();
        Kubemq.Response response = Kubemq.Response.newBuilder().setExecuted(true).build();

        lenient().when(client.sendRequest(any(Kubemq.Request.class))).thenReturn(response);
        when(pubSubClient.deleteEventsStoreChannel(any(String.class))).thenReturn(true);

        boolean result = pubSubClient.deleteEventsStoreChannel("channelName");

        assertTrue(result);
        log.info("deleteEventsStoreChannel test passed");
    }

    @Test
    @Order(46)
    public void testDeleteEventsStoreChannelNotDeleted() throws Exception {
        log.info("Testing deleteEventsStoreChannelNotDeleted");
        Kubemq.Request request = Kubemq.Request.newBuilder().setClientID(CLIENT_ID).build();
        Kubemq.Response response = Kubemq.Response.newBuilder().setExecuted(true).build();

        lenient().when(client.sendRequest(any(Kubemq.Request.class))).thenReturn(response);
        when(pubSubClient.deleteEventsStoreChannel(any(String.class))).thenReturn(false);

        boolean result = pubSubClient.deleteEventsStoreChannel("channelName");

        assertFalse(result);
        log.info("deleteEventsStoreChannelNotDeleted test passed");
    }

    @Test
    @Order(47)
    public void testDeleteEventsStoreChannelNegative() throws Exception {
        log.info("Testing deleteEventsStoreChannelNegative");
        Kubemq.Request request = Kubemq.Request.newBuilder().setClientID(CLIENT_ID).build();
        Kubemq.Response response = Kubemq.Response.newBuilder().setExecuted(false).setError("Error").build();

        lenient().when(client.sendRequest(any(Kubemq.Request.class))).thenReturn(response);
        lenient().when(pubSubClient.deleteEventsStoreChannel(any(String.class))).thenCallRealMethod();

        doThrow(new RuntimeException("Grpc Error."))
                .when(pubSubClient).deleteEventsStoreChannel("Null");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () ->  pubSubClient.deleteEventsStoreChannel("Null"));

        assertEquals("Grpc Error.", exception.getMessage());
        log.info("deleteEventsStoreChannelNegative test passed");
    }
}
