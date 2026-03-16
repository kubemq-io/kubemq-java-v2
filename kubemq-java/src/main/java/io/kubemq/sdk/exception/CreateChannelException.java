package io.kubemq.sdk.exception;

/**
 * Exception thrown when there is an error creating a channel.
 *
 * @deprecated Since 2.2.0, use {@link ServerException} or {@link ValidationException}. This class
 *     will be removed in v3.0.
 */
@Deprecated(since = "2.2.0", forRemoval = true)
public class CreateChannelException extends KubeMQException {

  private static final long serialVersionUID = 1L;

  /** Constructs a new instance. */
  public CreateChannelException() {
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
  public CreateChannelException(String message) {
    super(
        KubeMQException.newBuilder()
            .code(ErrorCode.SERVER_INTERNAL)
            .category(ErrorCategory.FATAL)
            .retryable(false)
            .message(message)
            .operation("createChannel"));
  }

  /**
   * Constructs a new instance.
   *
   * @param message the message
   * @param cause the cause
   */
  public CreateChannelException(String message, Throwable cause) {
    super(
        KubeMQException.newBuilder()
            .code(ErrorCode.SERVER_INTERNAL)
            .category(ErrorCategory.FATAL)
            .retryable(false)
            .message(message)
            .cause(cause)
            .operation("createChannel"));
  }
}
