package io.kubemq.sdk.exception;

/**
 * Thrown when the SDK detects a configuration error that cannot be resolved at runtime (e.g., TLS
 * version mismatch, invalid option combinations). Non-retryable.
 */
public class ConfigurationException extends KubeMQException {

  private static final long serialVersionUID = 1L;

  protected ConfigurationException(Builder builder) {
    super(builder);
  }

  /** Builder for {@link ConfigurationException} with configuration-error-specific defaults. */
  public static class Builder extends KubeMQException.Builder<Builder> {
    /**
     * Constructs a new Builder with FAILED_PRECONDITION code, VALIDATION category, and
     * non-retryable.
     */
    public Builder() {
      code(ErrorCode.FAILED_PRECONDITION);
      category(ErrorCategory.VALIDATION);
      retryable(false);
    }

    /**
     * Builds the {@link ConfigurationException} from this builder's state.
     *
     * @return a new ConfigurationException instance
     */
    @Override
    public ConfigurationException build() {
      return new ConfigurationException(this);
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
