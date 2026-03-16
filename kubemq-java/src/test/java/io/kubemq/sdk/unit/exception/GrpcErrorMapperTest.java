package io.kubemq.sdk.unit.exception;

import static org.junit.jupiter.api.Assertions.*;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.kubemq.sdk.exception.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for REQ-ERR-6: gRPC error mapping via GrpcErrorMapper. */
class GrpcErrorMapperTest {

  private static StatusRuntimeException grpcError(Status status) {
    return status.withDescription("Test error").asRuntimeException();
  }

  @Nested
  class StatusCodeMappingTests {

    @Test
    void ok_throwsIllegalArgument() {
      assertThrows(
          IllegalArgumentException.class,
          () -> GrpcErrorMapper.map(grpcError(Status.OK), "ping", null, null, false));
    }

    @Test
    void unavailable_mapsToConnectionException() {
      KubeMQException result =
          GrpcErrorMapper.map(grpcError(Status.UNAVAILABLE), "sendEvent", "ch1", "req1", false);

      assertInstanceOf(ConnectionException.class, result);
      assertEquals(ErrorCode.UNAVAILABLE, result.getCode());
      assertEquals(ErrorCategory.TRANSIENT, result.getCategory());
      assertTrue(result.isRetryable());
      assertEquals("sendEvent", result.getOperation());
      assertEquals("ch1", result.getChannel());
      assertEquals("req1", result.getRequestId());
      assertEquals(Status.UNAVAILABLE.getCode().value(), result.getStatusCode());
    }

    @Test
    void unauthenticated_mapsToAuthenticationException() {
      KubeMQException result =
          GrpcErrorMapper.map(
              grpcError(Status.UNAUTHENTICATED), "subscribe", "events", null, false);

      assertInstanceOf(AuthenticationException.class, result);
      assertEquals(ErrorCode.AUTHENTICATION_FAILED, result.getCode());
      assertEquals(ErrorCategory.AUTHENTICATION, result.getCategory());
      assertFalse(result.isRetryable());
    }

    @Test
    void permissionDenied_mapsToAuthorizationException() {
      KubeMQException result =
          GrpcErrorMapper.map(
              grpcError(Status.PERMISSION_DENIED), "send", "restricted", null, false);

      assertInstanceOf(AuthorizationException.class, result);
      assertEquals(ErrorCode.AUTHORIZATION_DENIED, result.getCode());
      assertEquals(ErrorCategory.AUTHORIZATION, result.getCategory());
      assertFalse(result.isRetryable());
    }

    @Test
    void deadlineExceeded_mapsToKubeMQTimeoutException() {
      KubeMQException result =
          GrpcErrorMapper.map(
              grpcError(Status.DEADLINE_EXCEEDED), "sendQuery", "q1", "req1", false);

      assertInstanceOf(KubeMQTimeoutException.class, result);
      assertEquals(ErrorCode.DEADLINE_EXCEEDED, result.getCode());
      assertEquals(ErrorCategory.TIMEOUT, result.getCategory());
      assertTrue(result.isRetryable());
    }

    @Test
    void invalidArgument_mapsToValidationException() {
      KubeMQException result =
          GrpcErrorMapper.map(
              grpcError(Status.INVALID_ARGUMENT), "createChannel", "ch", null, false);

      assertInstanceOf(ValidationException.class, result);
      assertEquals(ErrorCode.INVALID_ARGUMENT, result.getCode());
      assertEquals(ErrorCategory.VALIDATION, result.getCategory());
      assertFalse(result.isRetryable());
    }

    @Test
    void notFound_mapsToKubeMQExceptionWithNotFoundCategory() {
      KubeMQException result =
          GrpcErrorMapper.map(grpcError(Status.NOT_FOUND), "subscribe", "missing", null, false);

      assertEquals(ErrorCode.NOT_FOUND, result.getCode());
      assertEquals(ErrorCategory.NOT_FOUND, result.getCategory());
      assertFalse(result.isRetryable());
    }

    @Test
    void alreadyExists_mapsToValidationException() {
      KubeMQException result =
          GrpcErrorMapper.map(grpcError(Status.ALREADY_EXISTS), "createChannel", "ch", null, false);

      assertInstanceOf(ValidationException.class, result);
      assertEquals(ErrorCode.ALREADY_EXISTS, result.getCode());
    }

    @Test
    void resourceExhausted_mapsToThrottlingException() {
      KubeMQException result =
          GrpcErrorMapper.map(grpcError(Status.RESOURCE_EXHAUSTED), "sendBatch", "ch", null, false);

      assertInstanceOf(ThrottlingException.class, result);
      assertEquals(ErrorCode.RESOURCE_EXHAUSTED, result.getCode());
      assertEquals(ErrorCategory.THROTTLING, result.getCategory());
      assertTrue(result.isRetryable());
    }

    @Test
    void failedPrecondition_mapsToValidationException() {
      KubeMQException result =
          GrpcErrorMapper.map(grpcError(Status.FAILED_PRECONDITION), "op", "ch", null, false);

      assertInstanceOf(ValidationException.class, result);
      assertEquals(ErrorCode.FAILED_PRECONDITION, result.getCode());
    }

