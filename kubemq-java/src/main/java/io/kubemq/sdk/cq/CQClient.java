package io.kubemq.sdk.cq;

import io.kubemq.sdk.auth.CredentialProvider;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.client.Subscription;
import io.kubemq.sdk.common.KubeMQUtils;
import io.kubemq.sdk.exception.GRPCException;
import io.kubemq.sdk.observability.KubeMQLogger;
import kubemq.Kubemq;
import lombok.Builder;

import javax.annotation.concurrent.ThreadSafe;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * CQClient represents a client for connecting to the KubeMQ server for command and query operations.
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe. A single instance should be
 * shared across all threads. See {@link KubeMQClient} for usage patterns.</p>
 */
@ThreadSafe
public class CQClient extends KubeMQClient {

    @Builder
    public CQClient(String address, String clientId, String authToken, Boolean tls,
                    String tlsCertFile, String tlsKeyFile, String caCertFile,
                    byte[] caCertPem, byte[] tlsCertPem, byte[] tlsKeyPem,
                    String serverNameOverride, boolean insecureSkipVerify,
                    CredentialProvider credentialProvider,
                    int maxReceiveSize, int reconnectIntervalSeconds, Boolean keepAlive,
                    int pingIntervalInSeconds, int pingTimeoutInSeconds, KubeMQClient.Level logLevel,
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
    }


    /**
     * Sends a command to the specified channel.
     * Convenience method equivalent to building a {@link CommandMessage} and calling
     * {@link #sendCommand(CommandMessage)}.
     *
     * @param channel          the target channel name
     * @param body             the command body
     * @param timeoutInSeconds the timeout for the command in seconds
     * @return the command response
     */
    @SuppressWarnings("deprecation")
    public CommandResponseMessage sendCommand(String channel, byte[] body, int timeoutInSeconds) {
        return sendCommandRequest(CommandMessage.builder()
            .channel(channel)
            .body(body != null ? body : new byte[0])
            .timeoutInSeconds(timeoutInSeconds)
            .build());
    }

    /**
     * Sends a query to the specified channel.
     * Convenience method equivalent to building a {@link QueryMessage} and calling
     * {@link #sendQuery(QueryMessage)}.
     *
     * @param channel          the target channel name
     * @param body             the query body
     * @param timeoutInSeconds the timeout for the query in seconds
     * @return the query response
     */
    @SuppressWarnings("deprecation")
    public QueryResponseMessage sendQuery(String channel, byte[] body, int timeoutInSeconds) {
        return sendQueryRequest(QueryMessage.builder()
            .channel(channel)
            .body(body != null ? body : new byte[0])
            .timeoutInSeconds(timeoutInSeconds)
            .build());
    }

    /**
     * Sends a command to the KubeMQ server.
     * This is the preferred method name per cross-SDK verb alignment.
     *
     * @param message the command message to send
     * @return the command response message
     * @see #sendCommandRequest(CommandMessage)
     */
    @SuppressWarnings("deprecation")
    public CommandResponseMessage sendCommand(CommandMessage message) {
        return sendCommandRequest(message);
    }

    /**
     * Sends a command request to the KubeMQ server.
     *
     * @param message the command message to send
     * @return the command response message
     * @throws GRPCException if an error occurs during the gRPC call
     * @deprecated Use {@link #sendCommand(CommandMessage)} instead.
     *             This method will be removed in v3.0.
     */
    @Deprecated(since = "2.2.0", forRemoval = true)
    public CommandResponseMessage sendCommandRequest(CommandMessage message) {
            ensureNotClosed();
            message.validate();
            Kubemq.Request request = message.encode(this.getClientId());
            Kubemq.Response response = this.getClient().sendRequest(request);
            getLogger().debug("sendCommandRequest response received");
            return  CommandResponseMessage.builder().build().decode(response);
    }

    /**
     * Sends a query to the KubeMQ server.
     * This is the preferred method name per cross-SDK verb alignment.
     *
     * @param message the query message to send
     * @return the query response message
     * @see #sendQueryRequest(QueryMessage)
     */
    @SuppressWarnings("deprecation")
    public QueryResponseMessage sendQuery(QueryMessage message) {
        return sendQueryRequest(message);
    }

    /**
     * Sends a query request to the KubeMQ server.
     *
     * @param message the query message to send
     * @return the query response message
     * @throws GRPCException if an error occurs during the gRPC call
     * @deprecated Use {@link #sendQuery(QueryMessage)} instead.
     *             This method will be removed in v3.0.
     */
    @Deprecated(since = "2.2.0", forRemoval = true)
    public QueryResponseMessage sendQueryRequest(QueryMessage message) {
            ensureNotClosed();
            message.validate();
            Kubemq.Request request = message.encode(this.getClientId());
            Kubemq.Response response = this.getClient().sendRequest(request);
            getLogger().debug("sendQueryRequest response received");
            return  QueryResponseMessage.builder().build().decode(response);
    }

