package io.kubemq.sdk.testutil;

import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Reusable in-process gRPC server for unit tests. Wraps
 * InProcessServerBuilder/InProcessChannelBuilder for convenience.
 *
 * <p>Usage:
 *
 * <pre>
 * MockGrpcServer server = MockGrpcServer.create(new FakeKubeMQService());
 * ManagedChannel channel = server.getChannel();
 * // ... run tests ...
 * server.close();
 * </pre>
 */
public final class MockGrpcServer implements AutoCloseable {

  private final Server server;
  private final ManagedChannel channel;

  private MockGrpcServer(Server server, ManagedChannel channel) {
    this.server = server;
    this.channel = channel;
  }

  /**
   * Creates and starts an in-process gRPC server with the given service implementations.
   *
   * @param services one or more gRPC service implementations
   * @return a started MockGrpcServer
   * @throws IOException if the server fails to start
   */
  public static MockGrpcServer create(BindableService... services) throws IOException {
    String serverName = InProcessServerBuilder.generateName();
    InProcessServerBuilder builder = InProcessServerBuilder.forName(serverName).directExecutor();
    for (BindableService service : services) {
      builder.addService(service);
    }
    Server server = builder.build().start();
    ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    return new MockGrpcServer(server, channel);
  }

  public ManagedChannel getChannel() {
    return channel;
  }

  public Server getServer() {
    return server;
  }

  @Override
  public void close() throws Exception {
    channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
  }
}
