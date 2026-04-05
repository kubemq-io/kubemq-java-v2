package io.kubemq.sdk.client;

import io.grpc.stub.StreamObserver;
import kubemq.Kubemq;
import kubemq.kubemqGrpc;

/**
 * Minimal mock gRPC service implementing Ping and SendEvent for InProcessServer tests. Lives in the
 * {@code io.kubemq.sdk.client} package for access to package-private types.
 */
public class MockKubeMQService extends kubemqGrpc.kubemqImplBase {

  /** {@inheritDoc} */
  @Override
  public void ping(Kubemq.Empty request, StreamObserver<Kubemq.PingResult> responseObserver) {
    responseObserver.onNext(
        Kubemq.PingResult.newBuilder().setHost("mock-host").setVersion("mock-1.0.0").build());
    responseObserver.onCompleted();
  }

  /** {@inheritDoc} */
  @Override
  public void sendEvent(Kubemq.Event request, StreamObserver<Kubemq.Result> responseObserver) {
    responseObserver.onNext(
        Kubemq.Result.newBuilder().setEventID(request.getEventID()).setSent(true).build());
    responseObserver.onCompleted();
  }
}
