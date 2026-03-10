package io.kubemq.sdk.queues;

import io.kubemq.sdk.auth.CredentialProvider;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.KubeMQUtils;
import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.NotImplementedException;
import io.kubemq.sdk.exception.ValidationException;
import io.kubemq.sdk.observability.KubeMQLogger;
import kubemq.Kubemq;
import lombok.Builder;

import javax.annotation.concurrent.ThreadSafe;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * QueuesClient is a client for interacting with the Queues feature of KubeMQ.
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe. A single instance should be
 * shared across all threads. See {@link KubeMQClient} for usage patterns.</p>
 */
@ThreadSafe
public class QueuesClient extends KubeMQClient {

    private final QueueDownstreamHandler queueDownstreamHandler;
    private final QueueUpstreamHandler queueUpstreamHandler;

    @Builder
    public QueuesClient(String address, String clientId, String authToken, Boolean tls,
                        String tlsCertFile, String tlsKeyFile, String caCertFile,
                        byte[] caCertPem, byte[] tlsCertPem, byte[] tlsKeyPem,
                        String serverNameOverride, boolean insecureSkipVerify,
                        CredentialProvider credentialProvider,
                        int maxReceiveSize, int reconnectIntervalSeconds, Boolean keepAlive,
                        int pingIntervalInSeconds, int pingTimeoutInSeconds, Level logLevel,
                        io.kubemq.sdk.client.ReconnectionConfig reconnectionConfig,
                        io.kubemq.sdk.client.ConnectionStateListener connectionStateListener,
                        int shutdownTimeoutSeconds,
                        int connectionTimeoutSeconds,
                        int maxSendMessageSize,
                        Boolean waitForReady,
                        KubeMQLogger logger,
                        boolean validateOnBuild) {
        super(address, clientId, authToken, tls, tlsCertFile, tlsKeyFile, caCertFile,
              caCertPem, tlsCertPem, tlsKeyPem, serverNameOverride, insecureSkipVerify,
              credentialProvider,
              maxReceiveSize, reconnectIntervalSeconds, keepAlive, pingIntervalInSeconds,
              pingTimeoutInSeconds, logLevel, reconnectionConfig, connectionStateListener,
              shutdownTimeoutSeconds, connectionTimeoutSeconds, maxSendMessageSize, waitForReady,
              logger, validateOnBuild);
        this.queueDownstreamHandler = new QueueDownstreamHandler(this);
        this.queueUpstreamHandler = new QueueUpstreamHandler(this);
    }

    /**
     * Creates a queues channel.
     *
     * @param channel The name of the channel to create.
     * @return boolean True if the channel is created successfully, otherwise false.
     */
    public boolean createQueuesChannel(String channel) {
        ensureNotClosed();
        return KubeMQUtils.createChannelRequest(this, this.getClientId(), channel, "queues");
    }
    /**
     * Deletes a queues channel.
     *
     * @param channel The name of the channel to delete.
     * @return boolean True if the channel was successfully deleted, otherwise false.
     */
    public boolean deleteQueuesChannel(String channel) {
        ensureNotClosed();
        return KubeMQUtils.deleteChannelRequest(this, this.getClientId(), channel, "queues");
    }

    /**
     * Lists the queues channels.
     *
     * @param channelSearch The search term used to filter the list of queues channels.
     * @return List<QueuesChannel> A list of queues channels that match the search term.
     */
    public List<QueuesChannel> listQueuesChannels(String channelSearch) {
        ensureNotClosed();
        return KubeMQUtils.listQueuesChannels(this, this.getClientId(), channelSearch);
    }

    /**
     * Sends a message to the specified queue channel.
     * Convenience method equivalent to building a {@link QueueMessage} and calling
     * {@link #sendQueueMessage(QueueMessage)}.
     *
     * @param channel the target queue channel name
     * @param body    the message body
     * @return the result of the send operation
     */
    @SuppressWarnings("deprecation")
    public QueueSendResult sendQueueMessage(String channel, byte[] body) {
        return sendQueuesMessage(QueueMessage.builder()
            .channel(channel)
            .body(body != null ? body : new byte[0])
            .build());
    }

