package io.kubemq.sdk.unit.client;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.client.BufferOverflowPolicy;
import io.kubemq.sdk.client.BufferedMessage;
import io.kubemq.sdk.client.MessageBuffer;
import io.kubemq.sdk.exception.BackpressureException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MessageBufferTest {

  private BufferedMessage makeMessage(long sizeBytes) {
    return new BufferedMessage() {
      @Override
      public long estimatedSizeBytes() {
        return sizeBytes;
      }

      @Override
      public Object grpcRequest() {
        return "test";
      }

      @Override
      public MessageType messageType() {
        return MessageType.EVENT;
      }
    };
  }

  @Nested
  class ErrorPolicyTests {

    @Test
    void add_withinCapacity_succeeds() throws InterruptedException {
      MessageBuffer buffer = new MessageBuffer(1024, BufferOverflowPolicy.ERROR);
      buffer.add(makeMessage(100));
      assertEquals(1, buffer.size());
      assertEquals(100, buffer.currentSizeBytes());
    }

    @Test
    void add_exceedingCapacity_throwsBackpressureException() {
      MessageBuffer buffer = new MessageBuffer(100, BufferOverflowPolicy.ERROR);
      assertThrows(
          BackpressureException.class,
          () -> {
            buffer.add(makeMessage(101));
          });
    }

    @Test
    void add_multipleMessages_tracksSizeCorrectly() throws InterruptedException {
      MessageBuffer buffer = new MessageBuffer(500, BufferOverflowPolicy.ERROR);
      buffer.add(makeMessage(100));
      buffer.add(makeMessage(200));
      assertEquals(2, buffer.size());
      assertEquals(300, buffer.currentSizeBytes());
    }

    @Test
    void add_afterFlush_allowsMore() throws InterruptedException {
      MessageBuffer buffer = new MessageBuffer(100, BufferOverflowPolicy.ERROR);
      buffer.add(makeMessage(80));
      buffer.flush(msg -> {});
      assertEquals(0, buffer.size());
      assertEquals(0, buffer.currentSizeBytes());
      buffer.add(makeMessage(90));
      assertEquals(1, buffer.size());
    }
  }

  @Nested
  class BlockPolicyTests {

    @Test
    void add_withinCapacity_succeeds() throws InterruptedException {
      MessageBuffer buffer = new MessageBuffer(1024, BufferOverflowPolicy.BLOCK);
      buffer.add(makeMessage(100));
      assertEquals(1, buffer.size());
    }

    @Test
    void add_exceedingCapacity_blocksUntilSpaceAvailable() throws Exception {
      MessageBuffer buffer = new MessageBuffer(100, BufferOverflowPolicy.BLOCK);
      buffer.add(makeMessage(80));

      CountDownLatch addStarted = new CountDownLatch(1);
      CountDownLatch addCompleted = new CountDownLatch(1);

      Thread producer =
          new Thread(
              () -> {
                try {
                  addStarted.countDown();
                  buffer.add(makeMessage(50));
                  addCompleted.countDown();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });
      producer.start();
      addStarted.await(1, TimeUnit.SECONDS);
      Thread.sleep(100);
      assertFalse(addCompleted.await(50, TimeUnit.MILLISECONDS));

      buffer.flush(msg -> {});
      assertTrue(addCompleted.await(2, TimeUnit.SECONDS));
      producer.join(2000);
    }
  }

  @Nested
  class FlushTests {

    @Test
    void flush_drainsInFifoOrder() throws InterruptedException {
      MessageBuffer buffer = new MessageBuffer(1024, BufferOverflowPolicy.ERROR);
      buffer.add(makeMessage(10));
      buffer.add(makeMessage(20));
      buffer.add(makeMessage(30));

      List<Long> sizes = new ArrayList<>();
      buffer.flush(msg -> sizes.add(msg.estimatedSizeBytes()));

      assertEquals(List.of(10L, 20L, 30L), sizes);
      assertEquals(0, buffer.size());
      assertEquals(0, buffer.currentSizeBytes());
    }

    @Test
    void flush_emptyBuffer_doesNothing() {
      MessageBuffer buffer = new MessageBuffer(1024, BufferOverflowPolicy.ERROR);
      List<BufferedMessage> flushed = new ArrayList<>();
      buffer.flush(flushed::add);
      assertTrue(flushed.isEmpty());
    }
  }

  @Nested
  class DiscardTests {

    @Test
    void discardAll_clearsBuffer() throws InterruptedException {
      MessageBuffer buffer = new MessageBuffer(1024, BufferOverflowPolicy.ERROR);
      buffer.add(makeMessage(100));
      buffer.add(makeMessage(200));
      buffer.discardAll();
      assertEquals(0, buffer.size());
      assertEquals(0, buffer.currentSizeBytes());
    }

    @Test
    void discardAll_firesOnBufferDrainCallback() throws InterruptedException {
      MessageBuffer buffer = new MessageBuffer(1024, BufferOverflowPolicy.ERROR);
      AtomicInteger discardedCount = new AtomicInteger(0);
      buffer.setOnBufferDrainCallback(discardedCount::set);

      buffer.add(makeMessage(50));
      buffer.add(makeMessage(50));
      buffer.add(makeMessage(50));
      buffer.discardAll();

      assertEquals(3, discardedCount.get());
    }

    @Test
    void discardAll_emptyBuffer_doesNotFireCallback() {
      MessageBuffer buffer = new MessageBuffer(1024, BufferOverflowPolicy.ERROR);
      AtomicInteger callbackCount = new AtomicInteger(0);
      buffer.setOnBufferDrainCallback(callbackCount::set);
      buffer.discardAll();
      assertEquals(0, callbackCount.get());
    }
  }

  @Nested
  class ConcurrencyTests {

    @Test
    void concurrentAdds_maintainConsistency() throws Exception {
      MessageBuffer buffer = new MessageBuffer(10_000_000, BufferOverflowPolicy.ERROR);
      int threadCount = 10;
      int messagesPerThread = 100;
      ExecutorService executor = Executors.newFixedThreadPool(threadCount);

      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < threadCount; i++) {
        futures.add(
            executor.submit(
                () -> {
                  for (int j = 0; j < messagesPerThread; j++) {
                    try {
                      buffer.add(makeMessage(100));
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                    }
                  }
                }));
      }

      for (Future<?> f : futures) {
        f.get(5, TimeUnit.SECONDS);
      }

      executor.shutdown();
      assertEquals(threadCount * messagesPerThread, buffer.size());
      assertEquals((long) threadCount * messagesPerThread * 100, buffer.currentSizeBytes());
    }
  }
}
