package io.kubemq.sdk.transport;

import io.kubemq.sdk.common.ServerInfo;

/**
 * Defines the low-level communication contract with the KubeMQ server. This is the only layer that
 * imports gRPC packages.
 *
 * <p>Package-private: not part of the public API. Clients interact with the SDK through {@code
 * PubSubClient}, {@code QueuesClient}, and {@code CQClient}, which delegate to this interface
 * internally.
 */
interface Transport extends AutoCloseable {

  /**
   * Ping the server and return its metadata.
   *
   * @return server information
   */
  ServerInfo ping();

  /**
   * Check whether the transport is connected and ready to send.
   *
   * @return true if connected
   */
  boolean isReady();

  /** Shut down the transport, releasing all resources. */
  @Override
  void close();
}
