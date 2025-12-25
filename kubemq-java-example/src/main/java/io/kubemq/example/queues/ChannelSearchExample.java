package io.kubemq.example.queues;

import io.kubemq.sdk.common.ServerInfo;
import io.kubemq.sdk.queues.QueuesChannel;
import io.kubemq.sdk.queues.QueuesClient;

import java.util.List;

/**
 * Channel Search Example
 *
 * This example demonstrates how to search and filter queue channels in KubeMQ.
 * The listQueuesChannels method supports pattern-based searching to find
 * channels matching specific criteria.
 *
 * Search Capabilities:
 * - Empty string: Returns all channels
 * - Exact name: Returns specific channel if exists
 * - Pattern match: Searches for channels containing the pattern
 *
 * Channel Information Returned:
 * - Name: Channel identifier
 * - Type: Channel type (queues)
 * - isActive: Whether channel has active consumers
 * - Last activity timestamp
 * - Statistics (messages, volume, responses)
 *
 * Use Cases:
 * - Discovery of available channels
 * - Monitoring channel health
 * - Finding channels by naming convention
 * - Administrative channel management
 * - Dynamic consumer routing
 *
 * @see io.kubemq.sdk.queues.QueuesClient#listQueuesChannels(String)
 * @see io.kubemq.sdk.queues.QueuesChannel
 */
public class ChannelSearchExample {

    private final QueuesClient queuesClient;
    private final String address = "localhost:50000";
    private final String clientId = "channel-search-client";

    // Test channels with naming conventions
    private final String[] testChannels = {
            "orders-incoming",
            "orders-processing",
            "orders-completed",
            "payments-pending",
            "payments-completed",
            "notifications-email",
            "notifications-sms",
            "system-logs",
            "system-metrics"
    };

    /**
     * Initializes the QueuesClient and creates test channels.
     */
    public ChannelSearchExample() {
        queuesClient = QueuesClient.builder()
                .address(address)
                .clientId(clientId)
                .build();

        ServerInfo pingResult = queuesClient.ping();
        System.out.println("Connected to: " + pingResult.getHost() + " v" + pingResult.getVersion());
    }

    /**
     * Creates test channels with a naming convention.
     */
    public void setupTestChannels() {
        System.out.println("\n=== Setting Up Test Channels ===\n");

        System.out.println("Creating channels with naming conventions:");
        for (String channel : testChannels) {
            queuesClient.createQueuesChannel(channel);
            System.out.println("  Created: " + channel);
        }
        System.out.println();
    }

    /**
     * Demonstrates listing all channels (no filter).
     */
    public void listAllChannelsExample() {
        System.out.println("=== List All Channels ===\n");

        // Empty string returns all channels
        List<QueuesChannel> allChannels = queuesClient.listQueuesChannels("");

        System.out.println("All queue channels (" + allChannels.size() + " found):\n");

        for (QueuesChannel channel : allChannels) {
            printChannelInfo(channel);
        }
    }

    /**
     * Demonstrates searching for channels by prefix.
     */
    public void searchByPrefixExample() {
        System.out.println("\n=== Search by Prefix ===\n");

        // Search for channels starting with "orders"
        String prefix = "orders";
        System.out.println("Searching for channels containing '" + prefix + "':\n");

        List<QueuesChannel> orderChannels = queuesClient.listQueuesChannels(prefix);

        System.out.println("Found " + orderChannels.size() + " channel(s):");
        for (QueuesChannel channel : orderChannels) {
            System.out.println("  - " + channel.getName());
        }
        System.out.println();
    }

    /**
     * Demonstrates searching for channels by suffix pattern.
     */
    public void searchByPatternExample() {
        System.out.println("=== Search by Pattern ===\n");

        // Search for channels containing "completed"
        String pattern = "completed";
        System.out.println("Searching for channels containing '" + pattern + "':\n");

        List<QueuesChannel> completedChannels = queuesClient.listQueuesChannels(pattern);

        System.out.println("Found " + completedChannels.size() + " channel(s):");
        for (QueuesChannel channel : completedChannels) {
            System.out.println("  - " + channel.getName());
        }
        System.out.println();
    }

    /**
     * Demonstrates finding exact channel by name.
     */
    public void findExactChannelExample() {
        System.out.println("=== Find Exact Channel ===\n");

        String exactName = "payments-pending";
        System.out.println("Searching for exact channel: '" + exactName + "'\n");

        List<QueuesChannel> result = queuesClient.listQueuesChannels(exactName);

        if (!result.isEmpty()) {
            System.out.println("Channel found:");
            printChannelInfo(result.get(0));
        } else {
            System.out.println("Channel not found");
        }
    }

