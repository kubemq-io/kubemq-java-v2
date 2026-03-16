# How To: Handle Errors and Retries

Understand the exception hierarchy, check retryability, and configure automatic retry policies.

## Exception Hierarchy

All SDK exceptions extend `KubeMQException` (unchecked). Key subtypes:

| Exception | Retryable | When |
|---|---|---|
| `ConnectionException` | Yes | Network failure, server unreachable |
| `ConnectionNotReadyException` | Yes | Connection in CONNECTING/RECONNECTING state |
| `KubeMQTimeoutException` | Yes | Operation exceeded deadline |
| `ServerException` | Depends | Server returned an error |
| `AuthenticationException` | No | Invalid/expired token, TLS cert rejected |
| `AuthorizationException` | No | Insufficient permissions |
| `ValidationException` | No | Bad input (empty channel, null message) |
| `ConfigurationException` | No | Invalid client options |
| `ClientClosedException` | No | Client already closed |

## Inspecting Exceptions

```java
import io.kubemq.sdk.exception.*;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.pubsub.EventMessage;

public class ErrorInspection {
    public static void main(String[] args) {
        try (PubSubClient client = PubSubClient.builder()
                .address("localhost:50000")
                .clientId("error-demo")
                .build()) {

            client.sendEventsMessage(EventMessage.builder()
                    .channel("demo.errors")
                    .body("hello".getBytes())
                    .build());

        } catch (KubeMQException e) {
            System.err.println("Code:      " + e.getCode());
            System.err.println("Operation: " + e.getOperation());
            System.err.println("Channel:   " + e.getChannel());
            System.err.println("Retryable: " + e.isRetryable());
            System.err.println("Category:  " + e.getCategory());
            System.err.println("Timestamp: " + e.getTimestamp());

            if (e instanceof AuthenticationException) {
                System.err.println("Auth failure — check token or certificates");
            } else if (e instanceof ConnectionException) {
                System.err.println("Connection issue — server may be down");
            }
        }
    }
}
```

## Manual Retry Logic

```java
import io.kubemq.sdk.exception.KubeMQException;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.pubsub.EventMessage;

public class ManualRetry {
    public static void main(String[] args) throws InterruptedException {
        try (PubSubClient client = PubSubClient.builder()
                .address("localhost:50000")
                .clientId("retry-demo")
                .build()) {

            EventMessage msg = EventMessage.builder()
                    .channel("demo.retry")
                    .body("important data".getBytes())
                    .build();

            int maxAttempts = 3;
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                try {
                    client.sendEventsMessage(msg);
                    System.out.println("Sent on attempt " + (attempt + 1));
                    break;
                } catch (KubeMQException e) {
                    if (!e.isRetryable() || attempt == maxAttempts - 1) {
                        throw e;
                    }
                    long delay = (long) (500 * Math.pow(2, attempt));
                    System.out.println("Retrying in " + delay + "ms...");
                    Thread.sleep(delay);
                }
            }
        }
    }
}
```

## Built-in RetryPolicy

The SDK includes a `RetryPolicy` with exponential backoff and jitter:

```java
import io.kubemq.sdk.retry.RetryPolicy;
import java.time.Duration;

RetryPolicy policy = RetryPolicy.builder()
        .maxRetries(5)
        .initialBackoff(Duration.ofMillis(500))
        .maxBackoff(Duration.ofSeconds(30))
        .multiplier(2.0)
        .jitterType(RetryPolicy.JitterType.FULL)
        .maxConcurrentRetries(10)
        .build();

// Compute backoff for a specific attempt
Duration delay = policy.computeBackoff(2);  // 3rd attempt
System.out.println("Backoff: " + delay.toMillis() + "ms");
```

## Connection State Monitoring

React to connectivity changes to pause/resume operations:

```java
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.client.ConnectionStateListener;
import io.kubemq.sdk.client.ConnectionState;

public class StateMonitoring {
    public static void main(String[] args) throws InterruptedException {
        PubSubClient client = PubSubClient.builder()
                .address("localhost:50000")
                .clientId("state-demo")
                .connectionStateListener(new ConnectionStateListener() {
                    @Override
                    public void onConnected() {
                        System.out.println("READY — resume operations");
                    }
                    @Override
                    public void onDisconnected() {
                        System.out.println("DISCONNECTED — pause operations");
                    }
                    @Override
                    public void onReconnecting() {
                        System.out.println("RECONNECTING...");
                    }
                    @Override
                    public void onReconnected() {
                        System.out.println("RECONNECTED — resume operations");
                    }
                    @Override
                    public void onClosed() {
                        System.out.println("CLOSED — client shut down");
                    }
                })
                .build();

        System.out.println("Current state: " + client.getConnectionState());
        Thread.sleep(30_000);
        client.close();
    }
}
```

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `ClientClosedException` after calling `.close()` | Using client after shutdown | Create a new client instance |
| `ConnectionNotReadyException` immediately | `waitForReady(false)` set | Set `.waitForReady(true)` or wait for READY state |
| `ValidationException` on send | Empty channel or null body | Validate inputs before calling SDK methods |
| Retry loop never succeeds | Non-retryable error being retried | Check `e.isRetryable()` before retrying |
