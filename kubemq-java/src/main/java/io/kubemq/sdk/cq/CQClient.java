package io.kubemq.sdk.cq;

import io.kubemq.sdk.auth.CredentialProvider;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.client.Subscription;
import io.kubemq.sdk.common.ChannelDecoder;
import io.kubemq.sdk.common.KubeMQUtils;
import io.kubemq.sdk.exception.AuthenticationException;
import io.kubemq.sdk.exception.ClientClosedException;
import io.kubemq.sdk.exception.ConnectionException;
import io.kubemq.sdk.exception.GRPCException;
import io.kubemq.sdk.exception.KubeMQException;
import io.kubemq.sdk.exception.KubeMQTimeoutException;
import io.kubemq.sdk.exception.ValidationException;
import io.kubemq.sdk.observability.KubeMQLogger;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.concurrent.ThreadSafe;
import kubemq.Kubemq;
import lombok.Builder;

/**
 * CQClient represents a client for connecting to the KubeMQ server for command and query
 * operations.
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe. A single instance should be shared
 * across all threads. See {@link KubeMQClient} for usage patterns.
 */
@ThreadSafe
public class CQClient extends KubeMQClient {

  // JV-1b + JV-1c: Grace period (ms) after reconnection before accepting new command/query RPCs.
  // This allows subscription responders to re-establish before senders resume,
  // preventing server-side timeouts when the broker has no responders yet.
  // Set to 3000ms to cover: subscription ready-poll (~200ms) + gRPC subscribe round-trip
  // + broker-side registration + safety margin. Previously 2000ms which was insufficient
  // for queries when the subscription reconnect poll fired late in its cycle.
  private static final long POST_RECONNECT_STABILIZATION_MS = 3000;

  @Builder
  public CQClient(
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
      KubeMQClient.Level logLevel,
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
  }

  /**
   * Sends a command to the specified channel. Convenience method equivalent to building a {@link
   * CommandMessage} and calling {@link #sendCommand(CommandMessage)}.
   *
   * @param channel the target channel name (must not be null or empty)
   * @param body the command payload as a byte array; {@code null} is treated as empty
   * @param timeoutInSeconds the maximum time in seconds to wait for a response from a command
   *     subscriber
   * @return a {@link CommandResponseMessage} indicating whether the command was executed and any
   *     error reported by the responder
   * @throws ValidationException if the channel name is null or empty
   * @throws ClientClosedException if this client has been closed
   * @throws KubeMQTimeoutException if no response is received within the specified timeout
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws KubeMQException if the command fails for any other reason
   * @see CommandMessage
   * @see #sendCommand(CommandMessage)
   */
  @SuppressWarnings("deprecation")
  public CommandResponseMessage sendCommand(String channel, byte[] body, int timeoutInSeconds) {
    return sendCommandRequest(
        CommandMessage.builder()
            .channel(channel)
            .body(body != null ? body : new byte[0])
            .timeoutInSeconds(timeoutInSeconds)
            .build());
  }

  /**
   * Sends a query to the specified channel. Convenience method equivalent to building a {@link
   * QueryMessage} and calling {@link #sendQuery(QueryMessage)}.
   *
   * @param channel the target channel name (must not be null or empty)
   * @param body the query payload as a byte array; {@code null} is treated as empty
   * @param timeoutInSeconds the maximum time in seconds to wait for a response from a query
   *     subscriber
   * @return a {@link QueryResponseMessage} containing the responder's body, metadata, and
   *     execution status
   * @throws ValidationException if the channel name is null or empty
   * @throws ClientClosedException if this client has been closed
   * @throws KubeMQTimeoutException if no response is received within the specified timeout
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws KubeMQException if the query fails for any other reason
   * @see QueryMessage
   * @see #sendQuery(QueryMessage)
   */
  @SuppressWarnings("deprecation")
  public QueryResponseMessage sendQuery(String channel, byte[] body, int timeoutInSeconds) {
    return sendQueryRequest(
        QueryMessage.builder()
            .channel(channel)
            .body(body != null ? body : new byte[0])
            .timeoutInSeconds(timeoutInSeconds)
            .build());
  }

