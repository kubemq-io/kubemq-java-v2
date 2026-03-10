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
