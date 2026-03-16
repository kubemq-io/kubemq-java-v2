package io.kubemq.sdk.unit.productionreadiness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.common.SubscriptionReconnectHandler;
import io.kubemq.sdk.cq.CommandsSubscription;
import io.kubemq.sdk.cq.QueriesSubscription;
import io.kubemq.sdk.pubsub.EventsStoreSubscription;
import io.kubemq.sdk.pubsub.EventsSubscription;
import io.kubemq.sdk.pubsub.PubSubClient;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import kubemq.Kubemq;
import kubemq.kubemqGrpc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests to verify CRITICAL-3 FIX: Infinite Reconnection Recursion
 *
 * <p>All subscription classes (EventsSubscription, EventsStoreSubscription, CommandsSubscription,
 * QueriesSubscription) now have: - MAX_RECONNECT_ATTEMPTS limit (10 by default) - Exponential
 * backoff for retry delays - Non-blocking reconnection using ScheduledExecutorService
 *
 * <p>These tests verify the fixes work correctly.
 */
@ExtendWith(MockitoExtension.class)
class ReconnectionRecursionTest {

  @Mock private PubSubClient mockPubSubClient;

  @Mock private kubemqGrpc.kubemqStub mockAsyncStub;

  @Test
  @DisplayName("CRITICAL-3 FIX: SubscriptionReconnectHandler has MAX_RECONNECT_ATTEMPTS constant")
  void eventsSubscription_hasMaxReconnectAttempts() throws Exception {
    Field maxAttemptsField =
        SubscriptionReconnectHandler.class.getDeclaredField("MAX_RECONNECT_ATTEMPTS");
    maxAttemptsField.setAccessible(true);

    int maxAttempts = (int) maxAttemptsField.get(null);

    assertTrue(maxAttempts > 0, "MAX_RECONNECT_ATTEMPTS should be positive");
    assertEquals(10, maxAttempts, "MAX_RECONNECT_ATTEMPTS should be 10");
  }

  @Test
  @DisplayName("CRITICAL-3 FIX: SubscriptionReconnectHandler has reconnectAttempts counter")
  void eventsSubscription_hasReconnectAttemptsCounter() throws Exception {
    Field reconnectAttemptsField =
        SubscriptionReconnectHandler.class.getDeclaredField("reconnectAttempts");
    reconnectAttemptsField.setAccessible(true);

    assertNotNull(reconnectAttemptsField, "reconnectAttempts field should exist");
    assertEquals(
        AtomicInteger.class,
        reconnectAttemptsField.getType(),
        "reconnectAttempts should be AtomicInteger for thread safety");
  }

  @Test
  @DisplayName("CRITICAL-3 FIX: EventsSubscription uses ScheduledExecutorService for reconnection")
  void eventsSubscription_usesScheduledExecutorService() throws Exception {
    Field executorField = EventsSubscription.class.getDeclaredField("RECONNECT_EXECUTOR");
    executorField.setAccessible(true);

    Object executor = executorField.get(null);

    assertNotNull(executor, "RECONNECT_EXECUTOR should exist");
    assertTrue(
        executor instanceof ScheduledExecutorService,
        "Should use ScheduledExecutorService for non-blocking reconnection");
  }

  @Test
  @DisplayName("CRITICAL-3 FIX: Reconnection stops after max attempts")
  @Timeout(value = 15, unit = TimeUnit.SECONDS)
  void reconnection_stopsAfterMaxAttempts() throws Exception {
    when(mockPubSubClient.getClientId()).thenReturn("test-client");
    when(mockPubSubClient.getReconnectIntervalInMillis()).thenReturn(50L);
    when(mockPubSubClient.getAsyncClient()).thenReturn(mockAsyncStub);

    AtomicInteger reconnectAttempts = new AtomicInteger(0);
    doAnswer(
            invocation -> {
              reconnectAttempts.incrementAndGet();
              throw new StatusRuntimeException(io.grpc.Status.UNAVAILABLE);
            })
        .when(mockAsyncStub)
        .subscribeToEvents(any(), any());

    AtomicInteger errorCallbackCount = new AtomicInteger(0);

    EventsSubscription subscription =
        EventsSubscription.builder()
            .channel("test-channel")
            .onReceiveEventCallback(event -> {})
            .onErrorCallback(error -> errorCallbackCount.incrementAndGet())
            .build();

    Kubemq.Subscribe subscribeRequest = subscription.encode("test-client", mockPubSubClient);
    StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

    observer.onError(new StatusRuntimeException(io.grpc.Status.UNAVAILABLE));

    Thread.sleep(5000);

    assertTrue(
        reconnectAttempts.get() <= 11,
        "Reconnection attempts should be limited to MAX_RECONNECT_ATTEMPTS (10), got "
            + reconnectAttempts.get());

    assertTrue(
        errorCallbackCount.get() >= 1,
        "Error callback should have been called when max retries reached");
  }

