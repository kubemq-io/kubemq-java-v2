# Getting Started with Events in KubeMQ Java SDK

In this tutorial, you'll build a real-time event publisher and subscriber using KubeMQ's `PubSubClient`. By the end, you'll understand how fire-and-forget messaging works and when to choose events over queues.

## What You'll Build

A live notification system where a publisher sends user-signup events and a subscriber processes them in real time. Events are ephemeral — subscribers only receive messages while they're connected.

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

Every interaction starts with a client connection. The `PubSubClient` is purpose-built for events and events-store channels, keeping the API surface minimal.

```java
package com.example.notifications;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class NotificationSystem {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "notification-service";
    private static final String CHANNEL = "user.signups";

    public static void main(String[] args) throws InterruptedException {
        PubSubClient client = PubSubClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID)
                .build();

        ServerInfo info = client.ping();
        System.out.println("Connected to KubeMQ at: " + info.getHost());
```

The `ping()` call verifies connectivity before you proceed. If the server is unreachable, you'll get an immediate error rather than a confusing failure later.

## Step 2 — Create the Events Channel

Channels are lightweight — creating one is idempotent, so calling this multiple times is safe. It ensures the channel exists before you try to subscribe.

```java
        client.createEventsChannel(CHANNEL);
        System.out.println("Channel '" + CHANNEL + "' is ready");
```

## Step 3 — Subscribe to Events

Subscribers must connect *before* publishers send, because events are not persisted. This is the key difference from queues and events-store: if nobody is listening, the message is lost.

```java
        CountDownLatch latch = new CountDownLatch(3);

        Consumer<EventMessageReceived> onReceive = event -> {
            String body = new String(event.getBody());
            System.out.println("[Subscriber] New signup received:");
            System.out.println("  User: " + body);
            System.out.println("  Tags: " + event.getTags());
            latch.countDown();
        };

        Consumer<io.kubemq.sdk.exception.KubeMQException> onError = error -> {
            System.err.println("[Subscriber] Error: " + error.getMessage());
        };

        EventsSubscription subscription = EventsSubscription.builder()
                .channel(CHANNEL)
                .onReceiveEventCallback(onReceive)
                .onErrorCallback(onError)
                .build();

        client.subscribeToEvents(subscription);
        System.out.println("Listening for signup events...");
```

The `CountDownLatch` lets us wait for exactly 3 events before shutting down — a clean way to coordinate the publisher and subscriber in a single process.

## Step 4 — Publish Events

With the subscriber connected, it's time to send events. Each event carries a body (the payload), optional metadata, and key-value tags for filtering or routing.

```java
        Thread.sleep(500);

        String[] newUsers = {"alice@example.com", "bob@example.com", "carol@example.com"};

        for (int i = 0; i < newUsers.length; i++) {
            Map<String, String> tags = new HashMap<>();
            tags.put("source", "registration-api");
            tags.put("sequence", String.valueOf(i + 1));

            EventMessage message = EventMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .channel(CHANNEL)
                    .metadata("signup-service")
                    .body(newUsers[i].getBytes())
                    .tags(tags)
                    .build();

            client.sendEventsMessage(message);
            System.out.println("[Publisher] Sent signup event for: " + newUsers[i]);
        }
```

The short `Thread.sleep(500)` gives the subscription time to register on the server before we start publishing. In production, your publisher and subscriber would typically be separate processes.

## Step 5 — Wait and Clean Up

```java
        boolean allReceived = latch.await(5, TimeUnit.SECONDS);
        System.out.println(allReceived ? "\nAll events received!" : "\nTimed out waiting for events");

        subscription.cancel();
        client.deleteEventsChannel(CHANNEL);
        client.close();
        System.out.println("Notification system shut down.");
    }
}
```

Cancelling the subscription stops the background listener. Deleting the channel is optional but keeps your KubeMQ server tidy during development.

## Complete Program

