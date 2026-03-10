package io.kubemq.sdk.pubsub;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.auth.CredentialProvider;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.client.Subscription;
import io.kubemq.sdk.common.ChannelDecoder;
import io.kubemq.sdk.exception.GrpcErrorMapper;
import io.kubemq.sdk.exception.KubeMQException;
import io.kubemq.sdk.exception.ServerException;
import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.ErrorCategory;
import io.kubemq.sdk.observability.KubeMQLogger;
import kubemq.Kubemq;
import lombok.Builder;

import javax.annotation.concurrent.ThreadSafe;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * PubSubClient is a specialized client for publishing and subscribing to messages using KubeMQ.
 * It provides methods to send messages, create and delete channels, list channels, and subscribe to events.
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe. A single instance should be
 * shared across all threads. See {@link KubeMQClient} for usage patterns.</p>
 */
@ThreadSafe
public class PubSubClient extends KubeMQClient {

    private EventStreamHelper eventStreamHelper;

    @Builder
    public PubSubClient(String address, String clientId, String authToken, Boolean tls,
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
        eventStreamHelper = new EventStreamHelper();
    }

    /**
     * Publishes an event message.
     * This is the preferred method name per cross-SDK verb alignment.
     *
     * @param message the event message to publish
     * @throws KubeMQException if publishing the message fails
     * @see #sendEventsMessage(EventMessage)
     */
    @SuppressWarnings("deprecation")
    public void publishEvent(EventMessage message) {
        sendEventsMessage(message);
    }

    /**
     * Sends an event message.
     *
     * @param message the event message to be sent
     * @throws KubeMQException if sending the message fails
     * @deprecated Use {@link #publishEvent(EventMessage)} instead. This method
     *             will be removed in v3.0.
     */
    @Deprecated(since = "2.2.0", forRemoval = true)
    public void sendEventsMessage(EventMessage message) {
        ensureNotClosed();
        try {
            getLogger().debug("Sending event message");
            message.validate();
            Kubemq.Event event = message.encode(this.getClientId());
            eventStreamHelper.sendEventMessage(this,event);
        } catch (KubeMQException e) {
            throw e;
        } catch (StatusRuntimeException e) {
            throw GrpcErrorMapper.map(e, "sendEventsMessage", message.getChannel(), null, false);
        } catch (Exception e) {
            getLogger().error("Failed to send event message", e);
            throw KubeMQException.newBuilder()
                .code(ErrorCode.UNKNOWN_ERROR).category(ErrorCategory.FATAL).retryable(false)
                .message("sendEventsMessage failed: " + e.getMessage())
                .operation("sendEventsMessage").channel(message.getChannel())
                .cause(e).build();
        }
    }

    /**
     * Publishes an event store message.
     * This is the preferred method name per cross-SDK verb alignment.
     *
     * @param message the event store message to publish
     * @return the result of publishing the message
     * @throws KubeMQException if publishing the message fails
     * @see #sendEventsStoreMessage(EventStoreMessage)
     */
    @SuppressWarnings("deprecation")
    public EventSendResult publishEventStore(EventStoreMessage message) {
        return sendEventsStoreMessage(message);
    }

    /**
     * Sends an event store message.
     *
     * @param message the event store message to be sent
     * @return the result of sending the event store message
     * @throws KubeMQException if sending the message fails
     * @deprecated Use {@link #publishEventStore(EventStoreMessage)} instead.
     *             This method will be removed in v3.0.
     */
    @Deprecated(since = "2.2.0", forRemoval = true)
    public EventSendResult sendEventsStoreMessage(EventStoreMessage message) {
        ensureNotClosed();
        try {
            getLogger().debug("Sending event store message");
            message.validate();
            Kubemq.Event event = message.encode(this.getClientId());
            return eventStreamHelper.sendEventStoreMessage(this,event);
        } catch (KubeMQException e) {
            throw e;
        } catch (StatusRuntimeException e) {
            throw GrpcErrorMapper.map(e, "sendEventsStoreMessage", message.getChannel(), null, false);
        } catch (Exception e) {
            getLogger().error("Failed to send event store message", e);
            throw KubeMQException.newBuilder()
                .code(ErrorCode.UNKNOWN_ERROR).category(ErrorCategory.FATAL).retryable(false)
                .message("sendEventsStoreMessage failed: " + e.getMessage())
                .operation("sendEventsStoreMessage").channel(message.getChannel())
                .cause(e).build();
        }
    }