    /**
     * Sends a message to a queue channel.
     * This is the preferred method name per cross-SDK verb alignment.
     *
     * @param queueMessage the message to send
     * @return the result of the send operation
     * @see #sendQueuesMessage(QueueMessage)
     */
    @SuppressWarnings("deprecation")
    public QueueSendResult sendQueueMessage(QueueMessage queueMessage) {
        return sendQueuesMessage(queueMessage);
    }

    /**
     * Sends messages to a queues channel.
     *
     * @param queueMessage message to send in queue
     * @return QueueSendResult The result of the send operation.
     * @deprecated Use {@link #sendQueueMessage(QueueMessage)} instead.
     *             This method will be removed in v3.0.
     */
    @Deprecated(since = "2.2.0", forRemoval = true)
    public QueueSendResult sendQueuesMessage(QueueMessage queueMessage) {
        ensureNotClosed();
        queueMessage.validate();
        return this.queueUpstreamHandler.sendQueuesMessage(queueMessage);
    }

    /**
     * Sends a batch of messages to queues in a single gRPC call.
     * All messages are validated before sending. If any message fails validation,
     * no messages are sent and an IllegalArgumentException is thrown.
     *
     * @param queueMessages the list of messages to send
     * @return list of results, one per message, in the same order as input
     * @throws ValidationException if the list is null/empty or any message fails validation
     */
    public List<QueueSendResult> sendQueuesMessages(List<QueueMessage> queueMessages) {
        ensureNotClosed();
        if (queueMessages == null || queueMessages.isEmpty()) {
            throw ValidationException.builder()
                    .code(ErrorCode.INVALID_ARGUMENT)
                    .message("Queue messages list cannot be null or empty.")
                    .operation("sendQueuesMessages")
                    .build();
        }
        for (QueueMessage msg : queueMessages) {
            msg.validate();
        }
        return this.queueUpstreamHandler.sendQueuesMessages(queueMessages);
    }

    /**
     * Receives messages from a queue channel.
     * This is the preferred method name per cross-SDK verb alignment.
     *
     * @param queuesPollRequest the poll request configuration
     * @return the poll response containing received messages
     * @see #receiveQueuesMessages(QueuesPollRequest)
     */
    @SuppressWarnings("deprecation")
    public QueuesPollResponse receiveQueueMessages(QueuesPollRequest queuesPollRequest) {
        return receiveQueuesMessages(queuesPollRequest);
    }

    /**
     * Receives messages downstream from a queues channel.
     *
     * @param queuesPollRequest Queues Poll request to poll the messages from queue
     * @return QueuesPollResponse The result of the poll operation
     * @deprecated Use {@link #receiveQueueMessages(QueuesPollRequest)} instead.
     *             This method will be removed in v3.0.
     */
    @Deprecated(since = "2.2.0", forRemoval = true)
    public QueuesPollResponse receiveQueuesMessages(QueuesPollRequest queuesPollRequest) {
        ensureNotClosed();
        queuesPollRequest.validate();
        return this.queueDownstreamHandler.receiveQueuesMessages(queuesPollRequest);
    }

