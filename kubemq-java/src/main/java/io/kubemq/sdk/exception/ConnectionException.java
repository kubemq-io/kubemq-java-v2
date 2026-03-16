package io.kubemq.sdk.exception;

/** Thrown when a connection to KubeMQ server fails or is lost. */
public class ConnectionException extends KubeMQException {

  private static final long serialVersionUID = 1L;

  protected ConnectionException(Builder builder) {
    super(builder);
  }

  /** Builder for {@link ConnectionException} with connection-error-specific defaults. */
  public static class Builder extends KubeMQException.Builder<Builder> {
    /** Constructs a new Builder with CONNECTION_FAILED code, TRANSIENT category, and retryable. */
    public Builder() {
      code(ErrorCode.CONNECTION_FAILED);
      category(ErrorCategory.TRANSIENT);
      retryable(true);
    }

    /**
     * Builds the {@link ConnectionException} from this builder's state.
     *
     * @return a new ConnectionException instance
     */
    @Override
    public ConnectionException build() {
      return new ConnectionException(this);
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