  /**
   * Sends a command to the KubeMQ server. This is the preferred method name per cross-SDK verb
   * alignment.
   *
   * <p><b>Example:</b></p>
   * <pre>{@code
   * CQClient client = CQClient.builder()
   *     .address("localhost:50000")
   *     .clientId("command-sender")
   *     .build();
   *
   * CommandMessage command = CommandMessage.builder()
   *     .channel("commands.device-control")
   *     .body("restart".getBytes())
   *     .timeoutInSeconds(10)
   *     .build();
   *
   * CommandResponseMessage response = client.sendCommand(command);
   * System.out.println("Executed: " + response.isExecuted());
   * }</pre>
   *
   * @param message the command message specifying the target channel, body, metadata, optional
   *     tags, and timeout
   * @return a {@link CommandResponseMessage} indicating whether the command was executed
   *     successfully and any error reported by the responder
   * @throws ValidationException if the message is missing required fields (e.g. channel, timeout)
   * @throws ClientClosedException if this client has been closed
   * @throws KubeMQTimeoutException if no response is received within the command's timeout
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws KubeMQException if the command fails for any other reason
   * @see CommandMessage
   * @see CommandResponseMessage
   * @see #subscribeToCommands(CommandsSubscription)
   * @see #sendCommandRequest(CommandMessage)
   */
  @SuppressWarnings("deprecation")
  public CommandResponseMessage sendCommand(CommandMessage message) {
    return sendCommandRequest(message);
  }

  /**
   * Sends a command request to the KubeMQ server.
   *
   * @param message the command message specifying the target channel, body, and timeout
   * @return a {@link CommandResponseMessage} indicating whether the command was executed
   * @throws ValidationException if the message is missing required fields (e.g. channel, timeout)
   * @throws ClientClosedException if this client has been closed
   * @throws KubeMQTimeoutException if no response is received within the command's timeout
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws KubeMQException if the command fails for any other reason
   * @see CommandMessage
   * @see CommandResponseMessage
   * @deprecated Use {@link #sendCommand(CommandMessage)} instead. This method will be removed in
   *     v3.0.
   */
  @Deprecated(since = "2.2.0", forRemoval = true)
  public CommandResponseMessage sendCommandRequest(CommandMessage message) {
    ensureNotClosed();
    // JV-1 + JV-1b: Fast-fail during reconnection and stabilization window
    if (!isConnectionReadyAndStabilized(POST_RECONNECT_STABILIZATION_MS)) {
      throw io.kubemq.sdk.exception.ConnectionNotReadyException.create(
          getConnectionState() == io.kubemq.sdk.client.ConnectionState.READY
              ? "STABILIZING" : getConnectionState().name());
    }
    message.validate();
    Kubemq.Request request = message.encode(this.getClientId());
    Kubemq.Response response = this.getClient()
        .withDeadlineAfter(message.getTimeoutInSeconds() + 2, java.util.concurrent.TimeUnit.SECONDS)
        .sendRequest(request);
    getLogger().debug("sendCommandRequest response received");
    return CommandResponseMessage.builder().build().decode(response);
  }

  /**
   * Sends a query to the KubeMQ server. This is the preferred method name per cross-SDK verb
   * alignment.
   *
   * <p><b>Example:</b></p>
   * <pre>{@code
   * CQClient client = CQClient.builder()
   *     .address("localhost:50000")
   *     .clientId("query-sender")
   *     .build();
   *
   * QueryMessage query = QueryMessage.builder()
   *     .channel("queries.user-lookup")
   *     .body("{\"userId\": 42}".getBytes())
   *     .timeoutInSeconds(10)
   *     .build();
   *
   * QueryResponseMessage response = client.sendQuery(query);
   * System.out.println("Result: " + new String(response.getBody()));
   * }</pre>
   *
   * @param message the query message specifying the target channel, body, metadata, optional tags,
   *     and timeout
   * @return a {@link QueryResponseMessage} containing the responder's body, metadata, tags, and
   *     execution status
   * @throws ValidationException if the message is missing required fields (e.g. channel, timeout)
   * @throws ClientClosedException if this client has been closed
   * @throws KubeMQTimeoutException if no response is received within the query's timeout
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws KubeMQException if the query fails for any other reason
   * @see QueryMessage
   * @see QueryResponseMessage
   * @see #subscribeToQueries(QueriesSubscription)
   * @see #sendQueryRequest(QueryMessage)
   */
  @SuppressWarnings("deprecation")
  public QueryResponseMessage sendQuery(QueryMessage message) {
    return sendQueryRequest(message);
  }

