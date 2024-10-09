package io.kubemq.sdk.queues;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.KubeMQUtils;
import kubemq.Kubemq;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

/**
 * QueuesClient class is a client for interacting with the Queues feature of KubeMQ.
 * It extends the KubeMQClient class and provides methods for creating, deleting, and listing queues channels,
 * as well as sending and receiving messages from queues channels.
 */
@Slf4j
public class QueuesClient extends KubeMQClient {

   // private final QueueStreamHelper queueStreamHelper;

    @Builder
    public QueuesClient(String address, String clientId, String authToken, boolean tls, String tlsCertFile, String tlsKeyFile, String caCertFile,
                        int maxReceiveSize, int reconnectIntervalSeconds, Boolean keepAlive, int pingIntervalInSeconds, int pingTimeoutInSeconds, Level logLevel) {
        super(address, clientId, authToken, tls, tlsCertFile, tlsKeyFile, caCertFile, maxReceiveSize, reconnectIntervalSeconds, keepAlive, pingIntervalInSeconds, pingTimeoutInSeconds, logLevel);
        //this.queueStreamHelper=new QueueStreamHelper();
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
     * Sends messages to a queues channel.
     *
     * @param queueMessage message to send in queue
     * @return QueueSendResult The result of the send operation.
     */
    public QueueSendResult sendQueuesMessage(QueueMessage queueMessage) {
        queueMessage.validate();
      return new QueueStreamHelper().sendMessage(this, queueMessage.encode(this.getClientId()));
    }

    /**
     * Receives messages downstream from a queues channel.
     *
     * @param queuesPollRequest Queues Poll request to poll the messages from queue
     * @return QueuesPollResponse The result of the poll operation
     */
    public QueuesPollResponse receiveQueuesMessages(QueuesPollRequest queuesPollRequest) {
        queuesPollRequest.validate();
        return new QueueStreamHelper().receiveMessage(this, queuesPollRequest);
    }

    /**
     * Get waiting messages from a queue.
     *
     * @param channel The name of the queue channel.
     * @param maxMessages The maximum number of messages to pull.
     * @param waitTimeoutInSeconds The maximum amount of time to wait for messages in seconds.
     * @return A QueueMessagesWaiting object containing the waiting messages.
     * @throws IllegalArgumentException if channel is null, maxMessages is less than 1, or waitTimeoutInSeconds is less than 1.
     */
    public QueueMessagesWaiting waiting(String channel, int maxMessages, int waitTimeoutInSeconds) {
        log.debug("Get waiting messages from queue: {}", channel);
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
                .setRequestID(UUID.randomUUID().toString())
                .setClientID(this.getClientId())
                .setChannel(channel)
                .setMaxNumberOfMessages(maxMessages)
                .setWaitTimeSeconds(waitTimeoutInSeconds)
                .setIsPeak(true)
                .build();
        Kubemq.ReceiveQueueMessagesResponse rcvQueuesMessageResponse = this.getClient().receiveQueueMessages(rcvQueuesMessageReq);
        QueueMessagesWaiting rcvWaitingMessages = QueueMessagesWaiting.builder()
                .isError(rcvQueuesMessageResponse.getIsError())
                .error(rcvQueuesMessageResponse.getError())
                .build();
        if (rcvQueuesMessageResponse.getMessagesList().isEmpty()) {
            return rcvWaitingMessages;
        }
        log.debug("Waiting messages count: {}", rcvQueuesMessageResponse.getMessagesList().size());
        for (Kubemq.QueueMessage queueMessage : rcvQueuesMessageResponse.getMessagesList()) {
            rcvWaitingMessages.getMessages().add(QueueMessageWaitingPulled.decode(queueMessage,this.getClientId()));
        }
        return rcvWaitingMessages;
    }


    /**
     * Pulls messages from a queue.
     *
     * @param channel The name of the queue channel.
     * @param maxMessages The maximum number of messages to pull.
     * @param waitTimeoutInSeconds The maximum amount of time to wait for messages in seconds.
     * @return A QueueMessagesPulled object containing the pulled messages.
     * @throws IllegalArgumentException if channel is null, maxMessages is less than 1, or waitTimeoutInSeconds is less than 1.
     */
    public QueueMessagesPulled pull(String channel, int maxMessages, int waitTimeoutInSeconds) {
        log.debug("Pulling messages from queue: {}", channel);
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
                .setRequestID(UUID.randomUUID().toString())
                .setClientID(this.getClientId())
                .setChannel(channel)
                .setMaxNumberOfMessages(maxMessages)
                .setWaitTimeSeconds(waitTimeoutInSeconds)
                .setIsPeak(false)
                .build();
        Kubemq.ReceiveQueueMessagesResponse rcvQueuesMessageResponse = this.getClient().receiveQueueMessages(rcvQueuesMessageReq);
        QueueMessagesPulled rcvPulledMessages = QueueMessagesPulled.builder()
                .isError(rcvQueuesMessageResponse.getIsError())
                .error(rcvQueuesMessageResponse.getError())
                .build();
        if (rcvQueuesMessageResponse.getMessagesList().isEmpty()) {
            return rcvPulledMessages;
        }
        log.debug("Pulled messages count: {}", rcvQueuesMessageResponse.getMessagesList().size());
        for (Kubemq.QueueMessage queueMessage : rcvQueuesMessageResponse.getMessagesList()) {
            rcvPulledMessages.getMessages().add(QueueMessageWaitingPulled.decode(queueMessage,this.getClientId()));
        }
        return rcvPulledMessages;
    }

}
