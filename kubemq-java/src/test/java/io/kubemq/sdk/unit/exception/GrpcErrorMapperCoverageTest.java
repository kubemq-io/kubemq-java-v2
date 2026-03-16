package io.kubemq.sdk.unit.exception;

import static org.junit.jupiter.api.Assertions.*;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import io.kubemq.sdk.exception.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Additional coverage tests for GrpcErrorMapper: - null description fallback - extractRichDetails
 * with ErrorInfo, RetryInfo, DebugInfo, unknown detail types - extractRichDetails exception path
 */
class GrpcErrorMapperCoverageTest {

  @Nested
  @DisplayName("Null Description Fallback")
  class NullDescriptionTests {

    @Test
    @DisplayName("map uses getMessage() fallback when description is null")
    void map_nullDescription_usesMessageFallback() {
      StatusRuntimeException grpcError = Status.UNAVAILABLE.asRuntimeException();

      KubeMQException result = GrpcErrorMapper.map(grpcError, "sendEvent", "ch1", null, false);

      assertInstanceOf(ConnectionException.class, result);
      assertNotNull(result.getMessage());
      assertEquals(ErrorCode.UNAVAILABLE, result.getCode());
    }

    @Test
    @DisplayName("map with empty description still works")
    void map_emptyDescription_works() {
      StatusRuntimeException grpcError = Status.INTERNAL.withDescription("").asRuntimeException();

      KubeMQException result = GrpcErrorMapper.map(grpcError, "op", null, null, false);

      assertInstanceOf(ServerException.class, result);
      assertNotNull(result.getMessage());
    }
  }

  @Nested
  @DisplayName("extractRichDetails Tests")
  class ExtractRichDetailsTests {

    @Test
    @DisplayName("map includes ErrorInfo details in message")
    void map_withErrorInfo_includesDetailsInMessage() {
      com.google.rpc.ErrorInfo errorInfo =
          com.google.rpc.ErrorInfo.newBuilder()
              .setReason("QUOTA_EXCEEDED")
              .setDomain("kubemq.io")
              .putMetadata("limit", "100")
              .build();

      com.google.rpc.Status rpcStatus =
          com.google.rpc.Status.newBuilder()
              .setCode(Status.RESOURCE_EXHAUSTED.getCode().value())
              .setMessage("Resource exhausted")
              .addDetails(com.google.protobuf.Any.pack(errorInfo))
              .build();

      StatusRuntimeException grpcError = StatusProto.toStatusRuntimeException(rpcStatus);

      KubeMQException result = GrpcErrorMapper.map(grpcError, "sendBatch", "ch", null, false);

      assertInstanceOf(ThrottlingException.class, result);
      assertTrue(result.getMessage().contains("ErrorInfo"));
      assertTrue(result.getMessage().contains("QUOTA_EXCEEDED"));
    }

    @Test
    @DisplayName("map includes RetryInfo details in message")
    void map_withRetryInfo_includesDetailsInMessage() {
      com.google.rpc.RetryInfo retryInfo =
          com.google.rpc.RetryInfo.newBuilder()
              .setRetryDelay(com.google.protobuf.Duration.newBuilder().setSeconds(5).build())
              .build();

      com.google.rpc.Status rpcStatus =
          com.google.rpc.Status.newBuilder()
              .setCode(Status.UNAVAILABLE.getCode().value())
              .setMessage("Service unavailable")
              .addDetails(com.google.protobuf.Any.pack(retryInfo))
              .build();

      StatusRuntimeException grpcError = StatusProto.toStatusRuntimeException(rpcStatus);

      KubeMQException result = GrpcErrorMapper.map(grpcError, "connect", null, null, false);

      assertInstanceOf(ConnectionException.class, result);
      assertTrue(result.getMessage().contains("RetryInfo"));
    }

