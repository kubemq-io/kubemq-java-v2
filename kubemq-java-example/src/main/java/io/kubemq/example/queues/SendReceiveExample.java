package io.kubemq.example.queues;

import io.kubemq.sdk.queues.*;

public class SendReceiveExample {
    // TODO: Replace with your KubeMQ server address
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queues-send-receive-client";
    private static final String CHANNEL = "java-queues.send-receive";

    public static void main(String[] args) {
        // Create a queues client connected to the KubeMQ server
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            // Build and send a queue message
            QueueMessage message = QueueMessage.builder()
                    .channel(CHANNEL).body("Hello KubeMQ Queue!".getBytes()).metadata("simple-example").build();

            // Send the message to the queue
            QueueSendResult sendResult = client.sendQueuesMessage(message);
            System.out.println("Message sent - ID: " + sendResult.getId() + ", Error: " + sendResult.isError());

            // Poll to receive messages from the queue
            QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                    .channel(CHANNEL).pollMaxMessages(1).pollWaitTimeoutInSeconds(10).build();

            // Receive messages from the queue
            QueuesPollResponse response = client.receiveQueuesMessages(pollRequest);
            if (response.isError()) {
                System.err.println("Receive error: " + response.getError());
            } else {
                // Handle each received message and acknowledge it
                response.getMessages().forEach(msg -> {
                    System.out.println("Received - ID: " + msg.getId() + ", Body: " + new String(msg.getBody()));
                    msg.ack();
                });
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}

// Expected output:
// Message sent - ID: <message-id>, Error: false
// Received - ID: <message-id>, Body: Hello KubeMQ Queue!
