# Troubleshooting Guide

Common issues and solutions for the KubeMQ Java SDK.

## Table of Contents

1. [Cannot connect to KubeMQ server](#problem-cannot-connect-to-kubemq-server)
2. [Authentication failed](#problem-authentication-failed)
3. [Authorization denied](#problem-authorization-denied)
4. [Channel not found](#problem-channel-not-found)
5. [Message exceeds size limit](#problem-message-exceeds-size-limit)
6. [Operation timed out](#problem-operation-timed-out)
7. [Rate limited by server](#problem-rate-limited-by-server)
8. [Internal server error](#problem-internal-server-error)
9. [TLS handshake failed](#problem-tls-handshake-failed)
10. [Subscriber is not receiving messages](#problem-subscriber-is-not-receiving-messages)
11. [Queue messages keep redelivering](#problem-queue-messages-keep-redelivering)
12. [gRPC Status Code Mapping](#grpc-status-code-mapping)
13. [How to Enable Debug Logging](#how-to-enable-debug-logging)

---

## Problem: Cannot connect to KubeMQ server

**Error message:**
```
io.grpc.StatusRuntimeException: UNAVAILABLE: io exception
Channel to 'localhost:50000' not ready
```

**Cause:** The KubeMQ server is not running, the address is incorrect, or a firewall
is blocking the connection.

**Solution:**
1. Verify the KubeMQ server is running: `kubectl get pods -l app=kubemq`
2. Verify the address and port match the server configuration
3. Check for firewall rules blocking port 50000
4. If using Docker: `docker ps | grep kubemq`
5. Test connectivity: `telnet localhost 50000`

**Code example:**
```java
try {
    ServerInfo info = client.ping();
    System.out.println("Connected to: " + info.getHost());
} catch (ConnectionException e) {
    System.err.println("Connection failed: " + e.getMessage());
    // Check address and server status
}
```

**See also:** [Configuration](https://github.com/kubemq-io/kubemq-java-v2/blob/main/README.md#configuration), [ConnectionErrorHandlingExample](https://github.com/kubemq-io/kubemq-java-v2/tree/main/kubemq-java-example/src/main/java/io/kubemq/example/errorhandling/ConnectionErrorHandlingExample.java)

---

## Problem: Authentication failed

**Error message:**
```
io.grpc.StatusRuntimeException: UNAUTHENTICATED: invalid token
```

**Cause:** The auth token is missing, expired, or invalid.

**Solution:**
1. Verify the token is set in the client builder: `.authToken("your-token")`
2. Check the token has not expired
3. Verify the token matches the server's configured authentication
4. Ensure the token does not contain trailing whitespace or newlines
5. If using `CredentialProvider`, verify the provider returns a valid `TokenResult`

**Code example:**
```java
PubSubClient client = PubSubClient.builder()
    .address("localhost:50000")
    .clientId("my-service")
    .authToken("eyJhbGciOiJIUzI1NiJ9...")
    .build();
```

**See also:** [AuthTokenExample](https://github.com/kubemq-io/kubemq-java-v2/tree/main/kubemq-java-example/src/main/java/io/kubemq/example/config/AuthTokenExample.java)

---

## Problem: Authorization denied

**Error message:**
```
io.grpc.StatusRuntimeException: PERMISSION_DENIED: not allowed
```

**Cause:** The authenticated user does not have permission for the requested operation
or channel.

**Solution:**
1. Verify the user/token has access to the target channel
2. Check KubeMQ server ACL configuration
3. Ensure the operation (read/write) is permitted for this client
4. Verify the channel name is spelled correctly (ACL rules are channel-specific)

---

## Problem: Channel not found

**Error message:**
```
io.grpc.StatusRuntimeException: NOT_FOUND: channel not found
```

**Cause:** The target channel does not exist and auto-creation is not applicable for
this operation (e.g., subscribing to a store channel that has never had a publisher).

**Solution:**
1. Verify the channel name is spelled correctly (case-sensitive)
2. Create the channel explicitly before subscribing:
   ```java
   client.createEventsStoreChannel("my-channel");
   ```
3. For Events (non-store), channels are created on first publish automatically
4. For Queues, the channel is created when the first message is sent

---

## Problem: Message exceeds size limit

**Error message:**
```
io.grpc.StatusRuntimeException: RESOURCE_EXHAUSTED: message too large
```

**Cause:** The message body exceeds the server's configured maximum message size
(default: 100MB) or the client's `maxReceiveSize`.

**Solution:**
1. Check message body size before sending
2. Increase server-side limit if needed
3. Increase client-side receive limit:
   ```java
   PubSubClient client = PubSubClient.builder()
       .address("localhost:50000")
       .clientId("my-service")
       .maxReceiveSize(209715200) // 200MB
       .build();
   ```
4. Consider splitting large payloads across multiple messages

---

## Problem: Operation timed out

**Error message:**
```
io.grpc.StatusRuntimeException: DEADLINE_EXCEEDED: deadline exceeded after Xs
```

**Cause:** The server did not respond within the configured timeout. This can happen
with commands/queries when the handler is slow, or during queue poll with short wait times.

**Solution:**
1. Increase the operation timeout:
   ```java
   CommandMessage cmd = CommandMessage.builder()
       .channel("device.control")
       .body("restart".getBytes())
       .timeout(30000) // 30 seconds
       .build();
   ```
2. For queue polling, increase `pollWaitTimeoutInSeconds`
3. Check server-side processing time
4. Check network latency between client and server

---

## Problem: Rate limited by server

**Error message:**
```
io.grpc.StatusRuntimeException: RESOURCE_EXHAUSTED: rate limit exceeded
```

**Cause:** The client is sending messages faster than the server allows.

**Solution:**
1. Reduce send rate using application-level throttling
2. Use batch operations to group messages:
   ```java
   List<QueueMessage> batch = List.of(msg1, msg2, msg3);
   List<QueueSendResult> results = queuesClient.sendQueuesMessages(batch);
   ```
3. Check server-side rate limit configuration
4. Distribute load across multiple channels or clients

---

## Problem: Internal server error

**Error message:**
```
io.grpc.StatusRuntimeException: INTERNAL: internal server error
```

**Cause:** An unexpected error occurred on the KubeMQ server.

**Solution:**
1. Check KubeMQ server logs for details
2. Retry the operation -- internal errors are often transient
3. Verify the server version is compatible with the SDK version (see [COMPATIBILITY.md](https://github.com/kubemq-io/kubemq-java-v2/blob/main/COMPATIBILITY.md))
4. If persistent, restart the KubeMQ server and file a bug report

---

## Problem: TLS handshake failed

**Error message:**
```
io.netty.handler.ssl.NotSslRecordException: not an SSL/TLS record
javax.net.ssl.SSLHandshakeException: PKIX path building failed
```

**Cause:** TLS is misconfigured. Common causes: certificate file path is wrong,
certificate is expired, CA cert does not match server cert, connecting with TLS
to a non-TLS server (or vice versa).

**Solution:**
1. Verify certificate file paths exist and are readable
2. Check certificate expiration: `openssl x509 -in cert.pem -noout -dates`
3. Verify the CA certificate matches the server's certificate issuer
4. If server does not use TLS, remove `.tls(true)` from client configuration
5. If server uses self-signed certs, provide the CA cert:
   ```java
   PubSubClient client = PubSubClient.builder()
       .address("kubemq-server:50000")
       .clientId("my-service")
       .tls(true)
       .caCertFile("/path/to/ca.pem")
       .build();
   ```

**See also:** [TLSConnectionExample](https://github.com/kubemq-io/kubemq-java-v2/tree/main/kubemq-java-example/src/main/java/io/kubemq/example/config/TLSConnectionExample.java)

---

## Problem: Subscriber is not receiving messages

**Error message:** No error -- subscriber is silent.

**Cause:** Multiple possible causes: wrong channel name, subscriber started after
publisher, group name mismatch, or subscription callback error.

**Solution:**
1. Verify channel names match exactly between publisher and subscriber (case-sensitive)
2. For Events (non-store): subscriber must be running before publisher sends.
   Events are not persistent -- if no subscriber is active, the event is lost
3. For Events Store: use `EventsStoreType.StartFromFirst` or `EventsStoreType.StartAtSequence` to replay missed messages
4. Check that the `group` parameter matches if using group subscriptions
5. Verify the `onReceiveEventCallback` is not throwing exceptions silently
6. Add an `onErrorCallback` to catch subscription errors:
   ```java
   Subscription sub = client.subscribeToEvents(EventsSubscription.builder()
       .channel("my-channel")
       .onReceiveEventCallback(event ->
           System.out.println("Received: " + new String(event.getBody())))
       .onErrorCallback(err ->
           System.err.println("Subscription error: " + err.getMessage()))
       .build());
   ```

**See also:** [SubscribeToEventExample](https://github.com/kubemq-io/kubemq-java-v2/tree/main/kubemq-java-example/src/main/java/io/kubemq/example/pubsub/SubscribeToEventExample.java)

---

## Problem: Queue messages keep redelivering

**Error message:** No explicit error -- messages reappear in the queue.

**Cause:** Messages received from a queue were not acknowledged within the visibility
timeout, causing them to return to the queue for redelivery.

**Solution:**
1. Call `msg.ack()` after processing each message:
   ```java
   QueuesPollResponse response = client.receiveQueuesMessages(
       QueuesPollRequest.builder()
           .channel("tasks")
           .pollMaxMessages(10)
           .pollWaitTimeoutInSeconds(10)
           .build());
   for (QueueMessageReceived msg : response.getMessages()) {
       try {
           processMessage(msg);
           msg.ack();
       } catch (Exception e) {
           msg.reject();
       }
   }
   ```
2. Increase visibility timeout if processing takes longer than expected
3. Use `msg.reject()` to explicitly reject messages you cannot process
4. Check the dead letter queue for repeatedly failed messages
5. If processing is long, extend visibility with `msg.extendVisibility(seconds)`
6. Consider using `autoAckMessages(true)` if explicit ack is not needed

**See also:** [WaitingPullExample](https://github.com/kubemq-io/kubemq-java-v2/tree/main/kubemq-java-example/src/main/java/io/kubemq/example/queues/WaitingPullExample.java), [ReceiveMessageDLQ](https://github.com/kubemq-io/kubemq-java-v2/tree/main/kubemq-java-example/src/main/java/io/kubemq/example/queues/ReceiveMessageDLQ.java)

---

## gRPC Status Code Mapping

The SDK automatically maps gRPC `StatusRuntimeException` codes to typed SDK exceptions via `GrpcErrorMapper`. This table shows every gRPC status code and its corresponding SDK exception:

| gRPC Code | Value | SDK Exception | Category | Retryable | Typical Cause |
|-----------|-------|---------------|----------|-----------|---------------|
| `OK` | 0 | *(should never occur)* | — | — | — |
| `CANCELLED` | 1 | `OperationCancelledException` | Cancellation | No | Client cancelled the request |
| `CANCELLED` | 1 | `ConnectionException` | Transient | Yes | Server cancelled the request |
| `UNKNOWN` | 2 | `ConnectionException` | Transient | Yes | Unknown server-side error |
| `INVALID_ARGUMENT` | 3 | `ValidationException` | Validation | No | Invalid channel name, missing required field |
| `DEADLINE_EXCEEDED` | 4 | `KubeMQTimeoutException` | Timeout | Yes | Server too slow, network latency |
| `NOT_FOUND` | 5 | `KubeMQException` | Not Found | No | Channel does not exist |
| `ALREADY_EXISTS` | 6 | `ValidationException` | Validation | No | Resource already created |
| `PERMISSION_DENIED` | 7 | `AuthorizationException` | Authorization | No | Insufficient ACL permissions |
| `RESOURCE_EXHAUSTED` | 8 | `ThrottlingException` | Throttling | Yes | Rate limit exceeded, message too large |
| `FAILED_PRECONDITION` | 9 | `ValidationException` | Validation | No | Operation precondition not met |
| `ABORTED` | 10 | `ConnectionException` | Transient | Yes | Transaction conflict, retry needed |
| `OUT_OF_RANGE` | 11 | `ValidationException` | Validation | No | Sequence number out of range |
| `UNIMPLEMENTED` | 12 | `ServerException` | Fatal | No | Server does not support this operation |
| `INTERNAL` | 13 | `ServerException` | Fatal | No | Internal server error |
| `UNAVAILABLE` | 14 | `ConnectionException` | Transient | Yes | Server not running, network failure |
| `DATA_LOSS` | 15 | `ServerException` | Fatal | No | Unrecoverable data loss |
| `UNAUTHENTICATED` | 16 | `AuthenticationException` | Authentication | No | Invalid or expired auth token |

**Usage example** — handle errors by type rather than raw gRPC codes:
```java
try {
    client.sendEventsMessage(message);
} catch (ConnectionException e) {
    // Retryable: UNAVAILABLE, UNKNOWN, CANCELLED (server), ABORTED
    log.warn("Transient error, will retry: {}", e.getMessage());
} catch (KubeMQTimeoutException e) {
    // DEADLINE_EXCEEDED — increase timeout or check server load
    log.warn("Timeout: {}", e.getMessage());
} catch (ThrottlingException e) {
    // RESOURCE_EXHAUSTED — back off and retry
    log.warn("Rate limited: {}", e.getMessage());
} catch (AuthenticationException e) {
    // UNAUTHENTICATED — fix credentials
    log.error("Auth failed: {}", e.getMessage());
} catch (AuthorizationException e) {
    // PERMISSION_DENIED — check ACL
    log.error("Not authorized: {}", e.getMessage());
} catch (ValidationException e) {
    // INVALID_ARGUMENT, ALREADY_EXISTS, FAILED_PRECONDITION, OUT_OF_RANGE
    log.error("Invalid request: {}", e.getMessage());
} catch (ServerException e) {
    // UNIMPLEMENTED, INTERNAL, DATA_LOSS
    log.error("Server error: {}", e.getMessage());
} catch (KubeMQException e) {
    // Catch-all for any other SDK error
    log.error("SDK error (retryable={}): {}", e.isRetryable(), e.getMessage());
}
```

---

## How to Enable Debug Logging

The KubeMQ Java SDK uses [SLF4J](https://www.slf4j.org/) as its logging facade. All SDK loggers
use names under the `io.kubemq.sdk` package. To see debug output, configure your SLF4J backend.

### Option 1: Logback (recommended)

Add `logback.xml` (or `logback-test.xml` for tests) to `src/main/resources/`:

```xml
<configuration>
    <!-- Console appender -->
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Set KubeMQ SDK to DEBUG level -->
    <logger name="io.kubemq.sdk" level="DEBUG" />

    <!-- Root logger at INFO to avoid noise from other libraries -->
    <root level="INFO">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

Add the Logback dependency if not already present:
```xml
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.5.32</version>
</dependency>
```

### Option 2: java.util.logging (JUL) via SLF4J

If you use `java.util.logging`, add the SLF4J-JUL bridge and configure a `logging.properties`:

```properties
# logging.properties
io.kubemq.sdk.level=FINE
handlers=java.util.logging.ConsoleHandler
java.util.logging.ConsoleHandler.level=FINE
java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter
```

Load it with `-Djava.util.logging.config.file=logging.properties`.

### Option 3: Programmatic (custom KubeMQLogger)

You can provide your own `KubeMQLogger` implementation to the client builder:
```java
PubSubClient client = PubSubClient.builder()
    .address("localhost:50000")
    .clientId("debug-client")
    .logger(new Slf4jLoggerAdapter("io.kubemq.sdk"))  // or your custom impl
    .build();
```

### What to Expect at DEBUG Level

With DEBUG logging enabled, the SDK outputs:
- **Connection lifecycle:** channel construction, TLS certificate loading, reconnection attempts
- **Ping/keepalive:** periodic ping requests and results
- **Publish operations:** each event/queue message sent (channel, message ID)
- **Subscription events:** subscription creation, received messages (channel, event ID)
- **State transitions:** connection state machine changes (CONNECTING → READY, etc.)
- **Auth token updates:** token presence changes (not the token value itself)

At **TRACE** level (very verbose), you'll additionally see per-message payload details.

**Example DEBUG output:**
```
14:23:01.123 [main] DEBUG io.kubemq.sdk - Constructing channel to KubeMQ [address=dns:///localhost:50000]
14:23:01.456 [main] DEBUG io.kubemq.sdk - Client initialized for KubeMQ [address=localhost:50000, token_present=false]
14:23:01.789 [main] DEBUG io.kubemq.sdk - Pinging KubeMQ server [address=localhost:50000]
14:23:01.812 [main] DEBUG io.kubemq.sdk - Ping successful
14:23:02.100 [main] DEBUG io.kubemq.sdk - Event Message sent
```