    /**
     * Publishes an event message to the specified channel.
     * Convenience method equivalent to building an {@link EventMessage} and calling
     * {@link #publishEvent(EventMessage)}.
     *
     * @param channel the target channel name (must not be null or empty)
     * @param body    the message body
     */
    @SuppressWarnings("deprecation")
    public void publishEvent(String channel, byte[] body) {
        sendEventsMessage(EventMessage.builder()
            .channel(channel)
            .body(body != null ? body : new byte[0])
            .build());
    }

    /**
     * Publishes an event message with a string body to the specified channel.
     *
     * @param channel the target channel name
     * @param body    the message body as a string (encoded as UTF-8)
     */
    public void publishEvent(String channel, String body) {
        publishEvent(channel, body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0]);
    }

    /**
     * Publishes an event store message to the specified channel.
     *
     * @param channel the target channel name
     * @param body    the message body
     * @return the result of sending the event store message
     */
    @SuppressWarnings("deprecation")
    public EventSendResult publishEventStore(String channel, byte[] body) {
        return sendEventsStoreMessage(EventStoreMessage.builder()
            .channel(channel)
            .body(body != null ? body : new byte[0])
            .build());
    }

    /**
     * Creates an events channel with the specified name.
     *
     * @param channelName the name of the channel to be created
     * @return true if the channel was created successfully, false otherwise
     * @throws KubeMQException if creating the channel fails
     */
    public boolean createEventsChannel(String channelName) {
        return sendChannelManagementRequest("create-channel", "events",
                channelName, "createEventsChannel");
    }

    /**
     * Creates an events store channel with the specified name.
     *
     * @param channelName the name of the channel to be created
     * @return true if the channel was created successfully, false otherwise
     * @throws KubeMQException if creating the channel fails
     */
    public boolean createEventsStoreChannel(String channelName) {
        return sendChannelManagementRequest("create-channel", "events_store",
                channelName, "createEventsStoreChannel");
    }


    /**
     * Lists events channels that match the search criteria.
     *
     * @param search the search criteria for listing channels
     * @return a list of matching events channels
     * @throws KubeMQException if listing channels fails
     */
    public List<PubSubChannel> listEventsChannels(String search) {
        return queryChannelList(search, "events", "listEventsChannels");
    }

    /**
     * Lists events store channels that match the search criteria.
     *
     * @param search the search criteria for listing channels
     * @return a list of matching events store channels
     * @throws KubeMQException if listing channels fails
     */
    public List<PubSubChannel> listEventsStoreChannels(String search) {
        return queryChannelList(search, "events_store", "listEventsStoreChannels");
    }

    /**
     * Subscribes to events and returns the subscription handle for lifecycle control.
     *
     * @param subscription the subscription configuration with callbacks
     * @return the same subscription object, now active, with {@link EventsSubscription#cancel()} available
     * @throws KubeMQException if subscribing to events fails
     */
    public EventsSubscription subscribeToEvents(EventsSubscription subscription) {
        ensureNotClosed();
        try {
            getLogger().debug("Subscribing to events");
            subscription.validate();
            kubemq.Kubemq.Subscribe subscribe = subscription.encode(this.getClientId(),this);
            this.getAsyncClient().subscribeToEvents(subscribe, subscription.getObserver());
            return subscription;
        } catch (KubeMQException e) {
            throw e;
        } catch (StatusRuntimeException e) {
            throw GrpcErrorMapper.map(e, "subscribeToEvents", subscription.getChannel(), null, false);
        } catch (Exception e) {
            getLogger().error("Failed to subscribe to events", e);
            throw KubeMQException.newBuilder()
                .code(ErrorCode.UNKNOWN_ERROR).category(ErrorCategory.FATAL).retryable(false)
                .message("subscribeToEvents failed: " + e.getMessage())
                .operation("subscribeToEvents").channel(subscription.getChannel())
                .cause(e).build();
        }
    }

    /**
     * Subscribes to events store and returns the subscription handle for lifecycle control.
     *
     * @param subscription the subscription configuration with callbacks and start position
     * @return the same subscription object, now active, with {@link EventsStoreSubscription#cancel()} available
     * @throws KubeMQException if subscribing to events store fails
     */
    public EventsStoreSubscription subscribeToEventsStore(EventsStoreSubscription subscription) {
        ensureNotClosed();
        try {
            getLogger().debug("Subscribing to events store");
            subscription.validate();
            kubemq.Kubemq.Subscribe subscribe = subscription.encode(this.getClientId(),this);
            this.getAsyncClient().subscribeToEvents(subscribe, subscription.getObserver());
            return subscription;
        } catch (KubeMQException e) {
            throw e;
        } catch (StatusRuntimeException e) {
            throw GrpcErrorMapper.map(e, "subscribeToEventsStore", subscription.getChannel(), null, false);
        } catch (Exception e) {
            getLogger().error("Failed to subscribe to events store", e);
            throw KubeMQException.newBuilder()
                .code(ErrorCode.UNKNOWN_ERROR).category(ErrorCategory.FATAL).retryable(false)
                .message("subscribeToEventsStore failed: " + e.getMessage())
                .operation("subscribeToEventsStore").channel(subscription.getChannel())
                .cause(e).build();
        }
    }

    /**
     * Deletes the events channel with the specified name.
     *
     * @param channelName the name of the channel to be deleted
     * @return true if the channel was deleted successfully, false otherwise
     * @throws KubeMQException if deleting the channel fails
     */
    public boolean deleteEventsChannel(String channelName) {
        return sendChannelManagementRequest("delete-channel", "events",
                channelName, "deleteEventsChannel");
    }

    // ---- Async API Methods (REQ-CONC-2 / REQ-CONC-4) ----

    /**
     * Sends an event message asynchronously.
     *
     * <p>The returned future completes after the message is written to the gRPC stream.
     * Since events are fire-and-forget (no server acknowledgment), the future completing
     * does NOT indicate server receipt.</p>
     *
     * @param message the event message to send
     * @return a CompletableFuture that completes when the stream write is done
     */
    public CompletableFuture<Void> sendEventsMessageAsync(EventMessage message) {
        ensureNotClosed();
        message.validate();
        Kubemq.Event event = message.encode(this.getClientId());
        return CompletableFuture.runAsync(() -> {
            eventStreamHelper.sendEventMessage(this, event);
        }, getAsyncOperationExecutor());
    }

    /**
     * Sends an event store message asynchronously.
     *
     * @param message the event store message to send
     * @return a CompletableFuture that completes with the send result
     */
    public CompletableFuture<EventSendResult> sendEventsStoreMessageAsync(EventStoreMessage message) {
        ensureNotClosed();
        message.validate();
        Kubemq.Event event = message.encode(this.getClientId());
        return eventStreamHelper.sendEventStoreMessageAsync(this, event);
    }

    /**
     * Sends an event store message with a custom timeout.
     *
     * @param message the event store message to send
     * @param timeout the maximum time to wait for a response
     * @return the send result
     */
    public EventSendResult sendEventsStoreMessage(EventStoreMessage message, Duration timeout) {
        return unwrapFuture(sendEventsStoreMessageAsync(message), timeout);
    }

    /**
     * Creates an events channel asynchronously.
     *
     * @param channelName the channel name
     * @return a CompletableFuture that completes with true if created successfully
     */
    public CompletableFuture<Boolean> createEventsChannelAsync(String channelName) {
        ensureNotClosed();
        return executeWithCancellation(() -> createEventsChannel(channelName));
    }

    /**
     * Creates an events store channel asynchronously.
     *
     * @param channelName the channel name
     * @return a CompletableFuture that completes with true if created successfully
     */
    public CompletableFuture<Boolean> createEventsStoreChannelAsync(String channelName) {
        ensureNotClosed();
        return executeWithCancellation(() -> createEventsStoreChannel(channelName));
    }

    /**
     * Deletes an events channel asynchronously.
     *
     * @param channelName the channel name
     * @return a CompletableFuture that completes with true if deleted successfully
     */
    public CompletableFuture<Boolean> deleteEventsChannelAsync(String channelName) {
        ensureNotClosed();
        return executeWithCancellation(() -> deleteEventsChannel(channelName));
    }

    /**
     * Deletes an events store channel asynchronously.
     *
     * @param channelName the channel name
     * @return a CompletableFuture that completes with true if deleted successfully
     */
    public CompletableFuture<Boolean> deleteEventsStoreChannelAsync(String channelName) {
        ensureNotClosed();
        return executeWithCancellation(() -> deleteEventsStoreChannel(channelName));
    }

    /**
     * Lists events channels asynchronously.
     *
     * @param search the search criteria
     * @return a CompletableFuture that completes with the list of channels
     */
    public CompletableFuture<List<PubSubChannel>> listEventsChannelsAsync(String search) {
        ensureNotClosed();
        return executeWithCancellation(() -> listEventsChannels(search));
    }

    /**
     * Lists events store channels asynchronously.
     *
     * @param search the search criteria
     * @return a CompletableFuture that completes with the list of channels
     */
    public CompletableFuture<List<PubSubChannel>> listEventsStoreChannelsAsync(String search) {
        ensureNotClosed();
        return executeWithCancellation(() -> listEventsStoreChannels(search));
    }

    /**
     * Subscribes to events and returns a cancellable Subscription handle.
     *
     * @param subscription the subscription configuration with callbacks
     * @return a Subscription handle for cancellation
     */
    public Subscription subscribeToEventsWithHandle(EventsSubscription subscription) {
        ensureNotClosed();
        subscription.validate();
        Kubemq.Subscribe subscribe = subscription.encode(this.getClientId(), this);
        this.getAsyncClient().subscribeToEvents(subscribe, subscription.getObserver());
        return new Subscription(subscription::cancel, getAsyncOperationExecutor());
    }

    /**
     * Subscribes to events store and returns a cancellable Subscription handle.
     *
     * @param subscription the subscription configuration with callbacks
     * @return a Subscription handle for cancellation
     */
    public Subscription subscribeToEventsStoreWithHandle(EventsStoreSubscription subscription) {
        ensureNotClosed();
        subscription.validate();
        Kubemq.Subscribe subscribe = subscription.encode(this.getClientId(), this);
        this.getAsyncClient().subscribeToEvents(subscribe, subscription.getObserver());
        return new Subscription(subscription::cancel, getAsyncOperationExecutor());
    }

    /**
     * Deletes the events store channel with the specified name.
     *
     * @param channelName the name of the channel to be deleted
     * @return true if the channel was deleted successfully, false otherwise
     * @throws KubeMQException if deleting the channel fails
     */
    public boolean deleteEventsStoreChannel(String channelName) {
        return sendChannelManagementRequest("delete-channel", "events_store",
                channelName, "deleteEventsStoreChannel");
    }

    private boolean sendChannelManagementRequest(String metadata, String channelType,
                                                  String channelName, String operationName) {
        ensureNotClosed();
        try {
            getLogger().debug(operationName, "channel", channelName);
            Kubemq.Request request = Kubemq.Request.newBuilder()
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
            kubemq.Kubemq.Response response = this.getClient().sendRequest(request);
            return response.getExecuted();
        } catch (StatusRuntimeException e) {
            throw GrpcErrorMapper.map(e, operationName, channelName, null, false);
        } catch (Exception e) {
            getLogger().error("Failed: " + operationName, e);
            throw KubeMQException.newBuilder()
                .code(ErrorCode.UNKNOWN_ERROR).category(ErrorCategory.FATAL).retryable(false)
                .message(operationName + " failed: " + e.getMessage())
                .operation(operationName).channel(channelName)
                .cause(e).build();
        }
    }

    private List<PubSubChannel> queryChannelList(String search, String channelType,
                                                  String operationName) {
        ensureNotClosed();
        try {
            getLogger().debug(operationName, "search", search);
            Kubemq.Request request = Kubemq.Request.newBuilder()
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
            kubemq.Kubemq.Response response = this.getClient().sendRequest(request);
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
                .code(ErrorCode.UNKNOWN_ERROR).category(ErrorCategory.FATAL).retryable(false)
                .message(operationName + " failed: " + e.getMessage())
                .operation(operationName)
                .cause(e).build();
        }
    }

}
