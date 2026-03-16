package io.kubemq.sdk.exception;

import java.io.Serializable;
import java.time.Instant;

/**
 * Base exception for all KubeMQ SDK errors. Extends RuntimeException (unchecked) per Java SDK
 * convention. All SDK methods throw subtypes of this class -- never raw RuntimeException or gRPC
 * exceptions.
 */
public class KubeMQException extends RuntimeException implements Serializable {

  private static final long serialVersionUID = 1L;

  private final ErrorCode code;
  private final String operation;
  private final String channel;
  private final boolean retryable;
  private final String requestId;

  private final String messageId;
  private final int statusCode;
  private final Instant timestamp;
  private final String serverAddress;

  private final ErrorCategory category;

  /** Full constructor used by builder. */
  protected KubeMQException(Builder<?> builder) {
    super(builder.message, builder.cause);
    this.code = builder.code;
    this.operation = builder.operation;
    this.channel = builder.channel;
    this.retryable = builder.retryable;
    this.requestId = builder.requestId;
    this.messageId = builder.messageId;
    this.statusCode = builder.statusCode;
    this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
    this.serverAddress = builder.serverAddress;
    this.category = builder.category;
  }

  public ErrorCode getCode() {
    return code;
  }

  public String getOperation() {
    return operation;
  }

  public String getChannel() {
    return channel;
  }

  public boolean isRetryable() {
    return retryable;
  }

  public String getRequestId() {
    return requestId;
  }

  public String getMessageId() {
    return messageId;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public String getServerAddress() {
    return serverAddress;
  }

  public ErrorCategory getCategory() {
    return category;
  }

  /**
   * Returns a string representation including error code, operation, channel, and retryability.
   *
   * @return a formatted string with the exception details
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getClass().getSimpleName());
    sb.append("[code=").append(code);
    if (operation != null) {
      sb.append(", operation=").append(operation);
    }
    if (channel != null) {
      sb.append(", channel=").append(channel);
    }
    sb.append(", retryable=").append(retryable);
    if (requestId != null) {
      sb.append(", requestId=").append(requestId);
    }
    if (statusCode >= 0) {
      sb.append(", grpcStatus=").append(statusCode);
    }
    sb.append("]: ").append(getMessage());
    return sb.toString();
  }

  @SuppressWarnings("unchecked")
  public abstract static class Builder<T extends Builder<T>> {
    private ErrorCode code;
    private String message;
    private String operation;
    private String channel;
    private boolean retryable;
    private String requestId;
    private Throwable cause;
    private String messageId;
    private int statusCode = -1;
    private Instant timestamp;
    private String serverAddress;
    private ErrorCategory category;

    /**
     * Sets the error code.
     *
     * @param code the error code
     * @return this builder
     */
    public T code(ErrorCode code) {
      this.code = code;
      return (T) this;
    }

    /**
     * Sets the error message.
     *
     * @param message the error message
     * @return this builder
     */
    public T message(String message) {
      this.message = message;
      return (T) this;
    }

    /**
     * Sets the operation name.
     *
     * @param operation the operation name
     * @return this builder
     */
    public T operation(String operation) {
      this.operation = operation;
      return (T) this;
    }

    /**
     * Sets the channel name.
     *
     * @param channel the channel name
     * @return this builder
     */
    public T channel(String channel) {
      this.channel = channel;
      return (T) this;
    }

    /**
     * Sets whether the error is retryable.
     *
     * @param retryable true if retryable
     * @return this builder
     */
    public T retryable(boolean retryable) {
      this.retryable = retryable;
      return (T) this;
    }

    /**
     * Sets the request ID.
     *
     * @param requestId the request ID
     * @return this builder
     */
    public T requestId(String requestId) {
      this.requestId = requestId;
      return (T) this;
    }

    /**
     * Sets the cause.
     *
     * @param cause the cause
     * @return this builder
     */
    public T cause(Throwable cause) {
      this.cause = cause;
      return (T) this;
    }

    /**
     * Sets the message ID.
     *
     * @param messageId the message ID
     * @return this builder
     */
    public T messageId(String messageId) {
      this.messageId = messageId;
      return (T) this;
    }

    /**
     * Sets the gRPC status code.
     *
     * @param statusCode the status code
     * @return this builder
     */
    public T statusCode(int statusCode) {
      this.statusCode = statusCode;
      return (T) this;
    }

    /**
     * Sets the timestamp.
     *
     * @param timestamp the timestamp
     * @return this builder
     */
    public T timestamp(Instant timestamp) {
      this.timestamp = timestamp;
      return (T) this;
    }

    /**
     * Sets the server address.
     *
     * @param serverAddress the server address
     * @return this builder
     */
    public T serverAddress(String serverAddress) {
      this.serverAddress = serverAddress;
      return (T) this;
    }

    /**
     * Sets the error category.
     *
     * @param category the error category
     * @return this builder
     */
    public T category(ErrorCategory category) {
      this.category = category;
      return (T) this;
    }

    /**
     * Builds the exception.
     *
     * @return the built exception
     */
    public abstract KubeMQException build();
  }

  /** Default builder for constructing KubeMQException instances. */
  public static class DefaultBuilder extends Builder<DefaultBuilder> {
    /**
     * Builds the {@link KubeMQException} from this builder's state.
     *
     * @return a new KubeMQException instance
     */
    @Override
    public KubeMQException build() {
      return new KubeMQException(this);
    }
  }

  /**
   * Creates a builder for constructing a generic KubeMQException. Subclasses provide their own
   * {@code builder()} factory method.
   *
   * @return a new {@link DefaultBuilder} instance for configuring and building the exception
   */
  public static DefaultBuilder newBuilder() {
    return new DefaultBuilder();
  }
}
