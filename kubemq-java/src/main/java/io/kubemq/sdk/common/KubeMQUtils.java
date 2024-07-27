package io.kubemq.sdk.common;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.cq.CQChannel;
import io.kubemq.sdk.exception.CreateChannelException;
import io.kubemq.sdk.exception.DeleteChannelException;
import io.kubemq.sdk.exception.GRPCException;
import io.kubemq.sdk.exception.ListChannelsException;
import io.kubemq.sdk.pubsub.PubSubChannel;
import io.kubemq.sdk.queues.QueuesChannel;
import kubemq.Kubemq;
import kubemq.Kubemq.Request;
import kubemq.Kubemq.Response;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Utility class for managing KubeMQ channels.
 */
@Slf4j
public class KubeMQUtils {

    private static final String REQUESTS_CHANNEL = "kubemq.cluster.internal.requests";

    /**
     * Creates a channel on the KubeMQ server.
     *
     * @param kubeMQClient   The kubeMQClient object for communication with KubeMQ.
     * @param clientId    The ID of the client.
     * @param channelName The name of the channel to create.
     * @param channelType The type of the channel.
     * @return True if the channel creation was successful, otherwise null.
     * @throws CreateChannelException If there was an error during the channel creation.
     * @throws GRPCException          If there was an error with gRPC communication.
     */
    public static Boolean createChannelRequest(KubeMQClient kubeMQClient, String clientId, String channelName, String channelType) throws CreateChannelException, GRPCException{
        try {
            Kubemq.Request request = Kubemq.Request.newBuilder()
                    .setRequestID(UUID.randomUUID().toString())
                    .setRequestTypeData(Kubemq.Request.RequestType.Query)
                    .setRequestTypeDataValue(2)
                    .setMetadata("create-channel")
                    .setChannel(REQUESTS_CHANNEL)
                    .setClientID(clientId)
                    .putTags("channel_type", channelType)
                    .putTags("channel", channelName)
                    .putTags("client_id", clientId)
                    .setTimeout(10 * 1000)
                    .build();

            kubemq.Kubemq.Response response = kubeMQClient.getClient().sendRequest(request);
            if (response != null && response.getExecuted()) {
                return true;
            } else if (response != null) {
                throw new CreateChannelException(response.getError());
            }
        } catch (io.grpc.StatusRuntimeException e) {
            throw new GRPCException(e);
        }
        return null;
    }

    /**
     * Deletes a channel on the KubeMQ server.
     *
     * @param kubeMQClient   The kubeMQClient object for communication with KubeMQ.
     * @param clientId    The ID of the client.
     * @param channelName The name of the channel to delete.
     * @param channelType The type of the channel.
     * @return True if the channel deletion was successful, otherwise null.
     * @throws DeleteChannelException If there was an error during the channel deletion.
     * @throws GRPCException          If there was an error with gRPC communication.
     */
    public static Boolean deleteChannelRequest(KubeMQClient kubeMQClient, String clientId, String channelName, String channelType) throws DeleteChannelException, GRPCException{
        try {
            Kubemq.Request request = Kubemq.Request.newBuilder()
                    .setRequestID(UUID.randomUUID().toString())
                    .setRequestTypeData(Kubemq.Request.RequestType.Query)
                    .setRequestTypeDataValue(2)
                    .setMetadata("delete-channel")
                    .setChannel(REQUESTS_CHANNEL)
                    .setClientID(clientId)
                    .putTags("channel_type", channelType)
                    .putTags("channel", channelName)
                    .putTags("client_id", clientId)
                    .setTimeout(10 * 1000)
                    .build();
            kubemq.Kubemq.Response response = kubeMQClient.getClient().sendRequest(request);
            if (response != null && response.getExecuted()) {
                return true;
            } else if (response != null) {
                throw new DeleteChannelException(response.getError());
            }
        } catch (io.grpc.StatusRuntimeException e) {
            throw new GRPCException(e);
        }
        return null;
    }

