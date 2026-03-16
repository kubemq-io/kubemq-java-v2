package io.kubemq.sdk.exception;

/** Thrown when user callback code throws an exception during async processing. */
public class HandlerException extends KubeMQException {

  private static final long serialVersionUID = 1L;

  protected HandlerException(Builder builder) {
    super(builder);
  }

  /** Builder for {@link HandlerException} with handler-error-specific defaults. */
  public static class Builder extends KubeMQException.Builder<Builder> {
    /** Constructs a new Builder with HANDLER_ERROR code, FATAL category, and non-retryable. */
    public Builder() {
      code(ErrorCode.HANDLER_ERROR);
      category(ErrorCategory.FATAL);
      retryable(false);
    }

    /**
     * Builds the {@link HandlerException} from this builder's state.
     *
     * @return a new HandlerException instance
     */
    @Override
    public HandlerException build() {
      return new HandlerException(this);
    }
  }

  /**
   * Creates a new builder.
   *
   * @return a new Builder instance
   */
  public static Builder builder() {
    return new Builder();
  }
}
