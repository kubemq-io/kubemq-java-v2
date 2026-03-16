package io.kubemq.sdk.client;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Test-only accessor for protected KubeMQClient methods. Lives in the same package so it can reach
 * protected members.
 */
public final class KubeMQClientAccessor {

  private KubeMQClientAccessor() {}

  public static <T> CompletableFuture<T> executeWithCancellation(
      KubeMQClient client, Supplier<T> operation) {
    return client.executeWithCancellation(operation);
  }

  public static <T> T unwrapFuture(
      KubeMQClient client, CompletableFuture<T> future, Duration timeout) {
    return client.unwrapFuture(future, timeout);
  }

  public static RuntimeException unwrapException(
      KubeMQClient client, java.util.concurrent.ExecutionException e) {
    return client.unwrapException(e);
  }
}
