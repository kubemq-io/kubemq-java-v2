# Implementing CQRS with KubeMQ

This guide demonstrates a Command Query Responsibility Segregation (CQRS) architecture using KubeMQ's native messaging primitives: commands for writes, queries for reads, and events for state synchronization.

## Architecture

```
┌────────┐  command   ┌──────────────┐  event   ┌──────────────┐
│ Client │───────────▶│ Write Service │────────▶│ Read Service │
│        │            │ (cmd handler) │          │ (projection) │
│        │◀───────────│              │          │              │
│        │  query     └──────────────┘          └──────────────┘
│        │────────────────────────────────────▶│              │
│        │◀───────────────────────────────────│              │
└────────┘                                     └──────────────┘
```

1. **Commands** carry write intent — "create order", "update status". The write service processes commands and emits domain events.
2. **Events** propagate state changes to read-side projections asynchronously.
3. **Queries** retrieve data from the read-optimized projection, independent of the write model.

## Prerequisites

- KubeMQ server on `localhost:50000`
- Java 11+ and the `kubemq-java` dependency in your `pom.xml`

## Write Service — Command Handler

The write service subscribes to commands, validates them, applies business logic, and publishes domain events.

```java
import io.kubemq.sdk.cq.*;
import io.kubemq.sdk.pubsub.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WriteService {
    private static final Map<String, String> store = new ConcurrentHashMap<>();

    public static CommandsSubscription start(CQClient cq, PubSubClient pubsub) {
        CommandsSubscription sub = CommandsSubscription.builder()
                .channel("cqrs.commands")
                .onReceiveCommandCallback(cmd -> {
                    String body = new String(cmd.getBody());
                    String orderId = cmd.getTags().getOrDefault("order_id", "unknown");
                    System.out.println("[Write] Command: " + body);

                    store.put(orderId, body);

                    cq.sendResponseMessage(CommandResponseMessage.builder()
                            .commandReceived(cmd).isExecuted(true).build());

                    EventMessage event = EventMessage.builder()
                            .id(UUID.randomUUID().toString())
                            .channel("cqrs.events")
                            .body(cmd.getBody())
                            .metadata(orderId)
                            .build();
                    pubsub.sendEventsMessage(event);
                    System.out.println("[Write] Order " + orderId + " persisted, event emitted");
                })
                .onErrorCallback(err ->
                    System.err.println("[Write] Error: " + err.getMessage()))
                .build();

        cq.subscribeToCommands(sub);
        return sub;
    }
}
```

## Read Service — Query Handler with Event Projection

The read service maintains a denormalized projection updated by domain events, and serves queries against it.

```java
import io.kubemq.sdk.cq.*;
import io.kubemq.sdk.pubsub.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class ReadService {
    private static final Map<String, String> projection = new ConcurrentHashMap<>();

    public static void start(CQClient cq, PubSubClient pubsub) {
        EventsSubscription eventSub = EventsSubscription.builder()
                .channel("cqrs.events")
                .onReceiveEventCallback(event -> {
                    String key = event.getMetadata();
                    projection.put(key, new String(event.getBody()));
                    System.out.println("[Read] Projection updated: key=" + key);
                })
                .onErrorCallback(err ->
                    System.err.println("[Read] Event error: " + err.getMessage()))
                .build();
        pubsub.subscribeToEvents(eventSub);

        QueriesSubscription querySub = QueriesSubscription.builder()
                .channel("cqrs.queries")
                .onReceiveQueryCallback(q -> {
                    String key = new String(q.getBody());
                    String data = projection.getOrDefault(key, "");
                    boolean found = projection.containsKey(key);

                    String result = String.format(
                        "{\"found\":%b,\"data\":\"%s\"}", found, data);

                    cq.sendResponseMessage(QueryResponseMessage.builder()
                            .queryReceived(q).isExecuted(true)
                            .body(result.getBytes(StandardCharsets.UTF_8)).build());
                    System.out.println("[Read] Query served: key=" + key + " found=" + found);
                })
                .onErrorCallback(err ->
                    System.err.println("[Read] Query error: " + err.getMessage()))
                .build();
        cq.subscribeToQueries(querySub);
    }
}
```

## Client — Sending Commands and Queries

```java
import io.kubemq.sdk.cq.*;
import io.kubemq.sdk.pubsub.*;
import java.util.Map;

public class CqrsDemo {
    public static void main(String[] args) throws InterruptedException {
        CQClient cq = CQClient.builder()
                .address("localhost:50000").clientId("cqrs-cq").build();
        PubSubClient pubsub = PubSubClient.builder()
                .address("localhost:50000").clientId("cqrs-pubsub").build();

        cq.createCommandsChannel("cqrs.commands");
        cq.createQueriesChannel("cqrs.queries");
        pubsub.createEventsChannel("cqrs.events");

        WriteService.start(cq, pubsub);
        ReadService.start(cq, pubsub);
        Thread.sleep(1000);

        // Write via command
        Map<String, String> tags = Map.of("order_id", "ORD-001");
        CommandResponseMessage cmdResp = cq.sendCommandRequest(CommandMessage.builder()
                .channel("cqrs.commands")
                .body("{\"item\":\"widget\",\"qty\":5}".getBytes())
                .tags(tags).timeoutInSeconds(10).build());
        System.out.println("[Client] Command executed: " + cmdResp.isExecuted());

        Thread.sleep(500);

        // Read via query
        QueryResponseMessage queryResp = cq.sendQueryRequest(QueryMessage.builder()
                .channel("cqrs.queries")
                .body("ORD-001".getBytes())
                .timeoutInSeconds(10).build());
        System.out.println("[Client] Query result: " + new String(queryResp.getBody()));

        cq.close();
        pubsub.close();
    }
}
```

## Design Considerations

| Concern | Approach |
|---|---|
| **Consistency** | Eventually consistent — events propagate asynchronously to the read model |
| **Ordering** | Use events-store with sequence replay if strict ordering matters |
| **Durability** | Commands are request-reply; the write service persists before acking |
| **Scaling** | Read and write services scale independently via consumer groups |
| **Failure** | If the read service misses events, replay from events-store |

## When to Use This Pattern

- Systems where read and write workloads have different scaling requirements
- Domain models that benefit from separate write validation and read optimization
- Microservices that need event-driven state synchronization across bounded contexts
