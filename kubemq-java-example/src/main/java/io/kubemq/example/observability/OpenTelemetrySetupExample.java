package io.kubemq.example.observability;

import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.pubsub.*;
import io.kubemq.sdk.queues.*;

/**
 * OpenTelemetry Setup Example
 *
 * Demonstrates how the KubeMQ Java SDK integrates with OpenTelemetry for
 * observability including traces, metrics, and logs.
 *
 * The SDK has built-in OpenTelemetry support that auto-detects the OTel
 * agent/SDK. When OTel is available, the SDK automatically:
 * - Creates spans for send/receive operations
 * - Records metrics (message counts, latencies, errors)
 * - Adds trace context to log entries
 *
 * Prerequisites:
 * - OpenTelemetry Java Agent or SDK configured in your application
 * - An OTel collector or backend (Jaeger, Zipkin, etc.)
 *
 * To enable OTel with the Java agent:
 *   java -javaagent:opentelemetry-javaagent.jar \
 *        -Dotel.service.name=my-kubemq-service \
 *        -Dotel.traces.exporter=jaeger \
 *        -jar myapp.jar
 */
public class OpenTelemetrySetupExample {

    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-observability-otel-setup-client";

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== OpenTelemetry Setup Example ===\n");

        System.out.println("The KubeMQ Java SDK automatically integrates with OpenTelemetry.");
        System.out.println("When the OTel agent or SDK is present, the SDK will:");
        System.out.println("  1. Create spans for message send/receive operations");
        System.out.println("  2. Record metrics: message counts, latencies, errors");
        System.out.println("  3. Propagate trace context across messages");
        System.out.println("  4. Add structured log context\n");

        // Create a client (OTel auto-detected when agent/SDK present)
        try (PubSubClient client = PubSubClient.builder()
                .address(ADDRESS)
                .clientId(CLIENT_ID)
                .logLevel(KubeMQClient.Level.INFO)
                .build()) {

            ServerInfo info = client.ping();
            System.out.println("Connected to: " + info.getHost() + " v" + info.getVersion());

            // Create channel for send/receive (generates OTel spans)
            String channel = "java-observability.otel-setup";
            client.createEventsStoreChannel(channel);

            // Send messages (generates send spans and metrics)
            System.out.println("\nSending messages (generates OTel spans)...");
            for (int i = 1; i <= 3; i++) {
                EventStoreMessage message = EventStoreMessage.builder()
                        .id("otel-" + i)
                        .channel(channel)
                        .body(("Traced message #" + i).getBytes())
                        .metadata("otel-example")
                        .build();

                EventSendResult result = client.sendEventsStoreMessage(message);
                System.out.println("  Sent #" + i + " (sent=" + result.isSent() + ")");
            }

            // Subscribe and receive (generates receive spans)
            System.out.println("\nSubscribing (generates OTel receive spans)...");
            EventsStoreSubscription sub = EventsStoreSubscription.builder()
                    .channel(channel)
                    .eventsStoreType(EventsStoreType.StartFromFirst)
                    .onReceiveEventCallback(event ->
                        System.out.println("  Received: " + new String(event.getBody())))
                    .onErrorCallback(err ->
                        System.err.println("  Error: " + err.getMessage()))
                    .build();

            client.subscribeToEventsStore(sub);
            Thread.sleep(2000);

            // Clean up resources
            sub.cancel();
            client.deleteEventsStoreChannel(channel);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }

        System.out.println("\nOTel integration notes:");
        System.out.println("  - Spans appear in your trace backend (Jaeger, Zipkin, etc.)");
        System.out.println("  - Metrics available via OTel metrics exporter");
        System.out.println("  - Log correlation via trace_id and span_id");
        System.out.println("  - No code changes needed -- auto-detection works");
        System.out.println("\nOpenTelemetry setup example completed.");
    }
}
