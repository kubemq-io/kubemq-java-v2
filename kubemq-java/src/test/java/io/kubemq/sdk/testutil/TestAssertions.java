package io.kubemq.sdk.testutil;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Set;
import java.util.stream.Collectors;

/** Shared assertion utilities for SDK tests. */
public final class TestAssertions {

  private static final String SDK_THREAD_PREFIX = "kubemq-";

  private TestAssertions() {
    // utility class
  }

  /**
   * Asserts no SDK-specific threads are running (leaked). Filters by thread name prefix "kubemq-".
   *
   * <p>Requires SDK executors to use named ThreadFactory with "kubemq-" prefix.
   */
  public static void assertNoSdkThreadLeaks() {
    Set<String> sdkThreads =
        Thread.getAllStackTraces().keySet().stream()
            .map(Thread::getName)
            .filter(name -> name.startsWith(SDK_THREAD_PREFIX))
            .collect(Collectors.toSet());
    assertTrue(sdkThreads.isEmpty(), "Leaked SDK threads: " + sdkThreads);
  }

  /** Asserts no JVM deadlocks exist. */
  public static void assertNoDeadlocks() {
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    long[] deadlocked = bean.findDeadlockedThreads();
    assertNull(deadlocked, "Deadlocked threads detected");
  }

  /**
   * Asserts the exception is of the expected SDK error type and optionally contains the expected
   * message substring.
   *
   * @param e the exception to check
   * @param expectedType expected exception class
   * @param messageSubstring substring to search for in the message, or null to skip
   */
  public static void assertSdkException(
      Exception e, Class<?> expectedType, String messageSubstring) {
    assertTrue(
        expectedType.isInstance(e),
        "Expected " + expectedType.getSimpleName() + " but got " + e.getClass().getSimpleName());
    if (messageSubstring != null) {
      assertTrue(
          e.getMessage() != null && e.getMessage().contains(messageSubstring),
          "Expected message containing '" + messageSubstring + "' but got: " + e.getMessage());
    }
  }
}
