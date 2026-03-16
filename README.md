# KubeMQ Java SDK

[![Maven Central](https://img.shields.io/maven-central/v/io.kubemq.sdk/kubemq-sdk-Java)](https://central.sonatype.com/artifact/io.kubemq.sdk/kubemq-sdk-Java)
[![CI](https://github.com/kubemq-io/kubemq-java-v2/actions/workflows/ci.yml/badge.svg)](https://github.com/kubemq-io/kubemq-java-v2/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/kubemq-io/kubemq-java-v2/branch/main/graph/badge.svg)](https://codecov.io/gh/kubemq-io/kubemq-java-v2)
[![Javadoc](https://javadoc.io/badge2/io.kubemq.sdk/kubemq-sdk-Java/javadoc.svg)](https://javadoc.io/doc/io.kubemq.sdk/kubemq-sdk-Java)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## Description

KubeMQ is a message queue and message broker designed for containerized workloads.
The KubeMQ Java SDK provides a type-safe client for all KubeMQ messaging patterns —
Events, Events Store, Commands, Queries, and Queues — over gRPC transport with
built-in TLS, authentication, and reconnection support.

> **Migrating from v1?** See [MIGRATION.md](https://github.com/kubemq-io/kubemq-java-v2/blob/main/MIGRATION.md) for the upgrade guide.

## Table of Contents

- [Installation](#installation)
  - [Prerequisites](#prerequisites)
  - [Maven](#maven)
  - [Gradle](#gradle)
- [Quick Start](#quick-start)
- [Messaging Patterns](#messaging-patterns)
  - [Quick Start: Events (Pub/Sub)](#quick-start-events-pubsub)
  - [Quick Start: Queues](#quick-start-queues)
  - [Quick Start: RPC (Commands & Queries)](#quick-start-rpc-commands--queries)
- [Configuration](#configuration)
- [Error Handling](#error-handling)
- [Troubleshooting](#troubleshooting)
- [Performance](#performance)
- [Compatibility](#compatibility)
- [Security](#security)
- [Additional Resources](#additional-resources)
- [Contributing](#contributing)
- [License](#license)

## Installation

### Prerequisites

- Java 11 or higher (LTS releases 11, 17, and 21 are tested in CI)
- KubeMQ server running (default: `localhost:50000`) — [install guide](https://docs.kubemq.io/getting-started/quick-start)
- Maven 3.6+ or Gradle 7+

### Maven

```xml
<dependency>
    <groupId>io.kubemq.sdk</groupId>
    <artifactId>kubemq-sdk-Java</artifactId>
    <version>2.1.1</version>
</dependency>
```

### Gradle

```groovy
implementation 'io.kubemq.sdk:kubemq-sdk-Java:2.1.1'
```

## Quick Start

The simplest way to send and receive a message:

```java
// Publish an event (fire-and-forget)
PubSubClient client = PubSubClient.builder()
    .address("localhost:50000")
    .clientId("quick-start")
    .build();

client.sendEventsMessage(EventMessage.builder()
    .channel("hello")
    .body("Hello KubeMQ!".getBytes())
    .build());

client.close();
```

See [Messaging Patterns](#messaging-patterns) below for per-pattern quick starts including subscribing.

## Messaging Patterns

| Pattern | Delivery Guarantee | Use When | Example Use Case |
|---------|--------------------|----------|------------------|
| Events | At-most-once | Fire-and-forget broadcasting to multiple subscribers | Real-time notifications, log streaming |
| Events Store | At-least-once (persistent) | Subscribers must not miss messages, even if offline | Audit trails, event sourcing, replay |
| Queues | At-least-once (with ack) | Work must be processed by exactly one consumer with acknowledgment | Job processing, task distribution |
| Commands | At-most-once (request/reply) | You need confirmation that an action was executed | Device control, configuration changes |
| Queries | At-most-once (request/reply) | You need to retrieve data from a responder | Data lookups, service-to-service reads |

### Quick Start: Events (Pub/Sub)

**Publish an event:**
```java
PubSubClient client = PubSubClient.builder()
    .address("localhost:50000")
    .clientId("events-sender")
    .build();
EventSendResult result = client.sendEventsMessage(EventMessage.builder()
    .channel("notifications")
    .body("Hello KubeMQ!".getBytes())
    .build());
System.out.println("Event sent: " + result.getId());
client.close();
```

**Subscribe to events:**
```java
PubSubClient client = PubSubClient.builder()
    .address("localhost:50000")
    .clientId("events-receiver")
    .build();
client.subscribeToEvents(EventsSubscription.builder()
    .channel("notifications")
    .onReceiveEventCallback(event ->
        System.out.println("Received: " + new String(event.getBody())))
    .onErrorCallback(err -> System.err.println("Error: " + err.getMessage()))
    .build());
Thread.sleep(30000);
client.close();
```

**Expected output (subscriber):**
```
Received: Hello KubeMQ!
```

### Quick Start: Queues

**Send a queue message:**
```java
QueuesClient client = QueuesClient.builder()
    .address("localhost:50000")
    .clientId("queue-sender")
    .build();
QueueSendResult result = client.sendQueuesMessage(QueueMessage.builder()
    .channel("tasks")
    .body("Process this job".getBytes())
    .build());
System.out.println("Sent, expired: " + result.isExpired());
client.close();
```

**Receive and acknowledge:**
```java
QueuesClient client = QueuesClient.builder()
    .address("localhost:50000")
    .clientId("queue-receiver")
    .build();
QueuesPollResponse response = client.receiveQueuesMessages(QueuesPollRequest.builder()
    .channel("tasks")
    .pollMaxMessages(1)
    .pollWaitTimeoutInSeconds(10)
    .build());
for (QueueMessageReceived msg : response.getMessages()) {
    System.out.println("Processing: " + new String(msg.getBody()));
    msg.ack();
}
client.close();
```

**Expected output (receiver):**
```
Processing: Process this job
```

### Quick Start: RPC (Commands & Queries)

**Handle a command (responder):**
```java
CQClient client = CQClient.builder()
    .address("localhost:50000")
    .clientId("command-handler")
    .build();
client.subscribeToCommands(CommandsSubscription.builder()
    .channel("device.control")
    .onReceiveCommandCallback(cmd -> {
        System.out.println("Executing: " + new String(cmd.getBody()));
        return CommandResponseMessage.builder()
            .requestId(cmd.getId())
            .isExecuted(true)
            .build();
    })
    .onErrorCallback(err -> System.err.println("Error: " + err.getMessage()))
    .build());
```

**Send a command (caller):**
```java
CQClient client = CQClient.builder()
    .address("localhost:50000")
    .clientId("command-sender")
    .build();
CommandResponseMessage response = client.sendCommandRequest(CommandMessage.builder()
    .channel("device.control")
    .body("restart".getBytes())
    .timeout(5000)
    .build());
System.out.println("Executed: " + response.isExecuted());
client.close();
```

**Expected output (sender):**
```
Executed: true
```

For more examples, see the [examples directory](https://github.com/kubemq-io/kubemq-java-v2/tree/main/kubemq-java-example).

## Configuration

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `address` | `String` | `localhost:50000` | KubeMQ server gRPC address (`host:port`). Falls back to `KUBEMQ_ADDRESS` env var. |
| `clientId` | `String` | Auto-generated UUID | Unique identifier for this client instance |
| `authToken` | `String` | `null` | JWT authentication token for server access |
| `tls` | `boolean` | `false` | Enable TLS encryption for the connection |
| `tlsCertFile` | `String` | `null` | Path to TLS certificate file (PEM format) |
| `tlsKeyFile` | `String` | `null` | Path to TLS private key file (PEM format) |
| `tlsCaCertFile` | `String` | `null` | Path to CA certificate for server verification |
| `maxReceiveSize` | `int` | `104857600` | Maximum inbound message size in bytes (100MB) |
| `reconnectIntervalSeconds` | `int` | `5` | Seconds between reconnection attempts |
| `logLevel` | `Level` | `INFO` | Logging level (TRACE, DEBUG, INFO, WARN, ERROR, OFF) |

**Example:**
```java
PubSubClient client = PubSubClient.builder()
    .address("kubemq-server:50000")
    .clientId("my-service")
    .authToken("eyJ...")
    .tls(true)
    .tlsCertFile("/certs/client.pem")
    .tlsKeyFile("/certs/client-key.pem")
    .tlsCaCertFile("/certs/ca.pem")
    .reconnectIntervalSeconds(10)
    .build();
```

## Error Handling

The SDK uses a typed exception hierarchy rooted at `KubeMQException`:

| Exception | Category | When |
|-----------|----------|------|
| `ConnectionException` | Retryable | Server unavailable, network failure |
| `KubeMQTimeoutException` | Retryable | Deadline exceeded, server too slow |
| `AuthenticationException` | Non-retryable | Invalid or expired auth token |
| `AuthorizationException` | Non-retryable | Insufficient permissions |
| `ValidationException` | Non-retryable | Invalid request parameters |
| `GRPCException` | Varies | gRPC transport errors |

```java
try {
    client.sendEventsMessage(message);
} catch (ConnectionException e) {
    log.warn("Connection failed (retryable): {}", e.getMessage());
} catch (AuthenticationException e) {
    log.error("Auth failed (fix credentials): {}", e.getMessage());
} catch (ValidationException e) {
    log.error("Invalid request: {}", e.getMessage());
} catch (KubeMQException e) {
    log.error("SDK error: {}", e.getMessage());
}
```

## Troubleshooting

| Problem | Likely Cause | Quick Fix |
|---------|-------------|-----------|
| `UNAVAILABLE: io exception` | Server not running or wrong address | Verify server is running and address is correct |
| `UNAUTHENTICATED: invalid token` | Missing or expired auth token | Check `.authToken()` value and expiry |
| Subscriber not receiving messages | Wrong channel name or subscriber started after publisher | Verify channel names match; for Events, subscriber must be running first |
| `RESOURCE_EXHAUSTED: message too large` | Message body exceeds 100MB default | Increase `maxReceiveSize` or split payload |
| `SSLHandshakeException` | TLS misconfiguration | Verify cert paths and expiry; ensure server TLS mode matches client |

For detailed solutions with code examples, see [TROUBLESHOOTING.md](https://github.com/kubemq-io/kubemq-java-v2/blob/main/TROUBLESHOOTING.md).

## Performance

| Characteristic | Value | Notes |
|---------------|-------|-------|
| Max message size | 100 MB | Configurable via `maxReceiveSize` |
| Connection model | Single gRPC channel | All operations multiplex over one HTTP/2 connection |
| Serialization | Protocol Buffers | Binary encoding for efficient wire format |
| Batch send | Supported | `sendQueuesMessages(List<QueueMessage>)` |
| Batch receive | Supported | `pollMaxMessages` in `QueuesPollRequest` |

**Tips:**
1. Reuse client instances — one client per pattern handles all operations efficiently
2. Use batch APIs for high-throughput queue workloads
3. Do not block subscription callbacks — offload heavy work to a separate executor
4. Close clients when done — all client classes implement `AutoCloseable`

See [BENCHMARKS.md](https://github.com/kubemq-io/kubemq-java-v2/blob/main/kubemq-java/BENCHMARKS.md) for JMH benchmark results.

## Compatibility

| Java Version | Status |
|-------------|--------|
| Java 11 (LTS) | Supported (minimum, compile target) |
| Java 17 (LTS) | Supported (tested in CI) |
| Java 21 (LTS) | Supported (tested in CI) |

See [COMPATIBILITY.md](https://github.com/kubemq-io/kubemq-java-v2/blob/main/COMPATIBILITY.md) for the full SDK-to-server version matrix.

## Security

See [SECURITY.md](SECURITY.md) for vulnerability reporting. The SDK supports TLS and mTLS connections — for configuration details, see [How to Connect with TLS](docs/how-to/connect-with-tls.md).

## Additional Resources

- [KubeMQ Documentation](https://docs.kubemq.io/) — Official KubeMQ documentation and guides
- [Full Documentation Index](docs/INDEX.md) — Complete SDK documentation index
- [KubeMQ Concepts](docs/CONCEPTS.md) — Core KubeMQ messaging concepts
- [SDK Feature Parity Matrix](../sdk-feature-parity-matrix.md) — Cross-SDK feature comparison
- [CHANGELOG.md](https://github.com/kubemq-io/kubemq-java-v2/blob/main/kubemq-java/CHANGELOG.md) — Release history
- [MIGRATION.md](https://github.com/kubemq-io/kubemq-java-v2/blob/main/MIGRATION.md) — v1 to v2 migration guide
- [TROUBLESHOOTING.md](https://github.com/kubemq-io/kubemq-java-v2/blob/main/TROUBLESHOOTING.md) — Common issues and solutions
- [Examples](https://github.com/kubemq-io/kubemq-java-v2/tree/main/kubemq-java-example) — Runnable code examples for all patterns

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](https://github.com/kubemq-io/kubemq-java-v2/blob/main/kubemq-java/CONTRIBUTING.md) for guidelines on:
- Development setup and building
- Commit message format
- Pull request process
- Deprecation policy

## License

This project is licensed under the MIT License — see the [LICENSE](https://opensource.org/licenses/MIT) file for details.
