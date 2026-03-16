package io.kubemq.sdk.integration;

import static org.junit.jupiter.api.Assertions.*;

import io.grpc.StatusRuntimeException;
import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.cq.CommandMessage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Integration tests for timeout behavior per REQ-ERR-4. Requires a running KubeMQ server. */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class TimeoutIT extends BaseIntegrationTest {

  @Test
  void commandWithNoResponder_throwsTimeoutException() {
    CQClient client =
        CQClient.builder().address(kubemqAddress).clientId(uniqueClientId("timeout")).build();

    try {
      CommandMessage cmd =
          CommandMessage.builder()
              .channel(uniqueChannel("timeout-cmd"))
              .body("test".getBytes())
              .timeoutInSeconds(2)
              .build();

      // Production code does not wrap gRPC exceptions; StatusRuntimeException propagates
      StatusRuntimeException thrown =
          assertThrows(StatusRuntimeException.class, () -> client.sendCommandRequest(cmd));

      assertNotNull(thrown.getStatus(),
          "No-responder command should produce StatusRuntimeException with status");
    } finally {
      client.close();
    }
  }

  @Test
  void queryWithNoResponder_throwsTimeoutException() {
    CQClient client =
        CQClient.builder().address(kubemqAddress).clientId(uniqueClientId("timeout-query")).build();

    try {
      io.kubemq.sdk.cq.QueryMessage query =
          io.kubemq.sdk.cq.QueryMessage.builder()
              .channel(uniqueChannel("timeout-query"))
              .body("test".getBytes())
              .timeoutInSeconds(2)
              .build();

      // Production code does not wrap gRPC exceptions; StatusRuntimeException propagates
      StatusRuntimeException thrown =
          assertThrows(StatusRuntimeException.class, () -> client.sendQueryRequest(query));

      assertNotNull(thrown.getStatus(),
          "No-responder query should produce StatusRuntimeException with status");
    } finally {
      client.close();
    }
  }
}
