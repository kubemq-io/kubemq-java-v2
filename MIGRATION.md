# Migration Guide

## Migrating from v1.x to v2.x

This guide covers breaking changes and the upgrade procedure for migrating from the
KubeMQ Java SDK v1 (`io.kubemq:kubemq-java-sdk`) to v2 (`io.kubemq.sdk:kubemq-sdk-Java`).

### Breaking Changes Summary

| Area | v1 | v2 | Action Required |
|------|----|----|-----------------|
| Maven Coordinates | `io.kubemq:kubemq-java-sdk` | `io.kubemq.sdk:kubemq-sdk-Java` | Update `pom.xml` dependency |
| Package Imports | `io.kubemq.sdk.event.*`, `io.kubemq.sdk.commandquery.*`, `io.kubemq.sdk.queue.*` | `io.kubemq.sdk.pubsub.*`, `io.kubemq.sdk.cq.*`, `io.kubemq.sdk.queues.*` | Update all imports |
| Client Creation | Constructor-based, one channel object per operation | Builder pattern, dedicated client classes | Rewrite client init |
| Events API | `Channel.SendEvent(Event)` | `PubSubClient.sendEventsMessage(EventMessage)` | Rename methods |
| Queues API | `Queue.SendQueueMessage(Message)` | `QueuesClient.sendQueuesMessage(QueueMessage)` | Rename methods |
| CQ API | `Command.Send()` / `Query.Send()` | `CQClient.sendCommandRequest()` / `CQClient.sendQueryRequest()` | Rename methods |
| Connection | Manual channel management | Auto-reconnection built-in | Remove manual reconnect code |
| Java Version | Java 8+ | Java 11+ | Upgrade JDK if needed |
| Transport | REST + gRPC | gRPC only | Migrate REST usage to gRPC |

### Step-by-Step Upgrade Procedure

#### Step 1: Update Maven Dependency

**Before (v1):**
```xml
<dependency>
    <groupId>io.kubemq</groupId>
    <artifactId>kubemq-java-sdk</artifactId>
    <version>1.x.x</version>
</dependency>
```

**After (v2):**
```xml
<dependency>
    <groupId>io.kubemq.sdk</groupId>
    <artifactId>kubemq-sdk-Java</artifactId>
    <version>2.1.1</version>
</dependency>
```

#### Step 2: Update Client Initialization

**Before (v1):**
```java
import io.kubemq.sdk.event.Channel;

// v1: Constructor-based, separate channel object per operation
Channel eventChannel = new Channel(
    "my-channel", "client-id", false, serverAddress, null);
```

**After (v2):**
```java
import io.kubemq.sdk.pubsub.PubSubClient;

// v2: Builder pattern, single client handles all pub/sub operations
PubSubClient client = PubSubClient.builder()
    .address("localhost:50000")
    .clientId("client-id")
    .build();
```

#### Step 3: Update Event Operations

**Before (v1):**
```java
import io.kubemq.sdk.event.Event;

Event event = new Event();
event.setBody("Hello".getBytes());
event.setChannel("my-channel");
event.setClientID("client-id");
eventChannel.SendEvent(event);
```

**After (v2):**
```java
import io.kubemq.sdk.pubsub.EventMessage;

client.sendEventsMessage(EventMessage.builder()
    .channel("my-channel")
    .body("Hello".getBytes())
    .build());
```

#### Step 4: Update Queue Operations

**Before (v1):**
```java
import io.kubemq.sdk.queue.Queue;
import io.kubemq.sdk.queue.Message;

Queue queue = new Queue("tasks", "client-id", serverAddress);
Message msg = new Message();
msg.setBody("process this".getBytes());
queue.SendQueueMessage(msg);
```

**After (v2):**
```java
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.queues.QueueMessage;

QueuesClient client = QueuesClient.builder()
    .address("localhost:50000")
    .clientId("client-id")
    .build();

client.sendQueuesMessage(QueueMessage.builder()
    .channel("tasks")
    .body("process this".getBytes())
    .build());
```

#### Step 5: Update Command/Query Operations