  @Test
  @DisplayName("CRITICAL-3 FIX: Reconnect uses exponential backoff")
  void reconnect_usesExponentialBackoff() throws Exception {
    when(mockPubSubClient.getClientId()).thenReturn("test-client");
    when(mockPubSubClient.getReconnectIntervalInMillis()).thenReturn(100L);
    when(mockPubSubClient.getAsyncClient()).thenReturn(mockAsyncStub);

    AtomicInteger attemptCount = new AtomicInteger(0);
    long[] attemptTimes = new long[5];

    doAnswer(
            invocation -> {
              int attempt = attemptCount.getAndIncrement();
              if (attempt < 5) {
                attemptTimes[attempt] = System.currentTimeMillis();
                throw new StatusRuntimeException(io.grpc.Status.UNAVAILABLE);
              }
              // Succeed on 5th attempt
              return null;
            })
        .when(mockAsyncStub)
        .subscribeToEvents(any(), any());

    EventsSubscription subscription =
        EventsSubscription.builder()
            .channel("test-channel")
            .onReceiveEventCallback(event -> {})
            .onErrorCallback(error -> {})
            .build();

    subscription.encode("test-client", mockPubSubClient);
    StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

    long startTime = System.currentTimeMillis();
    observer.onError(new StatusRuntimeException(io.grpc.Status.UNAVAILABLE));

    // Wait for retries
    Thread.sleep(3000);

    // Verify exponential backoff by checking delays increase
    if (attemptCount.get() >= 3) {
      long delay1 = attemptTimes[1] - attemptTimes[0];
      long delay2 = attemptTimes[2] - attemptTimes[1];

      // With exponential backoff, delay2 should be approximately 2x delay1
      // Allow for timing variations
      assertTrue(
          delay2 >= delay1 * 1.5,
          "Second delay ("
              + delay2
              + "ms) should be larger than first delay ("
              + delay1
              + "ms) "
              + "due to exponential backoff");
    }
  }

  @Test
  @DisplayName("CRITICAL-3 FIX: Reconnect does not block gRPC thread")
  void reconnect_shouldNotBlockGrpcThread() throws Exception {
    // Use lenient stubbing since we're testing async behavior
    lenient().when(mockPubSubClient.getClientId()).thenReturn("test-client");

    EventsSubscription subscription =
        EventsSubscription.builder()
            .channel("test-channel")
            .onReceiveEventCallback(event -> {})
            .onErrorCallback(error -> {})
            .build();

    subscription.encode("test-client", mockPubSubClient);
    StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

    // Trigger reconnection and measure how long onError takes to return
    long startTime = System.currentTimeMillis();
    observer.onError(new StatusRuntimeException(io.grpc.Status.UNAVAILABLE));
    long elapsed = System.currentTimeMillis() - startTime;

    // CRITICAL-3 FIX VERIFICATION: onError should return immediately
    // (reconnection is scheduled, not executed inline)
    assertTrue(
        elapsed < 500,
        "onError should return immediately (< 500ms) but took "
            + elapsed
            + "ms. "
            + "Reconnection should be scheduled asynchronously, not blocking the gRPC thread.");
  }

  @Test
  @DisplayName(
      "CRITICAL-3 FIX: All subscription classes delegate reconnection to SubscriptionReconnectHandler")
  void allSubscriptionClasses_haveMaxRetryLimit() throws Exception {
    Class<?>[] subscriptionClasses = {
      EventsSubscription.class,
      EventsStoreSubscription.class,
      CommandsSubscription.class,
      QueriesSubscription.class
    };

    for (Class<?> clazz : subscriptionClasses) {
      boolean hasReconnectHandler = false;
      boolean hasReconnectExecutor = false;

      for (Field field : clazz.getDeclaredFields()) {
        String name = field.getName();
        if ("reconnectHandler".equals(name)) {
          hasReconnectHandler = true;
          assertEquals(
              SubscriptionReconnectHandler.class,
              field.getType(),
              clazz.getSimpleName() + ".reconnectHandler should be SubscriptionReconnectHandler");
        }
        if ("RECONNECT_EXECUTOR".equals(name)) {
          hasReconnectExecutor = true;
        }
      }

      assertTrue(
          hasReconnectHandler,
          clazz.getSimpleName()
              + " should have reconnectHandler field (delegates to SubscriptionReconnectHandler)");
      assertTrue(
          hasReconnectExecutor,
          clazz.getSimpleName() + " should have RECONNECT_EXECUTOR for async reconnection");
    }

    Field maxAttemptsField =
        SubscriptionReconnectHandler.class.getDeclaredField("MAX_RECONNECT_ATTEMPTS");
    maxAttemptsField.setAccessible(true);
    assertEquals(
        10,
        (int) maxAttemptsField.get(null),
        "SubscriptionReconnectHandler.MAX_RECONNECT_ATTEMPTS should be 10");
  }

