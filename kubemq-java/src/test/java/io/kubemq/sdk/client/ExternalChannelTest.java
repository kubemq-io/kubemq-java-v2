package io.kubemq.sdk.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.QueuesClient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for gRPC channel injection and custom interceptor support. Uses
 * InProcessChannel/InProcessServer for fast unit tests without Docker.
 */
class ExternalChannelTest {

  private Server server;
  private ManagedChannel inProcessChannel;

  @BeforeEach
  void setUp() throws IOException {
    String serverName = InProcessServerBuilder.generateName();
    server =
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(new MockKubeMQService())
            .build()
            .start();
    inProcessChannel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (inProcessChannel != null && !inProcessChannel.isShutdown()) {
      inProcessChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (server != null && !server.isShutdown()) {
      server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  // --- Helper: order-recording interceptor ---
  static class OrderRecordingInterceptor implements ClientInterceptor {
    final List<String> callLog;
    final String name;

    OrderRecordingInterceptor(String name, List<String> callLog) {
      this.name = name;
      this.callLog = callLog;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
        MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
      callLog.add(name);
      return next.newCall(method, callOptions);
    }
  }

  // ========== Build with external channel ==========

  @Nested
  class BuildWithExternalChannel {

    @Test
    void build_pubSubWithExternalChannel_skipsInternalChannelCreation() {
      PubSubClient client =
          PubSubClient.builder().clientId("test-pubsub").grpcChannel(inProcessChannel).build();

      assertSame(inProcessChannel, client.getManagedChannel());
      assertEquals("external-channel", client.getAddress());
      assertTrue(client.isExternalChannel());
      client.close();
    }

    @Test
    void build_queuesClientWithExternalChannel_skipsInternalChannelCreation() {
      QueuesClient client =
          QueuesClient.builder().clientId("test-queues").grpcChannel(inProcessChannel).build();

      assertSame(inProcessChannel, client.getManagedChannel());
      assertEquals("external-channel", client.getAddress());
      assertTrue(client.isExternalChannel());
      client.close();
    }

    @Test
    void build_cqClientWithExternalChannel_skipsInternalChannelCreation() {
      CQClient client =
          CQClient.builder().clientId("test-cq").grpcChannel(inProcessChannel).build();

      assertSame(inProcessChannel, client.getManagedChannel());
      assertEquals("external-channel", client.getAddress());
      assertTrue(client.isExternalChannel());
      client.close();
    }
  }

  // ========== Close lifecycle ==========

  @Nested
  class CloseLifecycle {

    @Test
    void closeWithExternalChannel_doesNotShutdownChannel() {
      PubSubClient client =
          PubSubClient.builder().clientId("test-close").grpcChannel(inProcessChannel).build();

      client.close();

      // External channel should NOT be shut down
      assertFalse(inProcessChannel.isShutdown());
      assertFalse(inProcessChannel.isTerminated());
    }

    @Test
    void closeWithExternalChannel_idempotent() {
      PubSubClient client =
          PubSubClient.builder().clientId("test-close-idem").grpcChannel(inProcessChannel).build();

      client.close();
      client.close(); // second close should not throw

      assertFalse(inProcessChannel.isShutdown());
    }
  }

  // ========== Interceptors ==========

  @Nested
  class InterceptorTests {

    @Test
    void buildWithNullInterceptors_behavesAsDefault() {
      PubSubClient client =
          PubSubClient.builder()
              .clientId("test-null-interceptors")
              .grpcChannel(inProcessChannel)
              .interceptors(null)
              .build();

      // Should work -- only AuthInterceptor applied
      assertNotNull(client.getClient());
      assertTrue(client.getUserInterceptors().isEmpty());
      client.close();
    }

    @Test
    void buildWithEmptyInterceptors_behavesAsDefault() {
      PubSubClient client =
          PubSubClient.builder()
              .clientId("test-empty-interceptors")
              .grpcChannel(inProcessChannel)
              .interceptors(Collections.emptyList())
              .build();

      assertNotNull(client.getClient());
      assertTrue(client.getUserInterceptors().isEmpty());
      client.close();
    }

    @Test
    void buildWithInterceptors_interceptorsAppliedInOrder() {
      List<String> callLog = new ArrayList<>();
      OrderRecordingInterceptor userInterceptor1 = new OrderRecordingInterceptor("user-1", callLog);
      OrderRecordingInterceptor userInterceptor2 = new OrderRecordingInterceptor("user-2", callLog);

      PubSubClient client =
          PubSubClient.builder()
              .clientId("test-order")
              .grpcChannel(inProcessChannel)
              .interceptors(List.of(userInterceptor1, userInterceptor2))
              .build();

      // Trigger a gRPC call to exercise the interceptors
      try {
        client.ping();
      } catch (Exception ignored) {
        // We care about the interceptor call order, not the result
      }

      // User interceptors should appear in callLog
      // gRPC makes LAST interceptor in list outermost, so user-2 executes first, then user-1
      // AuthInterceptor is not an OrderRecordingInterceptor so it won't appear in callLog
      assertTrue(callLog.size() >= 2);
      assertEquals("user-2", callLog.get(0)); // outermost (last in list) executes first
      assertEquals("user-1", callLog.get(1));
      client.close();
    }
  }

  // ========== Warning and edge case tests ==========

  @Nested
  class WarningAndEdgeCaseTests {

    @Test
    void buildWithGrpcChannelAndAddress_logsWarning() {
      // Should not throw; grpcChannel takes precedence
      PubSubClient client =
          PubSubClient.builder()
              .clientId("test-both")
              .grpcChannel(inProcessChannel)
              .address("localhost:50000")
              .build();

      // grpcChannel takes precedence but address is stored for logging
      assertSame(inProcessChannel, client.getManagedChannel());
      assertEquals("localhost:50000", client.getAddress());
      client.close();
    }

    @Test
    void buildWithGrpcChannelAndTlsParams_logsWarning() {
      // Should not throw; TLS params are ignored
      PubSubClient client =
          PubSubClient.builder()
              .clientId("test-tls-ignored")
              .grpcChannel(inProcessChannel)
              .tls(true)
              .build();

      assertSame(inProcessChannel, client.getManagedChannel());
      client.close();
    }

    @Test
    void buildWithGrpcChannelNoAddress_setsPlaceholder() {
      PubSubClient client =
          PubSubClient.builder().clientId("test-no-addr").grpcChannel(inProcessChannel).build();

      assertEquals("external-channel", client.getAddress());
      client.close();
    }
  }

  // ========== InProcess round-trip integration tests ==========

  @Nested
  class InProcessRoundTrip {

    @Test
    void inProcessRoundTrip_pubSubClient() {
      PubSubClient client =
          PubSubClient.builder()
              .clientId("test-roundtrip-pubsub")
              .grpcChannel(inProcessChannel)
              .build();

      // Ping should succeed via InProcessChannel
      var pingResult = client.ping();
      assertNotNull(pingResult);

      client.close();
      assertFalse(inProcessChannel.isShutdown());
    }

    @Test
    void inProcessRoundTrip_queuesClient() {
      QueuesClient client =
          QueuesClient.builder()
              .clientId("test-roundtrip-queues")
              .grpcChannel(inProcessChannel)
              .build();

      var pingResult = client.ping();
      assertNotNull(pingResult);

      client.close();
      assertFalse(inProcessChannel.isShutdown());
    }

    @Test
    void inProcessRoundTrip_cqClient() {
      CQClient client =
          CQClient.builder().clientId("test-roundtrip-cq").grpcChannel(inProcessChannel).build();

      var pingResult = client.ping();
      assertNotNull(pingResult);

      client.close();
      assertFalse(inProcessChannel.isShutdown());
    }
  }

  // ========== External channel state transition ==========

  @Nested
  class ExternalChannelStateTransition {

    @Test
    void externalChannelShutdown_transitionsToClosedState() throws Exception {
      // Use a separate channel that we control for shutdown testing
      String serverName = InProcessServerBuilder.generateName();
      Server localServer =
          InProcessServerBuilder.forName(serverName)
              .directExecutor()
              .addService(new MockKubeMQService())
              .build()
              .start();
      ManagedChannel localChannel =
          InProcessChannelBuilder.forName(serverName).directExecutor().build();

      CountDownLatch closedLatch = new CountDownLatch(1);
      ConnectionStateListener listener =
          new ConnectionStateListener() {
            @Override
            public void onClosed() {
              closedLatch.countDown();
            }
          };

      PubSubClient client =
          PubSubClient.builder()
              .clientId("test-shutdown-state")
              .grpcChannel(localChannel)
              .connectionStateListener(listener)
              .build();

      // Verify client is initially functional
      assertNotNull(client.ping());

      // Shut down the channel externally
      localChannel.shutdownNow();
      localChannel.awaitTermination(5, TimeUnit.SECONDS);

      // Wait for the async state monitor to detect SHUTDOWN and transition to CLOSED
      assertTrue(
          closedLatch.await(5, TimeUnit.SECONDS),
          "Expected onClosed() to be called after external channel SHUTDOWN");

      // Subsequent operations should fail with ClientClosedException
      assertThrows(RuntimeException.class, () -> client.ping());

      client.close(); // Idempotent close -- should not throw
      localServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  // ========== validateOnBuild with external channel ==========

  @Nested
  class ValidateOnBuildWithExternalChannel {

    @Test
    void buildWithGrpcChannelAndValidateOnBuild_failsIfChannelNotConnected() {
      // Create a channel with no matching server -- channel is not connected
      String orphanName = InProcessServerBuilder.generateName();
      ManagedChannel deadChannel =
          InProcessChannelBuilder.forName(orphanName).directExecutor().build();

      // validateOnBuild=true should attempt ping and fail
      assertThrows(
          RuntimeException.class,
          () ->
              PubSubClient.builder()
                  .clientId("test-validate-dead")
                  .grpcChannel(deadChannel)
                  .validateOnBuild(true)
                  .build());

      deadChannel.shutdownNow();
    }
  }
}
