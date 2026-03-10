package io.kubemq.sdk.integration;

import io.kubemq.sdk.exception.AuthenticationException;
import io.kubemq.sdk.exception.KubeMQException;
import io.kubemq.sdk.pubsub.EventMessage;
import io.kubemq.sdk.pubsub.EventsSubscription;
import io.kubemq.sdk.pubsub.PubSubClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for auth failure scenarios per REQ-TEST-2.
 * Requires a KubeMQ server configured with auth token validation.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@Disabled("Requires running KubeMQ server with auth enabled")
class AuthFailureIT extends BaseIntegrationTest {

    @Test
    void sendWithInvalidToken_throwsAuthenticationException() {
        PubSubClient client = PubSubClient.builder()
            .address(kubemqAddress)
            .clientId(uniqueClientId("auth-fail"))
            .authToken("invalid-token-xyz")
            .build();

        try {
            EventMessage msg = EventMessage.builder()
                .channel(uniqueChannel("auth-fail"))
                .body("test".getBytes())
                .build();

            KubeMQException thrown = assertThrows(KubeMQException.class,
                () -> client.sendEventsMessage(msg));

            assertInstanceOf(AuthenticationException.class, thrown,
                "Auth failure should produce AuthenticationException, got: "
                    + thrown.getClass().getSimpleName());
        } finally {
            client.close();
        }
    }

    @Test
    void subscribeWithInvalidToken_throwsAuthenticationException() {
        PubSubClient client = PubSubClient.builder()
            .address(kubemqAddress)
            .clientId(uniqueClientId("auth-sub-fail"))
            .authToken("invalid-token-xyz")
            .build();

        try {
            EventsSubscription subscription = EventsSubscription.builder()
                .channel(uniqueChannel("auth-sub-fail"))
                .onReceiveEventCallback(event -> {})
                .onErrorCallback(err -> {})
                .build();

            assertThrows(KubeMQException.class,
                () -> client.subscribeToEvents(subscription),
                "Subscribe with invalid token should throw");
        } finally {
            client.close();
        }
    }

    @Test
    void sendWithoutToken_toAuthEnabledServer_throwsAuthenticationException() {
        PubSubClient client = PubSubClient.builder()
            .address(kubemqAddress)
            .clientId(uniqueClientId("auth-no-token"))
            .build();

        try {
            EventMessage msg = EventMessage.builder()
                .channel(uniqueChannel("auth-no-token"))
                .body("test".getBytes())
                .build();

            KubeMQException thrown = assertThrows(KubeMQException.class,
                () -> client.sendEventsMessage(msg));

            assertInstanceOf(AuthenticationException.class, thrown);
        } finally {
            client.close();
        }
    }
}
