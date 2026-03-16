package io.kubemq.sdk.exception;

/** Thrown when the KubeMQ server encounters an internal error. */
public class ServerException extends KubeMQException {

  private static final long serialVersionUID = 1L;

  protected ServerException(Builder builder) {
    super(builder);
  }

  /** Builder for {@link ServerException} with server-error-specific defaults. */
  public static class Builder extends KubeMQException.Builder<Builder> {
    /** Constructs a new Builder with SERVER_INTERNAL code, FATAL category, and non-retryable. */
    public Builder() {
      code(ErrorCode.SERVER_INTERNAL);
      category(ErrorCategory.FATAL);
      retryable(false);
    }

    /**
     * Builds the {@link ServerException} from this builder's state.
     *
     * @return a new ServerException instance
     */
    @Override
    public ServerException build() {
      return new ServerException(this);
    }
  }

  /**
   * Creates a new builder.
   *
   * @return a new {@link Builder} instance with server-error-specific defaults
   */
  public static Builder builder() {
    return new Builder();
  }
}
