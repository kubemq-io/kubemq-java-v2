package io.kubemq.sdk.client;

import io.kubemq.sdk.exception.BackpressureException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Bounded FIFO buffer for messages published during reconnection. Thread-safe. Messages are
 * buffered while the client is in RECONNECTING state and flushed in FIFO order when the connection
 * is re-established.
 */
public class MessageBuffer {

  private final ConcurrentLinkedQueue<BufferedMessage> queue = new ConcurrentLinkedQueue<>();
  private final AtomicLong currentSizeBytes = new AtomicLong(0);
  private final long maxSizeBytes;
  private final BufferOverflowPolicy overflowPolicy;

  private final ReentrantLock blockLock = new ReentrantLock();
  private final Condition spaceAvailable = blockLock.newCondition();

  private volatile IntConsumer onBufferDrainCallback;

  /**
   * Constructs a new instance.
   *
   * @param maxSizeBytes the max size bytes
   * @param overflowPolicy the overflow policy
   */
  public MessageBuffer(long maxSizeBytes, BufferOverflowPolicy overflowPolicy) {
    this.maxSizeBytes = maxSizeBytes;
    this.overflowPolicy = overflowPolicy;
  }

  /**
   * Register a callback invoked when buffered messages are discarded. The callback receives the
   * count of discarded messages.
   *
   * @param callback the callback
   */
  public void setOnBufferDrainCallback(IntConsumer callback) {
    this.onBufferDrainCallback = callback;
  }

  /**
   * Add a message to the buffer.
   *
   * @param message the message to buffer
   * @throws BackpressureException if policy is ERROR and buffer is full
   * @throws InterruptedException if policy is BLOCK and thread is interrupted while waiting
   */
  public void add(BufferedMessage message) throws InterruptedException {
    long messageSize = message.estimatedSizeBytes();

    if (overflowPolicy == BufferOverflowPolicy.BLOCK) {
      blockLock.lock();
      try {
        while (currentSizeBytes.get() + messageSize > maxSizeBytes) {
          spaceAvailable.await();
        }
        enqueue(message, messageSize);
      } finally {
        blockLock.unlock();
      }
    } else {
      int casRetries = 0;
      while (casRetries < 100) {
        long current = currentSizeBytes.get();
        if (current + messageSize > maxSizeBytes) {
          throw BackpressureException.bufferFull(current, maxSizeBytes, messageSize);
        }
        if (currentSizeBytes.compareAndSet(current, current + messageSize)) {
          queue.add(message);
          return;
        }
        casRetries++;
        Thread.onSpinWait();
      }
      throw BackpressureException.bufferFull(currentSizeBytes.get(), maxSizeBytes, messageSize);
    }
  }

  private void enqueue(BufferedMessage message, long messageSize) {
    queue.add(message);
    currentSizeBytes.addAndGet(messageSize);
  }

  /**
   * Drain all buffered messages in FIFO order. Called after successful reconnection.
   *
   * @param sender the sender
   */
  public void flush(Consumer<BufferedMessage> sender) {
    BufferedMessage msg;
    while ((msg = queue.poll()) != null) {
      long size = msg.estimatedSizeBytes();
      currentSizeBytes.addAndGet(-size);
      sender.accept(msg);
      if (overflowPolicy == BufferOverflowPolicy.BLOCK) {
        blockLock.lock();
        try {
          spaceAvailable.signalAll();
        } finally {
          blockLock.unlock();
        }
      }
    }
  }

  /**
   * Discard all buffered messages. Invokes OnBufferDrain callback with count. Called when
   * transitioning to CLOSED state.
   */
  public void discardAll() {
    int count = 0;
    while (queue.poll() != null) {
      count++;
    }
    currentSizeBytes.set(0);

    if (overflowPolicy == BufferOverflowPolicy.BLOCK) {
      blockLock.lock();
      try {
        spaceAvailable.signalAll();
      } finally {
        blockLock.unlock();
      }
    }

    if (count > 0 && onBufferDrainCallback != null) {
      onBufferDrainCallback.accept(count);
    }
  }

  /**
   * Returns the current size.
   *
   * @return the result
   */
  public int size() {
    return queue.size();
  }

  /**
   * Returns the current size in bytes.
   *
   * @return the result
   */
  public long currentSizeBytes() {
    return currentSizeBytes.get();
  }
}
