# How To: Use Consumer Groups

Distribute messages across multiple subscribers using consumer groups for load-balanced processing.

## How Consumer Groups Work

When multiple subscribers join the same `group` on a channel, each message is delivered to **exactly one** subscriber in the group. Without a group, every subscriber receives every message (fan-out).

## Events — Load-Balanced Subscription

```java
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.pubsub.EventMessage;
import io.kubemq.sdk.pubsub.EventsSubscription;

public class EventConsumerGroup {
    public static void main(String[] args) throws InterruptedException {
        try (PubSubClient client = PubSubClient.builder()
                .address("localhost:50000")
                .clientId("events-group-demo")
                .build()) {

            // Two subscribers in the same group — each event goes to one
            client.subscribeToEvents(EventsSubscription.builder()
                    .channel("orders.created")
                    .group("order-processors")
                    .onReceiveEventCallback(event -> {
                        System.out.println("[Worker A] " + new String(event.getBody()));
                    })
                    .onErrorCallback(err -> System.err.println("A error: " + err))
                    .build());

            client.subscribeToEvents(EventsSubscription.builder()
                    .channel("orders.created")
                    .group("order-processors")
                    .onReceiveEventCallback(event -> {
                        System.out.println("[Worker B] " + new String(event.getBody()));
                    })
                    .onErrorCallback(err -> System.err.println("B error: " + err))
                    .build());

            Thread.sleep(1000);

            for (int i = 1; i <= 6; i++) {
                client.sendEventsMessage(EventMessage.builder()
                        .channel("orders.created")
                        .body(("Order #" + i).getBytes())
                        .build());
            }

            Thread.sleep(2000);
        }
    }
}
```

## Events Store — Persistent with Group

```java
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.pubsub.EventStoreMessage;
import io.kubemq.sdk.pubsub.EventsStoreSubscription;
import io.kubemq.sdk.pubsub.EventsStoreType;

public class EventStoreConsumerGroup {
    public static void main(String[] args) throws InterruptedException {
        try (PubSubClient client = PubSubClient.builder()
                .address("localhost:50000")
                .clientId("store-group-demo")
                .build()) {

            client.subscribeToEventsStore(EventsStoreSubscription.builder()
                    .channel("audit.logs")
                    .group("log-indexers")
                    .eventsStoreType(EventsStoreType.StartFromFirst)
                    .onReceiveEventCallback(event -> {
                        System.out.println("[Indexer] seq=" + event.getSequence()
                                + " body=" + new String(event.getBody()));
                    })
                    .onErrorCallback(err -> System.err.println("Error: " + err))
                    .build());

            Thread.sleep(1000);

            for (int i = 1; i <= 5; i++) {
                client.sendEventsStoreMessage(EventStoreMessage.builder()
                        .channel("audit.logs")
                        .body(("Log entry " + i).getBytes())
                        .build());
            }

            Thread.sleep(2000);
        }
    }
}
```

## Commands — Distributed Command Handlers

```java
import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.cq.CommandMessage;
import io.kubemq.sdk.cq.CommandResponseMessage;
import io.kubemq.sdk.cq.CommandsSubscription;

public class CommandConsumerGroup {
    public static void main(String[] args) throws InterruptedException {
        try (CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("cmd-group-demo")
                .build()) {

            client.subscribeToCommands(CommandsSubscription.builder()
                    .channel("commands.process")
                    .group("processors")
                    .onReceiveCommandCallback(cmd -> {
                        System.out.println("[Handler] " + new String(cmd.getBody()));
                        client.sendResponseMessage(CommandResponseMessage.builder()
                                .commandReceived(cmd)
                                .isExecuted(true)
                                .build());
                    })
                    .onErrorCallback(err -> System.err.println("Error: " + err))
                    .build());

            Thread.sleep(1000);

            var response = client.sendCommandRequest(CommandMessage.builder()
                    .channel("commands.process")
                    .body("do-work".getBytes())
                    .timeout(5000)
                    .build());
            System.out.println("Executed: " + response.isExecuted());
        }
    }
}
```

## Queries — Distributed Query Handlers

```java
import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.cq.QueryMessage;
import io.kubemq.sdk.cq.QueryResponseMessage;
import io.kubemq.sdk.cq.QueriesSubscription;

public class QueryConsumerGroup {
    public static void main(String[] args) throws InterruptedException {
        try (CQClient client = CQClient.builder()
                .address("localhost:50000")
                .clientId("query-group-demo")
                .build()) {

            client.subscribeToQueries(QueriesSubscription.builder()
                    .channel("queries.lookup")
                    .group("responders")
                    .onReceiveQueryCallback(query -> {
                        System.out.println("[Responder] " + new String(query.getBody()));
                        client.sendResponseMessage(QueryResponseMessage.builder()
                                .queryReceived(query)
                                .body("{\"status\":\"ok\"}".getBytes())
                                .isExecuted(true)
                                .build());
                    })
                    .onErrorCallback(err -> System.err.println("Error: " + err))
                    .build());

            Thread.sleep(1000);

            var response = client.sendQueryRequest(QueryMessage.builder()
                    .channel("queries.lookup")
                    .body("find-user".getBytes())
                    .timeout(5000)
                    .build());
            System.out.println("Response: " + new String(response.getBody()));
        }
    }
}
```

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| All subscribers receive every message | No `group` set | Add `.group("my-group")` to the subscription |
| One subscriber gets all messages | Only one subscriber in the group | Scale up by adding more group members |
| Messages stop after subscriber crash | No other group member available | Run 2+ subscribers per group for HA |
| Different groups get the same message | Groups are independent | This is correct — groups are isolated fan-out targets |
