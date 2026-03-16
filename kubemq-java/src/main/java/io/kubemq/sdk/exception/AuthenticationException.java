package io.kubemq.sdk.exception;

/** Thrown when authentication with KubeMQ server fails. */
public class AuthenticationException extends KubeMQException {

  private static final long serialVersionUID = 1L;

  protected AuthenticationException(Builder builder) {
    super(builder);
  }

  /** Builder for {@link AuthenticationException} with authentication-specific defaults. */
  public static class Builder extends KubeMQException.Builder<Builder> {
    /** Constructs a new Builder with AUTHENTICATION_FAILED code, AUTHENTICATION category, and non-retryable. */
    public Builder() {
      code(ErrorCode.AUTHENTICATION_FAILED);
      category(ErrorCategory.AUTHENTICATION);
      retryable(false);
    }

    /**
     * Builds the {@link AuthenticationException} from this builder's state.
     *
     * @return a new AuthenticationException instance
     */
    @Override
    public AuthenticationException build() {
      return new AuthenticationException(this);
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
