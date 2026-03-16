package io.kubemq.sdk.exception;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;

/**
 * Maps gRPC StatusRuntimeException to SDK-typed KubeMQException. Covers all 17 gRPC status codes
 * (0-16). Original gRPC error is always preserved in the cause chain.
 */
public final class GrpcErrorMapper {

  private GrpcErrorMapper() {}

  /**
   * Maps a gRPC StatusRuntimeException to a KubeMQException subtype.
   *
   * @param grpcError the gRPC exception to map
   * @param operation the operation that was being performed
   * @param channel the target channel (may be null)
   * @param requestId the request ID (may be null)
   * @param localContextCancelled true if the local context/token was cancelled
   * @return a KubeMQException subtype with the original gRPC error in the cause chain
   */
  public static KubeMQException map(
      StatusRuntimeException grpcError,
      String operation,
      String channel,
      String requestId,
      boolean localContextCancelled) {
    Status status = grpcError.getStatus();
    Status.Code code = status.getCode();
    String description =
        status.getDescription() != null ? status.getDescription() : grpcError.getMessage();
    int grpcStatusCode = code.value();

    String richDetails = extractRichDetails(grpcError);
    if (richDetails != null) {
      description = description + " [details: " + richDetails + "]";
    }

    String message =
        ErrorMessageBuilder.build(
            operation, channel, description, toErrorCode(code, localContextCancelled));

    switch (code) {
      case OK:
        throw new IllegalArgumentException(
            "StatusRuntimeException with OK status should never occur");

      case CANCELLED:
        if (localContextCancelled) {
          return OperationCancelledException.builder()
              .code(ErrorCode.CANCELLED_BY_CLIENT)
              .message(message)
              .operation(operation)
              .channel(channel)
              .requestId(requestId)
              .cause(grpcError)
              .statusCode(grpcStatusCode)
              .category(ErrorCategory.CANCELLATION)
              .retryable(false)
              .build();
        } else {
          return ConnectionException.builder()
              .code(ErrorCode.CANCELLED_BY_SERVER)
              .message(message)
              .operation(operation)
              .channel(channel)
              .requestId(requestId)
              .cause(grpcError)
              .statusCode(grpcStatusCode)
              .category(ErrorCategory.TRANSIENT)
              .retryable(true)
              .build();
        }

      case UNKNOWN:
        return ConnectionException.builder()
            .code(ErrorCode.UNKNOWN_ERROR)
            .message(message)
            .operation(operation)
            .channel(channel)
            .requestId(requestId)
            .cause(grpcError)
            .statusCode(grpcStatusCode)
            .category(ErrorCategory.TRANSIENT)
            .retryable(true)
            .build();

      case INVALID_ARGUMENT:
        return ValidationException.builder()
            .code(ErrorCode.INVALID_ARGUMENT)
            .message(message)
            .operation(operation)
            .channel(channel)
            .requestId(requestId)
            .cause(grpcError)
            .statusCode(grpcStatusCode)
            .build();

      case DEADLINE_EXCEEDED:
        return KubeMQTimeoutException.builder()
            .code(ErrorCode.DEADLINE_EXCEEDED)
            .message(message)
            .operation(operation)
            .channel(channel)
            .requestId(requestId)
            .cause(grpcError)
            .statusCode(grpcStatusCode)
            .build();

      case NOT_FOUND:
        return KubeMQException.newBuilder()
            .code(ErrorCode.NOT_FOUND)
            .message(message)
            .operation(operation)
            .channel(channel)
            .requestId(requestId)
            .cause(grpcError)
            .statusCode(grpcStatusCode)
            .category(ErrorCategory.NOT_FOUND)
            .retryable(false)
            .build();

      case ALREADY_EXISTS:
        return ValidationException.builder()
            .code(ErrorCode.ALREADY_EXISTS)
            .message(message)
            .operation(operation)
            .channel(channel)
            .requestId(requestId)
            .cause(grpcError)
            .statusCode(grpcStatusCode)
            .build();

      case PERMISSION_DENIED:
        return AuthorizationException.builder()
            .message(message)
            .operation(operation)
            .channel(channel)
            .requestId(requestId)
            .cause(grpcError)
            .statusCode(grpcStatusCode)
            .build();

      case RESOURCE_EXHAUSTED:
        return ThrottlingException.builder()
            .message(message)
            .operation(operation)
            .channel(channel)
            .requestId(requestId)
            .cause(grpcError)
            .statusCode(grpcStatusCode)
            .build();

      case FAILED_PRECONDITION:
        return ValidationException.builder()
            .code(ErrorCode.FAILED_PRECONDITION)
            .message(message)
            .operation(operation)
            .channel(channel)
            .requestId(requestId)
            .cause(grpcError)
            .statusCode(grpcStatusCode)
            .build();

      case ABORTED:
        return ConnectionException.builder()
            .code(ErrorCode.ABORTED)
            .message(message)
            .operation(operation)
            .channel(channel)
            .requestId(requestId)
            .cause(grpcError)
            .statusCode(grpcStatusCode)
            .category(ErrorCategory.TRANSIENT)
            .retryable(true)
            .build();

      case OUT_OF_RANGE:
        return ValidationException.builder()
            .code(ErrorCode.OUT_OF_RANGE)
            .message(message)
            .operation(operation)
            .channel(channel)
            .requestId(requestId)
            .cause(grpcError)
            .statusCode(grpcStatusCode)
            .build();

      case UNIMPLEMENTED:
        return ServerException.builder()
            .code(ErrorCode.SERVER_UNIMPLEMENTED)
            .message(message)
            .operation(operation)
            .channel(channel)
            .requestId(requestId)
            .cause(grpcError)
            .statusCode(grpcStatusCode)
            .build();

      case INTERNAL:
        return ServerException.builder()
            .code(ErrorCode.SERVER_INTERNAL)
            .message(message)
            .operation(operation)
            .channel(channel)
            .requestId(requestId)
            .cause(grpcError)
            .statusCode(grpcStatusCode)
            .build();

      case UNAVAILABLE:
        return ConnectionException.builder()
            .code(ErrorCode.UNAVAILABLE)
            .message(message)
            .operation(operation)
            .channel(channel)
            .requestId(requestId)
            .cause(grpcError)
            .statusCode(grpcStatusCode)
            .category(ErrorCategory.TRANSIENT)
            .retryable(true)
            .build();

      case DATA_LOSS:
        return ServerException.builder()
            .code(ErrorCode.DATA_LOSS)
            .message(message)
            .operation(operation)
            .channel(channel)
            .requestId(requestId)
            .cause(grpcError)
            .statusCode(grpcStatusCode)
            .build();

      case UNAUTHENTICATED:
        return AuthenticationException.builder()
            .message(message)
            .operation(operation)
            .channel(channel)
            .requestId(requestId)
            .cause(grpcError)
            .statusCode(grpcStatusCode)
            .build();

      default:
        return KubeMQException.newBuilder()
            .code(ErrorCode.UNKNOWN_ERROR)
            .message(message)
            .operation(operation)
            .channel(channel)
            .requestId(requestId)
            .cause(grpcError)
            .statusCode(grpcStatusCode)
            .category(ErrorCategory.FATAL)
            .retryable(false)
            .build();
    }
  }

