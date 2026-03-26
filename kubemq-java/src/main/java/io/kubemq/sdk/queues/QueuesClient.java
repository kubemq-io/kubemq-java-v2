package io.kubemq.sdk.queues;

import io.kubemq.sdk.auth.CredentialProvider;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.KubeMQUtils;
import io.kubemq.sdk.exception.AuthenticationException;
import io.kubemq.sdk.exception.ClientClosedException;
import io.kubemq.sdk.exception.ConnectionException;
import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.KubeMQException;
import io.kubemq.sdk.exception.KubeMQTimeoutException;
import io.kubemq.sdk.exception.NotImplementedException;
import io.kubemq.sdk.exception.ValidationException;
import io.kubemq.sdk.observability.KubeMQLogger;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.concurrent.ThreadSafe;
import kubemq.Kubemq;
import lombok.Builder;

/**
 * QueuesClient is a client for interacting with the Queues feature of KubeMQ.
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe. A single instance should be shared
 * across all threads. See {@link KubeMQClient} for usage patterns.
 */
@ThreadSafe
public class QueuesClient extends KubeMQClient {

  private final QueueDownstreamHandler queueDownstreamHandler;
  private final QueueUpstreamHandler queueUpstreamHandler;

  @Builder
  public QueuesClient(
      String address,
      String clientId,
      String authToken,
      Boolean tls,
      String tlsCertFile,
      String tlsKeyFile,
      String caCertFile,
      byte[] caCertPem,
      byte[] tlsCertPem,
      byte[] tlsKeyPem,
      String serverNameOverride,
      boolean insecureSkipVerify,
      CredentialProvider credentialProvider,
      int maxReceiveSize,
      int reconnectIntervalSeconds,
      Boolean keepAlive,
      int pingIntervalInSeconds,
      int pingTimeoutInSeconds,
      Level logLevel,
      io.kubemq.sdk.client.ReconnectionConfig reconnectionConfig,
      io.kubemq.sdk.client.ConnectionStateListener connectionStateListener,
      int shutdownTimeoutSeconds,
      int connectionTimeoutSeconds,
      int maxSendMessageSize,
      Boolean waitForReady,
      KubeMQLogger logger,
      boolean validateOnBuild) {
    super(
        address,
        clientId,
        authToken,
        tls,
        tlsCertFile,
        tlsKeyFile,
        caCertFile,
        caCertPem,
        tlsCertPem,
        tlsKeyPem,
        serverNameOverride,
        insecureSkipVerify,
        credentialProvider,
        maxReceiveSize,
        reconnectIntervalSeconds,
        keepAlive,
        pingIntervalInSeconds,
        pingTimeoutInSeconds,
        logLevel,
        reconnectionConfig,
        connectionStateListener,
        shutdownTimeoutSeconds,
        connectionTimeoutSeconds,
        maxSendMessageSize,
        waitForReady,
        logger,
        validateOnBuild);
    this.queueDownstreamHandler = new QueueDownstreamHandler(this);
    this.queueUpstreamHandler = new QueueUpstreamHandler(this);
  }

  /**
   * Creates a queues channel.
   *
   * @param channel the name of the queues channel to create (must not be null or empty)
   * @return {@code true} if the channel was created successfully
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws KubeMQException if creating the channel fails for any other reason
   * @see QueuesChannel
   * @see #deleteQueuesChannel(String)
   * @see #listQueuesChannels(String)
   */
  public boolean createQueuesChannel(String channel) {
    ensureNotClosed();
    KubeMQUtils.validateChannelName(channel, "createQueuesChannel");
    return KubeMQUtils.createChannelRequest(this, this.getClientId(), channel, "queues");
  }

  /**
   * Deletes a queues channel.
   *
   * @param channel the name of the queues channel to delete (must not be null or empty)
   * @return {@code true} if the channel was deleted successfully
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws KubeMQException if deleting the channel fails for any other reason
   * @see #createQueuesChannel(String)
   */
  public boolean deleteQueuesChannel(String channel) {
    ensureNotClosed();
    KubeMQUtils.validateChannelName(channel, "deleteQueuesChannel");
    return KubeMQUtils.deleteChannelRequest(this, this.getClientId(), channel, "queues");
  }

