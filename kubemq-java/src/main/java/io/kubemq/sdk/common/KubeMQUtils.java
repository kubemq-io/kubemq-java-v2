package io.kubemq.sdk.common;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.cq.CQChannel;
import io.kubemq.sdk.exception.CreateChannelException;
import io.kubemq.sdk.exception.DeleteChannelException;
import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.GRPCException;
import io.kubemq.sdk.exception.ListChannelsException;
import io.kubemq.sdk.exception.ValidationException;
import io.kubemq.sdk.observability.KubeMQLogger;
import io.kubemq.sdk.observability.KubeMQLoggerFactory;
import io.kubemq.sdk.pubsub.PubSubChannel;
import io.kubemq.sdk.queues.QueuesChannel;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import kubemq.Kubemq;
import kubemq.Kubemq.Request;
import kubemq.Kubemq.Response;

/**
 * Internal utility class for managing KubeMQ channels. Not part of the public API -- may be moved
 * or removed without notice.
 *
 * @deprecated Internal use only. Will be moved to the transport layer.
 */
@Deprecated
@Internal
public class KubeMQUtils {

  private static final KubeMQLogger LOG = KubeMQLoggerFactory.getLogger(KubeMQUtils.class);

  private static final String REQUESTS_CHANNEL = "kubemq.cluster.internal.requests";

  private static final Set<String> VALID_CHANNEL_TYPES =
      Set.of("events", "events_store", "commands", "queries", "queues");

