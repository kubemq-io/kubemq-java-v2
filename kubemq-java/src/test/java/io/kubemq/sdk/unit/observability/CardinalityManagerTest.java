package io.kubemq.sdk.unit.observability;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.observability.CardinalityConfig;
import io.kubemq.sdk.observability.KubeMQMetrics;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CardinalityManager (tested via KubeMQMetrics)")
class CardinalityManagerTest {

  private InMemoryMetricReader reader;
  private SdkMeterProvider meterProvider;

  @BeforeEach
  void setUp() {
    reader = InMemoryMetricReader.create();
    meterProvider = SdkMeterProvider.builder().registerMetricReader(reader).build();
  }

  @AfterEach
  void tearDown() {
    meterProvider.close();
  }

  @Test
  @DisplayName("channels below threshold are included in metrics")
  void belowThreshold() {
    KubeMQMetrics metrics = new KubeMQMetrics(meterProvider, "1.0", new CardinalityConfig(3, null));
    metrics.recordSentMessage("publish", "ch-1");
    metrics.recordSentMessage("publish", "ch-2");
    metrics.recordSentMessage("publish", "ch-3");

    Collection<MetricData> data = reader.collectAllMetrics();
    Optional<MetricData> counter =
        data.stream().filter(m -> m.getName().equals("messaging.client.sent.messages")).findFirst();
    assertTrue(counter.isPresent());

    long distinctChannels =
        counter.get().getData().getPoints().stream()
            .map(
                p ->
                    p.getAttributes()
                        .get(
                            io.opentelemetry.api.common.AttributeKey.stringKey(
                                "messaging.destination.name")))
            .filter(java.util.Objects::nonNull)
            .distinct()
            .count();
    assertEquals(3, distinctChannels, "All 3 channels should have destination.name");
  }

  @Test
  @DisplayName("channels above threshold omit destination.name")
  void aboveThreshold() {
    KubeMQMetrics metrics = new KubeMQMetrics(meterProvider, "1.0", new CardinalityConfig(2, null));
    metrics.recordSentMessage("publish", "ch-1");
    metrics.recordSentMessage("publish", "ch-2");
    metrics.recordSentMessage("publish", "ch-3");

    Collection<MetricData> data = reader.collectAllMetrics();
    Optional<MetricData> counter =
        data.stream().filter(m -> m.getName().equals("messaging.client.sent.messages")).findFirst();
    assertTrue(counter.isPresent());

    long pointsWithChannel =
        counter.get().getData().getPoints().stream()
            .filter(
                p ->
                    p.getAttributes()
                            .get(
                                io.opentelemetry.api.common.AttributeKey.stringKey(
                                    "messaging.destination.name"))
                        != null)
            .count();
    assertEquals(
        2,
        pointsWithChannel,
        "Only 2 channels should have destination.name (3rd exceeds threshold)");
  }

  @Test
  @DisplayName("allowlist channels are always included")
  void allowlistAlwaysIncluded() {
    Set<String> allowlist = new HashSet<>();
    allowlist.add("important-channel");
    KubeMQMetrics metrics =
        new KubeMQMetrics(meterProvider, "1.0", new CardinalityConfig(1, allowlist));
    metrics.recordSentMessage("publish", "ch-1");
    metrics.recordSentMessage("publish", "ch-2");
    metrics.recordSentMessage("publish", "important-channel");

    Collection<MetricData> data = reader.collectAllMetrics();
    Optional<MetricData> counter =
        data.stream().filter(m -> m.getName().equals("messaging.client.sent.messages")).findFirst();
    assertTrue(counter.isPresent());

    boolean hasImportantChannel =
        counter.get().getData().getPoints().stream()
            .anyMatch(
                p ->
                    "important-channel"
                        .equals(
                            p.getAttributes()
                                .get(
                                    io.opentelemetry.api.common.AttributeKey.stringKey(
                                        "messaging.destination.name"))));
    assertTrue(hasImportantChannel, "Allowlisted channel should always be included");
  }

  @Test
  @DisplayName("default config has max 100 channels")
  void defaultConfig() {
    CardinalityConfig config = CardinalityConfig.defaults();
    assertEquals(100, config.getMaxChannelCardinality());
    assertTrue(config.getChannelAllowlist().isEmpty());
  }

  @Test
  @DisplayName("allowlist is unmodifiable")
  void allowlistUnmodifiable() {
    Set<String> allowlist = new HashSet<>();
    allowlist.add("ch-1");
    CardinalityConfig config = new CardinalityConfig(10, allowlist);
    assertThrows(
        UnsupportedOperationException.class, () -> config.getChannelAllowlist().add("ch-2"));
  }
}
