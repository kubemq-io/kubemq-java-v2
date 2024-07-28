package io.kubemq.sdk.queues;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.KubeMQUtils;
import kubemq.Kubemq;
import kubemq.Kubemq.SendQueueMessageResult;
import kubemq.kubemqGrpc;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
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

    private static final MockedStatic<KubeMQUtils> mockedStatic = mockStatic(KubeMQUtils.class);
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
        QueueMessageWrapper message = mock(QueueMessageWrapper.class);
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
        QueueMessageWrapper messageMock = mock(QueueMessageWrapper.class);
        List<QueueMessageWrapper> messages = Collections.singletonList(messageMock);

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


//    @Test
//    @Order(25)
//    public void testReceiveQueuesMessages() throws Exception {
//        log.info("Testing receiveQueuesMessages");
//        Kubemq.ReceiveQueueMessagesRequest receiveRequest = Kubemq.ReceiveQueueMessagesRequest.newBuilder()
//                .setRequestID(UUID.randomUUID().toString())
//                .setClientID(CLIENT_ID)
//                .setChannel("channelName")
//                .setMaxNumberOfMessages(10)
//                .setWaitTimeSeconds(5)
//                .build();
//        Kubemq.ReceiveQueueMessagesResponse receiveResponse = Kubemq.ReceiveQueueMessagesResponse.newBuilder()
//                .setRequestID(receiveRequest.getRequestID())
//                .setMessagesExpired(0)
//                .setMessagesReceived(1)
//                .build();
//
//        when(kubeMQClient.getClient().receiveQueueMessages(any())).thenReturn(receiveResponse);
//
//        QueueMessagesReceived messagesReceived = queuesClient.receiveQueuesMessages(
//                receiveRequest.getRequestID(),
//                "channelName",
//                10,
//                5,
//                false
//        );
//
//        assertNotNull(messagesReceived);
//        assertEquals(1, messagesReceived.getMessagesReceived());
//        log.info("receiveQueuesMessages test passed");
//    }

    @Test
    @Order(30)
    public void testStreamQueuesMessages() throws Exception {
        log.info("Testing streamQueuesMessages");
        StreamObserver<Kubemq.StreamQueueMessagesResponse> responseObserver = mock(StreamObserver.class);
        StreamObserver<Kubemq.StreamQueueMessagesRequest> requestObserver = mock(StreamObserver.class);

        when(kubeMQClient.getAsyncClient().streamQueueMessage(any())).thenReturn(requestObserver);

        StreamObserver<Kubemq.StreamQueueMessagesRequest> result = queuesClient.streamQueuesMessages(responseObserver);

        assertNotNull(result);
        assertEquals(requestObserver, result);
        log.info("streamQueuesMessages test passed");
    }

    @Test
    @Order(35)
    public void testSendQueuesMessagesUpStream() throws Exception {
        log.info("Testing sendQueuesMessagesUpStream");
        //StreamObserver<Kubemq.QueuesUpstreamResponse> responseObserver = mock(StreamObserver.class);
        StreamObserver<Kubemq.QueuesUpstreamRequest> requestObserver = mock(StreamObserver.class);
        UpstreamSender upstreamSender = mock(UpstreamSender.class);

        when(kubeMQClient.getAsyncClient().queuesUpstream(any())).thenReturn(requestObserver);

        StreamObserver<Kubemq.QueuesUpstreamRequest> result = queuesClient.sendMessageQueuesUpStream(upstreamSender);

        assertNotNull(result);
        assertEquals(requestObserver, result);
        log.info("sendQueuesMessagesUpStream test passed");
    }

    @Test
    @Order(40)
    public void testReceiveQueuesMessagesDownStream() throws Exception {
        log.info("Testing receiveQueuesMessagesDownStream");

        StreamObserver<Kubemq.QueuesDownstreamRequest> requestObserver = mock(StreamObserver.class);
        when(kubeMQClient.getAsyncClient()).thenReturn(asyncClient);
        when(asyncClient.queuesDownstream(any())).thenReturn(requestObserver);

        QueuesPollRequest queuesPollRequest = mock(QueuesPollRequest.class);

        queuesClient.receiveQueuesMessagesDownStream(queuesPollRequest);

        ArgumentCaptor<StreamObserver> captor = ArgumentCaptor.forClass(StreamObserver.class);
        verify(asyncClient).queuesDownstream(captor.capture());

        StreamObserver<Kubemq.QueuesDownstreamResponse> capturedResponseObserver = captor.getValue();
        assertNotNull(capturedResponseObserver);

        log.info("receiveQueuesMessagesDownStream test passed");
    }

//    @Test
//    @Order(40)
//    public void testReceiveQueuesMessagesDownStream() throws Exception {
//        log.info("Testing receiveQueuesMessagesDownStream");
//        StreamObserver<Kubemq.QueuesDownstreamResponse> responseObserver = mock(StreamObserver.class);
//        StreamObserver<Kubemq.QueuesDownstreamRequest> requestObserver = mock(StreamObserver.class);
//
//        QueuesPollRequest queuesPollRequest = mock(QueuesPollRequest.class);
//
//        when(kubeMQClient.getAsyncClient().queuesDownstream(any())).thenReturn(requestObserver);
//
//         queuesClient.receiveQueuesMessagesDownStream(queuesPollRequest);
//
//        verify(asyncClient).queuesDownstream(responseObserver);
//        log.info("receiveQueuesMessagesDownStream test passed");
//    }

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
