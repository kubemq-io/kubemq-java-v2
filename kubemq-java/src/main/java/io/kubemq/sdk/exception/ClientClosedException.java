package io.kubemq.sdk.exception;

/** Thrown when an operation is attempted on a closed client. This is a non-retryable error. */
public class ClientClosedException extends KubeMQException {

  private static final long serialVersionUID = 1L;

  protected ClientClosedException(Builder builder) {
    super(builder);
  }

  /**
   * Creates a pre-configured ClientClosedException with a standard message.
   *
   * @return a new ClientClosedException instance
   */
  public static ClientClosedException create() {
    return new Builder().build();
  }

  /** Builder for {@link ClientClosedException} with client-closed-specific defaults. */
  public static class Builder extends KubeMQException.Builder<Builder> {
    /** Constructs a new Builder with CONNECTION_CLOSED code, FATAL category, non-retryable, and a standard message. */
    public Builder() {
      code(ErrorCode.CONNECTION_CLOSED);
      category(ErrorCategory.FATAL);
      retryable(false);
      message("Client has been closed. Create a new client instance for further operations.");
    }

    /**
     * Builds the {@link ClientClosedException} from this builder's state.
     *
     * @return a new ClientClosedException instance
     */
    @Override
    public ClientClosedException build() {
      return new ClientClosedException(this);
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
