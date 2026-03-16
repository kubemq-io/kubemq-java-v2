package io.kubemq.sdk.transport;

import java.util.function.Supplier;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Configuration for the transport layer. Contains no gRPC types -- pure Java configuration.
 *
 * <p>The {@code tokenSupplier} enables dynamic token retrieval per-call without leaking gRPC's
 * {@code ClientInterceptor} type into the public API.
 */
@Getter
@Builder
@ToString(exclude = {"tokenSupplier"})
public class TransportConfig {

  private final String address;
  private final boolean tls;
  private final String tlsCertFile;
  private final String tlsKeyFile;
  private final String caCertFile;
  private final byte[] caCertPem;
  private final byte[] tlsCertPem;
  private final byte[] tlsKeyPem;
  private final String serverNameOverride;
  private final boolean insecureSkipVerify;
  private final int maxReceiveSize;
  private final int maxSendMessageSize;
  private final boolean keepAlive;
  private final int keepAliveTimeSeconds;
  private final int keepAliveTimeoutSeconds;
  private final int connectionTimeoutSeconds;

  /**
   * Supplier for the current auth token. Called per-gRPC-call by the internally-created
   * AuthInterceptor. Returns null/empty if no token.
   */
  private final Supplier<String> tokenSupplier;
}
