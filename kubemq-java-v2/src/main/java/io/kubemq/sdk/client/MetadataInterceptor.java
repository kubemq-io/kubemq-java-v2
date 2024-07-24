package io.kubemq.sdk.client;

import io.grpc.*;

/**
 * MetadataInterceptor is a gRPC client interceptor used to add custom metadata to gRPC calls.
 * This interceptor is useful for including additional information such as authorization tokens or other headers
 * that need to be sent with each gRPC request.
 */
public class MetadataInterceptor implements ClientInterceptor {

    private final Metadata metadata;

    /**
     * Constructs a MetadataInterceptor with the specified metadata.
     *
     * @param metadata The metadata to be added to each gRPC call. This can include headers like authorization tokens.
     */
    public MetadataInterceptor(Metadata metadata) {
        this.metadata = metadata;
    }

    /**
     * Intercepts a gRPC call to add metadata to the request headers.
     *
     * @param method The method descriptor describing the method to be invoked.
     * @param callOptions The call options for the call.
     * @param next The next channel in the chain of interceptors.
     * @param <ReqT> The type of the request message.
     * @param <RespT> The type of the response message.
     * @return A new {@link ClientCall} that adds the specified metadata to the headers of each call.
     */
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                // Merge the custom metadata into the headers of the call.
                headers.merge(metadata);
                super.start(responseListener, headers);
            }
        };
    }
}
