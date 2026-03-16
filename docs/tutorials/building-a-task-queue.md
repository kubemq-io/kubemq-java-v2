# Building a Task Queue with KubeMQ Java SDK

In this tutorial, you'll build a reliable task queue using KubeMQ's `QueuesClient`. Unlike events, queued messages persist until a consumer explicitly acknowledges them — making queues ideal for work that must not be lost.

## What You'll Build

An image-processing pipeline where a producer enqueues resize jobs and a worker pulls them one at a time, processes each, and acknowledges or rejects based on the outcome.

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

## Step 1 — Create the Queue Client

The `QueuesClient` implements `AutoCloseable`, so you can use try-with-resources to guarantee cleanup — even if your code throws an exception partway through.

```java
package com.example.taskqueue;

import io.kubemq.sdk.queues.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ImageProcessingQueue {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "image-processor";
    private static final String CHANNEL = "jobs.image-resize";

    public static void main(String[] args) {
        try (QueuesClient client = QueuesClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID)
                .build()) {

            client.createQueuesChannel(CHANNEL);
            System.out.println("Queue '" + CHANNEL + "' is ready");
```

Using try-with-resources is idiomatic Java for resources that need deterministic cleanup. The connection closes automatically when the block exits.

## Step 2 — Enqueue Tasks

Each queue message has a body (the work payload), optional metadata, and key-value tags. Tags are useful for routing or filtering without deserializing the body.

```java
            String[] images = {
                "photo-001.jpg",
                "photo-002.png",
                "photo-003.jpg",
                "INVALID_FILE",
                "photo-005.jpg"
            };

            System.out.println("\n--- Enqueuing " + images.length + " resize jobs ---");

            for (int i = 0; i < images.length; i++) {
                Map<String, String> tags = new HashMap<>();
                tags.put("width", "800");
                tags.put("format", "webp");
                tags.put("priority", i == 0 ? "high" : "normal");

                QueueMessage message = QueueMessage.builder()
                        .id(UUID.randomUUID().toString())
                        .channel(CHANNEL)
                        .body(("resize:" + images[i]).getBytes())
                        .metadata("image-upload-service")
                        .tags(tags)
                        .build();

                QueueSendResult result = client.sendQueuesMessage(message);
                System.out.println("  Enqueued: " + images[i]
                        + " (id=" + result.getId() + ", error=" + result.isError() + ")");
            }
```

Notice we include `INVALID_FILE` — this lets us demonstrate rejection handling in the next step. In real systems, you'd validate before enqueuing, but workers still need to handle malformed input.

## Step 3 — Poll and Process Messages

The `receiveQueuesMessages` method performs a long-poll: it waits up to `pollWaitTimeoutInSeconds` for messages to arrive, then returns a batch. This is efficient because it avoids tight polling loops.

```java
            System.out.println("\n--- Processing jobs ---");

            QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                    .channel(CHANNEL)
                    .pollMaxMessages(10)
                    .pollWaitTimeoutInSeconds(5)
                    .build();

            QueuesPollResponse response = client.receiveQueuesMessages(pollRequest);

            if (response.isError()) {
                System.err.println("Poll error: " + response.getError());
                return;
            }

            int processed = 0;
            int failed = 0;

            for (QueueMessageReceived msg : response.getMessages()) {
                String body = new String(msg.getBody());
                String fileName = body.replace("resize:", "");
                System.out.println("\n  Processing: " + fileName);
                System.out.println("    Tags: " + msg.getTags());

                if (fileName.startsWith("INVALID")) {
                    msg.reject();
                    System.out.println("    -> REJECTED (invalid file name)");
                    failed++;
                } else {
                    simulateResize(fileName);
                    msg.ack();
                    System.out.println("    -> ACKNOWLEDGED (resize complete)");
                    processed++;
                }
            }

            System.out.println("\n--- Summary ---");
            System.out.println("  Processed: " + processed);
            System.out.println("  Rejected:  " + failed);
```

The `ack()` / `reject()` pattern is the backbone of reliable messaging. An acknowledged message is permanently removed from the queue. A rejected message becomes available again for redelivery — or routes to a dead-letter queue if configured.

## Step 4 — Clean Up Remaining Messages

After rejecting messages, you may want to drain the queue during development. The `autoAckMessages` flag tells the server to acknowledge everything it delivers, so nothing is left behind.

```java
            QueuesPollResponse cleanup = client.receiveQueuesMessages(
                    QueuesPollRequest.builder()
                            .channel(CHANNEL)
                            .pollMaxMessages(10)
                            .pollWaitTimeoutInSeconds(1)
                            .autoAckMessages(true)
                            .build());
            System.out.println("  Remaining cleaned up: " + cleanup.getMessages().size());

            client.deleteQueuesChannel(CHANNEL);
            System.out.println("\nImage processing queue shut down.");

        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void simulateResize(String fileName) {
        try { Thread.sleep(100); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

## Complete Program

```java
package com.example.taskqueue;

