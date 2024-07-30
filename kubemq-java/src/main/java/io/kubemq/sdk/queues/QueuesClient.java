package io.kubemq.sdk.queues;

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
public class QueuesClient extends KubeMQClient {

    @Builder
    public QueuesClient(String address, String clientId, String authToken, boolean tls, String tlsCertFile, String tlsKeyFile,
                    int maxReceiveSize, int reconnectIntervalSeconds, boolean keepAlive, int pingIntervalInSeconds, int pingTimeoutInSeconds, KubeMQClient.Level logLevel) {
        super(address, clientId, authToken, tls, tlsCertFile, tlsKeyFile, maxReceiveSize, reconnectIntervalSeconds, keepAlive, pingIntervalInSeconds, pingTimeoutInSeconds, logLevel);
    }

    /**
     * Creates a queues channel.
     *
     * @param channel The name of the channel to create.
     * @return boolean True if the channel is created successfully, otherwise false.
     */
    public boolean createQueuesChannel(String channel) {
        return KubeMQUtils.createChannelRequest(this, this.getClientId(), channel, "queues");
    }

    /**
     * Gets detailed information of a queue from the KubeMQ server queues.
     *
     * @param channelName The name of the queue channel.
     * @return QueuesDetailInfo containing detailed information about the queue.
     * @throws IllegalArgumentException if the channel name is null or empty.
     */
    public QueuesDetailInfo getQueuesInfo(String channelName) {
        log.debug("Sending getQueuesInfo");
        if (channelName == null || channelName.isEmpty()) {
            throw new IllegalArgumentException("Channel name is required");
        }

        Kubemq.QueuesInfoRequest request = Kubemq.QueuesInfoRequest.newBuilder()
                .setRequestID(UUID.randomUUID().toString())
                .setQueueName(channelName)
                .build();

        Kubemq.QueuesInfoResponse result = this.getClient().queuesInfo(request);
        log.debug("QueueInfo Received: {}", result);

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
        return KubeMQUtils.deleteChannelRequest(this, this.getClientId(), channel, "queues");
    }

    /**
     * Lists the queues channels.
     *
     * @param channelSearch The search term used to filter the list of queues channels.
     * @return List<QueuesChannel> A list of queues channels that match the search term.
     */
    public List<QueuesChannel> listQueuesChannels(String channelSearch) {
        return KubeMQUtils.listQueuesChannels(this, this.getClientId(), channelSearch);
    }

    /**
     * Sends a message to the KubeMQ server queues.
     *
     * @param message The message to be sent.
     * @return QueueSendResult The result of the message send operation.
     */
    public QueueSendResult sendQueuesMessage(QueueMessage message) {
        log.debug("Sending queues message");
        message.validate();
        Kubemq.QueueMessage queueMessage = message.encodeMessage(this.getClientId());
        SendQueueMessageResult result = this.getClient().sendQueueMessage(queueMessage);
        log.debug("Queue message sent: {}", result);
        return new QueueSendResult().decode(result);
    }

    /**
     * Sends a batch of messages to the KubeMQ server queues.
     *
     * @param queueMessages The list of messages to be sent.
     * @param batchId  The batch ID, if null a new UUID will be generated.
     * @return QueueMessagesBatchSendResult The result of the batch send operation.
     */
    public QueueMessagesBatchSendResult sendQueuesMessageInBatch(List<QueueMessage> queueMessages, String batchId) {
        log.debug("Sending queues messages in batch");
        // Converts a list of QueueMessageWrapper objects to a list of Kubemq.QueueMessage objects.
        List<Kubemq.QueueMessage> messages = new ArrayList<>();
        for (QueueMessage msg : queueMessages) {
            msg.validate();
            messages.add(msg.encodeMessage(this.getClientId()));
        }
        Kubemq.QueueMessagesBatchRequest queueMessagesBatchRequest = Kubemq.QueueMessagesBatchRequest.newBuilder()
                .setBatchID(batchId != null ? batchId : UUID.randomUUID().toString())
                .addAllMessages(messages)
                .build();

        Kubemq.QueueMessagesBatchResponse batchMessageResponse = this.getClient().sendQueueMessagesBatch(queueMessagesBatchRequest);
        log.debug("Batch queue messages sent: {}", batchMessageResponse);
        QueueMessagesBatchSendResult batchSendResult = QueueMessagesBatchSendResult.builder().build();
        batchSendResult.setBatchId(batchMessageResponse.getBatchID());
        batchSendResult.setHaveErrors(batchMessageResponse.getHaveErrors());

        for (SendQueueMessageResult msgRes : batchMessageResponse.getResultsList()) {
            batchSendResult.getResults().add(new QueueSendResult().decode(msgRes));
        }
        return batchSendResult;
    }


    /**
     * Sends messages to a queues channel.
     *
     * @param queueMessage message to send in queue
     * @return UpstreamResponse The stream from the upstream.
     */
    public QueueSendResult sendQueuesMessageUpStream(QueueMessage queueMessage) {
        queueMessage.validate();
      return new QueueStreamHelper().sendMessage(this, queueMessage.encode(this.getClientId()));
    }

    /**
     * Receives messages downstream from a queues channel.
     *
     * @param queuesPollRequest Queues Poll request to poll the messages from queue

     */
    public QueuesPollResponse receiveQueuesMessagesDownStream(QueuesPollRequest queuesPollRequest) {
        queuesPollRequest.validate();
        return new QueueStreamHelper().receiveMessage(this, queuesPollRequest);
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
                .setClientID(this.getClientId())
                .setWaitTimeSeconds(waitTimeInSeconds)
                .build();
        Kubemq.AckAllQueueMessagesResponse ackResp = this.getClient().ackAllQueueMessages(ackRequest);
        return QueueMessageAcknowledgment.decode(ackResp);
    }

}
