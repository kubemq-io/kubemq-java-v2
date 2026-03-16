package io.kubemq.sdk.unit.client;

import static org.junit.jupiter.api.Assertions.*;

import io.kubemq.sdk.cq.CQClient;
import io.kubemq.sdk.pubsub.PubSubClient;
import io.kubemq.sdk.queues.QueuesClient;
import io.kubemq.sdk.testutil.TestAssertions;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Resource leak detection per REQ-TEST-1 AC-6.
 *
 * <p>Java-specific approach (per Review R2 M-3): 1. gRPC leaks: assert ManagedChannel.isShutdown()
 * and isTerminated() 2. SDK thread leaks: filter by thread name prefix "kubemq-" 3. Deadlock
 * detection: ThreadMXBean.findDeadlockedThreads()
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ResourceLeakDetectionTest {

  private static final String SDK_THREAD_PREFIX = "kubemq-";

  @Test
  void pubSubClient_afterClose_noLeakedSdkThreads() throws InterruptedException {
    Set<String> threadsBefore = getSdkThreadNames();

    PubSubClient client =
        PubSubClient.builder().address("localhost:50000").clientId("leak-test-pubsub").build();
    client.close();

    Thread.sleep(2000);

    Set<String> threadsAfter = getSdkThreadNames();
    threadsAfter.removeAll(threadsBefore);

    assertTrue(
        threadsAfter.isEmpty(), "Leaked SDK threads after PubSubClient close: " + threadsAfter);
  }

  @Test
  void queuesClient_afterClose_noLeakedSdkThreads() throws InterruptedException {
    Set<String> threadsBefore = getSdkThreadNames();

    QueuesClient client =
        QueuesClient.builder().address("localhost:50000").clientId("leak-test-queues").build();
    client.close();

    Thread.sleep(2000);

    Set<String> threadsAfter = getSdkThreadNames();
    threadsAfter.removeAll(threadsBefore);

    assertTrue(
        threadsAfter.isEmpty(), "Leaked SDK threads after QueuesClient close: " + threadsAfter);
  }

  @Test
  void cqClient_afterClose_noLeakedSdkThreads() throws InterruptedException {
    Set<String> threadsBefore = getSdkThreadNames();

    CQClient client =
        CQClient.builder().address("localhost:50000").clientId("leak-test-cq").build();
    client.close();

    Thread.sleep(2000);

    Set<String> threadsAfter = getSdkThreadNames();
    threadsAfter.removeAll(threadsBefore);

    assertTrue(threadsAfter.isEmpty(), "Leaked SDK threads after CQClient close: " + threadsAfter);
  }

  @Test
  void afterClientLifecycle_noDeadlockedThreads() {
    CQClient client =
        CQClient.builder().address("localhost:50000").clientId("deadlock-test").build();
    client.close();

    ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();
    assertNull(deadlockedThreads, "Deadlocked threads detected after client lifecycle");
  }

  @Test
  void channel_isShutdownAfterClose() {
    PubSubClient client =
        PubSubClient.builder().address("localhost:50000").clientId("shutdown-check").build();

    assertNotNull(client.getManagedChannel());
    assertFalse(client.getManagedChannel().isShutdown());

    client.close();

    assertTrue(
        client.getManagedChannel().isShutdown(), "ManagedChannel must be shutdown after close()");
  }

  @Test
  void multipleClientLifecycles_noDeadlocks() {
    for (int i = 0; i < 5; i++) {
      PubSubClient client =
          PubSubClient.builder()
              .address("localhost:50000")
              .clientId("multi-lifecycle-" + i)
              .build();
      client.close();
    }

    TestAssertions.assertNoDeadlocks();
  }

  private Set<String> getSdkThreadNames() {
    return Thread.getAllStackTraces().keySet().stream()
        .map(Thread::getName)
        .filter(name -> name.startsWith(SDK_THREAD_PREFIX))
        .collect(Collectors.toSet());
  }
}
