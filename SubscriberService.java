package com.totalplay.receiver.receiverkubekube.event;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.totalplay.receiver.receiverkubekube.config.KubeMQConfig;
import com.totalplay.receiver.receiverkubekube.model.Request;

import io.kubemq.sdk.queues.QueueMessageReceived;
import io.kubemq.sdk.queues.QueuesPollRequest;
import io.kubemq.sdk.queues.QueuesPollResponse;
import io.kubemq.sdk.queues.QueuesClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * KubeMQ Queue Subscriber Service.
 *
 * This service polls messages from a KubeMQ queue channel and processes them.
 * It is designed to work with KubeMQ's server-side Dead Letter Queue (DLQ) mechanism:
 *
 *   - The PUBLISHER sets DLQ configuration on each message (maxRetries + DLQ channel name).
 *   - This SUBSCRIBER only needs to ack() or reject() messages.
 *   - The KubeMQ SERVER automatically routes messages to the DLQ after the configured
 *     number of rejections. No client-side DLQ logic is needed here.
 *
 * Connection management:
 *   - The SDK's QueueDownstreamHandler manages gRPC stream lifecycle internally.
 *   - When a stream closes (normal after each poll transaction), the SDK automatically
 *     re-establishes it on the next poll using the existing TCP channel.
 *   - DO NOT create a new QueuesClient to "reconnect" - this opens a new TCP connection
 *     and leaks the old one. The SDK handles reconnection transparently.
 *
 * Required configuration (via KubeMQConfig):
 *   - server.address:           KubeMQ server address (e.g., "localhost:50000")
 *   - queue.clientId:           Unique client identifier for this subscriber
 *   - queue.channel:            Queue channel name to subscribe to
 *   - poll.maxMessages:         Max messages to retrieve per poll (e.g., 10)
 *   - poll.waitTimeoutSeconds:  Server-side long-poll timeout in seconds (e.g., 30)
 *   - poll.visibilitySeconds:   Client-side visibility timeout in seconds (e.g., 60)
 *
 * NOTE: The RetryConfig dependency was removed. DLQ retry configuration is now
 * handled entirely on the publisher side via QueueMessage.attemptsBeforeDeadLetterQueue
 * and QueueMessage.deadLetterQueue fields. See PublisherService.java.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriberService {

    private final KubeMQConfig kubeMQConfig;
    private final PagoServiceC pagoServiceC;

    private QueuesClient queuesClient;
    private QueuesPollRequest pollRequest;
    private Thread workerThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Initializes the KubeMQ client, validates the connection, and starts the worker thread.
     *
     * This method runs once during Spring context initialization. If the KubeMQ server
     * is unreachable, ping() will throw and the application will fail to start.
     * This is intentional - running without a working KubeMQ connection is not useful.
     */
    @PostConstruct
    public void start() {
        log.info("Initializing KubeMQ Subscriber Service");
        log.info("Server: {}", kubeMQConfig.getServer().getAddress());
        log.info("Channel: {}", kubeMQConfig.getQueue().getChannel());

        // --- CLIENT SETUP ---
        // keepAlive(true): Sends periodic gRPC keepalive pings to prevent intermediate
        //   network infrastructure (load balancers, firewalls) from killing idle connections.
        //   Without this, the TCP connection can be silently dropped during idle periods.
        // pingIntervalInSeconds(30): How often to send keepalive pings.
        // pingTimeoutInSeconds(10): How long to wait for a keepalive response before
        //   considering the connection dead.
        // reconnectIntervalSeconds(5): Base interval for the SDK's internal reconnection
        //   logic when the gRPC channel enters TRANSIENT_FAILURE state.
        queuesClient = QueuesClient.builder()
                .address(kubeMQConfig.getServer().getAddress())
                .clientId(kubeMQConfig.getQueue().getClientId())
                .reconnectIntervalSeconds(5)
                .keepAlive(true)
                .pingIntervalInSeconds(30)
                .pingTimeoutInSeconds(10)
                .build();

        // Fail fast: if the server is unreachable, this throws and prevents startup.
        var serverInfo = queuesClient.ping();
        log.info("Connected to KubeMQ: {} v{}", serverInfo.getHost(), serverInfo.getVersion());

        // --- POLL REQUEST (built once, reused for every poll) ---
        // The poll request parameters never change at runtime, so we build it once here.
        // autoAckMessages(false): We need manual control to ack on success or reject on failure.
        // pollWaitTimeoutInSeconds: The server holds the connection open for this duration
        //   waiting for messages (long-polling). No additional sleep is needed after an empty response.
        // visibilitySeconds: Client-side timer. If a message is not ack'd/rejected within this
        //   duration, the SDK auto-rejects it. Set this higher than your worst-case processing time.
        pollRequest = QueuesPollRequest.builder()
                .channel(kubeMQConfig.getQueue().getChannel())
                .pollMaxMessages(kubeMQConfig.getPoll().getMaxMessages())
                .pollWaitTimeoutInSeconds(kubeMQConfig.getPoll().getWaitTimeoutSeconds())
                .autoAckMessages(false)
                .visibilitySeconds(kubeMQConfig.getPoll().getVisibilitySeconds())
                .build();

        // --- WORKER THREAD ---
        // setDaemon(true): Ensures this thread won't prevent JVM shutdown if @PreDestroy
        // is not called (e.g., during an abnormal termination).
        running.set(true);
        workerThread = new Thread(this::messageLoop, "KubeMQ-Subscriber");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("Worker thread started");
    }

    /**
     * Graceful shutdown: stops the worker thread and closes the KubeMQ client.
     *
     * Shutdown sequence:
     * 1. Set running=false to signal the loop to stop.
     * 2. Interrupt the worker thread to break out of a blocking poll.
     * 3. Wait up to 5 seconds for the worker to finish its current message.
     * 4. Close the QueuesClient to release the gRPC channel and all resources.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down KubeMQ Subscriber Service...");
        running.set(false);

        if (workerThread != null) {
            workerThread.interrupt();
            try {
                workerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for worker thread");
            }

            if (workerThread.isAlive()) {
                log.warn("Worker thread did not terminate within timeout");
            }
        }

        if (queuesClient != null) {
            try {
                queuesClient.close();
                log.info("KubeMQ client closed");
            } catch (Exception e) {
                log.error("Error closing KubeMQ client", e);
            }
        }
    }

    /**
     * Main message processing loop.
     *
     * This loop continuously polls the KubeMQ queue and processes messages.
     *
     * Important design decisions:
     *
     * - NO error counter or client re-initialization:
     *   The SDK's QueueDownstreamHandler.sendRequest() automatically re-establishes
     *   the gRPC bidirectional stream when it detects the stream is disconnected.
     *   Creating a new QueuesClient would open a new TCP connection and leak the old one.
     *   On poll errors, we simply log and continue - the SDK will reconnect the stream
     *   on the next poll attempt.
     *
     * - NO sleep after empty responses:
     *   The pollWaitTimeoutInSeconds parameter already implements server-side long-polling.
     *   The server blocks for that duration waiting for messages. Adding an extra sleep
     *   would only increase latency when messages arrive right after a timeout.
     *
     * - Exception handling with 5-second backoff:
     *   Exceptions (as opposed to poll errors returned in the response) indicate a
     *   more serious issue (e.g., gRPC channel failure). We add a brief sleep to
     *   prevent tight-looping, then retry. The SDK will re-establish connectivity.
     */
    private void messageLoop() {
        log.info("Starting message loop on channel: {}", kubeMQConfig.getQueue().getChannel());

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                QueuesPollResponse response = queuesClient.receiveQueuesMessages(pollRequest);

                if (response.isError()) {
                    // Poll errors are typically transient (stream closed, timeout, etc.)
                    // The SDK will re-establish the stream on the next poll automatically.
                    log.error("Error polling: {}", response.getError());
                    continue;
                }

                if (response.getMessages() != null) {
                    for (QueueMessageReceived message : response.getMessages()) {
                        processMessage(message);
                    }
                }
            } catch (Exception e) {
                if (running.get() && !Thread.currentThread().isInterrupted()) {
                    log.error("Error in message loop", e);
                    sleep(5000);
                }
            }
        }

        log.info("Message loop terminated");
    }

    /**
     * Process a single queue message.
     *
     * The processing logic is simple:
     *   - Parse the JSON body into a Request object.
     *   - Call pagoServiceC.service() to process the request.
     *   - On success: ack() the message (removes it from the queue permanently).
     *   - On failure: reject() the message (returns it to the queue for retry).
     *
     * DLQ handling:
     *   The KubeMQ server tracks how many times a message has been rejected (receiveCount).
     *   When receiveCount reaches the attemptsBeforeDeadLetterQueue value set by the publisher,
     *   the server automatically routes the message to the configured DLQ channel.
     *   This subscriber does NOT need to check receiveCount or manually route to DLQ.
     *
     *   This includes poison pill messages (unparseable JSON, unexpected formats, etc.) -
     *   they will be rejected, retried by the server, and eventually routed to DLQ.
     *
     * Exception safety:
     *   If processing throws an exception, we catch it and reject the message.
     *   If reject() itself fails (e.g., transaction already completed due to visibility timeout),
     *   we log the error but do not crash the loop.
     */
    private void processMessage(QueueMessageReceived message) {
        String messageId = message.getId();
        log.info("Processing message: id={}, attempt={}", messageId, message.getReceiveCount());

        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            Request request = objectMapper.readValue(body, Request.class);
            boolean success = pagoServiceC.service(request);

            if (success) {
                message.ack();
                log.info("Message {} processed successfully", messageId);
            } else {
                // reject() returns the message to the queue. The server will redeliver it.
                // After the publisher-configured max retries, the server routes to DLQ.
                message.reject();
                log.warn("Message {} processing failed, rejected for retry", messageId);
            }
        } catch (Exception e) {
            log.error("Exception processing message {}", messageId, e);
            try {
                message.reject();
            } catch (Exception ex) {
                // This can happen if the visibility timer expired and the message was
                // already auto-rejected by the SDK. Safe to log and move on.
                log.error("Failed to reject message {}", messageId, ex);
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Health check: returns true if the worker thread is running and the client exists.
     * Can be wired to a Spring Boot Actuator HealthIndicator.
     */
    public boolean isRunning() {
        return running.get() && queuesClient != null;
    }

    /**
     * Health check: verifies the KubeMQ server is reachable via a ping.
     * Can be wired to a Spring Boot Actuator HealthIndicator.
     */
    public boolean isConnected() {
        if (queuesClient == null) return false;
        try {
            queuesClient.ping();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