  /**
   * Sends a query request to the KubeMQ server.
   *
   * @param message the query message specifying the target channel, body, and timeout
   * @return a {@link QueryResponseMessage} containing the responder's body, metadata, and
   *     execution status
   * @throws ValidationException if the message is missing required fields (e.g. channel, timeout)
   * @throws ClientClosedException if this client has been closed
   * @throws KubeMQTimeoutException if no response is received within the query's timeout
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws KubeMQException if the query fails for any other reason
   * @see QueryMessage
   * @see QueryResponseMessage
   * @deprecated Use {@link #sendQuery(QueryMessage)} instead. This method will be removed in v3.0.
   */
  @Deprecated(since = "2.2.0", forRemoval = true)
  public QueryResponseMessage sendQueryRequest(QueryMessage message) {
    ensureNotClosed();
    // JV-1 + JV-1b: Fast-fail during reconnection and stabilization window
    if (!isConnectionReadyAndStabilized(POST_RECONNECT_STABILIZATION_MS)) {
      throw io.kubemq.sdk.exception.ConnectionNotReadyException.create(
          getConnectionState() == io.kubemq.sdk.client.ConnectionState.READY
              ? "STABILIZING" : getConnectionState().name());
    }
    message.validate();
    Kubemq.Request request = message.encode(this.getClientId());
    Kubemq.Response response = this.getClient()
        .withDeadlineAfter(message.getTimeoutInSeconds() + 2, java.util.concurrent.TimeUnit.SECONDS)
        .sendRequest(request);
    getLogger().debug("sendQueryRequest response received");
    return QueryResponseMessage.builder().build().decode(response);
  }

  /**
   * Sends a response message to the KubeMQ server.
   *
   * @param message the command response to send back to the command issuer, containing the
   *     execution status and optional error description
   * @throws ValidationException if the response message is missing required fields
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws KubeMQException if sending the response fails
   * @see CommandResponseMessage
   * @see CommandMessageReceived
   * @see #subscribeToCommands(CommandsSubscription)
   */
  public void sendResponseMessage(CommandResponseMessage message) {
    ensureNotClosed();
    message.validate();
    try {
      this.getClient()
          .withDeadlineAfter(getRequestTimeoutSeconds(), java.util.concurrent.TimeUnit.SECONDS)
          .sendResponse(message.encode(this.getClientId()));
    } catch (io.grpc.StatusRuntimeException e) {
      throw io.kubemq.sdk.exception.GrpcErrorMapper.map(e, "sendCommandResponse", null, null, false);
    }
  }

  /**
   * Sends a response message to the KubeMQ server.
   *
   * @param message the query response to send back to the query issuer, containing the result
   *     body, metadata, and execution status
   * @throws ValidationException if the response message is missing required fields
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws KubeMQException if sending the response fails
   * @see QueryResponseMessage
   * @see QueryMessageReceived
   * @see #subscribeToQueries(QueriesSubscription)
   */
  public void sendResponseMessage(QueryResponseMessage message) {
    ensureNotClosed();
    message.validate();
    try {
      this.getClient()
          .withDeadlineAfter(getRequestTimeoutSeconds(), java.util.concurrent.TimeUnit.SECONDS)
          .sendResponse(message.encode(this.getClientId()));
    } catch (io.grpc.StatusRuntimeException e) {
      throw io.kubemq.sdk.exception.GrpcErrorMapper.map(e, "sendQueryResponse", null, null, false);
    }
  }

  /**
   * Creates a commands channel on the KubeMQ server.
   *
   * @param channel the name of the commands channel to create (must not be null or empty)
   * @return {@code true} if the channel was created successfully
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws KubeMQException if creating the channel fails
   * @see CQChannel
   * @see #deleteCommandsChannel(String)
   * @see #listCommandsChannels(String)
   */
  public boolean createCommandsChannel(String channel) {
    ensureNotClosed();
    return KubeMQUtils.createChannelRequest(this, this.getClientId(), channel, "commands");
  }

  /**
   * Creates a queries channel on the KubeMQ server.
   *
   * @param channel the name of the queries channel to create (must not be null or empty)
   * @return {@code true} if the channel was created successfully
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws KubeMQException if creating the channel fails
   * @see CQChannel
   * @see #deleteQueriesChannel(String)
   * @see #listQueriesChannels(String)
   */
  public boolean createQueriesChannel(String channel) {
    ensureNotClosed();
    return KubeMQUtils.createChannelRequest(this, this.getClientId(), channel, "queries");
  }

