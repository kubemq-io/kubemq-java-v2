package io.kubemq.sdk.common;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * The ServerInfo class represents information about a server.
 *
 * <p>Attributes:
 * <ul>
 *     <li>host: The host of the server.</li>
 *     <li>version: The version of the server.</li>
 *     <li>serverStartTime: The start time of the server (in seconds).</li>
 *     <li>serverUpTimeSeconds: The uptime of the server (in seconds).</li>
 * </ul>
 */
@Getter
@Setter
@Builder
public class ServerInfo {

    /**
     * The host of the server.
     */
    private String host;

    /**
     * The version of the server.
     */
    private String version;

    /**
     * The start time of the server (in seconds).
     */
    private long serverStartTime;

    /**
     * The uptime of the server (in seconds).
     */
    private long serverUpTimeSeconds;

    /**
     * Returns a string representation of the ServerInfo object.
     *
     * @return A string representation of the ServerInfo object.
     */
    @Override
    public String toString() {
        return "ServerInfo{" +
                "host='" + host + '\'' +
                ", version='" + version + '\'' +
                ", serverStartTime=" + serverStartTime +
                ", serverUpTimeSeconds=" + serverUpTimeSeconds +
                '}';
    }
}
