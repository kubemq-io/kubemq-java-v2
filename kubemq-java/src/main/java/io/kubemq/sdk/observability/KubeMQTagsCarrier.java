package io.kubemq.sdk.observability;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.Map;

/**
 * OTel TextMap carrier over KubeMQ message tags ({@code Map<String, String>}).
 * <p>
 * Injects/extracts W3C Trace Context ({@code traceparent}, {@code tracestate})
 * into message tags for cross-service trace propagation.
 * <p>
 * <strong>WARNING:</strong> This class imports OTel types and must ONLY be loaded
 * within OTel-available code paths (inside {@code KubeMQTracing}).
 */
public final class KubeMQTagsCarrier {

    private KubeMQTagsCarrier() {}

    public static final TextMapGetter<Map<String, String>> GETTER =
            new TextMapGetter<Map<String, String>>() {
                @Override
                public Iterable<String> keys(Map<String, String> carrier) {
                    return carrier.keySet();
                }

                @Override
                public String get(Map<String, String> carrier, String key) {
                    return carrier != null ? carrier.get(key) : null;
                }
            };

    public static final TextMapSetter<Map<String, String>> SETTER =
            (carrier, key, value) -> {
                if (carrier != null) {
                    carrier.put(key, value);
                }
            };
}
