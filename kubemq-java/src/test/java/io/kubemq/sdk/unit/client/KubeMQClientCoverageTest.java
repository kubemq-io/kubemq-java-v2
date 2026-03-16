package io.kubemq.sdk.unit.client;

import static org.junit.jupiter.api.Assertions.*;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.client.KubeMQClientAccessor;
import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.exception.*;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Coverage tests for KubeMQClient methods not covered by other test classes. Uses CQClient as a
 * concrete subclass to test inherited behaviour.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class KubeMQClientCoverageTest {

  private CQClient createClient() {
    return CQClient.builder().address("localhost:50000").clientId("coverage-test").build();
  }

  @Nested
  class IsLocalhostAddressTests {

    @Test
    void localhost_withPort_returnsTrue() {
      assertTrue(KubeMQClient.isLocalhostAddress("localhost:50000"));
    }

    @Test
    void ipv4Loopback_withPort_returnsTrue() {
      assertTrue(KubeMQClient.isLocalhostAddress("127.0.0.1:50000"));
    }

    @Test
    void ipv6Loopback_withoutBrackets_returnsFalse() {
      // "::1" without brackets is ambiguous; the method treats the last ":" as port separator
      assertFalse(KubeMQClient.isLocalhostAddress("::1"));
    }

    @Test
    void ipv6Loopback_withPort_returnsTrue() {
      assertTrue(KubeMQClient.isLocalhostAddress("[::1]:50000"));
    }

    @Test
    void bracketedIpv6Loopback_returnsTrue() {
      assertTrue(KubeMQClient.isLocalhostAddress("[::1]"));
    }

    @Test
    void remoteHost_withPort_returnsFalse() {
      assertFalse(KubeMQClient.isLocalhostAddress("remote.host:50000"));
    }

    @Test
    void nullAddress_returnsFalse() {
      assertFalse(KubeMQClient.isLocalhostAddress(null));
    }

    @Test
    void emptyString_returnsFalse() {
      assertFalse(KubeMQClient.isLocalhostAddress(""));
    }

    @Test
    void localhostUpperCase_returnsTrue() {
      assertTrue(KubeMQClient.isLocalhostAddress("LOCALHOST:50000"));
    }

    @Test
    void ipAddress_notLoopback_returnsFalse() {
      assertFalse(KubeMQClient.isLocalhostAddress("192.168.1.1:50000"));
    }
  }

  @Nested
  class WrapGrpcExceptionTests {

    @Test
    void unauthenticated_returnsAuthenticationException() {
      CQClient client = createClient();
      try {
        StatusRuntimeException grpcEx = new StatusRuntimeException(Status.UNAUTHENTICATED);

        RuntimeException result = client.wrapGrpcException(grpcEx);

        assertInstanceOf(AuthenticationException.class, result);
        assertTrue(
            result.getMessage().contains("authentication")
                || result.getMessage().contains("Authentication"));
      } finally {
        client.close();
      }
    }

    @Test
    void unauthenticated_withTokenSet_mentionsRejected() {
      CQClient client =
          CQClient.builder()
              .address("localhost:50000")
              .clientId("auth-test")
              .authToken("my-token")
              .build();
      try {
        StatusRuntimeException grpcEx = new StatusRuntimeException(Status.UNAUTHENTICATED);

        RuntimeException result = client.wrapGrpcException(grpcEx);

        assertInstanceOf(AuthenticationException.class, result);
        assertTrue(result.getMessage().contains("rejected"));
      } finally {
        client.close();
      }
    }

    @Test
    void unavailable_returnsRuntimeException_notAuth() {
      CQClient client = createClient();
      try {
        StatusRuntimeException grpcEx = new StatusRuntimeException(Status.UNAVAILABLE);

        RuntimeException result = client.wrapGrpcException(grpcEx);

        assertNotNull(result);
        assertFalse(result instanceof AuthenticationException);
      } finally {
        client.close();
      }
    }

    @Test
    void internalError_returnsRuntimeException() {
      CQClient client = createClient();
      try {
        StatusRuntimeException grpcEx = new StatusRuntimeException(Status.INTERNAL);

        RuntimeException result = client.wrapGrpcException(grpcEx);

        assertNotNull(result);
        assertFalse(result instanceof AuthenticationException);
      } finally {
        client.close();
      }
    }
  }

  @Nested
  class ClassifyTlsExceptionTests {

    @Test
    void sslHandshakeException_withCertMessage_returnsAuthenticationException() {
      CQClient client = createClient();
      try {
        SSLHandshakeException ex = new SSLHandshakeException("certificate validation failed");

        RuntimeException result = client.classifyTlsException(ex);

        assertInstanceOf(AuthenticationException.class, result);
        assertTrue(result.getMessage().contains("certificate"));
      } finally {
        client.close();
      }
    }

    @Test
    void sslHandshakeException_withExpiredMessage_returnsAuthenticationException() {
      CQClient client = createClient();
      try {
        SSLHandshakeException ex = new SSLHandshakeException("expired certificate");

        RuntimeException result = client.classifyTlsException(ex);

        assertInstanceOf(AuthenticationException.class, result);
      } finally {
        client.close();
      }
    }

    @Test
    void sslHandshakeException_withProtocolMessage_returnsConfigurationException() {
      CQClient client = createClient();
      try {
        SSLHandshakeException ex = new SSLHandshakeException("no common protocol found");

        RuntimeException result = client.classifyTlsException(ex);

        assertInstanceOf(ConfigurationException.class, result);
        assertTrue(result.getMessage().contains("protocol"));
      } finally {
        client.close();
      }
    }

    @Test
    void sslHandshakeException_withCipherMessage_returnsConfigurationException() {
      CQClient client = createClient();
      try {
        SSLHandshakeException ex = new SSLHandshakeException("cipher suite negotiation failed");

        RuntimeException result = client.classifyTlsException(ex);

        assertInstanceOf(ConfigurationException.class, result);
      } finally {
        client.close();
      }
    }

    @Test
    void sslException_withIOExceptionCause_returnsConnectionException() {
      CQClient client = createClient();
      try {
        SSLException ex =
            new SSLException("ssl failure", new IOException("connection reset by peer"));

        RuntimeException result = client.classifyTlsException(ex);

        assertInstanceOf(ConnectionException.class, result);
        assertTrue(result.getMessage().contains("network error"));
      } finally {
        client.close();
      }
    }

    @Test
    void sslException_withConnectionResetMessage_returnsConnectionException() {
      CQClient client = createClient();
      try {
        SSLException ex = new SSLException("connection reset during handshake");

        RuntimeException result = client.classifyTlsException(ex);

        assertInstanceOf(ConnectionException.class, result);
      } finally {
        client.close();
      }
    }

    @Test
    void sslException_withBrokenPipeMessage_returnsConnectionException() {
      CQClient client = createClient();
      try {
        SSLException ex = new SSLException("broken pipe");

        RuntimeException result = client.classifyTlsException(ex);

        assertInstanceOf(ConnectionException.class, result);
      } finally {
        client.close();
      }
    }

    @Test
    void sslException_withGenericMessage_returnsAuthenticationException() {
      CQClient client = createClient();
      try {
        SSLException ex = new SSLException("unknown TLS error");

        RuntimeException result = client.classifyTlsException(ex);

        assertInstanceOf(AuthenticationException.class, result);
        assertTrue(result.getMessage().contains("TLS handshake failed"));
      } finally {
        client.close();
      }
    }

    @Test
    void sslException_withNullMessage_returnsAuthenticationException() {
      CQClient client = createClient();
      try {
        SSLException ex = new SSLException((String) null);

        RuntimeException result = client.classifyTlsException(ex);

        assertInstanceOf(AuthenticationException.class, result);
      } finally {
        client.close();
      }
    }
  }

  @Nested
  class CloseTests {

    @Test
    void close_isIdempotent_callingTwiceDoesNotThrow() {
      CQClient client = createClient();

      assertDoesNotThrow(
          () -> {
            client.close();
            client.close();
          });
    }

    @Test
    void close_setsClosedFlag() {
      CQClient client = createClient();
      assertFalse(client.isClosed());

      client.close();
      assertTrue(client.isClosed());
    }

    @Test
    void operationAfterClose_throwsClientClosedException() {
      CQClient client = createClient();
      client.close();

      assertThrows(
          ClientClosedException.class,
          () ->
              client.sendCommandRequest(
                  io.kubemq.sdk.cq.CommandMessage.builder()
                      .channel("ch")
                      .body("data".getBytes())
                      .timeoutInSeconds(5)
                      .build()));
    }
  }

  @Nested
  class ExecuteWithCancellationTests {

    @Test
    void successfulExecution_returnsResult() throws Exception {
      CQClient client = createClient();
      try {
        CompletableFuture<String> future =
            KubeMQClientAccessor.executeWithCancellation(client, () -> "hello");

        String result = future.get(5, TimeUnit.SECONDS);
        assertEquals("hello", result);
      } finally {
        client.close();
      }
    }

    @Test
    void supplierThrowsException_futureCompletesExceptionally() {
      CQClient client = createClient();
      try {
        CompletableFuture<String> future =
            KubeMQClientAccessor.executeWithCancellation(
                client,
                () -> {
                  throw new RuntimeException("boom");
                });

        ExecutionException ex =
            assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
        assertTrue(ex.getCause().getMessage().contains("boom"));
      } finally {
        client.close();
      }
    }

    @Test
    void supplierReturnsNull_futureCompletesWithNull() throws Exception {
      CQClient client = createClient();
      try {
        CompletableFuture<String> future =
            KubeMQClientAccessor.executeWithCancellation(client, () -> null);

        String result = future.get(5, TimeUnit.SECONDS);
        assertNull(result);
      } finally {
        client.close();
      }
    }
  }

  @Nested
  class UnwrapFutureTests {

    @Test
    void successfulFuture_returnsValue() {
      CQClient client = createClient();
      try {
        CompletableFuture<String> future = CompletableFuture.completedFuture("value");

        String result = KubeMQClientAccessor.unwrapFuture(client, future, Duration.ofSeconds(5));
        assertEquals("value", result);
      } finally {
        client.close();
      }
    }

    @Test
    void timedOutFuture_throwsKubeMQTimeoutException() {
      CQClient client = createClient();
      try {
        CompletableFuture<String> future = new CompletableFuture<>();

        assertThrows(
            KubeMQTimeoutException.class,
            () -> KubeMQClientAccessor.unwrapFuture(client, future, Duration.ofMillis(50)));
      } finally {
        client.close();
      }
    }

    @Test
    void failedFuture_withKubeMQException_propagatesDirectly() {
      CQClient client = createClient();
      try {
        KubeMQException cause =
            ValidationException.builder().message("bad input").operation("test").build();
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(cause);

        KubeMQException thrown =
            assertThrows(
                KubeMQException.class,
                () -> KubeMQClientAccessor.unwrapFuture(client, future, Duration.ofSeconds(5)));
        assertEquals("bad input", thrown.getMessage());
      } finally {
        client.close();
      }
    }

    @Test
    void failedFuture_withGenericException_wrapsInKubeMQException() {
      CQClient client = createClient();
      try {
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(new IllegalStateException("generic fail"));

        KubeMQException thrown =
            assertThrows(
                KubeMQException.class,
                () -> KubeMQClientAccessor.unwrapFuture(client, future, Duration.ofSeconds(5)));
        assertTrue(thrown.getMessage().contains("generic fail"));
      } finally {
        client.close();
      }
    }
  }

  @Nested
  class UnwrapExceptionTests {

    @Test
    void kubeMQExceptionCause_returnsCauseDirectly() {
      CQClient client = createClient();
      try {
        KubeMQException kubeMQCause =
            ValidationException.builder().message("validation error").operation("test").build();
        ExecutionException ee = new ExecutionException(kubeMQCause);

        RuntimeException result = KubeMQClientAccessor.unwrapException(client, ee);

        assertSame(kubeMQCause, result);
      } finally {
        client.close();
      }
    }

    @Test
    void nonKubeMQExceptionCause_wrapsInKubeMQException() {
      CQClient client = createClient();
      try {
        RuntimeException genericCause = new RuntimeException("generic");
        ExecutionException ee = new ExecutionException(genericCause);

        RuntimeException result = KubeMQClientAccessor.unwrapException(client, ee);

        assertInstanceOf(KubeMQException.class, result);
        assertTrue(result.getMessage().contains("generic"));
      } finally {
        client.close();
      }
    }

    @Test
    void nullCause_wrapsWithExecutionExceptionMessage() {
      CQClient client = createClient();
      try {
        ExecutionException ee = new ExecutionException("exec failed", null);

        RuntimeException result = KubeMQClientAccessor.unwrapException(client, ee);

        assertInstanceOf(KubeMQException.class, result);
        assertNotNull(result.getMessage());
      } finally {
        client.close();
      }
    }
  }

  @Nested
  class EnsureNotClosedTests {

    @Test
    void onOpenClient_doesNotThrow() {
      CQClient client = createClient();
      try {
        assertDoesNotThrow(client::ping);
      } catch (Exception e) {
        // ping may fail for connectivity, but should not throw ClientClosedException
        assertFalse(e instanceof ClientClosedException);
      } finally {
        client.close();
      }
    }
  }

  @Nested
  class ToStringTests {

    @Test
    void toString_containsAddressAndClientId() {
      CQClient client = createClient();
      try {
        String str = client.toString();

        assertTrue(str.contains("localhost:50000"));
        assertTrue(str.contains("coverage-test"));
        assertTrue(str.contains("KubeMQClient"));
      } finally {
        client.close();
      }
    }

    @Test
    void toString_withToken_showsTokenPresent() {
      CQClient client =
          CQClient.builder()
              .address("localhost:50000")
              .clientId("token-test")
              .authToken("secret")
              .build();
      try {
        String str = client.toString();
        assertTrue(str.contains("token_present=true"));
      } finally {
        client.close();
      }
    }

    @Test
    void toString_withoutToken_showsTokenNotPresent() {
      CQClient client = createClient();
      try {
        String str = client.toString();
        assertTrue(str.contains("token_present=false"));
      } finally {
        client.close();
      }
    }
  }

  @Nested
  class SetAuthTokenTests {

    @Test
    void setAuthToken_updatesToken() {
      CQClient client = createClient();
      try {
        assertNull(client.getAuthToken());

        client.setAuthToken("new-token");
        assertEquals("new-token", client.getAuthToken());
      } finally {
        client.close();
      }
    }

    @Test
    void setAuthToken_toNull_clearsToken() {
      CQClient client =
          CQClient.builder()
              .address("localhost:50000")
              .clientId("auth-clear-test")
              .authToken("initial")
              .build();
      try {
        assertEquals("initial", client.getAuthToken());

        client.setAuthToken(null);
        assertNull(client.getAuthToken());
      } finally {
        client.close();
      }
    }
  }

  @Nested
  class ConnectionStateTests {

    @Test
    void getConnectionState_onNewClient_isReady() {
      CQClient client = createClient();
      try {
        assertEquals(io.kubemq.sdk.client.ConnectionState.READY, client.getConnectionState());
      } finally {
        client.close();
      }
    }
  }

  @Nested
  class ExecutorTests {

    @Test
    void getCallbackExecutor_returnsNonNull() {
      CQClient client = createClient();
      try {
        assertNotNull(client.getCallbackExecutor());
      } finally {
        client.close();
      }
    }

    @Test
    void getAsyncOperationExecutor_returnsNonNull() {
      CQClient client = createClient();
      try {
        assertNotNull(client.getAsyncOperationExecutor());
      } finally {
        client.close();
      }
    }

    @Test
    void getCallbackExecutor_returnsSameInstance() {
      CQClient client = createClient();
      try {
        assertSame(client.getCallbackExecutor(), client.getCallbackExecutor());
      } finally {
        client.close();
      }
    }

    @Test
    void getAsyncOperationExecutor_returnsSameInstance() {
      CQClient client = createClient();
      try {
        assertSame(client.getAsyncOperationExecutor(), client.getAsyncOperationExecutor());
      } finally {
        client.close();
      }
    }
  }

  @Nested
  class InFlightOperationsTests {

    @Test
    void getInFlightOperations_returnsAtomicInteger() {
      CQClient client = createClient();
      try {
        assertNotNull(client.getInFlightOperations());
        assertEquals(0, client.getInFlightOperations().get());
      } finally {
        client.close();
      }
    }
  }
}
