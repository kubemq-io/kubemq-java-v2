package io.kubemq.sdk.unit.exception;

import static org.junit.jupiter.api.Assertions.*;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.kubemq.sdk.exception.*;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Parameterized test for gRPC status code → SDK error classification. Uses @EnumSource to verify
 * all 17 gRPC status codes (0-16) are mapped to correct ErrorCategory and retryable flag per
 * REQ-ERR-6.
 *
 * <p>Complements the per-code assertions in GrpcErrorMapperTest by providing a single parameterized
 * sweep that catches missed codes automatically.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ErrorClassificationParameterizedTest {

  private static final Set<Status.Code> RETRYABLE_CODES =
      EnumSet.of(
          Status.Code.UNAVAILABLE,
          Status.Code.DEADLINE_EXCEEDED,
          Status.Code.RESOURCE_EXHAUSTED,
          Status.Code.ABORTED,
          Status.Code.UNKNOWN,
          Status.Code.INTERNAL);

  @ParameterizedTest(name = "gRPC {0} → non-null category, correct retryable flag")
  @EnumSource(
      value = Status.Code.class,
      mode = EnumSource.Mode.EXCLUDE,
      names = {"OK"})
  void grpcStatusCode_mapsToNonNullCategoryWithCorrectRetryable(Status.Code code) {
    StatusRuntimeException grpcError =
        Status.fromCode(code).withDescription("test error for " + code).asRuntimeException();

    KubeMQException exception =
        GrpcErrorMapper.map(grpcError, "testOp", "testChannel", "req-1", false);

    assertNotNull(exception, "Mapped exception must not be null for " + code);
    assertNotNull(
        exception.getCategory(), "gRPC code " + code + " must map to a non-null ErrorCategory");
    assertNotNull(exception.getCode(), "gRPC code " + code + " must have a non-null ErrorCode");

    if (code == Status.Code.CANCELLED) {
      // CANCELLED with localContextCancelled=false → retryable
      assertTrue(exception.isRetryable(), code + " (server-cancelled) must be retryable");
    } else if (RETRYABLE_CODES.contains(code)) {
      assertTrue(exception.isRetryable(), code + " must be retryable");
    } else {
      assertFalse(exception.isRetryable(), code + " must NOT be retryable");
    }
  }

  @ParameterizedTest(name = "gRPC {0} → original exception preserved in cause chain")
  @EnumSource(
      value = Status.Code.class,
      mode = EnumSource.Mode.EXCLUDE,
      names = {"OK"})
  void grpcStatusCode_preservesOriginalExceptionInCause(Status.Code code) {
    StatusRuntimeException grpcError =
        Status.fromCode(code).withDescription("cause-chain test").asRuntimeException();

    KubeMQException exception = GrpcErrorMapper.map(grpcError, "testOp", null, null, false);

    assertSame(
        grpcError, exception.getCause(), "Original gRPC exception must be preserved for " + code);
  }

  @ParameterizedTest(name = "gRPC {0} → statusCode matches numeric value")
  @EnumSource(
      value = Status.Code.class,
      mode = EnumSource.Mode.EXCLUDE,
      names = {"OK"})
  void grpcStatusCode_numericValueMatchesGrpcCode(Status.Code code) {
    StatusRuntimeException grpcError =
        Status.fromCode(code).withDescription("numeric test").asRuntimeException();

    KubeMQException exception = GrpcErrorMapper.map(grpcError, "testOp", null, null, false);

    assertEquals(
        code.value(),
        exception.getStatusCode(),
        "statusCode must match gRPC numeric value for " + code);
  }

  @ParameterizedTest(name = "gRPC {0} → context fields propagated")
  @EnumSource(
      value = Status.Code.class,
      mode = EnumSource.Mode.EXCLUDE,
      names = {"OK"})
  void grpcStatusCode_contextFieldsPropagated(Status.Code code) {
    StatusRuntimeException grpcError =
        Status.fromCode(code).withDescription("context test").asRuntimeException();

    KubeMQException exception = GrpcErrorMapper.map(grpcError, "myOp", "myChannel", "myReq", false);

    assertEquals("myOp", exception.getOperation());
    assertEquals("myChannel", exception.getChannel());
    assertEquals("myReq", exception.getRequestId());
  }
}
