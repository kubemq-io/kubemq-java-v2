package io.kubemq.sdk.exception;

import java.time.Duration;

/**
 * Builds actionable error messages per GS REQ-ERR-5. Format: {Operation} failed on channel
 * "{Channel}": {cause} Suggestion: {how to fix} [Retries exhausted: {n}/{max} attempts over
 * {duration}]
 */
public final class ErrorMessageBuilder {

  private ErrorMessageBuilder() {}

  /**
   * Builds an error message with operation context and suggestion.
   *
   * @param operation the operation
   * @param channel the channel
   * @param cause the cause
   * @param code the error code
   * @return the result
   */
  public static String build(String operation, String channel, String cause, ErrorCode code) {
    StringBuilder sb = new StringBuilder();
    sb.append(operation);
    if (channel != null) {
      sb.append(" failed on channel \"").append(channel).append("\"");
    } else {
      sb.append(" failed");
    }
    sb.append(": ").append(cause);
    String suggestion = getSuggestion(code);
    if (suggestion != null) {
      sb.append("\n  Suggestion: ").append(suggestion);
    }
    return sb.toString();
  }

  /**
   * Appends retry exhaustion context to a message.
   *
   * @param baseMessage the base message
   * @param attempts the attempts
   * @param maxAttempts the max attempts
   * @param totalDuration the total duration
   * @return the result
   */
  public static String withRetryContext(
      String baseMessage, int attempts, int maxAttempts, Duration totalDuration) {
    return baseMessage
        + String.format(
            "\n  Retries exhausted: %d/%d attempts over %s",
            attempts, maxAttempts, formatDuration(totalDuration));
  }

  /** Returns a suggestion for the given error code. */
  static String getSuggestion(ErrorCode code) {
    if (code == null) {
      return null;
    }
    switch (code) {
      case AUTHENTICATION_FAILED:
        return "Check your auth token configuration. Ensure the token is valid and not expired.";
      case AUTHORIZATION_DENIED:
        return "Check channel permissions for this client ID.";
      case CONNECTION_FAILED:
      case UNAVAILABLE:
        return "Check server connectivity and firewall rules. "
            + "Verify the server address and port are correct.";
      case CONNECTION_TIMEOUT:
      case OPERATION_TIMEOUT:
      case DEADLINE_EXCEEDED:
        return "The operation timed out. Consider increasing the timeout "
            + "or checking server load.";
      case INVALID_ARGUMENT:
      case FAILED_PRECONDITION:
        return "Check the request parameters. Refer to the API documentation.";
      case NOT_FOUND:
        return "The channel or queue does not exist. "
            + "Create it first or check the channel name.";
      case RESOURCE_EXHAUSTED:
        return "The server is rate limiting requests. "
            + "Reduce request rate or contact your administrator.";
      case BUFFER_FULL:
        return "The SDK send buffer is full. Wait for the connection "
            + "to recover or increase the buffer size.";
      case SERVER_INTERNAL:
      case DATA_LOSS:
        return "An internal server error occurred. " + "Check server logs for details.";
      case SERVER_UNIMPLEMENTED:
        return "This operation is not supported by the server. "
            + "Check server version compatibility.";
      case STREAM_BROKEN:
        return "The stream was broken. The SDK will attempt to reconnect. "
            + "Check unacknowledged messages.";
      default:
        return null;
    }
  }

  private static String formatDuration(Duration d) {
    long millis = d.toMillis();
    if (millis < 1000) {
      return millis + "ms";
    }
    return String.format("%.1fs", millis / 1000.0);
  }
}
