package io.kubemq.sdk.exception;

/**
 * Thrown when an SDK method is called for a feature that is recognized by the KubeMQ server but not
 * yet implemented in this SDK version.
 *
 * <p>This exception signals an intentional gap, not a bug. The feature is documented in the feature
 * matrix ({@code clients/feature-matrix.md}) with rationale and tracking information.
 *
 * <p>This is never retryable -- the feature is structurally absent, not transiently unavailable.
 */
public class NotImplementedException extends KubeMQException {

  private static final long serialVersionUID = 1L;

  private NotImplementedException(Builder builder) {
    super(builder);
  }

  /** Builder for {@link NotImplementedException} with not-implemented-specific defaults. */
  public static class Builder extends KubeMQException.Builder<Builder> {
    /** Constructs a new Builder with FEATURE_NOT_IMPLEMENTED code, FATAL category, and non-retryable. */
    public Builder() {
      code(ErrorCode.FEATURE_NOT_IMPLEMENTED);
      category(ErrorCategory.FATAL);
      retryable(false);
    }

    /**
     * Builds the {@link NotImplementedException} from this builder's state.
     *
     * @return a new NotImplementedException instance
     */
    @Override
    public NotImplementedException build() {
      return new NotImplementedException(this);
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