  /**
   * Lists the queues channels.
   *
   * @param channelSearch a channel name filter; use an empty string or {@code null} to list all
   *     channels, or a partial name to match channels containing that substring
   * @return a list of {@link QueuesChannel} objects matching the search criteria, including
   *     channel statistics (e.g. pending message count, active subscribers)
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws KubeMQException if listing channels fails for any other reason
   * @see QueuesChannel
   * @see #createQueuesChannel(String)
   */
  public List<QueuesChannel> listQueuesChannels(String channelSearch) {
    ensureNotClosed();
    return KubeMQUtils.listQueuesChannels(this, this.getClientId(), channelSearch);
  }

  /**
   * Creates a channel of the specified type.
   *
   * @param name the name of the channel to create (must not be null or empty)
   * @param type the channel type: "events", "events_store", "commands", "queries", or "queues"
   * @return {@code true} if the channel was created successfully
   * @throws ValidationException if the type is invalid or the name is null/empty
   * @throws ClientClosedException if this client has been closed
   * @throws KubeMQException if creating the channel fails
   */
  public boolean createChannel(String name, String type) {
    KubeMQUtils.validateChannelType(type);
    ensureNotClosed();
    return KubeMQUtils.createChannelRequest(this, this.getClientId(), name, type);
  }

  /**
   * Deletes a channel of the specified type.
   *
   * @param name the name of the channel to delete (must not be null or empty)
   * @param type the channel type: "events", "events_store", "commands", "queries", or "queues"
   * @return {@code true} if the channel was deleted successfully
   * @throws ValidationException if the type is invalid or the name is null/empty
   * @throws ClientClosedException if this client has been closed
   * @throws KubeMQException if deleting the channel fails
   */
  public boolean deleteChannel(String name, String type) {
    KubeMQUtils.validateChannelType(type);
    ensureNotClosed();
    return KubeMQUtils.deleteChannelRequest(this, this.getClientId(), name, type);
  }

  /**
   * Lists channels of the specified type matching the search criteria.
   *
   * @param type the channel type: "events", "events_store", "commands", "queries", or "queues"
   * @param search a channel name filter; use an empty string or {@code null} to list all channels
   * @return a list of channel objects matching the search criteria
   * @throws ValidationException if the type is invalid
   * @throws ClientClosedException if this client has been closed
   * @throws KubeMQException if listing channels fails
   */
  public List<?> listChannels(String type, String search) {
    KubeMQUtils.validateChannelType(type);
    ensureNotClosed();
    switch (type) {
      case "events":
      case "events_store":
        return KubeMQUtils.listPubSubChannels(this, this.getClientId(), type, search);
      case "commands":
      case "queries":
        return KubeMQUtils.listCQChannels(this, this.getClientId(), type, search);
      case "queues":
        return KubeMQUtils.listQueuesChannels(this, this.getClientId(), search);
      default:
        throw ValidationException.builder()
            .message("Invalid channel type: " + type)
            .operation("listChannels")
            .build();
    }
  }

  /**
   * Sends a message to the specified queue channel. Convenience method equivalent to building a
   * {@link QueueMessage} and calling {@link #sendQueueMessage(QueueMessage)}.
   *
   * @param channel the target queue channel name (must not be null or empty)
   * @param body the message payload as a byte array; {@code null} is treated as empty
   * @return a {@link QueueSendResult} containing the server-assigned message ID, send status,
   *     and timestamp
   * @throws ValidationException if the channel name is null or empty
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws KubeMQException if sending the message fails
   * @see QueueMessage
   * @see #sendQueueMessage(QueueMessage)
   */
  @SuppressWarnings("deprecation")
  public QueueSendResult sendQueueMessage(String channel, byte[] body) {
    return sendQueuesMessage(
        QueueMessage.builder().channel(channel).body(body != null ? body : new byte[0]).build());
  }