    /**
     * Lists queue channels on the KubeMQ server.
     *
     * @param kubeMQClient    The kubeMQClient object for communication with KubeMQ.
     * @param clientId     The ID of the client.
     * @param channelSearch The search term to filter the channels.
     * @return A list of QueuesChannel objects.
     * @throws ListChannelsException If there was an error during the listing of channels.
     * @throws GRPCException         If there was an error with gRPC communication.
     */
    public static List<QueuesChannel> listQueuesChannels(KubeMQClient kubeMQClient, String clientId, String channelSearch) throws ListChannelsException, GRPCException {
        try {
            Request request = Request.newBuilder()
                    .setRequestID(UUID.randomUUID().toString())
                    .setRequestTypeData(Kubemq.Request.RequestType.Query)
                    .setRequestTypeDataValue(2)
                    .setMetadata("list-channels")
                    .setChannel(REQUESTS_CHANNEL)
                    .setClientID(clientId)
                    .putTags("channel_type", "queues")
                    .putTags("channel_search", channelSearch)
                    .setTimeout(10 * 1000)
                    .build();

            Response response = kubeMQClient.getClient().sendRequest(request);
            if (response != null && response.getExecuted()) {
                return ChannelDecoder.decodeQueuesChannelList(response.getBody().toByteArray());
            } else if (response != null) {
                log.error("Client failed to list queue channels, error: {}", response.getError());
                throw new ListChannelsException(response.getError());
            }
        } catch (io.grpc.StatusRuntimeException e) {
            throw new GRPCException(e);
        }
        catch (IOException e) {
            log.error("Failed to decode response body byte array", e);
            throw new RuntimeException(e);
        }
        return null;
    }

    /**
     * Lists PubSub channels on the KubeMQ server.
     *
     * @param kubeMQClient    The kubeMQClient object for communication with KubeMQ.
     * @param clientId     The ID of the client.
     * @param channelType  The type of PubSub channel.
     * @param channelSearch The search term to filter the channels.
     * @return A list of PubSubChannel objects.
     * @throws ListChannelsException If there was an error during the listing of channels.
     * @throws GRPCException         If there was an error with gRPC communication.
     */
    public static List<PubSubChannel> listPubSubChannels(KubeMQClient kubeMQClient, String clientId, String channelType, String channelSearch) throws ListChannelsException, GRPCException {
        try {
            Kubemq.Request request = Kubemq.Request.newBuilder()
                    .setRequestID(UUID.randomUUID().toString())
                    .setRequestTypeData(Kubemq.Request.RequestType.Query)
                    .setRequestTypeDataValue(2)
                    .setMetadata("list-channels")
                    .setChannel(REQUESTS_CHANNEL)
                    .setClientID(clientId)
                    .putTags("channel_type", channelType)
                    .putTags("channel_search", channelSearch != null ? channelSearch:"")
                    .setTimeout(10 * 1000)
                    .build();
            kubemq.Kubemq.Response response = kubeMQClient.getClient().sendRequest(request);
            if (response != null && response.getExecuted()) {
                return ChannelDecoder.decodePubSubChannelList(response.getBody().toByteArray());
            } else if (response != null) {
                log.error("Client failed to list PubSub channels, error: {}", response.getError());
                throw new ListChannelsException(response.getError());
            }
        } catch (io.grpc.StatusRuntimeException e) {
            throw new GRPCException(e);
        }
        catch (IOException e) {
            log.error("Failed to decode response body byte array", e);
            throw new RuntimeException(e);
        }
        return null;
    }

    /**
     * Lists CQ channels on the KubeMQ server.
     *
     * @param kubeMQClient    The kubeMQClient object for communication with KubeMQ.
     * @param clientId     The ID of the client.
     * @param channelType  The type of CQ channel.
     * @param channelSearch The search term to filter the channels.
     * @return A list of CQChannel objects.
     * @throws ListChannelsException If there was an error during the listing of channels.
     * @throws GRPCException         If there was an error with gRPC communication.
     */
    public static List<CQChannel> listCQChannels(KubeMQClient kubeMQClient, String clientId, String channelType, String channelSearch) throws ListChannelsException, GRPCException {
        try {
            Request request = Request.newBuilder()
                    .setRequestID(UUID.randomUUID().toString())
                    .setRequestTypeData(Kubemq.Request.RequestType.Query)
                    .setRequestTypeDataValue(2)
                    .setMetadata("list-channels")
                    .setChannel(REQUESTS_CHANNEL)
                    .setClientID(clientId)
                    .putTags("channel_type", channelType)
                    .putTags("channel_search", channelSearch != null ? channelSearch:"")
                    .setTimeout(10 * 1000)
                    .build();

            Response response = kubeMQClient.getClient().sendRequest(request);
            if (response != null && response.getExecuted()) {
                return ChannelDecoder.decodeCqChannelList(response.getBody().toByteArray());
            } else if (response != null) {
                log.error("Client failed to list CQ channels, error: {}", response.getError());
                throw new ListChannelsException(response.getError());
            }
        } catch (io.grpc.StatusRuntimeException e) {
            throw new GRPCException(e);
        }
        catch (IOException e) {
            log.error("Failed to decode response body byte array", e);
            throw new RuntimeException(e);
        }
        return null;
    }
}

