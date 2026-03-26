package io.kubemq.sdk.pubsub;

import io.grpc.StatusRuntimeException;
import io.kubemq.sdk.auth.CredentialProvider;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.client.Subscription;
import io.kubemq.sdk.common.ChannelDecoder;
import io.kubemq.sdk.common.KubeMQUtils;
import io.kubemq.sdk.exception.AuthenticationException;
import io.kubemq.sdk.exception.ClientClosedException;
import io.kubemq.sdk.exception.ConnectionException;
import io.kubemq.sdk.exception.ErrorCategory;
import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.GrpcErrorMapper;
import io.kubemq.sdk.exception.KubeMQException;
import io.kubemq.sdk.exception.KubeMQTimeoutException;
import io.kubemq.sdk.exception.ServerException;
import io.kubemq.sdk.exception.ValidationException;
import io.kubemq.sdk.observability.KubeMQLogger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.concurrent.ThreadSafe;
import kubemq.Kubemq;
import lombok.Builder;

/**
 * PubSubClient is a specialized client for publishing and subscribing to messages using KubeMQ. It
 * provides methods to send messages, create and delete channels, list channels, and subscribe to
 * events.
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe. A single instance should be shared
 * across all threads. See {@link KubeMQClient} for usage patterns.
 */
@ThreadSafe
public class PubSubClient extends KubeMQClient {

  private EventStreamHelper eventStreamHelper;

  @Builder
  public PubSubClient(
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
    eventStreamHelper = new EventStreamHelper();
  }

  /**
   * Publishes an event message. This is the preferred method name per cross-SDK verb alignment.
   *
   * <p><b>Example:</b></p>
   * <pre>{@code
   * PubSubClient client = PubSubClient.builder()
   *     .address("localhost:50000")
   *     .clientId("events-publisher")
   *     .build();
   *
   * EventMessage message = EventMessage.builder()
   *     .channel("events.temperature")
   *     .body("sensor-reading: 23.5".getBytes())
   *     .metadata("source=sensor-1")
   *     .build();
   *
   * client.publishEvent(message);
   * }</pre>
   *
   * @param message the event message containing the target channel, body, metadata, and optional
   *     tags to publish
   * @throws ValidationException if the message is missing required fields (e.g. channel)
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws KubeMQException if publishing the message fails for any other reason
   * @see EventMessage
   * @see #subscribeToEvents(EventsSubscription)
   * @see #sendEventsMessage(EventMessage)
   */
  @SuppressWarnings("deprecation")
  public void publishEvent(EventMessage message) {
    sendEventsMessage(message);
  }

  /**
   * Sends an event message.
   *
   * @param message the event message containing the target channel, body, and metadata to send
   * @throws ValidationException if the message is missing required fields (e.g. channel)
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws KubeMQException if sending the message fails for any other reason
   * @see EventMessage
   * @deprecated Use {@link #publishEvent(EventMessage)} instead. This method will be removed in
   *     v3.0.
   */
  @Deprecated(since = "2.2.0", forRemoval = true)
  public void sendEventsMessage(EventMessage message) {
    ensureNotClosed();
    // JV-1: Fast-fail during reconnection instead of waiting for server timeout
    if (getConnectionState() == io.kubemq.sdk.client.ConnectionState.RECONNECTING) {
      throw io.kubemq.sdk.exception.ConnectionNotReadyException.create("RECONNECTING");
    }
    try {
      getLogger().debug("Sending event message");
      message.validate();
      Kubemq.Event event = message.encode(this.getClientId());
      eventStreamHelper.sendEventMessage(this, event);
    } catch (KubeMQException e) {
      throw e;
    } catch (StatusRuntimeException e) {
      throw GrpcErrorMapper.map(e, "sendEventsMessage", message.getChannel(), null, false);
    } catch (Exception e) {
      getLogger().error("Failed to send event message", e);
      throw KubeMQException.newBuilder()
          .code(ErrorCode.UNKNOWN_ERROR)
          .category(ErrorCategory.FATAL)
          .retryable(false)
          .message("sendEventsMessage failed: " + e.getMessage())
          .operation("sendEventsMessage")
          .channel(message.getChannel())
          .cause(e)
          .build();
    }
  }

