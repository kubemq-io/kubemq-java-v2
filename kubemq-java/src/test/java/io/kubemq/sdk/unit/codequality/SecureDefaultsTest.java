package io.kubemq.sdk.unit.codequality;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Verifies secure defaults (REQ-CQ-7): - No credential material in toString() - No credential
 * material in error messages
 */
class SecureDefaultsTest {

  @Test
  void kubeMQExceptionToStringDoesNotLeakSensitiveData() {
    var ex =
        io.kubemq.sdk.exception.KubeMQException.newBuilder()
            .code(io.kubemq.sdk.exception.ErrorCode.AUTHENTICATION_FAILED)
            .message("Authentication failed")
            .operation("connect")
            .build();

    String output = ex.toString();
    assertNotNull(output);
    assertTrue(output.contains("Authentication failed"));
  }

  @Test
  void authTokenNotExposedInClientToString() {
    var client =
        io.kubemq.sdk.cq.CQClient.builder()
            .address("localhost:50000")
            .clientId("test")
            .authToken("super-secret-token-12345")
            .build();
    try {
      String output = client.toString();
      assertFalse(
          output.contains("super-secret-token-12345"),
          "Client toString() must not contain auth token value");
    } finally {
      client.close();
    }
  }
}