```java
package com.example.notifications;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class NotificationSystem {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "notification-service";
    private static final String CHANNEL = "user.signups";

    public static void main(String[] args) throws InterruptedException {
        PubSubClient client = PubSubClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID)
                .build();

        ServerInfo info = client.ping();
        System.out.println("Connected to KubeMQ at: " + info.getHost());

        client.createEventsChannel(CHANNEL);
        System.out.println("Channel '" + CHANNEL + "' is ready");

        CountDownLatch latch = new CountDownLatch(3);

        Consumer<EventMessageReceived> onReceive = event -> {
            String body = new String(event.getBody());
            System.out.println("[Subscriber] New signup received:");
            System.out.println("  User: " + body);
            System.out.println("  Tags: " + event.getTags());
            latch.countDown();
        };

        Consumer<io.kubemq.sdk.exception.KubeMQException> onError = error -> {
            System.err.println("[Subscriber] Error: " + error.getMessage());
        };

        EventsSubscription subscription = EventsSubscription.builder()
                .channel(CHANNEL)
                .onReceiveEventCallback(onReceive)
                .onErrorCallback(onError)
                .build();

        client.subscribeToEvents(subscription);
        System.out.println("Listening for signup events...");

        Thread.sleep(500);

        String[] newUsers = {"alice@example.com", "bob@example.com", "carol@example.com"};

        for (int i = 0; i < newUsers.length; i++) {
            Map<String, String> tags = new HashMap<>();
            tags.put("source", "registration-api");
            tags.put("sequence", String.valueOf(i + 1));

            EventMessage message = EventMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .channel(CHANNEL)
                    .metadata("signup-service")
                    .body(newUsers[i].getBytes())
                    .tags(tags)
                    .build();

            client.sendEventsMessage(message);
            System.out.println("[Publisher] Sent signup event for: " + newUsers[i]);
        }

        boolean allReceived = latch.await(5, TimeUnit.SECONDS);
        System.out.println(allReceived ? "\nAll events received!" : "\nTimed out waiting for events");

        subscription.cancel();
        client.deleteEventsChannel(CHANNEL);
        client.close();
        System.out.println("Notification system shut down.");
    }
}
```

## Expected Output

```
Connected to KubeMQ at: localhost
Channel 'user.signups' is ready
Listening for signup events...
[Publisher] Sent signup event for: alice@example.com
[Publisher] Sent signup event for: bob@example.com
[Publisher] Sent signup event for: carol@example.com
[Subscriber] New signup received:
  User: alice@example.com
  Tags: {source=registration-api, sequence=1}
[Subscriber] New signup received:
  User: bob@example.com
  Tags: {source=registration-api, sequence=2}
[Subscriber] New signup received:
  User: carol@example.com
  Tags: {source=registration-api, sequence=3}

All events received!
Notification system shut down.
```

## Error Handling

Common issues and how to handle them:

| Error | Cause | Fix |
|-------|-------|-----|
| `Connection refused` | KubeMQ server not running | Start the server: `docker run -p 50000:50000 kubemq/kubemq` |
| `Subscriber missed events` | Subscriber connected after publisher sent | Always subscribe before publishing |
| `KubeMQException` | Network interruption | Wrap client operations in try-catch; rebuild the client on disconnect |

For production, wrap your subscriber setup in a retry loop:

```java
int retries = 3;
for (int attempt = 1; attempt <= retries; attempt++) {
    try {
        client.subscribeToEvents(subscription);
        break;
    } catch (Exception e) {
        System.err.println("Attempt " + attempt + " failed: " + e.getMessage());
        if (attempt == retries) throw e;
        Thread.sleep(1000 * attempt);
    }
}
```

## Next Steps

- **[Building a Task Queue](building-a-task-queue.md)** — guaranteed delivery with acknowledgment
- **[Request-Reply with Commands](request-reply-with-commands.md)** — synchronous command execution
- **Events Store** — persistent events with replay from any point in time
- **Consumer Groups** — load-balance events across multiple subscribers
