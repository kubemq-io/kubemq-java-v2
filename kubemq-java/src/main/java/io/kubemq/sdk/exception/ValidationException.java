package io.kubemq.sdk.exception;

/** Thrown when an input validation fails for a KubeMQ operation. */
public class ValidationException extends KubeMQException {

  private static final long serialVersionUID = 1L;

  protected ValidationException(Builder builder) {
    super(builder);
  }

  /** Builder for {@link ValidationException} with validation-specific defaults. */
  public static class Builder extends KubeMQException.Builder<Builder> {
    /**
     * Constructs a new Builder with INVALID_ARGUMENT code, VALIDATION category, and non-retryable.
     */
    public Builder() {
      code(ErrorCode.INVALID_ARGUMENT);
      category(ErrorCategory.VALIDATION);
      retryable(false);
    }

    /**
     * Builds the {@link ValidationException} from this builder's state.
     *
     * @return a new ValidationException instance
     */
    @Override
    public ValidationException build() {
      return new ValidationException(this);
    }
  }

  /**
   * Creates a new builder.
   *
   * @return a new {@link Builder} instance with validation-specific defaults
   */
  public static Builder builder() {
    return new Builder();
  }
}
