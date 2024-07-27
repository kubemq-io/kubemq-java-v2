package io.kubemq.sdk.queues;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.KubeMQUtils;
import kubemq.Kubemq;
import kubemq.Kubemq.SendQueueMessageResult;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * QueuesClient class represents a client that connects to the KubeMQ server for queue operations.
 *
 * Example usage:
 * <pre>
 * QueuesClient client = QueuesClient.builder().kubeMQClient(new KubeMQClient("localhost:50000")).build();
 * boolean isChannelCreated = client.createQueuesChannel("myChannel");
 * List<QueuesChannel> channels = client.listQueuesChannels("myChannel");
 * client.deleteQueuesChannel("myChannel");
 * </pre>
 *
 * Note: Make sure to set the connection attributes correctly before connecting to the server.
 */
@Slf4j
@Builder
public class QueuesClient {
    /**
     * The KubeMQ client used for communication.
     */
    private final KubeMQClient kubeMQClient;

    /**
     * Creates a queues channel.
     *
     * @param channel The name of the channel to create.
     * @return boolean True if the channel is created successfully, otherwise false.
     */
    public boolean createQueuesChannel(String channel) {
        return KubeMQUtils.createChannelRequest(kubeMQClient, kubeMQClient.getClientId(), channel, "queues");
    }

    /**
     * Gets detailed information of a queue from the KubeMQ server queues.
     *
     * @param channelName The name of the queue channel.
     * @return QueuesDetailInfo containing detailed information about the queue.
     * @throws IllegalArgumentException if the channel name is null or empty.
     */
    public QueuesDetailInfo getQueuesInfo(String channelName) {
        log.trace("Sending getQueuesInfo");
        if (channelName == null || channelName.isEmpty()) {
            throw new IllegalArgumentException("Channel name is required");
        }

        Kubemq.QueuesInfoRequest request = Kubemq.QueuesInfoRequest.newBuilder()
                .setRequestID(UUID.randomUUID().toString())
                .setQueueName(channelName)
                .build();

        Kubemq.QueuesInfoResponse result = kubeMQClient.getClient().queuesInfo(request);
        log.trace("QueueInfo Received: {}", result);

        QueuesDetailInfo detailInfo = QueuesDetailInfo.builder()
                .refRequestID(result.getRefRequestID())
                .totalQueue(result.getInfo().getTotalQueue())
                .sent(result.getInfo().getSent())
                .delivered(result.getInfo().getDelivered())
                .waiting(result.getInfo().getWaiting())
                .queues(result.getInfo().getQueuesList())
                .build();
        return detailInfo;
    }

    /**
     * Deletes a queues channel.
     *
     * @param channel The name of the channel to delete.
     * @return boolean True if the channel was successfully deleted, otherwise false.
     */
    public boolean deleteQueuesChannel(String channel) {
        return KubeMQUtils.deleteChannelRequest(kubeMQClient, kubeMQClient.getClientId(), channel, "queues");
    }

    /**
     * Lists the queues channels.
     *
     * @param channelSearch The search term used to filter the list of queues channels.
     * @return List<QueuesChannel> A list of queues channels that match the search term.
     */
    public List<QueuesChannel> listQueuesChannels(String channelSearch) {
        return KubeMQUtils.listQueuesChannels(kubeMQClient, kubeMQClient.getClientId(), channelSearch);
    }

    /**
     * Sends a message to the KubeMQ server queues.
     *
     * @param message The message to be sent.
     * @return QueueSendResult The result of the message send operation.
     */
    public QueueSendResult sendQueuesMessage(QueueMessageWrapper message) {
        log.trace("Sending queues message");
        message.validate();
        Kubemq.QueueMessage queueMessage = message.encodeMessage(kubeMQClient.getClientId());
        SendQueueMessageResult result = kubeMQClient.getClient().sendQueueMessage(queueMessage);
        log.trace("Queue message sent: {}", result);
        return new QueueSendResult().decode(result);
    }

    /**
     * Sends a batch of messages to the KubeMQ server queues.
     *
     * @param queueMessages The list of messages to be sent.
     * @param batchId  The batch ID, if null a new UUID will be generated.
     * @return QueueMessagesBatchSendResult The result of the batch send operation.
     */
    public QueueMessagesBatchSendResult sendQueuesMessageInBatch(List<QueueMessageWrapper> queueMessages, String batchId) {
        log.trace("Sending queues messages in batch");
        // Converts a list of QueueMessageWrapper objects to a list of Kubemq.QueueMessage objects.
        List<Kubemq.QueueMessage> messages = new ArrayList<>();
        for (QueueMessageWrapper msg : queueMessages) {
            msg.validate();
            messages.add(msg.encodeMessage(kubeMQClient.getClientId()));
        }
        Kubemq.QueueMessagesBatchRequest queueMessagesBatchRequest = Kubemq.QueueMessagesBatchRequest.newBuilder()
                .setBatchID(batchId != null ? batchId : UUID.randomUUID().toString())
                .addAllMessages(messages)
                .build();

        Kubemq.QueueMessagesBatchResponse batchMessageResponse = kubeMQClient.getClient().sendQueueMessagesBatch(queueMessagesBatchRequest);
        log.trace("Batch queue messages sent: {}", batchMessageResponse);
        QueueMessagesBatchSendResult batchSendResult = QueueMessagesBatchSendResult.builder().build();
        batchSendResult.setBatchId(batchMessageResponse.getBatchID());
        batchSendResult.setHaveErrors(batchMessageResponse.getHaveErrors());

        for (SendQueueMessageResult msgRes : batchMessageResponse.getResultsList()) {
            batchSendResult.getResults().add(new QueueSendResult().decode(msgRes));
        }
        return batchSendResult;
    }

