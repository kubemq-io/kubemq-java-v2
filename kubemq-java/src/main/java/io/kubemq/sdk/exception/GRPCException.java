package io.kubemq.sdk.exception;

/**
 * Exception thrown when there is a gRPC error.
 *
 * @deprecated Since 2.2.0, use {@link ConnectionException}, {@link ServerException},
 *             or the specific subtype returned by {@link GrpcErrorMapper}.
 *             This class will be removed in v3.0.
 */
@Deprecated(since = "2.2.0", forRemoval = true)
public class GRPCException extends KubeMQException {

    private static final long serialVersionUID = 1L;

    public GRPCException() {
        super(KubeMQException.newBuilder()
            .code(ErrorCode.UNKNOWN_ERROR)
            .category(ErrorCategory.TRANSIENT)
            .retryable(true));
    }

    public GRPCException(String message) {
        super(KubeMQException.newBuilder()
            .code(ErrorCode.UNKNOWN_ERROR)
            .category(ErrorCategory.TRANSIENT)
            .retryable(true)
            .message(message));
    }

    public GRPCException(Throwable cause) {
        super(KubeMQException.newBuilder()
            .code(ErrorCode.UNKNOWN_ERROR)
            .category(ErrorCategory.TRANSIENT)
            .retryable(true)
            .message(cause != null ? cause.getMessage() : null)
            .cause(cause));
    }

    public GRPCException(String message, Throwable cause) {
        super(KubeMQException.newBuilder()
            .code(ErrorCode.UNKNOWN_ERROR)
            .category(ErrorCategory.TRANSIENT)
            .retryable(true)
            .message(message)
            .cause(cause));
    }
}