**Before (v1):**
```java
import io.kubemq.sdk.commandquery.ChannelParameters;
import io.kubemq.sdk.commandquery.Request;
import io.kubemq.sdk.commandquery.Response;

ChannelParameters params = new ChannelParameters();
params.setChannelName("device.control");
params.setClientID("client-id");
params.setRequestType(RequestType.Command);

Request request = new Request();
request.setBody("restart".getBytes());
Response response = channel.SendRequest(request);
```

**After (v2):**
```java
import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.cq.CommandMessage;
import io.kubemq.sdk.cq.CommandResponseMessage;

CQClient client = CQClient.builder()
    .address("localhost:50000")
    .clientId("client-id")
    .build();

CommandResponseMessage response = client.sendCommandRequest(CommandMessage.builder()
    .channel("device.control")
    .body("restart".getBytes())
    .timeout(5000)
    .build());
```

#### Step 6: Update Subscription Handling

**Before (v1):**
```java
// v1: Listener interface
eventChannel.SubscribeToEvents(new SubscribeRequest("my-channel"),
    event -> {
        System.out.println(new String(event.getBody()));
    });
```

**After (v2):**
```java
import io.kubemq.sdk.pubsub.EventsSubscription;
import io.kubemq.sdk.client.Subscription;

// v2: Builder-based subscription with callback, returns handle
Subscription sub = client.subscribeToEvents(EventsSubscription.builder()
    .channel("my-channel")
    .onReceiveEventCallback(event ->
        System.out.println(new String(event.getBody())))
    .onErrorCallback(err ->
        System.err.println("Error: " + err.getMessage()))
    .build());

// Cancel when done
sub.cancel();
```

#### Step 7: Update Error Handling

**Before (v1):**
```java
try {
    // v1: Generic ServerException
    channel.SendEvent(event);
} catch (ServerException e) {
    System.err.println(e.getMessage());
}
```

**After (v2):**
```java
import io.kubemq.sdk.exception.GRPCException;
import io.kubemq.sdk.exception.ValidationException;

try {
    client.sendEventsMessage(message);
} catch (ValidationException e) {
    // Invalid parameters (channel, body, etc.)
    System.err.println("Validation: " + e.getMessage());
} catch (GRPCException e) {
    // Server communication error
    System.err.println("gRPC error: " + e.getMessage());
}
```

#### Step 8: Remove Manual Reconnection Code

v2 handles reconnection automatically. Remove any custom reconnection logic:

```java
// v2: Reconnection is built-in, configure via builder
PubSubClient client = PubSubClient.builder()
    .address("localhost:50000")
    .clientId("my-service")
    .reconnectIntervalSeconds(5)
    .build();

// No manual reconnect needed — the SDK handles it
```

### Removed Features

| Feature | v1 | v2 | Alternative |
|---------|----|----|-------------|
| REST transport | Supported | Removed (gRPC only) | Use gRPC (default) |
| Manual channel management | Required | Automatic | Client handles channels |
| Per-channel client objects | Required | Not needed | Single client per pattern |

### New Features in v2

| Feature | Description |
|---------|-------------|
| Builder pattern | Type-safe configuration with `.builder()` |
| Auto-reconnection | Built-in reconnection with configurable interval and message buffering |
| TLS / mTLS support | Native TLS/mTLS support via builder configuration |
| Dead Letter Queue | Built-in DLQ support for queues |
| Delayed messages | Queue messages with delivery delay |
| Visibility timeout | Processing time window for queue messages |
| Group subscriptions | Load-balanced subscription groups |
| Batch operations | Send multiple queue messages at once |
| Subscription handles | `Subscription` object returned for lifecycle management |
| Async API | `*Async` methods returning `CompletableFuture` on all clients |
| OpenTelemetry | Optional tracing and metrics with W3C context propagation |
| Typed exceptions | `KubeMQException` hierarchy with error codes and categories |

> **Note:** If you encounter issues migrating from v1.x, please open a
> [GitHub issue](https://github.com/kubemq-io/kubemq-java-v2/issues).