    /**
     * Sends a response message to the KubeMQ server.
     *
     * @param message the response message to send
     * @throws GRPCException if an error occurs during the gRPC call
     */
    public void sendResponseMessage(CommandResponseMessage message) {
            ensureNotClosed();
            message.validate();
        this.getClient().sendResponse(message.encode(this.getClientId()));
    }

    /**
     * Sends a response message to the KubeMQ server.
     *
     * @param message the response message to send
     * @throws GRPCException if an error occurs during the gRPC call
     */
    public void sendResponseMessage(QueryResponseMessage message) {
            ensureNotClosed();
            message.validate();
        this.getClient().sendResponse(message.encode(this.getClientId()));
    }

    /**
     * Creates a commands channel on the KubeMQ server.
     *
     * @param channel the name of the channel to create
     * @return true if the channel was created successfully, false otherwise
     */
    public boolean createCommandsChannel(String channel) {
        ensureNotClosed();
        return KubeMQUtils.createChannelRequest(this, this.getClientId(), channel, "commands");
    }

    /**
     * Creates a queries channel on the KubeMQ server.
     *
     * @param channel the name of the channel to create
     * @return true if the channel was created successfully, false otherwise
     */
    public boolean createQueriesChannel(String channel) {
        ensureNotClosed();
        return KubeMQUtils.createChannelRequest(this, this.getClientId(), channel, "queries");
    }

    /**
     * Deletes a commands channel from the KubeMQ server.
     *
     * @param channel the name of the channel to delete
     * @return true if the channel was deleted successfully, false otherwise
     */
    public boolean deleteCommandsChannel(String channel) {
        ensureNotClosed();
        return KubeMQUtils.deleteChannelRequest(this, this.getClientId(), channel, "commands");
    }

    /**
     * Deletes a queries channel from the KubeMQ server.
     *
     * @param channel the name of the channel to delete
     * @return true if the channel was deleted successfully, false otherwise
     */
    public boolean deleteQueriesChannel(String channel) {
        ensureNotClosed();
        return KubeMQUtils.deleteChannelRequest(this, this.getClientId(), channel, "queries");
    }

    /**
     * Lists the available commands channels on the KubeMQ server.
     *
     * @param channelSearch the search pattern for filtering channel names
     * @return a list of CQChannel objects representing the commands channels
     */
    public List<CQChannel> listCommandsChannels(String channelSearch) {
        ensureNotClosed();
        return KubeMQUtils.listCQChannels(this, this.getClientId(), "commands", channelSearch);
    }

    /**
     * Lists the available queries channels on the KubeMQ server.
     *
     * @param channelSearch the search pattern for filtering channel names
     * @return a list of CQChannel objects representing the queries channels
     */
    public List<CQChannel> listQueriesChannels(String channelSearch) {
        ensureNotClosed();
        return KubeMQUtils.listCQChannels(this, this.getClientId(), "queries", channelSearch);
    }

    // ---- Async API Methods (REQ-CONC-2 / REQ-CONC-4) ----

    /**
     * Sends a command request asynchronously.
     *
     * @param message the command message to send
     * @return a CompletableFuture that completes with the response
     */
    public CompletableFuture<CommandResponseMessage> sendCommandRequestAsync(CommandMessage message) {
        ensureNotClosed();
        message.validate();
        return executeWithCancellation(() -> {
            Kubemq.Request request = message.encode(this.getClientId());
            Kubemq.Response response = this.getClient().sendRequest(request);
            return CommandResponseMessage.builder().build().decode(response);
        });
    }

    /**
     * Sends a command request with a custom timeout.
     *
     * @param message the command message
     * @param timeout maximum time to wait
     * @return the command response
     */
    public CommandResponseMessage sendCommandRequest(CommandMessage message, Duration timeout) {
        return unwrapFuture(sendCommandRequestAsync(message), timeout);
    }

    /**
     * Sends a query request asynchronously.
     *
     * @param message the query message to send
     * @return a CompletableFuture that completes with the response
     */
    public CompletableFuture<QueryResponseMessage> sendQueryRequestAsync(QueryMessage message) {
        ensureNotClosed();
        message.validate();
        return executeWithCancellation(() -> {
            Kubemq.Request request = message.encode(this.getClientId());
            Kubemq.Response response = this.getClient().sendRequest(request);
            return QueryResponseMessage.builder().build().decode(response);
        });
    }

    /**
     * Sends a query request with a custom timeout.
     *
     * @param message the query message
     * @param timeout maximum time to wait
     * @return the query response
     */
    public QueryResponseMessage sendQueryRequest(QueryMessage message, Duration timeout) {
        return unwrapFuture(sendQueryRequestAsync(message), timeout);
    }

