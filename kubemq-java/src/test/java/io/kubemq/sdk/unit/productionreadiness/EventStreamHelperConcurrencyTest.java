package io.kubemq.sdk.unit.productionreadiness;

import io.grpc.stub.StreamObserver;
import io.kubemq.sdk.client.KubeMQClient;
import io.kubemq.sdk.pubsub.EventSendResult;
import io.kubemq.sdk.pubsub.EventStreamHelper;
import kubemq.Kubemq;
import kubemq.kubemqGrpc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests to verify CRITICAL-1 FIX: EventStreamHelper Thread Safety
 *
 * The EventStreamHelper now uses a ConcurrentHashMap (pendingResponses) with per-request
 * CompletableFutures instead of a single shared future. This ensures thread-safe concurrent
 * access where each request gets its own response.
 *
 * These tests verify the fix works correctly.
 */
@ExtendWith(MockitoExtension.class)
class EventStreamHelperConcurrencyTest {

    @Mock
    private KubeMQClient mockClient;

    @Mock
    private kubemqGrpc.kubemqStub mockAsyncStub;

    @Mock
    private StreamObserver<Kubemq.Event> mockEventObserver;

    private EventStreamHelper eventStreamHelper;
    private StreamObserver<Kubemq.Result> capturedResultObserver;

    @BeforeEach
    void setup() {
        eventStreamHelper = new EventStreamHelper();
    }

    @Test
    @DisplayName("CRITICAL-1 FIX: Concurrent sendEventStoreMessage calls each receive their own response")
    void concurrentSendEventStoreMessage_shouldReceiveCorrectResponses() throws Exception {
        when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);

        // Capture the result observer and event observer to simulate responses
        ArgumentCaptor<StreamObserver<Kubemq.Result>> resultObserverCaptor =
                ArgumentCaptor.forClass(StreamObserver.class);

        doAnswer(invocation -> {
            capturedResultObserver = invocation.getArgument(0);
            return mockEventObserver;
        }).when(mockAsyncStub).sendEventsStream(any());