    @Test
    void aborted_mapsToConnectionException() {
      KubeMQException result =
          GrpcErrorMapper.map(grpcError(Status.ABORTED), "op", "ch", null, false);

      assertInstanceOf(ConnectionException.class, result);
      assertEquals(ErrorCode.ABORTED, result.getCode());
      assertEquals(ErrorCategory.TRANSIENT, result.getCategory());
      assertTrue(result.isRetryable());
    }

    @Test
    void outOfRange_mapsToValidationException() {
      KubeMQException result =
          GrpcErrorMapper.map(grpcError(Status.OUT_OF_RANGE), "op", null, null, false);

      assertInstanceOf(ValidationException.class, result);
      assertEquals(ErrorCode.OUT_OF_RANGE, result.getCode());
    }

    @Test
    void unimplemented_mapsToServerException() {
      KubeMQException result =
          GrpcErrorMapper.map(grpcError(Status.UNIMPLEMENTED), "op", null, null, false);

      assertInstanceOf(ServerException.class, result);
      assertEquals(ErrorCode.SERVER_UNIMPLEMENTED, result.getCode());
      assertEquals(ErrorCategory.FATAL, result.getCategory());
      assertFalse(result.isRetryable());
    }

    @Test
    void internal_mapsToServerException() {
      KubeMQException result =
          GrpcErrorMapper.map(grpcError(Status.INTERNAL), "op", null, null, false);

      assertInstanceOf(ServerException.class, result);
      assertEquals(ErrorCode.SERVER_INTERNAL, result.getCode());
    }

    @Test
    void dataLoss_mapsToServerException() {
      KubeMQException result =
          GrpcErrorMapper.map(grpcError(Status.DATA_LOSS), "op", null, null, false);

      assertInstanceOf(ServerException.class, result);
      assertEquals(ErrorCode.DATA_LOSS, result.getCode());
    }

    @Test
    void unknown_mapsToConnectionExceptionWithRetry() {
      KubeMQException result =
          GrpcErrorMapper.map(grpcError(Status.UNKNOWN), "op", null, null, false);

      assertInstanceOf(ConnectionException.class, result);
      assertEquals(ErrorCode.UNKNOWN_ERROR, result.getCode());
      assertTrue(result.isRetryable());
    }
  }

  @Nested
  class CancelledStatusTests {

    @Test
    void cancelled_withLocalContext_mapsToOperationCancelled() {
      KubeMQException result =
          GrpcErrorMapper.map(grpcError(Status.CANCELLED), "sendEvent", "ch", null, true);

      assertInstanceOf(OperationCancelledException.class, result);
      assertEquals(ErrorCode.CANCELLED_BY_CLIENT, result.getCode());
      assertEquals(ErrorCategory.CANCELLATION, result.getCategory());
      assertFalse(result.isRetryable());
    }

    @Test
    void cancelled_withoutLocalContext_mapsToConnectionException() {
      KubeMQException result =
          GrpcErrorMapper.map(grpcError(Status.CANCELLED), "sendEvent", "ch", null, false);

      assertInstanceOf(ConnectionException.class, result);
      assertEquals(ErrorCode.CANCELLED_BY_SERVER, result.getCode());
      assertEquals(ErrorCategory.TRANSIENT, result.getCategory());
      assertTrue(result.isRetryable());
    }
  }

  @Nested
  class CauseChainTests {

    @Test
    void originalGrpcException_isPreservedInCause() {
      StatusRuntimeException grpc = grpcError(Status.UNAVAILABLE);
      KubeMQException result = GrpcErrorMapper.map(grpc, "op", null, null, false);

      assertSame(grpc, result.getCause());
    }

    @Test
    void message_containsOperationAndDescription() {
      KubeMQException result =
          GrpcErrorMapper.map(grpcError(Status.UNAVAILABLE), "sendEvent", "orders", null, false);

      assertTrue(result.getMessage().contains("sendEvent"));
      assertTrue(result.getMessage().contains("orders"));
    }
  }

  @Nested
  class ContextPropagationTests {

    @Test
    void operation_isSetOnMappedException() {
      KubeMQException result =
          GrpcErrorMapper.map(grpcError(Status.UNAVAILABLE), "subscribe", "events", null, false);

      assertEquals("subscribe", result.getOperation());
    }

    @Test
    void channel_isSetOnMappedException() {
      KubeMQException result =
          GrpcErrorMapper.map(grpcError(Status.UNAVAILABLE), "op", "my-channel", null, false);

      assertEquals("my-channel", result.getChannel());
    }

    @Test
    void requestId_isSetOnMappedException() {
      KubeMQException result =
          GrpcErrorMapper.map(grpcError(Status.UNAVAILABLE), "op", null, "req-abc", false);

      assertEquals("req-abc", result.getRequestId());
    }

    @Test
    void statusCode_matchesGrpcCode() {
      KubeMQException result =
          GrpcErrorMapper.map(grpcError(Status.UNAVAILABLE), "op", null, null, false);

      assertEquals(14, result.getStatusCode());
    }

    @Test
    void nullChannel_handledGracefully() {
      KubeMQException result =
          GrpcErrorMapper.map(grpcError(Status.INTERNAL), "ping", null, null, false);

      assertNull(result.getChannel());
      assertNotNull(result.getMessage());
    }

    @Test
    void nullRequestId_handledGracefully() {
      KubeMQException result =
          GrpcErrorMapper.map(grpcError(Status.INTERNAL), "ping", null, null, false);

      assertNull(result.getRequestId());
    }
  }
}
