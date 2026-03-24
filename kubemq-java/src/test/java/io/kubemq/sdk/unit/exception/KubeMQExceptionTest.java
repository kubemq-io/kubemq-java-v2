package io.kubemq.sdk.unit.exception;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.exception.*;
import java.io.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for REQ-ERR-1: Typed exception hierarchy. */
class KubeMQExceptionTest {

  @Nested
  class BaseExceptionTests {

    @Test
    void builder_setsAllFields() {
      Instant now = Instant.now();
      RuntimeException cause = new RuntimeException("root");

      KubeMQException ex =
          KubeMQException.newBuilder()
              .code(ErrorCode.CONNECTION_FAILED)
              .message("Connection refused")
              .operation("sendEvent")
              .channel("test-channel")
              .retryable(true)
              .requestId("req-123")
              .messageId("msg-456")
              .statusCode(14)
              .timestamp(now)
              .serverAddress("localhost:50000")
              .category(ErrorCategory.TRANSIENT)
              .cause(cause)
              .build();

      assertEquals(ErrorCode.CONNECTION_FAILED, ex.getCode());
      assertEquals("Connection refused", ex.getMessage());
      assertEquals("sendEvent", ex.getOperation());
      assertEquals("test-channel", ex.getChannel());
      assertTrue(ex.isRetryable());
      assertEquals("req-123", ex.getRequestId());
      assertEquals("msg-456", ex.getMessageId());
      assertEquals(14, ex.getStatusCode());
      assertEquals(now, ex.getTimestamp());
      assertEquals("localhost:50000", ex.getServerAddress());
      assertEquals(ErrorCategory.TRANSIENT, ex.getCategory());
      assertEquals(cause, ex.getCause());
    }

    @Test
    void builder_defaultValues() {
      KubeMQException ex = KubeMQException.newBuilder().message("test").build();

      assertNull(ex.getCode());
      assertNull(ex.getOperation());
      assertNull(ex.getChannel());
      assertFalse(ex.isRetryable());
      assertNull(ex.getRequestId());
      assertNull(ex.getMessageId());
      assertEquals(-1, ex.getStatusCode());
      assertNotNull(ex.getTimestamp());
      assertNull(ex.getServerAddress());
      assertNull(ex.getCategory());
      assertNull(ex.getCause());
    }

    @Test
    void timestamp_defaultsToNow() {
      Instant before = Instant.now();
      KubeMQException ex = KubeMQException.newBuilder().message("test").build();
      Instant after = Instant.now();

      assertFalse(ex.getTimestamp().isBefore(before));
      assertFalse(ex.getTimestamp().isAfter(after));
    }

    @Test
    void extendsRuntimeException() {
      KubeMQException ex = KubeMQException.newBuilder().message("test").build();

      assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void implementsSerializable() {
      KubeMQException ex = KubeMQException.newBuilder().message("test").build();

      assertInstanceOf(Serializable.class, ex);
    }

    @Test
    void serialization_roundTrip() throws Exception {
      KubeMQException original =
          KubeMQException.newBuilder()
              .code(ErrorCode.CONNECTION_FAILED)
              .message("test serialization")
              .operation("ping")
              .channel("ch1")
              .retryable(true)
              .statusCode(14)
              .category(ErrorCategory.TRANSIENT)
              .build();

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(original);
      oos.close();

      ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
      KubeMQException deserialized = (KubeMQException) ois.readObject();

      assertEquals(original.getCode(), deserialized.getCode());
      assertEquals(original.getMessage(), deserialized.getMessage());
      assertEquals(original.getOperation(), deserialized.getOperation());
      assertEquals(original.getChannel(), deserialized.getChannel());
      assertEquals(original.isRetryable(), deserialized.isRetryable());
      assertEquals(original.getStatusCode(), deserialized.getStatusCode());
      assertEquals(original.getCategory(), deserialized.getCategory());
    }

    @Test
    void toString_includesKeyFields() {
      KubeMQException ex =
          KubeMQException.newBuilder()
              .code(ErrorCode.CONNECTION_FAILED)
              .message("Connection refused")
              .operation("sendEvent")
              .channel("test-channel")
              .retryable(true)
              .requestId("req-123")
              .statusCode(14)
              .build();

      String str = ex.toString();
      assertTrue(str.contains("KubeMQException"));
      assertTrue(str.contains("CONNECTION_FAILED"));
      assertTrue(str.contains("sendEvent"));
      assertTrue(str.contains("test-channel"));
      assertTrue(str.contains("retryable=true"));
      assertTrue(str.contains("req-123"));
      assertTrue(str.contains("grpcStatus=14"));
      assertTrue(str.contains("Connection refused"));
    }

    @Test
    void toString_omitsNullFields() {
      KubeMQException ex =
          KubeMQException.newBuilder()
              .code(ErrorCode.UNKNOWN_ERROR)
              .message("simple error")
              .build();

      String str = ex.toString();
      assertFalse(str.contains("operation="));
      assertFalse(str.contains("channel="));
      assertFalse(str.contains("requestId="));
    }

    @Test
    void canBeCaughtAsRuntimeException() {
      assertThrows(
          RuntimeException.class,
          () -> {
            throw KubeMQException.newBuilder().message("test").build();
          });
    }
  }

