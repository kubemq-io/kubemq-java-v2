package io.kubemq.sdk.exception;

/**
 * Thrown when a transport-layer (gRPC) error occurs. Used internally to distinguish transport
 * errors from handler errors.
 */
public class TransportException extends KubeMQException {

  private static final long serialVersionUID = 1L;

  protected TransportException(Builder builder) {
    super(builder);
  }

  public static class Builder extends KubeMQException.Builder<Builder> {
    /** Constructs a new instance. */
    public Builder() {
      code(ErrorCode.CONNECTION_FAILED);
      category(ErrorCategory.TRANSIENT);
      retryable(true);
    }

    /**
     * Builds and returns the constructed object.
     *
     * @return the result
     */
    @Override
    public TransportException build() {
      return new TransportException(this);
    }
  }

  /**
   * Creates a new builder instance.
   *
   * @return the result
   */
  public static Builder builder() {
    return new Builder();
  }
}
