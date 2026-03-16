# Request-Reply with Commands in KubeMQ Java SDK

In this tutorial, you'll build a command-and-response system using KubeMQ's `CQClient`. Commands are different from events and queues — the sender *blocks* until the handler responds, giving you synchronous confirmation that an action was executed.

## What You'll Build

A device-control system where a controller sends commands to restart services and the handler confirms execution. This pattern is ideal for operations where you need to know the outcome before proceeding.

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

## Step 1 — Create the CQ Client

The `CQClient` handles both Commands (fire-and-confirm) and Queries (fire-and-get-data). In this tutorial we focus on commands. The same client instance can serve as both sender and handler.

```java
package com.example.devicecontrol;

import io.kubemq.sdk.cq.*;
import io.kubemq.sdk.common.ServerInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DeviceController {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "device-controller";
    private static final String CHANNEL = "devices.commands";

    public static void main(String[] args) throws InterruptedException {
        CQClient client = CQClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID)
                .build();

        ServerInfo info = client.ping();
        System.out.println("Connected to KubeMQ at: " + info.getHost());

        client.createCommandsChannel(CHANNEL);
        System.out.println("Command channel '" + CHANNEL + "' is ready");
```

## Step 2 — Register the Command Handler

The handler subscribes to a channel and processes incoming commands. Every command must receive a response — either "executed" or an error — before the sender's timeout expires.

```java
        CountDownLatch latch = new CountDownLatch(3);

        CommandsSubscription subscription = CommandsSubscription.builder()
                .channel(CHANNEL)
                .onReceiveCommandCallback(cmd -> {
                    String body = new String(cmd.getBody());
                    System.out.println("\n[Handler] Received command: " + body);
                    System.out.println("  Tags: " + cmd.getTags());

                    boolean success = executeCommand(body);

                    CommandResponseMessage response = CommandResponseMessage.builder()
                            .commandReceived(cmd)
                            .isExecuted(success)
                            .error(success ? "" : "Command failed: " + body)
                            .build();

                    client.sendResponseMessage(response);
                    System.out.println("  [Handler] Response sent: executed=" + success);
                    latch.countDown();
                })
                .onErrorCallback(err ->
                    System.err.println("[Handler] Error: " + err.getMessage())
                )
                .build();

        client.subscribeToCommands(subscription);
        System.out.println("Command handler listening...");
```

The `commandReceived(cmd)` builder method links the response to the original request — KubeMQ uses this correlation to route the response back to the correct sender. Without it, the sender would time out.

## Step 3 — Send Commands and Await Responses

Each command includes a `timeoutInSeconds` — if the handler doesn't respond within that window, the sender gets a timeout error. This prevents your system from hanging indefinitely.

```java
        Thread.sleep(500);

        String[] commands = {"restart-web-server", "clear-cache", "UNKNOWN_ACTION"};

        for (String action : commands) {
            System.out.println("\n[Controller] Sending command: " + action);

            Map<String, String> tags = new HashMap<>();
            tags.put("action", action);
            tags.put("operator", "admin");

            try {
                CommandMessage command = CommandMessage.builder()
                        .channel(CHANNEL)
                        .body(action.getBytes())
                        .metadata("device-control-panel")
                        .tags(tags)
                        .timeoutInSeconds(10)
                        .build();

                CommandResponseMessage response = client.sendCommandRequest(command);

                if (response.isExecuted()) {
                    System.out.println("[Controller] Command executed successfully");
                } else {
                    System.out.println("[Controller] Command failed: " + response.getError());
                }
            } catch (Exception e) {
                System.out.println("[Controller] Command error: " + e.getMessage());
            }
        }
```

We deliberately include `UNKNOWN_ACTION` to show how the handler can reject commands it doesn't understand. The sender sees the failure immediately through the response.

## Step 4 — Clean Up

```java
        latch.await(15, TimeUnit.SECONDS);

        subscription.cancel();
        client.deleteCommandsChannel(CHANNEL);
        client.close();
        System.out.println("\nDevice controller shut down.");
    }
```

## Step 5 — The Command Executor

This simulates actual work. In production, this would interact with real services, databases, or hardware.

```java
    private static boolean executeCommand(String command) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        switch (command) {
            case "restart-web-server":
            case "clear-cache":
                return true;
            default:
                return false;
        }
    }
}
```

Returning `false` causes the handler to send `isExecuted=false` with an error message. The sender receives this as a normal response — not an exception — so it can decide what to do next.

## Complete Program