  /** Extracts rich error details from google.rpc.Status if present. */
  private static String extractRichDetails(StatusRuntimeException grpcError) {
    try {
      com.google.rpc.Status rpcStatus = StatusProto.fromThrowable(grpcError);
      if (rpcStatus == null || rpcStatus.getDetailsCount() == 0) {
        return null;
      }

      StringBuilder sb = new StringBuilder();
      for (com.google.protobuf.Any detail : rpcStatus.getDetailsList()) {
        if (detail.is(com.google.rpc.ErrorInfo.class)) {
          com.google.rpc.ErrorInfo errorInfo = detail.unpack(com.google.rpc.ErrorInfo.class);
          sb.append("ErrorInfo{reason=")
              .append(errorInfo.getReason())
              .append(", domain=")
              .append(errorInfo.getDomain())
              .append(", metadata=")
              .append(errorInfo.getMetadataMap())
              .append("} ");
        } else if (detail.is(com.google.rpc.RetryInfo.class)) {
          com.google.rpc.RetryInfo retryInfo = detail.unpack(com.google.rpc.RetryInfo.class);
          sb.append("RetryInfo{retryDelay=").append(retryInfo.getRetryDelay()).append("} ");
        } else if (detail.is(com.google.rpc.DebugInfo.class)) {
          com.google.rpc.DebugInfo debugInfo = detail.unpack(com.google.rpc.DebugInfo.class);
          sb.append("DebugInfo{detail=").append(debugInfo.getDetail()).append("} ");
        } else {
          sb.append("Unknown{typeUrl=").append(detail.getTypeUrl()).append("} ");
        }
      }
      return sb.toString().trim();
    } catch (Exception e) {
      return null;
    }
  }

  private static ErrorCode toErrorCode(Status.Code code, boolean localContextCancelled) {
    switch (code) {
      case CANCELLED:
        return localContextCancelled
            ? ErrorCode.CANCELLED_BY_CLIENT
            : ErrorCode.CANCELLED_BY_SERVER;
      case UNKNOWN:
        return ErrorCode.UNKNOWN_ERROR;
      case INVALID_ARGUMENT:
        return ErrorCode.INVALID_ARGUMENT;
      case DEADLINE_EXCEEDED:
        return ErrorCode.DEADLINE_EXCEEDED;
      case NOT_FOUND:
        return ErrorCode.NOT_FOUND;
      case ALREADY_EXISTS:
        return ErrorCode.ALREADY_EXISTS;
      case PERMISSION_DENIED:
        return ErrorCode.AUTHORIZATION_DENIED;
      case RESOURCE_EXHAUSTED:
        return ErrorCode.RESOURCE_EXHAUSTED;
      case FAILED_PRECONDITION:
        return ErrorCode.FAILED_PRECONDITION;
      case ABORTED:
        return ErrorCode.ABORTED;
      case OUT_OF_RANGE:
        return ErrorCode.OUT_OF_RANGE;
      case UNIMPLEMENTED:
        return ErrorCode.SERVER_UNIMPLEMENTED;
      case INTERNAL:
        return ErrorCode.SERVER_INTERNAL;
      case UNAVAILABLE:
        return ErrorCode.UNAVAILABLE;
      case DATA_LOSS:
        return ErrorCode.DATA_LOSS;
      case UNAUTHENTICATED:
        return ErrorCode.AUTHENTICATION_FAILED;
      default:
        return ErrorCode.UNKNOWN_ERROR;
    }
  }
}