    /**
     * Sends a command response message asynchronously.
     *
     * @param message the response message to send
     * @return a CompletableFuture that completes when the response is sent
     */
    public CompletableFuture<Void> sendResponseMessageAsync(CommandResponseMessage message) {
        ensureNotClosed();
        message.validate();
        return CompletableFuture.runAsync(() -> {
            this.getClient().sendResponse(message.encode(this.getClientId()));
        }, getAsyncOperationExecutor());
    }

    /**
     * Sends a query response message asynchronously.
     *
     * @param message the response message to send
     * @return a CompletableFuture that completes when the response is sent
     */
    public CompletableFuture<Void> sendResponseMessageAsync(QueryResponseMessage message) {
        ensureNotClosed();
        message.validate();
        return CompletableFuture.runAsync(() -> {
            this.getClient().sendResponse(message.encode(this.getClientId()));
        }, getAsyncOperationExecutor());
    }

    /**
     * Creates a commands channel asynchronously.
     */
    public CompletableFuture<Boolean> createCommandsChannelAsync(String channel) {
        ensureNotClosed();
        return executeWithCancellation(() -> createCommandsChannel(channel));
    }

    /**
     * Creates a queries channel asynchronously.
     */
    public CompletableFuture<Boolean> createQueriesChannelAsync(String channel) {
        ensureNotClosed();
        return executeWithCancellation(() -> createQueriesChannel(channel));
    }

    /**
     * Deletes a commands channel asynchronously.
     */
    public CompletableFuture<Boolean> deleteCommandsChannelAsync(String channel) {
        ensureNotClosed();
        return executeWithCancellation(() -> deleteCommandsChannel(channel));
    }

    /**
     * Deletes a queries channel asynchronously.
     */
    public CompletableFuture<Boolean> deleteQueriesChannelAsync(String channel) {
        ensureNotClosed();
        return executeWithCancellation(() -> deleteQueriesChannel(channel));
    }

    /**
     * Lists commands channels asynchronously.
     */
    public CompletableFuture<List<CQChannel>> listCommandsChannelsAsync(String channelSearch) {
        ensureNotClosed();
        return executeWithCancellation(() -> listCommandsChannels(channelSearch));
    }

    /**
     * Lists queries channels asynchronously.
     */
    public CompletableFuture<List<CQChannel>> listQueriesChannelsAsync(String channelSearch) {
        ensureNotClosed();
        return executeWithCancellation(() -> listQueriesChannels(channelSearch));
    }

    /**
     * Subscribes to commands and returns a cancellable Subscription handle.
     */
    public Subscription subscribeToCommandsWithHandle(CommandsSubscription commandsSubscription) {
        ensureNotClosed();
        commandsSubscription.validate();
        Kubemq.Subscribe subscribe = commandsSubscription.encode(this.getClientId(), this);
        this.getAsyncClient().subscribeToRequests(subscribe, commandsSubscription.getObserver());
        return new Subscription(commandsSubscription::cancel, getAsyncOperationExecutor());
    }

    /**
     * Subscribes to queries and returns a cancellable Subscription handle.
     */
    public Subscription subscribeToQueriesWithHandle(QueriesSubscription queriesSubscription) {
        ensureNotClosed();
        queriesSubscription.validate();
        Kubemq.Subscribe subscribe = queriesSubscription.encode(this.getClientId(), this);
        this.getAsyncClient().subscribeToRequests(subscribe, queriesSubscription.getObserver());
        return new Subscription(queriesSubscription::cancel, getAsyncOperationExecutor());
    }

    /**
     * Subscribes to commands and returns the subscription handle for lifecycle control.
     *
     * @param commandsSubscription the subscription configuration
     * @return the same subscription object, now active, with {@link CommandsSubscription#cancel()} available
     */
    public CommandsSubscription subscribeToCommands(CommandsSubscription commandsSubscription) {
        ensureNotClosed();
        commandsSubscription.validate();
        Kubemq.Subscribe subscribe = commandsSubscription.encode(this.getClientId(),this);
        this.getAsyncClient().subscribeToRequests(subscribe, commandsSubscription.getObserver());
        return commandsSubscription;
    }

    /**
     * Subscribes to queries and returns the subscription handle for lifecycle control.
     *
     * @param queriesSubscription the subscription configuration
     * @return the same subscription object, now active, with {@link QueriesSubscription#cancel()} available
     */
    public QueriesSubscription subscribeToQueries(QueriesSubscription queriesSubscription) {
        ensureNotClosed();
        queriesSubscription.validate();
        Kubemq.Subscribe subscribe = queriesSubscription.encode(this.getClientId(),this);
        this.getAsyncClient().subscribeToRequests(subscribe, queriesSubscription.getObserver());
        return queriesSubscription;
    }
}