  @Nested
  class ConnectionExceptionTests {

    @Test
    void defaultsSetCorrectly() {
      ConnectionException ex = ConnectionException.builder().message("Server down").build();

      assertEquals(ErrorCode.CONNECTION_FAILED, ex.getCode());
      assertEquals(ErrorCategory.TRANSIENT, ex.getCategory());
      assertTrue(ex.isRetryable());
      assertInstanceOf(KubeMQException.class, ex);
    }

    @Test
    void overrideDefaults() {
      ConnectionException ex =
          ConnectionException.builder()
              .code(ErrorCode.CONNECTION_CLOSED)
              .message("Closed")
              .retryable(false)
              .build();

      assertEquals(ErrorCode.CONNECTION_CLOSED, ex.getCode());
      assertFalse(ex.isRetryable());
    }
  }

  @Nested
  class AuthenticationExceptionTests {

    @Test
    void defaultsSetCorrectly() {
      AuthenticationException ex =
          AuthenticationException.builder().message("Invalid token").build();

      assertEquals(ErrorCode.AUTHENTICATION_FAILED, ex.getCode());
      assertEquals(ErrorCategory.AUTHENTICATION, ex.getCategory());
      assertFalse(ex.isRetryable());
      assertInstanceOf(KubeMQException.class, ex);
    }
  }

  @Nested
  class AuthorizationExceptionTests {

    @Test
    void defaultsSetCorrectly() {
      AuthorizationException ex =
          AuthorizationException.builder().message("Permission denied").build();

      assertEquals(ErrorCode.AUTHORIZATION_DENIED, ex.getCode());
      assertEquals(ErrorCategory.AUTHORIZATION, ex.getCategory());
      assertFalse(ex.isRetryable());
    }
  }

  @Nested
  class KubeMQTimeoutExceptionTests {

    @Test
    void defaultsSetCorrectly() {
      KubeMQTimeoutException ex =
          KubeMQTimeoutException.builder().message("Operation timed out").build();

      assertEquals(ErrorCode.OPERATION_TIMEOUT, ex.getCode());
      assertEquals(ErrorCategory.TIMEOUT, ex.getCategory());
      assertTrue(ex.isRetryable());
      assertInstanceOf(KubeMQException.class, ex);
    }

    @Test
    void doesNotClashWithJdkTimeoutException() {
      assertNotEquals(java.util.concurrent.TimeoutException.class, KubeMQTimeoutException.class);
    }
  }

  @Nested
  class ValidationExceptionTests {

    @Test
    void defaultsSetCorrectly() {
      ValidationException ex = ValidationException.builder().message("Invalid argument").build();

      assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getCode());
      assertEquals(ErrorCategory.VALIDATION, ex.getCategory());
      assertFalse(ex.isRetryable());
    }
  }

  @Nested
  class ServerExceptionTests {

    @Test
    void defaultsSetCorrectly() {
      ServerException ex = ServerException.builder().message("Internal error").build();

      assertEquals(ErrorCode.SERVER_INTERNAL, ex.getCode());
      assertEquals(ErrorCategory.FATAL, ex.getCategory());
      assertFalse(ex.isRetryable());
    }
  }