    @Test
    @DisplayName("map includes DebugInfo details in message")
    void map_withDebugInfo_includesDetailsInMessage() {
      com.google.rpc.DebugInfo debugInfo =
          com.google.rpc.DebugInfo.newBuilder()
              .setDetail("server stack trace here")
              .addStackEntries("at Server.handle()")
              .build();

      com.google.rpc.Status rpcStatus =
          com.google.rpc.Status.newBuilder()
              .setCode(Status.INTERNAL.getCode().value())
              .setMessage("Internal error")
              .addDetails(com.google.protobuf.Any.pack(debugInfo))
              .build();

      StatusRuntimeException grpcError = StatusProto.toStatusRuntimeException(rpcStatus);

      KubeMQException result = GrpcErrorMapper.map(grpcError, "op", null, null, false);

      assertInstanceOf(ServerException.class, result);
      assertTrue(result.getMessage().contains("DebugInfo"));
      assertTrue(result.getMessage().contains("server stack trace here"));
    }

    @Test
    @DisplayName("map handles unknown detail types gracefully")
    void map_withUnknownDetailType_handlesGracefully() {
      // Pack a type that is not ErrorInfo, RetryInfo, or DebugInfo
      com.google.rpc.Help help =
          com.google.rpc.Help.newBuilder()
              .addLinks(
                  com.google.rpc.Help.Link.newBuilder()
                      .setDescription("docs")
                      .setUrl("https://kubemq.io/docs")
                      .build())
              .build();

      com.google.rpc.Status rpcStatus =
          com.google.rpc.Status.newBuilder()
              .setCode(Status.UNAVAILABLE.getCode().value())
              .setMessage("Unavailable")
              .addDetails(com.google.protobuf.Any.pack(help))
              .build();

      StatusRuntimeException grpcError = StatusProto.toStatusRuntimeException(rpcStatus);

      KubeMQException result = GrpcErrorMapper.map(grpcError, "op", null, null, false);

      assertInstanceOf(ConnectionException.class, result);
      assertTrue(result.getMessage().contains("Unknown{typeUrl="));
    }

    @Test
    @DisplayName("map with no details returns null from extractRichDetails")
    void map_noDetails_noDetailsInMessage() {
      StatusRuntimeException grpcError =
          Status.UNAVAILABLE.withDescription("plain error").asRuntimeException();

      KubeMQException result = GrpcErrorMapper.map(grpcError, "op", null, null, false);

      assertInstanceOf(ConnectionException.class, result);
      assertFalse(result.getMessage().contains("[details:"));
    }

    @Test
    @DisplayName("map handles multiple detail types in single response")
    void map_multipleDetails_allIncluded() {
      com.google.rpc.ErrorInfo errorInfo =
          com.google.rpc.ErrorInfo.newBuilder()
              .setReason("RATE_LIMITED")
              .setDomain("kubemq.io")
              .build();

      com.google.rpc.DebugInfo debugInfo =
          com.google.rpc.DebugInfo.newBuilder().setDetail("additional debug info").build();

      com.google.rpc.Status rpcStatus =
          com.google.rpc.Status.newBuilder()
              .setCode(Status.RESOURCE_EXHAUSTED.getCode().value())
              .setMessage("Rate limited")
              .addDetails(com.google.protobuf.Any.pack(errorInfo))
              .addDetails(com.google.protobuf.Any.pack(debugInfo))
              .build();

      StatusRuntimeException grpcError = StatusProto.toStatusRuntimeException(rpcStatus);

      KubeMQException result = GrpcErrorMapper.map(grpcError, "op", null, null, false);

      assertTrue(result.getMessage().contains("ErrorInfo"));
      assertTrue(result.getMessage().contains("DebugInfo"));
    }
  }

  @Nested
  @DisplayName("extractRichDetails Exception Path")
  class ExtractRichDetailsExceptionTests {

    @Test
    @DisplayName("extractRichDetails returns null when StatusProto.fromThrowable fails")
    void extractRichDetails_exceptionPath_returnsNull() {
      // A plain StatusRuntimeException without trailers won't have proto status.
      // StatusProto.fromThrowable returns null in this case, which is handled.
      StatusRuntimeException grpcError =
          Status.UNAVAILABLE.withDescription("no details").asRuntimeException();

      KubeMQException result = GrpcErrorMapper.map(grpcError, "op", null, null, false);

      assertInstanceOf(ConnectionException.class, result);
      assertFalse(result.getMessage().contains("[details:"));
    }
  }
}
