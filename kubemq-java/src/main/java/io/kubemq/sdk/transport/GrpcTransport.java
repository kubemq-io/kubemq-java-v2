package io.kubemq.sdk.transport;

import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.kubemq.sdk.common.ServerInfo;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import kubemq.Kubemq;
import kubemq.kubemqGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC-based implementation of {@link Transport}. This is the ONLY class in the transport package
 * that creates gRPC channels and stubs.
 *
 * <p>Interceptors (auth, error mapping, etc.) are built internally from {@link TransportConfig} --
 * no gRPC types leak outside this package.
 */
class GrpcTransport implements Transport {

  private static final Logger LOG = LoggerFactory.getLogger(GrpcTransport.class);

  private final TransportConfig config;
  private ManagedChannel managedChannel;
  private kubemqGrpc.kubemqBlockingStub blockingStub;
  private kubemqGrpc.kubemqStub asyncStub;

  GrpcTransport(TransportConfig config) {
    this.config = config;
    initChannel();
  }

  private List<ClientInterceptor> buildInterceptors() {
    List<ClientInterceptor> chain = new ArrayList<>();
    if (config.getTokenSupplier() != null) {
      chain.add(new TransportAuthInterceptor(config.getTokenSupplier()));
    }
    return chain;
  }

  private void initChannel() {
    String resolvedAddress = config.getAddress();
    if (!resolvedAddress.contains("://")) {
      resolvedAddress = "dns:///" + resolvedAddress;
    }

    LOG.debug("GrpcTransport: opening channel to {}", resolvedAddress);

    if (config.isTls()) {
      try {
        NettyChannelBuilder ncb =
            NettyChannelBuilder.forTarget(resolvedAddress)
                .negotiationType(NegotiationType.TLS)
                .maxInboundMessageSize(config.getMaxReceiveSize());

        if (hasContent(config.getServerNameOverride())) {
          ncb.overrideAuthority(config.getServerNameOverride());
        }

        ncb.sslContext(buildSslContext());
        applyKeepAlive(ncb);
        managedChannel = ncb.build();
      } catch (SSLException e) {
        throw io.kubemq.sdk.exception.ConfigurationException.builder()
            .message("TLS configuration error: " + e.getMessage())
            .operation("connect")
            .cause(e)
            .build();
      }
    } else {
      ManagedChannelBuilder<?> mcb =
          ManagedChannelBuilder.forTarget(resolvedAddress)
              .maxInboundMessageSize(config.getMaxReceiveSize())
              .usePlaintext();
      applyKeepAlive(mcb);
      managedChannel = mcb.build();
    }

    List<ClientInterceptor> interceptors = buildInterceptors();
    Channel channel =
        interceptors.isEmpty()
            ? managedChannel
            : ClientInterceptors.intercept(managedChannel, interceptors);
    this.blockingStub = kubemqGrpc.newBlockingStub(channel);
    this.asyncStub = kubemqGrpc.newStub(channel);

    LOG.debug("GrpcTransport: channel initialized, tls={}", config.isTls());
  }

  private void applyKeepAlive(ManagedChannelBuilder<?> builder) {
    int keepaliveTime =
        config.getKeepAliveTimeSeconds() == 0 ? 10 : config.getKeepAliveTimeSeconds();
    int keepaliveTimeout =
        config.getKeepAliveTimeoutSeconds() == 0 ? 5 : config.getKeepAliveTimeoutSeconds();
    builder.keepAliveTime(keepaliveTime, TimeUnit.SECONDS);
    builder.keepAliveTimeout(keepaliveTimeout, TimeUnit.SECONDS);
    builder.keepAliveWithoutCalls(config.isKeepAlive());
  }

  private SslContext buildSslContext() throws SSLException {
    SslContextBuilder sslBuilder = SslContextBuilder.forClient();
    sslBuilder.protocols("TLSv1.3", "TLSv1.2");

    if (config.isInsecureSkipVerify()) {
      LOG.warn("TLS certificate verification is disabled -- insecure; use only in development");
      sslBuilder.trustManager(
          io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE);
    } else {
      if (hasContent(config.getCaCertPem())) {
        sslBuilder.trustManager(new ByteArrayInputStream(config.getCaCertPem()));
      } else if (hasContent(config.getCaCertFile())) {
        sslBuilder.trustManager(new File(config.getCaCertFile()));
      }
    }

    if (hasContent(config.getTlsCertPem()) && hasContent(config.getTlsKeyPem())) {
      sslBuilder.keyManager(
          new ByteArrayInputStream(config.getTlsCertPem()),
          new ByteArrayInputStream(config.getTlsKeyPem()));
    } else if (hasContent(config.getTlsCertFile()) && hasContent(config.getTlsKeyFile())) {
      sslBuilder.keyManager(new File(config.getTlsCertFile()), new File(config.getTlsKeyFile()));
    }

    return sslBuilder.build();
  }

  private static boolean hasContent(byte[] data) {
    return data != null && data.length > 0;
  }

  private static boolean hasContent(String s) {
    return s != null && !s.isEmpty();
  }

  @Override
  public ServerInfo ping() {
    try {
      Kubemq.PingResult result = blockingStub.ping(null);
      return ServerInfo.builder()
          .host(result.getHost())
          .version(result.getVersion())
          .serverStartTime(result.getServerStartTime())
          .serverUpTimeSeconds(result.getServerUpTimeSeconds())
          .build();
    } catch (StatusRuntimeException e) {
      throw io.kubemq.sdk.exception.GrpcErrorMapper.map(e, "ping", null, null, false);
    }
  }

  @Override
  public boolean isReady() {
    return managedChannel != null && !managedChannel.isShutdown() && !managedChannel.isTerminated();
  }

  @Override
  public void close() {
    if (managedChannel != null) {
      try {
        managedChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        if (!managedChannel.isTerminated()) {
          managedChannel.shutdownNow();
        }
      }
    }
    LOG.debug("GrpcTransport: closed");
  }
}
