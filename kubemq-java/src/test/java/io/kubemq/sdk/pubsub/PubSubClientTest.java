package io.kubemq.sdk.pubsub;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ChannelDecoder;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PubSubClientTest {

    private static final String CLIENT_ID = "TestsClientID";
    private static final MockedStatic<ChannelDecoder> mockedStatic = mockStatic(ChannelDecoder.class);
    @Mock
    private kubemqGrpc.kubemqBlockingStub client;

    @Mock
    private kubemqGrpc.kubemqStub asyncClient;

    @Mock
    private KubeMQClient kubeMQClient;

    @InjectMocks
    private PubSubClient pubSubClient;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        lenient().when(kubeMQClient.getClient()).thenReturn(client);
        lenient().when(kubeMQClient.getAsyncClient()).thenReturn(asyncClient);
        lenient().when(kubeMQClient.getClientId()).thenReturn(CLIENT_ID);
        pubSubClient = PubSubClient.builder().kubeMQClient(kubeMQClient).build(); // Manually inject the initialized kubeMQClient
    }

    @Test
    @Order(1)
    public void testCreateEventsChannel() throws Exception {
        log.info("Testing createEventsChannel");
        Kubemq.Request request = Kubemq.Request.newBuilder().setClientID(CLIENT_ID).build();
        Kubemq.Response response = Kubemq.Response.newBuilder().setExecuted(true).build();

        when(client.sendRequest(any(Kubemq.Request.class))).thenReturn(response);

        boolean result = pubSubClient.createEventsChannel("channelName");

        assertTrue(result);
        verify(client).sendRequest(any(Kubemq.Request.class));
        log.info("createEventsChannel test passed");
    }

    @Test
    @Order(5)
    public void testCreateEventsStoreChannel() throws Exception {
        log.info("Testing createEventsStoreChannel");
        Kubemq.Request request = Kubemq.Request.newBuilder().build();
        Kubemq.Response response = Kubemq.Response.newBuilder().setExecuted(true).build();

        when(client.sendRequest(any(Kubemq.Request.class))).thenReturn(response);

        boolean result = pubSubClient.createEventsStoreChannel("channelName");

        assertTrue(result);
        verify(client).sendRequest(any(Kubemq.Request.class));
        log.info("createEventsStoreChannel test passed");
    }

    @Test
    @Order(10)
    public void testListEventsChannels() throws Exception {
        log.info("Testing listEventsChannels");
        Kubemq.Request request = Kubemq.Request.newBuilder().build();
        Kubemq.Response response = Kubemq.Response.newBuilder().setExecuted(true).build();
        List<PubSubChannel> expectedChannels = Collections.singletonList(
                new PubSubChannel(
                        "channel1", "type1", 1622014799L, true,
                        new PubSubStats(100, 200, 0, 0, 0, 0),
                        new PubSubStats(150, 300, 0, 0, 0, 0)
                ));
        mockedStatic.when(() -> ChannelDecoder.decodePubSubChannelList(response.toByteArray())).thenReturn(expectedChannels);

        when(client.sendRequest(any(Kubemq.Request.class))).thenReturn(response);
        List<PubSubChannel> result = pubSubClient.listEventsChannels("search");

        assertNotNull(result);
        verify(client).sendRequest(any(Kubemq.Request.class));
        log.info("listEventsChannels test passed");
    }

    @Test
    @Order(15)
    public void testListEventsStoreChannels() throws Exception {
        log.info("Testing listEventsStoreChannels");
        Kubemq.Request request = Kubemq.Request.newBuilder().build();
        Kubemq.Response response = Kubemq.Response.newBuilder().setExecuted(true).build();
        List<PubSubChannel> expectedChannels = Collections.singletonList(
                new PubSubChannel(
                        "channel1", "type1", 1622014799L, true,
                        new PubSubStats(100, 200, 0, 0, 0, 0),
                        new PubSubStats(150, 300, 0, 0, 0, 0)
                ));

        mockedStatic.when(() -> ChannelDecoder.decodePubSubChannelList(response.toByteArray())).thenReturn(expectedChannels);
        when(client.sendRequest(any(Kubemq.Request.class))).thenReturn(response);

        List<PubSubChannel> result = pubSubClient.listEventsStoreChannels("search");

        assertNotNull(result);
        verify(client).sendRequest(any(Kubemq.Request.class));
        log.info("listEventsStoreChannels test passed");
    }

    @Test
    @Order(20)
    public void testSubscribeToEvents() throws Exception {
        log.info("Testing subscribeToEvents");
        EventsSubscription subscription = mock(EventsSubscription.class);
        Kubemq.Subscribe subscribe = Kubemq.Subscribe.newBuilder().build();

        when(subscription.encode(anyString())).thenReturn(subscribe);

        pubSubClient.subscribeToEvents(subscription);

        verify(asyncClient).subscribeToEvents(eq(subscribe), any(StreamObserver.class));
        log.info("subscribeToEvents test passed");
    }

    @Test
    @Order(25)
    public void testSubscribeToEventsStore() throws Exception {
        log.info("Testing subscribeToEventsStore");
        EventsStoreSubscription subscription = mock(EventsStoreSubscription.class);
        Kubemq.Subscribe subscribe = Kubemq.Subscribe.newBuilder().build();

        when(subscription.encode(anyString())).thenReturn(subscribe);

        pubSubClient.subscribeToEventsStore(subscription);

        verify(asyncClient).subscribeToEvents(eq(subscribe), any(StreamObserver.class));
        log.info("subscribeToEventsStore test passed");
    }

    @Test
    @Order(30)
    public void testSendEventsMessage() throws Exception {
        log.info("Testing sendEventsMessage");
        EventMessage eventMessage = mock(EventMessage.class);
        Kubemq.Event event = Kubemq.Event.newBuilder().build();
        Kubemq.Result result = Kubemq.Result.newBuilder().setSent(true).build();

        when(eventMessage.encode(anyString())).thenReturn(event);
        when(client.sendEvent(event)).thenReturn(result);

        EventSendResult eventSendResult = pubSubClient.sendEventsMessage(eventMessage);

        assertTrue(eventSendResult.isSent());
        verify(eventMessage).validate();
        verify(client).sendEvent(event);
        log.info("sendEventsMessage test passed");
    }

    @Test
    @Order(35)
    public void testSendEventsStoreMessage() throws Exception {
        log.info("Testing sendEventsStoreMessage");
        EventStoreMessage eventStoreMessage = mock(EventStoreMessage.class);
        Kubemq.Event event = Kubemq.Event.newBuilder().build();
        Kubemq.Result result = Kubemq.Result.newBuilder().setSent(true).build();

        when(eventStoreMessage.encode(anyString())).thenReturn(event);
        when(client.sendEvent(event)).thenReturn(result);

        EventSendResult eventSendResult = pubSubClient.sendEventsStoreMessage(eventStoreMessage);

        assertTrue(eventSendResult.isSent());
        verify(eventStoreMessage).validate();
        verify(client).sendEvent(event);
        log.info("sendEventsStoreMessage test passed");
    }

    @Test
    @Order(37)
    public void testSendToEventsStream() throws Exception {
        log.info("Testing sendToEventsStream");
        StreamObserver subscription = mock(StreamObserver.class);
        StreamObserver<Kubemq.Event> result = mock(StreamObserver.class);
        when(pubSubClient.sendEventsStream(subscription)).thenReturn(result);

        assertEquals(pubSubClient.sendEventsStream(subscription),result);
        log.info("sendToEventsStream test passed");
    }

    @Test
    @Order(40)
    public void testDeleteEventsChannel() throws Exception {
        log.info("Testing deleteEventsChannel");
        Kubemq.Request request = Kubemq.Request.newBuilder().build();
        Kubemq.Response response = Kubemq.Response.newBuilder().setExecuted(true).build();

        when(client.sendRequest(any(Kubemq.Request.class))).thenReturn(response);

        boolean result = pubSubClient.deleteEventsChannel("channelName");

        assertTrue(result);
        verify(client).sendRequest(any(Kubemq.Request.class));
        log.info("deleteEventsChannel test passed");
    }

    @Test
    @Order(45)
    public void testDeleteEventsStoreChannel() throws Exception {
        log.info("Testing deleteEventsStoreChannel");
        Kubemq.Request request = Kubemq.Request.newBuilder().build();
        Kubemq.Response response = Kubemq.Response.newBuilder().setExecuted(true).build();

        when(client.sendRequest(any(Kubemq.Request.class))).thenReturn(response);

        boolean result = pubSubClient.deleteEventsStoreChannel("channelName");

        assertTrue(result);
        verify(client).sendRequest(any(Kubemq.Request.class));
        log.info("deleteEventsStoreChannel test passed");
    }
}
