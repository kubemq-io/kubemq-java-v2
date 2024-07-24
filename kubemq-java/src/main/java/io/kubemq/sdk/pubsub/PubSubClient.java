package io.kubemq.sdk.pubsub;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ChannelUtility;
import kubemq.Kubemq;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

/**
 * PubSubClient is a specialized client for publishing and subscribing to messages using KubeMQ.
 * It provides methods to send messages, create and delete channels, list channels, and subscribe to events.
 */
@Slf4j
@Builder
public class PubSubClient {

    /**
     * The KubeMQ client used for communication.
     */
    private final KubeMQClient kubeMQClient;

    /**
     * Sends an event message.
     *
     * @param message the event message to be sent
     * @return the result of sending the event message
     * @throws RuntimeException if sending the message fails
     */
    public EventSendResult sendEventsMessage(EventMessage message) {
        try {
            log.debug("Sending event message");
            message.validate();
            Kubemq.Event event = message.encode(kubeMQClient.getClientId());
            kubemq.Kubemq.Result result = kubeMQClient.getClient().sendEvent(event);
            return EventSendResult.decode(result);
        } catch (Exception e) {
            log.error("Failed to send event message", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Sends an event store message.
     *
     * @param message the event store message to be sent
     * @return the result of sending the event store message
     * @throws RuntimeException if sending the message fails
     */
    public EventSendResult sendEventsStoreMessage(EventStoreMessage message) {
        try {
            log.debug("Sending event store message");
            message.validate();
            Kubemq.Event event = message.encode(kubeMQClient.getClientId());
            kubemq.Kubemq.Result result = kubeMQClient.getClient().sendEvent(event);
            return EventSendResult.decode(result);
        } catch (Exception e) {
            log.error("Failed to send event store message", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates an events channel with the specified name.
     *
     * @param channelName the name of the channel to be created
     * @return true if the channel was created successfully, false otherwise
     * @throws RuntimeException if creating the channel fails
     */
    public boolean createEventsChannel(String channelName) {
        try {
            log.debug("Creating events channel: {}", channelName);
            Kubemq.Request request = Kubemq.Request.newBuilder()
                    .setRequestID(UUID.randomUUID().toString())
                    .setRequestTypeData(Kubemq.Request.RequestType.Query)
                    .setMetadata("create-channel")
                    .setChannel("kubemq.cluster.internal.requests")  // Hardcoded value
                    .setClientID(kubeMQClient.getClientId())
                    .putTags("channel_type", "events")
                    .putTags("channel", channelName)
                    .putTags("client_id", kubeMQClient.getClientId())
                    .setTimeout(10 * 1000)
                    .build();
            kubemq.Kubemq.Response response = kubeMQClient.getClient().sendRequest(request);
            return response.getExecuted();
        } catch (Exception e) {
            log.error("Failed to create events channel", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates an events store channel with the specified name.
     *
     * @param channelName the name of the channel to be created
     * @return true if the channel was created successfully, false otherwise
     * @throws RuntimeException if creating the channel fails
     */
    public boolean createEventsStoreChannel(String channelName) {
        try {
            log.debug("Creating events store channel: {}", channelName);
            Kubemq.Request request = Kubemq.Request.newBuilder()
                    .setRequestID(UUID.randomUUID().toString())
                    .setRequestTypeData(Kubemq.Request.RequestType.Query)
                    .setMetadata("create-channel")
                    .setChannel("kubemq.cluster.internal.requests")  // Hardcoded value
                    .setClientID(kubeMQClient.getClientId())
                    .putTags("channel_type", "events_store")
                    .putTags("channel", channelName)
                    .putTags("client_id", kubeMQClient.getClientId())
                    .setTimeout(10 * 1000)
                    .build();
            kubemq.Kubemq.Response response = kubeMQClient.getClient().sendRequest(request);
            return response.getExecuted();
        } catch (Exception e) {
            log.error("Failed to create events store channel", e);
            throw new RuntimeException(e);
        }
    }


    /**
     * Lists events channels that match the search criteria.
     *
     * @param search the search criteria for listing channels
     * @return a list of matching events channels
     * @throws RuntimeException if listing channels fails
     */
    public List<PubSubChannel> listEventsChannels(String search) {
        try {
            log.debug("Listing events channels with search: {}", search);
            Kubemq.Request request = Kubemq.Request.newBuilder()
                    .setRequestID(UUID.randomUUID().toString())
                    .setRequestTypeData(Kubemq.Request.RequestType.Query)
                    .setRequestTypeDataValue(2)
                    .setMetadata("list-channels")
                    .setChannel("kubemq.cluster.internal.requests")  // Hardcoded value
                    .setClientID(kubeMQClient.getClientId())
                    .putTags("channel_type", "events")
                    .putTags("channel_search", search != null?search:"")
                    .setTimeout(10 * 1000)
                    .build();
            kubemq.Kubemq.Response response = kubeMQClient.getClient().sendRequest(request);
            if (response.getExecuted()) {
                return ChannelUtility.decodePubSubChannelList(response.toByteArray());
            } else {
                throw new RuntimeException(response.getError());
            }
        } catch (Exception e) {
            log.error("Failed to list events channels", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Lists events store channels that match the search criteria.
     *
     * @param search the search criteria for listing channels
     * @return a list of matching events store channels
     * @throws RuntimeException if listing channels fails
     */
    public List<PubSubChannel> listEventsStoreChannels(String search) {
        try {
            log.debug("Listing events store channels with search: {}", search);
            Kubemq.Request request = Kubemq.Request.newBuilder()
                    .setRequestID(UUID.randomUUID().toString())
                    .setRequestTypeData(Kubemq.Request.RequestType.Query)
                    .setRequestTypeDataValue(2)
                    .setMetadata("list-channels")
                    .setChannel("kubemq.cluster.internal.requests")  // Hardcoded value
                    .setClientID(kubeMQClient.getClientId())
                    .putTags("channel_type", "events_store")
                    .putTags("channel_search", search != null?search:"")
                    .setTimeout(10 * 1000)
                    .build();
            kubemq.Kubemq.Response response = kubeMQClient.getClient().sendRequest(request);
            if (response.getExecuted()) {
                return ChannelUtility.decodePubSubChannelList(response.toByteArray());
            } else {
                throw new RuntimeException(response.getError());
            }
        } catch (Exception e) {
            log.error("Failed to list events store channels", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Subscribes to events and processes them using the provided subscription.
     *
     * @param subscription the subscription to handle received events
     * @throws RuntimeException if subscribing to events fails
     */
    public void subscribeToEvents(EventsSubscription subscription) {
        try {
            log.debug("Subscribing to events");
            subscription.validate();
            kubemq.Kubemq.Subscribe subscribe = subscription.encode(kubeMQClient.getClientId());
            kubeMQClient.getClient().subscribeToEvents(subscribe);
        } catch (Exception e) {
            log.error("Failed to subscribe to events", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Subscribes to events store and processes them using the provided subscription.
     *
     * @param subscription the subscription to handle received events store messages
     * @throws RuntimeException if subscribing to events store fails
     */
    public void subscribeToEventsStore(EventsStoreSubscription subscription) {
        try {
            log.debug("Subscribing to events store");
            subscription.validate();
            kubemq.Kubemq.Subscribe subscribe = subscription.encode(kubeMQClient.getClientId());

            kubeMQClient.getClient().subscribeToEvents(subscribe);
        } catch (Exception e) {
            log.error("Failed to subscribe to events store", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Deletes the events channel with the specified name.
     *
     * @param channelName the name of the channel to be deleted
     * @return true if the channel was deleted successfully, false otherwise
     * @throws RuntimeException if deleting the channel fails
     */
    public boolean deleteEventsChannel(String channelName) {
        try {
            log.debug("Deleting events channel: {}", channelName);
            Kubemq.Request request = Kubemq.Request.newBuilder()
                    .setRequestID(UUID.randomUUID().toString())
                    .setRequestTypeData(Kubemq.Request.RequestType.Query)
                    .setMetadata("delete-channel")
                    .setChannel("kubemq.cluster.internal.requests")  // Hardcoded value
                    .setClientID(kubeMQClient.getClientId())
                    .putTags("channel_type", "events")
                    .putTags("channel", channelName)
                    .putTags("client_id", kubeMQClient.getClientId())
                    .setTimeout(10 * 1000)
                    .build();
            kubemq.Kubemq.Response response = kubeMQClient.getClient().sendRequest(request);
            return response.getExecuted();
        } catch (Exception e) {
            log.error("Failed to delete events channel", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Deletes the events store channel with the specified name.
     *
     * @param channelName the name of the channel to be deleted
     * @return true if the channel was deleted successfully, false otherwise
     * @throws RuntimeException if deleting the channel fails
     */
    public boolean deleteEventsStoreChannel(String channelName) {
        try {
            log.debug("Deleting events store channel: {}", channelName);
            Kubemq.Request request = Kubemq.Request.newBuilder()
                    .setRequestID(UUID.randomUUID().toString())
                    .setRequestTypeData(Kubemq.Request.RequestType.Query)
                    .setMetadata("delete-channel")
                    .setChannel("kubemq.cluster.internal.requests")
                    .setClientID(kubeMQClient.getClientId())
                    .putTags("channel_type", "events_store")
                    .putTags("channel", channelName)
                    .putTags("client_id", kubeMQClient.getClientId())
                    .setTimeout(10 * 1000)
                    .build();
            kubemq.Kubemq.Response response = kubeMQClient.getClient().sendRequest(request);
            return response.getExecuted();
        } catch (Exception e) {
            log.error("Failed to delete events store channel", e);
            throw new RuntimeException(e);
        }
    }

}
