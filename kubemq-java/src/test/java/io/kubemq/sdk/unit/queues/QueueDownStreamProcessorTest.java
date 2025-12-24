package io.kubemq.sdk.unit.queues;

import io.kubemq.sdk.queues.QueueDownStreamProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueueDownStreamProcessor.
 */
@DisplayName("QueueDownStreamProcessor Tests")
class QueueDownStreamProcessorTest {

    @Nested
    @DisplayName("Task Execution Tests")
    class TaskExecutionTests {

        @Test
        @DisplayName("addTask executes task successfully")
        void addTask_executesTaskSuccessfully() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean taskExecuted = new AtomicBoolean(false);

            QueueDownStreamProcessor.addTask(() -> {
                taskExecuted.set(true);
                latch.countDown();
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS), "Task should complete within timeout");
            assertTrue(taskExecuted.get(), "Task should have been executed");
        }

        @Test
        @DisplayName("addTask executes multiple tasks in order")
        void addTask_executesMultipleTasksInOrder() throws InterruptedException {
            int taskCount = 5;
            CountDownLatch latch = new CountDownLatch(taskCount);
            AtomicInteger counter = new AtomicInteger(0);
            int[] executionOrder = new int[taskCount];

            for (int i = 0; i < taskCount; i++) {
                final int taskIndex = i;
                QueueDownStreamProcessor.addTask(() -> {
                    executionOrder[taskIndex] = counter.incrementAndGet();
                    latch.countDown();
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "All tasks should complete within timeout");
            assertEquals(taskCount, counter.get(), "All tasks should have been executed");

            // Verify tasks executed in order
            for (int i = 0; i < taskCount; i++) {
                assertEquals(i + 1, executionOrder[i], "Task " + i + " should have executed in order");
            }
        }

        @Test
        @DisplayName("addTask handles task exceptions gracefully")
        void addTask_handlesTaskExceptionsGracefully() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(2);
            AtomicBoolean secondTaskExecuted = new AtomicBoolean(false);

            // First task throws exception
            QueueDownStreamProcessor.addTask(() -> {
                latch.countDown();
                throw new RuntimeException("Test exception");
            });

            // Second task should still execute
            QueueDownStreamProcessor.addTask(() -> {
                secondTaskExecuted.set(true);
                latch.countDown();
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS), "Both tasks should complete within timeout");
            assertTrue(secondTaskExecuted.get(), "Second task should have executed despite first task exception");
        }

        @Test
        @DisplayName("addTask executes task with data")
        void addTask_executesTaskWithData() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            String testData = "test-data-value";
            AtomicBoolean dataProcessed = new AtomicBoolean(false);

            QueueDownStreamProcessor.addTask(() -> {
                // Simulate processing data
                if ("test-data-value".equals(testData)) {
                    dataProcessed.set(true);
                }
                latch.countDown();
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS), "Task should complete within timeout");
            assertTrue(dataProcessed.get(), "Task should have processed data correctly");
        }
    }

    @Nested
    @DisplayName("Concurrent Task Tests")
    class ConcurrentTaskTests {

        @Test
        @DisplayName("addTask handles concurrent task submissions")
        void addTask_handlesConcurrentTaskSubmissions() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(threadCount);
            AtomicInteger executedCount = new AtomicInteger(0);

            // Start multiple threads that submit tasks concurrently
            for (int i = 0; i < threadCount; i++) {
                new Thread(() -> {
                    try {
                        startLatch.await(); // Wait for all threads to be ready
                        QueueDownStreamProcessor.addTask(() -> {
                            executedCount.incrementAndGet();
                            completionLatch.countDown();
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }

            // Release all threads at once
            startLatch.countDown();

            assertTrue(completionLatch.await(10, TimeUnit.SECONDS), "All tasks should complete within timeout");
            assertEquals(threadCount, executedCount.get(), "All tasks should have been executed");
        }
    }
}