  @Nested
  class ThrottlingExceptionTests {

    @Test
    void defaultsSetCorrectly() {
      ThrottlingException ex = ThrottlingException.builder().message("Rate limited").build();

      assertEquals(ErrorCode.RESOURCE_EXHAUSTED, ex.getCode());
      assertEquals(ErrorCategory.THROTTLING, ex.getCategory());
      assertTrue(ex.isRetryable());
    }
  }

  @Nested
  class HandlerExceptionTests {

    @Test
    void defaultsSetCorrectly() {
      HandlerException ex = HandlerException.builder().message("Handler threw NPE").build();

      assertEquals(ErrorCode.HANDLER_ERROR, ex.getCode());
      assertEquals(ErrorCategory.FATAL, ex.getCategory());
      assertFalse(ex.isRetryable());
    }

    @Test
    void wrapsOriginalCause() {
      NullPointerException npe = new NullPointerException("oops");
      HandlerException ex =
          HandlerException.builder().message("User callback failed").cause(npe).build();

      assertEquals(npe, ex.getCause());
    }
  }

  @Nested
  class StreamBrokenExceptionTests {

    @Test
    void defaultsSetCorrectly() {
      StreamBrokenException ex = StreamBrokenException.builder().message("Stream lost").build();

      assertEquals(ErrorCode.STREAM_BROKEN, ex.getCode());
      assertEquals(ErrorCategory.TRANSIENT, ex.getCategory());
      assertTrue(ex.isRetryable());
    }

    @Test
    void withUnacknowledgedMessageIds() {
      List<String> ids = Arrays.asList("msg-1", "msg-2", "msg-3");
      StreamBrokenException ex =
          StreamBrokenException.builder()
              .message("Stream broken with in-flight messages")
              .unacknowledgedMessageIds(ids)
              .build();

      assertEquals(ids, ex.getUnacknowledgedMessageIds());
    }

    @Test
    void unacknowledgedMessageIds_defaultsToEmptyList() {
      StreamBrokenException ex = StreamBrokenException.builder().message("Stream broken").build();

      assertNotNull(ex.getUnacknowledgedMessageIds());
      assertTrue(ex.getUnacknowledgedMessageIds().isEmpty());
    }

    @Test
    void unacknowledgedMessageIds_areImmutable() {
      List<String> ids = Arrays.asList("msg-1");
      StreamBrokenException ex =
          StreamBrokenException.builder().message("test").unacknowledgedMessageIds(ids).build();

      assertThrows(
          UnsupportedOperationException.class, () -> ex.getUnacknowledgedMessageIds().add("msg-2"));
    }
  }

  @Nested
  class TransportExceptionTests {

    @Test
    void defaultsSetCorrectly() {
      TransportException ex = TransportException.builder().message("Transport failure").build();

      assertEquals(ErrorCode.CONNECTION_FAILED, ex.getCode());
      assertEquals(ErrorCategory.TRANSIENT, ex.getCategory());
      assertTrue(ex.isRetryable());
    }
  }

  @Nested
  class BackpressureExceptionTests {

    @Test
    void defaultsSetCorrectly() {
      BackpressureException ex = BackpressureException.builder().message("Buffer full").build();

      assertEquals(ErrorCode.BUFFER_FULL, ex.getCode());
      assertEquals(ErrorCategory.BACKPRESSURE, ex.getCategory());
      assertFalse(ex.isRetryable());
    }
  }

  @Nested
  class OperationCancelledExceptionTests {

    @Test
    void defaultsSetCorrectly() {
      OperationCancelledException ex =
          OperationCancelledException.builder().message("Cancelled").build();

      assertEquals(ErrorCode.CANCELLED_BY_CLIENT, ex.getCode());
      assertEquals(ErrorCategory.CANCELLATION, ex.getCategory());
      assertFalse(ex.isRetryable());
    }
  }

  @Nested
  class RetryThrottledExceptionTests {

    @Test
    void defaultsSetCorrectly() {
      RetryThrottledException ex = RetryThrottledException.builder().message("Throttled").build();

      assertEquals(ErrorCode.RETRY_THROTTLED, ex.getCode());
      assertEquals(ErrorCategory.BACKPRESSURE, ex.getCategory());
      assertFalse(ex.isRetryable());
    }
  }

