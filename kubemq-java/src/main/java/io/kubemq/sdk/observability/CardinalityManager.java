package io.kubemq.sdk.observability;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages metric cardinality for the {@code messaging.destination.name} attribute.
 * <p>
 * When unique channel count exceeds the configured threshold:
 * <ol>
 *   <li>Emits a WARN log (once)</li>
 *   <li>Omits {@code messaging.destination.name} from new metric series</li>
 *   <li>Channels in the allowlist are always included regardless of threshold</li>
 * </ol>
 */
final class CardinalityManager {

    private final CardinalityConfig config;
    private final ConcurrentHashMap.KeySetView<String, Boolean> observedChannels =
            ConcurrentHashMap.newKeySet();
    private final AtomicBoolean thresholdWarningEmitted = new AtomicBoolean(false);
    private final KubeMQLogger logger;

    CardinalityManager(CardinalityConfig config) {
        this.config = config;
        this.logger = KubeMQLoggerFactory.getLogger(
                "io.kubemq.sdk.observability.CardinalityManager");
    }

    /**
     * Returns true if the channel should be included as a metric attribute.
     */
    boolean shouldIncludeChannel(String channel) {
        if (config.getChannelAllowlist().contains(channel)) {
            return true;
        }

        if (observedChannels.contains(channel)) {
            return true;
        }

        if (observedChannels.size() >= config.getMaxChannelCardinality()) {
            if (thresholdWarningEmitted.compareAndSet(false, true)) {
                logger.warn("Metric cardinality threshold exceeded",
                        "threshold", config.getMaxChannelCardinality(),
                        "action", "omitting messaging.destination.name for new channels",
                        "hint", "Configure channelAllowlist or increase maxChannelCardinality");
            }
            return false;
        }

        observedChannels.add(channel);
        return true;
    }
}
