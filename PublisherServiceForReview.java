package com.totalplay.receiver.receiverkubekube.event;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.core.JsonProcessingException;
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
    @PostConstruct
    public void start() {
        log.info("Initializing KubeMQ Subscriber Service");
        log.info("Server: {}", kubeMQConfig.getServer().getAddress());
        log.info("Channel: {}", kubeMQConfig.getQueue().getChannel());
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
    private void processMessage(QueueMessageReceived message) throws JsonProcessingException {
        String messageId = message.getId();
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String account = "";

        try {
            Request request = objectMapper.readValue(body, Request.class);
            account= request.getRequestApply().getAccount();
        log.info("Processing message: id={}, attempt={}, account={}", messageId, message.getReceiveCount(),account);


            boolean success = pagoServiceC.service(request);

            if (success) {
                message.ack();
                log.info("Message {} processed successfully account={}", messageId,account);
            } else {
                // reject() returns the message to the queue. The server will redeliver it.
                // After the publisher-configured max retries, the server routes to DLQ.
                message.reject();
                log.warn("Message {} processing failed, rejected for retry account={}", messageId,account);
            }
        } catch (Exception e) {
            log.error("Exception processing message {} account={}", messageId,account, e);
            try {
                message.reject();
            } catch (Exception ex) {
                // This can happen if the visibility timer expired and the message was
                // already auto-rejected by the SDK. Safe to log and move on.
                log.error("Failed to reject message {} account={}", messageId,account, ex);
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
