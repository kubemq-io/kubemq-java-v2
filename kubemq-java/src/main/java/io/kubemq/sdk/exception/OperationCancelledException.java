package io.kubemq.sdk.exception;

/** Thrown when a KubeMQ operation is cancelled by the client. */
public class OperationCancelledException extends KubeMQException {

  private static final long serialVersionUID = 1L;

  protected OperationCancelledException(Builder builder) {
    super(builder);
  }

  /** Builder for {@link OperationCancelledException} with cancellation-specific defaults. */
  public static class Builder extends KubeMQException.Builder<Builder> {
    /**
     * Constructs a new Builder with CANCELLED_BY_CLIENT code, CANCELLATION category, and
     * non-retryable.
     */
    public Builder() {
      code(ErrorCode.CANCELLED_BY_CLIENT);
      category(ErrorCategory.CANCELLATION);
      retryable(false);
    }

    /**
     * Builds the {@link OperationCancelledException} from this builder's state.
     *
     * @return a new OperationCancelledException instance
     */
    @Override
    public OperationCancelledException build() {
      return new OperationCancelledException(this);
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
