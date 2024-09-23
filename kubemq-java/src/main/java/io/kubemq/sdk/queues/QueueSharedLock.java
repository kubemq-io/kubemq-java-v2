package io.kubemq.sdk.queues;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class QueueSharedLock {
    // Singleton Lock instance shared across the application
    private static final Lock sharedLock = new ReentrantLock();

    public static Lock getLock() {
        return sharedLock;
    }
}
