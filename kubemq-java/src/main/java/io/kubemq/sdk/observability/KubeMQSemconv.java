package io.kubemq.sdk.observability;

import io.opentelemetry.api.common.AttributeKey;

/**
 * OpenTelemetry semantic convention constants for KubeMQ SDK. Based on OTel messaging semconv
 * v1.27.0.
 *
 * <p><strong>WARNING:</strong> This class imports OTel types and must ONLY be loaded within
 * OTel-available code paths (inside {@code KubeMQTracing}/{@code KubeMQMetrics}). Never reference
 * this class from eagerly-loaded client classes.
 */
public final class KubeMQSemconv {

  private KubeMQSemconv() {}

  // ---- System identifier ----
  public static final String MESSAGING_SYSTEM_VALUE = "kubemq";

  // ---- Required span attributes (messaging semconv) ----
  public static final AttributeKey<String> MESSAGING_SYSTEM =
      AttributeKey.stringKey("messaging.system");
  public static final AttributeKey<String> MESSAGING_OPERATION_NAME =
      AttributeKey.stringKey("messaging.operation.name");
  public static final AttributeKey<String> MESSAGING_OPERATION_TYPE =
      AttributeKey.stringKey("messaging.operation.type");
  public static final AttributeKey<String> MESSAGING_DESTINATION_NAME =
      AttributeKey.stringKey("messaging.destination.name");
  public static final AttributeKey<String> MESSAGING_MESSAGE_ID =
      AttributeKey.stringKey("messaging.message.id");
  public static final AttributeKey<String> MESSAGING_CLIENT_ID =
      AttributeKey.stringKey("messaging.client.id");
  public static final AttributeKey<String> MESSAGING_CONSUMER_GROUP_NAME =
      AttributeKey.stringKey("messaging.consumer.group.name");
  public static final AttributeKey<String> SERVER_ADDRESS =
      AttributeKey.stringKey("server.address");
  public static final AttributeKey<Long> SERVER_PORT = AttributeKey.longKey("server.port");
  public static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");

  // ---- Recommended span attributes ----
  public static final AttributeKey<Long> MESSAGING_MESSAGE_BODY_SIZE =
      AttributeKey.longKey("messaging.message.body.size");

  // ---- Batch attributes ----
  public static final AttributeKey<Long> MESSAGING_BATCH_MESSAGE_COUNT =
      AttributeKey.longKey("messaging.batch.message_count");

  // ---- Retry span event attributes ----
  public static final String RETRY_EVENT_NAME = "retry";
  public static final AttributeKey<Long> RETRY_ATTEMPT = AttributeKey.longKey("retry.attempt");
  public static final AttributeKey<Double> RETRY_DELAY_SECONDS =
      AttributeKey.doubleKey("retry.delay_seconds");

  // ---- DLQ span event ----
  public static final String DLQ_EVENT_NAME = "message.dead_lettered";

  // ---- Instrumentation scope ----
  public static final String INSTRUMENTATION_SCOPE_NAME = "io.kubemq.sdk";

  // ---- Operation name constants ----
  public static final String OP_PUBLISH = "publish";
  public static final String OP_PROCESS = "process";
  public static final String OP_RECEIVE = "receive";
  public static final String OP_SETTLE = "settle";
  public static final String OP_SEND = "send";
}