  /**
   * Sends a message to a queue channel. This is the preferred method name per cross-SDK verb
   * alignment.
   *
   * <p><b>Example:</b></p>
   * <pre>{@code
   * QueuesClient client = QueuesClient.builder()
   *     .address("localhost:50000")
   *     .clientId("queue-sender")
   *     .build();
   *
   * QueueMessage message = QueueMessage.builder()
   *     .channel("queues.orders")
   *     .body("order-payload".getBytes())
   *     .metadata("priority=high")
   *     .build();
   *
   * QueueSendResult result = client.sendQueueMessage(message);
   * System.out.println("Message sent, ID: " + result.getId());
   * }</pre>
   *
   * @param queueMessage the queue message containing the target channel, body, metadata, optional
   *     tags, and optional policy (e.g. expiration, delay, max receive count)
   * @return a {@link QueueSendResult} containing the server-assigned message ID, send status,
   *     and timestamp
   * @throws ValidationException if the message is missing required fields (e.g. channel)
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws KubeMQException if sending the message fails for any other reason
   * @see QueueMessage
   * @see QueueSendResult
   * @see #receiveQueueMessages(QueuesPollRequest)
   * @see #sendQueuesMessage(QueueMessage)
   */
  @SuppressWarnings("deprecation")
  public QueueSendResult sendQueueMessage(QueueMessage queueMessage) {
    return sendQueuesMessage(queueMessage);
  }

  /**
   * Sends messages to a queues channel.
   *
   * @param queueMessage the queue message containing the target channel, body, and metadata to
   *     send
   * @return a {@link QueueSendResult} containing the server-assigned message ID, send status,
   *     and timestamp
   * @throws ValidationException if the message is missing required fields (e.g. channel)
   * @throws ClientClosedException if this client has been closed
   * @throws KubeMQException if sending the message fails
   * @see QueueMessage
   * @see QueueSendResult
   * @deprecated Use {@link #sendQueueMessage(QueueMessage)} instead. This method will be removed in
   *     v3.0.
   */
  @Deprecated(since = "2.2.0", forRemoval = true)
  public QueueSendResult sendQueuesMessage(QueueMessage queueMessage) {
    ensureNotClosed();
    queueMessage.validate();
    return this.queueUpstreamHandler.sendQueuesMessage(queueMessage);
  }

  /**
   * Sends a batch of messages to queues in a single gRPC call. All messages are validated before
   * sending. If any message fails validation, no messages are sent and an IllegalArgumentException
   * is thrown.
   *
   * @param queueMessages the list of queue messages to send; each must have a valid channel and
   *     body
   * @return a list of {@link QueueSendResult} objects, one per input message in the same order,
   *     each containing the server-assigned message ID and send status
   * @throws ValidationException if the list is null/empty or any message fails validation
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws KubeMQException if the batch send operation fails
   * @see QueueMessage
   * @see QueueSendResult
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
   * Receives messages from a queue channel. This is the preferred method name per cross-SDK verb
   * alignment.
   *
   * <p><b>Example:</b></p>
   * <pre>{@code
   * QueuesClient client = QueuesClient.builder()
   *     .address("localhost:50000")
   *     .clientId("queue-receiver")
   *     .build();
   *
   * QueuesPollRequest request = QueuesPollRequest.builder()
   *     .channel("queues.orders")
   *     .pollMaxMessages(10)
   *     .pollWaitTimeoutInSeconds(30)
   *     .autoAckMessages(false)
   *     .build();
   *
   * QueuesPollResponse response = client.receiveQueueMessages(request);
   * for (QueueMessageReceived msg : response.getMessages()) {
   *     System.out.println("Got: " + new String(msg.getBody()));
   *     msg.ack();
   * }
   * }</pre>
   *
   * @param queuesPollRequest the poll request specifying the channel, maximum number of messages,
   *     wait timeout, visibility timeout, and auto-acknowledge mode
   * @return a {@link QueuesPollResponse} containing the list of received
   *     {@link QueueMessageReceived} objects; each message must be explicitly acknowledged,
   *     rejected, or re-queued unless auto-acknowledge is enabled
   * @throws ValidationException if the poll request is missing required fields (e.g. channel)
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws KubeMQException if receiving messages fails for any other reason
   * @see QueuesPollRequest
   * @see QueuesPollResponse
   * @see QueueMessageReceived
   * @see #sendQueueMessage(QueueMessage)
   * @see #receiveQueuesMessages(QueuesPollRequest)
   */
  @SuppressWarnings("deprecation")
  public QueuesPollResponse receiveQueueMessages(QueuesPollRequest queuesPollRequest) {
    return receiveQueuesMessages(queuesPollRequest);
  }

