package io.kubemq.sdk.exception;

/** Thrown when a KubeMQ operation times out. */
public class KubeMQTimeoutException extends KubeMQException {

  private static final long serialVersionUID = 1L;

  protected KubeMQTimeoutException(Builder builder) {
    super(builder);
  }

  /** Builder for {@link KubeMQTimeoutException} with timeout-specific defaults. */
  public static class Builder extends KubeMQException.Builder<Builder> {
    /** Constructs a new Builder with OPERATION_TIMEOUT code, TIMEOUT category, and retryable. */
    public Builder() {
      code(ErrorCode.OPERATION_TIMEOUT);
      category(ErrorCategory.TIMEOUT);
      retryable(true);
    }

    /**
     * Builds the {@link KubeMQTimeoutException} from this builder's state.
     *
     * @return a new KubeMQTimeoutException instance
     */
    @Override
    public KubeMQTimeoutException build() {
      return new KubeMQTimeoutException(this);
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
