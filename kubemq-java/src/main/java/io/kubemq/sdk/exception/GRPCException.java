package io.kubemq.sdk.exception;

/**
 * Exception thrown when there is a gRPC error.
 *
 * @deprecated Since 2.2.0, use {@link ConnectionException}, {@link ServerException}, or the
 *     specific subtype returned by {@link GrpcErrorMapper}. This class will be removed in v3.0.
 */
@Deprecated(since = "2.2.0", forRemoval = true)
public class GRPCException extends KubeMQException {

  private static final long serialVersionUID = 1L;

  /** Constructs a new instance. */
  public GRPCException() {
    super(
        KubeMQException.newBuilder()
            .code(ErrorCode.UNKNOWN_ERROR)
            .category(ErrorCategory.TRANSIENT)
            .retryable(true));
  }

  /**
   * Constructs a new instance.
   *
   * @param message the message
   */
  public GRPCException(String message) {
    super(
        KubeMQException.newBuilder()
            .code(ErrorCode.UNKNOWN_ERROR)
            .category(ErrorCategory.TRANSIENT)
            .retryable(true)
            .message(message));
  }

  /**
   * Constructs a new instance.
   *
   * @param cause the cause
   */
  public GRPCException(Throwable cause) {
    super(
        KubeMQException.newBuilder()
            .code(ErrorCode.UNKNOWN_ERROR)
            .category(ErrorCategory.TRANSIENT)
            .retryable(true)
            .message(cause != null ? cause.getMessage() : null)
            .cause(cause));
  }

  /**
   * Constructs a new instance.
   *
   * @param message the message
   * @param cause the cause
   */
  public GRPCException(String message, Throwable cause) {
    super(
        KubeMQException.newBuilder()
            .code(ErrorCode.UNKNOWN_ERROR)
            .category(ErrorCategory.TRANSIENT)
            .retryable(true)
            .message(message)
            .cause(cause));
  }
}
