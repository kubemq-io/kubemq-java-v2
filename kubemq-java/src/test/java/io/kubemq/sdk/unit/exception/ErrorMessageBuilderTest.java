package io.kubemq.sdk.unit.exception;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.exception.ErrorCode;
import io.kubemq.sdk.exception.ErrorMessageBuilder;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Unit tests for REQ-ERR-5: Actionable error messages via ErrorMessageBuilder. */
class ErrorMessageBuilderTest {

  @Test
  void build_withChannelIncludesChannelInMessage() {
    String msg =
        ErrorMessageBuilder.build(
            "sendEvent", "orders", "Connection refused", ErrorCode.CONNECTION_FAILED);

    assertTrue(msg.contains("sendEvent"));
    assertTrue(msg.contains("failed on channel \"orders\""));
    assertTrue(msg.contains("Connection refused"));
  }

  @Test
  void build_withoutChannelOmitsChannelClause() {
    String msg =
        ErrorMessageBuilder.build("ping", null, "Server unreachable", ErrorCode.CONNECTION_FAILED);

    assertTrue(msg.contains("ping failed"));
    assertFalse(msg.contains("channel"));
    assertTrue(msg.contains("Server unreachable"));
  }

  @Test
  void build_includesSuggestionForKnownCodes() {
    String msg =
        ErrorMessageBuilder.build(
            "subscribe", "events", "Authentication failed", ErrorCode.AUTHENTICATION_FAILED);

    assertTrue(msg.contains("Suggestion:"));
    assertTrue(msg.contains("auth token"));
  }

  @Test
  void build_noSuggestionForUnknownCode() {
    String msg = ErrorMessageBuilder.build("op", null, "something", ErrorCode.UNKNOWN_ERROR);

    assertFalse(msg.contains("Suggestion:"));
  }

  @Test
  void build_noSuggestionForNullCode() {
    String msg = ErrorMessageBuilder.build("op", null, "something", null);

    assertFalse(msg.contains("Suggestion:"));
  }

  @Test
  void withRetryContext_appendsRetryInfo() {
    String base = "sendEvent failed: timeout";
    String result = ErrorMessageBuilder.withRetryContext(base, 3, 5, Duration.ofMillis(1500));

    assertTrue(result.startsWith(base));
    assertTrue(result.contains("Retries exhausted: 3/5 attempts over 1.5s"));
  }

  @Test
  void withRetryContext_shortDuration_showsMilliseconds() {
    String result = ErrorMessageBuilder.withRetryContext("msg", 1, 3, Duration.ofMillis(450));

    assertTrue(result.contains("450ms"));
  }

  @Test
  void withRetryContext_longDuration_showsSeconds() {
    String result = ErrorMessageBuilder.withRetryContext("msg", 3, 3, Duration.ofSeconds(10));

    assertTrue(result.contains("10.0s"));
  }

  @Test
  void suggestion_connectionFailed_includesSuggestion() {
    String msg = ErrorMessageBuilder.build("op", null, "err", ErrorCode.CONNECTION_FAILED);
    assertTrue(msg.contains("Suggestion:"));
    assertTrue(msg.contains("connectivity"));
  }

  @Test
  void suggestion_unavailable_includesSuggestion() {
    String msg = ErrorMessageBuilder.build("op", null, "err", ErrorCode.UNAVAILABLE);
    assertTrue(msg.contains("Suggestion:"));
    assertTrue(msg.contains("connectivity"));
  }

  @Test
  void suggestion_authorizationDenied_includesSuggestion() {
    String msg = ErrorMessageBuilder.build("op", null, "err", ErrorCode.AUTHORIZATION_DENIED);
    assertTrue(msg.contains("Suggestion:"));
    assertTrue(msg.contains("permissions"));
  }

  @Test
  void suggestion_connectionTimeout_includesSuggestion() {
    String msg = ErrorMessageBuilder.build("op", null, "err", ErrorCode.CONNECTION_TIMEOUT);
    assertTrue(msg.contains("Suggestion:"));
    assertTrue(msg.contains("timed out"));
  }

  @Test
  void suggestion_operationTimeout_includesSuggestion() {
    String msg = ErrorMessageBuilder.build("op", null, "err", ErrorCode.OPERATION_TIMEOUT);
    assertTrue(msg.contains("Suggestion:"));
    assertTrue(msg.contains("timed out"));
  }

