package io.kubemq.sdk.exception;

/**
 * Thrown when an operation is attempted while the connection is not READY and waitForReady is
 * false. This is a non-retryable error -- the caller should wait for the connection to become ready
 * rather than retrying.
 */
public class ConnectionNotReadyException extends KubeMQException {

  private static final long serialVersionUID = 1L;

  protected ConnectionNotReadyException(Builder builder) {
    super(builder);
  }

  /**
   * Creates a ConnectionNotReadyException with the current connection state description.
   *
   * @param stateDescription the current connection state
   * @return a new ConnectionNotReadyException instance
   */
  public static ConnectionNotReadyException create(String stateDescription) {
    return new Builder()
        .message(
            "Connection is not ready (state="
                + stateDescription
                + "). Set waitForReady=true to block until ready.")
        .build();
  }

  /**
   * Builder for {@link ConnectionNotReadyException} with connection-not-ready-specific defaults.
   */
  public static class Builder extends KubeMQException.Builder<Builder> {
    /** Constructs a new Builder with UNAVAILABLE code, TRANSIENT category, and non-retryable. */
    public Builder() {
      code(ErrorCode.UNAVAILABLE);
      category(ErrorCategory.TRANSIENT);
      retryable(false);
    }

    /**
     * Builds the {@link ConnectionNotReadyException} from this builder's state.
     *
     * @return a new ConnectionNotReadyException instance
     */
    @Override
    public ConnectionNotReadyException build() {
      return new ConnectionNotReadyException(this);
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
