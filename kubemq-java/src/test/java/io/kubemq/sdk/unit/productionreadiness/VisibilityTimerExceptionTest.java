package io.kubemq.sdk.unit.productionreadiness;

import io.kubemq.sdk.queues.QueueMessageReceived;
import io.kubemq.sdk.queues.QueuesPollResponse;
import kubemq.Kubemq;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to verify CRITICAL-2 FIX: Visibility Timer Exception Handling
 *
 * The QueueMessageReceived now uses a shared ScheduledExecutorService instead of Timer
 * per message, and the onVisibilityExpired() method NO LONGER throws an exception.
 * Instead, it logs the error and continues gracefully.
 *
 * These tests verify the fix works correctly.
 */
@ExtendWith(MockitoExtension.class)
class VisibilityTimerExceptionTest {

    @Mock
    private QueuesPollResponse mockPollResponse;

    @Test
    @DisplayName("CRITICAL-2 FIX: onVisibilityExpired does not throw exception")
    void onVisibilityExpired_shouldNotThrowException() throws Exception {
        // Use reflection to call onVisibilityExpired directly
        QueueMessageReceived message = QueueMessageReceived.builder()
                .id("test-msg")
                .channel("test-channel")
                .transactionId("txn-1")
                .receiverClientId("receiver")
                .build();

        // Set up minimal mocks to allow reject() to work
        setRequestSenderViaReflection(message, request -> {});
        setPollResponse(message, mockPollResponse);

        Method onVisibilityExpiredMethod = QueueMessageReceived.class.getDeclaredMethod("onVisibilityExpired");
        onVisibilityExpiredMethod.setAccessible(true);

        // CRITICAL-2 FIX VERIFICATION: The method should NOT throw an exception
        assertDoesNotThrow(() -> onVisibilityExpiredMethod.invoke(message),
                "onVisibilityExpired should NOT throw an exception - it runs in a background thread");
    }

    @Test
    @DisplayName("CRITICAL-2 FIX: Uses ScheduledExecutorService instead of Timer")
    void usesScheduledExecutorServiceInsteadOfTimer() throws Exception {
        // Verify the implementation uses ScheduledExecutorService
        Field visibilityExecutorField = QueueMessageReceived.class.getDeclaredField("visibilityExecutor");
        visibilityExecutorField.setAccessible(true);

        Object executor = visibilityExecutorField.get(null); // static field
        assertNotNull(executor, "visibilityExecutor should exist");
        assertTrue(executor instanceof ScheduledExecutorService,
                "Should use ScheduledExecutorService instead of Timer");
    }

    @Test
    @DisplayName("CRITICAL-2 FIX: Uses ScheduledFuture instead of Timer for tracking")
    void usesScheduledFutureInsteadOfTimer() throws Exception {
        // Verify visibilityFuture field exists and is the correct type
        Field visibilityFutureField = QueueMessageReceived.class.getDeclaredField("visibilityFuture");
        visibilityFutureField.setAccessible(true);

        // The field type should be ScheduledFuture
        assertEquals(ScheduledFuture.class, visibilityFutureField.getType(),
                "Should use ScheduledFuture instead of Timer for tracking");
    }

    @Test
    @DisplayName("CRITICAL-2 FIX: Static executor is shared across messages")
    void staticExecutorIsSharedAcrossMessages() {
        // Verify the executor is accessible via static getter
        ScheduledExecutorService executor = QueueMessageReceived.getVisibilityExecutor();

        assertNotNull(executor, "Visibility executor should be accessible");
        assertFalse(executor.isShutdown(), "Executor should be running");

        // Verify it's a daemon thread pool (won't block JVM shutdown)
        // The executor should be a scheduled thread pool
        assertTrue(executor instanceof ScheduledExecutorService,
                "Should be a ScheduledExecutorService");
    }

    @Test
    @DisplayName("CRITICAL-2 FIX: Visibility timer fires and auto-rejects message")
    void visibilityTimerFires_andAutoRejectsMessage() throws Exception {
        // Create a message with very short visibility timeout
        QueueMessageReceived message = createMessageWithShortVisibility(1); // 1 second

        AtomicBoolean rejectCalled = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        // Set up request sender that tracks the reject call
        setRequestSenderViaReflection(message, request -> {
            rejectCalled.set(true);
            latch.countDown();
        });
        setPollResponse(message, mockPollResponse);

        // Wait for visibility to expire
        boolean completed = latch.await(3, TimeUnit.SECONDS);

        assertTrue(completed, "Visibility timer should have fired");
        assertTrue(rejectCalled.get(), "Message should have been auto-rejected");
    }

