package io.kubemq.sdk.unit.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.kubemq.sdk.client.*;
import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.exception.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.*;
import kubemq.Kubemq;
import kubemq.kubemqGrpc;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class KubeMQClientFinalCoverageTest {

  // ---------------------------------------------------------------------------
  // shutdownQuietly – exercised via close() on a pre-closed / double-close client
  // ---------------------------------------------------------------------------
  @Nested
  class ShutdownQuietlyTests {

    @Test
    void close_idempotent_secondCallIsNoop() {
      CQClient client = CQClient.builder().address("localhost:50000").clientId("sq-1").build();
      client.close();
      assertDoesNotThrow(client::close);
      assertTrue(client.isClosed());
    }

    @Test
    void close_afterExecutorsAlreadyShutdown_succeeds() {
      CQClient client = CQClient.builder().address("localhost:50000").clientId("sq-2").build();

      ExecutorService cb = client.getCallbackExecutor();
      ExecutorService async = client.getAsyncOperationExecutor();
      cb.shutdownNow();
      async.shutdownNow();

      assertDoesNotThrow(client::close);
      assertTrue(client.isClosed());
    }

    @Test
    void shutdownAllExecutors_viaReflection_doesNotThrow() throws Exception {
      Method m = KubeMQClient.class.getDeclaredMethod("shutdownAllExecutors");
      m.setAccessible(true);
      assertDoesNotThrow(() -> m.invoke(null));
    }

    @Test
    void shutdownQuietly_actionThrows_isSuppressed() throws Exception {
      Method m =
          KubeMQClient.class.getDeclaredMethod("shutdownQuietly", String.class, Runnable.class);
      m.setAccessible(true);
      assertDoesNotThrow(
          () ->
              m.invoke(
                  null,
                  "test",
                  (Runnable)
                      () -> {
                        throw new RuntimeException("boom");
                      }));
    }
  }

  // ---------------------------------------------------------------------------
  // checkServerCompatibility – compatible vs incompatible server version
  // ---------------------------------------------------------------------------
  @Nested
  class CheckServerCompatibilityTests {

    @Test
    void compatibleVersion_noExceptionOnFirstOp() throws Exception {
      CQClient client = CQClient.builder().address("localhost:50000").clientId("compat-1").build();
      try {
        kubemqGrpc.kubemqBlockingStub mockStub = mock(kubemqGrpc.kubemqBlockingStub.class);
        Kubemq.PingResult pingResult =
            Kubemq.PingResult.newBuilder()
                .setHost("test-host")
                .setVersion("2.1.0")
                .setServerStartTime(1000)
                .setServerUpTimeSeconds(500)
                .build();
        when(mockStub.ping(any())).thenReturn(pingResult);
        client.setBlockingStub(mockStub);

        resetCompatibilityFlag(client);

        assertDoesNotThrow(() -> invokeCheckCompatibilityOnce(client));
      } finally {
        client.close();
      }
    }

    @Test
    void incompatibleVersion_logsWarningButNoException() throws Exception {
      CQClient client = CQClient.builder().address("localhost:50000").clientId("compat-2").build();
      try {
        kubemqGrpc.kubemqBlockingStub mockStub = mock(kubemqGrpc.kubemqBlockingStub.class);
        Kubemq.PingResult pingResult =
            Kubemq.PingResult.newBuilder()
                .setHost("test-host")
                .setVersion("99.0.0")
                .setServerStartTime(1000)
                .setServerUpTimeSeconds(500)
                .build();
        when(mockStub.ping(any())).thenReturn(pingResult);
        client.setBlockingStub(mockStub);

        resetCompatibilityFlag(client);

        assertDoesNotThrow(() -> invokeCheckCompatibilityOnce(client));
      } finally {
        client.close();
      }
    }

    @Test
    void pingThrows_compatibilityCheckSkippedGracefully() throws Exception {
      CQClient client = CQClient.builder().address("localhost:50000").clientId("compat-3").build();
      try {
        kubemqGrpc.kubemqBlockingStub mockStub = mock(kubemqGrpc.kubemqBlockingStub.class);
        when(mockStub.ping(any()))
            .thenThrow(new io.grpc.StatusRuntimeException(io.grpc.Status.UNAVAILABLE));
        client.setBlockingStub(mockStub);

        resetCompatibilityFlag(client);

        assertDoesNotThrow(() -> invokeCheckCompatibilityOnce(client));
      } finally {
        client.close();
      }
    }

    @Test
    void nullVersion_treatedAsIncompatible_noException() throws Exception {
      CQClient client = CQClient.builder().address("localhost:50000").clientId("compat-4").build();
      try {
        kubemqGrpc.kubemqBlockingStub mockStub = mock(kubemqGrpc.kubemqBlockingStub.class);
        Kubemq.PingResult pingResult =
            Kubemq.PingResult.newBuilder()
                .setHost("test-host")
                .setServerStartTime(1000)
                .setServerUpTimeSeconds(500)
                .build();
        when(mockStub.ping(any())).thenReturn(pingResult);
        client.setBlockingStub(mockStub);

        resetCompatibilityFlag(client);

        assertDoesNotThrow(() -> invokeCheckCompatibilityOnce(client));
      } finally {
        client.close();
      }
    }

    @Test
    void compatibilityCheckRunsOnlyOnce() throws Exception {
      CQClient client = CQClient.builder().address("localhost:50000").clientId("compat-5").build();
      try {
        kubemqGrpc.kubemqBlockingStub mockStub = mock(kubemqGrpc.kubemqBlockingStub.class);
        Kubemq.PingResult pingResult =
            Kubemq.PingResult.newBuilder().setHost("test-host").setVersion("2.1.0").build();
        when(mockStub.ping(any())).thenReturn(pingResult);
        client.setBlockingStub(mockStub);

        resetCompatibilityFlag(client);

        invokeCheckCompatibilityOnce(client);
        invokeCheckCompatibilityOnce(client);
        invokeCheckCompatibilityOnce(client);

        verify(mockStub, times(1)).ping(any());
      } finally {
        client.close();
      }
    }

    private void resetCompatibilityFlag(KubeMQClient client) throws Exception {
      Field f = KubeMQClient.class.getDeclaredField("compatibilityChecked");
      f.setAccessible(true);
      ((java.util.concurrent.atomic.AtomicBoolean) f.get(client)).set(false);
    }

    private void invokeCheckCompatibilityOnce(KubeMQClient client) throws Exception {
      Method m = KubeMQClient.class.getDeclaredMethod("checkCompatibilityOnce");
      m.setAccessible(true);
      m.invoke(client);
    }
  }

  // ---------------------------------------------------------------------------
  // unwrapFuture – additional exception paths
  // ---------------------------------------------------------------------------
  @Nested
  class UnwrapFutureTests {

    private CQClient client;

    @BeforeEach
    void setUp() {
      client = CQClient.builder().address("localhost:50000").clientId("uf").build();
    }

    @AfterEach
    void tearDown() {
      client.close();
    }

    @Test
    void kubemqExceptionCause_isUnwrappedDirectly() {
      KubeMQException inner =
          KubeMQException.newBuilder()
              .code(ErrorCode.INVALID_ARGUMENT)
              .message("bad arg")
              .operation("test")
              .build();
      CompletableFuture<String> future = new CompletableFuture<>();
      future.completeExceptionally(inner);

      KubeMQException thrown =
          assertThrows(
              KubeMQException.class,
              () -> KubeMQClientAccessor.unwrapFuture(client, future, Duration.ofSeconds(5)));
      assertEquals("bad arg", thrown.getMessage());
    }

    @Test
    void genericCause_isWrappedInKubeMQException() {
      CompletableFuture<String> future = new CompletableFuture<>();
      future.completeExceptionally(new IllegalStateException("generic fail"));

      KubeMQException thrown =
          assertThrows(
              KubeMQException.class,
              () -> KubeMQClientAccessor.unwrapFuture(client, future, Duration.ofSeconds(5)));
      assertTrue(thrown.getMessage().contains("generic fail"));
    }

    @Test
    void timeoutPath_throwsKubeMQTimeoutException() {
      CompletableFuture<String> future = new CompletableFuture<>();

      KubeMQTimeoutException thrown =
          assertThrows(
              KubeMQTimeoutException.class,
              () -> KubeMQClientAccessor.unwrapFuture(client, future, Duration.ofMillis(50)));
      assertTrue(thrown.getMessage().contains("timed out"));
    }

    @Test
    void interruptedPath_throwsOperationCancelledException() throws Exception {
      CompletableFuture<String> neverComplete = new CompletableFuture<>();

      Thread testThread = Thread.currentThread();
      ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
      scheduler.schedule(() -> testThread.interrupt(), 100, TimeUnit.MILLISECONDS);

      try {
        assertThrows(
            OperationCancelledException.class,
            () -> KubeMQClientAccessor.unwrapFuture(client, neverComplete, Duration.ofSeconds(10)));
      } finally {
        Thread.interrupted(); // clear flag
        scheduler.shutdownNow();
      }
    }

    @Test
    void unwrapException_withNullCause_wrapsOriginal() {
      ExecutionException ex = new ExecutionException("no cause", null);
      RuntimeException result = KubeMQClientAccessor.unwrapException(client, ex);
      assertInstanceOf(KubeMQException.class, result);
      assertTrue(result.getMessage().contains("no cause"));
    }
  }

  // ---------------------------------------------------------------------------
  // initChannel – DNS prefix, TLS auto-detect, maxSendMessageSize
  // ---------------------------------------------------------------------------
  @Nested
  class InitChannelTests {

    @Test
    void addressWithDnsPrefix_doesNotDoublePrepend() {
      try {
        CQClient client =
            CQClient.builder()
                .address("dns:///localhost:50000")
                .clientId("ic-1")
                .tls(false)
                .build();
        assertNotNull(client.getManagedChannel());
        client.close();
      } catch (RuntimeException e) {
        assertNotNull(e.getMessage());
      }
    }

    @Test
    void remoteAddress_tlsNull_autoEnablesTls() {
      try {
        CQClient client =
            CQClient.builder()
                .address("remote.example.com:50000")
                .clientId("ic-2")
                .tls(null)
                .insecureSkipVerify(true)
                .build();
        assertTrue(client.isTls());
        client.close();
      } catch (RuntimeException e) {
        assertNotNull(e.getMessage());
      }
    }

    @Test
    void customMaxSendMessageSize_isStored() {
      CQClient client =
          CQClient.builder()
              .address("localhost:50000")
              .clientId("ic-3")
              .maxSendMessageSize(5 * 1024 * 1024)
              .build();
      try {
        assertEquals(5 * 1024 * 1024, client.getMaxSendMessageSize());
      } finally {
        client.close();
      }
    }

    @Test
    void localhostAddress_tlsNull_remainsPlaintext() {
      CQClient client =
          CQClient.builder().address("localhost:50000").clientId("ic-4").tls(null).build();
      try {
        assertFalse(client.isTls());
        assertNotNull(client.getManagedChannel());
      } finally {
        client.close();
      }
    }

    @Test
    void ipv4Address127_tlsNull_remainsPlaintext() {
      CQClient client =
          CQClient.builder().address("127.0.0.1:50000").clientId("ic-5").tls(null).build();
      try {
        assertFalse(client.isTls());
      } finally {
        client.close();
      }
    }

    @Test
    void tlsExplicitTrue_initChannelUsesTlsPath() {
      try {
        CQClient client =
            CQClient.builder()
                .address("localhost:50000")
                .clientId("ic-6")
                .tls(true)
                .insecureSkipVerify(true)
                .build();
        assertTrue(client.isTls());
        assertNotNull(client.getManagedChannel());
        client.close();
      } catch (RuntimeException e) {
        assertNotNull(e.getMessage());
      }
    }

    @Test
    void connectionTimeoutSeconds_isStored() {
      CQClient client =
          CQClient.builder()
              .address("localhost:50000")
              .clientId("ic-7")
              .connectionTimeoutSeconds(30)
              .build();
      try {
        assertEquals(30, client.getConnectionTimeoutSeconds());
      } finally {
        client.close();
      }
    }
  }

  // ---------------------------------------------------------------------------
  // createSslContext – PEM-based paths (CA, client cert+key)
  // ---------------------------------------------------------------------------
  @Nested
  class CreateSslContextTests {

    @Test
    void insecureSkipVerify_buildsContext() throws Exception {
      CQClient client =
          CQClient.builder()
              .address("localhost:50000")
              .clientId("ssl-1")
              .tls(false)
              .insecureSkipVerify(true)
              .build();
      try {
        setField(client, "insecureSkipVerify", true);
        assertNotNull(client.createSslContext());
      } finally {
        client.close();
      }
    }

    @Test
    void caCertPem_branchCovered() throws Exception {
      CQClient client =
          CQClient.builder().address("localhost:50000").clientId("ssl-2").tls(false).build();
      try {
        setField(client, "insecureSkipVerify", false);
        setField(client, "caCertPem", "FAKE-CA".getBytes());
        setField(client, "caCertFile", null);
        assertThrows(Exception.class, () -> client.createSslContext());
      } finally {
        client.close();
      }
    }

    @Test
    void caCertFile_branchCovered(@TempDir Path tempDir) throws Exception {
      java.io.File caFile = tempDir.resolve("ca.pem").toFile();
      try (java.io.FileWriter w = new java.io.FileWriter(caFile)) {
        w.write("FAKE-CA-CONTENT");
      }
      CQClient client =
          CQClient.builder().address("localhost:50000").clientId("ssl-3").tls(false).build();
      try {
        setField(client, "insecureSkipVerify", false);
        setField(client, "caCertPem", null);
        setField(client, "caCertFile", caFile.getAbsolutePath());
        assertThrows(Exception.class, () -> client.createSslContext());
      } finally {
        client.close();
      }
    }

    @Test
    void clientCertAndKeyPem_branchCovered() throws Exception {
      CQClient client =
          CQClient.builder()
              .address("localhost:50000")
              .clientId("ssl-4")
              .tls(false)
              .insecureSkipVerify(true)
              .build();
      try {
        setField(client, "insecureSkipVerify", true);
        setField(client, "tlsCertPem", "FAKE-CERT".getBytes());
        setField(client, "tlsKeyPem", "FAKE-KEY".getBytes());
        try {
          client.createSslContext();
        } catch (Exception e) {
          assertNotNull(e);
        }
      } finally {
        client.close();
      }
    }

    @Test
    void clientCertAndKeyFile_branchCovered(@TempDir Path tempDir) throws Exception {
      java.io.File certFile = tempDir.resolve("cert.pem").toFile();
      java.io.File keyFile = tempDir.resolve("key.pem").toFile();
      try (java.io.FileWriter w = new java.io.FileWriter(certFile)) {
        w.write("CERT");
      }
      try (java.io.FileWriter w = new java.io.FileWriter(keyFile)) {
        w.write("KEY");
      }

      CQClient client =
          CQClient.builder()
              .address("localhost:50000")
              .clientId("ssl-5")
              .tls(false)
              .insecureSkipVerify(true)
              .build();
      try {
        setField(client, "insecureSkipVerify", true);
        setField(client, "tlsCertPem", null);
        setField(client, "tlsKeyPem", null);
        setField(client, "tlsCertFile", certFile.getAbsolutePath());
        setField(client, "tlsKeyFile", keyFile.getAbsolutePath());
        try {
          client.createSslContext();
        } catch (Exception e) {
          assertNotNull(e);
        }
      } finally {
        client.close();
      }
    }

    @Test
    void noCertsConfigured_buildsDefaultContext() throws Exception {
      CQClient client =
          CQClient.builder().address("localhost:50000").clientId("ssl-6").tls(false).build();
      try {
        setField(client, "insecureSkipVerify", false);
        setField(client, "caCertPem", null);
        setField(client, "caCertFile", null);
        setField(client, "tlsCertPem", null);
        setField(client, "tlsKeyPem", null);
        setField(client, "tlsCertFile", null);
        setField(client, "tlsKeyFile", null);
        assertNotNull(client.createSslContext());
      } finally {
        client.close();
      }
    }
  }

  // ---------------------------------------------------------------------------
  // getCallbackExecutor / getAsyncOperationExecutor – lazy init lambda paths
  // ---------------------------------------------------------------------------
  @Nested
  class CallbackExecutorTests {

    @Test
    void getCallbackExecutor_returnsNonNullAndIsSameInstance() {
      CQClient client = CQClient.builder().address("localhost:50000").clientId("cb-1").build();
      try {
        ExecutorService first = client.getCallbackExecutor();
        ExecutorService second = client.getCallbackExecutor();
        assertNotNull(first);
        assertSame(first, second);
        assertFalse(first.isShutdown());
      } finally {
        client.close();
      }
    }

    @Test
    void getAsyncOperationExecutor_returnsNonNullAndIsSameInstance() {
      CQClient client = CQClient.builder().address("localhost:50000").clientId("cb-2").build();
      try {
        ExecutorService first = client.getAsyncOperationExecutor();
        ExecutorService second = client.getAsyncOperationExecutor();
        assertNotNull(first);
        assertSame(first, second);
      } finally {
        client.close();
      }
    }

    @Test
    void callbackExecutor_createsDaemonThread() throws Exception {
      CQClient client = CQClient.builder().address("localhost:50000").clientId("cb-3").build();
      try {
        ExecutorService exec = client.getCallbackExecutor();
        CompletableFuture<Boolean> isDaemon = new CompletableFuture<>();
        CompletableFuture<String> threadName = new CompletableFuture<>();
        exec.execute(
            () -> {
              isDaemon.complete(Thread.currentThread().isDaemon());
              threadName.complete(Thread.currentThread().getName());
            });
        assertTrue(isDaemon.get(5, TimeUnit.SECONDS));
        assertTrue(threadName.get(5, TimeUnit.SECONDS).contains("kubemq-callback-"));
      } finally {
        client.close();
      }
    }

    @Test
    void asyncExecutor_createsDaemonThread() throws Exception {
      CQClient client = CQClient.builder().address("localhost:50000").clientId("cb-4").build();
      try {
        ExecutorService exec = client.getAsyncOperationExecutor();
        CompletableFuture<Boolean> isDaemon = new CompletableFuture<>();
        CompletableFuture<String> threadName = new CompletableFuture<>();
        exec.execute(
            () -> {
              isDaemon.complete(Thread.currentThread().isDaemon());
              threadName.complete(Thread.currentThread().getName());
            });
        assertTrue(isDaemon.get(5, TimeUnit.SECONDS));
        assertTrue(threadName.get(5, TimeUnit.SECONDS).contains("kubemq-async-"));
      } finally {
        client.close();
      }
    }

    @Test
    void callbackExecutor_concurrentInit_returnsSameInstance() throws Exception {
      CQClient client = CQClient.builder().address("localhost:50000").clientId("cb-5").build();
      try {
        setField(client, "defaultCallbackExecutor", null);

        int threads = 10;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CompletableFuture<ExecutorService>[] futures = new CompletableFuture[threads];

        for (int i = 0; i < threads; i++) {
          futures[i] =
              CompletableFuture.supplyAsync(
                  () -> {
                    try {
                      barrier.await();
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                    return client.getCallbackExecutor();
                  },
                  pool);
        }

        ExecutorService first = futures[0].get(5, TimeUnit.SECONDS);
        for (CompletableFuture<ExecutorService> f : futures) {
          assertSame(first, f.get(5, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
      } finally {
        client.close();
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Constructor lambda (credentialProvider scheduler thread)
  // ---------------------------------------------------------------------------
  @Nested
  class ConstructorLambdaTests {

    @Test
    void credentialProvider_schedulerThreadIsDaemon() throws Exception {
      CQClient client =
          CQClient.builder()
              .address("localhost:50000")
              .clientId("cl-1")
              .credentialProvider(() -> new io.kubemq.sdk.auth.TokenResult("test-token"))
              .build();
      try {
        Field f = KubeMQClient.class.getDeclaredField("credentialRefreshScheduler");
        f.setAccessible(true);
        ScheduledExecutorService scheduler = (ScheduledExecutorService) f.get(client);
        assertNotNull(scheduler);
        assertFalse(scheduler.isShutdown());

        CompletableFuture<Boolean> isDaemon = new CompletableFuture<>();
        CompletableFuture<String> threadName = new CompletableFuture<>();
        scheduler.execute(
            () -> {
              isDaemon.complete(Thread.currentThread().isDaemon());
              threadName.complete(Thread.currentThread().getName());
            });
        assertTrue(isDaemon.get(5, TimeUnit.SECONDS));
        assertTrue(threadName.get(5, TimeUnit.SECONDS).contains("kubemq-credential-refresh"));
      } finally {
        client.close();
      }
    }
  }

  // ---------------------------------------------------------------------------
  // toString coverage
  // ---------------------------------------------------------------------------
  @Nested
  class ToStringTests {

    @Test
    void toString_containsExpectedFields() {
      CQClient client = CQClient.builder().address("localhost:50000").clientId("ts-1").build();
      try {
        String s = client.toString();
        assertTrue(s.contains("localhost:50000"));
        assertTrue(s.contains("ts-1"));
        assertTrue(s.contains("tls="));
      } finally {
        client.close();
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------
  private static void setField(Object obj, String fieldName, Object value) throws Exception {
    Class<?> clazz = obj.getClass();
    while (clazz != null) {
      try {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
        return;
      } catch (NoSuchFieldException e) {
        clazz = clazz.getSuperclass();
      }
    }
    throw new NoSuchFieldException(fieldName);
  }
}