        // Capture sent events to track their IDs
        List<String> sentEventIds = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            Kubemq.Event event = invocation.getArgument(0);
            sentEventIds.add(event.getEventID());
            return null;
        }).when(mockEventObserver).onNext(any());

        int numThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<EventSendResult>> futures = new ArrayList<>();

        // Submit concurrent tasks
        for (int i = 0; i < numThreads; i++) {
            final int threadNum = i;
            final String originalEventId = "event-" + threadNum;

            Future<EventSendResult> future = executor.submit(() -> {
                startLatch.await(); // Wait for all threads to be ready

                Kubemq.Event event = Kubemq.Event.newBuilder()
                        .setEventID(originalEventId)
                        .setChannel("test-channel")
                        .setStore(true)
                        .build();

                return eventStreamHelper.sendEventStoreMessage(mockClient, event);
            });
            futures.add(future);
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait a bit for all threads to call sendEventStoreMessage and send events
        Thread.sleep(300);

        // Now simulate responses for each event using the captured event IDs
        // The new implementation generates unique IDs for each request
        for (String eventId : sentEventIds) {
            Kubemq.Result result = Kubemq.Result.newBuilder()
                    .setEventID(eventId)
                    .setSent(true)
                    .build();

            if (capturedResultObserver != null) {
                capturedResultObserver.onNext(result);
            }
            Thread.sleep(20); // Small delay between responses
        }

        // Collect results
        int successCount = 0;
        int timeoutCount = 0;

        for (Future<EventSendResult> future : futures) {
            try {
                EventSendResult result = future.get(5, TimeUnit.SECONDS);
                if (result != null && result.isSent()) {
                    successCount++;
                }
            } catch (TimeoutException e) {
                timeoutCount++;
            } catch (Exception e) {
                // Other exceptions
            }
        }

        executor.shutdown();

        // CRITICAL-1 FIX VERIFICATION: Each thread should receive a response
        assertEquals(numThreads, successCount,
                "All " + numThreads + " threads should receive a response. Got " + successCount +
                " successes and " + timeoutCount + " timeouts.");
    }

    @Test
    @DisplayName("CRITICAL-1 FIX: Second concurrent call does not overwrite first call's future")
    void secondConcurrentCall_shouldNotOverwriteFirstFuture() throws Exception {
        when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);

        List<String> sentEventIds = new CopyOnWriteArrayList<>();

        doAnswer(invocation -> {
            capturedResultObserver = invocation.getArgument(0);
            return mockEventObserver;
        }).when(mockAsyncStub).sendEventsStream(any());

        doAnswer(invocation -> {
            Kubemq.Event event = invocation.getArgument(0);
            sentEventIds.add(event.getEventID());
            return null;
        }).when(mockEventObserver).onNext(any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch thread1Started = new CountDownLatch(1);
        CountDownLatch allDone = new CountDownLatch(2);

        AtomicInteger thread1Result = new AtomicInteger(-1);
        AtomicInteger thread2Result = new AtomicInteger(-1);

        // Thread 1: Start first, wait for response
        Future<?> future1 = executor.submit(() -> {
            Kubemq.Event event = Kubemq.Event.newBuilder()
                    .setEventID("event-1")
                    .setChannel("test-channel")
                    .setStore(true)
                    .build();

            thread1Started.countDown();

            try {
                EventSendResult result = eventStreamHelper.sendEventStoreMessage(mockClient, event);
                if (result != null && result.isSent()) {
                    thread1Result.set(1); // Got valid result
                } else if (result != null) {
                    thread1Result.set(0); // Got result with error
                }
            } catch (Exception e) {
                thread1Result.set(-2); // Exception
            } finally {
                allDone.countDown();
            }
        });

        // Wait for thread 1 to start
        thread1Started.await();
        Thread.sleep(100);

        // Thread 2: Start after thread 1, should get its own future
        Future<?> future2 = executor.submit(() -> {
            Kubemq.Event event = Kubemq.Event.newBuilder()
                    .setEventID("event-2")
                    .setChannel("test-channel")
                    .setStore(true)
                    .build();

            try {
                EventSendResult result = eventStreamHelper.sendEventStoreMessage(mockClient, event);
                if (result != null && result.isSent()) {
                    thread2Result.set(1); // Got valid result
                } else if (result != null) {
                    thread2Result.set(0); // Got result with error
                }
            } catch (Exception e) {
                thread2Result.set(-2); // Exception
            } finally {
                allDone.countDown();
            }
        });

        Thread.sleep(200);

        // Send responses for both events using the captured IDs
        for (String eventId : sentEventIds) {
            Kubemq.Result result = Kubemq.Result.newBuilder()
                    .setEventID(eventId)
                    .setSent(true)
                    .build();
            capturedResultObserver.onNext(result);
            Thread.sleep(50);
        }

        // Wait for completion
        allDone.await(5, TimeUnit.SECONDS);

        executor.shutdown();

        // CRITICAL-1 FIX VERIFICATION: Both threads should receive valid responses
        assertEquals(1, thread1Result.get(),
                "Thread 1 should receive a valid response");
        assertEquals(1, thread2Result.get(),
                "Thread 2 should receive a valid response");
    }

    @Test
    @DisplayName("CRITICAL-1 FIX: Verify pendingResponses uses per-request futures")
    void verifyPendingResponsesUsesPerRequestFutures() throws Exception {
        // Use reflection to verify the implementation uses per-request futures
        Field pendingResponsesField = EventStreamHelper.class.getDeclaredField("pendingResponses");
        pendingResponsesField.setAccessible(true);

        Map<String, CompletableFuture<EventSendResult>> pendingResponses =
                (Map<String, CompletableFuture<EventSendResult>>) pendingResponsesField.get(eventStreamHelper);

        assertNotNull(pendingResponses, "pendingResponses map should exist");
        assertTrue(pendingResponses instanceof ConcurrentHashMap,
                "pendingResponses should be a ConcurrentHashMap for thread safety");

        // Verify the map is initially empty
        assertTrue(pendingResponses.isEmpty(), "pendingResponses should be empty initially");
    }

    @Test
    @DisplayName("CRITICAL-1 FIX: Verify requestTimestamps exists for timeout tracking")
    void verifyRequestTimestampsExists() throws Exception {
        // Use reflection to verify the timeout tracking mechanism exists
        Field requestTimestampsField = EventStreamHelper.class.getDeclaredField("requestTimestamps");
        requestTimestampsField.setAccessible(true);

        Map<String, Long> requestTimestamps =
                (Map<String, Long>) requestTimestampsField.get(eventStreamHelper);

        assertNotNull(requestTimestamps, "requestTimestamps map should exist for timeout tracking");
        assertTrue(requestTimestamps instanceof ConcurrentHashMap,
                "requestTimestamps should be a ConcurrentHashMap for thread safety");
    }

    @Test
    @DisplayName("CRITICAL-1 FIX: Verify cleanup executor exists for stale request cleanup")
    void verifyCleanupExecutorExists() {
        // Verify the cleanup executor is accessible
        ScheduledExecutorService cleanupExecutor = EventStreamHelper.getCleanupExecutor();

        assertNotNull(cleanupExecutor, "Cleanup executor should exist");
        assertFalse(cleanupExecutor.isShutdown(), "Cleanup executor should be running");
    }

    @Test
    @DisplayName("CRITICAL-1 FIX: Responses are matched by event ID")
    void responsesAreMatchedByEventId() throws Exception {
        when(mockClient.getAsyncClient()).thenReturn(mockAsyncStub);
        lenient().when(mockClient.getRequestTimeoutSeconds()).thenReturn(30);

        List<String> sentEventIds = new CopyOnWriteArrayList<>();

        doAnswer(invocation -> {
            capturedResultObserver = invocation.getArgument(0);
            return mockEventObserver;
        }).when(mockAsyncStub).sendEventsStream(any());

        doAnswer(invocation -> {
            Kubemq.Event event = invocation.getArgument(0);
            sentEventIds.add(event.getEventID());
            return null;
        }).when(mockEventObserver).onNext(any());

        // Send two events
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        List<EventSendResult> results = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 2; i++) {
            final int idx = i;
            executor.submit(() -> {
                Kubemq.Event event = Kubemq.Event.newBuilder()
                        .setEventID("original-event-" + idx)
                        .setChannel("test-channel")
                        .setStore(true)
                        .build();
                try {
                    EventSendResult result = eventStreamHelper.sendEventStoreMessage(mockClient, event);
                    results.add(result);
                } catch (Exception e) {
                    // Ignore
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for events to be sent (longer timeout for CI environments)
        Thread.sleep(500);

        // Send responses in REVERSE order to verify ID matching works
        for (int i = sentEventIds.size() - 1; i >= 0; i--) {
            Kubemq.Result result = Kubemq.Result.newBuilder()
                    .setEventID(sentEventIds.get(i))
                    .setSent(true)
                    .build();
            capturedResultObserver.onNext(result);
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Both requests should still get matched correctly despite reverse order
        assertEquals(2, results.size(), "Both requests should receive responses");
        for (EventSendResult result : results) {
            assertTrue(result.isSent(), "Each result should indicate sent=true");
        }
    }
}
