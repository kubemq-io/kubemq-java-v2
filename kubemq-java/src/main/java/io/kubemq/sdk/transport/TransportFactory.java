package io.kubemq.sdk.transport;

import io.kubemq.sdk.common.Internal;

/**
 * Factory for creating {@link Transport} instances.
 *
 * <p>Clients use this factory; they never instantiate {@code GrpcTransport}
 * directly. The factory signature does NOT expose gRPC types. Interceptors
 * are built internally by {@code GrpcTransport} based on
 * {@link TransportConfig}, which carries the token supplier and feature
 * flags needed to configure the interceptor chain.</p>
 */
@Internal
public final class TransportFactory {

    private TransportFactory() {}

    /**
     * Creates a new gRPC-based Transport.
     * Interceptors (auth, error mapping, etc.) are built internally from config.
     *
     * @param config transport configuration (no gRPC types)
     * @return a new Transport instance
     */
    public static Transport create(TransportConfig config) {
        return new GrpcTransport(config);
    }
}
