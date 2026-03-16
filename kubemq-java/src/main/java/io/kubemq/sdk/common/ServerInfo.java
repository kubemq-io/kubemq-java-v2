package io.kubemq.sdk.common;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Reports the host, version, uptime, and capabilities of a connected KubeMQ server, returned by
 * ping operations.
 *
 * <p>Attributes:
 *
 * <ul>
 *   <li>host: The host of the server.
 *   <li>version: The version of the server.
 *   <li>serverStartTime: The start time of the server (in seconds).
 *   <li>serverUpTimeSeconds: The uptime of the server (in seconds).
 * </ul>
 *
 * @see io.kubemq.sdk.client.KubeMQClient#ping()
 * @see ConnectionState
 */
@Getter
@Setter
@Builder
public class ServerInfo {

  /** The host of the server. */
  private String host;

  /** The version of the server. */
  private String version;

  /** The start time of the server (in seconds). */
  private long serverStartTime;

  /** The uptime of the server (in seconds). */
  private long serverUpTimeSeconds;

  /**
   * Returns a string representation of the ServerInfo object.
   *
   * @return A string representation of the ServerInfo object.
   */
  @Override
  public String toString() {
    return "ServerInfo{"
        + "host='"
        + host
        + '\''
        + ", version='"
        + version
        + '\''
        + ", serverStartTime="
        + serverStartTime
        + ", serverUpTimeSeconds="
        + serverUpTimeSeconds
        + '}';
  }
}
