# Getting Started with Events Store in KubeMQ Java SDK

In this tutorial, you'll build a persistent event publisher and subscriber using KubeMQ's `PubSubClient` and Events Store. By the end, you'll understand how stored events differ from ephemeral events and when to use them for replay and audit trails.

## What You'll Build

A user-activity log where events are persisted in the store. Subscribers can connect *after* events are published and still receive them — unlike regular events, which are lost if nobody is listening.

## Prerequisites

- **Java 11+** installed (`java --version`)
- **KubeMQ server** running on `localhost:50000` ([quickstart guide](https://docs.kubemq.io/getting-started/quick-start))
- **Maven** or **Gradle** for dependency management

Add the SDK dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.kubemq</groupId>
    <artifactId>kubemq-sdk-java</artifactId>
    <version>2.0.0</version>
</dependency>
```

## Step 1 — Connect to the KubeMQ Server

Create a `PubSubClient` and verify connectivity. The same client is used for both regular events and events-store channels.

```java
package com.example.eventsstore;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class PersistentPubSubExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-eventsstore-persistent-pubsub-client";
    private static final String CHANNEL = "user.activity";

    public static void main(String[] args) throws InterruptedException {
        PubSubClient client = PubSubClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID)
                .build();

        ServerInfo info = client.ping();
        System.out.println("Connected to: " + info.getHost());
```

## Step 2 — Create the Events Store Channel

Events Store channels are persistent — they must be created explicitly. Creating a channel is idempotent.

```java
        client.createEventsStoreChannel(CHANNEL);
```

## Step 3 — Subscribe to Stored Events

Subscribe with `EventsStoreType.StartNewOnly` to receive only events published after the subscription starts. Other options include `StartFromFirst` (replay all) and `StartFromLast` (only new).

```java
        CountDownLatch latch = new CountDownLatch(3);

        Consumer<EventStoreMessageReceived> onReceive = event -> {
            System.out.println("Received stored event:");
            System.out.println("  ID: " + event.getId());
            System.out.println("  Sequence: " + event.getSequence());
            System.out.println("  Body: " + new String(event.getBody()));
            latch.countDown();
        };

        EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                .channel(CHANNEL)
                .eventsStoreType(EventsStoreType.StartNewOnly)
                .onReceiveEventCallback(onReceive)
                .onErrorCallback(err -> System.err.println("Error: " + err.getMessage()))
                .build();

        client.subscribeToEventsStore(subscription);
        System.out.println("Subscribed to events store: " + CHANNEL);
        Thread.sleep(500);
```

## Step 4 — Send Persistent Events

Build `EventStoreMessage` with channel, body, optional metadata, and tags. Each message is persisted and can be replayed by subscribers.

```java
        for (int i = 1; i <= 3; i++) {
            Map<String, String> tags = new HashMap<>();
            tags.put("sequence", String.valueOf(i));

            EventStoreMessage message = EventStoreMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .channel(CHANNEL)
                    .metadata("Persistent event")
                    .body(("Stored event #" + i).getBytes())
                    .tags(tags)
                    .build();

            EventSendResult result = client.sendEventsStoreMessage(message);
            System.out.println("Sent event #" + i + " (sent=" + result.isSent() + ")");
        }
```

## Step 5 — Wait and Clean Up

```java
        latch.await(5, TimeUnit.SECONDS);

        subscription.cancel();
        client.deleteEventsStoreChannel(CHANNEL);
        client.close();
        System.out.println("\nPersistent pub/sub example completed.");
    }
}
```

## Complete Program

```java
package com.example.eventsstore;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class PersistentPubSubExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-eventsstore-persistent-pubsub-client";
    private static final String CHANNEL = "user.activity";

    public static void main(String[] args) throws InterruptedException {
        PubSubClient client = PubSubClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID)
                .build();

        ServerInfo info = client.ping();
        System.out.println("Connected to: " + info.getHost());

        client.createEventsStoreChannel(CHANNEL);

        CountDownLatch latch = new CountDownLatch(3);

        Consumer<EventStoreMessageReceived> onReceive = event -> {
            System.out.println("Received stored event:");
            System.out.println("  ID: " + event.getId());
            System.out.println("  Sequence: " + event.getSequence());
            System.out.println("  Body: " + new String(event.getBody()));
            latch.countDown();
        };

        EventsStoreSubscription subscription = EventsStoreSubscription.builder()
                .channel(CHANNEL)
                .eventsStoreType(EventsStoreType.StartNewOnly)
                .onReceiveEventCallback(onReceive)
                .onErrorCallback(err -> System.err.println("Error: " + err.getMessage()))
                .build();

        client.subscribeToEventsStore(subscription);
        System.out.println("Subscribed to events store: " + CHANNEL);
        Thread.sleep(500);

        for (int i = 1; i <= 3; i++) {
            Map<String, String> tags = new HashMap<>();
            tags.put("sequence", String.valueOf(i));

            EventStoreMessage message = EventStoreMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .channel(CHANNEL)
                    .metadata("Persistent event")
                    .body(("Stored event #" + i).getBytes())
                    .tags(tags)
                    .build();

            EventSendResult result = client.sendEventsStoreMessage(message);
            System.out.println("Sent event #" + i + " (sent=" + result.isSent() + ")");
        }

        latch.await(5, TimeUnit.SECONDS);

        subscription.cancel();
        client.deleteEventsStoreChannel(CHANNEL);
        client.close();
        System.out.println("\nPersistent pub/sub example completed.");
    }
}
```

## Expected Output

```
Connected to: localhost
Subscribed to events store: user.activity
Sent event #1 (sent=true)
Sent event #2 (sent=true)
Sent event #3 (sent=true)
Received stored event:
  ID: <message-id>
  Sequence: <sequence>
  Body: Stored event #1
Received stored event:
  ID: <message-id>
  Sequence: <sequence>
  Body: Stored event #2
Received stored event:
  ID: <message-id>
  Sequence: <sequence>
  Body: Stored event #3

Persistent pub/sub example completed.
```

## Events Store vs Regular Events

| Feature | Events | Events Store |
|---------|--------|--------------|
| Persistence | Ephemeral | Stored |
| Replay | No | Yes (StartFromFirst, StartAtSequence, etc.) |
| Subscriber timing | Must be connected before publish | Can connect after publish |
| Use case | Real-time notifications | Audit logs, replay, late joiners |

## Next Steps

- **[Getting Started with Events](../../../docs/tutorials/getting-started-events.md)** — ephemeral fire-and-forget messaging
- **[Building a Task Queue](../../../docs/tutorials/building-a-task-queue.md)** — guaranteed delivery with acknowledgment
- **Consumer Groups** — load-balance events store across multiple subscribers
