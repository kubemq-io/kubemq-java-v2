package io.kubemq.sdk.testutil;

import java.util.UUID;

/**
 * Utility for generating unique channel and client names in tests.
 * Prevents test interference when running in parallel.
 */
public final class TestChannelNames {

    private TestChannelNames() {
        // utility class
    }

    /**
     * Creates a unique channel name: "{prefix}-{uuid8}".
     *
     * @param prefix descriptive prefix for the channel
     * @return unique channel name
     */
    public static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Creates a unique client ID: "{prefix}-client-{uuid8}".
     *
     * @param prefix descriptive prefix for the client
     * @return unique client ID
     */
    public static String uniqueClientId(String prefix) {
        return prefix + "-client-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
