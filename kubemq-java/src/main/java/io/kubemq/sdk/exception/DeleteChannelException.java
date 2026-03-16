package io.kubemq.sdk.exception;

/**
 * Exception thrown when there is an error deleting a channel.
 *
 * @deprecated Since 2.2.0, use {@link ServerException} or {@link ValidationException}. This class
 *     will be removed in v3.0.
 */
@Deprecated(since = "2.2.0", forRemoval = true)
public class DeleteChannelException extends KubeMQException {

  private static final long serialVersionUID = 1L;

  /** Constructs a new instance. */
  public DeleteChannelException() {
    super(
        KubeMQException.newBuilder()
            .code(ErrorCode.SERVER_INTERNAL)
            .category(ErrorCategory.FATAL)
            .retryable(false));
  }

  /**
   * Constructs a new instance.
   *
   * @param message the message
   */
  public DeleteChannelException(String message) {
    super(
        KubeMQException.newBuilder()
            .code(ErrorCode.SERVER_INTERNAL)
            .category(ErrorCategory.FATAL)
            .retryable(false)
            .message(message)
            .operation("deleteChannel"));
  }

  /**
   * Constructs a new instance.
   *
   * @param message the message
   * @param cause the cause
   */
  public DeleteChannelException(String message, Throwable cause) {
    super(
        KubeMQException.newBuilder()
            .code(ErrorCode.SERVER_INTERNAL)
            .category(ErrorCategory.FATAL)
            .retryable(false)
            .message(message)
            .cause(cause)
            .operation("deleteChannel"));
  }
}