  /**
   * Publishes an event store message. This is the preferred method name per cross-SDK verb
   * alignment.
   *
   * <p><b>Example:</b></p>
   * <pre>{@code
   * PubSubClient client = PubSubClient.builder()
   *     .address("localhost:50000")
   *     .clientId("store-publisher")
   *     .build();
   *
   * EventStoreMessage message = EventStoreMessage.builder()
   *     .channel("events_store.orders")
   *     .body("order-id: 12345".getBytes())
   *     .metadata("type=new-order")
   *     .build();
   *
   * EventSendResult result = client.publishEventStore(message);
   * System.out.println("Sent: " + result.isSuccess());
   * }</pre>
   *
   * @param message the event store message containing the target channel, body, metadata, and
   *     optional tags to publish
   * @return an {@link EventSendResult} indicating whether the message was successfully persisted
   *     by the server, including the server-assigned message ID
   * @throws ValidationException if the message is missing required fields (e.g. channel)
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws KubeMQException if publishing the message fails for any other reason
   * @see EventStoreMessage
   * @see EventSendResult
   * @see #subscribeToEventsStore(EventsStoreSubscription)
   * @see #sendEventsStoreMessage(EventStoreMessage)
   */
  @SuppressWarnings("deprecation")
  public EventSendResult publishEventStore(EventStoreMessage message) {
    return sendEventsStoreMessage(message);
  }

  /**
   * Sends an event store message.
   *
   * @param message the event store message containing the target channel, body, and metadata to
   *     send
   * @return an {@link EventSendResult} containing the send status and server-assigned message ID
   * @throws ValidationException if the message is missing required fields (e.g. channel)
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws KubeMQException if sending the message fails for any other reason
   * @see EventStoreMessage
   * @see EventSendResult
   * @deprecated Use {@link #publishEventStore(EventStoreMessage)} instead. This method will be
   *     removed in v3.0.
   */
  @Deprecated(since = "2.2.0", forRemoval = true)
  public EventSendResult sendEventsStoreMessage(EventStoreMessage message) {
    ensureNotClosed();
    // JV-1: Fast-fail during reconnection instead of waiting for server timeout
    if (getConnectionState() == io.kubemq.sdk.client.ConnectionState.RECONNECTING) {
      throw io.kubemq.sdk.exception.ConnectionNotReadyException.create("RECONNECTING");
    }
    try {
      getLogger().debug("Sending event store message");
      message.validate();
      Kubemq.Event event = message.encode(this.getClientId());
      return eventStreamHelper.sendEventStoreMessage(this, event);
    } catch (KubeMQException e) {
      throw e;
    } catch (StatusRuntimeException e) {
      throw GrpcErrorMapper.map(e, "sendEventsStoreMessage", message.getChannel(), null, false);
    } catch (Exception e) {
      getLogger().error("Failed to send event store message", e);
      throw KubeMQException.newBuilder()
          .code(ErrorCode.UNKNOWN_ERROR)
          .category(ErrorCategory.FATAL)
          .retryable(false)
          .message("sendEventsStoreMessage failed: " + e.getMessage())
          .operation("sendEventsStoreMessage")
          .channel(message.getChannel())
          .cause(e)
          .build();
    }
  }

  /**
   * Publishes an event message to the specified channel. Convenience method equivalent to building
   * an {@link EventMessage} and calling {@link #publishEvent(EventMessage)}.
   *
   * @param channel the target channel name (must not be null or empty)
   * @param body the message payload as a byte array; {@code null} is treated as empty
   * @throws ValidationException if the channel name is null or empty
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws KubeMQException if publishing the message fails
   * @see EventMessage
   * @see #publishEvent(EventMessage)
   */
  @SuppressWarnings("deprecation")
  public void publishEvent(String channel, byte[] body) {
    sendEventsMessage(
        EventMessage.builder().channel(channel).body(body != null ? body : new byte[0]).build());
  }

