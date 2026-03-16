package io.kubemq.sdk.auth;

/**
 * Thrown by a {@link CredentialProvider} when token retrieval fails.
 *
 * <p>Implementers should set {@link #isRetryable()} to indicate whether the SDK should retry (e.g.,
 * transient infrastructure failure) or not (e.g., invalid/expired credentials).
 */
public class CredentialException extends Exception {

  private static final long serialVersionUID = 1L;
  private final boolean retryable;

  /**
   * Creates a non-retryable credential exception.
   *
   * @param message the message
   */
  public CredentialException(String message) {
    this(message, false);
  }

  /**
   * Constructs a new instance.
   *
   * @param message the message
   * @param retryable the retryable
   */
  public CredentialException(String message, boolean retryable) {
    super(message);
    this.retryable = retryable;
  }

  /**
   * Constructs a new instance.
   *
   * @param message the message
   * @param cause the cause
   * @param retryable the retryable
   */
  public CredentialException(String message, Throwable cause, boolean retryable) {
    super(message, cause);
    this.retryable = retryable;
  }

  /**
   * Whether this failure is transient and the SDK should retry.
   *
   * @return true if the provider failure is transient (e.g., network timeout, credential store
   *     temporarily unavailable)
   */
  public boolean isRetryable() {
    return retryable;
  }
}
