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
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

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
    public void testReceiveQueuesMessages() throws Exception {
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
    @Order(50)
    void testWaitingWithSingleMessage() {
        String channel = "test-channel";
        int maxMessages = 10;
        int waitTimeoutInSeconds = 5;

        QueueMessagesWaiting mockResult = createMockResult(1, false, null);
        when(queuesClient.waiting(eq(channel), eq(maxMessages), eq(waitTimeoutInSeconds)))
                .thenReturn(mockResult);

        QueueMessagesWaiting result = queuesClient.waiting(channel, maxMessages, waitTimeoutInSeconds);

        assertNotNull(result);
        assertFalse(result.isError());
        assertNull(result.getError());
        assertEquals(1, result.getMessages().size());
        verifyMessageContent(result.getMessages().get(0));

        verify(queuesClient).waiting(channel, maxMessages, waitTimeoutInSeconds);
    }

    @Test
    @Order(51)
    void testWaitingWithMaxMessages() {
        String channel = "test-channel";
        int maxMessages = 5;
        int waitTimeoutInSeconds = 5;

        QueueMessagesWaiting mockResult = createMockResult(maxMessages, false, null);
        when(queuesClient.waiting(eq(channel), eq(maxMessages), eq(waitTimeoutInSeconds)))
                .thenReturn(mockResult);

        QueueMessagesWaiting result = queuesClient.waiting(channel, maxMessages, waitTimeoutInSeconds);

        assertNotNull(result);
        assertFalse(result.isError());
        assertNull(result.getError());
        assertEquals(maxMessages, result.getMessages().size());
        result.getMessages().forEach(this::verifyMessageContent);

        verify(queuesClient).waiting(channel, maxMessages, waitTimeoutInSeconds);
    }

    @Test
    @Order(52)

    void testWaitingWithNoMessages() {
        String channel = "empty-channel";
        int maxMessages = 10;
        int waitTimeoutInSeconds = 5;

        QueueMessagesWaiting mockResult = createMockResult(0, false, null);
        when(queuesClient.waiting(eq(channel), eq(maxMessages), eq(waitTimeoutInSeconds)))
                .thenReturn(mockResult);

        QueueMessagesWaiting result = queuesClient.waiting(channel, maxMessages, waitTimeoutInSeconds);

        assertNotNull(result);
        assertFalse(result.isError());
        assertNull(result.getError());
        assertTrue(result.getMessages().isEmpty());

        verify(queuesClient).waiting(channel, maxMessages, waitTimeoutInSeconds);
    }

    @Test
    @Order(53)
    void testWaitingWithError() {
        String channel = "error-channel";
        int maxMessages = 10;
        int waitTimeoutInSeconds = 5;
        String errorMessage = "Test error message";

        QueueMessagesWaiting mockResult = createMockResult(0, true, errorMessage);
        when(queuesClient.waiting(eq(channel), eq(maxMessages), eq(waitTimeoutInSeconds)))
                .thenReturn(mockResult);

        QueueMessagesWaiting result = queuesClient.waiting(channel, maxMessages, waitTimeoutInSeconds);

        assertNotNull(result);
        assertTrue(result.isError());
        assertEquals(errorMessage, result.getError());
        assertTrue(result.getMessages().isEmpty());

        verify(queuesClient).waiting(channel, maxMessages, waitTimeoutInSeconds);
    }

    @Test
    @Order(54)
    void testWaitingWithShortTimeout() {
        testWaitingWithTimeout(1);
    }

    @Test
    @Order(55)
    void testWaitingWithMediumTimeout() {
        testWaitingWithTimeout(5);
    }

    @Test
    @Order(56)
    void testWaitingWithLongTimeout() {
        testWaitingWithTimeout(100);
    }

    private void testWaitingWithTimeout(int waitTimeoutInSeconds) {
        String channel = "test-channel";
        int maxMessages = 10;

        QueueMessagesWaiting mockResult = createMockResult(1, false, null);
        when(queuesClient.waiting(eq(channel), eq(maxMessages), eq(waitTimeoutInSeconds)))
                .thenReturn(mockResult);

        QueueMessagesWaiting result = queuesClient.waiting(channel, maxMessages, waitTimeoutInSeconds);

        assertNotNull(result);
        assertFalse(result.isError());
        assertNull(result.getError());
        assertEquals(1, result.getMessages().size());

        verify(queuesClient).waiting(channel, maxMessages, waitTimeoutInSeconds);
    }

    @Test
    @Order(57)
    void testWaitingWithNullChannel() {
        int maxMessages = 10;
        int waitTimeoutInSeconds = 5;

        doThrow(new IllegalArgumentException("channel cannot be null."))
                .when(queuesClient).waiting(isNull(), anyInt(), anyInt());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> queuesClient.waiting(null, maxMessages, waitTimeoutInSeconds));

        assertEquals("channel cannot be null.", exception.getMessage());
    }


    @Test
    @Order(58)
    void testWaitingWithZeroMaxMessages() {
        String channel = "test-channel";
        int waitTimeoutInSeconds = 5;

        doThrow(new IllegalArgumentException("maxMessages must be greater than 0."))
                .when(queuesClient).waiting(anyString(), eq(0), anyInt());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> queuesClient.waiting(channel, 0, waitTimeoutInSeconds));

        assertEquals("maxMessages must be greater than 0.", exception.getMessage());
    }

    @Test
    @Order(59)
    void testWaitingWithNegativeMaxMessages() {
        String channel = "test-channel";
        int waitTimeoutInSeconds = 5;

        doThrow(new IllegalArgumentException("maxMessages must be greater than 0."))
                .when(queuesClient).waiting(anyString(), eq(-10), anyInt());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> queuesClient.waiting(channel, -10, waitTimeoutInSeconds));

        assertEquals("maxMessages must be greater than 0.", exception.getMessage());
    }

    @Test
    @Order(60)
    void testWaitingWithZeroWaitTimeout() {
        String channel = "test-channel";
        int maxMessages = 10;

        doThrow(new IllegalArgumentException("waitTimeoutInSeconds must be greater than 0."))
                .when(queuesClient).waiting(anyString(), anyInt(), eq(0));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> queuesClient.waiting(channel, maxMessages, 0));

        assertEquals("waitTimeoutInSeconds must be greater than 0.", exception.getMessage());
    }

    @Test
    @Order(61)
    void testWaitingWithNegativeWaitTimeout() {
        String channel = "test-channel";
        int maxMessages = 10;

        doThrow(new IllegalArgumentException("waitTimeoutInSeconds must be greater than 0."))
                .when(queuesClient).waiting(anyString(), anyInt(), eq(-5));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> queuesClient.waiting(channel, maxMessages, -5));

        assertEquals("waitTimeoutInSeconds must be greater than 0.", exception.getMessage());
    }

    private QueueMessagesWaiting createMockResult(int messageCount, boolean isError, String errorMessage) {
        List<QueueMessageWaitingPulled> messages = IntStream.range(0, messageCount)
                .mapToObj(i -> QueueMessageWaitingPulled.builder()
                        .id("test-message-id-" + i)
                        .channel("test-channel")
                        .metadata("test-metadata")
                        .body(("test-body-" + i).getBytes())
                        .fromClientId("test-sender-client")
                        .tags(Collections.singletonMap("key", "value"))
                        .timestamp(Instant.now())
                        .sequence((long) i)
                        .receiveCount(1)
                        .isReRouted(false)
                        .reRouteFromQueue("")
                        .expiredAt(Instant.now().plusSeconds(3600))
                        .delayedTo(Instant.now())
                        .receiverClientId(CLIENT_ID)
                        .build())
                .collect(Collectors.toList());

        return QueueMessagesWaiting.builder()
                .messages(messages)
                .isError(isError)
                .error(errorMessage)
                .build();
    }

    private void verifyMessageContent(QueueMessageWaitingPulled message) {
        assertNotNull(message);
        assertTrue(message.getId().startsWith("test-message-id-"));
        assertEquals("test-channel", message.getChannel());
        assertEquals("test-metadata", message.getMetadata());
        assertTrue(new String(message.getBody()).startsWith("test-body-"));
        assertEquals("test-sender-client", message.getFromClientId());
        assertEquals(Collections.singletonMap("key", "value"), message.getTags());
        assertNotNull(message.getTimestamp());
        assertTrue(message.getSequence() >= 0);
        assertEquals(1, message.getReceiveCount());
        assertFalse(message.isReRouted());
        assertEquals("", message.getReRouteFromQueue());
        assertNotNull(message.getExpiredAt());
        assertNotNull(message.getDelayedTo());
        assertEquals(CLIENT_ID, message.getReceiverClientId());
    }

    @Test
    @Order(70)
    void testPullWithSingleMessage() {
        String channel = "test-channel";
        int maxMessages = 10;
        int waitTimeoutInSeconds = 5;

        QueueMessagesPulled mockResult = createMockPullResult(1, false, null);
        when(queuesClient.pull(eq(channel), eq(maxMessages), eq(waitTimeoutInSeconds)))
                .thenReturn(mockResult);

        QueueMessagesPulled result = queuesClient.pull(channel, maxMessages, waitTimeoutInSeconds);

        assertNotNull(result);
        assertFalse(result.isError());
        assertNull(result.getError());
        assertEquals(1, result.getMessages().size());
        verifyPulledMessageContent(result.getMessages().get(0));

        verify(queuesClient).pull(channel, maxMessages, waitTimeoutInSeconds);
    }

    @Test
    @Order(71)
    void testPullWithMaxMessages() {
        String channel = "test-channel";
        int maxMessages = 5;
        int waitTimeoutInSeconds = 5;

        QueueMessagesPulled mockResult = createMockPullResult(maxMessages, false, null);
        when(queuesClient.pull(eq(channel), eq(maxMessages), eq(waitTimeoutInSeconds)))
                .thenReturn(mockResult);

        QueueMessagesPulled result = queuesClient.pull(channel, maxMessages, waitTimeoutInSeconds);

        assertNotNull(result);
        assertFalse(result.isError());
        assertNull(result.getError());
        assertEquals(maxMessages, result.getMessages().size());
        result.getMessages().forEach(this::verifyPulledMessageContent);

        verify(queuesClient).pull(channel, maxMessages, waitTimeoutInSeconds);
    }

    @Test
    @Order(72)
    void testPullWithNoMessages() {
        String channel = "empty-channel";
        int maxMessages = 10;
        int waitTimeoutInSeconds = 5;

        QueueMessagesPulled mockResult = createMockPullResult(0, false, null);
        when(queuesClient.pull(eq(channel), eq(maxMessages), eq(waitTimeoutInSeconds)))
                .thenReturn(mockResult);

        QueueMessagesPulled result = queuesClient.pull(channel, maxMessages, waitTimeoutInSeconds);

        assertNotNull(result);
        assertFalse(result.isError());
        assertNull(result.getError());
        assertTrue(result.getMessages().isEmpty());

        verify(queuesClient).pull(channel, maxMessages, waitTimeoutInSeconds);
    }

    @Test
    @Order(73)
    void testPullWithError() {
        String channel = "error-channel";
        int maxMessages = 10;
        int waitTimeoutInSeconds = 5;
        String errorMessage = "Test error message";

        QueueMessagesPulled mockResult = createMockPullResult(0, true, errorMessage);
        when(queuesClient.pull(eq(channel), eq(maxMessages), eq(waitTimeoutInSeconds)))
                .thenReturn(mockResult);

        QueueMessagesPulled result = queuesClient.pull(channel, maxMessages, waitTimeoutInSeconds);

        assertNotNull(result);
        assertTrue(result.isError());
        assertEquals(errorMessage, result.getError());
        assertTrue(result.getMessages().isEmpty());

        verify(queuesClient).pull(channel, maxMessages, waitTimeoutInSeconds);
    }

    @Test
    @Order(74)
    void testPullWithShortTimeout() {
        testPullWithTimeout(1);
    }

    @Test
    @Order(75)
    void testPullWithMediumTimeout() {
        testPullWithTimeout(5);
    }

    @Test
    @Order(76)
    void testPullWithLongTimeout() {
        testPullWithTimeout(100);
    }

    private void testPullWithTimeout(int waitTimeoutInSeconds) {
        String channel = "test-channel";
        int maxMessages = 10;

        QueueMessagesPulled mockResult = createMockPullResult(1, false, null);
        when(queuesClient.pull(eq(channel), eq(maxMessages), eq(waitTimeoutInSeconds)))
                .thenReturn(mockResult);

        QueueMessagesPulled result = queuesClient.pull(channel, maxMessages, waitTimeoutInSeconds);

        assertNotNull(result);
        assertFalse(result.isError());
        assertNull(result.getError());
        assertEquals(1, result.getMessages().size());

        verify(queuesClient).pull(channel, maxMessages, waitTimeoutInSeconds);
    }

    @Test
    @Order(77)
    void testPullWithNullChannel() {
        int maxMessages = 10;
        int waitTimeoutInSeconds = 5;

        doThrow(new IllegalArgumentException("channel cannot be null."))
                .when(queuesClient).pull(isNull(), anyInt(), anyInt());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> queuesClient.pull(null, maxMessages, waitTimeoutInSeconds));

        assertEquals("channel cannot be null.", exception.getMessage());
    }

    @Test
    @Order(78)
    void testPullWithZeroMaxMessages() {
        String channel = "test-channel";
        int waitTimeoutInSeconds = 5;

        doThrow(new IllegalArgumentException("maxMessages must be greater than 0."))
                .when(queuesClient).pull(anyString(), eq(0), anyInt());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> queuesClient.pull(channel, 0, waitTimeoutInSeconds));

        assertEquals("maxMessages must be greater than 0.", exception.getMessage());
    }

    @Test
    @Order(79)
    void testPullWithNegativeMaxMessages() {
        String channel = "test-channel";
        int waitTimeoutInSeconds = 5;

        doThrow(new IllegalArgumentException("maxMessages must be greater than 0."))
                .when(queuesClient).pull(anyString(), eq(-10), anyInt());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> queuesClient.pull(channel, -10, waitTimeoutInSeconds));

        assertEquals("maxMessages must be greater than 0.", exception.getMessage());
    }

    @Test
    @Order(80)
    void testPullWithZeroWaitTimeout() {
        String channel = "test-channel";
        int maxMessages = 10;

        doThrow(new IllegalArgumentException("waitTimeoutInSeconds must be greater than 0."))
                .when(queuesClient).pull(anyString(), anyInt(), eq(0));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> queuesClient.pull(channel, maxMessages, 0));

        assertEquals("waitTimeoutInSeconds must be greater than 0.", exception.getMessage());
    }

    @Test
    @Order(81)
    void testPullWithNegativeWaitTimeout() {
        String channel = "test-channel";
        int maxMessages = 10;

        doThrow(new IllegalArgumentException("waitTimeoutInSeconds must be greater than 0."))
                .when(queuesClient).pull(anyString(), anyInt(), eq(-5));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> queuesClient.pull(channel, maxMessages, -5));

        assertEquals("waitTimeoutInSeconds must be greater than 0.", exception.getMessage());
    }

    private QueueMessagesPulled createMockPullResult(int messageCount, boolean isError, String errorMessage) {
        List<QueueMessageWaitingPulled> messages = IntStream.range(0, messageCount)
                .mapToObj(i -> QueueMessageWaitingPulled.builder()
                        .id("test-message-id-" + i)
                        .channel("test-channel")
                        .metadata("test-metadata")
                        .body(("test-body-" + i).getBytes())
                        .fromClientId("test-sender-client")
                        .tags(Collections.singletonMap("key", "value"))
                        .timestamp(Instant.now())
                        .sequence((long) i)
                        .receiveCount(1)
                        .isReRouted(false)
                        .reRouteFromQueue("")
                        .expiredAt(Instant.now().plusSeconds(3600))
                        .delayedTo(Instant.now())
                        .receiverClientId(CLIENT_ID)
                        .build())
                .collect(Collectors.toList());

        return QueueMessagesPulled.builder()
                .messages(messages)
                .isError(isError)
                .error(errorMessage)
                .build();
    }

    private void verifyPulledMessageContent(QueueMessageWaitingPulled message) {
        assertNotNull(message);
        assertTrue(message.getId().startsWith("test-message-id-"));
        assertEquals("test-channel", message.getChannel());
        assertEquals("test-metadata", message.getMetadata());
        assertTrue(new String(message.getBody()).startsWith("test-body-"));
        assertEquals("test-sender-client", message.getFromClientId());
        assertEquals(Collections.singletonMap("key", "value"), message.getTags());
        assertNotNull(message.getTimestamp());
        assertTrue(message.getSequence() >= 0);
        assertEquals(1, message.getReceiveCount());
        assertFalse(message.isReRouted());
        assertEquals("", message.getReRouteFromQueue());
        assertNotNull(message.getExpiredAt());
        assertNotNull(message.getDelayedTo());
        assertEquals(CLIENT_ID, message.getReceiverClientId());
    }
}