  @Nested
  class PartialFailureExceptionTests {

    @Test
    void defaultsSetCorrectly() {
      PartialFailureException ex =
          PartialFailureException.builder().message("Partial failure").build();

      assertEquals(ErrorCode.PARTIAL_FAILURE, ex.getCode());
      assertEquals(ErrorCategory.TRANSIENT, ex.getCategory());
      assertFalse(ex.isRetryable());
    }

    @Test
    void withPerMessageErrors() {
      Map<String, KubeMQException> errors = new HashMap<>();
      errors.put("msg-1", ConnectionException.builder().message("Failed").build());

      PartialFailureException ex =
          PartialFailureException.builder()
              .message("Batch partially failed")
              .perMessageErrors(errors)
              .build();

      assertEquals(1, ex.getPerMessageErrors().size());
      assertNotNull(ex.getPerMessageErrors().get("msg-1"));
    }

    @Test
    void perMessageErrors_defaultsToEmptyMap() {
      PartialFailureException ex = PartialFailureException.builder().message("test").build();

      assertNotNull(ex.getPerMessageErrors());
      assertTrue(ex.getPerMessageErrors().isEmpty());
    }

    @Test
    void perMessageErrors_areImmutable() {
      PartialFailureException ex =
          PartialFailureException.builder()
              .message("test")
              .perMessageErrors(new HashMap<>())
              .build();

      assertThrows(
          UnsupportedOperationException.class, () -> ex.getPerMessageErrors().put("k", null));
    }
  }

  @Nested
  class HierarchyTests {

    @Test
    void allSubclasses_extendKubeMQException() {
      assertInstanceOf(KubeMQException.class, ConnectionException.builder().message("t").build());
      assertInstanceOf(
          KubeMQException.class, AuthenticationException.builder().message("t").build());
      assertInstanceOf(
          KubeMQException.class, AuthorizationException.builder().message("t").build());
      assertInstanceOf(
          KubeMQException.class, KubeMQTimeoutException.builder().message("t").build());
      assertInstanceOf(KubeMQException.class, ValidationException.builder().message("t").build());
      assertInstanceOf(KubeMQException.class, ServerException.builder().message("t").build());
      assertInstanceOf(KubeMQException.class, ThrottlingException.builder().message("t").build());
      assertInstanceOf(KubeMQException.class, HandlerException.builder().message("t").build());
      assertInstanceOf(KubeMQException.class, StreamBrokenException.builder().message("t").build());
      assertInstanceOf(KubeMQException.class, TransportException.builder().message("t").build());
      assertInstanceOf(KubeMQException.class, BackpressureException.builder().message("t").build());
      assertInstanceOf(
          KubeMQException.class, OperationCancelledException.builder().message("t").build());
      assertInstanceOf(
          KubeMQException.class, RetryThrottledException.builder().message("t").build());
      assertInstanceOf(
          KubeMQException.class, PartialFailureException.builder().message("t").build());
    }

    @Test
    void allSubclasses_extendRuntimeException() {
      assertInstanceOf(RuntimeException.class, ConnectionException.builder().message("t").build());
      assertInstanceOf(
          RuntimeException.class, AuthenticationException.builder().message("t").build());
      assertInstanceOf(
          RuntimeException.class, KubeMQTimeoutException.builder().message("t").build());
    }

    @Test
    void allSubclasses_canBeCaughtAsKubeMQException() {
      assertThrows(
          KubeMQException.class,
          () -> {
            throw ConnectionException.builder().message("test").build();
          });
      assertThrows(
          KubeMQException.class,
          () -> {
            throw AuthenticationException.builder().message("test").build();
          });
      assertThrows(
          KubeMQException.class,
          () -> {
            throw KubeMQTimeoutException.builder().message("test").build();
          });
    }

    @Test
    void legacyExceptions_extendKubeMQException() {
      assertInstanceOf(KubeMQException.class, new GRPCException("test"));
      assertInstanceOf(KubeMQException.class, new CreateChannelException("test"));
      assertInstanceOf(KubeMQException.class, new DeleteChannelException("test"));
      assertInstanceOf(KubeMQException.class, new ListChannelsException("test"));
    }
  }
}
