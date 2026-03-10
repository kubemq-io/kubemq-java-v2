package io.kubemq.sdk.integration;

import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.cq.CommandMessage;
import io.kubemq.sdk.exception.KubeMQException;
import io.kubemq.sdk.exception.KubeMQTimeoutException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for timeout behavior per REQ-ERR-4.
 * Requires a running KubeMQ server.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@Disabled("Requires running KubeMQ server")
class TimeoutIT extends BaseIntegrationTest {

    @Test
    void commandWithNoResponder_throwsTimeoutException() {
        CQClient client = CQClient.builder()
            .address(kubemqAddress)
            .clientId(uniqueClientId("timeout"))
            .build();

        try {
            CommandMessage cmd = CommandMessage.builder()
                .channel(uniqueChannel("timeout-cmd"))
                .body("test".getBytes())
                .timeoutInSeconds(2)
                .build();

            KubeMQException thrown = assertThrows(KubeMQException.class,
                () -> client.sendCommandRequest(cmd));

            assertInstanceOf(KubeMQTimeoutException.class, thrown,
                "No-responder command should produce KubeMQTimeoutException, got: "
                    + thrown.getClass().getSimpleName());
        } finally {
            client.close();
        }
    }

    @Test
    void queryWithNoResponder_throwsTimeoutException() {
        CQClient client = CQClient.builder()
            .address(kubemqAddress)
            .clientId(uniqueClientId("timeout-query"))
            .build();

        try {
            io.kubemq.sdk.cq.QueryMessage query = io.kubemq.sdk.cq.QueryMessage.builder()
                .channel(uniqueChannel("timeout-query"))
                .body("test".getBytes())
                .timeoutInSeconds(2)
                .build();

            KubeMQException thrown = assertThrows(KubeMQException.class,
                () -> client.sendQueryRequest(query));

            assertInstanceOf(KubeMQTimeoutException.class, thrown,
                "No-responder query should produce KubeMQTimeoutException");
        } finally {
            client.close();
        }
    }
}
