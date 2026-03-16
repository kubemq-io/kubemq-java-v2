/**
 * Transport layer for the KubeMQ SDK.
 *
 * <p>This package isolates all gRPC-specific code behind the {@code Transport} interface. Classes
 * outside this package should not import {@code io.grpc.*} or {@code kubemq.*} types directly.
 *
 * <p>All types except {@link io.kubemq.sdk.transport.TransportConfig} and {@link
 * io.kubemq.sdk.transport.TransportFactory} are package-private and not part of the public API.
 */
package io.kubemq.sdk.transport;
