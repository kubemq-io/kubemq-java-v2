package io.kubemq.sdk.observability;

import java.util.Collections;
import java.util.Set;

/**
 * Configuration for metric cardinality management.
 * Controls the maximum number of unique channel names tracked as metric attributes
 * and provides an allowlist for channels that should always be tracked.
 */
public final class CardinalityConfig {

    private final int maxChannelCardinality;
    private final Set<String> channelAllowlist;

    public CardinalityConfig(int maxChannelCardinality, Set<String> channelAllowlist) {
        this.maxChannelCardinality = maxChannelCardinality;
        this.channelAllowlist = channelAllowlist != null
                ? Collections.unmodifiableSet(channelAllowlist)
                : Collections.emptySet();
    }

    /**
     * Returns default configuration: max 100 unique channels, no allowlist.
     */
    public static CardinalityConfig defaults() {
        return new CardinalityConfig(100, Collections.emptySet());
    }

    public int getMaxChannelCardinality() {
        return maxChannelCardinality;
    }

    public Set<String> getChannelAllowlist() {
        return channelAllowlist;
    }
}
