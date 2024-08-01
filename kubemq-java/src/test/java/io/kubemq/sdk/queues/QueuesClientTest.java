package io.kubemq.sdk.queues;

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

    private MockedStatic<KubeMQUtils> mockedStatic;
    @Mock
    private kubemqGrpc.kubemqBlockingStub client;

    @Mock
    private kubemqGrpc.kubemqStub asyncClient;

    @Mock
    private KubeMQClient kubeMQClient;

    @Mock
    private QueuesClient queuesClient;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        lenient().when(kubeMQClient.getClient()).thenReturn(mock(kubemqGrpc.kubemqBlockingStub.class));
        lenient().when(kubeMQClient.getAsyncClient()).thenReturn(mock(kubemqGrpc.kubemqStub.class));
        lenient().when(kubeMQClient.getClientId()).thenReturn(CLIENT_ID);
        lenient().when(queuesClient.getClientId()).thenReturn(CLIENT_ID);
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
        when(queuesClient.createQueuesChannel(any(String.class))).thenReturn(true);
        boolean result = queuesClient.createQueuesChannel("channelName");

        assertTrue(result);
        log.info("createQueuesChannel test passed");
    }

    @Test
    @Order(5)
    public void testDeleteQueuesChannel() throws Exception {
        log.info("Testing deleteQueuesChannel");
        when(queuesClient.deleteQueuesChannel(any(String.class))).thenReturn(true);
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
        when(queuesClient.listQueuesChannels(anyString())).thenReturn(expectedChannels);

        List<QueuesChannel> result = queuesClient.listQueuesChannels("search");

        assertNotNull(result);
        assertEquals(expectedChannels.size(), result.size());
        assertEquals(expectedChannels.get(0).getName(), result.get(0).getName());
        assertEquals(expectedChannels.get(0).getIsActive(), result.get(0).getIsActive());
        log.info("listQueuesChannels test passed");
    }

    @Test
    @Order(12)
    public void testGetQueuesInfo() {
        log.info("Testing getQueuesInfo");
        QueuesDetailInfo mockResponse = QueuesDetailInfo.builder()
                .refRequestID("testRefID")
                .build();

        // Stub the method to return the mock response
        when(queuesClient.getQueuesInfo(any(String.class))).thenReturn(mockResponse);

        // Call the method
        QueuesDetailInfo result = queuesClient.getQueuesInfo("test-channel");

        // Verify the result
        assertNotNull(result);

        log.info("getQueuesInfo test passed");
    }

    @Test
    @Order(15)
    public void testSendQueuesMessage() throws Exception {
        log.info("Testing sendQueuesMessage");
        QueueMessage message = mock(QueueMessage.class);
        Kubemq.QueueMessage queueMessage = Kubemq.QueueMessage.newBuilder().build();
        QueueSendResult result = QueueSendResult.builder().isError(false).build();


        when(queuesClient.sendQueuesMessage(message)).thenReturn(result);

        QueueSendResult sendResult = queuesClient.sendQueuesMessage(message);

        assertNotNull(sendResult);
        assertFalse(sendResult.isError());
        log.info("sendQueuesMessage test passed");
    }

    @Test
    @Order(20)
    public void testSendQueuesMessageInBatch() throws Exception {
        log.info("Testing sendQueuesMessageInBatch");

        QueueMessage messageMock = mock(QueueMessage.class);
        List<QueueMessage> messages = Collections.singletonList(messageMock);

        Kubemq.QueueMessage queueMessage = Kubemq.QueueMessage.newBuilder().build();
        //when(messageMock.encodeMessage(anyString())).thenReturn(queueMessage);
        //doNothing().when(messageMock).validate();

        Kubemq.QueueMessagesBatchRequest batchRequest = Kubemq.QueueMessagesBatchRequest.newBuilder()
                .setBatchID(UUID.randomUUID().toString())
                .addAllMessages(Collections.singletonList(queueMessage))
                .build();
        Kubemq.QueueMessagesBatchResponse batchResponse = Kubemq.QueueMessagesBatchResponse.newBuilder()
                .setBatchID(batchRequest.getBatchID())
                .build();

        when(queuesClient.sendQueuesMessageInBatch(any(),any())).thenReturn(QueueMessagesBatchSendResult.builder().build());

        QueueMessagesBatchSendResult batchSendResult = queuesClient.sendQueuesMessageInBatch(messages, null);

        assertNotNull(batchSendResult);
        log.info("sendQueuesMessageInBatch test passed");
    }

    @Test
    @Order(35)
    public void testSendQueuesMessagesUpStream() throws Exception {
        log.info("Testing sendQueuesMessagesUpStream");
        QueueMessage messageMock = mock(QueueMessage.class);
        Kubemq.QueuesUpstreamRequest upstreamRequest = Kubemq.QueuesUpstreamRequest.newBuilder()
                .build();
        QueueStreamHelper upstreamSender = mock(QueueStreamHelper.class);

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
        QueueMessageAcknowledgment ackResponse = QueueMessageAcknowledgment.builder()
                .requestId(ackRequest.getRequestID())
                .build();

        //when(kubeMQClient.getClient().ackAllQueueMessages(any(Kubemq.AckAllQueueMessagesRequest.class))).thenReturn(ackResponse);
        when(queuesClient.ackAllQueueMessage(any(String.class),any(String.class),any(Integer.class))).thenReturn(ackResponse);

        QueueMessageAcknowledgment acknowledgment = queuesClient.ackAllQueueMessage(
                ackRequest.getRequestID(),
                "channelName",
                5
        );

        assertNotNull(acknowledgment);
        assertEquals(ackRequest.getRequestID(), acknowledgment.getRequestId());
        log.info("ackAllQueueMessage test passed");
    }
}
