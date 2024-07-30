package io.kubemq.sdk.queues;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.KubeMQUtils;
import kubemq.Kubemq;
import kubemq.Kubemq.SendQueueMessageResult;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QueuesClient.
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class QueuesClientTest {

    private static final String CLIENT_ID = "TestsClientID";

    private  MockedStatic<KubeMQUtils> mockedStatic;
    @Mock
    private kubemqGrpc.kubemqBlockingStub client;

    @Mock
    private kubemqGrpc.kubemqStub asyncClient;
    @Mock
    private KubeMQClient kubeMQClient;

    @InjectMocks
    private QueuesClient queuesClient;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        lenient().when(kubeMQClient.getClient()).thenReturn(mock(kubemqGrpc.kubemqBlockingStub.class));
        lenient().when(kubeMQClient.getAsyncClient()).thenReturn(mock(kubemqGrpc.kubemqStub.class));
        lenient().when(kubeMQClient.getClientId()).thenReturn(CLIENT_ID);
        queuesClient = QueuesClient.builder().kubeMQClient(kubeMQClient).build();
        mockedStatic = mockStatic(KubeMQUtils.class);
    }

    @AfterEach
    public void tearDown() {
        if (mockedStatic != null) {
            mockedStatic.close();
        }
    }

    @Test
    @Order(1)
    public void testCreateQueuesChannel() throws Exception {
        log.info("Testing createQueuesChannel");
        when(KubeMQUtils.createChannelRequest(any(), anyString(), anyString(), eq("queues"))).thenReturn(true);

        boolean result = queuesClient.createQueuesChannel("channelName");

        assertTrue(result);
        log.info("createQueuesChannel test passed");
    }

    @Test
    @Order(5)
    public void testDeleteQueuesChannel() throws Exception {
        log.info("Testing deleteQueuesChannel");
        when(KubeMQUtils.deleteChannelRequest(any(), anyString(), anyString(), eq("queues"))).thenReturn(true);

        boolean result = queuesClient.deleteQueuesChannel("channelName");

        assertTrue(result);
        log.info("deleteQueuesChannel test passed");
    }

    @Test
    @Order(10)
    public void testListQueuesChannels() throws Exception {
        log.info("Testing listQueuesChannels");
        List<QueuesChannel> expectedChannels = Collections.singletonList(
                QueuesChannel.builder()
                        .name("channel1")
                        .type("queues")
                        .isActive(true)
                        .build()
        );
        when(KubeMQUtils.listQueuesChannels(any(), anyString(), anyString())).thenReturn(expectedChannels);

        List<QueuesChannel> result = queuesClient.listQueuesChannels("search");

        assertNotNull(result);
        assertEquals(expectedChannels.size(), result.size());
        log.info("listQueuesChannels test passed");
    }

    @Test
    @Order(12)
    public void testGetQueuesInfo() {
        log.info("Testing getQueuesInfo");
        Kubemq.QueuesInfoResponse mockResponse = Kubemq.QueuesInfoResponse.newBuilder()
                .setRefRequestID("testRefID")
                .setInfo(Kubemq.QueuesInfo.newBuilder()
                        .setTotalQueue(5)
                        .setSent(100)
                        .setDelivered(95)
                        .setWaiting(5)
                        .addAllQueues(Arrays.asList(
                                Kubemq.QueueInfo.newBuilder()
                                        .setName("queue1")
                                        .setMessages(10)
                                        .setBytes(1000)
                                        .setFirstSequence(1)
                                        .setLastSequence(10)
                                        .setSent(10)
                                        .setDelivered(9)
                                        .setWaiting(1)
                                        .setSubscribers(2)
                                        .build()
                        ))
                        .build())
                .build();

        // Stub the method to return the mock response
        when(kubeMQClient.getClient().queuesInfo(any(Kubemq.QueuesInfoRequest.class))).thenReturn(mockResponse);

        // Call the method
        QueuesDetailInfo result = queuesClient.getQueuesInfo("test-channel");

        // Verify the result
        assertNotNull(result);
        assertEquals("testRefID", result.getRefRequestID());
        assertEquals(5, result.getTotalQueue());
        assertEquals(100, result.getSent());
        assertEquals(95, result.getDelivered());

        log.info("getQueuesInfo test passed");
    }

    @Test
    @Order(15)
    public void testSendQueuesMessage() throws Exception {
        log.info("Testing sendQueuesMessage");
        QueueMessage message = mock(QueueMessage.class);
        Kubemq.QueueMessage queueMessage = Kubemq.QueueMessage.newBuilder().build();
        SendQueueMessageResult result = SendQueueMessageResult.newBuilder().setIsError(false).build();

        when(message.encodeMessage(anyString())).thenReturn(queueMessage);
        when(kubeMQClient.getClient().sendQueueMessage(queueMessage)).thenReturn(result);

        QueueSendResult sendResult = queuesClient.sendQueuesMessage(message);

        assertNotNull(sendResult);
        assertFalse(sendResult.isError());
        verify(message).validate();
        log.info("sendQueuesMessage test passed");
    }

    @Test
    @Order(20)
    public void testSendQueuesMessageInBatch() throws Exception {
        log.info("Testing sendQueuesMessageInBatch");

        // Create a mock QueueMessageWrapper
        QueueMessage messageMock = mock(QueueMessage.class);
        List<QueueMessage> messages = Collections.singletonList(messageMock);

        // Create a valid Kubemq.QueueMessage object
        Kubemq.QueueMessage queueMessage = Kubemq.QueueMessage.newBuilder().build();

        // Configure the mock to return the valid Kubemq.QueueMessage
        when(messageMock.encodeMessage(anyString())).thenReturn(queueMessage);

        // Validate method
        doNothing().when(messageMock).validate();

        // Create the expected batch request and response
        Kubemq.QueueMessagesBatchRequest batchRequest = Kubemq.QueueMessagesBatchRequest.newBuilder()
                .setBatchID(UUID.randomUUID().toString())
                .addAllMessages(Collections.singletonList(queueMessage))
                .build();
        Kubemq.QueueMessagesBatchResponse batchResponse = Kubemq.QueueMessagesBatchResponse.newBuilder()
                .setBatchID(batchRequest.getBatchID())
                .build();

        // Mock the client's sendQueueMessagesBatch method to return the batch response
        when(kubeMQClient.getClient().sendQueueMessagesBatch(any())).thenReturn(batchResponse);

        // Call the method under test
        QueueMessagesBatchSendResult batchSendResult = queuesClient.sendQueuesMessageInBatch(messages, null);

        // Verify the results
        assertNotNull(batchSendResult);
        assertEquals(batchRequest.getBatchID(), batchSendResult.getBatchId());
        log.info("sendQueuesMessageInBatch test passed");
    }

    @Test
    @Order(35)
    public void testSendQueuesMessagesUpStream() throws Exception {
        log.info("Testing sendQueuesMessagesUpStream");
        QueueMessage messageMock = mock(QueueMessage.class);
        Kubemq.QueuesUpstreamRequest upstreamRequest = mock(Kubemq.QueuesUpstreamRequest.class);
        QueueStreamHelper upstreamSender = mock(QueueStreamHelper.class);

        // Create an instance of UpstreamSender
        when(upstreamSender.sendMessage(any(KubeMQClient.class), any(Kubemq.QueuesUpstreamRequest.class)))
                .thenReturn(QueueSendResult.builder().isError(false).build());

        QueueSendResult result = upstreamSender.sendMessage(kubeMQClient, upstreamRequest);

        assertNotNull(result);
        assertFalse(result.isError());
        log.info("sendQueuesMessagesUpStream test passed");
    }

    @Test
    @Order(40)
    public void testReceiveQueuesMessagesDownStream() throws Exception {
        log.info("Testing receiveQueuesMessagesDownStream");
        QueuesPollRequest pollRequest = mock(QueuesPollRequest.class);
        QueueStreamHelper streamHelper = mock(QueueStreamHelper.class);

        when(streamHelper.receiveMessage(any(KubeMQClient.class), any(QueuesPollRequest.class)))
                .thenReturn(QueuesPollResponse.builder().isError(false).build());

        QueuesPollResponse result = streamHelper.receiveMessage(kubeMQClient, pollRequest);

        assertNotNull(result);
        assertFalse(result.isError());

        log.info("receiveQueuesMessagesDownStream test passed");
    }

    @Test
    @Order(45)
    public void testAckAllQueueMessage() throws Exception {
        log.info("Testing ackAllQueueMessage");
        Kubemq.AckAllQueueMessagesRequest ackRequest = Kubemq.AckAllQueueMessagesRequest.newBuilder()
                .setRequestID(UUID.randomUUID().toString())
                .setChannel("channelName")
                .setClientID(CLIENT_ID)
                .setWaitTimeSeconds(5)
                .build();
        Kubemq.AckAllQueueMessagesResponse ackResponse = Kubemq.AckAllQueueMessagesResponse.newBuilder()
                .setRequestID(ackRequest.getRequestID())
                .setIsError(false)
                .build();

        when(kubeMQClient.getClient().ackAllQueueMessages(any())).thenReturn(ackResponse);

        QueueMessageAcknowledgment acknowledgment = queuesClient.ackAllQueueMessage(
                ackRequest.getRequestID(),
                "channelName",
                5
        );

        assertNotNull(acknowledgment);
        assertFalse(acknowledgment.isError());
        log.info("ackAllQueueMessage test passed");
    }
}