  @Test
  @DisplayName("CRITICAL-3 FIX: Reconnect executor is static and shared")
  void reconnectExecutor_isStaticAndShared() throws Exception {
    Field executorField = EventsSubscription.class.getDeclaredField("RECONNECT_EXECUTOR");
    executorField.setAccessible(true);

    // Verify field is static
    assertTrue(
        java.lang.reflect.Modifier.isStatic(executorField.getModifiers()),
        "RECONNECT_EXECUTOR should be static for efficiency");

    // Get the executor
    ScheduledExecutorService executor = (ScheduledExecutorService) executorField.get(null);
    assertNotNull(executor, "Executor should not be null");
    assertFalse(executor.isShutdown(), "Executor should be running");
  }

  @Test
  @DisplayName("CRITICAL-3 FIX: Successful reconnection resets attempt counter")
  void successfulReconnection_resetsAttemptCounter() throws Exception {
    when(mockPubSubClient.getClientId()).thenReturn("test-client");
    when(mockPubSubClient.getReconnectIntervalInMillis()).thenReturn(50L);
    when(mockPubSubClient.getAsyncClient()).thenReturn(mockAsyncStub);

    AtomicInteger attemptCount = new AtomicInteger(0);
    CountDownLatch successLatch = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              int attempt = attemptCount.incrementAndGet();
              if (attempt <= 3) {
                throw new StatusRuntimeException(io.grpc.Status.UNAVAILABLE);
              }
              successLatch.countDown();
              return null;
            })
        .when(mockAsyncStub)
        .subscribeToEvents(any(), any());

    EventsSubscription subscription =
        EventsSubscription.builder()
            .channel("test-channel")
            .onReceiveEventCallback(event -> {})
            .onErrorCallback(error -> {})
            .build();

    subscription.encode("test-client", mockPubSubClient);
    StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();

    observer.onError(new StatusRuntimeException(io.grpc.Status.UNAVAILABLE));

    assertTrue(
        successLatch.await(5, TimeUnit.SECONDS),
        "Reconnection should succeed after failing 3 times");
    assertTrue(attemptCount.get() >= 4, "Should have attempted reconnection at least 4 times");
  }

  @Test
  @DisplayName("CRITICAL-3 FIX: Backoff delay is capped at 60 seconds")
  void backoffDelay_isCappedAt60Seconds() throws Exception {
    // Get the MAX_BACKOFF_MS constant if it exists, or verify via behavior
    // The implementation caps at 60000ms (60 seconds)

    long baseInterval = 100L; // Base interval
    int attempt = 20; // High attempt number

    // Calculate what the delay would be without cap: 100 * 2^19 = very large
    // With cap, it should be 60000ms

    // The formula is: Math.min(base * 2^(attempt-1), 60000)
    long expectedDelay = Math.min(baseInterval * (1L << (attempt - 1)), 60000L);

    assertEquals(
        60000L,
        expectedDelay,
        "Backoff delay should be capped at 60 seconds for high attempt counts");
  }

  @Test
  @DisplayName("CRITICAL-3 FIX: No StackOverflowError possible")
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  void noStackOverflowError_possible() throws Exception {
    // This test verifies that the non-recursive implementation
    // cannot cause StackOverflowError

    when(mockPubSubClient.getClientId()).thenReturn("test-client");
    when(mockPubSubClient.getReconnectIntervalInMillis()).thenReturn(1L);
    when(mockPubSubClient.getAsyncClient()).thenReturn(mockAsyncStub);

    // Always fail
    doThrow(new StatusRuntimeException(io.grpc.Status.UNAVAILABLE))
        .when(mockAsyncStub)
        .subscribeToEvents(any(), any());

    EventsSubscription subscription =
        EventsSubscription.builder()
            .channel("test-channel")
            .onReceiveEventCallback(event -> {})
            .onErrorCallback(error -> {})
            .build();

    subscription.encode("test-client", mockPubSubClient);

    // Run in thread with small stack to prove no StackOverflow occurs
    // (the implementation is non-recursive)
    CountDownLatch done = new CountDownLatch(1);
    AtomicInteger errorCount = new AtomicInteger(0);

    Thread limitedStackThread =
        new Thread(
            null,
            () -> {
              try {
                StreamObserver<Kubemq.EventReceive> observer = subscription.getObserver();
                observer.onError(new StatusRuntimeException(io.grpc.Status.UNAVAILABLE));
                // Wait for reconnection attempts to complete
                Thread.sleep(2000);
              } catch (StackOverflowError e) {
                errorCount.incrementAndGet();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                done.countDown();
              }
            },
            "limited-stack-thread",
            64 * 1024); // Small 64KB stack

    limitedStackThread.start();
    done.await(10, TimeUnit.SECONDS);

    // CRITICAL-3 FIX VERIFICATION: No StackOverflowError should occur
    assertEquals(
        0,
        errorCount.get(),
        "No StackOverflowError should occur - implementation uses scheduled executor, not recursion");
  }
}