  /**
   * Publishes an event message with a string body to the specified channel.
   *
   * @param channel the target channel name (must not be null or empty)
   * @param body the message body as a string (encoded as UTF-8); {@code null} is treated as empty
   * @throws ValidationException if the channel name is null or empty
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws KubeMQException if publishing the message fails
   * @see #publishEvent(String, byte[])
   */
  public void publishEvent(String channel, String body) {
    publishEvent(channel, body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0]);
  }

  /**
   * Publishes an event store message to the specified channel.
   *
   * @param channel the target channel name (must not be null or empty)
   * @param body the message payload as a byte array; {@code null} is treated as empty
   * @return an {@link EventSendResult} indicating whether the message was persisted by the server
   * @throws ValidationException if the channel name is null or empty
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws KubeMQException if publishing the message fails
   * @see EventStoreMessage
   * @see #publishEventStore(EventStoreMessage)
   */
  @SuppressWarnings("deprecation")
  public EventSendResult publishEventStore(String channel, byte[] body) {
    return sendEventsStoreMessage(
        EventStoreMessage.builder()
            .channel(channel)
            .body(body != null ? body : new byte[0])
            .build());
  }

  /**
   * Creates an events channel with the specified name.
   *
   * @param channelName the name of the events channel to create (must not be null or empty)
   * @return {@code true} if the channel was created successfully
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws ServerException if the server rejects the channel creation
   * @throws KubeMQException if creating the channel fails for any other reason
   * @see PubSubChannel
   * @see #deleteEventsChannel(String)
   * @see #listEventsChannels(String)
   */
  public boolean createEventsChannel(String channelName) {
    return sendChannelManagementRequest(
        "create-channel", "events", channelName, "createEventsChannel");
  }

  /**
   * Creates an events store channel with the specified name.
   *
   * @param channelName the name of the events store channel to create (must not be null or empty)
   * @return {@code true} if the channel was created successfully
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws ServerException if the server rejects the channel creation
   * @throws KubeMQException if creating the channel fails for any other reason
   * @see PubSubChannel
   * @see #deleteEventsStoreChannel(String)
   * @see #listEventsStoreChannels(String)
   */
  public boolean createEventsStoreChannel(String channelName) {
    return sendChannelManagementRequest(
        "create-channel", "events_store", channelName, "createEventsStoreChannel");
  }

  /**
   * Lists events channels that match the search criteria.
   *
   * @param search a channel name filter; use an empty string or {@code null} to list all channels,
   *     or a partial name to match channels containing that substring
   * @return a list of {@link PubSubChannel} objects for events channels matching the search
   *     criteria, including channel statistics (e.g. active subscribers)
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws ServerException if the server fails to process the listing request
   * @throws KubeMQException if listing channels fails for any other reason
   * @see PubSubChannel
   * @see #createEventsChannel(String)
   */
  public List<PubSubChannel> listEventsChannels(String search) {
    return queryChannelList(search, "events", "listEventsChannels");
  }

  /**
   * Lists events store channels that match the search criteria.
   *
   * @param search a channel name filter; use an empty string or {@code null} to list all channels,
   *     or a partial name to match channels containing that substring
   * @return a list of {@link PubSubChannel} objects for events store channels matching the search
   *     criteria, including channel statistics (e.g. active subscribers)
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws ServerException if the server fails to process the listing request
   * @throws KubeMQException if listing channels fails for any other reason
   * @see PubSubChannel
   * @see #createEventsStoreChannel(String)
   */
  public List<PubSubChannel> listEventsStoreChannels(String search) {
    return queryChannelList(search, "events_store", "listEventsStoreChannels");
  }

  /**
   * Subscribes to events and returns the subscription handle for lifecycle control.
   *
   * <p><b>Example:</b></p>
   * <pre>{@code
   * PubSubClient client = PubSubClient.builder()
   *     .address("localhost:50000")
   *     .clientId("events-subscriber")
   *     .build();
   *
   * EventsSubscription subscription = EventsSubscription.builder()
   *     .channel("events.temperature")
   *     .group("consumers")
   *     .onReceiveEvent(event -> {
   *         System.out.println("Received: " + new String(event.getBody()));
   *     })
   *     .onError(err -> {
   *         System.err.println("Error: " + err.getMessage());
   *     })
   *     .build();
   *
   * client.subscribeToEvents(subscription);
   * // Later: subscription.cancel();
   * }</pre>
   *
   * @param subscription the subscription configuration specifying the channel, optional consumer
   *     group, {@code onReceiveEvent} callback, and {@code onError} callback
   * @return the same subscription object, now active, with {@link EventsSubscription#cancel()}
   *     available for stopping the subscription
   * @throws ValidationException if the subscription is missing required fields (e.g. channel or
   *     callbacks)
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws KubeMQException if subscribing to events fails for any other reason
   * @see EventsSubscription
   * @see EventMessageReceived
   * @see #publishEvent(EventMessage)
   */
  public EventsSubscription subscribeToEvents(EventsSubscription subscription) {
    ensureNotClosed();
    try {
      getLogger().debug("Subscribing to events");
      subscription.validate();
      kubemq.Kubemq.Subscribe subscribe = subscription.encode(this.getClientId(), this);
      this.getAsyncClient().subscribeToEvents(subscribe, subscription.getObserver());
      return subscription;
    } catch (KubeMQException e) {
      throw e;
    } catch (StatusRuntimeException e) {
      throw GrpcErrorMapper.map(e, "subscribeToEvents", subscription.getChannel(), null, false);
    } catch (Exception e) {
      getLogger().error("Failed to subscribe to events", e);
      throw KubeMQException.newBuilder()
          .code(ErrorCode.UNKNOWN_ERROR)
          .category(ErrorCategory.FATAL)
          .retryable(false)
          .message("subscribeToEvents failed: " + e.getMessage())
          .operation("subscribeToEvents")
          .channel(subscription.getChannel())
          .cause(e)
          .build();
    }
  }

  /**
   * Subscribes to events store and returns the subscription handle for lifecycle control.
   *
   * @param subscription the subscription configuration specifying the channel, optional consumer
   *     group, start position (e.g. from sequence, from time), {@code onReceiveEvent} callback,
   *     and {@code onError} callback
   * @return the same subscription object, now active, with {@link EventsStoreSubscription#cancel()}
   *     available for stopping the subscription
   * @throws ValidationException if the subscription is missing required fields (e.g. channel or
   *     callbacks)
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws KubeMQException if subscribing to events store fails for any other reason
   * @see EventsStoreSubscription
   * @see EventStoreMessageReceived
   * @see #publishEventStore(EventStoreMessage)
   */
  public EventsStoreSubscription subscribeToEventsStore(EventsStoreSubscription subscription) {
    ensureNotClosed();
    try {
      getLogger().debug("Subscribing to events store");
      subscription.validate();
      kubemq.Kubemq.Subscribe subscribe = subscription.encode(this.getClientId(), this);
      this.getAsyncClient().subscribeToEvents(subscribe, subscription.getObserver());
      return subscription;
    } catch (KubeMQException e) {
      throw e;
    } catch (StatusRuntimeException e) {
      throw GrpcErrorMapper.map(
          e, "subscribeToEventsStore", subscription.getChannel(), null, false);
    } catch (Exception e) {
      getLogger().error("Failed to subscribe to events store", e);
      throw KubeMQException.newBuilder()
          .code(ErrorCode.UNKNOWN_ERROR)
          .category(ErrorCategory.FATAL)
          .retryable(false)
          .message("subscribeToEventsStore failed: " + e.getMessage())
          .operation("subscribeToEventsStore")
          .channel(subscription.getChannel())
          .cause(e)
          .build();
    }
  }

  /**
   * Deletes the events channel with the specified name.
   *
   * @param channelName the name of the events channel to delete (must not be null or empty)
   * @return {@code true} if the channel was deleted successfully
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws ServerException if the server rejects the deletion (e.g. channel not found)
   * @throws KubeMQException if deleting the channel fails for any other reason
   * @see #createEventsChannel(String)
   */
  public boolean deleteEventsChannel(String channelName) {
    return sendChannelManagementRequest(
        "delete-channel", "events", channelName, "deleteEventsChannel");
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
    return sendChannelManagementRequest("create-channel", type, name, "createChannel");
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
    return sendChannelManagementRequest("delete-channel", type, name, "deleteChannel");
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
    switch (type) {
      case "events":
      case "events_store":
        return queryChannelList(search, type, "listChannels");
      case "commands":
      case "queries":
        return KubeMQUtils.listCQChannels(this, this.getClientId(), type, search);
      case "queues":
        return KubeMQUtils.listQueuesChannels(this, this.getClientId(), search);
      default:
        throw ValidationException.builder()
            .code(ErrorCode.INVALID_ARGUMENT)
            .message("Invalid channel type: " + type)
            .operation("listChannels")
            .build();
    }
  }

  // ---- Async API Methods (REQ-CONC-2 / REQ-CONC-4) ----

  /**
   * Sends an event message asynchronously.
   *
   * <p>The returned future completes after the message is written to the gRPC stream. Since events
   * are fire-and-forget (no server acknowledgment), the future completing does NOT indicate server
   * receipt.
   *
   * @param message the event message containing the target channel, body, and metadata to send
   * @return a {@link CompletableFuture} that completes when the stream write finishes; the future
   *     completes exceptionally with a {@link KubeMQException} subtype on failure
   * @throws ValidationException if the message is missing required fields (e.g. channel)
   * @throws ClientClosedException if this client has been closed
   * @see EventMessage
   * @see #publishEvent(EventMessage)
   */
  public CompletableFuture<Void> sendEventsMessageAsync(EventMessage message) {
    ensureNotClosed();
    // JV-1: Fast-fail during reconnection instead of waiting for server timeout
    if (getConnectionState() == io.kubemq.sdk.client.ConnectionState.RECONNECTING) {
      CompletableFuture<Void> rejected = new CompletableFuture<>();
      rejected.completeExceptionally(
          io.kubemq.sdk.exception.ConnectionNotReadyException.create("RECONNECTING"));
      return rejected;
    }
    message.validate();
    Kubemq.Event event = message.encode(this.getClientId());
    return CompletableFuture.runAsync(
        () -> {
          eventStreamHelper.sendEventMessage(this, event);
        },
        getAsyncOperationExecutor());
  }

  /**
   * Sends an event store message asynchronously.
   *
   * @param message the event store message containing the target channel, body, and metadata to
   *     send
   * @return a {@link CompletableFuture} that completes with an {@link EventSendResult} containing
   *     the send status and server-assigned message ID; completes exceptionally on failure
   * @throws ValidationException if the message is missing required fields (e.g. channel)
   * @throws ClientClosedException if this client has been closed
   * @see EventStoreMessage
   * @see EventSendResult
   * @see #publishEventStore(EventStoreMessage)
   */
  public CompletableFuture<EventSendResult> sendEventsStoreMessageAsync(EventStoreMessage message) {
    ensureNotClosed();
    // JV-1: Fast-fail during reconnection instead of waiting for server timeout
    if (getConnectionState() == io.kubemq.sdk.client.ConnectionState.RECONNECTING) {
      CompletableFuture<EventSendResult> rejected = new CompletableFuture<>();
      rejected.completeExceptionally(
          io.kubemq.sdk.exception.ConnectionNotReadyException.create("RECONNECTING"));
      return rejected;
    }
    message.validate();
    Kubemq.Event event = message.encode(this.getClientId());
    return eventStreamHelper.sendEventStoreMessageAsync(this, event);
  }

  /**
   * Sends an event store message with a custom timeout.
   *
   * @param message the event store message containing the target channel, body, and metadata to
   *     send
   * @param timeout the maximum time to wait for the server acknowledgment before throwing
   * @return an {@link EventSendResult} containing the send status and server-assigned message ID
   * @throws KubeMQTimeoutException if the operation exceeds the specified timeout
   * @throws ValidationException if the message is missing required fields (e.g. channel)
   * @throws ClientClosedException if this client has been closed
   * @throws KubeMQException if sending the message fails
   * @see #sendEventsStoreMessageAsync(EventStoreMessage)
   */
  public EventSendResult sendEventsStoreMessage(EventStoreMessage message, Duration timeout) {
    return unwrapFuture(sendEventsStoreMessageAsync(message), timeout);
  }

  /**
   * Creates an events channel asynchronously.
   *
   * @param channelName the name of the events channel to create (must not be null or empty)
   * @return a {@link CompletableFuture} that completes with {@code true} if the channel was
   *     created successfully; completes exceptionally with a {@link KubeMQException} on failure
   * @throws ClientClosedException if this client has been closed
   * @see #createEventsChannel(String)
   */
  public CompletableFuture<Boolean> createEventsChannelAsync(String channelName) {
    ensureNotClosed();
    return executeWithCancellation(() -> createEventsChannel(channelName));
  }

  /**
   * Creates an events store channel asynchronously.
   *
   * @param channelName the name of the events store channel to create (must not be null or empty)
   * @return a {@link CompletableFuture} that completes with {@code true} if the channel was
   *     created successfully; completes exceptionally with a {@link KubeMQException} on failure
   * @throws ClientClosedException if this client has been closed
   * @see #createEventsStoreChannel(String)
   */
  public CompletableFuture<Boolean> createEventsStoreChannelAsync(String channelName) {
    ensureNotClosed();
    return executeWithCancellation(() -> createEventsStoreChannel(channelName));
  }

  /**
   * Deletes an events channel asynchronously.
   *
   * @param channelName the name of the events channel to delete (must not be null or empty)
   * @return a {@link CompletableFuture} that completes with {@code true} if the channel was
   *     deleted successfully; completes exceptionally with a {@link KubeMQException} on failure
   * @throws ClientClosedException if this client has been closed
   * @see #deleteEventsChannel(String)
   */
  public CompletableFuture<Boolean> deleteEventsChannelAsync(String channelName) {
    ensureNotClosed();
    return executeWithCancellation(() -> deleteEventsChannel(channelName));
  }

  /**
   * Deletes an events store channel asynchronously.
   *
   * @param channelName the name of the events store channel to delete (must not be null or empty)
   * @return a {@link CompletableFuture} that completes with {@code true} if the channel was
   *     deleted successfully; completes exceptionally with a {@link KubeMQException} on failure
   * @throws ClientClosedException if this client has been closed
   * @see #deleteEventsStoreChannel(String)
   */
  public CompletableFuture<Boolean> deleteEventsStoreChannelAsync(String channelName) {
    ensureNotClosed();
    return executeWithCancellation(() -> deleteEventsStoreChannel(channelName));
  }

  /**
   * Lists events channels asynchronously.
   *
   * @param search a channel name filter; use an empty string or {@code null} to list all channels
   * @return a {@link CompletableFuture} that completes with a list of {@link PubSubChannel}
   *     matching the filter; completes exceptionally with a {@link KubeMQException} on failure
   * @throws ClientClosedException if this client has been closed
   * @see #listEventsChannels(String)
   */
  public CompletableFuture<List<PubSubChannel>> listEventsChannelsAsync(String search) {
    ensureNotClosed();
    return executeWithCancellation(() -> listEventsChannels(search));
  }

  /**
   * Lists events store channels asynchronously.
   *
   * @param search a channel name filter; use an empty string or {@code null} to list all channels
   * @return a {@link CompletableFuture} that completes with a list of {@link PubSubChannel}
   *     matching the filter; completes exceptionally with a {@link KubeMQException} on failure
   * @throws ClientClosedException if this client has been closed
   * @see #listEventsStoreChannels(String)
   */
  public CompletableFuture<List<PubSubChannel>> listEventsStoreChannelsAsync(String search) {
    ensureNotClosed();
    return executeWithCancellation(() -> listEventsStoreChannels(search));
  }

  /**
   * Subscribes to events and returns a cancellable Subscription handle.
   *
   * @param subscription the subscription configuration specifying the channel, optional consumer
   *     group, {@code onReceiveEvent} callback, and {@code onError} callback
   * @return a {@link Subscription} handle whose {@link Subscription#cancel()} method stops the
   *     subscription
   * @throws ValidationException if the subscription is missing required fields
   * @throws ClientClosedException if this client has been closed
   * @see EventsSubscription
   * @see #subscribeToEvents(EventsSubscription)
   */
  public Subscription subscribeToEventsWithHandle(EventsSubscription subscription) {
    ensureNotClosed();
    try {
      subscription.validate();
      Kubemq.Subscribe subscribe = subscription.encode(this.getClientId(), this);
      this.getAsyncClient().subscribeToEvents(subscribe, subscription.getObserver());
      return new Subscription(subscription::cancel, getAsyncOperationExecutor());
    } catch (KubeMQException e) {
      throw e;
    } catch (StatusRuntimeException e) {
      throw GrpcErrorMapper.map(e, "subscribeToEventsWithHandle", subscription.getChannel(), null, false);
    } catch (Exception e) {
      throw KubeMQException.newBuilder()
          .code(ErrorCode.UNKNOWN_ERROR)
          .category(ErrorCategory.FATAL)
          .retryable(false)
          .message("subscribeToEventsWithHandle failed: " + e.getMessage())
          .operation("subscribeToEventsWithHandle")
          .channel(subscription.getChannel())
          .cause(e)
          .build();
    }
  }

  /**
   * Subscribes to events store and returns a cancellable Subscription handle.
   *
   * @param subscription the subscription configuration specifying the channel, optional consumer
   *     group, start position, {@code onReceiveEvent} callback, and {@code onError} callback
   * @return a {@link Subscription} handle whose {@link Subscription#cancel()} method stops the
   *     subscription
   * @throws ValidationException if the subscription is missing required fields
   * @throws ClientClosedException if this client has been closed
   * @see EventsStoreSubscription
   * @see #subscribeToEventsStore(EventsStoreSubscription)
   */
  public Subscription subscribeToEventsStoreWithHandle(EventsStoreSubscription subscription) {
    ensureNotClosed();
    try {
      subscription.validate();
      Kubemq.Subscribe subscribe = subscription.encode(this.getClientId(), this);
      this.getAsyncClient().subscribeToEvents(subscribe, subscription.getObserver());
      return new Subscription(subscription::cancel, getAsyncOperationExecutor());
    } catch (KubeMQException e) {
      throw e;
    } catch (StatusRuntimeException e) {
      throw GrpcErrorMapper.map(e, "subscribeToEventsStoreWithHandle", subscription.getChannel(), null, false);
    } catch (Exception e) {
      throw KubeMQException.newBuilder()
          .code(ErrorCode.UNKNOWN_ERROR)
          .category(ErrorCategory.FATAL)
          .retryable(false)
          .message("subscribeToEventsStoreWithHandle failed: " + e.getMessage())
          .operation("subscribeToEventsStoreWithHandle")
          .channel(subscription.getChannel())
          .cause(e)
          .build();
    }
  }

  /**
   * Deletes the events store channel with the specified name.
   *
   * @param channelName the name of the events store channel to delete (must not be null or empty)
   * @return {@code true} if the channel was deleted successfully
   * @throws ClientClosedException if this client has been closed
   * @throws ConnectionException if the connection to the KubeMQ server is unavailable
   * @throws AuthenticationException if the authentication token is invalid or expired
   * @throws ServerException if the server rejects the deletion (e.g. channel not found)
   * @throws KubeMQException if deleting the channel fails for any other reason
   * @see #createEventsStoreChannel(String)
   */
  public boolean deleteEventsStoreChannel(String channelName) {
    return sendChannelManagementRequest(
        "delete-channel", "events_store", channelName, "deleteEventsStoreChannel");
  }

  private static final int CHANNEL_MGMT_DEADLINE_SECONDS = 10;

  private boolean sendChannelManagementRequest(
      String metadata, String channelType, String channelName, String operationName) {
    ensureNotClosed();
    KubeMQUtils.validateChannelName(channelName, operationName);
    try {
      getLogger().debug(operationName, "channel", channelName);
      Kubemq.Request request =
          Kubemq.Request.newBuilder()
              .setRequestID(UUID.randomUUID().toString())
              .setRequestTypeData(Kubemq.Request.RequestType.Query)
              .setRequestTypeDataValue(2)
              .setMetadata(metadata)
              .setChannel("kubemq.cluster.internal.requests")
              .setClientID(this.getClientId())
              .putTags("channel_type", channelType)
              .putTags("channel", channelName)
              .putTags("client_id", this.getClientId())
              .setTimeout(10 * 1000)
              .build();
      kubemq.Kubemq.Response response = this.getClient()
          .withDeadlineAfter(CHANNEL_MGMT_DEADLINE_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
          .sendRequest(request);
      return response.getExecuted();
    } catch (StatusRuntimeException e) {
      throw GrpcErrorMapper.map(e, operationName, channelName, null, false);
    } catch (Exception e) {
      getLogger().error("Failed: " + operationName, e);
      throw KubeMQException.newBuilder()
          .code(ErrorCode.UNKNOWN_ERROR)
          .category(ErrorCategory.FATAL)
          .retryable(false)
          .message(operationName + " failed: " + e.getMessage())
          .operation(operationName)
          .channel(channelName)
          .cause(e)
          .build();
    }
  }

  private List<PubSubChannel> queryChannelList(
      String search, String channelType, String operationName) {
    ensureNotClosed();
    try {
      getLogger().debug(operationName, "search", search);
      Kubemq.Request request =
          Kubemq.Request.newBuilder()
              .setRequestID(UUID.randomUUID().toString())
              .setRequestTypeData(Kubemq.Request.RequestType.Query)
              .setRequestTypeDataValue(2)
              .setMetadata("list-channels")
              .setChannel("kubemq.cluster.internal.requests")
              .setClientID(this.getClientId())
              .putTags("channel_type", channelType)
              .putTags("channel_search", search != null ? search : "")
              .setTimeout(10 * 1000)
              .build();
      kubemq.Kubemq.Response response = this.getClient()
          .withDeadlineAfter(CHANNEL_MGMT_DEADLINE_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
          .sendRequest(request);
      getLogger().debug(operationName + " response received");
      if (response.getExecuted()) {
        return ChannelDecoder.decodePubSubChannelList(response.getBody().toByteArray());
      } else {
        throw ServerException.builder()
            .message(operationName + " failed: " + response.getError())
            .operation(operationName)
            .build();
      }
    } catch (KubeMQException e) {
      throw e;
    } catch (StatusRuntimeException e) {
      throw GrpcErrorMapper.map(e, operationName, null, null, false);
    } catch (Exception e) {
      getLogger().error("Failed: " + operationName, e);
      throw KubeMQException.newBuilder()
          .code(ErrorCode.UNKNOWN_ERROR)
          .category(ErrorCategory.FATAL)
          .retryable(false)
          .message(operationName + " failed: " + e.getMessage())
          .operation(operationName)
          .cause(e)
          .build();
    }
  }
}