    /**
     * Demonstrates categorizing channels by naming convention.
     */
    public void categorizeChannelsExample() {
        System.out.println("=== Categorize Channels by Domain ===\n");

        String[] domains = {"orders", "payments", "notifications", "system"};

        for (String domain : domains) {
            List<QueuesChannel> domainChannels = queuesClient.listQueuesChannels(domain);

            System.out.println(domain.toUpperCase() + " domain:");
            if (domainChannels.isEmpty()) {
                System.out.println("  (no channels found)");
            } else {
                for (QueuesChannel channel : domainChannels) {
                    String status = channel.getIsActive() ? "[ACTIVE]" : "[INACTIVE]";
                    System.out.println("  " + status + " " + channel.getName());
                }
            }
            System.out.println();
        }
    }

    /**
     * Demonstrates finding active vs inactive channels.
     */
    public void findActiveChannelsExample() {
        System.out.println("=== Find Active/Inactive Channels ===\n");

        List<QueuesChannel> allChannels = queuesClient.listQueuesChannels("");

        System.out.println("Channel Status Summary:");
        System.out.println("─".repeat(50));

        int activeCount = 0;
        int inactiveCount = 0;

        for (QueuesChannel channel : allChannels) {
            // Only show our test channels
            boolean isTestChannel = false;
            for (String tc : testChannels) {
                if (channel.getName().equals(tc)) {
                    isTestChannel = true;
                    break;
                }
            }

            if (isTestChannel) {
                if (channel.getIsActive()) {
                    activeCount++;
                    System.out.println("  [✓] " + channel.getName() + " - ACTIVE");
                } else {
                    inactiveCount++;
                    System.out.println("  [ ] " + channel.getName() + " - inactive");
                }
            }
        }

        System.out.println("─".repeat(50));
        System.out.println("Summary: " + activeCount + " active, " + inactiveCount + " inactive\n");
    }

    /**
     * Demonstrates searching for non-existent channels.
     */
    public void searchNonExistentExample() {
        System.out.println("=== Search for Non-Existent Channels ===\n");

        String nonExistent = "xyz-does-not-exist-123";
        System.out.println("Searching for: '" + nonExistent + "'");

        List<QueuesChannel> result = queuesClient.listQueuesChannels(nonExistent);

        if (result.isEmpty()) {
            System.out.println("Result: No channels found (empty list returned)");
            System.out.println("\nNote: This is normal behavior - no error is thrown.");
        } else {
            System.out.println("Found " + result.size() + " channels (unexpected)");
        }
        System.out.println();
    }

    /**
     * Demonstrates a monitoring use case.
     */
    public void monitoringExample() {
        System.out.println("=== Monitoring Use Case ===\n");

        System.out.println("Scenario: Monitor all 'orders' channels for issues\n");

        List<QueuesChannel> orderChannels = queuesClient.listQueuesChannels("orders");

        System.out.println("ORDER CHANNELS HEALTH CHECK");
        System.out.println("═".repeat(60));

        for (QueuesChannel channel : orderChannels) {
            System.out.println("\nChannel: " + channel.getName());
            System.out.println("  Status: " + (channel.getIsActive() ? "ACTIVE" : "INACTIVE"));
            System.out.println("  Last Activity: " + channel.getLastActivity());

            // Check statistics
            if (channel.getIncoming() != null) {
                System.out.println("  Incoming:");
                System.out.println("    Messages: " + channel.getIncoming().getMessages());
                System.out.println("    Volume: " + channel.getIncoming().getVolume() + " bytes");
            }

            if (channel.getOutgoing() != null) {
                System.out.println("  Outgoing:");
                System.out.println("    Messages: " + channel.getOutgoing().getMessages());
                System.out.println("    Volume: " + channel.getOutgoing().getVolume() + " bytes");
            }
        }

        System.out.println("\n" + "═".repeat(60) + "\n");
    }

    /**
     * Helper method to print channel information.
     */
    private void printChannelInfo(QueuesChannel channel) {
        System.out.println("Channel: " + channel.getName());
        System.out.println("  Type: " + channel.getType());
        System.out.println("  Active: " + channel.getIsActive());
        System.out.println("  Last Activity: " + channel.getLastActivity());
        System.out.println();
    }

    /**
     * Cleans up test channels.
     */
    public void cleanup() {
        System.out.println("=== Cleaning Up ===\n");

        for (String channel : testChannels) {
            try {
                queuesClient.deleteQueuesChannel(channel);
                System.out.println("Deleted: " + channel);
            } catch (Exception e) {
                // Ignore - channel might not exist
            }
        }

        queuesClient.close();
        System.out.println("\nCleanup complete.");
    }

    /**
     * Main method demonstrating all channel search patterns.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           KubeMQ Channel Search Examples                     ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        ChannelSearchExample example = new ChannelSearchExample();

        try {
            // Setup test channels
            example.setupTestChannels();

            // Search examples
            example.searchByPrefixExample();
            example.searchByPatternExample();
            example.findExactChannelExample();

            // Categorization
            example.categorizeChannelsExample();

            // Status checking
            example.findActiveChannelsExample();

            // Edge case
            example.searchNonExistentExample();

            // Monitoring use case
            example.monitoringExample();

            // List all (comprehensive)
            example.listAllChannelsExample();

        } finally {
            example.cleanup();
        }

        System.out.println("\nChannel search examples completed.");
    }
}
