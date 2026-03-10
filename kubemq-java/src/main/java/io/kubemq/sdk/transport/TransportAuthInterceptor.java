package io.kubemq.sdk.transport;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

import java.util.function.Supplier;

/**
 * gRPC interceptor that injects authorization tokens into call metadata.
 *
 * <p>The token is read via a {@link Supplier} on every call, supporting
 * mutable tokens and credential providers without re-creating the
 * interceptor or the gRPC channel.</p>
 */
class TransportAuthInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> AUTH_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final Supplier<String> tokenSupplier;

    TransportAuthInterceptor(Supplier<String> tokenSupplier) {
        this.tokenSupplier = tokenSupplier;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String token = tokenSupplier.get();
                if (token != null && !token.isEmpty()) {
                    headers.put(AUTH_KEY, token);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
