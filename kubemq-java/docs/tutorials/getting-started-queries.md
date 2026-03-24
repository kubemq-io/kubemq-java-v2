# Getting Started with Queries in KubeMQ Java SDK

In this tutorial, you'll build a request-reply system using KubeMQ's `CQClient` and queries. By the end, you'll understand how to send queries, handle them with a responder, and receive data responses synchronously.

## What You'll Build

A data lookup service where a client sends a query (e.g., "Get user data") and a handler responds with structured data. Queries are request-reply with a data payload — ideal for read operations and lookups.

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

## Step 1 — Connect and Create the Queries Channel

Use `CQClient` for commands and queries. Create the channel before subscribing or sending.

```java
package com.example.queries;

import io.kubemq.sdk.cq.*;

import java.util.HashMap;
import java.util.Map;

public class QueryExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queries-client";
    private static final String CHANNEL = "data.lookup";

    public static void main(String[] args) throws InterruptedException {
        CQClient client = CQClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID)
                .build();

        client.ping();
        client.createQueriesChannel(CHANNEL);
```

## Step 2 — Subscribe to Handle Incoming Queries

Register a handler with `QueriesSubscription`. The callback receives each query; use `sendResponseMessage` to reply with data.

```java
        QueriesSubscription sub = QueriesSubscription.builder()
                .channel(CHANNEL)
                .onReceiveQueryCallback(query -> {
                    System.out.println("  Handler received: " + new String(query.getBody()));
                    client.sendResponseMessage(QueryResponseMessage.builder()
                            .queryReceived(query)
                            .isExecuted(true)
                            .body("{\"result\": \"data\"}".getBytes())
                            .build());
                })
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        client.subscribeToQueries(sub);
        Thread.sleep(300);
```

## Step 3 — Send a Query and Wait for the Response

Build a `QueryMessage` with channel, body, optional metadata, tags, and timeout. `sendQueryRequest` blocks until the response arrives or the timeout expires.

```java
        Map<String, String> tags = new HashMap<>();
        tags.put("type", "lookup");

        QueryMessage query = QueryMessage.builder()
                .channel(CHANNEL)
                .body("Get user data".getBytes())
                .metadata("Query metadata")
                .tags(tags)
                .timeoutInSeconds(10)
                .build();

        QueryResponseMessage response = client.sendQueryRequest(query);
        System.out.println("Query executed: " + response.isExecuted());
        System.out.println("Response body: " + new String(response.getBody()));
```

## Step 4 — Clean Up

```java
        sub.cancel();
        client.deleteQueriesChannel(CHANNEL);
        client.close();
    }
}
```

## Complete Program

```java
package com.example.queries;

import io.kubemq.sdk.cq.*;

import java.util.HashMap;
import java.util.Map;

public class QueryExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queries-client";
    private static final String CHANNEL = "data.lookup";

    public static void main(String[] args) throws InterruptedException {
        CQClient client = CQClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID)
                .build();

        client.ping();
        client.createQueriesChannel(CHANNEL);

        QueriesSubscription sub = QueriesSubscription.builder()
                .channel(CHANNEL)
                .onReceiveQueryCallback(query -> {
                    System.out.println("  Handler received: " + new String(query.getBody()));
                    client.sendResponseMessage(QueryResponseMessage.builder()
                            .queryReceived(query)
                            .isExecuted(true)
                            .body("{\"result\": \"data\"}".getBytes())
                            .build());
                })
                .onErrorCallback(err -> System.err.println("Error: " + err))
                .build();

        client.subscribeToQueries(sub);
        Thread.sleep(300);

        Map<String, String> tags = new HashMap<>();
        tags.put("type", "lookup");

        QueryMessage query = QueryMessage.builder()
                .channel(CHANNEL)
                .body("Get user data".getBytes())
                .metadata("Query metadata")
                .tags(tags)
                .timeoutInSeconds(10)
                .build();

        QueryResponseMessage response = client.sendQueryRequest(query);
        System.out.println("Query executed: " + response.isExecuted());
        System.out.println("Response body: " + new String(response.getBody()));

        sub.cancel();
        client.deleteQueriesChannel(CHANNEL);
        client.close();
    }
}
```

## Expected Output

```
  Handler received: Get user data
Query executed: true
Response body: {"result": "data"}
```

## Queries vs Commands

| Feature | Commands | Queries |
|---------|----------|---------|
| Response | Execution status (success/fail) | Data payload |
| Use case | Mutations, actions | Read operations, lookups |
| Handler | Returns executed/error | Returns data in body |

## Next Steps

- **[Request-Reply with Commands](../../../docs/tutorials/request-reply-with-commands.md)** — synchronous command execution with status response
- **[Getting Started with Events](../../../docs/tutorials/getting-started-events.md)** — fire-and-forget messaging
- **Handle queries in a separate process** — run the handler as a long-lived service and send queries from another client