  /**
   * Receives messages downstream from a queues channel.
   *
   * @param queuesPollRequest the poll request specifying the channel, maximum number of messages,
   *     wait timeout, and auto-acknowledge mode
   * @return a {@link QueuesPollResponse} containing the received messages and any error
   *     information
   * @throws ValidationException if the poll request is missing required fields (e.g. channel)
   * @throws ClientClosedException if this client has been closed
   * @throws KubeMQException if the poll operation fails
   * @see QueuesPollRequest
   * @see QueuesPollResponse
   * @deprecated Use {@link #receiveQueueMessages(QueuesPollRequest)} instead. This method will be
   *     removed in v3.0.
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
   * @throws IllegalArgumentException if a channel is null, maxMessages is less than 1, or
   *     waitTimeoutInSeconds is less than 1.
   */
  public QueueMessagesWaiting waiting(String channel, int maxMessages, int waitTimeoutInSeconds) {
    ensureNotClosed();
    KubeMQUtils.validateChannelName(channel, "waiting");
    getLogger().debug("Get waiting messages from queue", "channel", channel);
    if (maxMessages < 1) {
      throw new IllegalArgumentException("maxMessages must be greater than 0.");
    }
    if (waitTimeoutInSeconds < 1) {
      throw new IllegalArgumentException("waitTimeoutInSeconds must be greater than 0.");
    }

    Kubemq.ReceiveQueueMessagesRequest rcvQueuesMessageReq =
        Kubemq.ReceiveQueueMessagesRequest.newBuilder()
            .setRequestID(UUID.randomUUID().toString())
            .setClientID(this.getClientId())
            .setChannel(channel)
            .setMaxNumberOfMessages(maxMessages)
            .setWaitTimeSeconds(waitTimeoutInSeconds)
            .setIsPeak(true)
            .build();
    Kubemq.ReceiveQueueMessagesResponse rcvQueuesMessageResponse =
        this.getClient().receiveQueueMessages(rcvQueuesMessageReq);
    QueueMessagesWaiting rcvWaitingMessages =
        QueueMessagesWaiting.builder()
            .isError(rcvQueuesMessageResponse.getIsError())
            .error(rcvQueuesMessageResponse.getError())
            .build();
    if (rcvQueuesMessageResponse.getMessagesList().isEmpty()) {
      return rcvWaitingMessages;
    }
    getLogger()
        .debug(
            "Waiting messages received",
            "count",
            rcvQueuesMessageResponse.getMessagesList().size());
    for (Kubemq.QueueMessage queueMessage : rcvQueuesMessageResponse.getMessagesList()) {
      rcvWaitingMessages
          .getMessages()
          .add(QueueMessageWaitingPulled.decode(queueMessage, this.getClientId()));
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
   * @throws IllegalArgumentException if a channel is null, maxMessages is less than 1, or
   *     waitTimeoutInSeconds is less than 1.
   */
  public QueueMessagesPulled pull(String channel, int maxMessages, int waitTimeoutInSeconds) {
    ensureNotClosed();
    KubeMQUtils.validateChannelName(channel, "pull");
    getLogger().debug("Pulling messages from queue", "channel", channel);
    if (maxMessages < 1) {
      throw new IllegalArgumentException("maxMessages must be greater than 0.");
    }
    if (waitTimeoutInSeconds < 1) {
      throw new IllegalArgumentException("waitTimeoutInSeconds must be greater than 0.");
    }

    Kubemq.ReceiveQueueMessagesRequest rcvQueuesMessageReq =
        Kubemq.ReceiveQueueMessagesRequest.newBuilder()
            .setRequestID(UUID.randomUUID().toString())
            .setClientID(this.getClientId())
            .setChannel(channel)
            .setMaxNumberOfMessages(maxMessages)
            .setWaitTimeSeconds(waitTimeoutInSeconds)
            .setIsPeak(false)
            .build();
    Kubemq.ReceiveQueueMessagesResponse rcvQueuesMessageResponse =
        this.getClient().receiveQueueMessages(rcvQueuesMessageReq);
    QueueMessagesPulled rcvPulledMessages =
        QueueMessagesPulled.builder()
            .isError(rcvQueuesMessageResponse.getIsError())
            .error(rcvQueuesMessageResponse.getError())
            .build();
    if (rcvQueuesMessageResponse.getMessagesList().isEmpty()) {
      return rcvPulledMessages;
    }
    getLogger()
        .debug("Messages pulled", "count", rcvQueuesMessageResponse.getMessagesList().size());
    for (Kubemq.QueueMessage queueMessage : rcvQueuesMessageResponse.getMessagesList()) {
      rcvPulledMessages
          .getMessages()
          .add(QueueMessageWaitingPulled.decode(queueMessage, this.getClientId()));
    }
    return rcvPulledMessages;
  }

  // ---- Async API Methods (REQ-CONC-2 / REQ-CONC-4) ----

  /**
   * Sends a queue message asynchronously.
   *
   * @param queueMessage the queue message containing the target channel, body, and metadata
   * @return a {@link CompletableFuture} that completes with a {@link QueueSendResult} containing
   *     the send status and server-assigned message ID; completes exceptionally on failure
   * @throws ValidationException if the message is missing required fields (e.g. channel)
   * @throws ClientClosedException if this client has been closed
   * @see QueueMessage
   * @see QueueSendResult
   * @see #sendQueueMessage(QueueMessage)
   */
  public CompletableFuture<QueueSendResult> sendQueuesMessageAsync(QueueMessage queueMessage) {
    ensureNotClosed();
    queueMessage.validate();
    return this.queueUpstreamHandler.sendQueuesMessageAsync(queueMessage);
  }

  /**
   * Sends a queue message with a custom timeout.
   *
   * @param queueMessage the queue message containing the target channel, body, and metadata
   * @param timeout the maximum time to wait for the server acknowledgment before throwing
   * @return a {@link QueueSendResult} containing the send status and server-assigned message ID
   * @throws KubeMQTimeoutException if the operation exceeds the specified timeout
   * @throws ValidationException if the message is missing required fields (e.g. channel)
   * @throws ClientClosedException if this client has been closed
   * @throws KubeMQException if sending the message fails
   * @see #sendQueuesMessageAsync(QueueMessage)
   */
  public QueueSendResult sendQueuesMessage(QueueMessage queueMessage, Duration timeout) {
    return unwrapFuture(sendQueuesMessageAsync(queueMessage), timeout);
  }

  /**
   * Receives queue messages asynchronously.
   *
   * @param queuesPollRequest the poll request specifying the channel, maximum number of messages,
   *     wait timeout, and auto-acknowledge mode
   * @return a {@link CompletableFuture} that completes with a {@link QueuesPollResponse}
   *     containing received messages; completes exceptionally on failure
   * @throws ValidationException if the poll request is missing required fields (e.g. channel)
   * @throws ClientClosedException if this client has been closed
   * @see QueuesPollRequest
   * @see QueuesPollResponse
   * @see #receiveQueueMessages(QueuesPollRequest)
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
   * @param queuesPollRequest the poll request specifying the channel, maximum number of messages,
   *     wait timeout, and auto-acknowledge mode
   * @param timeout the maximum time to wait for the response before throwing
   * @return a {@link QueuesPollResponse} containing received messages and any error information
   * @throws KubeMQTimeoutException if the operation exceeds the specified timeout
   * @throws ValidationException if the poll request is missing required fields (e.g. channel)
   * @throws ClientClosedException if this client has been closed
   * @throws KubeMQException if receiving messages fails
   * @see #receiveQueuesMessagesAsync(QueuesPollRequest)
   */
  public QueuesPollResponse receiveQueuesMessages(
      QueuesPollRequest queuesPollRequest, Duration timeout) {
    return unwrapFuture(receiveQueuesMessagesAsync(queuesPollRequest), timeout);
  }

  /**
   * Creates a queues channel asynchronously.
   *
   * @param channel the name of the queues channel to create (must not be null or empty)
   * @return a {@link CompletableFuture} that completes with {@code true} if the channel was
   *     created successfully; completes exceptionally with a {@link KubeMQException} on failure
   * @throws ClientClosedException if this client has been closed
   * @see #createQueuesChannel(String)
   */
  public CompletableFuture<Boolean> createQueuesChannelAsync(String channel) {
    ensureNotClosed();
    return executeWithCancellation(() -> createQueuesChannel(channel));
  }

  /**
   * Deletes a queues channel asynchronously.
   *
   * @param channel the name of the queues channel to delete (must not be null or empty)
   * @return a {@link CompletableFuture} that completes with {@code true} if the channel was
   *     deleted successfully; completes exceptionally with a {@link KubeMQException} on failure
   * @throws ClientClosedException if this client has been closed
   * @see #deleteQueuesChannel(String)
   */
  public CompletableFuture<Boolean> deleteQueuesChannelAsync(String channel) {
    ensureNotClosed();
    return executeWithCancellation(() -> deleteQueuesChannel(channel));
  }

  /**
   * Lists queues channels asynchronously.
   *
   * @param channelSearch a channel name filter; use an empty string or {@code null} to list all
   *     channels
   * @return a {@link CompletableFuture} that completes with a list of {@link QueuesChannel}
   *     matching the filter; completes exceptionally with a {@link KubeMQException} on failure
   * @throws ClientClosedException if this client has been closed
   * @see #listQueuesChannels(String)
   */
  public CompletableFuture<List<QueuesChannel>> listQueuesChannelsAsync(String channelSearch) {
    ensureNotClosed();
    return executeWithCancellation(() -> listQueuesChannels(channelSearch));
  }

  /**
   * Gets waiting messages from a queue asynchronously.
   *
   * @param channel the name of the queue channel to peek (must not be null)
   * @param maxMessages the maximum number of messages to peek (must be &gt; 0)
   * @param waitTimeoutInSeconds the maximum time to wait for messages in seconds (must be &gt; 0)
   * @return a {@link CompletableFuture} that completes with a {@link QueueMessagesWaiting} object
   *     containing the peeked messages without removing them from the queue; completes
   *     exceptionally on failure
   * @throws ClientClosedException if this client has been closed
   * @see #waiting(String, int, int)
   */
  public CompletableFuture<QueueMessagesWaiting> waitingAsync(
      String channel, int maxMessages, int waitTimeoutInSeconds) {
    ensureNotClosed();
    return executeWithCancellation(() -> waiting(channel, maxMessages, waitTimeoutInSeconds));
  }

  /**
   * Pulls messages from a queue asynchronously.
   *
   * @param channel the name of the queue channel to pull from (must not be null)
   * @param maxMessages the maximum number of messages to pull (must be &gt; 0)
   * @param waitTimeoutInSeconds the maximum time to wait for messages in seconds (must be &gt; 0)
   * @return a {@link CompletableFuture} that completes with a {@link QueueMessagesPulled} object
   *     containing the pulled messages (removed from the queue); completes exceptionally on
   *     failure
   * @throws ClientClosedException if this client has been closed
   * @see #pull(String, int, int)
   */
  public CompletableFuture<QueueMessagesPulled> pullAsync(
      String channel, int maxMessages, int waitTimeoutInSeconds) {
    ensureNotClosed();
    return executeWithCancellation(() -> pull(channel, maxMessages, waitTimeoutInSeconds));
  }

  /**
   * Purges all messages from a queue channel.
   *
   * <p><strong>Not yet implemented.</strong> The KubeMQ server supports this operation ({@code
   * AckAllQueueMessages}), but this SDK version does not expose it. See {@code
   * clients/feature-matrix.md} for status.
   *
   * @param channel the queue channel to purge
   * @throws NotImplementedException always
   */
  public void purgeQueue(String channel) {
    throw NotImplementedException.builder()
        .message(
            "purgeQueue is not implemented in this SDK version. "
                + "Server supports AckAllQueueMessages. "
                + "See clients/feature-matrix.md for tracking.")
        .operation("purgeQueue")
        .channel(channel)
        .build();
  }
}