import io.kubemq.sdk.queues.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ImageProcessingQueue {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "image-processor";
    private static final String CHANNEL = "jobs.image-resize";

    public static void main(String[] args) {
        try (QueuesClient client = QueuesClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID)
                .build()) {

            client.createQueuesChannel(CHANNEL);
            System.out.println("Queue '" + CHANNEL + "' is ready");

            String[] images = {
                "photo-001.jpg",
                "photo-002.png",
                "photo-003.jpg",
                "INVALID_FILE",
                "photo-005.jpg"
            };

            System.out.println("\n--- Enqueuing " + images.length + " resize jobs ---");

            for (int i = 0; i < images.length; i++) {
                Map<String, String> tags = new HashMap<>();
                tags.put("width", "800");
                tags.put("format", "webp");
                tags.put("priority", i == 0 ? "high" : "normal");

                QueueMessage message = QueueMessage.builder()
                        .id(UUID.randomUUID().toString())
                        .channel(CHANNEL)
                        .body(("resize:" + images[i]).getBytes())
                        .metadata("image-upload-service")
                        .tags(tags)
                        .build();

                QueueSendResult result = client.sendQueuesMessage(message);
                System.out.println("  Enqueued: " + images[i]
                        + " (id=" + result.getId() + ", error=" + result.isError() + ")");
            }

            System.out.println("\n--- Processing jobs ---");

            QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                    .channel(CHANNEL)
                    .pollMaxMessages(10)
                    .pollWaitTimeoutInSeconds(5)
                    .build();

            QueuesPollResponse response = client.receiveQueuesMessages(pollRequest);

            if (response.isError()) {
                System.err.println("Poll error: " + response.getError());
                return;
            }

            int processed = 0;
            int failed = 0;

            for (QueueMessageReceived msg : response.getMessages()) {
                String body = new String(msg.getBody());
                String fileName = body.replace("resize:", "");
                System.out.println("\n  Processing: " + fileName);
                System.out.println("    Tags: " + msg.getTags());

                if (fileName.startsWith("INVALID")) {
                    msg.reject();
                    System.out.println("    -> REJECTED (invalid file name)");
                    failed++;
                } else {
                    simulateResize(fileName);
                    msg.ack();
                    System.out.println("    -> ACKNOWLEDGED (resize complete)");
                    processed++;
                }
            }

            System.out.println("\n--- Summary ---");
            System.out.println("  Processed: " + processed);
            System.out.println("  Rejected:  " + failed);

            QueuesPollResponse cleanup = client.receiveQueuesMessages(
                    QueuesPollRequest.builder()
                            .channel(CHANNEL)
                            .pollMaxMessages(10)
                            .pollWaitTimeoutInSeconds(1)
                            .autoAckMessages(true)
                            .build());
            System.out.println("  Remaining cleaned up: " + cleanup.getMessages().size());

            client.deleteQueuesChannel(CHANNEL);
            System.out.println("\nImage processing queue shut down.");

        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void simulateResize(String fileName) {
        try { Thread.sleep(100); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

## Expected Output

```
Queue 'jobs.image-resize' is ready

--- Enqueuing 5 resize jobs ---
  Enqueued: photo-001.jpg (id=a1b2c3d4..., error=false)
  Enqueued: photo-002.png (id=e5f6g7h8..., error=false)
  Enqueued: photo-003.jpg (id=i9j0k1l2..., error=false)
  Enqueued: INVALID_FILE (id=m3n4o5p6..., error=false)
  Enqueued: photo-005.jpg (id=q7r8s9t0..., error=false)

--- Processing jobs ---

  Processing: photo-001.jpg
    Tags: {width=800, format=webp, priority=high}
    -> ACKNOWLEDGED (resize complete)

  Processing: photo-002.png
    Tags: {width=800, format=webp, priority=normal}
    -> ACKNOWLEDGED (resize complete)

  Processing: photo-003.jpg
    Tags: {width=800, format=webp, priority=normal}
    -> ACKNOWLEDGED (resize complete)

  Processing: INVALID_FILE
    Tags: {width=800, format=webp, priority=normal}
    -> REJECTED (invalid file name)

  Processing: photo-005.jpg
    Tags: {width=800, format=webp, priority=normal}
    -> ACKNOWLEDGED (resize complete)

--- Summary ---
  Processed: 4
  Rejected:  1
  Remaining cleaned up: 1

Image processing queue shut down.
```

## Error Handling

| Error | Cause | Fix |
|-------|-------|-----|
| `Poll timeout with 0 messages` | No messages in queue | Expected when queue is empty; retry with backoff |
| `sendQueuesMessage returns isError=true` | Channel doesn't exist or server issue | Call `createQueuesChannel` first |
| `Message redelivered after reject` | Rejected messages return to the queue | Configure a dead-letter queue or use `autoAckMessages` |

For production workers, wrap the poll loop with exponential backoff:

```java
int emptyPolls = 0;
while (running) {
    QueuesPollResponse resp = client.receiveQueuesMessages(pollRequest);
    if (resp.getMessages().isEmpty()) {
        emptyPolls++;
        long delay = Math.min(1000L * (1 << emptyPolls), 30_000);
        Thread.sleep(delay);
    } else {
        emptyPolls = 0;
        for (QueueMessageReceived msg : resp.getMessages()) {
            processMessage(msg);
        }
    }
}
```

## Next Steps

- **[Getting Started with Events](getting-started-events.md)** — fire-and-forget real-time messaging
- **[Request-Reply with Commands](request-reply-with-commands.md)** — synchronous command execution
- **Delayed Messages** — schedule tasks for future delivery
- **Dead-Letter Queues** — automatically capture messages that fail repeatedly
