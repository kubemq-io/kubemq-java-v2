package io.kubemq.sdk.exception;

/** Thrown when the client lacks required permissions for a KubeMQ operation. */
public class AuthorizationException extends KubeMQException {

  private static final long serialVersionUID = 1L;

  protected AuthorizationException(Builder builder) {
    super(builder);
  }

  /** Builder for {@link AuthorizationException} with authorization-specific defaults. */
  public static class Builder extends KubeMQException.Builder<Builder> {
    /** Constructs a new Builder with AUTHORIZATION_DENIED code, AUTHORIZATION category, and non-retryable. */
    public Builder() {
      code(ErrorCode.AUTHORIZATION_DENIED);
      category(ErrorCategory.AUTHORIZATION);
      retryable(false);
    }

    /**
     * Builds the {@link AuthorizationException} from this builder's state.
     *
     * @return a new AuthorizationException instance
     */
    @Override
    public AuthorizationException build() {
      return new AuthorizationException(this);
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
