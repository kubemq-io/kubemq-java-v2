# Building an Event-Driven Processing Pipeline

This guide walks through a multi-stage processing pipeline that combines KubeMQ events for real-time fan-out with queues for reliable, exactly-once delivery.

## Architecture

```
┌──────────┐    events     ┌─────────────┐    queue     ┌──────────┐
│ Producer │──────────────▶│  Processor  │─────────────▶│  Output  │
│ (ingest) │  (fan-out)    │  (transform)│  (reliable)  │ (persist)│
└──────────┘               └─────────────┘              └──────────┘
```

1. **Producer** publishes raw data as events on a pub/sub channel.
2. **Processor** subscribes to events, transforms each payload, and enqueues results into a queue.
3. **Output** worker pulls from the queue with ack/nack semantics to guarantee delivery.

This separation lets you scale each stage independently. Events handle real-time fan-out while queues provide backpressure and delivery guarantees.

## Prerequisites

- KubeMQ server on `localhost:50000`
- Java 11+ and the `kubemq-java` dependency in your `pom.xml`

## Stage 1 — Event Producer

The producer ingests raw data and publishes it as events. Multiple subscribers can receive each event.

```java
import io.kubemq.sdk.pubsub.*;
import java.util.UUID;

public class PipelineProducer {
    public static void publishOrders(PubSubClient client) {
        String[] orders = {
            "{\"id\":\"ORD-1\",\"item\":\"widget\",\"qty\":5}",
            "{\"id\":\"ORD-2\",\"item\":\"gadget\",\"qty\":2}",
            "{\"id\":\"ORD-3\",\"item\":\"gizmo\",\"qty\":10}",
        };
        for (String order : orders) {
            EventMessage msg = EventMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .channel("pipeline.ingest")
                    .body(order.getBytes())
                    .metadata("source:api")
                    .build();
            client.sendEventsMessage(msg);
            System.out.println("[Producer] Published: " + order);
        }
    }
}
```

## Stage 2 — Event Processor

The processor subscribes to events, transforms payloads, and enqueues enriched results for reliable downstream consumption.

```java
import io.kubemq.sdk.pubsub.*;
import io.kubemq.sdk.queues.*;
import java.time.Instant;

public class PipelineProcessor {
    public static EventsSubscription start(PubSubClient pubsub, QueuesClient queues) {
        EventsSubscription subscription = EventsSubscription.builder()
                .channel("pipeline.ingest")
                .onReceiveEventCallback(event -> {
                    String body = new String(event.getBody());
                    System.out.println("[Processor] Received: " + body);

                    String enriched = String.format(
                        "{\"original\":%s,\"processed_at\":\"%s\"}", body, Instant.now());

                    QueueMessage queueMsg = QueueMessage.builder()
                            .channel("pipeline.output")
                            .body(enriched.getBytes())
                            .build();

                    QueueSendResult result = queues.sendQueuesMessage(queueMsg);
                    if (result.isError()) {
                        System.err.println("[Processor] Queue error: " + result.getError());
                    } else {
                        System.out.println("[Processor] Enqueued: " + result.getId());
                    }
                })
                .onErrorCallback(err ->
                    System.err.println("[Processor] Error: " + err.getMessage()))
                .build();

        pubsub.subscribeToEvents(subscription);
        return subscription;
    }
}
```

## Stage 3 — Output Worker

The output worker pulls from the queue with exactly-once semantics. Failed messages remain on the queue for retry.

```java
import io.kubemq.sdk.queues.*;

public class PipelineOutput {
    public static void consume(QueuesClient client) {
        QueuesPollRequest request = QueuesPollRequest.builder()
                .channel("pipeline.output")
                .pollMaxMessages(10)
                .pollWaitTimeoutInSeconds(5)
                .build();

        QueuesPollResponse response = client.receiveQueuesMessages(request);
        if (response.isError()) {
            System.err.println("[Output] Error: " + response.getError());
            return;
        }

        System.out.println("[Output] Received " + response.getMessages().size() + " messages:");
        response.getMessages().forEach(msg -> {
            System.out.println("  → " + new String(msg.getBody()));
            msg.ack();
        });
    }
}
```

## Putting It Together

```java
public class PipelineDemo {
    public static void main(String[] args) throws InterruptedException {
        PubSubClient pubsub = PubSubClient.builder()
                .address("localhost:50000").clientId("pipeline-pubsub").build();
        QueuesClient queues = QueuesClient.builder()
                .address("localhost:50000").clientId("pipeline-queues").build();

        pubsub.createEventsChannel("pipeline.ingest");

        EventsSubscription sub = PipelineProcessor.start(pubsub, queues);
        Thread.sleep(500);

        PipelineProducer.publishOrders(pubsub);
        Thread.sleep(2000);

        PipelineOutput.consume(queues);

        sub.cancel();
        pubsub.deleteEventsChannel("pipeline.ingest");
        pubsub.close();
        queues.close();
    }
}
```

## Error Handling

- **Producer failures**: Log and skip; events are fire-and-forget by design.
- **Processor failures**: If the queue send fails, the event is lost. Use events-store instead of events if you need replay capability.
- **Output failures**: Messages stay in the queue. Use dead-letter queues for messages that fail repeatedly.

## When to Use This Pattern

- Stream processing with decoupled stages
- Ingestion pipelines where throughput matters more than ordering
- Systems that need both real-time notifications and guaranteed delivery