  /**
   * Deletes a commands channel from the KubeMQ server.
   *
   * @param channel the name of the commands channel to delete (must not be null or empty)
   * @return {@code true} if the channel was deleted successfully
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws KubeMQException if deleting the channel fails
   * @see #createCommandsChannel(String)
   */
  public boolean deleteCommandsChannel(String channel) {
    ensureNotClosed();
    return KubeMQUtils.deleteChannelRequest(this, this.getClientId(), channel, "commands");
  }

  /**
   * Deletes a queries channel from the KubeMQ server.
   *
   * @param channel the name of the queries channel to delete (must not be null or empty)
   * @return {@code true} if the channel was deleted successfully
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws KubeMQException if deleting the channel fails
   * @see #createQueriesChannel(String)
   */
  public boolean deleteQueriesChannel(String channel) {
    ensureNotClosed();
    return KubeMQUtils.deleteChannelRequest(this, this.getClientId(), channel, "queries");
  }

  /**
   * Lists the available commands channels on the KubeMQ server.
   *
   * @param channelSearch a channel name filter; use an empty string or {@code null} to list all
   *     channels, or a partial name to match channels containing that substring
   * @return a list of {@link CQChannel} objects for commands channels matching the search
   *     criteria, including channel statistics (e.g. active subscribers)
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws KubeMQException if listing channels fails
   * @see CQChannel
   * @see #createCommandsChannel(String)
   */
  public List<CQChannel> listCommandsChannels(String channelSearch) {
    ensureNotClosed();
    return KubeMQUtils.listCQChannels(this, this.getClientId(), "commands", channelSearch);
  }

