package io.kubemq.sdk.exception;

import java.util.Collections;
import java.util.List;

/** Thrown when a gRPC stream breaks with in-flight messages. */
public class StreamBrokenException extends KubeMQException {

  private static final long serialVersionUID = 1L;
  private final List<String> unacknowledgedMessageIds;

  protected StreamBrokenException(Builder builder) {
    super(builder);
    this.unacknowledgedMessageIds =
        builder.unacknowledgedMessageIds != null
            ? Collections.unmodifiableList(builder.unacknowledgedMessageIds)
            : Collections.emptyList();
  }

  public List<String> getUnacknowledgedMessageIds() {
    return unacknowledgedMessageIds;
  }

  public static class Builder extends KubeMQException.Builder<Builder> {
    private List<String> unacknowledgedMessageIds;

    /** Constructs a new instance. */
    public Builder() {
      code(ErrorCode.STREAM_BROKEN);
      category(ErrorCategory.TRANSIENT);
      retryable(true);
    }

    /**
     * Performs the unacknowledged message ids operation.
     *
     * @param ids the ids
     * @return the result
     */
    public Builder unacknowledgedMessageIds(List<String> ids) {
      this.unacknowledgedMessageIds = ids;
      return this;
    }

    /**
     * Builds and returns the constructed object.
     *
     * @return the result
     */
    @Override
    public StreamBrokenException build() {
      return new StreamBrokenException(this);
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
