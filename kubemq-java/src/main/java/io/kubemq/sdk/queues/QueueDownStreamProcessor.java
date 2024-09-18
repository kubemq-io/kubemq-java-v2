package io.kubemq.sdk.queues;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * Define the class that handles queue message responses
 */
public class QueueDownStreamProcessor {

    // Single static queue to hold message processing tasks
    private static final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();

    // Executor service to run the task queue processor
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // Static block to start the worker thread
    static {
        executorService.submit(() -> {
            while (true) {
                try {
                    // Take and execute the next task from the queue
                    Runnable task = taskQueue.take();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore interrupt flag
                    break; // Exit if interrupted
                } catch (Exception e) {
                    // Log and handle other exceptions
                    e.printStackTrace();
                }
            }
        });
    }

    // Add task to the queue
    public static void addTask(Runnable task) {
        try {
            taskQueue.put(task); // Block if the queue is full
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
