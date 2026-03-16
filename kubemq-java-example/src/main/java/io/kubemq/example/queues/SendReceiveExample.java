package io.kubemq.example.queues;

import io.kubemq.sdk.queues.*;

public class SendReceiveExample {
    // TODO: Replace with your KubeMQ server address
    private static final String ADDRESS = "localhost:50000";
    private static final String CLIENT_ID = "java-queues-send-receive-client";
    private static final String CHANNEL = "java-queues.send-receive";

    public static void main(String[] args) {
        try (QueuesClient client = QueuesClient.builder().address(ADDRESS).clientId(CLIENT_ID).build()) {
            QueueMessage message = QueueMessage.builder()
                    .channel(CHANNEL).body("Hello KubeMQ Queue!".getBytes()).metadata("simple-example").build();

            QueueSendResult sendResult = client.sendQueuesMessage(message);
            System.out.println("Message sent - ID: " + sendResult.getId() + ", Error: " + sendResult.isError());

            QueuesPollRequest pollRequest = QueuesPollRequest.builder()
                    .channel(CHANNEL).pollMaxMessages(1).pollWaitTimeoutInSeconds(10).build();

            QueuesPollResponse response = client.receiveQueuesMessages(pollRequest);
            if (response.isError()) {
                System.err.println("Receive error: " + response.getError());
            } else {
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