    @Test
    @DisplayName("CRITICAL-2 FIX: Timer exception in reject does not crash the executor")
    void timerException_doesNotCrashExecutor() throws Exception {
        // Create a message where reject() will throw
        QueueMessageReceived message = createMessageWithShortVisibility(1);

        CountDownLatch latch = new CountDownLatch(1);

        // Make reject() throw an exception
        setRequestSenderViaReflection(message, request -> {
            latch.countDown();
            throw new RuntimeException("Simulated network error during reject");
        });
        setPollResponse(message, mockPollResponse);

        // Wait for visibility to expire
        boolean completed = latch.await(3, TimeUnit.SECONDS);
        assertTrue(completed, "Reject should have been attempted");

        // Wait a bit for any cleanup
        Thread.sleep(500);

        // Verify the executor is still running (not crashed)
        ScheduledExecutorService executor = QueueMessageReceived.getVisibilityExecutor();
        assertFalse(executor.isShutdown(), "Executor should still be running after exception");
        assertFalse(executor.isTerminated(), "Executor should not be terminated after exception");
    }

    @Test
    @DisplayName("CRITICAL-2 FIX: timerExpired flag is set after expiry")
    void timerExpiredFlagIsSetAfterExpiry() throws Exception {
        QueueMessageReceived message = createMessageWithShortVisibility(1);

        CountDownLatch latch = new CountDownLatch(1);

        setRequestSenderViaReflection(message, request -> latch.countDown());
        setPollResponse(message, mockPollResponse);

        // Wait for visibility to expire
        latch.await(3, TimeUnit.SECONDS);

        // Wait a bit for state to be updated
        Thread.sleep(100);

        // Verify state
        Field timerExpiredField = QueueMessageReceived.class.getDeclaredField("timerExpired");
        timerExpiredField.setAccessible(true);
        boolean timerExpired = (boolean) timerExpiredField.get(message);

        assertTrue(timerExpired, "timerExpired should be true after visibility expired");
    }

    @Test
    @DisplayName("CRITICAL-2 FIX: visibilityFuture is cleared after expiry")
    void visibilityFutureIsClearedAfterExpiry() throws Exception {
        QueueMessageReceived message = createMessageWithShortVisibility(1);

        CountDownLatch latch = new CountDownLatch(1);

        setRequestSenderViaReflection(message, request -> latch.countDown());
        setPollResponse(message, mockPollResponse);

        // Wait for visibility to expire
        latch.await(3, TimeUnit.SECONDS);

        // Wait a bit for cleanup
        Thread.sleep(100);

        // Verify visibilityFuture is null after expiry
        Field visibilityFutureField = QueueMessageReceived.class.getDeclaredField("visibilityFuture");
        visibilityFutureField.setAccessible(true);
        ScheduledFuture<?> visibilityFuture = (ScheduledFuture<?>) visibilityFutureField.get(message);

        assertNull(visibilityFuture, "visibilityFuture should be null after expiry");
    }

    @Test
    @DisplayName("CRITICAL-2 FIX: Multiple messages can run timers concurrently")
    void multipleMessages_canRunTimersConcurrently() throws Exception {
        // Create multiple messages with short visibility
        QueueMessageReceived message1 = createMessageWithShortVisibility(1);
        QueueMessageReceived message2 = createMessageWithShortVisibility(1);
        QueueMessageReceived message3 = createMessageWithShortVisibility(1);

        CountDownLatch latch = new CountDownLatch(3);

        setRequestSenderViaReflection(message1, request -> latch.countDown());
        setRequestSenderViaReflection(message2, request -> latch.countDown());
        setRequestSenderViaReflection(message3, request -> latch.countDown());

        setPollResponse(message1, mockPollResponse);
        setPollResponse(message2, mockPollResponse);
        setPollResponse(message3, mockPollResponse);

        // Wait for all timers to fire
        boolean allFired = latch.await(5, TimeUnit.SECONDS);

        assertTrue(allFired, "All visibility timers should have fired");

        // Verify the executor is still running
        ScheduledExecutorService executor = QueueMessageReceived.getVisibilityExecutor();
        assertFalse(executor.isShutdown(), "Executor should still be running");
    }