    /**
     * Receives messages from a queues channel.
     *
     * @param id                   The request ID.
     * @param channel              The name of the channel to receive messages from.
     * @param maxMessages          The maximum number of messages to receive.
     * @param waitTimeoutInSeconds The maximum time in seconds to wait for new messages.
     * @param peak                 Whether to peak at the messages without removing them from the queue.
     * @return QueueMessagesReceived The response object containing the received messages.
     */
    public QueueMessagesReceived receiveQueuesMessages(String id, String channel, int maxMessages, int waitTimeoutInSeconds, boolean peak) {
        log.trace("Receiving queues messages");
        if (channel == null) {
            throw new IllegalArgumentException("channel cannot be null.");
        }
        if (maxMessages < 1) {
            throw new IllegalArgumentException("maxMessages must be greater than 0.");
        }
        if (waitTimeoutInSeconds < 1) {
            throw new IllegalArgumentException("waitTimeoutInSeconds must be greater than 0.");
        }

        Kubemq.ReceiveQueueMessagesRequest rcvQueuesMessageReq = Kubemq.ReceiveQueueMessagesRequest.newBuilder()
                .setRequestID(id != null ? id : UUID.randomUUID().toString())
                .setClientID(kubeMQClient.getClientId())
                .setChannel(channel)
                .setMaxNumberOfMessages(maxMessages)
                .setWaitTimeSeconds(waitTimeoutInSeconds)
                .setIsPeak(peak)
                .build();

        Kubemq.ReceiveQueueMessagesResponse rcvQueuesMessageResponse = kubeMQClient.getClient().receiveQueueMessages(rcvQueuesMessageReq);
        log.trace("Queues messages received: {}", rcvQueuesMessageResponse);
        QueueMessagesReceived queueMessagesReceived = QueueMessagesReceived.builder()
                .messagesExpired(rcvQueuesMessageResponse.getMessagesExpired())
                .error(rcvQueuesMessageResponse.getError())
                .isError(rcvQueuesMessageResponse.getIsError())
                .messagesReceived(rcvQueuesMessageResponse.getMessagesReceived())
                .isPeak(rcvQueuesMessageResponse.getIsPeak())
                .requestID(rcvQueuesMessageResponse.getRequestID())
                .build();

        for (Kubemq.QueueMessage queueMessage : rcvQueuesMessageResponse.getMessagesList()) {
            queueMessagesReceived.getMessages().add(QueueMessageWrapper.decode(queueMessage));
        }
        return queueMessagesReceived;
    }

    /**
     * Streams messages to a queues channel.
     *
     * @param streamQueueMessageResponse The response observer for the stream.
     * @return StreamObserver<Kubemq.StreamQueueMessagesRequest> The request observer for the stream.
     */
    public StreamObserver<Kubemq.StreamQueueMessagesRequest> streamQueuesMessages(StreamObserver<Kubemq.StreamQueueMessagesResponse> streamQueueMessageResponse) {
        return kubeMQClient.getAsyncClient().streamQueueMessage(streamQueueMessageResponse);
    }

    /**
     * Sends messages upstream to a queues channel.
     *
     * @param queueUploadstreamResponse The response observer for the upstream.
     * @return StreamObserver<kubemq.Kubemq.QueuesUpstreamRequest> The request observer for the upstream.
     */
    public StreamObserver<kubemq.Kubemq.QueuesUpstreamRequest> sendQueuesMessagesUpStream(StreamObserver<Kubemq.QueuesUpstreamResponse> queueUploadstreamResponse) {
        return kubeMQClient.getAsyncClient().queuesUpstream(queueUploadstreamResponse);
    }

    /**
     * Receives messages downstream from a queues channel.
     *
     * @param queueDownstreamResponse The response observer for the downstream.
     * @return StreamObserver<kubemq.Kubemq.QueuesDownstreamRequest> The request observer for the downstream.
     */
    public StreamObserver<kubemq.Kubemq.QueuesDownstreamRequest> receiveQueuesMessagesDownStream(StreamObserver<Kubemq.QueuesDownstreamResponse> queueDownstreamResponse) {
        return kubeMQClient.getAsyncClient().queuesDownstream(queueDownstreamResponse);
    }

    /**
     * Acknowledges all messages in a queue.
     *
     * @param requestId        The request ID.
     * @param queueName        The name of the queue.
     * @param waitTimeInSeconds The wait time in seconds.
     * @return QueueMessageAcknowledgment The acknowledgment response.
     */
    public QueueMessageAcknowledgment ackAllQueueMessage(String requestId, String queueName, int waitTimeInSeconds) {
        Kubemq.AckAllQueueMessagesRequest ackRequest = Kubemq.AckAllQueueMessagesRequest.newBuilder()
                .setRequestID(requestId)
                .setChannel(queueName)
                .setClientID(kubeMQClient.getClientId())
                .setWaitTimeSeconds(waitTimeInSeconds)
                .build();
        Kubemq.AckAllQueueMessagesResponse ackResp = kubeMQClient.getClient().ackAllQueueMessages(ackRequest);
        return QueueMessageAcknowledgment.decode(ackResp);
    }

}
