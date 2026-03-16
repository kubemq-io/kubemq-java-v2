package io.kubemq.sdk.exception;

/**
 * Utility to classify any KubeMQException. Used internally by retry logic to determine retry
 * eligibility.
 */
public final class ErrorClassifier {

  private ErrorClassifier() {}

  /**
   * Returns true if the error should be retried based on its classification.
   *
   * @param ex the ex
   * @return the result
   */
  public static boolean shouldRetry(KubeMQException ex) {
    return ex.isRetryable();
  }

  /**
   * Returns true if the error should use extended backoff (throttling).
   *
   * @param ex the ex
   * @return the result
   */
  public static boolean shouldUseExtendedBackoff(KubeMQException ex) {
    return ex.getCategory() == ErrorCategory.THROTTLING;
  }

  /**
   * Returns the error.type string for OTel attributes. Maps ErrorCategory to lowercase string per
   * GS REQ-OBS-3.
   *
   * @param ex the ex
   * @return the result
   */
  public static String toOtelErrorType(KubeMQException ex) {
    return ex.getCategory() != null ? ex.getCategory().name().toLowerCase() : "unknown";
  }
}
