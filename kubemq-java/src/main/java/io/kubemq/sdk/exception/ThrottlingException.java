package io.kubemq.sdk.exception;

/** Thrown when the server is rate limiting requests. */
public class ThrottlingException extends KubeMQException {

  private static final long serialVersionUID = 1L;

  protected ThrottlingException(Builder builder) {
    super(builder);
  }

  /** Builder for {@link ThrottlingException} with throttling-specific defaults. */
  public static class Builder extends KubeMQException.Builder<Builder> {
    /** Constructs a new Builder with RESOURCE_EXHAUSTED code, THROTTLING category, and retryable. */
    public Builder() {
      code(ErrorCode.RESOURCE_EXHAUSTED);
      category(ErrorCategory.THROTTLING);
      retryable(true);
    }

    /**
     * Builds the {@link ThrottlingException} from this builder's state.
     *
     * @return a new ThrottlingException instance
     */
    @Override
    public ThrottlingException build() {
      return new ThrottlingException(this);
    }
  }

  /**
   * Creates a new builder.
   *
   * @return the result
   */
  public static Builder builder() {
    return new Builder();
  }
}