  @Test
  void suggestion_deadlineExceeded_includesSuggestion() {
    String msg = ErrorMessageBuilder.build("op", null, "err", ErrorCode.DEADLINE_EXCEEDED);
    assertTrue(msg.contains("Suggestion:"));
    assertTrue(msg.contains("timed out"));
  }

  @Test
  void suggestion_invalidArgument_includesSuggestion() {
    String msg = ErrorMessageBuilder.build("op", null, "err", ErrorCode.INVALID_ARGUMENT);
    assertTrue(msg.contains("Suggestion:"));
    assertTrue(msg.contains("parameters"));
  }

  @Test
  void suggestion_failedPrecondition_includesSuggestion() {
    String msg = ErrorMessageBuilder.build("op", null, "err", ErrorCode.FAILED_PRECONDITION);
    assertTrue(msg.contains("Suggestion:"));
    assertTrue(msg.contains("parameters"));
  }

  @Test
  void suggestion_notFound_includesSuggestion() {
    String msg = ErrorMessageBuilder.build("op", null, "err", ErrorCode.NOT_FOUND);
    assertTrue(msg.contains("Suggestion:"));
    assertTrue(msg.contains("does not exist"));
  }

  @Test
  void suggestion_resourceExhausted_includesSuggestion() {
    String msg = ErrorMessageBuilder.build("op", null, "err", ErrorCode.RESOURCE_EXHAUSTED);
    assertTrue(msg.contains("Suggestion:"));
    assertTrue(msg.contains("rate limiting"));
  }

  @Test
  void suggestion_bufferFull_includesSuggestion() {
    String msg = ErrorMessageBuilder.build("op", null, "err", ErrorCode.BUFFER_FULL);
    assertTrue(msg.contains("Suggestion:"));
    assertTrue(msg.contains("buffer"));
  }

  @Test
  void suggestion_serverInternal_includesSuggestion() {
    String msg = ErrorMessageBuilder.build("op", null, "err", ErrorCode.SERVER_INTERNAL);
    assertTrue(msg.contains("Suggestion:"));
    assertTrue(msg.contains("server error"));
  }

  @Test
  void suggestion_dataLoss_includesSuggestion() {
    String msg = ErrorMessageBuilder.build("op", null, "err", ErrorCode.DATA_LOSS);
    assertTrue(msg.contains("Suggestion:"));
    assertTrue(msg.contains("server"));
  }

  @Test
  void suggestion_serverUnimplemented_includesSuggestion() {
    String msg = ErrorMessageBuilder.build("op", null, "err", ErrorCode.SERVER_UNIMPLEMENTED);
    assertTrue(msg.contains("Suggestion:"));
    assertTrue(msg.contains("not supported"));
  }

  @Test
  void suggestion_streamBroken_includesSuggestion() {
    String msg = ErrorMessageBuilder.build("op", null, "err", ErrorCode.STREAM_BROKEN);
    assertTrue(msg.contains("Suggestion:"));
    assertTrue(msg.contains("broken"));
  }

  @Test
  void noSuggestion_forCodesWithoutSuggestions() {
    assertFalse(
        ErrorMessageBuilder.build("op", null, "err", ErrorCode.HANDLER_ERROR)
            .contains("Suggestion:"));
    assertFalse(
        ErrorMessageBuilder.build("op", null, "err", ErrorCode.CANCELLED_BY_CLIENT)
            .contains("Suggestion:"));
    assertFalse(
        ErrorMessageBuilder.build("op", null, "err", ErrorCode.CANCELLED_BY_SERVER)
            .contains("Suggestion:"));
    assertFalse(
        ErrorMessageBuilder.build("op", null, "err", ErrorCode.PARTIAL_FAILURE)
            .contains("Suggestion:"));
    assertFalse(
        ErrorMessageBuilder.build("op", null, "err", ErrorCode.RETRY_THROTTLED)
            .contains("Suggestion:"));
    assertFalse(
        ErrorMessageBuilder.build("op", null, "err", ErrorCode.ABORTED).contains("Suggestion:"));
  }

  @Test
  void build_messageFormatMatchesSpec() {
    String msg =
        ErrorMessageBuilder.build(
            "SendEvent",
            "orders.created",
            "UNAVAILABLE: server shutting down",
            ErrorCode.UNAVAILABLE);

    assertTrue(msg.startsWith("SendEvent failed on channel \"orders.created\":"));
  }
}
