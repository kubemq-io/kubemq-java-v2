# How To: Monitor with OpenTelemetry

Instrument KubeMQ operations with distributed tracing and metrics using OpenTelemetry.

## How It Works

The SDK auto-detects OpenTelemetry on the classpath and creates spans/metrics for every operation. When OTel is not present, a zero-overhead no-op implementation is used — no code changes needed.

## Dependencies

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <version>1.40.0</version>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk</artifactId>
    <version>1.40.0</version>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
    <version>1.40.0</version>
</dependency>
```

## Tracing Setup

```java
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.pubsub.EventMessage;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.semconv.ResourceAttributes;

public class TracingExample {
    public static void main(String[] args) {
        // 1. Configure OTel SDK before creating KubeMQ client
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(
                        ResourceAttributes.SERVICE_NAME, "order-service")));

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(
                        OtlpGrpcSpanExporter.builder()
                                .setEndpoint("http://localhost:4317")
                                .build())
                        .build())
                .setResource(resource)
                .build();

        OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .buildAndRegisterGlobal();

        // 2. Create the KubeMQ client — tracing is automatic
        try (PubSubClient client = PubSubClient.builder()
                .address("localhost:50000")
                .clientId("traced-service")
                .build()) {

            // 3. Operations produce spans automatically
            client.sendEventsMessage(EventMessage.builder()
                    .channel("orders.created")
                    .body("{\"orderId\":\"ORD-001\"}".getBytes())
                    .build());

            System.out.println("Event sent — check your OTel collector for traces");

        } finally {
            tracerProvider.shutdown();
        }
    }
}
```

## Span Attributes

Every SDK span includes these semantic attributes:

| Attribute | Example |
|---|---|
| `messaging.system` | `kubemq` |
| `messaging.operation.name` | `publish`, `receive`, `send` |
| `messaging.destination.name` | `orders.created` |
| `messaging.client.id` | `traced-service` |
| `messaging.message.id` | `<uuid>` |
| `server.address` | `localhost` |
| `server.port` | `50000` |

## Custom Parent Spans

Wrap KubeMQ operations in your own business spans:

```java
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

Tracer tracer = GlobalOpenTelemetry.getTracer("my-service");

Span orderSpan = tracer.spanBuilder("processOrder").startSpan();
try (Scope scope = orderSpan.makeCurrent()) {
    // KubeMQ spans are automatically linked as children
    client.sendEventsMessage(EventMessage.builder()
            .channel("orders.created")
            .body(orderJson.getBytes())
            .build());
} finally {
    orderSpan.end();
}
```

## Metrics

When OTel metrics are configured, the SDK records:

| Metric | Type | Description |
|---|---|---|
| `kubemq.connection.opened` | Counter | Connection established |
| `kubemq.connection.closed` | Counter | Connection terminated |
| `kubemq.messages.sent` | Counter | Messages published/sent |
| `kubemq.messages.received` | Counter | Messages consumed |
| `kubemq.operation.duration` | Histogram | Operation latency (seconds) |

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| No spans in collector | OTel SDK not on classpath | Add `opentelemetry-sdk` dependency |
| Spans appear but no export | Exporter not configured | Add and configure `opentelemetry-exporter-otlp` |
| Missing parent-child links | OTel configured after client creation | Initialize OTel SDK **before** creating KubeMQ client |
| `NoOp` tracer logged | OTel API present but no SDK | Add `opentelemetry-sdk` (not just `opentelemetry-api`) |
