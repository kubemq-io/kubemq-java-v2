package io.kubemq.sdk.exception;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a partial batch failure. Reserved for future use when the server supports per-message
 * batch status. Added per GS Future Enhancement guidance.
 */
public class PartialFailureException extends KubeMQException {

  private static final long serialVersionUID = 1L;
  private final Map<String, KubeMQException> perMessageErrors;

  protected PartialFailureException(Builder builder) {
    super(builder);
    this.perMessageErrors =
        builder.perMessageErrors != null
            ? Collections.unmodifiableMap(builder.perMessageErrors)
            : Collections.emptyMap();
  }

  public Map<String, KubeMQException> getPerMessageErrors() {
    return perMessageErrors;
  }

  /** Builder for {@link PartialFailureException} with partial-failure-specific defaults. */
  public static class Builder extends KubeMQException.Builder<Builder> {
    private Map<String, KubeMQException> perMessageErrors;

    /**
     * Constructs a new Builder with PARTIAL_FAILURE code, TRANSIENT category, and non-retryable.
     */
    public Builder() {
      code(ErrorCode.PARTIAL_FAILURE);
      category(ErrorCategory.TRANSIENT);
      retryable(false);
    }

    /**
     * Sets the per-message error map for this partial failure.
     *
     * @param errors map of message ID to the error for that message
     * @return this builder
     */
    public Builder perMessageErrors(Map<String, KubeMQException> errors) {
      this.perMessageErrors = errors;
      return this;
    }

    /**
     * Builds the {@link PartialFailureException} from this builder's state.
     *
     * @return a new PartialFailureException instance
     */
    @Override
    public PartialFailureException build() {
      return new PartialFailureException(this);
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