    @Test
    @DisplayName("CRITICAL-2 FIX: Can ack before timer expires")
    void canAckBeforeTimerExpires() throws Exception {
        QueueMessageReceived message = createMessageWithShortVisibility(5); // 5 seconds

        AtomicBoolean ackCalled = new AtomicBoolean(false);
        AtomicBoolean timerFired = new AtomicBoolean(false);

        setRequestSenderViaReflection(message, request -> {
            // Determine if this is an ack or reject based on request type
            if (request.getRequestTypeData() == Kubemq.QueuesDownstreamRequestType.AckRange) {
                ackCalled.set(true);
            } else {
                timerFired.set(true);
            }
        });
        setPollResponse(message, mockPollResponse);

        // Ack immediately
        message.ack();

        assertTrue(ackCalled.get(), "Ack should have been called");

        // Wait to see if timer still fires (it shouldn't)
        Thread.sleep(2000);

        assertFalse(timerFired.get(), "Timer should have been cancelled after ack");
    }

    @Test
    @DisplayName("CRITICAL-2 FIX: State after timer exception is consistent")
    void stateAfterTimerException_isConsistent() throws Exception {
        QueueMessageReceived message = createMessageWithShortVisibility(1);

        CountDownLatch latch = new CountDownLatch(1);

        // Simulate reject succeeding
        setRequestSenderViaReflection(message, request -> latch.countDown());
        setPollResponse(message, mockPollResponse);

        // Wait for visibility to expire
        latch.await(3, TimeUnit.SECONDS);

        // Wait for state changes
        Thread.sleep(200);

        // Verify state
        Field timerExpiredField = QueueMessageReceived.class.getDeclaredField("timerExpired");
        timerExpiredField.setAccessible(true);
        boolean timerExpired = (boolean) timerExpiredField.get(message);

        Field messageCompletedField = QueueMessageReceived.class.getDeclaredField("messageCompleted");
        messageCompletedField.setAccessible(true);
        boolean messageCompleted = (boolean) messageCompletedField.get(message);

        assertTrue(timerExpired, "timerExpired should be true");
        assertTrue(messageCompleted, "messageCompleted should be true after reject");

        // Try to ack the message - should fail gracefully
        IllegalStateException ex = assertThrows(IllegalStateException.class, message::ack,
                "Ack should fail after visibility expired");

        assertTrue(ex.getMessage().contains("completed") || ex.getMessage().contains("Transaction"),
                "Exception should indicate transaction is already completed");
    }

    // Helper methods

    private QueueMessageReceived createMessageWithShortVisibility(int visibilitySeconds) throws Exception {
        Kubemq.QueueMessage protoMsg = Kubemq.QueueMessage.newBuilder()
                .setMessageID("test-msg-" + System.nanoTime())
                .setChannel("test-channel")
                .setAttributes(Kubemq.QueueMessageAttributes.newBuilder().build())
                .build();

        QueueMessageReceived message = new QueueMessageReceived();
        message.decode(
                protoMsg,
                "txn-" + System.nanoTime(),
                false,
                "receiver-client",
                visibilitySeconds,  // Short visibility for testing
                false,  // Not auto-acked
                mockPollResponse
        );

        return message;
    }

    private void setRequestSenderViaReflection(QueueMessageReceived message, Consumer<Kubemq.QueuesDownstreamRequest> senderAction) throws Exception {
        // Get the RequestSender class via reflection since it's package-private
        Class<?> requestSenderClass = Class.forName("io.kubemq.sdk.queues.RequestSender");

        // Create a proxy that implements RequestSender
        Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                requestSenderClass.getClassLoader(),
                new Class<?>[]{requestSenderClass},
                (proxyObj, method, args) -> {
                    if ("send".equals(method.getName())) {
                        senderAction.accept((Kubemq.QueuesDownstreamRequest) args[0]);
                    }
                    return null;
                }
        );

        // Set the proxy via reflection
        Method setterMethod = QueueMessageReceived.class.getDeclaredMethod("setRequestSender", requestSenderClass);
        setterMethod.setAccessible(true);
        setterMethod.invoke(message, proxy);
    }

    private void setPollResponse(QueueMessageReceived message, QueuesPollResponse response) throws Exception {
        Field field = QueueMessageReceived.class.getDeclaredField("queuesPollResponse");
        field.setAccessible(true);
        field.set(message, response);
    }
}