```java
package com.example.devicecontrol;

import io.kubemq.sdk.cq.*;
import io.kubemq.sdk.common.ServerInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DeviceController {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "device-controller";
    private static final String CHANNEL = "devices.commands";

    public static void main(String[] args) throws InterruptedException {
        CQClient client = CQClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID)
                .build();

        ServerInfo info = client.ping();
        System.out.println("Connected to KubeMQ at: " + info.getHost());

        client.createCommandsChannel(CHANNEL);
        System.out.println("Command channel '" + CHANNEL + "' is ready");

        CountDownLatch latch = new CountDownLatch(3);

        CommandsSubscription subscription = CommandsSubscription.builder()
                .channel(CHANNEL)
                .onReceiveCommandCallback(cmd -> {
                    String body = new String(cmd.getBody());
                    System.out.println("\n[Handler] Received command: " + body);
                    System.out.println("  Tags: " + cmd.getTags());

                    boolean success = executeCommand(body);

                    CommandResponseMessage response = CommandResponseMessage.builder()
                            .commandReceived(cmd)
                            .isExecuted(success)
                            .error(success ? "" : "Command failed: " + body)
                            .build();

                    client.sendResponseMessage(response);
                    System.out.println("  [Handler] Response sent: executed=" + success);
                    latch.countDown();
                })
                .onErrorCallback(err ->
                    System.err.println("[Handler] Error: " + err.getMessage())
                )
                .build();

        client.subscribeToCommands(subscription);
        System.out.println("Command handler listening...");

        Thread.sleep(500);

        String[] commands = {"restart-web-server", "clear-cache", "UNKNOWN_ACTION"};

        for (String action : commands) {
            System.out.println("\n[Controller] Sending command: " + action);

            Map<String, String> tags = new HashMap<>();
            tags.put("action", action);
            tags.put("operator", "admin");

            try {
                CommandMessage command = CommandMessage.builder()
                        .channel(CHANNEL)
                        .body(action.getBytes())
                        .metadata("device-control-panel")
                        .tags(tags)
                        .timeoutInSeconds(10)
                        .build();

                CommandResponseMessage response = client.sendCommandRequest(command);

                if (response.isExecuted()) {
                    System.out.println("[Controller] Command executed successfully");
                } else {
                    System.out.println("[Controller] Command failed: " + response.getError());
                }
            } catch (Exception e) {
                System.out.println("[Controller] Command error: " + e.getMessage());
            }
        }

        latch.await(15, TimeUnit.SECONDS);

        subscription.cancel();
        client.deleteCommandsChannel(CHANNEL);
        client.close();
        System.out.println("\nDevice controller shut down.");
    }

    private static boolean executeCommand(String command) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        switch (command) {
            case "restart-web-server":
            case "clear-cache":
                return true;
            default:
                return false;
        }
    }
}
```

## Expected Output

```
Connected to KubeMQ at: localhost
Command channel 'devices.commands' is ready
Command handler listening...

[Controller] Sending command: restart-web-server

[Handler] Received command: restart-web-server
  Tags: {action=restart-web-server, operator=admin}
  [Handler] Response sent: executed=true
[Controller] Command executed successfully

[Controller] Sending command: clear-cache

[Handler] Received command: clear-cache
  Tags: {action=clear-cache, operator=admin}
  [Handler] Response sent: executed=true
[Controller] Command executed successfully

[Controller] Sending command: UNKNOWN_ACTION

[Handler] Received command: UNKNOWN_ACTION
  Tags: {action=UNKNOWN_ACTION, operator=admin}
  [Handler] Response sent: executed=false
[Controller] Command failed: Command failed: UNKNOWN_ACTION

Device controller shut down.
```

## Error Handling

| Error | Cause | Fix |
|-------|-------|-----|
| `Timeout` | No handler responded in time | Increase `timeoutInSeconds` or verify handler is running |
| `No handler` | No subscriber on the channel | Start the handler before sending commands |
| `Handler crash` | Exception in callback | Wrap handler logic in try-catch; always send a response |

The most critical rule: **always send a response from the handler**. If your handler throws an exception without responding, the sender blocks until timeout. Wrap your handler logic defensively:

```java
.onReceiveCommandCallback(cmd -> {
    try {
        boolean ok = processCommand(cmd);
        client.sendResponseMessage(CommandResponseMessage.builder()
                .commandReceived(cmd).isExecuted(ok).build());
    } catch (Exception e) {
        client.sendResponseMessage(CommandResponseMessage.builder()
                .commandReceived(cmd).isExecuted(false)
                .error(e.getMessage()).build());
    }
})
```

## Next Steps

- **[Getting Started with Events](getting-started-events.md)** — fire-and-forget real-time messaging
- **[Building a Task Queue](building-a-task-queue.md)** — guaranteed delivery with acknowledgment
- **Queries** — like commands, but the response carries a data payload
- **Consumer Groups** — load-balance commands across multiple handlers
