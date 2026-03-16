package io.kubemq.sdk.exception;

/** Thrown when concurrent retry limit is reached and a retry attempt is rejected. */
public class RetryThrottledException extends KubeMQException {

  private static final long serialVersionUID = 1L;

  protected RetryThrottledException(Builder builder) {
    super(builder);
  }

  /** Builder for {@link RetryThrottledException} with retry-throttling-specific defaults. */
  public static class Builder extends KubeMQException.Builder<Builder> {
    /** Constructs a new Builder with RETRY_THROTTLED code, THROTTLING category, and non-retryable. */
    public Builder() {
      code(ErrorCode.RETRY_THROTTLED);
      category(ErrorCategory.THROTTLING);
      retryable(false);
    }

    /**
     * Builds the {@link RetryThrottledException} from this builder's state.
     *
     * @return a new RetryThrottledException instance
     */
    @Override
    public RetryThrottledException build() {
      return new RetryThrottledException(this);
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
