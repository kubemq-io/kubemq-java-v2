package io.kubemq.sdk.unit.queues;

import com.google.protobuf.ByteString;
import io.kubemq.sdk.queues.*;
import kubemq.Kubemq;
import kubemq.kubemqGrpc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QueuesClient - waiting() and pull() methods.
 */
@ExtendWith(MockitoExtension.class)
class QueuesClientTest {

    @Mock
    private kubemqGrpc.kubemqBlockingStub mockBlockingStub;

    private QueuesClient client;

    @BeforeEach
    void setup() {
        client = QueuesClient.builder()
                .address("localhost:50000")
                .clientId("test-client")
                .build();
    }

    @AfterEach
    void teardown() {
        if (client != null) {
            client.close();
        }
    }

    @Nested
    @DisplayName("Waiting Method Validation Tests")
    class WaitingValidationTests {

        @Test
        @DisplayName("Q-21: waiting with null channel throws IllegalArgumentException")
        void waiting_nullChannel_throwsIllegalArgumentException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> client.waiting(null, 10, 5)
            );
            assertTrue(exception.getMessage().contains("channel"));
        }

        @Test
        @DisplayName("Q-22: waiting with zero maxMessages throws IllegalArgumentException")
        void waiting_zeroMaxMessages_throwsIllegalArgumentException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> client.waiting("test-queue", 0, 5)
            );
            assertTrue(exception.getMessage().contains("maxMessages"));
        }

        @Test
        @DisplayName("Q-23: waiting with negative maxMessages throws IllegalArgumentException")
        void waiting_negativeMaxMessages_throwsIllegalArgumentException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> client.waiting("test-queue", -1, 5)
            );
            assertTrue(exception.getMessage().contains("maxMessages"));
        }

        @Test
        @DisplayName("Q-24: waiting with zero waitTimeout throws IllegalArgumentException")
        void waiting_zeroWaitTimeout_throwsIllegalArgumentException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> client.waiting("test-queue", 10, 0)
            );
            assertTrue(exception.getMessage().contains("waitTimeoutInSeconds"));
        }

        @Test
        @DisplayName("Q-25: waiting with negative waitTimeout throws IllegalArgumentException")
        void waiting_negativeWaitTimeout_throwsIllegalArgumentException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> client.waiting("test-queue", 10, -1)
            );
            assertTrue(exception.getMessage().contains("waitTimeoutInSeconds"));
        }
    }

    @Nested
    @DisplayName("Waiting Method Success Tests")
    class WaitingSuccessTests {

        @Test
        @DisplayName("Q-26: waiting returns empty result when no messages")
        void waiting_noMessages_returnsEmptyResult() {
            Kubemq.ReceiveQueueMessagesResponse emptyResponse = Kubemq.ReceiveQueueMessagesResponse.newBuilder()
                    .setIsError(false)
                    .build();

            when(mockBlockingStub.receiveQueueMessages(any())).thenReturn(emptyResponse);
            client.setBlockingStub(mockBlockingStub);

            QueueMessagesWaiting result = client.waiting("test-queue", 10, 5);

            assertNotNull(result);
            assertFalse(result.isError());
            assertTrue(result.getMessages().isEmpty());
        }

        @Test
        @DisplayName("Q-27: waiting returns messages when available")
        void waiting_withMessages_returnsMessages() {
            Kubemq.ReceiveQueueMessagesResponse response = Kubemq.ReceiveQueueMessagesResponse.newBuilder()
                    .setIsError(false)
                    .addMessages(Kubemq.QueueMessage.newBuilder()
                            .setMessageID("msg-1")
                            .setChannel("test-queue")
                            .setBody(ByteString.copyFromUtf8("body-1"))
                            .build())
                    .addMessages(Kubemq.QueueMessage.newBuilder()
                            .setMessageID("msg-2")
                            .setChannel("test-queue")
                            .setBody(ByteString.copyFromUtf8("body-2"))
                            .build())
                    .build();

            when(mockBlockingStub.receiveQueueMessages(any())).thenReturn(response);
            client.setBlockingStub(mockBlockingStub);

            QueueMessagesWaiting result = client.waiting("test-queue", 10, 5);

            assertNotNull(result);
            assertFalse(result.isError());
            assertEquals(2, result.getMessages().size());
        }

        @Test
        @DisplayName("Q-28: waiting returns error result when server returns error")
        void waiting_serverError_returnsErrorResult() {
            Kubemq.ReceiveQueueMessagesResponse errorResponse = Kubemq.ReceiveQueueMessagesResponse.newBuilder()
                    .setIsError(true)
                    .setError("Queue not found")
                    .build();

            when(mockBlockingStub.receiveQueueMessages(any())).thenReturn(errorResponse);
            client.setBlockingStub(mockBlockingStub);

            QueueMessagesWaiting result = client.waiting("test-queue", 10, 5);

            assertNotNull(result);
            assertTrue(result.isError());
            assertEquals("Queue not found", result.getError());
        }

        @Test
        @DisplayName("Q-29: waiting uses isPeak=true")
        void waiting_usesPeakTrue() {
            Kubemq.ReceiveQueueMessagesResponse response = Kubemq.ReceiveQueueMessagesResponse.newBuilder()
                    .setIsError(false)
                    .build();

            when(mockBlockingStub.receiveQueueMessages(any())).thenReturn(response);
            client.setBlockingStub(mockBlockingStub);

            client.waiting("test-queue", 10, 5);

            verify(mockBlockingStub).receiveQueueMessages(argThat(request ->
                    request.getIsPeak() == true
            ));
        }
    }

    @Nested
    @DisplayName("Pull Method Validation Tests")
    class PullValidationTests {

        @Test
        @DisplayName("Q-30: pull with null channel throws IllegalArgumentException")
        void pull_nullChannel_throwsIllegalArgumentException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> client.pull(null, 10, 5)
            );
            assertTrue(exception.getMessage().contains("channel"));
        }

        @Test
        @DisplayName("Q-31: pull with zero maxMessages throws IllegalArgumentException")
        void pull_zeroMaxMessages_throwsIllegalArgumentException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> client.pull("test-queue", 0, 5)
            );
            assertTrue(exception.getMessage().contains("maxMessages"));
        }

        @Test
        @DisplayName("Q-32: pull with negative maxMessages throws IllegalArgumentException")
        void pull_negativeMaxMessages_throwsIllegalArgumentException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> client.pull("test-queue", -1, 5)
            );
            assertTrue(exception.getMessage().contains("maxMessages"));
        }

        @Test
        @DisplayName("Q-33: pull with zero waitTimeout throws IllegalArgumentException")
        void pull_zeroWaitTimeout_throwsIllegalArgumentException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> client.pull("test-queue", 10, 0)
            );
            assertTrue(exception.getMessage().contains("waitTimeoutInSeconds"));
        }

        @Test
        @DisplayName("Q-34: pull with negative waitTimeout throws IllegalArgumentException")
        void pull_negativeWaitTimeout_throwsIllegalArgumentException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> client.pull("test-queue", 10, -1)
            );
            assertTrue(exception.getMessage().contains("waitTimeoutInSeconds"));
        }
    }

    @Nested
    @DisplayName("Pull Method Success Tests")
    class PullSuccessTests {

        @Test
        @DisplayName("Q-35: pull returns empty result when no messages")
        void pull_noMessages_returnsEmptyResult() {
            Kubemq.ReceiveQueueMessagesResponse emptyResponse = Kubemq.ReceiveQueueMessagesResponse.newBuilder()
                    .setIsError(false)
                    .build();

            when(mockBlockingStub.receiveQueueMessages(any())).thenReturn(emptyResponse);
            client.setBlockingStub(mockBlockingStub);

            QueueMessagesPulled result = client.pull("test-queue", 10, 5);

            assertNotNull(result);
            assertFalse(result.isError());
            assertTrue(result.getMessages().isEmpty());
        }

        @Test
        @DisplayName("Q-36: pull returns messages when available")
        void pull_withMessages_returnsMessages() {
            Kubemq.ReceiveQueueMessagesResponse response = Kubemq.ReceiveQueueMessagesResponse.newBuilder()
                    .setIsError(false)
                    .addMessages(Kubemq.QueueMessage.newBuilder()
                            .setMessageID("msg-1")
                            .setChannel("test-queue")
                            .setBody(ByteString.copyFromUtf8("body-1"))
                            .build())
                    .addMessages(Kubemq.QueueMessage.newBuilder()
                            .setMessageID("msg-2")
                            .setChannel("test-queue")
                            .setBody(ByteString.copyFromUtf8("body-2"))
                            .build())
                    .build();

            when(mockBlockingStub.receiveQueueMessages(any())).thenReturn(response);
            client.setBlockingStub(mockBlockingStub);

            QueueMessagesPulled result = client.pull("test-queue", 10, 5);

            assertNotNull(result);
            assertFalse(result.isError());
            assertEquals(2, result.getMessages().size());
        }

        @Test
        @DisplayName("Q-37: pull returns error result when server returns error")
        void pull_serverError_returnsErrorResult() {
            Kubemq.ReceiveQueueMessagesResponse errorResponse = Kubemq.ReceiveQueueMessagesResponse.newBuilder()
                    .setIsError(true)
                    .setError("Queue not found")
                    .build();

            when(mockBlockingStub.receiveQueueMessages(any())).thenReturn(errorResponse);
            client.setBlockingStub(mockBlockingStub);

            QueueMessagesPulled result = client.pull("test-queue", 10, 5);

            assertNotNull(result);
            assertTrue(result.isError());
            assertEquals("Queue not found", result.getError());
        }

        @Test
        @DisplayName("Q-38: pull uses isPeak=false")
        void pull_usesPeakFalse() {
            Kubemq.ReceiveQueueMessagesResponse response = Kubemq.ReceiveQueueMessagesResponse.newBuilder()
                    .setIsError(false)
                    .build();

            when(mockBlockingStub.receiveQueueMessages(any())).thenReturn(response);
            client.setBlockingStub(mockBlockingStub);

            client.pull("test-queue", 10, 5);

            verify(mockBlockingStub).receiveQueueMessages(argThat(request ->
                    request.getIsPeak() == false
            ));
        }
    }

    @Nested
    @DisplayName("SendQueuesMessage Validation Tests")
    class SendQueuesMessageValidationTests {

        @Test
        @DisplayName("Q-39: sendQueuesMessage validates message")
        void sendQueuesMessage_validatesMessage() {
            QueueMessage invalidMessage = QueueMessage.builder()
                    .channel(null)  // Invalid - null channel
                    .body("test".getBytes())
                    .build();

            assertThrows(IllegalArgumentException.class, () ->
                    client.sendQueuesMessage(invalidMessage)
            );
        }
    }

    @Nested
    @DisplayName("ReceiveQueuesMessages Validation Tests")
    class ReceiveQueuesMessagesValidationTests {

        @Test
        @DisplayName("Q-40: receiveQueuesMessages validates request")
        void receiveQueuesMessages_validatesRequest() {
            QueuesPollRequest invalidRequest = QueuesPollRequest.builder()
                    .channel(null)  // Invalid - null channel
                    .pollMaxMessages(10)
                    .pollWaitTimeoutInSeconds(5)
                    .build();

            assertThrows(IllegalArgumentException.class, () ->
                    client.receiveQueuesMessages(invalidRequest)
            );
        }
    }

    @Nested
    @DisplayName("Channel Operations Tests")
    class ChannelOperationsTests {

        @Test
        @DisplayName("Q-41: createQueuesChannel calls KubeMQUtils")
        void createQueuesChannel_callsKubeMQUtils() {
            Kubemq.Response successResponse = Kubemq.Response.newBuilder()
                    .setExecuted(true)
                    .build();

            when(mockBlockingStub.sendRequest(any())).thenReturn(successResponse);
            client.setBlockingStub(mockBlockingStub);

            boolean result = client.createQueuesChannel("test-queue");

            assertTrue(result);
            verify(mockBlockingStub).sendRequest(any());
        }

        @Test
        @DisplayName("Q-42: deleteQueuesChannel calls KubeMQUtils")
        void deleteQueuesChannel_callsKubeMQUtils() {
            Kubemq.Response successResponse = Kubemq.Response.newBuilder()
                    .setExecuted(true)
                    .build();

            when(mockBlockingStub.sendRequest(any())).thenReturn(successResponse);
            client.setBlockingStub(mockBlockingStub);

            boolean result = client.deleteQueuesChannel("test-queue");

            assertTrue(result);
            verify(mockBlockingStub).sendRequest(any());
        }

        @Test
        @DisplayName("Q-43: listQueuesChannels calls KubeMQUtils")
        void listQueuesChannels_callsKubeMQUtils() {
            // Create empty response
            Kubemq.Response response = Kubemq.Response.newBuilder()
                    .setExecuted(true)
                    .setBody(ByteString.copyFromUtf8("[]"))  // Empty JSON array
                    .build();

            when(mockBlockingStub.sendRequest(any())).thenReturn(response);
            client.setBlockingStub(mockBlockingStub);

            java.util.List<QueuesChannel> channels = client.listQueuesChannels("test");

            assertNotNull(channels);
            verify(mockBlockingStub).sendRequest(any());
        }
    }

    @Nested
    @DisplayName("Request Parameter Tests")
    class RequestParameterTests {

        @Test
        @DisplayName("Q-44: waiting sets correct request parameters")
        void waiting_setsCorrectRequestParameters() {
            Kubemq.ReceiveQueueMessagesResponse response = Kubemq.ReceiveQueueMessagesResponse.newBuilder()
                    .setIsError(false)
                    .build();

            when(mockBlockingStub.receiveQueueMessages(any())).thenReturn(response);
            client.setBlockingStub(mockBlockingStub);

            client.waiting("my-queue", 25, 15);

            verify(mockBlockingStub).receiveQueueMessages(argThat(request ->
                    request.getChannel().equals("my-queue") &&
                    request.getMaxNumberOfMessages() == 25 &&
                    request.getWaitTimeSeconds() == 15 &&
                    request.getIsPeak() == true &&
                    request.getClientID().equals("test-client")
            ));
        }

        @Test
        @DisplayName("Q-45: pull sets correct request parameters")
        void pull_setsCorrectRequestParameters() {
            Kubemq.ReceiveQueueMessagesResponse response = Kubemq.ReceiveQueueMessagesResponse.newBuilder()
                    .setIsError(false)
                    .build();

            when(mockBlockingStub.receiveQueueMessages(any())).thenReturn(response);
            client.setBlockingStub(mockBlockingStub);

            client.pull("my-queue", 30, 20);

            verify(mockBlockingStub).receiveQueueMessages(argThat(request ->
                    request.getChannel().equals("my-queue") &&
                    request.getMaxNumberOfMessages() == 30 &&
                    request.getWaitTimeSeconds() == 20 &&
                    request.getIsPeak() == false &&
                    request.getClientID().equals("test-client")
            ));
        }
    }
}
