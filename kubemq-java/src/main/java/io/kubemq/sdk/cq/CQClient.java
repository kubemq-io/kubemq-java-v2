package io.kubemq.sdk.cq;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.KubeMQUtils;
import io.kubemq.sdk.exception.GRPCException;
import kubemq.Kubemq;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * CQClient class represents a client for connecting to the KubeMQ server for command and query operations.
 */
@Slf4j
@Builder
public class CQClient {

    /**
     * The KubeMQ client used for communication.
     */
    private final KubeMQClient kubeMQClient;

    /**
     * Sends a command request to the KubeMQ server.
     *
     * @param message the command message to send
     * @return the command response message
     * @throws GRPCException if an error occurs during the gRPC call
     */
    public CommandResponseMessage sendCommandRequest(CommandMessage message) {
        try {
            message.validate();
            Kubemq.Request request = message.encode(kubeMQClient.getClientId());
            Kubemq.Response response = kubeMQClient.getClient().sendRequest(request);
            return new CommandResponseMessage().decode(response);
        } catch (GRPCException e) {
            log.error(e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Sends a query request to the KubeMQ server.
     *
     * @param message the query message to send
     * @return the query response message
     * @throws GRPCException if an error occurs during the gRPC call
     */
    public QueryResponseMessage sendQueryRequest(QueryMessage message) {
        try {
            message.validate();
            Kubemq.Request request = message.encode(kubeMQClient.getClientId());
            Kubemq.Response response = kubeMQClient.getClient().sendRequest(request);
            return new QueryResponseMessage().decode(response);
        } catch (GRPCException e) {
            log.error(e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Sends a response message to the KubeMQ server.
     *
     * @param message the response message to send
     * @throws GRPCException if an error occurs during the gRPC call
     */
    public void sendResponseMessage(CommandResponseMessage message) {
        try {
            message.validate();
            kubeMQClient.getClient().sendResponse(message.encode(kubeMQClient.getClientId()));
        } catch (GRPCException e) {
            log.error(e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Creates a commands channel on the KubeMQ server.
     *
     * @param channel the name of the channel to create
     * @return true if the channel was created successfully, false otherwise
     */
    public boolean createCommandsChannel(String channel) {
        return KubeMQUtils.createChannelRequest(kubeMQClient, kubeMQClient.getClientId(), channel, "commands");
    }

    /**
     * Creates a queries channel on the KubeMQ server.
     *
     * @param channel the name of the channel to create
     * @return true if the channel was created successfully, false otherwise
     */
    public boolean createQueriesChannel(String channel) {
        return KubeMQUtils.createChannelRequest(kubeMQClient, kubeMQClient.getClientId(), channel, "queries");
    }

    /**
     * Deletes a commands channel from the KubeMQ server.
     *
     * @param channel the name of the channel to delete
     * @return true if the channel was deleted successfully, false otherwise
     */
    public boolean deleteCommandsChannel(String channel) {
        return KubeMQUtils.deleteChannelRequest(kubeMQClient, kubeMQClient.getClientId(), channel, "commands");
    }

    /**
     * Deletes a queries channel from the KubeMQ server.
     *
     * @param channel the name of the channel to delete
     * @return true if the channel was deleted successfully, false otherwise
     */
    public boolean deleteQueriesChannel(String channel) {
        return KubeMQUtils.deleteChannelRequest(kubeMQClient, kubeMQClient.getClientId(), channel, "queries");
    }

    /**
     * Lists the available commands channels on the KubeMQ server.
     *
     * @param channelSearch the search pattern for filtering channel names
     * @return a list of CQChannel objects representing the commands channels
     */
    public List<CQChannel> listCommandsChannels(String channelSearch) {
        return KubeMQUtils.listCQChannels(kubeMQClient, kubeMQClient.getClientId(), "commands", channelSearch);
    }

    /**
     * Lists the available queries channels on the KubeMQ server.
     *
     * @param channelSearch the search pattern for filtering channel names
     * @return a list of CQChannel objects representing the queries channels
     */
    public List<CQChannel> listQueriesChannels(String channelSearch) {
        return KubeMQUtils.listCQChannels(kubeMQClient, kubeMQClient.getClientId(), "queries", channelSearch);
    }

    /**
     * Subscribes to commands on the KubeMQ server.
     *
     * @param commandsSubscription the commands subscription configuration
     */
    public void subscribeToCommands(CommandsSubscription commandsSubscription) {
        commandsSubscription.validate();
        StreamObserver<Kubemq.Request> commandSubscriptionObserver = new StreamObserver<Kubemq.Request>() {
            @Override
            public void onNext(Kubemq.Request messageReceive) {
                log.trace("CommandsSubscription-> CommandMessageReceived Received: '{}'", messageReceive);
                commandsSubscription.raiseOnReceiveMessage(CommandMessageReceived.decode(messageReceive));
            }

            @Override
            public void onError(Throwable t) {
                log.error("Error in CommandsSubscription: ", t);
                commandsSubscription.raiseOnError(t.getMessage());
            }

            @Override
            public void onCompleted() {
                log.trace("CommandsSubscription Stream completed.");
            }
        };

        Kubemq.Subscribe subscribe = commandsSubscription.encode(kubeMQClient.getClientId());
        kubeMQClient.getAsyncClient().subscribeToRequests(subscribe, commandSubscriptionObserver);
    }

    /**
     * Subscribes to queries on the KubeMQ server.
     *
     * @param queriesSubscription the queries subscription configuration
     */
    public void subscribeToQueries(QueriesSubscription queriesSubscription) {
        queriesSubscription.validate();
        StreamObserver<Kubemq.Request> querySubscriptionObserver = new StreamObserver<Kubemq.Request>() {
            @Override
            public void onNext(Kubemq.Request messageReceive) {
                log.trace("QueriesSubscription-> QueryMessageReceived Received: '{}'", messageReceive);
                queriesSubscription.raiseOnReceiveMessage(QueryMessageReceived.decode(messageReceive));
            }

            @Override
            public void onError(Throwable t) {
                log.error("Error in QueriesSubscription: ", t);
                queriesSubscription.raiseOnError(t.getMessage());
            }

            @Override
            public void onCompleted() {
                log.trace("QueriesSubscription Stream completed.");
            }
        };

        Kubemq.Subscribe subscribe = queriesSubscription.encode(kubeMQClient.getClientId());
        kubeMQClient.getAsyncClient().subscribeToRequests(subscribe, querySubscriptionObserver);
    }
}