  private static final int CHANNEL_MGMT_DEADLINE_SECONDS = 10;
  private static final int MAX_CHANNEL_LENGTH = 256;
  private static final Pattern CHANNEL_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._\\-/:]+$");

  /**
   * Validates a channel name for publish/send operations. Channel names must be non-null,
   * non-empty, at most 256 characters, and contain only alphanumeric characters, dots, dashes,
   * underscores, forward slashes, and colons.
   *
   * @param channel the channel name to validate
   * @param operation the operation context for error reporting
   * @throws ValidationException if channel name is invalid
   */
  public static void validateChannelName(String channel, String operation) {
    if (channel == null || channel.trim().isEmpty()) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message("Channel name is required and cannot be empty.")
          .operation(operation)
          .build();
    }
    if (channel.length() > MAX_CHANNEL_LENGTH) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message(
              "Channel name must be at most "
                  + MAX_CHANNEL_LENGTH
                  + " characters. Got: "
                  + channel.length())
          .operation(operation)
          .channel(channel)
          .build();
    }
    if (!CHANNEL_NAME_PATTERN.matcher(channel).matches()) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message(
              "Channel name contains invalid characters. "
                  + "Allowed: alphanumeric, dots, dashes, underscores, forward slashes, colons. "
                  + "Got: '"
                  + channel
                  + "'")
          .operation(operation)
          .channel(channel)
          .build();
    }
  }

  /**
   * Validates that the given channel type is one of the supported types.
   *
   * @param type the channel type to validate
   * @throws ValidationException if the type is null or not one of the supported types
   */
  public static void validateChannelType(String type) {
    if (type == null || !VALID_CHANNEL_TYPES.contains(type)) {
      throw ValidationException.builder()
          .code(ErrorCode.INVALID_ARGUMENT)
          .message(
              "Invalid channel type: "
                  + type
                  + ". Must be one of: events, events_store, commands, queries, queues")
          .operation("validateChannelType")
          .build();
    }
  }

  /**
   * Creates a channel on the KubeMQ server.
   *
   * @param kubeMQClient The kubeMQClient object for communication with KubeMQ.
   * @param clientId The ID of the client.
   * @param channelName The name of the channel to create.
   * @param channelType The type of the channel.
   * @return True if the channel creation was successful, otherwise null.
   * @throws CreateChannelException If there was an error during the channel creation.
   * @throws GRPCException If there was an error with gRPC communication.
   */
  public static Boolean createChannelRequest(
      KubeMQClient kubeMQClient, String clientId, String channelName, String channelType)
      throws CreateChannelException, GRPCException {
    try {
      Kubemq.Request request =
          Kubemq.Request.newBuilder()
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
   * @param kubeMQClient The kubeMQClient object for communication with KubeMQ.
   * @param clientId The ID of the client.
   * @param channelName The name of the channel to delete.
   * @param channelType The type of the channel.
   * @return True if the channel deletion was successful, otherwise null.
   * @throws DeleteChannelException If there was an error during the channel deletion.
   * @throws GRPCException If there was an error with gRPC communication.
   */
  public static Boolean deleteChannelRequest(
      KubeMQClient kubeMQClient, String clientId, String channelName, String channelType)
      throws DeleteChannelException, GRPCException {
    try {
      Kubemq.Request request =
          Kubemq.Request.newBuilder()
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
      kubemq.Kubemq.Response response = kubeMQClient.getClient()
          .withDeadlineAfter(CHANNEL_MGMT_DEADLINE_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
          .sendRequest(request);
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
   * @param kubeMQClient The kubeMQClient object for communication with KubeMQ.
   * @param clientId The ID of the client.
   * @param channelSearch The search term to filter the channels.
   * @return A list of QueuesChannel objects.
   * @throws ListChannelsException If there was an error during the listing of channels.
   * @throws GRPCException If there was an error with gRPC communication.
   */
  public static List<QueuesChannel> listQueuesChannels(
      KubeMQClient kubeMQClient, String clientId, String channelSearch)
      throws ListChannelsException, GRPCException {
    try {
      Request request =
          Request.newBuilder()
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

      Response response = kubeMQClient.getClient()
          .withDeadlineAfter(CHANNEL_MGMT_DEADLINE_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
          .sendRequest(request);
      if (response != null && response.getExecuted()) {
        return ChannelDecoder.decodeQueuesChannelList(response.getBody().toByteArray());
      } else if (response != null) {
        LOG.error("Client failed to list queue channels", "error", response.getError());
        throw new ListChannelsException(response.getError());
      }
    } catch (io.grpc.StatusRuntimeException e) {
      throw io.kubemq.sdk.exception.GrpcErrorMapper.map(e, "listQueuesChannels", null, null, false);
    } catch (IOException e) {
      LOG.error("Failed to decode response body byte array", e);
      throw io.kubemq.sdk.exception.KubeMQException.newBuilder()
          .code(io.kubemq.sdk.exception.ErrorCode.UNKNOWN_ERROR)
          .category(io.kubemq.sdk.exception.ErrorCategory.FATAL)
          .retryable(false)
          .message("listQueuesChannels failed: " + e.getMessage())
          .operation("listQueuesChannels")
          .cause(e)
          .build();
    }
    return null;
  }

  /**
   * Lists PubSub channels on the KubeMQ server.
   *
   * @param kubeMQClient The kubeMQClient object for communication with KubeMQ.
   * @param clientId The ID of the client.
   * @param channelType The type of PubSub channel.
   * @param channelSearch The search term to filter the channels.
   * @return A list of PubSubChannel objects.
   * @throws ListChannelsException If there was an error during the listing of channels.
   * @throws GRPCException If there was an error with gRPC communication.
   */
  public static List<PubSubChannel> listPubSubChannels(
      KubeMQClient kubeMQClient, String clientId, String channelType, String channelSearch)
      throws ListChannelsException, GRPCException {
    try {
      Kubemq.Request request =
          Kubemq.Request.newBuilder()
              .setRequestID(UUID.randomUUID().toString())
              .setRequestTypeData(Kubemq.Request.RequestType.Query)
              .setRequestTypeDataValue(2)
              .setMetadata("list-channels")
              .setChannel(REQUESTS_CHANNEL)
              .setClientID(clientId)
              .putTags("channel_type", channelType)
              .putTags("channel_search", channelSearch != null ? channelSearch : "")
              .setTimeout(10 * 1000)
              .build();
      kubemq.Kubemq.Response response = kubeMQClient.getClient()
          .withDeadlineAfter(CHANNEL_MGMT_DEADLINE_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
          .sendRequest(request);
      if (response != null && response.getExecuted()) {
        return ChannelDecoder.decodePubSubChannelList(response.getBody().toByteArray());
      } else if (response != null) {
        LOG.error("Client failed to list PubSub channels", "error", response.getError());
        throw new ListChannelsException(response.getError());
      }
    } catch (io.grpc.StatusRuntimeException e) {
      throw io.kubemq.sdk.exception.GrpcErrorMapper.map(e, "listPubSubChannels", null, null, false);
    } catch (IOException e) {
      LOG.error("Failed to decode response body byte array", e);
      throw io.kubemq.sdk.exception.KubeMQException.newBuilder()
          .code(io.kubemq.sdk.exception.ErrorCode.UNKNOWN_ERROR)
          .category(io.kubemq.sdk.exception.ErrorCategory.FATAL)
          .retryable(false)
          .message("listPubSubChannels failed: " + e.getMessage())
          .operation("listPubSubChannels")
          .cause(e)
          .build();
    }
    return null;
  }

  /**
   * Lists CQ channels on the KubeMQ server.
   *
   * @param kubeMQClient The kubeMQClient object for communication with KubeMQ.
   * @param clientId The ID of the client.
   * @param channelType The type of CQ channel.
   * @param channelSearch The search term to filter the channels.
   * @return A list of CQChannel objects.
   * @throws ListChannelsException If there was an error during the listing of channels.
   * @throws GRPCException If there was an error with gRPC communication.
   */
  public static List<CQChannel> listCQChannels(
      KubeMQClient kubeMQClient, String clientId, String channelType, String channelSearch)
      throws ListChannelsException, GRPCException {
    try {
      Request request =
          Request.newBuilder()
              .setRequestID(UUID.randomUUID().toString())
              .setRequestTypeData(Kubemq.Request.RequestType.Query)
              .setRequestTypeDataValue(2)
              .setMetadata("list-channels")
              .setChannel(REQUESTS_CHANNEL)
              .setClientID(clientId)
              .putTags("channel_type", channelType)
              .putTags("channel_search", channelSearch != null ? channelSearch : "")
              .setTimeout(10 * 1000)
              .build();

      Response response = kubeMQClient.getClient()
          .withDeadlineAfter(CHANNEL_MGMT_DEADLINE_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
          .sendRequest(request);
      if (response != null && response.getExecuted()) {
        return ChannelDecoder.decodeCqChannelList(response.getBody().toByteArray());
      } else if (response != null) {
        LOG.error("Client failed to list CQ channels", "error", response.getError());
        throw new ListChannelsException(response.getError());
      }
    } catch (io.grpc.StatusRuntimeException e) {
      throw io.kubemq.sdk.exception.GrpcErrorMapper.map(e, "listCQChannels", null, null, false);
    } catch (IOException e) {
      LOG.error("Failed to decode response body byte array", e);
      throw io.kubemq.sdk.exception.KubeMQException.newBuilder()
          .code(io.kubemq.sdk.exception.ErrorCode.UNKNOWN_ERROR)
          .category(io.kubemq.sdk.exception.ErrorCategory.FATAL)
          .retryable(false)
          .message("listCQChannels failed: " + e.getMessage())
          .operation("listCQChannels")
          .cause(e)
          .build();
    }
    return null;
  }
}