  /**
   * Lists the available queries channels on the KubeMQ server.
   *
   * @param channelSearch a channel name filter; use an empty string or {@code null} to list all
   *     channels, or a partial name to match channels containing that substring
   * @return a list of {@link CQChannel} objects for queries channels matching the search
   *     criteria, including channel statistics (e.g. active subscribers)
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws KubeMQException if listing channels fails
   * @see CQChannel
   * @see #createQueriesChannel(String)
   */
  public List<CQChannel> listQueriesChannels(String channelSearch) {
    ensureNotClosed();
    return KubeMQUtils.listCQChannels(this, this.getClientId(), "queries", channelSearch);
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

  // ---- Async API Methods (REQ-CONC-2 / REQ-CONC-4) ----

  /**
   * Sends a command request asynchronously.
   *
   * @param message the command message specifying the target channel, body, and timeout
   * @return a {@link CompletableFuture} that completes with a {@link CommandResponseMessage}
   *     indicating execution status; completes exceptionally with a {@link KubeMQException}
   *     subtype on failure
   * @throws ValidationException if the message is missing required fields (e.g. channel, timeout)
   * @throws ClientClosedException if this client has been closed
   * @see CommandMessage
   * @see CommandResponseMessage
   * @see #sendCommand(CommandMessage)
   */
  public CompletableFuture<CommandResponseMessage> sendCommandRequestAsync(CommandMessage message) {
    ensureNotClosed();
    // JV-1 + JV-1b: Fast-fail during reconnection AND during the post-reconnect
    // stabilization window. This prevents sending commands to a broker that has no
    // subscribed responders yet (which would cause a 5-second server-side timeout).
    if (!isConnectionReadyAndStabilized(POST_RECONNECT_STABILIZATION_MS)) {
      CompletableFuture<CommandResponseMessage> rejected = new CompletableFuture<>();
      rejected.completeExceptionally(
          io.kubemq.sdk.exception.ConnectionNotReadyException.create(
              getConnectionState() == io.kubemq.sdk.client.ConnectionState.READY
                  ? "STABILIZING" : getConnectionState().name()));
      return rejected;
    }
    message.validate();
    CompletableFuture<CommandResponseMessage> future = new CompletableFuture<>();
    getInFlightOperations().incrementAndGet();
    // Ensure inFlightOperations is decremented exactly once, regardless of completion path
    // (onCompleted, onError, or external cancellation/timeout)
    future.whenComplete((r, ex) -> getInFlightOperations().decrementAndGet());
    Kubemq.Request request = message.encode(this.getClientId());
    // True async: use the gRPC async stub with StreamObserver callback.
    // No thread is blocked waiting for the response.
    this.getAsyncClient()
        .withDeadlineAfter(
            message.getTimeoutInSeconds() + 2, java.util.concurrent.TimeUnit.SECONDS)
        .sendRequest(
            request,
            new io.grpc.stub.StreamObserver<Kubemq.Response>() {
              @Override
              public void onNext(Kubemq.Response response) {
                future.complete(CommandResponseMessage.builder().build().decode(response));
              }

              @Override
              public void onError(Throwable t) {
                future.completeExceptionally(t);
              }

              @Override
              public void onCompleted() {
                if (!future.isDone()) {
                  future.complete(CommandResponseMessage.builder().build());
                }
              }
            });
    return future;
  }

  /**
   * Sends a command request with a custom timeout.
   *
   * @param message the command message specifying the target channel, body, and timeout
   * @param timeout the maximum time to wait for the response before throwing
   * @return a {@link CommandResponseMessage} indicating whether the command was executed
   * @throws KubeMQTimeoutException if the operation exceeds the specified timeout
   * @throws ValidationException if the message is missing required fields (e.g. channel, timeout)
   * @throws ClientClosedException if this client has been closed
   * @throws KubeMQException if the command fails
   * @see #sendCommandRequestAsync(CommandMessage)
   */
  public CommandResponseMessage sendCommandRequest(CommandMessage message, Duration timeout) {
    return unwrapFuture(sendCommandRequestAsync(message), timeout);
  }

  /**
   * Sends a query request asynchronously.
   *
   * @param message the query message specifying the target channel, body, and timeout
   * @return a {@link CompletableFuture} that completes with a {@link QueryResponseMessage}
   *     containing the responder's body, metadata, and execution status; completes exceptionally
   *     with a {@link KubeMQException} subtype on failure
   * @throws ValidationException if the message is missing required fields (e.g. channel, timeout)
   * @throws ClientClosedException if this client has been closed
   * @see QueryMessage
   * @see QueryResponseMessage
   * @see #sendQuery(QueryMessage)
   */
  public CompletableFuture<QueryResponseMessage> sendQueryRequestAsync(QueryMessage message) {
    ensureNotClosed();
    // JV-1 + JV-1b: Fast-fail during reconnection AND during the post-reconnect
    // stabilization window. This prevents sending queries to a broker that has no
    // subscribed responders yet (which would cause a 5-second server-side timeout).
    if (!isConnectionReadyAndStabilized(POST_RECONNECT_STABILIZATION_MS)) {
      CompletableFuture<QueryResponseMessage> rejected = new CompletableFuture<>();
      rejected.completeExceptionally(
          io.kubemq.sdk.exception.ConnectionNotReadyException.create(
              getConnectionState() == io.kubemq.sdk.client.ConnectionState.READY
                  ? "STABILIZING" : getConnectionState().name()));
      return rejected;
    }
    message.validate();
    CompletableFuture<QueryResponseMessage> future = new CompletableFuture<>();
    getInFlightOperations().incrementAndGet();
    future.whenComplete((r, ex) -> getInFlightOperations().decrementAndGet());
    Kubemq.Request request = message.encode(this.getClientId());
    // True async: use the gRPC async stub with StreamObserver callback.
    // No thread is blocked waiting for the response.
    this.getAsyncClient()
        .withDeadlineAfter(
            message.getTimeoutInSeconds() + 2, java.util.concurrent.TimeUnit.SECONDS)
        .sendRequest(
            request,
            new io.grpc.stub.StreamObserver<Kubemq.Response>() {
              @Override
              public void onNext(Kubemq.Response response) {
                future.complete(QueryResponseMessage.builder().build().decode(response));
              }

              @Override
              public void onError(Throwable t) {
                future.completeExceptionally(t);
              }

              @Override
              public void onCompleted() {
                if (!future.isDone()) {
                  future.complete(QueryResponseMessage.builder().build());
                }
              }
            });
    return future;
  }

  /**
   * Sends a query request with a custom timeout.
   *
   * @param message the query message specifying the target channel, body, and timeout
   * @param timeout the maximum time to wait for the response before throwing
   * @return a {@link QueryResponseMessage} containing the responder's body, metadata, and
   *     execution status
   * @throws KubeMQTimeoutException if the operation exceeds the specified timeout
   * @throws ValidationException if the message is missing required fields (e.g. channel, timeout)
   * @throws ClientClosedException if this client has been closed
   * @throws KubeMQException if the query fails
   * @see #sendQueryRequestAsync(QueryMessage)
   */
  public QueryResponseMessage sendQueryRequest(QueryMessage message, Duration timeout) {
    return unwrapFuture(sendQueryRequestAsync(message), timeout);
  }

  /**
   * Sends a command response message asynchronously.
   *
   * @param message the command response to send back to the command issuer, containing the
   *     execution status and optional error description
   * @return a {@link CompletableFuture} that completes when the response has been sent to the
   *     server; completes exceptionally with a {@link KubeMQException} on failure
   * @throws ValidationException if the response message is missing required fields
   * @throws ClientClosedException if this client has been closed
   * @see CommandResponseMessage
   * @see #subscribeToCommands(CommandsSubscription)
   */
  public CompletableFuture<Void> sendResponseMessageAsync(CommandResponseMessage message) {
    ensureNotClosed();
    message.validate();
    return CompletableFuture.runAsync(
        () -> {
          this.getClient().sendResponse(message.encode(this.getClientId()));
        },
        getAsyncOperationExecutor());
  }

  /**
   * Sends a query response message asynchronously.
   *
   * @param message the query response to send back to the query issuer, containing the result
   *     body, metadata, and execution status
   * @return a {@link CompletableFuture} that completes when the response has been sent to the
   *     server; completes exceptionally with a {@link KubeMQException} on failure
   * @throws ValidationException if the response message is missing required fields
   * @throws ClientClosedException if this client has been closed
   * @see QueryResponseMessage
   * @see #subscribeToQueries(QueriesSubscription)
   */
  public CompletableFuture<Void> sendResponseMessageAsync(QueryResponseMessage message) {
    ensureNotClosed();
    message.validate();
    return CompletableFuture.runAsync(
        () -> {
          this.getClient().sendResponse(message.encode(this.getClientId()));
        },
        getAsyncOperationExecutor());
  }

  /**
   * Creates a commands channel asynchronously.
   *
   * @param channel the name of the commands channel to create (must not be null or empty)
   * @return a {@link CompletableFuture} that completes with {@code true} if the channel was
   *     created successfully; completes exceptionally with a {@link KubeMQException} on failure
   * @throws ClientClosedException if this client has been closed
   * @see #createCommandsChannel(String)
   */
  public CompletableFuture<Boolean> createCommandsChannelAsync(String channel) {
    ensureNotClosed();
    return executeWithCancellation(() -> createCommandsChannel(channel));
  }

  /**
   * Creates a queries channel asynchronously.
   *
   * @param channel the name of the queries channel to create (must not be null or empty)
   * @return a {@link CompletableFuture} that completes with {@code true} if the channel was
   *     created successfully; completes exceptionally with a {@link KubeMQException} on failure
   * @throws ClientClosedException if this client has been closed
   * @see #createQueriesChannel(String)
   */
  public CompletableFuture<Boolean> createQueriesChannelAsync(String channel) {
    ensureNotClosed();
    return executeWithCancellation(() -> createQueriesChannel(channel));
  }

  /**
   * Deletes a commands channel asynchronously.
   *
   * @param channel the name of the commands channel to delete (must not be null or empty)
   * @return a {@link CompletableFuture} that completes with {@code true} if the channel was
   *     deleted successfully; completes exceptionally with a {@link KubeMQException} on failure
   * @throws ClientClosedException if this client has been closed
   * @see #deleteCommandsChannel(String)
   */
  public CompletableFuture<Boolean> deleteCommandsChannelAsync(String channel) {
    ensureNotClosed();
    return executeWithCancellation(() -> deleteCommandsChannel(channel));
  }

  /**
   * Deletes a queries channel asynchronously.
   *
   * @param channel the name of the queries channel to delete (must not be null or empty)
   * @return a {@link CompletableFuture} that completes with {@code true} if the channel was
   *     deleted successfully; completes exceptionally with a {@link KubeMQException} on failure
   * @throws ClientClosedException if this client has been closed
   * @see #deleteQueriesChannel(String)
   */
  public CompletableFuture<Boolean> deleteQueriesChannelAsync(String channel) {
    ensureNotClosed();
    return executeWithCancellation(() -> deleteQueriesChannel(channel));
  }

  /**
   * Lists commands channels asynchronously.
   *
   * @param channelSearch a channel name filter; use an empty string or {@code null} to list all
   *     channels
   * @return a {@link CompletableFuture} that completes with a list of {@link CQChannel} matching
   *     the filter; completes exceptionally with a {@link KubeMQException} on failure
   * @throws ClientClosedException if this client has been closed
   * @see #listCommandsChannels(String)
   */
  public CompletableFuture<List<CQChannel>> listCommandsChannelsAsync(String channelSearch) {
    ensureNotClosed();
    return executeWithCancellation(() -> listCommandsChannels(channelSearch));
  }

  /**
   * Lists queries channels asynchronously.
   *
   * @param channelSearch a channel name filter; use an empty string or {@code null} to list all
   *     channels
   * @return a {@link CompletableFuture} that completes with a list of {@link CQChannel} matching
   *     the filter; completes exceptionally with a {@link KubeMQException} on failure
   * @throws ClientClosedException if this client has been closed
   * @see #listQueriesChannels(String)
   */
  public CompletableFuture<List<CQChannel>> listQueriesChannelsAsync(String channelSearch) {
    ensureNotClosed();
    return executeWithCancellation(() -> listQueriesChannels(channelSearch));
  }

  /**
   * Subscribes to commands and returns a cancellable Subscription handle.
   *
   * @param commandsSubscription the subscription configuration specifying the channel, optional
   *     consumer group, {@code onReceiveCommand} callback, and {@code onError} callback
   * @return a {@link Subscription} handle whose {@link Subscription#cancel()} method stops the
   *     subscription
   * @throws ValidationException if the subscription is missing required fields
   * @throws ClientClosedException if this client has been closed
   * @see CommandsSubscription
   * @see CommandMessageReceived
   * @see #subscribeToCommands(CommandsSubscription)
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
   *
   * @param queriesSubscription the subscription configuration specifying the channel, optional
   *     consumer group, {@code onReceiveQuery} callback, and {@code onError} callback
   * @return a {@link Subscription} handle whose {@link Subscription#cancel()} method stops the
   *     subscription
   * @throws ValidationException if the subscription is missing required fields
   * @throws ClientClosedException if this client has been closed
   * @see QueriesSubscription
   * @see QueryMessageReceived
   * @see #subscribeToQueries(QueriesSubscription)
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
   * @param commandsSubscription the subscription configuration specifying the channel, optional
   *     consumer group, {@code onReceiveCommand} callback, and {@code onError} callback
   * @return the same subscription object, now active, with {@link CommandsSubscription#cancel()}
   *     available for stopping the subscription
   * @throws ValidationException if the subscription is missing required fields (e.g. channel or
   *     callbacks)
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws KubeMQException if subscribing fails for any other reason
   * @see CommandsSubscription
   * @see CommandMessageReceived
   * @see #sendCommand(CommandMessage)
   * @see #sendResponseMessage(CommandResponseMessage)
   */
  public CommandsSubscription subscribeToCommands(CommandsSubscription commandsSubscription) {
    ensureNotClosed();
    commandsSubscription.validate();
    Kubemq.Subscribe subscribe = commandsSubscription.encode(this.getClientId(), this);
    this.getAsyncClient().subscribeToRequests(subscribe, commandsSubscription.getObserver());
    return commandsSubscription;
  }

  /**
   * Subscribes to queries and returns the subscription handle for lifecycle control.
   *
   * @param queriesSubscription the subscription configuration specifying the channel, optional
   *     consumer group, {@code onReceiveQuery} callback, and {@code onError} callback
   * @return the same subscription object, now active, with {@link QueriesSubscription#cancel()}
   *     available for stopping the subscription
   * @throws ValidationException if the subscription is missing required fields (e.g. channel or
   *     callbacks)
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws KubeMQException if subscribing fails for any other reason
   * @see QueriesSubscription
   * @see QueryMessageReceived
   * @see #sendQuery(QueryMessage)
   * @see #sendResponseMessage(QueryResponseMessage)
   */
  public QueriesSubscription subscribeToQueries(QueriesSubscription queriesSubscription) {
    ensureNotClosed();
    queriesSubscription.validate();
    Kubemq.Subscribe subscribe = queriesSubscription.encode(this.getClientId(), this);
    this.getAsyncClient().subscribeToRequests(subscribe, queriesSubscription.getObserver());
    return queriesSubscription;
  }
}