    /**
     * Get waiting messages from a queue.
     *
     * @param channel The name of the queue channel.
     * @param maxMessages The maximum number of messages to pull.
     * @param waitTimeoutInSeconds The maximum amount of time to wait for messages in seconds.
     * @return A QueueMessagesWaiting object containing the waiting messages.
     * @throws IllegalArgumentException if a channel is null, maxMessages is less than 1, or waitTimeoutInSeconds is less than 1.
     */
    public QueueMessagesWaiting waiting(String channel, int maxMessages, int waitTimeoutInSeconds) {
        ensureNotClosed();
        getLogger().debug("Get waiting messages from queue", "channel", channel);
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
        getLogger().debug("Waiting messages received", "count", rcvQueuesMessageResponse.getMessagesList().size());
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
     * @throws IllegalArgumentException if a channel is null, maxMessages is less than 1, or waitTimeoutInSeconds is less than 1.
     */
    public QueueMessagesPulled pull(String channel, int maxMessages, int waitTimeoutInSeconds) {
        ensureNotClosed();
        getLogger().debug("Pulling messages from queue", "channel", channel);
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
        getLogger().debug("Messages pulled", "count", rcvQueuesMessageResponse.getMessagesList().size());
        for (Kubemq.QueueMessage queueMessage : rcvQueuesMessageResponse.getMessagesList()) {
            rcvPulledMessages.getMessages().add(QueueMessageWaitingPulled.decode(queueMessage,this.getClientId()));
        }
        return rcvPulledMessages;
    }

    // ---- Async API Methods (REQ-CONC-2 / REQ-CONC-4) ----

    /**
     * Sends a queue message asynchronously.
     *
     * @param queueMessage the message to send
     * @return a CompletableFuture that completes with the send result
     */
    public CompletableFuture<QueueSendResult> sendQueuesMessageAsync(QueueMessage queueMessage) {
        ensureNotClosed();
        queueMessage.validate();
        return this.queueUpstreamHandler.sendQueuesMessageAsync(queueMessage);
    }

    /**
     * Sends a queue message with a custom timeout.
     *
     * @param queueMessage the message to send
     * @param timeout      the maximum time to wait
     * @return the send result
     */
    public QueueSendResult sendQueuesMessage(QueueMessage queueMessage, Duration timeout) {
        return unwrapFuture(sendQueuesMessageAsync(queueMessage), timeout);
    }

    /**
     * Receives queue messages asynchronously.
     *
     * @param queuesPollRequest the poll request configuration
     * @return a CompletableFuture that completes with the poll response
     */
    public CompletableFuture<QueuesPollResponse> receiveQueuesMessagesAsync(
            QueuesPollRequest queuesPollRequest) {
        ensureNotClosed();
        queuesPollRequest.validate();
        return this.queueDownstreamHandler.receiveQueuesMessagesAsync(queuesPollRequest);
    }

    /**
     * Receives queue messages with a custom timeout.
     *
     * @param queuesPollRequest the poll request configuration
     * @param timeout           the maximum time to wait
     * @return the poll response
     */
    public QueuesPollResponse receiveQueuesMessages(QueuesPollRequest queuesPollRequest,
                                                     Duration timeout) {
        return unwrapFuture(receiveQueuesMessagesAsync(queuesPollRequest), timeout);
    }

    /**
     * Creates a queues channel asynchronously.
     */
    public CompletableFuture<Boolean> createQueuesChannelAsync(String channel) {
        ensureNotClosed();
        return executeWithCancellation(() -> createQueuesChannel(channel));
    }

    /**
     * Deletes a queues channel asynchronously.
     */
    public CompletableFuture<Boolean> deleteQueuesChannelAsync(String channel) {
        ensureNotClosed();
        return executeWithCancellation(() -> deleteQueuesChannel(channel));
    }

    /**
     * Lists queues channels asynchronously.
     */
    public CompletableFuture<List<QueuesChannel>> listQueuesChannelsAsync(String channelSearch) {
        ensureNotClosed();
        return executeWithCancellation(() -> listQueuesChannels(channelSearch));
    }

    /**
     * Gets waiting messages from a queue asynchronously.
     */
    public CompletableFuture<QueueMessagesWaiting> waitingAsync(String channel, int maxMessages,
                                                                 int waitTimeoutInSeconds) {
        ensureNotClosed();
        return executeWithCancellation(() -> waiting(channel, maxMessages, waitTimeoutInSeconds));
    }

    /**
     * Pulls messages from a queue asynchronously.
     */
    public CompletableFuture<QueueMessagesPulled> pullAsync(String channel, int maxMessages,
                                                             int waitTimeoutInSeconds) {
        ensureNotClosed();
        return executeWithCancellation(() -> pull(channel, maxMessages, waitTimeoutInSeconds));
    }

    /**
     * Purges all messages from a queue channel.
     *
     * <p><strong>Not yet implemented.</strong> The KubeMQ server supports this operation
     * ({@code AckAllQueueMessages}), but this SDK version does not expose it.
     * See {@code clients/feature-matrix.md} for status.</p>
     *
     * @param channel the queue channel to purge
     * @throws NotImplementedException always
     */
    public void purgeQueue(String channel) {
        throw NotImplementedException.builder()
                .message("purgeQueue is not implemented in this SDK version. "
                        + "Server supports AckAllQueueMessages. "
                        + "See clients/feature-matrix.md for tracking.")
                .operation("purgeQueue")
                .channel(channel)
                .build();
    }

}
